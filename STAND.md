# Stand (Übergabe)

_Zuletzt aktualisiert: 2026-10-26, 12:40 Uhr (Philip ist wach)_

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
- Rundgang aller Versionen (mit Agent und Waffenkammer): **alle 17 Versionen OK** (1.21.1 nach Abbruch neu
  gelaufen; nie `test.sh` während eines Laufs ändern!).
- **Release 0.2.0 angestoßen** (Philip: „gleich raus und weiter testen“): `mod.version` und `.github/release-version`
  auf 0.2.0, Neuigkeiten in `.github/neu.md`. Die restlichen Versionen laufen im Spiel weiter; Fehler → 0.2.1.
- Truhe direkt neben dem Agenten-Ständer in der Waffenkammer entfernt (Philips Bild), Inhalt in die Nachbartruhe.
- Video an Philip geschickt (`villa_rundgang.mp4`).

## Version 0.2.1 (in Arbeit, Fehler aus Philips 20 Bildern)

- Erledigt und in 26.3 im Spiel geprüft (`tools/ingame/szenarien/bugs021.txt`): Personalküche mit Wand und
  Gartentür, Tresor wie auf Philips Bild (Diamantberg, Tresor-Blöcke aus dem Spiel), Garagendurchgang 2×3 mit
  Schiebetür, Balkon-/Penthouse-Doppeltür ohne Loch, Privattür (`PrivattuerBlock`, nur Bewohner, bleibt offen),
  Clubtür, Weinlounge als Weinkeller, VIP-Sofa auf dem Podest, DJ-Pult vorn, Wohnzimmer-U, Kamin symmetrisch,
  Penthouse-Lounge und Ostecke, Bunkerlager, Lava-Graben (`Graben`, Marken „graben“ und „haus“, zweistufiger
  Tausch gegen Obsidian), G-Klasse kommt die Rampe hinauf (`RAMPE_OFFEN_AB` in villa.py), Autos mit Maßstab
  (`Autotyp.massstab`), Tische `tafel_marmor`/`tafel_holz` (moebel.tisch), Sofas ohne Spalt.
- Rutsche (`Rutsche`, Marke „rutsche“): schiebt jetzt richtig (in 1 s unten im Pool). Wichtig: Bewegung an den
  Spieler geht ab 26.3 über `syncVelocity` (bis 26.2 `hurtMarked`), Hilfe `Kompat.bewegungSenden`.
- Waffenhaltung endgültig (Stand 0.2.2, Philip: „Griff in der Hand wie beim Schwert“): Mit Pistole/Geldkanone
  nimmt die Spielerfigur die Zielhaltung ein (`mixin/client/ArmHaltungMixin`: Armhaltung CROSSBOW_HOLD; bis 1.21.1
  `PlayerRenderer.getArmPose(Spieler, Hand)`, 1.21.2–1.21.3 Merkmal `holdsChargedCrossbow` in `extractHandState`,
  1.21.4–1.21.8 `getArmPose(Player, ItemStack, Hand)`, ab 1.21.9 `AvatarRenderer`). `WAFFEN_HALTUNG` von außen:
  [0,90,0] / [0,-90,0], Maßstab 0,85, Verschiebung [0,1.8,-3.1] (Griff senkrecht durch die Faust), Ich-Sicht
  [0,100,0] / [0,-100,0]. Rüstungsständer mit Waffe bekommen den Arm auf -90° (Waffenkammer). Prüfen an einer
  Schaufensterpuppe (`minecraft:mannequin`, ab 1.21.9) von der Seite (Szenario `show.txt`), ältere Versionen mit
  `check022.txt`; am Spieler steht der Körper nach einem Teleport schräg zum Kopf. Achsen in der Hand vor der Drehung:
  +y vorn, +z oben, x seitlich; Drehfolge erst z, dann y, dann x (Mojang-Standard). Zum Prüfen am Spieler: von
  hinten muss man das Ende ohne Mündung sehen, von vorn in die Mündung; die Ich-Sicht braucht eingeblendete
  Hand (kein F1). [0,-90,-80] zeigte nach hinten, [0,-90,100] hatte den Griff oben.
