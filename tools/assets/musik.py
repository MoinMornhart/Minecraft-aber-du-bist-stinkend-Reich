#!/usr/bin/env python3
"""Erzeugt Grafiken, Modelle und Texte der Musikanlage (DJ-Pult) und der Lautsprecher.

Eigene Pixel-Grafiken im Bonzen-Look: glänzend schwarz mit Gold. Schreibt wie assets.py nach
src/main/resources (Texturen, Block- und Item-Modelle, Item-Definitionen, Blockzustände,
Beutetabellen, Werkzeug-Tag) und ergänzt die Sprachdateien de_de/en_us.

Aufruf: python3 tools/assets/musik.py
"""
from __future__ import annotations

import json
import math

from assets import ASSETS, NS, RES, B, bs_waagerecht, neu, rauschen, schreibe_json

# ---------------------------------------------------------------------------
# Farben
# ---------------------------------------------------------------------------
SCHWARZ = (18, 18, 21)
LACK = (30, 30, 35)
GLANZ = (58, 58, 66)
GOLD = (214, 175, 55)
GOLD_HELL = (255, 223, 120)
GOLD_DUNKEL = (140, 105, 25)
GRAU = (70, 70, 76)


def lackiert(saat: int):
    """Glänzend schwarzer Grund mit feinem Rauschen."""
    img, d = neu(LACK)
    rauschen(img, 3, saat)
    return img, d


def goldrand(d) -> None:
    d.rectangle([0, 0, 15, 15], outline=GOLD)
    d.line([(0, 0), (15, 0)], fill=GOLD_HELL)
    d.line([(0, 15), (15, 15)], fill=GOLD_DUNKEL)


def kreis(img, mx: float, my: float, r: float, farbe, innen: float = 0.0) -> None:
    """Gefüllter Kreisring von Radius innen bis r (Pixelmitten zählen)."""
    px = img.load()
    for x in range(16):
        for y in range(16):
            a = math.hypot(x + 0.5 - mx, y + 0.5 - my)
            if innen <= a <= r:
                px[x, y] = farbe + (255,)


# ---------------------------------------------------------------------------
# Texturen
# ---------------------------------------------------------------------------


def tex_anlage_oben():
    """Draufsicht: zwei Plattenteller mit Goldrand, dazwischen das Mischpult, vorne Regler."""
    img, d = lackiert(31)
    for mx in (4.5, 11.5):
        kreis(img, mx, 6.5, 3.4, (150, 150, 158), 2.6)  # silberner Tellerrand
        kreis(img, mx, 6.5, 2.6, (16, 16, 18))          # Schallplatte
        kreis(img, mx, 6.5, 1.9, (52, 52, 58), 1.5)     # Rille
        kreis(img, mx, 6.5, 0.8, (200, 35, 50))         # Etikett
    # Mischpult in der Mitte mit zwei Schiebereglern
    d.line([(8, 2), (8, 10)], fill=SCHWARZ)
    d.point((8, 4), fill=GOLD_HELL)
    d.point((8, 8), fill=GOLD_HELL)
    # Drehregler und Crossfader vorne
    for x in (2, 5, 10, 13):
        d.point((x, 12), fill=GOLD)
    d.line([(4, 14), (11, 14)], fill=GRAU)
    d.point((7, 14), fill=GOLD_HELL)
    d.point((8, 14), fill=GOLD_HELL)
    goldrand(d)
    return img


def kante(d) -> None:
    """Goldene Oberkante; das Modell zeigt an den Seiten nur die Zeilen 2–15 (Pult ist 14 Pixel hoch)."""
    d.line([(0, 2), (15, 2)], fill=GOLD_HELL)
    d.line([(0, 3), (15, 3)], fill=GOLD)
    d.line([(0, 15), (15, 15)], fill=GOLD_DUNKEL)


