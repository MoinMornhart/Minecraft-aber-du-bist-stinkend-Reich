"""Setzt Trailer Teil 6 (0.4.2) aus trailer6a (Abzweig von oben) und trailer6b (James fährt über zwei Abzweige,
vierfach schneller) zusammen. Ergebnis: build/ingame/trailer6/trailer6_roh.mp4 und trailer6_kapitel.txt, danach
video.py und an docs/trailer.mp4 hängen.
"""
import subprocess
from pathlib import Path

W = Path(__file__).resolve().parents[2]
S = W / "build/ingame/trailer6"
S.mkdir(parents=True, exist_ok=True)


def laenge(d):
    return float(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(d)]))


# Stück, Rohaufnahme, Tempo (Faktor), Anfang (Sekunden der Aufnahme)
STUECKE = [
    ("a", W / "build/ingame/paper/trailer6a_roh.mp4", 1.0, 0.3),
    ("b", W / "build/ingame/paper/trailer6b_roh.mp4", 4.0, 0.3),
]

liste, kapitel, versatz = [], [], 0.0
for name, roh, tempo, von in STUECKE:
    teil = S / f"teil_{name}.mp4"
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", f"{von}", "-i", str(roh),
                    "-vf", f"setpts=PTS/{tempo},fps=25", "-c:v", "libx264", "-preset", "veryfast", "-crf", "18",
                    "-pix_fmt", "yuv420p", "-an", str(teil)], check=True)
    for z in roh.with_name(roh.name.replace("_roh.mp4", "_kapitel.txt")).read_text(encoding="utf-8").splitlines():
        if z:
            t, text = z.split("|", 1)
            kapitel.append((versatz + max(0.0, (float(t) - von) / tempo), text))
    versatz += laenge(teil)
    liste.append(f"file '{teil}'")
(S / "liste.txt").write_text("\n".join(liste) + "\n")
subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", str(S / "liste.txt"), "-c", "copy",
                str(S / "trailer6_roh.mp4")], check=True)
kapitel.sort()
(S / "trailer6_kapitel.txt").write_text("".join(f"{t:.1f}|{x}\n" for t, x in kapitel), encoding="utf-8")
print(f"{versatz:.1f} s, {len(kapitel)} Kapitel")
for t, x in kapitel:
    print(f"  {t:6.1f} {x}")
