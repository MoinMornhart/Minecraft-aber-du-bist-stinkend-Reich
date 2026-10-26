#!/usr/bin/env python3
"""Textur des Hais (128 × 64), selbst gemalt: graublauer Rücken, weißer Bauch mit weichem Übergang, Kiemenschlitze,
schwarze Augen, Unterkiefer mit weißen Zähnen. Die Würfel (texOffs, Breite, Höhe, Tiefe) müssen genau zu
src/main/java/.../client/hai/HaiModell.java passen.

Aufruf: python3 tools/assets/hai.py (läuft auch über assets.py)
"""
from __future__ import annotations

import random
from pathlib import Path

from PIL import Image

from handy import wuerfel

WURZEL = Path(__file__).resolve().parents[2]
ZIEL = WURZEL / "src/main/resources/assets/stinkendreich/textures/entity/hai.png"

RUECKEN = (74, 86, 100)
FLANKE = (112, 124, 136)
BAUCH = (226, 226, 220)
AUGE = (12, 12, 14)
MAUL = (60, 26, 30)
ZAHN = (246, 244, 236)

# Name: (u, v, Breite, Höhe, Tiefe) wie im Modell
TEILE = {
    "rumpf": (0, 0, 10, 9, 16),
    "kopf": (0, 26, 8, 7, 8),
    "schnauze": (34, 26, 6, 5, 4),
    "kiefer": (56, 26, 6, 2, 7),
    "rueckenflosse": (54, 0, 1, 8, 6),
    "flosse_links": (70, 0, 9, 1, 5),
    "flosse_rechts": (70, 7, 9, 1, 5),
    "schwanz": (0, 42, 6, 6, 11),
    "schwanz_oben": (36, 42, 1, 11, 4),
    "schwanz_unten": (48, 42, 1, 7, 3),
}


def mischen(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def rauschen(c, zufall, staerke=6):
    d = zufall.randint(-staerke, staerke)
    return tuple(max(0, min(255, v + d)) for v in c)


def koerper(zufall, augen=False, kiemen=False):
    """Färbt einen Rumpfteil: oben Rücken, Seiten mit Übergang zum Bauch, unten Bauch."""
    def f(seite, x, y, w, h):
        if seite == "oben":
            return rauschen(RUECKEN, zufall)
        if seite == "unten":
            return rauschen(BAUCH, zufall, 3)
        if seite in ("vorn", "hinten"):
            t = y / max(1, h - 1)
        else:
            t = y / max(1, h - 1)
            # Augen: oben vorn an beiden Seiten (Spalte vorn ist bei „rechts“ links, bei „links“ rechts)
            vorn_x = 1 if seite == "rechts" else w - 2
            if augen and x == vorn_x and y == 2:
                return AUGE
            # Kiemen: drei dunkle senkrechte Schlitze hinten an den Kopfseiten
            if kiemen:
                hinten = [w - 2, w - 3, w - 4] if seite == "rechts" else [1, 2, 3]
                if x in hinten and 2 <= y <= h - 3 and (x % 1 == 0):
                    return mischen(RUECKEN, AUGE, 0.45)
        if t < 0.45:
            farbe = mischen(RUECKEN, FLANKE, t / 0.45)
        elif t < 0.65:
            farbe = mischen(FLANKE, BAUCH, (t - 0.45) / 0.2)
        else:
            farbe = BAUCH
        return rauschen(farbe, zufall, 4)
    return f


def flosse(zufall, spitze_hell=False):
    def f(seite, x, y, w, h):
        c = RUECKEN if seite != "unten" else FLANKE
        if spitze_hell and seite in ("rechts", "links") and y >= h - 2:
            c = mischen(c, BAUCH, 0.3)
        return rauschen(c, zufall, 5)
    return f


def kiefer(zufall):
    def f(seite, x, y, w, h):
        if seite == "oben":
            # Rand mit Zähnen, innen dunkles Maul
            rand = x == 0 or x == w - 1 or y == 0
            return ZAHN if rand and (x + y) % 2 == 0 else (MAUL if not rand else mischen(ZAHN, MAUL, 0.3))
        if seite == "vorn":
            return ZAHN if y == 0 and x % 2 == 0 else rauschen(BAUCH, zufall, 3)
        if seite in ("rechts", "links") and y == 0:
            return ZAHN if x % 2 == 0 else MAUL
        return rauschen(BAUCH, zufall, 3)
    return f


def schnauze(zufall):
    basis = koerper(zufall)

    def f(seite, x, y, w, h):
        # Unterseite der Schnauze: Oberkiefer mit Zähnen
        if seite == "unten":
            rand = x == 0 or x == w - 1 or y == 0
            return ZAHN if rand and (x + y) % 2 == 0 else MAUL
        if seite == "vorn" and y == h - 1:
            return ZAHN if x % 2 == 0 else MAUL
        return basis(seite, x, y, w, h)
    return f


def hai() -> Image.Image:
    zufall = random.Random(7)
    bild = Image.new("RGBA", (128, 64), (0, 0, 0, 0))
    maler = {
        "rumpf": koerper(zufall),
        "kopf": koerper(zufall, augen=True, kiemen=True),
        "schnauze": schnauze(zufall),
        "kiefer": kiefer(zufall),
        "rueckenflosse": flosse(zufall),
        "flosse_links": flosse(zufall, True),
        "flosse_rechts": flosse(zufall, True),
        "schwanz": koerper(zufall),
        "schwanz_oben": flosse(zufall),
        "schwanz_unten": flosse(zufall),
    }
    for name, (u, v, w, h, d) in TEILE.items():
        wuerfel(bild, u, v, w, h, d, maler[name])
    return bild


def main() -> dict:
    ZIEL.parent.mkdir(parents=True, exist_ok=True)
    hai().save(ZIEL)
    return {"de": {}, "en": {}}


if __name__ == "__main__":
    main()
