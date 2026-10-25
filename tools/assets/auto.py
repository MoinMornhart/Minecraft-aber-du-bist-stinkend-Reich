#!/usr/bin/env python3
"""Erzeugt die Lackierungen des Bonzenautos (acht Farben), das Item-Bild und die Texte.

Die Aufteilung der Textur (256 x 256) passt zu AutoModell.java: jeder Quader hat dort
seine Ecke (texOffs) und seine Maße; die sechs Seiten liegen wie bei allen Minecraft-Modellen.

Aufruf: python3 tools/assets/auto.py
"""
from __future__ import annotations

import json
import random
from pathlib import Path

from PIL import Image, ImageDraw

WURZEL = Path(__file__).resolve().parents[2]
ASSETS = WURZEL / "src/main/resources/assets/stinkendreich"

FARBEN = {
    "schwarz": (24, 24, 28),
    "weiss": (232, 234, 236),
    "rot": (190, 22, 28),
    "gelb": (238, 196, 26),
    "gold": (212, 168, 64),
    "blau": (24, 64, 170),
    "gruen": (24, 120, 60),
    "pink": (226, 92, 168),
}
SCHWARZ = (18, 18, 20)
CHROM = (200, 204, 210)
LEDER = (226, 214, 190)
LEDER_DUNKEL = (180, 166, 140)
INNEN = (40, 34, 30)
GLAS = (120, 170, 196)
LICHT = (255, 248, 210)
ROT = (210, 20, 24)


def mischen(a, b, t):
    return tuple(int(x + (y - x) * t) for x, y in zip(a, b))


def flaeche(d: ImageDraw.ImageDraw, x0, y0, b, h, farbe):
    if b > 0 and h > 0:
        d.rectangle([x0, y0, x0 + b - 1, y0 + h - 1], fill=farbe + (255,))


def quader(d, u, v, b, h, t, oben, unten, seite, vorne, hinten):
    """Malt die sechs Seiten eines Quaders (b = Breite x, h = Höhe y, t = Tiefe z)."""
    flaeche(d, u + t, v, b, t, oben)
    flaeche(d, u + t + b, v, b, t, unten)
    flaeche(d, u, v + t, t, h, seite)
    flaeche(d, u + t, v + t, b, h, vorne)
    flaeche(d, u + t + b, v + t, t, h, seite)
    flaeche(d, u + 2 * t + b, v + t, b, h, hinten)


def glanz(img: Image.Image, saat: int) -> None:
    """Leichte Unregelmäßigkeit im Lack, damit er nicht flach wirkt."""
    r = random.Random(saat)
    px = img.load()
    for x in range(img.width):
        for y in range(img.height):
            p = px[x, y]
            if p[3] == 255:
                d = r.randint(-4, 4)
                px[x, y] = (max(0, min(255, p[0] + d)), max(0, min(255, p[1] + d)), max(0, min(255, p[2] + d)), 255)


