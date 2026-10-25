# Stand (Übergabe)

_Zuletzt aktualisiert: 2026-10-25, 17:00 Uhr_

## Erledigt

- **Baustein 1: Multi-Version-Setup.** Stonecutter 0.9.8 mit 17 Versionen (1.21 bis 1.21.11 mit Java 21, 26.1 bis 26.3 mit Java 25), eine .jar je Version, Mojang-Namen über `loom-back-compat`. Alle Versionsunterschiede stecken in `Kompat`/`KompatClient` oder in `//?`-Bedingungen.
- **Baustein 2: Villa.** Eigener Entwurf (`tools/villa/villa.py` → `villa.nbt`), entsteht in jeder neuen Welt am Spawn mit geebnetem und angeblendetem Gelände. 29 Mechanismen als eigene Blöcke (Sicherheitsstufen 0–3, Laser, Bewegungsmelder, Sirenen, Zugangskarte, Kartenleser, Tastenfeld, Tore, Rollläden, Schiebetüren, Aufzug zu allen Etagen und in den Bunker, Schmuck-Tresor mit Fallen und Entschärfknöpfen, Kameras mit Monitoren, Geheimgang, Fluchttunnel, Poolabdeckung, Whirlpool, Kamin …). **Komplett-Test in allen 17 Versionen bestanden** (25.10., Bilder je Version identisch). Ab 1.21.5 fiel beim Ebnen Laubstreu ab; das Aufräumen nach dem Bau entfernt jetzt alle herrenlosen Pflanzenreste.
- **Baustein 3: Butler.** Spieltest in 26.3 bestanden: Auftrag „hol mir 16 Eisen“ im Chat → Tunnel graben, Erz abbauen, im Hochofen schmelzen, liefern in gut 3 Minuten; „sortier die Truhen“ legt jede Sorte in ihre Stammtruhe.
  - `butler/ButlerEntity`: unverwundbar (nur /kill), folgt oder wartet, öffnet Holztüren, Taschen mit 27 Plätzen, speichert Aufträge mit. `gehZu` zählt Teilwege als Fortschritt; `hinkommen` springt bei Öfen, Truhen und Werkbank notfalls daneben.
  - Aufträge (`butler/auftrag/`): Erz mit Tunnelbau (`SammelAuftrag`, `Tunnel`, `Abbau`), Bäume fällen und neu pflanzen (`HolzAuftrag`), jagen (`JagdAuftrag`), echte Öfen (`OfenAuftrag`), herstellen (`HerstellAuftrag`), Speisekammer (`VorratAuftrag`), Truhen sortieren (`SortierAuftrag`), liefern (`LieferAuftrag`), kommen/heim (`GehAuftrag`).
  - `Planer` zerlegt Bestellungen mehrstufig, `Sprache` versteht deutsche Sätze, `ButlerBefehle`: `/butler`, `/butler rufen`, `/butler truhe`, Chat „Butler, …“, Menü per Rechtsklick.
  - Schutz (`Schutz`, `SpielerBloecke` + Mixin in `BlockItem.placeBlock`): nichts in der Villa, nichts Gesetztes, nur natürliche Blöcke.
  - Optional Claude (`ButlerKI`): offizielle Anthropic-Java-Bibliothek 2.75.0 (per `include` in der .jar), Modell aus der Config (Standard `claude-opus-5-5`), Werkzeuge = Butler-Befehle. Key nur aus `config/stinkendreich.json`.
