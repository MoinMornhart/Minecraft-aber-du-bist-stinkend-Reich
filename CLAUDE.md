# Minecraft, aber du bist stinkend reich

@STAND.md

## Feste Entscheidungen (von Philip, 2026-10-24)

- **Repo:** öffentlich, `MoinMornhart/Minecraft-aber-du-bist-stinkend-Reich`. Nur private Nutzung.
- **„Verfasst mit Claude 🤖“: JA.** Als letzte Zeile (durch Leerzeile getrennt) in jeder Commit-Message, jedem PR, Issue und jeder Release-Note. Im README einmal als Fußzeile ganz unten.
- **Update-Funktion: JA.** Die Mod prüft beim Start die GitHub-Releases dieses Repos und bietet die passende neue .jar für die laufende Minecraft-Version an.
- **Loader: nur Fabric.** Eine .jar pro Minecraft-Version (1.21 bis zur neuesten), eine Codebasis mit Stonecutter, Code mit Mojangs offiziellen Namen (Mojmap).
- **Villa: eigener Entwurf im Stil der „Super Safe Modern Mansion“**, keine Kopie. Darf ins Repo und in die Releases. Sie bekommt alle Mechanismen des Originals (über 25) als eigene Blöcke/Block-Entities.
- **Villa erscheint in jeder neuen Welt** (egal welcher Welttyp) am Spawn, mit geebneter Fläche und angeblendetem Gelände. Bestehende Welten bleiben unberührt.
- **Reihenfolge der Bausteine:** Multi-Version-Setup → Villa (Feature 1) → Butler (Feature 2) → Bonzenauto (Feature 3).
- **Bilder:** Jeden sichtbaren Zwischenstand als echten Minecraft-Screenshot an Philip in den Chat schicken (SendUserFile) und auf die Fortschrittsseite legen.
- **Abschluss jeder Arbeitsrunde (Philip, 26.10.):** Immer als letzter Schritt, bevor eine Runde als fertig gemeldet wird: README auf den neuen Stand bringen und den Trailer neu aufnehmen, mit den Neuerungen (`tools/ingame/trailer_szenario.py`, `tools/ingame/video.py`), ins README einbinden (`docs/trailer.mp4`) und an Philip schicken.

## Harte Regeln

- **Die Original-Map (`/vorlage`) ist nur Referenz** für Stil und Mechanismen-Liste. Sie und alles direkt daraus Erzeugte (Welt, Regionen, Exporte, Analyse-Bilder) NIE committen und nie in eine .jar oder ein Release packen. Analyse-Ausgaben gehen nur nach `/vorlage/analyse/`. Die Nutzungsbedingungen von chamo572 verbieten einen Re-Upload.
- `.nbt`-Dateien sind nur im Ordner der eigenen Villa erlaubt (`src/main/resources/data/stinkendreich/structure/`), und nur, wenn sie aus unserem eigenen Generator (`tools/villa/`) stammen.
- **Anthropic-API-Key** nur in der lokalen Config-Datei des Spielers, nie im Code, nie im Repo, nie in Logs oder Screenshots.
- Der Butler baut nichts an der Villa ab und zerstört keine Spielerbauten.
- Code sauber und kommentiert; alle Einstellungen in der Config-Datei der Mod.
- Fortschrittsseite (Artifact „Stinkend Reich Fortschritt“) nach jedem fertigen Baustein und bei jedem neuen Zwischenergebnis mit derselben Datei neu veröffentlichen.
