#!/usr/bin/env bash
# Test des Paper-Plugins im Spiel: ein echter Paper-Server mit unserem Plugin (paper/build/libs) erzeugt eine neue
# Welt, dazu ein ganz normales Minecraft ohne Mods (tools/ingame/vanilla_klient.py) auf einem virtuellen Bildschirm.
#
# Aufruf:   tools/ingame/paper.sh [szenario-datei]          (Minecraft 26.3)
# Vorher:   ./gradlew -p paper build
# Ausgabe:  build/ingame/paper/*.png und build/ingame/paper/logs/
# Umgebung: PORT (Spielserver, Standard 25575), SEED, SICHTWEITE, PAPER_BUILD (Paper-Build, Standard 226)
#
# Szenario-Zeilen wie bei tools/ingame/test.sh: warte, taste, bild, klick, linksklick, maus, halte, druecke,
# loslassen, tippe, server </befehl oder Chattext>, merke, bis <sek> <muster> (im Serverprotokoll).
set -uo pipefail

V=26.3
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SZENARIO="${1:-$ROOT/tools/ingame/szenarien/paper_start.txt}"
RUN="$ROOT/build/paper-test"
AUS="$ROOT/build/ingame/paper"
LOGS="$AUS/logs"
PORT="${PORT:-25575}"
mkdir -p "$RUN" "$AUS" "$LOGS"
cd "$ROOT"
melde() { echo "[$(date +%H:%M:%S)] paper: $*"; }

# ---------- 1. Paper und Plugin bereitlegen ----------
BUILD="${PAPER_BUILD:-226}"
PAPER="$ROOT/tools/ingame/server-mods/paper/paper-$V-$BUILD.jar"
if [[ ! -f "$PAPER" ]]; then
  mkdir -p "$(dirname "$PAPER")"
  URL="$(curl -fsSL "https://fill.papermc.io/v3/projects/paper/versions/$V/builds/$BUILD" | python3 -c \
    'import json,sys; print(json.load(sys.stdin)["downloads"]["server:default"]["url"])')"
  melde "Lade Paper $V Build $BUILD …"
  curl -fsSL -o "$PAPER" "$URL" || { melde "FEHLER: Paper nicht geladen"; exit 1; }
fi
PLUGIN="$(ls -t "$ROOT"/paper/build/libs/stinkendreich_paper-*.jar 2>/dev/null | head -1)"
[[ -n "$PLUGIN" ]] || { melde "FEHLER: erst ./gradlew -p paper build"; exit 1; }

rm -rf "$RUN/world" "$RUN/world_nether" "$RUN/world_the_end" "$RUN/plugins/StinkendReich" "$RUN/stinkendreich-befehle.txt"
mkdir -p "$RUN/plugins"
rm -f "$RUN/plugins/"stinkendreich-paper-*.jar "$RUN/plugins/"stinkendreich_paper-*.jar
cp "$PLUGIN" "$RUN/plugins/"
echo "eula=true" > "$RUN/eula.txt"
cat > "$RUN/server.properties" <<EOF
level-seed=${SEED:-stinkendreich}
server-port=$PORT
online-mode=false
gamemode=creative
difficulty=peaceful
spawn-protection=0
view-distance=6
simulation-distance=4
enable-command-block=true
sync-chunk-writes=false
EOF
python3 - "$RUN/ops.json" <<'PY'
import hashlib, json, sys, uuid
h = bytearray(hashlib.md5(b"OfflinePlayer:Philip").digest()); h[6] = (h[6] & 0x0f) | 0x30; h[8] = (h[8] & 0x3f) | 0x80
json.dump([{"uuid": str(uuid.UUID(bytes=bytes(h))), "name": "Philip", "level": 4, "bypassesPlayerLimit": False}], open(sys.argv[1], "w"))
PY

# ---------- 2. Server starten ----------
melde "Paper-Server startet …"
( cd "$RUN" && STINKENDREICH_AUTOTEST=befehle exec /usr/lib/jvm/java-25-openjdk-amd64/bin/java -Xmx3G -jar "$PAPER" --nogui ) \
  > "$LOGS/server.log" 2>&1 </dev/null &
