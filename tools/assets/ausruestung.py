#!/usr/bin/env python3
"""Agenten-Ausrüstung: Bond-Sonnenbrille, Smoking, Anzughose, Lackschuhe und die Agenten-Waffen.

Schreibt nach src/main/resources/assets/stinkendreich:
  textures/item/<name>.png, models/item/<name>.json, items/<name>.json
  Rüstung am Körper (dieselben Bilder für alle Minecraft-Versionen):
    textures/models/armor/agent_layer_1.png, agent_layer_2.png          (bis 1.21.1)
    textures/entity/equipment/humanoid/agent.png, humanoid_leggings/agent.png   (ab 1.21.2)
    models/equipment/agent.json (1.21.2–1.21.3) und equipment/agent.json (ab 1.21.4)

Die Körpertexturen haben das klassische 64 × 32-Rüstungsformat: Kopf (0, 0), Körper (16, 16), Arme (40, 16),
Beine (0, 16). Was durchsichtig bleibt, wird nicht gezeichnet (so sitzt die Brille nur vor den Augen).
Wird von tools/assets/assets.py aufgerufen.
"""
from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw

WURZEL = Path(__file__).resolve().parents[2]
ASSETS = WURZEL / "src/main/resources/assets/stinkendreich"
NS = "stinkendreich"

SCHWARZ = (18, 18, 22, 255)
SATIN = (52, 52, 64, 255)
WEISS = (244, 244, 240, 255)
GOLD = (232, 186, 52, 255)
GOLD_DUNKEL = (160, 112, 22, 255)
GLAS = (30, 34, 44, 255)
GLANZ = (120, 130, 150, 255)
# Pixel-Sonnenbrille in Goldgelb: Grundfarbe, Schatten unten, Lichtreflex
BRILLE = (246, 198, 32, 255)
BRILLE_SCHATTEN = (196, 140, 14, 255)
BRILLE_GLANZ = (255, 246, 196, 255)

# Auflösung der Körperschicht 1 (Kopf, Smoking, Schuhe): vierfach, damit die Brille fein genug wird
FEIN = 4

TEXTE = {
    "de": {
        "item.stinkendreich.bond_sonnenbrille": "Bond-Sonnenbrille",
        "item.stinkendreich.smoking": "Smoking",
        "item.stinkendreich.anzughose": "Anzughose",
        "item.stinkendreich.lackschuhe": "Lackschuhe",
        "item.stinkendreich.goldene_pistole": "Goldene Pistole",
        "item.stinkendreich.laseruhr": "Laser-Armbanduhr",
        "item.stinkendreich.geldkanone": "Geldkanone",
        "stinkendreich.agent.brille": "Nachtsicht · Agentenblick: Gegner leuchten durch Wände",
        "stinkendreich.agent.smoking": "Resistenz II · Feuerfest",
        "stinkendreich.agent.hose": "Schnelligkeit II",
        "stinkendreich.agent.schuhe": "Sprungkraft II · kein Fallschaden",
        "stinkendreich.agent.set": "Ganzes Set (Lizenz zum Töten): Stärke II, Regeneration",
        "stinkendreich.agent.pistole": "Rechtsklick: Schuss (8 Schaden, 64 Blöcke)",
        "stinkendreich.agent.laser": "Rechtsklick: Laserstrahl (12 Schaden, setzt in Brand)",
        "stinkendreich.agent.geld": "Rechtsklick: Geldregen – blendet und bremst Gegner",
    },
    "en": {
        "item.stinkendreich.bond_sonnenbrille": "Agent Sunglasses",
        "item.stinkendreich.smoking": "Tuxedo",
        "item.stinkendreich.anzughose": "Suit Trousers",
        "item.stinkendreich.lackschuhe": "Patent Leather Shoes",
        "item.stinkendreich.goldene_pistole": "Golden Pistol",
        "item.stinkendreich.laseruhr": "Laser Wristwatch",
        "item.stinkendreich.geldkanone": "Money Cannon",
        "stinkendreich.agent.brille": "Night vision · agent sight: enemies glow through walls",
        "stinkendreich.agent.smoking": "Resistance II · fireproof",
        "stinkendreich.agent.hose": "Speed II",
        "stinkendreich.agent.schuhe": "Jump boost II · no fall damage",
        "stinkendreich.agent.set": "Full set (licence to kill): Strength II, regeneration",
        "stinkendreich.agent.pistole": "Right-click: shot (8 damage, 64 blocks)",
        "stinkendreich.agent.laser": "Right-click: laser beam (12 damage, sets on fire)",
        "stinkendreich.agent.geld": "Right-click: money shower – blinds and slows enemies",
    },
}


