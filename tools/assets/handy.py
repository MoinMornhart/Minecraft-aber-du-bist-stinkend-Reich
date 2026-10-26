#!/usr/bin/env python3
"""Texturen und Texte für 0.3: goldenes Handy, Mitarbeiterkiste, Mitarbeiter (Latzhose, Bauhelm) und Bodyguard
(Eisengolem im Smoking mit Sonnenbrille und goldener Pistole).

Mitarbeiter: 64×64 im Layout des Butler-Modells (oben wie ein Spieler-Skin, die Frackteile darunter bleiben
durchsichtig). Bodyguard: 128×128 im Layout des Eisengolems (Kopf 0,0 · Nase 24,0 · Körper 0,40 · Hüfte 0,70 ·
rechter Arm 60,21 · linker Arm 60,58 · Beine 37,0 und 60,0), komplett selbst gemalt.

Aufruf: über assets.py (main() gibt die Texte zurück)
"""
from __future__ import annotations

import json
import random
from pathlib import Path

from PIL import Image, ImageDraw

WURZEL = Path(__file__).resolve().parents[2]
ASSETS = WURZEL / "src/main/resources/assets/stinkendreich"
NS = "stinkendreich"

GOLD, GOLD_HELL, GOLD_DUNKEL = (232, 186, 52), (255, 228, 122), (160, 112, 22)