def tex_anlage_vorne():
    """Seite zum DJ: Aussteuerungsanzeige (grün–gelb–rot) im schwarzen Feld, goldene Regler."""
    img, d = lackiert(32)
    kante(d)
    d.rectangle([1, 5, 14, 11], fill=SCHWARZ)
    for x, h in zip((3, 5, 7, 9, 11, 13), (4, 6, 7, 5, 6, 3)):
        for k in range(h):
            farbe = (60, 220, 90) if k < 3 else (240, 210, 40) if k < 5 else (235, 50, 50)
            d.point((x, 11 - k), fill=farbe)
    for x in (3, 6, 9, 12):
        d.point((x, 13), fill=GOLD_HELL)
    return img


def tex_anlage_hinten():
    """Seite zum Publikum: goldenes Dollarzeichen auf schwarzem Lack."""
    img, d = lackiert(33)
    kante(d)
    dollar = [
        "...#...",
        ".#####.",
        "##.#...",
        ".####..",
        "...#.##",
        "##.#.##",
        ".#####.",
        "...#...",
    ]
    for y, zeile in enumerate(dollar):
        for x, z in enumerate(zeile):
            if z == "#":
                d.point((4 + x, 5 + y), fill=GOLD_HELL if y < 4 else GOLD)
    return img


def tex_anlage_seite():
    img, d = lackiert(34)
    kante(d)
    d.line([(1, 6), (14, 6)], fill=GLANZ)
    d.line([(1, 13), (14, 13)], fill=GOLD_DUNKEL)
    return img


def tex_lautsprecher_vorne():
    """Große Membran mit Goldring, darüber der Hochtöner."""
    img, d = lackiert(41)
    goldrand(d)
    kreis(img, 8.0, 10.0, 5.0, GOLD, 4.1)
    kreis(img, 8.0, 10.0, 4.1, (26, 26, 29))
    kreis(img, 8.0, 10.0, 3.0, (46, 46, 52), 2.3)
    kreis(img, 8.0, 10.0, 1.4, GLANZ)
    img.putpixel((7, 9), (140, 140, 150, 255))
    # Hochtöner: kleiner Goldring mit Kalotte
    d.rectangle([6, 2, 9, 3], fill=GOLD)
    d.rectangle([7, 2, 8, 3], fill=GLANZ)
    return img


def tex_lautsprecher_seite():
    img, d = lackiert(42)
    d.line([(0, 0), (0, 15)], fill=GOLD)
    d.line([(15, 0), (15, 15)], fill=GOLD_DUNKEL)
    d.line([(0, 0), (15, 0)], fill=GOLD_HELL)
    d.line([(0, 15), (15, 15)], fill=GOLD_DUNKEL)
    return img


def tex_lautsprecher_oben():
    img, d = lackiert(43)
    goldrand(d)
    d.rectangle([3, 3, 12, 12], outline=GLANZ)
    return img


TEXTUREN = {
    "musikanlage_oben": tex_anlage_oben,
    "musikanlage_vorne": tex_anlage_vorne,
    "musikanlage_hinten": tex_anlage_hinten,
    "musikanlage_seite": tex_anlage_seite,
    "lautsprecher_vorne": tex_lautsprecher_vorne,
    "lautsprecher_seite": tex_lautsprecher_seite,
    "lautsprecher_oben": tex_lautsprecher_oben,
}

# ---------------------------------------------------------------------------
# Modelle (Vorderseite nach Norden, für facing=north)
# ---------------------------------------------------------------------------


