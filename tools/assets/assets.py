#!/usr/bin/env python3
"""Erzeugt alle Grafiken und Datendateien der Mechanik-Blöcke (eigene Assets, nichts kopiert).

Schreibt nach src/main/resources:
  assets/stinkendreich/textures/block|item/*.png   selbst gezeichnete 16x16-Texturen
  assets/stinkendreich/models/block|item/*.json    Blockmodelle, Item-Modelle (bis 1.21.3)
  assets/stinkendreich/items/*.json                Item-Definitionen (ab 1.21.4)
  assets/stinkendreich/blockstates/*.json          Zustände (Richtung, an/aus, offen/zu)
  data/stinkendreich/loot_table/blocks/*.json      Blöcke lassen sich selbst fallen
  data/minecraft/tags/block/mineable/*.json        richtiges Werkzeug

Aufruf: python3 tools/assets/assets.py
"""
from __future__ import annotations

import json
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

WURZEL = Path(__file__).resolve().parents[2]
RES = WURZEL / "src/main/resources"
ASSETS = RES / "assets/stinkendreich"
NS = "stinkendreich"

# ---------------------------------------------------------------------------
# Texturen
# ---------------------------------------------------------------------------


def neu(farbe=(40, 42, 46)) -> tuple[Image.Image, ImageDraw.ImageDraw]:
    img = Image.new("RGBA", (16, 16), farbe + (255,))
    return img, ImageDraw.Draw(img)


def rauschen(img: Image.Image, staerke: int = 6, saat: int = 1) -> Image.Image:
    rnd = random.Random(saat)
    px = img.load()
    for x in range(16):
        for y in range(16):
            r, g, b, a = px[x, y]
            d = rnd.randint(-staerke, staerke)
            px[x, y] = (max(0, min(255, r + d)), max(0, min(255, g + d)), max(0, min(255, b + d)), a)
    return img


def rahmen(d: ImageDraw.ImageDraw, hell=(78, 82, 88), dunkel=(22, 23, 26)) -> None:
    d.line([(0, 0), (15, 0)], fill=hell)
    d.line([(0, 0), (0, 15)], fill=hell)
    d.line([(0, 15), (15, 15)], fill=dunkel)
    d.line([(15, 0), (15, 15)], fill=dunkel)


def metall(saat=1, farbe=(52, 55, 60)):
    img, d = neu(farbe)
    rauschen(img, 4, saat)
    rahmen(d)
    return img, d


def tex_technik_seite():
    img, d = metall(2)
    for y in (5, 10):
        d.line([(2, y), (13, y)], fill=(44, 46, 50))
    return img


def tex_kartenleser(an: bool):
    img, d = metall(3, (24, 25, 28))
    d.rectangle([3, 2, 12, 5], fill=(12, 12, 14))            # Kartenschlitz
    d.line([(4, 3), (11, 3)], fill=(2, 2, 3))
    d.rectangle([4, 8, 11, 12], fill=(30, 60, 90))            # Anzeige
    d.line([(5, 10), (10, 10)], fill=(90, 170, 230))
    led = (60, 230, 90) if an else (200, 40, 40)
    d.rectangle([7, 13, 8, 14], fill=led)
    return img


