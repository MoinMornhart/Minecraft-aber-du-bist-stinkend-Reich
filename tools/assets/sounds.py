#!/usr/bin/env python3
"""Erzeugt die eigenen Geräusche der Mod per Synthese (keine fremden Aufnahmen).

Schreibt Ogg-Vorbis-Dateien (mono, 44,1 kHz) nach assets/stinkendreich/sounds/ und die
passende sounds.json. Braucht ffmpeg mit libvorbis.

Aufruf: python3 tools/assets/sounds.py [--alle]   (ohne --alle nur fehlende Dateien)
"""
from __future__ import annotations

import json
import sys
import subprocess
import tempfile
import wave
from pathlib import Path

import numpy as np

WURZEL = Path(__file__).resolve().parents[2]
ZIEL = WURZEL / "src/main/resources/assets/stinkendreich/sounds"
RATE = 44100


def zeit(sekunden: float) -> np.ndarray:
    return np.arange(int(RATE * sekunden)) / RATE


def huelle(n: int, an: float = 0.005, ab: float = 0.02) -> np.ndarray:
    """Weiches Ein- und Ausblenden gegen Knacken."""
    e = np.ones(n)
    a, b = int(RATE * an), int(RATE * ab)
    e[:a] = np.linspace(0, 1, a)
    e[n - b:] = np.linspace(1, 0, b)
    return e


def ton(freq: float, dauer: float, obertoene=(1.0,), abklingen: float = 0.0) -> np.ndarray:
    t = zeit(dauer)
    s = sum(st * np.sin(2 * np.pi * freq * (i + 1) * t) for i, st in enumerate(obertoene))
    if abklingen:
        s *= np.exp(-t / abklingen)
    return s * huelle(len(t))


def stille(dauer: float) -> np.ndarray:
    return np.zeros(int(RATE * dauer))


def piep_ok() -> np.ndarray:
    p = ton(1760, 0.07, (1.0, 0.2))
    return np.concatenate([p, stille(0.05), ton(2350, 0.09, (1.0, 0.2))])


def piep_fehler() -> np.ndarray:
    t = zeit(0.4)
    s = np.sign(np.sin(2 * np.pi * 180 * t)) * 0.5 + np.sin(2 * np.pi * 360 * t) * 0.3
    return s * huelle(len(t), ab=0.05)


def sirene() -> np.ndarray:
    t = zeit(1.4)
    freq = 650 + 550 * np.sin(np.pi * t / 1.4) ** 2        # auf und ab
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    s = np.sin(phase) + 0.35 * np.sin(2 * phase) + 0.15 * np.sin(3 * phase)
    return s * huelle(len(t), ab=0.08)


def glocke(freq: float, dauer: float) -> np.ndarray:
    return ton(freq, dauer, (1.0, 0.45, 0.25, 0.12), abklingen=dauer / 3)


def gong() -> np.ndarray:
    a = glocke(880, 1.2)
    b = glocke(698.5, 1.4)
    aus = np.zeros(int(RATE * 1.9))
    aus[:len(a)] += a
    start = int(RATE * 0.45)
    aus[start:start + len(b)] += b
    return aus


def klingel() -> np.ndarray:
    aus = np.zeros(int(RATE * 2.2))
    for i, f in enumerate((659.3, 523.3, 587.3, 392.0)):
        g = glocke(f, 1.3)
        s = int(RATE * 0.32 * i)
        ende = min(len(aus), s + len(g))
        aus[s:ende] += g[:ende - s]
    return aus


def tresor() -> np.ndarray:
    t = zeit(0.7)
    rnd = np.random.default_rng(7)
    rumms = np.sin(2 * np.pi * 70 * t) * np.exp(-t / 0.12)
    klirr = rnd.normal(0, 1, len(t)) * np.exp(-t / 0.03) * 0.4
    hall = (np.sin(2 * np.pi * 410 * t) + 0.6 * np.sin(2 * np.pi * 1130 * t)) * np.exp(-t / 0.25) * 0.25
    return (rumms + klirr + hall) * huelle(len(t), ab=0.1)