- Bilder aus Philips Nachrichten lassen sich aus dem Sitzungsprotokoll holen (Einträge vom Typ „attachment“).
- Zweite Runde (Sprachnachricht 25.10. abends): Haustür-Achse symmetrisch (Tor/Brücke/Teppich/Beete/Brunnen
  einen Block nach Westen, `ZUFAHRT_X0/X1` in VillaPlatzierung), Sicherheitstüren mit Kartenleser (Büro,
  Kontrollraum; neue Tür `sicherheitstuer`), Wand zum Kontrollraum, Gaming-Stuhl (`gamingstuhl`, Sitz),
  Zentrale auf dem Schreibtisch (Szenarien angepasst), Gästezimmer neu mit Fernseher, Bad mit Badewanne
  (`wanne_kopf`/`wanne_fuss`, Sitz) und Toilette mit Spülung (`ToiletteBlock`, Ton `toilette`), gefüllte Truhen,
  Landeplatz eine Lage höher (`LANDEPLATZ`), drei Sofas am Penthouse-Fernseher, Feuerstelle aus dem Beet,
  Clubtür am Gartenzugang (Tunnelhülle hatte x 30 wieder zugemauert), Geheimclub (`geheimclub()`, Geheimregal mit
  Schalter an der Club-Bar), Bunkertüren, Laser bündig in der Wand, Münzgrube (`goldmuenzen`, `Goldbad`) mit
  Sprungbrett, Tiefgarage mit Rampe mittig (`RAMPE_X0/X1`), Sektturm neu, Totem = Geldbündel (Bild und Name
  des Spiels ersetzt in `assets/minecraft`).
- Wunsch: YouTube-Videos im Fernseher richtig abspielen. Geprüft: geht nur mit WATERMeDIA (VLC) und deren
  YouTube-Erweiterung; Stand Juli 2026 für 1.21.1, 1.21.11, 26.1.2 und 26.2, noch nicht 26.3. Plan: optionale
  Anbindung, sobald es sie für 26.3 gibt (Philip spielt 26.3); sonst bleibt Vorschaubild + Browser-Knopf.
- Totem der Unsterblichkeit = Geldbündel: Bild und Name in `src/main/resources/assets/minecraft/` (von
  ausruestung.py erzeugt, `python3 tools/assets/assets.py` nach Änderungen ausführen).
- Club-Bar „ein bisschen Wasser“: in der neuen Villa nicht nachvollziehbar (`/villa lecks` findet nichts dort).

## Version 0.2.1 (veröffentlicht 25.10., 17 Dateien) und 0.2.2 (in Arbeit)

- 0.2.1: https://github.com/MoinMornhart/Minecraft-aber-du-bist-stinkend-Reich/releases/tag/v0.2.1
- 0.2.2: Penthouse-Fernseher mittig (Marke x 63.0), Waffen mit Zielhaltung (Griff wie ein Schwertgriff),
  Show-Tänzerinnen im Geheimclub (`club/TaenzerinEntity`, `client/club/Taenzerin*`, Texturen aus
  `tools/assets/taenzerin.py`; im Bauplan als Entities in `geheimclub()` mit „stange|Kostüm“ oder „laufen|Kostüm“,
  den Anker setzt die Figur beim ersten Tick selbst). Tänzerinnen tragen Glitzer-Showkostüme (bewusst nicht
  freizügiger, weil Repo öffentlich und Minecraft auch von Kindern gespielt wird).

## Version 0.2.2 (veröffentlicht, in allen 17 Versionen geprüft) und 0.2.3 (veröffentlicht 25.10. nachts)

- 0.2.3: Konzertflügel (`klavier/`, `client/klavier/KlavierBildschirm`, Modell `konzertfluegel` + 8 unsichtbare
  `fluegel_teil` in deko.py, Töne `klavier_c2…c6` aus sounds.py, Stücke in `Lieder`), Tresorfallen nur für
  Eindringlinge (`FallenplatteBlock`), Lava-Graben bei Bewohner auf dem ganzen Grundstück und im Lockdown immer
  (`Graben`), Goldmünzen kletterbar (Tag `minecraft:climbable`, `Goldbad` nur noch ohne Fallschaden), Leiter am
  Sprungturm an Eisensäule, James folgt nach `/butler rufen`.
- Türen: Haustür = Torflügel `portaltuer` (tueren.py, Modus „schiebetuer“, Radius 3), Banktür `banktuer` und
  `sicherheitstuer` sind `SicherheitstuerBlock` (Kartenleser öffnet direkt beide Flügel, bleibt offen, Rechtsklick
  schließt), Tür Ballsaal–Wohnzimmer, Wandlücken neben Gästezimmer- und Badtür zu.
- Weiße Decken: `register/ModDecken` (decke_holz, _teppich_weiss/_rot/_gelb, _grau, _gold, _schwarz, _schiefer;
  Vorbild per ID, `ofFullCopy`), Modelle in assets.py (`DECKEN`), villa.py `decken_weiss()` ersetzt sichtbare Böden.
- `pruefe_vor_glas()` in villa.py: Wandgeräte vor Glas → Villa wird nicht gespeichert.
- Magischer Eingang `mechanik/Geheimgang` (schleichen vor Geheimregal, Bewohner/Karte/Stufe 0, schließt nach 10 s),
  Geheimschalter liest den echten Zustand des Regals. Whirlpool taut Eis auf und dampft. Drehteller hält sich an
  der Marke. Garten-Lounge neu, Club-Sofa mit einem Block Abstand, Tänzerinnen im zweiteiligen Showkostüm (so viel
  wie ein Bikini, nicht freizügiger: Repo öffentlich, Kinder spielen Minecraft).
