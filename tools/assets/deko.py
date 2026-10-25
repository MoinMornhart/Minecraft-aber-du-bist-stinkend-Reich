#!/usr/bin/env python3
"""Deko-Blöcke der Villa: Möbel, Partyzubehör, Küchengeräte und Goldkram (eigene Pixelgrafik).

Jeder Block besteht aus Kästen (Elementen) mit Materialien. Materialien sind kachelbare 16×16-Texturen
(Gold, Samt, Marmor, Glas …) oder Bilder für eine Fläche (Herdplatte, Flaschenregal, Neonschild).
Die Vorderseite zeigt im Modell nach Norden; der Blockzustand dreht nach der Blickrichtung.

Schreibt nach src/main/resources:
  deko_bloecke.json                          Liste für ModDeko.java (Name, Form, Licht, Klang)
  assets/stinkendreich/textures/block/deko/  Materialien (zwei davon animiert)
  assets/stinkendreich/models/…, blockstates/…, items/…
  data/stinkendreich/loot_table/blocks/…

Wird von tools/assets/assets.py aufgerufen (Sprachdateien und Werkzeug-Listen).
"""
from __future__ import annotations

import json
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

WURZEL = Path(__file__).resolve().parents[2]
RES = WURZEL / "src/main/resources"
ASSETS = RES / "assets/stinkendreich"
NS = "stinkendreich"
TEX = ASSETS / "textures/block/deko"

GOLD = (232, 186, 52)
GOLD_HELL = (255, 228, 120)
GOLD_DUNKEL = (160, 112, 22)
LEER = (0, 0, 0, 0)


def neu(farbe=(0, 0, 0), alpha=255, w=16, h=16):
    img = Image.new("RGBA", (w, h), (*farbe, alpha))
    return img, ImageDraw.Draw(img)


def rauschen(img, staerke, saat):
    rnd = random.Random(saat)
    px = img.load()
    for x in range(img.width):
        for y in range(img.height):
            r, g, b, a = px[x, y]
            if a:
                d = rnd.randint(-staerke, staerke)
                px[x, y] = (max(0, min(255, r + d)), max(0, min(255, g + d)), max(0, min(255, b + d)), a)
    return img


# ---------------------------------------------------------------------------
# Materialien
# ---------------------------------------------------------------------------


def m_gold():
    img, d = neu(GOLD)
    for y in range(16):
        d.line([(0, y), (15, y)], fill=GOLD_HELL if y % 5 == 0 else (GOLD_DUNKEL if y % 5 == 3 else GOLD))
    for x, y in ((3, 2), (11, 7), (6, 12)):
        d.point((x, y), fill=(255, 250, 210))
    return img


def m_chrom():
    img, d = neu((196, 202, 210))
    for y in range(16):
        g = 196 + int(30 * math.sin(y / 15 * math.pi))
        d.line([(0, y), (15, y)], fill=(g, g + 4, g + 10))
    return rauschen(img, 3, 2)


def m_samt_rot():
    img, _ = neu((150, 20, 34))
    return rauschen(img, 10, 3)


def m_holz_dunkel():
    img, d = neu((70, 42, 26))
    rnd = random.Random(4)
    for y in range(16):
        if rnd.random() < 0.35:
            d.line([(0, y), (15, y)], fill=(56, 32, 20))
    return rauschen(img, 4, 4)


def m_marmor():
    img, d = neu((238, 236, 232))
    rauschen(img, 4, 5)
    rnd = random.Random(5)
    x, y = 2, 0
    for _ in range(24):
        d.point((x % 16, y % 16), fill=(176, 174, 180))
        x += rnd.choice((0, 1, 1))
        y += 1
    return img


def m_lack_weiss():
    img, d = neu((244, 244, 240))
    d.rectangle([0, 0, 15, 15], outline=(222, 222, 216))
    d.line([(2, 1), (13, 1)], fill=GOLD)
    return img


def m_glas():
    img, d = neu((0, 0, 0), 0)
    for i in range(3):
        d.point((2 + i, 5 - i), fill=(236, 246, 252, 255))
        d.point((11 + i, 13 - i), fill=(236, 246, 252, 255))
    d.rectangle([0, 0, 15, 15], outline=(220, 232, 240, 255))
    return img


def m_schwarzglas():
    img, d = neu((18, 18, 22))
    for i in range(4):
        d.point((3 + i, 6 - i), fill=(70, 74, 86))
    return img


def m_keramik():
    img, _ = neu((246, 246, 244))
    return rauschen(img, 2, 6)


def m_pink():
    img, d = neu((240, 120, 170))
    for y in range(0, 16, 4):
        d.line([(0, y), (15, y)], fill=(255, 160, 200))
    return rauschen(img, 3, 7)


def m_schnabel():
    img, d = neu((240, 180, 190))
    d.rectangle([0, 8, 15, 15], fill=(30, 30, 30))
    return img


def m_led():
    img, _ = neu((255, 250, 232))
    return img


def m_kerze():
    img, d = neu((246, 240, 224))
    d.rectangle([0, 0, 15, 3], fill=(255, 200, 80))
    d.rectangle([5, 0, 10, 1], fill=(255, 250, 200))
    return img


def m_kristall():
    img, d = neu((0, 0, 0), 0)
    for x in range(16):
        for y in range(16):
            if (x + y) % 4 == 0 or (x - y) % 4 == 0:
                d.point((x, y), fill=(220, 236, 255, 255))
    return img


def m_spiegel():
    """Discokugel: Spiegelfacetten, 4 Bilder, die Lichtpunkte wandern."""
    bilder = []
    for f in range(4):
        img, d = neu((150, 156, 168))
        for x in range(0, 16, 2):
            for y in range(0, 16, 2):
                g = 150 + ((x * 7 + y * 13 + f * 5) % 7) * 15
                d.rectangle([x, y, x + 1, y + 1], fill=(g, min(255, g + 6), min(255, g + 20)))
                d.point((x + 1, y + 1), fill=(90, 96, 110))
        for i in range(3):
            x, y = (3 + i * 5 + f * 4) % 16, (2 + i * 6 + f * 3) % 16
            d.point((x, y), fill=(255, 255, 255))
            d.point(((x + 1) % 16, y), fill=(255, 240, 255))
        bilder.append(img)
    return bilder


def m_tanz():
    """Tanzfläche: 2 × 2 Leuchtfelder mit dunklen Fugen, Farben wechseln reihum (8 Bilder)."""
    farben = [(255, 40, 160), (40, 220, 255), (255, 220, 40), (90, 255, 90), (180, 70, 255), (255, 120, 30)]
    bilder = []
    for f in range(8):
        img, d = neu((10, 10, 14))
        for i, (x0, y0) in enumerate(((1, 1), (9, 1), (1, 9), (9, 9))):
            c = farben[(f + (0, 2, 3, 1)[i]) % len(farben)]
            d.rectangle([x0, y0, x0 + 5, y0 + 5], fill=c)
            d.rectangle([x0 + 1, y0 + 1, x0 + 4, y0 + 4], fill=tuple(min(255, v + 60) for v in c))
        bilder.append(img)
    return bilder


def m_goldbarren():
    img, d = neu(GOLD)
    d.rectangle([0, 0, 15, 15], outline=GOLD_DUNKEL)
    d.line([(1, 1), (14, 1)], fill=GOLD_HELL)
    d.rectangle([5, 6, 10, 9], outline=GOLD_DUNKEL)
    d.point((7, 7), fill=GOLD_DUNKEL)
    d.point((8, 8), fill=GOLD_DUNKEL)
    return img


