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


def m_lack_schwarz():
    img, d = neu((14, 14, 18))
    for i in range(5):
        d.point((2 + i, 8 - i), fill=(80, 80, 92))
    return img


MATERIALIEN = {
    "gold": m_gold, "chrom": m_chrom, "samt_rot": m_samt_rot, "holz_dunkel": m_holz_dunkel, "marmor": m_marmor,
    "lack_weiss": m_lack_weiss, "glas": m_glas, "schwarzglas": m_schwarzglas, "keramik": m_keramik, "pink": m_pink,
    "schnabel": m_schnabel, "led": m_led, "kerze": m_kerze, "kristall": m_kristall, "goldbarren": m_goldbarren,
    "geld_oben": m_geld_oben, "geld_seite": m_geld_seite, "flaschen": m_flaschen, "herd_oben": m_herd_oben,
    "ofen_front": m_ofen_front, "kuehl_front": m_kuehl_front, "weinkuehl_front": m_weinkuehl_front,
    "champagner": m_champagner, "cocktail": m_cocktail, "sekt": m_sekt, "fluegel_tasten": m_fluegel_tasten,
    "lack_schwarz": m_lack_schwarz,
    "neon_bar": lambda: neon("BAR", (255, 40, 160)), "neon_vip": lambda: neon("VIP", (255, 200, 40)),
    "neon_club": lambda: neon("CLUB", (40, 220, 255)),
}
ANIMIERT = {"spiegel": (m_spiegel, 5), "tanz": (m_tanz, 8)}

# ---------------------------------------------------------------------------
# Modelle: Kästen in Pixeln, Vorderseite nach Norden
# ---------------------------------------------------------------------------


def k(von, bis, mat, **flaechen):
    """Kasten mit einem Material; einzelne Flächen (north, up …) können ein anderes bekommen."""
    return {"von": von, "bis": bis, "mat": mat, "flaechen": flaechen}


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
    "fluegel_tasten": (fluegel_tasten(), [[0, 0, 0, 16, 12, 16]], 0, True, "holz", "axe", "Konzertflügel (Tasten)", "Grand Piano (Keys)"),
    "fluegel_korpus": (fluegel_korpus(), [[0, 0, 0, 16, 13, 15]], 0, True, "holz", "axe", "Konzertflügel (Korpus)", "Grand Piano (Body)"),
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
            uv = uv_fuer(f, t["von"], t["bis"])
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
        liste.append({"name": name, "form": form or form_aus_teilen(teile), "licht": licht, "fest": fest, "klang": klang})
        werkzeuge[werkzeug].append(f"{NS}:{name}")
        de[f"block.{NS}.{name}"], en[f"block.{NS}.{name}"] = name_de, name_en
    schreibe_json(RES / "deko_bloecke.json", liste)
    print(f"{len(BLOECKE)} Deko-Blöcke, {len(MATERIALIEN) + len(ANIMIERT)} Materialien geschrieben")
    return {"werkzeuge": werkzeuge, "de": de, "en": en}


if __name__ == "__main__":
    main()
