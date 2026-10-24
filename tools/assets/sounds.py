#!/usr/bin/env python3
"""Erzeugt die eigenen Geräusche der Mod per Synthese (keine fremden Aufnahmen).

Schreibt Ogg-Vorbis-Dateien (mono, 44,1 kHz) nach assets/stinkendreich/sounds/ und die
passende sounds.json. Braucht ffmpeg mit libvorbis.

Aufruf: python3 tools/assets/sounds.py
"""
from __future__ import annotations

import json
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


GERAEUSCHE = {
    "piep_ok": (piep_ok, "Piepen", "Beep"),
    "piep_fehler": (piep_fehler, "Fehlerton", "Error buzz"),
    "sirene": (sirene, "Alarmsirene heult", "Siren wails"),
    "gong": (gong, "Aufzug-Gong", "Elevator chime"),
    "klingel": (klingel, "Türklingel", "Doorbell"),
    "tresor": (tresor, "Schwere Tür", "Heavy door"),
}


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
    for name, (fabrik, de, en) in GERAEUSCHE.items():
        speichern(name, fabrik())
        sounds_json[name] = {"subtitle": f"stinkendreich.ton.{name}", "sounds": [f"stinkendreich:{name}"]}
        untertitel_de[f"stinkendreich.ton.{name}"] = de
        untertitel_en[f"stinkendreich.ton.{name}"] = en
    (ZIEL.parent / "sounds.json").write_text(json.dumps(sounds_json, indent=2) + "\n", encoding="utf-8")
    for datei, texte in (("de_de", untertitel_de), ("en_us", untertitel_en)):
        pfad = ZIEL.parent / "lang" / f"{datei}.json"
        vorhanden = json.loads(pfad.read_text(encoding="utf-8"))
        vorhanden.update(texte)
        pfad.write_text(json.dumps(dict(sorted(vorhanden.items())), indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{len(GERAEUSCHE)} Geräusche geschrieben")


if __name__ == "__main__":
    main()