def tex_tastenfeld(an: bool):
    img, d = metall(4, (26, 27, 30))
    d.rectangle([3, 1, 12, 3], fill=(20, 70, 40) if not an else (40, 160, 80))
    for i in range(12):
        x = 3 + (i % 3) * 3
        y = 5 + (i // 3) * 3
        d.rectangle([x, y, x + 1, y + 1], fill=(190, 194, 200) if i != 11 else (80, 200, 100))
    return img


def tex_bewegungsmelder(an: bool):
    img, d = neu((220, 222, 224))
    rahmen(d, (240, 240, 240), (170, 172, 175))
    d.ellipse([3, 3, 12, 12], fill=(245, 245, 245), outline=(190, 190, 192))
    d.ellipse([6, 6, 9, 9], fill=(255, 120, 40) if an else (120, 120, 125))
    return img


def tex_alarmsirene():
    img, d = neu((150, 20, 20))
    rahmen(d, (200, 60, 60), (90, 10, 10))
    d.ellipse([2, 2, 13, 13], fill=(220, 40, 30))
    for x in range(4, 12, 2):
        d.line([(x, 4), (x, 11)], fill=(140, 20, 15))
    d.ellipse([6, 6, 9, 9], fill=(255, 210, 120))
    return img


def tex_aufzugstafel():
    img, d = neu((170, 174, 180))
    for y in range(16):
        d.line([(0, y), (15, y)], fill=(165 + (y % 3) * 4, 169 + (y % 3) * 4, 175 + (y % 3) * 4))
    rahmen(d, (210, 212, 216), (110, 112, 118))
    d.polygon([(8, 3), (5, 7), (11, 7)], fill=(255, 200, 60))
    d.polygon([(5, 9), (11, 9), (8, 13)], fill=(255, 200, 60))
    return img


def tex_kamera():
    img, d = neu((205, 208, 212))
    rauschen(img, 3, 5)
    rahmen(d, (235, 236, 238), (150, 152, 156))
    return img


def tex_kamera_linse():
    img, d = neu((20, 20, 24))
    d.ellipse([3, 3, 12, 12], fill=(10, 10, 12), outline=(60, 60, 70))
    d.ellipse([6, 6, 9, 9], fill=(40, 90, 200))
    d.point((7, 7), fill=(200, 230, 255))
    d.point((13, 2), fill=(230, 30, 30))
    return img


def tex_tuerklingel():
    img, d = neu((176, 140, 64))
    rauschen(img, 5, 6)
    rahmen(d, (220, 186, 100), (120, 90, 30))
    d.ellipse([5, 5, 10, 10], fill=(240, 236, 220), outline=(130, 100, 40))
    return img


def tex_kaminsteuerung():
    img, d = metall(7, (24, 24, 26))
    d.polygon([(8, 3), (11, 9), (8, 13), (5, 9)], fill=(240, 120, 30))
    d.polygon([(8, 7), (9, 10), (8, 12), (7, 10)], fill=(255, 220, 90))
    return img


def tex_zentrale_front():
    img, d = metall(8, (30, 32, 36))
    d.rectangle([2, 2, 13, 9], fill=(10, 24, 40))
    for i, y in enumerate((4, 6, 8)):
        d.line([(3, y), (6 + i * 2, y)], fill=(60, 220, 120))
    d.rectangle([10, 3, 12, 5], fill=(220, 60, 50))
    for i, f in enumerate(((60, 200, 90), (230, 200, 40), (230, 120, 30), (210, 40, 40))):
        d.rectangle([2 + i * 3, 11, 3 + i * 3, 13], fill=f)
    return img


def tex_monitor_front():
    img, d = neu((18, 18, 20))
    d.rectangle([1, 2, 14, 12], fill=(70, 90, 110))
    for y in range(3, 12, 2):
        d.line([(2, y), (13, y)], fill=(80, 102, 124))
    d.rectangle([2, 8, 6, 11], fill=(60, 120, 60))
    d.rectangle([8, 5, 12, 11], fill=(150, 150, 160))
    d.point((13, 3), fill=(240, 40, 40))
    d.line([(6, 14), (9, 14)], fill=(60, 60, 64))
    return img


def tex_laser_front():
    img, d = metall(9, (22, 22, 24))
    d.ellipse([4, 4, 11, 11], fill=(60, 0, 0), outline=(120, 120, 128))
    d.ellipse([6, 6, 9, 9], fill=(255, 40, 30))
    d.point((7, 7), fill=(255, 200, 190))
    return img


def tex_laserstrahl():
    img, d = neu((255, 30, 20))
    for x in range(16):
        d.point((x, 7), fill=(255, 180, 170))
        d.point((x, 8), fill=(255, 180, 170))
    return img


def tex_warnleuchte(an: bool):
    basis = (255, 50, 30) if an else (110, 20, 18)
    img, d = neu(basis)
    for i in range(0, 16, 4):
        d.line([(i, 0), (i, 15)], fill=tuple(int(c * 0.8) for c in basis))
        d.line([(0, i), (15, i)], fill=tuple(int(c * 0.8) for c in basis))
    if an:
        d.ellipse([5, 5, 10, 10], fill=(255, 230, 200))
    rahmen(d, (60, 60, 62), (30, 30, 32))
    return img


def tex_torsteuerung():
    img, d = metall(10, (60, 62, 66))
    for i in range(-16, 16, 4):
        d.polygon([(i, 15), (i + 2, 15), (i + 18, 0), (i + 16, 0)], fill=(230, 190, 30))
    d.rectangle([3, 3, 12, 12], fill=(48, 50, 54))
    d.rectangle([6, 6, 9, 9], fill=(90, 200, 90))
    return img


def tex_tor():
    img, d = neu((18, 18, 20))
    for x in (1, 5, 9, 13):
        d.rectangle([x, 0, x + 1, 15], fill=(55, 56, 60))
        d.line([(x, 0), (x, 15)], fill=(80, 82, 86))
    d.rectangle([0, 0, 15, 1], fill=(190, 150, 50))
    d.rectangle([0, 14, 15, 15], fill=(40, 40, 44))
    return img


def tex_lamellen(hell, dunkel, saat):
    img, d = neu(hell)
    for y in range(0, 16, 4):
        d.line([(0, y + 3), (15, y + 3)], fill=dunkel)
        d.line([(0, y), (15, y)], fill=tuple(min(255, c + 12) for c in hell))
    return rauschen(img, 2, saat)


def tex_schiebetuer():
    img, d = neu((176, 206, 220))
    for x in range(16):
        for y in range(16):
            if (x + y) % 7 == 0:
                d.point((x, y), fill=(196, 222, 234))
    d.rectangle([0, 0, 15, 15], outline=(60, 64, 70))
    d.line([(14, 6), (14, 9)], fill=(140, 144, 150))
    return img


def tex_panzertuer():
    img, d = neu((120, 124, 130))
    rauschen(img, 5, 11)
    d.rectangle([0, 0, 15, 15], outline=(80, 82, 88))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13), (7, 2), (7, 13)):
        d.point((x, y), fill=(190, 192, 196))
    d.line([(1, 7), (14, 7)], fill=(96, 98, 104))
    return img


