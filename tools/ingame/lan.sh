#!/usr/bin/env bash
# Test wie NoRisk „Hosted Worlds“ oder „Im LAN öffnen“: Philip spielt eine Einzelspielerwelt mit der Mod und öffnet
# sie, ein Freund („Max“) tritt bei. Beide Spiele laufen auf eigenen virtuellen Bildschirmen; Bilder zeigen den Freund.
#
# Aufruf:   GAST=ohne|mit tools/ingame/lan.sh <minecraft-version> <szenario>
#           GAST=ohne: der Freund hat ein ganz normales Minecraft (Philip hat dann Polymer mit in seinem Profil)
#           GAST=mit:  der Freund hat die Mod auch (wie beim Teilen des NoRisk-Profils)
# Ausgabe:  build/ingame/<version>/lan_<gast>/*.png und …/logs/
# Szenario: wie test.sh (warte, server, bis, merke, bild, taste, klick, halte, video, kapitel); „taste“, „klick“, „halte“,
#           „bild“ und „video“
#           gelten für den Freund, „server“-Zeilen laufen als Philip (für den Freund: „server /execute as Max …“).
set -uo pipefail

V="${1:?Minecraft-Version angeben, z. B. 26.3}"
SZENARIO="${2:?Szenario angeben}"
GAST="${GAST:-mit}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
RUN="$ROOT/versions/$V/run"
AUS="$ROOT/build/ingame/$V/lan_$GAST"
LOGS="$AUS/logs"
PORT="${PORT:-25572}"
mkdir -p "$RUN" "$AUS" "$LOGS"
cd "$ROOT"

melde() { echo "[$(date +%H:%M:%S)] $V/lan: $*"; }

# ---------- 1. Welt: wie test.sh vom Server erzeugen lassen (oder die vorhandene nehmen) ----------
if [[ "${WELT_BEHALTEN:-1}" != 1 || ! -f "$RUN/testwelt/level.dat" ]]; then
  rm -rf "$RUN/testwelt"
  echo "eula=true" > "$RUN/eula.txt"
  printf 'level-name=testwelt\nlevel-seed=%s\nonline-mode=false\ngamemode=creative\ndifficulty=peaceful\nspawn-protection=0\nview-distance=6\nsimulation-distance=4\nsync-chunk-writes=false\n' \
    "${SEED:-stinkendreich}" > "$RUN/server.properties"
  melde "Server erzeugt die Welt …"
  STINKENDREICH_AUTOTEST=server-stop timeout 900 ./gradlew ":$V:runServer" --console=plain > "$LOGS/server.log" 2>&1 </dev/null
  [[ -f "$RUN/testwelt/level.dat" ]] || { melde "FEHLER: keine Welt"; tail -40 "$LOGS/server.log"; exit 1; }
fi
rm -rf "$RUN/saves/testwelt" "$RUN/screenshots"; mkdir -p "$RUN/saves"
cp -r "$RUN/testwelt" "$RUN/saves/testwelt"
python3 "$ROOT/tools/ingame/welt_vorbereiten.py" "$RUN/saves/testwelt/level.dat"

optionen() {
  mkdir -p "$1"
  cat > "$1/options.txt" <<EOF
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
  # Serverliste mit „Ressourcenpaket annehmen“ (servers.dat ist unkomprimiertes NBT)
  python3 - "$1/servers.dat" "localhost:$PORT" <<'PY'
import struct, sys
def s(t): b = t.encode(); return struct.pack(">H", len(b)) + b
eintrag = b"\x08" + s("ip") + s(sys.argv[2]) + b"\x08" + s("name") + s("Philips Welt") + b"\x01" + s("acceptTextures") + b"\x01" + b"\x00"
open(sys.argv[1], "wb").write(b"\x0a" + s("") + b"\x09" + s("servers") + b"\x0a" + struct.pack(">i", 1) + eintrag + b"\x00")
PY
}
optionen "$RUN"
GAST_ORDNER="$RUN/gast_$GAST"
rm -rf "$GAST_ORDNER/screenshots"
optionen "$GAST_ORDNER"
# Für Trailer: CHAT_AUS=1 blendet bei Max den Chat aus
[[ "${CHAT_AUS:-0}" == 1 ]] && echo "chatVisibility:2" >> "$GAST_ORDNER/options.txt"

# Philips Profil: bei Freunden ohne Mod gehört Polymer dazu
rm -rf "$RUN/mods"; mkdir -p "$RUN/mods"
[[ "$GAST" == ohne || "${MITPOLYMER:-0}" == 1 ]] && cp "$ROOT/tools/ingame/server-mods/$V/"*.jar "$RUN/mods/" 2>/dev/null

