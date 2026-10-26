#!/usr/bin/env python3
"""Titelbild der README: Screenshot aus dem Spiel, links abgedunkelt, darauf Icon und Schriftzug
in Pixelschrift (klein ohne Glättung gezeichnet und pixelgenau vergrößert, wie die Schrift im Spiel).

Aufruf: python3 tools/assets/banner.py <screenshot.png>   → docs/bilder/banner.jpg
Den Screenshot liefert das Foto-Szenario: tools/ingame/szenarien/fotos.txt (Bild f10_banner).
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

WURZEL = Path(__file__).resolve().parents[2]
SCHRIFT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
BREITE, HOEHE = 1280, 420


def pixeltext(text: str, faktor: int, farbe: tuple, schatten: tuple) -> Image.Image:
    """Text in 11-Pixel-Schrift ohne Glättung, mit Schatten wie im Spiel, dann `faktor`-fach vergrößert."""
    schrift = ImageFont.truetype(SCHRIFT, 11)
    links, oben, rechts, unten = schrift.getbbox(text)
    klein = Image.new("RGBA", (rechts - links + 2, unten - oben + 2), (0, 0, 0, 0))
    d = ImageDraw.Draw(klein)
    d.fontmode = "1"
    d.text((1 - links, 1 - oben), text, font=schrift, fill=(*schatten, 255))
    d.text((-links, -oben), text, font=schrift, fill=(*farbe, 255))
    return klein.resize((klein.width * faktor, klein.height * faktor), Image.NEAREST)


def main() -> None:
    quelle = Path(sys.argv[1])
    bild = Image.open(quelle).convert("RGB")
    # Ausschnitt um die Villa, auf Bannerformat
    skala = BREITE / bild.width
    bild = bild.resize((BREITE, round(bild.height * skala)), Image.LANCZOS)
    oben = max(0, min(bild.height - HOEHE, round(bild.height * 0.30)))
    bild = bild.crop((0, oben, BREITE, oben + HOEHE)).convert("RGBA")

    # Links abdunkeln, damit der Schriftzug lesbar ist
    verlauf = Image.new("RGBA", (BREITE, HOEHE), (0, 0, 0, 0))
    d = ImageDraw.Draw(verlauf)
    for x in range(BREITE):
        anteil = max(0.0, 1.0 - x / (BREITE * 0.58))
        d.line([(x, 0), (x, HOEHE)], fill=(8, 12, 10, int(200 * anteil ** 1.2)))
    bild = Image.alpha_composite(bild, verlauf)

    icon = Image.open(WURZEL / "docs/bilder/icon.png").convert("RGBA").resize((144, 144), Image.NEAREST)
    bild.alpha_composite(icon, (44, (HOEHE - 144) // 2))

    x = 214
    zeile1 = pixeltext("Minecraft, aber du bist", 3, (255, 255, 255), (63, 63, 63))
    zeile2 = pixeltext("stinkend reich", 6, (255, 213, 64), (88, 60, 0))
    zeile3 = pixeltext("Villa · Butler · Festung · Haie", 3, (215, 230, 220), (40, 45, 42))
    gesamt = zeile1.height + 4 + zeile2.height + 14 + zeile3.height
    y = (HOEHE - gesamt) // 2
    for z, abstand in ((zeile1, 4), (zeile2, 14), (zeile3, 0)):
        bild.alpha_composite(z, (x, y))
        y += z.height + abstand

    ziel = WURZEL / "docs/bilder/banner.jpg"
    bild.convert("RGB").save(ziel, quality=90, optimize=True)
    print("geschrieben:", ziel.relative_to(WURZEL), f"{BREITE}×{HOEHE}")


if __name__ == "__main__":
    main()
