#!/usr/bin/env python3
"""Erzeugt Textur, Glöckchen-Item und Texte für den Butler.

Textur (64x64): oben die Menschengestalt im klassischen Layout (Kopf, Körper, ein Arm, ein Bein,
linke Seite gespiegelt), unten die Zusatzteile des eigenen Modells (Frackschöße, Fliege, Haar),
siehe ButlerModell.java.

Aufruf: python3 tools/assets/butler.py
"""
from __future__ import annotations

import json
import random
from pathlib import Path

from PIL import Image, ImageDraw

WURZEL = Path(__file__).resolve().parents[2]
ASSETS = WURZEL / "src/main/resources/assets/stinkendreich"

# Farben
HAUT = (222, 176, 140)
HAUT_DUNKEL = (196, 148, 114)
HAAR = (58, 52, 48)
HAAR_GRAU = (150, 150, 152)
FRACK = (22, 22, 28)
FRACK_HELL = (38, 38, 48)
REVERS = (44, 44, 56)
HEMD = (238, 238, 232)
HEMD_SCHATTEN = (208, 208, 202)
WESTE = (70, 70, 82)
KNOPF = (200, 170, 70)
FLIEGE = (12, 12, 14)
HOSE = (26, 26, 32)
STREIFEN = (46, 46, 56)
SCHUH = (8, 8, 10)
SCHUH_GLANZ = (60, 60, 66)
HANDSCHUH = (244, 244, 240)
AUGE = (40, 60, 110)


def flaeche(img: Image.Image, x0: int, y0: int, b: int, h: int, farbe, rauschen: int = 6, saat: int = 0) -> None:
    """Füllt ein Rechteck mit leicht verrauschter Farbe (wirkt wie Stoff)."""
    r = random.Random(saat * 1000 + x0 * 31 + y0)
    for x in range(x0, x0 + b):
        for y in range(y0, y0 + h):
            d = r.randint(-rauschen, rauschen)
            img.putpixel((x, y), tuple(max(0, min(255, c + d)) for c in farbe) + (255,))


def quader(img, u, v, b, h, t, oben, unten, rechts, vorne, links, hinten, saat=0):
    """Malt die sechs Seiten eines Modellquaders (Minecraft-Aufteilung ab u, v)."""
    flaeche(img, u + t, v, b, t, oben, saat=saat)
    flaeche(img, u + t + b, v, b, t, unten, saat=saat + 1)
    flaeche(img, u, v + t, t, h, rechts, saat=saat + 2)
    flaeche(img, u + t, v + t, b, h, vorne, saat=saat + 3)
    flaeche(img, u + t + b, v + t, t, h, links, saat=saat + 4)
    flaeche(img, u + 2 * t + b, v + t, b, h, hinten, saat=saat + 5)


def kopf(img: Image.Image) -> None:
    quader(img, 0, 0, 8, 8, 8, HAAR, HAUT_DUNKEL, HAUT, HAUT, HAUT, HAAR, saat=1)
    p = img.putpixel
    # Haar seitlich und hinten, graue Schläfen
    for x in range(0, 8):
        for y in range(8, 11):
            p((x, y), HAAR + (255,))          # rechte Seite oben
            p((16 + x, y), HAAR + (255,))     # linke Seite oben
    for y in range(11, 13):
        for x in (5, 6, 7):
            p((x, y), HAAR_GRAU + (255,))
        for x in (16, 17, 18):
            p((x, y), HAAR_GRAU + (255,))
    for x in range(24, 32):
        for y in range(8, 14):
            p((x, y), HAAR + (255,))
    # Gesicht (vorne: x 8..15, y 8..15)
    for x in range(8, 16):
        p((x, 8), HAAR + (255,))               # Haaransatz
    p((8, 9), HAAR + (255,))
    p((15, 9), HAAR + (255,))
    for x in (9, 10, 13, 14):
        p((x, 10), HAAR_GRAU + (255,))         # Augenbrauen
    p((9, 11), (250, 250, 250, 255))
    p((10, 11), AUGE + (255,))
    p((13, 11), AUGE + (255,))
    p((14, 11), (250, 250, 250, 255))
    p((11, 12), HAUT_DUNKEL + (255,))          # Nase
    p((12, 12), HAUT_DUNKEL + (255,))
    for x in range(10, 14):
        p((x, 13), HAAR_GRAU + (255,))         # gepflegter Schnurrbart
    p((9, 13), HAAR_GRAU + (255,))
    p((14, 13), HAAR_GRAU + (255,))
    for x in (11, 12):
        p((x, 14), (170, 100, 90, 255))        # Mund
    # Hutschicht (32..63 oben) bleibt durchsichtig


