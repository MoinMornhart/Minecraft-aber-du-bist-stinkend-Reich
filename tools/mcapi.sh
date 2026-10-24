#!/usr/bin/env bash
# Zeigt Felder und Methoden einer Minecraft-Klasse in einer bestimmten Version (Mojang-Namen).
# Braucht einen Gradle-Lauf vorher, damit Loom die Jars im Cache hat.
#
# Aufruf: tools/mcapi.sh <version> <klasse> [suchtext]
#   z. B. tools/mcapi.sh 26.3 net.minecraft.client.gui.components.ChatComponent addMessage
V="${1:?Version}"; KLASSE="${2:?Klasse}"; FILTER="${3:-}"
BASIS="$HOME/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft"
JAR="$(ls "$BASIS"/minecraft-merged-deobf/"$V"/*.jar 2>/dev/null | head -1)"
[[ -z "$JAR" ]] && JAR="$(ls "$BASIS"/minecraft-merged/"$V"-loom.mappings*/*.jar 2>/dev/null | head -1)"
[[ -z "$JAR" ]] && { echo "Kein Jar für $V gefunden" >&2; exit 1; }
javap -cp "$JAR" -p "$KLASSE" 2>/dev/null | grep -i -- "$FILTER"