- Tests: `check023.txt`, `check023b.txt`, `check023c.txt` (Weltkoordinaten = Villa + (-56, 62, -56)).
- Neu geplant: README-Bilder, Trailer (`tools/ingame/trailer_szenario.py`), Server-Version ohne Mod beim Spieler
  (Philip: „soll alles gehen“ → Fabric-Server mit Ressourcenpaket und Paper-Plugin).

## Version 0.3 (in Arbeit, Philips Wünsche vom 25.10.)

- 0.2.3 ist in allen 17 Versionen geprüft (`kurz023.txt`, je 9 Prüfungen OK).
- **Handy** (`handy/`, `client/handy/HandyBildschirm`, Texte und Bild aus `tools/assets/handy.py`): Netz-Nachricht
  „handy“ mit `anruf|limo/heli`, `fahr|Ziel`, `liefer|Was`, `auftrag|Text`, `mitarbeiter`, `bodyguards|rufen/heim`.
  Geschenk einmal je Spieler (Marke `stinkendreich.handy` in `VillaPlatzierung.beimBetreten`, auch alte Welten).
  Knöpfe im Test (1280×720, Maßstab 2): Startseite y 188 + 42 je Knopf, Lieferservice y 185 + 34 je Knopf.
  `AnrufAuftrag`: Limo kommt zum Spieler (Parkplatz 25–40 Blöcke), `HeliBereitstellung` stellt den Hubschrauber
  bereit, `Lieferservice` (Tiere über `Kompat.entityTypNachId`, Waren ins Inventar). Test: `handy.txt` (OK).
- **Mitarbeiter** (`mitarbeiter/`): `MitarbeiterEntity extends ButlerEntity` (`istButler()` false), Kiste =
  `MitarbeiterkisteItem` (BlockItem der Truhe). Test `mitarbeiter.txt`: nach 5 Min. 16 Eisenerz + 16 Kohle in der Kiste.
- **Bodyguards** (`bodyguard/`): Golem-Modell mit eigener Smoking-Textur, unverwundbar, Pistole 4,5–24 Blöcke,
  Nahkampf, Creeper nur mit der Pistole (Nahkampf-Ziel ausgeschlossen, Creeper zählen bei der Zielwahl näher).
- **Straßennetz** (`strasse/`): `Strassennetz` (Speicher `stinkendreich-strassen.json`, Routen, Kreisel),
  `StrassenBau` (Spalten nach Abstand zur Mittellinie: Fahrbahn ≤ 2,5, Bordstein ≤ 3,5, Seitenstreifen ≤ 4,5; ein
  Block wird erst gebaut, wenn die Linie 12 Schritte vorbei ist; Stufen aus Tiefenschiefer-Platten; über Wasser
  mindestens einen Block hoch), `StrassenZiele` (Dorf, Mine, Endportal, Netherportal). Wegpunkte alle 3 Blöcke;
  `Chauffeur` fährt bei dichten Wegpunkten „auf Schienen“ (Ziel 6 Blöcke voraus, bremst vor Kurven).
  Befehle: `/butler strassen`, `/butler strasse <ziel>`, `/butler strassenpunkt <strasse> <punkt>` (Test-Teleport).
  Test: `strasse2.txt` (Bau, Fotos von oben, Fahrt zum Dorf): James fährt in 26.3 bis ins Dorf („Wir sind da“).
- README-Abschnitt „Goldenes Handy“ mit Fotos aus `fotos03.txt` und `fotos03_lieferung.txt` (Kiste-Bild ist ein
  Ausschnitt aus dem Mitarbeiter-Test). Alle 17 Versionen lassen sich bauen.
- **Release 0.3.0 angestoßen** (26.10. nachts). Kurztest für alle Versionen: `kurz03.txt` (Handy-Geschenk,
  Lieferung, Mitarbeiter, Bodyguards gegen Zombie, Kreisel, Fahrt ins Dorf).

## Server-Version (Spieler ohne Mod, in Arbeit seit 26.10. nachts)

- Weg: Fabric-Server mit unserer Mod **und Polymer** (Bibliothek von Patbox, gibt es für 1.21–26.3). Ist Polymer
  installiert, übersetzt `server/ServerPaket` (nur 26.3, Dateien mit `//? if >=26.3`) alles für Spieler ohne Mod;
  Spieler mit Mod erkennt der Server am Paket „hallo“ (`netz/ModErkennung`, Client meldet einen Empfänger an).
  Polymer nur als `modCompileOnly` (`deps.polymer` in stonecutter.properties.toml), Betreiber installieren es selbst.