def gloeckchen() -> np.ndarray:
    """Helles Tischglöckchen, zweimal angeschlagen."""
    aus = np.zeros(int(RATE * 1.3))
    for start in (0.0, 0.28):
        g = ton(2093.0, 0.9, (1.0, 0.0, 0.35, 0.0, 0.18), abklingen=0.22)
        s = int(RATE * start)
        aus[s:s + len(g)] += g[:len(aus) - s]
    return aus


def motor() -> np.ndarray:
    """Sportwagen-Motor im Leerlauf, nahtlos wiederholbar (alle Anteile passen genau in 2 Sekunden).
    Die Mod hebt beim Fahren die Tonhöhe an."""
    t = zeit(2.0)
    grund = 41.0                                            # Zündfrequenz (82 Schwingungen in 2 s)
    s = np.zeros_like(t)
    for k, staerke in enumerate((1.0, 0.7, 0.55, 0.4, 0.3, 0.22, 0.15, 0.1), start=1):
        s += staerke * np.sin(2 * np.pi * grund * k * t + k * 0.7)
    blubbern = 0.75 + 0.25 * np.sin(2 * np.pi * 10.5 * t)   # 21 Wellen in 2 s
    s *= blubbern
    s += 0.12 * np.sign(np.sin(2 * np.pi * grund * 2 * t)) * blubbern
    return s


def anlasser() -> np.ndarray:
    """Anlasser orgelt kurz, dann springt der Motor an und heult auf."""
    t1 = zeit(0.55)
    orgeln = np.sign(np.sin(2 * np.pi * (180 + 60 * np.sin(2 * np.pi * 6 * t1)) * t1)) * 0.35 * huelle(len(t1))
    t2 = zeit(1.1)
    freq = 40 + 70 * np.exp(-((t2 - 0.35) ** 2) / 0.03) + 10 * t2
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    roehren = sum(st * np.sin(k * phase) for k, st in enumerate((1.0, 0.7, 0.5, 0.35, 0.2), start=1))
    roehren *= np.minimum(1.0, t2 * 8) * np.exp(-t2 / 1.4)
    return np.concatenate([orgeln, roehren * huelle(len(t2), ab=0.15)])


def hupe() -> np.ndarray:
    """Zweiklang-Hupe (Fanfare), kurz und kräftig."""
    t = zeit(0.7)
    s = np.zeros_like(t)
    for f in (415.0, 523.0):
        phase = 2 * np.pi * f * t
        s += np.sign(np.sin(phase)) * 0.35 + np.sin(2 * phase) * 0.2 + np.sin(3 * phase) * 0.1
    return s * huelle(len(t), an=0.01, ab=0.06)


def reifen() -> np.ndarray:
    """Quietschende Reifen: schwankender hoher Ton mit Rauschen."""
    t = zeit(0.45)
    rnd = np.random.default_rng(11)
    freq = 1650 + 120 * np.sin(2 * np.pi * 9 * t)
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    s = np.sin(phase) * 0.6 + np.sin(2 * phase) * 0.15 + rnd.normal(0, 0.25, len(t))
    return s * huelle(len(t), an=0.03, ab=0.12)


def crash() -> np.ndarray:
    """Aufprall: dumpfer Schlag, Blech und klirrende Splitter."""
    t = zeit(0.9)
    rnd = np.random.default_rng(5)
    schlag = np.sin(2 * np.pi * 55 * t) * np.exp(-t / 0.09)
    blech = (np.sin(2 * np.pi * 620 * t) + 0.7 * np.sin(2 * np.pi * 977 * t) + 0.5 * np.sin(2 * np.pi * 1530 * t)) * np.exp(-t / 0.18) * 0.35
    splitter = rnd.normal(0, 1, len(t)) * np.exp(-t / 0.05) * 0.5
    klirr = np.zeros_like(t)
    for start, f in ((0.08, 3100), (0.15, 4200), (0.22, 3600), (0.3, 5100)):
        k = (t >= start) * np.sin(2 * np.pi * f * t) * np.exp(-np.maximum(0, t - start) / 0.04) * 0.25
        klirr += k
    return (schlag + blech + splitter + klirr) * huelle(len(t), ab=0.1)


def midi(n: float) -> float:
    return 440.0 * 2 ** ((n - 69) / 12)