# ---------------------------------------------------------------------------
# Item-Bilder (16 × 16)
# ---------------------------------------------------------------------------


def neu():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


# Die berühmte Pixel-Sonnenbrille („Deal with it“), spiegelgleich, 16 Brillenpixel breit:
# B Balken/Glas, L Lichtreflex, S Schatten, . frei. Zeile 0 ist der Balken über die ganze Breite.
BRILLE_MUSTER = [
    "BBBBBBBBBBBBBBBB",
    ".BLLBBB..BBBLLB.",
    "..BLBB....BBLB..",
    "...SS......SS...",
]
BRILLE_FARBE = {"B": BRILLE, "L": BRILLE_GLANZ, "S": BRILLE_SCHATTEN}


def brille_zeichnen(d: ImageDraw.ImageDraw, x0: int, y0: int, pixel: int) -> None:
    """Zeichnet das Brillenmuster ab (x0, y0), jedes Brillenpixel pixel × pixel groß."""
    for zy, zeile in enumerate(BRILLE_MUSTER):
        for zx, zeichen in enumerate(zeile):
            if zeichen in BRILLE_FARBE:
                d.rectangle([x0 + zx * pixel, y0 + zy * pixel, x0 + (zx + 1) * pixel - 1, y0 + (zy + 1) * pixel - 1],
                            fill=BRILLE_FARBE[zeichen])


def i_brille():
    img, d = neu()
    brille_zeichnen(d, 0, 6, 1)
    return img


def i_smoking():
    img, d = neu()
    d.polygon([(2, 2), (6, 1), (9, 1), (13, 2), (14, 14), (1, 14)], fill=SCHWARZ)
    d.polygon([(6, 1), (9, 1), (8, 9), (7, 9)], fill=WEISS)
    d.polygon([(5, 2), (7, 9), (4, 6)], fill=SATIN)
    d.polygon([(10, 2), (8, 9), (11, 6)], fill=SATIN)
    d.rectangle([6, 2, 9, 3], fill=SCHWARZ)
    d.point((7, 2), fill=(60, 60, 70, 255))
    for y in (10, 12):
        d.point((7, y), fill=GOLD)
    d.rectangle([1, 13, 2, 14], fill=WEISS)
    d.rectangle([13, 13, 14, 14], fill=WEISS)
    return img


def i_hose():
    img, d = neu()
    d.rectangle([3, 1, 12, 3], fill=SCHWARZ)
    d.line([(3, 2), (12, 2)], fill=GOLD_DUNKEL)
    d.rectangle([3, 4, 7, 15], fill=SCHWARZ)
    d.rectangle([8, 4, 12, 15], fill=SCHWARZ)
    d.line([(3, 4), (3, 15)], fill=SATIN)
    d.line([(12, 4), (12, 15)], fill=SATIN)
    d.line([(7, 6), (7, 15)], fill=(8, 8, 10, 255))
    return img


def i_schuhe():
    img, d = neu()
    for x0, y0 in ((1, 6), (8, 9)):
        d.polygon([(x0, y0 + 4), (x0 + 1, y0 + 1), (x0 + 3, y0), (x0 + 5, y0 + 2), (x0 + 7, y0 + 3), (x0 + 7, y0 + 5), (x0, y0 + 5)], fill=SCHWARZ)
        d.line([(x0 + 1, y0 + 2), (x0 + 3, y0 + 1)], fill=GLANZ)
        d.line([(x0, y0 + 5), (x0 + 7, y0 + 5)], fill=(60, 40, 30, 255))
    return img


