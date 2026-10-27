#!/usr/bin/env python3
"""Hai-Modell für das Paper-Plugin (Spieler ohne Mod sehen den Hai als Anzeige-Figur).

Die Maße und Textur-Stellen sind dieselben wie in der Mod (`client/hai/HaiModell.java`, Textur 128 × 64 aus
`tools/assets/hai.py`). Wie bei den Fahrzeugen der Server-Version bleibt der Modellraum der Figuren erhalten
(y nach unten, vorn ist −z, verkleinert auf ein Viertel, um 8 verschoben); die Anzeige stellt das Modell auf den Kopf.

Zwei Modelle, damit der Schwanz schlagen kann: `hai` (Rumpf, Kopf, Flossen; Ursprung in der Mitte des Rumpfs,
damit die Anzeige ihn dort neigen kann) und `hai_schwanz` (Schwanz mit Sichelflosse, Ursprung am Ende des Rumpfs,
um den er schlägt). Die Abstände braucht das Plugin (`hai/Haie.java`: MITTE, SCHWANZ).
Ausgabe: paper/src/main/resources/zusatz/assets/stinkendreich/{models,items}/…/polymer/hai*.json
"""
import json
import math
from pathlib import Path

WURZEL = Path(__file__).resolve().parents[2]
ZIEL = WURZEL / "paper/src/main/resources/zusatz/assets/stinkendreich"
BREITE, HOEHE = 128, 64
KLEIN = 0.25

# Teile: Name, Eltern, Drehpunkt (relativ zu den Eltern), Drehung (Bogenmaß), Kästen (uv, von, Maße)
TEILE = [
    ("rumpf", None, (0, 21, -7), (0, 0, 0), [((0, 0), (-5, -8, 0), (10, 9, 16))]),
    ("kopf", "rumpf", (0, 0, 0), (0, 0, 0), [((0, 26), (-4, -7, -8), (8, 7, 8))]),
    ("schnauze", "kopf", (0, 0, 0), (0, 0, 0), [((34, 26), (-3, -6, -12), (6, 5, 4))]),
    ("kiefer", "kopf", (0, -1, -1), (0.12, 0, 0), [((56, 26), (-3, 0, -11), (6, 2, 7))]),
    ("rueckenflosse", "rumpf", (0, -7, 4), (-0.55, 0, 0), [((54, 0), (-0.5, -8, 0), (1, 8, 6))]),
    ("flosse_links", "rumpf", (4, -1, 2), (0, -0.4, 0.55), [((70, 0), (0, 0, 0), (9, 1, 5))]),
    ("flosse_rechts", "rumpf", (-4, -1, 2), (0, 0.4, -0.55), [((70, 7), (-9, 0, 0), (9, 1, 5))]),
    ("schwanz", "rumpf", (0, 0, 16), (0, 0, 0), [((0, 42), (-3, -6, 0), (6, 6, 11))]),
    ("schwanzflosse", "schwanz", (0, -3, 10), (0, 0, 0), []),
    ("oben", "schwanzflosse", (0, 0, 0), (-0.75, 0, 0), [((36, 42), (-0.5, -11, 0), (1, 11, 4))]),
    ("unten", "schwanzflosse", (0, 0, 0), (0.7, 0, 0), [((48, 42), (-0.5, 0, 0), (1, 7, 3))]),
]
SCHWANZ = {"schwanz", "schwanzflosse", "oben", "unten"}
# Drehpunkt des Schwanzes im Modellraum (Rumpf + Schwanz): dort liegt im Schwanz-Modell der Ursprung
SCHWANZ_PUNKT = (0, 21, 9)
# Mitte des Rumpfs (Ursprung des Körper-Modells)
MITTE = (0, 17, 0)


def p(wert):
    return round(wert * KLEIN + 8, 4)


def flaeche(u1, v1, u2, v2):
    return {"uv": [round(u1 * 16 / BREITE, 4), round(v1 * 16 / HOEHE, 4), round(u2 * 16 / BREITE, 4),
                   round(v2 * 16 / HOEHE, 4)], "texture": "#hai"}


def flaechen(u, v, w, h, d):
    """Flächen wie ModelPart.Cube (gleiche Ecken wie FahrzeugAnzeige.flaechen in der Mod)."""
    l, m, n, o, q5 = u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    q, r, s = v, v + d, v + d + h
    return {
        "down": flaeche(l, q, m, r), "up": flaeche(m, r, n, q),
        "north": flaeche(m, s, l, r), "south": flaeche(q5, s, o, r),
        "west": flaeche(l, s, u, r), "east": flaeche(o, s, m, r),
    }


def modell(teile, verschiebung):
    nach_name = {t[0]: t for t in TEILE}
    elemente = []
    for name, eltern, punkt, dreh, kaesten in TEILE:
        if name not in teile:
            continue
        # Drehpunkt über alle Eltern aufsummieren (Eltern sind hier nie gedreht)
        px, py, pz = punkt
        e = eltern
        while e:
            _, e2, ep, _, _ = nach_name[e]
            px, py, pz = px + ep[0], py + ep[1], pz + ep[2]
            e = e2
        px, py, pz = px - verschiebung[0], py - verschiebung[1], pz - verschiebung[2]
        for (u, v), von, masse in kaesten:
            el = {
                "from": [p(px + von[0]), p(py + von[1]), p(pz + von[2])],
                "to": [p(px + von[0] + masse[0]), p(py + von[1] + masse[1]), p(pz + von[2] + masse[2])],
                "faces": flaechen(u, v, *masse),
            }
            if any(dreh):
                el["rotation"] = {"origin": [p(px), p(py), p(pz)], "x": round(math.degrees(dreh[0]), 3),
                                  "y": round(math.degrees(dreh[1]), 3), "z": round(math.degrees(dreh[2]), 3)}
            elemente.append(el)
    return {"textures": {"hai": "stinkendreich:item/polymer/hai", "particle": "stinkendreich:item/polymer/hai"},
            "elements": elemente}


def schreiben(name, daten):
    m = ZIEL / "models/item/polymer" / (name + ".json")
    i = ZIEL / "items/polymer" / (name + ".json")
    m.parent.mkdir(parents=True, exist_ok=True)
    i.parent.mkdir(parents=True, exist_ok=True)
    m.write_text(json.dumps(daten, separators=(",", ":")) + "\n")
    i.write_text(json.dumps({"model": {"type": "minecraft:model", "model": "stinkendreich:item/polymer/" + name}}) + "\n")


def main():
    alle = {t[0] for t in TEILE}
    schreiben("hai", modell(alle - SCHWANZ, MITTE))
    schreiben("hai_schwanz", modell(SCHWANZ, SCHWANZ_PUNKT))
    print("Hai-Modelle geschrieben:", ZIEL)


if __name__ == "__main__":
    main()