def koerper(img: Image.Image) -> None:
    # Körper: u=16 v=16, 8 breit, 12 hoch, 4 tief
    quader(img, 16, 16, 8, 12, 4, FRACK, FRACK, FRACK, FRACK, FRACK, FRACK, saat=2)
    p = img.putpixel
    vx, vy = 20, 20  # Vorderseite
    for y in range(12):
        for x in range(8):
            mitte = 2 <= x <= 5
            if mitte and y < 9:
                farbe = HEMD if (x in (3, 4) or y < 2) else WESTE
                if y >= 2 and x in (2, 5):
                    farbe = WESTE
                if y < 2:
                    farbe = HEMD
                p((vx + x, vy + y), farbe + (255,))
            elif x in (1, 6) and y < 7:
                p((vx + x, vy + y), REVERS + (255,))   # Revers
    # Fliege und Knöpfe
    for x in (2, 3, 4, 5):
        p((vx + x, vy + 0), FLIEGE + (255,))
    p((vx + 3, vy + 1), FLIEGE + (255,))
    p((vx + 4, vy + 1), FLIEGE + (255,))
    for y in (4, 6, 8):
        p((vx + 3, vy + y), KNOPF + (255,))
    for x in range(8):
        p((vx + x, vy + 11), FRACK_HELL + (255,))  # Saum
    # Rücken: Naht in der Mitte
    for y in range(4, 12):
        p((32 + 3, 20 + y), FRACK_HELL + (255,))
        p((32 + 4, 20 + y), FRACK_HELL + (255,))


def arm(img: Image.Image) -> None:
    # Arm: u=40 v=16, 4x12x4 – schwarzer Ärmel, weiße Manschette, weiße Handschuhe
    quader(img, 40, 16, 4, 12, 4, FRACK, HANDSCHUH, FRACK, FRACK, FRACK, FRACK, saat=3)
    for seite_u in (40, 44, 48, 52):
        for x in range(seite_u, seite_u + 4):
            img.putpixel((x, 20 + 8), HEMD_SCHATTEN + (255,))
            for y in (29, 30, 31):
                img.putpixel((x, 20 + y - 20), HANDSCHUH + (255,))


def bein(img: Image.Image) -> None:
    # Bein: u=0 v=16, 4x12x4 – Hose mit Seitenstreifen, polierte Schuhe
    quader(img, 0, 16, 4, 12, 4, HOSE, SCHUH, HOSE, HOSE, HOSE, HOSE, saat=4)
    for y in range(20, 30):
        img.putpixel((1, y), STREIFEN + (255,))    # rechte Außenseite
        img.putpixel((10, y), STREIFEN + (255,))   # linke Außenseite
    for seite_u in (0, 4, 8, 12):
        for x in range(seite_u, seite_u + 4):
            for y in (30, 31):
                img.putpixel((x, y), SCHUH + (255,))
    img.putpixel((5, 30), SCHUH_GLANZ + (255,))
    img.putpixel((6, 30), SCHUH_GLANZ + (255,))


def zusatzteile(img: Image.Image) -> None:
    # Frackschöße (0,32) und (12,32): je ca. 9x9 Pixel Fläche
    flaeche(img, 0, 32, 10, 10, FRACK, saat=5)
    flaeche(img, 12, 32, 10, 10, FRACK, saat=6)
    for x in range(0, 10):
        img.putpixel((x, 41), FRACK_HELL + (255,))
    for x in range(12, 22):
        img.putpixel((x, 41), FRACK_HELL + (255,))
    # Fliege (24,32): Satin mit Glanz
    flaeche(img, 24, 32, 11, 3, FLIEGE, rauschen=2, saat=7)
    for x in (26, 31):
        img.putpixel((x, 33), (48, 48, 56, 255))
    # Haar oben (0,42): 34x10
    flaeche(img, 0, 42, 35, 10, HAAR, rauschen=8, saat=8)
    for x in range(0, 35, 3):
        img.putpixel((x, 46), (74, 68, 62, 255))   # Kammspuren


def textur() -> Image.Image:
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    kopf(img)
    koerper(img)
    arm(img)
    bein(img)
    zusatzteile(img)
    return img


