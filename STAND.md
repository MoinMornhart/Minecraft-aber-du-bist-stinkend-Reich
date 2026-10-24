# Stand (Übergabe)

_Zuletzt aktualisiert: 2026-10-24_

## Erledigt

- Repo-Grundgerüst: `CLAUDE.md`, `STAND.md`, `README.md`, `.gitignore` (sperrt Vorlage und `.nbt`), Cloud-Start-Hook für Philips Skills.
- Recherche Versionen: Releases seit 1.21 sind 1.21 bis 1.21.11 (Java 21, obfuskiert) und 26.1, 26.1.1, 26.1.2, 26.2, 26.3 (Java 25, ohne Obfuskation). 26.4 ist im Snapshot. Die genaue Liste wird gegen das Mojang-Versionsmanifest geprüft, sobald das Netz frei ist.

## Offen / blockiert

- **Netzwerk der Cloud-Umgebung** sperrt Mojang-, Fabric-, NeoForge-, Stonecutter- und Modrinth-Server. Ohne Freigabe kein Gradle-Build und kein Ingame-Test.
- **Vorlage-Map** fehlt noch in `/vorlage`.
- **Loader-Entscheidung** (Fabric / NeoForge / beide) steht noch aus.
- **Villa-Daten und öffentliche Releases:** Weg, wie die Villa zum Spieler kommt, ohne dass sie in einer öffentlichen .jar steckt, muss Philip bestätigen.

## Nächster Schritt

Baustein 1: Multi-Version-Setup mit Stonecutter, sobald Netz und Loader-Entscheidung da sind.
