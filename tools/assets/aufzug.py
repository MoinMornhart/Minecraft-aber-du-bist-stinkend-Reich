#!/usr/bin/env python3
"""Textur der Aufzugskabine (256 × 256, eigene Pixelgrafik).

Die Bereiche passen zu den Würfeln in client/aufzug/AufzugModell.java (Minecrafts Würfel-Abwicklung:
oben links die Deckflächen, darunter die Seiten). Glas ist durchsichtig bis auf Lichtreflexe.

Aufruf: python3 tools/assets/aufzug.py
"""
from __future__ import annotations

import random
from pathlib import Path

from PIL import Image, ImageDraw

WURZEL = Path(__file__).resolve().parents[2]
ZIEL = WURZEL / "src/main/resources/assets/stinkendreich/textures/entity/aufzugkabine.png"

GOLD = (232, 186, 52, 255)
GOLD_HELL = (255, 228, 120, 255)
GOLD_DUNKEL = (160, 112, 22, 255)


def wuerfel(u: int, v: int, w: int, h: int, d: int) -> dict[str, tuple[int, int, int, int]]:
    """Flächen eines Würfels in der Textur: Name -> (x0, y0, x1, y1) einschließlich."""
    return {
        "oben": (u + d, v, u + d + w - 1, v + d - 1),
        "unten": (u + d + w, v, u + d + 2 * w - 1, v + d - 1),
        "rechts": (u, v + d, u + d - 1, v + d + h - 1),
        "vorne": (u + d, v + d, u + d + w - 1, v + d + h - 1),
        "links": (u + d + w, v + d, u + 2 * d + w - 1, v + d + h - 1),
        "hinten": (u + 2 * d + w, v + d, u + 2 * d + 2 * w - 1, v + d + h - 1),
    }


def gold_flaeche(d: ImageDraw.ImageDraw, r) -> None:
    x0, y0, x1, y1 = r
    d.rectangle(r, fill=GOLD)
    for y in range(y0, y1 + 1):
        if (y - y0) % 4 == 0:
            d.line([(x0, y), (x1, y)], fill=GOLD_HELL)
        elif (y - y0) % 4 == 2:
            d.line([(x0, y), (x1, y)], fill=GOLD_DUNKEL)


def gold_wuerfel(d, u, v, w, h, tiefe) -> None:
    for r in wuerfel(u, v, w, h, tiefe).values():
        gold_flaeche(d, r)


def marmor(img: Image.Image, r, saat: int) -> None:
    """Weißer Marmor mit grauen Adern und Goldrand."""
    rnd = random.Random(saat)
    x0, y0, x1, y1 = r
    px = img.load()
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            g = 236 + rnd.randint(-6, 4)
            px[x, y] = (g, g, g - 4, 255)
    d = ImageDraw.Draw(img)
    for _ in range(5):
        x, y = rnd.randint(x0, x1), rnd.randint(y0, y1)
        for _ in range(30):
            d.point((x, y), fill=(170, 170, 176, 255))
            x = min(x1, max(x0, x + rnd.choice((-1, 0, 1))))
            y = min(y1, max(y0, y + rnd.choice((0, 1))))
    d.rectangle(r, outline=GOLD)
    d.rectangle((x0 + 1, y0 + 1, x1 - 1, y1 - 1), outline=GOLD_DUNKEL)


def glas(d: ImageDraw.ImageDraw, r) -> None:
    """Durchsichtig bis auf schräge Lichtreflexe in zwei Ecken."""
    x0, y0, x1, y1 = r
    d.rectangle(r, fill=(0, 0, 0, 0))
    for i in range(3):
        d.point((x0 + 2 + i, y0 + 5 - i), fill=(236, 246, 252, 255))
        d.point((x1 - 6 + i, y1 - 3 - i), fill=(236, 246, 252, 255))
    for i in range(2):
        d.point((x0 + 2 + i, y0 + 9 - i), fill=(236, 246, 252, 255))


def samt(img: Image.Image, r, saat: int, knoepfe: bool = False) -> None:
    rnd = random.Random(saat)
    x0, y0, x1, y1 = r
    px = img.load()
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            px[x, y] = (150 + rnd.randint(-10, 8), 20, 34 + rnd.randint(-4, 4), 255)
    if knoepfe:
        d = ImageDraw.Draw(img)
        for x in range(x0 + 2, x1, 4):
            for y in range(y0 + 2, y1, 4):
                d.point((x, y), fill=GOLD)


def main() -> None:
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # Boden: oben Marmor, Rest dunkel
    for name, r in wuerfel(0, 0, 28, 1, 28).items():
        if name == "oben":
            marmor(img, r, 1)
        else:
            gold_flaeche(d, r)
    # Decke: unten weiß mit Goldrand, Rest weiß
    for name, r in wuerfel(0, 30, 28, 1, 28).items():
        d.rectangle(r, fill=(236, 236, 232, 255))
        if name == "unten":
            d.rectangle(r, outline=GOLD)
    # Lichtfeld
    for r in wuerfel(120, 0, 14, 1, 14).values():
        d.rectangle(r, fill=(255, 248, 220, 255))
        d.rectangle(r, outline=(255, 230, 160, 255))
    # Glaswände
    for r in wuerfel(0, 60, 1, 44, 26).values():
        glas(d, r)
    for r in wuerfel(0, 132, 24, 44, 1).values():
        glas(d, r)
    # Gold: Pfosten, Sturz, Handläufe
    gold_wuerfel(d, 120, 20, 2, 44, 2)
    gold_wuerfel(d, 140, 20, 24, 3, 1)
    gold_wuerfel(d, 140, 30, 1, 1, 20)
    # Bank: Sockel aus dunklem Holz mit Goldleiste, Sitz und Lehne aus rotem Samt
    for r in wuerfel(60, 132, 22, 6, 6).values():
        d.rectangle(r, fill=(52, 30, 20, 255))
        d.line([(r[0], r[1]), (r[2], r[1])], fill=GOLD)
    for i, r in enumerate(wuerfel(120, 132, 24, 2, 8).values()):
        samt(img, r, 10 + i)
    for i, r in enumerate(wuerfel(120, 150, 24, 11, 2).values()):
        samt(img, r, 20 + i, knoepfe=True)
        ImageDraw.Draw(img).rectangle(r, outline=GOLD_DUNKEL)

    ZIEL.parent.mkdir(parents=True, exist_ok=True)
    img.save(ZIEL)
    print("geschrieben:", ZIEL.relative_to(WURZEL))


if __name__ == "__main__":
    main()
