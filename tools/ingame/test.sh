#!/usr/bin/env bash
# Test im Spiel ohne Grafikkarte: Xvfb (virtueller Bildschirm) und Mesa (Software-OpenGL).
#  1. Ein Server erzeugt mit der Mod eine neue Welt und stoppt sich danach selbst.
#  2. Der Client lädt diese Welt. Ein Szenario drückt per xdotool Tasten und macht Screenshots.
#
# Aufruf:   tools/ingame/test.sh <minecraft-version> [szenario-datei]
# Ausgabe:  build/ingame/<version>/*.png und build/ingame/<version>/logs/
# Umgebung: PORT, SEED, SICHTWEITE, WELT_BEHALTEN=1, LAUTSTAERKE (Gesamtlautstärke 0.0–1.0, sonst stumm)
#
# Szenario-Zeilen:  warte <sek> | taste <xdotool-taste> | befehl </befehl …> | bild <name>
#                   klick (Rechtsklick) | linksklick <x> <y> | maus <x> <y>   (Bildschirmpixel bei 1280x720)
#                   halte <sek> <taste…>   (z. B. „halte 3 w space“)
#                   druecke <taste…> | loslassen <taste…>   (Tasten festhalten, z. B. für ein Bild mitten im Drift)
#                   server </befehl oder Chattext>   (über eine Datei, ohne Tippen)
#                   klient schliessen|butlermenue      (Bildschirm schließen / Butler-Menü öffnen)
#                   merke                (merkt sich die Stelle im Client-Log für das nächste „bis“)
#                   bis <sek> <muster>   (wartet, bis eine Zeile im Client-Log nach „merke“ – sonst ab jetzt –
#                                         zum Muster passt, höchstens <sek>)
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
if [[ "${WELT_BEHALTEN:-0}" != 1 || ! -f "$RUN/testwelt/level.dat" ]]; then
rm -rf "$RUN/testwelt"
echo "eula=true" > "$RUN/eula.txt"
cat > "$RUN/server.properties" <<EOF
level-name=testwelt
level-seed=${SEED:-stinkendreich}
server-port=${PORT:-25565}
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
soundCategory_master:${LAUTSTAERKE:-0.0}
soundCategory_music:0.0
soundCategory_ambient:0.0
soundCategory_weather:0.0
tutorialStep:none
skipMultiplayerWarning:true
joinedFirstServer:true
narrator:0
lang:de_de
EOF

# ---------- 3. Client auf einem virtuellen Bildschirm starten ----------
NR=99
while [[ -e /tmp/.X11-unix/X$NR ]]; do NR=$((NR - 1)); done
Xvfb ":$NR" -screen 0 1280x720x24 +extension GLX +render -nolisten tcp >/dev/null 2>&1 &
XVFB=$!
export DISPLAY=":$NR" LIBGL_ALWAYS_SOFTWARE=1
# Ab 26.x öffnet Minecraft das Fenster über SDL3; mit EGL statt GLX klappt Software-OpenGL
export SDL_VIDEO_FORCE_EGL=1 XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/tmp/xdg-$UID}"
mkdir -p "$XDG_RUNTIME_DIR" && chmod 700 "$XDG_RUNTIME_DIR"

# Nur die eigenen Minecraft-Prozesse beenden (erkennbar am Arbeitsordner), falls mehrere Tests laufen
eigene_beenden() {
  local p
  for p in $(pgrep -f "net.fabricmc.devlaunchinjector.Main"); do
    [[ "$(readlink "/proc/$p/cwd" 2>/dev/null)" == "$RUN" ]] && kill "$p" 2>/dev/null
  done
}
aufraeumen() {
  eigene_beenden
  kill "$CLIENT" "$XVFB" 2>/dev/null
}
trap aufraeumen EXIT

melde "Client startet …"
rm -f "$RUN/stinkendreich-befehle.txt" "$RUN/stinkendreich-klient.txt"
STINKENDREICH_AUTOTEST=befehle ./gradlew ":$V:runClient" --console=plain \
  --args="--quickPlaySingleplayer testwelt --width 1280 --height 720 --username Philip" \
  > "$LOGS/client.log" 2>&1 </dev/null &
CLIENT=$!

for _ in $(seq 1 900); do
  grep -qE "joined the game|hat das Spiel betreten" "$LOGS/client.log" && break
  if ! kill -0 "$CLIENT" 2>/dev/null; then
    melde "FEHLER: Client ist abgestürzt"; tail -60 "$LOGS/client.log"; exit 1
  fi
  sleep 1
done
grep -qE "joined the game|hat das Spiel betreten" "$LOGS/client.log" || { melde "FEHLER: Welt nicht betreten"; tail -60 "$LOGS/client.log"; exit 1; }
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

# Kopie lesen: so darf das Szenario während eines Laufs bearbeitet werden
cp "$SZENARIO" "$RUN/szenario.txt"
while read -r wort rest || [[ -n "$wort" ]]; do
  case "$wort" in
    ""|\#*) ;;
    warte) sleep "$rest" ;;
    taste) xdotool key $rest; sleep 0.5 ;;
    befehl) xdotool key t; sleep "${TIPP_PAUSE:-1}"; xdotool type --delay 30 "$rest"; sleep 0.4; xdotool key Return; sleep 1.5 ;;
    bild) bild "$rest" ;;
    klick) xdotool click 3; sleep 1 ;;                                   # Rechtsklick (Block benutzen)
    linksklick) set -- $rest; xdotool mousemove "$1" "$2"; sleep 0.3; xdotool click 1; sleep 1 ;;
    maus) set -- $rest; xdotool mousemove "$1" "$2"; sleep 0.3 ;;
    server) printf '%s\n' "$rest" >> "$RUN/stinkendreich-befehle.txt"; sleep 1 ;;   # Befehl ohne Tippen (Autotest-Modus)
    klient) printf '%s\n' "$rest" >> "$RUN/stinkendreich-klient.txt"; sleep 1 ;;   # schliessen | butlermenue
    merke) MARKE=$(wc -l < "$LOGS/client.log") ;;
    bis) set -- $rest; frist="$1"; shift; muster="$*"; ab="${MARKE:-$(wc -l < "$LOGS/client.log")}"; MARKE=""; ende=$((SECONDS + frist))
         until tail -n +"$((ab + 1))" "$LOGS/client.log" | grep -qaE "$muster"; do
           if (( SECONDS >= ende )); then melde "WARNUNG: „$muster“ kam nicht innerhalb von $frist s"; break; fi
           sleep 2
         done ;;
    druecke) xdotool keydown $rest ;;
    loslassen) xdotool keyup $rest; sleep 0.3 ;;
    halte) set -- $rest; dauer="$1"; shift; xdotool keydown "$@"; sleep "$dauer"; xdotool keyup "$@"; sleep 0.3 ;;   # Tasten gedrückt halten
    *) melde "Unbekannte Szenario-Zeile: $wort $rest" ;;
  esac
done < "$RUN/szenario.txt"

melde "Fertig"