def m_musikanlage() -> dict:
    """Pult 14 Pixel hoch, darauf zwei leicht erhabene Plattenteller."""
    t = {"particle": B("musikanlage_seite"), "vorne": B("musikanlage_vorne"), "hinten": B("musikanlage_hinten"),
         "seite": B("musikanlage_seite"), "oben": B("musikanlage_oben")}

    def teller(x0: float) -> dict:
        return {
            "from": [x0, 14, 3], "to": [x0 + 7, 14.5, 10],
            "faces": {
                "up": {"uv": [x0, 3, x0 + 7, 10], "texture": "#oben"},
                "north": {"uv": [x0, 0, x0 + 7, 0.5], "texture": "#seite"},
                "south": {"uv": [x0, 0, x0 + 7, 0.5], "texture": "#seite"},
                "east": {"uv": [0, 0, 7, 0.5], "texture": "#seite"},
                "west": {"uv": [0, 0, 7, 0.5], "texture": "#seite"},
            },
        }

    return {
        "parent": "minecraft:block/block",
        "textures": t,
        "elements": [
            {
                "from": [0, 0, 0], "to": [16, 14, 16],
                "faces": {
                    "north": {"uv": [0, 2, 16, 16], "texture": "#vorne", "cullface": "north"},
                    "south": {"uv": [0, 2, 16, 16], "texture": "#hinten", "cullface": "south"},
                    "east": {"uv": [0, 2, 16, 16], "texture": "#seite", "cullface": "east"},
                    "west": {"uv": [0, 2, 16, 16], "texture": "#seite", "cullface": "west"},
                    "up": {"uv": [0, 0, 16, 16], "texture": "#oben"},
                    "down": {"uv": [0, 0, 16, 16], "texture": "#seite", "cullface": "down"},
                },
            },
            teller(1.0),
            teller(8.0),
        ],
    }


def m_lautsprecher() -> dict:
    return {"parent": "minecraft:block/orientable",
            "textures": {"front": B("lautsprecher_vorne"), "side": B("lautsprecher_seite"), "top": B("lautsprecher_oben")}}


BLOECKE = {
    "musikanlage": dict(modell=m_musikanlage(), de="Musikanlage (DJ-Pult)", en="Sound System (DJ Desk)"),
    "lautsprecher": dict(modell=m_lautsprecher(), de="Lautsprecher", en="Speaker"),
}