def aufzug_musik() -> np.ndarray:
    """Fahrstuhlmusik: entspannter Bossa nova in F-Dur, 8 Takte, nahtlos wiederholbar.

    E-Piano spielt Septakkorde im Bossa-Rhythmus, darüber ein Vibraphon, unten ein weicher Bass,
    dazu Besen auf jedem Achtel. Ausklingende Töne am Ende werden an den Anfang gefaltet.
    """
    tempo = 112
    schlag = 60 / tempo
    laenge = int(RATE * schlag * 4 * 8)
    puffer = np.zeros(laenge + RATE * 3)

    def setze(start_schlag: float, signal: np.ndarray, lautstaerke: float) -> None:
        a = int(RATE * start_schlag * schlag)
        puffer[a:a + len(signal)] += signal * lautstaerke

    def epiano(f: float, dauer: float) -> np.ndarray:
        t = zeit(dauer + 1.2)
        s = (np.sin(2 * np.pi * f * t) + 0.28 * np.sin(2 * np.pi * 2 * f * t)
             + 0.12 * np.sin(2 * np.pi * 7 * f * t) * np.exp(-t / 0.05)) * np.exp(-t / 0.9)
        s *= 1 + 0.15 * np.sin(2 * np.pi * 4.5 * t)
        aus = np.ones_like(t)
        n = int(RATE * dauer)
        aus[n:] = np.exp(-(t[n:] - t[n]) / 0.15)
        return s * aus * huelle(len(t), an=0.004, ab=0.05)

    def vibraphon(f: float, dauer: float) -> np.ndarray:
        t = zeit(dauer + 1.5)
        s = (np.sin(2 * np.pi * f * t) + 0.22 * np.sin(2 * np.pi * 4 * f * t) * np.exp(-t / 0.3)) * np.exp(-t / 1.3)
        s *= 1 + 0.35 * np.sin(2 * np.pi * 5.2 * t)
        return s * huelle(len(t), an=0.003, ab=0.1)

    def bass(f: float, dauer: float) -> np.ndarray:
        t = zeit(dauer)
        return (np.sin(2 * np.pi * f * t) + 0.25 * np.sin(2 * np.pi * 2 * f * t)) * np.exp(-t / 0.7) * huelle(len(t), an=0.01, ab=0.08)

    def besen(stark: bool) -> np.ndarray:
        t = zeit(0.12)
        rnd = np.random.default_rng(int(stark) + 7)
        rauschen = rnd.normal(0, 1, len(t))
        rauschen = np.convolve(rauschen, np.ones(6) / 6, mode="same") - np.convolve(rauschen, np.ones(40) / 40, mode="same")
        return rauschen * np.exp(-t / (0.05 if stark else 0.025))

    akkorde = [(53, 57, 60, 64), (53, 57, 60, 62), (55, 58, 62, 65), (52, 55, 58, 60),
               (53, 57, 60, 64), (53, 57, 58, 62), (55, 58, 62, 65), (52, 55, 58, 64)]
    grundtoene = [(41, 48), (38, 45), (43, 50), (36, 43), (41, 48), (46, 41), (43, 50), (36, 43)]
    rhythmus = [(0.0, 0.9), (1.5, 0.45), (3.0, 0.8)]
    melodie = [
        [(0, 69, 1.5), (1.5, 72, 1), (2.5, 76, 1.5)],
        [(0, 74, 1), (1, 72, 1), (2, 69, 2)],
        [(0, 70, 1.5), (1.5, 74, 1), (2.5, 77, 1.5)],
        [(0, 76, 1), (1, 74, 0.5), (1.5, 72, 0.5), (2, 70, 2)],
        [(0, 69, 1), (1, 72, 1), (2, 77, 1), (3, 76, 1)],
        [(0, 74, 2), (2, 77, 1), (3, 81, 1)],
        [(0, 79, 1.5), (1.5, 77, 0.5), (2, 74, 2)],
        [(0, 76, 1), (1, 67, 1), (2, 70, 1), (3, 72, 1)],
    ]
    for takt in range(8):
        b0 = takt * 4
        for start, dauer in rhythmus:
            for note in akkorde[takt]:
                setze(b0 + start, epiano(midi(note), dauer * schlag), 0.09)
        wurzel, quinte = grundtoene[takt]
        setze(b0, bass(midi(wurzel), 1.4 * schlag), 0.5)
        setze(b0 + 2, bass(midi(quinte), 1.4 * schlag), 0.42)
        for start, note, dauer in melodie[takt]:
            setze(b0 + start, vibraphon(midi(note), dauer * schlag), 0.28)
        for achtel in range(8):
            setze(b0 + achtel / 2, besen(achtel % 4 == 2), 0.05 if achtel % 4 == 2 else 0.03)
    # Nachhall der letzten Töne an den Anfang falten, damit die Schleife nahtlos ist
    ergebnis = puffer[:laenge].copy()
    rest = puffer[laenge:]
    ergebnis[:len(rest)] += rest
    return ergebnis


