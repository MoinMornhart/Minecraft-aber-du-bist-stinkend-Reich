#!/usr/bin/env python3
"""Erzeugt Aussehen und Datendateien aller Türen und Tore der Villa (eigene Pixelgrafik, nichts kopiert).

Zwei Arten:

1. Türen zum Aufklappen (Prunktür, Glastür, Stahltür, Bunkertür): verhalten sich wie Türen aus dem Spiel
   und nutzen dessen Türformen (minecraft:block/door_*). Hier entstehen nur Texturen (unten, oben, Item),
   Blockzustände, Item-Dateien und Beute-Tabellen.

2. Torflügel (Tor, Garagentor, Rollladen, Schiebetür, Panzertür, Tresortür, Gitterfalle, Poolabdeckung,
   Geheimregal): Jeder Block ist ein Feld einer größeren Fläche. Er kennt seine Spalte (links, mitte, rechts,
   einzeln) und Zeile (oben, mitte, unten, einzeln). Pro Typ gibt es ein Texturblatt 64×64 mit 4×4 Feldern;
   das Modell nimmt das passende Feld, so sitzen Rahmen, Griffe und Riegel an den richtigen Stellen.
   Beim Öffnen läuft die Stufe 0 → 3; die Modelle der Stufen 1 und 2 zeigen das Feld verschoben
   (je Typ zur Seite, nach oben oder nach unten), Stufe 3 ist leer.

Wird von tools/assets/assets.py aufgerufen (dort kommen Sprachdateien und Werkzeug-Listen dazu).
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

# ---------------------------------------------------------------------------
# Farben
# ---------------------------------------------------------------------------
GOLD = (230, 184, 50)
GOLD_HELL = (255, 228, 122)
GOLD_DUNKEL = (160, 112, 22)
CHROM = (214, 220, 228)
CHROM_DUNKEL = (118, 124, 134)
LEER = (0, 0, 0, 0)


def rgba(c, a=255):
    return c if len(c) == 4 else (*c, a)


def heller(c, n):
    return tuple(max(0, min(255, v + n)) for v in c[:3])


def rauschen(img: Image.Image, staerke: int, saat: int, nur_deckend: bool = True) -> Image.Image:
    rnd = random.Random(saat)
    px = img.load()
    for x in range(img.width):
        for y in range(img.height):
            r, g, b, a = px[x, y]
            if nur_deckend and a == 0:
                continue
            d = rnd.randint(-staerke, staerke)
            px[x, y] = (max(0, min(255, r + d)), max(0, min(255, g + d)), max(0, min(255, b + d)), a)
    return img


def leinwand(w: int, h: int, farbe=LEER) -> tuple[Image.Image, ImageDraw.ImageDraw]:
    img = Image.new("RGBA", (w, h), rgba(farbe))
    return img, ImageDraw.Draw(img)


def niete(d: ImageDraw.ImageDraw, x: int, y: int, hell=(206, 210, 216), dunkel=(52, 54, 58)) -> None:
    d.point((x, y), fill=rgba(hell))
    d.point((x + 1, y + 1), fill=rgba(dunkel))


def warnstreifen(d: ImageDraw.ImageDraw, x0: int, y0: int, x1: int, y1: int) -> None:
    """Gelb-schwarze Schrägstreifen (Gefahrenzone)."""
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            d.point((x, y), fill=rgba((236, 186, 24) if (x + y) % 6 < 3 else (24, 24, 26)))


def griffstange(d: ImageDraw.ImageDraw, x: int, y0: int, y1: int) -> None:
    """Senkrechte Griffstange aus Gold mit Glanzkante."""
    d.line([(x, y0), (x, y1)], fill=rgba(GOLD))
    d.line([(x + 1, y0), (x + 1, y1)], fill=rgba(GOLD_DUNKEL))
    d.point((x, y0), fill=rgba(GOLD_HELL))
    d.point((x, y1), fill=rgba(GOLD_DUNKEL))


# ---------------------------------------------------------------------------
# Türen zum Aufklappen: 16×32 (oben + unten), Scharniere links, Griff rechts (wie im Spiel)
# ---------------------------------------------------------------------------


def tuer_prunk() -> Image.Image:
    """Weiß lackiert, vier vertiefte Kassetten mit Goldleisten, Goldkrone oben, Goldgriff und Trittblech."""
    img, d = leinwand(16, 32, (238, 234, 224))
    rauschen(img, 3, 31)
    weiss_schatten = (204, 198, 186)
    # Rahmen des Türblatts: helle Kante links/oben, Schatten rechts/unten
    d.line([(0, 0), (0, 31)], fill=rgba((250, 248, 242)))
    d.line([(15, 0), (15, 31)], fill=rgba(weiss_schatten))
    d.line([(0, 0), (15, 0)], fill=rgba((250, 248, 242)))
    # Kassetten mit Goldleisten: oben eine hohe mit Bogen, darunter zwei, unten eine
    for (x0, y0, x1, y1) in ((3, 3, 12, 13), (3, 17, 12, 24), (3, 26, 12, 28)):
        d.rectangle([x0, y0, x1, y1], fill=rgba((228, 222, 210)))
        d.rectangle([x0, y0, x1, y1], outline=rgba(GOLD))
        d.line([(x0, y0), (x1, y0)], fill=rgba(GOLD_HELL))
        d.line([(x1, y0 + 1), (x1, y1)], fill=rgba(GOLD_DUNKEL))
        d.line([(x0 + 1, y1), (x1, y1)], fill=rgba(GOLD_DUNKEL))
    # Bogen in der oberen Kassette und kleine Krone
    for x, y in ((4, 5), (5, 4), (10, 4), (11, 5)):
        d.point((x, y), fill=rgba(GOLD))
    for x, y in ((6, 8), (8, 7), (10, 8), (6, 9), (7, 9), (8, 9), (9, 9), (10, 9), (7, 8), (9, 8)):
        d.point((x, y), fill=rgba(GOLD))
    d.point((8, 6), fill=rgba(GOLD_HELL))
    d.line([(6, 10), (10, 10)], fill=rgba(GOLD_DUNKEL))
    # Scharniere (links) und Goldgriff mit Rosette (rechts, Höhe der Teilung)
    for y in (3, 4, 26, 27):
        d.point((0, y), fill=rgba(GOLD))
    d.rectangle([12, 14, 13, 18], fill=rgba(GOLD))
    d.point((12, 14), fill=rgba(GOLD_HELL))
    d.line([(10, 15), (13, 15)], fill=rgba(GOLD_HELL))
    d.point((10, 16), fill=rgba(GOLD_DUNKEL))
    d.point((13, 18), fill=rgba(GOLD_DUNKEL))
    # Trittblech aus Gold
    d.rectangle([0, 30, 15, 31], fill=rgba(GOLD))
    d.line([(0, 30), (15, 30)], fill=rgba(GOLD_HELL))
    return img


def tuer_glas() -> Image.Image:
    """Ganzglastür im schmalen Goldrahmen, Quersprosse, lange Griffstange."""
    img, d = leinwand(16, 32, LEER)
    # Glasfläche durchsichtig, nur Lichtreflexe
    for x, y in ((3, 4), (4, 3), (3, 20), (4, 19), (5, 18), (11, 11), (12, 10), (11, 27), (12, 26)):
        d.point((x, y), fill=rgba((236, 246, 250)))
    # Rahmen
    d.rectangle([0, 0, 15, 31], outline=rgba(GOLD))
    d.rectangle([1, 1, 14, 30], outline=rgba(GOLD_DUNKEL))
    d.line([(0, 0), (15, 0)], fill=rgba(GOLD_HELL))
    d.line([(0, 0), (0, 31)], fill=rgba(GOLD_HELL))
    d.rectangle([0, 29, 15, 31], fill=rgba(GOLD))
    d.line([(1, 29), (14, 29)], fill=rgba(GOLD_HELL))
    # Quersprosse in Höhe der Teilung
    d.line([(1, 15), (14, 15)], fill=rgba(GOLD))
    d.line([(1, 16), (14, 16)], fill=rgba(GOLD_DUNKEL))
    griffstange(d, 12, 9, 22)
    return img


def tuer_stahl() -> Image.Image:
    """Gebürsteter Stahl mit Nieten, rundes Bullauge, Hebelgriff, Warnstreifen unten."""
    img, d = leinwand(16, 32, (128, 132, 138))
    for y in range(32):
        d.line([(0, y), (15, y)], fill=rgba(heller((128, 132, 138), 6 if y % 3 == 0 else (-4 if y % 3 == 2 else 0))))
    rauschen(img, 3, 33)
    d.rectangle([0, 0, 15, 31], outline=rgba((86, 90, 96)))
    for x in (2, 13):
        for y in (2, 9, 16, 23):
            niete(d, x, y)
    # Bullauge mit Chromring
    d.ellipse([4, 3, 11, 10], fill=rgba((34, 52, 70)), outline=rgba(CHROM))
    d.point((6, 5), fill=rgba((150, 190, 220)))
    d.point((7, 5), fill=rgba((110, 150, 180)))
    # Verstärkungsrippe und Hebelgriff
    d.line([(1, 16), (14, 16)], fill=rgba((100, 104, 110)))
    d.line([(1, 17), (14, 17)], fill=rgba((150, 154, 160)))
    d.rectangle([12, 14, 13, 15], fill=rgba((60, 62, 66)))
    d.line([(9, 15), (13, 15)], fill=rgba(CHROM))
    d.line([(9, 16), (13, 16)], fill=rgba(CHROM_DUNKEL))
    warnstreifen(d, 1, 27, 14, 30)
    return img


def tuer_bunker() -> Image.Image:
    """Dunkler Panzerstahl: Handrad, zwei schwere Riegel mit Führungen, Schloss, Warnstreifen."""
    img, d = leinwand(16, 32, (62, 66, 72))
    rauschen(img, 3, 34)
    d.rectangle([0, 0, 15, 31], outline=rgba((36, 38, 42)))
    d.rectangle([1, 1, 14, 30], outline=rgba((84, 88, 94)))
    for x in (2, 13):
        for y in (2, 7, 12, 19, 24):
            niete(d, x, y)
    # Handrad oben mittig
    d.ellipse([4, 4, 11, 11], outline=rgba(CHROM))
    d.line([(7, 4), (7, 11)], fill=rgba(CHROM_DUNKEL))
    d.line([(4, 7), (11, 7)], fill=rgba(CHROM_DUNKEL))
    d.rectangle([7, 7, 8, 8], fill=rgba((220, 60, 50)))
    # Zwei Riegel: Stange von der Mitte bis über die Kante (rechts), mit Führungsbügeln
    for y in (14, 21):
        d.rectangle([6, y, 15, y + 1], fill=rgba(CHROM))
        d.line([(6, y + 1), (15, y + 1)], fill=rgba(CHROM_DUNKEL))
        for x in (8, 12):
            d.rectangle([x, y - 1, x, y + 2], fill=rgba((30, 32, 36)))
        d.rectangle([4, y - 1, 5, y + 2], fill=rgba((180, 140, 40)))   # Riegelknauf
    # Schlosskasten mit Schlüsselloch
    d.rectangle([9, 16, 13, 19], fill=rgba((40, 42, 46)), outline=rgba((110, 114, 120)))
    d.point((11, 17), fill=rgba((0, 0, 0)))
    d.point((11, 18), fill=rgba((0, 0, 0)))
    warnstreifen(d, 2, 27, 13, 29)
    return img


def tuer_privat() -> Image.Image:
    """Privattür: schwarzer Lack mit Goldkanten, Fingerabdruck-Scanner mit grüner Leuchte, Schild „PRIVAT“."""
    img, d = leinwand(16, 32, (26, 26, 30))
    rauschen(img, 2, 35)
    d.rectangle([0, 0, 15, 31], outline=rgba(GOLD))
    d.line([(0, 0), (15, 0)], fill=rgba(GOLD_HELL))
    d.line([(0, 0), (0, 31)], fill=rgba(GOLD_HELL))
    # Zwei tiefe Kassetten mit dünner Goldleiste
    for (x0, y0, x1, y1) in ((3, 3, 12, 13), (3, 18, 12, 28)):
        d.rectangle([x0, y0, x1, y1], fill=rgba((18, 18, 22)), outline=rgba(GOLD_DUNKEL))
    # Schild „PRIVAT“ als goldener Balken mit dunklen Strichen
    d.rectangle([4, 6, 11, 8], fill=rgba(GOLD))
    for x in (5, 7, 9):
        d.point((x, 7), fill=rgba((40, 30, 10)))
    # Fingerabdruck-Scanner neben dem Griff: Glasfeld mit grüner Leuchte
    d.rectangle([11, 14, 14, 17], fill=rgba((30, 40, 46)), outline=rgba(CHROM))
    d.point((12, 15), fill=rgba((80, 240, 120)))
    d.point((13, 16), fill=rgba((60, 200, 100)))
    d.rectangle([12, 19, 13, 22], fill=rgba(GOLD))
    d.point((12, 19), fill=rgba(GOLD_HELL))
    d.rectangle([0, 30, 15, 31], fill=rgba(GOLD))
    return img


def tuer_sicherheit() -> Image.Image:
    """Sicherheitstür fürs Büro: dunkles, gebürstetes Metall, Goldkante, senkrechter LED-Streifen, Kartenschlitz,
    Schild „SECURITY“ als Strich. Öffnet nur über den Kartenleser."""
    img, d = leinwand(16, 32, (44, 46, 52))
    for y in range(32):
        d.line([(0, y), (15, y)], fill=rgba(heller((44, 46, 52), 5 if y % 3 == 0 else 0)))
    rauschen(img, 2, 36)
    d.rectangle([0, 0, 15, 31], outline=rgba(GOLD))
    d.line([(0, 0), (15, 0)], fill=rgba(GOLD_HELL))
    d.rectangle([2, 3, 13, 28], outline=rgba((70, 72, 80)))
    for y in range(4, 28):
        d.point((3, y), fill=rgba((70, 210, 255) if y % 2 == 0 else (40, 150, 200)))
    d.rectangle([10, 13, 13, 18], fill=rgba((20, 20, 24)), outline=rgba(CHROM))
    d.line([(11, 15), (12, 15)], fill=rgba((0, 0, 0)))
    d.point((11, 17), fill=rgba((80, 240, 120)))
    d.line([(6, 6), (11, 6)], fill=rgba(GOLD))
    d.rectangle([0, 30, 15, 31], fill=rgba(GOLD_DUNKEL))
    return img


def tuer_club() -> Image.Image:
    """Clubtür: schwarzes, gestepptes Leder mit goldenen Knöpfen, rundes Bullauge mit lila Leuchtring, Goldgriff."""
    img, d = leinwand(16, 32, (22, 18, 26))
    for y in range(2, 30, 4):
        for x in range(2 + (y // 4) % 2 * 2, 15, 4):
            d.point((x, y), fill=rgba(GOLD))
            d.point((x, y + 1), fill=rgba((40, 34, 46)))
    d.rectangle([0, 0, 15, 31], outline=rgba(GOLD_DUNKEL))
    d.ellipse([4, 3, 11, 10], fill=rgba((40, 10, 60)), outline=rgba((200, 80, 255)))
    d.point((6, 5), fill=rgba((230, 180, 255)))
    d.rectangle([12, 14, 13, 19], fill=rgba(GOLD))
    d.point((12, 14), fill=rgba(GOLD_HELL))
    d.rectangle([0, 30, 15, 31], fill=rgba(GOLD))
    return img


def tuer_bank() -> Image.Image:
    """Banktür: dicker, polierter Tresorstahl, goldenes Handrad mit Speichen, Schild „BANK“, Riegel zur Kante,
    Kartenschlitz mit grüner Leuchte. Öffnet nur über den Kartenleser und bleibt offen, bis man sie schließt."""
    img, d = leinwand(16, 32, (150, 156, 166))
    for y in range(32):
        d.line([(0, y), (15, y)], fill=rgba(heller((150, 156, 166), 8 if y % 4 == 0 else (-6 if y % 4 == 2 else 0))))
    rauschen(img, 3, 37)
    d.rectangle([0, 0, 15, 31], outline=rgba((70, 74, 82)))
    d.rectangle([1, 1, 14, 30], outline=rgba(GOLD_DUNKEL))
    # Schild „BANK“ oben als Goldplatte mit dunklen Buchstabenstrichen
    d.rectangle([3, 2, 12, 5], fill=rgba(GOLD))
    d.line([(3, 2), (12, 2)], fill=rgba(GOLD_HELL))
    for x in (4, 6, 9, 11):
        d.line([(x, 3), (x, 4)], fill=rgba((60, 40, 10)))
    # Goldenes Handrad mit vier Speichen
    d.ellipse([3, 8, 12, 17], outline=rgba(GOLD))
    d.line([(7, 8), (7, 17)], fill=rgba(GOLD_DUNKEL))
    d.line([(8, 8), (8, 17)], fill=rgba(GOLD))
    d.line([(3, 12), (12, 12)], fill=rgba(GOLD))
    d.line([(3, 13), (12, 13)], fill=rgba(GOLD_DUNKEL))
    d.rectangle([6, 11, 9, 14], fill=rgba(GOLD_HELL), outline=rgba(GOLD_DUNKEL))
    # Bolzen rundum
    for x in (2, 13):
        for y in (8, 13, 19, 25):
            niete(d, x, y)
    # Zwei Riegel zur Schlossseite
    for y in (20, 24):
        d.rectangle([5, y, 15, y + 1], fill=rgba(CHROM))
        d.line([(5, y + 1), (15, y + 1)], fill=rgba(CHROM_DUNKEL))
    # Kartenschlitz mit Leuchte
    d.rectangle([9, 15, 12, 18], fill=rgba((24, 24, 28)), outline=rgba(GOLD))
    d.point((10, 17), fill=rgba((80, 240, 120)))
    d.rectangle([0, 29, 15, 31], fill=rgba(GOLD))
    d.line([(0, 29), (15, 29)], fill=rgba(GOLD_HELL))
    return img


def item_aus_tuer(tuer: Image.Image) -> Image.Image:
    """Item-Bild: Tür auf 10×16 verkleinert, mittig."""
    klein = tuer.resize((10, 16), Image.NEAREST)
    img = Image.new("RGBA", (16, 16), LEER)
    img.alpha_composite(klein, (3, 0))
    return img


AUFKLAPPTUEREN = {
    # name: (Zeichner, Werkzeug, Deutsch, Englisch)
    "prunktuer": (tuer_prunk, "axe", "Prunktür", "Grand Door"),
    "glastuer": (tuer_glas, "axe", "Glastür", "Glass Door"),
    "stahltuer": (tuer_stahl, "pickaxe", "Stahltür", "Steel Door"),
    "bunkertuer": (tuer_bunker, "pickaxe", "Bunkertür", "Bunker Door"),
    "privattuer": (tuer_privat, "axe", "Privattür (nur Bewohner)", "Private Door (residents only)"),
    "sicherheitstuer": (tuer_sicherheit, "pickaxe", "Sicherheitstür (Zugangskarte)", "Security Door (key card)"),
    "clubtuer": (tuer_club, "axe", "Clubtür", "Club Door"),
    "banktuer": (tuer_bank, "pickaxe", "Banktür (Zugangskarte)", "Bank Door (key card)"),
}


def tuer_blockzustand(name: str) -> dict:
    """Wie die Holztür im Spiel: Richtung × Hälfte × Scharnier × offen."""
    v = {}
    drehung = {"east": 0, "south": 90, "west": 180, "north": 270}
    for f, y in drehung.items():
        for haelfte, teil in (("lower", "bottom"), ("upper", "top")):
            for scharnier in ("left", "right"):
                for offen in (False, True):
                    modell = f"{NS}:block/{name}_{teil}_{scharnier}" + ("_open" if offen else "")
                    dy = y
                    if offen:
                        dy = (y + (90 if scharnier == "left" else 270)) % 360
                    e = {"model": modell}
                    if dy:
                        e["y"] = dy
                    v[f"facing={f},half={haelfte},hinge={scharnier},open={str(offen).lower()}"] = e
    return {"variants": v}


# ---------------------------------------------------------------------------
# Torflügel: Zeichner für ganze Flächen w×h (in Blöcken)
# ---------------------------------------------------------------------------


def je_zelle(w: int, h: int, grund, fn) -> Image.Image:
    """Zeichnet Feld für Feld; fn(d, ox, oy, cx, cy) bekommt den Ursprung des Feldes in Pixeln."""
    img, d = leinwand(w * 16, h * 16, grund)
    for cy in range(h):
        for cx in range(w):
            fn(d, cx * 16, cy * 16, cx, cy)
    return img


def z_tor(w: int, h: int) -> Image.Image:
    """Schmiedeeisernes Tor: Stäbe mit Goldspitzen, Goldringe, unten geschlossene Platte, kräftige Pfosten."""
    img, d = leinwand(w * 16, h * 16, LEER)
    W, H = w * 16, h * 16
    schwarz, kante = (26, 26, 30), (74, 76, 84)
    for x in range(W):
        if x % 4 in (1, 2):
            d.line([(x, 4), (x, H - 1)], fill=rgba(schwarz if x % 4 == 1 else kante))
    # Spitzen über der oberen Querstange
    for x in range(1, W, 4):
        d.point((x, 1), fill=rgba(GOLD_HELL))
        d.line([(x, 2), (x + 1, 2)], fill=rgba(GOLD))
        d.line([(x - 1, 3), (x + 2, 3)], fill=rgba(GOLD_DUNKEL))
    # Querstangen oben und in jedem Feld ein Goldring je Stab
    d.rectangle([0, 4, W - 1, 5], fill=rgba(schwarz))
    d.line([(0, 4), (W - 1, 4)], fill=rgba(kante))
    for oy in range(16, H, 16):
        d.rectangle([0, oy, W - 1, oy], fill=rgba(schwarz))
    for oy in range(0, H, 16):
        for x in range(1, W, 4):
            d.point((x, oy + 9), fill=rgba(GOLD))
            d.point((x + 1, oy + 9), fill=rgba(GOLD_DUNKEL))
    # Untere Platte mit Goldrand
    d.rectangle([0, H - 7, W - 1, H - 1], fill=rgba(schwarz))
    d.line([(0, H - 7), (W - 1, H - 7)], fill=rgba(GOLD))
    d.rectangle([2, H - 5, W - 3, H - 3], outline=rgba(GOLD_DUNKEL))
    # Pfosten an den Enden
    for x0 in (0, W - 3):
        d.rectangle([x0, 0, x0 + 2, H - 1], fill=rgba(schwarz))
        d.line([(x0 + 1, 0), (x0 + 1, H - 1)], fill=rgba(kante))
        d.rectangle([x0, 0, x0 + 2, 1], fill=rgba(GOLD))
    return img


def z_garagentor(w: int, h: int) -> Image.Image:
    """Sektionaltor in Anthrazit: Fugen alle 4 Pixel, oben ein Fensterband, unten Chromleiste."""
    W, H = w * 16, h * 16
    img, d = leinwand(W, H, (56, 60, 66))
    for y in range(H):
        if y % 4 == 3:
            d.line([(0, y), (W - 1, y)], fill=rgba((34, 36, 40)))
        elif y % 4 == 0:
            d.line([(0, y), (W - 1, y)], fill=rgba((78, 82, 90)))
    rauschen(img, 2, 41)
    # Fensterband in der obersten Reihe
    for x0 in range(3, W - 3, 8):
        d.rectangle([x0, 5, x0 + 4, 9], fill=rgba((30, 44, 58)), outline=rgba((150, 156, 164)))
        d.point((x0 + 1, 6), fill=rgba((140, 180, 210)))
    # Chromleiste und Gummidichtung unten, Rahmen außen
    d.line([(0, H - 3), (W - 1, H - 3)], fill=rgba(CHROM))
    d.rectangle([0, H - 2, W - 1, H - 1], fill=rgba((20, 20, 22)))
    d.rectangle([0, 0, W - 1, H - 1], outline=rgba((30, 32, 36)))
    return img


def z_rollladen(w: int, h: int) -> Image.Image:
    """Aluminium-Lamellen, oben Kasten, unten Endleiste."""
    W, H = w * 16, h * 16
    img, d = leinwand(W, H, (196, 200, 206))
    for y in range(H):
        if y % 3 == 2:
            d.line([(0, y), (W - 1, y)], fill=rgba((150, 154, 160)))
        elif y % 3 == 0:
            d.line([(0, y), (W - 1, y)], fill=rgba((218, 222, 228)))
    rauschen(img, 2, 42)
    d.rectangle([0, 0, W - 1, 3], fill=rgba((110, 114, 120)))
    d.line([(0, 3), (W - 1, 3)], fill=rgba((80, 84, 90)))
    d.rectangle([0, H - 3, W - 1, H - 1], fill=rgba((100, 104, 110)))
    d.line([(0, H - 1), (W - 1, H - 1)], fill=rgba((40, 40, 44)))
    return img


def z_schiebetuer(w: int, h: int) -> Image.Image:
    """Ganzglas-Schiebetür: jeder Flügel (eine Spalte) im Goldrahmen, Griffstange an der Mittelfuge."""
    W, H = w * 16, h * 16
    img, d = leinwand(W, H, LEER)
    for cx in range(w):
        ox = cx * 16
        for x, y in ((ox + 3, 6), (ox + 4, 5), (ox + 5, 4), (ox + 11, H - 9), (ox + 12, H - 10)):
            d.point((x, y), fill=rgba((236, 246, 250)))
        d.rectangle([ox, 0, ox + 15, H - 1], outline=rgba(GOLD))
        d.line([(ox, 0), (ox + 15, 0)], fill=rgba(GOLD_HELL))
        d.line([(ox + 15, 0), (ox + 15, H - 1)], fill=rgba(GOLD_DUNKEL))
        d.rectangle([ox, H - 3, ox + 15, H - 1], fill=rgba(GOLD))
        d.line([(ox, H - 3), (ox + 15, H - 3)], fill=rgba(GOLD_HELL))
        # Griff an der Seite zur Mitte der Tür (bei einem einzelnen Flügel rechts)
        gx = ox + 12 if (cx < w / 2 - 0.5 or w == 1) else ox + 2
        m = H // 2
        griffstange(d, gx, m - 10, m + 9)
    return img


def z_portaltuer(w: int, h: int) -> Image.Image:
    """Große Haustür (Portal): je Flügel ein weiß lackierter Rahmen mit Goldleisten, oben Glas mit Sprossen,
    unten eine Kassette mit Goldkrone, lange Griffstange an der Mittelfuge."""
    W, H = w * 16, h * 16
    img, d = leinwand(W, H, LEER)
    weiss, schatten = (238, 234, 224), (204, 198, 186)
    d.rectangle([0, 0, W - 1, H - 1], fill=rgba(weiss))
    rauschen(img, 2, 38)
    for cx in range(w):
        ox = cx * 16
        # Glasfeld oben (zwei Drittel), Kassette unten
        glas_unten = max(4, int(H * 0.62))
        d.rectangle([ox + 3, 3, ox + 12, glas_unten], fill=LEER)
        for x, y in ((ox + 4, 6), (ox + 5, 5), (ox + 6, 4), (ox + 10, glas_unten - 5), (ox + 11, glas_unten - 6)):
            d.point((x, y), fill=rgba((236, 246, 250)))
        d.rectangle([ox + 3, 3, ox + 12, glas_unten], outline=rgba(GOLD))
        d.line([(ox + 3, 3), (ox + 12, 3)], fill=rgba(GOLD_HELL))
        for y in range(3 + 8, glas_unten, 8):                      # waagerechte Sprossen
            d.line([(ox + 4, y), (ox + 11, y)], fill=rgba(GOLD))
        k0, k1 = glas_unten + 3, H - 5
        if k1 > k0 + 2:
            d.rectangle([ox + 3, k0, ox + 12, k1], fill=rgba((228, 222, 210)), outline=rgba(GOLD))
            d.line([(ox + 3, k0), (ox + 12, k0)], fill=rgba(GOLD_HELL))
            my = (k0 + k1) // 2
            for x, y in ((ox + 6, my), (ox + 7, my - 1), (ox + 8, my), (ox + 9, my - 1), (ox + 10, my),
                         (ox + 6, my + 1), (ox + 7, my + 1), (ox + 8, my + 1), (ox + 9, my + 1), (ox + 10, my + 1)):
                d.point((x, y), fill=rgba(GOLD))
        # Kanten: hell links/oben, Schatten rechts
        d.line([(ox, 0), (ox, H - 1)], fill=rgba((250, 248, 242)))
        d.line([(ox + 15, 0), (ox + 15, H - 1)], fill=rgba(schatten))
        d.line([(ox, 0), (ox + 15, 0)], fill=rgba(GOLD))
        d.rectangle([ox, H - 3, ox + 15, H - 1], fill=rgba(GOLD))
        d.line([(ox, H - 3), (ox + 15, H - 3)], fill=rgba(GOLD_HELL))
        # Griffstange an der Seite zur Mitte der Tür
        gx = ox + 13 if (cx < w / 2 - 0.5 or w == 1) else ox + 1
        m = H // 2
        griffstange(d, gx, m - 12, m + 11)
    return img


def z_panzertuer(w: int, h: int) -> Image.Image:
    """Schwerer Panzerstahl: Nieten am Rand, je Reihe ein Riegel über die Mittelfuge, Warnstreifen unten."""
    W, H = w * 16, h * 16
    img, d = leinwand(W, H, (92, 96, 104))
    for y in range(H):
        if y % 16 == 0:
            d.line([(0, y), (W - 1, y)], fill=rgba((70, 74, 80)))
    rauschen(img, 4, 43)
    d.rectangle([0, 0, W - 1, H - 1], outline=rgba((44, 46, 50)))
    d.rectangle([1, 1, W - 2, H - 2], outline=rgba((120, 124, 132)))
    for x in range(3, W - 2, 6):
        niete(d, x, 3)
        niete(d, x, H - 8)
    for y in range(3, H - 6, 6):
        niete(d, 3, y)
        niete(d, W - 4, y)
    warnstreifen(d, 2, H - 5, W - 3, H - 3)
    # Fuge und Riegel: bei zwei Flügeln in der Mitte, sonst an der rechten Kante (Schloss)
    for cy in range(h):
        y = cy * 16 + 8
        if w == 2:
            d.line([(16, 1), (16, H - 2)], fill=rgba((30, 32, 36)))
            d.rectangle([10, y - 1, 21, y], fill=rgba(CHROM))
            d.line([(10, y), (21, y)], fill=rgba(CHROM_DUNKEL))
            d.rectangle([8, y - 2, 9, y + 1], fill=rgba((40, 42, 46)))
            d.rectangle([22, y - 2, 23, y + 1], fill=rgba((40, 42, 46)))
        elif w == 1:
            d.rectangle([7, y - 1, 15, y], fill=rgba(CHROM))
            d.line([(7, y), (15, y)], fill=rgba(CHROM_DUNKEL))
            d.rectangle([5, y - 2, 6, y + 1], fill=rgba((40, 42, 46)))
    return img


def z_tresortuer(w: int, h: int) -> Image.Image:
    """Runde Tresortür: Stahlring mit Bolzen, polierte Tür, goldenes Handrad mit drei Speichen."""
    W, H = w * 16, h * 16
    img, d = leinwand(W, H, (54, 56, 62))
    rauschen(img, 3, 44)
    d.rectangle([0, 0, W - 1, H - 1], outline=rgba((32, 34, 38)))
    cx, cy = (W - 1) / 2, (H - 1) / 2
    r = min(W, H) / 2 - 1
    px = img.load()
    for x in range(W):
        for y in range(H):
            a = math.hypot(x - cx, y - cy)
            if a <= r:
                if a > r - 2.2:
                    farbe = (150, 154, 162)                                   # Außenring
                elif a > r - 3.4:
                    farbe = (40, 42, 46)                                      # Fuge
                else:
                    t = 0.5 + 0.5 * math.cos(math.atan2(y - cy, x - cx) * 2)   # gebürsteter Glanz
                    g = int(150 + 50 * t)
                    farbe = (g, g + 4, g + 10)
                px[x, y] = (*farbe, 255)
    # Bolzen rund um die Tür
    n = 16 if r > 10 else 8
    for i in range(n):
        wnk = 2 * math.pi * i / n
        bx, by = round(cx + (r - 4.5) * math.cos(wnk)), round(cy + (r - 4.5) * math.sin(wnk))
        d.point((bx, by), fill=rgba(GOLD))
    # Handrad
    rr = r * 0.45
    for i in range(3):
        wnk = 2 * math.pi * i / 3 - math.pi / 2
        d.line([(cx, cy), (cx + rr * math.cos(wnk), cy + rr * math.sin(wnk))], fill=rgba(GOLD), width=2 if r > 10 else 1)
        kx, ky = cx + rr * math.cos(wnk), cy + rr * math.sin(wnk)
        d.ellipse([kx - 1.5, ky - 1.5, kx + 1.5, ky + 1.5], fill=rgba(GOLD_HELL))
    d.ellipse([cx - rr * 0.35, cy - rr * 0.35, cx + rr * 0.35, cy + rr * 0.35], fill=rgba(GOLD), outline=rgba(GOLD_DUNKEL))
    return img


def z_gitter(w: int, h: int) -> Image.Image:
    """Dickes Stahlgitter mit Querstreben, Spitzen unten (fährt aus dem Boden hoch)."""
    W, H = w * 16, h * 16
    img, d = leinwand(W, H, LEER)
    for x in range(1, W, 4):
        d.rectangle([x, 0, x + 1, H - 1], fill=rgba((118, 122, 130)))
        d.line([(x, 0), (x, H - 1)], fill=rgba((170, 174, 182)))
    for oy in range(0, H, 16):
        for y in (oy + 4, oy + 12):
            d.rectangle([0, y, W - 1, y + 1], fill=rgba((84, 88, 96)))
            d.line([(0, y), (W - 1, y)], fill=rgba((140, 144, 152)))
    d.rectangle([0, 0, W - 1, 1], fill=rgba((60, 62, 68)))
    return img


def z_poolabdeckung(w: int, h: int) -> Image.Image:
    """Dunkelblaue Lamellenabdeckung mit Edelstahlrahmen (von oben gesehen)."""
    W, H = w * 16, h * 16
    img, d = leinwand(W, H, (28, 50, 98))
    for x in range(W):
        if x % 4 == 3:
            d.line([(x, 0), (x, H - 1)], fill=rgba((18, 34, 70)))
        elif x % 4 == 0:
            d.line([(x, 0), (x, H - 1)], fill=rgba((44, 72, 128)))
    rauschen(img, 2, 45)
    d.rectangle([0, 0, W - 1, H - 1], outline=rgba(CHROM))
    d.rectangle([1, 1, W - 2, H - 2], outline=rgba(CHROM_DUNKEL))
    return img


# Typ: (Zeichner, Form, Bewegung, Werkzeug, Deutsch, Englisch)
#   Bewegung: hoch, runter, seite (+Achse), auseinander (linke Felder −, rechte +)
TORFLUEGEL = {
    "tor": (z_tor, "duenn", "runter", "pickaxe", "Tor", "Gate"),
    "garagentor": (z_garagentor, "duenn", "hoch", "pickaxe", "Garagentor", "Garage Door"),
    "rollladen": (z_rollladen, "duenn", "hoch", "pickaxe", "Rollladen", "Security Shutter"),
    "schiebetuer": (z_schiebetuer, "duenn", "auseinander", "pickaxe", "Schiebetür", "Sliding Door"),
    "portaltuer": (z_portaltuer, "duenn", "auseinander", "axe", "Portaltür (Haustür)", "Grand Entrance Door"),
    "panzertuer": (z_panzertuer, "duenn", "auseinander", "pickaxe", "Panzertür", "Blast Door"),
    "tresortuer": (z_tresortuer, "duenn", "seite", "pickaxe", "Tresortür", "Vault Door"),
    "gitterfalle": (z_gitter, "duenn", "runter", "pickaxe", "Gitterfalle", "Cage Trap"),
    "poolabdeckung": (z_poolabdeckung, "flach", "seite", "pickaxe", "Poolabdeckung", "Pool Cover"),
    "geheimregal": (None, "voll", "seite", "axe", "Geheimregal", "Secret Bookshelf"),
}

SPALTEN = ["links", "mitte", "rechts", "einzeln"]
ZEILEN = ["oben", "mitte", "unten", "einzeln"]
# Verschiebung in Pixeln je Stufe (Stufe 3 = leer)
WEG = [0, 5, 11]


def texturblatt(zeichner) -> Image.Image:
    """64×64-Blatt: Spalte s, Zeile z. Jedes Feld stammt aus einer passenden Beispielfläche, damit z. B.
    bei zwei Flügeln Fuge und Riegel zusammenpassen und Mittelfelder keinen Rand haben."""
    blatt = Image.new("RGBA", (64, 64), LEER)
    for si, s in enumerate(SPALTEN):
        for zi, z in enumerate(ZEILEN):
            w = {"links": 2, "rechts": 2, "mitte": 3, "einzeln": 1}[s]
            h = {"oben": 2, "unten": 2, "mitte": 3, "einzeln": 1}[z]
            cx = {"links": 0, "mitte": 1, "rechts": w - 1, "einzeln": 0}[s]
            cy = {"oben": 0, "mitte": 1, "unten": h - 1, "einzeln": 0}[z]
            flaeche = zeichner(w, h)
            blatt.alpha_composite(flaeche.crop((cx * 16, cy * 16, cx * 16 + 16, cy * 16 + 16)), (si * 16, zi * 16))
    return blatt


def verschiebung(bewegung: str, spalte: str, stufe: int) -> tuple[float, float]:
    """(dx, dy) in Pixeln im Modell (Fläche längs x; Blockzustand dreht für Achse z)."""
    weg = WEG[stufe]
    if bewegung == "hoch":
        return 0, weg
    if bewegung == "runter":
        return 0, -weg
    if bewegung == "auseinander" and spalte == "links":
        return -weg, 0
    return weg, 0


def m_fluegel(typ: str, form: str, bewegung: str, spalte: str, zeile: str, stufe: int) -> dict:
    """Modell eines Feldes in einer Stufe."""
    dx, dy = verschiebung(bewegung, spalte, stufe)
    if form == "voll":
        einzug = 0.25 if stufe else 0   # beim Verschieben etwas schmaler, sonst flackert die Vorderseite
        regal, holz = "minecraft:block/bookshelf", "minecraft:block/oak_planks"
        return {"parent": "minecraft:block/block", "textures": {"particle": regal, "r": regal, "h": holz},
                "elements": [{"from": [dx, dy, einzug], "to": [16 + dx, 16 + dy, 16 - einzug], "faces": {
                    "north": {"texture": "#r"}, "south": {"texture": "#r"}, "east": {"texture": "#h"},
                    "west": {"texture": "#h"}, "up": {"texture": "#h"}, "down": {"texture": "#h"}}}]}
    tex = f"{NS}:block/tueren/{typ}"
    u0, v0 = SPALTEN.index(spalte) * 4, ZEILEN.index(zeile) * 4
    u1, v1 = u0 + 4, v0 + 4
    if form == "flach":
        von, bis = [dx, 0, 0], [16 + dx, 2, 16]
        flaechen = {
            "up": {"uv": [u0, v0, u1, v1], "texture": "#t"},
            "down": {"uv": [u0, v0, u1, v1], "texture": "#t"},
            "north": {"uv": [u0, v0, u1, v0 + 0.5], "texture": "#t"},
            "south": {"uv": [u0, v1 - 0.5, u1, v1], "texture": "#t"},
            "east": {"uv": [u1 - 0.5, v0, u1, v1], "texture": "#t", "rotation": 90},
            "west": {"uv": [u0, v0, u0 + 0.5, v1], "texture": "#t", "rotation": 90},
        }
    else:
        von, bis = [dx, dy, 6], [16 + dx, 16 + dy, 10]
        flaechen = {
            "south": {"uv": [u0, v0, u1, v1], "texture": "#t"},
            "north": {"uv": [u1, v0, u0, v1], "texture": "#t"},
            "east": {"uv": [u1 - 1, v0, u1, v1], "texture": "#t"},
            "west": {"uv": [u0, v0, u0 + 1, v1], "texture": "#t"},
            "up": {"uv": [u0, v0, u1, v0 + 1], "texture": "#t"},
            "down": {"uv": [u0, v1 - 1, u1, v1], "texture": "#t"},
        }
    return {"parent": "minecraft:block/block", "textures": {"particle": tex, "t": tex},
            "elements": [{"from": von, "to": bis, "faces": flaechen}]}


def fluegel_modellname(typ: str, form: str, spalte: str, zeile: str, stufe: int) -> str:
    if form == "voll":
        return f"{NS}:block/tueren/{typ}_{stufe}"
    return f"{NS}:block/tueren/{typ}_{spalte}_{zeile}_{stufe}"


def fluegel_blockzustand(typ: str, form: str) -> dict:
    v = {}
    for achse in ("x", "z"):
        for s in SPALTEN:
            for z in ZEILEN:
                for stufe in range(4):
                    schluessel = f"axis={achse},spalte={s},stufe={stufe},zeile={z}"
                    if stufe == 3:
                        v[schluessel] = {"model": f"{NS}:block/leer"}
                        continue
                    e = {"model": fluegel_modellname(typ, form, s, z, stufe)}
                    if achse == "z":
                        e["y"] = 90
                    v[schluessel] = e
    return {"variants": v}


# ---------------------------------------------------------------------------
# Schreiben
# ---------------------------------------------------------------------------


def schreibe_json(pfad: Path, daten) -> None:
    pfad.parent.mkdir(parents=True, exist_ok=True)
    pfad.write_text(json.dumps(daten, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def beute(name: str, nur_untere_haelfte: bool = False) -> dict:
    eintrag = {"type": "minecraft:item", "name": f"{NS}:{name}"}
    if nur_untere_haelfte:
        eintrag["conditions"] = [{"condition": "minecraft:block_state_property", "block": f"{NS}:{name}",
                                  "properties": {"half": "lower"}}]
    return {"type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [eintrag], "conditions": [{"condition": "minecraft:survives_explosion"}]}]}


def main() -> dict:
    """Schreibt alles und gibt Werkzeug-Listen und Namen für assets.py zurück."""
    tex_ordner = ASSETS / "textures/block/tueren"
    modell_ordner = ASSETS / "models/block/tueren"
    tex_ordner.mkdir(parents=True, exist_ok=True)
    # Alte Dateien dieses Generators entfernen (es bleiben nur die aktuellen)
    for alt in list(tex_ordner.glob("*.png")) + list(modell_ordner.glob("*.json")):
        alt.unlink()
    texte_de, texte_en = {}, {}
    werkzeuge: dict[str, list[str]] = {"pickaxe": [], "axe": []}

    # Türen zum Aufklappen
    for name, (zeichner, werkzeug, de, en) in AUFKLAPPTUEREN.items():
        tuer = zeichner()
        tuer.crop((0, 0, 16, 16)).save(tex_ordner / f"{name}_oben.png")
        tuer.crop((0, 16, 16, 32)).save(tex_ordner / f"{name}_unten.png")
        (ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
        item_aus_tuer(tuer).save(ASSETS / "textures/item" / f"{name}.png")
        for teil in ("bottom", "top"):
            for scharnier in ("left", "right"):
                for offen in ("", "_open"):
                    schreibe_json(ASSETS / "models/block" / f"{name}_{teil}_{scharnier}{offen}.json", {
                        "parent": f"minecraft:block/door_{teil}_{scharnier}{offen}",
                        "textures": {"bottom": f"{NS}:block/tueren/{name}_unten", "top": f"{NS}:block/tueren/{name}_oben"}})
        schreibe_json(ASSETS / "blockstates" / f"{name}.json", tuer_blockzustand(name))
        schreibe_json(ASSETS / "models/item" / f"{name}.json",
                      {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
        schreibe_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
        schreibe_json(RES / f"data/{NS}/loot_table/blocks/{name}.json", beute(name, nur_untere_haelfte=True))
        werkzeuge[werkzeug].append(f"{NS}:{name}")
        texte_de[f"block.{NS}.{name}"], texte_en[f"block.{NS}.{name}"] = de, en

    # Torflügel
    for typ, (zeichner, form, bewegung, werkzeug, de, en) in TORFLUEGEL.items():
        if zeichner:
            texturblatt(zeichner).save(tex_ordner / f"{typ}.png")
        for s in SPALTEN:
            for z in ZEILEN:
                for stufe in range(3):
                    name = fluegel_modellname(typ, form, s, z, stufe).split("/", 1)[1]
                    schreibe_json(ASSETS / "models/block" / f"{name}.json", m_fluegel(typ, form, bewegung, s, z, stufe))
        schreibe_json(ASSETS / "blockstates" / f"{typ}.json", fluegel_blockzustand(typ, form))
        item_modell = fluegel_modellname(typ, form, "einzeln", "einzeln", 0)
        schreibe_json(ASSETS / "models/item" / f"{typ}.json", {"parent": item_modell})
        schreibe_json(ASSETS / "items" / f"{typ}.json", {"model": {"type": "minecraft:model", "model": item_modell}})
        schreibe_json(RES / f"data/{NS}/loot_table/blocks/{typ}.json", beute(typ))
        werkzeuge[werkzeug].append(f"{NS}:{typ}")
        texte_de[f"block.{NS}.{typ}"], texte_en[f"block.{NS}.{typ}"] = de, en

    print(f"{len(AUFKLAPPTUEREN)} Türen, {len(TORFLUEGEL)} Torflügel-Typen geschrieben")
    return {"werkzeuge": werkzeuge, "de": texte_de, "en": texte_en}


if __name__ == "__main__":
    main()
