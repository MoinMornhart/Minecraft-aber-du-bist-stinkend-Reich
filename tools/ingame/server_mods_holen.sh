#!/usr/bin/env bash
# Holt die Server-Mods für den Test der Server-Version (VANILLA=1 tools/ingame/test.sh …): Polymer passend zur
# Minecraft-Version. Sie landen in tools/ingame/server-mods/<version>/ (nicht im Repo).
# Aufruf: tools/ingame/server_mods_holen.sh 26.3
set -euo pipefail
V="${1:?Minecraft-Version angeben, z. B. 26.3}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
POLYMER="$(grep -A8 "^\[\"$V\"\]" "$ROOT/stonecutter.properties.toml" | sed -n 's/^deps.polymer = "\(.*\)"/\1/p')"
[[ -n "$POLYMER" ]] || { echo "Für $V ist keine Polymer-Version eingetragen (deps.polymer)"; exit 1; }
ZIEL="$ROOT/tools/ingame/server-mods/$V"
mkdir -p "$ZIEL"
curl -fsSL -o "$ZIEL/polymer-bundled-$POLYMER.jar" \
  "https://maven.nucleoid.xyz/eu/pb4/polymer-bundled/$POLYMER/polymer-bundled-$POLYMER.jar"
echo "Polymer $POLYMER liegt in $ZIEL"