GERAEUSCHE = {
    "piep_ok": (piep_ok, "Piepen", "Beep"),
    "piep_fehler": (piep_fehler, "Fehlerton", "Error buzz"),
    "sirene": (sirene, "Alarmsirene heult", "Siren wails"),
    "gong": (gong, "Aufzug-Gong", "Elevator chime"),
    "klingel": (klingel, "Türklingel", "Doorbell"),
    "tresor": (tresor, "Schwere Tür", "Heavy door"),
    "gloeckchen": (gloeckchen, "Glöckchen klingelt", "Bell rings"),
    "motor": (motor, "Motor brummt", "Engine hums"),
    "anlasser": (anlasser, "Motor startet", "Engine starts"),
    "hupe": (hupe, "Hupe", "Horn"),
    "reifen": (reifen, "Reifen quietschen", "Tyres squeal"),
    "crash": (crash, "Blech kracht", "Metal crunches"),
    "aufzug_musik": (aufzug_musik, "Fahrstuhlmusik", "Elevator music"),
}
# Lange Stücke werden gestreamt statt ganz in den Speicher geladen
GESTREAMT = {"aufzug_musik"}


def speichern(name: str, signal: np.ndarray) -> None:
    signal = signal / max(1e-9, np.max(np.abs(signal))) * 0.85
    daten = (signal * 32767).astype(np.int16)
    with tempfile.TemporaryDirectory() as tmp:
        wav = Path(tmp) / f"{name}.wav"
        with wave.open(str(wav), "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(daten.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "4",
                        str(ZIEL / f"{name}.ogg")], check=True)


def main() -> None:
    ZIEL.mkdir(parents=True, exist_ok=True)
    sounds_json = {}
    untertitel_de, untertitel_en = {}, {}
    alle = "--alle" in sys.argv
    for name, (fabrik, de, en) in GERAEUSCHE.items():
        if alle or not (ZIEL / f"{name}.ogg").exists():
            speichern(name, fabrik())
        eintrag = {"name": f"stinkendreich:{name}", "stream": True} if name in GESTREAMT else f"stinkendreich:{name}"
        sounds_json[name] = {"subtitle": f"stinkendreich.ton.{name}", "sounds": [eintrag]}
        untertitel_de[f"stinkendreich.ton.{name}"] = de
        untertitel_en[f"stinkendreich.ton.{name}"] = en
    # Musikanlage: Strom mit dem leeren Fabric-Klang; die Datei des Spielers liefert MusikSound
    sounds_json["musik"] = {"subtitle": "stinkendreich.ton.musik",
                            "sounds": [{"name": "fabric-sound-api-v1:empty", "stream": True}]}
    untertitel_de["stinkendreich.ton.musik"] = "Musik spielt"
    untertitel_en["stinkendreich.ton.musik"] = "Music plays"
    (ZIEL.parent / "sounds.json").write_text(json.dumps(sounds_json, indent=2) + "\n", encoding="utf-8")
    for datei, texte in (("de_de", untertitel_de), ("en_us", untertitel_en)):
        pfad = ZIEL.parent / "lang" / f"{datei}.json"
        vorhanden = json.loads(pfad.read_text(encoding="utf-8"))
        vorhanden.update(texte)
        pfad.write_text(json.dumps(dict(sorted(vorhanden.items())), indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{len(GERAEUSCHE)} Geräusche geschrieben")


if __name__ == "__main__":
    main()
