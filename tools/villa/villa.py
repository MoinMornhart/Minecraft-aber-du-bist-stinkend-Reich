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
from moebel import (aquarium, baum, bett, blumenbeet, brunnen_kuppel, doppeltuer, hecke, laterne, pflanze, sofa,
                    stuhl, tisch, truhe, tuer)

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

# Haus
HX0, HX1, HZ0, HZ1 = 30, 79, 36, 67
# Aufzugsschacht (außen 4x4, innen 2x2), Tür nach Süden
AX0, AX1, AZ0, AZ1 = 56, 59, 37, 40

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
    # Lava-Kanal unter begehbarem Glas
    for k in (3, 4):
        b.ring(k, k, n - k, n - k, G - 2, "magma_block")
        b.ring(k, k, n - k, n - k, G - 1, "lava")
        b.ring(k, k, n - k, n - k, G, "glass")
    # Wassergraben, 5 breit, 3 tief, mit Steinziegel-Rand und Lichtern am Grund
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
    # Liegen und Sonnenschirme
    for x in range(42, 68, 5):
        b.setze(x, G + 1, 29, zustand("white_bed", facing="north", part="head", occupied="false"))
        b.setze(x, G + 1, 30, zustand("white_bed", facing="north", part="foot", occupied="false"))
        if x % 10 == 2:
            b.quader(x + 2, G + 1, 30, x + 2, G + 2, 30, "end_rod")
            b.quader(x + 1, G + 3, 29, x + 3, G + 3, 31, "white_wool")
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
    # Eingang (Doppeltür mit Glas daneben)
    b.luft(53, EG + 1, HZ1, 56, EG + 3, HZ1)
    doppeltuer(b, 54, EG + 1, HZ1, "south", "dark_oak_door")
    b.setze(53, EG + 1, HZ1, GLAS)
    b.setze(56, EG + 1, HZ1, GLAS)
    b.marke("haustuer", 54, EG + 1, HZ1, 55, EG + 2, HZ1)
    # Balkon über dem Eingang: Obergeschoss-Platte kragt 3 Blöcke aus
    b.quader(HX0, OG, HZ1 + 1, HX1, OG, HZ1 + 3, WEISS)
    for x in range(HX0, HX1 + 1):
        b.setze(x, OG + 1, HZ1 + 3, GLAS)
    for z in range(HZ1 + 1, HZ1 + 4):
        b.setze(HX0, OG + 1, z, GLAS)
        b.setze(HX1, OG + 1, z, GLAS)
    b.luft(54, OG + 1, HZ1, 55, OG + 2, HZ1)
    doppeltuer(b, 54, OG + 1, HZ1, "south", "birch_door")
    # Dachplatte mit großem Überstand (2 seitlich, 6 nach Süden)
    b.quader(HX0 - 2, DACH, HZ0 - 2, HX1 + 2, DACH, HZ1 + 6, WEISS)
    b.quader(HX0 - 2, DACH - 1, HZ1 + 6, HX1 + 2, DACH - 1, HZ1 + 6, WEISS)
    # Lichtleiste unter dem Überstand
    for x in range(HX0, HX1 + 1, 4):
        b.setze(x, DACH, HZ1 + 4, LICHT)
    # Deckenlichter (bündig in den Platten)
    for x in range(HX0 + 3, HX1 - 1, 5):
        for z in range(HZ0 + 3, HZ1 - 1, 5):
            b.setze(x, OG, z, LICHT)
            b.setze(x, DACH, z, LICHT)


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
    # Aufzugsschacht: schwarze Ecken, Glaswände, bis unter das Penthouse-Dach
    for y in range(BUNKER + 1, PH_DACH):
        for x in range(AX0, AX1 + 1):
            for z in range(AZ0, AZ1 + 1):
                rand = x in (AX0, AX1) or z in (AZ0, AZ1)
                ecke = x in (AX0, AX1) and z in (AZ0, AZ1)
                if ecke:
                    b.setze(x, y, z, SCHWARZ)
                elif rand:
                    b.setze(x, y, z, "glass")
                else:
                    b.setze(x, y, z, "air")
    # Haltestellen: Boden in der Kabine, Öffnung nach Süden
    halte = {"bunker": BUNKER, "eg": EG, "og": OG, "dach": DACH}
    for name, y in halte.items():
        b.quader(AX0 + 1, y, AZ0 + 1, AX1 - 1, y, AZ1 - 1, "polished_deepslate")
        b.luft(AX0 + 1, y + 1, AZ1, AX1 - 1, y + 3, AZ1)
        b.marke("aufzug", name, AX0 + 1, y + 1, AZ0 + 1)
    # Zwischen den Haltestellen ist der Kabinenboden offen (Schacht)
    for y in range(BUNKER + 1, PH_DACH):
        if y not in halte.values():
            b.luft(AX0 + 1, y, AZ0 + 1, AX1 - 1, y, AZ1 - 1)