def m_geld_oben():
    """Geldbündel von oben: grüner Schein mit Muster, Papierband in der Mitte."""
    img, d = neu((120, 170, 110))
    d.rectangle([0, 0, 15, 15], outline=(80, 120, 74))
    d.ellipse([5, 3, 10, 8], outline=(70, 110, 64))
    d.line([(2, 11), (13, 11)], fill=(90, 140, 84))
    d.line([(2, 13), (9, 13)], fill=(90, 140, 84))
    d.rectangle([0, 6, 15, 9], fill=(236, 226, 196))
    d.line([(0, 6), (15, 6)], fill=(200, 186, 150))
    return img


def m_geld_seite():
    img, d = neu((200, 220, 190))
    for y in range(0, 16, 2):
        d.line([(0, y), (15, y)], fill=(130, 170, 120))
    d.rectangle([6, 0, 9, 15], fill=(236, 226, 196))
    return img


def m_flaschen():
    """Barregal von vorn: drei Fächer voller bunter Flaschen, Hintergrundlicht."""
    img, d = neu((40, 26, 18))
    rnd = random.Random(9)
    for fach in range(3):
        y0 = fach * 5 + 1
        d.rectangle([1, y0, 14, y0 + 3], fill=(240, 196, 110))
        x = 2
        while x < 14:
            farbe = rnd.choice([(40, 110, 50), (120, 30, 30), (200, 160, 60), (230, 230, 240), (60, 40, 30), (40, 80, 140)])
            d.rectangle([x, y0 + 1, x + 1, y0 + 3], fill=farbe)
            d.point((x, y0), fill=farbe)
            x += 3
        d.line([(0, y0 + 4), (15, y0 + 4)], fill=GOLD_DUNKEL)
    return img


def m_herd_oben():
    img, d = neu((16, 16, 20))
    for cx, cy in ((4, 4), (11, 4), (4, 11), (11, 11)):
        d.ellipse([cx - 3, cy - 3, cx + 3, cy + 3], outline=(110, 110, 120))
        d.point((cx, cy), fill=(200, 60, 40))
    d.line([(0, 15), (15, 15)], fill=GOLD)
    return img


def m_ofen_front():
    img, d = neu((236, 236, 232))
    d.rectangle([1, 4, 14, 14], fill=(26, 26, 30))
    d.rectangle([3, 7, 12, 12], fill=(60, 40, 30))
    d.point((4, 8), fill=(255, 160, 80))
    d.point((9, 10), fill=(255, 120, 60))
    d.line([(3, 5), (12, 5)], fill=GOLD)
    for x in (3, 6, 9, 12):
        d.point((x, 2), fill=(60, 60, 66))
    return img


def m_kuehl_front():
    img, d = neu((200, 206, 214))
    rauschen(img, 3, 10)
    d.line([(7, 0), (7, 15)], fill=(120, 126, 134))
    d.rectangle([5, 2, 5, 13], fill=GOLD)
    d.rectangle([9, 2, 9, 13], fill=GOLD)
    d.rectangle([11, 3, 13, 5], fill=(30, 40, 60))
    return img


def m_weinkuehl_front():
    img, d = neu((20, 22, 30))
    for fach in range(4):
        y = 2 + fach * 3
        d.line([(1, y + 2), (14, y + 2)], fill=(70, 50, 30))
        for x in range(2, 14, 3):
            d.rectangle([x, y, x + 1, y + 1], fill=(90, 20, 40) if (x + fach) % 2 else (40, 70, 40))
    d.rectangle([0, 0, 15, 15], outline=(160, 166, 176))
    d.line([(14, 4), (14, 11)], fill=GOLD)
    return img


def m_champagner():
    img, d = neu((30, 70, 40))
    d.rectangle([0, 0, 15, 4], fill=GOLD)
    d.rectangle([2, 7, 13, 11], fill=(236, 226, 196))
    return img


def m_cocktail():
    img, d = neu((255, 140, 60))
    d.rectangle([0, 0, 15, 4], fill=(255, 210, 120))
    return img


def m_sekt():
    """Sektgläser: Glas mit goldenem Inhalt, oben durchsichtig."""
    img, d = neu((0, 0, 0), 0)
    d.rectangle([0, 0, 15, 15], outline=(230, 240, 250, 255))
    d.rectangle([1, 6, 14, 14], fill=(250, 214, 110, 255))
    for x in (3, 8, 12):
        d.point((x, 9), fill=(255, 250, 220, 255))
    return img


def neon(text: str, farbe) -> Image.Image:
    """Leuchtschrift auf durchsichtigem Grund (5-Pixel-Schrift, eigene Buchstaben)."""
    buchstaben = {
        "B": ["110", "101", "110", "101", "110"], "A": ["010", "101", "111", "101", "101"],
        "R": ["110", "101", "110", "101", "101"], "V": ["101", "101", "101", "101", "010"],
        "I": ["111", "010", "010", "010", "111"], "P": ["110", "101", "110", "100", "100"],
        "C": ["011", "100", "100", "100", "011"], "L": ["100", "100", "100", "100", "111"],
        "U": ["101", "101", "101", "101", "111"],
    }
    img, d = neu((0, 0, 0), 0)
    breite = len(text) * 4 - 1
    x0, y0 = (16 - breite) // 2, 5
    hell = tuple(min(255, v + 90) for v in farbe)
    for i, ch in enumerate(text):
        for zy, zeile in enumerate(buchstaben[ch]):
            for zx, bit in enumerate(zeile):
                if bit == "1":
                    d.point((x0 + i * 4 + zx, y0 + zy), fill=(*hell, 255))
    # Leuchtrand um die Schrift
    px = img.load()
    for x in range(16):
        for y in range(16):
            if px[x, y][3] == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and px[x + dx, y + dy][:3] == hell
                                        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = (*farbe, 255)
    return img


def m_fluegel_tasten():
    img, d = neu((240, 240, 236))
    for x in range(0, 16, 2):
        d.line([(x, 0), (x, 15)], fill=(200, 200, 196))
    for x in (1, 3, 7, 9, 11, 15):
        d.rectangle([x, 0, x, 8], fill=(20, 20, 24))
    return img


