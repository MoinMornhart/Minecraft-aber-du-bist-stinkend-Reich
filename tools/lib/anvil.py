"""Liest Minecraft-Welten (Anvil-Regionsdateien) direkt mit Python, ohne Minecraft.

Unterstützt die Chunk-Formate ab 1.13 (Paletten), also auch die 1.14/1.15-Welt
der Vorlage und alle neueren Welten. Ältere Welten mit numerischen Block-IDs
(vor 1.13) werden übersprungen.
"""
from __future__ import annotations

import gzip
import io
import math
import zlib
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterator

import nbtlib
import numpy as np

# Ab dieser DataVersion (20w17a, 1.16) passen Paletten-Indizes nicht mehr über
# Long-Grenzen hinweg; davor schon.
DATAVERSION_OHNE_UEBERLAUF = 2527


@dataclass
class Abschnitt:
    """Ein 16x16x16-Abschnitt eines Chunks."""

    cx: int
    sy: int
    cz: int
    palette: list[str]
    # Indizes in die Palette, Reihenfolge y, z, x (wie in Minecraft)
    indizes: np.ndarray


@dataclass
class Chunk:
    cx: int
    cz: int
    datenversion: int
    abschnitte: list[Abschnitt] = field(default_factory=list)
    block_entities: list[dict] = field(default_factory=list)
    entities: list[dict] = field(default_factory=list)


def nbt_zu_python(tag):
    """nbtlib-Tags rekursiv in normale Python-Typen umwandeln."""
    if isinstance(tag, nbtlib.tag.Compound):
        return {str(k): nbt_zu_python(v) for k, v in tag.items()}
    if isinstance(tag, (nbtlib.tag.List,)):
        return [nbt_zu_python(v) for v in tag]
    if isinstance(tag, (nbtlib.tag.ByteArray, nbtlib.tag.IntArray, nbtlib.tag.LongArray)):
        return [int(v) for v in tag]
    if isinstance(tag, nbtlib.tag.String):
        return str(tag)
    if isinstance(tag, (nbtlib.tag.Float, nbtlib.tag.Double)):
        return float(tag)
    if isinstance(tag, (nbtlib.tag.Byte, nbtlib.tag.Short, nbtlib.tag.Int, nbtlib.tag.Long)):
        return int(tag)
    return tag


def blockzustand_text(eintrag) -> str:
    """Paletten-Eintrag als 'minecraft:name[prop=wert,...]'."""
    name = str(eintrag["Name"])
    props = eintrag.get("Properties")
    if props:
        teile = ",".join(f"{k}={props[k]}" for k in sorted(props.keys()))
        return f"{name}[{teile}]"
    return name


