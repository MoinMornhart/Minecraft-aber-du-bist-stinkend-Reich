#!/usr/bin/env bash
# Test des Paper-Plugins im Spiel: ein echter Paper-Server mit unserem Plugin (paper/build/libs) erzeugt eine neue
# Welt, dazu ein ganz normales Minecraft ohne Mods (tools/ingame/vanilla_klient.py) auf einem virtuellen Bildschirm.
#
# Aufruf:   tools/ingame/paper.sh [szenario-datei]          (Minecraft 26.3)
# Vorher:   ./gradlew -p paper build
# Ausgabe:  build/ingame/paper/*.png und build/ingame/paper/logs/
# Umgebung: PORT (Spielserver, Standard 25575), SEED, SICHTWEITE, PAPER_BUILD (Paper-Build, Standard 226),
#           WELT_BEHALTEN=1 (Welt und Plugin-Daten vom letzten Lauf weiterbenutzen),
#           KEINE_VILLA=1 (Plugin baut keine Villa – so entsteht eine „schon bestehende“ Welt für Tests),
#           GEYSER=1 (Geyser-Plugin dazu und ein Test-Bedrock-Client „Bob“ aus tools/ingame/bedrock/bot.js; Protokoll
#           build/ingame/paper/logs/bedrock.log; vorher einmal „npm install --ignore-scripts“ in tools/ingame/bedrock)
#
# Szenario-Zeilen wie bei tools/ingame/test.sh: warte, taste, bild, klick, linksklick, maus, halte, druecke,
# loslassen, tippe, server </befehl oder Chattext>, merke [anfang], bis <sek> <muster> (im Serverprotokoll).
# Mit GEYSER=1 dazu: bedrock <befehl für bot.js> und bbis <sek> <muster> (im Protokoll des Bedrock-Clients).
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

if [[ "${WELT_BEHALTEN:-0}" != 1 ]]; then
  rm -rf "$RUN/world" "$RUN/world_nether" "$RUN/world_the_end" "$RUN/plugins/StinkendReich"
fi
rm -f "$RUN/stinkendreich-befehle.txt"
mkdir -p "$RUN/plugins/StinkendReich"
if [[ "${KEINE_VILLA:-0}" == 1 ]]; then
  printf 'villa:\n  amSpawn: false\n' > "$RUN/plugins/StinkendReich/config.yml"
elif grep -q "amSpawn: false" "$RUN/plugins/StinkendReich/config.yml" 2>/dev/null; then
  rm -f "$RUN/plugins/StinkendReich/config.yml"           # Einstellung aus einem KEINE_VILLA-Lauf zurücknehmen
fi
rm -f "$RUN/plugins/"stinkendreich-paper-*.jar "$RUN/plugins/"stinkendreich_paper-*.jar
cp "$PLUGIN" "$RUN/plugins/"

# Geyser (Bedrock-Spieler): neuester Stand für Paper von Modrinth, Anmeldung ohne Xbox (nur im Test!)
BPORT="${BPORT:-19132}"
rm -f "$RUN/plugins/Geyser-Spigot.jar"
if [[ "${GEYSER:-0}" == 1 ]]; then
  GEYSER_JAR="$(ls -t "$ROOT"/tools/ingame/server-mods/paper/Geyser-Spigot-*.jar 2>/dev/null | head -1)"
  if [[ -z "$GEYSER_JAR" ]]; then
    read -r GVER GURL < <(curl -fsSL "https://api.modrinth.com/v2/project/geyser/version?loaders=%5B%22paper%22%5D&game_versions=%5B%22$V%22%5D" \
      | python3 -c 'import json,sys; v=json.load(sys.stdin)[0]; print(v["version_number"], v["files"][0]["url"])')
    GEYSER_JAR="$ROOT/tools/ingame/server-mods/paper/Geyser-Spigot-$GVER.jar"
    melde "Lade Geyser $GVER …"
    curl -fsSL -o "$GEYSER_JAR" "$GURL" || { melde "FEHLER: Geyser nicht geladen"; exit 1; }
  fi
  cp "$GEYSER_JAR" "$RUN/plugins/Geyser-Spigot.jar"
  mkdir -p "$RUN/plugins/Geyser-Spigot"
  python3 - "$RUN/plugins/Geyser-Spigot/config.yml" "$BPORT" <<'PY'
import re, sys
datei, port = sys.argv[1], sys.argv[2]
try:
    text = open(datei, encoding="utf-8").read()
except FileNotFoundError:
    text = ""
