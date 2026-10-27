### Neu in 0.4.2 – Villen jederzeit, Bodyguards zielen

- **Paper: Villen jederzeit.** Das Plugin baut die erste Villa jetzt auch in einer Welt, die es schon vor dem Plugin gab (vorher nur in ganz neuen Welten – auf einem bestehenden Server gab es deshalb weder Villa noch James). Jeder Spieler, der irgendwann dazukommt, bekommt seine eigene Villa, auch wenn gerade eine andere gebaut wird.
- **Nie über Spielerbauten:** Bevor eine Villa gebaut wird, prüft das Plugin (und die Mod für die Villen der Freunde) den Platz. Stehen dort Häuser, ein Dorf oder Spuren unter der Erde (Fackeln, Werkbank …), nimmt es den nächsten freien Platz. Landminen der Festung liegen nur noch auf natürlichem Boden.
- **Kein Doppelbau mehr:** Kamen zwei Spieler gleichzeitig dazu, bekamen beide auf Paper dieselbe Nummer und denselben Platz. Jetzt bekommt jeder seinen eigenen.
- **Neustart mitten im Bau:** Der Bau geht danach an derselben Stelle weiter (Plugin sofort, Mod beim nächsten Betreten des Besitzers) – keine halbe Villa mehr in der Landschaft.
- **Herr der ersten Villa ohne Neu-Verbinden:** Wer auf die erste Villa wartet, wird nach dem Bau ihr Herr, bekommt die Zugangskarte und wird vor die Tür gebracht. `/villa bauen` macht den, der den Befehl gibt, zum Herrn; `/villa auftraege` zeigt, was gerade gebaut wird.
- **Bodyguards zielen:** Sie drehen sich zum Gegner und heben die goldene Pistole (Mod: Arm des Golems; Paper und Freunde ohne Mod: Zielhaltung mit beiden Armen), das Mündungsfeuer sitzt vorn an der Pistole. Wie echte Personenschützer bleiben sie bei dir und schießen; in den Nahkampf gehen sie erst, wenn ein Gegner nah herankommt.
- **Beide Bodyguards arbeiten:** Einer der beiden kam bisher nie dazu, sich ein Ziel zu suchen oder hinterherzulaufen (Minecraft fragt die KI eines Wesens nur jeden zweiten Tick ab – je nach gerader oder ungerader Nummer). Jetzt sucht jeder selbst, und bei mehreren Gegnern nimmt jeder einen anderen. Auf Paper hat außerdem die eingebaute Golem-KI beide auf denselben Gegner geschickt – die ist jetzt abgeschaltet.
- **Haie** mit ungerader Nummer haben aus demselben Grund nie gejagt – behoben. James erkennt jetzt auch beim Hinterherlaufen zuverlässig, wenn er festhängt.
- **Bodyguards stecken nicht mehr fest:** Sie erscheinen nur auf freien Stellen (vorher manchmal in einer Wand) und stehen gleich wieder neben dir, wenn sie in einer Wand stecken oder nicht hinterherkommen.

### Neu in 0.4.1 – Paper-Server

