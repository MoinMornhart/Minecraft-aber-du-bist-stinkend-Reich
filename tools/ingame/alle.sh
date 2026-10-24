#!/usr/bin/env bash
# Testet die Mod nacheinander in jeder Minecraft-Version (siehe test.sh).
# Ergebnis je Version in build/ingame/ergebnis.txt, Bilder in build/ingame/<version>/.
#
# Aufruf: tools/ingame/alle.sh [szenario] [version ...]
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SZENARIO="${1:-$ROOT/tools/ingame/szenarien/start.txt}"
shift || true
VERSIONEN=("$@")
if [[ ${#VERSIONEN[@]} -eq 0 ]]; then
  VERSIONEN=(1.21 1.21.1 1.21.2 1.21.3 1.21.4 1.21.5 1.21.6 1.21.7 1.21.8 1.21.9 1.21.10 1.21.11 26.1 26.1.1 26.1.2 26.2 26.3)
fi
ERGEBNIS="$ROOT/build/ingame/ergebnis.txt"
mkdir -p "$(dirname "$ERGEBNIS")"
for v in "${VERSIONEN[@]}"; do
  if timeout 1800 "$ROOT/tools/ingame/test.sh" "$v" "$SZENARIO"; then
    echo "$v OK $(date +%H:%M)" >> "$ERGEBNIS"
  else
    echo "$v FEHLER $(date +%H:%M)" >> "$ERGEBNIS"
  fi
done
echo "Alle Versionen durch"
