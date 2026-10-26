# Stand (Übergabe)

_Zuletzt aktualisiert: 2026-10-26, 02:50 Uhr (Nachtschicht, Philip schläft)_

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
- Offen: Fernseher, Drehteller, 1.21.9–1.21.11 (Polymer 0.14/0.15: `xyz.nucleoid.packettweaker.PacketContext`,
  andere Paketnamen für Anzeige-Daten), darunter ohne Schaufensterpuppen/Dialoge, danach Paper-Plugin.

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
