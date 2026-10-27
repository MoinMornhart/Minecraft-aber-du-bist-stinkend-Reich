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
import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
from spielerhaut import linke_seite  # noqa: E402

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
    "stinkendreich.handy.orte": "Meine Orte …",
    "stinkendreich.handy.titel.orte": "Meine Orte",
    "stinkendreich.handy.orte.name": "Name für diesen Ort",
    "stinkendreich.handy.orte.speichern": "Ort hier speichern",
    "stinkendreich.handy.orte.leer": "Noch keine Orte gespeichert",
    "stinkendreich.handy.gaeste": "Gästeliste",
    "stinkendreich.handy.titel.gaeste": "Gästeliste",
    "stinkendreich.handy.gaeste.name": "Spielername",
    "stinkendreich.handy.gaeste.eintragen": "Als Gast eintragen",
    "stinkendreich.handy.gaeste.leer": "Noch keine Gäste",
    "stinkendreich.handy.lieferung": "Lieferservice",
    "stinkendreich.handy.auftraege": "Aufträge",
    "stinkendreich.handy.mitarbeiter": "Mitarbeiter bestellen",
    "stinkendreich.handy.bodyguards": "Bodyguards rufen",
    "stinkendreich.handy.bodyguards_heim": "Bodyguards heim",
    "stinkendreich.handy.bruecke": "Zugbrücke ein/aus",
    "stinkendreich.handy.smarthome": "Smart Home",
    "stinkendreich.handy.titel.smarthome": "Smart Home", "stinkendreich.handy.titel.smart": "Smart Home",
    "stinkendreich.handy.sh.rollo": "Rollläden", "stinkendreich.handy.sh.rollo.an": "zu", "stinkendreich.handy.sh.rollo.aus": "offen",
    "stinkendreich.handy.sh.bruecke": "Zugbrücke", "stinkendreich.handy.sh.bruecke.an": "eingefahren", "stinkendreich.handy.sh.bruecke.aus": "unten",
    "stinkendreich.handy.sh.licht": "Licht", "stinkendreich.handy.sh.licht.an": "an", "stinkendreich.handy.sh.licht.aus": "aus",
    "stinkendreich.handy.sh.disco": "Disco", "stinkendreich.handy.sh.disco.an": "läuft", "stinkendreich.handy.sh.disco.aus": "aus",
    "stinkendreich.handy.sh.automatik": "Automatik", "stinkendreich.handy.sh.automatik.an": "an", "stinkendreich.handy.sh.automatik.aus": "aus",
    "stinkendreich.handy.sh.inkognito": "Inkognito", "stinkendreich.handy.sh.inkognito.an": "versteckt", "stinkendreich.handy.sh.inkognito.aus": "aus",
    "stinkendreich.handy.sh.krisenbunker": "Krisenbunker", "stinkendreich.handy.sh.krisenbunker.an": "dicht", "stinkendreich.handy.sh.krisenbunker.aus": "aus",
    "stinkendreich.handy.sh.tor": "Eingangstor", "stinkendreich.handy.sh.tor.an": "bleibt zu", "stinkendreich.handy.sh.tor.aus": "automatisch",
    "stinkendreich.handy.sh.garage": "Garagentor", "stinkendreich.handy.sh.garage.an": "bleibt zu", "stinkendreich.handy.sh.garage.aus": "automatisch",
    "stinkendreich.handy.smart.tor.zu": "Tor zu halten", "stinkendreich.handy.smart.tor.auf": "Tor automatisch",
    "stinkendreich.handy.smart.garage.zu": "Garage zu halten", "stinkendreich.handy.smart.garage.auf": "Garage automatisch",
    "stinkendreich.smarthome.tor.an": "▥ Das Eingangstor bleibt geschlossen.", "stinkendreich.smarthome.tor.aus": "Das Eingangstor öffnet wieder automatisch.",
    "stinkendreich.smarthome.garage.an": "▦ Das Garagentor bleibt geschlossen.", "stinkendreich.smarthome.garage.aus": "Das Garagentor öffnet wieder automatisch.",
    "stinkendreich.handy.sh.haie": "Haibecken", "stinkendreich.handy.sh.haie.los": "Gast rein!",
    "stinkendreich.handy.smart.rollo.zu": "Rollläden ▼",
    "stinkendreich.handy.smart.rollo.auf": "Rollläden ▲",
    "stinkendreich.handy.smart.bruecke.zu": "Brücke ein",
    "stinkendreich.handy.smart.bruecke.auf": "Brücke aus",
    "stinkendreich.handy.smart.licht.an": "Licht an",
    "stinkendreich.handy.smart.licht.aus": "Licht aus",
    "stinkendreich.handy.smart.disco.an": "Disco an",
    "stinkendreich.handy.smart.disco.aus": "Disco aus",
    "stinkendreich.handy.smart.automatik.an": "Automatik an",
    "stinkendreich.handy.smart.inkognito.an": "◐ Inkognito",
    "stinkendreich.handy.smart.inkognito.aus": "Villa zurück",
    "stinkendreich.handy.smart.krisenbunker.an": "☢ Bunker",
    "stinkendreich.handy.smart.krisenbunker.aus": "Haus an",
    "stinkendreich.smarthome.krisenbunker.an": "☢ Krisenbunker: Aufzug gesperrt, Haus dicht. Raus nur über den Notausgang.",
    "stinkendreich.smarthome.krisenbunker.aus": "Krisenbunker aus, der Aufzug fährt wieder.",
    "stinkendreich.aufzug.gesperrt": "☢ Aufzug gesperrt (Krisenbunker)",
    "stinkendreich.handy.smart.haie.auf": "♒ Haie auf",
    "stinkendreich.handy.smart.haie.zu": "Haie zu",
    "stinkendreich.smarthome.haie.auf": "♒ Das Haibecken ist offen!",
    "stinkendreich.smarthome.haie.zu": "Haibecken geschlossen.",
    "stinkendreich.smarthome.inkognito.an": "◐ Inkognito: Die Villa fährt in den Boden …",
    "stinkendreich.smarthome.inkognito.aus": "Die Villa taucht wieder auf …",
    "stinkendreich.inkognito.laeuft": "Die Villa fährt gerade, bitte kurz warten.",
    "stinkendreich.inkognito.gemerkt": "Die Villa fährt noch – sobald sie angekommen ist, geht es weiter.",
    "stinkendreich.inkognito.zutief": "Hier ist unter der Villa kein Platz zum Versenken.",
    "stinkendreich.handy.smart.automatik.aus": "Automatik aus",
    "stinkendreich.smarthome.nur_bewohner": "Das Smart Home steuern nur Bewohner.",
    "stinkendreich.smarthome.rollo.an": "Rollläden fahren runter",
    "stinkendreich.smarthome.rollo.aus": "Rollläden fahren hoch",
    "stinkendreich.smarthome.bruecke.an": "Zugbrücke fährt ein",
    "stinkendreich.smarthome.bruecke.aus": "Zugbrücke fährt aus",
    "stinkendreich.smarthome.licht.an": "Licht an",
    "stinkendreich.smarthome.licht.aus": "Licht aus",
    "stinkendreich.smarthome.farbe.an": "Lichtfarbe: %s",
    "stinkendreich.smarthome.farbe.aus": "Lichtfarbe: %s",
    "stinkendreich.smarthome.disco.an": "Disco! Die Lichter tanzen",
    "stinkendreich.smarthome.disco.aus": "Disco aus",
    "stinkendreich.smarthome.automatik.an": "Automatik an: abends Rollläden runter, Licht an, Brücke ein",
    "stinkendreich.smarthome.automatik.aus": "Automatik aus",
    "stinkendreich.smarthome.abend": "🌙 Guten Abend: Rollläden runter, Licht an, Brücke eingefahren",
    "stinkendreich.smarthome.morgen": "☀ Guten Morgen: Rollläden hoch, Licht aus, Brücke ausgefahren",
    "stinkendreich.smarthome.farbe.weiss": "Weiß",
    "stinkendreich.handy.smart.farbe.weiss": "Licht: Weiß",
    "stinkendreich.smarthome.farbe.warm": "Warmweiß",
    "stinkendreich.handy.smart.farbe.warm": "Licht: Warmweiß",
    "stinkendreich.smarthome.farbe.rot": "Rot",
    "stinkendreich.handy.smart.farbe.rot": "Licht: Rot",
    "stinkendreich.smarthome.farbe.gruen": "Grün",
    "stinkendreich.handy.smart.farbe.gruen": "Licht: Grün",
    "stinkendreich.smarthome.farbe.blau": "Blau",
    "stinkendreich.handy.smart.farbe.blau": "Licht: Blau",
    "stinkendreich.smarthome.farbe.lila": "Lila",
    "stinkendreich.handy.smart.farbe.lila": "Licht: Lila",
    "stinkendreich.smarthome.farbe.pink": "Pink",
    "stinkendreich.handy.smart.farbe.pink": "Licht: Pink",
    "stinkendreich.smarthome.farbe.gold": "Gold",
    "stinkendreich.handy.smart.farbe.gold": "Licht: Gold",
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
    "stinkendreich.handy.auftrag.stopp": "■ Abbrechen",
    "stinkendreich.handy.auftrag.reset": "↺ James neu",
    "stinkendreich.karte.von": "Zugangskarte · Villa von %s",
    "stinkendreich.villa.baubeginn": "Du bekommst deine eigene Villa! Sie entsteht gerade in der Nachbarschaft, das dauert etwa eine Minute.",
    "stinkendreich.villa.baustelle": "🏗 Deine Villa wird gebaut … %s %%",
    "stinkendreich.villa.warteschlange": "🏗 Deine Villa kommt gleich dran …",
    "stinkendreich.villa.willkommen": "Willkommen in deiner eigenen Villa, %s! James erwartet dich in der Halle.",
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
    "stinkendreich.ohnemod.fernseher_hinweis": "Ohne Mod bleibt der Bildschirm dunkel. Das Video läuft im Browser deines PCs.",
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
    "stinkendreich.handy.orte": "My places …",
    "stinkendreich.handy.titel.orte": "My places",
    "stinkendreich.handy.orte.name": "Name for this place",
    "stinkendreich.handy.orte.speichern": "Save this place",
    "stinkendreich.handy.orte.leer": "No places saved yet",
    "stinkendreich.handy.gaeste": "Guest list",
    "stinkendreich.handy.titel.gaeste": "Guest list",
    "stinkendreich.handy.gaeste.name": "Player name",
    "stinkendreich.handy.gaeste.eintragen": "Add as guest",
    "stinkendreich.handy.gaeste.leer": "No guests yet",
    "stinkendreich.handy.lieferung": "Delivery",
    "stinkendreich.handy.auftraege": "Orders",
    "stinkendreich.handy.mitarbeiter": "Order workers",
    "stinkendreich.handy.bodyguards": "Call bodyguards",
    "stinkendreich.handy.bodyguards_heim": "Bodyguards home",
    "stinkendreich.handy.bruecke": "Drawbridge in/out",
    "stinkendreich.handy.smarthome": "Smart Home",
    "stinkendreich.handy.titel.smarthome": "Smart Home", "stinkendreich.handy.titel.smart": "Smart Home",
    "stinkendreich.handy.sh.rollo": "Shutters", "stinkendreich.handy.sh.rollo.an": "closed", "stinkendreich.handy.sh.rollo.aus": "open",
    "stinkendreich.handy.sh.bruecke": "Drawbridge", "stinkendreich.handy.sh.bruecke.an": "raised", "stinkendreich.handy.sh.bruecke.aus": "down",
    "stinkendreich.handy.sh.licht": "Lights", "stinkendreich.handy.sh.licht.an": "on", "stinkendreich.handy.sh.licht.aus": "off",
    "stinkendreich.handy.sh.disco": "Disco", "stinkendreich.handy.sh.disco.an": "on", "stinkendreich.handy.sh.disco.aus": "off",
    "stinkendreich.handy.sh.automatik": "Automatic", "stinkendreich.handy.sh.automatik.an": "on", "stinkendreich.handy.sh.automatik.aus": "off",
    "stinkendreich.handy.sh.inkognito": "Incognito", "stinkendreich.handy.sh.inkognito.an": "hidden", "stinkendreich.handy.sh.inkognito.aus": "off",
    "stinkendreich.handy.sh.krisenbunker": "Crisis bunker", "stinkendreich.handy.sh.krisenbunker.an": "sealed", "stinkendreich.handy.sh.krisenbunker.aus": "off",
    "stinkendreich.handy.sh.tor": "Front gate", "stinkendreich.handy.sh.tor.an": "stays shut", "stinkendreich.handy.sh.tor.aus": "automatic",
    "stinkendreich.handy.sh.garage": "Garage door", "stinkendreich.handy.sh.garage.an": "stays shut", "stinkendreich.handy.sh.garage.aus": "automatic",
    "stinkendreich.handy.smart.tor.zu": "Keep gate shut", "stinkendreich.handy.smart.tor.auf": "Gate automatic",
    "stinkendreich.handy.smart.garage.zu": "Keep garage shut", "stinkendreich.handy.smart.garage.auf": "Garage automatic",
    "stinkendreich.smarthome.tor.an": "▥ The front gate stays shut.", "stinkendreich.smarthome.tor.aus": "The front gate opens automatically again.",
    "stinkendreich.smarthome.garage.an": "▦ The garage door stays shut.", "stinkendreich.smarthome.garage.aus": "The garage door opens automatically again.",
    "stinkendreich.handy.sh.haie": "Shark tank", "stinkendreich.handy.sh.haie.los": "Throw in!",
    "stinkendreich.handy.smart.rollo.zu": "Shutters ▼",
    "stinkendreich.handy.smart.rollo.auf": "Shutters ▲",
    "stinkendreich.handy.smart.bruecke.zu": "Bridge in",
    "stinkendreich.handy.smart.bruecke.auf": "Bridge out",
    "stinkendreich.handy.smart.licht.an": "Lights on",
    "stinkendreich.handy.smart.licht.aus": "Lights off",
    "stinkendreich.handy.smart.disco.an": "Disco on",
    "stinkendreich.handy.smart.disco.aus": "Disco off",
    "stinkendreich.handy.smart.automatik.an": "Auto on",
    "stinkendreich.handy.smart.inkognito.an": "◐ Incognito",
    "stinkendreich.handy.smart.inkognito.aus": "Villa back",
    "stinkendreich.handy.smart.krisenbunker.an": "☢ Crisis bunker",
    "stinkendreich.handy.smart.krisenbunker.aus": "House on",
    "stinkendreich.smarthome.krisenbunker.an": "☢ Crisis bunker: elevator locked, house sealed. Exit only via the emergency exit.",
    "stinkendreich.smarthome.krisenbunker.aus": "Crisis bunker off, the elevator runs again.",
    "stinkendreich.aufzug.gesperrt": "☢ Elevator locked (crisis bunker)",
    "stinkendreich.handy.smart.haie.auf": "♒ Sharks",
    "stinkendreich.handy.smart.haie.zu": "Close",
    "stinkendreich.smarthome.haie.auf": "♒ The shark tank is open!",
    "stinkendreich.smarthome.haie.zu": "Shark tank closed.",
    "stinkendreich.smarthome.inkognito.an": "◐ Incognito: the villa sinks into the ground …",
    "stinkendreich.smarthome.inkognito.aus": "The villa rises again …",
    "stinkendreich.inkognito.laeuft": "The villa is moving, please wait.",
    "stinkendreich.inkognito.gemerkt": "The villa is still moving – it will continue as soon as it arrives.",
    "stinkendreich.inkognito.zutief": "There is no room below the villa to sink it.",
    "stinkendreich.handy.smart.automatik.aus": "Auto off",
    "stinkendreich.smarthome.nur_bewohner": "Only residents can control the smart home.",
    "stinkendreich.smarthome.rollo.an": "Shutters going down",
    "stinkendreich.smarthome.rollo.aus": "Shutters going up",
    "stinkendreich.smarthome.bruecke.an": "Drawbridge retracting",
    "stinkendreich.smarthome.bruecke.aus": "Drawbridge extending",
    "stinkendreich.smarthome.licht.an": "Lights on",
    "stinkendreich.smarthome.licht.aus": "Lights off",
    "stinkendreich.smarthome.farbe.an": "Light colour: %s",
    "stinkendreich.smarthome.farbe.aus": "Light colour: %s",
    "stinkendreich.smarthome.disco.an": "Disco! The lights are dancing",
    "stinkendreich.smarthome.disco.aus": "Disco off",
    "stinkendreich.smarthome.automatik.an": "Automation on: in the evening shutters down, lights on, bridge in",
    "stinkendreich.smarthome.automatik.aus": "Automation off",
    "stinkendreich.smarthome.abend": "🌙 Good evening: shutters down, lights on, bridge retracted",
    "stinkendreich.smarthome.morgen": "☀ Good morning: shutters up, lights off, bridge extended",
    "stinkendreich.smarthome.farbe.weiss": "White",
    "stinkendreich.handy.smart.farbe.weiss": "Light: White",
    "stinkendreich.smarthome.farbe.warm": "Warm White",
    "stinkendreich.handy.smart.farbe.warm": "Light: Warm White",
    "stinkendreich.smarthome.farbe.rot": "Red",
    "stinkendreich.handy.smart.farbe.rot": "Light: Red",
    "stinkendreich.smarthome.farbe.gruen": "Green",
    "stinkendreich.handy.smart.farbe.gruen": "Light: Green",
    "stinkendreich.smarthome.farbe.blau": "Blue",
    "stinkendreich.handy.smart.farbe.blau": "Light: Blue",
    "stinkendreich.smarthome.farbe.lila": "Purple",
    "stinkendreich.handy.smart.farbe.lila": "Light: Purple",
    "stinkendreich.smarthome.farbe.pink": "Pink",
    "stinkendreich.handy.smart.farbe.pink": "Light: Pink",
    "stinkendreich.smarthome.farbe.gold": "Gold",
    "stinkendreich.handy.smart.farbe.gold": "Light: Gold",
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
    "stinkendreich.handy.auftrag.stopp": "■ Cancel",
    "stinkendreich.handy.auftrag.reset": "↺ Reset James",
    "stinkendreich.karte.von": "Key Card · %s's Villa",
    "stinkendreich.villa.baubeginn": "You're getting your own villa! It's being built in the neighbourhood, this takes about a minute.",
    "stinkendreich.villa.baustelle": "🏗 Building your villa … %s %%",
    "stinkendreich.villa.warteschlange": "🏗 Your villa is next in line …",
    "stinkendreich.villa.willkommen": "Welcome to your own villa, %s! James is waiting for you in the hall.",
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
    "stinkendreich.ohnemod.fernseher_hinweis": "Without the mod the screen stays dark. The video plays in your PC's browser.",
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
    """Bergmann der Firma „Stinkend Reich“: gelber Schutzhelm mit Stirnlampe, Warnweste mit Reflexstreifen und
    goldenem $ auf dem Rücken, Firmenausweis, dunkles Arbeitshemd, Lederhandschuhe, Arbeitshose mit Knieschonern
    und Stiefel. Jede Fläche leicht schattiert (oben heller, unten dunkler, etwas Körnung) wie bei guten Skins."""
    zufall = random.Random(7)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    haut, haar = (222, 172, 134), (88, 58, 36)
    weste, weste_dunkel = (255, 128, 24), (226, 98, 12)
    reflex, reflex_hell = (196, 202, 210), (236, 240, 246)
    hemd, hemd_dunkel = (52, 62, 82), (40, 48, 66)
    hose, hose_dunkel = (44, 56, 86), (34, 44, 70)
    helm, helm_hell, helm_dunkel = (252, 204, 30), (255, 230, 110), (208, 160, 12)
    leder, leder_dunkel = (176, 124, 68), (138, 94, 50)
    stiefel, sohle = (74, 50, 32), (32, 28, 26)
    seite_licht = {"oben": 1.08, "vorn": 1.0, "rechts": 0.9, "links": 0.9, "hinten": 0.86, "unten": 0.74}

    def ton(c, s, y=0, h=1, koernung=0.035):
        """Farbe mit Seitenlicht, sanftem Verlauf nach unten und Körnung."""
        f = seite_licht.get(s, 1.0) * (1.0 - 0.06 * (y / max(h - 1, 1)))
        f *= 1.0 + zufall.uniform(-koernung, koernung)
        return tuple(max(0, min(255, int(v * f))) for v in c[:3])

    # --- Kopf: Gesicht mit Augenbrauen, Augen, Nase, Bartstoppeln; Haar an den Seiten und hinten ---
    stoppel = dunkler(haut, 0.84)

    def kopf(s, x, y, w, h):
        if s == "oben":
            return ton(haar, s)
        if s == "unten":
            return ton(haut, s)
        if s == "hinten":
            return ton(haar, s, y, h) if y < 6 else ton(haut, s)
        if s in ("rechts", "links"):
            hinten = x < 4 if s == "rechts" else x > 3
            ohr = (x == 4 if s == "rechts" else x == 3) and y in (4, 5)
            if y < 4 or (hinten and y < 6):
                return ton(haar, s, y, h)
            if ohr:
                return ton(dunkler(haut, 0.88), s)
            return ton(stoppel if y >= 6 else haut, s)
        # vorn
        if y < 3:
            return ton(haar, s)
        if y == 3:
            return ton((70, 46, 28), s, koernung=0) if x in (1, 2, 5, 6) else ton(haut, s)     # Augenbrauen
        if y == 4:
            if x in (1, 6):
                return (244, 244, 244)                                                       # Augenweiß
            if x in (2, 5):
                return (58, 96, 150)                                                         # Iris
            return ton(haut, s)
        if y == 5:
            if x in (3, 4):
                return ton(dunkler(haut, 0.9), s, koernung=0)                                # Nase
            return ton(haut, s)
        if y == 6:
            if x in (3, 4):
                return (150, 82, 72)                                                         # Mund
            return ton(stoppel if x in (1, 2, 5, 6) else haut, s)
        return ton(stoppel, s)                                                               # Kinn mit Stoppeln
    wuerfel(img, 0, 0, 8, 8, 8, kopf)

    # --- Hut-Schicht: Schutzhelm mit Krempe, Gummiband und Stirnlampe ---
    def helmfarbe(s, x, y):
        if s == "oben":
            if x in (3, 4):
                return helm_hell                                                             # Mittelrippe
            return ton(helm, s, koernung=0.02) if 0 < x < 7 and 0 < y < 7 else ton(helm_dunkel, s, koernung=0.02)
        if y == 3:
            return ton(helm_dunkel, s, koernung=0.02)                                        # Krempe
        if y == 2:
            return (34, 34, 38)                                                              # Band der Lampe
        return ton(helm_hell if y == 0 else helm, s, koernung=0.02)

    def hut(s, x, y, w, h):
        if s == "unten" or (s != "oben" and y > 3):
            return None
        if s == "vorn" and 2 <= x <= 5 and 1 <= y <= 2:
            if y == 2 and x in (3, 4):
                return (255, 250, 214)                                                       # Lampenglas
            if y == 1 and x in (3, 4):
                return (236, 236, 220)
            return (70, 72, 78)                                                              # Lampengehäuse
        return helmfarbe(s, x, y)
    wuerfel(img, 32, 0, 8, 8, 8, hut)

    # --- Körper: Warnweste über dem Arbeitshemd, zwei Reflexstreifen, Gürtel, Ausweis, $ auf dem Rücken ---
    dollar = {(4, 0), (3, 1), (4, 1), (5, 1), (3, 2), (4, 3), (5, 4), (3, 5), (4, 5), (5, 5), (4, 6)}

    def koerper(s, x, y, w, h):
        if s == "oben":
            return ton(hemd, s) if s == "oben" and 2 <= x <= 5 and y in (1, 2) else ton(weste, s)
        if s == "unten":
            return ton(hose, s)
        if y == 11:
            return ton(hose, s)
        if y == 10:
            if s == "vorn" and x in (3, 4):
                return (196, 196, 204) if x == 3 else (150, 150, 160)                       # Gürtelschnalle
            return ton((66, 44, 28), s, koernung=0.02)                                       # Gürtel
        if y in (6, 8):
            return reflex_hell if (x + y) % 3 == 0 else reflex                               # Reflexstreifen
        if s == "vorn":
            if y < 3 and 3 <= x <= 4:
                return ton(hemd if y > 0 else hemd_dunkel, s)                                # Ausschnitt
            if y < 2 and x in (2, 5):
                return ton(hemd_dunkel, s)                                                   # Kragen
            if (x, y) == (5, 3) or (x, y) == (6, 3):
                return (240, 240, 236) if y == 3 and x == 6 else (230, 232, 236)             # Ausweis
            if (x, y) == (5, 4) or (x, y) == (6, 4):
                return GOLD if x == 5 else (60, 100, 170)
            if x == 4 and y >= 3:
                return ton(weste_dunkel, s, koernung=0.01)                                   # Reißverschluss
        if s == "hinten" and (x - 0, y) in dollar:
            return GOLD_HELL if (x + y) % 2 else GOLD
        return ton(weste, s, y, h)
    wuerfel(img, 16, 16, 8, 12, 4, koerper)

    # --- Arme: Hemdsärmel mit Falten, Lederhandschuhe mit Stulpe ---
    def arm(s, x, y, w, h):
        if s == "oben":
            return ton(hemd, s)
        if s == "unten":
            return ton(leder_dunkel, s)
        if y >= 9:
            if y == 9:
                return ton(leder_dunkel, s, koernung=0.02)                                   # Stulpe
            return ton(leder, s, y - 9, 3)
        if y in (3, 7) and (x + y) % 2 == 0:
            return ton(hemd_dunkel, s)                                                       # Falten
        return ton(hemd, s, y, 9)
    wuerfel(img, 40, 16, 4, 12, 4, arm)

    # --- Beine: Arbeitshose mit Knieschonern und Reflexband, Stiefel mit Sohle ---
    def bein(s, x, y, w, h):
        if s == "oben":
            return ton(hose, s)
        if s == "unten":
            return sohle
        if y == 11:
            return sohle if s != "vorn" or x in (0, 3) else (58, 58, 62)                     # Sohle, vorn Stahlkappe
        if y == 10:
            return ton(stiefel, s, koernung=0.02)
        if y == 9:
            return ton(dunkler(stiefel, 1.15), s, koernung=0.02) if s != "vorn" or x in (0, 3) else (196, 180, 150)   # Schnürung
        if y == 7:
            return reflex_hell if (x + y) % 3 == 0 else reflex                               # Reflexband
        if s == "vorn" and y in (4, 5):
            return ton((58, 60, 66), s, koernung=0.02) if y == 4 or 0 < x < 3 else ton((44, 46, 50), s)   # Knieschoner
        if s == "vorn" and y < 3 and x == 3:
            return ton(hose_dunkel, s)                                                       # Naht
        return ton(hose, s, y, 9)
    wuerfel(img, 0, 16, 4, 12, 4, bein)
    return img


