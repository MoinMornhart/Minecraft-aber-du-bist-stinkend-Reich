#!/usr/bin/env bash
# Test im Spiel ohne Grafikkarte: Xvfb (virtueller Bildschirm) und Mesa (Software-OpenGL).
#  1. Ein Server erzeugt mit der Mod eine neue Welt und stoppt sich danach selbst.
#  2. Der Client lädt diese Welt. Ein Szenario drückt per xdotool Tasten und macht Screenshots.
#
# Aufruf:   tools/ingame/test.sh <minecraft-version> [szenario-datei]
# Ausgabe:  build/ingame/<version>/*.png und build/ingame/<version>/logs/
# Umgebung: PORT, SEED, SICHTWEITE, WELT_BEHALTEN=1, LAUTSTAERKE (Gesamtlautstärke 0.0–1.0, sonst stumm)
#           VANILLA=1: Server-Version prüfen – ein Server mit der Mod (und Polymer aus tools/ingame/server-mods/),
#                      dazu ein ganz normales Minecraft ohne Mods als Spieler (tools/ingame/vanilla_klient.py)
#
# Szenario-Zeilen:  warte <sek> | taste <xdotool-taste> | befehl </befehl …> | bild <name>
#                   klick (Rechtsklick) | linksklick <x> <y> | maus <x> <y>   (Bildschirmpixel bei 1280x720)
#                   klickbild <name>     (Rechtsklick und gleich danach ein Bild, z. B. Schuss mit Strahl)
#                   video start <name> | video stopp   (Bildschirm als Video aufnehmen: <name>_roh.mp4)
#                   kapitel <text>       (Kapitelmarke im laufenden Video, für tools/ingame/video.py)
#                   server fahrt <sek> | x y z gier neigung | …   (flüssige Kamerafahrt, siehe Autotest)
#                   halte <sek> <taste…>   (z. B. „halte 3 w space“)
#                   druecke <taste…> | loslassen <taste…>   (Tasten festhalten, z. B. für ein Bild mitten im Drift)
#                   server </befehl oder Chattext>   (über eine Datei, ohne Tippen)
#                   klient schliessen|butlermenue      (Bildschirm schließen / Butler-Menü öffnen)
#                   klient fernsehermenue | klient fernseher <befehl>   (nächster Fernseher: Menü / Befehl wie !abspielen|Link)
#                   tippe <text>         (tippt in ein offenes Eingabefeld, z. B. im Handy)
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
# Auch mit behaltener Welt den Port dieses Laufs setzen (sonst hört der Server auf dem Port eines früheren Laufs)
if grep -q "^server-port=" "$RUN/server.properties" 2>/dev/null; then
  sed -i "s/^server-port=.*/server-port=${PORT:-25565}/" "$RUN/server.properties"
else
  echo "server-port=${PORT:-25565}" >> "$RUN/server.properties"
fi

# ---------- 2. Welt als Einzelspieler-Spielstand vorbereiten ----------
# (bei VANILLA=1 spielt der normale Client in einem eigenen Ordner auf dem Server)
KLIENT_ORDNER="$RUN"
[[ "${VANILLA:-0}" == 1 && "${MITMOD:-0}" != 1 ]] && KLIENT_ORDNER="$RUN/vanilla"
mkdir -p "$KLIENT_ORDNER"
rm -rf "$RUN/saves/testwelt" "$KLIENT_ORDNER/screenshots"
mkdir -p "$RUN/saves"
cp -r "$RUN/testwelt" "$RUN/saves/testwelt"
python3 "$ROOT/tools/ingame/welt_vorbereiten.py" "$RUN/saves/testwelt/level.dat"
cat > "$KLIENT_ORDNER/options.txt" <<EOF
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
if [[ "${VANILLA:-0}" == 1 ]]; then
  # Philip darf auf dem Server Befehle ausführen (Offline-Kennung wie bei online-mode=false)
  python3 - "$RUN/ops.json" <<'PY'