TEXTE_DE = {
    "item.stinkendreich.handy": "Goldenes Handy",
    "item.stinkendreich.mitarbeiterkiste": "Mitarbeiterkiste",
    "entity.stinkendreich.mitarbeiter": "Mitarbeiter",
    "entity.stinkendreich.bodyguard": "Bodyguard",
    "stinkendreich.bodyguard.name": "Bodyguard",
    "stinkendreich.bodyguard.da": "Ihre Bodyguards sind da, Chef.",
    "stinkendreich.bodyguard.weg": "Die Bodyguards gehen nach Hause.",
    "stinkendreich.mitarbeiter.da": "Drei Mitarbeiter sind eingetroffen: Diamanten, Eisen und Kohle. Rechtsklick auf einen wechselt seine Aufgabe.",
    "stinkendreich.handy.mitarbeiter.da": "Ihre Mitarbeiterkiste ist im Inventar. Stellen Sie sie dort auf, wo gearbeitet werden soll.",
    "stinkendreich.mitarbeiter.aufgabe": "%s sucht jetzt: %s",
    "stinkendreich.mitarbeiter.fremd": "%s arbeitet nicht für dich.",
    "stinkendreich.handy.titel.start": "Stinkend Reich",
    "stinkendreich.handy.titel.navi": "Navigation",
    "stinkendreich.handy.titel.lieferung": "Lieferservice",
    "stinkendreich.handy.titel.auftrag": "Aufträge an James",
    "stinkendreich.handy.anruf_limo": "Limo rufen",
    "stinkendreich.handy.anruf_heli": "Hubschrauber rufen",
    "stinkendreich.handy.navi": "Navigation",
    "stinkendreich.handy.lieferung": "Lieferservice",
    "stinkendreich.handy.auftraege": "Aufträge",
    "stinkendreich.handy.mitarbeiter": "Mitarbeiter bestellen",
    "stinkendreich.handy.bodyguards": "Bodyguards rufen",
    "stinkendreich.handy.bodyguards_heim": "Bodyguards heim",
    "stinkendreich.handy.zurueck": "← Zurück",
    "stinkendreich.handy.navi.villa": "Nach Hause (Villa)",
    "stinkendreich.handy.navi.dorf": "Zum Dorf",
    "stinkendreich.handy.navi.endportal": "Zum Endportal",
    "stinkendreich.handy.navi.netherportal": "Zum Netherportal",
    "stinkendreich.handy.navi.mine": "Zur Mine",
    "stinkendreich.handy.navi.ort": "Gemerkter Ort …",
    "stinkendreich.handy.navi.los": "Dorthin fahren",
    "stinkendreich.handy.liefer.kuehe": "2 Kühe",
    "stinkendreich.handy.liefer.schweine": "2 Schweine",
    "stinkendreich.handy.liefer.schafe": "2 Schafe",
    "stinkendreich.handy.liefer.huehner": "4 Hühner",
    "stinkendreich.handy.liefer.hund": "Hund (zahm)",
    "stinkendreich.handy.liefer.katze": "Katze (zahm)",
    "stinkendreich.handy.liefer.papagei": "Papagei (zahm)",
    "stinkendreich.handy.liefer.festessen": "Festessen",
    "stinkendreich.handy.liefer.feuerwerk": "64 Raketen",
    "stinkendreich.handy.liefer.gold": "Ein Sack Gold",
    "stinkendreich.handy.auftrag.eisen": "32 Eisen holen",
    "stinkendreich.handy.auftrag.eisen.text": "hol 32 eisen",
    "stinkendreich.handy.auftrag.diamanten": "16 Diamanten holen",
    "stinkendreich.handy.auftrag.diamanten.text": "hol 16 diamanten",
    "stinkendreich.handy.auftrag.holz": "64 Holz holen",
    "stinkendreich.handy.auftrag.holz.text": "hol 64 holz",
    "stinkendreich.handy.auftrag.essen": "Essen kochen",
    "stinkendreich.handy.auftrag.essen.text": "koch essen",
    "stinkendreich.handy.auftrag.sortieren": "Truhen sortieren",
    "stinkendreich.handy.auftrag.sortieren.text": "sortier die truhen",
    "stinkendreich.handy.auftrag.heim": "Nach Hause gehen",
    "stinkendreich.handy.auftrag.heim.text": "geh nach hause",
    "stinkendreich.handy.auftrag.frei": "Eigener Auftrag …",
    "stinkendreich.handy.auftrag.senden": "Auftrag senden",
    "stinkendreich.handy.unterwegs": "Sehr wohl, Sir! Ich bin mit der Limo unterwegs zu Ihnen.",
    "stinkendreich.handy.lieferung.unterwegs": "Ihre Bestellung ist unterwegs, Sir.",
    "stinkendreich.handy.da": "Ihr Wagen, Sir: %s. Bitte steigen Sie ein.",
    "stinkendreich.handy.lieferung.da": "Ihre Lieferung, Sir: %s.",
    "stinkendreich.handy.lieferung.nichts": "nichts",
    "stinkendreich.handy.lieferung.kuehe": "2 Kühe",
    "stinkendreich.handy.lieferung.schweine": "2 Schweine",
    "stinkendreich.handy.lieferung.schafe": "2 Schafe",
    "stinkendreich.handy.lieferung.huehner": "4 Hühner",
    "stinkendreich.handy.lieferung.hund": "ein Hund (er gehört schon Ihnen)",
    "stinkendreich.handy.lieferung.katze": "eine Katze (sie gehört schon Ihnen)",
    "stinkendreich.handy.lieferung.papagei": "ein Papagei (er gehört schon Ihnen)",
    "stinkendreich.handy.lieferung.festessen": "ein Festessen (in Ihrem Inventar)",
    "stinkendreich.handy.lieferung.feuerwerk": "64 Feuerwerksraketen (in Ihrem Inventar)",
    "stinkendreich.handy.lieferung.gold": "Gold (in Ihrem Inventar)",
    "stinkendreich.handy.status.unterwegs": "Ist unterwegs zu dir",
    "stinkendreich.handy.status.faehrtvor": "Fährt vor",
    # Server-Version: Dialogfenster für Spieler ohne Mod
    "stinkendreich.ohnemod.oeffnen": "Öffnen",
    "stinkendreich.ohnemod.code_neu": "Neuer Code (nur zum Ändern)",
    "stinkendreich.ohnemod.code_hinweis": "Code eingeben und öffnen. Zum Ändern: alten Code oben, neuen Code darunter.",
    "stinkendreich.ohnemod.musik_hinweis": "Eigene Musik und Spotify gehen nur mit der Mod auf dem Rechner. Hier laufen die Schallplatten von Minecraft:",
    "stinkendreich.ohnemod.klavier_hinweis": "Tasten anklicken, das Fenster bleibt offen. Esc schließt es.",
    "stinkendreich.ohnemod.kamera_hinweis": "Kamera wählen. Schleichen (Shift) beendet den Kamerablick.",
    "stinkendreich.ohnemod.kamera": "Kamera %s",
    "stinkendreich.ohnemod.kamera_ende": "Schleichen (Shift) zum Beenden",
    "stinkendreich.ohnemod.keine_kamera": "Keine Kamera in der Nähe.",
    "stinkendreich.ohnemod.klaviatur": "Selbst spielen (Klaviatur)",
}
TEXTE_EN = {
    "item.stinkendreich.handy": "Golden Phone",
    "item.stinkendreich.mitarbeiterkiste": "Worker Crate",
    "entity.stinkendreich.mitarbeiter": "Worker",
    "entity.stinkendreich.bodyguard": "Bodyguard",
    "stinkendreich.bodyguard.name": "Bodyguard",
    "stinkendreich.bodyguard.da": "Your bodyguards are here, boss.",
    "stinkendreich.bodyguard.weg": "The bodyguards are going home.",
    "stinkendreich.mitarbeiter.da": "Three workers have arrived: diamonds, iron and coal. Right-click one to change its job.",
    "stinkendreich.handy.mitarbeiter.da": "Your worker crate is in your inventory. Place it where the work should be done.",
    "stinkendreich.mitarbeiter.aufgabe": "%s now looks for: %s",
    "stinkendreich.mitarbeiter.fremd": "%s doesn't work for you.",
    "stinkendreich.handy.titel.start": "Filthy Rich",
    "stinkendreich.handy.titel.navi": "Navigation",
    "stinkendreich.handy.titel.lieferung": "Delivery",
    "stinkendreich.handy.titel.auftrag": "Orders for James",
    "stinkendreich.handy.anruf_limo": "Call the limo",
    "stinkendreich.handy.anruf_heli": "Call the helicopter",
    "stinkendreich.handy.navi": "Navigation",
    "stinkendreich.handy.lieferung": "Delivery",
    "stinkendreich.handy.auftraege": "Orders",
    "stinkendreich.handy.mitarbeiter": "Order workers",
    "stinkendreich.handy.bodyguards": "Call bodyguards",
    "stinkendreich.handy.bodyguards_heim": "Bodyguards home",
    "stinkendreich.handy.zurueck": "← Back",
    "stinkendreich.handy.navi.villa": "Home (villa)",
    "stinkendreich.handy.navi.dorf": "To the village",
    "stinkendreich.handy.navi.endportal": "To the End portal",
    "stinkendreich.handy.navi.netherportal": "To the Nether portal",
    "stinkendreich.handy.navi.mine": "To the mine",
    "stinkendreich.handy.navi.ort": "Saved place …",
    "stinkendreich.handy.navi.los": "Drive there",
    "stinkendreich.handy.liefer.kuehe": "2 cows",
    "stinkendreich.handy.liefer.schweine": "2 pigs",
    "stinkendreich.handy.liefer.schafe": "2 sheep",
    "stinkendreich.handy.liefer.huehner": "4 chickens",
    "stinkendreich.handy.liefer.hund": "Dog (tame)",
    "stinkendreich.handy.liefer.katze": "Cat (tame)",
    "stinkendreich.handy.liefer.papagei": "Parrot (tame)",
    "stinkendreich.handy.liefer.festessen": "Feast",
    "stinkendreich.handy.liefer.feuerwerk": "64 rockets",
    "stinkendreich.handy.liefer.gold": "A sack of gold",
    "stinkendreich.handy.auftrag.eisen": "Get 32 iron",
    "stinkendreich.handy.auftrag.eisen.text": "hol 32 eisen",
    "stinkendreich.handy.auftrag.diamanten": "Get 16 diamonds",
    "stinkendreich.handy.auftrag.diamanten.text": "hol 16 diamanten",
    "stinkendreich.handy.auftrag.holz": "Get 64 wood",
    "stinkendreich.handy.auftrag.holz.text": "hol 64 holz",
    "stinkendreich.handy.auftrag.essen": "Cook food",
    "stinkendreich.handy.auftrag.essen.text": "koch essen",
    "stinkendreich.handy.auftrag.sortieren": "Sort the chests",
    "stinkendreich.handy.auftrag.sortieren.text": "sortier die truhen",
    "stinkendreich.handy.auftrag.heim": "Go home",
    "stinkendreich.handy.auftrag.heim.text": "geh nach hause",
    "stinkendreich.handy.auftrag.frei": "Your own order …",
    "stinkendreich.handy.auftrag.senden": "Send order",
    "stinkendreich.handy.unterwegs": "Very well, sir! I'm on my way with the limo.",
    "stinkendreich.handy.lieferung.unterwegs": "Your order is on its way, sir.",
    "stinkendreich.handy.da": "Your car, sir: %s. Please get in.",
    "stinkendreich.handy.lieferung.da": "Your delivery, sir: %s.",
    "stinkendreich.handy.lieferung.nichts": "nothing",
    "stinkendreich.handy.lieferung.kuehe": "2 cows",
    "stinkendreich.handy.lieferung.schweine": "2 pigs",
    "stinkendreich.handy.lieferung.schafe": "2 sheep",
    "stinkendreich.handy.lieferung.huehner": "4 chickens",
    "stinkendreich.handy.lieferung.hund": "a dog (already yours)",
    "stinkendreich.handy.lieferung.katze": "a cat (already yours)",
    "stinkendreich.handy.lieferung.papagei": "a parrot (already yours)",
    "stinkendreich.handy.lieferung.festessen": "a feast (in your inventory)",
    "stinkendreich.handy.lieferung.feuerwerk": "64 firework rockets (in your inventory)",
    "stinkendreich.handy.lieferung.gold": "gold (in your inventory)",
    "stinkendreich.handy.status.unterwegs": "On the way to you",
    "stinkendreich.handy.status.faehrtvor": "Pulling up",
    "stinkendreich.ohnemod.oeffnen": "Open",
    "stinkendreich.ohnemod.code_neu": "New code (only to change it)",
    "stinkendreich.ohnemod.code_hinweis": "Enter the code and open. To change it: old code on top, new code below.",
    "stinkendreich.ohnemod.musik_hinweis": "Your own music and Spotify need the mod on your computer. Here you can play Minecraft's records:",
    "stinkendreich.ohnemod.klavier_hinweis": "Click the keys, the window stays open. Esc closes it.",
    "stinkendreich.ohnemod.kamera_hinweis": "Pick a camera. Sneak (Shift) ends the camera view.",
    "stinkendreich.ohnemod.kamera": "Camera %s",
    "stinkendreich.ohnemod.kamera_ende": "Sneak (Shift) to leave",
    "stinkendreich.ohnemod.keine_kamera": "No camera nearby.",
    "stinkendreich.ohnemod.klaviatur": "Play yourself (keyboard)",
}