def erdgeschoss() -> None:
    """Halle, Wohnzimmer mit Aquarium und Kamin, Küche und Esszimmer."""
    y = EG + 1
    # Halle: heller Boden, Teppich zur Treppe
    b.quader(50, EG, HZ0 + 1, 59, EG, HZ1 - 1, QUARZ)
    b.quader(54, EG, 51, 55, EG, HZ1 - 1, "red_wool")
    pflanze(b, 52, y, HZ1 - 1, "potted_bamboo")
    pflanze(b, 57, y, HZ1 - 1, "potted_bamboo")
    # Trennwand Wohnzimmer | Halle mit Aquarium
    b.quader(49, y, HZ0 + 1, 49, OG - 1, HZ1 - 1, WEISS)
    aquarium(b, 47, y, 52, 49, OG - 1, 63)
    b.luft(49, y, 64, 49, y + 2, 66)
    # Wohnzimmer
    b.quader(HX0 + 1, EG, HZ0 + 1, 48, EG, HZ1 - 1, HOLZBODEN)
    b.quader(HX0 + 1, y + 1, 46, HX0 + 1, y + 3, 54, SCHWARZ)          # Fernseher
    b.quader(HX0 + 1, y, 45, HX0 + 1, y, 55, zustand("dark_oak_slab", type="top", waterlogged="false"))
    sofa(b, 39, y, 46, 9, "west", "polished_blackstone_stairs", None)
    sofa(b, 36, y, 44, 4, "south", "polished_blackstone_stairs", None)
    sofa(b, 36, y, 56, 4, "north", "polished_blackstone_stairs", None)
    b.quader(34, EG, 46, 37, EG, 54, "red_wool")
    tisch(b, 35, y, 48, 36, 52, "smooth_quartz_slab")
    # Kamin an der Nordwand
    b.quader(36, y, HZ0 + 1, 42, y + 4, HZ0 + 1, "polished_blackstone_bricks")
    b.luft(38, y, HZ0 + 1, 40, y + 1, HZ0 + 1)
    for x in (38, 39, 40):
        b.setze(x, y, HZ0 + 1, zustand("campfire", lit="true", facing="south", signal_fire="false", waterlogged="false"))
    b.quader(38, y + 2, HZ0 + 1, 40, y + 2, HZ0 + 1, "iron_bars")
    # Bücherwand
    b.quader(HX0 + 1, y, HZ0 + 1, HX0 + 1, y + 3, 42, "bookshelf")
    pflanze(b, HX0 + 1, y, HZ1 - 1, "potted_fern")
    pflanze(b, 46, y, HZ0 + 1, "potted_azalea_bush")
    # Küche (Nordostecke)
    b.quader(60, EG, HZ0 + 1, HX1 - 1, EG, HZ1 - 1, QUARZ)
    b.quader(60, y, HZ0 + 1, 60, OG - 1, 50, WEISS)
    b.luft(60, y, 44, 60, y + 2, 46)
    for x in range(62, HX1):
        b.setze(x, y, HZ0 + 1, "polished_deepslate")
        b.setze(x, y + 1, HZ0 + 1, zustand("smooth_quartz_slab", type="bottom", waterlogged="false"))
    b.setze(66, y, HZ0 + 1, zustand("water_cauldron", level="3"))
    b.setze(69, y, HZ0 + 1, zustand("smoker", facing="south", lit="false"))
    b.setze(70, y, HZ0 + 1, zustand("furnace", facing="south", lit="false"))
    b.quader(HX1 - 1, y, HZ0 + 1, HX1 - 1, y + 2, HZ0 + 2, "iron_block")   # Kühlschrank
    b.quader(65, y, 44, 72, y, 45, "polished_deepslate")                  # Kochinsel
    b.quader(65, y + 1, 44, 72, y + 1, 45, zustand("smooth_quartz_slab", type="bottom", waterlogged="false"))
    for x in range(66, 72, 2):
        b.setze(x, y, 47, zustand("dark_oak_stairs", facing="south", half="bottom", shape="straight", waterlogged="false"))
    # Esszimmer
    tisch(b, 66, y, 56, 74, 58, "dark_oak_slab")
    for x in range(66, 75):
        stuhl(b, x, y, 55, "south")
        stuhl(b, x, y, 59, "north")
    for x in range(67, 74, 3):
        b.setze(x, OG - 1, 57, zustand("lantern", hanging="true", waterlogged="false"))
    # Tür zur Garage
    b.luft(HX1, y, 60, HX1, y + 1, 60)
    tuer(b, HX1, y, 60, "east", "iron_door")
    b.marke("garagentuer", HX1, y, 60)