- Erledigt (26.3): Blöcke (`BlockUebersetzung`: volle Würfel als Polymer-Texturblock, Rest als Barriere bzw.
  Strukturleere + Anzeige-Figur mit dem echten Modell, Modelle aus den Blockzuständen via `Blockmodelle`), Items
  (Papier mit unserem Item-Modell), Töne, Ressourcenpaket (Polymer baut und schickt es), James/Mitarbeiter/
  Tänzerinnen als Schaufensterpuppe mit eigener Haut, Bodyguards als Eisengolem, Sitze unsichtbar, Handy als
  Dialogfenster (`HandyDialog`, Befehl `/handy …`), Autos und Hubschrauber als Item-Modelle aus den Fahrzeugdaten
  (`FahrzeugAnzeige`, Modellraum mit y nach unten, Anzeige um x gedreht, Maßstab 4 × massstab), Fahren mit W/A/S/D
  auf dem Server (`AutoEntity.fahrerOhneMod`, Hubschrauber: Leertaste hoch, Strg runter).
- Test: `VANILLA=1 PORT=25571 tools/ingame/test.sh 26.3 tools/ingame/szenarien/vanilla*.txt` (Server mit Mod und
  Polymer aus `tools/ingame/server-mods/`, holen mit `tools/ingame/server_mods_holen.sh 26.3`, dazu ein normales
  Minecraft über `tools/ingame/vanilla_klient.py`). Tasten gehen dort per `xdotool --window`, Bilder vom Bildschirm.
- Bedienfelder ohne Mod (`netz/OhneMod`: Blöcke rufen auf dem Server `OhneMod.zeigen`, die Server-Version meldet
  Dialoge an; Knöpfe schicken `/stinkendreich knopf <art> <x y z> <wert>` = dieselbe Netz-Aktion wie die Bildschirme
  der Mod, `Netz.ausfuehren` prüft die Reichweite): Butler-Menü (`ButlerDialog`, `/butler knopf <nr> <befehl>`),
  Zentrale, Tastenfeld (Code + neuer Code), Aufzug, Flügel mit Klaviatur (Fenster bleibt offen), Musikanlage
  (ohne Mod: Schallplatten von Minecraft), Monitor (`KameraBlick`: Kamera des Clients auf eine Hilfsfigur,
  Springen = nächste Kamera, Schleichen = Ende). Aufzugskabine als Modell (`FahrzeugAnzeige.kabine`).
  Blöcke ohne Kollision bekommen ein Klickfeld; `mixin/BlockBenutzenMixin` verhindert, dass nach einem Blockklick
  zusätzlich das Handy aufgeht. Getestet (26.3, `vanilla_bedien.txt`): Tastenfeld, Aufzug, Klaviatur, Kameras.
- Tasten im Test ohne Mod: das Spielfenster ist ein Kindfenster der Wurzel (`xwininfo -root -tree`), Fahren mit
  W/A und Hubschrauber mit Leertaste geprüft (`vanilla_auto.txt`).
- Bodyguards ohne Mod: großer Türsteher im Smoking (Schaufensterpuppe mit Haut `bodyguard_mensch` aus handy.py,
  Attribut SCALE 1,45 über `modifyRawEntityAttributeData`).
- Spieler mit Mod auf dem Polymer-Server (`MITMOD=1`, Client ohne Polymer; `MITPOLYMER=1` mit): geht. Wichtig:
  `ModErkennung` merkt sich beim Anmelden (`ServerConfigurationConnectionEvents.CONFIGURE`), wer die Mod hat, sonst
  schwankt die Erkennung am Anfang der Spielphase (Figur als Ersatz, danach Mod-Daten → Verbindungsabbruch). Alle
  Polymer-Übersetzungen fragen `ModErkennung.hatMod(PacketContext)`; ohne Kontext (Polymer beim Laden von Chunks!)
  immer „ohne Mod“ – ein Absturz dort lässt den Server den Chunk neu erzeugen (Villa weg).
- Tests: `vanilla_bedien.txt` (alle Bedienfelder), `vanilla_figuren.txt` (Bodyguards, Butler, Geheimclub),
  `vanilla_oben.txt` (Villa von oben).
