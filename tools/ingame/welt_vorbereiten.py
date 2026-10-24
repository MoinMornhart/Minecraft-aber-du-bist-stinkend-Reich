#!/usr/bin/env python3
"""Macht eine vom Server erzeugte Welt für den Einzelspieler-Test bereit: Cheats an, Mittag, kein Regen."""
import sys

import nbtlib
from nbtlib.tag import Byte, Int, Long

pfad = sys.argv[1]
datei = nbtlib.load(pfad)
daten = datei["Data"]
daten["allowCommands"] = Byte(1)
daten["GameType"] = Int(1)
daten["DayTime"] = Long(6000)
daten["raining"] = Byte(0)
daten["thundering"] = Byte(0)
daten["clearWeatherTime"] = Int(999999)
datei.save()
print(f"Welt vorbereitet: {pfad}")