def _entpacken(longs, anzahl_palette: int, ueberlauf: bool) -> np.ndarray:
    """Gepackte Paletten-Indizes (4096 Stück) aus einem Long-Array lesen."""
    bits = max(4, math.ceil(math.log2(anzahl_palette))) if anzahl_palette > 1 else 4
    daten = np.array([int(v) & 0xFFFFFFFFFFFFFFFF for v in longs], dtype=np.uint64)
    i = np.arange(4096, dtype=np.uint64)
    maske = np.uint64((1 << bits) - 1)
    b = np.uint64(bits)
    if ueberlauf:
        pos = i * b
        k = (pos // np.uint64(64)).astype(np.int64)
        o = pos % np.uint64(64)
        lo = daten[k] >> o
        # Teil aus dem nächsten Long, falls der Wert über die Grenze ragt
        k2 = np.minimum(k + 1, len(daten) - 1)
        rest = np.uint64(64) - o
        hi = np.where(o + b > np.uint64(64), daten[k2] << np.where(rest == 64, np.uint64(0), rest), np.uint64(0))
        werte = (lo | hi) & maske
    else:
        pro_long = np.uint64(64 // bits)
        k = (i // pro_long).astype(np.int64)
        o = (i % pro_long) * b
        werte = (daten[k] >> o) & maske
    return werte.astype(np.int32)


def _lies_chunk(wurzel) -> Chunk | None:
    datenversion = int(wurzel.get("DataVersion", 0))
    level = wurzel["Level"] if "Level" in wurzel else wurzel
    if "xPos" not in level:
        return None
    chunk = Chunk(int(level["xPos"]), int(level["zPos"]), datenversion)
    ueberlauf = datenversion < DATAVERSION_OHNE_UEBERLAUF

    abschnitte = level.get("Sections") if "Sections" in level else level.get("sections", [])
    for sek in abschnitte or []:
        sy = int(sek["Y"])
        if "Palette" in sek:  # 1.13 bis 1.17
            palette_nbt = sek["Palette"]
            gepackt = sek.get("BlockStates")
        elif "block_states" in sek:  # ab 1.18
            palette_nbt = sek["block_states"]["palette"]
            gepackt = sek["block_states"].get("data")
        else:
            continue
        palette = [blockzustand_text(p) for p in palette_nbt]
        if gepackt is None or len(palette) == 1:
            indizes = np.zeros(4096, dtype=np.int32)
        else:
            indizes = _entpacken(gepackt, len(palette), ueberlauf)
        chunk.abschnitte.append(Abschnitt(chunk.cx, sy, chunk.cz, palette, indizes))

    be = level.get("TileEntities") if "TileEntities" in level else wurzel.get("block_entities", [])
    chunk.block_entities = [nbt_zu_python(t) for t in (be or [])]
    ents = level.get("Entities") if "Entities" in level else []
    chunk.entities = [nbt_zu_python(t) for t in (ents or [])]
    return chunk


def lies_region(pfad: Path) -> Iterator[Chunk]:
    """Alle Chunks einer .mca-Datei liefern."""
    daten = pfad.read_bytes()
    if len(daten) < 8192:
        return
    for i in range(1024):
        offset = int.from_bytes(daten[i * 4:i * 4 + 3], "big")
        sektoren = daten[i * 4 + 3]
        if offset == 0 or sektoren == 0:
            continue
        start = offset * 4096
        laenge = int.from_bytes(daten[start:start + 4], "big")
        art = daten[start + 4]
        nutzlast = daten[start + 5:start + 4 + laenge]
        if art & 0x80:  # Chunk liegt in eigener .mcc-Datei
            cx, cz = i % 32, i // 32
            rx, rz = (int(x) for x in pfad.stem.split(".")[1:3])
            extern = pfad.with_name(f"c.{rx * 32 + cx}.{rz * 32 + cz}.mcc")
            if not extern.exists():
                continue
            nutzlast = extern.read_bytes()
            art &= 0x7F
        try:
            if art == 2:
                roh = zlib.decompress(nutzlast)
            elif art == 1:
                roh = gzip.decompress(nutzlast)
            elif art == 3:
                roh = nutzlast
            else:
                continue
            wurzel = nbtlib.File.parse(io.BytesIO(roh))
        except Exception:
            continue
        chunk = _lies_chunk(wurzel)
        if chunk is not None:
            yield chunk


def lies_entity_region(pfad: Path) -> Iterator[dict]:
    """Entities aus dem entities/-Ordner (ab 1.17) liefern."""
    daten = pfad.read_bytes()
    if len(daten) < 8192:
        return
    for i in range(1024):
        offset = int.from_bytes(daten[i * 4:i * 4 + 3], "big")
        if offset == 0:
            continue
        start = offset * 4096
        laenge = int.from_bytes(daten[start:start + 4], "big")
        art = daten[start + 4]
        try:
            roh = zlib.decompress(daten[start + 5:start + 4 + laenge]) if art == 2 else gzip.decompress(daten[start + 5:start + 4 + laenge])
            wurzel = nbtlib.File.parse(io.BytesIO(roh))
        except Exception:
            continue
        for e in wurzel.get("Entities", []):
            yield nbt_zu_python(e)


def lies_nbt_datei(pfad: Path) -> dict:
    """level.dat, scoreboard.dat usw. (gzip-komprimiert oder roh)."""
    roh = pfad.read_bytes()
    try:
        roh = gzip.decompress(roh)
    except OSError:
        pass
    return nbt_zu_python(nbtlib.File.parse(io.BytesIO(roh)))


class Volumen:
    """Ein Quader der Welt als numpy-Array von Block-Namen-Indizes.

    `bloecke[y, z, x]` ist ein Index in `namen`. Koordinaten beginnen bei `ursprung`.
    """

    def __init__(self, ursprung: tuple[int, int, int], groesse: tuple[int, int, int]):
        self.ursprung = ursprung
        self.groesse = groesse  # (dx, dy, dz)
        dx, dy, dz = groesse
        self.namen: list[str] = ["minecraft:air"]
        self._index: dict[str, int] = {"minecraft:air": 0}
        self.bloecke = np.zeros((dy, dz, dx), dtype=np.int32)

    def index_von(self, name: str) -> int:
        if name not in self._index:
            self._index[name] = len(self.namen)
            self.namen.append(name)
        return self._index[name]

    def einfuegen(self, abschnitt: Abschnitt) -> None:
        """Einen Chunk-Abschnitt übernehmen, soweit er im Quader liegt."""
        ox, oy, oz = self.ursprung
        dx, dy, dz = self.groesse
        bx, by, bz = abschnitt.cx * 16, abschnitt.sy * 16, abschnitt.cz * 16
        x0, x1 = max(bx, ox), min(bx + 16, ox + dx)
        y0, y1 = max(by, oy), min(by + 16, oy + dy)
        z0, z1 = max(bz, oz), min(bz + 16, oz + dz)
        if x0 >= x1 or y0 >= y1 or z0 >= z1:
            return
        umrechnung = np.array([self.index_von(n) for n in abschnitt.palette], dtype=np.int32)
        lokal = umrechnung[abschnitt.indizes].reshape(16, 16, 16)
        self.bloecke[y0 - oy:y1 - oy, z0 - oz:z1 - oz, x0 - ox:x1 - ox] = lokal[
            y0 - by:y1 - by, z0 - bz:z1 - bz, x0 - bx:x1 - bx
        ]