def tex_gitter():
    img, d = neu((26, 26, 28))
    for i in range(0, 16, 4):
        d.rectangle([i + 1, 0, i + 2, 15], fill=(120, 122, 128))
        d.rectangle([0, i + 1, 15, i + 2], fill=(100, 102, 108))
    return img


def tex_poolabdeckung():
    img, d = neu((30, 52, 100))
    for y in range(0, 16, 3):
        d.line([(0, y), (15, y)], fill=(40, 66, 120))
    return rauschen(img, 3, 12)


def tex_whirlpool_oben():
    img, d = neu((226, 232, 236))
    d.rectangle([0, 0, 15, 15], outline=(190, 198, 204))
    for x, y in ((4, 4), (11, 4), (4, 11), (11, 11), (7, 7), (8, 8), (7, 8), (8, 7)):
        d.point((x, y), fill=(60, 90, 120))
    d.ellipse([5, 5, 10, 10], outline=(120, 160, 200))
    return img


def tex_zugangskarte():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([1, 4, 14, 12], radius=1, fill=(22, 22, 24, 255), outline=(200, 160, 50, 255))
    d.rectangle([3, 6, 5, 8], fill=(220, 180, 60, 255))     # Chip
    d.line([(7, 10), (12, 10)], fill=(200, 160, 50, 255))
    d.line([(7, 7), (12, 7)], fill=(90, 90, 96, 255))
    return img


TEXTUREN = {
    "technik_seite": tex_technik_seite,
    "kartenleser": lambda: tex_kartenleser(False),
    "kartenleser_an": lambda: tex_kartenleser(True),
    "tastenfeld": lambda: tex_tastenfeld(False),
    "tastenfeld_an": lambda: tex_tastenfeld(True),
    "bewegungsmelder": lambda: tex_bewegungsmelder(False),
    "bewegungsmelder_an": lambda: tex_bewegungsmelder(True),
    "alarmsirene": tex_alarmsirene,
    "aufzugstafel": tex_aufzugstafel,
    "kamera": tex_kamera,
    "kamera_linse": tex_kamera_linse,
    "tuerklingel": tex_tuerklingel,
    "kaminsteuerung": tex_kaminsteuerung,
    "sicherheitszentrale": tex_zentrale_front,
    "monitor": tex_monitor_front,
    "laser_sender": tex_laser_front,
    "laserstrahl": tex_laserstrahl,
    "warnleuchte": lambda: tex_warnleuchte(False),
    "warnleuchte_an": lambda: tex_warnleuchte(True),
    "torsteuerung": tex_torsteuerung,
    "tor": tex_tor,
    "garagentor": lambda: tex_lamellen((232, 234, 236), (170, 174, 178), 13),
    "rollladen": lambda: tex_lamellen((150, 154, 160), (96, 100, 106), 14),
    "schiebetuer": tex_schiebetuer,
    "panzertuer": tex_panzertuer,
    "gitterfalle": tex_gitter,
    "poolabdeckung": tex_poolabdeckung,
    "whirlpoolduese": tex_whirlpool_oben,
}

