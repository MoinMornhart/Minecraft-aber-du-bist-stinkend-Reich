#!/usr/bin/env python3
"""Eigener Entwurf der Villa im Stil einer modernen Hochsicherheits-Villa.

Erzeugt src/main/resources/data/stinkendreich/structure/villa/villa.nbt und
villa.json (Markierungen für die Mod: Spawnpunkt, Aufzug, Etagen, Auto, Butler).

Aufbau der Strukturdatei (112 x 56 x 112):
  y = G (25)          Geländeoberkante (Grasschicht); darüber Haus, Garten, Mauern
  y = 4 bis 18        Untergrundbasis (Bunker) unter dem Haus
  Grundstück          äußerer Zaun, Lava-Kanal unter Glas, Wassergraben, Innenmauer, Tor im Süden

Aufruf:  python3 tools/villa/villa.py [--vorschau]
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

from bau import Bau, zustand
from moebel import (kingsize_bett, reihe, strandliege, aquarium, baum, bett, bilderrahmen, blumenbeet, brunnen_kuppel, deko, doppeltuer, fass, gemaelde,
                    hecke, kronleuchter, laterne, pflanze, sofa, stuhl, tisch, truhe, tuer)

WURZEL = Path(__file__).resolve().parents[2]
ZIEL = WURZEL / "src/main/resources/data/stinkendreich/structure/villa"

# ---------------- Maße ----------------
GROESSE = (112, 56, 112)
G = 25                 # Grasschicht
EG = G                 # Erdgeschoss-Boden
OG = G + 6             # Obergeschoss-Boden
DACH = G + 12          # Dachplatte
PH_DACH = G + 17       # Dach des Penthouse
BUNKER = 5             # Bunker-Boden
UG = 15                # Boden des Untergeschosses (Nachtclub, Foyer, Tiefgarage)
UG_DECKE = 22          # Decke des Untergeschosses

# Haus
HX0, HX1, HZ0, HZ1 = 30, 79, 36, 67
# Aufzugsschacht (außen 4x4, innen 2x2), Tür nach Süden
AX0, AX1, AZ0, AZ1 = 56, 59, 37, 40
# Haltestellen des Aufzugs: Name -> Höhe des Bodens
AUFZUG_HALTE = {"bunker": BUNKER, "keller": UG, "eg": EG, "og": OG, "dach": DACH, "heli": PH_DACH}

# Materialien
WEISS = "white_concrete"
SCHWARZ = "black_concrete"
GRAU = "gray_concrete"
HELLGRAU = "light_gray_concrete"
QUARZ = "smooth_quartz"
GLAS = "glass_pane"
HOLZBODEN = "dark_oak_planks"
LICHT = "sea_lantern"

b = Bau(*GROESSE)


def grundstueck() -> None:
    """Rasen, Zaun, Lava-Kanal, Wassergraben, Innenmauer."""
    n = GROESSE[0] - 1
    # Außenzaun mit Leuchtpfosten
    b.ring(1, 1, n - 1, n - 1, G + 1, QUARZ)
    b.ring(1, 1, n - 1, n - 1, G + 2, "iron_bars")
    for i in range(1, n, 11):
        for x, z in ((i, 1), (i, n - 1), (1, i), (n - 1, i)):
            b.setze(x, G + 1, z, QUARZ)
            b.setze(x, G + 2, z, QUARZ)
            b.setze(x, G + 3, z, LICHT)
    # Lava-Kanal unter begehbarem Glas; Seitenwände, damit nichts in Höhlen läuft
    for y in (G - 2, G - 1):
        b.ring(2, 2, n - 2, n - 2, y, "polished_blackstone")
    for k in (3, 4):
        b.ring(k, k, n - k, n - k, G - 2, "magma_block")
        b.ring(k, k, n - k, n - k, G - 1, "lava")
        b.ring(k, k, n - k, n - k, G, "glass")
    # Wassergraben, 5 breit, 3 tief, mit Seitenwänden und Lichtern am Grund
    for y in range(G - 4, G):
        b.ring(5, 5, n - 5, n - 5, y, "dark_prismarine")
        b.ring(11, 11, n - 11, n - 11, y, "dark_prismarine")
    for k in range(6, 11):
        b.ring(k, k, n - k, n - k, G - 4, "dark_prismarine")
        for y in range(G - 3, G):
            b.ring(k, k, n - k, n - k, y, "water")
        b.ring(k, k, n - k, n - k, G, "air")
    b.ring(5, 5, n - 5, n - 5, G, "polished_andesite")
    b.ring(11, 11, n - 11, n - 11, G, "polished_andesite")
    for i in range(8, n - 8, 7):
        for x, z in ((i, 8), (i, n - 8), (8, i), (n - 8, i)):
            b.setze(x, G - 4, z, LICHT)
    # Innenmauer: weiß mit schwarzem Abschluss und Sicherheitsgitter
    for y in range(G + 1, G + 4):
        b.ring(12, 12, n - 12, n - 12, y, WEISS, breite=2)
    b.ring(12, 12, n - 12, n - 12, G + 4, SCHWARZ, breite=2)
    b.ring(12, 12, n - 12, n - 12, G + 5, "iron_bars")
    for i in range(12, n - 11, 9):
        for x, z in ((i, 12), (i, n - 12), (12, i), (n - 12, i)):
            b.setze(x, G + 5, z, LICHT)


def tor_und_bruecke() -> None:
    """Tor im Süden, Brücke über Graben und Lava, roter Teppich bis zum Haus."""
    n = GROESSE[0] - 1
    x0, x1 = 51, 60
    # Öffnungen in Zaun und Mauer
    b.luft(x0 + 1, G + 1, n - 1, x1 - 1, G + 3, n - 1)
    b.luft(x0 + 1, G + 1, n - 13, x1 - 1, G + 5, n - 12)
    # Torpfeiler mit Lichtern
    for x in (x0, x1):
        b.quader(x, G + 1, n - 13, x, G + 6, n - 12, SCHWARZ)
        b.setze(x, G + 7, n - 13, LICHT)
        b.setze(x, G + 7, n - 12, LICHT)
    b.marke("tor", x0 + 1, G + 1, n - 13, x1 - 1, G + 4, n - 12)
    # Brücke
    b.quader(x0, G, n - 12, x1, G, n - 1, QUARZ)
    for z in range(n - 11, n):
        for x in (x0, x1):
            b.setze(x, G + 1, z, zustand("quartz_slab", type="bottom", waterlogged="false"))
            b.setze(x, G + 2, z, GLAS)
    b.quader(x0 + 3, G, n - 12, x1 - 3, G, n - 1, "red_concrete")
    # Teppich vom Tor bis zur Haustür
    b.quader(x0 + 3, G, 70, x1 - 3, G, n - 14, "red_concrete")
    b.quader(x0 + 1, G, 70, x0 + 2, G, n - 14, QUARZ)
    b.quader(x1 - 2, G, 70, x1 - 1, G, n - 14, QUARZ)
    for z in range(74, n - 14, 6):
        laterne(b, x0, G + 1, z)
        laterne(b, x1, G + 1, z)


def vorplatz() -> None:
    """Zwei Glaskuppel-Brunnen, Beete, Zufahrt zur Garage."""
    brunnen_kuppel(b, 40, G, 84)
    brunnen_kuppel(b, 71, G, 84)
    blumenbeet(b, 36, G + 1, 72, 45, 76)
    blumenbeet(b, 66, G + 1, 72, 75, 76)
    # Zufahrt: vom Tor nach Osten und hinauf zur Garage
    b.quader(61, G, 92, 91, G, 97, "polished_andesite")
    b.quader(82, G, 68, 91, G, 97, "polished_andesite")
    b.quader(61, G, 94, 91, G, 95, "gray_concrete")
    for z in (72, 80, 88):
        laterne(b, 92, G + 1, z)
    # Hecken entlang der Innenmauer
    hecke(b, 14, G + 1, 96, 49, 97, 2)
    hecke(b, 14, G + 1, 14, 15, 95, 2)


def garten() -> None:
    """Hinterhof mit Pool, Holzdeck, Liegen, Grill und Bäumen; Westgarten mit Bäumen."""
    # Holzdeck und Pool
    b.quader(36, G, 15, 74, G, 31, "stripped_dark_oak_wood")
    b.quader(40, G - 3, 18, 69, G - 1, 27, "white_concrete")
    b.quader(41, G - 2, 19, 68, G, 26, "water")
    for x in range(42, 68, 5):
        b.setze(x, G - 3, 22, LICHT)
        b.setze(x, G - 3, 23, LICHT)
    b.ring(40, 18, 69, 27, G, QUARZ)
    # Strandliegen mit Blick auf den Pool, dazwischen Sonnenschirme und Loungetische
    for x in range(42, 68, 5):
        strandliege(b, x, G + 1, 30, "north")
        deko(b, x + 2, G + 1, 30, "loungetisch")
        if x % 10 == 2:
            deko(b, x + 2, G + 1, 29, "sonnenschirm")
    # Grill-Ecke
    b.quader(76, G, 16, 84, G, 24, "polished_andesite")
    b.setze(78, G + 1, 17, zustand("campfire", lit="true", facing="south", signal_fire="false", waterlogged="false"))
    b.setze(77, G + 1, 17, zustand("smoker", facing="south", lit="false"))
    b.setze(79, G + 1, 17, zustand("smooth_quartz_slab", type="top", waterlogged="false"))
    b.marke("grill", 78, G + 1, 17)
    tisch(b, 78, G + 1, 21, 82, 22)
    for x in range(78, 83):
        stuhl(b, x, G + 1, 20, "south")
        stuhl(b, x, G + 1, 23, "north")
    # Bäume und Beete
    for x, z, art in ((20, 20, "oak"), (26, 30, "birch"), (20, 44, "oak"), (22, 60, "birch"), (19, 76, "oak"),
                      (88, 28, "oak"), (92, 40, "birch"), (30, 20, "birch"), (86, 18, "oak"), (28, 88, "birch")):
        baum(b, x, G + 1, z, art, hoehe=6 + (x + z) % 3)
    blumenbeet(b, 16, G + 1, 50, 26, 56)
    blumenbeet(b, 84, G + 1, 30, 94, 36)
    # Pavillon im Westgarten
    for x, z in ((15, 64), (23, 64), (15, 72), (23, 72)):
        b.quader(x, G + 1, z, x, G + 4, z, "quartz_pillar")
    b.quader(14, G + 5, 63, 24, G + 5, 73, zustand("smooth_quartz_slab", type="bottom", waterlogged="false"))
    b.quader(16, G, 65, 22, G, 71, "stripped_dark_oak_wood")
    sofa(b, 17, G + 1, 66, 5, "south")
    tisch(b, 18, G + 1, 69, 20, 69)


def tuerrahmen(x0: int, y0: int, z: int, x1: int, y1: int, gold: int | None = None) -> None:
    """Schwarzer Rahmen um eine Türöffnung in einer Glaswand (Pfosten links/rechts, Sturz oben),
    damit die Tür nicht frei im Glas steht. `gold`: Höhe einer goldenen Sturzleiste."""
    b.quader(x0, y0, z, x0, y1, z, SCHWARZ)
    b.quader(x1, y0, z, x1, y1, z, SCHWARZ)
    oben = y0 + 2 if gold is None else gold
    b.quader(x0 + 1, oben, z, x1 - 1, y1, z, SCHWARZ)
    if gold is not None:
        b.quader(x0, gold, z, x1, gold, z, "gold_block")


def huelle() -> None:
    """Außenwände, Decken, Dachplatten mit weitem Überstand, Glasfassade im Süden."""
    # Fundament und Böden
    b.quader(HX0, EG, HZ0, HX1, EG, HZ1, "polished_deepslate")
    b.quader(HX0, OG, HZ0, HX1, OG, HZ1, WEISS)
    b.quader(HX0, DACH, HZ0, HX1, DACH, HZ1, WEISS)
    # Wände bis zur Dachplatte
    b.waende(HX0, EG + 1, HZ0, HX1, DACH - 1, HZ1, SCHWARZ)
    # Innenraum leer räumen
    b.luft(HX0 + 1, EG + 1, HZ0 + 1, HX1 - 1, OG - 1, HZ1 - 1)
    b.luft(HX0 + 1, OG + 1, HZ0 + 1, HX1 - 1, DACH - 1, HZ1 - 1)
    # Graue Paneele an Ost-, West- und Nordseite
    for y in (EG + 1, EG + 5, OG + 1, OG + 5):
        for x in range(HX0, HX1 + 1):
            b.setze(x, y, HZ0, GRAU)
        for z in range(HZ0, HZ1 + 1):
            b.setze(HX0, y, z, GRAU)
            b.setze(HX1, y, z, GRAU)
    # Fensterbänder an Ost, West und Nord
    for y0 in (EG + 2, OG + 2):
        for z in range(HZ0 + 3, HZ1 - 2):
            if z % 7 not in (0, 1):
                b.quader(HX0, y0, z, HX0, y0 + 2, z, GLAS)
                b.quader(HX1, y0, z, HX1, y0 + 2, z, GLAS)
        for x in range(HX0 + 3, HX1 - 2):
            if x % 7 not in (0, 1) and not (AX0 - 1 <= x <= AX1 + 1):
                b.quader(x, y0, HZ0, x, y0 + 2, HZ0, GLAS)
    # Südfassade: Pfeiler alle 7 Blöcke, dazwischen Glas über die ganze Höhe
    pfeiler = list(range(HX0, HX1 + 1, 7)) + [HX1]
    for x in range(HX0, HX1 + 1):
        for y in list(range(EG + 1, OG)) + list(range(OG + 1, DACH)):
            b.setze(x, y, HZ1, SCHWARZ if x in pfeiler else GLAS)
    # Eingang: Öffnung für die automatische Schiebetür (siehe mechanismen())
    b.luft(54, EG + 1, HZ1, 55, EG + 3, HZ1)
    b.marke("haustuer", 54, EG + 1, HZ1, 55, EG + 3, HZ1)
    tuerrahmen(53, EG + 1, HZ1, 56, OG - 1, gold=EG + 4)
    # Balkon über dem Eingang: Obergeschoss-Platte kragt 3 Blöcke aus
    b.quader(HX0, OG, HZ1 + 1, HX1, OG, HZ1 + 3, WEISS)
    for x in range(HX0, HX1 + 1):
        b.setze(x, OG + 1, HZ1 + 3, GLAS)
    for z in range(HZ1 + 1, HZ1 + 4):
        b.setze(HX0, OG + 1, z, GLAS)
        b.setze(HX1, OG + 1, z, GLAS)
    b.luft(54, OG + 1, HZ1, 55, OG + 2, HZ1)
    tuerrahmen(53, OG + 1, HZ1, 56, OG + 3)
    doppeltuer(b, 54, OG + 1, HZ1, "south", "stinkendreich:glastuer")
    # Dachplatte mit großem Überstand (2 seitlich, 6 nach Süden)
    b.quader(HX0 - 2, DACH, HZ0 - 2, HX1 + 2, DACH, HZ1 + 6, WEISS)
    b.quader(HX0 - 2, DACH - 1, HZ1 + 6, HX1 + 2, DACH - 1, HZ1 + 6, WEISS)
    # Lichtleiste unter dem Überstand
    for x in range(HX0, HX1 + 1, 4):
        b.setze(x, DACH, HZ1 + 4, LICHT)


def treppe_und_aufzug() -> None:
    """Gerade Treppe im Westteil der Halle, gläserner Aufzugsschacht vom Bunker bis zum Penthouse."""
    for i in range(6):
        for x in (50, 51):
            b.setze(x, EG + 1 + i, 50 - i, zustand("dark_oak_stairs", facing="north", half="bottom", shape="straight", waterlogged="false"))
            for y in range(EG + 1, EG + 1 + i):
                b.setze(x, y, 50 - i, "dark_oak_planks")
    b.luft(50, OG, 45, 51, OG, 50)
    for z in range(45, 51):
        b.setze(52, OG + 1, z, GLAS)
    b.setze(50, OG + 1, 51, GLAS)
    b.setze(51, OG + 1, 51, GLAS)
    # Aufzugsschacht: Glaswände, in den Ecken warm leuchtende Lichtsäulen, bis unter das Penthouse-Dach.
    # Darin fährt die gläserne Kabine (Entity, siehe AufzugKabine.java). Oben ragt der Kopf über das Dach.
    for y in range(BUNKER + 1, PH_DACH + 4):
        for x in range(AX0, AX1 + 1):
            for z in range(AZ0, AZ1 + 1):
                rand = x in (AX0, AX1) or z in (AZ0, AZ1)
                ecke = x in (AX0, AX1) and z in (AZ0, AZ1)
                if ecke:
                    b.setze(x, y, z, zustand("ochre_froglight", axis="y"))
                elif rand:
                    b.setze(x, y, z, "glass")
                else:
                    b.setze(x, y, z, "air")
    b.quader(AX0, PH_DACH + 4, AZ0, AX1, PH_DACH + 4, AZ1, WEISS)
    # Haltestellen: Boden im Schacht (die Kabine nimmt ihn beim Durchfahren kurz heraus), Öffnung nach Süden
    for name, y in AUFZUG_HALTE.items():
        b.quader(AX0 + 1, y, AZ0 + 1, AX1 - 1, y, AZ1 - 1, "polished_deepslate")
        b.luft(AX0 + 1, y + 1, AZ1, AX1 - 1, y + 3, AZ1)
        b.setze(AX0 + 1, y + 4, AZ1, "gold_block")
        b.setze(AX1 - 1, y + 4, AZ1, "gold_block")
        b.marke("aufzug", name, AX0 + 2, y + 1, AZ1 + 1)


def erdgeschoss() -> None:
    """Ballsaal (Halle), Wohnzimmer mit Aquarium und Kamin, Luxusküche mit Personalküche, Esszimmer."""
    y = EG + 1
    # --- Ballsaal: Marmor im Schachbrett, roter Teppich mit Goldkante, Säulen, Konzertflügel, Sektturm ---
    for x in range(50, 60):
        for z in range(HZ0 + 1, HZ1):
            b.setze(x, EG, z, QUARZ if (x + z) % 2 == 0 else "polished_blackstone")
    b.quader(54, EG, 61, 55, EG, HZ1 - 1, "red_wool")
    b.quader(53, EG, 61, 53, EG, HZ1 - 1, "gold_block")
    b.quader(56, EG, 61, 56, EG, HZ1 - 1, "gold_block")
    for z in (53, 57, 61):
        for x in (50, 59):
            b.setze(x, y, z, "chiseled_quartz_block")
            b.quader(x, y + 1, z, x, OG - 2, z, zustand("quartz_pillar", axis="y"))
            b.setze(x, OG - 1, z, "gold_block")
    deko(b, 57, y, 63, "fluegel_tasten", "north")
    deko(b, 57, y, 64, "fluegel_korpus", "north")
    deko(b, 57, y, 62, "barhocker")
    b.setze(52, y, 64, "gold_block")
    deko(b, 52, y + 1, 64, "sektturm")
    pflanze(b, 51, y, HZ1 - 1, "potted_bamboo")
    pflanze(b, 58, y, HZ1 - 1, "potted_bamboo")
    # Kronleuchter: über dem Luftraum an Ketten, im Eingang unter der Decke
    for z in (52, 55, 58):
        for x in (54, 55):
            kronleuchter(b, x, DACH - 1, z, kette=3)
    for x in (54, 55):
        kronleuchter(b, x, OG - 1, 64)
    # Trennwand Wohnzimmer | Ballsaal mit Aquarium
    b.quader(49, y, HZ0 + 1, 49, OG - 1, HZ1 - 1, WEISS)
    aquarium(b, 47, y, 52, 49, OG - 1, 63)
    b.luft(49, y, 64, 49, y + 2, 66)

    # --- Wohnzimmer: Kamin mit Gemälde, Fernsehwand mit Goldrahmen, weiße Sofas, großer Teppich ---
    b.quader(HX0 + 1, EG, HZ0 + 1, 48, EG, HZ1 - 1, HOLZBODEN)
    b.quader(33, EG, 43, 39, EG, 57, "yellow_wool")
    b.quader(34, EG, 44, 38, EG, 56, "red_wool")
    b.quader(HX0 + 1, y + 1, 46, HX0 + 1, y + 3, 54, SCHWARZ)          # Fernseher
    b.quader(HX0 + 1, y + 4, 45, HX0 + 1, y + 4, 55, "gold_block")
    b.quader(HX0 + 1, y + 1, 45, HX0 + 1, y + 3, 45, "gold_block")
    b.quader(HX0 + 1, y + 1, 55, HX0 + 1, y + 3, 55, "gold_block")
    b.quader(HX0 + 1, y, 45, HX0 + 1, y, 55, zustand("dark_oak_slab", type="top", waterlogged="false"))
    sofa(b, 39, y, 46, 9, "west", "smooth_quartz_stairs", None)
    sofa(b, 36, y, 44, 4, "south", "smooth_quartz_stairs", None)
    sofa(b, 36, y, 56, 4, "north", "smooth_quartz_stairs", None)
    tisch(b, 35, y, 48, 36, 52, "smooth_quartz_slab")
    deko(b, 35, y + 1, 49, "champagner")
    deko(b, 36, y + 1, 51, "goldbarren", "west")
    # Kamin an der Nordwand, darüber das Gemälde „Villa bei Nacht“
    b.quader(36, y, HZ0 + 1, 42, y + 4, HZ0 + 1, "polished_blackstone_bricks")
    b.luft(38, y, HZ0 + 1, 40, y + 1, HZ0 + 1)
    for x in (38, 39, 40):
        b.setze(x, y, HZ0 + 1, zustand("campfire", lit="true", facing="south", signal_fire="false", waterlogged="false"))
    b.quader(38, y + 2, HZ0 + 1, 40, y + 2, HZ0 + 1, "iron_bars")
    b.quader(36, y + 4, HZ0 + 1, 42, y + 4, HZ0 + 1, "gold_block")
    gemaelde(b, 37, y + 3, HZ0 + 2, "south", "villa_nacht", 4, 2)
    # Bücherwand, Vitrine mit Pokalen
    b.quader(HX0 + 1, y, HZ0 + 1, HX0 + 1, y + 3, 42, "bookshelf")
    for i, z in enumerate((60, 62, 64)):
        b.setze(HX0 + 1, y, z, "polished_blackstone")
        deko(b, HX0 + 1, y + 1, z, "pokal", "east")
    pflanze(b, HX0 + 1, y, HZ1 - 1, "potted_fern")
    pflanze(b, 46, y, HZ0 + 1, "potted_azalea_bush")
    kronleuchter(b, 37, OG - 1, 50)
    kronleuchter(b, 42, OG - 1, 50)

    # --- Luxusküche: Marmor, weiße Fronten mit Gold, Kochinsel mit Barhockern ---
    b.quader(60, EG, HZ0 + 1, HX1 - 1, EG, HZ1 - 1, QUARZ)
    b.quader(60, y, HZ0 + 1, 60, OG - 1, 50, WEISS)
    b.luft(60, y, 44, 60, y + 2, 46)
    gemaelde(b, 59, y + 1, 47, "west", "goldrausch", 4, 3)
    deko(b, 62, y, HZ0 + 1, "kuehlschrank", "south")
    deko(b, 62, y + 1, HZ0 + 1, "kuehlschrank", "south")
    deko(b, 63, y, HZ0 + 1, "weinkuehlschrank", "south")
    deko(b, 63, y + 1, HZ0 + 1, "weinkuehlschrank", "south")
    for x in (64, 65, 67, 69, 70, 71, 72):
        b.setze(x, y, HZ0 + 1, "polished_blackstone")
    deko(b, 66, y, HZ0 + 1, "spuele", "south")
    deko(b, 68, y, HZ0 + 1, "herd", "south")
    deko(b, 68, OG - 2, HZ0 + 1, "dunstabzug", "south")
    deko(b, 70, y + 1, HZ0 + 1, "kaffeemaschine", "south")
    deko(b, 72, y + 1, HZ0 + 1, "cocktail")
    # Kochinsel mit Goldenden, Barhocker, Hängelampen
    b.quader(64, y, 43, 71, y, 44, "polished_blackstone")
    for x in (64, 71):
        b.quader(x, y, 43, x, y, 44, "gold_block")
    deko(b, 66, y + 1, 43, "champagner")
    deko(b, 69, y + 1, 44, "cocktail")
    for x in (65, 67, 69):
        deko(b, x, y, 46, "barhocker")
    for x in (65, 68, 70):
        b.setze(x, OG - 1, 43, zustand("chain", axis="y", waterlogged="false"))
        b.setze(x, OG - 2, 43, zustand("lantern", hanging="true", waterlogged="false"))
    # Personalküche hinter der Küche: hier kocht und schmilzt James (echte Öfen), Vorräte und Brennstoff
    b.quader(73, y, HZ0 + 1, 73, OG - 1, 44, WEISS)
    b.luft(73, y, 42, 73, y + 1, 43)
    tuer(b, 73, y, 42, "east", "stinkendreich:prunktuer", "right")
    b.setze(75, y, HZ0 + 1, zustand("smoker", facing="south", lit="false"))
    b.setze(76, y, HZ0 + 1, zustand("furnace", facing="south", lit="false"))
    b.setze(77, y, HZ0 + 1, zustand("blast_furnace", facing="south", lit="false"))
    for x in (75, 76, 77):
        b.marke("ofen", x, y, HZ0 + 1)
    fass(b, 74, y, HZ0 + 1, "south", [("cooked_beef", 32), ("bread", 32), ("baked_potato", 32), ("golden_carrot", 16),
                                       ("cooked_salmon", 16), ("pumpkin_pie", 8)])
    fass(b, 78, y, HZ0 + 1, "south", [("coal", 64), ("coal", 64), ("charcoal", 32)])
    b.marke("vorrat", 74, y, HZ0 + 1)
    b.marke("brennstoff", 78, y, HZ0 + 1)
    b.setze(78, y, HZ0 + 3, zustand("water_cauldron", level="3"))

    # --- Esszimmer: lange Marmortafel mit Kerzen, zwei Kronleuchter, Bildnis von James ---
    tisch(b, 66, y, 56, 74, 58, "smooth_quartz_slab")
    for x in range(66, 75):
        stuhl(b, x, y, 55, "south", "dark_oak_stairs")
        stuhl(b, x, y, 59, "north", "dark_oak_stairs")
    for x in (67, 70, 73):
        b.setze(x, y + 1, 57, zustand("yellow_candle", candles="3", lit="true", waterlogged="false"))
    kronleuchter(b, 68, OG - 1, 57)
    kronleuchter(b, 72, OG - 1, 57)
    b.quader(65, EG, 54, 75, EG, 60, "red_wool")
    gemaelde(b, HX1 - 1, y + 1, 56, "west", "james", 2, 2)
    deko(b, 61, y, 52, "sektturm")
    # Tür zur Garage
    b.luft(HX1, y, 60, HX1, y + 1, 60)
    b.marke("garagentuer", HX1, y, 60)


def obergeschoss() -> None:
    """Galerie um den Luftraum des Ballsaals, Schlafzimmer mit Himmelbett, Bad mit Goldwanne,
    Schmucktresor, Überwachungsraum und Büro."""
    y = OG + 1
    # Galerie mit Luftraum über dem Ballsaal, Goldkante und Glasgeländer
    b.quader(50, OG, HZ0 + 1, 59, OG, HZ1 - 1, QUARZ)
    b.luft(50, OG, 45, 51, OG, 50)
    b.luft(53, OG, 51, 56, OG, 60)
    for x in range(52, 58):
        for z in (50, 61):
            b.setze(x, OG, z, "gold_block")
            b.setze(x, y, z, GLAS)
    for z in range(51, 61):
        for x in (52, 57):
            b.setze(x, OG, z, "gold_block")
            b.setze(x, y, z, GLAS)
    # Wände der Räume
    b.quader(49, y, HZ0 + 1, 49, DACH - 1, HZ1 - 1, WEISS)
    b.quader(60, y, HZ0 + 1, 60, DACH - 1, HZ1 - 1, WEISS)
    b.luft(49, y, 58, 49, y + 1, 59)
    b.luft(60, y, 58, 60, y + 1, 59)
    gemaelde(b, 50, y, 54, "east", "der_bonze", 2, 3)
    gemaelde(b, 59, y + 1, 52, "west", "yacht", 4, 2)

    # Schlafzimmer: Himmelbett mit Goldpfosten, Kronleuchter, Gemälde über dem Kopfende
    b.quader(HX0 + 1, OG, 50, 48, OG, HZ1 - 1, HOLZBODEN)
    b.quader(HX0 + 1, y, 49, 48, DACH - 1, 49, WEISS)
    b.luft(44, y, 49, 45, y + 1, 49)
    kingsize_bett(b, HX0 + 1, y, 58, 2, "west")
    for x, z in ((HX0 + 1, 56), (HX0 + 1, 59), (HX0 + 3, 56), (HX0 + 3, 59)):
        b.quader(x, y, z, x, y + 2, z, "gold_block")
    b.quader(HX0 + 1, y + 3, 56, HX0 + 3, y + 3, 59, "red_wool")
    gemaelde(b, HX0 + 1, y + 4, 57, "east", "goldbarren", 2, 2)
    b.quader(34, OG, 54, 38, OG, 61, "white_wool")
    sofa(b, 44, y, 62, 3, "west", "smooth_quartz_stairs")
    kronleuchter(b, 40, DACH - 1, 57)
    deko(b, 47, y, 51, "champagner")

    # Bad: Marmor, goldene Badewanne, Regendusche, goldene Toilette, Doppelwaschbecken
    b.quader(HX0 + 1, OG, HZ0 + 1, 48, OG, 48, QUARZ)
    # Badewanne auf dem Boden des Obergeschosses (nicht in der Bodenplatte, sonst läuft sie ins Wohnzimmer)
    b.quader(HX0 + 2, y, HZ0 + 2, HX0 + 6, y, HZ0 + 4, "gold_block")
    b.quader(HX0 + 3, y, HZ0 + 3, HX0 + 5, y, HZ0 + 3, "water")
    b.quader(HX0 + 12, y, HZ0 + 1, HX0 + 14, y + 3, HZ0 + 3, "glass")
    b.luft(HX0 + 13, y, HZ0 + 2, HX0 + 13, y + 2, HZ0 + 2)
    b.setze(HX0 + 13, y + 3, HZ0 + 2, zustand("water", level="0"))
    b.gewollter_fluss.add((HX0 + 13, y + 3, HZ0 + 2))   # Regendusche im Glaskasten
    deko(b, HX0 + 8, y, HZ0 + 1, "spuele", "south")
    deko(b, HX0 + 9, y, HZ0 + 1, "spuele", "south")
    deko(b, HX0 + 17, y, HZ0 + 1, "goldtoilette", "south")
    for z in range(41, 48):
        truhe(b, 48, y, z, "west", [])
    kronleuchter(b, 39, DACH - 1, 42)

    # Schmucktresor (Nordosten): außen weiß wie die anderen Räume, innen Gold; runde Tresortür zum Flur
    b.quader(61, OG, HZ0 + 1, 67, DACH - 1, 47, WEISS)
    b.luft(62, y, HZ0 + 2, 66, DACH - 2, 46)
    b.quader(62, OG, HZ0 + 2, 66, OG, 46, "gold_block")
    b.luft(63, y, 47, 64, y + 1, 47)
    b.marke("tresortuer", 63, y, 47, 64, y + 1, 47)
    b.quader(62, y + 2, 47, 65, y + 2, 47, "gold_block")
    b.quader(65, y, 47, 65, y + 1, 47, "gold_block")
    for z in range(HZ0 + 2, 45, 2):
        truhe(b, 66, y, z, "west", [("diamond", 16), ("emerald", 32), ("gold_ingot", 64), ("golden_apple", 4)])
    b.quader(62, y, HZ0 + 2, 65, y, HZ0 + 2, "diamond_block")
    b.quader(65, y, 45, 66, y, 46, "emerald_block")
    deko(b, 64, y, 39, "goldbarren", "south")
    deko(b, 65, y, 41, "geldstapel", "west")
    deko(b, 62, y, 45, "goldbarren", "east")
    for z, ding in ((40, "diamond"), (42, "emerald"), (44, "nether_star")):
        bilderrahmen(b, 62, y + 2, z, "east", ding)
    kronleuchter(b, 64, DACH - 2, 42)

    # Überwachungsraum (Nordost hinten)
    b.quader(68, OG, HZ0 + 1, HX1 - 1, OG, 50, "gray_concrete")
    b.quader(68, y, 51, HX1 - 1, DACH - 1, 51, WEISS)
    b.luft(70, y, 51, 71, y + 1, 51)
    b.quader(69, y + 1, HZ0 + 1, HX1 - 1, y + 3, HZ0 + 1, SCHWARZ)        # Monitorwand
    for x in range(69, HX1, 2):
        b.marke("monitor", x, y + 2, HZ0 + 1)
    tisch(b, 69, y, 40, HX1 - 1, 40, "polished_blackstone_slab")
    for x in (71, 74, 77):
        stuhl(b, x, y, 42, "north", "polished_blackstone_stairs")

    # Büro mit Sicherheitszentrale (Südosten): Chefschreibtisch mit drei Überwachungsmonitoren
    # (Rechtsklick zeigt alle Kameras der Villa), Geld, Pokal, Gemälde
    b.quader(61, OG, 52, HX1 - 1, OG, HZ1 - 1, HOLZBODEN)
    b.quader(66, y, 60, 72, y, 61, "polished_blackstone")
    b.quader(66, y, 60, 66, y, 61, "gold_block")
    b.quader(72, y, 60, 72, y, 61, "gold_block")
    # Drei flache Monitore, die äußeren schräg zum Platz; davor Tastatur und Maus
    for x, winkel in ((68, "rechts"), (69, "gerade"), (70, "links")):
        mech(x, y + 1, 61, "monitor", "north", {"winkel": winkel})
    deko(b, 69, y + 1, 60, "tastatur", "north")
    stuhl(b, 69, y, 59, "south", "polished_blackstone_stairs")
    deko(b, 67, y + 1, 60, "geldstapel", "north")
    deko(b, 71, y + 1, 60, "pokal")
    b.quader(65, OG, 57, 73, OG, 59, "red_wool")
    b.quader(HX1 - 1, y, 53, HX1 - 1, y + 3, 64, "bookshelf")
    gemaelde(b, 72, y + 1, 52, "south", "privatjet", 3, 2)
    kronleuchter(b, 69, DACH - 1, 58)
    b.marke("sicherheitszentrale", 61, y, 54)


def penthouse() -> None:
    """Privates Penthouse auf dem Dach (Schlafbereich): XXL-Kingsize-Bett, XXL-Fernseher, Hausbar, Lounge;
    draußen Dachgarten, Whirlpool und Glasgeländer."""
    x0, x1, z0, z1 = 42, 67, 37, 58
    y = DACH + 1
    b.waende(x0, y, z0, x1, PH_DACH - 1, z1, SCHWARZ)
    for x in range(x0 + 1, x1):
        for yy in range(y, PH_DACH):
            b.setze(x, yy, z1, GLAS)
    for z in range(z0 + 2, z1):
        for yy in range(y + 1, PH_DACH - 1):
            b.setze(x0, yy, z, GLAS)
            b.setze(x1, yy, z, GLAS)
    b.luft(x0 + 1, y, z0 + 1, x1 - 1, PH_DACH - 1, z1 - 1)
    b.quader(x0 + 1, DACH, z0 + 1, x1 - 1, DACH, z1 - 1, HOLZBODEN)
    b.quader(x0 - 2, PH_DACH, z0 - 2, x1 + 2, PH_DACH, z1 + 5, WEISS)
    for x in range(x0, x1 + 1, 4):
        b.setze(x, PH_DACH, z1 + 3, LICHT)
    b.luft(54, y, z1, 55, y + 1, z1)
    tuerrahmen(53, y, z1, 56, y + 2)
    doppeltuer(b, 54, y, z1, "south", "stinkendreich:glastuer")
    # Hausbar an der Nordwand: Theke mit Goldkante, Flaschenregale, Barhocker
    b.quader(x0 + 2, y, z0 + 3, x0 + 10, y, z0 + 3, "polished_blackstone")
    b.quader(x0 + 2, y, z0 + 4, x0 + 10, y, z0 + 4, "gold_block")
    for x in range(x0 + 2, x0 + 11):
        deko(b, x, y, z0 + 1, "flaschenregal", "south")
        deko(b, x, y + 1, z0 + 1, "flaschenregal", "south")
    for x in range(x0 + 3, x0 + 11, 2):
        deko(b, x, y, z0 + 5, "barhocker")
    deko(b, x0 + 4, y + 1, z0 + 3, "cocktail")
    deko(b, x0 + 8, y + 1, z0 + 3, "champagner")
    # XXL-Kingsize-Bett im Nordosten: ein Bett aus einem Stück (4 × 3 Blöcke, Satindecke, weißes Fell), Samt-
    # Kopfteil mit Goldrahmen in der Wand, Nachttische mit Lampen, Bettbank, lila Leuchtband im Boden
    kingsize_bett(b, 61, y, z0 + 1, 4, "north")
    for x in range(61, 65):
        b.quader(x, y, z0, x, y + 2, z0, zustand("stinkendreich:kopfteil", facing="south", waterlogged="false"))
    b.quader(60, y, z0, 60, y + 3, z0, "gold_block")
    b.quader(65, y, z0, 65, y + 3, z0, "gold_block")
    b.quader(61, y + 3, z0, 64, y + 3, z0, "gold_block")
    for x in (60, 65):
        deko(b, x, y, z0 + 1, "nachttisch", "south")
        deko(b, x, y + 1, z0 + 1, "tischlampe", "south")
    reihe(b, 64, y, z0 + 4, 4, "bettbank", "south")
    b.quader(60, DACH, z0 + 5, 65, DACH, z0 + 5, "crying_obsidian")
    b.quader(61, y, z0 + 6, 64, y, z0 + 6, "white_carpet")
    kronleuchter(b, 62, PH_DACH - 1, z0 + 2)
    kronleuchter(b, 63, PH_DACH - 1, z0 + 2)
    # XXL-Fernseher an einer frei stehenden Wand gegenüber dem Bett (zeigt YouTube-Vorschau, siehe
    # FernseherEntity); die Wand trennt den Schlafbereich von der Lounge
    b.quader(59, y, 47, 66, PH_DACH - 1, 47, SCHWARZ)
    b.quader(59, y, 47, 66, y, 47, "gold_block")
    b.marke("fernseher", 62.5, y + 0.4, 46.85, 180.0)
    # Kinosofa für zwei (roter Samt, Zierkissen) mit Popcorn-Tisch, genau vor dem Fernseher
    reihe(b, 63, y, 44, 2, "sofa_samt", "south")
    deko(b, 64, y, 44, "kinotisch", "south")
    # Lounge mit Blick über die Stadt: weißes Fell, Sofas, Sektturm
    b.quader(50, DACH, 44, 57, DACH, 50, "white_wool")
    sofa(b, x0 + 6, y, z1 - 4, 8, "south", "smooth_quartz_stairs")
    tisch(b, x0 + 7, y, z1 - 2, x0 + 12, z1 - 2, "smooth_quartz_slab")
    deko(b, x0 + 8, y + 1, z1 - 2, "sektturm")
    deko(b, x0 + 11, y + 1, z1 - 2, "champagner")
    sofa(b, x1 - 2, y, 52, 4, "west", "smooth_quartz_stairs", None)
    deko(b, x1 - 4, y, 53, "goldbarren", "east")
    kronleuchter(b, 53, PH_DACH - 1, 47)
    # Glasgeländer um die Dachterrasse
    rx0, rx1, rz0, rz1 = HX0 - 2, HX1 + 2, HZ0 - 2, HZ1 + 6
    b.ring(rx0, rz0, rx1, rz1, y, GLAS)
    # Dachgarten-Beete und Whirlpool
    for (bx0, bz0, bx1, bz1) in ((HX0, HZ0, HX0 + 8, HZ0 + 6), (HX1 - 8, HZ1 - 2, HX1, HZ1 + 4), (HX0, HZ1 - 2, HX0 + 8, HZ1 + 4)):
        b.quader(bx0, y, bz0, bx1, y, bz1, "grass_block")
        b.ring(bx0, bz0, bx1, bz1, y, "polished_andesite")
        for x in range(bx0 + 1, bx1):
            for z in range(bz0 + 1, bz1):
                if (x + z) % 4 == 0:
                    b.setze(x, y + 1, z, zustand("azalea_leaves", persistent="true", distance="1", waterlogged="false"))
                elif (x + z) % 4 == 2:
                    b.setze(x, y + 1, z, ("poppy", "allium", "oxeye_daisy")[(x * z) % 3])
    b.quader(HX1 - 9, y, HZ0, HX1 - 1, y, HZ0 + 7, QUARZ)
    b.quader(HX1 - 8, y, HZ0 + 1, HX1 - 2, y, HZ0 + 6, "water")
    b.setze(HX1 - 5, DACH, HZ0 + 3, LICHT)
    b.marke("whirlpool", HX1 - 5, y, HZ0 + 3)
    deko(b, HX1 - 10, y, HZ0 + 2, "champagner")
    deko(b, HX1 - 10, y, HZ0 + 4, "cocktail")


def dachparty() -> None:
    """Party auf der Dachterrasse südlich vom Penthouse: Tanzfläche unter Lichterketten, DJ, Feuerstelle
    mit Lounge, Sonnenliegen am Whirlpool."""
    y = DACH + 1
    # Tanzfläche unter freiem Himmel, Lichterketten an vier Pfosten
    for x in range(50, 59):
        for z in range(64, 71):
            deko(b, x, DACH, z, "tanzflaeche")
    for x, z in ((48, 63), (60, 63), (48, 72), (60, 72)):
        b.quader(x, y, z, x, y + 3, z, "polished_blackstone_wall")
        b.setze(x, y + 4, z, LICHT)
    for z in (63, 72):
        b.quader(49, y + 3, z, 59, y + 3, z, zustand("chain", axis="x", waterlogged="false"))
        for x in range(50, 59, 2):
            b.setze(x, y + 2, z, zustand("lantern", hanging="true", waterlogged="false"))
    # DJ-Pult mit Lautsprechern am Südrand, Blick zur Tanzfläche
    b.setze(54, y, 72, zustand("stinkendreich:musikanlage", facing="north"))
    b.marke("dj_dach", 54, y, 72)
    for x in (52, 56):
        b.setze(x, y, 72, zustand("stinkendreich:lautsprecher", facing="north"))
        b.setze(x, y + 1, 72, zustand("stinkendreich:lautsprecher", facing="north"))
    # Feuerstelle mit Lounge (Westen)
    b.quader(37, DACH, 66, 41, DACH, 70, "polished_blackstone")
    b.setze(39, y, 68, zustand("campfire", lit="true", facing="north", signal_fire="false", waterlogged="false"))
    for (x, z, blick) in ((39, 65, "south"), (39, 71, "north")):
        sofa(b, x - 1, y, z, 3, blick, "smooth_quartz_stairs", None)
    deko(b, 42, y, 68, "champagner")
    # Strandliegen auf der Ostseite mit Sonnenschirmen und Loungetischen
    for z in range(45, 58, 3):
        strandliege(b, HX1 - 2, y, z, "west")
        deko(b, HX1 - 2, y, z + 1, "sonnenschirm" if (z - 45) % 6 == 0 else "loungetisch")


def hubschrauberlandeplatz() -> None:
    """Landeplatz auf dem Penthouse-Dach: dunkle Fläche mit gelbem Kreis und weißem H, Randlichter,
    Windsack, Glasgeländer. Der Aufzug fährt bis hier hoch."""
    y = PH_DACH + 1
    cx, cz = 55, 50
    b.quader(cx - 6, PH_DACH, cz - 6, cx + 6, PH_DACH, cz + 6, "gray_concrete")
    for dx in range(-6, 7):
        for dz in range(-6, 7):
            r = (dx * dx + dz * dz) ** 0.5
            if 4.6 <= r <= 5.6:
                b.setze(cx + dx, PH_DACH, cz + dz, "yellow_concrete")
    for dz in range(-3, 4):
        b.setze(cx - 2, PH_DACH, cz + dz, "white_concrete")
        b.setze(cx + 2, PH_DACH, cz + dz, "white_concrete")
    for dx in (-1, 0, 1):
        b.setze(cx + dx, PH_DACH, cz, "white_concrete")
    for d in range(-6, 7, 3):
        for (x, z) in ((cx + d, cz - 6), (cx + d, cz + 6), (cx - 6, cz + d), (cx + 6, cz + d)):
            b.setze(x, PH_DACH, z, zustand("verdant_froglight", axis="y"))
    b.marke("hubschrauber", cx + 0.5, y, cz + 0.5, 180.0)
    # Glasgeländer am Dachrand, Windsack an der Südostecke
    b.ring(40, 35, 69, 63, y, GLAS)
    b.quader(67, y, 61, 67, y + 4, 61, "polished_blackstone_wall")
    for i, farbe in enumerate(("orange_wool", "white_wool", "orange_wool")):
        b.setze(66 - i, y + 4, 61, farbe)


def poolparty() -> None:
    """Poolparty im Garten: Poolbar mit Barhockern, DJ, Flamingos, Wasserrutsche, bunte Lichter, Palmen."""
    y = G + 1
    # Poolbar am Westende
    b.quader(35, G, 17, 39, G, 28, "stripped_dark_oak_wood")
    b.quader(37, y, 19, 37, y, 26, "polished_blackstone")
    b.quader(36, y, 19, 36, y, 26, "gold_block")
    for z in range(19, 27):
        deko(b, 35, y, z, "flaschenregal", "east")
    for z in range(20, 27, 2):
        deko(b, 38, y, z, "barhocker")
    for z, ding in ((20, "cocktail"), (23, "champagner"), (25, "cocktail")):
        deko(b, 37, y + 1, z, ding)
    b.quader(35, y + 3, 18, 39, y + 3, 27, "white_wool")
    for (x, z) in ((35, 18), (39, 18), (35, 27), (39, 27)):
        b.quader(x, y, z, x, y + 2, z, "dark_oak_fence")
    deko(b, 37, y + 2, 18, "neon_bar", "south")
    # Flamingos im Pool
    for x, z, blick in ((47, 21, "east"), (58, 24, "west"), (63, 20, "north")):
        deko(b, x, G, z, "flamingo", blick, nass=True)
    # DJ am Nordrand des Pools
    b.setze(54, y, 16, zustand("stinkendreich:musikanlage", facing="south"))
    b.marke("dj_pool", 54, y, 16)
    for x in (51, 57):
        b.setze(x, y, 16, zustand("stinkendreich:lautsprecher", facing="south"))
        b.setze(x, y + 1, 16, zustand("stinkendreich:lautsprecher", facing="south"))
    # Wasserrutsche: Turm am Ostende, Rinne aus Glas mit fließendem Wasser hinab in den Pool
    b.quader(70, G, 19, 73, G + 6, 22, "smooth_quartz")
    b.luft(71, G + 1, 20, 72, G + 5, 21)
    for yy in range(G + 1, G + 7):
        b.setze(72, yy, 20, zustand("ladder", facing="west", waterlogged="false"))
    b.luft(71, G + 1, 19, 71, G + 2, 19)
    b.quader(70, G + 7, 19, 73, G + 7, 22, "smooth_quartz_slab")
    for i in range(5):
        x, yy = 69 - i, G + 6 - i
        b.setze(x, yy - 1, 21, "light_blue_stained_glass")
        b.setze(x, yy, 20, "glass")
        b.setze(x, yy, 22, "glass")
        b.setze(x, yy, 21, zustand("water", level="0") if i == 0 else zustand("water", level=str(min(7, i))))
        b.gewollter_fluss.add((x, yy, 21))
    # Bunte Lichter an Pfosten rund um den Pool
    for x in range(40, 70, 6):
        for z in (17, 28):
            b.quader(x, y, z, x, y + 1, z, "polished_blackstone_wall")
            b.setze(x, y + 2, z, ("pearlescent_froglight", "verdant_froglight", "ochre_froglight")[(x // 6) % 3])
    # Palmen (Dschungelbäume) an den Ecken
    for x, z in ((33, 14), (75, 30)):
        baum(b, x, y, z, "jungle", hoehe=7)


def garage() -> None:
    """Garage östlich des Hauses: Einfahrt zur Rampe in die Tiefgarage, Werkstatt für James, Werkzeugwand."""
    x0, x1, z0, z1 = 80, 94, 46, 67
    b.quader(x0, EG, z0, x1, EG, z1, "polished_andesite")
    b.waende(x0, EG + 1, z0, x1, OG - 1, z1, GRAU)
    b.luft(x0 + 1, EG + 1, z0 + 1, x1 - 1, OG - 1, z1 - 1)
    b.quader(x0 - 1, OG, z0 - 1, x1 + 2, OG, z1 + 3, WEISS)
    for x in range(x0 + 2, x1 - 1, 3):
        b.setze(x, OG, z1 + 1, LICHT)
        b.setze(x, OG, 56, LICHT)
    b.luft(x0 + 2, EG + 1, z1, x1 - 2, EG + 4, z1)
    b.marke("garagentor", x0 + 2, EG + 1, z1, x1 - 2, EG + 4, z1)
    # Werkstatt an der Westwand: Werkbank, Amboss, Schmiedetisch, zwei Hochöfen und ein Kohlenfass für James
    y = EG + 1
    b.setze(x0 + 1, y, z0 + 1, "crafting_table")
    b.setze(x0 + 1, y, z0 + 2, zustand("anvil", facing="north"))
    b.setze(x0 + 1, y, z0 + 3, "smithing_table")
    for z in (z0 + 4, z0 + 5):
        b.setze(x0 + 1, y, z, zustand("blast_furnace", facing="east", lit="false"))
        b.marke("ofen", x0 + 1, y, z)
    fass(b, x0 + 1, y, z0 + 6, "east", [("coal", 64), ("coal", 64)])
    b.marke("brennstoff", x0 + 1, y, z0 + 6)
    # Werkzeugwand: Lochwand aus Fichtenholz, Werkzeuge in Rahmen, Licht darüber
    b.quader(x0 + 2, y, z0, x0 + 8, y + 3, z0, "stripped_spruce_wood")
    for i, ding in enumerate(("diamond_pickaxe", "diamond_axe", "diamond_shovel", "diamond_hoe", "shears",
                              "flint_and_steel", "diamond_sword")):
        bilderrahmen(b, x0 + 2 + i, y + 1 + i % 2, z0 + 1, "south", ding, unsichtbar=True)
    b.quader(x0 + 2, y + 3, z0 + 1, x0 + 8, y + 3, z0 + 1, zustand("spruce_trapdoor", facing="south", half="top",
                                                                    open="false", powered="false", waterlogged="false"))
    gemaelde(b, x0 + 1, y + 1, 55, "east", "bonzenauto", 3, 2)
    # Rampe in die Tiefgarage (Ostseite): Absturzsicherung am Rand der Grube
    for z in range(59, z1 - 1):
        b.setze(89, y, z, GLAS)
    for x in range(90, 94):
        b.setze(x, y, 58, GLAS)
    b.setze(89, y, 58, GLAS)


def keller() -> None:
    """Untergeschoss zwischen Bunker und Erdgeschoss: Nachtclub (Westen) mit Treppe aus dem Garten,
    Foyer am Aufzug mit Weinlounge, Tiefgarage (Osten) mit Rampe aus der Garage und Drehteller."""
    y = UG + 1
    oben = UG_DECKE - 1
    # Hülle: fest von der Bunkerdecke bis unter das Erdgeschoss (sonst offene Höhlen und Erdreich)
    b.quader(29, UG - 1, 31, 97, EG - 1, 68, "polished_deepslate")

    # --- Nachtclub ---
    b.luft(31, y, 37, 53, oben, 65)
    b.quader(31, UG, 37, 53, UG, 65, "polished_blackstone")
    b.quader(31, UG_DECKE, 37, 53, UG_DECKE, 65, SCHWARZ)
    for x in (30, 54):
        b.quader(x, y, 36, x, oben, 66, SCHWARZ)
    for z in (36, 66):
        b.quader(30, y, z, 54, oben, z, SCHWARZ)
    # Leuchtband aus weinendem Obsidian (lila, leuchtet) rund um den Raum
    for x in (30, 54):
        b.quader(x, y + 3, 37, x, y + 3, 65, "crying_obsidian")
    for z in (36, 66):
        b.quader(31, y + 3, z, 53, y + 3, z, "crying_obsidian")
    # Deckenlichter in drei Farben
    lichter_farben = ("pearlescent_froglight", "verdant_froglight", "ochre_froglight")
    for i, x in enumerate(range(33, 53, 5)):
        for j, z in enumerate(range(39, 65, 5)):
            b.setze(x, UG_DECKE, z, zustand(lichter_farben[(i + j) % 3], axis="y"))
    # Unsichtbare Lichtquellen in Kopfhöhe: der Club bleibt dunkel-edel, aber man sieht genug
    for x in range(33, 53, 5):
        for z in range(40, 65, 6):
            if b.hole(x, y + 3, z) == "minecraft:air":
                b.setze(x, y + 3, z, zustand("light", level="10", waterlogged="false"))
    # Tanzfläche (leuchtet und wechselt die Farben), darüber Discokugeln
    for x in range(37, 46):
        for z in range(45, 54):
            deko(b, x, UG, z, "tanzflaeche")
    for x, z in ((39, 47), (43, 51), (41, 49), (39, 52), (44, 46)):
        deko(b, x, oben, z, "discokugel")
    # DJ-Pult auf dem Podest im Norden, Leuchtschrift CLUB
    b.quader(39, y, 37, 45, y, 39, "polished_blackstone_bricks")
    b.quader(40, y + 1, 39, 41, y + 1, 39, "polished_blackstone")
    b.quader(43, y + 1, 39, 44, y + 1, 39, "polished_blackstone")
    b.setze(42, y + 1, 38, zustand("stinkendreich:musikanlage", facing="south"))
    b.marke("dj_club", 42, y + 1, 38)
    for x in (39, 45):
        for dy in (1, 2, 3):
            b.setze(x, y + dy, 37, zustand("stinkendreich:lautsprecher", facing="south"))
    deko(b, 42, y + 4, 37, "neon_club", "south")
    # Bar an der Südwand: Theke, Flaschenregale, Barhocker, Leuchtschrift BAR
    b.quader(33, y, 62, 47, y, 62, "polished_blackstone")
    b.quader(33, y, 61, 47, y, 61, "gold_block")
    for x in range(34, 47):
        deko(b, x, y, 65, "flaschenregal", "north")
        deko(b, x, y + 1, 65, "flaschenregal", "north")
    for x in range(34, 47, 2):
        deko(b, x, y, 60, "barhocker")
    for x, ding in ((35, "cocktail"), (38, "champagner"), (41, "cocktail"), (44, "champagner"), (46, "cocktail")):
        deko(b, x, y + 1, 62, ding)
    deko(b, 40, y + 3, 65, "neon_bar", "north")
    # VIP-Lounge im Osten: Podest, weiße Sofas, Sektturm, Geld auf dem Tisch, Leuchtschrift VIP
    b.quader(48, y, 50, 53, y, 59, "polished_blackstone_bricks")
    b.quader(48, y + 1, 50, 48, y + 1, 54, zustand("chain", axis="z", waterlogged="false"))
    b.quader(48, y + 1, 56, 48, y + 1, 59, zustand("chain", axis="z", waterlogged="false"))
    sofa(b, 53, y + 1, 52, 6, "west", "smooth_quartz_stairs", None)
    tisch(b, 51, y + 1, 53, 51, 56, "smooth_quartz_slab")
    deko(b, 51, y + 2, 53, "sektturm")
    deko(b, 51, y + 2, 55, "geldstapel", "west")
    deko(b, 53, y + 3, 55, "neon_vip", "west")
    # Sitzecken im Westen
    sofa(b, 31, y, 39, 5, "east", farbe="schwarz")
    tisch(b, 33, y, 40, 33, 42, "smooth_quartz_slab")
    deko(b, 33, y + 1, 41, "champagner")
    sofa(b, 31, y, 53, 5, "east", farbe="schwarz")
    tisch(b, 33, y, 54, 33, 56, "smooth_quartz_slab")
    deko(b, 33, y + 1, 55, "cocktail")
    # Treppe aus dem Westgarten (offener Abgang mit Glasgeländer, dann Tunnel) bis zur Clubtür
    b.luft(30, y, 47, 30, y + 2, 48)
    b.quader(19, UG - 1, 46, 30, EG - 1, 49, "polished_deepslate")
    for i in range(10):
        x = 19 + i
        for z in (47, 48):
            b.setze(x, G - i, z, zustand("polished_blackstone_stairs", facing="west", half="bottom", shape="straight",
                                         waterlogged="false"))
            # Vorne offen bis in den Himmel, ab x = 24 unter der Tunneldecke
            b.luft(x, G - i + 1, z, x, G + 3 if x < 24 else G - 1, z)
    b.luft(29, y, 47, 29, y + 2, 48)
    for x in range(19, 24):
        b.setze(x, G + 1, 46, GLAS)
        b.setze(x, G + 1, 49, GLAS)
    b.setze(18, G + 1, 46, GLAS)
    b.setze(18, G + 1, 49, GLAS)
    for z in (46, 49):
        b.quader(24, G, z, 24, G + 3, z, SCHWARZ)
    b.quader(24, G + 3, 46, 24, G + 3, 49, SCHWARZ)
    deko(b, 24, G + 4, 47, "neon_club", "west")
    deko(b, 24, G + 4, 48, "neon_vip", "west")
    b.quader(24, G, 47, 29, G, 48, "polished_deepslate")
    b.marke("club_eingang", 18.5, G + 1, 48.0, 90.0)

    # --- Foyer am Aufzug und Weinlounge ---
    b.luft(55, y, 41, 60, oben, 48)
    b.quader(55, UG, 41, 60, UG, 48, QUARZ)
    b.luft(54, y, 44, 54, y + 2, 45)
    b.luft(61, y, 44, 61, y + 2, 45)
    b.quader(54, y + 3, 44, 54, y + 3, 45, "gold_block")
    b.quader(61, y + 3, 44, 61, y + 3, 45, "gold_block")
    for x in range(55, 61, 2):
        b.setze(x, UG_DECKE, 44, LICHT)
    b.luft(55, y, 50, 60, oben, 65)
    b.luft(57, y, 49, 58, y + 2, 49)
    b.quader(55, UG, 49, 60, UG, 65, HOLZBODEN)
    for z in range(51, 65):
        deko(b, 60, y, z, "weinkuehlschrank", "west")
        deko(b, 60, y + 1, z, "weinkuehlschrank", "west")
    sofa(b, 55, y, 54, 6, "east", farbe="samt")
    tisch(b, 57, y, 55, 57, 58, "dark_oak_slab")
    deko(b, 57, y + 1, 56, "champagner")
    kronleuchter(b, 57, oben, 57)
    kronleuchter(b, 57, oben, 45)

    # --- Tiefgarage ---
    b.luft(62, y, 32, 96, oben, 66)
    b.quader(62, UG, 32, 96, UG, 66, "gray_concrete")
    b.quader(62, UG_DECKE, 32, 96, UG_DECKE, 66, WEISS)
    for x in range(64, 96, 4):
        for z in range(34, 66, 4):
            b.setze(x, UG_DECKE, z, LICHT)
    for x in (61, 97):
        b.quader(x, y, 32, x, oben, 66, WEISS)
        b.quader(x, y + 2, 32, x, y + 2, 66, SCHWARZ)
    for z in (31, 67):
        b.quader(62, y, z, 96, oben, z, WEISS)
        b.quader(62, y + 2, z, 96, y + 2, z, SCHWARZ)
    b.luft(61, y, 44, 61, y + 2, 45)
    # Stellplätze mit weißen Linien: Reihe im Süden (Blick nach Norden) und im Norden (Blick nach Süden)
    for x in (62, 67, 72, 77, 82, 87):
        b.quader(x, UG, 59, x, UG, 66, "white_concrete")
        b.quader(x, UG, 32, x, UG, 39, "white_concrete")
    plaetze = [(64.5 + i * 5, 63.0, 180.0) for i in range(5)] + [(64.5 + i * 5, 35.5, 0.0) for i in range(5)]
    for i, (px, pz, blick) in enumerate(plaetze):
        b.marke("stellplatz", px, y, pz, blick, i)
    b.marke("auto", plaetze[0][0], y, plaetze[0][1], plaetze[0][2])
    # Rampe aus der Garage: x 90–93, von z 65 (Garagenboden) bis z 46 (Tiefgarage), halbe Stufen
    for x in (89, 94):
        b.quader(x, y, 47, x, EG - 1, 66, WEISS)
    b.quader(95, y, 47, 96, oben, 66, "polished_deepslate")
    for z in range(46, 66):
        h = 16 + (z - 46) * 0.5                       # Höhe der Fahrbahn
        voll = int(h) - 1 if h == int(h) else int(h) - 1
        b.quader(90, UG, z, 93, voll, z, "polished_andesite")
        if h != int(h):
            b.quader(90, int(h), z, 93, int(h), z, zustand("smooth_stone_slab", type="bottom", waterlogged="false"))
        frei_ab = int(h) + (1 if h != int(h) else 0)
        b.luft(90, frei_ab, z, 93, EG - 1 if z < 59 else OG - 1, z)
        if z >= 59:
            b.quader(90, EG, z, 93, EG, z, "air") if h < EG else None
    b.marke("rampe_oben", 91.5, EG + 1, 66.5)
    # Drehteller am Fuß der Rampe: goldener Ring im Boden, Mitte für den Drehteller (Entity)
    for dx in range(-4, 5):
        for dz in range(-4, 5):
            r = (dx * dx + dz * dz) ** 0.5
            if 3.3 <= r <= 4.3:
                b.setze(91 + dx, UG, 40 + dz, "gold_block")
            elif r < 3.3:
                b.setze(91 + dx, UG, 40 + dz, "polished_andesite")
    b.marke("drehteller", 91.5, y, 40.5)


def bunker() -> None:
    """Untergrundbasis: Kommandozentrale, Panikraum, Waffenkammer, Haupttresor, Fluchttunnel."""
    x0, x1, z0, z1 = 36, 73, 30, 69
    y0, y1 = BUNKER, BUNKER + 7
    b.quader(x0 - 1, y0 - 1, z0 - 1, x1 + 1, y1 + 1, z1 + 1, "polished_deepslate")
    b.luft(x0 + 1, y0 + 1, z0 + 1, x1 - 1, y1 - 1, z1 - 1)
    b.quader(x0 + 1, y0, z0 + 1, x1 - 1, y0, z1 - 1, "polished_andesite")
    for x in range(x0 + 3, x1 - 1, 4):
        for z in range(z0 + 3, z1 - 1, 4):
            b.setze(x, y1, z, LICHT)
    y = y0 + 1
    # Flur entlang des Aufzugs (Nord-Süd)
    b.quader(54, y0, z0 + 1, 61, y0, z1 - 1, "gray_concrete")
    for z in range(z0 + 1, z1, 5):
        b.setze(54, y0, z, LICHT)
        b.setze(61, y0, z, LICHT)
    # Trennwände
    b.quader(53, y, z0 + 1, 53, y1 - 1, z1 - 1, "deepslate_tiles")
    b.quader(62, y, z0 + 1, 62, y1 - 1, z1 - 1, "deepslate_tiles")
    b.quader(x0 + 1, y, 50, 52, y1 - 1, 50, "deepslate_tiles")
    b.quader(63, y, 50, x1 - 1, y1 - 1, 50, "deepslate_tiles")
    for (tx, tz) in ((53, 44), (53, 58), (62, 44), (62, 58)):
        b.luft(tx, y, tz, tx, y + 2, tz + 1)
    # Kommandozentrale (Nordwest)
    b.quader(x0 + 2, y + 1, z0 + 1, 51, y + 3, z0 + 1, SCHWARZ)
    for x in range(x0 + 3, 51, 2):
        b.marke("bunker_monitor", x, y + 2, z0 + 1)
    tisch(b, x0 + 3, y, z0 + 4, 50, z0 + 4, "polished_blackstone_slab")
    for x in range(x0 + 4, 50, 3):
        stuhl(b, x, y, z0 + 6, "north", "polished_blackstone_stairs")
    b.marke("bunker_zentrale", 45, y, 44)
    # Waffenkammer (Nordost): sechs Rüstungsständer mit voll verzauberten Sets (die Mod rüstet sie beim
    # Platzieren aus, siehe Waffenkammer.java), davor goldene Podeste, Truhen mit Agenten-Waffen und Vorräten
    b.quader(63, y0, z0 + 1, x1 - 1, y0, 49, "polished_blackstone")              # dunkler Boden
    b.quader(x1 - 1, y, z0 + 1, x1 - 1, y1 - 1, 49, SCHWARZ)                   # Rückwand schwarz
    b.quader(x1 - 1, y1 - 1, z0 + 1, x1 - 1, y1 - 1, 49, "gold_block")          # Goldleiste oben
    for i, z in enumerate(range(z0 + 2, 49, 3)):
        b.setze(x1 - 2, y0, z, "gold_block")                                    # goldener Sockel im Boden
        b.setze(x1 - 2, y, z, "smooth_quartz_slab")                             # Podest
        b.setze(x1 - 2, y1, z, LICHT)                                           # Spot von oben
        b.setze(x1 - 1, y + 3, z, "gold_block")                                 # Goldplakette hinter dem Kopf
        b.marke("ruestung", x1 - 1.5, y + 0.5, z + 0.5, 90.0, i)
    vorraete = [
        [("stinkendreich:goldene_pistole", 1), ("stinkendreich:laseruhr", 1), ("stinkendreich:geldkanone", 1)],
        [("stinkendreich:bond_sonnenbrille", 1), ("stinkendreich:smoking", 1), ("stinkendreich:anzughose", 1),
         ("stinkendreich:lackschuhe", 1)],
        [("totem_of_undying", 1), ("totem_of_undying", 1), ("enchanted_golden_apple", 8), ("golden_apple", 32)],
        [("ender_pearl", 16), ("firework_rocket", 64), ("arrow", 64), ("spectral_arrow", 64),
         ("netherite_ingot", 16), ("diamond", 64), ("experience_bottle", 64), ("shield", 1)],
    ]
    # Vier Truhen; der Platz direkt neben dem ersten Ständer bleibt frei (verdeckte sonst den Agenten-Anzug)
    for x, inhalt in zip(range(63, x1 - 2, 2), vorraete):
        truhe(b, x, y, z0 + 1, "south", inhalt)
    # Panikraum (Südwest): Betten, Vorräte
    for z in range(53, 66, 3):
        bett(b, x0 + 1, y, z, "west", "gray")
    for x in range(x0 + 4, 51, 2):
        truhe(b, x, y, z1 - 1, "north", [("bread", 32), ("cooked_beef", 32), ("golden_carrot", 16)])
    # Haupttresor (Südost) als Geldspeicher: Goldwand, ein Berg aus Gold zum Hineinspringen, Sprungbrett
    b.quader(63, y, 51, x1 - 1, y1 - 1, 51, "iron_block")
    b.quader(x1 - 1, y, 52, x1 - 1, y1 - 1, z1 - 1, "gold_block")
    mx, mz = 67.5, 60.5
    for x in range(63, x1 - 1):
        for z in range(52, z1):
            r = ((x - mx) ** 2 + ((z - mz) * 0.8) ** 2) ** 0.5
            hoehe = max(0, int(4.5 - r * 0.75))
            for dy in range(hoehe):
                b.setze(x, y + dy, z, "raw_gold_block" if (x * 3 + z + dy) % 4 == 0 else "gold_block")
    b.quader(66, y + 4, 52, 68, y + 4, 55, zustand("smooth_quartz_slab", type="bottom", waterlogged="false"))
    b.quader(66, y, 52, 68, y + 3, 52, "iron_block")
    for yy in range(y, y + 4):
        b.setze(69, yy, 53, zustand("ladder", facing="east", waterlogged="false"))
    b.quader(70, y, 53, 70, y + 3, 53, "iron_block")
    for x, z in ((64, 66), (71, 66), (64, 53)):
        deko(b, x, y, z, "geldstapel", "north")
    for x, z in ((63, 58), (71, 58)):
        deko(b, x, y, z, "goldbarren", "east")
    kronleuchter(b, 67, y1 - 1, 60)
    # Fluchttunnel nach Westen, Leiter hoch in den Westgarten; rundum Tiefenschiefer, damit kein
    # Grundwasser aus Höhlen hereinläuft
    b.quader(16, y0 - 1, 59, x0 - 1, y + 3, 62, "polished_deepslate")
    b.quader(15, y, 58, 19, G - 1, 62, "polished_deepslate")
    b.luft(x0 - 1, y, 60, 18, y + 2, 61)
    b.quader(x0 - 1, y0, 60, 18, y0, 61, "polished_andesite")
    for x in range(20, x0, 5):
        b.setze(x, y + 2, 60, LICHT)
    for yy in range(y, G + 1):
        b.setze(17, yy, 60, zustand("ladder", facing="east", waterlogged="false"))
        b.setze(16, yy, 60, "polished_deepslate")
        b.setze(17, yy, 59, "polished_deepslate")
        b.setze(17, yy, 61, "polished_deepslate")
        b.setze(18, yy, 59 if yy > y + 2 else 58, "polished_deepslate")
    b.setze(17, G, 60, zustand("dark_oak_trapdoor", facing="east", half="top", open="false", powered="false", waterlogged="false"))
    b.marke("fluchtluke", 17, G, 60)


# Mod-Blöcke mit eigener Block-Entity (für gespeicherte Werte wie Modus, Name, Code)
def mech(x: int, y: int, z: int, block: str, richtung: str | None = None, zustand_extra: dict | None = None,
         **werte) -> None:
    """Setzt einen Mechanik-Block der Mod; Werte landen in seiner Block-Entity, zustand_extra in den Blockzustand."""
    props = dict(zustand_extra or {})
    if richtung:
        props["facing"] = richtung
    if block in ("kartenleser", "tastenfeld", "bewegungsmelder"):
        props["powered"] = "false"
    if block == "warnleuchte":
        props["lit"] = "false"
    daten = {"id": "stinkendreich:mechanik", "stinkendreich": json.dumps({"werte": werte})} if werte else None
    b.setze(x, y, z, zustand(f"stinkendreich:{block}", **props), daten)


def fluegel(x0, y0, z0, x1, y1, z1, name: str, offen: bool) -> None:
    """Fläche aus Torflügeln (Tor, Rollladen, Schiebetür …), offen oder geschlossen.

    Jedes Feld bekommt seine Lage im Ganzen (Spalte längs der Fläche, Zeile von oben nach unten), damit
    Rahmen und Griffe stimmen. Liegt die Fläche flach (Poolabdeckung), zählen die Zeilen von Norden nach Süden.
    """
    def lage(i: int, n: int, namen: tuple[str, str, str]) -> str:
        if n == 1:
            return "einzeln"
        return namen[0] if i == 0 else namen[2] if i == n - 1 else namen[1]

    flach = y0 == y1 and x0 != x1 and z0 != z1
    achse = "z" if (x0 == x1 and not flach) else "x"
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                i, n = (z - z0, z1 - z0 + 1) if achse == "z" else (x - x0, x1 - x0 + 1)
                j, m = (z - z0, z1 - z0 + 1) if flach else (y1 - y, y1 - y0 + 1)
                b.setze(x, y, z, zustand(f"stinkendreich:{name}", open=str(offen).lower(), stufe="3" if offen else "0",
                                         axis=achse, spalte=lage(i, n, ("links", "mitte", "rechts")),
                                         zeile=lage(j, m, ("oben", "mitte", "unten"))))


def mechanismen() -> None:
    """Setzt alle Mechanismen der Villa (eigene Blöcke der Mod, keine Command-Blöcke)."""
    n = GROESSE[0] - 1
    yo = OG + 1
    pfeiler = set(range(HX0, HX1 + 1, 7)) | {HX1}

    # Tor: öffnet für Bewohner und Kartenträger, Klingel außen
    fluegel(52, G + 1, n - 13, 59, G + 4, n - 12, "tor", False)
    mech(55, G - 1, n - 13, "torsteuerung", modus="tor", ziel="stinkendreich:tor", radius=6)
    mech(60, G + 2, n - 11, "tuerklingel", "south")

    # Automatische Schiebetüren: Haustür und Durchgang zur Garage
    fluegel(54, EG + 1, HZ1, 55, EG + 3, HZ1, "schiebetuer", False)
    mech(54, EG - 1, HZ1, "torsteuerung", modus="schiebetuer", ziel="stinkendreich:schiebetuer", radius=4)
    fluegel(HX1, EG + 1, 60, HX1, EG + 2, 60, "schiebetuer", False)
    mech(HX1, EG - 1, 60, "torsteuerung", modus="schiebetuer", ziel="stinkendreich:schiebetuer", radius=3)

    # Garagentor
    fluegel(82, EG + 1, 67, 92, EG + 4, 67, "garagentor", False)
    mech(87, EG - 1, 67, "torsteuerung", modus="tor", ziel="stinkendreich:garagentor", radius=6)

    # Rollläden vor den Fenstern im Erdgeschoss: schließen ab Stufe 2 (Lockdown)
    luecke = []
    for x in range(HX0 + 1, HX1 + 1):
        if x in pfeiler or 53 <= x <= 56 or x == HX1:
            if luecke:
                fluegel(luecke[0], EG + 1, HZ1 + 1, luecke[-1], OG - 1, HZ1 + 1, "rollladen", True)
            luecke = []
        else:
            luecke.append(x)
    mech(55, EG - 1, HZ1 + 2, "torsteuerung", modus="rollladen", ziel="stinkendreich:rollladen", radius=24)

    # Sicherheitszentralen und Überwachung
    mech(61, yo, 54, "sicherheitszentrale", "east")
    mech(45, BUNKER + 1, 44, "sicherheitszentrale", "north")
    for m in b.marken["monitor"] + b.marken["bunker_monitor"]:
        mech(m[0], m[1], m[2], "monitor", "south", {"winkel": "wand"})
    for (x, y, z, r) in ((51, G + 5, n - 11, "south"), (60, G + 5, n - 14, "north"), (51, EG + 4, HZ1 + 1, "south"),
                         (58, EG + 4, HZ1 + 1, "south"), (55, OG + 3, HZ0 - 1, "north"), (65, yo + 3, 46, "north"),
                         (93, EG + 4, 47, "south"), (57, BUNKER + 4, 68, "north")):
        mech(x, y, z, "kamera", r)
    truhe(b, 77, yo, 66, "north", [("stinkendreich:zugangskarte", 1)] * 4)

    # Bewegungsmelder (Alarm bei Eindringlingen, Lichtautomatik), Sirenen, Warnleuchten
    mech(52, EG + 4, HZ0 + 1, "bewegungsmelder", "south")
    mech(51, G + 3, n - 11, "bewegungsmelder", "south")
    mech(60, BUNKER + 3, 31, "bewegungsmelder", "south")
    mech(91, EG + 3, 47, "bewegungsmelder", "south")                       # Garage: Licht an, wenn jemand kommt
    for (x, y) in ((90, EG + 3), (92, EG + 3), (91, EG + 4)):
        b.setze(x, y, 46, "redstone_lamp")
    mech(16, G + 4, 64, "bewegungsmelder", "east")                         # Pavillon: Licht an
    for z in (63, 65):
        b.setze(15, G + 4, z, "redstone_lamp")
    mech(56, OG + 4, HZ1 + 1, "alarmsirene", "south")
    mech(52, yo + 3, HZ0 + 1, "alarmsirene", "south")
    mech(57, BUNKER + 4, 31, "alarmsirene", "south")
    for x in (51, 60):
        mech(x, G + 7, n - 13, "warnleuchte")
    mech(57, BUNKER + 7, 50, "warnleuchte")
    mech(64, DACH - 1, 42, "warnleuchte")

    # Laser (ab Stufe 3): Westgarten, Bunkerflur, Tresor
    for z in (40, 58):
        mech(16, G + 1, z, "laser_sender", "east")
    for y in (BUNKER + 1, BUNKER + 2):
        mech(54, y, 45, "laser_sender", "east")
    for y in (yo, yo + 1):
        mech(62, y, 43, "laser_sender", "east")

    # Schmuck-Tresor: runde Tresortür mit Tastenfeld außen und Hebel innen, Fallenplatten, Gitterfalle, Entschärfknöpfe
    fluegel(63, yo, 47, 64, yo + 1, 47, "tresortuer", False)
    mech(62, yo, 47, "torsteuerung", modus="redstone", ziel="stinkendreich:tresortuer", radius=3)
    mech(62, yo + 1, 48, "tastenfeld", "south")
    b.setze(62, yo + 1, 46, zustand("lever", face="wall", facing="north", powered="false"))
    fluegel(63, yo, 46, 64, yo + 1, 46, "gitterfalle", True)
    for (x, z) in ((63, 42), (64, 40), (65, 43), (63, 45)):
        b.setze(x, OG, z, "stinkendreich:fallenplatte")
    mech(66, yo + 2, 47, "entschaerfknopf")
    b.quader(62, yo, 66, 62, DACH - 1, 66, WEISS)
    mech(62, yo + 1, 66, "entschaerfknopf")

    # Waffenkammer im Bunker: Bunkertür (Schloss und Riegel) mit Kartenleser auf beiden Seiten
    b.quader(62, BUNKER + 1, 45, 62, BUNKER + 3, 45, "deepslate_tiles")
    b.setze(62, BUNKER + 3, 44, "deepslate_tiles")
    tuer(b, 62, BUNKER + 1, 44, "east", "stinkendreich:bunkertuer")
    mech(61, BUNKER + 2, 43, "kartenleser", "west")
    mech(63, BUNKER + 2, 43, "kartenleser", "east")

    # Aufzug: Ruftafel rechts neben der Tür an jeder Haltestelle; die Kabine steht 1,5 Blöcke links
    # und 2,5 Blöcke hinter der Tafel
    namen = {"bunker": "Untergrundbasis", "keller": "Club & Tiefgarage", "eg": "Erdgeschoss", "og": "Obergeschoss",
             "dach": "Penthouse", "heli": "Hubschrauber"}
    for name, y in AUFZUG_HALTE.items():
        mech(AX1, y + 2, AZ1 + 1, "aufzugstafel", "south", name=namen[name],
             kabine_dx="-1.5", kabine_dz="-2.5", kabine_dy="-1")

    # Geheimgang: Bücherwand im Panikraum vor dem Fluchttunnel
    b.luft(35, BUNKER + 1, 60, 35, BUNKER + 3, 61)
    b.quader(36, BUNKER + 1, 57, 36, BUNKER + 3, 64, "bookshelf")
    fluegel(36, BUNKER + 1, 60, 36, BUNKER + 3, 61, "geheimregal", False)
    mech(36, BUNKER + 2, 63, "geheimschalter")

    # Panikraum: Panzertür, Verriegelung per Schalter innen
    fluegel(53, BUNKER + 1, 58, 53, BUNKER + 3, 59, "panzertuer", True)
    mech(52, BUNKER + 1, 56, "torsteuerung", modus="schalter", ziel="stinkendreich:panzertuer", radius=3)

    # Komfort: Poolabdeckung mit Hebel, Whirlpool, Kaminschalter, Musikanlage
    fluegel(41, G + 1, 19, 68, G + 1, 26, "poolabdeckung", True)
    mech(70, G, 22, "torsteuerung", modus="redstone_invers", ziel="stinkendreich:poolabdeckung", radius=29)
    b.setze(70, G + 1, 22, zustand("lever", face="floor", facing="north", powered="false"))
    mech(HX1 - 5, DACH, HZ0 + 3, "whirlpoolduese")
    mech(43, EG + 2, HZ0 + 1, "kaminsteuerung", "south")
    b.setze(52, DACH + 1, 40, zustand("jukebox", has_record="false"))


def licht_im_garten() -> None:
    """Unsichtbare Lichtblöcke auf dem Grundstück, damit nachts keine Monster spawnen und Alarm auslösen."""
    for x in range(15, GROESSE[0] - 15, 9):
        for z in range(15, GROESSE[2] - 15, 9):
            if b.hole(x, G + 1, z) is None:
                b.setze(x, G + 1, z, zustand("light", level="15", waterlogged="false"))


def lichter() -> None:
    """Hängeleuchten unter allen Decken, nur wo der Raum frei ist (zuletzt ausführen)."""
    def haengen(x0, x1, z0, z1, y, abstand=4):
        for x in range(x0, x1 + 1, abstand):
            for z in range(z0, z1 + 1, abstand):
                if b.hole(x, y, z) == "minecraft:air" and b.hole(x, y + 1, z) not in (None, "minecraft:air"):
                    b.setze(x, y, z, zustand("lantern", hanging="true", waterlogged="false"))
    haengen(HX0 + 3, HX1 - 2, HZ0 + 3, HZ1 - 2, OG - 1)          # Erdgeschoss
    haengen(HX0 + 3, HX1 - 2, HZ0 + 3, HZ1 - 2, DACH - 1)        # Obergeschoss
    haengen(44, 65, 40, 56, PH_DACH - 1)                         # Penthouse
    haengen(82, 92, 48, 64, OG - 1, 3)                           # Garage


def main() -> None:
    grundstueck()
    tor_und_bruecke()
    vorplatz()
    garten()
    garage()
    huelle()
    erdgeschoss()
    obergeschoss()
    penthouse()
    dachparty()
    hubschrauberlandeplatz()
    poolparty()
    bunker()
    keller()
    # Treppe und Aufzug nach allen Räumen, die beim Bau Luft schaffen (sonst fehlte der Schacht im Bunker)
    treppe_und_aufzug()
    mechanismen()
    licht_im_garten()
    lichter()

    # Spawnpunkt vor dem Haus, Blick nach Norden auf den Eingang
    b.marke("spawn", 55.5, G + 1, 93.5, 180.0)
    b.marke("butler", 57.5, EG + 1, 60.5)
    # Lieferkiste des Butlers in der Halle (Bestellungen „… in die Truhe“)
    truhe(b, 59, EG + 1, 62, "west", [])
    b.marke("lieferkiste", 59, EG + 1, 62)
    b.verbinden()

    # Dichtheitsprüfung: kein Wasser und keine Lava darf auslaufen, kein Raum unter der Erde offen sein
    probleme = b.pruefe_dichtheit(G)
    if probleme:
        print(f"{len(probleme)} Lecks im Bauplan, Villa wird nicht gespeichert:")
        for p in probleme[:40]:
            print("  " + p)
        sys.exit(1)

    ZIEL.mkdir(parents=True, exist_ok=True)
    b.speichern(ZIEL / "villa.nbt")
    meta = {
        "groesse": list(GROESSE),
        "boden_y": G,
        "etagen": {"bunker": BUNKER + 1, "eg": EG + 1, "og": OG + 1, "dach": DACH + 1},
        "marken": b.marken,
    }
    (ZIEL / "villa.json").write_text(json.dumps(meta, indent=1, ensure_ascii=False), encoding="utf-8")
    print(f"villa.nbt: {len(b.bloecke)} Blöcke, {len(b.entities)} Entities, {(ZIEL / 'villa.nbt').stat().st_size // 1024} KB")

    if "--vorschau" in sys.argv:
        from render import isometrisch
        aus = WURZEL / "build/villa"
        aus.mkdir(parents=True, exist_ok=True)
        vol = b.als_volumen()
        # Bunker ausblenden, damit man das Haus sieht
        oben = vol.bloecke.copy()
        oben[:G - 6] = 0
        vol.bloecke = oben
        isometrisch(vol, kante=6, blickrichtung=0).save(aus / "vorschau_iso.png")
        print(f"Vorschau: {aus / 'vorschau_iso.png'}")


if __name__ == "__main__":
    main()