# ---------------------------------------------------------------------------
# Bodyguard als Mensch (64×64, Spieler-Haut) für Spieler ohne Mod (Server-Version: Schaufensterpuppe, größer)
# ---------------------------------------------------------------------------

def bodyguard_mensch() -> Image.Image:
    """Glatzköpfiger Türsteher im Smoking mit Sonnenbrille und Ohrstöpsel (Spieler-Hautlayout mit linken Gliedern)."""
    zufall = random.Random(13)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    haut = (176, 128, 96)
    jacke, jacke_hell = (24, 24, 30), (42, 42, 52)
    hemd, fliege = (240, 240, 236), (14, 14, 16)

    def kopf(s, x, y, w, h):
        if s == "unten":
            return dunkler(haut)
        if s == "vorn":
            if y == 3 and 1 <= x <= 6:
                return (12, 12, 14) if x not in (3, 4) else (40, 40, 44)      # Sonnenbrille
            if y == 2 and x in (1, 2, 5, 6):
                return dunkler(haut, 0.7)                                     # Augenbrauen
            if y == 6 and 2 <= x <= 5:
                return dunkler(haut, 0.75)                                    # Mund
        if s in ("rechts", "links") and y == 3:
            return (12, 12, 14)                                               # Bügel der Brille
        if s == "rechts" and y == 4 and x == 3:
            return (60, 60, 64)                                               # Ohrstöpsel
        return haut if zufall.random() > 0.06 else dunkler(haut, 0.94)
    wuerfel(img, 0, 0, 8, 8, 8, kopf)

    def koerper(s, x, y, w, h):
        if s == "oben":
            return jacke
        if s == "unten":
            return jacke
        if s == "vorn":
            if y == 0 and 3 <= x <= 4:
                return fliege                                                 # Fliege
            if y < 6 and abs(x - 3.5) < 1.6 - y * 0.2:
                return hemd
            if y < 6 and abs(x - 3.5) < 1.9 - y * 0.2:
                return jacke_hell                                             # Revers
            if x in (3, 4) and y in (7, 9):
                return (60, 60, 66)                                           # Knöpfe
        return jacke if (x + y) % 6 else jacke_hell
    wuerfel(img, 16, 16, 8, 12, 4, koerper)

    def arm(s, x, y, w, h):
        if s == "oben":
            return jacke
        if y >= 11:
            return hemd if s != "unten" else haut                             # Manschette
        if s == "unten":
            return haut
        return jacke if (x + y) % 5 else jacke_hell
    wuerfel(img, 40, 16, 4, 12, 4, arm)
    wuerfel(img, 32, 48, 4, 12, 4, arm)

    def bein(s, x, y, w, h):
        if s == "unten" or y >= 10:
            return (10, 10, 12) if y < 11 or s == "unten" else (36, 36, 40)  # Lackschuhe
        if s == "rechts" and x == 2:
            return (40, 40, 48)                                               # Galonstreifen
        return (20, 20, 26)
    wuerfel(img, 0, 16, 4, 12, 4, bein)
    wuerfel(img, 16, 48, 4, 12, 4, bein)
    return img


