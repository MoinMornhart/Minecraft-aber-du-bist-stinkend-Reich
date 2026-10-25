#!/usr/bin/env python3
"""Erzeugt die Texturen der Show-Tänzerinnen im Geheimclub (vier Kostüme) und ihre Texte.

Textur (64x64): oben die Menschengestalt im klassischen Layout (Kopf, Körper, schmaler Arm mit 3 Pixeln, Bein;
die linke Seite ist gespiegelt), unten die Zusatzteile des eigenen Modells (lange Haare, Federschmuck),
siehe TaenzerinModell.java. Kostüm: zweiteiliges Glitzer-Showkostüm im Stil einer Las-Vegas-Revue (etwa so viel
wie ein Bikini), Netzstrümpfe, Schuhe in Kostümfarbe.

Aufruf: python3 tools/assets/taenzerin.py
"""
from __future__ import annotations

import random
from pathlib import Path

from PIL import Image

WURZEL = Path(__file__).resolve().parents[2]
ASSETS = WURZEL / "src/main/resources/assets/stinkendreich"

# (Haut, Haar, Kostüm, Glitzer, Augen, Feder) je Kostüm
KOSTUEME = [
    ((240, 202, 172), (236, 204, 104), (214, 168, 40), (255, 238, 150), (70, 120, 200), (255, 250, 236)),   # Gold
    ((214, 164, 124), (34, 28, 34), (226, 54, 146), (255, 168, 220), (90, 60, 40), (255, 120, 200)),        # Pink
    ((184, 128, 88), (112, 62, 30), (30, 190, 196), (176, 255, 248), (60, 140, 90), (120, 240, 240)),       # Türkis
    ((118, 78, 54), (128, 54, 160), (196, 198, 212), (255, 255, 255), (60, 40, 30), (190, 120, 240)),       # Silber
]
LIPPEN = (196, 40, 70)
WIMPERN = (24, 18, 22)

TEXTE_DE = {"entity.stinkendreich.taenzerin": "Show-Tänzerin"}
TEXTE_EN = {"entity.stinkendreich.taenzerin": "Show Dancer"}


def dunkler(farbe, faktor=0.82):
    return tuple(int(c * faktor) for c in farbe[:3]) + (255,)


def voll(farbe):
    return tuple(farbe[:3]) + (255,)


class Haut:
    """Malt die sechs Seiten eines Würfels (Minecraft-UV-Layout) mit einer Funktion f(seite, x, y, breite, hoehe)."""

    def __init__(self, bild: Image.Image):
        self.bild = bild

    def wuerfel(self, u, v, w, h, d, f):
        seiten = {
            "oben": (u + d, v, w, d), "unten": (u + d + w, v, w, d),
            "rechts": (u, v + d, d, h), "vorn": (u + d, v + d, w, h),
            "links": (u + d + w, v + d, d, h), "hinten": (u + 2 * d + w, v + d, w, h),
        }
        for name, (x0, y0, bw, bh) in seiten.items():
            for y in range(bh):
                for x in range(bw):
                    farbe = f(name, x, y, bw, bh)
                    if farbe is not None:
                        self.bild.putpixel((x0 + x, y0 + y), voll(farbe) if len(farbe) == 3 else farbe)


