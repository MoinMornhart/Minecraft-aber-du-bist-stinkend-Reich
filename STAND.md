# Stand (Übergabe)

_Zuletzt aktualisiert: 2026-10-25_

## Erledigt

- **Baustein 1: Multi-Version-Setup.** Stonecutter 0.9.8 mit 17 Versionen (1.21 bis 1.21.11 mit Java 21, 26.1 bis 26.3 mit Java 25), eine .jar je Version, Mojang-Namen über `loom-back-compat`. Alle Versionsunterschiede stecken in `Kompat`/`KompatClient` oder in `//?`-Bedingungen.
- **Baustein 2: Villa.** Eigener Entwurf (`tools/villa/villa.py` → `villa.nbt`), entsteht in jeder neuen Welt am Spawn mit geebnetem und angeblendetem Gelände. 29 Mechanismen als eigene Blöcke (Sicherheitsstufen 0–3, Laser, Bewegungsmelder, Sirenen, Zugangskarte, Kartenleser, Tastenfeld, Tore, Rollläden, Schiebetüren, Aufzug zu allen Etagen und in den Bunker, Schmuck-Tresor mit Fallen und Entschärfknöpfen, Kameras mit Monitoren, Geheimgang, Fluchttunnel, Poolabdeckung, Whirlpool, Kamin …). In 26.3 vollständig im Spiel getestet; Komplett-Test aller Versionen lief am 25.10. nachts (Ergebnis: `build/ingame/ergebnis.txt` im Test-Worktree).
- **Baustein 3: Butler (Code fertig, Spieltest läuft).**
  - `butler/ButlerEntity`: unverwundbar (nur /kill), folgt oder wartet, öffnet Holztüren, Taschen mit 27 Plätzen, speichert Aufträge mit.
  - Aufträge (`butler/auftrag/`): Erz abbauen mit Tunnelbau (`SammelAuftrag`, `Tunnel`, `Abbau`), Bäume fällen und neu pflanzen (`HolzAuftrag`), jagen (`JagdAuftrag`, schont Jungtiere, Namens- und Leinentiere, lässt immer zwei übrig), echte Öfen benutzen (`OfenAuftrag`), herstellen (`HerstellAuftrag`), Speisekammer (`VorratAuftrag`), Truhen sortieren (`SortierAuftrag`), liefern (`LieferAuftrag`), kommen/heim (`GehAuftrag`).
  - `Planer` zerlegt Bestellungen mehrstufig (Fackeln → Stöcke + Kohle → Bretter → Holz), `Sprache` versteht deutsche Sätze, `ButlerBefehle`: `/butler`, `/butler rufen`, `/butler truhe`, Chat „Butler, …“, Menü per Rechtsklick.
  - Schutz (`Schutz`, `SpielerBloecke` + Mixin in `BlockItem.placeBlock`): nichts in der Villa, nichts Gesetztes, nur natürliche Blöcke.
  - Optional Claude (`ButlerKI`): offizielle Anthropic-Java-Bibliothek 2.75.0 (per `include` in der .jar), Modell aus der Config (Standard `claude-opus-5-5`, Aufwand niedrig, Ersatzmodell bei Ablehnung), Werkzeuge = Butler-Befehle. Key nur aus `config/stinkendreich.json`.
  - Eigenes Modell (Frackschöße, Fliege, Haar) und Textur aus `tools/assets/butler.py`, Butler-Glöckchen als Item, eigener Ton.
  - Villa hat dafür jetzt Speisekammer (Fass mit Essen), Kohlenfässer, Hochöfen in Küche und Garage und eine Lieferkiste in der Halle (Markierungen `vorrat`, `brennstoff`, `ofen`, `lieferkiste`).
- Werkzeuge: `tools/ingame/test.sh` (Spieltest mit Xvfb/Mesa, Szenarien in `tools/ingame/szenarien/`, `PORT=` für parallele Läufe), `tools/mcapi.sh` (Minecraft-Klassen je Version nachschlagen).

## Offen

- Butler-Spieltest auswerten (Szenario `butler.txt`, Version 26.3), danach alle Versionen testen.
- Baustein 4: Bonzenauto (Entity, Modell, Sounds, Fahrphysik, Drift, Licht, Hupe, Kamera, Farben, Sitze; Platz in der Garage = Markierung `auto`).
- Update-Funktion (GitHub-Releases), README mit Screenshots, Releases pro Version.
- Original-Map fehlt weiterhin in `/vorlage` (nur zum Abgleich der Mechanismen-Liste, nie committen).

## Nächster Schritt

Butler-Test auswerten und Fehler beheben, dann Bonzenauto.
