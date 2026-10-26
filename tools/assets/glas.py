"""Sauberes Glas wie in „Clear Glass“-Ressourcenpaketen: ersetzt die Glastextur von Minecraft durch eine eigene,
fast unsichtbare Scheibe ohne Rand, nur mit ein paar Glanz-Pixeln. Eigene Pixel, nichts von Mojang kopiert.
Aufruf: python3 tools/assets/glas.py"""
from pathlib import Path
from PIL import Image

WURZEL = Path(__file__).resolve().parents[2]
ZIEL = WURZEL / "src/main/resources/assets/minecraft/textures/block"


def scheibe() -> Image.Image:
    """Volle Glasfläche ohne Rand: ganz leicht getönt, nur ein paar helle Glanz-Pixel schräg in der Scheibe."""
    img = Image.new("RGBA", (16, 16), (225, 240, 245, 10))
    px = img.load()
    for (x, y) in ((3, 2), (2, 3), (4, 3), (3, 4), (2, 5), (5, 2), (11, 9), (10, 10), (12, 10), (11, 11)):
        px[x, y] = (245, 250, 255, 120)
    return img


def scheibe_oben() -> Image.Image:
    """Oberkante einer Glasscheibe (glass_pane_top): schmaler heller Streifen."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in (7, 8):
            px[x, y] = (235, 245, 250, 0)
    return img


if __name__ == "__main__":
    ZIEL.mkdir(parents=True, exist_ok=True)
    scheibe().save(ZIEL / "glass.png")
    scheibe_oben().save(ZIEL / "glass_pane_top.png")
    print("Glas geschrieben:", ZIEL / "glass.png")
