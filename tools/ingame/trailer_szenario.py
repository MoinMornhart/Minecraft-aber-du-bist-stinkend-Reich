#!/usr/bin/env python3
"""Schreibt das Drehbuch für den Trailer der Mod (tools/ingame/szenarien/trailer.txt).

Kürzer und schneller als der Rundgang (video_szenario.py): kurze Kamerafahrten mit großen Schlagzeilen,
dazwischen die besten Vorführungen (Tor, Portaltür, Ballsaal, Konzertflügel im Wohnzimmer, Esszimmer, Fernseher mit Video und Fernbedienung, Geheimclub,
Butler, Auto, Hubschrauber, Handy mit Navi, Bodyguards, Straße zum Dorf, Agenten-Waffen) und am Ende die Villa bei
Sonnenuntergang. Der Fernseher spielt ein Video von der Platte (YouTube ist beim Dreh nicht erreichbar), in Zeitlupe.

Aufruf: python3 tools/ingame/trailer_szenario.py
Danach: SICHTWEITE=12 STINKENDREICH_TESTVIDEO=<video.mp4> STINKENDREICH_VIDEOTEMPO=0.333 \
            tools/ingame/test.sh 26.3 tools/ingame/szenarien/trailer.txt
        ZEITLUPE=3 python3 tools/ingame/video.py build/ingame/26.3/trailer_roh.mp4 build/ingame/26.3/trailer_kapitel.txt \\
            build/ingame/26.3/trailer.mp4 "Minecraft, aber du bist stinkend reich" "Fabric-Mod für 1.21 bis 26.3"
"""
from __future__ import annotations

import math
from pathlib import Path

ZIEL = Path(__file__).resolve().parent / "szenarien" / "trailer.txt"
# Zeitlupe wie beim Rundgang: Das Spiel läuft beim Dreh langsamer, video.py spielt die Aufnahme schneller ab
ZEITLUPE = 3
zeilen: list[str] = []


def z(*texte: str) -> None:
    """Zeilen anhängen; Wartezeiten (warte, halte, bis) werden mit der Zeitlupe gestreckt."""
    for t in texte:
        teile = t.split(" ", 2)
        if teile[0] in ("warte", "halte", "bis") and len(teile) > 1:
            teile[1] = f"{float(teile[1]) * ZEITLUPE:g}"
            t = " ".join(teile)
        zeilen.append(t)


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
    z(f"server /villa ort {punkt(*start)}", "warte 1")
    fahrt(sekunden, start, (x, y, z_, gier, neigung))


def titel(text: str, tag: bool = True) -> None:
    if tag:
        z("server /time set 6000")
    z(f"kapitel {text}")


# ---------------------------------------------------------------------------
z("# Trailer (erzeugt von tools/ingame/trailer_szenario.py)",
  "server /gamemode spectator", "server /time set 6000", "server /weather clear", "server /difficulty peaceful",
  "server /villa stufe 0", "server /butler strasse dorf", "server /villa ort 160 80 175 139 22")
# Leere Hände (das geschenkte Handy läge sonst in der Hand und öffnete sich bei jedem Rechtsklick)
zeilen += ["warte 30", "server /clear @p", "taste F1", f"server /tick rate {20 / ZEITLUPE:.3f}", "warte 5", "video start trailer"]

titel("Jede neue Welt startet hier")
fahrt(9, (160, 80, 175, 139, 22), (118, 58, 128, 139, 19), (80, 48, 138, 165, 16), (56, 36, 118, 180, 10))
titel("Mit eigenem Tor")
z("server /villa ort 55.9 27 106 180 4", "warte 2")
fahrt(4, (55.9, 27, 106, 180, 4), (55.6, 27.2, 88, 180, 2))
titel("… und einer Haustür aus Gold")
z("server /gamemode creative", "server /villa ort 55 26.5 76 180 2", "warte 1", "server /villa ort 55 26 70 180 2", "warte 2.5",
  "server /gamemode spectator")
fahrt(3, (55, 27.6, 70, 180, 2), (55.5, 27.6, 65.5, 192, 5))

titel("Ein Ballsaal mit Kronleuchtern")
zufahren(4, 51.5, 29, 66, 200, 14, zurueck=0.8, schwenk=18)  # dahinter liegt gleich die Südwand
# Der Flügel steht seit 0.3.5 im Wohnzimmer, gleich links hinter der Tür aus dem Ballsaal (Bank bei x 44, z 63)
titel("Ein Konzertflügel, der wirklich spielt")
z("server /villa ort 44.5 26 62.4 0 30", "server /gamemode creative", "warte 1", "klick", "warte 1", "linksklick 376 292",
  "warte 1", "taste Escape", "server /gamemode spectator")
zufahren(5, 40.5, 27.4, 60.5, -40, 22, zurueck=2.5, schwenk=-12)
titel("Ein Esszimmer für 18 Gäste")
zufahren(4, 70.5, 29, 52, 0, 38, zurueck=2, schwenk=10)

titel("Ein Butler, der für dich arbeitet")
z("server /villa ort 57.5 26 66 180 5", "warte 1",
  "server /execute at @p rotated ~ 0 run tp @e[type=stinkendreich:butler,limit=1] ^ ^ ^3.2 facing entity @p feet",
  "server /data merge entity @e[type=stinkendreich:butler,limit=1] {NoAI:1b}",
  "server /villa ort 57.5 27.4 66 180 8", "warte 2.5",
  "server /data merge entity @e[type=stinkendreich:butler,limit=1] {NoAI:0b}")