# ---------------------------------------------------------------------------
# Modelle
# ---------------------------------------------------------------------------

SEITE = f"{NS}:block/technik_seite"


def m_wandplatte(front: str, seite: str = SEITE) -> dict:
    """Flache Platte an der Wand, Vorderseite zeigt nach Norden (für facing=north)."""
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": front, "front": front, "seite": seite},
        "elements": [{
            "from": [2, 2, 14], "to": [14, 14, 16],
            "faces": {
                "north": {"uv": [2, 2, 14, 14], "texture": "#front"},
                "south": {"uv": [2, 2, 14, 14], "texture": "#seite", "cullface": "south"},
                "east": {"uv": [14, 2, 16, 14], "texture": "#seite"},
                "west": {"uv": [0, 2, 2, 14], "texture": "#seite"},
                "up": {"uv": [2, 14, 14, 16], "texture": "#seite"},
                "down": {"uv": [2, 0, 14, 2], "texture": "#seite"},
            },
        }],
    }


def m_kamera() -> dict:
    gehaeuse = f"{NS}:block/kamera"
    linse = f"{NS}:block/kamera_linse"
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": gehaeuse, "g": gehaeuse, "l": linse},
        "elements": [
            {"from": [6, 9, 14], "to": [10, 11, 16], "faces": {f: {"texture": "#g"} for f in ("north", "south", "east", "west", "up", "down")}},
            {"from": [5, 7, 6], "to": [11, 12, 14], "rotation": {"angle": -22.5, "axis": "x", "origin": [8, 10, 14]},
             "faces": {"north": {"texture": "#l"}, "south": {"texture": "#g"}, "east": {"texture": "#g"},
                       "west": {"texture": "#g"}, "up": {"texture": "#g"}, "down": {"texture": "#g"}}},
        ],
    }


def m_orientierbar(front: str, seite: str = SEITE, oben: str = SEITE) -> dict:
    return {"parent": "minecraft:block/orientable", "textures": {"front": front, "side": seite, "top": oben}}


def m_wuerfel(tex: str) -> dict:
    return {"parent": "minecraft:block/cube_all", "textures": {"all": tex}}


def m_saeule(seite: str, oben: str) -> dict:
    return {"parent": "minecraft:block/cube_column", "textures": {"side": seite, "end": oben}}


def m_flach(tex: str) -> dict:
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": tex, "t": tex},
        "elements": [{"from": [0, 0, 0], "to": [16, 2, 16], "faces": {
            "up": {"texture": "#t"}, "down": {"texture": "#t", "cullface": "down"},
            "north": {"uv": [0, 14, 16, 16], "texture": "#t"}, "south": {"uv": [0, 14, 16, 16], "texture": "#t"},
            "east": {"uv": [0, 14, 16, 16], "texture": "#t"}, "west": {"uv": [0, 14, 16, 16], "texture": "#t"}}}],
    }


def m_laserstrahl() -> dict:
    t = f"{NS}:block/laserstrahl"
    return {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "textures": {"particle": t, "t": t},
        "elements": [{"from": [7.5, 0, 7.5], "to": [8.5, 16, 8.5], "shade": False,
                      "faces": {f: {"texture": "#t"} for f in ("north", "south", "east", "west")}}],
    }


LEER = {"textures": {"particle": "minecraft:block/iron_block"}, "elements": []}

# Drehungen für Blockzustände
WAAGERECHT = {"north": 0, "east": 90, "south": 180, "west": 270}
ALLE = {"north": (0, 0), "east": (0, 90), "south": (0, 180), "west": (0, 270), "up": (270, 0), "down": (90, 0)}


