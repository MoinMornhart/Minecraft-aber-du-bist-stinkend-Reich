#!/usr/bin/env python3
"""Schreibt das Drehbuch für den Trailer der Mod (tools/ingame/szenarien/trailer.txt).

Kürzer und schneller als der Rundgang (video_szenario.py): kurze Kamerafahrten mit großen Schlagzeilen,
dazwischen die besten Vorführungen (Tor, Portaltür, Ballsaal, Konzertflügel im Wohnzimmer, Esszimmer, Fernseher mit Video und Fernbedienung, Geheimclub,
echte Haie, Butler, Auto, Straßen mit Brücken, Hubschrauber, Handy mit Navi und Gästeliste, Bodyguards mit Pistole, Agenten-Waffen,
Nachbarvilla mit Nummernschild) und am Ende die Villa bei
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
# Vor der Aufnahme: Nachbarvilla für einen Freund bauen lassen (im Hintergrund, mit Straße) und einen Wagen mit
# Nummernschild davorstellen; danach zurück zur Spawn-Villa
# (erst wenn die automatischen Straßen stehen: Die Brücken-Aufnahme braucht die Endportal-Straße als Nummer 3)
zeilen += ["warte 30", "bis 900 Straße Endportal: Ende", "merke", "server /villa nachbar Aircrafter", "bis 600 Baustelle: Villa 1 fertig",
           "merke", "bis 900 Straße Villa von Aircrafter: Ende", "server /villa hin 1", "warte 2",
           "server /villa ort 55.5 26 94 180 0",
           "server /execute at @p run summon stinkendreich:gelaendewagen ~ ~ ~ {Rotation:[180f,0f]}", "warte 8",
           "server /villa hin 0", "warte 5"]
# Leere Hände (das geschenkte Handy läge sonst in der Hand und öffnete sich bei jedem Rechtsklick)
zeilen += ["server /clear @p", "taste F1", f"server /tick rate {20 / ZEITLUPE:.3f}", "warte 5", "video start trailer"]

titel("Jede neue Welt startet hier")
fahrt(9, (160, 80, 175, 139, 22), (118, 58, 128, 139, 19), (80, 48, 138, 165, 16), (56, 36, 118, 180, 10))
titel("Mit eigenem Tor")
z("server /villa ort 55.9 27 106 180 4", "warte 2")
fahrt(4, (55.9, 27, 106, 180, 4), (55.6, 27.2, 88, 180, 2))
titel("… mit Zugbrücke über dem Graben")
z("server /villa ort 55 33 120 180 28", "warte 1", "server /villa bruecke ein", "warte 5", "server /villa bruecke aus", "warte 4")
titel("… und einer Haustür aus Gold")
z("server /gamemode creative", "server /villa ort 55 26.5 76 180 2", "warte 1", "server /villa ort 55 26 70 180 2", "warte 2.5",
  "server /gamemode spectator")
fahrt(3, (55, 26.2, 70, 180, -2), (55, 26.2, 64.5, 186, -6))   # Augenhöhe wie ein Spieler, mitten durch die Tür

titel("Ein Ballsaal mit Kronleuchtern")
zufahren(4, 51.5, 29, 66, 200, 14, zurueck=0.8, schwenk=18)  # dahinter liegt gleich die Südwand
# Der Flügel steht seit 0.3.5 im Wohnzimmer, links hinter der Tür aus dem Ballsaal (Bank bei x 40, z 60)
titel("Ein Konzertflügel, der wirklich spielt")
z("server /villa ort 40.5 26 59.4 0 30", "server /gamemode creative", "warte 1", "klick", "warte 1", "linksklick 376 292",
  "warte 1", "taste Escape", "server /gamemode spectator")
zufahren(5, 44, 27.4, 58, 38, 22, zurueck=2, schwenk=-12)
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

titel("Smart Home: Licht, Farben, Disco – per Handy")
z("server /item replace entity @p weapon.mainhand with stinkendreich:handy", "server /villa ort 43 27 57 0 -28", "warte 1",
  "server /execute as @p run handy smart|licht|an", "server /execute as @p run handy smart|farbe|lila", "warte 2",
  "server /execute as @p run handy smart|farbe|blau", "warte 1.5", "server /execute as @p run handy smart|disco|an", "warte 4",
  "server /execute as @p run handy smart|disco|aus", "server /execute as @p run handy smart|farbe|warm", "server /clear @p")
titel("Ein Nachtclub im Keller")
zufahren(4, 42, 17.6, 58, 180, 8)
titel("VIP-Restaurant unter einer Glaskuppel im Meeresaquarium")
zufahren(5, 46.5, 16.6, 79.8, 0, 8, zurueck=2.5, schwenk=12)
titel("Ein Haifischbecken im Wohnzimmer")
zufahren(4, 44, 23.5, 59, 180, 10, zurueck=1.5, schwenk=10)
titel("… mit echten Haien")
z("server /villa ort 47.5 22.8 46 90 0", "warte 3.5")
titel("… für freche Gäste")
z("server /difficulty normal", "server /summon zombie -10 88 -6 {PersistenceRequired:1b}", "server /villa ort 48 30 50 90 55",
  "warte 1.5", "server /execute as @p run handy smart|haie|auf", "warte 4", "server /kill @e[type=zombie]",
  "server /difficulty peaceful")
titel("… und ein Geheimclub hinter dem Bücherregal")
z("server /gamemode survival", "server /villa ort 31.5 16 64 90 5", "warte 1", "druecke shift", "warte 1.2", "loslassen shift",
  "warte 1.5", "server /gamemode spectator")
zufahren(4, 16.5, 9, 80, 150, 30, zurueck=2)

titel("Ein Bunker mit Waffenkammer")
zufahren(5, 64.2, 7.5, 40, 270, 10, zurueck=1, schwenk=10)
titel("Und ein Berg aus Gold")
zufahren(4, 67.5, 10, 52.6, 0, 25, zurueck=0.4, schwenk=-20, hoch=1.5)

titel("Eine Bunker-Suite mit Riesen-Fernseher")
z("server /villa ort 44.5 9.2 60.5 180 14", "klient fernseher !abspielen|aqz-KE-bpKQ", "warte 2")
zufahren(4, 51.5, 10.4, 58.5, 115, 24, zurueck=1.5, schwenk=-10)
z("klient fernseher !stopp")
titel("Echte Regenduschen")
z("server /villa ort 47.5 6.2 64.4 0 12", "server /setblock -9 70 11 stinkendreich:duschkopf[facing=north,an=true,wand=false]",
  "server /setblock -9 69 11 stinkendreich:duscharmatur[facing=north,an=true]", "warte 3.5")
titel("Die Kommandozentrale")
fahrt(5, (53.5, 6.5, 44.5, 90, 8), (46, 7.4, 42, 160, 14), (45.5, 7.2, 40, 180, 22))
titel("Modus FESTUNG")
z("server /villa stufe 4", "warte 1")
fahrt(12, (55, 40, 150, 180, 15), (-15, 50, 125, 222, 22), (-32, 55, 55, 270, 22), (55, 72, -35, 0, 32))
titel("Abwehrgeschütze, Himmelslaser, Minen")
z("server /difficulty normal", "server /villa ort 56 33 90 180 6",
  "server /execute as @p at @s run summon zombie ^ ^-6 ^9", "server /execute as @p at @s run summon zombie ^3 ^-6 ^11", "warte 5",
  "server /kill @e[type=zombie]", "server /difficulty peaceful")
titel("Raus durch den geheimen Notausgang")
z("server /villa luke", "warte 1")
fahrt(6, (-47, 30, 61, 270, 10), (-60, 45, 75, 250, 12), (-75, 60, 90, 240, 15))
z("server /villa stufe 0")

titel("Tiefgarage mit Drehteller")
z("server /gamemode creative", "server /villa ort 91.5 17 45.2 180 30", "warte 1",
  "server /execute at @p run summon stinkendreich:bonzenauto ~ ~ ~-4.7 {Rotation:[0f,0f],stinkendreich:\"{\\\"farbe\\\":4}\"}",
  "warte 1.5", "klick", "warte 6", "halte 0.4 shift", "server /gamemode spectator")
titel("James fährt dich – auf Straßen, die er selbst baut")
z("server /gamemode creative", "server /villa ort 55.5 26 86 0 15", "warte 1.5", "merke", "server Butler, hol den Wagen",
  "bis 40 vorgefahren", "warte 1", "server /ride @p mount @e[type=stinkendreich:stretchlimo,sort=nearest,limit=1]",
  "warte 1", "server Butler, fahr mich zum Dorf", "warte 9", "server /gamemode spectator")
titel("Straßen mit Brücken übers Meer")
z("server /butler strassenpunkt 3 700", "server /tp @p ~ ~7 ~ 0 32", "warte 4")
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
titel("Gästeliste: Freunde kommen ohne Karte rein")
z("server /execute as @p run handy gast|rein|Aircrafter", "server /execute as @p run handy gast|rein|Steve",
  "linksklick 640 566", "warte 3", "taste Escape", "warte 0.5", "klick", "warte 1.5")
titel("Bodyguards im Sakko, mit goldener Pistole")
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

titel("Inkognito: Die Villa fährt in die Erde")
z("server /villa ort 118 58 128 139 19", "warte 1", "server /villa inkognito an")
fahrt(11, (118, 58, 128, 139, 19), (90, 52, 140, 160, 18), (56, 46, 150, 180, 16))
titel("… und taucht wieder auf")
z("server /villa inkognito aus")
fahrt(11, (56, 46, 150, 180, 16), (90, 52, 140, 160, 18), (118, 58, 128, 139, 19))

titel("Neue Freunde bekommen ihre eigene Villa")
z("server /villa hin 1", "warte 1", "server /villa ort 55 75 200 180 35", "warte 2")
fahrt(10, (55, 75, 200, 180, 35), (55, 60, 160, 180, 30), (55, 48, 130, 180, 20))
titel("… mit Straße und ihrem Namen auf dem Nummernschild")
z("server /villa ort 55.5 26.3 99.5 180 12", "warte 1")
fahrt(5, (55.5, 26.3, 99.5, 180, 12), (55.5, 26.3, 98.4, 180, 14))
z("server /villa hin 0", "warte 1")

titel("Minecraft, aber du bist stinkend reich", tag=False)
z("server /time set 12650", "server /villa ort 56 52 142 180 22", "warte 2")
fahrt(10, (56, 52, 142, 180, 22), (90, 56, 140, 150, 19), (118, 58, 128, 120, 17))
titel("Jetzt auf GitHub – Fabric, Minecraft 1.21 bis 26.3", tag=False)
z("warte 4", "video stopp", "server /tick rate 20")

ZIEL.write_text("\n".join(zeilen) + "\n", encoding="utf-8")
print(f"{ZIEL}: {len(zeilen)} Zeilen")