SERVER=$!
for _ in $(seq 1 600); do
  grep -q "Done (" "$LOGS/server.log" && break
  kill -0 "$SERVER" 2>/dev/null || { melde "FEHLER: Server ist abgestürzt"; tail -60 "$LOGS/server.log"; exit 1; }
  sleep 1
done
grep -q "Done (" "$LOGS/server.log" || { melde "FEHLER: Server nicht bereit"; tail -40 "$LOGS/server.log"; kill "$SERVER"; exit 1; }

# ---------- 3. Normales Minecraft auf einem virtuellen Bildschirm ----------
KLIENT="$RUN/klient"
mkdir -p "$KLIENT"
rm -rf "$KLIENT/screenshots"
cat > "$KLIENT/options.txt" <<EOF
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
python3 - "$KLIENT/servers.dat" "localhost:$PORT" <<'PY'
import struct, sys
def s(t): b = t.encode(); return struct.pack(">H", len(b)) + b
eintrag = b"\x08" + s("ip") + s(sys.argv[2]) + b"\x08" + s("name") + s("Test") + b"\x01" + s("acceptTextures") + b"\x01" + b"\x00"
open(sys.argv[1], "wb").write(b"\x0a" + s("") + b"\x09" + s("servers") + b"\x0a" + struct.pack(">i", 1) + eintrag + b"\x00")
PY
NR=99
while [[ -e /tmp/.X11-unix/X$NR ]]; do NR=$((NR - 1)); done
Xvfb ":$NR" -screen 0 1280x720x24 +extension GLX +render -nolisten tcp >/dev/null 2>&1 &
XVFB=$!
export DISPLAY=":$NR" LIBGL_ALWAYS_SOFTWARE=1 SDL_VIDEO_FORCE_EGL=1 XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/tmp/xdg-$UID}"
mkdir -p "$XDG_RUNTIME_DIR" && chmod 700 "$XDG_RUNTIME_DIR"
aufraeumen() {
  kill "${CLIENT:-}" "$XVFB" 2>/dev/null
  if kill -0 "$SERVER" 2>/dev/null; then
    kill "$SERVER" 2>/dev/null
    for _ in $(seq 1 60); do kill -0 "$SERVER" 2>/dev/null || break; sleep 0.5; done
  fi
}
trap aufraeumen EXIT
melde "Normales Minecraft startet …"
python3 "$ROOT/tools/ingame/vanilla_klient.py" "$V" "$KLIENT" "localhost:$PORT" Philip > "$LOGS/client.log" 2>&1 </dev/null &
CLIENT=$!
CHATLOG="$LOGS/server.log"
for _ in $(seq 1 900); do
  grep -qE "joined the game" "$CHATLOG" && break
  kill -0 "$CLIENT" 2>/dev/null || { melde "FEHLER: Client ist abgestürzt"; tail -60 "$LOGS/client.log"; exit 1; }
  sleep 1
done
grep -qE "joined the game" "$CHATLOG" || { melde "FEHLER: Server nicht betreten"; tail -40 "$LOGS/client.log"; exit 1; }
melde "Auf dem Server, warte auf die Darstellung …"
sleep "${RENDERZEIT:-25}"
FENSTER="$(xwininfo -root -tree 2>/dev/null | grep -E '"Minecraft [0-9]' | grep -v 'Hidden' | head -1 | awk '{print $1}')"
[[ -n "$FENSTER" ]] && FENSTER="$((FENSTER))"
if [[ -z "$FENSTER" ]]; then
  for w in $(xdotool search --onlyvisible --name ".*" 2>/dev/null); do
    breite="$(xdotool getwindowgeometry "$w" 2>/dev/null | sed -n 's/.*Geometry: \([0-9]*\)x.*/\1/p')"
    [[ "${breite:-0}" -ge 1000 ]] && FENSTER="$w"
  done