def i_pistole():
    img, d = neu()
    d.rectangle([2, 4, 13, 7], fill=GOLD)
    d.line([(2, 4), (13, 4)], fill=(255, 228, 120, 255))
    d.rectangle([13, 5, 14, 6], fill=(40, 40, 44, 255))
    d.polygon([(4, 8), (8, 8), (7, 14), (3, 14)], fill=GOLD_DUNKEL)
    d.rectangle([4, 9, 6, 13], fill=(90, 60, 30, 255))
    d.line([(8, 8), (9, 10)], fill=GOLD_DUNKEL)
    d.point((3, 3), fill=GOLD)
    return img


def i_laseruhr():
    img, d = neu()
    d.rectangle([5, 0, 10, 4], fill=(40, 40, 46, 255))
    d.rectangle([5, 11, 10, 15], fill=(40, 40, 46, 255))
    d.ellipse([3, 3, 12, 12], fill=GOLD)
    d.ellipse([5, 5, 10, 10], fill=(16, 22, 40, 255))
    d.line([(7, 7), (7, 5)], fill=WEISS)
    d.line([(7, 7), (9, 7)], fill=WEISS)
    d.point((10, 5), fill=(255, 40, 40, 255))
    d.point((13, 7), fill=GOLD_DUNKEL)
    return img


def i_geldkanone():
    img, d = neu()
    d.rectangle([3, 5, 13, 9], fill=GOLD)
    d.ellipse([11, 4, 15, 10], fill=GOLD_DUNKEL)
    d.ellipse([12, 5, 14, 9], fill=(30, 30, 30, 255))
    d.rectangle([4, 10, 6, 14], fill=(90, 60, 30, 255))
    for x, y in ((1, 2), (5, 1), (9, 2)):
        d.rectangle([x, y, x + 2, y + 1], fill=(110, 170, 100, 255))
    d.line([(3, 6), (12, 6)], fill=(255, 228, 120, 255))
    return img


ITEMS = {"bond_sonnenbrille": i_brille, "smoking": i_smoking, "anzughose": i_hose, "lackschuhe": i_schuhe,
         "goldene_pistole": i_pistole, "laseruhr": i_laseruhr, "geldkanone": i_geldkanone}


# ---------------------------------------------------------------------------
# Rüstung am Körper (64 × 32)
# ---------------------------------------------------------------------------


# Vorderseite des Smokings (8 × 12 Pixel, Körper-Vorderseite bei x 20–27, y 20–31):
# K Jacke, S Satin-Revers, W Hemd, B Fliege, G Goldknopf, E Einstecktuch
SMOKING_FRONT = [
    "KSWBBWSK",
    "KSBBBBSK",
    "KSSWWSSK",
    "KKSWWSEK",
    "KKSWWSKK",
    "KKKWWKKK",
    "KKKWWKKK",
    "KKKGKKKK",
    "KKKKKKKK",
    "KKKGKKKK",
    "KKKKKKKK",
    "KKKKKKKK",
]
FARBE = {"K": SCHWARZ, "S": SATIN, "W": WEISS, "B": (6, 6, 8, 255), "G": GOLD, "E": WEISS}