def kostuem_bild(nr: int) -> Image.Image:
    haut, haar, stoff, glitzer, augen, feder = KOSTUEME[nr]
    zufall = random.Random(100 + nr)
    bild = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    m = Haut(bild)

    def paillette(x, y):
        """Pailletten: Grundfarbe, dazwischen helle Glitzerpunkte."""
        r = zufall.random()
        return glitzer if r < 0.22 else (dunkler(stoff, 0.86) if r < 0.4 else stoff)

    # Kopf: Haar oben und hinten, an den Seiten bis zur Wange, vorne Pony, Gesicht mit Wimpern, Augen, Lippen
    def kopf(seite, x, y, w, h):
        if seite == "oben":
            return haar
        if seite == "unten":
            return dunkler(haut)
        if seite == "hinten":
            return haar if zufall.random() > 0.15 else dunkler(haar, 0.85)
        if seite in ("rechts", "links"):
            return haar if y < 6 or (seite == "rechts" and x < 3) or (seite == "links" and x > 4) else haut
        # vorn
        if y < 2 or (y < 3 and x in (0, 7)) or (x in (0, 7) and y < 7):
            return haar
        if y == 3 and x in (1, 2, 5, 6):
            return WIMPERN
        if y == 4 and x in (1, 6):
            return (250, 250, 250)
        if y == 4 and x in (2, 5):
            return augen
        if y == 5 and x in (1, 6):
            return (236, 150, 150)                        # Rouge
        if y == 6 and x in (3, 4):
            return LIPPEN
        return haut
    m.wuerfel(0, 0, 8, 8, 8, kopf)
    m.wuerfel(32, 0, 8, 8, 8, lambda *a: (0, 0, 0, 0))   # Hut-Schicht leer

    # Körper: zweiteiliges Showkostüm wie in einer Las-Vegas-Revue (Pailletten-Top mit Trägern, Bauch frei,
    # Glitzergürtel und kurzes Höschen), also so viel wie ein Bikini – nicht mehr
    def koerper(seite, x, y, w, h):
        if seite == "oben":
            return haut
        if seite == "unten":
            return stoff
        if y < 3:
            if seite in ("vorn", "hinten") and x in (1, 6):
                return glitzer                            # Träger
            return haut
        if y < 6:
            return paillette(x, y)                        # Top
        if y < 9:
            if seite == "vorn" and y == 7 and x in (3, 4):
                return dunkler(haut, 0.9)                 # Bauchnabel
            return haut                                   # Bauch frei
        if y == 9:
            return glitzer                                # Glitzergürtel
        return paillette(x, y)                            # Höschen
    m.wuerfel(16, 16, 8, 12, 4, koerper)

    # Arm (schmal, 3 Pixel): Haut, oben Armreif, unten Glitzerhandschuh bis zum Handgelenk
    def arm(seite, x, y, w, h):
        if seite == "oben":
            return haut
        if seite == "unten":
            return stoff
        if y == 2:
            return glitzer                                # Armreif am Oberarm
        if y >= 10:
            return paillette(x, y)                        # Handschuh
        return haut if (x + y) % 7 else dunkler(haut, 0.95)
    m.wuerfel(40, 16, 3, 12, 4, arm)

    # Bein: oben das Ende des Bodys, dann Netzstrumpf (Haut mit feinem Rautenmuster), unten Pumps
    def bein(seite, x, y, w, h):
        if seite == "oben":
            return stoff
        if seite == "unten":
            return dunkler(stoff, 0.6)
        if y < 1:
            return paillette(x, y)                        # Saum des Höschens
        if y >= 10:
            return dunkler(stoff, 0.75) if y == 11 else stoff  # Pumps
        if (x + y) % 2 == 0:
            return dunkler(haut, 0.86)                    # Netzstrumpf
        return haut
    m.wuerfel(0, 16, 4, 12, 4, bein)

    # Lange Haare hinten (8 × 13 × 1) und Federschmuck (2 × 5 × 1)
    m.wuerfel(0, 32, 8, 13, 1, lambda s, x, y, w, h: haar if (x + y) % 5 else dunkler(haar, 0.85))
    m.wuerfel(20, 32, 2, 5, 1, lambda s, x, y, w, h: glitzer if y == 4 else (feder if (x + y) % 3 else dunkler(feder, 0.9)))
    return bild


def main() -> dict:
    ordner = ASSETS / "textures/entity"
    ordner.mkdir(parents=True, exist_ok=True)
    for nr in range(len(KOSTUEME)):
        kostuem_bild(nr).save(ordner / f"taenzerin_{nr}.png")
    return {"de": TEXTE_DE, "en": TEXTE_EN}


if __name__ == "__main__":
    main()