def bs_waagerecht(modell: str, extra: dict | None = None) -> dict:
    """facing-Varianten, optional mit weiterer Eigenschaft (z. B. powered=true -> anderes Modell)."""
    varianten = {}
    for f, y in WAAGERECHT.items():
        if extra:
            for wert, m in extra.items():
                varianten[f"facing={f},{wert}"] = {"model": m, "y": y} if y else {"model": m}
        else:
            varianten[f"facing={f}"] = {"model": modell, "y": y} if y else {"model": modell}
    return {"variants": varianten}


def bs_alle(modell: str) -> dict:
    v = {}
    for f, (x, y) in ALLE.items():
        e = {"model": modell}
        if x:
            e["x"] = x
        if y:
            e["y"] = y
        v[f"facing={f}"] = e
    return {"variants": v}


def bs_einfach(modell: str) -> dict:
    return {"variants": {"": {"model": modell}}}


def bs_offen(modell: str) -> dict:
    return {"variants": {"open=false": {"model": modell}, "open=true": {"model": f"{NS}:block/leer"}}}


def B(name: str) -> str:
    return f"{NS}:block/{name}"


# Name -> (Blockmodelle {modellname: json}, Blockzustand, Item-Modell, Werkzeug, Deutsch, Englisch)
BLOECKE: dict[str, dict] = {
    "sicherheitszentrale": dict(modelle={"sicherheitszentrale": m_orientierbar(B("sicherheitszentrale"))},
                                zustand=bs_waagerecht(B("sicherheitszentrale")), de="Sicherheitszentrale", en="Security Console"),
    "laser_sender": dict(modelle={"laser_sender": {"parent": "minecraft:block/cube",
                                                     "textures": {"particle": SEITE, "north": B("laser_sender"), "south": SEITE,
                                                                  "east": SEITE, "west": SEITE, "up": SEITE, "down": SEITE}}},
                         zustand=bs_alle(B("laser_sender")), de="Laser-Sender", en="Laser Emitter"),
    "laserstrahl": dict(modelle={"laserstrahl": m_laserstrahl()},
                        zustand={"variants": {"axis=y": {"model": B("laserstrahl")},
                                              "axis=x": {"model": B("laserstrahl"), "x": 90, "y": 90},
                                              "axis=z": {"model": B("laserstrahl"), "x": 90}}},
                        item=False, loot=False, de="Laserstrahl", en="Laser Beam"),
    "bewegungsmelder": dict(modelle={"bewegungsmelder": m_wandplatte(B("bewegungsmelder"), B("bewegungsmelder")),
                                     "bewegungsmelder_an": m_wandplatte(B("bewegungsmelder_an"), B("bewegungsmelder"))},
                            zustand=bs_waagerecht("", {"powered=false": B("bewegungsmelder"), "powered=true": B("bewegungsmelder_an")}),
                            de="Bewegungsmelder", en="Motion Sensor"),
    "alarmsirene": dict(modelle={"alarmsirene": m_wandplatte(B("alarmsirene"))},
                        zustand=bs_waagerecht(B("alarmsirene")), de="Alarmsirene", en="Alarm Siren"),
    "warnleuchte": dict(modelle={"warnleuchte": m_wuerfel(B("warnleuchte")), "warnleuchte_an": m_wuerfel(B("warnleuchte_an"))},
                        zustand={"variants": {"lit=false": {"model": B("warnleuchte")}, "lit=true": {"model": B("warnleuchte_an")}}},
                        de="Warnleuchte", en="Warning Light"),
    "kartenleser": dict(modelle={"kartenleser": m_wandplatte(B("kartenleser")), "kartenleser_an": m_wandplatte(B("kartenleser_an"))},
                        zustand=bs_waagerecht("", {"powered=false": B("kartenleser"), "powered=true": B("kartenleser_an")}),
                        de="Kartenleser", en="Card Reader"),
    "tastenfeld": dict(modelle={"tastenfeld": m_wandplatte(B("tastenfeld")), "tastenfeld_an": m_wandplatte(B("tastenfeld_an"))},
                       zustand=bs_waagerecht("", {"powered=false": B("tastenfeld"), "powered=true": B("tastenfeld_an")}),
                       de="Tastenfeld", en="Keypad"),
    "tuerklingel": dict(modelle={"tuerklingel": m_wandplatte(B("tuerklingel"), B("tuerklingel"))},
                        zustand=bs_waagerecht(B("tuerklingel")), de="Türklingel", en="Doorbell"),
    "torsteuerung": dict(modelle={"torsteuerung": m_wuerfel(B("torsteuerung"))}, zustand=bs_einfach(B("torsteuerung")),
                         de="Torsteuerung", en="Gate Controller"),
    "tor": dict(modelle={"tor": m_wuerfel(B("tor"))}, zustand=bs_offen(B("tor")), de="Tor", en="Gate"),
    "garagentor": dict(modelle={"garagentor": m_wuerfel(B("garagentor"))}, zustand=bs_offen(B("garagentor")),
                       de="Garagentor", en="Garage Door"),
    "rollladen": dict(modelle={"rollladen": m_wuerfel(B("rollladen"))}, zustand=bs_offen(B("rollladen")),
                      de="Rollladen", en="Security Shutter"),
    "schiebetuer": dict(modelle={"schiebetuer": m_wuerfel(B("schiebetuer"))}, zustand=bs_offen(B("schiebetuer")),
                        de="Schiebetür", en="Sliding Door"),
    "panzertuer": dict(modelle={"panzertuer": m_wuerfel(B("panzertuer"))}, zustand=bs_offen(B("panzertuer")),
                       de="Panzertür", en="Blast Door"),
    "poolabdeckung": dict(modelle={"poolabdeckung": m_flach(B("poolabdeckung"))}, zustand=bs_offen(B("poolabdeckung")),
                          de="Poolabdeckung", en="Pool Cover"),
    "geheimregal": dict(modelle={"geheimregal": m_saeule("minecraft:block/bookshelf", "minecraft:block/oak_planks")},
                        zustand=bs_offen(B("geheimregal")), werkzeug="axe", de="Geheimregal", en="Secret Bookshelf"),
    "geheimschalter": dict(modelle={"geheimschalter": m_saeule("minecraft:block/bookshelf", "minecraft:block/oak_planks")},
                           zustand=bs_einfach(B("geheimschalter")), werkzeug="axe", de="Geheimschalter", en="Secret Switch"),
    "aufzugstafel": dict(modelle={"aufzugstafel": m_wandplatte(B("aufzugstafel"), B("aufzugstafel"))},
                         zustand=bs_waagerecht(B("aufzugstafel")), de="Aufzugstafel", en="Elevator Panel"),
    "fallenplatte": dict(modelle={"fallenplatte": m_wuerfel("minecraft:block/gold_block")}, zustand=bs_einfach(B("fallenplatte")),
                         de="Fallenplatte", en="Trap Plate"),
    "gitterfalle": dict(modelle={"gitterfalle": m_wuerfel(B("gitterfalle"))}, zustand=bs_offen(B("gitterfalle")),
                        de="Gitterfalle", en="Cage Trap"),
    "entschaerfknopf": dict(modelle={"entschaerfknopf": m_wuerfel("minecraft:block/deepslate_bricks")},
                            zustand=bs_einfach(B("entschaerfknopf")), de="Entschärfknopf", en="Hidden Disarm Button"),
    "kamera": dict(modelle={"kamera": m_kamera()}, zustand=bs_waagerecht(B("kamera")), de="Überwachungskamera", en="Security Camera"),
    "monitor": dict(modelle={"monitor": m_orientierbar(B("monitor"), "minecraft:block/black_concrete", "minecraft:block/black_concrete")},
                    zustand=bs_waagerecht(B("monitor")), de="Überwachungsmonitor", en="Security Monitor"),
    "whirlpoolduese": dict(modelle={"whirlpoolduese": m_saeule("minecraft:block/quartz_block_side", B("whirlpoolduese"))},
                           zustand=bs_einfach(B("whirlpoolduese")), de="Whirlpool-Düse", en="Whirlpool Jet"),
    "kaminsteuerung": dict(modelle={"kaminsteuerung": m_wandplatte(B("kaminsteuerung"))},
                           zustand=bs_waagerecht(B("kaminsteuerung")), de="Kaminschalter", en="Fireplace Switch"),
}

