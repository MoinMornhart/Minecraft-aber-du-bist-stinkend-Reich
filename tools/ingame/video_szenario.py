#!/usr/bin/env python3
"""Schreibt das Drehbuch für das Rundgang-Video (tools/ingame/szenarien/video.txt).

Jeder Raum bekommt eine kurze Kamerafahrt: Die Kamera gleitet auf einen bewährten Fotostandpunkt zu und
schwenkt dabei leicht. Dazwischen führen kleine Vorführungen die Mechanismen vor (Tor, Haustür, Butler,
Aufzug, Fernseher, Hinsetzen, Hubschrauber, Drehteller, Chauffeur, Agenten-Waffen, Poolabdeckung …).

Aufruf: python3 tools/ingame/video_szenario.py
Danach: SICHTWEITE=10 tools/ingame/test.sh 26.3 tools/ingame/szenarien/video.txt
        python3 tools/ingame/video.py build/ingame/26.3/rundgang_roh.mp4 build/ingame/26.3/rundgang_kapitel.txt build/ingame/26.3/rundgang.mp4
"""
from __future__ import annotations

import math
from pathlib import Path

ZIEL = Path(__file__).resolve().parent / "szenarien" / "video.txt"
zeilen: list[str] = []


def z(*texte: str) -> None:
    zeilen.extend(texte)


def punkt(x, y, z_, gier, neigung) -> str:
    return f"{x:.2f} {y:.2f} {z_:.2f} {gier:.1f} {neigung:.1f}"


def fahrt(sekunden: float, *punkte) -> None:
    """Kamerafahrt durch die Punkte; wartet, bis sie fertig ist (Meldung im Protokoll)."""
    z("merke", f"server fahrt {sekunden} | " + " | ".join(punkt(*p) for p in punkte),
      f"bis {int(sekunden * 3 + 20)} Fahrt fertig")


def zufahren(sekunden: float, x, y, z_, gier, neigung, zurueck=3.5, schwenk=-15.0, hoch=0.0) -> None:
    """Gleitet von etwas weiter hinten (und leicht verdreht) auf den Standpunkt zu."""
    dx, dz = -math.sin(math.radians(gier)), math.cos(math.radians(gier))
    start = (x - dx * zurueck, y + hoch, z_ - dz * zurueck, gier + schwenk, neigung)
    z(f"server /villa ort {punkt(*start)}", "warte 1.5")
    fahrt(sekunden, start, (x, y, z_, gier, neigung))


def kapitel(text: str) -> None:
    z(f"kapitel {text}")


# ---------------------------------------------------------------------------
z("# Video: Rundgang durch die ganze Villa mit allen Mechanismen (erzeugt von tools/ingame/video_szenario.py)",
  "server /gamemode spectator", "server /time set 6000", "server /weather clear", "server /difficulty peaceful",
  "server /villa ort 118 58 128 139 19", "warte 30", "taste F1", "warte 5",
  "video start rundgang")

# Anflug
kapitel("Das Anwesen")
fahrt(16, (118, 58, 128, 139, 19), (95, 54, 140, 158, 18), (62, 46, 142, 178, 17), (56, 33, 112, 180, 9))
kapitel("Einfahrtstor öffnet sich (Sicherheitsstufe 0)")
z("server /villa ort 55.9 27 106 180 4", "warte 2", "server /villa stufe 0", "warte 3.5")
fahrt(6, (55.9, 27, 106, 180, 4), (55.7, 27.3, 92, 180, 3), (55.2, 27.2, 80, 180, 2))
kapitel("Gläserne Schiebetür")
z("server /gamemode creative", "server /villa ort 55 26.5 76 180 2", "warte 1.5", "server /villa ort 55 26 71.5 180 2", "warte 3",
  "server /gamemode spectator")
fahrt(4, (55, 27.6, 71.5, 180, 2), (55.5, 27.6, 66.5, 195, 5))

# Butler
kapitel("Butler James wartet in der Halle")
z("server /villa ort 57.5 26 66 180 5", "warte 1",
  "server /execute at @p rotated ~ 0 run tp @e[type=stinkendreich:butler,limit=1] ^ ^ ^3.2 facing entity @p feet",
  "server /data merge entity @e[type=stinkendreich:butler,limit=1] {NoAI:1b}",
  "server /villa ort 57.5 27.4 66 180 8", "warte 3", "server /gamemode creative", "warte 1", "klient butlermenue", "warte 4",
  "klient schliessen", "warte 1", "server /gamemode spectator",
  "server /data merge entity @e[type=stinkendreich:butler,limit=1] {NoAI:0b}")

