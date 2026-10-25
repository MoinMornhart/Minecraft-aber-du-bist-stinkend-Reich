"""Möbel und Deko aus Vanilla-Blöcken für den Villa-Entwurf."""
from __future__ import annotations

from bau import Bau, RICHTUNG_VEKTOR, GEGENRICHTUNG, zustand

RECHTS_VON = {"north": "east", "east": "south", "south": "west", "west": "north"}


def tuer(b: Bau, x: int, y: int, z: int, facing: str, material: str = "dark_oak_door", scharnier: str = "left") -> None:
    """Zweiteilige Tür; facing = Richtung, in die man beim Hinausgehen schaut."""
    b.setze(x, y, z, zustand(material, facing=facing, half="lower", hinge=scharnier, open="false"))
    b.setze(x, y + 1, z, zustand(material, facing=facing, half="upper", hinge=scharnier, open="false"))


def doppeltuer(b: Bau, x: int, y: int, z: int, facing: str, material: str = "dark_oak_door") -> None:
    """Zwei Türflügel nebeneinander (rechts vom ersten, aus Sicht der Blickrichtung)."""
    rx, _, rz = RICHTUNG_VEKTOR[RECHTS_VON[facing]]
    tuer(b, x, y, z, facing, material, "left")
    tuer(b, x + rx, y, z + rz, facing, material, "right")


def bett(b: Bau, x: int, y: int, z: int, facing: str, farbe: str = "white") -> None:
    """Bett mit Kopfende bei (x, y, z); facing zeigt vom Fuß- zum Kopfende."""
    vx, _, vz = RICHTUNG_VEKTOR[facing]
    b.setze(x, y, z, zustand(f"{farbe}_bed", facing=facing, part="head", occupied="false"))
    b.setze(x - vx, y, z - vz, zustand(f"{farbe}_bed", facing=facing, part="foot", occupied="false"))


def sofa(b: Bau, x: int, y: int, z: int, laenge: int, blick: str, material: str = "smooth_quartz_stairs",
         kissen: str | None = "gray_carpet") -> None:
    """Sofa aus Treppenstufen. blick = Richtung, in die man sitzend schaut."""
    lehne = GEGENRICHTUNG[blick]
    rx, _, rz = RICHTUNG_VEKTOR[RECHTS_VON[blick]]
    for i in range(laenge):
        b.setze(x + rx * i, y, z + rz * i, zustand(material, facing=lehne, half="bottom", shape="straight", waterlogged="false"))
    # Armlehnen
    for seite in (-1, laenge):
        b.setze(x + rx * seite, y, z + rz * seite, zustand(material.replace("_stairs", "_slab"), type="bottom", waterlogged="false"))
    if kissen:
        bx, _, bz = RICHTUNG_VEKTOR[lehne]
        for i in range(laenge):
            b.setze(x + rx * i + bx, y, z + rz * i + bz, zustand(material.replace("_stairs", "_slab"), type="top", waterlogged="false"))


def tisch(b: Bau, x0: int, y: int, z0: int, x1: int, z1: int, platte: str = "dark_oak_slab") -> None:
    """Tischplatte als obere Stufenhälfte; Beine an den Ecken."""
    b.quader(x0, y, z0, x1, y, z1, zustand(platte, type="top", waterlogged="false"))


def stuhl(b: Bau, x: int, y: int, z: int, blick: str, material: str = "dark_oak_stairs") -> None:
    b.setze(x, y, z, zustand(material, facing=GEGENRICHTUNG[blick], half="bottom", shape="straight", waterlogged="false"))


def pflanze(b: Bau, x: int, y: int, z: int, art: str = "potted_bamboo") -> None:
    b.setze(x, y, z, art)


def hecke(b: Bau, x0: int, y: int, z0: int, x1: int, z1: int, hoehe: int = 1, art: str = "azalea_leaves") -> None:
    b.quader(x0, y, z0, x1, y + hoehe - 1, z1, zustand(art, persistent="true", distance="1", waterlogged="false"))


def baum(b: Bau, x: int, y: int, z: int, art: str = "oak", hoehe: int = 6, krone: int = 2) -> None:
    """Einfacher, gepflegter Baum mit runder Krone (Blätter verrotten nicht)."""
    for i in range(hoehe):
        b.setze(x, y + i, z, zustand(f"{art}_log", axis="y"))
    blatt = zustand(f"{art}_leaves", persistent="true", distance="1", waterlogged="false")
    mitte = y + hoehe - 1
    for dy in range(-krone, krone + 1):
        r = krone + 1 - abs(dy) // 2
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if dx * dx + dz * dz + dy * dy * 2 <= r * r + 1 and not (dx == 0 and dz == 0 and dy <= 0):
                    if b.hole(x + dx, mitte + dy, z + dz) is None:
                        b.setze(x + dx, mitte + dy, z + dz, blatt)
    b.setze(x, mitte + krone + 1, z, blatt)


