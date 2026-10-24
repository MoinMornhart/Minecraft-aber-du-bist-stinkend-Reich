# Stand (Übergabe)

_Zuletzt aktualisiert: 2026-10-24_

## Erledigt

- Repo-Grundgerüst: `CLAUDE.md`, `STAND.md`, `README.md`, `.gitignore`, Cloud-Start-Hook für Philips Skills.
- Entscheidungen (siehe `CLAUDE.md`): öffentliches Repo, Update-Funktion, „Verfasst mit Claude 🤖“, nur Fabric, **eigene Villa im Stil des Originals**, Villa in jeder neuen Welt.
- Recherche Versionen: 1.21 bis 1.21.11 (Java 21) und 26.1, 26.1.1, 26.1.2, 26.2, 26.3 (Java 25, unverschleiert). 26.4 im Snapshot.
- `docs/BAUPLAN.md`: technischer Plan für alle Bausteine.
- `tools/lib/anvil.py`, `tools/lib/render.py`, `tools/vorlage/analyse.py`: liest die Original-Map (1.13+ Formate) aus, listet alle Command-Blöcke mit Befehlen, Schilder, Scoreboards, Entities und rendert Draufsicht und Isometrie. Getestet mit `tools/tests/test_anvil.py` und einer künstlichen 1.15-Welt.
- JDK 25 in der Cloud-Umgebung per apt installiert (für 26.x).

## Offen / blockiert

- **Netzwerk der Cloud-Umgebung** sperrt Mojang-, Fabric-, Stonecutter- und Modrinth-Server. Ohne Freigabe kein Gradle-Build und kein Ingame-Test.
- **Original-Map** fehlt noch in `/vorlage` (Philip lädt die Zip im Chat hoch oder schickt den Link). Danach: `python3 tools/vorlage/analyse.py`.

## Nächster Schritt

Baustein 1: Multi-Version-Setup mit Stonecutter, sobald das Netz frei ist. Parallel: Map analysieren, sobald sie da ist.
