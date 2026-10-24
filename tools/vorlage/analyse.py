#!/usr/bin/env python3
"""Analysiert die Original-Map in /vorlage, nur lokal und nur als Referenz.

Liest alle Command-Blöcke samt Befehlen, Schilder, Scoreboards, Datapacks und
markierte Entities aus und rendert Draufsicht und isometrische Ansichten.
Alles landet in /vorlage/analyse/ und wird nie committet (.gitignore).

Aufruf:  python3 tools/vorlage/analyse.py [vorlage-ordner]
"""
from __future__ import annotations

import json
import sys
import zipfile
from collections import Counter, defaultdict
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "lib"))
from anvil import Volumen, lies_entity_region, lies_nbt_datei, lies_region  # noqa: E402
from render import draufsicht, isometrisch  # noqa: E402

COMMAND_BLOECKE = ("minecraft:command_block", "minecraft:chain_command_block", "minecraft:repeating_command_block")
AUSLOESER = ("button", "lever", "pressure_plate", "tripwire", "observer", "daylight_detector", "target")

# Blöcke, die in einer normalen Welt von selbst vorkommen. Alles andere zählt als gebaut.
NATUERLICH = {
    "air", "cave_air", "void_air", "stone", "granite", "diorite", "andesite", "dirt", "coarse_dirt", "grass_block",
    "podzol", "mycelium", "bedrock", "water", "lava", "sand", "red_sand", "gravel", "clay", "sandstone", "red_sandstone",
    "coal_ore", "iron_ore", "gold_ore", "diamond_ore", "emerald_ore", "lapis_ore", "redstone_ore", "copper_ore",
    "deepslate", "tuff", "calcite", "grass", "short_grass", "tall_grass", "fern", "large_fern", "dandelion", "poppy",
    "azure_bluet", "oxeye_daisy", "cornflower", "blue_orchid", "allium", "lily_of_the_valley", "red_tulip",
    "orange_tulip", "white_tulip", "pink_tulip", "sunflower", "lilac", "rose_bush", "peony", "dead_bush", "sugar_cane",
    "kelp", "kelp_plant", "seagrass", "tall_seagrass", "snow", "snow_block", "ice", "packed_ice", "lily_pad",
    "vine", "brown_mushroom", "red_mushroom", "pumpkin", "melon", "cactus", "bubble_column", "obsidian",
    "oak_log", "spruce_log", "birch_log", "jungle_log", "acacia_log", "dark_oak_log",
    "oak_leaves", "spruce_leaves", "birch_leaves", "jungle_leaves", "acacia_leaves", "dark_oak_leaves",
    "infested_stone", "mossy_cobblestone", "spawner", "cobweb", "terracotta", "sweet_berry_bush", "bamboo",
}


def kurz(name: str) -> str:
    return name.split("[")[0].removeprefix("minecraft:")


def eigenschaften(name: str) -> dict[str, str]:
    if "[" not in name:
        return {}
    return dict(p.split("=", 1) for p in name.split("[", 1)[1].rstrip("]").split(","))


def finde_welt(vorlage: Path) -> Path:
    """Welt-Ordner (mit level.dat) finden; Zip-Archive vorher entpacken."""
    for zipdatei in sorted(vorlage.glob("*.zip")):
        ziel = vorlage / "entpackt" / zipdatei.stem
        if not ziel.exists():
            print(f"Entpacke {zipdatei.name} …")
            with zipfile.ZipFile(zipdatei) as z:
                for eintrag in z.infolist():
                    # Schutz vor Pfaden außerhalb des Zielordners
                    pfad = (ziel / eintrag.filename).resolve()
                    if not str(pfad).startswith(str(ziel.resolve())):
                        continue
                    z.extract(eintrag, ziel)
    kandidaten = [p.parent for p in vorlage.rglob("level.dat") if "analyse" not in p.parts]
    if not kandidaten:
        sys.exit(f"Keine Welt (level.dat) in {vorlage} gefunden.")
    # die Welt mit den meisten Regionsdateien nehmen
    return max(kandidaten, key=lambda w: len(list((w / "region").glob("*.mca"))))


