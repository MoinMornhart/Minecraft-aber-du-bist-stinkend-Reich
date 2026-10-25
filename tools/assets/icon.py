#!/usr/bin/env python3
"""Icon der Mod im Minecraft-Pixelstil: Goldbarren-Pyramide mit Krone auf Smaragdgrund.

Gezeichnet wird auf 32 × 32 Pixeln und ohne Glättung vergrößert:
  - src/main/resources/assets/stinkendreich/icon.png  (128 × 128, für das Mod-Menü)
  - docs/bilder/icon.png                               (512 × 512, für die README)

Aufruf: python3 tools/assets/icon.py
"""
from pathlib import Path

from PIL import Image

WURZEL = Path(__file__).resolve().parents[2]
N = 32

# Farben
FARBE = {
    "o": (59, 36, 0),        # Umriss
    "H": (255, 246, 194),    # Glanz
    "L": (255, 222, 89),     # Gold hell
    "M": (242, 180, 22),     # Gold mittel
    "D": (199, 134, 10),     # Gold dunkel
    "E": (143, 90, 4),       # Gold sehr dunkel
    "r": (214, 40, 52),      # Rubin
    "R": (255, 138, 138),    # Rubin Glanz
    "b": (42, 111, 224),     # Saphir
    "w": (255, 255, 255),    # Glitzern
    "y": (255, 243, 160),    # Glitzern gelblich
}
RAHMEN = (217, 162, 27)
RAHMEN_DUNKEL = (120, 82, 8)
SCHATTEN = (5, 30, 22)


def hintergrund() -> list[list[tuple | None]]:
    """Abgerundetes Quadrat, Smaragd mit Lichtschein in der Mitte, in wenigen Stufen (Pixellook)."""
    stufen = [(8, 40, 30), (12, 58, 43), (19, 82, 60), (29, 110, 80), (42, 140, 101)]
    bild = [[None] * N for _ in range(N)]
    for y in range(N):
        for x in range(N):
            if not im_quadrat(x, y, 4):
                continue
            d = ((x - 15.5) ** 2 + (y - 15.0) ** 2) ** 0.5
            hell = max(0.0, 1.0 - d / 16.0)
            bild[y][x] = stufen[min(len(stufen) - 1, int(hell * len(stufen)))]
    # Rahmen: außen dunkles Gold, innen helles Gold
    for y in range(N):
        for x in range(N):
            if bild[y][x] is None:
                continue
            if not im_quadrat(x, y, 4, rand=1):
                bild[y][x] = RAHMEN_DUNKEL
            elif not im_quadrat(x, y, 4, rand=2):
                bild[y][x] = RAHMEN
    return bild


def im_quadrat(x: int, y: int, r: int, rand: int = 0) -> bool:
    """Liegt (x, y) im abgerundeten Quadrat (Eckradius r), um `rand` Pixel nach innen versetzt?"""
    lo, hi = rand, N - 1 - rand
    if x < lo or y < lo or x > hi or y > hi:
        return False
    rr = max(0, r - rand)
    cx = min(max(x, lo + rr), hi - rr)
    cy = min(max(y, lo + rr), hi - rr)
    return (x - cx) ** 2 + (y - cy) ** 2 <= rr * rr + 0.5


def barren(vorne: list[list[str]], x0: int, y0: int) -> None:
    """Ein Goldbarren, 8 Pixel breit: zwei Reihen Oberseite, drei Reihen Vorderseite."""
    for x in range(x0 + 1, x0 + 7):
        vorne[y0][x] = "H" if x < x0 + 4 else "L"
    for x in range(x0, x0 + 8):
        vorne[y0 + 1][x] = "L"
    for dy, farbe in ((2, "M"), (3, "M"), (4, "D")):
        for x in range(x0, x0 + 8):
            vorne[y0 + dy][x] = farbe
    for dy in (2, 3, 4):
        vorne[y0 + dy][x0] = "L" if dy < 4 else "M"   # linke Kante heller
        vorne[y0 + dy][x0 + 7] = "E"                   # rechte Kante dunkel


KRONE = [
    "H...HH...H",
    "L...LL...L",
    "LM..LL..ML",
    "LMM.LL.MML",
    "LMMMLLMMML",
    "MrMMbbMMrM",
    "DDDDDDDDDD",
]


def vordergrund() -> list[list[str]]:
    vorne = [["."] * N for _ in range(N)]
    for x0 in (4, 12, 20):          # unten drei
        barren(vorne, x0, 21)
    for x0 in (8, 16):              # Mitte zwei
        barren(vorne, x0, 17)
    barren(vorne, 12, 13)           # oben einer
    for dy, zeile in enumerate(KRONE):  # Krone, eine Reihe Abstand zum Barren (wird Umriss)
        for dx, z in enumerate(zeile):
            if z != ".":
                vorne[5 + dy][11 + dx] = z
    # Umriss um alles Goldene
    umriss = [zeile[:] for zeile in vorne]
    for y in range(N):
        for x in range(N):
            if vorne[y][x] != ".":
                continue
            for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if 0 <= nx < N and 0 <= ny < N and vorne[ny][nx] != ".":
                    umriss[y][x] = "o"
                    break
    # Glitzern (ohne Umriss)
    for cx, cy in ((6, 10), (26, 8)):
        for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            umriss[cy + dy][cx + dx] = "w" if (dx, dy) == (0, 0) else "y"
    for x, y in ((24, 15), (7, 16), (27, 19), (9, 5)):
        umriss[y][x] = "y"
    return umriss


def zeichnen() -> Image.Image:
    bild = hintergrund()
    # Schatten unter der Pyramide
    for x in range(5, 27):
        if bild[27][x] is not None:
            bild[27][x] = SCHATTEN
    vorne = vordergrund()
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    for y in range(N):
        for x in range(N):
            if bild[y][x] is None:
                continue
            z = vorne[y][x]
            farbe = FARBE[z] if z != "." else bild[y][x]
            img.putpixel((x, y), (*farbe, 255))
    return img


def main() -> None:
    img = zeichnen()
    ziele = [(WURZEL / "src/main/resources/assets/stinkendreich/icon.png", 128),
             (WURZEL / "docs/bilder/icon.png", 512)]
    for pfad, groesse in ziele:
        pfad.parent.mkdir(parents=True, exist_ok=True)
        img.resize((groesse, groesse), Image.NEAREST).save(pfad, optimize=True)
        print("geschrieben:", pfad.relative_to(WURZEL), f"{groesse}×{groesse}")


if __name__ == "__main__":
    main()