def obergeschoss() -> None:
    """Schlafzimmer, Bad, Tresorraum, Überwachungsraum und Büro."""
    y = OG + 1
    # Galerie
    b.quader(50, OG, HZ0 + 1, 59, OG, HZ1 - 1, QUARZ)
    b.luft(50, OG, 45, 51, OG, 50)
    # Wände der Räume
    b.quader(49, y, HZ0 + 1, 49, DACH - 1, HZ1 - 1, WEISS)
    b.quader(60, y, HZ0 + 1, 60, DACH - 1, HZ1 - 1, WEISS)
    b.luft(49, y, 58, 49, y + 1, 59)
    b.luft(60, y, 58, 60, y + 1, 59)
    # Schlafzimmer
    b.quader(HX0 + 1, OG, 50, 48, OG, HZ1 - 1, HOLZBODEN)
    b.quader(HX0 + 1, y, 49, 48, DACH - 1, 49, WEISS)
    b.luft(44, y, 49, 45, y + 1, 49)
    bett(b, HX0 + 1, y, 57, "west", "black")
    bett(b, HX0 + 1, y, 58, "west", "black")
    b.quader(HX0 + 1, y, 55, HX0 + 1, y + 2, 55, "dark_oak_log")
    b.quader(HX0 + 1, y, 60, HX0 + 1, y + 2, 60, "dark_oak_log")
    b.setze(HX0 + 1, y + 3, 57, zustand("lantern", hanging="false", waterlogged="false"))
    b.quader(34, OG, 54, 38, OG, 61, "white_wool")
    sofa(b, 44, y, 62, 3, "west", "smooth_quartz_stairs")
    # Bad und Ankleide (Nordwesten)
    b.quader(HX0 + 1, OG, HZ0 + 1, 48, OG, 48, "white_concrete")
    b.quader(HX0 + 2, y - 1, HZ0 + 2, HX0 + 6, y - 1, HZ0 + 4, "quartz_bricks")
    b.quader(HX0 + 3, y - 1, HZ0 + 3, HX0 + 5, y - 1, HZ0 + 3, "water")
    b.quader(HX0 + 2, y, HZ0 + 2, HX0 + 6, y, HZ0 + 4, zustand("quartz_slab", type="bottom", waterlogged="false"))
    b.quader(HX0 + 3, y, HZ0 + 3, HX0 + 5, y, HZ0 + 3, "air")
    b.setze(HX0 + 9, y, HZ0 + 1, zustand("water_cauldron", level="3"))
    b.quader(HX0 + 12, y, HZ0 + 1, HX0 + 14, y + 3, HZ0 + 3, "glass")
    b.luft(HX0 + 13, y, HZ0 + 2, HX0 + 13, y + 2, HZ0 + 2)
    b.setze(HX0 + 13, y + 3, HZ0 + 2, zustand("water", level="0"))
    for z in range(41, 48):
        truhe(b, 48, y, z, "west", [])
    # Tresorraum für den Schmuck (Nordosten, dicke Wände)
    b.quader(61, OG, HZ0 + 1, 67, DACH - 1, 47, "polished_deepslate_bricks")
    b.luft(62, y, HZ0 + 2, 66, DACH - 2, 46)
    b.quader(62, OG, HZ0 + 2, 66, OG, 46, "gold_block")
    b.luft(61, y, 41, 61, y + 1, 42)
    tuer(b, 61, y, 41, "west", "iron_door", "left")
    tuer(b, 61, y, 42, "west", "iron_door", "right")
    b.marke("tresortuer", 61, y, 41, 61, y + 1, 42)
    for z in range(HZ0 + 2, 47, 2):
        truhe(b, 66, y, z, "west", [("diamond", 16), ("emerald", 32), ("gold_ingot", 64), ("golden_apple", 4)])
    b.quader(62, y, HZ0 + 2, 65, y, HZ0 + 2, "diamond_block")
    b.quader(62, y, 46, 64, y, 46, "emerald_block")
    for z in (40, 43):
        b.setze(64, y, z, zustand("light_weighted_pressure_plate", power="0"))
        b.marke("tresorfalle", 64, y, z)
    b.marke("entschaerfknopf", 62, y + 3, 44)
    # Überwachungsraum (Nordost hinten)
    b.quader(68, OG, HZ0 + 1, HX1 - 1, OG, 50, "gray_concrete")
    b.quader(68, y, 51, HX1 - 1, DACH - 1, 51, WEISS)
    b.luft(70, y, 51, 71, y + 1, 51)
    b.quader(69, y + 1, HZ0 + 1, HX1 - 1, y + 3, HZ0 + 1, SCHWARZ)        # Monitorwand
    for x in range(69, HX1, 2):
        b.setze(x, y + 2, HZ0 + 1, "light_blue_stained_glass")
        b.marke("monitor", x, y + 2, HZ0 + 1)
    tisch(b, 69, y, 40, HX1 - 1, 40, "polished_blackstone_slab")
    for x in (71, 74, 77):
        stuhl(b, x, y, 42, "north", "polished_blackstone_stairs")
    # Büro mit Sicherheitszentrale (Südosten)
    b.quader(61, OG, 52, HX1 - 1, OG, HZ1 - 1, HOLZBODEN)
    tisch(b, 66, y, 60, 72, 61, "dark_oak_slab")
    stuhl(b, 69, y, 59, "south")
    b.quader(HX1 - 1, y, 53, HX1 - 1, y + 3, 64, "bookshelf")
    b.setze(62, y + 1, 53, "redstone_lamp")
    b.marke("sicherheitszentrale", 62, y + 1, 53)