def schicht_1() -> Image.Image:
    """Sonnenbrille (Kopf), Smoking (Körper und Arme), Lackschuhe (Füße).

    Aufteilung wie bei jeder Rüstung: Kopf ab (0, 0) mit Gesicht bei x 8–15, y 8–15 (Augen in Reihe 12, bei manchen Skins 13),
    Körper ab (16, 16), Arme ab (40, 16), Beine ab (0, 16). Durchsichtig bleibt unsichtbar."""
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # --- Smoking ---
    d.rectangle([16, 20, 39, 31], fill=SCHWARZ)           # Seiten und Rücken
    d.rectangle([20, 16, 35, 19], fill=SCHWARZ)           # Schultern oben, Saum unten
    for y, reihe in enumerate(SMOKING_FRONT):
        for x, z in enumerate(reihe):
            d.point((20 + x, 20 + y), fill=FARBE[z])
    d.line([(32, 20), (39, 20)], fill=WEISS)              # Hemdkragen hinten
    d.point((35, 30), fill=SATIN)                         # Rückenschlitz
    d.point((35, 31), fill=SATIN)
    # Ärmel (40–55 × 20–31): schwarz bis kurz vor die Hand, weiße Manschette mit Goldknopf, Hand frei
    d.rectangle([40, 20, 55, 29], fill=SCHWARZ)
    d.rectangle([44, 16, 47, 19], fill=SCHWARZ)           # Schulter oben
    d.line([(40, 29), (55, 29)], fill=WEISS)
    d.point((45, 29), fill=GOLD)
    d.point((49, 29), fill=GOLD)
    # --- Lackschuhe: untere drei Reihen der Beine (0–15 × 29–31), Glanz vorn, Sohle unten ---
    d.rectangle([0, 29, 15, 31], fill=SCHWARZ)
    d.line([(4, 29), (7, 29)], fill=GLANZ)
    d.point((5, 30), fill=(70, 76, 92, 255))
    d.line([(0, 31), (15, 31)], fill=(30, 22, 18, 255))   # Sohlenkante
    d.rectangle([8, 16, 11, 19], fill=(40, 28, 20, 255))  # Sohle (Unterseite)
    # --- Vierfache Auflösung (Minecraft rechnet die Lage auf dem Körper anteilig um), dann die Brille:
    #     Gesicht bei x 32–63, y 32–63; ein Brillenpixel = 2 × 2; Balken knapp über den Augen ---
    img = img.resize((64 * FEIN, 32 * FEIN), Image.NEAREST)
    d = ImageDraw.Draw(img)
    brille_zeichnen(d, 8 * FEIN, 46, 2)
    d.rectangle([4 * FEIN, 46, 8 * FEIN - 1, 47], fill=BRILLE)     # Bügel rechts (Kopfseite, vorn bei x 31)
    d.rectangle([16 * FEIN, 46, 20 * FEIN - 1, 47], fill=BRILLE)   # Bügel links (vorn bei x 64)
    return img


def schicht_2() -> Image.Image:
    """Anzughose: Beine mit Satin-Galonstreifen außen und Bügelfalte, Bund mit Goldschnalle."""
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 20, 15, 31], fill=SCHWARZ)
    d.rectangle([4, 16, 11, 19], fill=SCHWARZ)
    d.line([(1, 20), (1, 31)], fill=(62, 62, 74, 255))    # Galonstreifen (Außenseite, auch gespiegelt)
    d.line([(2, 20), (2, 31)], fill=SATIN)
    d.line([(6, 21), (6, 31)], fill=(8, 8, 10, 255))      # Bügelfalte vorn
    d.rectangle([16, 26, 39, 31], fill=SCHWARZ)           # Bund am Körper
    d.rectangle([20, 16, 35, 19], fill=SCHWARZ)
    d.line([(16, 26), (39, 26)], fill=SATIN)
    d.rectangle([23, 26, 24, 27], fill=GOLD)
    return img


def schreibe_json(pfad: Path, daten) -> None:
    pfad.parent.mkdir(parents=True, exist_ok=True)
    pfad.write_text(json.dumps(daten, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main() -> dict:
    for name, fabrik in ITEMS.items():
        pfad = ASSETS / "textures/item" / f"{name}.png"
        pfad.parent.mkdir(parents=True, exist_ok=True)
        fabrik().save(pfad)
        schreibe_json(ASSETS / "models/item" / f"{name}.json",
                      {"parent": "minecraft:item/generated" if name not in ("goldene_pistole", "geldkanone") else "minecraft:item/handheld",
                       "textures": {"layer0": f"{NS}:item/{name}"}})
        schreibe_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
    s1, s2 = schicht_1(), schicht_2()
    for pfad, bild in ((ASSETS / "textures/models/armor/agent_layer_1.png", s1), (ASSETS / "textures/models/armor/agent_layer_2.png", s2),
                       (ASSETS / "textures/entity/equipment/humanoid/agent.png", s1),
                       (ASSETS / "textures/entity/equipment/humanoid_leggings/agent.png", s2)):
        pfad.parent.mkdir(parents=True, exist_ok=True)
        bild.save(pfad)
    ausruestung = {"layers": {"humanoid": [{"texture": f"{NS}:agent"}], "humanoid_leggings": [{"texture": f"{NS}:agent"}]}}
    schreibe_json(ASSETS / "models/equipment/agent.json", ausruestung)
    schreibe_json(ASSETS / "equipment/agent.json", ausruestung)
    return TEXTE


if __name__ == "__main__":
    main()
