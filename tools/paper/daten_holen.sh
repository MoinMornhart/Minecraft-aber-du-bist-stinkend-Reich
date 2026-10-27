#!/usr/bin/env bash
# Holt aus der Mod (Minecraft 26.3) alles, was das Paper-Plugin zusätzlich zu den Bildern und Modellen der Mod braucht:
#  1. paper/src/main/resources/bloecke.json – Kollision, Umriss, Leuchtkraft und Klasse aller Mod-Blöcke
#     (geschrieben von dev/PaperDaten beim Serverstart),
#  2. paper/src/main/resources/paket/ – die Item-Modelle der Fahrzeuge, der Aufzugskabine, des Drehtellers und des
#     Fernsehers, die die Server-Version (FahrzeugAnzeige) aus den Fahrzeugdaten berechnet.
# Dafür startet ein Fabric-Server mit der Mod und Polymer (tools/ingame/server_mods_holen.sh 26.3) und stoppt sich selbst.
# Aufruf: tools/paper/daten_holen.sh   (nach Änderungen an Blöcken oder Fahrzeugen der Mod)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
RUN="$ROOT/versions/26.3/run"
cd "$ROOT"
[[ -n "$(ls tools/ingame/server-mods/26.3/*.jar 2>/dev/null)" ]] || tools/ingame/server_mods_holen.sh 26.3
mkdir -p "$RUN/mods"
cp tools/ingame/server-mods/26.3/*.jar "$RUN/mods/"
rm -f "$RUN/polymer/resource_pack.zip"
STINKENDREICH_AUTOTEST=paperdaten,server-stop STINKENDREICH_PAPERDATEN="$ROOT/paper/src/main/resources/bloecke.json" \
  timeout 900 ./gradlew :26.3:runServer --console=plain > "$ROOT/build/paperdaten.log" 2>&1 </dev/null || true
rm -rf "$RUN/mods"
grep -a "Paper-Daten" "$ROOT/build/paperdaten.log" || { echo "FEHLER: keine Paper-Daten, siehe build/paperdaten.log"; exit 1; }
ZIP="$RUN/polymer/resource_pack.zip"
[[ -f "$ZIP" ]] || { echo "FEHLER: Polymer hat kein Ressourcenpaket gebaut"; exit 1; }
ZIEL="$ROOT/paper/src/main/resources/paket"
rm -rf "$ZIEL"
mkdir -p "$ZIEL"
# Nur das von der Mod Berechnete (Polymer selbst und die Blockmodelle braucht das Plugin nicht)
unzip -q -o "$ZIP" 'assets/stinkendreich/models/item/polymer/*' 'assets/stinkendreich/textures/item/polymer/*' \
  'assets/stinkendreich/items/polymer/fahrzeug/*' 'assets/stinkendreich/items/polymer/*.json' -d "$ZIEL"
echo "Paper-Daten aktualisiert: $(find "$ZIEL" -type f | wc -l) Dateien in paper/src/main/resources/paket"