# Erdgeschoss
kapitel("Ballsaal")
zufahren(7, 54.9, 26.5, 66, 180, 2, zurueck=2.5, schwenk=-10)
fahrt(6, (54.9, 26.5, 66, 180, 2), (52, 31, 63, 165, 20), (51, 33, 61, 160, 25))
kapitel("Luxusküche")
zufahren(6, 70, 27.5, 50, 180, 15)
kapitel("Personalküche für James")
zufahren(5, 76, 27, 43, 180, 10, zurueck=2.5)
kapitel("Esszimmer")
zufahren(6, 70, 27.5, 64, 180, 15)
kapitel("Wohnzimmer mit Kamin")
zufahren(6, 44, 27.5, 54, 120, 10)
fahrt(4, (44, 27.5, 54, 120, 10), (40, 27.5, 49, 175, 6), (39, 27.5, 46, 180, 5))

# Obergeschoss
kapitel("Obergeschoss: Schlafzimmer mit Himmelbett")
zufahren(6, 37, 33.6, 57.5, 90, 15, zurueck=2.5)
kapitel("Bad mit goldener Wanne")
zufahren(5, 44, 33.5, 46, 135, 15, zurueck=2.5)
kapitel("Büro mit Überwachungsmonitoren")
zufahren(6, 69, 34.2, 57.6, 0, 25, zurueck=3)
kapitel("Tresortür öffnet sich")
z("server /villa ort 62 31 47", "server /setblock ~ ~ ~ minecraft:redstone_block", "server /villa ort 63.9 32 51 180 4", "warte 4")
fahrt(4, (63.9, 32, 51, 180, 4), (64.5, 33.5, 46.3, 180, 25))
z("warte 1", "server /villa ort 62 31 47", "server /setblock ~ ~ ~ minecraft:air")

# Sicherheit
kapitel("Sicherheitsstufe 3: Rollläden runter, Laser an")
z("server /villa ort 56 33 90 180 6", "warte 2", "server /villa stufe 3", "warte 5")
z("server /villa ort 23 28.5 52 180 20", "warte 1.5")
fahrt(5, (23, 28.5, 52, 180, 20), (23, 28.5, 49, 185, 22))

# Bunker
kapitel("Untergrundbasis: Laser im Gang")
zufahren(5, 57.5, 7, 52, 180, 8, zurueck=3)
kapitel("Waffenkammer")
zufahren(7, 64.2, 7.5, 40, 270, 10, zurueck=3, schwenk=10)
fahrt(5, (64.2, 7.5, 40, 270, 10), (68.6, 7.4, 34.6, 235, 8))
kapitel("Geldspeicher mit Goldberg")
zufahren(6, 67.5, 10, 52, 0, 25, zurueck=3)
z("server /villa stufe 1", "warte 2")

# Aufzug
kapitel("Aufzug: aus dem Bunker bis ins Penthouse")
z("server /gamemode creative", "server /villa ort 58 6 42 180 8", "warte 3", "klient aufzug Penthouse", "warte 2",
  "taste F5", "warte 3", "taste F5", "warte 4", "taste F5", "warte 6", "server /gamemode spectator")

# Penthouse
kapitel("Penthouse: XXL-Kingsize-Bett")
zufahren(7, 62.5, 39.4, 43.2, 180, 22, zurueck=2.5, schwenk=12)
kapitel("XXL-Fernseher")
z("server /data merge entity @e[type=stinkendreich:fernseher,limit=1] {stinkendreich_video:\"aqz-KE-bpKQ\"}")
zufahren(5, 62.5, 39.6, 39.5, 0, 4, zurueck=2)
kapitel("Kinosofa: Rechtsklick zum Hinsetzen")
z("server /gamemode creative", "server /villa ort 62.5 38 45.6 180 50", "warte 1.5", "klick", "warte 1.5", "taste F5",
  "warte 4", "taste F5", "taste F5", "halte 0.4 shift", "server /gamemode spectator")
kapitel("Lounge und Hausbar")
zufahren(6, 52, 40.5, 52, 135, 15)