TEXTE_DE = {
    "itemGroup.stinkendreich": "Stinkend Reich",
    "item.stinkendreich.zugangskarte": "Zugangskarte",
    "stinkendreich.stufe.0": "Stufe 0 – Aus",
    "stinkendreich.stufe.1": "Stufe 1 – Alarm",
    "stinkendreich.stufe.2": "Stufe 2 – Lockdown",
    "stinkendreich.stufe.3": "Stufe 3 – Laser",
    "stinkendreich.stufe.gesetzt": "Sicherheitssystem: %s",
    "stinkendreich.alarm.start": "ALARM! %s (bei %s %s %s)",
    "stinkendreich.alarm.ende": "Alarm beendet.",
    "stinkendreich.alarm.an": "Alarm",
    "stinkendreich.alarm.aus": "ruhig",
    "stinkendreich.alarm.grund.zentrale": "%s hat die Zentrale ohne Berechtigung benutzt",
    "stinkendreich.alarm.grund.laser": "%s hat einen Laser durchbrochen",
    "stinkendreich.alarm.grund.bewegung": "Bewegung erkannt: %s",
    "stinkendreich.alarm.grund.karte": "%s hat ohne Karte versucht, eine Tür zu öffnen",
    "stinkendreich.alarm.grund.code": "%s hat dreimal einen falschen Code eingegeben",
    "stinkendreich.alarm.grund.tresor": "%s hat die Tresorfalle ausgelöst",
    "stinkendreich.zentrale.status": "Sicherheitssystem: %s · Zustand: %s · Bewohner: %s",
    "stinkendreich.zentrale.eingetragen": "Du bist jetzt Bewohner der Villa.",
    "stinkendreich.zentrale.alarm_aus": "Alarm abschalten",
    "stinkendreich.zentrale.eintragen": "Mich als Bewohner eintragen",
    "stinkendreich.zugang.verweigert": "Zugriff verweigert.",
    "stinkendreich.zugang.karte": "Karte erkannt – Tür offen.",
    "stinkendreich.zugang.keine_karte": "Keine Zugangskarte!",
    "stinkendreich.zugang.code": "Code richtig – Tür offen.",
    "stinkendreich.zugang.falscher_code": "Falscher Code!",
    "stinkendreich.zugang.code_geaendert": "Neuer Code gespeichert.",
    "stinkendreich.zugang.code_nicht_geaendert": "Code nicht geändert (alter Code falsch, kein Bewohner oder neuer Code nicht 4–8 Ziffern).",
    "stinkendreich.tresor.falle": "FALLE! Der Tresor ist verriegelt.",
    "stinkendreich.tresor.entschaerft": "Tresorfallen für 60 Sekunden entschärft.",
    "stinkendreich.tresor.knopf": "Klick … noch %s Knopf",
    "stinkendreich.klingel": "Es klingelt am Tor: %s",
    "stinkendreich.tastenfeld.aendern": "Code ändern",
    "stinkendreich.tastenfeld.code": "Code",
    "stinkendreich.tastenfeld.alt": "Alter Code",
    "stinkendreich.tastenfeld.neu": "Neuer Code",
    "stinkendreich.kamera.beenden": "Überwachung beenden",
    "stinkendreich.kamera.keine": "Keine Kameras in der Nähe.",
    "stinkendreich.kamera.nummer": "Kamera %s von %s",
}
TEXTE_EN = {
    "itemGroup.stinkendreich": "Filthy Rich",
    "item.stinkendreich.zugangskarte": "Access Card",
    "stinkendreich.stufe.0": "Level 0 – Off",
    "stinkendreich.stufe.1": "Level 1 – Alarm",
    "stinkendreich.stufe.2": "Level 2 – Lockdown",
    "stinkendreich.stufe.3": "Level 3 – Lasers",
    "stinkendreich.stufe.gesetzt": "Security system: %s",
    "stinkendreich.alarm.start": "ALARM! %s (at %s %s %s)",
    "stinkendreich.alarm.ende": "Alarm ended.",
    "stinkendreich.alarm.an": "alarm",
    "stinkendreich.alarm.aus": "quiet",
    "stinkendreich.alarm.grund.zentrale": "%s used the console without permission",
    "stinkendreich.alarm.grund.laser": "%s broke a laser beam",
    "stinkendreich.alarm.grund.bewegung": "Motion detected: %s",
    "stinkendreich.alarm.grund.karte": "%s tried to open a door without a card",
    "stinkendreich.alarm.grund.code": "%s entered a wrong code three times",
    "stinkendreich.alarm.grund.tresor": "%s triggered the vault trap",
    "stinkendreich.zentrale.status": "Security system: %s · State: %s · Residents: %s",
    "stinkendreich.zentrale.eingetragen": "You are now a resident of the villa.",
    "stinkendreich.zentrale.alarm_aus": "Turn off alarm",
    "stinkendreich.zentrale.eintragen": "Register me as resident",
    "stinkendreich.zugang.verweigert": "Access denied.",
    "stinkendreich.zugang.karte": "Card accepted – door open.",
    "stinkendreich.zugang.keine_karte": "No access card!",
    "stinkendreich.zugang.code": "Code correct – door open.",
    "stinkendreich.zugang.falscher_code": "Wrong code!",
    "stinkendreich.zugang.code_geaendert": "New code saved.",
    "stinkendreich.zugang.code_nicht_geaendert": "Code not changed (old code wrong, not a resident, or new code not 4–8 digits).",
    "stinkendreich.tresor.falle": "TRAP! The vault is locked.",
    "stinkendreich.tresor.entschaerft": "Vault traps disarmed for 60 seconds.",
    "stinkendreich.tresor.knopf": "Click … %s button(s) left",
    "stinkendreich.klingel": "Someone is ringing at the gate: %s",
    "stinkendreich.tastenfeld.aendern": "Change code",
    "stinkendreich.tastenfeld.code": "Code",
    "stinkendreich.tastenfeld.alt": "Old code",
    "stinkendreich.tastenfeld.neu": "New code",
    "stinkendreich.kamera.beenden": "Stop watching",
    "stinkendreich.kamera.keine": "No cameras nearby.",
    "stinkendreich.kamera.nummer": "Camera %s of %s",
}


