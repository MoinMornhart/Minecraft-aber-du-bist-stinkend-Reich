#!/usr/bin/env bash
# Test im Spiel ohne Grafikkarte: Xvfb (virtueller Bildschirm) und Mesa (Software-OpenGL).
#  1. Ein Server erzeugt mit der Mod eine neue Welt und stoppt sich danach selbst.
#  2. Der Client lädt diese Welt. Ein Szenario drückt per xdotool Tasten und macht Screenshots.
#
# Aufruf:   tools/ingame/test.sh <minecraft-version> [szenario-datei]
# Ausgabe:  build/ingame/<version>/*.png und build/ingame/<version>/logs/
#
# Szenario-Zeilen:  warte <sek> | taste <xdotool-taste> | befehl </befehl …> | bild <name>
set -uo pipefail

V="${1:?Minecraft-Version angeben, z. B. 26.3}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SZENARIO="${2:-$ROOT/tools/ingame/szenarien/start.txt}"
RUN="$ROOT/versions/$V/run"
AUS="$ROOT/build/ingame/$V"
LOGS="$AUS/logs"
mkdir -p "$RUN" "$AUS" "$LOGS"
cd "$ROOT"

melde() { echo "[$(date +%H:%M:%S)] $V: $*"; }

# ---------- 1. Neue Welt mit dem Server erzeugen ----------
rm -rf "$RUN/testwelt"
echo "eula=true" > "$RUN/eula.txt"
cat > "$RUN/server.properties" <<EOF
level-name=testwelt
level-seed=${SEED:-stinkendreich}
online-mode=false
gamemode=creative
difficulty=peaceful
spawn-protection=0
view-distance=6
simulation-distance=4
enable-command-block=true
sync-chunk-writes=false
EOF
melde "Server erzeugt die Welt …"
STINKENDREICH_AUTOTEST=server-stop timeout 900 ./gradlew ":$V:runServer" --console=plain > "$LOGS/server.log" 2>&1 </dev/null
if [[ ! -f "$RUN/testwelt/level.dat" ]]; then
  melde "FEHLER: Server hat keine Welt erzeugt"; tail -40 "$LOGS/server.log"; exit 1
fi

# ---------- 2. Welt als Einzelspieler-Spielstand vorbereiten ----------
rm -rf "$RUN/saves/testwelt" "$RUN/screenshots"
mkdir -p "$RUN/saves"
cp -r "$RUN/testwelt" "$RUN/saves/testwelt"
python3 "$ROOT/tools/ingame/welt_vorbereiten.py" "$RUN/saves/testwelt/level.dat"
cat > "$RUN/options.txt" <<EOF
onboardAccessibility:false
pauseOnLostFocus:false
renderDistance:${SICHTWEITE:-8}
simulationDistance:5
guiScale:2
soundCategory_master:0.0
tutorialStep:none
skipMultiplayerWarning:true
joinedFirstServer:true
narrator:0
lang:de_de
EOF

# ---------- 3. Client auf einem virtuellen Bildschirm starten ----------
NR=99
while [[ -e /tmp/.X11-unix/X$NR ]]; do NR=$((NR - 1)); done
Xvfb ":$NR" -screen 0 1280x720x24 -nolisten tcp >/dev/null 2>&1 &
XVFB=$!
export DISPLAY=":$NR" LIBGL_ALWAYS_SOFTWARE=1

aufraeumen() {
  pkill -f "net.fabricmc.devlaunchinjector.Main" 2>/dev/null
  kill "$CLIENT" "$XVFB" 2>/dev/null
}
trap aufraeumen EXIT

melde "Client startet …"
./gradlew ":$V:runClient" --console=plain \
  --args="--quickPlaySingleplayer testwelt --width 1280 --height 720 --username Philip" \
  > "$LOGS/client.log" 2>&1 </dev/null &
CLIENT=$!

for _ in $(seq 1 900); do
  grep -q "joined the game" "$LOGS/client.log" && break
  if ! kill -0 "$CLIENT" 2>/dev/null; then
    melde "FEHLER: Client ist abgestürzt"; tail -60 "$LOGS/client.log"; exit 1
  fi
  sleep 1
done
grep -q "joined the game" "$LOGS/client.log" || { melde "FEHLER: Welt nicht betreten"; tail -60 "$LOGS/client.log"; exit 1; }
melde "In der Welt, warte auf die Darstellung …"
sleep "${RENDERZEIT:-25}"

FENSTER="$(xdotool search --name "Minecraft" | head -1)"
xdotool windowfocus "$FENSTER" 2>/dev/null
xdotool mousemove 640 360

# ---------- 4. Szenario abarbeiten ----------
bild() {
  local name="$1" vorher
  vorher="$(ls "$RUN/screenshots" 2>/dev/null | wc -l)"
  xdotool key F2
  for _ in $(seq 1 60); do
    [[ "$(ls "$RUN/screenshots" 2>/dev/null | wc -l)" -gt "$vorher" ]] && break
    sleep 0.5
  done
  sleep 1
  local neu
  neu="$(ls -t "$RUN/screenshots"/*.png 2>/dev/null | head -1)"
  if [[ -n "$neu" ]]; then
    mv "$neu" "$AUS/$name.png"; melde "Bild: $AUS/$name.png"
  else
    melde "WARNUNG: kein Screenshot für $name"
  fi
}

while read -r wort rest || [[ -n "$wort" ]]; do
  case "$wort" in
    ""|\#*) ;;
    warte) sleep "$rest" ;;
    taste) xdotool key $rest; sleep 0.5 ;;
    befehl) xdotool key t; sleep 1; xdotool type --delay 30 "$rest"; sleep 0.4; xdotool key Return; sleep 1.5 ;;
    bild) bild "$rest" ;;
    *) melde "Unbekannte Szenario-Zeile: $wort $rest" ;;
  esac
done < "$SZENARIO"

melde "Fertig"