# Dach
kapitel("Dachparty mit DJ-Pult")
zufahren(7, 54, 40.5, 76, 180, 18)
kapitel("Whirlpool und Strandliegen auf dem Dach")
zufahren(6, 70, 41, 47, 211, 19)
kapitel("Luxus-Hubschrauber: selbst fliegen")
z("server /villa ort 66 47.5 60 135 18", "warte 2", "server /gamemode creative", "server /villa ort 58 43 50.5 90 0", "warte 1",
  "server /ride @p mount @e[type=stinkendreich:hubschrauber,sort=nearest,limit=1]", "warte 6",
  "halte 3.5 space", "halte 5 w", "druecke w a", "warte 3", "loslassen w a", "halte 3 w", "warte 2",
  "server /gamemode spectator")

# Garten und Pool
kapitel("Poolparty mit Rutsche")
zufahren(7, 54, 30, 34, 160, 20)
fahrt(5, (54, 30, 34, 160, 20), (76, 30, 22, 100, 20))
kapitel("Poolabdeckung fährt zu")
z("server /villa ort 70.5 26 22.5 0 0", "server /setblock ~ ~ ~ minecraft:lever[face=floor,facing=north,powered=true]",
  "server /villa ort 55 36 31 180 40", "warte 7")

# Keller
kapitel("Nachtclub im Keller")
zufahren(7, 42, 17.6, 58, 180, 8)
fahrt(5, (42, 17.6, 58, 180, 8), (50, 17.6, 54, 90, 5))
kapitel("Tiefgarage mit Fuhrpark")
zufahren(7, 75, 19.5, 49, 180, 20, zurueck=4)
kapitel("Drehteller wendet das Auto")
z("server /gamemode creative", "server /villa ort 91.5 17 45.2 180 30", "warte 1",
  "server /execute at @p run summon stinkendreich:bonzenauto ~ ~ ~-4.7 {Rotation:[0f,0f],stinkendreich:\"{\\\"farbe\\\":4}\"}",
  "warte 2", "klick", "warte 8", "halte 0.4 shift", "server /gamemode spectator")

# Chauffeur
kapitel("James als Chauffeur: „Butler, hol den Wagen“")
z("server /gamemode creative", "server /villa ort 55.5 26 86 0 15", "warte 2", "merke", "server Butler, hol den Wagen",
  "bis 40 vorgefahren", "warte 3", "server /gamemode spectator")

# Agent
kapitel("Agenten-Anzug: Bond-Sonnenbrille, Smoking, Lackschuhe")
z("server /gamemode survival", "server /difficulty normal",
  "server /item replace entity @p armor.head with stinkendreich:bond_sonnenbrille",
  "server /item replace entity @p armor.chest with stinkendreich:smoking",
  "server /item replace entity @p armor.legs with stinkendreich:anzughose",
  "server /item replace entity @p armor.feet with stinkendreich:lackschuhe",
  "server /item replace entity @p weapon.mainhand with stinkendreich:goldene_pistole",
  "server /villa ort 55.5 26 93.5 0 0", "warte 2", "taste F5", "taste F5", "warte 4", "taste F5")
kapitel("Goldene Pistole, Laser-Uhr, Geldkanone")
z("server /execute as @p at @s run summon vindicator ^ ^ ^9 {NoAI:1b}",
  "server /execute as @p at @s run summon vindicator ^1.5 ^ ^11 {NoAI:1b}", "warte 2", "klick", "warte 1.5",
  "server /item replace entity @p weapon.mainhand with stinkendreich:laseruhr", "warte 1", "klick", "warte 2",
  "server /item replace entity @p weapon.mainhand with stinkendreich:geldkanone",
  "server /execute as @e[type=vindicator] run data merge entity @s {NoAI:0b}", "klick", "warte 3",
  "server /kill @e[type=vindicator]", "server /gamemode spectator")

# Abend
kapitel("Abends über der Villa")
z("server /time set 12650", "server /villa ort 56 52 142 180 22", "warte 3")
fahrt(14, (56, 52, 142, 180, 22), (90, 56, 140, 150, 19), (118, 58, 128, 120, 17))
z("warte 2", "video stopp")

ZIEL.write_text("\n".join(zeilen) + "\n", encoding="utf-8")
print(f"{ZIEL}: {len(zeilen)} Zeilen")