def rgba(c, a=255):
    return (c[0], c[1], c[2], a)


def dunkler(c, f=0.82):
    return tuple(int(v * f) for v in c[:3])


def wuerfel(bild: Image.Image, u, v, w, h, d, f) -> None:
    """Malt die sechs Seiten eines Modellwürfels (Minecraft-UV-Layout) mit f(seite, x, y, breite, hoehe) → Farbe."""
    seiten = {
        "oben": (u + d, v, w, d), "unten": (u + d + w, v, w, d),
        "rechts": (u, v + d, d, h), "vorn": (u + d, v + d, w, h),
        "links": (u + d + w, v + d, d, h), "hinten": (u + 2 * d + w, v + d, w, h),
    }
    for name, (x0, y0, bw, bh) in seiten.items():
        for y in range(bh):
            for x in range(bw):
                farbe = f(name, x, y, bw, bh)
                if farbe is not None:
                    bild.putpixel((x0 + x, y0 + y), rgba(farbe) if len(farbe) == 3 else farbe)


# ---------------------------------------------------------------------------
# Items
# ---------------------------------------------------------------------------

def handy() -> Image.Image:
    """Goldenes Smartphone mit dunklem Bildschirm, App-Kacheln und Kamera."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([4, 1, 11, 14], radius=1, fill=rgba(GOLD), outline=rgba(GOLD_DUNKEL))
    d.line([(5, 1), (10, 1)], fill=rgba(GOLD_HELL))
    d.rectangle([5, 3, 10, 12], fill=rgba((20, 24, 40)))
    farben = [(80, 200, 120), (230, 90, 90), (90, 150, 240), (240, 200, 60), (190, 110, 230), (240, 140, 60)]
    i = 0
    for y in (4, 7, 10):
        for x in (6, 8):
            if y < 12:
                d.rectangle([x, y, x + 1, y + 1], fill=rgba(farben[i % len(farben)]))
                i += 1
    d.point((7, 2), fill=rgba((40, 40, 44)))
    d.point((8, 13), fill=rgba(GOLD_HELL))
    return img


def kiste() -> Image.Image:
    """Holzkiste mit Goldbeschlag und gelbem Bauhelm obendrauf."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([1, 6, 14, 14], fill=rgba((150, 104, 52)), outline=rgba((92, 62, 30)))
    for y in (9, 12):
        d.line([(2, y), (13, y)], fill=rgba((120, 82, 40)))
    d.rectangle([7, 8, 8, 10], fill=rgba(GOLD))
    # Bauhelm
    d.chord([3, 1, 12, 9], 180, 360, fill=rgba((250, 210, 40)), outline=rgba((190, 150, 20)))
    d.line([(2, 5), (13, 5)], fill=rgba((230, 190, 30)))
    d.line([(7, 2), (8, 2)], fill=rgba((255, 240, 140)))
    return img


