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
import math
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
FLANKE = (38, 38, 42)
GETOENT = (14, 18, 26, 196)   # getöntes Glas: dunkel, etwa ein Viertel durchsichtig
FEIN = 4   # Auflösung der Textur: 4 Texel pro Modellpixel (runde Felgen und Scheinwerfer werden fein gemalt)


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


def mat_glas_dunkel(d, r, lack):
    """Getönte Scheibe (undurchsichtig) mit schrägem Lichtreflex, z. B. Seitenfenster des Hubschraubers."""
    x0, y0, b, h = r
    if b <= 0 or h <= 0:
        return
    d.rectangle([x0, y0, x0 + b - 1, y0 + h - 1], fill=(26, 34, 52, 255))
    for i in range(-h, b, max(4, b // 2)):
        d.line([(x0 + i, y0 + h - 1), (x0 + i + h - 1, y0)], fill=(70, 92, 124, 255))
    d.rectangle([x0, y0, x0 + b - 1, y0 + h - 1], outline=(14, 16, 22, 255))


def mat_glas_getoent(d, r, lack):
    """Getönte Scheibe (Limousine): dunkel und halbdurchsichtig – von außen schwarz glänzend, von innen sieht man
    gedämpft hinaus (das Auto wird mit Transparenz gezeichnet). Fein bemalt in fein_getoent."""
    x0, y0, b, h = r
    if b > 0 and h > 0:
        d.rectangle([x0, y0, x0 + b - 1, y0 + h - 1], fill=GETOENT)


def mat_sterne(d, r, lack):
    """Sternenhimmel unter dem Dach der Limousine: dunkelblau mit hellen Punkten."""
    fuellen(d, r, (14, 16, 30))
    x0, y0, b, h = r
    zufall = random.Random(b * 31 + h)
    for _ in range(max(1, b * h // 12)):
        d.point((x0 + zufall.randrange(max(1, b)), y0 + zufall.randrange(max(1, h))), fill=(250, 244, 210, 255))


def mat_licht_streifen(d, r, lack):
    """Schmaler LED-Streifen (Tagfahrlicht)."""
    fuellen(d, r, (236, 244, 255))


def mat_rueck_streifen(d, r, lack):
    """Durchgehendes Rücklichtband: rot mit dunklen Fugen."""
    fuellen(d, r, ROT)
    x0, y0, b, h = r
    for x in range(x0 + 3, x0 + b - 1, 4):
        d.line([(x, y0), (x, y0 + h - 1)], fill=(120, 10, 14, 255))


def mat_wabe(d, r, lack):
    """Wabengitter (Lufteinlässe des Supersportwagens)."""
    fuellen(d, r, (14, 14, 16))
    x0, y0, b, h = r
    for y in range(y0, y0 + h, 2):
        for x in range(x0 + (y - y0) % 4 // 2, x0 + b, 2):
            d.point((x, y), fill=(46, 46, 52, 255))


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


def mat_leer(d, r, lack):
    """Durchsichtig; die Fläche wird erst in der feinen Auflösung bemalt (siehe FEIN_MALER)."""
    x0, y0, b, h = r
    if b > 0 and h > 0:
        d.rectangle([x0, y0, x0 + b - 1, y0 + h - 1], fill=(0, 0, 0, 0))


def fein_flanke(d, r):
    """Reifenflanke: glatt, ohne Rauschen (die Flanken der sechs Reifenstreifen liegen übereinander)."""
    x0, y0, b, h = r
    d.rectangle([x0, y0, x0 + b - 1, y0 + h - 1], fill=(*FLANKE, 255))


def fein_felge(d, r):
    """Runde Goldfelge mit acht Chromspeichen und Nabe; außerhalb des Kreises durchsichtig."""
    x0, y0, b, h = r
    d.rectangle([x0, y0, x0 + b - 1, y0 + h - 1], fill=(0, 0, 0, 0))
    mx, my, rad = x0 + b / 2, y0 + h / 2, min(b, h) / 2
    def kreis(rr, farbe):
        d.ellipse([mx - rr, my - rr, mx + rr - 1, my + rr - 1], fill=farbe)
    kreis(rad, (*FLANKE, 255))                                  # schmaler Gummirand
    kreis(rad - FEIN * 0.5, (*GOLD, 255))                        # Felgenhorn
    kreis(rad - FEIN * 1.25, (58, 50, 36, 255))                  # Felgenbett (dunkel)
    for i in range(8):
        w = math.tau * i / 8
        for dw in (-0.09, 0.09):
            d.line([(mx, my), (mx + (rad - FEIN) * math.cos(w + dw), my + (rad - FEIN) * math.sin(w + dw))],
                   fill=(*CHROM, 255), width=max(1, FEIN // 2))
    kreis(FEIN * 1.4, (*GOLD, 255))                              # Nabe
    kreis(FEIN * 0.6, (*CHROM, 255))


def fein_getoent(d, r):
    """Getöntes Glas fein: gleichmäßig dunkel und halbdurchsichtig, dazu zwei dezente schräge Lichtreflexe."""
    x0, y0, b, h = r
    d.rectangle([x0, y0, x0 + b - 1, y0 + h - 1], fill=GETOENT)
    for i in range(-h, b, max(24, b // 2)):
        d.line([(x0 + i, y0 + h - 1), (x0 + i + h - 1, y0)], fill=(70, 86, 112, 200), width=2)


def fein_scheinwerfer_rund(d, r):
    """Runder Scheinwerfer im Chromring (fein)."""
    x0, y0, b, h = r
    d.rectangle([x0, y0, x0 + b - 1, y0 + h - 1], fill=(*SCHWARZ, 255))
    d.ellipse([x0, y0, x0 + b - 1, y0 + h - 1], fill=(*CHROM, 255))
    d.ellipse([x0 + FEIN * 0.6, y0 + FEIN * 0.6, x0 + b - 1 - FEIN * 0.6, y0 + h - 1 - FEIN * 0.6], fill=(*LICHT, 255))
    d.ellipse([x0 + b * 0.3, y0 + h * 0.3, x0 + b * 0.5, y0 + h * 0.5], fill=(255, 255, 255, 255))


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


def mat_grill_quer(d, r, lack):
    """Kühlergrill mit drei waagerechten Chromstreben (Geländewagen)."""
    fuellen(d, r, SCHWARZ)
    x0, y0, b, h = r
    for i in range(1, 4):
        y = y0 + i * h // 4
        d.line([(x0 + 1, y), (x0 + b - 2, y)], fill=(*CHROM, 255))
    d.rectangle([x0, y0, x0 + b - 1, y0 + h - 1], outline=(*CHROM, 255))


def mat_scheinwerfer_rund(d, r, lack):
    """Runder Scheinwerfer im Chromring auf schwarzem Grund."""
    fuellen(d, r, SCHWARZ)
    x0, y0, b, h = r
    d.ellipse([x0, y0, x0 + b - 1, y0 + h - 1], fill=(*CHROM, 255))
    d.ellipse([x0 + 1, y0 + 1, x0 + b - 2, y0 + h - 2], fill=(*LICHT, 255))


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
    "carbon": mat_carbon, "bar": farbe_mat((60, 34, 22)), "orange": farbe_mat((250, 150, 30)),
    "gruen": farbe_mat((30, 200, 70)), "glas_dunkel": mat_glas_dunkel,
    "grill_quer": mat_grill_quer, "scheinwerfer_rund": mat_scheinwerfer_rund,
    "reifen_flanke": farbe_mat(FLANKE), "felge_rund": mat_leer,
    "glas_getoent": mat_glas_getoent, "sterne": mat_sterne, "led": mat_licht_streifen, "rueck_band": mat_rueck_streifen,
    "wabe": mat_wabe,
}

# Materialien, die nach dem Vergrößern noch einmal fein gemalt werden
FEIN_MALER = {"reifen_flanke": fein_flanke, "felge_rund": fein_felge, "scheinwerfer_rund": fein_scheinwerfer_rund,
              "glas_getoent": fein_getoent}

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
        while breite < max(bw for bw, _ in eindeutig.values()):
            breite *= 2
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
        schilder = []
        for t in self.teile:
            teile.append({"name": t.name, "eltern": t.eltern, "pivot": list(t.pivot), "drehung": list(t.drehung),
                          "kaesten": [{"uv": list(self.platz[k["schluessel"]]), "von": k["von"], "masse": k["masse"]}
                                      for k in t.kaesten]})
            # Nummernschilder: Darauf schreibt das Spiel den Namen des Besitzers (KennzeichenLayer)
            for k in t.kaesten:
                if "kennzeichen" in k["flaechen"].values():
                    schilder.append({"pfad": self.pfad(t), "von": k["von"], "masse": k["masse"]})
        return {"breite": self.breite, "hoehe": self.hoehe, "teile": teile, "schilder": schilder}

    def pfad(self, teil):
        """Namen der Teile von der Wurzel bis zu diesem Teil."""
        namen = {t.name: t for t in self.teile}
        kette = [teil.name]
        while namen[kette[0]].eltern is not None:
            kette.insert(0, namen[kette[0]].eltern)
        return kette

    def textur(self, lack) -> Image.Image:
        """Malt die Lackierung: erst grob (1 Texel pro Pixel), dann FEIN-fach vergrößert; runde Teile
        (Felgen, Scheinwerfer) und die Reifenflanken werden in der feinen Auflösung neu gemalt.
        Minecraft rechnet die Texturkoordinaten selbst auf die größere Datei um."""
        img = Image.new("RGBA", (self.breite, self.hoehe), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        fein = []
        for (w, h, dd, flaechen), (u, v) in self.platz.items():
            f = dict(flaechen)
            for seite, r in self.bereiche(u, v, w, h, dd).items():
                MATERIALIEN[f[seite]](d, r, lack)
                if f[seite] in FEIN_MALER:
                    fein.append((FEIN_MALER[f[seite]], r))
        glanz(img, sum(lack))
        img = img.resize((self.breite * FEIN, self.hoehe * FEIN), Image.NEAREST)
        d = ImageDraw.Draw(img)
        for maler, (x0, y0, b, h) in fein:
            if b > 0 and h > 0:
                maler(d, (x0 * FEIN, y0 * FEIN, b * FEIN, h * FEIN))
        return img

    @staticmethod
    def bereiche(u, v, w, h, dd):
        """Lage der sechs Flächen eines Kastens auf der Textur (wie Minecraft sie abwickelt)."""
        return {"oben": (u + dd, v, w, dd), "unten": (u + dd + w, v, w, dd), "links": (u, v + dd, dd, h),
                "vorne": (u + dd, v + dd, w, h), "rechts": (u + dd + w, v + dd, dd, h),
                "hinten": (u + 2 * dd + w, v + dd, w, h)}


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
    """Vier runde Räder (Namen rad_vl/vr/hl/hr, vorne gelenkt). Der Reifen ist ein Zwölfeck aus sechs
    Streifen, die um je 30 Grad um die Achse gedreht sind; ihre Enden bilden die Lauffläche. Außen sitzt
    auf jeder Seite eine flache runde Goldfelge (Kreis auf durchsichtigem Grund)."""
    streifen = durchmesser * math.tan(math.radians(15))
    felge = durchmesser - 3
    for name, px, pz in (("rad_vl", -x, z_vorne), ("rad_vr", x, z_vorne), ("rad_hl", -x, z_hinten), ("rad_hr", x, z_hinten)):
        rad = m.teil(name, (px, y, pz))
        for i in range(6):
            teil = rad if i == 0 else m.teil(f"{name}_{i}", (0, 0, 0), (math.radians(30 * i), 0, 0), eltern=name)
            teil.kasten((-w / 2, -streifen / 2, -durchmesser / 2), (w, streifen, durchmesser), "reifen",
                        seite="reifen_flanke")
        for sx in (-w / 2 - 0.05, w / 2 + 0.05):                       # knapp vor der Flanke: kein Flimmern
            rad.kasten((sx, -felge / 2, -felge / 2), (0, felge, felge), "felge_rund")


def reserverad(m: Modell, mitte, durchmesser, dicke):
    """Rundes Reserverad an der Hecktür (Achse längs zum Auto): sechs Streifen um je 30 Grad um die
    Z-Achse gedreht, hinten eine runde Goldfelge."""
    streifen = durchmesser * math.tan(math.radians(15))
    felge = durchmesser - 3
    rad = m.teil("reserverad", mitte)
    for i in range(6):
        teil = rad if i == 0 else m.teil(f"reserverad_{i}", (0, 0, 0), (0, 0, math.radians(30 * i)), eltern="reserverad")
        teil.kasten((-streifen / 2, -durchmesser / 2, -dicke / 2), (streifen, durchmesser, dicke), "reifen",
                    vorne="reifen_flanke", hinten="reifen_flanke")
    rad.kasten((-felge / 2, -felge / 2, dicke / 2 + 0.05), (felge, felge, 0), "felge_rund")


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
    """Kastiger Luxus-Geländewagen im Stil der großen Klassiker (4 × 2 Blöcke, gut 2 hoch): fast senkrechte
    Frontscheibe, flache Haube mit Blinkern auf den Kotflügeln, runde Scheinwerfer, Grill mit drei Querstreben,
    schwarze Kotflügelverbreiterungen, Sidepipes, Reserverad an der Hecktür. Teil-Rahmen: y = 0 ist der Boden."""
    m = Modell("gelaendewagen")
    k = m.teil("karosserie")
    # Fahrgestell, Stoßstangen, Unterfahrschutz
    k.kasten((-14, -6, -30), (28, 2, 60), "schwarz")
    k.kasten((-16, -8, -33), (32, 4, 3), "schwarz", vorne="lack_dunkel")
    k.kasten((-16, -8, 30), (32, 4, 3), "schwarz")
    k.kasten((-8, -5, -33), (16, 1, 3), "chrom")
    # Motorraum mit flacher Haube, Kotflügel vorn mit Blinkern obendrauf
    k.kasten((-12, -18, -30), (24, 10, 16), "lack")
    for x in (-15, 12):
        k.kasten((x, -18, -30), (3, 5, 16), "lack", seite="seite")
        k.kasten((x + 0.5, -19, -29.5), (2, 1, 3), "orange")
    k.kasten((-11, -18.5, -28), (22, 0.5, 12), "lack_dunkel")                        # Haubenkante
    # Front: Grill mit Querstreben, Emblem, runde Scheinwerfer, Blinker darunter
    k.kasten((-7, -17, -30.6), (14, 8, 1), "grill_quer")
    k.kasten((-1.5, -14.5, -31.2), (3, 3, 1), "gold")
    k.kasten((-13, -17, -30.7), (5, 5, 1), "schwarz", vorne="scheinwerfer_rund")
    k.kasten((8, -17, -30.7), (5, 5, 1), "schwarz", vorne="scheinwerfer_rund")
    k.kasten((-13, -11.5, -30.6), (4, 1, 1), "orange").kasten((9, -11.5, -30.6), (4, 1, 1), "orange")
    k.kasten((-4, -9, -33.6), (8, 2, 1), "kennzeichen")
    # Seitenwände der Kabine bis zur Gürtellinie (18 px); hinten über dem Rad nur der Kotflügel
    for x in (-15, 13):
        k.kasten((x, -18, -14), (2, 12, 26), "lack", seite="seite")
        k.kasten((x, -18, 12), (2, 5, 18), "lack", seite="lack")
        k.kasten((x, -12, 26), (2, 6, 4), "lack")
        k.kasten((x + (-0.5 if x < 0 else 2), -15, -6), (0.5, 1, 3), "chrom")         # Türgriffe
        k.kasten((x + (-0.5 if x < 0 else 2), -15, 8), (0.5, 1, 3), "chrom")
    k.kasten((-13, -18, 29), (26, 12, 1), "lack")                                    # Hecktür unten
    # Kotflügelverbreiterungen über den Rädern, Sidepipes vor den Hinterrädern
    for x in (-17.5, 15):
        for z in (-29, 11):
            k.kasten((x, -14, z), (2.5, 1.5, 17), "schwarz")
        k.kasten((x + (0.5 if x < 0 else 0), -7, 4), (2, 1.5, 6), "chrom")
    k.kasten((-13, -7, -14), (26, 1, 43), "innen")                                   # Innenboden
    # Aufbau: fast senkrechte Frontscheibe, Säulen, Seitenscheiben, Dach mit Dachreling
    k.kasten((-13, -32, -14), (26, 14, 1), "glas")
    for x in (-15, 13):
        k.kasten((x, -32, -14), (2, 14, 2), "lack")                                   # A-Säule
        k.kasten((x, -32, 4), (2, 14, 2), "schwarz")                                  # B-Säule
        k.kasten((x, -32, 27), (2, 14, 3), "lack")                                    # D-Säule
        k.kasten((x + (0.5 if x < 0 else 0.5), -31, -12), (1, 12, 16), "glas")
        k.kasten((x + 0.5, -31, 6), (1, 12, 21), "glas")
    k.kasten((-15, -34, -14), (30, 2, 44), "lack", oben="lack")
    k.kasten((-14, -35, -10), (1, 1, 36), "schwarz").kasten((13, -35, -10), (1, 1, 36), "schwarz")
    k.kasten((-13, -32, 29), (26, 14, 1), "glas")
    # Hinten: senkrechte Rücklichter, Kennzeichen, Reserverad mit Hülle an der Tür (zur Seite versetzt)
    k.kasten((-15, -18, 30), (2, 7, 1), "rot").kasten((13, -18, 30), (2, 7, 1), "rot")
    k.kasten((-4, -9, 33), (8, 2, 1), "kennzeichen")
    reserverad(m, (-4.5, 2.5, 31.5), 13, 3)
    # Spiegel an den A-Säulen
    k.kasten((-18, -22, -13), (3, 2, 2), "lack", vorne="schwarz", hinten="chrom")
    k.kasten((15, -22, -13), (3, 2, 2), "lack", vorne="schwarz", hinten="chrom")
    s = m.teil("sitze")
    for x in (-12, 3):
        for z in (-10, 10):
            sitz(s, x, -10, z, lehne_hoehe=7)
    m.teil("lenkrad").kasten((4, -15, -12), (5, 4, 1), "schwarz")
    m.teil("armaturen").kasten((-13, -19, -14), (26, 2, 3), "schwarz", oben="lack_dunkel")
    raeder(m, 15, 17.5, -21, 20, w=5, durchmesser=13)
    return m


def hubschrauber() -> Modell:
    """Luxus-Hubschrauber (im Spiel 1,4-fach vergrößert: gut 7 Blöcke lang, Rotor 12 Blöcke): Kabine mit großen Scheiben und Goldstreifen,
    Kufen, Triebwerksverkleidung, Heckausleger mit Seitenleitwerk, Fünfblatt-Hauptrotor (Teil „rotor“) und
    Heckrotor (Teil „heckrotor“); beide dreht FahrzeugModell. Vorn ist -Z, y = 0 der Boden."""
    m = Modell("hubschrauber")
    k = m.teil("karosserie")
    # Kufen mit Streben
    for x in (-14, 12):
        k.kasten((x, -1.5, -20), (2, 1.5, 40), "chrom")
        k.kasten((x, -3.5, -22.5), (2, 2.5, 3), "chrom")
        for z in (-12, 10):
            k.kasten((x + 0.5, -7, z), (1, 5.5, 1.5), "chrom")
    # Kabine: Bauch, Oberteil mit Fenstern, Nase mit Frontscheibe
    k.kasten((-11, -14, -18), (22, 8, 32), "lack", unten="schwarz", seite="seite")
    k.kasten((-10.5, -25, -13), (21, 11, 26), "lack")
    for x in (-11, 10.5):
        k.kasten((x, -23.5, -11.5), (0.5, 8, 10), "glas_dunkel")
        k.kasten((x, -23.5, 0), (0.5, 8, 11), "glas_dunkel")
        k.kasten((x - (0.3 if x < 0 else -0.5), -15.5, -18), (0.3, 2, 32), "gold")          # Goldstreifen
        k.kasten((x - (0.2 if x < 0 else -0.5), -24, -0.5), (0.2, 18, 0.5), "lack_dunkel")   # Türfuge
    k.kasten((-9.5, -25, -19), (19, 5, 6), "glas")
    k.kasten((-9, -20, -24), (18, 6, 6), "lack")
    k.kasten((-8.5, -24, -24), (17, 4, 5), "glas")
    k.kasten((-7, -18, -27), (14, 6, 3), "lack", vorne="lack_dunkel")
    k.kasten((-2, -15, -27.4), (4, 2, 0.5), "gold")
    # Innen: Boden, Cockpit, vier Ledersitze
    k.kasten((-10, -6.5, -17), (20, 0.5, 30), "innen")
    k.kasten((-9, -17, -19), (18, 3, 3), "schwarz", oben="carbon")
    s_ = m.teil("sitze")
    for x in (-10.5, 1.5):
        for z in (-12, 0):
            sitz(s_, x, -9, z, lehne_hoehe=7)
    # Triebwerk, Auspuff, Rotormast
    k.kasten((-6, -29, -8), (12, 4, 20), "lack_dunkel")
    k.kasten((-7.5, -28, 6), (2, 2, 4), "chrom").kasten((5.5, -28, 6), (2, 2, 4), "chrom")
    k.kasten((-1, -35, -1), (2, 6, 2), "chrom")
    # Heckausleger, Leitwerk, Höhenflosse
    k.kasten((-3.5, -22, 14), (7, 6, 14), "lack")
    k.kasten((-2.5, -21.5, 28), (5, 5, 14), "lack")
    k.kasten((-2, -21, 42), (4, 4, 12), "lack")
    k.kasten((-1, -34, 50), (2, 13, 6), "lack")
    k.kasten((-1, -35, 51), (2, 1, 5), "gold")
    k.kasten((-8, -22, 46), (16, 1, 4), "lack")
    # Positionslichter: links rot, rechts grün, Blitz am Leitwerk
    k.kasten((-11.6, -14, -2), (0.5, 1, 1.5), "rot").kasten((11.1, -14, -2), (0.5, 1, 1.5), "gruen")
    k.kasten((-0.5, -36, 53), (1, 1, 1), "rot")
    # Hauptrotor: Nabe und fünf Blätter (Blatt 0 im Teil selbst, die anderen um je 72 Grad gedreht)
    r = m.teil("rotor", (0, 24 - 35, 0))
    r.kasten((-2.5, -1, -2.5), (5, 2, 5), "chrom")
    for i in range(5):
        blatt = r if i == 0 else m.teil(f"rotor_{i}", (0, 0, 0), (0, math.radians(72 * i), 0), eltern="rotor")
        blatt.kasten((-1.5, -0.6, -66), (3, 0.6, 63), "schwarz")
        blatt.kasten((-1.5, -0.65, -67), (3, 0.7, 4), "gold")
    # Heckrotor rechts am Leitwerk (dreht um die Querachse)
    h = m.teil("heckrotor", (1.5, 24 - 28, 53))
    h.kasten((0, -7, -1), (0.5, 14, 2), "schwarz").kasten((0, -1, -7), (0.5, 2, 14), "schwarz")
    h.kasten((-0.2, -1, -1), (1, 2, 2), "chrom")
    return m


def supersportwagen() -> Modell:
    """Supersportwagen im Stil der italienischen Keile (4,5 × 2 Blöcke, Maßstab 1,5): flach und kantig, lange schräge
    Haube, weit nach vorn gezogene Frontscheibe, kurzes Dach, Motorhaube mit Lamellen schräg zum Heck, riesige
    Lufteinlässe in den Flanken und vorn, Y-förmige Lichter, Diffusor mit Endrohren in der Mitte, Heckflügel."""
    m = Modell("supersportwagen")
    k = m.teil("karosserie")
    # Unterbau, Schweller und Flanken
    k.kasten((-16, -7, -34), (32, 4, 68), "lack", unten="schwarz", seite="seite")
    k.kasten((-15, -3, -36), (30, 1, 72), "carbon")                                     # Seitenschweller / Boden
    k.kasten((-16, -11, -14), (2, 4, 20), "lack").kasten((14, -11, -14), (2, 4, 20), "lack")   # Türen
    # Lufteinlässe hinter den Türen (Wabengitter), darüber eine scharfe Kante
    k.kasten((-16.5, -11, 6), (1, 5, 10), "wabe").kasten((15.5, -11, 6), (1, 5, 10), "wabe")
    k.kasten((-16.5, -12, 4), (1, 1, 14), "lack").kasten((15.5, -12, 4), (1, 1, 14), "lack")
    # Heck: Flanken bis zur Höhe der Motorhaube
    k.kasten((-16, -12, 16), (32, 5, 20), "lack", oben="wabe")
    # Front: niedrige Nase mit drei großen Einlässen und Carbon-Splitter
    k.kasten((-16, -8, -36), (32, 1, 4), "lack")
    k.kasten((-15, -7, -36.6), (9, 4, 1), "wabe").kasten((6, -7, -36.6), (9, 4, 1), "wabe")
    k.kasten((-4, -6, -36.6), (8, 3, 1), "wabe")
    k.kasten((-17, -3, -38), (34, 1, 4), "carbon")
    # Y-förmige Scheinwerfer: schmaler Streifen und schräger Strich
    k.kasten((-15, -9, -36.4), (8, 1, 1), "led").kasten((7, -9, -36.4), (8, 1, 1), "led")
    m.teil("licht_l", (-8, -9, -36.4), (0, 0, 0.5), eltern="karosserie").kasten((0, -0.5, 0), (4, 1, 1), "led")
    m.teil("licht_r", (8, -9, -36.4), (0, 0, -0.5), eltern="karosserie").kasten((-4, -0.5, 0), (4, 1, 1), "led")
    # Heck: Y-Rücklichter, schwarzes Gitter, Diffusor mit Lamellen und zwei Endrohren in der Mitte
    k.kasten((-15, -11, 35.6), (9, 1, 1), "rot").kasten((6, -11, 35.6), (9, 1, 1), "rot")
    m.teil("ruecklicht_l", (-6, -11, 36.0), (0, 0, -0.5), eltern="karosserie").kasten((0, -0.5, 0), (4, 1, 1), "rot")
    m.teil("ruecklicht_r", (6, -11, 36.0), (0, 0, 0.5), eltern="karosserie").kasten((-4, -0.5, 0), (4, 1, 1), "rot")
    k.kasten((-14, -9, 35.8), (28, 3, 1), "wabe")
    k.kasten((-16, -5, 34), (32, 2, 3), "carbon")
    for x in (-12, -8, 7, 11):
        k.kasten((x, -5, 36.5), (1, 2, 1), "carbon")
    k.kasten((-3.5, -6.5, 36.4), (3, 2, 1), "chrom", hinten="schwarz").kasten((0.5, -6.5, 36.4), (3, 2, 1), "chrom", hinten="schwarz")
    k.kasten((-4, -11.5, 36.2), (8, 2, 1), "kennzeichen")
    # Innenraum
    k.kasten((-13, -8, -13), (26, 1, 19), "innen")
    # Haube: flache Platte, die von der Nase schräg bis zur Frontscheibe ansteigt
    m.teil("haube", (0, -8, -36), (0.16, 0, 0), eltern="karosserie").kasten((-15, 0, 0), (30, 2, 23), "lack")
    # Frontscheibe weit vorn und flach, mit schwarzen Säulen; kurzes Dach
    m.teil("scheibe", (0, -11, -15), (-0.95, 0, 0), eltern="karosserie").kasten((-12, -16, 0), (24, 16, 1), "glas") \
        .kasten((-14, -16, -0.5), (2, 16, 2), "lack_dunkel").kasten((12, -16, -0.5), (2, 16, 2), "lack_dunkel")
    k.kasten((-12, -22.5, -4), (24, 1, 11), "lack")
    # Motorhaube mit Lamellen fällt vom Dach schräg zum Heck ab, seitlich dunkle Lamellenflächen
    m.teil("motorhaube", (0, -22, 6), (-0.42, 0, 0), eltern="karosserie").kasten((-12, 0, 0), (24, 1, 25), "lamellen")
    for z0, y0, tiefe in ((6, -21, 6), (12, -19, 6), (18, -17, 6), (24, -15, 6)):
        k.kasten((-15, y0, z0), (2, -12 - y0, tiefe), "lack_dunkel").kasten((13, y0, z0), (2, -12 - y0, tiefe), "lack_dunkel")
    # Seitenscheiben (vorn zur Säule hin schräg abgestuft)
    m.teil("seitenscheiben").kasten((-15, -21, -5), (1, 9, 11), "glas").kasten((14, -21, -5), (1, 9, 11), "glas") \
        .kasten((-15, -17, -10), (1, 5, 5), "glas").kasten((14, -17, -10), (1, 5, 5), "glas")
    # Außenspiegel und Heckflügel
    k.kasten((-19, -15, -10), (3, 2, 2), "lack").kasten((16, -15, -10), (3, 2, 2), "lack")
    sp = m.teil("spoiler")
    sp.kasten((-15, -18, 29), (30, 1, 6), "carbon")
    sp.kasten((-15, -21, 28), (1, 4, 7), "carbon").kasten((14, -21, 28), (1, 4, 7), "carbon")
    sp.kasten((-9, -17, 31), (2, 5, 2), "carbon").kasten((7, -17, 31), (2, 5, 2), "carbon")
    s = m.teil("sitze")
    for x in (-11, 2):
        sitz(s, x, -9, -6, lehne_hoehe=6, mat="leder_knoepfe")
    m.teil("lenkrad").kasten((4, -13, -10), (5, 4, 1), "schwarz")
    raeder(m, 15, 19, -22, 22)
    return m


def stretchlimo() -> Modell:
    """Stretch-Limousine (8 Blöcke lang) wie eine echte: lange, niedrige Karosserie, durchgehendes Band aus getönten
    Scheiben mit Chromleiste, großer Chromgrill mit Kühlerfigur, Chromstoßstangen, LED-Lichter, durchgehendes
    Rücklichtband. Innen zwei Ledersofas, Bar mit Champagner und ein Sternenhimmel unter dem Dach."""
    m = Modell("stretchlimo")
    k = m.teil("karosserie")
    k.kasten((-15, -9, -64), (30, 7, 128), "lack", unten="schwarz", seite="seite")
    k.kasten((-15, -12, -64), (30, 3, 16), "lack")                                   # lange Haube
    k.kasten((-15, -12, 54), (30, 3, 10), "lack")                                    # Kofferraum
    # Türen mit Chrom-Zierleiste oben (unter den Fenstern)
    k.kasten((-15, -12, -48), (2, 3, 102), "lack", oben="chrom").kasten((13, -12, -48), (2, 3, 102), "lack", oben="chrom")
    # Chrom-Stoßstangen vorn und hinten
    k.kasten((-15.5, -4, -65.5), (31, 1.5, 2), "chrom").kasten((-15.5, -4, 63.5), (31, 1.5, 2), "chrom")
    # Großer Chromgrill mit senkrechten Streben, Kühlerfigur, LED-Scheinwerfer
    k.kasten((-8, -11.5, -64.6), (16, 7, 1), "chrom")
    k.kasten((-7, -10.5, -64.9), (14, 5, 1), "grill")
    k.kasten((-1, -14, -61), (2, 2, 3), "gold")
    k.kasten((-14.5, -10.5, -64.6), (6, 2, 1), "chrom", vorne="licht").kasten((8.5, -10.5, -64.6), (6, 2, 1), "chrom", vorne="licht")
    k.kasten((-14.5, -11.5, -64.55), (6, 0.6, 1), "led").kasten((8.5, -11.5, -64.55), (6, 0.6, 1), "led")
    # Durchgehendes Rücklichtband und Kennzeichen
    k.kasten((-14.5, -11, 63.6), (29, 1.5, 1), "rueck_band")
    k.kasten((-4, -6, -64.8), (8, 2, 1), "kennzeichen").kasten((-4, -6, 64), (8, 2, 1), "kennzeichen")
    k.kasten((-13, -10, -52), (26, 1, 106), "innen")
    k.kasten((-13, -14, -51), (26, 3, 7), "schwarz", oben="innen")                    # Armaturenbrett
    # Fahrgastraum: getönte Scheiben als durchgehendes Band (Dach so hoch, dass ein sitzender Spieler mit dem Kopf
    # darunter passt), schmale schwarze Säulen, Dach mit Chromkante
    k.kasten((-15, -29, -45), (1, 17, 98), "glas_getoent").kasten((14, -29, -45), (1, 17, 98), "glas_getoent")
    for z in (-21, 3, 27):
        k.kasten((-15.3, -29, z), (0.6, 17, 1.5), "schwarz").kasten((14.7, -29, z), (0.6, 17, 1.5), "schwarz")
    k.kasten((-15, -31, -45), (30, 2, 99), "lack", unten="sterne", seite="chrom")      # Dach, Kante in Chrom
    m.teil("scheibe", (0, -12, -52), (-0.5, 0, 0), eltern="karosserie").kasten((-13, -18, 0), (26, 18, 1), "glas") \
        .kasten((-15, -18, -0.5), (2, 18, 2), "lack").kasten((13, -18, -0.5), (2, 18, 2), "lack")
    k.kasten((-14, -29, 53), (28, 17, 1), "glas_getoent")                            # getönte Heckscheibe
    k.kasten((-17.5, -15, -47), (2.5, 2, 1.5), "lack").kasten((15, -15, -47), (2.5, 2, 1.5), "lack")   # Spiegel
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


TEXTE_DE = {
    "key.categories.stinkendreich": "Stinkend Reich",
    "key.category.stinkendreich.bonzenauto": "Stinkend Reich: Bonzenauto",
    "key.stinkendreich.hupe": "Hupe",
    "key.stinkendreich.licht": "Scheinwerfer",
    "key.stinkendreich.kamera": "Kamera im Auto",
    "stinkendreich.auto.hinweis": "W/S Gas und Bremse · A/D lenken · Leertaste Handbremse (Driften) · H Hupe · G Licht · V Kamera · Schleichen: aussteigen",
    "stinkendreich.hubschrauber.hinweis": "Leertaste steigen · Strg sinken · W/S vor und zurück · A/D drehen · G Licht · V Kamera · Schleichen: aussteigen",
    "stinkendreich.ton.motor": "Motor brummt",
    "stinkendreich.ton.anlasser": "Motor startet",
    "stinkendreich.ton.hupe": "Hupe",
    "stinkendreich.ton.reifen": "Reifen quietschen",
    "stinkendreich.ton.crash": "Blech kracht",
}
TEXTE_EN = {
    "key.categories.stinkendreich": "Filthy Rich",
    "key.category.stinkendreich.bonzenauto": "Filthy Rich: car",
    "key.stinkendreich.hupe": "Horn",
    "key.stinkendreich.licht": "Headlights",
    "key.stinkendreich.kamera": "Car camera",
    "stinkendreich.auto.hinweis": "W/S throttle and brake · A/D steer · Space handbrake (drift) · H horn · G lights · V camera · Sneak: get out",
    "stinkendreich.hubschrauber.hinweis": "Space climb · Ctrl descend · W/S forward and back · A/D turn · G lights · V camera · Sneak: get out",
    "stinkendreich.ton.motor": "Engine hums",
    "stinkendreich.ton.anlasser": "Engine starts",
    "stinkendreich.ton.hupe": "Horn",
    "stinkendreich.ton.reifen": "Tyres squeal",
    "stinkendreich.ton.crash": "Metal crunches",
}

FAHRZEUGE = {
    # Typ: (Bauplan, Deutsch, Englisch, Farben, die es gibt)
    "bonzenauto": (bonzenauto, "Bonzenauto", "Fat Cat Car"),
    "gelaendewagen": (gelaendewagen, "Luxus-Geländewagen", "Luxury Off-Roader"),
    "supersportwagen": (supersportwagen, "Supersportwagen", "Supercar"),
    "stretchlimo": (stretchlimo, "Stretch-Limousine", "Stretch Limousine"),
    "hubschrauber": (hubschrauber, "Luxus-Hubschrauber", "Luxury Helicopter"),
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
    elif typ == "hubschrauber":
        d.rectangle([3, 6, 11, 10], fill=(24, 24, 28, 255))
        d.rectangle([2, 7, 4, 9], fill=glas)
        d.rectangle([11, 7, 15, 8], fill=(24, 24, 28, 255))
        d.line([(1, 4), (14, 4)], fill=(60, 60, 66, 255))
        d.point((7, 5), fill=(200, 204, 210, 255))
        d.line([(3, 12), (11, 12)], fill=(200, 204, 210, 255))
        d.point((5, 11), fill=(200, 204, 210, 255))
        d.point((9, 11), fill=(200, 204, 210, 255))
        d.line([(4, 9), (10, 9)], fill=gold)
        return img
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
    de, en = dict(TEXTE_DE), dict(TEXTE_EN)
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