# ---------- 2. Zwei virtuelle Bildschirme ----------
bildschirm() {
  local nr=99
  while [[ -e /tmp/.X11-unix/X$nr ]]; do nr=$((nr - 1)); done
  Xvfb ":$nr" -screen 0 1280x720x24 +extension GLX +render -nolisten tcp >/dev/null 2>&1 &
  echo "$nr $!"
}
read -r NR_HOST XVFB_HOST < <(bildschirm); sleep 1
read -r NR_GAST XVFB_GAST < <(bildschirm); sleep 1
export LIBGL_ALWAYS_SOFTWARE=1 SDL_VIDEO_FORCE_EGL=1 XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/tmp/xdg-$UID}"
mkdir -p "$XDG_RUNTIME_DIR" && chmod 700 "$XDG_RUNTIME_DIR"

HOST=""; GAST_PID=""
aufraeumen() {
  local p
  for p in $(pgrep -f "net.fabricmc.devlaunchinjector.Main"); do
    case "$(readlink "/proc/$p/cwd" 2>/dev/null)" in "$RUN"|"$GAST_ORDNER") kill "$p" 2>/dev/null ;; esac
  done
  kill ${HOST:-} ${GAST_PID:-} "$XVFB_HOST" "$XVFB_GAST" 2>/dev/null
  for _ in $(seq 1 60); do
    pgrep -f "[v]anilla_klient.py.*$GAST_ORDNER" >/dev/null || pgrep -f "[d]evlaunchinjector" >/dev/null || break
    sleep 0.5
  done
  rm -rf "$RUN/mods"
}
trap aufraeumen EXIT

# ---------- 3. Philip öffnet seine Welt ----------
rm -f "$RUN/stinkendreich-befehle.txt"
melde "Philip startet seine Einzelspielerwelt (mit Mod$([[ -n "$(ls "$RUN/mods" 2>/dev/null)" ]] && echo " und Polymer")) …"
DISPLAY=":$NR_HOST" STINKENDREICH_AUTOTEST=befehle ./gradlew ":$V:runClient" --console=plain \
  --args="--quickPlaySingleplayer testwelt --width 1280 --height 720 --username Philip" \
  > "$LOGS/host.log" 2>&1 </dev/null &
HOST=$!
for _ in $(seq 1 900); do
  grep -qE "joined the game|hat das Spiel betreten" "$LOGS/host.log" && break
  kill -0 "$HOST" 2>/dev/null || { melde "FEHLER: Philips Spiel ist abgestürzt"; tail -60 "$LOGS/host.log"; exit 1; }
  sleep 1
done
sleep 10
printf 'offline\n/publish true %s\n' "$PORT" >> "$RUN/stinkendreich-befehle.txt"
for _ in $(seq 1 60); do
  grep -qaE "Port $PORT|port $PORT" "$LOGS/host.log" && break
  sleep 1
done
melde "Welt geöffnet: $(grep -aE "Port $PORT|port $PORT" "$LOGS/host.log" | tail -1 | sed 's/.*]: //')"

# ---------- 4. Max tritt bei ----------
if [[ "$GAST" == ohne ]]; then
  melde "Max (normales Minecraft) tritt bei …"
  DISPLAY=":$NR_GAST" python3 "$ROOT/tools/ingame/vanilla_klient.py" "$V" "$GAST_ORDNER" "localhost:$PORT" Max \
    > "$LOGS/gast.log" 2>&1 </dev/null &
  GAST_PID=$!
else
  # Dasselbe Entwicklungs-Minecraft wie Philips (mit der Mod), nur mit eigenem Ordner und Namen
  melde "Max (mit Mod) tritt bei …"
  JAVA_PID=""
  for p in $(pgrep -f "net.fabricmc.devlaunchinjector.Main"); do
    [[ "$(readlink "/proc/$p/cwd" 2>/dev/null)" == "$RUN" ]] && JAVA_PID="$p"
  done
  [[ -n "$JAVA_PID" ]] || { melde "FEHLER: Philips Java-Prozess nicht gefunden"; exit 1; }
  mapfile -d '' ARGS < "/proc/$JAVA_PID/cmdline"
  NEU=()
  for ((i = 0; i < ${#ARGS[@]}; i++)); do
    case "${ARGS[$i]}" in
      --quickPlaySingleplayer) NEU+=("--quickPlayMultiplayer" "localhost:$PORT"); i=$((i + 1)) ;;
      --username) NEU+=("--username" "Max"); i=$((i + 1)) ;;
      *) NEU+=("${ARGS[$i]}") ;;
    esac
  done
  (cd "$GAST_ORDNER" && DISPLAY=":$NR_GAST" exec "${NEU[@]}") > "$LOGS/gast.log" 2>&1 </dev/null &
  GAST_PID=$!
