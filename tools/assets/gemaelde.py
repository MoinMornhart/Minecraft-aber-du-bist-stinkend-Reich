#!/usr/bin/env python3
"""Eigene Gemälde für die Villa (selbst gezeichnete Pixelbilder, 16 Pixel pro Block, nichts kopiert).

Schreibt nach src/main/resources:
  assets/stinkendreich/textures/painting/<name>.png   das Bild
  data/stinkendreich/painting_variant/<name>.json     Größe, Titel, Künstler (ab 1.21; Titel ab 1.21.2 sichtbar)
  data/minecraft/tags/painting_variant/placeable.json  auch beim normalen Aufhängen möglich
  assets/stinkendreich/lang/*.json                    Titel der Bilder (über assets.py)

Aufruf: python3 tools/assets/gemaelde.py [--vorschau <datei.png>]
"""
from __future__ import annotations

import json
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw

WURZEL = Path(__file__).resolve().parents[2]
RES = WURZEL / "src/main/resources"
ASSETS = RES / "assets/stinkendreich"
NS = "stinkendreich"

GOLD = (236, 190, 52)
GOLD_HELL = (255, 232, 130)
GOLD_MITTEL = (212, 160, 40)
GOLD_DUNKEL = (150, 100, 20)
GOLD_TIEF = (96, 60, 12)
SCHWARZ = (18, 18, 22)
HAUT = (222, 176, 140)
HAUT_DUNKEL = (190, 142, 108)
WEISS = (240, 240, 236)


def neu(w: int, h: int, farbe=(0, 0, 0)) -> tuple[Image.Image, ImageDraw.ImageDraw]:
    img = Image.new("RGB", (w, h), farbe)
    return img, ImageDraw.Draw(img)


def verlauf(d: ImageDraw.ImageDraw, w: int, y0: int, y1: int, oben, unten) -> None:
    """Senkrechter Farbverlauf mit leichtem Raster (wirkt wie gemalt, nicht wie ein Computer-Verlauf)."""
    for y in range(y0, y1 + 1):
        t = (y - y0) / max(1, y1 - y0)
        for x in range(w):
            tt = min(1.0, max(0.0, t + (0.04 if (x + y) % 2 == 0 else -0.04)))
            d.point((x, y), fill=tuple(round(o + (u - o) * tt) for o, u in zip(oben, unten)))


def muenze(d: ImageDraw.ImageDraw, x: int, y: int) -> None:
    """Goldmünze, 3 Pixel breit, mit Glanz oben links."""
    d.line([(x, y), (x + 2, y)], fill=GOLD)
    d.point((x, y), fill=GOLD_HELL)
    d.point((x + 1, y + 1), fill=GOLD_DUNKEL)


