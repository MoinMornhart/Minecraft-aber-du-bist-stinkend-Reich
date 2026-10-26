"""Sauberes Glas wie in „Clear Glass“-Ressourcenpaketen: ersetzt die Glastextur von Minecraft durch eine eigene,
fast unsichtbare Scheibe mit feinem hellem Rand (keine Schlieren). Eigene Pixel, nichts von Mojang kopiert.
Aufruf: python3 tools/assets/glas.py"""
from pathlib import Path
from PIL import Image

WURZEL = Path(__file__).resolve().parents[2]
ZIEL = WURZEL / "src/main/resources/assets/minecraft/textures/block"


def scheibe() -> Image.Image:
    """Volle Glasfläche: Innen ganz leicht bläulich-durchsichtig, Rand 1 Pixel hell und halbdurchsichtig."""
    img = Image.new("RGBA", (16, 16), (225, 240, 245, 12))
    px = img.load()
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            px[x, y] = (235, 245, 250, 45)
    return img


def scheibe_oben() -> Image.Image:
    """Oberkante einer Glasscheibe (glass_pane_top): schmaler heller Streifen."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in (7, 8):
            px[x, y] = (235, 245, 250, 45)
    return img


if __name__ == "__main__":
    ZIEL.mkdir(parents=True, exist_ok=True)
    scheibe().save(ZIEL / "glass.png")
    scheibe_oben().save(ZIEL / "glass_pane_top.png")
    print("Glas geschrieben:", ZIEL / "glass.png")
