#!/usr/bin/env python3
"""Macht aus einer Videoaufnahme des Spieltests (test.sh: „video start …“) ein fertiges Video:
Kapiteltitel unten links mit Ein- und Ausblenden, großer Titel am Anfang, Musik der Villa darunter.

Aufruf: python3 tools/ingame/video.py <name>_roh.mp4 <name>_kapitel.txt <ausgabe.mp4> [titel] [untertitel]
Die Kapiteldatei hat Zeilen „Sekunde|Text“ (schreibt test.sh mit „kapitel <text>“).
"""
from __future__ import annotations

import subprocess
import sys
import tempfile
from pathlib import Path

WURZEL = Path(__file__).resolve().parents[2]
MUSIK = WURZEL / "src/main/resources/assets/stinkendreich/sounds/aufzug_musik.ogg"
SCHRIFT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
GOLD = "0xE8BA34"
DAUER_KAPITEL = 4.5


def laenge(datei: Path) -> float:
    aus = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "default=nw=1:nk=1", str(datei)],
                         capture_output=True, text=True, check=True)
    return float(aus.stdout.strip())


def einblenden(t0: float, t1: float) -> str:
    """Deckkraft: 0,4 s ein, 0,4 s aus."""
    return f"if(lt(t,{t0}+0.4),(t-{t0})/0.4,if(lt(t,{t1}-0.4),1,({t1}-t)/0.4))"


def text_filter(textdatei: Path, t0: float, t1: float, groesse: int, x: str, y: str, farbe: str = "white") -> str:
    return (f"drawtext=fontfile={SCHRIFT}:textfile={textdatei}:fontsize={groesse}:fontcolor={farbe}"
            f":shadowcolor=black@0.8:shadowx=3:shadowy=3:box=1:boxcolor=black@0.35:boxborderw=14"
            f":x={x}:y={y}:enable='between(t,{t0},{t1})':alpha='{einblenden(t0, t1)}'")


def main() -> None:
    roh, kapitel_datei, ziel = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
    titel = sys.argv[4] if len(sys.argv) > 4 else "Minecraft, aber du bist stinkend reich"
    untertitel = sys.argv[5] if len(sys.argv) > 5 else "Rundgang durch die Villa"
    gesamt = laenge(roh)
    kapitel = []
    for zeile in kapitel_datei.read_text(encoding="utf-8").splitlines():
        if "|" in zeile:
            t, text = zeile.split("|", 1)
            kapitel.append((float(t), text.strip()))
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        filter_teile = []
        # Titel am Anfang, mittig
        for i, (text, groesse, farbe, y) in enumerate(((titel, 54, GOLD, "(h/2)-70"), (untertitel, 34, "white", "(h/2)+10"))):
            f = tmp / f"titel{i}.txt"
            f.write_text(text, encoding="utf-8")
            filter_teile.append(text_filter(f, 0.8, 6.0, groesse, "(w-text_w)/2", y, farbe))
        # Kapitel unten links
        for i, (t, text) in enumerate(kapitel):
            f = tmp / f"kapitel{i}.txt"
            f.write_text(text, encoding="utf-8")
            ende = min(t + DAUER_KAPITEL, gesamt)
            filter_teile.append(text_filter(f, t, ende, 36, "40", "h-110"))
        # Ganz am Ende ausblenden
        filter_teile.append(f"fade=t=out:st={max(0.0, gesamt - 2.0)}:d=2")
        filter_datei = tmp / "filter.txt"
        filter_datei.write_text(",".join(filter_teile), encoding="utf-8")
        befehl = ["ffmpeg", "-y", "-loglevel", "error", "-i", str(roh), "-stream_loop", "-1", "-i", str(MUSIK),
                  "-filter_script:v", str(filter_datei),
                  "-af", f"volume=0.7,afade=t=in:d=1.5,afade=t=out:st={max(0.0, gesamt - 3.0)}:d=3",
                  "-map", "0:v", "-map", "1:a", "-t", f"{gesamt:.2f}",
                  "-c:v", "libx264", "-preset", "slow", "-crf", "26", "-pix_fmt", "yuv420p",
                  "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", str(ziel)]
        subprocess.run(befehl, check=True)
    print(f"{ziel}: {laenge(ziel):.0f} s, {ziel.stat().st_size / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
