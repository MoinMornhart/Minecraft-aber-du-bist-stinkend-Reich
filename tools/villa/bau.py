"""Werkzeugkasten für den Villa-Entwurf: Blöcke setzen, Formen bauen, als Strukturdatei speichern.

Koordinaten: x nach Osten, y nach oben, z nach Süden. (0, 0, 0) ist die untere
Nordwest-Ecke der Strukturdatei. Nicht gesetzte Positionen bleiben beim Platzieren
unverändert (Struktur-Leere); „minecraft:air“ räumt aktiv aus.
"""
from __future__ import annotations

import gzip
import io
import json
import sys
from pathlib import Path

import nbtlib
import numpy as np
from nbtlib.tag import Byte, Compound, Double, Float, Int, List, Long, Short, String

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "lib"))
from anvil import Volumen  # noqa: E402

# DataVersion von Minecraft 1.21. Neuere Versionen rüsten die Datei beim Laden selbst auf.
DATENVERSION_121 = 3953

LUFT = "minecraft:air"

# Blockfamilien, deren Seiten sich mit Nachbarn verbinden
VERBINDEND = ("_pane", "iron_bars", "_fence")
MAUERN = ("_wall",)
# Blöcke, an die sich Zäune/Scheiben NICHT anlehnen
NICHT_VOLL = ("air", "slab", "stairs", "carpet", "door", "trapdoor", "button", "lever", "torch", "lantern", "flower",
              "sapling", "grass", "fern", "leaves", "water", "lava", "rail", "sign", "bed", "chest", "pressure_plate",
              "end_rod", "chain", "candle", "pot", "rod", "vine", "banner", "head", "skull", "light", "campfire",
              "cauldron", "bell", "anvil", "hopper", "_gate", "ladder", "glass_pane", "bars", "fence", "_wall",
              "scaffolding", "cobweb", "snow", "moss_carpet", "pickle", "kelp", "coral", "tulip", "poppy",
              "dandelion", "allium", "orchid", "daisy", "cornflower", "lily", "bush", "azalea", "dripleaf",
              "frame", "sculk_sensor", "lectern", "grindstone", "stonecutter", "brewing", "stinkendreich:")


def zustand(name: str, **props) -> str:
    """Blockzustand als Text, z. B. zustand('oak_stairs', facing='north') -> 'minecraft:oak_stairs[facing=north]'."""
    if ":" not in name:
        name = "minecraft:" + name
    if not props:
        return name
    teile = ",".join(f"{k}={str(v).lower()}" for k, v in sorted(props.items()))
    return f"{name}[{teile}]"


def basisname(block: str) -> str:
    return block.split("[")[0]


def eigenschaften(block: str) -> dict[str, str]:
    if "[" not in block:
        return {}
    return dict(p.split("=", 1) for p in block.split("[", 1)[1].rstrip("]").split(","))


def ist_voll(block: str | None) -> bool:
    if block is None:
        return False
    n = basisname(block)
    if n.endswith("sea_lantern"):
        return True
    return not any(t in n for t in NICHT_VOLL)


# Blöcke, in die fließendes Wasser oder Lava NICHT hineinläuft, obwohl sie nicht voll sind:
# Türen (ausdrücklich ausgenommen) und alles, was Wasser speichern kann (Stufen, Scheiben, Laternen …)
HAELT_FLUESSIGKEIT = ("door", "slab", "stairs", "trapdoor", "_pane", "bars", "fence", "_wall", "lantern", "chain",
                      "ladder", "sign", "light", "campfire", "chest", "barrel", "glass", "leaves", "sea_pickle",
                      "coral", "kelp", "seagrass", "stinkendreich:")


def voller_modblock(block: str) -> bool:
    """Eigene Blöcke ohne Ausrichtung (Torsteuerung, Düse …) sind volle Würfel."""
    return basisname(block).startswith("stinkendreich:") and "facing" not in eigenschaften(block) and "open" not in eigenschaften(block)


def haelt_fluessigkeit(block: str | None) -> bool:
    """Läuft Wasser von nebenan NICHT in diesen Block? (None = unbekanntes Gelände: kann eine Höhle sein)."""
    if block is None:
        return False
    n = basisname(block)
    if n in ("minecraft:water", "minecraft:lava"):
        return True
    return ist_voll(block) or any(t in n for t in HAELT_FLUESSIGKEIT)


GEGENRICHTUNG = {"north": "south", "south": "north", "east": "west", "west": "east"}
RICHTUNG_VEKTOR = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0)}