- Server-Version jetzt ab 26.1 (Fences `//? if >=26.1` in server/* und an den Server-Stellen in ButlerEntity,
  StinkendReich, Handy, HandyItem, AutoEntity, HubschrauberEntity; `deps.polymer` für 26.1–26.3). Geprüft mit
  `vanilla_figuren.txt` in 26.1.2 und 26.2. Vorsicht beim Umstellen: nur diese Stellen ändern, nicht alle
  `>=26.x`-Fences im Projekt.
- Server-Version jetzt ab 1.21.10 (Fences `//? if >=1.21.10`). Unterschiede der Polymer-Versionen in
  `server/PolymerKompat` (Paket-Kontext: ab 26.1 Fabric `PacketContext`, davor `xyz.nucleoid.packettweaker`;
  Anzeige-Daten `DisplayEntityData` bzw. `DisplayTrackedData`; Kamera- und Mitfahrer-Pakete selbst gebaut).
  1.21.9 fehlt (Polymer 0.14.2 kann keine Haut für Schaufensterpuppen). Keine Reflexion über Feldnamen: in der
  fertigen .jar für 1.21.x heißen die Felder anders (ab 26.1 ist Minecraft nicht mehr verschleiert).
  Geprüft mit `vanilla_figuren.txt`: 1.21.10, 1.21.11, 26.1.2, 26.2, 26.3.
- Server-Version jetzt ab 1.21.9 (Fences `//? if >=1.21.9`, `deps.polymer` 0.14.2 für 1.21.9). Die Haut der
  Schaufensterpuppen entsteht über das Datenformat von Minecraft (`ResolvableProfile.CODEC` mit `texture`/`model`),
  nicht mehr über Polymers Hilfsfunktion (die fehlte in 0.14.2). Geprüft mit `vanilla_figuren.txt` in 1.21.9.
- Drehteller und Fernseher ohne Mod: Item-Modelle wie die Aufzugskabine (`FahrzeugAnzeige.einfachesModell`), der
  Fernseher zeigt das Bild „aus“ und hat ein Klickfeld; Rechtsklick öffnet ein Dialogfenster (YouTube-Link,
  „Auf dem PC abspielen“ öffnet den Browser, Ausschalten), Aktion „fernseher“ wie im Menü der Mod.
- Wichtige Fehler der Server-Version gefunden und behoben (Test `vanilla_richtung.txt`, `vanilla_modelle.txt`):
  1. Volle Mod-Blöcke (Deckenblöcke = Böden, Laser-Sender …) erschienen als Notenblock: `BlockUebersetzung` muss
     `PolymerTexturedBlock` sein, sonst wandelt Polymer die Texturzustände wieder in normale Notenblöcke um.
  2. Anzeige-Figuren übernahmen Datenfelder der echten Figur, wenn Typ und Nummer passten (Polymer filtert nur
     danach): „ohne KI“ wurde zu „dreht sich immer zum Spieler“, die Lackfarbe des Autos zur Helligkeit (Autos fast
     schwarz). `ServerPaket.fremdeDatenEntfernen` löscht ab Feld 8 (Anzeige-Figuren) bzw. 15 (Schaufensterpuppen).
  3. Texturkoordinaten der Modelle: jetzt Ecke für Ecke wie `ModelPart.Cube` (vorher Ober-/Unterseite vertauscht
     und Seiten um 180° gedreht, das Fernsehbild stand auf dem Kopf).
  4. Bis 1.21.10 dürfen Modellteile nur um eine Achse in 22,5°-Schritten gedreht sein (Autos waren dort lila);
     `FahrzeugAnzeige` schreibt für diese Versionen das alte Format und lässt andere Drehungen weg.
  5. Anzeige-Figuren nehmen das Licht am Fußpunkt; stehende Modelle bekommen das Licht ihrer Mitte (`licht`).
- Testhilfe: `debug-profile.json` im Ordner des normalen Clients (`versions/<v>/run/vanilla/`) mit
  `looking_at_block_state: alwaysOn` zeigt oben links den Blockzustand, auf den man schaut (ab 26.x).
- `test.sh` wartet jetzt, bis Server und Client wirklich beendet sind (sonst schrieb ein noch speichernder Server in
  die Welt des nächsten Laufs: „Failed to load level data“).
- In Arbeit: 1.21.6–1.21.8 (Polymer 0.13, `deps.polymer` eingetragen, Server-Fences jetzt `>=1.21.6`). Dort gibt
  es keine Schaufensterpuppen: Menschenfiguren werden zum Standbild (`FahrzeugAnzeige.figur`, Spielermodell mit
  Haut, Überzüge mit `dehnen`) mit Klickfeld. Übersetzt in 1.21.5–1.21.10 und 26.3, noch nicht im Spiel geprüft
  (Server-Mods holen: `tools/ingame/server_mods_holen.sh 1.21.8`).
- Offen: 1.21–1.21.5 (ohne Dialogfenster → Truhen-Menüs), Paper-Plugin (Philip fragen, ob nötig).

## Version 0.3.6 (in Arbeit, Philips Wünsche vom 26.10. mittags)

- Erledigt (26.3 geprüft): Haustür öffnet alle drei Reihen (`TorsteuerungBlock.spalteSchalten`), Teppich unter der Tür,
  James sitzt in der Limo tiefer (`vehicleAttachment` 0,6), **Zugbrücke** (`mechanik/Zugbruecke`, Marke „bruecke“,
  Animation mit Block-Displays: ab 26.3 BlockState als `id`/`properties`, davor `Name`/`Properties`; Mauern davor,
  Sicherung, fährt für Bewohner zu Fuß und im Auto aus, Schalter innen/außen, Handy, James, `/villa bruecke ein|aus`).
- Rechte: Zugangskarte = Bewohner (`VillaZustand.istBewohner`), Alarmstufe nur der Eigentümer (erster Bewohner).
- Aufzug: Haltestelle „Untergrundbasis“ (Wert `karte=ja`) nur mit Zugangskarte (`AufzugstafelBlock.nurMitKarte`).
- Bunker-Suite (`bunker_suite()` in villa.py): Fernseher 8 × 4 (`FernseherEntity.setzeGroesse`, Marke mit Breite/Höhe),
  Bad mit Goldtoilette, Kingsize-Bett, Küche; Gang mit Stahlrippen, Rohren, Warnstreifen (`bunker_gang()`).
  Geheimregal im Bunker geht per Schleichen auf wie am Club (gleiche `Geheimgang`-Logik).
- In Arbeit: **Stufe 4 „Festung“** (`VillaZustand.FESTUNG`, nur an der Zentrale mit `kommando=ja` in der
  Kommandozentrale), `Festung` (Ziele: Monster + Eindringlinge), `LandmineBlock` (55 Minen im Rasen, `festung()` in
  villa.py), `AbwehrgeschuetzBlock` (9 auf der Innenmauer, Laser 40 Blöcke), Zugbrücke in der Festung immer gesichert,
  Notausgang (Tunnel unter Graben und Lava hindurch bis x 2, Leiter, Luke, Privattür im Außenzaun, grüne Schilder
  `notausgang_links/_rechts` aus deko.py), Kommandozentrale wie das Büro (`kommandozentrale()`), Tür zur Suite.
  Test: `festung038.txt`, Testhilfe `klient zentrale <wert>` und `klient zentralemenue`.
- Danach: README komplett neu mit allen Bildern geprüft (Philip: Drohnenbild veraltet), Trailer, Release 0.3.6.
- Ganz am Schluss (Philip: „Rest des Tages dafür“): **Paper-Plugin** und **mehrere Villen auf dem Server** mit
  Abstand, Straßen zwischen den Villen, Villen im Handy-Navi, Besitzer über dem Butler, auf der Zugangskarte und
  auf dem Nummernschild.

## Version 0.3.5 (veröffentlicht 26.10. vormittags, 17 Dateien: https://github.com/MoinMornhart/Minecraft-aber-du-bist-stinkend-Reich/releases/tag/v0.3.5)

- Nachttisch mit Schublade: `moebel/NachttischBlock` (DekoBlock + EntityBlock, Rechtsklick öffnet) und
  `moebel/SchubladeBlockEntity` (9 Plätze, `ChestMenu` GENERIC_9x1; Speichern ab 1.21.6 ValueOutput/ValueInput, davor
  CompoundTag). In deko.py `SCHUBLADE = {"nachttisch"}` → Eintrag „schublade“ in deko_bloecke.json, `ModDeko` baut den
  Block. Alte Welten: `Level.getBlockEntity` legt die fehlende Schublade beim ersten Öffnen an.
- Flügel: Ballsaal leer, `konzertfluegel(x0, y, z0, tasten)` in villa.py; Wohnzimmer (39, EG+1, 61, Tasten Nord, Bank
  bei 40/60, mitten im freien Teil südlich der Sofas; Philip wollte ihn weg vom Fenster) und Penthouse (47, DACH+1, 44, Tasten Ost, Bank bei 50/45, vier Blöcke von der Glaswand).
- Neue Sitzmöbel (VERBUND in deko.py): `esszimmerstuhl` (Sitzhöhe 9) im Esszimmer (x 66–74, z 55 und 59) und
  `gartenbank` (Sitzhöhe 7) an der Grillecke; Gaming-Stühle im Kontrollraum und Bunker statt Steintreppen.
- Barhocker sind Sitze (`SITZE` in deko.py, Höhe 11); in villa.py schauen sie zur Theke (Poolbar west, Club süd,
  Weinkeller zum Fass). `SitzEntity` lässt den Blick nur ±100° um die Blockrichtung drehen.
- Tests: `moebel035.txt` (alles OK, 26.3), README-Fotos `fotos035.txt` (Flügel, Barhocker sitzen). Alle 17 Versionen
  übersetzen. Trailer neu (3:31, `docs/trailer.mp4`).
- README: Bilder Ballsaal (ohne Flügel), Wohnzimmer-Flügel, Spielfenster, Esszimmer, Penthouse-Flügel, Schublade,
  Grillecke (mit sitzender Figur); Trailer mit Kapiteln „Ballsaal“, „Konzertflügel“ (Wohnzimmer) und „Esszimmer“.

## Version 0.3.4 (veröffentlicht 26.10. morgens: https://github.com/MoinMornhart/Minecraft-aber-du-bist-stinkend-Reich/releases/tag/v0.3.4)

- Bodyguards: echter Eisengolem + Überzug. `BodyguardRenderer` legt beim ersten Zeichnen
  `textures/entity/bodyguard_smoking.png` (nur Sakko, Hose, Hemd mit Krawatte, rote Blume, Sonnenbrille; Rest
  durchsichtig, aus `handy.py` `bodyguard_smoking()`) über die Golem-Textur aus Minecraft (auch aus Ressourcenpaketen)
  und merkt sich das Ergebnis als `DynamicTexture`. Keine Golem-Textur im Repo.
- Autos: Limo-Dach in Kopfhöhe + Luft, Frontscheibe weit vorn und schräg (Säulen im Teil „scheibe“), Supersportwagen
  und Cabrio Maßstab 1,5, Supersportwagen mit Säulen am Dach. Prüfen mit `autos_kopf.txt` (Schaufensterpuppen auf den
  Sitzen, Plattform am Himmel bei 198–262 / 150 / 196–218).
- Eingang (villa.py): Teppich und Quarzrand von `HZ1 + 1` bis unter das Tor (`n - 13`), keine Laterne an der
  Abzweigung, `kurve_innen`/`kurve_aussen` runden die Zufahrt zur Garage, Mittellinie im Bogen (Mitte 80/88, r 7).
- Mitarbeiter: neuer Skin (`handy.py` `mitarbeiter()`: Helm als Hut-Schicht mit Stirnlampe, Warnweste, Ausweis, $).
- Tests: `eingang_mitarbeiter.txt`, `eingang_mitarbeiter2.txt` (26.3, alles OK).
- **YouTube im Fernseher (eigene Lösung):** `client/fernseher/VideoWerkzeuge` (yt-dlp, Deno, FFmpeg aus PATH oder nach
  Zustimmung von GitHub nach `.minecraft/stinkendreich/werkzeuge`, yt-dlp `-U` einmal je Start), `VideoQuelle` (yt-dlp
  `-j`, H.264 bis 480p + Ton), `VideoAbspieler` (zwei FFmpeg-Prozesse: Bilder 640×360 RGBA 30/s in die Bildschirmfläche
  einer 2048×512-Textur bei 16/16, Ton Mono 48 kHz als `AudioStream` über `VideoTon`/`FabricSoundInstance`, Klang
  „fernseher_ton“ mit 32 Blöcken Hörweite; Uhr startet beim ersten Tonabruf, Lücken = Stille + später überspringen,
  überfällige Bilder verwirft schon der Lese-Thread), `FernseherVideos` (Abgleich mit dem Server je Tick, 48 Blöcke,
  Hinweise über der Hotbar), `FernseherFreigabeBildschirm` (Zustimmung, Config `fernseherHilfsprogrammeLaden`).
  Server: `FernseherEntity` Feld WIEDERGABE ("L<Tick>" läuft, "P<Ticks>" angehalten), Befehle `!abspielen|Link`,
  `!pause`, `!weiter`, `!spulen|s`, `!stopp`. Test ohne YouTube: `STINKENDREICH_TESTVIDEO=<datei>` (dann nur FFmpeg),
  Zeitlupe für Trailer `STINKENDREICH_VIDEOTEMPO=0.333`. Szenario `fernseher_video.txt` (26.3 OK: Video, Pause,
  Spulen, Menü, Stopp). YouTube selbst ist in der Cloud nicht testbar (Downloads/Programme von außen gesperrt).
- **Fernbedienung** (`fernseher/Fernbedienung`, `FernbedienungBlock` mit Zustand `tiefe` 0–8 = liegt auf dem Möbel,
  `FernbedienungItem`: Rechtsklick steuert den nächsten Fernseher bis 32 Blöcke, Schleichen + Rechtsklick legt ab).
  Modell in deko.py (`TIEFER`, Material `fernbedienung_hd`). In der Villa auf dem Nachttisch (Schlafzimmer OG) und dem
  Loungetisch im Penthouse-Kino. Szenario `fernbedienung.txt`.
- **Handy-Navi „Meine Orte“:** Seite ORTE im `HandyBildschirm` (Liste, ✕ löscht, Name + „Ort hier speichern“),
  Server `Handy` Aktionen `orte`, `ort|merken|Name`, `ort|vergessen|Name`; Liste kommt als neues Paket
  `netz/Rueckmeldung` („orte“) zurück. Ohne Mod im `HandyDialog` (Textfeld + Speichern). `ButlerEntity.ort` findet
  auch „meine Mine“; Mine-Synonyme: minen, erzmine, zeche, schacht. Szenario `navi_orte.txt` (OK).
- Philips Regel: am Ende jeder Runde README und Trailer (`trailer_szenario.py`, jetzt mit Fernseher, Fernbedienung,
  Navi) neu. Trailer-Dreh: `SICHTWEITE=12 STINKENDREICH_TESTVIDEO=<alter Trailer> STINKENDREICH_VIDEOTEMPO=0.333
  tools/ingame/test.sh 26.3 tools/ingame/szenarien/trailer.txt`, dann `ZEITLUPE=3 python3 tools/ingame/video.py …`.
- Geprüft vor 0.3.4: alle 17 Versionen übersetzen; im Spiel 26.3 (Golems, Autos, Eingang, Mitarbeiter, Fernseher-
  Video, Fernbedienung, Navi) und 1.21 (Fernbedienung, Video im Fernseher).

## Version 0.3.3 (veröffentlicht 26.10. morgens, 17 Dateien: https://github.com/MoinMornhart/Minecraft-aber-du-bist-stinkend-Reich/releases/tag/v0.3.3)

- Server-Version 1.21.9–26.3 mit allen Darstellungs-Fixes (siehe oben), Drehteller und Fernseher ohne Mod,
  Chauffeur-Rettung. Vorher geprüft: alle 17 Versionen übersetzen, 26.3 ohne Mod (`vanilla_richtung`, `_auto`,
  `_bedien`, `_figuren`, `_modelle`, `_club`, `_tv`) und mit Mod (`kurz03` alles OK), 1.21.9 ohne Mod
  (`vanilla_figuren`, `vanilla_richtung`). README: Server-Abschnitt ab 1.21.9, neue Bilder (Penthouse, Autos,
  Garage, Fernseher-Menü).

## Versionstest 0.3.1 (kurz03, alle 17 Versionen)

- Überall OK: Handy-Geschenk, Lieferung, Mitarbeiter, Bodyguards, Kreisel, Fahrt ins Dorf (außer einmal 26.1.1:
  Wagen hing am Fluss in einer Rinne fest; im zweiten Lauf kam er an). In 1.21.7 und 26.1 war der Zombie nach 12 s
  noch da (langsamer Server) → kurz03 wartet jetzt 20 s. `Chauffeur.zurueckAufDieStrasse` setzt bei wiederholtem
  Festhängen jedes Mal 4 Punkte weiter vorn auf und nie in Wasser oder Lava.

## Version 0.3.2 (veröffentlicht 26.10. nachts)

- Server-Version: Spieler mit und ohne Mod gemeinsam (Erkennung beim Anmelden), Bodyguards ohne Mod als
  Türsteher im Smoking, Trailer im README (`docs/trailer.mp4`, Vorschaubild `docs/bilder/trailer.jpg`).
  Vorher: kurz03 in 26.3 (Einzelspieler) alles OK, `vanilla_figuren.txt` mit und ohne Mod OK.

## Version 0.3.1 (veröffentlicht 26.10. nachts)

- Versionstest 0.3.0 (`kurz03.txt`, alle 17): Handy, Lieferung, Mitarbeiter, Bodyguards, Kreisel überall OK.
  Fahrt ins Dorf scheitert in 1.21.2/1.21.3 (Zeit zu knapp) und 26.1/26.1.2/26.2 (Kreuzung über einem Fluss voller
  Geländer). Fix in `StrassenBau`: Spalte neu bauen, wenn die Straße näher vorbeikommt, Geländer/Bordstein/Mast auf
  der Fahrbahn entfernen, Wasser neben der Straße abdichten (`wasserAbdichten`). Geprüft mit kurz03 (Wartezeit für die
  Fahrt jetzt 360 s): 26.1, 26.2 und 1.21.2 kommen im Dorf an, alle anderen Prüfungen OK.
- Dazu als Vorschau die Server-Version für 26.3 (README: „Server: Freunde spielen ohne Mod mit“). Klicks ohne Mod:
  `BlockBenutzenMixin` rechnet mit den echten Formen nach, worauf der Spieler zeigt (kleine Möbel sind für ihn ganze
  Barriere-Blöcke), `FigurBenutzenMixin` merkt sich Klicks auf Figuren, damit das Handy nicht zusätzlich aufgeht.

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

0.3 fertig machen (Fahrt bis ins Dorf, README-Fotos, alle 17 Versionen), veröffentlichen, dann Trailer und die
Server-Version.
