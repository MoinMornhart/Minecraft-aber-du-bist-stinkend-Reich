#!/usr/bin/env bash
# Baut das Ressourcenpaket des Paper-Plugins so, wie es das Plugin beim Start baut (inhaltsgleich), für das GitHub-
# Release: startet einen Paper-Server mit dem Plugin, wartet auf „Ressourcenpaket: … KB“ und stoppt ihn wieder.
# Aufruf: tools/paper/paket_bauen.sh <plugin.jar> <ziel.zip>    (im Release-Ablauf, Java 25 im PATH)
set -euo pipefail
PLUGIN="$(realpath "$1")"; ZIEL="$(realpath -m "$2")"
V=26.3; BUILD="${PAPER_BUILD:-226}"
DIR="$(mktemp -d)"
cd "$DIR"
URL="$(curl -fsSL "https://fill.papermc.io/v3/projects/paper/versions/$V/builds/$BUILD" | python3 -c \
  'import json,sys; print(json.load(sys.stdin)["downloads"]["server:default"]["url"])')"
curl -fsSL -o paper.jar "$URL"
mkdir -p plugins
cp "$PLUGIN" plugins/
echo "eula=true" > eula.txt
printf 'online-mode=false\nlevel-type=minecraft\\:flat\nspawn-protection=0\nserver-port=25599\n' > server.properties
java -Xmx2G -jar paper.jar --nogui > server.log 2>&1 &
PID=$!
for i in $(seq 1 600); do
  if grep -q "Ressourcenpaket: .* KB" server.log; then break; fi
  if ! kill -0 "$PID" 2>/dev/null; then cat server.log; echo "Server beendet, kein Paket"; exit 1; fi
  sleep 1
done
kill "$PID" 2>/dev/null || true
wait "$PID" 2>/dev/null || true
grep "Ressourcenpaket" server.log
cp plugins/StinkendReich/ressourcenpaket.zip "$ZIEL"
ls -la "$ZIEL"
