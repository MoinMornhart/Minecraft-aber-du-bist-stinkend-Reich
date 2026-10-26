#!/usr/bin/env python3
"""Startet ein ganz normales Minecraft ohne Mods (für den Test der Server-Version: Server mit Mod, Spieler ohne).

Nimmt das Spiel und die Bibliotheken aus dem Gradle-Zwischenspeicher von Loom (dieselben Dateien, die auch der
Entwicklungs-Client nutzt) und verbindet sich gleich mit einem Server.

Aufruf: python3 tools/ingame/vanilla_klient.py <version> <spielordner> <server:port> <name>
Die Bildschirm-Umgebung (DISPLAY usw.) kommt vom Aufrufer (tools/ingame/test.sh mit VANILLA=1).
"""
from __future__ import annotations

import glob
import json
import os
import sys
from pathlib import Path

LOOM = Path.home() / ".gradle/caches/fabric-loom"
MODULE = Path.home() / ".gradle/caches/modules-2/files-2.1"


def erlaubt(regeln: list | None) -> bool:
    """Bibliotheksregeln von Mojang für Linux auswerten."""
    if not regeln:
        return True
    ok = False
    for r in regeln:
        system = r.get("os", {}).get("name")
        if system in (None, "linux"):
            ok = r["action"] == "allow"
    return ok


def klassenpfad(info: dict, version: str) -> list[str]:
    pfad: list[str] = []
    for lib in info["libraries"]:
        if not erlaubt(lib.get("rules")):
            continue
        gruppe, name, ver = lib["name"].split(":")[:3]
        # Alle Dateien der Bibliothek (Hauptdatei und Linux-Natives), wie Gradle sie abgelegt hat
        for datei in sorted(glob.glob(str(MODULE / gruppe / name / ver / "*" / f"{name}-{ver}*.jar"))):
            if datei.endswith("-sources.jar") or datei in pfad:
                continue
            if "natives-" in datei and "natives-linux" not in datei:
                continue
            pfad.append(datei)
    pfad.append(str(LOOM / version / "minecraft-client.jar"))
    return pfad


def assets_ordner(info: dict, version: str, spielordner: Path) -> tuple[Path, str]:
    """Eigener Assets-Ordner mit dem Index unter dem Namen, den Minecraft erwartet (Loom hängt die Version vorn an)."""
    index = info["assetIndex"]["id"]
    ziel = spielordner / "assets"
    (ziel / "indexes").mkdir(parents=True, exist_ok=True)
    quelle = LOOM / "assets" / "indexes" / f"{version}-{index}.json"
    verweis = ziel / "indexes" / f"{index}.json"
    if not verweis.exists():
        verweis.symlink_to(quelle)
    if not (ziel / "objects").exists():
        (ziel / "objects").symlink_to(LOOM / "assets" / "objects")
    return ziel, index


def main() -> None:
    version, spielordner, server, name = sys.argv[1:5]
    spielordner_p = Path(spielordner).resolve()
    spielordner_p.mkdir(parents=True, exist_ok=True)
    info = json.loads((LOOM / version / "mojang_minecraft_info.json").read_text(encoding="utf-8"))
    java_ver = info.get("javaVersion", {}).get("majorVersion", 21)
    java = next((j for j in (f"/usr/lib/jvm/java-{java_ver}-openjdk-amd64/bin/java", "java") if os.path.exists(j) or j == "java"))
    assets, index = assets_ordner(info, version, spielordner_p)
    befehl = [
        java, "-Xmx3G", "-cp", ":".join(klassenpfad(info, version)), info["mainClass"],
        "--username", name, "--version", version, "--gameDir", str(spielordner_p),
        "--assetsDir", str(assets), "--assetIndex", index, "--accessToken", "0", "--userType", "legacy",
        "--versionType", "release", "--width", "1280", "--height", "720", "--quickPlayMultiplayer", server,
    ]
    os.chdir(spielordner_p)
    os.execvp(befehl[0], befehl)


if __name__ == "__main__":
    main()