def schildtext(be: dict) -> list[str]:
    zeilen = []
    for schluessel in ("Text1", "Text2", "Text3", "Text4"):
        if schluessel in be:
            zeilen.append(be[schluessel])
    for seite in ("front_text", "back_text"):
        if seite in be:
            zeilen.extend(be[seite].get("messages", []))
    return [z for z in zeilen if z and z not in ('{"text":""}', '""')]


def gruppieren(positionen: list[tuple[int, int, int]], abstand: int = 2) -> list[list[int]]:
    """Command-Blöcke, die höchstens `abstand` auseinander liegen, zu Gruppen zusammenfassen."""
    rest = set(range(len(positionen)))
    gruppen = []
    while rest:
        start = rest.pop()
        gruppe, offen = [start], [start]
        while offen:
            i = offen.pop()
            xi, yi, zi = positionen[i]
            nachbarn = [j for j in rest if max(abs(positionen[j][0] - xi), abs(positionen[j][1] - yi), abs(positionen[j][2] - zi)) <= abstand]
            for j in nachbarn:
                rest.discard(j)
                gruppe.append(j)
                offen.append(j)
        gruppen.append(sorted(gruppe, key=lambda k: positionen[k]))
    return sorted(gruppen, key=lambda g: positionen[g[0]])


