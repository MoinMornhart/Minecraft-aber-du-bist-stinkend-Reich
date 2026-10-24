"""Einfache Vorschaubilder für Block-Volumen: Draufsicht und isometrische Ansicht.

Nur zur Analyse und für schnelle Entwurfsvorschauen. Echte Bilder aus dem
Spiel entstehen mit Minecraft selbst.
"""
from __future__ import annotations

import numpy as np
from PIL import Image, ImageDraw

from anvil import Volumen

# Farbe nach Teilstring im Blocknamen; die erste passende Regel gewinnt.
FARBREGELN: list[tuple[str, tuple[int, int, int]]] = [
    ("command_block", (220, 40, 40)),
    ("water", (52, 95, 200)),
    ("lava", (230, 110, 20)),
    ("glass_pane", (190, 225, 235)),
    ("glass", (175, 215, 230)),
    ("white_concrete", (232, 234, 235)),
    ("light_gray_concrete", (160, 160, 152)),
    ("gray_concrete", (75, 80, 85)),
    ("black_concrete", (22, 24, 28)),
    ("quartz", (236, 230, 222)),
    ("smooth_stone", (165, 165, 165)),
    ("snow", (248, 250, 252)),
    ("grass_block", (100, 160, 70)),
    ("short_grass", (110, 170, 75)),
    ("tall_grass", (110, 170, 75)),
    ("fern", (90, 150, 70)),
    ("leaves", (60, 120, 45)),
    ("dirt", (134, 96, 67)),
    ("farmland", (110, 75, 50)),
    ("sand", (220, 210, 160)),
    ("gravel", (135, 130, 125)),
    ("stripped_", (180, 140, 90)),
    ("_log", (100, 78, 48)),
    ("_wood", (100, 78, 48)),
    ("dark_oak", (66, 45, 25)),
    ("spruce", (114, 84, 50)),
    ("birch", (196, 180, 125)),
    ("oak", (162, 130, 78)),
    ("planks", (160, 125, 80)),
    ("terracotta", (150, 92, 66)),
    ("brick", (150, 85, 70)),
    ("iron", (215, 215, 215)),
    ("gold", (245, 205, 55)),
    ("diamond", (95, 220, 210)),
    ("emerald", (40, 200, 95)),
    ("redstone", (180, 20, 20)),
    ("lamp", (230, 190, 110)),
    ("glowstone", (240, 210, 120)),
    ("sea_lantern", (210, 230, 225)),
    ("carpet", (180, 60, 60)),
    ("wool", (220, 220, 220)),
    ("concrete", (140, 140, 140)),
    ("stone", (125, 125, 125)),
    ("deepslate", (80, 80, 85)),
    ("andesite", (135, 135, 135)),
    ("diorite", (200, 200, 200)),
    ("granite", (150, 105, 85)),
    ("flower", (230, 80, 140)),
    ("bedrock", (60, 60, 60)),
]
STANDARDFARBE = (170, 120, 200)

LUFT = ("minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:structure_void", "minecraft:barrier", "minecraft:light")


def farbe(name: str) -> tuple[int, int, int]:
    rein = name.split("[")[0]
    for teil, f in FARBREGELN:
        if teil in rein:
            return f
    return STANDARDFARBE


def _farbtabelle(vol: Volumen) -> tuple[np.ndarray, np.ndarray]:
    farben = np.array([farbe(n) for n in vol.namen], dtype=np.float32)
    sichtbar = np.array([n.split("[")[0] not in LUFT for n in vol.namen], dtype=bool)
    return farben, sichtbar


def draufsicht(vol: Volumen, markierungen: list[tuple[int, int]] | None = None, massstab: int = 4) -> Image.Image:
    """Blick von oben; heller = höher. Markierungen (Weltkoordinaten x, z) als rote Kreise."""
    farben, sichtbar = _farbtabelle(vol)
    dy, dz, dx = vol.bloecke.shape
    vis = sichtbar[vol.bloecke]
    # höchster sichtbarer Block je Spalte
    hat = vis.any(axis=0)
    hoehe = dy - 1 - np.argmax(vis[::-1], axis=0)
    oberster = np.take_along_axis(vol.bloecke, hoehe[None, :, :], axis=0)[0]
    bild = farben[oberster]
    licht = 0.55 + 0.45 * (hoehe / max(dy - 1, 1))
    bild = bild * licht[:, :, None]
    bild[~hat] = (20, 20, 25)
    img = Image.fromarray(np.clip(bild, 0, 255).astype(np.uint8), "RGB")
    img = img.resize((dx * massstab, dz * massstab), Image.NEAREST)
    if markierungen:
        zeichnen = ImageDraw.Draw(img)
        ox, _, oz = vol.ursprung
        for x, z in markierungen:
            px, pz = (x - ox) * massstab + massstab // 2, (z - oz) * massstab + massstab // 2
            zeichnen.ellipse([px - 4, pz - 4, px + 4, pz + 4], outline=(255, 30, 30), width=2)
    return img


def isometrisch(vol: Volumen, kante: int = 6, blickrichtung: int = 0) -> Image.Image:
    """Isometrische Ansicht mit drei Flächenhelligkeiten (oben, links, rechts).

    blickrichtung 0..3 dreht das Volumen in 90-Grad-Schritten um die y-Achse.
    """
    bloecke = np.rot90(vol.bloecke, k=blickrichtung, axes=(1, 2))
    farben, sichtbar = _farbtabelle(vol)
    vis = sichtbar[bloecke]
    dy, dz, dx = bloecke.shape

    # Nur Blöcke mit mindestens einer freien Fläche nach oben, Süden (+z) oder Osten (+x)
    frei_oben = np.ones_like(vis)
    frei_oben[:-1] = ~vis[1:]
    frei_sued = np.ones_like(vis)
    frei_sued[:, :-1] = ~vis[:, 1:]
    frei_ost = np.ones_like(vis)
    frei_ost[:, :, :-1] = ~vis[:, :, 1:]
    zeigen = vis & (frei_oben | frei_sued | frei_ost)

    w = kante
    h = kante // 2
    breite = (dx + dz) * w + 2 * w
    hoehe_px = (dx + dz) * h + dy * kante + 2 * kante
    img = Image.new("RGB", (breite, hoehe_px), (24, 28, 32))
    zeichnen = ImageDraw.Draw(img)
    ys, zs, xs = np.nonzero(zeigen)
    # Malerreihenfolge: Blickrichtung ist (+x, +y, +z), also nach x+y+z aufsteigend zeichnen
    reihenfolge = np.lexsort((xs, ys, xs + ys + zs))
    x0 = dz * w + w
    y0 = dy * kante + kante
    for i in reihenfolge:
        y, z, x = int(ys[i]), int(zs[i]), int(xs[i])
        f = farben[bloecke[y, z, x]]
        px = x0 + (x - z) * w
        py = y0 + (x + z) * h - y * kante
        oben = [(px, py - h), (px + w, py), (px, py + h), (px - w, py)]
        links = [(px - w, py), (px, py + h), (px, py + h + kante), (px - w, py + kante)]
        rechts = [(px, py + h), (px + w, py), (px + w, py + kante), (px, py + h + kante)]
        if frei_sued[y, z, x]:
            zeichnen.polygon(links, fill=tuple(int(c * 0.72) for c in f))
        if frei_ost[y, z, x]:
            zeichnen.polygon(rechts, fill=tuple(int(c * 0.86) for c in f))
        if frei_oben[y, z, x]:
            zeichnen.polygon(oben, fill=tuple(int(c) for c in f))
    return img.crop(img.getbbox() or (0, 0, breite, hoehe_px))