# ---------------------------------------------------------------------------
# Mitarbeiter (64×64, Butler-Modell)
# ---------------------------------------------------------------------------

def mitarbeiter() -> Image.Image:
    zufall = random.Random(7)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    haut, haar = (214, 168, 128), (96, 64, 40)
    latz, latz_dunkel = (40, 88, 160), (30, 66, 122)
    hemd = (232, 120, 40)
    helm, helm_dunkel = (250, 206, 40), (196, 156, 20)

    def kopf(s, x, y, w, h):
        if s == "oben":
            return haar
        if s == "unten":
            return dunkler(haut)
        if s == "hinten":
            return haar if y < 6 else haut
        if s in ("rechts", "links"):
            return haar if y < 3 else haut
        if y < 2:
            return haar
        if y == 3 and x in (1, 2, 5, 6):
            return (250, 250, 250) if x in (1, 6) else (60, 90, 140)
        if y == 5 and 2 <= x <= 5:
            return (110, 70, 50) if x in (3, 4) else dunkler(haut, 0.9)     # Schnurrbart
        if y == 6 and x in (3, 4):
            return (170, 90, 80)
        return haut if zufall.random() > 0.05 else dunkler(haut, 0.95)
    wuerfel(img, 0, 0, 8, 8, 8, kopf)

    # Bauhelm als Hut-Schicht: oben und Seiten gelb, vorn Schirm
    def hut(s, x, y, w, h):
        if s == "unten":
            return None
        if s == "oben":
            return helm if (x + y) % 5 else helm_dunkel
        if y < 3:
            return helm
        if y == 3:
            return helm_dunkel
        return None
    wuerfel(img, 32, 0, 8, 8, 8, hut)

    def koerper(s, x, y, w, h):
        if s == "oben":
            return hemd
        if s == "unten":
            return latz
        if y < 3:
            if s in ("vorn", "hinten") and x in (1, 6):
                return latz_dunkel                               # Träger
            return hemd
        if s == "vorn" and y == 4 and x in (2, 5):
            return GOLD                                          # Knöpfe
        if s == "vorn" and 5 <= y <= 6 and 3 <= x <= 4:
            return (200, 200, 205)                               # Brusttasche mit Stift
        return latz if (x + y) % 4 else latz_dunkel
    wuerfel(img, 16, 16, 8, 12, 4, koerper)

    def arm(s, x, y, w, h):
        if s == "oben":
            return hemd
        if y >= 10:
            return (120, 90, 60)                                 # Arbeitshandschuh
        return hemd if y < 5 else haut
    wuerfel(img, 40, 16, 4, 12, 4, arm)

    def bein(s, x, y, w, h):
        if s == "unten":
            return (40, 34, 30)
        if y >= 10:
            return (70, 50, 34) if y < 11 else (50, 36, 26)      # Arbeitsstiefel
        return latz if (x + y) % 5 else latz_dunkel
    wuerfel(img, 0, 16, 4, 12, 4, bein)
    return img