class Bau:
    """Ein Quader voller Blöcke, der als Minecraft-Strukturdatei gespeichert wird."""

    def __init__(self, dx: int, dy: int, dz: int):
        self.groesse = (dx, dy, dz)
        self.bloecke: dict[tuple[int, int, int], str] = {}
        self.block_daten: dict[tuple[int, int, int], dict] = {}
        self.entities: list[tuple[tuple[float, float, float], dict]] = []
        self.marken: dict[str, list] = {}
        # Stellen, an denen Wasser absichtlich fließt (z. B. Regendusche), für die Dichtheitsprüfung
        self.gewollter_fluss: set[tuple[int, int, int]] = set()

    # ---------- Grundoperationen ----------
    def drin(self, x: int, y: int, z: int) -> bool:
        dx, dy, dz = self.groesse
        return 0 <= x < dx and 0 <= y < dy and 0 <= z < dz

    def setze(self, x: int, y: int, z: int, block: str, daten: dict | None = None) -> None:
        if not self.drin(x, y, z):
            return
        if ":" not in block:
            block = "minecraft:" + block
        self.bloecke[(x, y, z)] = block
        if daten is not None:
            self.block_daten[(x, y, z)] = daten
        else:
            self.block_daten.pop((x, y, z), None)

    def hole(self, x: int, y: int, z: int) -> str | None:
        return self.bloecke.get((x, y, z))

    def quader(self, x0, y0, z0, x1, y1, z1, block: str) -> None:
        """Gefüllter Quader, Ecken inklusive."""
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.setze(x, y, z, block)

    def luft(self, x0, y0, z0, x1, y1, z1) -> None:
        self.quader(x0, y0, z0, x1, y1, z1, LUFT)

    def waende(self, x0, y0, z0, x1, y1, z1, block: str) -> None:
        """Nur die vier Seitenwände eines Quaders."""
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for x in range(min(x0, x1), max(x0, x1) + 1):
                self.setze(x, y, z0, block)
                self.setze(x, y, z1, block)
            for z in range(min(z0, z1), max(z0, z1) + 1):
                self.setze(x0, y, z, block)
                self.setze(x1, y, z, block)

    def ring(self, x0, z0, x1, z1, y, block: str, breite: int = 1) -> None:
        """Rechteckiger Ring in Höhe y, nach innen `breite` Blöcke breit."""
        for b in range(breite):
            for x in range(x0 + b, x1 - b + 1):
                self.setze(x, y, z0 + b, block)
                self.setze(x, y, z1 - b, block)
            for z in range(z0 + b, z1 - b + 1):
                self.setze(x0 + b, y, z, block)
                self.setze(x1 - b, y, z, block)

    def marke(self, name: str, *werte) -> None:
        """Merkt sich eine Position (z. B. Spawnpunkt, Aufzug) für die Mod."""
        self.marken.setdefault(name, []).append(list(werte))

    def entity(self, x: float, y: float, z: float, nbt: dict) -> None:
        self.entities.append(((x, y, z), nbt))

    # ---------- Nachbearbeitung ----------
    def pruefe_dichtheit(self, boden_y: int) -> list[str]:
        """
        Sucht Lecks im Bauplan, bevor sie im Spiel Räume fluten:
          - Wasser oder Lava neben Luft oder nicht festen Blöcken (läuft aus) oder neben
            unbekanntem Gelände unterhalb der Grasschicht (Höhle möglich)
          - unterirdische Räume (Luft unter der Grasschicht), die an unbekanntes Gelände grenzen
            (Grundwasser und Höhlen können hereinlaufen)
        """
        probleme = []
        seiten = ((0, -1, 0), (1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1))
        for (x, y, z), block in self.bloecke.items():
            name = basisname(block)
            if name in ("minecraft:water", "minecraft:lava"):
                for dx, dy, dz in seiten:
                    n = self.hole(x + dx, y + dy, z + dz)
                    if n is None and y + dy > boden_y:
                        continue  # über der Grasschicht: dort ist draußen nur Luft, das ist kein Leck
                    if n is None or not haelt_fluessigkeit(n):
                        probleme.append(f"{name[10:]} bei {x} {y} {z} läuft nach {x + dx} {y + dy} {z + dz} ({n or 'Gelände'})")
            elif y < boden_y and (name == LUFT or not ist_voll(block)) and not voller_modblock(block):
                for dx, dy, dz in seiten + ((0, 1, 0),):
                    if self.drin(x + dx, y + dy, z + dz) and self.hole(x + dx, y + dy, z + dz) is None:
                        probleme.append(f"Untergrund offen bei {x} {y} {z} zum Gelände {x + dx} {y + dy} {z + dz}")
        return [p for p in probleme if not any(f"bei {x} {y} {z} " in p for (x, y, z) in self.gewollter_fluss)]

    def verbinden(self) -> None:
        """Glasscheiben, Gitter, Zäune und Mauern mit ihren Nachbarn verbinden."""
        for (x, y, z), block in list(self.bloecke.items()):
            n = basisname(block)
            ist_verbindend = any(t in n for t in VERBINDEND) and "_gate" not in n
            ist_mauer = any(n.endswith(t) for t in MAUERN)
            if not (ist_verbindend or ist_mauer):
                continue
            props = eigenschaften(block)
            for richtung, (vx, _, vz) in RICHTUNG_VEKTOR.items():
                nachbar = self.hole(x + vx, y, z + vz)
                nb = basisname(nachbar) if nachbar else ""
                verbindet = ist_voll(nachbar) or (
                    nachbar is not None and (any(t in nb for t in VERBINDEND) or nb.endswith("_wall") or "_gate" in nb)
                )
                if ist_mauer:
                    props[richtung] = "low" if verbindet else "none"
                else:
                    props[richtung] = "true" if verbindet else "false"
            if ist_mauer:
                oben = self.hole(x, y + 1, z)
                gerade = (props["north"] != "none" and props["south"] != "none" and props["east"] == "none" and props["west"] == "none") or (
                    props["east"] != "none" and props["west"] != "none" and props["north"] == "none" and props["south"] == "none")
                props["up"] = "false" if gerade and not oben else "true"
            self.bloecke[(x, y, z)] = zustand(n, **props)

    # ---------- Speichern ----------
    def als_nbt(self, nur_bereich: tuple | None = None) -> nbtlib.File:
        palette: list[str] = []
        index: dict[str, int] = {}
        bloecke = []
        for (x, y, z), block in sorted(self.bloecke.items(), key=lambda e: (e[0][1], e[0][2], e[0][0])):
            if block not in index:
                index[block] = len(palette)
                palette.append(block)
            eintrag = {"pos": List[Int]([Int(x), Int(y), Int(z)]), "state": Int(index[block])}
            if (x, y, z) in self.block_daten:
                eintrag["nbt"] = nbt_aus_python(self.block_daten[(x, y, z)])
            bloecke.append(Compound(eintrag))

        palette_nbt = []
        for block in palette:
            p = {"Name": String(basisname(block))}
            props = eigenschaften(block)
            if props:
                p["Properties"] = Compound({k: String(v) for k, v in props.items()})
            palette_nbt.append(Compound(p))

        entities = []
        for (x, y, z), daten in self.entities:
            entities.append(Compound({
                "pos": List[Double]([Double(x), Double(y), Double(z)]),
                "blockPos": List[Int]([Int(int(x)), Int(int(y)), Int(int(z))]),
                "nbt": nbt_aus_python(daten),
            }))

        dx, dy, dz = self.groesse
        wurzel = Compound({
            "DataVersion": Int(DATENVERSION_121),
            "size": List[Int]([Int(dx), Int(dy), Int(dz)]),
            "palette": List[Compound](palette_nbt),
            "blocks": List[Compound](bloecke),
            "entities": List[Compound](entities),
        })
        return nbtlib.File(wurzel, root_name="")

    def speichern(self, pfad: Path) -> None:
        pfad.parent.mkdir(parents=True, exist_ok=True)
        datei = self.als_nbt()
        datei.save(pfad, gzipped=True)

    def als_volumen(self) -> Volumen:
        vol = Volumen((0, 0, 0), self.groesse)
        for (x, y, z), block in self.bloecke.items():
            vol.bloecke[y, z, x] = vol.index_von(block)
        return vol


def nbt_aus_python(wert):
    """Python-Werte in NBT umwandeln. Ganzzahlen werden Int, außer mit Endung: ('b', 1) usw."""
    if isinstance(wert, nbtlib.tag.Base):
        return wert
    if isinstance(wert, dict):
        return Compound({k: nbt_aus_python(v) for k, v in wert.items()})
    if isinstance(wert, tuple) and len(wert) == 2 and isinstance(wert[0], str) and wert[0] in ("b", "s", "l", "f", "d"):
        art, zahl = wert
        return {"b": Byte, "s": Short, "l": Long, "f": Float, "d": Double}[art](zahl)
    if isinstance(wert, bool):
        return Byte(1 if wert else 0)
    if isinstance(wert, int):
        return Int(wert)
    if isinstance(wert, float):
        return Double(wert)
    if isinstance(wert, str):
        return String(wert)
    if isinstance(wert, list):
        if not wert:
            return List[String]([])
        inhalt = [nbt_aus_python(v) for v in wert]
        return List[type(inhalt[0])](inhalt)
    raise TypeError(f"Unbekannter NBT-Wert: {wert!r}")