def glocke() -> Image.Image:
    """Item: goldenes Tischglöckchen mit dunklem Griff."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([7, 1, 8, 4], fill=(70, 48, 30, 255))            # Griff
    d.ellipse([6, 0, 9, 2], fill=(90, 62, 40, 255))
    d.polygon([(4, 12), (5, 7), (7, 5), (8, 5), (10, 7), (11, 12)], fill=(222, 182, 64, 255))
    d.line([(6, 7), (5, 11)], fill=(250, 226, 130, 255))          # Glanz
    d.rectangle([2, 12, 13, 13], fill=(196, 154, 46, 255))        # Rand
    d.line([(3, 12), (12, 12)], fill=(246, 214, 110, 255))
    d.point((8, 14), fill=(150, 110, 30, 255))                    # Klöppel
    return img


TEXTE_DE = {
    "entity.stinkendreich.butler": "Butler",
    "item.stinkendreich.butlerglocke": "Butler-Glöckchen",
    "stinkendreich.butler.fremd": "%s dient nur %s.",
    "stinkendreich.butler.taschen": "Taschen von %s",
    "stinkendreich.butler.status.folgt": "Folgt Ihnen",
    "stinkendreich.butler.status.wartet": "Wartet auf Anweisungen",
    "stinkendreich.butler.status.tunnel": "Gräbt sich zu %s vor (%s/%s)",
    "stinkendreich.butler.status.sucht": "Sucht %s (%s/%s)",
    "stinkendreich.butler.status.baut": "Baut %s ab (%s/%s)",
    "stinkendreich.butler.status.baumsuche": "Sucht Bäume (%s/%s Holz)",
    "stinkendreich.butler.status.holz": "Fällt Bäume (%s/%s Holz)",
    "stinkendreich.butler.status.jagd": "Auf der Jagd (%s/%s Fleisch)",
    "stinkendreich.butler.status.ofen": "Am Ofen: %s (%s/%s)",
    "stinkendreich.butler.status.ofenwarten": "Wartet am Ofen: %s (%s/%s)",
    "stinkendreich.butler.status.herstellen": "Stellt her: %s (%s/%s)",
    "stinkendreich.butler.status.liefert": "Bringt Ihnen %s",
    "stinkendreich.butler.status.liefertruhe": "Bringt %s zur Truhe",
    "stinkendreich.butler.status.vorrat": "Holt %s aus der Speisekammer",
    "stinkendreich.butler.status.sortiert": "Sortiert Truhen (%s/%s)",
    "stinkendreich.butler.status.heim": "Geht nach Hause",
    "stinkendreich.butler.status.kommt": "Kommt zu Ihnen",
    "stinkendreich.butler.unbekannt": "Das kenne ich nicht: %s",
    "stinkendreich.butler.voll": "Meine Taschen sind voll.",
    "stinkendreich.butler.nur": "In der Nähe habe ich nur %s × %s gefunden.",
    "stinkendreich.butler.nichtgefunden": "Verzeihung, in der Nähe finde ich nichts davon: %s.",
    "stinkendreich.butler.keinbaum": "Verzeihung, in der Nähe steht kein Baum, den ich fällen darf.",
    "stinkendreich.butler.jagenaus": "Das Jagen ist in den Einstellungen abgeschaltet.",
    "stinkendreich.butler.keintier": "Verzeihung, hier gibt es keine Tiere, die ich jagen darf.",
    "stinkendreich.butler.nichtschmelzbar": "%s lässt sich nicht schmelzen.",
    "stinkendreich.butler.keinofen": "Ich finde keinen freien Ofen in der Nähe.",
    "stinkendreich.butler.keinbrennstoff": "Mir fehlt Brennstoff (Kohle, Holz oder Bretter).",
    "stinkendreich.butler.zutatenfehlen": "Mir fehlen die Zutaten für %s.",
    "stinkendreich.butler.bitteschoen": "Bitte sehr: %s × %s.",
    "stinkendreich.butler.intruhe": "Ich habe %s × %s in die Truhe gelegt.",
    "stinkendreich.butler.truhevoll": "Die Truhe ist voll, es passten nur %s × %s hinein. Den Rest trage ich bei mir.",
    "stinkendreich.butler.keinetruhe": "Hier stehen keine Truhen zum Sortieren.",
    "stinkendreich.butler.sortiert": "Erledigt: %s Truhen aufgeräumt, %s Gegenstände sortiert.",
    "stinkendreich.butler.kannnicht": "Verzeihung, %s kann ich nicht selbst beschaffen.",
    "stinkendreich.butler.hallo": "Guten Tag, %s. Ich stehe zu Diensten. Sprechen Sie mich im Chat mit „Butler, …“ an oder klicken Sie mich an.",
    "stinkendreich.butler.keinereigener": "In dieser Welt gibt es schon einen Butler, und weitere sind in den Einstellungen abgeschaltet.",
    "stinkendreich.butler.unterwegs": "Ihr Butler ist unterwegs …",
    "stinkendreich.butler.zurstelle": "Zur Stelle.",
    "stinkendreich.butler.keiner": "Sie haben noch keinen Butler – ich stelle Ihnen einen ein.",
    "stinkendreich.butler.sehrwohl": "Sehr wohl, ich besorge %s × %s.",
    "stinkendreich.butler.sehrwohl.truhe": "Sehr wohl, ich besorge %s × %s und lege es in die Truhe.",
    "stinkendreich.butler.kocht": "Sehr wohl, ich bereite Ihnen eine Mahlzeit zu.",
    "stinkendreich.butler.sortiert.los": "Sehr wohl, ich räume die Truhen auf.",
    "stinkendreich.butler.folgen": "Ich folge Ihnen.",
    "stinkendreich.butler.bruecke.zu": "Sehr wohl, ich sichere die Zugbrücke. Sie fährt ein, sobald niemand mehr davorsteht, und kommt nur für Sie heraus.",
    "stinkendreich.butler.bruecke.auf": "Sehr wohl, die Zugbrücke fährt aus und bleibt offen.",
    "stinkendreich.butler.bleiben": "Ich warte hier.",
    "stinkendreich.butler.heim": "Ich gehe nach Hause.",
    "stinkendreich.butler.kommt": "Ich komme.",
    "stinkendreich.butler.stopp": "Wie Sie wünschen, ich lasse alles stehen und liegen.",
    "stinkendreich.butler.gast.neu": "Sehr wohl. %s steht jetzt auf der Gästeliste und darf ohne Karte herein.",
    "stinkendreich.butler.gast.schon": "%s steht bereits auf der Gästeliste.",
    "stinkendreich.butler.gast.weg": "%s ist kein Gast mehr. Ohne Karte gilt er wieder als Eindringling.",
    "stinkendreich.butler.gast.keiner": "%s stand nicht auf der Gästeliste.",
    "stinkendreich.butler.gast.liste": "Unsere Gäste: %s",
    "stinkendreich.butler.gast.leer": "Die Gästeliste ist leer.",
    "stinkendreich.butler.gast.nur_bewohner": "Die Gästeliste führen nur die Bewohner der Villa.",
    "stinkendreich.butler.zurueckgesetzt": "James ist zurückgesetzt: %s alte(r) James entlassen, ein frischer wartet in der Halle.",
    "stinkendreich.butler.danach": " · danach noch %s Schritte",
    "stinkendreich.butler.hilfe": "Aufträge: „hol 32 Eisen“, „fäll 20 Holz“, „hol Essen“, „koch etwas“, „mach 16 Fackeln“, „sortier die Truhen“, „folg mir“, „bleib hier“, „geh nach Hause“, „komm her“, „stopp“. Mit „… in die Truhe“ liefere ich in die Lieferkiste. Als Chauffeur: „fahr mich zum Strand“, „fahr mich dahin“ (wohin Sie schauen), „fahr mich nach Hause“, „hol den Wagen“, „merk dir den Ort als Strand“. Zugbrücke: „mach die Brücke zu“ / „mach die Brücke auf“. Als Pilot: „flieg mich zum Strand“, „hol den Hubschrauber“. Befehle: /butler rufen, /butler truhe, /butler orte.",
    "stinkendreich.butler.fahren.holt": "Sehr wohl, ich hole den Wagen.",
    "stinkendreich.butler.flug.holt": "Sehr wohl, ich hole den Hubschrauber.",
    "stinkendreich.butler.flug.heim": "Sehr wohl, ich fliege Sie nach Hause.",
    "stinkendreich.butler.flug.blick": "Sehr wohl, ich fliege Sie dorthin (%s %s %s).",
    "stinkendreich.butler.flug.ort": "Sehr wohl, ich fliege Sie zu „%s“.",
    "stinkendreich.butler.flug.anflug": "Ich komme mit dem Hubschrauber zu Ihnen, Sir.",
    "stinkendreich.butler.flug.bereit": "Der Hubschrauber steht bereit, Sir. Bitte steigen Sie ein.",
    "stinkendreich.butler.flug.da": "Wir sind gelandet, Sir.",
    "stinkendreich.butler.flug.keiner": "Ich finde keinen Hubschrauber, Sir.",
    "stinkendreich.butler.status.fliegt": "Fliegt Sie %s (noch %s Blöcke)",
    "stinkendreich.butler.status.fliegtzu": "Fliegt zu Ihnen",
    "stinkendreich.butler.fahren.heim": "Sehr wohl, ich fahre Sie nach Hause.",
    "stinkendreich.butler.fahren.villa": "zur Villa",
    "stinkendreich.butler.fahren.blick": "Sehr wohl, ich fahre Sie dorthin (%s %s %s).",
    "stinkendreich.butler.fahren.ort": "Sehr wohl, ich fahre Sie zu „%s“.",
    "stinkendreich.butler.fahren.vorgefahren": "%s ist vorgefahren, Sir. Bitte steigen Sie ein.",
    "stinkendreich.butler.fahren.einsteigen": "Bitte steigen Sie ein, Sir.",
    "stinkendreich.butler.fahren.wohin": "Wohin darf es gehen, Sir?",
    "stinkendreich.butler.fahren.da": "Wir sind da, Sir.",
    "stinkendreich.butler.fahren.fest": "Verzeihung, hier komme ich mit dem Wagen nicht weiter.",
    "stinkendreich.butler.fahren.fastda": "Weiter komme ich mit dem Wagen nicht, Sir – wir sind fast da, den Rest gehen Sie besser zu Fuß.",
    "stinkendreich.butler.fahren.keinwagen": "Ich finde keinen Wagen in der Nähe, Sir.",
    "stinkendreich.butler.fahren.ausgestiegen": "Ich warte hier auf Sie, Sir.",
    "stinkendreich.butler.fahren.nichtgesehen": "Ich sehe dort kein Ziel, Sir. Schauen Sie bitte auf eine Stelle am Boden.",
    "stinkendreich.butler.ort.gemerkt": "Sehr wohl, ich merke mir diesen Ort als „%s“.",
    "stinkendreich.butler.ort.ohnename": "Wie soll ich den Ort nennen? Sagen Sie zum Beispiel „merk dir den Ort als Strand“.",
    "stinkendreich.butler.ort.unbekannt": "Den Ort „%s“ kenne ich nicht. Gespeicherte Orte: %s",
    "stinkendreich.butler.ort.vergessen": "Den Ort „%s“ habe ich vergessen.",
    "stinkendreich.butler.ort.keine": "Ich habe mir noch keine Orte gemerkt. Sagen Sie zum Beispiel „merk dir den Ort als Strand“.",
    "stinkendreich.butler.ort.liste": "Gespeicherte Orte: %s",
    "stinkendreich.butler.status.faehrt": "Fährt Sie %s (noch %s Blöcke)",
    "stinkendreich.butler.status.wartetimwagen": "Wartet am Steuer auf Sie",
    "stinkendreich.butler.status.holtwagen": "Holt den Wagen",
    "stinkendreich.butler.verstehtnicht": "Verzeihung, das habe ich nicht verstanden. Sagen Sie zum Beispiel „hol 32 Eisen“ oder „hilfe“.",
    "stinkendreich.butler.leer": "Meine Taschen sind leer.",
    "stinkendreich.butler.dabei": "Ich trage bei mir:",
    "stinkendreich.butler.truhe.keine": "Schauen Sie dabei auf eine Truhe (und rufen Sie vorher Ihren Butler mit /butler rufen).",
    "stinkendreich.butler.truhe.ok": "Sehr wohl, ab jetzt liefere ich in die Truhe bei %s %s %s.",
    "stinkendreich.butler.ki.denkt": "%s überlegt …",
    "stinkendreich.butler.ki.ablehnung": "Verzeihung, dabei kann ich Ihnen nicht helfen.",
    "stinkendreich.butler.ki.fehler": "(Claude ist gerade nicht erreichbar: %s – ich versuche es ohne.)",
    "stinkendreich.butler.menue.titel": "%s – Ihr Butler",
    "stinkendreich.butler.menue.folgen": "Folgen",
    "stinkendreich.butler.menue.bleiben": "Hier warten",
    "stinkendreich.butler.menue.heim": "Nach Hause",
    "stinkendreich.butler.menue.komm": "Komm her",
    "stinkendreich.butler.menue.eisen": "32 Eisenbarren",
    "stinkendreich.butler.menue.kohle": "32 Kohle",
    "stinkendreich.butler.menue.holz": "32 Holz",
    "stinkendreich.butler.menue.diamanten": "8 Diamanten",
    "stinkendreich.butler.menue.essen": "Essen bringen",
    "stinkendreich.butler.menue.kochen": "Kochen",
    "stinkendreich.butler.menue.fackeln": "16 Fackeln",
    "stinkendreich.butler.menue.sortieren": "Truhen sortieren",
    "stinkendreich.butler.menue.inventar": "Taschen ansehen",
    "stinkendreich.butler.menue.stopp": "Stopp",
    "stinkendreich.butler.menue.eingabe": "Auftrag, z. B. „hol 20 Glas in die Truhe“",
    "stinkendreich.butler.menue.senden": "Auftrag geben",
    "stinkendreich.ton.gloeckchen": "Glöckchen klingelt",
}
TEXTE_EN = {
    "entity.stinkendreich.butler": "Butler",
    "item.stinkendreich.butlerglocke": "Butler Bell",
    "stinkendreich.butler.fremd": "%s only serves %s.",
    "stinkendreich.butler.taschen": "%s's pockets",
    "stinkendreich.butler.status.folgt": "Following you",
    "stinkendreich.butler.status.wartet": "Awaiting instructions",
    "stinkendreich.butler.status.tunnel": "Digging towards %s (%s/%s)",
    "stinkendreich.butler.status.sucht": "Looking for %s (%s/%s)",
    "stinkendreich.butler.status.baut": "Mining %s (%s/%s)",
    "stinkendreich.butler.status.baumsuche": "Looking for trees (%s/%s logs)",
    "stinkendreich.butler.status.holz": "Felling trees (%s/%s logs)",
    "stinkendreich.butler.status.jagd": "Hunting (%s/%s meat)",
    "stinkendreich.butler.status.ofen": "At the furnace: %s (%s/%s)",
    "stinkendreich.butler.status.ofenwarten": "Waiting at the furnace: %s (%s/%s)",
    "stinkendreich.butler.status.herstellen": "Crafting: %s (%s/%s)",
    "stinkendreich.butler.status.liefert": "Bringing you %s",
    "stinkendreich.butler.status.liefertruhe": "Taking %s to the chest",
    "stinkendreich.butler.status.vorrat": "Fetching %s from the pantry",
    "stinkendreich.butler.status.sortiert": "Sorting chests (%s/%s)",
    "stinkendreich.butler.status.heim": "Going home",
    "stinkendreich.butler.status.kommt": "Coming to you",
    "stinkendreich.butler.unbekannt": "I don't know that: %s",
    "stinkendreich.butler.voll": "My pockets are full.",
    "stinkendreich.butler.nur": "I only found %s × %s nearby.",
    "stinkendreich.butler.nichtgefunden": "I beg your pardon, I can't find any of this nearby: %s.",
    "stinkendreich.butler.keinbaum": "I beg your pardon, there is no tree nearby I may fell.",
    "stinkendreich.butler.jagenaus": "Hunting is switched off in the settings.",
    "stinkendreich.butler.keintier": "I beg your pardon, there are no animals here I may hunt.",
    "stinkendreich.butler.nichtschmelzbar": "%s cannot be smelted.",
    "stinkendreich.butler.keinofen": "I can't find a free furnace nearby.",
    "stinkendreich.butler.keinbrennstoff": "I am out of fuel (coal, logs or planks).",
    "stinkendreich.butler.zutatenfehlen": "I lack the ingredients for %s.",
    "stinkendreich.butler.bitteschoen": "Here you are: %s × %s.",
    "stinkendreich.butler.intruhe": "I put %s × %s into the chest.",
    "stinkendreich.butler.truhevoll": "The chest is full, only %s × %s fit. I'll keep the rest.",
    "stinkendreich.butler.keinetruhe": "There are no chests here to sort.",
    "stinkendreich.butler.sortiert": "Done: tidied %s chests, sorted %s items.",
    "stinkendreich.butler.kannnicht": "I beg your pardon, I cannot obtain %s myself.",
    "stinkendreich.butler.hallo": "Good day, %s. At your service. Address me in chat with \"Butler, …\" or click on me.",
    "stinkendreich.butler.keinereigener": "This world already has a butler, and more are switched off in the settings.",
    "stinkendreich.butler.unterwegs": "Your butler is on his way …",
    "stinkendreich.butler.zurstelle": "At your service.",
    "stinkendreich.butler.keiner": "You don't have a butler yet – I'll hire one for you.",
    "stinkendreich.butler.sehrwohl": "Very well, I'll fetch %s × %s.",
    "stinkendreich.butler.sehrwohl.truhe": "Very well, I'll fetch %s × %s and put it in the chest.",
    "stinkendreich.butler.kocht": "Very well, I'll prepare a meal for you.",
    "stinkendreich.butler.sortiert.los": "Very well, I'll tidy up the chests.",
    "stinkendreich.butler.folgen": "I'll follow you.",
    "stinkendreich.butler.bruecke.zu": "Very well, I'll secure the drawbridge. It retracts as soon as nobody is in front of it and only comes out for you.",
    "stinkendreich.butler.bruecke.auf": "Very well, the drawbridge extends and stays open.",
    "stinkendreich.butler.bleiben": "I'll wait here.",
    "stinkendreich.butler.heim": "I'm going home.",
    "stinkendreich.butler.kommt": "Coming.",
    "stinkendreich.butler.stopp": "As you wish, I'll drop everything.",
    "stinkendreich.butler.gast.neu": "Very well. %s is now on the guest list and may enter without a card.",
    "stinkendreich.butler.gast.schon": "%s is already on the guest list.",
    "stinkendreich.butler.gast.weg": "%s is no longer a guest. Without a card they count as an intruder again.",
    "stinkendreich.butler.gast.keiner": "%s was not on the guest list.",
    "stinkendreich.butler.gast.liste": "Our guests: %s",
    "stinkendreich.butler.gast.leer": "The guest list is empty.",
    "stinkendreich.butler.gast.nur_bewohner": "Only residents of the villa keep the guest list.",
    "stinkendreich.butler.zurueckgesetzt": "James has been reset: %s old James dismissed, a fresh one is waiting in the hall.",
    "stinkendreich.butler.danach": " · then %s more steps",
    "stinkendreich.butler.hilfe": "Orders: \"fetch 32 iron\", \"chop 20 wood\", \"fetch food\", \"cook something\", \"make 16 torches\", \"sort the chests\", \"follow me\", \"stay here\", \"go home\", \"come here\", \"stop\" (German words work best). Add \"in die Truhe\" to deliver into the chest. As chauffeur: \"fahr mich zum Strand\", \"fahr mich dahin\" (where you look), \"fahr mich nach Hause\", \"hol den Wagen\", \"merk dir den Ort als Strand\". Drawbridge: \"mach die Brücke zu\" / \"mach die Brücke auf\". As pilot: \"flieg mich zum Strand\", \"hol den Hubschrauber\". Commands: /butler rufen, /butler truhe, /butler orte.",
    "stinkendreich.butler.fahren.holt": "Very well, I'll fetch the car.",
    "stinkendreich.butler.flug.holt": "Very well, I'll fetch the helicopter.",
    "stinkendreich.butler.flug.heim": "Very well, I'll fly you home.",
    "stinkendreich.butler.flug.blick": "Very well, I'll fly you there (%s %s %s).",
    "stinkendreich.butler.flug.ort": "Very well, I'll fly you to \"%s\".",
    "stinkendreich.butler.flug.anflug": "I'm coming to you with the helicopter, sir.",
    "stinkendreich.butler.flug.bereit": "The helicopter is ready, sir. Please get in.",
    "stinkendreich.butler.flug.da": "We have landed, sir.",
    "stinkendreich.butler.flug.keiner": "I can't find a helicopter, sir.",
    "stinkendreich.butler.status.fliegt": "Flying you %s (%s blocks to go)",
    "stinkendreich.butler.status.fliegtzu": "Flying to you",
    "stinkendreich.butler.fahren.heim": "Very well, I'll drive you home.",
    "stinkendreich.butler.fahren.villa": "to the villa",
    "stinkendreich.butler.fahren.blick": "Very well, I'll drive you there (%s %s %s).",
    "stinkendreich.butler.fahren.ort": "Very well, I'll drive you to \"%s\".",
    "stinkendreich.butler.fahren.vorgefahren": "The %s has pulled up, sir. Please get in.",
    "stinkendreich.butler.fahren.einsteigen": "Please get in, sir.",
    "stinkendreich.butler.fahren.wohin": "Where to, sir?",
    "stinkendreich.butler.fahren.da": "We have arrived, sir.",
    "stinkendreich.butler.fahren.fest": "I beg your pardon, I can't get any further with the car here.",
    "stinkendreich.butler.fahren.fastda": "I can't get any closer with the car, sir – we're nearly there, best walk the rest.",
    "stinkendreich.butler.fahren.keinwagen": "I can't find a car nearby, sir.",
    "stinkendreich.butler.fahren.ausgestiegen": "I'll wait here for you, sir.",
    "stinkendreich.butler.fahren.nichtgesehen": "I can't see a destination there, sir. Please look at a spot on the ground.",
    "stinkendreich.butler.ort.gemerkt": "Very well, I'll remember this place as \"%s\".",
    "stinkendreich.butler.ort.ohnename": "What shall I call this place? Say for example \"merk dir den Ort als Strand\".",
    "stinkendreich.butler.ort.unbekannt": "I don't know the place \"%s\". Saved places: %s",
    "stinkendreich.butler.ort.vergessen": "I have forgotten the place \"%s\".",
    "stinkendreich.butler.ort.keine": "I haven't saved any places yet. Say for example \"merk dir den Ort als Strand\".",
    "stinkendreich.butler.ort.liste": "Saved places: %s",
    "stinkendreich.butler.status.faehrt": "Driving you %s (%s blocks to go)",
    "stinkendreich.butler.status.wartetimwagen": "Waiting for you at the wheel",
    "stinkendreich.butler.status.holtwagen": "Fetching the car",
    "stinkendreich.butler.verstehtnicht": "I beg your pardon, I didn't understand. Try \"hol 32 Eisen\" or \"hilfe\".",
    "stinkendreich.butler.leer": "My pockets are empty.",
    "stinkendreich.butler.dabei": "I am carrying:",
    "stinkendreich.butler.truhe.keine": "Look at a chest while doing this (and call your butler first with /butler rufen).",
    "stinkendreich.butler.truhe.ok": "Very well, from now on I'll deliver into the chest at %s %s %s.",
    "stinkendreich.butler.ki.denkt": "%s is thinking …",
    "stinkendreich.butler.ki.ablehnung": "I beg your pardon, I cannot help you with that.",
    "stinkendreich.butler.ki.fehler": "(Claude is unreachable right now: %s – I'll manage without.)",
    "stinkendreich.butler.menue.titel": "%s – your butler",
    "stinkendreich.butler.menue.folgen": "Follow",
    "stinkendreich.butler.menue.bleiben": "Wait here",
    "stinkendreich.butler.menue.heim": "Go home",
    "stinkendreich.butler.menue.komm": "Come here",
    "stinkendreich.butler.menue.eisen": "32 iron ingots",
    "stinkendreich.butler.menue.kohle": "32 coal",
    "stinkendreich.butler.menue.holz": "32 logs",
    "stinkendreich.butler.menue.diamanten": "8 diamonds",
    "stinkendreich.butler.menue.essen": "Bring food",
    "stinkendreich.butler.menue.kochen": "Cook",
    "stinkendreich.butler.menue.fackeln": "16 torches",
    "stinkendreich.butler.menue.sortieren": "Sort chests",
    "stinkendreich.butler.menue.inventar": "Look in pockets",
    "stinkendreich.butler.menue.stopp": "Stop",
    "stinkendreich.butler.menue.eingabe": "Order, e.g. \"hol 20 Glas in die Truhe\"",
    "stinkendreich.butler.menue.senden": "Give order",
    "stinkendreich.ton.gloeckchen": "Bell rings",
}


def schreibe_json(pfad: Path, daten) -> None:
    pfad.parent.mkdir(parents=True, exist_ok=True)
    pfad.write_text(json.dumps(daten, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main() -> None:
    (ASSETS / "textures/entity").mkdir(parents=True, exist_ok=True)
    textur().save(ASSETS / "textures/entity/butler.png")
    glocke().save(ASSETS / "textures/item/butlerglocke.png")
    schreibe_json(ASSETS / "models/item/butlerglocke.json",
                  {"parent": "minecraft:item/generated", "textures": {"layer0": "stinkendreich:item/butlerglocke"}})
    schreibe_json(ASSETS / "items/butlerglocke.json",
                  {"model": {"type": "minecraft:model", "model": "stinkendreich:item/butlerglocke"}})
    for datei, texte in (("de_de", TEXTE_DE), ("en_us", TEXTE_EN)):
        pfad = ASSETS / "lang" / f"{datei}.json"
        vorhanden = json.loads(pfad.read_text(encoding="utf-8"))
        vorhanden.update(texte)
        schreibe_json(pfad, dict(sorted(vorhanden.items())))
    print("Butler-Textur, Glöckchen und", len(TEXTE_DE), "Texte geschrieben")


if __name__ == "__main__":
    main()