def textur(lack) -> Image.Image:
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    dunkel = mischen(lack, (0, 0, 0), 0.35)
    hell = mischen(lack, (255, 255, 255), 0.25)
    streifen = mischen(lack, (0, 0, 0), 0.55) if sum(lack) > 300 else mischen(lack, (255, 255, 255), 0.6)

    # Unterbau 30x5x72: Seiten mit dunklem Schweller, vorne Lufteinlass
    quader(d, 0, 0, 30, 5, 72, lack, SCHWARZ, lack, lack, lack)
    flaeche(d, 0, 72 + 3, 72, 2, SCHWARZ)
    flaeche(d, 102, 72 + 3, 72, 2, SCHWARZ)
    flaeche(d, 72 + 4, 72 + 1, 22, 3, SCHWARZ)
    flaeche(d, 174 + 4, 72 + 1, 4, 2, CHROM)      # Auspuff
    flaeche(d, 174 + 22, 72 + 1, 4, 2, CHROM)
    # Motorhaube 30x3x26 mit zwei Rennstreifen
    quader(d, 0, 80, 30, 3, 26, lack, SCHWARZ, lack, lack, lack)
    flaeche(d, 26 + 10, 80, 3, 26, streifen)
    flaeche(d, 26 + 17, 80, 3, 26, streifen)
    flaeche(d, 26 + 4, 80 + 4, 22, 1, hell)        # Lichtkante
    # Türen 2x4x34 (beide Seiten) mit Chromleiste und Türgriff
    for v in (80, 120):
        quader(d, 114, v, 2, 4, 34, CHROM, SCHWARZ, lack, lack, lack)
        flaeche(d, 114, v + 34 + 1, 34, 1, hell)
        flaeche(d, 114 + 14, v + 34 + 2, 3, 1, CHROM)
        flaeche(d, 150, v + 34 + 1, 34, 1, hell)
        flaeche(d, 150 + 14, v + 34 + 2, 3, 1, CHROM)
    # Heck 30x4x12
    quader(d, 0, 112, 30, 4, 12, lack, SCHWARZ, lack, lack, lack)
    flaeche(d, 12 + 10, 112, 3, 12, streifen)
    flaeche(d, 12 + 17, 112, 3, 12, streifen)
    flaeche(d, 54 + 8, 124 + 1, 14, 2, dunkel)     # Nummernschild-Mulde
    flaeche(d, 54 + 10, 124 + 1, 10, 1, (236, 236, 230))
    # Innenboden 26x1x32
    quader(d, 0, 160, 26, 1, 32, INNEN, SCHWARZ, INNEN, INNEN, INNEN)
    # Frontscheibe 26x6x1: Rahmen in Lackfarbe, getöntes Glas
    quader(d, 120, 160, 26, 6, 1, lack, lack, lack, GLAS, GLAS)
    flaeche(d, 121, 161, 26, 1, lack)
    flaeche(d, 121, 161, 1, 6, lack)
    flaeche(d, 146, 161, 1, 6, lack)
    flaeche(d, 148, 161, 26, 1, lack)
    # Sitze: helles Leder
    quader(d, 180, 160, 9, 4, 2, LEDER, LEDER_DUNKEL, LEDER_DUNKEL, LEDER, LEDER_DUNKEL)
    quader(d, 180, 170, 9, 1, 7, LEDER, LEDER_DUNKEL, LEDER_DUNKEL, LEDER_DUNKEL, LEDER_DUNKEL)
    # Lenkrad
    quader(d, 216, 160, 5, 4, 1, SCHWARZ, SCHWARZ, SCHWARZ, SCHWARZ, SCHWARZ)
    flaeche(d, 217 + 2, 161 + 1, 1, 2, CHROM)
    # Spoiler (Kohlefaser-Optik) und Stützen
    quader(d, 0, 196, 30, 1, 5, (36, 36, 40), SCHWARZ, SCHWARZ, (36, 36, 40), (36, 36, 40))
    for x in range(5, 35, 2):
        flaeche(d, x, 196, 1, 5, (28, 28, 30))
    quader(d, 80, 196, 2, 4, 2, SCHWARZ, SCHWARZ, SCHWARZ, SCHWARZ, SCHWARZ)
    # Scheinwerfer, Rückleuchten, Grill, Spiegel
    quader(d, 0, 206, 7, 2, 1, CHROM, CHROM, CHROM, LICHT, CHROM)
    quader(d, 20, 206, 8, 2, 1, ROT, ROT, ROT, ROT, ROT)
    quader(d, 42, 206, 12, 3, 1, SCHWARZ, SCHWARZ, SCHWARZ, SCHWARZ, SCHWARZ)
    for x in range(43, 55, 2):
        flaeche(d, x, 207, 1, 3, CHROM)
    quader(d, 70, 206, 2, 1, 2, lack, lack, lack, lack, CHROM)
    # Räder 4x10x10: Lauffläche schwarz, Seiten mit Felge
    quader(d, 0, 216, 4, 10, 10, (30, 30, 32), (30, 30, 32), (30, 30, 32), (30, 30, 32), (30, 30, 32))
    for ox in (0, 14):
        felge(d, ox, 226)
    glanz(img, sum(lack))
    return img


def felge(d: ImageDraw.ImageDraw, x0: int, y0: int) -> None:
    """Radseite 10x10: Reifen außen, Leichtmetallfelge mit Speichen innen."""
    d.rectangle([x0, y0, x0 + 9, y0 + 9], fill=(30, 30, 32, 255))
    d.ellipse([x0 + 1, y0 + 1, x0 + 8, y0 + 8], fill=(170, 174, 180, 255))
    d.ellipse([x0 + 2, y0 + 2, x0 + 7, y0 + 7], fill=(60, 62, 66, 255))
    for dx, dy in ((0, -2), (0, 2), (-2, 0), (2, 0)):
        d.line([(x0 + 4.5, y0 + 4.5), (x0 + 4.5 + dx, y0 + 4.5 + dy)], fill=(200, 204, 210, 255))
    d.rectangle([x0 + 4, y0 + 4, x0 + 5, y0 + 5], fill=(212, 168, 64, 255))


