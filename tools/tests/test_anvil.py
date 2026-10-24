"""Tests für den Welt-Leser: baut kleine Testwelten im 1.14- und 1.18-Format und liest sie zurück.

Aufruf:  python3 -m pytest tools/tests   (oder direkt: python3 tools/tests/test_anvil.py)
"""
from __future__ import annotations

import io
import math
import random
import sys
import tempfile
import zlib
from pathlib import Path

import nbtlib
from nbtlib.tag import Byte, Compound, Int, List, Long, LongArray, String

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "lib"))
from anvil import Volumen, lies_region  # noqa: E402


def packen(indizes: list[int], anzahl_palette: int, ueberlauf: bool) -> list[int]:
    """Gegenstück zum Entpacken, nach der Formatbeschreibung im Minecraft-Wiki."""
    bits = max(4, math.ceil(math.log2(anzahl_palette)))
    if ueberlauf:
        gesamt = 0
        for i, v in enumerate(indizes):
            gesamt |= v << (i * bits)
        anzahl = math.ceil(4096 * bits / 64)
        longs = [(gesamt >> (64 * k)) & 0xFFFFFFFFFFFFFFFF for k in range(anzahl)]
    else:
        pro_long = 64 // bits
        longs = []
        for k in range(math.ceil(4096 / pro_long)):
            wert = 0
            for j, v in enumerate(indizes[k * pro_long:(k + 1) * pro_long]):
                wert |= v << (j * bits)
            longs.append(wert)
    # Long-Arrays sind vorzeichenbehaftet
    return [v - (1 << 64) if v >= 1 << 63 else v for v in longs]


def region_schreiben(pfad: Path, chunks: dict[tuple[int, int], Compound]) -> None:
    kopf = bytearray(8192)
    daten = bytearray()
    sektor = 2
    for (cx, cz), wurzel in chunks.items():
        puffer = io.BytesIO()
        nbtlib.File(wurzel).write(puffer)
        roh = zlib.compress(puffer.getvalue())
        # Länge (inkl. Kompressionsbyte), Kompression 2 = zlib, Daten
        eintrag = (len(roh) + 1).to_bytes(4, "big") + bytes([2]) + roh
        eintrag += bytes((-len(eintrag)) % 4096)
        anzahl = len(eintrag) // 4096
        i = (cx % 32) + (cz % 32) * 32
        kopf[i * 4:i * 4 + 3] = sektor.to_bytes(3, "big")
        kopf[i * 4 + 3] = anzahl
        daten += eintrag
        sektor += anzahl
    pfad.write_bytes(bytes(kopf) + bytes(daten))


def abschnitt_alt(palette: list[str], indizes: list[int], y: int) -> Compound:
    return Compound({
        "Y": Byte(y),
        "Palette": List[Compound]([Compound({"Name": String(n)}) for n in palette]),
        "BlockStates": LongArray([Long(v) for v in packen(indizes, len(palette), True)]),
    })


def test_format_114_und_118():
    rnd = random.Random(7)
    # Palette mit 37 Einträgen -> 6 Bit, Werte ragen beim alten Format über Long-Grenzen
    palette = ["minecraft:air"] + [f"minecraft:test_{i}" for i in range(35)] + ["minecraft:command_block[conditional=false,facing=up]"]
    indizes = [rnd.randrange(len(palette)) for _ in range(4096)]

    alt = Compound({
        "DataVersion": Int(2230),  # 1.15.2
        "Level": Compound({
            "xPos": Int(0), "zPos": Int(0),
            "Sections": List[Compound]([abschnitt_alt(palette, indizes, 4)]),
            "TileEntities": List[Compound]([Compound({
                "id": String("minecraft:command_block"), "x": Int(1), "y": Int(64), "z": Int(2),
                "Command": String("say hallo"), "auto": Byte(0),
            })]),
            "Entities": List[Compound]([]),
        }),
    })
    neu = Compound({
        "DataVersion": Int(3953),  # 1.21
        "xPos": Int(1), "zPos": Int(0),
        "sections": List[Compound]([Compound({
            "Y": Byte(4),
            "block_states": Compound({
                "palette": List[Compound]([Compound({"Name": String(n)}) for n in palette]),
                "data": LongArray([Long(v) for v in packen(indizes, len(palette), False)]),
            }),
        })]),
        "block_entities": List[Compound]([]),
    })

    with tempfile.TemporaryDirectory() as tmp:
        pfad = Path(tmp) / "r.0.0.mca"
        region_schreiben(pfad, {(0, 0): alt, (1, 0): neu})
        chunks = list(lies_region(pfad))

    assert len(chunks) == 2
    for chunk in chunks:
        ab = chunk.abschnitte[0]
        assert ab.sy == 4
        assert ab.palette == palette
        assert ab.indizes.tolist() == indizes, f"Indizes falsch im Chunk {chunk.cx}"
    assert chunks[0].block_entities[0]["Command"] == "say hallo"

    vol = Volumen((0, 64, 0), (32, 16, 16))
    for chunk in chunks:
        for ab in chunk.abschnitte:
            vol.einfuegen(ab)
    # Block bei x=3, y=64+5, z=7 im ersten Chunk
    flach = 5 * 256 + 7 * 16 + 3
    assert vol.namen[vol.bloecke[5, 7, 3]] == palette[indizes[flach]]
    assert vol.namen[vol.bloecke[5, 7, 16 + 3]] == palette[indizes[flach]]


if __name__ == "__main__":
    test_format_114_und_118()
    print("OK")