titel("Ein Penthouse über den Wolken")
zufahren(5, 62.5, 39.4, 43.2, 180, 22, zurueck=2.5, schwenk=12)
titel("Ein Fernseher, der Videos wirklich abspielt")
z("server /villa ort 63.5 39.6 41.6 0 10", "klient fernseher !abspielen|aqz-KE-bpKQ", "warte 3")
zufahren(5, 63.5, 39.6, 42.4, 0, 8, zurueck=2, schwenk=8)
titel("… mit Fernbedienung")
zufahren(3.5, 62.6, 39.4, 43.6, -20, 48, zurueck=1, schwenk=-10)
z("klient fernseher !stopp")
titel("Whirlpool auf dem Dach")
zufahren(4, 70, 41, 47, 211, 19)

titel("Ein Nachtclub im Keller")
zufahren(4, 42, 17.6, 58, 180, 8)
titel("… und ein Geheimclub hinter dem Bücherregal")
z("server /gamemode survival", "server /villa ort 31.5 16 64 90 5", "warte 1", "druecke shift", "warte 1.2", "loslassen shift",
  "warte 1.5", "server /gamemode spectator")
zufahren(4, 16.5, 9, 80, 150, 30, zurueck=2)

titel("Ein Bunker mit Waffenkammer")
zufahren(5, 64.2, 7.5, 40, 270, 10, zurueck=1, schwenk=10)
titel("Und ein Berg aus Gold")
zufahren(4, 67.5, 10, 52.6, 0, 25, zurueck=0.4, schwenk=-20, hoch=1.5)

titel("Tiefgarage mit Drehteller")
z("server /gamemode creative", "server /villa ort 91.5 17 45.2 180 30", "warte 1",
  "server /execute at @p run summon stinkendreich:bonzenauto ~ ~ ~-4.7 {Rotation:[0f,0f],stinkendreich:\"{\\\"farbe\\\":4}\"}",
  "warte 1.5", "klick", "warte 6", "halte 0.4 shift", "server /gamemode spectator")
titel("James fährt dich – auf Straßen, die er selbst baut")
z("server /gamemode creative", "server /villa ort 55.5 26 86 0 15", "warte 1.5", "merke", "server Butler, hol den Wagen",
  "bis 40 vorgefahren", "warte 1", "server /ride @p mount @e[type=stinkendreich:stretchlimo,sort=nearest,limit=1]",
  "warte 1", "server Butler, fahr mich zum Dorf", "warte 9", "server /gamemode spectator")
titel("… oder fliegt dich")
z("server /villa ort 66 47.5 60 135 18", "warte 1.5", "server /gamemode creative", "server /villa ort 58 43 50.5 90 0", "warte 1",
  "server /ride @p mount @e[type=stinkendreich:hubschrauber,sort=nearest,limit=1]", "warte 4",
  "halte 3 space", "halte 4 w", "druecke w a", "warte 2", "loslassen w a", "server /gamemode spectator")

titel("Ein goldenes Handy")
z("server /gamemode creative", "server /villa ort 55.5 26 90 180 -6", "warte 1.5",
  "server /item replace entity @p weapon.mainhand with stinkendreich:handy", "warte 0.5",
  "server /execute as @p run handy ort|merken|Strandbar", "server /execute as @p run handy ort|merken|Meine Mine",
  "klick", "warte 2.5")
titel("… mit Navi zu deinen Lieblingsorten")
z("linksklick 640 272", "warte 1.2", "linksklick 640 406", "warte 2.5", "taste Escape", "warte 0.5", "klick", "warte 1.5")
titel("Bodyguards: Eisengolems im Sakko")
z("merke", "linksklick 640 440", "bis 10 Bodyguards sind da", "warte 1", "server /gamemode spectator",
  "server /execute at @e[type=stinkendreich:bodyguard,limit=1,sort=nearest] run tp @p ^ ^1.6 ^5 facing entity "
  "@e[type=stinkendreich:bodyguard,limit=1,sort=nearest] eyes", "warte 3")

titel("Agenten-Anzug, goldene Pistole, Geldkanone")
z("server /gamemode survival", "server /difficulty normal",
  "server /item replace entity @p armor.head with stinkendreich:bond_sonnenbrille",
  "server /item replace entity @p armor.chest with stinkendreich:smoking",
  "server /item replace entity @p armor.legs with stinkendreich:anzughose",
  "server /item replace entity @p armor.feet with stinkendreich:lackschuhe",
  "server /item replace entity @p weapon.mainhand with stinkendreich:geldkanone",
  "server /villa ort 55.5 26 93.5 0 0", "warte 1.5", "taste F5", "taste F5", "warte 2", "taste F5",
  "server /execute as @p at @s run summon vindicator ^ ^ ^9 {NoAI:1b}",
  "server /execute as @p at @s run summon vindicator ^1.5 ^ ^11", "warte 1", "klick", "warte 1", "klick", "warte 2",
  "server /kill @e[type=vindicator]", "server /gamemode spectator", "server /difficulty peaceful")

titel("Minecraft, aber du bist stinkend reich", tag=False)
z("server /time set 12650", "server /villa ort 56 52 142 180 22", "warte 2")
fahrt(10, (56, 52, 142, 180, 22), (90, 56, 140, 150, 19), (118, 58, 128, 120, 17))
titel("Jetzt auf GitHub – Fabric, Minecraft 1.21 bis 26.3", tag=False)
z("warte 4", "video stopp", "server /tick rate 20")

ZIEL.write_text("\n".join(zeilen) + "\n", encoding="utf-8")
print(f"{ZIEL}: {len(zeilen)} Zeilen")