# ---------------------------------------------------------------------------
# Texte (Menü der Musikanlage)
# ---------------------------------------------------------------------------
TEXTE = {
    # Schlüssel: (Deutsch, Englisch)
    "stinkendreich.musik.titel": ("Musikanlage", "Sound System"),
    "stinkendreich.musik.reiter.dateien": ("Eigene Musik", "Own Music"),
    "stinkendreich.musik.reiter.spotify": ("Spotify", "Spotify"),
    "stinkendreich.musik.laeuft": ("▶ %s  (DJ: %s)", "▶ %s  (DJ: %s)"),
    "stinkendreich.musik.gestoppt": ("■ Gerade läuft nichts", "■ Nothing playing"),
    "stinkendreich.musik.spotify_bei": ("Spotify bei %s: %s", "Spotify at %s: %s"),
    "stinkendreich.musik.fehlt": ("Diese Datei fehlt in deinem Musikordner, deshalb hörst du nichts.",
                                  "This file is missing from your music folder, so you hear nothing."),
    "stinkendreich.musik.leer.mp3": ("Noch keine Musik da. Lege OGG- oder MP3-Dateien in den Musikordner (Knopf unten).",
                                     "No music yet. Put OGG or MP3 files into the music folder (button below)."),
    "stinkendreich.musik.leer.ogg": ("Noch keine Musik da. Lege OGG-Dateien in den Musikordner (Knopf unten). MP3 geht hier nicht.",
                                     "No music yet. Put OGG files into the music folder (button below). MP3 is not available."),
    "stinkendreich.musik.seite": ("Seite %s/%s", "Page %s/%s"),
    "stinkendreich.musik.zurueck": ("⏮ Zurück", "⏮ Back"),
    "stinkendreich.musik.abspielen": ("▶ Spielen", "▶ Play"),
    "stinkendreich.musik.stopp": ("■ Stopp", "■ Stop"),
    "stinkendreich.musik.weiter": ("Weiter ⏭", "Next ⏭"),
    "stinkendreich.musik.lautstaerke": ("Lautstärke: %s %%", "Volume: %s %%"),
    "stinkendreich.musik.zufall.an": ("Zufall: an", "Shuffle: on"),
    "stinkendreich.musik.zufall.aus": ("Zufall: aus", "Shuffle: off"),
    "stinkendreich.musik.wiederholen.aus": ("Wiederholen: aus", "Repeat: off"),
    "stinkendreich.musik.wiederholen.alle": ("Wiederholen: alle", "Repeat: all"),
    "stinkendreich.musik.wiederholen.eins": ("Wiederholen: einen", "Repeat: one"),
    "stinkendreich.musik.ordner": ("Ordner öffnen", "Open folder"),
    "stinkendreich.musik.formate.mp3": ("OGG und MP3 aus %s", "OGG and MP3 from %s"),
    "stinkendreich.musik.formate.ogg": ("Nur OGG (MP3 nicht verfügbar) aus %s", "OGG only (no MP3) from %s"),
    "stinkendreich.spotify.einrichten": ("Spotify ist noch nicht eingerichtet. So geht's:", "Spotify is not set up yet. How to:"),
    "stinkendreich.spotify.schritt1": ("1. App auf developer.spotify.com anlegen", "1. Create an app on developer.spotify.com"),
    "stinkendreich.spotify.schritt2": ("2. Als Redirect-URI eintragen: %s", "2. Add this redirect URI: %s"),
    "stinkendreich.spotify.schritt3": ("3. Client-ID in config/stinkendreich.json", "3. Put the client ID into config/stinkendreich.json"),
    "stinkendreich.spotify.premium": ("Nur mit Spotify Premium. Die Musik läuft auf deinem Spotify-Gerät (Handy, PC, Box), nicht im Spiel – die anderen sehen nur den Titel.",
                                      "Spotify Premium only. Music plays on your Spotify device (phone, PC, speaker), not in the game – others only see the title."),
    "stinkendreich.spotify.anleitung": ("Anleitung öffnen", "Open guide"),
    "stinkendreich.spotify.verbinden": ("Mit Spotify verbinden", "Connect to Spotify"),
    "stinkendreich.spotify.warten": ("Bitte im Browser bei Spotify anmelden und zustimmen …", "Please log in to Spotify in your browser and agree …"),
    "stinkendreich.spotify.link": ("[Falls sich kein Browser öffnet: hier klicken, um dich bei Spotify anzumelden]",
                                   "[If no browser opens: click here to log in to Spotify]"),
    "stinkendreich.spotify.verbunden": ("Spotify ist verbunden. Viel Spaß beim Auflegen!", "Spotify is connected. Have fun DJing!"),
    "stinkendreich.spotify.nichts": ("Gerade läuft nichts auf Spotify.", "Nothing is playing on Spotify."),
    "stinkendreich.spotify.kein_geraet": ("Kein aktives Spotify-Gerät. Öffne Spotify auf Handy oder PC und spiel kurz etwas ab.",
                                          "No active Spotify device. Open Spotify on your phone or PC and play something briefly."),
    "stinkendreich.spotify.pause": ("⏸ Pause", "⏸ Pause"),
    "stinkendreich.spotify.lautstaerke": ("Spotify-Lautstärke: %s %%", "Spotify volume: %s %%"),
    "stinkendreich.spotify.playlists": ("Deine Playlists:", "Your playlists:"),
    "stinkendreich.spotify.keine_playlists": ("Keine Playlists gefunden.", "No playlists found."),
    "stinkendreich.spotify.laedt": ("Playlists werden geladen …", "Loading playlists …"),
    "stinkendreich.spotify.trennen": ("Trennen", "Disconnect"),
    "stinkendreich.spotify.fehler.premium": ("Dafür brauchst du Spotify Premium.", "This needs Spotify Premium."),
    "stinkendreich.spotify.fehler.geraet": ("Kein aktives Spotify-Gerät. Öffne Spotify auf Handy oder PC und spiel kurz etwas ab.",
                                            "No active Spotify device. Open Spotify on your phone or PC and play something briefly."),
    "stinkendreich.spotify.fehler.verweigert": ("Spotify hat abgelehnt. Ist dein Konto in deiner Spotify-App unter „User Management“ eingetragen?",
                                                "Spotify refused. Is your account listed under “User Management” in your Spotify app?"),
    "stinkendreich.spotify.fehler.lautstaerke": ("Dieses Gerät erlaubt keine Lautstärke von außen.", "This device does not allow remote volume."),
    "stinkendreich.spotify.fehler.abgelaufen": ("Nicht (mehr) mit Spotify verbunden. Bitte neu verbinden.", "Not connected to Spotify (anymore). Please connect again."),
    "stinkendreich.spotify.fehler.bremse": ("Spotify bremst gerade (zu viele Anfragen). Kurz warten.", "Spotify is rate limiting. Please wait a moment."),
    "stinkendreich.spotify.fehler.netz": ("Spotify ist nicht erreichbar. Besteht eine Internetverbindung?", "Spotify is unreachable. Are you online?"),
    "stinkendreich.spotify.fehler.port": ("Port %s ist belegt. Wähle in config/stinkendreich.json einen anderen spotifyPort und trage ihn auch bei Spotify ein.",
                                          "Port %s is in use. Choose another spotifyPort in config/stinkendreich.json and add it at Spotify too."),
    "stinkendreich.spotify.fehler.client": ("Anmeldung fehlgeschlagen. Stimmen Client-ID und Redirect-URI in deiner Spotify-App?",
                                            "Login failed. Do client ID and redirect URI match your Spotify app?"),
    "stinkendreich.spotify.fehler.abgebrochen": ("Anmeldung bei Spotify abgebrochen.", "Spotify login cancelled."),
    "stinkendreich.spotify.fehler.zeit": ("Keine Anmeldung innerhalb von 5 Minuten. Bitte erneut verbinden.", "No login within 5 minutes. Please connect again."),
    "stinkendreich.spotify.fehler.unbekannt": ("Spotify meldet Fehler %s.", "Spotify reports error %s."),
    "stinkendreich.ton.musik": ("Musik spielt", "Music plays"),
}


