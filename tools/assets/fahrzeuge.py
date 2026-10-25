#!/usr/bin/env python3
"""Fahrzeug-Baukasten: Modelle und Lackierungen aller Autos (und später des Hubschraubers).

Ein Modell besteht aus Teilen (mit Drehpunkt und Drehung) und Kästen. Jeder Kasten bekommt pro Seite
ein Material (Lack, Chrom, Glas, Leder, Reifen …). Der Baukasten verteilt die Kästen selbst auf der
Textur (wie bei Minecraft-Modellen: oben die Deck-, darunter die Seitenflächen) und malt jede Lackfarbe.

Schreibt nach src/main/resources:
  assets/stinkendreich/fahrzeuge/<typ>.json        Modell für FahrzeugModell.java (Teile, Kästen, UV)
  assets/stinkendreich/textures/entity/<typ>/<farbe>.png
  assets/stinkendreich/textures/item/<typ>.png, models/item, items

Koordinaten wie in Minecraft-Modellen: Pixel, y = 24 ist der Boden, kleinere y liegen höher,
vorne ist -Z. Aufruf: python3 tools/assets/fahrzeuge.py
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
GOLD = (226, 180, 52)
LEDER = (226, 214, 190)
LEDER_DUNKEL = (180, 166, 140)
INNEN = (40, 34, 30)
LICHT = (255, 248, 210)
ROT = (210, 20, 24)
REIFEN = (30, 30, 32)


def mischen(a, b, t):
    return tuple(int(x + (y - x) * t) for x, y in zip(a, b))


# ---------------------------------------------------------------------------
# Materialien: malen eine Fläche (x0, y0, b, h) in der Lackfarbe `lack`
# ---------------------------------------------------------------------------


def fuellen(d, r, farbe):
    x0, y0, b, h = r
    if b > 0 and h > 0:
        d.rectangle([x0, y0, x0 + b - 1, y0 + h - 1], fill=(*farbe, 255))


def mat_lack(d, r, lack):
    fuellen(d, r, lack)


def mat_lack_dunkel(d, r, lack):
    fuellen(d, r, mischen(lack, (0, 0, 0), 0.35))


def mat_streifen(d, r, lack):
    """Lack mit zwei Rennstreifen längs."""
    fuellen(d, r, lack)
    x0, y0, b, h = r
    farbe = mischen(lack, (0, 0, 0), 0.55) if sum(lack) > 300 else mischen(lack, (255, 255, 255), 0.6)
    for x in (x0 + b // 2 - 4, x0 + b // 2 + 2):
        d.rectangle([x, y0, x + 2, y0 + h - 1], fill=(*farbe, 255))


def mat_seite(d, r, lack):
    """Seitenwand: Lack mit dunklem Schweller unten und Zierleiste."""
    fuellen(d, r, lack)
    x0, y0, b, h = r
    if h >= 4:
        d.rectangle([x0, y0 + h - 2, x0 + b - 1, y0 + h - 1], fill=(*SCHWARZ, 255))
        d.line([(x0, y0 + 1), (x0 + b - 1, y0 + 1)], fill=(*mischen(lack, (255, 255, 255), 0.3), 255))


def farbe_mat(farbe):
    return lambda d, r, lack: fuellen(d, r, farbe)


def mat_glas(d, r, lack):
    """Durchsichtig mit Lichtreflexen (Ausschnitt-Modus), man sieht hinein und hinaus."""
    x0, y0, b, h = r
    d.rectangle([x0, y0, x0 + b - 1, y0 + h - 1], fill=(0, 0, 0, 0))
    for i in range(min(3, b, h)):
        d.point((x0 + 1 + i, y0 + min(h - 1, 3) - i), fill=(220, 236, 246, 255))
    if b > 6 and h > 3:
        d.point((x0 + b - 3, y0 + h - 2), fill=(220, 236, 246, 255))


def mat_reifen(d, r, lack):
    fuellen(d, r, REIFEN)
    x0, y0, b, h = r
    for y in range(y0, y0 + h, 2):
        d.line([(x0, y), (x0 + b - 1, y)], fill=(42, 42, 46, 255))


def mat_felge(d, r, lack):
    """Radseite: Reifen außen, goldene Felge mit Chromspeichen."""
    x0, y0, b, h = r
    d.rectangle([x0, y0, x0 + b - 1, y0 + h - 1], fill=(*REIFEN, 255))
    d.ellipse([x0 + 1, y0 + 1, x0 + b - 2, y0 + h - 2], fill=(*GOLD, 255))
    d.ellipse([x0 + 2, y0 + 2, x0 + b - 3, y0 + h - 3], fill=(70, 60, 40, 255))
    mx, my = x0 + (b - 1) / 2, y0 + (h - 1) / 2
    for dx, dy in ((0, -1), (0, 1), (-1, 0), (1, 0), (-0.7, -0.7), (0.7, 0.7), (-0.7, 0.7), (0.7, -0.7)):
        d.line([(mx, my), (mx + dx * (b / 2 - 2), my + dy * (h / 2 - 2))], fill=(*CHROM, 255))
    d.rectangle([int(mx), int(my), int(mx) + 1, int(my) + 1], fill=(*GOLD, 255))


def mat_grill(d, r, lack):
    fuellen(d, r, SCHWARZ)
    x0, y0, b, h = r
    for x in range(x0 + 1, x0 + b - 1, 2):
        d.line([(x, y0), (x, y0 + h - 1)], fill=(*CHROM, 255))
    d.rectangle([x0, y0, x0 + b - 1, y0 + h - 1], outline=(*CHROM, 255))


def mat_kennzeichen(d, r, lack):
    """Nummernschild (weiß, schwarzer Rand, angedeutete Schrift)."""
    fuellen(d, r, (236, 236, 230))
    x0, y0, b, h = r
    d.rectangle([x0, y0, x0 + b - 1, y0 + h - 1], outline=(*SCHWARZ, 255))
    for x in range(x0 + 1, x0 + b - 1, 2):
        d.point((x, y0 + h // 2), fill=(*SCHWARZ, 255))


def mat_lamellen(d, r, lack):
    fuellen(d, r, mischen(lack, (0, 0, 0), 0.2))
    x0, y0, b, h = r
    for y in range(y0, y0 + h, 2):
        d.line([(x0, y), (x0 + b - 1, y)], fill=(*SCHWARZ, 255))


def mat_carbon(d, r, lack):
    fuellen(d, r, (36, 36, 40))
    x0, y0, b, h = r
    for x in range(x0, x0 + b, 2):
        d.line([(x, y0), (x, y0 + h - 1)], fill=(28, 28, 30, 255))


def mat_leder_knoepfe(d, r, lack):
    fuellen(d, r, LEDER)
    x0, y0, b, h = r
    for x in range(x0 + 1, x0 + b - 1, 3):
        for y in range(y0 + 1, y0 + h - 1, 3):
            d.point((x, y), fill=(*LEDER_DUNKEL, 255))


MATERIALIEN = {
    "lack": mat_lack, "lack_dunkel": mat_lack_dunkel, "streifen": mat_streifen, "seite": mat_seite,
    "schwarz": farbe_mat(SCHWARZ), "chrom": farbe_mat(CHROM), "gold": farbe_mat(GOLD), "glas": mat_glas,
    "leder": farbe_mat(LEDER), "leder_dunkel": farbe_mat(LEDER_DUNKEL), "leder_knoepfe": mat_leder_knoepfe,
    "innen": farbe_mat(INNEN), "licht": farbe_mat(LICHT), "rot": farbe_mat(ROT), "reifen": mat_reifen,
    "felge": mat_felge, "grill": mat_grill, "kennzeichen": mat_kennzeichen, "lamellen": mat_lamellen,
    "carbon": mat_carbon, "bar": farbe_mat((60, 34, 22)),
}

# ---------------------------------------------------------------------------
# Modell-Baukasten
# ---------------------------------------------------------------------------

SEITEN = ("oben", "unten", "links", "vorne", "rechts", "hinten")


class Teil:
    def __init__(self, name, pivot=(0, 24, 0), drehung=(0, 0, 0), eltern=None):
        self.name, self.pivot, self.drehung, self.eltern = name, pivot, drehung, eltern
        self.kaesten = []

    def kasten(self, von, masse, mat="lack", **seiten):
        """Kasten an Position `von` (Ecke) mit Maßen (Breite x, Höhe y, Tiefe z) in Pixeln."""
        flaechen = {s: seiten.get(s, seiten.get("seite" if s in ("links", "rechts") else "_", mat)) for s in SEITEN}
        self.kaesten.append({"von": list(von), "masse": list(masse), "flaechen": flaechen})
        return self


class Modell:
    def __init__(self, typ):
        self.typ = typ
        self.teile: list[Teil] = []

    def teil(self, name, pivot=(0, 24, 0), drehung=(0, 0, 0), eltern=None) -> Teil:
        t = Teil(name, pivot, drehung, eltern)
        self.teile.append(t)
        return t

    def packen(self, breite=256):
        """Verteilt alle Kästen auf der Textur (Regale nach Höhe); gleiche Kästen teilen sich ihren Platz."""
        eindeutig = {}
        for t in self.teile:
            for k in t.kaesten:
                w, h, dd = (int(round(v + 0.499)) for v in k["masse"])
                schluessel = (w, h, dd, tuple(sorted(k["flaechen"].items())))
                k["schluessel"] = schluessel
                eindeutig.setdefault(schluessel, (2 * (dd + w), dd + h))
        reihenfolge = sorted(eindeutig, key=lambda s: -eindeutig[s][1])
        x = y = regal = 0
        platz = {}
        for s in reihenfolge:
            bw, bh = eindeutig[s]
            if x + bw > breite:
                x, y, regal = 0, y + regal, 0
            platz[s] = (x, y)
            x += bw
            regal = max(regal, bh)
        hoehe = 64
        while hoehe < y + regal:
            hoehe *= 2
        self.breite, self.hoehe, self.platz = breite, hoehe, platz

    def als_json(self):
        teile = []
        for t in self.teile:
            teile.append({"name": t.name, "eltern": t.eltern, "pivot": list(t.pivot), "drehung": list(t.drehung),
                          "kaesten": [{"uv": list(self.platz[k["schluessel"]]), "von": k["von"], "masse": k["masse"]}
                                      for k in t.kaesten]})
        return {"breite": self.breite, "hoehe": self.hoehe, "teile": teile}

    def textur(self, lack) -> Image.Image:
        img = Image.new("RGBA", (self.breite, self.hoehe), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        for (w, h, dd, flaechen), (u, v) in self.platz.items():
            f = dict(flaechen)
            bereiche = {"oben": (u + dd, v, w, dd), "unten": (u + dd + w, v, w, dd), "links": (u, v + dd, dd, h),
                        "vorne": (u + dd, v + dd, w, h), "rechts": (u + dd + w, v + dd, dd, h),
                        "hinten": (u + 2 * dd + w, v + dd, w, h)}
            for seite, r in bereiche.items():
                MATERIALIEN[f[seite]](d, r, lack)
        glanz(img, sum(lack))
        return img


def glanz(img: Image.Image, saat: int) -> None:
    r = random.Random(saat)
    px = img.load()
    for x in range(img.width):
        for y in range(img.height):
            p = px[x, y]
            if p[3] == 255:
                dd = r.randint(-4, 4)
                px[x, y] = (max(0, min(255, p[0] + dd)), max(0, min(255, p[1] + dd)), max(0, min(255, p[2] + dd)), 255)


def raeder(m: Modell, x, y, z_vorne, z_hinten, w=4, durchmesser=10):
    """Vier Räder: Lauffläche Reifen, Seiten Goldfelge. Namen rad_vl/vr/hl/hr (vorne gelenkt)."""
    for name, px, pz in (("rad_vl", -x, z_vorne), ("rad_vr", x, z_vorne), ("rad_hl", -x, z_hinten), ("rad_hr", x, z_hinten)):
        m.teil(name, (px, y, pz)).kasten((-w / 2, -durchmesser / 2, -durchmesser / 2), (w, durchmesser, durchmesser),
                                         "reifen", seite="felge")


def sitz(t: Teil, x, y_sitz, z, breite=9, lehne_hoehe=4, tiefe=7, mat="leder"):
    """Sitzfläche und Lehne (Lehne hinten, also bei +Z)."""
    t.kasten((x, y_sitz, z), (breite, 1, tiefe), mat)
    t.kasten((x, y_sitz - lehne_hoehe, z + tiefe), (breite, lehne_hoehe, 2), mat)


# ---------------------------------------------------------------------------
# Die Fahrzeuge
# ---------------------------------------------------------------------------


def bonzenauto() -> Modell:
    """Sportcabrio (4,5 × 2 Blöcke): lange Haube mit Goldfigur, Rennstreifen, Heckspoiler, Goldfelgen."""
    m = Modell("bonzenauto")
    k = m.teil("karosserie")
    k.kasten((-15, -8, -36), (30, 5, 72), "lack", unten="schwarz", seite="seite")
    k.kasten((-15, -11, -36), (30, 3, 26), "lack", oben="streifen")
    k.kasten((-15, -12, -10), (2, 4, 34), "lack", oben="chrom")
    k.kasten((13, -12, -10), (2, 4, 34), "lack", oben="chrom")
    k.kasten((-15, -12, 24), (30, 4, 12), "lack", oben="streifen")
    k.kasten((-13, -9, -9), (26, 1, 32), "innen")
    k.kasten((-1, -13, -33), (2, 2, 3), "gold")                     # Kühlerfigur
    k.kasten((-4, -7, -36.7), (8, 2, 1), "kennzeichen")            # Kennzeichen vorn
    k.kasten((-4, -10, 36), (8, 2, 1), "kennzeichen")              # Kennzeichen hinten
    m.teil("scheibe", (0, -11, -10), (-0.5, 0, 0), eltern="karosserie").kasten((-13, -6, 0), (26, 6, 1), "glas",
                                                                               oben="lack", unten="lack")
    s = m.teil("sitze")
    for x in (-11, 2):
        for z in (-3, 13):
            sitz(s, x, -10, z)
    m.teil("lenkrad").kasten((4, -14, -6), (5, 4, 1), "schwarz")
    sp = m.teil("spoiler")
    sp.kasten((-15, -17, 30), (30, 1, 5), "carbon")
    sp.kasten((-10, -16, 31), (2, 4, 2), "schwarz").kasten((8, -16, 31), (2, 4, 2), "schwarz")
    li = m.teil("lichter")
    li.kasten((-14, -10, -36.6), (7, 2, 1), "chrom", vorne="licht").kasten((7, -10, -36.6), (7, 2, 1), "chrom", vorne="licht")
    li.kasten((-14, -11, 35.6), (8, 2, 1), "rot").kasten((6, -11, 35.6), (8, 2, 1), "rot")
    li.kasten((-6, -8, -36.5), (12, 3, 1), "grill")
    li.kasten((-17, -13, -9), (2, 1, 2), "lack", vorne="chrom").kasten((15, -13, -9), (2, 1, 2), "lack", vorne="chrom")
    raeder(m, 14, 19, -22, 23)
    return m


def gelaendewagen() -> Modell:
    """Kastiger Luxus-Geländewagen (4 × 2 Blöcke, fast 2 hoch): Dach auf Säulen, Reserverad, runde Scheinwerfer."""
    m = Modell("gelaendewagen")
    k = m.teil("karosserie")
    k.kasten((-15, -16, -32), (30, 12, 64), "lack", unten="schwarz", seite="seite")    # Wanne bis zur Gürtellinie
    k.kasten((-16, -6, -34), (32, 3, 3), "schwarz")                                   # Stoßstange vorn
    k.kasten((-16, -6, 31), (32, 3, 3), "schwarz")                                    # Stoßstange hinten
    k.kasten((-17, -7, -14), (1, 1, 30), "chrom").kasten((16, -7, -14), (1, 1, 30), "chrom")   # Trittbretter
    k.kasten((-8, -15, -32.6), (16, 7, 1), "grill")
    k.kasten((-1, -13, -33.2), (2, 2, 1), "gold")                                     # Emblem
    k.kasten((-13, -15, -32.7), (4, 4, 1), "chrom", vorne="licht").kasten((9, -15, -32.7), (4, 4, 1), "chrom", vorne="licht")
    k.kasten((-14, -14, 32), (3, 5, 1), "rot").kasten((11, -14, 32), (3, 5, 1), "rot")
    k.kasten((-4, -6, -34.6), (8, 2, 1), "kennzeichen").kasten((-4, -6, 34), (8, 2, 1), "kennzeichen")
    k.kasten((-13, -17, -18), (26, 1, 48), "innen")                                   # Innenboden
    # Aufbau: Säulen, Dach, Front- und Heckscheibe
    for x in (-15, 13):
        for z in (-18, 6, 28):
            k.kasten((x, -28, z), (2, 12, 2), "lack")
    k.kasten((-15, -30, -18), (30, 2, 48), "lack", oben="lack_dunkel")
    k.kasten((-10, -31, -14), (20, 1, 2), "licht")                                    # Lichtleiste auf dem Dach
    k.kasten((-13, -28, -18), (26, 12, 1), "glas")
    k.kasten((-13, -28, 29), (26, 12, 1), "glas")
    k.kasten((-6, -26, 32), (12, 12, 3), "reifen", hinten="felge")                   # Reserverad
    k.kasten((-17, -24, -16), (2, 2, 2), "lack", vorne="chrom").kasten((15, -24, -16), (2, 2, 2), "lack", vorne="chrom")
    s = m.teil("sitze")
    for x in (-12, 3):
        for z in (-12, 8):
            sitz(s, x, -18, z, lehne_hoehe=6)
    m.teil("lenkrad").kasten((4, -22, -14), (5, 4, 1), "schwarz")
    raeder(m, 14, 18, -20, 21, w=5, durchmesser=12)
    return m


def supersportwagen() -> Modell:
    """Flacher Keil-Supersportwagen (4,5 × 2 Blöcke): Mittelmotor mit Lamellen, Lufteinlässe, Heckflügel."""
    m = Modell("supersportwagen")
    k = m.teil("karosserie")
    k.kasten((-16, -7, -36), (32, 4, 72), "lack", unten="schwarz", seite="seite")
    k.kasten((-15, -9, -36), (30, 2, 22), "lack", oben="streifen")                    # flache Haube
    k.kasten((-16, -4, -37), (32, 1, 3), "carbon")                                     # Frontsplitter
    k.kasten((-16, -12, -14), (2, 5, 20), "lack").kasten((14, -12, -14), (2, 5, 20), "lack")   # Türen
    k.kasten((-17, -11, 2), (1, 4, 8), "schwarz").kasten((16, -11, 2), (1, 4, 8), "schwarz")   # Lufteinlässe
    k.kasten((-15, -12, 6), (30, 5, 30), "lack", oben="lamellen")                      # Motorabdeckung
    k.kasten((-16, -6, 34), (32, 3, 3), "carbon", hinten="grill")                      # Diffusor
    k.kasten((-13, -9.5, -36.6), (8, 1, 1), "licht").kasten((5, -9.5, -36.6), (8, 1, 1), "licht")
    k.kasten((-13, -11, 35.6), (9, 2, 1), "rot").kasten((4, -11, 35.6), (9, 2, 1), "rot")
    k.kasten((-4, -6, 36.2), (8, 2, 1), "kennzeichen")
    k.kasten((-13, -8, -13), (26, 1, 19), "innen")
    k.kasten((-11, -18, -6), (22, 1, 12), "lack_dunkel")                               # Dach
    k.kasten((-14, -17, 30), (28, 1, 5), "carbon")                                     # Heckflügel
    k.kasten((-9, -16, 32), (2, 4, 2), "schwarz").kasten((7, -16, 32), (2, 4, 2), "schwarz")
    m.teil("scheibe", (0, -12, -14), (-0.9, 0, 0), eltern="karosserie").kasten((-12, -10, 0), (24, 10, 1), "glas")
    m.teil("seitenscheiben").kasten((-15, -17, -8), (1, 5, 13), "glas").kasten((14, -17, -8), (1, 5, 13), "glas")
    s = m.teil("sitze")
    for x in (-11, 2):
        sitz(s, x, -9, -6, lehne_hoehe=6, mat="leder_knoepfe")
    m.teil("lenkrad").kasten((4, -13, -10), (5, 4, 1), "schwarz")
    raeder(m, 15, 19, -22, 22)
    return m


def stretchlimo() -> Modell:
    """Stretch-Limousine (8 Blöcke lang): lange Fahrgastzelle mit Ledersofas, Bar mit Champagner, Dach auf Säulen."""
    m = Modell("stretchlimo")
    k = m.teil("karosserie")
    k.kasten((-15, -8, -64), (30, 6, 128), "lack", unten="schwarz", seite="seite")
    k.kasten((-15, -11, -64), (30, 3, 22), "lack")                                   # Haube
    k.kasten((-15, -11, 54), (30, 3, 10), "lack")                                    # Kofferraum
    k.kasten((-15, -12, -42), (2, 4, 96), "lack").kasten((13, -12, -42), (2, 4, 96), "lack")
    k.kasten((-8, -10, -64.6), (16, 5, 1), "grill")
    k.kasten((-1, -13, -61), (2, 2, 3), "gold")                                      # Kühlerfigur
    k.kasten((-14, -10, -64.7), (5, 2, 1), "chrom", vorne="licht").kasten((9, -10, -64.7), (5, 2, 1), "chrom", vorne="licht")
    k.kasten((-14, -10, 63.6), (6, 2, 1), "rot").kasten((8, -10, 63.6), (6, 2, 1), "rot")
    k.kasten((-4, -6, -64.8), (8, 2, 1), "kennzeichen").kasten((-4, -6, 64), (8, 2, 1), "kennzeichen")
    k.kasten((-13, -9, -42), (26, 1, 96), "innen")
    # Säulen und Dach
    for x in (-15, 13):
        for z in (-42, -20, 4, 28, 52):
            k.kasten((x, -22, z), (2, 10, 2), "lack")
    k.kasten((-15, -24, -42), (30, 2, 96), "lack", oben="lack_dunkel")
    m.teil("scheibe", (0, -12, -42), (-0.35, 0, 0), eltern="karosserie").kasten((-13, -11, 0), (26, 11, 1), "glas")
    k.kasten((-13, -22, 53), (26, 10, 1), "glas")
    # Fahrgastraum: zwei Ledersofas längs, Bar mit Champagner in der Mitte
    s = m.teil("sitze")
    sitz(s, -11, -10, -38, mat="leder")                                               # Fahrer
    sitz(s, 2, -10, -38, mat="leder")
    s.kasten((-12, -11, -16), (5, 2, 60), "leder_knoepfe").kasten((-14, -17, -16), (2, 6, 60), "leder_knoepfe")
    s.kasten((7, -11, -16), (5, 2, 60), "leder_knoepfe").kasten((12, -17, -16), (2, 6, 60), "leder_knoepfe")
    s.kasten((-3, -12, 0), (6, 3, 12), "bar", oben="gold")
    s.kasten((-1, -18, 3), (2, 6, 2), "schwarz").kasten((-1, -19, 3), (2, 1, 2), "gold")      # Champagner
    m.teil("lenkrad").kasten((4, -14, -34), (5, 4, 1), "schwarz")
    raeder(m, 14, 19, -48, 46)
    return m


FAHRZEUGE = {
    # Typ: (Bauplan, Deutsch, Englisch, Farben, die es gibt)
    "bonzenauto": (bonzenauto, "Bonzenauto", "Fat Cat Car"),
    "gelaendewagen": (gelaendewagen, "Luxus-Geländewagen", "Luxury Off-Roader"),
    "supersportwagen": (supersportwagen, "Supersportwagen", "Supercar"),
    "stretchlimo": (stretchlimo, "Stretch-Limousine", "Stretch Limousine"),
}


def item_bild(typ: str) -> Image.Image:
    """Kleines Seitenbild des Fahrzeugs als Item."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    gold, dunkel, glas = (212, 168, 64, 255), (150, 112, 36, 255), (120, 170, 196, 255)
    schwarz = (30, 30, 32, 255)
    if typ == "gelaendewagen":
        d.rectangle([1, 4, 14, 10], fill=(24, 24, 28, 255))
        d.rectangle([3, 5, 7, 7], fill=glas)
        d.rectangle([9, 5, 12, 7], fill=glas)
        d.line([(1, 10), (14, 10)], fill=(80, 80, 84, 255))
        rad = ((4, 13), (11, 13))
    elif typ == "supersportwagen":
        d.polygon([(0, 11), (2, 8), (7, 6), (10, 6), (15, 9), (15, 11)], fill=(238, 196, 26, 255))
        d.polygon([(7, 7), (9, 7), (10, 8), (6, 8)], fill=glas)
        rad = ((3, 12), (12, 12))
    elif typ == "stretchlimo":
        d.rectangle([0, 7, 15, 10], fill=(18, 18, 20, 255))
        d.rectangle([3, 5, 13, 7], fill=(18, 18, 20, 255))
        for x in range(4, 13, 3):
            d.rectangle([x, 6, x + 1, 6], fill=glas)
        rad = ((2, 12), (13, 12))
    else:
        d.polygon([(1, 10), (3, 7), (6, 6), (9, 4), (12, 5), (14, 8), (15, 10), (15, 11), (1, 11)], fill=gold)
        d.polygon([(7, 6), (9, 5), (11, 6), (10, 7), (7, 7)], fill=glas)
        d.line([(1, 11), (15, 11)], fill=dunkel)
        rad = ((4, 12), (12, 12))
    for cx, cy in rad:
        d.ellipse([cx - 2, cy - 2, cx + 1, cy + 1], fill=schwarz)
        d.point((cx - 1, cy - 1), fill=gold)
    return img