fi
for _ in $(seq 1 600); do
  grep -qE "Max joined the game|Max hat das Spiel betreten" "$LOGS/host.log" && break
  kill -0 "$GAST_PID" 2>/dev/null || { melde "FEHLER: Max' Spiel ist abgestürzt"; tail -60 "$LOGS/gast.log"; exit 1; }
  sleep 1
done
grep -qE "Max joined the game|Max hat das Spiel betreten" "$LOGS/host.log" || { melde "FEHLER: Max kam nicht in die Welt"; tail -40 "$LOGS/gast.log"; exit 1; }
melde "Max ist in der Welt, warte auf die Darstellung …"
sleep "${RENDERZEIT:-25}"

export DISPLAY=":$NR_GAST"
FENSTER="$(xwininfo -root -tree 2>/dev/null | grep -E '"Minecraft [0-9]' | grep -v 'Hidden' | head -1 | awk '{print $1}')"
[[ -n "$FENSTER" ]] && FENSTER="$((FENSTER))"
[[ -n "$FENSTER" ]] || FENSTER="$(xdotool search --name "Minecraft" | head -1)"
melde "Fenster von Max: $FENSTER ($(xdotool getwindowname "$FENSTER" 2>/dev/null))"
xdotool windowactivate "$FENSTER" 2>/dev/null; xdotool windowfocus "$FENSTER" 2>/dev/null
xdotool mousemove 640 360

# ---------- 5. Szenario ----------
bild() {
  if ffmpeg -y -loglevel error -f x11grab -video_size 1280x720 -i ":$NR_GAST" -frames:v 1 "$AUS/$1.png" </dev/null; then
    melde "Bild: $AUS/$1.png"
  else
    melde "WARNUNG: kein Bild für $1"
  fi
}
# Videoaufnahme von Max' Bildschirm (wie test.sh: „video start <name>“, „video stopp“, „kapitel <text>“)
VIDEO_PID=""
video() {
  case "$1" in
    start)
      VIDEO_NAME="$2"; : > "$AUS/${VIDEO_NAME}_kapitel.txt"
      ffmpeg -y -loglevel error -f x11grab -framerate "${VIDEO_FPS:-25}" -video_size 1280x720 -draw_mouse 0 -i ":$NR_GAST" \
        -c:v libx264 -preset ultrafast -crf 18 -pix_fmt yuv420p "$AUS/${VIDEO_NAME}_roh.mp4" </dev/null &
      VIDEO_PID=$!; VIDEO_START=$(date +%s.%N); melde "Video läuft: $AUS/${VIDEO_NAME}_roh.mp4" ;;
    stopp)
      if [[ -n "$VIDEO_PID" ]]; then kill -INT "$VIDEO_PID" 2>/dev/null; wait "$VIDEO_PID" 2>/dev/null; VIDEO_PID=""; melde "Video gespeichert"; fi ;;
  esac
}
kapitel() {
  [[ -n "$VIDEO_PID" ]] && printf '%.1f|%s\n' "$(echo "$(date +%s.%N) - $VIDEO_START" | bc)" "$1" >> "$AUS/${VIDEO_NAME}_kapitel.txt"
}
CHATLOG="$LOGS/host.log"
cp "$SZENARIO" "$AUS/szenario.txt"
while read -r wort rest || [[ -n "$wort" ]]; do
  case "$wort" in
    ""|\#*) ;;
    warte) sleep "$rest" ;;
    taste) xdotool key --window "$FENSTER" $rest; sleep 0.5 ;;
    klick) xdotool click 3; sleep 1 ;;
    halte) set -- $rest; dauer="$1"; shift; xdotool keydown --window "$FENSTER" "$@"; sleep "$dauer"; xdotool keyup --window "$FENSTER" "$@"; sleep 0.3 ;;
    bild) bild "$rest" ;;
    server) printf '%s\n' "$rest" >> "$RUN/stinkendreich-befehle.txt"; sleep 1 ;;
    merke) MARKE=$(wc -l < "$CHATLOG") ;;
    bis) set -f; set -- $rest; set +f; frist="$1"; shift; muster="$*"; ab="${MARKE:-$(wc -l < "$CHATLOG")}"; MARKE=""; ende=$((SECONDS + frist))
         until tail -n +"$((ab + 1))" "$CHATLOG" | grep -qaE "$muster"; do
           if (( SECONDS >= ende )); then melde "WARNUNG: „$muster“ kam nicht innerhalb von $frist s"; break; fi
           sleep 2
         done ;;
    video) video $rest ;;
    kapitel) kapitel "$rest" ;;
    *) melde "Unbekannte Szenario-Zeile: $wort $rest" ;;
  esac
done < "$AUS/szenario.txt"
video stopp
melde "Fertig"
