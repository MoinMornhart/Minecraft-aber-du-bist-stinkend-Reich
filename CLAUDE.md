# Minecraft, aber du bist stinkend reich

@STAND.md

## Feste Entscheidungen (von Philip, 2026-10-24)

- **Repo:** öffentlich, `MoinMornhart/Minecraft-aber-du-bist-stinkend-Reich`. Nur private Nutzung.
- **„Verfasst mit Claude 🤖“: JA.** Als letzte Zeile (durch Leerzeile getrennt) in jeder Commit-Message, jedem PR, Issue und jeder Release-Note. Im README einmal als Fußzeile ganz unten.
- **Update-Funktion: JA.** Die Mod prüft beim Start die GitHub-Releases dieses Repos und bietet die passende neue .jar für die laufende Minecraft-Version an.
- **Reihenfolge der Bausteine:** Multi-Version-Setup → Villa (Feature 1) → Butler (Feature 2) → Bonzenauto (Feature 3).

## Harte Regeln

- **`/vorlage` und alle daraus erzeugten Dateien (Welt, Regionen, `.nbt`-Strukturen) NIE committen und NIE in eine öffentliche .jar oder ein GitHub-Release packen.** Die Nutzungsbedingungen von chamo572 verbieten einen Re-Upload. Die `.gitignore` sperrt `*.nbt`, `*.mca`, `level.dat`, `/vorlage/` und `/villa-export/`. Vor jedem Commit `git status` prüfen.
- **Anthropic-API-Key** nur in der lokalen Config-Datei des Spielers, nie im Code, nie im Repo, nie in Logs oder Screenshots.
- Der Butler baut nichts an der Villa ab und zerstört keine Spielerbauten.
- Code sauber und kommentiert; alle Einstellungen in der Config-Datei der Mod.
- Fortschrittsseite (Artifact „Stinkend Reich Fortschritt“) nach jedem fertigen Baustein und bei jedem neuen Zwischenergebnis mit derselben Datei neu veröffentlichen.