# ---------------------------------------------------------------------------
# Bodyguard (128×128, Eisengolem-Modell)
# ---------------------------------------------------------------------------

def bodyguard() -> Image.Image:
    zufall = random.Random(11)
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    eisen, eisen_dunkel = (196, 192, 186), (150, 146, 140)
    jacke, jacke_hell = (24, 24, 30), (40, 40, 50)
    hemd = (240, 240, 236)

    def metall(f=1.0):
        c = eisen if zufall.random() > 0.15 else eisen_dunkel
        return dunkler(c, f)

    def kopf(s, x, y, w, h):
        if s == "vorn":
            if y in (3, 4) and 0 <= x <= 7:
                return (12, 12, 14) if x not in (3, 4) else (40, 40, 44)   # Sonnenbrille
            if y == 2 and x in (1, 2, 5, 6):
                return (90, 90, 92)                                       # Brauen
            if y == 8 and 2 <= x <= 5:
                return dunkler(eisen, 0.6)                                # Mund
        if s == "hinten" and y in (3, 4):
            return (12, 12, 14)                                           # Bügel
        if s in ("rechts", "links") and y == 3:
            return (12, 12, 14)
        return metall()
    wuerfel(img, 0, 0, 8, 10, 8, kopf)
    wuerfel(img, 24, 0, 2, 4, 2, lambda s, x, y, w, h: metall(0.95))     # Nase

    def koerper(s, x, y, w, h):
        if s == "vorn":
            mitte = w // 2
            # Hemd als V unter dem Kragen, Fliege, weiße Knopfleiste
            if y < 7 and abs(x - mitte + 0.5) < 3.5 - y * 0.45:
                if y == 1 and abs(x - mitte + 0.5) < 2:
                    return (14, 14, 16)                                   # Fliege
                return hemd
            if abs(x - mitte + 0.5) < 0.6 and y >= 7:
                return (10, 10, 12)
            if x in (3, 14) and y == 3:
                return (200, 30, 40)                                      # Einstecktuch
            if y >= 7 and abs(x - mitte + 0.5) < 1.6 and y % 2 == 0:
                return GOLD                                               # Knöpfe
        if s == "oben":
            return jacke_hell if (x + y) % 6 else jacke
        return jacke if (x + y) % 7 else jacke_hell
    wuerfel(img, 0, 40, 18, 12, 11, koerper)
    wuerfel(img, 0, 70, 9, 5, 6, lambda s, x, y, w, h: (16, 16, 20) if y else GOLD)   # Hüfte mit Goldgürtel

    def arm(s, x, y, w, h, pistole=False):
        if s == "unten":
            return metall()
        if y >= 24:
            # Hand aus Eisen; rechts hält sie die goldene Pistole
            if pistole and s == "vorn" and 25 <= y <= 28 and 1 <= x <= 4:
                return GOLD if y != 26 else GOLD_DUNKEL
            if pistole and s in ("rechts", "links") and y == 26 and 1 <= x <= 4:
                return GOLD
            return metall()
        if y >= 21:
            return hemd if y == 21 else (12, 12, 14)                     # Manschette und Ärmelende
        return jacke if (x + y) % 6 else jacke_hell
    wuerfel(img, 60, 21, 4, 30, 6, lambda s, x, y, w, h: arm(s, x, y, w, h, True))
    wuerfel(img, 60, 58, 4, 30, 6, arm)

    def bein(s, x, y, w, h):
        if s == "unten":
            return (6, 6, 8)
        if y >= 13:
            return (8, 8, 10) if (x + y) % 3 else (60, 60, 66)            # Lackschuhe
        if s in ("rechts", "links") and x == w // 2:
            return (44, 44, 54)                                           # Seitenstreifen
        return (20, 20, 26)
    wuerfel(img, 37, 0, 6, 16, 5, bein)
    wuerfel(img, 60, 0, 6, 16, 5, bein)
    return img


def schreibe_json(pfad: Path, daten) -> None:
    pfad.parent.mkdir(parents=True, exist_ok=True)
    pfad.write_text(json.dumps(daten, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main() -> dict:
    (ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
    (ASSETS / "textures/entity").mkdir(parents=True, exist_ok=True)
    for name, bild in (("handy", handy()), ("mitarbeiterkiste", kiste())):
        bild.save(ASSETS / "textures/item" / f"{name}.png")
        schreibe_json(ASSETS / "models/item" / f"{name}.json",
                      {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
        schreibe_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
    mitarbeiter().save(ASSETS / "textures/entity/mitarbeiter.png")
    bodyguard().save(ASSETS / "textures/entity/bodyguard.png")
    return {"de": TEXTE_DE, "en": TEXTE_EN}


if __name__ == "__main__":
    main()