fi
melde "Fenster: $FENSTER"
xdotool windowactivate "$FENSTER" 2>/dev/null; xdotool windowraise "$FENSTER" 2>/dev/null; xdotool windowfocus "$FENSTER" 2>/dev/null
xdotool mousemove 640 360

# ---------- 4. Szenario ----------
bild() {
  local name="$1"
  if ffmpeg -y -loglevel error -f x11grab -video_size 1280x720 -i "$DISPLAY" -frames:v 1 "$AUS/$name.png" </dev/null; then
    melde "Bild: $AUS/$name.png"
  else
    melde "WARNUNG: kein Bild für $name"
  fi
}
# Video wie in test.sh: „video start <name>“ … „kapitel <text>“ … „video stopp“
VIDEO_PID=""
video() {
  case "$1" in
    start)
      VIDEO_NAME="$2"; : > "$AUS/${VIDEO_NAME}_kapitel.txt"
      ffmpeg -y -loglevel error -f x11grab -framerate "${VIDEO_FPS:-25}" -video_size 1280x720 -draw_mouse 0 -i "$DISPLAY" \
        -c:v libx264 -preset ultrafast -crf 18 -pix_fmt yuv420p "$AUS/${VIDEO_NAME}_roh.mp4" </dev/null &
      VIDEO_PID=$!; VIDEO_START=$(date +%s.%N); melde "Video läuft: $AUS/${VIDEO_NAME}_roh.mp4" ;;
    stopp)
      if [[ -n "$VIDEO_PID" ]]; then kill -INT "$VIDEO_PID" 2>/dev/null; wait "$VIDEO_PID" 2>/dev/null; VIDEO_PID=""; melde "Video gespeichert"; fi ;;
  esac
}
kapitel() {
  [[ -n "$VIDEO_PID" ]] && printf '%.1f|%s\n' "$(echo "$(date +%s.%N) - $VIDEO_START" | bc)" "$1" >> "$AUS/${VIDEO_NAME}_kapitel.txt"
}
cp "$SZENARIO" "$RUN/szenario.txt"
while read -r wort rest || [[ -n "$wort" ]]; do
  case "$wort" in
    ""|\#*) ;;
    warte) sleep "$rest" ;;
    taste) xdotool key --window "$FENSTER" $rest; sleep 0.5 ;;
    tippe) xdotool type --delay 40 "$rest"; sleep 0.5 ;;
    bild) bild "$rest" ;;
    klick) xdotool click 3; sleep 1 ;;
    schnellklick) xdotool click 3 ;;
    linksklick) set -- $rest; xdotool mousemove "$1" "$2"; sleep 0.3; xdotool click 1; sleep 1 ;;
    maus) set -- $rest; xdotool mousemove "$1" "$2"; sleep 0.3 ;;
    server) printf '%s\n' "$rest" >> "$RUN/stinkendreich-befehle.txt"; sleep 1 ;;
    merke) MARKE=$(wc -l < "$CHATLOG") ;;
    bis) set -- $rest; frist="$1"; shift; muster="$*"; ab="${MARKE:-$(wc -l < "$CHATLOG")}"; MARKE=""; ende=$((SECONDS + frist))
         until tail -n +"$((ab + 1))" "$CHATLOG" | grep -qaE "$muster"; do
           if (( SECONDS >= ende )); then melde "WARNUNG: „$muster“ kam nicht innerhalb von $frist s"; break; fi
           sleep 2
         done ;;
    druecke) xdotool keydown --window "$FENSTER" $rest ;;
    loslassen) xdotool keyup --window "$FENSTER" $rest; sleep 0.3 ;;
    halte) set -- $rest; dauer="$1"; shift; xdotool keydown --window "$FENSTER" "$@"; sleep "$dauer"; xdotool keyup --window "$FENSTER" "$@"; sleep 0.3 ;;
    video) video $rest ;;
    kapitel) kapitel "$rest" ;;
    *) melde "Unbekannte Szenario-Zeile: $wort $rest" ;;
  esac
done < "$RUN/szenario.txt"
video stopp
melde "Fertig"