import hashlib, json, sys, uuid
h = bytearray(hashlib.md5(b"OfflinePlayer:Philip").digest()); h[6] = (h[6] & 0x0f) | 0x30; h[8] = (h[8] & 0x3f) | 0x80
json.dump([{"uuid": str(uuid.UUID(bytes=bytes(h))), "name": "Philip", "level": 4, "bypassesPlayerLimit": False}], open(sys.argv[1], "w"))
PY
  # Serverliste mit „Ressourcenpaket annehmen“, damit keine Rückfrage kommt (servers.dat ist unkomprimiertes NBT)
  python3 - "$KLIENT_ORDNER/servers.dat" "localhost:${PORT:-25565}" <<'PY'
import struct, sys
def s(t): b = t.encode(); return struct.pack(">H", len(b)) + b
eintrag = b"\x08" + s("ip") + s(sys.argv[2]) + b"\x08" + s("name") + s("Test") + b"\x01" + s("acceptTextures") + b"\x01" + b"\x00"
open(sys.argv[1], "wb").write(b"\x0a" + s("") + b"\x09" + s("servers") + b"\x0a" + struct.pack(">i", 1) + eintrag + b"\x00")
PY
  # Server-Mods (Polymer) nur für diesen Lauf in den Mods-Ordner legen
  rm -rf "$RUN/mods"; mkdir -p "$RUN/mods"
  cp "$ROOT/tools/ingame/server-mods/$V/"*.jar "$RUN/mods/" 2>/dev/null
fi

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
  local p i noch
  for p in $(pgrep -f "net.fabricmc.devlaunchinjector.Main"); do
    [[ "$(readlink "/proc/$p/cwd" 2>/dev/null)" == "$RUN" ]] && kill "$p" 2>/dev/null
  done
  # Warten, bis alles beendet ist: ein Server, der noch speichert, würde sonst in die Welt des nächsten Laufs schreiben
  for i in $(seq 1 60); do
    noch=0
    for p in $(pgrep -f "net.fabricmc.devlaunchinjector.Main"); do
      [[ "$(readlink "/proc/$p/cwd" 2>/dev/null)" == "$RUN" ]] && noch=1
    done
    [[ $noch == 0 ]] && break
    sleep 0.5
  done
}
aufraeumen() {
  eigene_beenden
  kill "$CLIENT" "$XVFB" ${SERVER:-} 2>/dev/null
  [[ "${VANILLA:-0}" == 1 ]] && rm -rf "$RUN/mods"
}
trap aufraeumen EXIT

rm -f "$RUN/stinkendreich-befehle.txt" "$RUN/stinkendreich-klient.txt"
if [[ "${VANILLA:-0}" == 1 ]]; then
  melde "Server startet (mit Mod) …"
  STINKENDREICH_AUTOTEST=befehle ./gradlew ":$V:runServer" --console=plain > "$LOGS/server_spiel.log" 2>&1 </dev/null &
  SERVER=$!
  for _ in $(seq 1 600); do
    grep -q "Done (" "$LOGS/server_spiel.log" && break
    kill -0 "$SERVER" 2>/dev/null || { melde "FEHLER: Server ist abgestürzt"; tail -60 "$LOGS/server_spiel.log"; exit 1; }
    sleep 1
  done
  if [[ "${MITMOD:-0}" == 1 ]]; then
    # Spieler MIT Mod auf demselben Server (prüft, dass die Server-Version Mod-Spielern nichts übersetzt)
    melde "Minecraft mit Mod verbindet sich mit dem Server …"
    # Der Server hat Polymer schon geladen; der Client soll es nicht haben (MITPOLYMER=1: doch)
    [[ "${MITPOLYMER:-0}" == 1 ]] || rm -rf "$RUN/mods"
    ./gradlew ":$V:runClient" --console=plain \
      --args="--quickPlayMultiplayer localhost:${PORT:-25565} --width 1280 --height 720 --username Philip" \
      > "$LOGS/client.log" 2>&1 </dev/null &
  else
    melde "Normales Minecraft (ohne Mod) startet …"
    python3 "$ROOT/tools/ingame/vanilla_klient.py" "$V" "$KLIENT_ORDNER" "localhost:${PORT:-25565}" Philip \
      > "$LOGS/client.log" 2>&1 </dev/null &
  fi
  CLIENT=$!