def penthouse() -> None:
    """Gläserne Lounge auf dem Dach, Dachgarten, Whirlpool, Glasgeländer."""
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
    doppeltuer(b, 54, y, z1, "south", "birch_door")
    # Bar und Lounge
    b.quader(x0 + 2, y, z0 + 2, x0 + 9, y, z0 + 2, "polished_blackstone")
    b.quader(x0 + 2, y + 1, z0 + 2, x0 + 9, y + 1, z0 + 2, zustand("smooth_quartz_slab", type="bottom", waterlogged="false"))
    for x in range(x0 + 2, x0 + 10, 2):
        stuhl(b, x, y, z0 + 4, "north", "polished_blackstone_stairs")
    sofa(b, x0 + 6, y, z1 - 4, 8, "south", "smooth_quartz_stairs")
    tisch(b, x0 + 7, y, z1 - 2, x0 + 12, z1 - 2, "smooth_quartz_slab")
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


def garage() -> None:
    """Garage östlich des Hauses mit großem Tor nach Süden und Stellplatz für das Bonzenauto."""
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
    b.marke("auto", (x0 + x1) / 2 + 0.5, EG + 1, 56.5)
    # Werkbank und Werkzeugwand
    b.setze(x1 - 1, EG + 1, z0 + 1, "crafting_table")
    b.setze(x1 - 1, EG + 1, z0 + 2, "anvil")
    b.setze(x1 - 1, EG + 1, z0 + 3, "smithing_table")
    b.quader(x0 + 1, EG + 2, z0 + 1, x0 + 6, EG + 3, z0 + 1, "spruce_planks")
    b.luft(HX1 + 1, EG + 1, 60, x0, EG + 2, 60)
    b.quader(HX1 + 1, EG, 60, x0, EG, 60, "polished_andesite")


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
        b.setze(x, y + 2, z0 + 1, "lime_stained_glass")
    tisch(b, x0 + 3, y, z0 + 4, 50, z0 + 4, "polished_blackstone_slab")
    for x in range(x0 + 4, 50, 3):
        stuhl(b, x, y, z0 + 6, "north", "polished_blackstone_stairs")
    b.setze(45, y, 44, "redstone_lamp")
    b.marke("bunker_zentrale", 45, y, 44)
    # Waffenkammer (Nordost)
    for z in range(z0 + 2, 49, 3):
        b.entity(x1 - 1.5, y, z + 0.5, {"id": "minecraft:armor_stand", "Rotation": [90.0, 0.0], "ShowArms": ("b", 1)})
    for x in range(63, x1, 2):
        truhe(b, x, y, z0 + 1, "south", [("iron_sword", 1), ("bow", 1), ("arrow", 64), ("shield", 1)])
    # Panikraum (Südwest): Betten, Vorräte
    for z in range(53, 66, 3):
        bett(b, x0 + 1, y, z, "west", "gray")
    for x in range(x0 + 4, 51, 2):
        truhe(b, x, y, z1 - 1, "north", [("bread", 32), ("cooked_beef", 32), ("golden_carrot", 16)])
    # Haupttresor (Südost): Goldwand
    b.quader(63, y, 51, x1 - 1, y1 - 1, 51, "iron_block")
    b.quader(x1 - 1, y, 52, x1 - 1, y1 - 1, z1 - 1, "gold_block")
    b.quader(66, y, 56, 70, y, 64, "gold_block")
    # Fluchttunnel nach Westen, Leiter hoch in den Westgarten
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
    b.setze(17, G, 60, zustand("iron_trapdoor", facing="east", half="top", open="false", powered="false", waterlogged="false"))
    b.marke("fluchtluke", 17, G, 60)


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
    treppe_und_aufzug()
    bunker()

    # Spawnpunkt vor dem Haus, Blick nach Norden auf den Eingang
    b.marke("spawn", 55.5, G + 1, 76.5, 180.0)
    b.marke("butler", 57.5, EG + 1, 60.5)
    b.verbinden()

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