def setze(text, abschnitt, schluessel, wert):
    """Setzt „schluessel: wert“ im Abschnitt (zwei Ebenen, z. B. java/auth-type); legt fehlende Teile an."""
    teile = abschnitt.split("/")
    muster = r"(?m)^" + r"".join(rf"{re.escape(t)}:\n(?:(?:  )*.*\n)*?{'  ' * (i + 1)}" for i, t in enumerate(teile)) + re.escape(schluessel) + r": .*$"
    if re.search(muster, text):
        return re.sub(muster, lambda m: m.group(0).rsplit(": ", 1)[0] + ": " + wert, text, count=1)
    block = "".join("  " * i + t + ":\n" for i, t in enumerate(teile)) + "  " * len(teile) + schluessel + ": " + wert + "\n"
    return text + ("\n" if text and not text.endswith("\n") else "") + block
text = setze(text, "bedrock", "port", port)
text = setze(text, "bedrock", "transport", "raknet")          # der Test-Client kann nur RakNet (Standard: NetherNet)
text = setze(text, "java", "auth-type", "offline")
text = setze(text, "advanced/bedrock", "validate-bedrock-login", "false")
open(datei, "w", encoding="utf-8").write(text)
PY
fi
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
white-list=false
enforce-whitelist=false
EOF
python3 - "$RUN/ops.json" <<'PY'
import hashlib, json, sys, uuid
h = bytearray(hashlib.md5(b"OfflinePlayer:Philip").digest()); h[6] = (h[6] & 0x0f) | 0x30; h[8] = (h[8] & 0x3f) | 0x80
json.dump([{"uuid": str(uuid.UUID(bytes=bytes(h))), "name": "Philip", "level": 4, "bypassesPlayerLimit": False}], open(sys.argv[1], "w"))
PY

# ---------- 2. Server starten ----------
melde "Paper-Server startet …"
( cd "$RUN" && STINKENDREICH_AUTOTEST=befehle exec /usr/lib/jvm/java-25-openjdk-amd64/bin/java -Xmx3G -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar "$PAPER" --nogui ) \
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
# Für Trailer: CHAT_AUS=1 blendet den Chat aus (Rückmeldungen der Testbefehle stören im Bild)
[[ "${CHAT_AUS:-0}" == 1 ]] && echo "chatVisibility:2" >> "$KLIENT/options.txt"
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
  kill "${CLIENT:-}" "$XVFB" ${BOT:-} 2>/dev/null
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
# Bedrock-Spieler „Bob“ über Geyser (Test-Client ohne Grafik, schreibt mit, was er bekommt)
BOT=""
if [[ "${GEYSER:-0}" == 1 ]]; then
  : > "$RUN/bedrock-befehle.txt"; : > "$LOGS/bedrock.log"
  node "$ROOT/tools/ingame/bedrock/bot.js" 127.0.0.1 "$BPORT" Bob "$LOGS/bedrock.log" "$RUN/bedrock-befehle.txt" \
    > "$LOGS/bedrock-konsole.log" 2>&1 </dev/null &
  BOT=$!
  melde "Bedrock-Client „Bob“ verbindet sich über Geyser (Port $BPORT) …"
fi
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
    merke) if [[ "$rest" == anfang ]]; then MARKE=0; else MARKE=$(wc -l < "$CHATLOG"); fi ;;   # „merke anfang“: ab Serverstart
    bis) set -f; set -- $rest; set +f; frist="$1"; shift; muster="$*"; ab="${MARKE:-$(wc -l < "$CHATLOG")}"; MARKE=""; ende=$((SECONDS + frist))
         until tail -n +"$((ab + 1))" "$CHATLOG" | grep -qaE "$muster"; do
           if (( SECONDS >= ende )); then melde "WARNUNG: „$muster“ kam nicht innerhalb von $frist s"; break; fi
           sleep 2
         done ;;
    druecke) xdotool keydown --window "$FENSTER" $rest ;;
    loslassen) xdotool keyup --window "$FENSTER" $rest; sleep 0.3 ;;
    halte) set -- $rest; dauer="$1"; shift; xdotool keydown --window "$FENSTER" "$@"; sleep "$dauer"; xdotool keyup --window "$FENSTER" "$@"; sleep 0.3 ;;
    video) video $rest ;;
    bedrock) printf '%s\n' "$rest" >> "$RUN/bedrock-befehle.txt"; sleep 1 ;;
    bbis) set -f; set -- $rest; set +f; frist="$1"; shift; muster="$*"; ende=$((SECONDS + frist))
          until grep -qaE "$muster" "$LOGS/bedrock.log"; do
            if (( SECONDS >= ende )); then melde "WARNUNG: „$muster“ kam beim Bedrock-Client nicht innerhalb von $frist s"; break; fi
            sleep 2
          done ;;
    kapitel) kapitel "$rest" ;;
    *) melde "Unbekannte Szenario-Zeile: $wort $rest" ;;
  esac
done < "$RUN/szenario.txt"
video stopp
melde "Fertig"