def main() -> None:
    for name, fabrik in TEXTUREN.items():
        pfad = ASSETS / "textures/block" / f"{name}.png"
        pfad.parent.mkdir(parents=True, exist_ok=True)
        fabrik().save(pfad)

    texte_de = {k: v[0] for k, v in TEXTE.items()}
    texte_en = {k: v[1] for k, v in TEXTE.items()}
    for name, b in BLOECKE.items():
        schreibe_json(ASSETS / "models/block" / f"{name}.json", b["modell"])
        schreibe_json(ASSETS / "blockstates" / f"{name}.json", bs_waagerecht(B(name)))
        # bis 1.21.3: Item-Modell; ab 1.21.4: Item-Definition
        schreibe_json(ASSETS / "models/item" / f"{name}.json", {"parent": B(name)})
        schreibe_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": B(name)}})
        schreibe_json(RES / f"data/{NS}/loot_table/blocks/{name}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        })
        texte_de[f"block.{NS}.{name}"] = b["de"]
        texte_en[f"block.{NS}.{name}"] = b["en"]

    # Spitzhacke baut schneller ab (Einträge anderer Generatoren bleiben erhalten)
    tag = RES / "data/minecraft/tags/block/mineable/pickaxe.json"
    werte = json.loads(tag.read_text(encoding="utf-8"))["values"] if tag.exists() else []
    for name in BLOECKE:
        if f"{NS}:{name}" not in werte:
            werte.append(f"{NS}:{name}")
    schreibe_json(tag, {"replace": False, "values": werte})

    for datei, texte in (("de_de", texte_de), ("en_us", texte_en)):
        pfad = ASSETS / "lang" / f"{datei}.json"
        vorhanden = json.loads(pfad.read_text(encoding="utf-8")) if pfad.exists() else {}
        vorhanden.update(texte)
        schreibe_json(pfad, dict(sorted(vorhanden.items())))
    print(f"{len(TEXTUREN)} Texturen, {len(BLOECKE)} Blöcke, {len(TEXTE)} Texte geschrieben")


if __name__ == "__main__":
    main()
