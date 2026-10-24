# Bauplan

Technischer Plan für „Minecraft, aber du bist stinkend reich“. Mod-ID: `stinkendreich`, Paket: `de.moinmornhart.stinkendreich`.

## 1. Multi-Version-Setup

| Punkt | Entscheidung |
|---|---|
| Loader | nur Fabric, mit Fabric API |
| Versionen | jede Release-Version ab 1.21 bis zur neuesten (Stand 24.10.2026: 17 Versionen bis 26.3) |
| Werkzeug | Stonecutter: eine Codebasis in `src/`, ein Build-Knoten pro Version unter `versions/<mc>/`, je eine `.jar` |
| Namen im Code | Mojangs offizielle Namen (Mojmap). 1.21.x über `loom.officialMojangMappings()`, ab 26.1 ist das Spiel ohnehin unverschleiert. So ist der Quelltext für alle Versionen gleich benannt. |
| Java | 21 für 1.21.x, 25 für 26.x (Gradle-Toolchains) |
| Loom | `fabric-loom` für 1.21.x, `net.fabricmc.fabric-loom` (ohne Mappings, `implementation` statt `modImplementation`) ab 26.1 |
| Unterschiede zwischen Versionen | Stonecutter-Kommentare (`//? if >=1.21.2 {`) direkt im Code, nur wo sich die Minecraft-API wirklich ändert (z. B. Render-States ab 1.21.2, Item-Modelle ab 1.21.4) |
| Dateiname | `stinkendreich-<modversion>+mc<mcversion>.jar` |

## 2. Villa (Feature 1)

**Eigener Entwurf im Stil der „Super Safe Modern Mansion“.** Das Original liegt nur lokal in `/vorlage` und dient als Referenz für Stil, Raumaufteilung und die Liste der Mechanismen. Es wird nie kopiert, committet oder ausgeliefert.

### Ablauf
1. `tools/vorlage/analyse.py` liest die Original-Map aus (Command-Blöcke mit Befehlen, Schilder, Scoreboards, Datapacks) und rendert Draufsichten. Ausgabe nur nach `/vorlage/analyse/`.
2. Daraus entsteht die Mechanismen-Liste (über 25) mit Beschreibung, was jeder Mechanismus im Original tut.
3. `tools/villa/` erzeugt die eigene Villa als Strukturdateien (`.nbt`, DataVersion von 1.21). Spätere Minecraft-Versionen rüsten sie beim Laden selbst über den DataFixer auf.
4. Die Mechanismen stecken als eigene Mod-Blöcke direkt in der Struktur, ohne Command-Blöcke.

### Platzierung in jeder neuen Welt
- Eigene Struktur mit eigener Platzierungsregel: Sie entsteht genau einmal, im Chunk des Welt-Spawns, den Minecraft vorab aus dem Klima-Sampler berechnet.
- Geländeanpassung über Minecrafts „Beardifier“ (`terrain_adaptation`): Unter dem Haus wird aufgefüllt, darüber freigeräumt, mit weichem Übergang ins Gelände. Das passiert schon bei der Weltgenerierung, also ohne abgehackte Bäume oder Kanten.
- Teile: Haus mit Grundstück (`beard_box`), Garten (`beard_thin`), Untergrundbasis (ohne Anpassung, eigene Wände).
- Nach dem Erzeugen wird der Welt-Spawn auf den Eingang der Villa gesetzt.
- Bestehende Welten bleiben unberührt, weil ihr Spawn-Chunk schon erzeugt ist.
- Flachwelten: Platzierung beim ersten Betreten auf der flachen Oberfläche.

### Mechanismen (vorläufig, Liste wird nach der Analyse vollständig)
| Mechanismus | Umsetzung |
|---|---|
| Sicherheitssystem mit Stufen | Block „Sicherheitszentrale“ mit GUI, Stufen 0 bis 3, steuert alle verbundenen Sicherheitsblöcke im Villa-Netz |
| Laser | Laser-Sender und -Empfänger, sichtbarer Strahl (Block-Entity-Renderer), lösen je nach Stufe Alarm, Fallen oder Schaden aus |
| Zugangskarte | Item „Zugangskarte“ plus Kartenleser-Block, funktioniert für jeden Spieler mit Karte |
| Passwort-Tür | Tastenfeld-Block mit GUI, Code in der Config bzw. im Block einstellbar |
| Aufzüge | Aufzug-Steuerung mit Etagenwahl, Kabine fährt sichtbar, alle Etagen und Untergrundbasis, für jeden Spieler |
| Schmuck-Tresor | Tresortür, Fallen beim falschen Öffnen, versteckte Entschärf-Knöpfe |
| Überwachungsraum | Kamera-Blöcke und Monitor; Rechtsklick auf den Monitor schaltet die Ansicht auf die Kameras |

## 3. Butler (Feature 2)

- Eigene Entity (`PathfinderMob`), eigenes Modell im Anzug mit Fliege, spawnt in der Villa.
- Modi: folgen, zu Hause bleiben, Auftrag ausführen.
- Aufträge als Warteschlange: Erz abbauen (z. B. „32 Eisen“), Holz, Kohle, Essen beschaffen, im Ofen schmelzen oder kochen, Truhen sortieren. Abgabe in eine Truhe oder direkt an den Spieler.
- Schutz: Der Butler baut nie Blöcke im Villa-Bereich ab und nie Blöcke, die ein Spieler gesetzt hat (eigene Markierung pro Chunk).
- Bedienung ohne API-Key: Befehl `/butler …` und eigenes Menü.
- Optional Claude: Chat mit dem Butler über die Anthropic-API mit Tool-Use (`mine`, `collect`, `smelt`, `cook`, `sort`, `follow`, `stay`, `come`, `status`). Der API-Key steht nur in `config/stinkendreich.json`.

## 4. Bonzenauto (Feature 3)

- Eigene Entity mit eigenem Modell, eigenen Texturen und eigenen Sounds (Motor mit Drehzahl, Hupe, Start), steht in der Garage.
- Fahrphysik: Motorkraft, Luft- und Rollwiderstand, Bremse, Lenkung mit Trägheit, Drift per Handbremse, Steigungen und Stufen.
- Scheinwerfer (echtes Licht vor dem Auto), Hupe, Kamera-Modi (Verfolger, Cockpit, Motorhaube).
- Farbe wechseln mit Farbstoff, bis zu vier Sitze.

## 5. Allgemein

- **Config:** `config/stinkendreich.json`, alle Einstellungen mit Erklärung, wird beim ersten Start angelegt.
- **Update-Funktion:** Beim Start fragt die Mod die GitHub-Releases ab, findet die `.jar` für die laufende Minecraft-Version und bietet sie im Titelbildschirm an. Nach Zustimmung wird sie heruntergeladen; die alte `.jar` wird nach dem Beenden des Spiels entfernt.
- **Tests:** Jede Version startet einmal im Spiel (ohne Grafikkarte über Xvfb und Mesa), lädt eine neue Welt, macht Screenshots von Villa, Butler und Auto und beendet sich. Die Bilder gehen an Philip und auf die Fortschrittsseite.