def m_samt_knoepfe():
    """Gepolstertes Kopfteil: roter Samt mit goldenen Knöpfen im Rautenmuster, Goldkante."""
    img, d = neu((140, 18, 32))
    rauschen(img, 6, 21)
    for y in range(2, 16, 4):
        for x in range(2 + (y // 4 % 2) * 2, 16, 4):
            d.point((x, y), fill=GOLD)
            d.point((x - 1, y - 1), fill=(110, 12, 24))
    d.rectangle([0, 0, 15, 15], outline=GOLD_DUNKEL)
    return img


def m_tastatur():
    img, d = neu((30, 30, 34))
    for y in range(1, 15, 3):
        for x in range(1, 15, 2):
            d.point((x, y), fill=(150, 150, 158))
    return img


def m_maus():
    img, d = neu((40, 40, 44))
    d.line([(7, 0), (7, 6)], fill=(20, 20, 22))
    d.point((7, 3), fill=(80, 200, 255))
    return img


def m_lack_schwarz():
    img, d = neu((14, 14, 18))
    for i in range(5):
        d.point((2 + i, 8 - i), fill=(80, 80, 92))
    return img


def m_leder_weiss():
    """Weißes Nappaleder mit feiner Steppnaht am Rand."""
    img, d = neu((236, 232, 222))
    rauschen(img, 3, 31)
    for i in range(1, 15, 2):
        d.point((i, 1), fill=(214, 208, 196))
        d.point((i, 14), fill=(214, 208, 196))
    return img


def m_samt_schwarz():
    img, _ = neu((30, 28, 34))
    return rauschen(img, 6, 32)


def m_satin_gold():
    """Bettdecke: champagnerfarbener Satin mit Glanzstreifen und Steppkaros."""
    img, d = neu((206, 168, 96))
    for i in range(-16, 16, 6):
        d.line([(i, 15), (i + 15, 0)], fill=(226, 192, 120))
    for x in range(0, 16, 8):
        d.line([(x, 0), (x, 15)], fill=(180, 142, 74))
    for y in range(0, 16, 8):
        d.line([(0, y), (15, y)], fill=(180, 142, 74))
    return rauschen(img, 3, 33)


def m_satin_schwarz():
    """Bettdecke: schwarzer Satin mit weichem, schrägem Glanz (kein Raster, sonst wirkt es wie Dielen)."""
    img, d = neu((22, 20, 26))
    px = img.load()
    for x in range(16):
        for y in range(16):
            g = (x + y) % 16
            licht = max(0, 10 - abs(g - 8) * 2)
            px[x, y] = (22 + licht, 20 + licht, 26 + licht + licht // 2, 255)
    return rauschen(img, 2, 42)


def m_laken():
    """Bettwäsche: weißer Satin mit Goldpaspel an der Kante."""
    img, d = neu((246, 244, 238))
    rauschen(img, 2, 34)
    d.line([(0, 15), (15, 15)], fill=GOLD)
    return img


def m_matratze():
    img, d = neu((240, 238, 232))
    for x in range(0, 16, 4):
        for y in range(2 + (x // 4 % 2) * 2, 16, 4):
            d.point((x, y), fill=(214, 212, 206))
    return img


def m_kissen_weiss():
    img, d = neu((250, 250, 246))
    d.rectangle([0, 0, 15, 15], outline=(222, 220, 214))
    d.line([(2, 2), (6, 2)], fill=(255, 255, 255))
    return img


def m_fell():
    """Weißes Fell (Überwurf am Bettende)."""
    img, d = neu((236, 234, 230))
    rnd = random.Random(35)
    for _ in range(60):
        x, y = rnd.randrange(16), rnd.randrange(16)
        d.line([(x, y), (x + rnd.choice((-1, 0, 1)), y + 1)], fill=rnd.choice([(212, 208, 202), (255, 255, 252), (224, 220, 214)]))
    return img


def m_teak():
    """Teakholz der Strandliegen: warm, mit Maserung."""
    img, d = neu((150, 96, 54))
    rnd = random.Random(36)
    for y in range(16):
        if rnd.random() < 0.4:
            d.line([(0, y), (15, y)], fill=(128, 80, 44))
    for y in range(0, 16, 4):
        d.line([(0, y), (15, y)], fill=(100, 62, 32))
    return rauschen(img, 4, 36)


def m_polster_weiss():
    """Dickes Liegenpolster mit Paspel."""
    img, d = neu((244, 242, 236))
    rauschen(img, 2, 37)
    d.rectangle([0, 0, 15, 15], outline=(210, 206, 198))
    for x in (5, 10):
        d.point((x, 7), fill=(214, 210, 202))
    return img


def m_handtuch():
    """Frotteehandtuch, türkis-weiß gestreift."""
    img, d = neu((250, 250, 250))
    for y in range(16):
        if y % 6 in (0, 1, 2):
            d.line([(0, y), (15, y)], fill=(40, 186, 196))
    return rauschen(img, 3, 38)


def m_schirm():
    """Sonnenschirmstoff: Creme mit Goldbordüre."""
    img, d = neu((246, 238, 220))
    rauschen(img, 2, 39)
    d.rectangle([0, 0, 15, 15], outline=GOLD)
    return img


def m_schirm_glatt():
    img, _ = neu((246, 238, 220))
    return rauschen(img, 2, 43)


def m_lampenschirm():
    img, d = neu((252, 236, 196))
    for y in range(16):
        d.line([(0, y), (15, y)], fill=(255, 240 - y, 200 - y * 2))
    d.line([(0, 0), (15, 0)], fill=GOLD)
    d.line([(0, 15), (15, 15)], fill=GOLD)
    return img


def m_nachttisch_front():
    """Nachttisch von vorn: zwei Schubladen in schwarzem Lack mit Goldgriffen."""
    img, d = neu((16, 16, 20))
    for y0 in (3, 9):
        d.rectangle([2, y0, 13, y0 + 4], outline=(46, 46, 52))
        d.line([(6, y0 + 2), (9, y0 + 2)], fill=GOLD)
    d.line([(0, 15), (15, 15)], fill=GOLD_DUNKEL)
    return img


def m_popcorn_tuete():
    img, d = neu((250, 250, 250))
    for x in range(0, 16, 4):
        d.rectangle([x, 0, x + 1, 15], fill=(210, 30, 40))
    return img


def m_popcorn():
    img, d = neu((250, 236, 190))
    rnd = random.Random(40)
    for _ in range(40):
        d.point((rnd.randrange(16), rnd.randrange(16)), fill=rnd.choice([(255, 250, 230), (236, 200, 110), (255, 255, 255)]))
    return img


def m_schreibunterlage():
    """Schreibtischunterlage aus schwarzem Leder mit feiner Goldnaht (64 × 64, passt zum Kasten x 0–16,
    z 2,5–13,5; die Naht liegt 2 Texel innerhalb der Kante)."""
    img = Image.new("RGBA", (64, 64), (26, 24, 26, 255))
    rauschen(img, 3, 41)
    d = ImageDraw.Draw(img)
    x0, z0, x1, z1 = 2, 12, 61, 51
    for x in range(x0, x1 + 1, 3):
        d.line([(x, z0), (x + 1, z0)], fill=(150, 116, 46))
        d.line([(x, z1), (x + 1, z1)], fill=(150, 116, 46))
    for z in range(z0, z1 + 1, 3):
        d.line([(x0, z), (x0, z + 1)], fill=(150, 116, 46))
        d.line([(x1, z), (x1, z + 1)], fill=(150, 116, 46))
    return img


def m_buero_hd():
    """Fein aufgelöste Bürotextur (64 × 64, 4 Texel pro Pixel): Tastatur oben links (48 × 18), Maus darunter.
    Gemalt wie der Sitzende sie sieht und dann um 180° gedreht, weil er von Norden auf den Block schaut."""
    img = Image.new("RGBA", (64, 64), (18, 18, 22, 255))
    # --- Tastatur (aus Sicht des Sitzenden) ---
    t = Image.new("RGBA", (48, 18), (24, 24, 28, 255))
    d = ImageDraw.Draw(t)
    d.rectangle([0, 0, 47, 17], outline=(70, 70, 78))
    taste, hell, schrift = (44, 44, 50), (58, 58, 66), (200, 200, 210)
    # Reihen: F-Tasten, Zahlen, drei Buchstabenreihen, unten Leertaste; rechts der Ziffernblock
    reihen_y = (1, 4, 7, 10, 13)
    for i, y in enumerate(reihen_y):
        for x in range(2, 35, 3):
            d.rectangle([x, y, x + 1, y + 1], fill=taste)
            d.point((x, y), fill=hell)
            if (x + i) % 2:
                d.point((x + 1, y + 1), fill=schrift)
    d.rectangle([2, 15, 7, 16], fill=taste)
    d.rectangle([9, 15, 26, 16], fill=taste)           # Leertaste
    d.line([(9, 15), (26, 15)], fill=hell)
    d.rectangle([28, 15, 34, 16], fill=taste)
    for y in reihen_y[1:]:
        for x in (38, 41, 44):
            d.rectangle([x, y, x + 1, y + 1], fill=taste)
            d.point((x, y), fill=hell)
    d.rectangle([38, 15, 42, 16], fill=taste)
    d.point((45, 1), fill=(80, 220, 120))               # Kontrollleuchte
    d.point((46, 1), fill=(80, 220, 120))
    img.paste(t.rotate(180), (0, 0))
    # --- Maus (7 × 12 Texel bei 0,24): Tasten vorn, Mausrad, Goldlogo hinten ---
    m = Image.new("RGBA", (7, 12), (22, 22, 26, 255))
    d = ImageDraw.Draw(m)
    d.rectangle([0, 0, 6, 11], outline=(52, 52, 58))
    d.line([(3, 0), (3, 4)], fill=(8, 8, 10))
    d.rectangle([3, 1, 3, 2], fill=(150, 150, 160))
    d.point((3, 8), fill=GOLD)
    d.point((1, 1), fill=(90, 90, 100))
    img.paste(m.rotate(180), (0, 24))
    return img


MATERIALIEN = {
    "gold": m_gold, "chrom": m_chrom, "samt_rot": m_samt_rot, "holz_dunkel": m_holz_dunkel, "marmor": m_marmor,
    "lack_weiss": m_lack_weiss, "glas": m_glas, "schwarzglas": m_schwarzglas, "keramik": m_keramik, "pink": m_pink,
    "schnabel": m_schnabel, "led": m_led, "kerze": m_kerze, "kristall": m_kristall, "goldbarren": m_goldbarren,
    "geld_oben": m_geld_oben, "geld_seite": m_geld_seite, "flaschen": m_flaschen, "herd_oben": m_herd_oben,
    "ofen_front": m_ofen_front, "kuehl_front": m_kuehl_front, "weinkuehl_front": m_weinkuehl_front,
    "champagner": m_champagner, "cocktail": m_cocktail, "sekt": m_sekt, "fluegel_tasten": m_fluegel_tasten,
    "lack_schwarz": m_lack_schwarz, "tastatur": m_tastatur, "maus": m_maus, "samt_knoepfe": m_samt_knoepfe,
    "neon_bar": lambda: neon("BAR", (255, 40, 160)), "neon_vip": lambda: neon("VIP", (255, 200, 40)),
    "neon_club": lambda: neon("CLUB", (40, 220, 255)),
    "leder_weiss": m_leder_weiss, "samt_schwarz": m_samt_schwarz, "satin_gold": m_satin_gold, "satin_schwarz": m_satin_schwarz, "laken": m_laken,
    "matratze": m_matratze, "kissen_weiss": m_kissen_weiss, "fell": m_fell, "teak": m_teak,
    "polster_weiss": m_polster_weiss, "handtuch": m_handtuch, "schirm": m_schirm, "schirm_glatt": m_schirm_glatt, "lampenschirm": m_lampenschirm,
    "nachttisch_front": m_nachttisch_front, "popcorn_tuete": m_popcorn_tuete, "popcorn": m_popcorn,
    "schreibunterlage": m_schreibunterlage, "buero_hd": m_buero_hd,
}
ANIMIERT = {"spiegel": (m_spiegel, 5), "tanz": (m_tanz, 8)}

# ---------------------------------------------------------------------------
# Modelle: Kästen in Pixeln, Vorderseite nach Norden
# ---------------------------------------------------------------------------


def k(von, bis, mat, uv=None, drehung=None, **flaechen):
    """Kasten mit einem Material; einzelne Flächen (north, up …) können ein anderes bekommen.
    uv: eigene Ausschnitte je Fläche (sonst nach Lage im Block); drehung: {"angle", "axis", "origin"}."""
    t = {"von": von, "bis": bis, "mat": mat, "flaechen": flaechen}
    if uv:
        t["uv"] = uv
    if drehung:
        t["drehung"] = drehung
    return t


def gedreht(achse, winkel, ursprung):
    return {"angle": winkel, "axis": achse, "origin": ursprung}


def ring(y0, y1, a0, a1, dicke, mat):
    """Viereckiger Ring (z. B. Schwimmring, Kronleuchterreif) aus vier Kästen."""
    return [k([a0, y0, a0], [a1, y1, a0 + dicke], mat), k([a0, y0, a1 - dicke], [a1, y1, a1], mat),
            k([a0, y0, a0 + dicke], [a0 + dicke, y1, a1 - dicke], mat), k([a1 - dicke, y0, a0 + dicke], [a1, y1, a1 - dicke], mat)]


def kronleuchter():
    teile = [k([6, 15, 6], [10, 16, 10], "gold"), k([7.5, 6, 7.5], [8.5, 15, 8.5], "gold"),
             k([2, 6, 7.5], [14, 7, 8.5], "gold"), k([7.5, 6, 2], [8.5, 7, 14], "gold"),
             k([7, 2, 7], [9, 4, 9], "kristall")]
    teile += ring(4, 5, 2, 14, 1, "gold")
    for x, z in ((2, 2), (13, 2), (2, 13), (13, 13), (7.5, 2), (7.5, 13), (2, 7.5), (13, 7.5)):
        teile.append(k([x, 5, z], [x + 1, 8, z + 1], "kerze"))
    for x, z in ((4, 2), (11, 2), (4, 13), (11, 13), (2, 4), (2, 11), (13, 4), (13, 11)):
        teile.append(k([x, 2, z], [x + 1, 4, z + 1], "kristall"))
    return teile


def goldbarren():
    teile = []
    for x in (2, 6, 10):
        teile.append(k([x, 0, 3], [x + 4, 3, 13], "goldbarren"))
    for x in (4, 8):
        teile.append(k([x, 3, 3], [x + 4, 6, 13], "goldbarren"))
    teile.append(k([6, 6, 3], [10, 9, 13], "goldbarren"))
    return teile


def geldstapel():
    return [k([1, 0, 2], [8, 7, 14], "geld_seite", up="geld_oben"),
            k([8, 0, 2], [15, 4, 14], "geld_seite", up="geld_oben"),
            k([9, 4, 4], [14, 6, 13], "geld_seite", up="geld_oben")]


def pokal():
    return [k([4, 0, 4], [12, 2, 12], "gold"), k([5, 2, 5], [11, 4, 11], "schwarzglas"),
            k([7, 4, 7], [9, 8, 9], "gold"), k([5, 8, 5], [11, 14, 11], "gold"),
            k([3, 9, 7.5], [5, 13, 8.5], "gold"), k([11, 9, 7.5], [13, 13, 8.5], "gold")]


def kaffeemaschine():
    return [k([3, 0, 6], [13, 12, 14], "chrom", north="schwarzglas"), k([3, 0, 3], [13, 1, 6], "chrom"),
            k([6, 5, 3], [10, 6, 6], "schwarzglas"), k([6, 1, 3.5], [8, 3, 5.5], "keramik"),
            k([4, 12, 7], [12, 13, 13], "gold")]


def spuele():
    return [k([0, 0, 0], [16, 14, 16], "lack_weiss"), k([0, 14, 0], [16, 16, 16], "marmor"),
            k([7, 16, 12], [9, 24, 14], "gold"), k([7, 22, 7], [9, 24, 12], "gold")]


def dunstabzug():
    return [k([1, 0, 2], [15, 5, 16], "chrom", down="led"), k([5, 5, 9], [11, 16, 16], "chrom")]


def goldtoilette():
    return [k([4, 0, 2], [12, 7, 12], "gold"), k([3.5, 7, 1.5], [12.5, 8, 12], "gold"),
            k([3, 7, 12], [13, 15, 16], "gold"), k([7, 15, 13], [9, 16, 15], "keramik")]


def barhocker():
    return [k([4, 0, 4], [12, 1, 12], "gold"), k([7, 1, 7], [9, 9, 9], "gold"),
            k([5, 4, 5], [11, 5, 11], "gold"), k([4, 9, 4], [12, 11, 12], "samt_rot")]


def cocktail():
    return [k([7.5, 0, 7.5], [8.5, 3, 8.5], "glas"), k([6, 3, 6], [10, 6, 10], "cocktail"),
            k([9, 6, 8], [10, 9, 9], "holz_dunkel"), k([7, 9, 6], [12, 10, 11], "pink")]


def champagner():
    return [k([5, 0, 5], [11, 6, 11], "chrom"), k([7, 4, 7], [9, 13, 9], "champagner")]


def sektturm():
    return [k([1, 0, 1], [15, 4, 15], "sekt"), k([3, 4, 3], [13, 8, 13], "sekt"),
            k([5, 8, 5], [11, 12, 11], "sekt"), k([7, 12, 7], [9, 15, 9], "sekt")]


def flamingo():
    teile = ring(12, 15, 1, 15, 3, "pink")
    teile += [k([11, 15, 6], [13, 26, 8], "pink"), k([10, 24, 3], [14, 28, 7], "pink"),
              k([10.5, 24.5, 1], [13.5, 26.5, 3], "schnabel")]
    return teile


def neonschild(mat):
    return [k([0, 3, 15], [16, 13, 16], mat)]


def fluegel_tasten():
    """Vorderteil des Konzertflügels: Tastatur, Notenpult, Beine."""
    return [k([0, 6, 0], [16, 12, 16], "lack_schwarz"), k([0, 10, -3], [16, 11, 0], "lack_schwarz", up="fluegel_tasten"),
            k([1, 0, 1], [3, 6, 3], "lack_schwarz"), k([13, 0, 1], [15, 6, 3], "lack_schwarz"),
            k([2, 12, 6], [14, 17, 7], "lack_schwarz"), k([7, 0, 6], [9, 2, 10], "gold")]


def fluegel_korpus():
    """Hinterteil des Flügels: Korpus mit schräg geöffnetem Deckel."""
    return [k([0, 6, 0], [16, 12, 12], "lack_schwarz"), k([2, 6, 12], [14, 12, 15], "lack_schwarz"),
            k([7, 0, 9], [9, 6, 11], "lack_schwarz"),
            {"von": [0, 12, 0], "bis": [16, 13, 14], "mat": "lack_schwarz", "flaechen": {},
             "drehung": {"angle": 22.5, "axis": "x", "origin": [8, 12, 0]}},
            k([13, 12, 2], [14, 20, 3], "gold")]



def tastatur():
    """Schreibunterlage aus Leder, flache Tastatur mit Ziffernblock und Maus (rechts aus Sicht des Sitzenden,
    der von Norden auf den Tisch schaut). Tastatur und Maus nutzen die feine Bürotextur."""
    return [k([0, 0, 2.5], [16, 0.3, 13.5], "schreibunterlage"),
            k([3.5, 0.3, 6], [15.5, 1.0, 10.5], "lack_schwarz", uv={"up": [0, 0, 12, 4.5]}, up="buero_hd"),
            k([0.8, 0.3, 7], [2.6, 1.1, 10], "lack_schwarz", uv={"up": [0, 6, 1.75, 9]}, up="buero_hd"),
            k([1.1, 1.1, 7.6], [2.3, 1.4, 9.6], "lack_schwarz")]


def sofa(spalte, polster="leder_weiss", kissen="leder_weiss", deko_kissen=None):
    """Sofastück (Sitzrichtung Norden): Sockel, Sitzkissen, Rückenlehne mit geneigtem Rückenkissen,
    Armlehne nur am Ende (links = Westen aus Sicht des Sitzenden), goldene Füße an den Enden."""
    links, rechts = spalte in ("links", "einzeln"), spalte in ("rechts", "einzeln")
    x0, x1 = (3 if links else 0), (13 if rechts else 16)
    t = [k([0, 0, 2], [16, 1.5, 15], "lack_schwarz"),
         k([0, 1.5, 1], [16, 5, 16], polster),
         k([x0 + 0.2, 5, 1.2], [x1 - 0.2, 8, 12.5], kissen),
         k([0, 5, 12.5], [16, 13, 16], polster),
         k([x0 + 0.4, 8, 10.5], [x1 - 0.4, 14, 12.5], kissen, drehung=gedreht("x", 22.5, [8, 8, 10.5]))]
    if links:
        t += [k([0, 1.5, 1], [3, 11, 16], polster), k([0.5, 0, 1.5], [2.5, 1.5, 3.5], "gold"), k([0.5, 0, 13], [2.5, 1.5, 15], "gold")]
        if deko_kissen:
            t.append(k([3.2, 8, 7], [7.5, 12.5, 10], deko_kissen, drehung=gedreht("z", -22.5, [3.2, 8, 8])))
    if rechts:
        t += [k([13, 1.5, 1], [16, 11, 16], polster), k([13.5, 0, 1.5], [15.5, 1.5, 3.5], "gold"), k([13.5, 0, 13], [15.5, 1.5, 15], "gold")]
        if deko_kissen:
            t.append(k([8.5, 8, 7], [12.8, 12.5, 10], deko_kissen, drehung=gedreht("z", 22.5, [12.8, 8, 8])))
    return t


def _bett_rand(spalte, y0, y1, z0, z1, mat):
    """Seitlich herabhängende Decke bzw. Fell an den Außenkanten des Betts."""
    t = []
    if spalte in ("links", "einzeln"):
        t.append(k([-0.6, y0, z0], [0, y1, z1], mat))
    if spalte in ("rechts", "einzeln"):
        t.append(k([16, y0, z0], [16.6, y1, z1], mat))
    return t


def _bett_unterbau(spalte, z0, z1):
    """Schwarzer Lacksockel mit Goldkante und Matratze."""
    return [k([0, 0, z0], [16, 3, z1], "lack_schwarz"), k([0, 3, z0], [16, 3.5, z1], "gold"),
            k([0, 3.5, z0], [16, 9, z1], "matratze")] + _bett_rand(spalte, 3.6, 10.5, z0, z1, "satin_schwarz")


def bett_kopf(spalte):
    """Kopfteil des XXL-Betts (Kopf im Norden): Kissen, Zierkissen, umgeschlagenes Laken, Decke."""
    zier = "samt_rot" if spalte != "mitte" else "satin_gold"
    return _bett_unterbau(spalte, 0, 16) + [
        k([0, 9, 0], [16, 9.3, 8], "laken"),
        k([0.8, 9.3, 0.6], [15.2, 13.5, 6.8], "kissen_weiss"),
        k([2.5, 9.6, 5.6], [13.5, 15, 8], zier, drehung=gedreht("x", -22.5, [8, 9.6, 8])),
        k([0, 9, 8], [16, 10.5, 16], "satin_schwarz"),
        k([0, 10.5, 8], [16, 11, 10], "laken")]


def bett_fuss(spalte):
    """Mittelstück des XXL-Betts: Decke über die ganze Länge."""
    return _bett_unterbau(spalte, 0, 16) + [k([0, 9, 0], [16, 10.5, 16], "satin_schwarz")]


def bett_ende(spalte):
    """Fußende des XXL-Betts (Deko, nicht zum Schlafen): Decke fällt vorn herab, weißes Fell quer darüber."""
    return _bett_unterbau(spalte, 0, 15.4) + [
        k([0, 9, 0], [16, 10.5, 16], "satin_schwarz"), k([0, 3.6, 15.4], [16, 10.5, 16], "satin_schwarz"),
        k([0, 10.5, 3], [16, 11.6, 10.5], "fell")] + _bett_rand(spalte, 6, 11.6, 3, 10.5, "fell")


def bettbank(spalte):
    """Gepolsterte Bank am Bettende (Sitzrichtung Norden): Samt mit Goldknöpfen, Goldbeine an den Enden."""
    t = [k([0, 5, 3], [16, 9, 13], "samt_rot", up="samt_knoepfe"), k([0, 4, 3], [16, 5, 13], "gold")]
    if spalte in ("links", "einzeln"):
        t += [k([0.5, 0, 3.5], [2, 4, 5], "gold"), k([0.5, 0, 11], [2, 4, 12.5], "gold")]
    if spalte in ("rechts", "einzeln"):
        t += [k([14, 0, 3.5], [15.5, 4, 5], "gold"), k([14, 0, 11], [15.5, 4, 12.5], "gold")]
    return t


def nachttisch():
    return [k([1, 0, 1], [15, 13, 15], "lack_schwarz", north="nachttisch_front"), k([0.5, 13, 0.5], [15.5, 14, 15.5], "marmor")]


def tischlampe():
    return [k([5.5, 0, 5.5], [10.5, 1, 10.5], "gold"), k([7.5, 1, 7.5], [8.5, 8, 8.5], "gold"),
            k([3, 8, 4], [13, 15, 12], "lampenschirm"), k([4, 8, 3], [12, 15, 13], "lampenschirm"),
            k([7, 15, 7], [9, 15.5, 9], "gold")]


def kinotisch():
    """Goldener Beistelltisch am Kinosofa mit Popcorn-Tüte und zwei Drinks."""
    return [k([6, 0, 6], [10, 0.5, 10], "gold"), k([7.5, 0.5, 7.5], [8.5, 9, 8.5], "gold"),
            k([2, 9, 3], [14, 10, 13], "marmor"), k([3, 9, 2], [13, 10, 14], "marmor"),
            k([4, 10, 5], [9, 15, 10], "popcorn_tuete", up="popcorn"), k([4.5, 15, 5.5], [8.5, 16, 9.5], "popcorn"),
            k([10.5, 10, 4.5], [12.5, 13, 6.5], "cocktail"), k([10.5, 10, 9.5], [12.5, 14, 11.5], "champagner")]


def loungetisch():
    """Goldener Beistelltisch für die Liegen: Cocktail und Champagner im Eiskübel."""
    return [k([6, 0, 6], [10, 0.5, 10], "gold"), k([7.5, 0.5, 7.5], [8.5, 9, 8.5], "gold"),
            k([2, 9, 3], [14, 10, 13], "marmor"), k([3, 9, 2], [13, 10, 14], "marmor"),
            k([4, 10, 4], [6, 13, 6], "cocktail"), k([4, 13, 4], [6, 13.5, 6], "pink"),
            k([8.5, 10, 7.5], [12.5, 14, 11.5], "chrom"), k([9.8, 13, 8.8], [11.2, 18, 10.2], "champagner")]


def _liege_gestell(beine_z):
    t = [k([1.5, 3, 0], [3, 5, 16], "teak"), k([13, 3, 0], [14.5, 5, 16], "teak"), k([3, 3.6, 0], [13, 4.4, 16], "teak")]
    for z in beine_z:
        t += [k([1.5, 0, z], [3, 3, z + 1.5], "teak"), k([13, 0, z], [14.5, 3, z + 1.5], "teak")]
    return t


def liege_kopf():
    """Kopfteil der Strandliege (Blick nach Norden zum Fußteil): Teakgestell, hochgestellte Rückenlehne
    mit dickem Polster, Nackenrolle aus Handtuch."""
    lehne = gedreht("x", -45, [8, 4.5, 6])
    return _liege_gestell((13,)) + [
        k([2, 4.4, 0], [14, 7, 6.2], "polster_weiss"),
        k([3, 3.4, 6], [13, 4.6, 15.5], "teak", drehung=lehne),
        k([2, 4.6, 6], [14, 7, 15.5], "polster_weiss", drehung=lehne),
        k([3, 7, 12.5], [13, 8.6, 14.5], "handtuch", drehung=lehne)]


def liege_fuss():
    """Fußteil der Strandliege mit gefaltetem Handtuch."""
    return _liege_gestell((1.5,)) + [k([2, 4.4, 0.5], [14, 7, 16], "polster_weiss"),
                                     k([4, 7, 2], [12, 7.8, 7.5], "handtuch")]


def sonnenschirm():
    """Großer Sonnenschirm (Schirm 2,7 Blöcke breit, ab 1,75 Blöcken Höhe) auf Chromfuß."""
    return [k([4.5, 0, 4.5], [11.5, 1, 11.5], "chrom"), k([7.5, 1, 7.5], [8.5, 31, 8.5], "gold"),
            k([-13, 28, -5], [29, 29, 21], "schirm"), k([-5, 28, -13], [21, 29, 29], "schirm"),
            k([-9, 28, -9], [25, 29, 25], "schirm", drehung=gedreht("y", 45, [8, 28, 8])),
            k([-7, 29, -7], [23, 30, 23], "schirm_glatt"), k([0, 30, 0], [16, 31, 16], "schirm_glatt"),
            k([5, 31, 5], [11, 31.5, 11], "schirm_glatt"), k([7, 31.5, 7], [9, 32, 9], "gold")]


# Name: (Teile, Kollisionsform oder None = aus den Teilen, Licht, fest, Klang, Werkzeug, Deutsch, Englisch)
BLOECKE = {
    "tanzflaeche": ([k([0, 0, 0], [16, 16, 16], "schwarzglas", up="tanz")], None, 12, True, "glas", "pickaxe", "Tanzfläche", "Dance Floor"),
    "discokugel": ([k([7.5, 11, 7.5], [8.5, 16, 8.5], "chrom"), k([3, 3, 5], [13, 9, 11], "spiegel"),
                    k([5, 1, 5], [11, 11, 11], "spiegel"), k([5, 3, 3], [11, 9, 13], "spiegel"),
                    k([4, 2, 4], [12, 10, 12], "spiegel")], [[3, 1, 3, 13, 16, 13]], 10, False, "glas", "pickaxe", "Discokugel", "Disco Ball"),
    "kronleuchter": (kronleuchter(), [[2, 2, 2, 14, 16, 14]], 15, False, "kette", "pickaxe", "Kronleuchter", "Chandelier"),
    "flaschenregal": ([k([0, 0, 0], [16, 16, 16], "holz_dunkel", north="flaschen")], None, 0, True, "holz", "axe", "Flaschenregal", "Bottle Shelf"),
    "goldbarren": (goldbarren(), None, 0, True, "metall", "pickaxe", "Goldbarren", "Gold Bars"),
    "geldstapel": (geldstapel(), None, 0, True, "wolle", "axe", "Geldstapel", "Stacks of Cash"),
    "pokal": (pokal(), None, 0, True, "metall", "pickaxe", "Goldpokal", "Golden Trophy"),
    "kaffeemaschine": (kaffeemaschine(), None, 0, True, "metall", "pickaxe", "Kaffeemaschine", "Espresso Machine"),
    "herd": ([k([0, 0, 0], [16, 16, 16], "lack_weiss", north="ofen_front", up="herd_oben")], None, 0, True, "metall", "pickaxe", "Herd", "Stove"),
    "spuele": (spuele(), [[0, 0, 0, 16, 16, 16]], 0, True, "stein", "pickaxe", "Spüle", "Sink"),
    "kuehlschrank": ([k([0, 0, 0], [16, 16, 16], "chrom", north="kuehl_front")], None, 0, True, "metall", "pickaxe", "Kühlschrank", "Fridge"),
    "weinkuehlschrank": ([k([0, 0, 0], [16, 16, 16], "schwarzglas", north="weinkuehl_front")], None, 4, True, "glas", "pickaxe", "Weinkühlschrank", "Wine Cooler"),
    "dunstabzug": (dunstabzug(), None, 8, True, "metall", "pickaxe", "Dunstabzug", "Range Hood"),
    "goldtoilette": (goldtoilette(), None, 0, True, "metall", "pickaxe", "Goldene Toilette", "Golden Toilet"),
    "barhocker": (barhocker(), None, 0, True, "metall", "pickaxe", "Barhocker", "Bar Stool"),
    "cocktail": (cocktail(), [[6, 0, 6, 10, 10, 10]], 0, False, "glas", "pickaxe", "Cocktail", "Cocktail"),
    "champagner": (champagner(), None, 0, True, "glas", "pickaxe", "Champagner im Eiskübel", "Champagne on Ice"),
    "sektturm": (sektturm(), None, 0, True, "glas", "pickaxe", "Sektturm", "Champagne Tower"),
    "flamingo": (flamingo(), [[1, 12, 1, 15, 15, 15]], 0, False, "wolle", "axe", "Flamingo-Schwimmring", "Flamingo Float"),
    "neon_bar": (neonschild("neon_bar"), None, 10, False, "glas", "pickaxe", "Leuchtschrift BAR", "Neon Sign BAR"),
    "neon_vip": (neonschild("neon_vip"), None, 10, False, "glas", "pickaxe", "Leuchtschrift VIP", "Neon Sign VIP"),
    "neon_club": (neonschild("neon_club"), None, 10, False, "glas", "pickaxe", "Leuchtschrift CLUB", "Neon Sign CLUB"),
    "kopfteil": ([k([0, 0, 0], [16, 16, 16], "samt_rot", north="samt_knoepfe")], None, 0, True, "wolle", "axe", "Gepolstertes Kopfteil", "Tufted Headboard"),
    "tastatur": (tastatur(), [[0, 0, 2.5, 16, 1.4, 13.5]], 0, False, "stein", "pickaxe", "Tastatur, Maus und Schreibunterlage", "Keyboard, Mouse and Desk Mat"),
    "nachttisch": (nachttisch(), [[1, 0, 1, 15, 14, 15]], 0, True, "holz", "axe", "Nachttisch", "Nightstand"),
    "tischlampe": (tischlampe(), [[3, 0, 3, 13, 15.5, 13]], 14, True, "glas", "pickaxe", "Tischlampe", "Table Lamp"),
    "kinotisch": (kinotisch(), [[2, 0, 2, 14, 10, 14]], 0, True, "metall", "pickaxe", "Kinotisch mit Popcorn", "Movie Side Table"),
    "loungetisch": (loungetisch(), [[2, 0, 2, 14, 10, 14]], 0, True, "metall", "pickaxe", "Loungetisch", "Lounge Side Table"),
    "liege_kopf": (liege_kopf(), [[1.5, 0, 0, 14.5, 7, 16]], 0, True, "holz", "axe", "Strandliege (Kopfteil)", "Sun Lounger (Head)"),
    "liege_fuss": (liege_fuss(), [[1.5, 0, 0, 14.5, 7, 16]], 0, True, "holz", "axe", "Strandliege (Fußteil)", "Sun Lounger (Foot)"),
    "sonnenschirm": (sonnenschirm(), [[7, 0, 7, 9, 16, 9]], 0, True, "wolle", "axe", "Sonnenschirm", "Parasol"),
    "fluegel_tasten": (fluegel_tasten(), [[0, 0, 0, 16, 12, 16]], 0, True, "holz", "axe", "Konzertflügel (Tasten)", "Grand Piano (Keys)"),
    "fluegel_korpus": (fluegel_korpus(), [[0, 0, 0, 16, 13, 15]], 0, True, "holz", "axe", "Konzertflügel (Korpus)", "Grand Piano (Body)"),
}

# Sitzhöhe in Pixeln für Sitzmöbel (Rechtsklick setzt sich drauf)
SITZE = {"liege_kopf": 7}

# Möbel, die sich mit Nachbarn verbinden: Name → (Teile je Spalte, Form, Licht, fest, Klang, Werkzeug,
# Deutsch, Englisch, Sitzhöhe oder None)
SPALTEN = ("links", "mitte", "rechts", "einzeln")
VERBUND = {
    "sofa_weiss": (lambda sp: sofa(sp), [[0, 0, 1, 16, 8, 16], [0, 8, 12, 16, 13, 16]], 0, True, "wolle", "axe",
                   "Ledersofa (weiß)", "Leather Sofa (White)", 8),
    "sofa_samt": (lambda sp: sofa(sp, "samt_rot", "samt_rot", "satin_gold"), [[0, 0, 1, 16, 8, 16], [0, 8, 12, 16, 13, 16]], 0, True,
                  "wolle", "axe", "Samtsofa (rot)", "Velvet Sofa (Red)", 8),
    "sofa_schwarz": (lambda sp: sofa(sp, "samt_schwarz", "samt_schwarz", "samt_rot"), [[0, 0, 1, 16, 8, 16], [0, 8, 12, 16, 13, 16]], 0, True,
                     "wolle", "axe", "Samtsofa (schwarz)", "Velvet Sofa (Black)", 8),
    "bett_ende": (bett_ende, [[0, 0, 0, 16, 11, 16]], 0, True, "wolle", "axe", "Kingsize-Bett (Fußende)", "King-Size Bed (Foot End)", None),
    "bettbank": (bettbank, [[0, 0, 3, 16, 9, 13]], 0, True, "wolle", "axe", "Bettbank", "Bed Bench", 9),
}

FLAECHEN = ("north", "south", "east", "west", "up", "down")


def tex(mat: str) -> str:
    return f"{NS}:block/deko/{mat}"


def uv_fuer(flaeche: str, von, bis):
    """Ausschnitt aus der Materialtextur wie bei Minecraft-Würfeln (auf 0…16 begrenzt)."""
    x0, y0, z0 = von
    x1, y1, z1 = bis
    w = {"north": (16 - x1, 16 - y1, 16 - x0, 16 - y0), "south": (x0, 16 - y1, x1, 16 - y0),
         "west": (z0, 16 - y1, z1, 16 - y0), "east": (16 - z1, 16 - y1, 16 - z0, 16 - y0),
         "up": (x0, z0, x1, z1), "down": (x0, 16 - z1, x1, 16 - z0)}[flaeche]
    return [max(0.0, min(16.0, round(v, 3))) for v in w]


def modell(teile) -> dict:
    mats = sorted({t["mat"] for t in teile} | {m for t in teile for m in t["flaechen"].values()})
    elemente = []
    for t in teile:
        e = {"from": t["von"], "to": t["bis"], "faces": {}}
        for f in FLAECHEN:
            mat = t["flaechen"].get(f, t["mat"])
            uv = list(t["uv"][f]) if f in t.get("uv", {}) else uv_fuer(f, t["von"], t["bis"])
            if uv[0] == uv[2] or uv[1] == uv[3]:
                uv = [0, 0, 16, 16] if f in ("up", "down") else uv
                if uv[0] == uv[2]:
                    uv[2] = min(16.0, uv[0] + 1)
                if uv[1] == uv[3]:
                    uv[3] = min(16.0, uv[1] + 1)
            e["faces"][f] = {"uv": uv, "texture": f"#{mat}"}
        if "drehung" in t:
            e["rotation"] = t["drehung"]
        elemente.append(e)
    texturen = {m: tex(m) for m in mats}
    texturen["particle"] = tex(teile[0]["flaechen"].get("north", teile[0]["mat"]))
    return {"parent": "minecraft:block/block", "textures": texturen, "elements": elemente}


def form_aus_teilen(teile):
    kaesten = []
    for t in teile:
        if "drehung" in t:
            continue
        x0, y0, z0 = (max(0.0, min(16.0, v)) for v in t["von"])
        x1, y1, z1 = (max(0.0, min(16.0, v)) for v in t["bis"])
        if x1 > x0 and y1 > y0 and z1 > z0:
            kaesten.append([x0, y0, z0, x1, y1, z1])
    return kaesten


def schreibe_json(pfad: Path, daten) -> None:
    pfad.parent.mkdir(parents=True, exist_ok=True)
    pfad.write_text(json.dumps(daten, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def kingsize_bett(de: dict, en: dict) -> None:
    """XXL-Kingsize-Bett (KingsizeBettBlock): Modelle je Teil und Spalte, Blockzustände, Beute nur vom Kopfteil."""
    varianten = {}
    for teil, fabrik in (("head", bett_kopf), ("foot", bett_fuss)):
        for sp in SPALTEN:
            schreibe_json(ASSETS / "models/block/deko" / f"bett_{teil}_{sp}.json", modell(fabrik(sp)))
            for richtung, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
                varianten[f"facing={richtung},part={teil},spalte={sp}"] = {"model": f"{NS}:block/deko/bett_{teil}_{sp}", **({"y": y} if y else {})}
    schreibe_json(ASSETS / "blockstates/kingsize_bett.json", {"variants": varianten})
    schreibe_json(ASSETS / "models/item/kingsize_bett.json", {"parent": f"{NS}:block/deko/bett_head_einzeln"})
    schreibe_json(ASSETS / "items/kingsize_bett.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/kingsize_bett"}})
    schreibe_json(RES / f"data/{NS}/loot_table/blocks/kingsize_bett.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:kingsize_bett",
                                            "conditions": [{"condition": "minecraft:block_state_property", "block": f"{NS}:kingsize_bett",
                                                            "properties": {"part": "head"}}]}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    de[f"block.{NS}.kingsize_bett"], en[f"block.{NS}.kingsize_bett"] = "XXL-Kingsize-Bett", "XXL King-Size Bed"


def main() -> dict:
    TEX.mkdir(parents=True, exist_ok=True)
    for alt in TEX.glob("*"):
        alt.unlink()
    for name, fabrik in MATERIALIEN.items():
        fabrik().save(TEX / f"{name}.png")
    for name, (fabrik, takt) in ANIMIERT.items():
        bilder = fabrik()
        streifen = Image.new("RGBA", (16, 16 * len(bilder)))
        for i, b in enumerate(bilder):
            streifen.paste(b, (0, 16 * i))
        streifen.save(TEX / f"{name}.png")
        schreibe_json(TEX / f"{name}.png.mcmeta", {"animation": {"frametime": takt}})

    liste = []
    werkzeuge = {"pickaxe": [], "axe": []}
    de, en = {}, {}
    for name, (teile, form, licht, fest, klang, werkzeug, name_de, name_en) in BLOECKE.items():
        schreibe_json(ASSETS / "models/block/deko" / f"{name}.json", modell(teile))
        modell_id = f"{NS}:block/deko/{name}"
        varianten = {}
        for richtung, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            varianten[f"facing={richtung}"] = {"model": modell_id, **({"y": y} if y else {})}
        schreibe_json(ASSETS / "blockstates" / f"{name}.json", {"variants": varianten})
        schreibe_json(ASSETS / "models/item" / f"{name}.json", {"parent": modell_id})
        schreibe_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": modell_id}})
        schreibe_json(RES / f"data/{NS}/loot_table/blocks/{name}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
        eintrag = {"name": name, "form": form or form_aus_teilen(teile), "licht": licht, "fest": fest, "klang": klang}
        if name in SITZE:
            eintrag["sitz"] = SITZE[name]
        liste.append(eintrag)
        werkzeuge[werkzeug].append(f"{NS}:{name}")
        de[f"block.{NS}.{name}"], en[f"block.{NS}.{name}"] = name_de, name_en
    for name, (fabrik, form, licht, fest, klang, werkzeug, name_de, name_en, sitz) in VERBUND.items():
        varianten = {}
        for sp in SPALTEN:
            schreibe_json(ASSETS / "models/block/deko" / f"{name}_{sp}.json", modell(fabrik(sp)))
            for richtung, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
                varianten[f"facing={richtung},spalte={sp}"] = {"model": f"{NS}:block/deko/{name}_{sp}", **({"y": y} if y else {})}
        schreibe_json(ASSETS / "blockstates" / f"{name}.json", {"variants": varianten})
        modell_id = f"{NS}:block/deko/{name}_einzeln"
        schreibe_json(ASSETS / "models/item" / f"{name}.json", {"parent": modell_id})
        schreibe_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": modell_id}})
        schreibe_json(RES / f"data/{NS}/loot_table/blocks/{name}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
        eintrag = {"name": name, "form": form, "licht": licht, "fest": fest, "klang": klang, "verbund": True}
        if sitz is not None:
            eintrag["sitz"] = sitz
        liste.append(eintrag)
        werkzeuge[werkzeug].append(f"{NS}:{name}")
        de[f"block.{NS}.{name}"], en[f"block.{NS}.{name}"] = name_de, name_en
    kingsize_bett(de, en)
    schreibe_json(RES / "deko_bloecke.json", liste)
    print(f"{len(BLOECKE) + len(VERBUND)} Deko-Blöcke, {len(MATERIALIEN) + len(ANIMIERT)} Materialien geschrieben")
    return {"werkzeuge": werkzeuge, "de": de, "en": en}


if __name__ == "__main__":
    main()