def goldberg(img: Image.Image, d: ImageDraw.ImageDraw, w: int, h: int, spitze_x: float, hoehe: int, saat: int,
             breite: float = 0.55) -> None:
    """Berg aus Goldmünzen und Edelsteinen bis zum unteren Bildrand."""
    rnd = random.Random(saat)
    px = img.load()
    for x in range(w):
        dx = (x - spitze_x) / (w * breite)
        rand = h - 1 - int(hoehe * max(0.0, 1 - dx * dx)) + rnd.randint(-1, 0)
        for y in range(max(0, rand), h):
            tiefe = y - rand
            if tiefe == 0:
                farbe = GOLD_HELL
            else:
                farbe = (GOLD, GOLD_MITTEL, GOLD, GOLD_DUNKEL)[(x * 7 + y * 3 + rnd.randint(0, 3)) % 4]
                if tiefe > hoehe * 0.6:
                    farbe = GOLD_DUNKEL if (x + y) % 3 else GOLD_TIEF
            px[x, y] = farbe
    # Münzkanten und Edelsteine auf der Oberfläche
    for _ in range(w * h // 30):
        x = rnd.randrange(1, w - 3)
        y = rnd.randrange(h - hoehe, h - 1)
        if px[x, y] in (GOLD, GOLD_MITTEL):
            muenze(d, x, y)
    for _ in range(w // 6):
        x = rnd.randrange(2, w - 2)
        y = rnd.randrange(h - hoehe // 2, h - 1)
        if px[x, y] != GOLD_HELL:
            stein = rnd.choice([(220, 40, 60), (40, 200, 110), (70, 140, 255), (230, 230, 255)])
            d.point((x, y), fill=stein)
            d.point((x + 1, y), fill=tuple(c // 2 for c in stein))


def rahmen(img: Image.Image) -> Image.Image:
    """Feiner Goldrand innen (die Holzkante malt das Spiel selbst)."""
    d = ImageDraw.Draw(img)
    w, h = img.size
    d.rectangle([0, 0, w - 1, h - 1], outline=GOLD_DUNKEL)
    d.line([(0, 0), (w - 1, 0)], fill=GOLD_MITTEL)
    d.line([(0, 0), (0, h - 1)], fill=GOLD_MITTEL)
    return img


# ---------------------------------------------------------------------------
# Die Bilder
# ---------------------------------------------------------------------------


def goldrausch() -> Image.Image:
    """4×3: Ein Herr im Frack springt vom Sprungbrett kopfüber in einen Tresor voller Goldmünzen."""
    w, h = 64, 48
    img, d = neu(w, h)
    # Tresorwand: Stahlplatten mit Nieten
    verlauf(d, w, 0, h - 1, (54, 62, 74), (30, 34, 42))
    for x in range(0, w, 16):
        d.line([(x, 0), (x, h)], fill=(24, 28, 34))
        for y in range(3, h, 6):
            d.point((x + 2, y), fill=(110, 120, 134))
            d.point((x + 13, y), fill=(110, 120, 134))
    # Offene runde Tresortür hinten rechts
    d.ellipse([46, 4, 62, 20], fill=(88, 96, 108), outline=(150, 158, 170))
    d.ellipse([49, 7, 59, 17], outline=(60, 66, 76))
    for wnk in range(0, 360, 60):
        r = math.radians(wnk)
        d.line([(54, 12), (54 + 4 * math.cos(r), 12 + 4 * math.sin(r))], fill=GOLD)
    d.point((54, 12), fill=GOLD_HELL)
    # Großer Goldberg
    goldberg(img, d, w, h, spitze_x=38, hoehe=26, saat=7)
    # Sprungbrett links mit Leiter
    d.rectangle([0, 15, 15, 16], fill=(236, 236, 228))
    d.line([(0, 17), (15, 17)], fill=(150, 150, 146))
    d.rectangle([2, 17, 3, h - 1], fill=(120, 124, 132))
    for y in range(19, h, 3):
        d.line([(1, y), (4, y)], fill=(150, 154, 160))
    # Der Springer: kopfüber, Arme voraus, Zylinder fliegt hinterher
    sx, sy = 26, 20
    d.line([(sx - 5, sy - 6), (sx - 1, sy - 2)], fill=SCHWARZ, width=2)                 # Beine
    d.point((sx - 6, sy - 7), fill=(60, 30, 20))                                           # Schuhe
    d.point((sx - 5, sy - 7), fill=(60, 30, 20))
    d.polygon([(sx - 2, sy - 3), (sx + 1, sy - 3), (sx + 3, sy + 1), (sx, sy + 1)], fill=SCHWARZ)  # Frack
    d.line([(sx - 3, sy - 1), (sx - 4, sy + 1)], fill=SCHWARZ)                              # Frackschoß
    d.point((sx + 1, sy - 1), fill=WEISS)                                                   # Hemd
    d.rectangle([sx + 2, sy + 1, sx + 3, sy + 2], fill=HAUT)                                # Kopf
    d.line([(sx + 2, sy + 3), (sx + 5, sy + 6)], fill=SCHWARZ)                              # Arme
    d.point((sx + 5, sy + 6), fill=WEISS)                                                   # Handschuhe
    d.point((sx + 4, sy + 6), fill=WEISS)
    d.rectangle([sx - 1, sy - 9, sx + 1, sy - 8], fill=SCHWARZ)                             # Zylinder
    d.line([(sx - 2, sy - 7), (sx + 2, sy - 7)], fill=SCHWARZ)
    d.line([(sx - 1, sy - 8), (sx + 1, sy - 8)], fill=GOLD)
    # Spritzende Münzen an der Eintauchstelle
    for x, y in ((33, 22), (35, 20), (37, 23), (31, 21), (36, 18), (39, 21), (34, 24)):
        muenze(d, x, y)
    return rahmen(img)


def bonze() -> Image.Image:
    """2×3: Bildnis eines Herrn mit Zylinder, Monokel und Stock, auf einem Goldhaufen."""
    w, h = 32, 48
    img, d = neu(w, h)
    # Roter Samtvorhang mit Goldstreifen
    verlauf(d, w, 0, h - 1, (128, 18, 30), (70, 8, 18))
    for x in range(2, w, 6):
        d.line([(x, 0), (x, h)], fill=(150, 28, 40))
        d.line([(x + 1, 0), (x + 1, h)], fill=(96, 12, 22))
    d.line([(0, 2), (w, 2)], fill=GOLD)
    for x in range(0, w, 3):
        d.point((x, 3), fill=GOLD_DUNKEL)
    goldberg(img, d, w, h, spitze_x=16, hoehe=9, saat=3, breite=0.9)
    cx = 15
    # Zylinder mit Goldband
    d.rectangle([cx - 4, 5, cx + 3, 12], fill=SCHWARZ)
    d.line([(cx - 4, 5), (cx + 3, 5)], fill=(50, 50, 58))
    d.rectangle([cx - 4, 11, cx + 3, 11], fill=GOLD)
    d.line([(cx - 6, 13), (cx + 5, 13)], fill=SCHWARZ)
    # Gesicht
    d.rectangle([cx - 4, 14, cx + 3, 20], fill=HAUT)
    d.line([(cx - 4, 20), (cx + 3, 20)], fill=HAUT_DUNKEL)
    d.point((cx - 2, 16), fill=SCHWARZ)                     # linkes Auge
    d.ellipse([cx, 15, cx + 3, 18], outline=GOLD)           # Monokel
    d.point((cx + 1, 16), fill=SCHWARZ)
    d.line([(cx + 3, 18), (cx + 4, 24)], fill=GOLD_HELL)    # Kette
    d.point((cx - 1, 17), fill=HAUT_DUNKEL)                 # Nase
    d.line([(cx - 4, 18), (cx + 3, 18)], fill=WEISS)        # Schnurrbart
    d.point((cx - 5, 17), fill=WEISS)
    d.point((cx + 4, 17), fill=WEISS)
    d.line([(cx - 2, 19), (cx + 1, 19)], fill=(150, 80, 70))  # zufriedener Mund
    # Frack, Hemd, goldene Fliege und Weste
    d.polygon([(cx - 7, 22), (cx + 6, 22), (cx + 7, 40), (cx - 8, 40)], fill=SCHWARZ)
    d.polygon([(cx - 2, 21), (cx + 1, 21), (cx + 2, 30), (cx - 3, 30)], fill=WEISS)
    d.rectangle([cx - 2, 21, cx + 1, 22], fill=GOLD)
    d.point((cx - 1, 21), fill=GOLD_HELL)
    d.rectangle([cx - 2, 24, cx + 1, 31], fill=(110, 20, 30))
    for y in (25, 27, 29):
        d.point((cx - 1, y), fill=GOLD)
    d.line([(cx - 8, 26), (cx - 8, 36)], fill=(40, 40, 50))       # Arm links
    # Stock mit Goldknauf in der rechten Hand
    d.line([(cx + 9, 25), (cx + 9, 44)], fill=SCHWARZ)
    d.rectangle([cx + 8, 23, cx + 10, 24], fill=GOLD_HELL)
    d.rectangle([cx + 7, 28, cx + 8, 29], fill=WEISS)             # Handschuh
    d.line([(cx + 6, 24), (cx + 7, 28)], fill=SCHWARZ)
    # Beine im Gold
    d.rectangle([cx - 5, 40, cx - 2, 43], fill=SCHWARZ)
    d.rectangle([cx + 1, 40, cx + 4, 43], fill=SCHWARZ)
    return rahmen(img)


def goldbarren() -> Image.Image:
    """2×2: Stillleben, eine Pyramide aus Goldbarren auf dunklem Samt, darüber ein Lichtkegel."""
    w, h = 32, 32
    img, d = neu(w, h)
    verlauf(d, w, 0, h - 1, (26, 22, 40), (10, 8, 18))
    # Lichtkegel
    for y in range(h):
        halb = 3 + y * 0.45
        for x in range(w):
            if abs(x - 16) < halb and (x + y) % 2 == 0:
                r, g, b = img.getpixel((x, y))
                d.point((x, y), fill=(min(255, r + 18), min(255, g + 16), min(255, b + 10)))
    d.rectangle([0, 26, w, h], fill=(70, 14, 24))
    d.line([(0, 26), (w, 26)], fill=(110, 26, 38))

    def barren(x, y):
        d.polygon([(x, y + 4), (x + 7, y + 4), (x + 6, y), (x + 1, y)], fill=GOLD)
        d.line([(x + 1, y), (x + 6, y)], fill=GOLD_HELL)
        d.line([(x, y + 4), (x + 7, y + 4)], fill=GOLD_DUNKEL)
        d.point((x + 3, y + 2), fill=GOLD_DUNKEL)
        d.point((x + 4, y + 2), fill=GOLD_DUNKEL)
    for reihe, anzahl in enumerate((4, 3, 2, 1)):
        y = 21 - reihe * 5
        x0 = 16 - anzahl * 4
        for i in range(anzahl):
            barren(x0 + i * 8, y)
    for x, y in ((6, 6), (25, 9), (12, 3), (22, 4)):
        d.point((x, y), fill=GOLD_HELL)
        d.point((x - 1, y), fill=GOLD_DUNKEL)
        d.point((x + 1, y), fill=GOLD_DUNKEL)
        d.point((x, y - 1), fill=GOLD_DUNKEL)
        d.point((x, y + 1), fill=GOLD_DUNKEL)
    return rahmen(img)


def yacht() -> Image.Image:
    """4×2: Weiße Megayacht im Sonnenuntergang."""
    w, h = 64, 32
    img, d = neu(w, h)
    verlauf(d, w, 0, 18, (60, 30, 90), (250, 150, 60))
    d.ellipse([36, 11, 50, 25], fill=(255, 220, 120))
    d.ellipse([38, 13, 48, 23], fill=(255, 240, 170))
    verlauf(d, w, 19, h - 1, (40, 60, 110), (14, 24, 54))
    for y in range(19, h, 2):                                  # Spiegelung der Sonne
        breite = max(1, 7 - (y - 19) // 2)
        for x in range(43 - breite, 43 + breite, 2):
            d.point((x + (y % 4) // 2, y), fill=(255, 200, 110))
    for i in range(14):
        x, y = (i * 17) % 60 + 2, 21 + (i * 5) % 10
        d.line([(x, y), (x + 3, y)], fill=(70, 96, 150))
    # Rumpf
    d.polygon([(4, 18), (34, 18), (30, 22), (8, 22)], fill=(244, 244, 240))
    d.line([(8, 22), (30, 22)], fill=(180, 180, 176))
    d.line([(6, 19), (32, 19)], fill=(30, 40, 60))
    d.line([(9, 21), (29, 21)], fill=GOLD)
    # Aufbauten in drei Decks
    d.polygon([(10, 18), (28, 18), (26, 15), (12, 15)], fill=(236, 236, 232))
    d.line([(13, 16), (25, 16)], fill=(40, 60, 90))
    d.polygon([(14, 15), (25, 15), (23, 13), (16, 13)], fill=(228, 228, 224))
    d.line([(17, 14), (22, 14)], fill=(40, 60, 90))
    d.rectangle([19, 11, 20, 12], fill=(210, 210, 206))
    d.line([(20, 7), (20, 11)], fill=(200, 200, 196))           # Mast
    for x in range(14, 25, 3):
        d.point((x, 16), fill=(255, 220, 120))                   # Licht in den Fenstern
    # Möwen
    for x, y in ((8, 6), (14, 4), (56, 7)):
        d.point((x, y), fill=(30, 20, 40))
        d.point((x - 1, y - 1), fill=(30, 20, 40))
        d.point((x + 1, y - 1), fill=(30, 20, 40))
    return rahmen(img)


def privatjet() -> Image.Image:
    """3×2: Privatjet mit Goldstreifen über den Wolken."""
    w, h = 48, 32
    img, d = neu(w, h)
    verlauf(d, w, 0, h - 1, (40, 110, 200), (150, 200, 240))
    rnd = random.Random(5)
    for cx, cy, r in ((8, 26, 6), (18, 28, 7), (30, 27, 6), (42, 29, 7), (5, 31, 5), (36, 31, 6), (24, 31, 6)):
        for _ in range(60):
            a = rnd.random() * math.tau
            rr = r * math.sqrt(rnd.random())
            d.point((round(cx + rr * math.cos(a) * 1.4), round(cy + rr * math.sin(a) * 0.6)), fill=(250, 252, 255))
    # Rumpf
    d.polygon([(6, 14), (36, 12), (42, 13), (44, 15), (40, 17), (8, 17)], fill=(246, 246, 248))
    d.line([(8, 17), (40, 17)], fill=(170, 176, 186))
    d.line([(8, 15), (42, 15)], fill=GOLD)
    for x in range(14, 36, 3):
        d.point((x, 14), fill=(40, 60, 90))
    d.line([(40, 13), (42, 13)], fill=(40, 60, 90))              # Cockpit
    d.polygon([(6, 14), (2, 6), (6, 6), (12, 14)], fill=(236, 236, 240))     # Leitwerk
    d.line([(3, 7), (6, 7)], fill=GOLD)
    d.polygon([(18, 16), (28, 16), (20, 23), (16, 23)], fill=(220, 222, 228))  # Flügel
    d.rectangle([9, 12, 14, 13], fill=(200, 204, 212))                          # Triebwerk
    return rahmen(img)


def james() -> Image.Image:
    """2×2: Bildnis von James, dem Butler, mit Silbertablett."""
    w, h = 32, 32
    img, d = neu(w, h)
    verlauf(d, w, 0, h - 1, (40, 70, 60), (16, 34, 28))
    d.ellipse([4, 2, 27, 30], fill=(54, 92, 78))
    cx = 15
    # Kopf mit grauem Haar
    d.rectangle([cx - 5, 5, cx + 4, 6], fill=(150, 150, 152))
    d.rectangle([cx - 5, 7, cx + 4, 14], fill=HAUT)
    d.line([(cx - 5, 7), (cx - 5, 10)], fill=(150, 150, 152))
    d.line([(cx + 4, 7), (cx + 4, 10)], fill=(150, 150, 152))
    d.point((cx - 2, 9), fill=(40, 60, 110))
    d.point((cx + 1, 9), fill=(40, 60, 110))
    d.line([(cx - 3, 8), (cx - 1, 8)], fill=(110, 110, 112))
    d.line([(cx, 8), (cx + 2, 8)], fill=(110, 110, 112))
    d.point((cx - 1, 11), fill=HAUT_DUNKEL)
    d.line([(cx - 2, 13), (cx + 1, 13)], fill=(170, 100, 90))
    # Frack, Weste, Fliege
    d.polygon([(cx - 9, 17), (cx + 8, 17), (cx + 10, 31), (cx - 11, 31)], fill=(22, 22, 28))
    d.polygon([(cx - 3, 15), (cx + 2, 15), (cx + 3, 26), (cx - 4, 26)], fill=WEISS)
    d.rectangle([cx - 3, 18, cx + 2, 26], fill=(70, 70, 82))
    d.line([(cx - 2, 15), (cx + 1, 15)], fill=SCHWARZ)
    d.point((cx - 3, 16), fill=SCHWARZ)
    d.point((cx + 2, 16), fill=SCHWARZ)
    for y in (20, 23):
        d.point((cx - 1, y), fill=GOLD)
    # Silbertablett mit Glocke, auf der weiß behandschuhten Hand
    d.line([(cx + 6, 22), (cx + 14, 22)], fill=(200, 204, 210))
    d.ellipse([cx + 7, 17, cx + 13, 23], fill=(214, 218, 224))
    d.rectangle([cx + 7, 21, cx + 13, 22], fill=(214, 218, 224))
    d.point((cx + 10, 16), fill=(230, 232, 236))
    d.point((cx + 8, 19), fill=(250, 250, 252))
    d.rectangle([cx + 9, 23, cx + 11, 24], fill=(244, 244, 240))
    return rahmen(img)


def bonzenauto() -> Image.Image:
    """3×2: Das goldene Bonzenauto auf dem roten Teppich, mit Scheinwerfern im Studio."""
    w, h = 48, 32
    img, d = neu(w, h)
    verlauf(d, w, 0, h - 1, (20, 18, 30), (46, 40, 60))
    for x0 in (6, 40):
        for y in range(0, 24):
            breite = y // 3
            for x in range(x0 - breite, x0 + breite + 1, 2):
                if 0 <= x < w:
                    r, g, b = img.getpixel((x, y))
                    d.point((x, y), fill=(min(255, r + 22), min(255, g + 20), min(255, b + 14)))
    d.polygon([(0, 31), (48, 31), (40, 24), (8, 24)], fill=(150, 20, 30))
    d.line([(8, 24), (40, 24)], fill=(190, 40, 50))
    # Karosserie
    d.polygon([(6, 21), (8, 17), (16, 16), (20, 12), (32, 12), (36, 16), (42, 17), (43, 21)], fill=GOLD)
    d.line([(8, 17), (42, 17)], fill=GOLD_HELL)
    d.polygon([(21, 13), (31, 13), (34, 16), (18, 16)], fill=(40, 50, 70))
    d.line([(26, 13), (26, 16)], fill=GOLD_DUNKEL)
    d.line([(6, 21), (43, 21)], fill=GOLD_DUNKEL)
    d.point((42, 18), fill=(255, 255, 220))                         # Scheinwerfer
    d.point((7, 18), fill=(220, 40, 40))                            # Rücklicht
    for rx in (13, 35):
        d.ellipse([rx - 3, 18, rx + 3, 24], fill=SCHWARZ)
        d.ellipse([rx - 1, 20, rx + 1, 22], fill=(200, 204, 212))
    return rahmen(img)


def villa_nacht() -> Image.Image:
    """4×2: Die Villa bei Nacht unter Sternen, alle Fenster erleuchtet."""
    w, h = 64, 32
    img, d = neu(w, h)
    verlauf(d, w, 0, h - 1, (8, 10, 34), (30, 36, 80))
    rnd = random.Random(11)
    for _ in range(40):
        d.point((rnd.randrange(w), rnd.randrange(16)), fill=(230, 230, 255) if rnd.random() < 0.7 else GOLD_HELL)
    d.ellipse([50, 3, 56, 9], fill=(250, 246, 220))
    d.ellipse([52, 2, 58, 8], fill=(10, 12, 38))
    d.rectangle([0, 26, w, h], fill=(16, 40, 22))
    # Haus: zwei Geschosse und Penthouse, Glasfront
    d.rectangle([12, 14, 52, 26], fill=(24, 24, 28))
    d.rectangle([10, 13, 54, 13], fill=(230, 230, 226))
    d.rectangle([10, 19, 54, 19], fill=(230, 230, 226))
    d.rectangle([22, 9, 42, 12], fill=(24, 24, 28))
    d.rectangle([20, 8, 44, 8], fill=(230, 230, 226))
    for x in range(13, 52):
        if x % 7 not in (0, 1):
            for y in (15, 16, 17, 21, 22, 23, 24):
                d.point((x, y), fill=(255, 214, 120) if (x * y) % 5 else (255, 240, 180))
    for x in range(23, 42):
        if x % 4:
            d.point((x, 10), fill=(255, 214, 120))
            d.point((x, 11), fill=(255, 200, 100))
    d.rectangle([4, 27, 60, 28], fill=(60, 140, 200))               # Pool davor
    d.line([(4, 27), (60, 27)], fill=(140, 210, 240))
    return rahmen(img)


# Name: (Zeichner, Breite, Höhe in Blöcken, Titel deutsch, Titel englisch)
GEMAELDE = {
    "goldrausch": (goldrausch, 4, 3, "Goldrausch", "Gold Rush"),
    "der_bonze": (bonze, 2, 3, "Der Bonze", "The Tycoon"),
    "goldbarren": (goldbarren, 2, 2, "Stillleben mit Goldbarren", "Still Life with Gold Bars"),
    "yacht": (yacht, 4, 2, "Yacht im Abendrot", "Yacht at Sunset"),
    "privatjet": (privatjet, 3, 2, "Über den Wolken", "Above the Clouds"),
    "james": (james, 2, 2, "James", "James"),
    "bonzenauto": (bonzenauto, 3, 2, "Das Bonzenauto", "The Tycoon Car"),
    "villa_nacht": (villa_nacht, 4, 2, "Villa bei Nacht", "Villa at Night"),
}
KUENSTLER = "Stinkend Reich"


def schreibe_json(pfad: Path, daten) -> None:
    pfad.parent.mkdir(parents=True, exist_ok=True)
    pfad.write_text(json.dumps(daten, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main() -> dict:
    """Schreibt Bilder und Daten; gibt die Titel für die Sprachdateien zurück."""
    de, en = {}, {}
    for name, (zeichner, bw, bh, titel_de, titel_en) in GEMAELDE.items():
        bild = zeichner()
        assert bild.size == (bw * 16, bh * 16), name
        pfad = ASSETS / "textures/painting" / f"{name}.png"
        pfad.parent.mkdir(parents=True, exist_ok=True)
        bild.save(pfad)
        schreibe_json(RES / f"data/{NS}/painting_variant/{name}.json", {
            "asset_id": f"{NS}:{name}", "width": bw, "height": bh,
            "title": {"translate": f"painting.{NS}.{name}.title", "color": "yellow"},
            "author": {"translate": f"painting.{NS}.{name}.author", "color": "gray"},
        })
        de[f"painting.{NS}.{name}.title"], en[f"painting.{NS}.{name}.title"] = titel_de, titel_en
        de[f"painting.{NS}.{name}.author"] = en[f"painting.{NS}.{name}.author"] = KUENSTLER
    schreibe_json(RES / "data/minecraft/tags/painting_variant/placeable.json",
                  {"replace": False, "values": [f"{NS}:{name}" for name in GEMAELDE]})
    if "--vorschau" in sys.argv:
        vorschau(Path(sys.argv[sys.argv.index("--vorschau") + 1]))
    print(f"{len(GEMAELDE)} Gemälde geschrieben")
    return {"de": de, "en": en}


def vorschau(ziel: Path) -> None:
    bilder = [z() for z, *_ in GEMAELDE.values()]
    breite = sum(b.width for b in bilder) + 6 * (len(bilder) + 1)
    out = Image.new("RGB", (breite, 60), (90, 70, 50))
    x = 6
    for b in bilder:
        out.paste(b, (x, 6))
        x += b.width + 6
    out.resize((out.width * 4, out.height * 4), Image.NEAREST).save(ziel)


if __name__ == "__main__":
    main()