- **Paper-Plugin:** Für Server mit [Paper](https://papermc.io/) (Minecraft 26.3) gibt es jetzt `stinkendreich_paper-0.4.1+paper26.3.jar` für den `plugins`-Ordner. Die Spieler brauchen keine Mod: Der Server liefert das Ressourcenpaket selbst aus, alles öffnet sich als Fenster.
- **Auf Paper dabei:** Villa am Spawn und eine eigene Villa für jeden weiteren Spieler (im Hintergrund gebaut, im Test in 13 Sekunden, längster Tick 0,06 s), Türen, Tore, Rollläden, Kartenleser, Tastenfeld, Aufzug, Zugbrücke mit Animation, Sicherheitssystem mit Laser, Bewegungsmeldern, Sirenen, Warnleuchten und Tresorfallen, Festung mit Minengürtel, Abwehrgeschützen und Notausgang, Lava-Graben, Inkognito-Modus, Smart Home mit Lichtfarben und Disco, Gästeliste, Überwachungskameras, Konzertflügel mit Klaviatur, Musikanlage, Kamin, Whirlpool, Dusche, Toilette, Schublade, Gemälde, fahrbare Autos, Hubschrauber, Fernseher (Video-Link im Fenster, abspielen im Browser) und Butler James.
- **Auch auf Paper:** Agenten-Anzug mit allen Kräften, goldene Pistole, Laseruhr und Geldkanone, die Waffenkammer im Bunker, Bodyguards und Lieferservice per Handy, tanzende Tänzerinnen im Geheimclub und echte Haie mit eigenem Modell, die auch auf „Friedlich“ zubeißen. Dazu die Haifalle (roter Knopf, Smart Home, Automatik bei Angriffen), die Wasserrutsche und das Goldmünzen-Becken ohne Fallschaden.
- **Paper:** Türen, Haifallen-Boden und Abwehrgeschütze bekommen ihren richtigen Grundzustand (der Glasboden über dem Haibecken war vorher unsichtbar und offen).
- **Kamin:** Der Schalter im Wohnzimmer löscht und zündet jetzt alle vier Feuer (das linke war 6 Blöcke entfernt und blieb an).
- **James holt Erz auf Paper:** „Butler, hol mir 16 Eisen“ (auch Kohle, Gold, Diamanten, Kupfer, Smaragde, Redstone, Lapis): Treppentunnel, ganze Ader, Hochofen in der Villa, Lieferung. **Mitarbeiterkiste** im Handy: drei Mitarbeiter bauen Diamanten, Eisen und Kohle in ihre Kiste ab.
- **James fällt Bäume und sortiert Truhen auf Paper:** „Butler, hol mir 16 Holz“ (nur natürliche Bäume, an jeder Stelle wächst ein neuer Setzling) und „Butler, sortier die Truhen“ (alle Truhen und Fässer um dich herum, jede Sorte in ihre Stammtruhe). Dazu „Butler, geh jagen“ (Nutztiere, Jungtiere und benannte Tiere nie, von jeder Art bleiben zwei; abschaltbar). Alles auch als Knopf im Butler-Menü.
- **Straßen zwischen den Villen auf Paper:** Für jede neue Villa baut der Server eine Straße von der Spawn-Villa bis vor ihre Zufahrt (Mittellinie, Laternen, Einschnitte, Brücken). **James als Chauffeur:** „Butler, fahr mich zur Villa von Max“ – die Limo fährt vor, James fährt dich hin.
- **James als Pilot auf Paper:** „Butler, flieg mich ins Dorf“ (oder nach Hause, zur Villa eines Nachbarn; auch im Handy): Der Hubschrauber holt dich ab, landet neben dir und fliegt dich über alles hinweg zum Ziel.
- **Handy auf Paper:** Ein Rechtsklick auf Truhe, Tür oder Knopf öffnet diese (vorher ging das Handy auf).
- **Neue Skins:** James mit gekämmten Haaren und grauen Schläfen, Frack mit Revers und Einstecktuch, Weste mit Goldknöpfen, Fliege und Frackschößen. Die Tänzerinnen mit Haaren mit Volumen, langen Haaren über den Rücken, Federschmuck und Glitzer-Top. Beide mit zweiter Hautschicht (3D-Effekt).
- **Ohne Mod vollständig:** James, Mitarbeiter und Tänzerinnen fehlten auf Servern ohne Mod (Schaufensterpuppe, Paper) der linke Arm und das linke Bein. Jetzt haben alle Häute beide Seiten.
- **Eigene Welt für Freunde öffnen (LAN oder NoRisk Client „Hosted Worlds“):** Jeder Freund bekommt seine eigene Villa, eine Straße zu dir und seinen eigenen James. Freunde ohne Mod flogen vorher sofort raus („container_set_content“) – behoben. Mit Polymer im Profil können auch Freunde mit normalem Minecraft mitspielen.
- **Ressourcenpaket für Spieler ohne Mod:** Die Mod schaltet Polymers Versand selbst ein (vorher musste man ihn in `config/polymer/auto-host.json` von Hand einschalten). Einstellung `serverRessourcenpaket`.
- **Server ohne Mod 1.21.6–1.21.8:** Menschenfiguren (Standbilder) sind nicht mehr schwarz, wenn sie ein Stück im Boden stehen.
- **Aufzug ohne Mod:** Ein Klick auf die Aufzugstafel öffnete bei Spielern ohne Mod das Handy statt der Etagenwahl (der Klick landete bei der Lampe dahinter). Jetzt antwortet zuerst der angeklickte Block.
- **Trailer** mit neuen Kapiteln „Paper-Server“, „Neue Skins“ und „Freunde in deiner Welt“.

### Neu in 0.4.0 – Nachbarschaft

- **Jeder bekommt seine eigene Villa:** Kommt auf einem Server ein neuer Spieler dazu, entsteht für ihn eine eigene Villa mit Butler, Fuhrpark, Hubschrauber, Bunker und Festung – mit großem Abstand im Raster rund um die erste (Standard 320 Blöcke, in der Config `villenAbstand`, abschaltbar mit `mehrereVillen`).
- **Ohne Server-Hänger:** Die Villa wird im Hintergrund Stück für Stück gebaut. Der Server läuft dabei normal weiter (im Test längster Tick 0,1 s, vorher stand er 15 bis 18 Sekunden still). Der Neue sieht den Fortschritt in Prozent und wird nach etwa einer halben Minute vor seine Haustür gebracht.
- **Guter Platz:** Die Mod sucht den flachsten freien Platz in der Nähe und lässt Plätze aus, über die schon eine Straße führt.
- **Straßen zwischen den Villen:** Jede neue Villa wird ans Straßennetz angeschlossen; die Straße endet dort, wo ihre Zufahrt auf Geländehöhe ankommt. Straßen umfahren jetzt jede Villa, und der Straßenbau lädt die Welt im Hintergrund (beim ersten Betreten ruckelte der Server vorher spürbar).
- **James und das Handy kennen die anderen Villen:** „James, fahr mich zur Villa von Aircrafter“, im Handy-Navi stehen sie gleich mit drin (auch ohne Mod). „Nach Hause“ heißt für jeden seine eigene Villa.
- **Nummernschilder mit Namen:** Auf jedem Auto steht vorn und hinten der Name des Besitzers in Gold.
- Die Zufahrt jeder neuen Villa ist im Hang ein offener Einschnitt statt eines Tunnels. Pflanzenreste rund um eine frisch gebaute Villa werden weggeräumt.
- **Inkognito:** Drückt man „wieder auftauchen“, während die Villa noch nach unten fährt, merkt sie sich das jetzt und kommt direkt danach wieder hoch (vorher ging der Befehl verloren und sie blieb unten).

### Neu in 0.3.9

- **Inkognito-Modus komplett neu und geprüft:** Vor der Fahrt wird die ganze Villa samt allen Bildern, Fischen, Autos, Rüstungsständern und James als genaue Kopie gespeichert und am Ziel exakt so wieder aufgestellt. Ein Vergleich Block für Block und Figur für Figur nach einmal runter und wieder hoch meldet „alles gleich“. Kein rieselnder Kies, kein auslaufendes Wasser. Wer beim Auftauchen auf der Wiese steht, steht danach oben drauf.
- **Straßen immer an der Luft:** Keine Straßen mehr unter der Erde, Brücken über Meer und Flüsse, offene Einschnitte statt Tunnel, halbe Stufen statt Erdreihen quer über der Fahrbahn. Ab Minecraft 26.1 zählten Gras, Blumen und Moos nicht mehr als „natürlich“, deshalb blieben Straßenbau und James davor hängen. `/butler strassen neu` baut alle Straßen neu.
- **Auto:** Streifen am Bordstein oder Geländer bremst kaum noch, nur ein echter Aufprall.
- **Aufzug** fährt ohne Ruckeln (die Kabine sprang auf dem Bildschirm alle 8 Ticks zurück).
- **Tor und Garage** öffnen nur noch, wenn man direkt davor steht (zu Fuß 6, im Auto 14 Blöcke), und gehen zuverlässig wieder zu. Neu im Smart Home: „Eingangstor bleibt zu“ und „Garagentor bleibt zu“.
- **Zugbrücke** fährt nach dem Weggehen in wenigen Sekunden ein (ein geparktes Auto mit James hielt sie vorher für immer draußen).
- **Gästeliste:** „James, behandle Aircrafter als Gast“, im Handy unter „Gästeliste“ oder mit `/butler gast <Name>`. Gäste kommen ohne Karte durch Tor, Türen und über die Brücke und sind keine Eindringlinge.
- **Echte Haie** mit eigenem Modell (Rückenflosse, Zähne, Sichelschwanz), die wirklich zubeißen, auch auf „Friedlich“.
- **Bodyguards** halten die goldene Pistole in der Hand.
- **Handy** geht im Hubschrauber und im Auto auf.
- **YouTube im Fernseher:** kein endloses „Video wird geladen“ mehr nach den ersten Bildern, schnellerer Start.
- **Absturz beim Öffnen der Kameras** mit dem NoRisk Client behoben.
- Neue Welten: Haupttreppe mit allen Stufen, Sektturm im Erdgeschoss und Bambus am Bunker-Bett entfernt.

### Neu in 0.3.8

- **Straßen:** Die automatischen Straßen führen jetzt an die Oberfläche der Ziele (in 0.3.7 konnte eine Straße zu einem weit entfernten Ziel in den Boden hinein gebaut werden). Bitte 0.3.7 nicht mehr verwenden.

### Neu in 0.3.7

- **Läuft jetzt auch mit dem NoRisk Client** und anderen Launchern mit älterem Fabric Loader (die Mod verlangt keine bestimmte Loader-Version mehr).
- **Inkognito-Modus repariert:** Bilder, Rüstungsständer, Autos und Hubschrauber bleiben nach dem Runter- und Hochfahren an ihrem Platz (vorher wanderten Bilder zur Seite und Fahrzeuge sanken in den Boden). Schon verrutschte Sachen in alten Welten bleiben, wie sie sind.
- **YouTube im Fernseher:** Videos holt jetzt yt-dlp selbst; der Fehler „403 Forbidden“ von FFmpeg sollte damit weg sein.
- **Straßen sofort:** Sobald du die Welt betrittst, baut die Mod nacheinander Straßen zum Dorf, zur Mine, zum Netherportal und zum Endportal.

### Neu in 0.3.6

- **Modus FESTUNG (Stufe 4):** nur in der neuen Kommandozentrale im Bunker einschaltbar. Die Villa macht komplett dicht: Sirenen, Zugbrücke ein, Lava im Graben, auf der höheren Mauer abwechselnd Abwehrgeschütze und Laser in den Himmel, ein Minengürtel rund ums Grundstück. Alles fährt mit Animation hoch, nichts kommt in den Chat. Bewohner und Kartenbesitzer bleiben verschont.
- **Notausgang:** Vom Bunker führt ein Tunnel 50 Blöcke weit zu einer versteckten Falltür im Rasen, die nur mit Zugangskarte aufgeht.
- **Bunker:** Bunker-Suite mit 8 × 4 Blöcke großem Fernseher, Bad mit Goldtoilette, Kingsize-Bett und Küche; der Aufzug fährt nur mit Zugangskarte in den Bunker; Kommandozentrale mit Monitorwand und Chefschreibtisch.
- **Zugbrücke** mit Animation (Schalter, Handy, James) und neue Haustür, die alle drei Reihen öffnet.
- **Smart Home im Handy:** ein Schalter je Funktion mit Symbol und farbigem Zustand: Rollläden, Zugbrücke, Licht in acht Farben, Disco, Automatik (abends zu, morgens auf), Krisenbunker und Haibecken.
- **Inkognito-Modus:** Die ganze Villa fährt wie ein Aufzug in die Erde, darüber schließt sich eine Wiese, auf der Tarnbäume wachsen. Unten bleibt alles erhalten, wer drei Blöcke tief gräbt, landet im Haus. Auf Knopfdruck fährt sie mit allem wieder hoch. Wer drinnen steht, fährt ohne Schaden mit.
- **Krisenbunker:** Der Aufzug ist komplett gesperrt, Rollläden zu, Zugbrücke hoch, nur noch der Bunker lebt. Raus geht es dann nur über den Notausgang.
- **Echte Regenduschen** im Bad oben und im Bunker: Armatur aufdrehen, es regnet bis zum Boden und rauscht.
- **Club:** größere Bar vor einer Glaswand zum Meeresaquarium, große Tanzfläche, **VIP-Seerestaurant** unter einer Glaskuppel mitten im Aquarium (Fische, Korallen, Delfine, Haie).
- **Haifischbecken** im Wohnzimmer unter einem Glasboden mit echten Haien: Ein roter Knopf oder das Handy wirft freche Gäste zu den Haien, wer einen Bewohner angreift, landet von selbst dort.
- **Lava** im Graben jetzt nur noch im Lockdown und in der Festung.
- Monitore am Büro- und Bunker-Schreibtisch zeigen zum Stuhl.
- **Klares Glas** ohne Rahmen in der ganzen Welt, wie bei beliebten Ressourcenpaketen.

### Neu in 0.3.5

- **Nachttisch mit Schublade:** Rechtsklick auf einen Nachttisch öffnet seine Schublade mit 9 Plätzen, zum Beispiel für Zugangskarte, Uhr und Gold. Beim Abbauen fällt der Inhalt heraus. Das geht auch mit Nachttischen in bestehenden Welten.
- **Konzertflügel umgezogen:** Der Flügel steht nicht mehr im Ballsaal, sondern mitten im Wohnzimmer, links, wenn du aus dem Ballsaal hereinkommst, mit Abstand zu Fenster und Wänden. Ein zweiter Flügel steht mitten im Penthouse zwischen Hausbar und Lounge.
- **Esszimmer und Grillecke:** neue Esszimmerstühle aus weißem Leder mit Goldrahmen (18 an der Marmortafel) und Teak-Gartenbänke an der Grillecke. Auf beiden kann man sich mit Rechtsklick hinsetzen. Die Holztreppen sind weg, auch im Kontrollraum und im Bunker (dort jetzt Gaming-Stühle).
- **Barhocker zum Hinsetzen:** an allen Bars (Poolbar, Hausbar im Penthouse, Nachtclub, Geheimclub, Küche, Weinkeller), man schaut dabei zur Theke.
- **Trailer und README** zeigen die neuen Möbel.

### Neu in 0.3.4

- **YouTube im Fernseher:** Link einfügen, „Im Fernseher abspielen“ – das Video läuft direkt im Spiel, der Ton kommt aus dem Fernseher, alle in der Nähe sehen dieselbe Stelle. Anhalten, Weiter, Spulen und Stopp im Menü. Beim ersten Video fragt die Mod einmal, ob sie die kostenlosen Hilfsprogramme yt-dlp, FFmpeg und Deno (zusammen etwa 200 MB, von GitHub) laden darf.
- **Fernbedienung:** in der Hand oder abgelegt auf Nachttisch und Tisch (Schleichen + Rechtsklick), öffnet das Menü des nächsten Fernsehers. In der Villa liegt je eine im Schlafzimmer und im Penthouse-Kino.
- **Handy-Navi „Meine Orte“:** eigene Orte unter einem Namen speichern und hinfahren lassen; James kennt sie auch im Chat („James, bring mich zu meiner Mine“). „Bergwerk“, „Zeche“ und „Schacht“ versteht er jetzt als Mine.
- **Bodyguards:** echte Eisengolems mit Sakko, Krawatte, roter Blume und Sonnenbrille (Nase, Hände und Füße bleiben wie beim Golem).
- **Autos:** In der Limo passt man mit dem Kopf unter das Dach, die Frontscheibe sitzt weiter vorn; Supersportwagen und Bonzenauto sind 1,5-mal so groß.
- **Mitarbeiter:** neuer Skin mit Schutzhelm, Stirnlampe, Warnweste und Firmenausweis.
- **Villa (neue Welten):** Teppich bis direkt an die Haustür und unter das Tor, runde Kurven an der Zufahrt zur Garage.

### Neu in 0.3.3

- **Server-Version (Vorschau) jetzt für Minecraft 1.21.9 bis 26.3** und deutlich schöner für Spieler ohne Mod: Böden, Teppiche und andere Mod-Blöcke sehen richtig aus (vorher teils wie Notenblöcke), Autos haben ihre Lackfarbe und fahren mit der Front voraus, Drehteller und Fernseher sind zu sehen. Ein Rechtsklick auf den Fernseher öffnet ein Fenster: YouTube-Link eingeben, Video im Browser ansehen oder ausschalten.
- **Chauffeur:** Bleibt der Wagen auf einer Fahrt hängen, setzt James ihn ein Stück weiter vorn wieder auf die Straße und nie ins Wasser.

### Neu in 0.3.2

- **Server-Version (Vorschau, 26.3):** Spieler mit Mod und Spieler ohne Mod können jetzt gemeinsam auf demselben Server spielen. In 0.3.1 flog ein Spieler mit Mod beim Betreten eines Servers mit Polymer wieder raus. Spieler ohne Mod sehen die Bodyguards jetzt als große Türsteher im Smoking mit Sonnenbrille statt als normale Eisengolems.
- **Trailer:** Ein 3-Minuten-Trailer aus echtem Minecraft steht im README.

### Neu in 0.3.1

- **Straßen:** Wo eine Straße sich selbst nahe kommt (Kurven an Flüssen, Brücken), stehen keine Geländer und Bordsteine mehr mitten auf der Fahrbahn. Wasser aus Höhlen und Hängen läuft nicht mehr auf die Straße. James kommt so auch in Welten bis ins Dorf, in denen er vorher an einer Flusskreuzung hängen blieb.
- **Vorschau – Server ohne Mod beim Spieler (nur 26.3):** Liegen auf einem Fabric-Server diese Mod und die Bibliothek Polymer, können Freunde mit ganz normalem Minecraft mitspielen. Sie bekommen beim Betreten automatisch ein Ressourcenpaket und sehen Villa, Möbel, James, Tänzerinnen, Autos und Hubschrauber. Handy, Butler-Menü, Sicherheitszentrale, Tastenfeld, Aufzug, Flügel, Musikanlage (mit den Schallplatten von Minecraft) und Überwachungskameras gehen über Fenster, die Minecraft selbst mitbringt; Autos fahren sie mit W/A/S/D. Anleitung im README unter „Installation“.

### Neu in 0.3.0 – das XXL-Update

- **Goldenes Handy:** Rechtsklick öffnet ein Luxus-Handy mit Uhr und Akku. Damit rufst du James an – er kommt wirklich mit der Stretch-Limo vorgefahren oder landet mit dem Hubschrauber neben dir. Dazu Navigation (Villa, Dorf, Mine, Endportal, Netherportal und alle gemerkten Orte), Aufträge per Knopfdruck, Mitarbeiter und Bodyguards.
- **Lieferservice:** Kühe, Schweine, Schafe, Hühner, ein gezähmter Hund, eine Katze oder ein Papagei, ein Festessen, 64 Feuerwerksraketen oder ein Sack Gold – James bringt die Bestellung mit der Limo vorbei.
- **Straßennetz:** Bevor James zum ersten Mal irgendwohin fährt, baut er eine echte Straße: zuerst einen Kreisel mit Springbrunnen-Insel vor der Villa, dann Straßen mit Mittelstreifen, Bordstein, Laternen und Brücken zum Ziel. Neue Ziele zweigen vom nächsten Straßenstück ab, so wächst ein Netz. Du kannst die Straßen auch selbst befahren. Abgerissen wird nichts: weder an der Villa noch an deinen Bauten.
- **Mitarbeiter:** Übers Handy bestellt, kommt eine Mitarbeiterkiste ins Inventar. Stell sie auf, und drei Mitarbeiter steigen aus – einer sucht Diamanten, einer Eisen, einer Kohle. Ihre Beute bringen sie in die Kiste. Rechtsklick auf einen Mitarbeiter wechselt seine Aufgabe (auch Gold, Smaragde, Redstone, Lapis, Holz).
- **Bodyguards:** Zwei Eisengolems im Smoking mit Sonnenbrille. Sie können nicht sterben, folgen dir und beschützen dich: auf Abstand mit der Pistole, aus der Nähe mit Golem-Fäusten. Übers Handy rufst du sie oder schickst sie heim.

### Neu in 0.2.3

- **Konzertflügel, der wirklich spielt:** Ein großer Flügel (3 × 3 Blöcke) mit offenem Deckel, goldenem Innenleben, Notenpult und Klavierbank. Rechtsklick öffnet die Klaviatur mit drei Oktaven (Maus oder Computertasten), alle in der Nähe hören mit. Der Flügel spielt auch selbst: Für Elise, Ode an die Freude, Kanon in D.
- **Magischer Eingang zum Geheimclub:** Vor dem Bücherregal im Nachtclub kurz schleichen – es funkelt, ein Zauberklang ertönt, und das Regal gleitet auf. Nach 10 Sekunden schließt es sich von selbst. Der Schalter im Regal geht weiterhin.
- **Türen:** Die Haustür ist jetzt eine große weiß-goldene Portaltür mit Glasfenstern, die aufgleitet, wenn du davorstehst. Neue Banktür im Bunker vor der Kommandozentrale (nur mit Zugangskarte). Sicherheitstüren (Büro, Kontrollraum, Banktür) bleiben nach dem Öffnen offen, bis du sie per Rechtsklick wieder schließt, zum Beispiel von innen. Neue Doppeltür zwischen Ballsaal und Wohnzimmer, Wandlücken neben der Gästezimmer- und der Badtür geschlossen.
- **Weiße Decken:** Überall in Haus und Penthouse ist die Decke jetzt weiß, auch unter Holzböden und Teppichen (neue Deckenblöcke: oben wie vorher, unten weiß).
- **Tresor:** Die Fallen im Tresor lösen nur noch bei Eindringlingen aus, nicht mehr bei dir.
- **Lava-Graben:** Lava, sobald ein Bewohner irgendwo auf dem Grundstück ist (auch im Garten). Im Lockdown ist immer Lava im Graben.
- **Whirlpool:** beheizt – er friert nie mehr zu und dampft.
- **Goldspeicher:** Leiter zum Sprungbrett hängt richtig, in den Goldmünzen kannst du mit Schleichen stehen bleiben und mit der Leertaste nach oben klettern.
- **James** bleibt nach dem Rufen bei dir, statt wieder nach Hause zu gehen.
- **Geheimclub:** Zwischen Bar und Sofa ist ein Block Platz, die Tänzerinnen tragen zweiteilige Glitzer-Showkostüme.
- **Kleinigkeiten:** Garten-Lounge neu (Sofas als U, Marmortisch, Kronleuchter), Alarmanlagen hängen nicht mehr vor Fenstern, der Drehteller bleibt genau in der Mitte seines Goldrings.

### Neu in 0.2.2

- **Show-Tänzerinnen im Geheimclub:** Drei tanzen an den Stangen und drehen sich langsam darum, drei laufen durch den Club und tanzen, wenn sie stehen bleiben. Vier Glitzer-Showkostüme mit Federschmuck. Sie sind unverwundbar wie James.
- **Waffen wie ein Schwert in der Hand:** Mit Pistole oder Geldkanone nimmt deine Figur die Zielhaltung ein (Arme nach vorn wie mit geladener Armbrust). Der braune Griff steckt senkrecht in der Faust wie ein Schwertgriff, die Mündung zeigt geradeaus. Die Rüstungsständer in der Waffenkammer zielen auch. Die Ich-Sicht bleibt wie in 0.2.1.
- **Penthouse:** Der Fernseher hängt jetzt genau mittig über den drei Samtsofas.

### Neu in 0.2.1 (Fehler und Wünsche aus Philips Bildern und Sprachnachricht)

- **Lava-Graben:** Ist die Alarmanlage an und du bist zu Hause, wird aus dem Wasser im Graben Lava. Gehst du raus, ist es wieder Wasser (abschaltbar in der Config: `lavaGraben`).
- **Geheimclub:** Hinter einem Bücherregal an der Bar des Nachtclubs (Schalter im Regal) führt eine Treppe hinab zu einem geheimen Club mit Show-Bühne, Tanzstangen, Samtnischen und eigener Bar.
- **Goldspeicher zum Hineinspringen:** Münzgrube aus Goldmünzen, in die man weich einsinkt (kein Fallschaden), mit Sprungbrett und Leiter.
- **Türen überall:** Sicherheitstüren mit Kartenleser fürs Büro und den Kontrollraum (nur mit Zugangskarte), Privattür vom Foyer in die Tiefgarage (nur Bewohner), Clubtür am Gartenzugang (vorher zugemauert), Tür in den Club, Glastür aus der Personalküche in den Garten, Türen im Bunker, Durchgang ins Haus aus der Garage (2 breit, Schiebetür).
- **Haus symmetrisch:** Tor, Brücke, roter Teppich, Beete und Brunnen liegen jetzt genau auf der Achse der Haustür (vorher einen Block versetzt). Doppeltüren ohne Loch am Balkon und im Penthouse.
- **Obergeschoss:** Lücke zum Kontrollraum geschlossen, Gaming-Stuhl am Schreibtisch, Sicherheitszentrale auf dem Schreibtisch, Gästezimmer neu eingerichtet (XXL-Bett, Fernseher, Leseecke, Kommode), Bad mit Badewanne zum Reinsetzen, Toilette mit Spülung, gefüllte Truhen.
- **Penthouse:** Landeplatz eine Lage höher (von innen nur noch weiße Decke), drei Samtsofas vor dem Fernseher, Lounge neu (Weg vom Aufzug zur Tür frei), Ostecke eingerichtet. Feuerstelle auf der Dachterrasse nicht mehr im Beet.
- **Tiefgarage:** Rampe mittig unter dem Garagentor, daneben eine Fahrspur zum Parken oben, neue Stellplätze in der Bucht neben der Rampe. Die G-Klasse kommt die Rampe hinauf.
- **Räume:** Personalküche mit eigener Wand, Schmucktresor mit Diamantberg und Tresoren, Weinlounge als Weinkeller, Lager im Bunker, Wohnzimmer-Sofas als U vor dem Fernseher, Kamin symmetrisch zum Gemälde, VIP-Sofa ganz auf dem Podest, DJ-Pult vorn, Sektturm aus einzelnen Schalen, Tische aus einem Stück statt halber Stufen, Sofas ohne Spalt am Boden. Laser bündig in der Wand.
- **Wasserrutsche zum Rutschen:** Leiter bis auf die Plattform, lange Glasrinne, man wird kräftig bergab geschoben und landet im Pool.
- **Autos größer** (Bonzenauto und Supersportwagen 1,25-fach, Geländewagen 1,15-fach).
- **Aufzug:** schiebt niemanden mehr, fährt nicht leer los, niemand steckt mehr im Schachtboden fest.
- **Agenten-Ausrüstung:** Waffen mit Griff in der Hand und Mündung nach vorn, goldene Pixel-Sonnenbrille, das Totem der Unsterblichkeit ist ein Bündel Geldscheine.

Hinweis: Die geänderte Villa entsteht nur in **neuen Welten**.