def item() -> Image.Image:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    gold, dunkel = (212, 168, 64, 255), (150, 112, 36, 255)
    d.polygon([(1, 10), (3, 7), (6, 6), (9, 4), (12, 5), (14, 8), (15, 10), (15, 11), (1, 11)], fill=gold)
    d.polygon([(7, 6), (9, 5), (11, 6), (10, 7), (7, 7)], fill=(120, 170, 196, 255))
    d.line([(1, 11), (15, 11)], fill=dunkel)
    d.line([(13, 4), (15, 4)], fill=(30, 30, 32, 255))
    for cx in (4, 12):
        d.ellipse([cx - 2, 10, cx + 1, 13], fill=(30, 30, 32, 255))
        d.point((cx - 1, 11), fill=(190, 190, 196, 255))
    d.point((15, 9), fill=(255, 248, 210, 255))
    return img


TEXTE_DE = {
    "entity.stinkendreich.bonzenauto": "Bonzenauto",
    "item.stinkendreich.bonzenauto": "Bonzenauto",
    "key.categories.stinkendreich": "Stinkend Reich",
    "key.category.stinkendreich.bonzenauto": "Stinkend Reich: Bonzenauto",
    "key.stinkendreich.hupe": "Hupe",
    "key.stinkendreich.licht": "Scheinwerfer",
    "key.stinkendreich.kamera": "Kamera im Auto",
    "stinkendreich.auto.hinweis": "W/S Gas und Bremse · A/D lenken · Leertaste Handbremse (Driften) · H Hupe · L Licht · V Kamera · Schleichen: aussteigen",
    "stinkendreich.ton.motor": "Motor brummt",
    "stinkendreich.ton.anlasser": "Motor startet",
    "stinkendreich.ton.hupe": "Hupe",
    "stinkendreich.ton.reifen": "Reifen quietschen",
    "stinkendreich.ton.crash": "Blech kracht",
}
TEXTE_EN = {
    "entity.stinkendreich.bonzenauto": "Fat Cat Car",
    "item.stinkendreich.bonzenauto": "Fat Cat Car",
    "key.categories.stinkendreich": "Filthy Rich",
    "key.category.stinkendreich.bonzenauto": "Filthy Rich: car",
    "key.stinkendreich.hupe": "Horn",
    "key.stinkendreich.licht": "Headlights",
    "key.stinkendreich.kamera": "Car camera",
    "stinkendreich.auto.hinweis": "W/S throttle and brake · A/D steer · Space handbrake (drift) · H horn · L lights · V camera · Sneak: get out",
    "stinkendreich.ton.motor": "Engine hums",
    "stinkendreich.ton.anlasser": "Engine starts",
    "stinkendreich.ton.hupe": "Horn",
    "stinkendreich.ton.reifen": "Tyres squeal",
    "stinkendreich.ton.crash": "Metal crunches",
}


def schreibe_json(pfad: Path, daten) -> None:
    pfad.parent.mkdir(parents=True, exist_ok=True)
    pfad.write_text(json.dumps(daten, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main() -> None:
    ordner = ASSETS / "textures/entity/bonzenauto"
    ordner.mkdir(parents=True, exist_ok=True)
    for name, lack in FARBEN.items():
        textur(lack).save(ordner / f"{name}.png")
    item().save(ASSETS / "textures/item/bonzenauto.png")
    schreibe_json(ASSETS / "models/item/bonzenauto.json",
                  {"parent": "minecraft:item/generated", "textures": {"layer0": "stinkendreich:item/bonzenauto"}})
    schreibe_json(ASSETS / "items/bonzenauto.json",
                  {"model": {"type": "minecraft:model", "model": "stinkendreich:item/bonzenauto"}})
    for datei, texte in (("de_de", TEXTE_DE), ("en_us", TEXTE_EN)):
        pfad = ASSETS / "lang" / f"{datei}.json"
        vorhanden = json.loads(pfad.read_text(encoding="utf-8"))
        vorhanden.update(texte)
        schreibe_json(pfad, dict(sorted(vorhanden.items())))
    print(f"{len(FARBEN)} Lackierungen, Item und {len(TEXTE_DE)} Texte geschrieben")


if __name__ == "__main__":
    main()