- **Baustein 4: Bonzenauto.** Spieltest in 26.3 bestanden: Garage, einsteigen (Fahrer schaut in Fahrtrichtung, Kamera dahinter), Gas mit Trägheit, Kurve, Drift mit Reifenqualm, Scheinwerfer (Taste **G**, L ist in Minecraft „Fortschritte“), Hupe, drei Kameraarten, acht Lackfarben, vier Sitze.
- **Update-Funktion** (`update/UpdatePruefer`, `client/update/UpdateKlient`), **README**, **Release-Ablauf** (`.github/workflows/release.yml`: bei Tag `v*` oder neuer Nummer in `.github/release-version` alle 17 .jar bauen und anhängen).
- **Alle 17 Versionen im Spiel getestet:** Villa-Komplett-Test und Rundgang (`rundgang.txt`: Villa, Butler-Auftrag „hol 4 Eisen in die Truhe“ bis zur Lieferkiste, Auto, Poolabdeckung) überall bestanden.
- Werkzeuge: `tools/ingame/test.sh` (Spieltest mit Xvfb/Mesa; Aktionen u. a. `server`, `klient`, `halte`, `druecke`/`loslassen`, `merke`/`bis`), `tools/ingame/alle.sh` (alle Versionen nacheinander), Szenarien in `tools/ingame/szenarien/` (`villa_komplett`, `butler`, `auto`, `rundgang`), `tools/mcapi.sh` (Minecraft-Klassen je Version nachschlagen).

## Version 0.2 (in Arbeit, Stand 25.10. abends)

- Fertig und in 26.3 im Spiel getestet: Fehler aus Philips Bildern, neue Türen, Aufzugsfahrt, Glow-up der Räume,
  4 Partybereiche, Musik (DJ-Pult, Spotify), Tiefgarage mit Rampe, Fuhrpark und Drehteller (`auto/DrehtellerEntity`),
  Fahrzeug-Baukasten `tools/assets/fahrzeuge.py` (alle Autos, runde Räder, Textur 4-fach), XXL-Fernseher
  (`fernseher/`), Büro-Monitore (`Winkel` links/rechts/gerade/wand), Penthouse als privater Schlafbereich.
- Möbel (`tools/assets/deko.py`, `moebel/`): XXL-Kingsize-Bett (`KingsizeBettBlock`, echtes Bett, Spalten verbinden
  sich), Bettende/Bettbank/Sofas als `VerbundDekoBlock`, Nachttisch, Tischlampe, Kinotisch, Strandliegen,
  Sonnenschirm, Loungetisch; Hinsetzen über `SitzEntity`. Kurztest auch in 1.21 bestanden.
- Butler als Chauffeur (`butler/Chauffeur`, `FahrAuftrag`, Orte merken) und Pilot (`butler/Pilot`, `FlugAuftrag`),
  Luxus-Hubschrauber (`auto/HubschrauberEntity`) auf dem Landeplatz.
- OP-Ausrüstung (`ausruestung/`): Agenten-Anzug als echte Rüstung (`AgentRuestung`, je Version anders angelegt:
  bis 1.21.1 registriertes Material mit Schichten, 1.21.2–1.21.4 `ArmorItem`, ab 1.21.5 `humanoidArmor`),
  Texturen aus `tools/assets/ausruestung.py`, Kräfte in `AgentKraefte` (Nachtsicht + leuchtende Gegner, Resistenz,
  Tempo, Sprung, kein Fallschaden, Set: Stärke + Regeneration), Waffen `AgentWaffe` (Pistole, Laseruhr, Geldkanone,
  Sofort-Treffer ohne Blockschaden), Waffenkammer mit 6 verzauberten Ständern (`Waffenkammer`, Marke „ruestung“).
  Test: `tools/ingame/szenarien/agent.txt` und `agent_waffen.txt`.
- Achtung bei Testfotos: Kamera nicht in Kronleuchter stellen (die Kristalle sahen aus wie schwebende Flächen).
  Wüstenzombies geben Hunger (grüne Ringe im Bild) – für Waffenbilder Diener (Vindicator) nehmen.
- README-Fotos neu (`fotos.txt` + `fotos2.txt`, mit `warte 30` vor F1, sonst kommt F1 beim Laden nicht an).
- Video-Werkzeug: `fahrt` im Testmodus (Kamerafahrt über Wegpunkte), `video start/stopp` und `kapitel` in
  `test.sh`, `tools/ingame/video.py` (Titel, Kapitel, Musik), Drehbuch aus `tools/ingame/video_szenario.py`.
  Für flüssige Bilder keinen zweiten Spieltest parallel laufen lassen (sonst nur ~6 Bilder/s).