def schreibe_json(pfad: Path, daten) -> None:
    pfad.parent.mkdir(parents=True, exist_ok=True)
    pfad.write_text(json.dumps(daten, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main() -> dict:
    de, en = {}, {}
    for typ, (bauplan, name_de, name_en) in FAHRZEUGE.items():
        m = bauplan()
        m.packen()
        schreibe_json(ASSETS / "fahrzeuge" / f"{typ}.json", m.als_json())
        ordner = ASSETS / "textures/entity" / typ
        ordner.mkdir(parents=True, exist_ok=True)
        for farbe, lack in FARBEN.items():
            m.textur(lack).save(ordner / f"{farbe}.png")
        (ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
        item_bild(typ).save(ASSETS / "textures/item" / f"{typ}.png")
        schreibe_json(ASSETS / "models/item" / f"{typ}.json",
                      {"parent": "minecraft:item/generated", "textures": {"layer0": f"stinkendreich:item/{typ}"}})
        schreibe_json(ASSETS / "items" / f"{typ}.json", {"model": {"type": "minecraft:model", "model": f"stinkendreich:item/{typ}"}})
        de[f"entity.stinkendreich.{typ}"] = de[f"item.stinkendreich.{typ}"] = name_de
        en[f"entity.stinkendreich.{typ}"] = en[f"item.stinkendreich.{typ}"] = name_en
        print(f"{typ}: Textur {m.breite}×{m.hoehe}, {sum(len(t.kaesten) for t in m.teile)} Kästen")
    return {"de": de, "en": en}


if __name__ == "__main__":
    main()