else
melde "Client startet …"
STINKENDREICH_AUTOTEST=befehle ./gradlew ":$V:runClient" --console=plain \
  --args="--quickPlaySingleplayer testwelt --width 1280 --height 720 --username Philip" \
  > "$LOGS/client.log" 2>&1 </dev/null &
CLIENT=$!
fi

# Protokoll mit den Chatzeilen: der Entwicklungs-Client schreibt sie mit, ein normales Minecraft nicht (dann der Server)
CHATLOG="$LOGS/client.log"
[[ "${VANILLA:-0}" == 1 ]] && CHATLOG="$LOGS/server_spiel.log"
for _ in $(seq 1 900); do
  grep -qE "joined the game|hat das Spiel betreten" "$CHATLOG" && break
  if ! kill -0 "$CLIENT" 2>/dev/null; then
    melde "FEHLER: Client ist abgestürzt"; tail -60 "$LOGS/client.log"; exit 1
  fi
  sleep 1
done
grep -qE "joined the game|hat das Spiel betreten" "$CHATLOG" || { melde "FEHLER: Welt nicht betreten"; tail -60 "$LOGS/client.log"; exit 1; }
melde "In der Welt, warte auf die Darstellung …"
sleep "${RENDERZEIT:-25}"

# Das Spielfenster ist das große Kindfenster der Wurzel mit „Minecraft“ im Namen (xdotool search liefert bei einem
# normalen Minecraft manchmal die Wurzel selbst, dann kommen keine Tasten an)
FENSTER="$(xwininfo -root -tree 2>/dev/null | grep -E '"Minecraft [0-9]' | grep -v 'Hidden' | head -1 | awk '{print $1}')"
[[ -n "$FENSTER" ]] && FENSTER="$((FENSTER))"
[[ -n "$FENSTER" ]] || FENSTER="$(xdotool search --onlyvisible --name "Minecraft" | head -1)"
[[ -n "$FENSTER" ]] || FENSTER="$(xdotool search --name "Minecraft" | head -1)"
# Ein normales Minecraft (SDL ohne Fensterverwaltung) hat manchmal keinen Fensternamen: dann das größte sichtbare Fenster
if [[ -z "$FENSTER" ]]; then
  for w in $(xdotool search --onlyvisible --name ".*" 2>/dev/null); do
    breite="$(xdotool getwindowgeometry "$w" 2>/dev/null | sed -n 's/.*Geometry: \([0-9]*\)x.*/\1/p')"
    [[ "${breite:-0}" -ge 1000 ]] && FENSTER="$w"
  done
fi
melde "Fenster: $FENSTER ($(xdotool getwindowname "$FENSTER" 2>/dev/null))"
if [[ "${VANILLA:-0}" == 1 ]]; then
  for w in $(xdotool search --name ".*" 2>/dev/null); do
    echo "  Fenster $w: '$(xdotool getwindowname "$w" 2>/dev/null)' $(xdotool getwindowgeometry "$w" 2>/dev/null | tr '\n' ' ')" >> "$LOGS/fenster.txt"
  done
  xwininfo -root -tree >> "$LOGS/fenster.txt" 2>&1
  # Ein Klick ins Fenster gibt ihm den Fokus (SDL nimmt sonst keine Tasten an); vorher Chat öffnen, damit der Klick nichts abbaut
  xdotool windowactivate "$FENSTER" 2>/dev/null; xdotool windowraise "$FENSTER" 2>/dev/null
fi
xdotool windowfocus "$FENSTER" 2>/dev/null
xdotool mousemove 640 360

