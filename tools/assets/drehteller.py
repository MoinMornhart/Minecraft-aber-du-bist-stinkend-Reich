#!/usr/bin/env python3
"""Textur des Drehtellers: runde Stahlscheibe mit Riffelmuster, Speichen, Goldrand und Goldstern.

Modell: ein Kasten 108 × 1 × 108 (DrehtellerModell.java), Textur 512 × 128 in doppelter Auflösung (1024 × 256).
Die Oberseite liegt bei (108, 0) mit 108 × 108; außerhalb des Kreises ist sie durchsichtig.
Wird von tools/assets/assets.py aufgerufen.
"""
from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw

WURZEL = Path(__file__).resolve().parents[2]
ZIEL = WURZEL / "src/main/resources/assets/stinkendreich/textures/entity/drehteller.png"
F = 2   # Auflösung


TEXTE = {"de": {"entity.stinkendreich.drehteller": "Drehteller"},
         "en": {"entity.stinkendreich.drehteller": "Turntable"}}


def main() -> dict:
    img = Image.new("RGBA", (512 * F, 128 * F), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    x0, y0, g = 108 * F, 0, 108 * F
    cx, cy, r = x0 + g / 2, y0 + g / 2, g / 2 - 1
    px = img.load()
    for x in range(x0, x0 + g):
        for y in range(y0, y0 + g):
            a = math.hypot(x - cx + 0.5, y - cy + 0.5)
            if a > r:
                continue
            if a > r - 5 * F:
                farbe = (232, 186, 52) if a > r - 3 * F else (160, 112, 22)
            else:
                # Riffelblech: kleine schräge Striche im Raster
                gx, gy = (x - x0) // (3 * F), (y - y0) // (3 * F)
                riffel = ((x - x0) % (3 * F) < F) and ((gx + gy) % 2 == 0)
                g0 = 74 if riffel else 58
                farbe = (g0, g0 + 2, g0 + 6)
            px[x, y] = (*farbe, 255)
    for i in range(12):
        w = math.tau * i / 12
        d.line([(cx + 12 * F * math.cos(w), cy + 12 * F * math.sin(w)),
                (cx + (r - 6 * F) * math.cos(w), cy + (r - 6 * F) * math.sin(w))], fill=(96, 100, 108, 255), width=F)
    # Goldstern in der Mitte
    spitzen = []
    for i in range(10):
        w = math.tau * i / 10 - math.pi / 2
        rr = (11 if i % 2 == 0 else 5) * F
        spitzen.append((cx + rr * math.cos(w), cy + rr * math.sin(w)))
    d.polygon(spitzen, fill=(232, 186, 52, 255), outline=(160, 112, 22, 255))
    # Seiten und Unterseite bleiben durchsichtig: die quadratische Platte soll rund wirken
    ZIEL.parent.mkdir(parents=True, exist_ok=True)
    img.save(ZIEL)
    return TEXTE


if __name__ == "__main__":
    main()