- Rundgang aller Versionen (mit Agent und Waffenkammer): 1.21, 1.21.2, 1.21.3, 1.21.4 OK. 1.21.1 brach ab, weil
  `test.sh` während des Laufs geändert wurde (nie während eines Laufs ändern!) – muss neu. Rest ab 1.21.5 offen.
- **Release 0.2.0 angestoßen** (Philip: „gleich raus und weiter testen“): `mod.version` und `.github/release-version`
  auf 0.2.0, Neuigkeiten in `.github/neu.md`. Die restlichen Versionen laufen im Spiel weiter; Fehler → 0.2.1.
- Truhe direkt neben dem Agenten-Ständer in der Waffenkammer entfernt (Philips Bild), Inhalt in die Nachbartruhe.
- Offen für 0.2: restliche Versionen testen, Video an Philip.

## Version 0.3 (geplant, Philips Wünsche vom 25.10.)

- **Straßennetz:** Vor der ersten Fahrt zu einem Ziel baut der Chauffeur eine echte Straße (Kreisel vor der Villa,
  Abzweigungen). Ziele: Mine, Endportal, nächstes Dorf, Netherportal und alle gemerkten Orte; das Netz wächst
  weiter wie ein echtes Straßennetz und ist selbst befahrbar. Nichts an Villa oder Spielerbauten abreißen.
- **Handy:** Butler anrufen (kommt wirklich mit Limo oder Hubschrauber, kurze Wartezeit), Aufträge geben,
  Lieferservice (z. B. Tiere).
- **Mitarbeiter:** übers Handy bestellen → Mitarbeiterkiste ins Inventar → platzieren → Mitarbeiter erscheinen;
  jeder bekommt eine Aufgabe (Diamanten, Kohle, Eisen …), die Beute landet in der Kiste; James oder Philip holt ab.

## Release

- **v0.1.0 ist veröffentlicht:** https://github.com/MoinMornhart/Minecraft-aber-du-bist-stinkend-Reich/releases/tag/v0.1.0 (17 .jar, je ~45 MB wegen der mitgelieferten Anthropic-Bibliothek).
- Tags lassen sich aus der Cloud-Sitzung nicht pushen. Deshalb startet der Release-Ablauf auch, wenn sich `.github/release-version` auf einem Branch ändert (Nummer muss zu `mod.version` passen); er legt Tag und Release selbst an.
- Update-Funktion gegen das echte Release getestet: eine Testfassung 0.0.9 bietet 0.1.0 im Chat an, die Download-Adresse liefert genau die Datei aus dem Release.
- Nach dem Release behoben (kommt mit 0.1.1): Die Mod-Version kommt jetzt aus `fabric.mod.json`. Vorher stand sie für die aktive Stonecutter-Version (26.3) fest im Quelltext und wäre bei einer neuen Nummer nicht mitgewandert.

## Offen

- Die Arbeit liegt auf dem Branch `claude/elegant-darwin-00k3xq`; ob sie in `main` kommt (Pull Request), entscheidet Philip.
- Claude-Anbindung des Butlers (`ButlerKI`) ist nur übersetzt, nicht mit echtem Schlüssel getestet.
- Original-Map fehlt weiterhin in `/vorlage` (nur zum Abgleich der Mechanismen-Liste, nie committen).
- Feinschliff: Innenräume heller; Kamera im Butler-Spieltest im Tunnel zu nah.
- Zweiter Arbeitsordner `/home/user/sr-test` (Git-Worktree) für parallele Spieltests; kann weg, wenn er nicht mehr gebraucht wird.

## Nächster Schritt

Auf Philips Rückmeldung warten (Pull Request nach `main`? Wünsche zum Feinschliff?). Für ein neues Release: `mod.version` und `.github/release-version` auf 0.1.1 setzen und pushen.