# ---------- 4. Szenario abarbeiten ----------
bild() {
  local name="$1" vorher
  vorher="$(ls "$KLIENT_ORDNER/screenshots" 2>/dev/null | wc -l)"
  xdotool key F2
  for _ in $(seq 1 60); do
    [[ "$(ls "$KLIENT_ORDNER/screenshots" 2>/dev/null | wc -l)" -gt "$vorher" ]] && break
    sleep 0.5
  done
  sleep 1
  local neu
  neu="$(ls -t "$KLIENT_ORDNER/screenshots"/*.png 2>/dev/null | head -1)"
  if [[ -n "$neu" ]]; then
    mv "$neu" "$AUS/$name.png"; melde "Bild: $AUS/$name.png"
  elif ffmpeg -y -loglevel error -f x11grab -video_size 1280x720 -i "$DISPLAY" -frames:v 1 "$AUS/$name.png" </dev/null; then
    melde "Bild (vom Bildschirm): $AUS/$name.png"
  else
    melde "WARNUNG: kein Screenshot für $name"
  fi
}

# Videoaufnahme des virtuellen Bildschirms (ohne Mauszeiger); Kapitel mit Sekunden seit dem Start
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

# Tasten bei einem normalen Minecraft direkt ans Fenster schicken (es bekommt ohne Fensterverwaltung keinen Fokus)
FENSTER_ARG=""
[[ "${VANILLA:-0}" == 1 && -n "$FENSTER" ]] && FENSTER_ARG="--window $FENSTER"

# Kopie lesen: so darf das Szenario während eines Laufs bearbeitet werden
cp "$SZENARIO" "$RUN/szenario.txt"
while read -r wort rest || [[ -n "$wort" ]]; do
  case "$wort" in
    ""|\#*) ;;
    warte) sleep "$rest" ;;
    taste) if [[ "${VANILLA:-0}" == 1 ]]; then xdotool key --window "$FENSTER" $rest; else xdotool key $rest; fi; sleep 0.5 ;;
    befehl) xdotool key t; sleep "${TIPP_PAUSE:-1}"; xdotool type --delay 30 "$rest"; sleep 0.4; xdotool key Return; sleep 1.5 ;;
    tippe) xdotool type --delay 40 "$rest"; sleep 0.5 ;;                 # Text in ein offenes Eingabefeld tippen
    bild) bild "$rest" ;;
    klick) xdotool click 3; sleep 1 ;;                                   # Rechtsklick (Block benutzen)
    klickbild) xdotool click 3; sleep 0.25; bild "$rest" ;;               # Rechtsklick und sofort ein Bild (z. B. Schuss)
    linksklick) set -- $rest; xdotool mousemove "$1" "$2"; sleep 0.3; xdotool click 1; sleep 1 ;;
    maus) set -- $rest; xdotool mousemove "$1" "$2"; sleep 0.3 ;;
    server) printf '%s\n' "$rest" >> "$RUN/stinkendreich-befehle.txt"; sleep 1 ;;   # Befehl ohne Tippen (Autotest-Modus)
    klient) printf '%s\n' "$rest" >> "$RUN/stinkendreich-klient.txt"; sleep 1 ;;   # schliessen | butlermenue
    merke) MARKE=$(wc -l < "$CHATLOG") ;;
    bis) set -f; set -- $rest; set +f; frist="$1"; shift; muster="$*"; ab="${MARKE:-$(wc -l < "$CHATLOG")}"; MARKE=""; ende=$((SECONDS + frist))
         until tail -n +"$((ab + 1))" "$CHATLOG" | grep -qaE "$muster"; do
           if (( SECONDS >= ende )); then melde "WARNUNG: „$muster“ kam nicht innerhalb von $frist s"; break; fi
           sleep 2
         done ;;
    druecke) xdotool keydown $FENSTER_ARG $rest ;;
    loslassen) xdotool keyup $FENSTER_ARG $rest; sleep 0.3 ;;
    halte) set -- $rest; dauer="$1"; shift; xdotool keydown $FENSTER_ARG "$@"; sleep "$dauer"; xdotool keyup $FENSTER_ARG "$@"; sleep 0.3 ;;   # Tasten gedrückt halten
    video) video $rest ;;
    kapitel) kapitel "$rest" ;;
    *) melde "Unbekannte Szenario-Zeile: $wort $rest" ;;
  esac
done < "$RUN/szenario.txt"
video stopp

melde "Fertig"