def main() -> None:
    vorlage = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / "vorlage")
    welt = finde_welt(vorlage)
    aus = vorlage / "analyse"
    aus.mkdir(parents=True, exist_ok=True)
    print(f"Welt: {welt}")

    level = lies_nbt_datei(welt / "level.dat").get("Data", {})
    info = {
        "name": level.get("LevelName"),
        "version": (level.get("Version") or {}).get("Name"),
        "datenversion": level.get("DataVersion"),
        "spawn": [level.get("SpawnX"), level.get("SpawnY"), level.get("SpawnZ")],
        "spielmodus": level.get("GameType"),
        "spielregeln": level.get("GameRules", {}),
        "datapacks": level.get("DataPacks", {}),
    }

    # ---------- Durchlauf 1: Statistik, Command-Blöcke, Block-Entities ----------
    zaehler: Counter[str] = Counter()
    cb_bloecke: dict[tuple[int, int, int], str] = {}
    ausloeser: dict[tuple[int, int, int], str] = {}
    block_entities: dict[tuple[int, int, int], dict] = {}
    entities: list[dict] = []
    gebaut_min = np.array([10**9] * 3)
    gebaut_max = np.array([-10**9] * 3)
    y_min, y_max = 10**9, -10**9
    chunks = 0

    for mca in sorted((welt / "region").glob("*.mca")):
        for chunk in lies_region(mca):
            chunks += 1
            for be in chunk.block_entities:
                if "x" in be:
                    block_entities[(be["x"], be["y"], be["z"])] = be
            entities.extend(chunk.entities)
            for ab in chunk.abschnitte:
                anzahl = np.bincount(ab.indizes, minlength=len(ab.palette))
                for name, n in zip(ab.palette, anzahl):
                    if n:
                        zaehler[kurz(name)] += int(n)
                gebaut_idx = [i for i, n in enumerate(ab.palette) if kurz(n) not in NATUERLICH]
                if not gebaut_idx:
                    continue
                maske = np.isin(ab.indizes, gebaut_idx).reshape(16, 16, 16)
                ys, zs, xs = np.nonzero(maske)
                if len(xs):
                    lo = np.array([xs.min() + ab.cx * 16, ys.min() + ab.sy * 16, zs.min() + ab.cz * 16])
                    hi = np.array([xs.max() + ab.cx * 16, ys.max() + ab.sy * 16, zs.max() + ab.cz * 16])
                    gebaut_min = np.minimum(gebaut_min, lo)
                    gebaut_max = np.maximum(gebaut_max, hi)
                    y_min, y_max = min(y_min, lo[1]), max(y_max, hi[1])
                for i, name in enumerate(ab.palette):
                    k = kurz(name)
                    ist_cb = name.split("[")[0] in COMMAND_BLOECKE
                    ist_ausloeser = any(a in k for a in AUSLOESER)
                    if not (ist_cb or ist_ausloeser):
                        continue
                    for flach in np.nonzero(ab.indizes == i)[0]:
                        y, z, x = int(flach // 256), int((flach // 16) % 16), int(flach % 16)
                        pos = (ab.cx * 16 + x, ab.sy * 16 + y, ab.cz * 16 + z)
                        (cb_bloecke if ist_cb else ausloeser)[pos] = name

    entity_dir = welt / "entities"
    if entity_dir.exists():
        for mca in sorted(entity_dir.glob("*.mca")):
            entities.extend(lies_entity_region(mca))

    print(f"{chunks} Chunks, {len(cb_bloecke)} Command-Blöcke, {len(block_entities)} Block-Entities, {len(entities)} Entities")

    # ---------- Command-Blöcke mit Befehlen ----------
    positionen = sorted(cb_bloecke)
    eintraege = []
    for pos in positionen:
        name = cb_bloecke[pos]
        be = block_entities.get(pos, {})
        nah = [f"{kurz(n)}@{p}" for p, n in ausloeser.items() if max(abs(p[0] - pos[0]), abs(p[1] - pos[1]), abs(p[2] - pos[2])) <= 2]
        eintraege.append({
            "pos": list(pos),
            "typ": kurz(name),
            "richtung": eigenschaften(name).get("facing"),
            "bedingt": eigenschaften(name).get("conditional") == "true",
            "immer_aktiv": bool(be.get("auto", 0)),
            "befehl": be.get("Command", ""),
            "name": be.get("CustomName"),
            "ausloeser_in_der_naehe": nah,
        })
    (aus / "commandbloecke.json").write_text(json.dumps(eintraege, indent=1, ensure_ascii=False), encoding="utf-8")

    gruppen = gruppieren([tuple(e["pos"]) for e in eintraege])

    # ---------- Schilder, Bücher, Strukturblöcke ----------
    schilder = [{"pos": list(p), "text": schildtext(be)} for p, be in sorted(block_entities.items()) if schildtext(be)]
    (aus / "schilder.json").write_text(json.dumps(schilder, indent=1, ensure_ascii=False), encoding="utf-8")
    sonstige_be = Counter(be.get("id", "?") for be in block_entities.values())

    # ---------- Entities mit Tags oder Namen (oft Marker für Mechanismen) ----------
    markiert = []
    for e in entities:
        if e.get("Tags") or e.get("CustomName"):
            markiert.append({
                "id": e.get("id"),
                "pos": [round(v, 1) for v in e.get("Pos", [])],
                "tags": e.get("Tags", []),
                "name": e.get("CustomName"),
                "unsichtbar": bool(e.get("Invisible", 0)),
            })
    (aus / "entities.json").write_text(json.dumps(markiert, indent=1, ensure_ascii=False), encoding="utf-8")

    # ---------- Scoreboard und Datapacks ----------
    scoreboard = {}
    sb_pfad = welt / "data" / "scoreboard.dat"
    if sb_pfad.exists():
        daten = lies_nbt_datei(sb_pfad).get("data", {})
        scoreboard = {
            "ziele": [{"name": o.get("Name"), "kriterium": o.get("CriteriaName"), "anzeige": o.get("DisplayName")} for o in daten.get("Objectives", [])],
            "werte": [{"spieler": s.get("Name"), "ziel": s.get("Objective"), "wert": s.get("Score")} for s in daten.get("PlayerScores", [])],
            "teams": [t.get("Name") for t in daten.get("Teams", [])],
        }
    (aus / "scoreboard.json").write_text(json.dumps(scoreboard, indent=1, ensure_ascii=False), encoding="utf-8")
    funktionen = {}
    dp_dir = welt / "datapacks"
    if dp_dir.exists():
        for f in sorted(dp_dir.rglob("*.mcfunction")):
            funktionen[str(f.relative_to(dp_dir))] = f.read_text(encoding="utf-8", errors="replace")

    # ---------- Volumen und Bilder ----------
    rand = 12
    lo = gebaut_min - rand
    hi = gebaut_max + rand
    groesse = (int(hi[0] - lo[0] + 1), int(hi[1] - lo[1] + 1), int(hi[2] - lo[2] + 1))
    print(f"Gebauter Bereich: {gebaut_min.tolist()} bis {gebaut_max.tolist()} (Größe {groesse})")
    vol = Volumen((int(lo[0]), int(lo[1]), int(lo[2])), groesse)
    for mca in sorted((welt / "region").glob("*.mca")):
        for chunk in lies_region(mca):
            if chunk.cx * 16 + 16 < lo[0] or chunk.cx * 16 > hi[0] or chunk.cz * 16 + 16 < lo[2] or chunk.cz * 16 > hi[2]:
                continue
            for ab in chunk.abschnitte:
                vol.einfuegen(ab)
    np.savez_compressed(aus / "volumen.npz", bloecke=vol.bloecke, namen=np.array(vol.namen), ursprung=np.array(vol.ursprung))
    draufsicht(vol, [(p[0], p[2]) for p in positionen]).save(aus / "draufsicht.png")
    for r in range(4):
        isometrisch(vol, kante=4, blickrichtung=r).save(aus / f"iso_{r}.png")

    # ---------- Bericht ----------
    z = []
    z.append(f"# Analyse der Vorlage: {info['name']}\n")
    z.append(f"- Minecraft-Version: {info['version']} (DataVersion {info['datenversion']})")
    z.append(f"- Spawn: {info['spawn']}, Spielmodus: {info['spielmodus']}")
    z.append(f"- Chunks: {chunks}, gebauter Bereich: {gebaut_min.tolist()} bis {gebaut_max.tolist()}, Größe {groesse}")
    z.append(f"- Command-Blöcke: {len(eintraege)} in {len(gruppen)} Gruppen")
    z.append(f"- Datapacks: {info['datapacks']}")
    z.append(f"- Block-Entities: {dict(sonstige_be.most_common())}")
    z.append("\n## Häufigste gebaute Blöcke\n")
    for name, n in [(k, v) for k, v in zaehler.most_common() if k not in NATUERLICH][:80]:
        z.append(f"- {name}: {n}")
    z.append("\n## Scoreboard\n")
    z.append("```json\n" + json.dumps(scoreboard, indent=1, ensure_ascii=False) + "\n```")
    z.append("\n## Command-Block-Gruppen\n")
    for g_nr, gruppe in enumerate(gruppen, 1):
        erster = eintraege[gruppe[0]]["pos"]
        z.append(f"### Gruppe {g_nr} bei {erster} ({len(gruppe)} Blöcke)\n")
        for i in gruppe:
            e = eintraege[i]
            merkmale = [e["typ"], f"→{e['richtung']}"]
            if e["bedingt"]:
                merkmale.append("bedingt")
            if e["immer_aktiv"]:
                merkmale.append("immer aktiv")
            if e["ausloeser_in_der_naehe"]:
                merkmale.append("Auslöser: " + ", ".join(e["ausloeser_in_der_naehe"]))
            z.append(f"- `{e['pos']}` {' · '.join(merkmale)}\n  `{e['befehl']}`")
        z.append("")
    z.append("\n## Schilder\n")
    for s in schilder:
        z.append(f"- {s['pos']}: {' | '.join(s['text'])}")
    z.append("\n## Markierte Entities\n")
    for e in markiert:
        z.append(f"- {e['id']} {e['pos']} Tags={e['tags']} Name={e['name']}")
    if funktionen:
        z.append("\n## Datapack-Funktionen\n")
        for pfad, inhalt in funktionen.items():
            z.append(f"### {pfad}\n```\n{inhalt}\n```")
    (aus / "bericht.md").write_text("\n".join(z), encoding="utf-8")
    (aus / "welt.json").write_text(json.dumps(info, indent=1, ensure_ascii=False, default=str), encoding="utf-8")
    print(f"Fertig: {aus}")


if __name__ == "__main__":
    main()