# ---------------------------------------------------------------------------
# Bodyguard (128×128, Eisengolem-Modell)
# ---------------------------------------------------------------------------

def bodyguard_smoking() -> Image.Image:
    """Überzug für den Eisengolem (Layout 128 × 128 wie seine Textur): nur Sakko und Sonnenbrille, alles andere
    bleibt durchsichtig. Die Mod legt das Bild beim Start über die echte Golem-Textur aus Minecraft (Nase, Gesicht,
    Hände und Füße bleiben so, wie Minecraft sie zeichnet)."""
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    jacke, jacke_hell = (18, 18, 22), (30, 30, 38)
    hemd = (240, 240, 236)
    glas, glanz = (12, 12, 14), (70, 74, 86)

    def kopf(s, x, y, w, h):
        # Sonnenbrille genau über den Augen (Zeilen 5 und 6 des Gesichts), Bügel an den Seiten
        if s == "vorn" and y in (5, 6):
            if x in (3, 4):
                return glas if y == 5 else None                            # Steg über der Nase
            return glanz if (y == 5 and x in (1, 5)) else glas
        if s == "rechts" and y == 5 and x >= 3:
            return glas
        if s == "links" and y == 5 and x <= 4:
            return glas
        return None
    wuerfel(img, 0, 0, 8, 10, 8, kopf)

    def koerper(s, x, y, w, h):
        # Sakko wie auf Philips Bild: schwarz, weißes Hemd als schmales V mit Krawatte, rote Blume am Revers
        if s == "vorn":
            mitte = (w - 1) / 2
            if y <= 9 and abs(x - mitte) < 0.6:
                return (8, 8, 10)                                         # Krawatte
            if y < 6 and abs(x - mitte) < 2.6 - y * 0.4:
                return hemd
            if (x, y) in ((13, 2), (14, 2), (13, 3), (14, 3)):
                return (205, 25, 35) if (x, y) != (13, 2) else (235, 60, 60)   # Blüte
            if (x, y) in ((14, 4), (14, 5)):
                return (40, 130, 40)                                      # Stiel
            if (x, y) == (13, 5):
                return (60, 160, 50)                                      # Blatt
        return jacke if (x + y) % 9 else jacke_hell
    wuerfel(img, 0, 40, 18, 12, 11, koerper)
    wuerfel(img, 0, 70, 9, 5, 6, lambda s, x, y, w, h: jacke)               # Hosenbund

    def arm(s, x, y, w, h):
        # Ärmel bis kurz vor die Hand, die eiserne Hand bleibt frei
        if s == "unten" or y >= 25:
            return None
        return jacke if (x + y) % 9 else jacke_hell
    wuerfel(img, 60, 21, 4, 30, 6, arm)
    wuerfel(img, 60, 58, 4, 30, 6, arm)

    def bein(s, x, y, w, h):
        # Anzughose bis kurz über die eisernen Füße
        if s == "unten" or y >= 13:
            return None
        return jacke if (x + y) % 9 else jacke_hell
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
    # Linke Seite als Spiegelbild: so ist der Mitarbeiter auch als Schaufensterpuppe (Server ohne Mod) vollständig
    linke_seite(mitarbeiter()).save(ASSETS / "textures/entity/mitarbeiter.png")
    bodyguard_smoking().save(ASSETS / "textures/entity/bodyguard_smoking.png")
    bodyguard_mensch().save(ASSETS / "textures/entity/bodyguard_mensch.png")
    return {"de": TEXTE_DE, "en": TEXTE_EN}


if __name__ == "__main__":
    main()
