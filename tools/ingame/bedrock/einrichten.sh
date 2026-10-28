#!/usr/bin/env bash
# Richtet den Test-Client für Bedrock-Spieler ein (bot.js): bedrock-protocol ohne Bau-Skripte installieren und die
# mitgelieferte Linux-Bibliothek von raknet-native für den laufenden Kernel freigeben (sie sucht nach der Kernel-
# Hauptversion, mitgeliefert ist nur linux-5). Die reine JavaScript-Variante (jsp-raknet) spricht RakNet 10 – Bedrock
# und Geyser wollen 11.
set -euo pipefail
cd "$(dirname "$0")"
npm install --no-audit --no-fund --ignore-scripts
P=node_modules/raknet-native/prebuilds
ZIEL="$P/linux-$(uname -r | cut -d. -f1)-x64"
[[ -d "$ZIEL" ]] || cp -r "$P/linux-5-x64" "$ZIEL"
node -e "require('raknet-native'); console.log('raknet-native geladen')"