def schreibe_json(pfad: Path, daten) -> None:
    pfad.parent.mkdir(parents=True, exist_ok=True)
    pfad.write_text(json.dumps(daten, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main() -> None:
    # Texturen
    for name, fabrik in TEXTUREN.items():
        pfad = ASSETS / "textures/block" / f"{name}.png"
        pfad.parent.mkdir(parents=True, exist_ok=True)
        fabrik().save(pfad)
    (ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
    tex_zugangskarte().save(ASSETS / "textures/item/zugangskarte.png")

    schreibe_json(ASSETS / "models/block/leer.json", LEER)
    werkzeuge: dict[str, list[str]] = {"pickaxe": [], "axe": []}
    for name, b in BLOECKE.items():
        for modell, inhalt in b["modelle"].items():
            schreibe_json(ASSETS / "models/block" / f"{modell}.json", inhalt)
        schreibe_json(ASSETS / "blockstates" / f"{name}.json", b["zustand"])
        if b.get("item", True):
            # bis 1.21.3: Item-Modell; ab 1.21.4: Item-Definition
            schreibe_json(ASSETS / "models/item" / f"{name}.json", {"parent": B(name)})
            schreibe_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": B(name)}})
        if b.get("loot", True):
            schreibe_json(RES / f"data/{NS}/loot_table/blocks/{name}.json", {
                "type": "minecraft:block",
                "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                           "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            })
            werkzeuge[b.get("werkzeug", "pickaxe")].append(f"{NS}:{name}")
        TEXTE_DE[f"block.{NS}.{name}"] = b["de"]
        TEXTE_EN[f"block.{NS}.{name}"] = b["en"]

    schreibe_json(ASSETS / "models/item/zugangskarte.json",
                  {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/zugangskarte"}})
    schreibe_json(ASSETS / "items/zugangskarte.json",
                  {"model": {"type": "minecraft:model", "model": f"{NS}:item/zugangskarte"}})
    for werkzeug, liste in werkzeuge.items():
        schreibe_json(RES / f"data/minecraft/tags/block/mineable/{werkzeug}.json", {"replace": False, "values": liste})

    # Sprachdateien ergänzen (vorhandene Einträge bleiben)
    for datei, texte in (("de_de", TEXTE_DE), ("en_us", TEXTE_EN)):
        pfad = ASSETS / "lang" / f"{datei}.json"
        vorhanden = json.loads(pfad.read_text(encoding="utf-8")) if pfad.exists() else {}
        vorhanden.update(texte)
        schreibe_json(pfad, dict(sorted(vorhanden.items())))
    print(f"{len(TEXTUREN) + 1} Texturen, {len(BLOECKE)} Blöcke geschrieben")


if __name__ == "__main__":
    main()