def laterne(b: Bau, x: int, y: int, z: int, hoehe: int = 3) -> None:
    """Moderne Gartenleuchte: dunkler Pfosten mit Licht oben."""
    for i in range(hoehe):
        b.setze(x, y + i, z, zustand("polished_blackstone_wall"))
    b.setze(x, y + hoehe, z, "sea_lantern")


def blumenbeet(b: Bau, x0: int, y: int, z0: int, x1: int, z1: int, blumen=("poppy", "allium", "oxeye_daisy", "cornflower", "azure_bluet")) -> None:
    """Beet: Erde mit Blumen im Wechsel, Rand aus Steinziegelstufen."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            rand = x in (x0, x1) or z in (z0, z1)
            if rand:
                b.setze(x, y, z, zustand("smooth_stone_slab", type="bottom", waterlogged="false"))
            else:
                b.setze(x, y - 1, z, "grass_block")
                b.setze(x, y, z, blumen[(x * 7 + z * 3) % len(blumen)])


def brunnen_kuppel(b: Bau, cx: int, y: int, cz: int) -> None:
    """Brunnen mit Glaskuppel: Quarzsockel, Wasserbecken, leuchtende Mitte, Glashaube."""
    for dx in range(-4, 5):
        for dz in range(-4, 5):
            d = max(abs(dx), abs(dz))
            if d == 4:
                b.setze(cx + dx, y, cz + dz, "smooth_quartz")
            elif d <= 3:
                b.setze(cx + dx, y - 1, cz + dz, "quartz_bricks")
                b.setze(cx + dx, y, cz + dz, "water")
    b.setze(cx, y - 1, cz, "sea_lantern")
    b.setze(cx, y, cz, "quartz_pillar")
    b.setze(cx, y + 1, cz, "sea_lantern")
    # Kuppel aus Glas
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            for dy in range(1, 5):
                d2 = dx * dx + dz * dz + (dy * 1.15) ** 2
                if 9.5 <= d2 <= 15.5 and b.hole(cx + dx, y + dy, cz + dz) is None:
                    b.setze(cx + dx, y + dy, cz + dz, "glass")


def aquarium(b: Bau, x0: int, y0: int, z0: int, x1: int, y1: int, z1: int) -> None:
    """Wassergefülltes Glasbecken mit Seegras, Korallen und Tropenfischen."""
    b.quader(x0, y0, z0, x1, y1, z1, "glass")
    b.quader(x0 + 1, y0, z0 + 1, x1 - 1, y1 - 1, z1 - 1, "water")
    b.quader(x0 + 1, y0, z0 + 1, x1 - 1, y0, z1 - 1, "sand")
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            if (x + z) % 3 == 0:
                b.setze(x, y0 + 1, z, zustand("seagrass"))
            elif (x + z) % 5 == 1:
                b.setze(x, y0 + 1, z, zustand("tube_coral", waterlogged="true"))
    b.setze(x0 + 1, y1 - 1, z0 + 1, zustand("sea_pickle", pickles="4", waterlogged="true"))
    mx, mz = (x0 + x1) / 2 + 0.5, (z0 + z1) / 2 + 0.5
    for i, variante in enumerate((117506305, 67764993, 50790401)):
        b.entity(mx + (i - 1) * 0.6, y0 + 2.2, mz, {
            "id": "minecraft:tropical_fish", "Variant": variante, "PersistenceRequired": ("b", 1),
            "Rotation": [0.0, 0.0], "Motion": [0.0, 0.0, 0.0], "Pos": [0.0, 0.0, 0.0],
        })


def truhe(b: Bau, x: int, y: int, z: int, facing: str, inhalt: list[tuple[str, int]]) -> None:
    """Truhe mit Inhalt (Gegenstand, Anzahl) im Format von Minecraft 1.21."""
    items = [{"Slot": ("b", i), "id": name if ":" in name else f"minecraft:{name}", "count": anzahl}
             for i, (name, anzahl) in enumerate(inhalt)]
    b.setze(x, y, z, zustand("chest", facing=facing, type="single", waterlogged="false"),
            {"id": "minecraft:chest", "Items": items})


def fass(b: Bau, x: int, y: int, z: int, facing: str, inhalt: list[tuple[str, int]]) -> None:
    """Fass mit Inhalt (Gegenstand, Anzahl), z. B. als Küchenschrank oder Vorratsbehälter."""
    items = [{"Slot": ("b", i), "id": name if ":" in name else f"minecraft:{name}", "count": anzahl}
             for i, (name, anzahl) in enumerate(inhalt)]
    b.setze(x, y, z, zustand("barrel", facing=facing, open="false"), {"id": "minecraft:barrel", "Items": items})
