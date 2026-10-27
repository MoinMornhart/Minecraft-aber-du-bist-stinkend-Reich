"""Setzt die Stücke von paper_trailer3a … l zu einer Rohaufnahme mit Kapiteldatei zusammen (Wartezeiten fehlen)."""
import subprocess
from pathlib import Path

B = Path(__file__).resolve().parents[2] / "build/ingame/paper"
S = Path(__file__).resolve().parents[2] / "build/ingame/paper/trailer3"
S.mkdir(parents=True, exist_ok=True)


def laenge(d):
    return float(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(d)]))


# Leerlauf herausschneiden (Sekunden im Stück) und zusätzliche Kapitel (Sekunden nach dem Schnitt)
SCHNITTE = {"e": [(0.0, 4.0), (12.0, None)], "h": [(3.0, 12.0), (15.0, None)], "j": [(15.0, None)]}
EXTRA = {"e": [(10.0, "Erledigt: jede Sorte liegt in ihrer Stammtruhe")]}

liste, kapitel, versatz = [], [], 0.0
for buchstabe in "abcdefghijkl":
    roh = B / f"paper_trailer3{buchstabe}_roh.mp4"
    if not roh.exists():
        print("fehlt:", roh.name)
        continue
    for z in (B / f"paper_trailer3{buchstabe}_kapitel.txt").read_text().splitlines():
        if z:
            t, text = z.split("|", 1)
            kapitel.append((versatz + max(0.0, float(t) - 0.3), text))
    for t, text in EXTRA.get(buchstabe, []):
        kapitel.append((versatz + t, text))
    # Erste 0,3 s weg (Teleport-Ruckler beim Start), einheitlich 25 Bilder/s
    for n, (von, bis) in enumerate(SCHNITTE.get(buchstabe, [(0.0, None)])):
        teil = S / f"teil_{buchstabe}{n}.mp4"
        dauer = [] if bis is None else ["-t", f"{bis - von}"]
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", f"{0.3 + von}", "-i", str(roh), *dauer, "-vf", "fps=25",
                        "-c:v", "libx264", "-preset", "veryfast", "-crf", "18", "-pix_fmt", "yuv420p", "-an", str(teil)],
                       check=True)
        versatz += laenge(teil)
        liste.append(f"file '{teil}'")
(S / "liste.txt").write_text("\n".join(liste) + "\n")
subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", str(S / "liste.txt"), "-c", "copy",
                str(S / "paper_trailer3_roh.mp4")], check=True)
kapitel.sort()
(S / "paper_trailer3_kapitel.txt").write_text("".join(f"{t:.1f}|{x}\n" for t, x in kapitel))
print(f"{versatz:.1f} s, {len(kapitel)} Kapitel")
for t, x in kapitel:
    print(f"  {t:6.1f} {x}")
