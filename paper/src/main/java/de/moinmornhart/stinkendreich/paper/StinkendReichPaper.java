package de.moinmornhart.stinkendreich.paper;

import de.moinmornhart.stinkendreich.paper.block.Blockdaten;
import de.moinmornhart.stinkendreich.paper.block.VirtuelleBloecke;
import de.moinmornhart.stinkendreich.paper.villa.Ausstattung;
import de.moinmornhart.stinkendreich.paper.villa.Bauplan;
import de.moinmornhart.stinkendreich.paper.villa.Baustelle;
import de.moinmornhart.stinkendreich.paper.villa.FigurenUebersetzen;
import de.moinmornhart.stinkendreich.paper.villa.VillaBefehle;
import de.moinmornhart.stinkendreich.paper.villa.Villen;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Paper-Plugin „Minecraft, aber du bist stinkend reich“: dieselbe Villa wie in der Fabric-Mod, für Server, auf denen
 * die Spieler keine Mod brauchen. Alles Sichtbare kommt über ein Ressourcenpaket, das der Server selbst ausliefert.
 */
public final class StinkendReichPaper extends JavaPlugin implements Listener {
    private Blockdaten blockdaten;
    private VirtuelleBloecke bloecke;
    private Bauplan bauplan;
    private Villen villen;
    private Ausstattung ausstattung;
    private FigurenUebersetzen figuren;
    private Ressourcenpaket paket;
    private de.moinmornhart.stinkendreich.paper.block.Mechanik mechanik;
    private de.moinmornhart.stinkendreich.paper.butler.Butler butler;
    private Gaeste gaeste;
    private SmartHome smartHome;
    private de.moinmornhart.stinkendreich.paper.auto.Fahrzeuge fahrzeuge;
    private de.moinmornhart.stinkendreich.paper.block.Zugang zugang;
    private de.moinmornhart.stinkendreich.paper.block.Tore tore;
    private de.moinmornhart.stinkendreich.paper.block.Sicherheit sicherheit;
    private de.moinmornhart.stinkendreich.paper.block.Zugbruecke zugbruecke;
    private de.moinmornhart.stinkendreich.paper.block.Kameras kameras;
    private de.moinmornhart.stinkendreich.paper.block.Festung festung;
    private de.moinmornhart.stinkendreich.paper.block.Fernseher fernseher;
    private de.moinmornhart.stinkendreich.paper.butler.Bodyguards bodyguards;
    private de.moinmornhart.stinkendreich.paper.ausruestung.Agent agent;
    private de.moinmornhart.stinkendreich.paper.butler.Taenzerinnen taenzerinnen;
    private de.moinmornhart.stinkendreich.paper.hai.Haie haie;
    private de.moinmornhart.stinkendreich.paper.block.Inkognito inkognito;
    private de.moinmornhart.stinkendreich.paper.block.Unterhaltung unterhaltung;
    /** Laufende Baustelle (immer nur eine) und wartende Aufträge. */
    private Baustelle baustelle;
    private final Deque<Runnable> warteschlange = new ArrayDeque<>();
    /** Spieler, die auf ihre Villa warten (werden nach dem Bau hingebracht). */
    private final Set<UUID> wartende = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            blockdaten = new Blockdaten(this);
            bloecke = new VirtuelleBloecke(this, blockdaten);
            bauplan = new Bauplan(this);
            villen = new Villen(this);
            ausstattung = new Ausstattung(this);
            figuren = new FigurenUebersetzen(this);
            paket = new Ressourcenpaket(this);
            paket.bauen(blockdaten.modelle());
            paket.bereitstellen();
        } catch (Exception e) {
            getLogger().severe("Start fehlgeschlagen: " + e);
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        gaeste = new Gaeste(this);
        smartHome = new SmartHome(this);
        mechanik = new de.moinmornhart.stinkendreich.paper.block.Mechanik(this, bloecke);
        tore = new de.moinmornhart.stinkendreich.paper.block.Tore(this, bloecke, mechanik);
        zugang = new de.moinmornhart.stinkendreich.paper.block.Zugang(this, bloecke, mechanik);
        sicherheit = new de.moinmornhart.stinkendreich.paper.block.Sicherheit(this, bloecke, mechanik, zugang, tore);
        zugbruecke = new de.moinmornhart.stinkendreich.paper.block.Zugbruecke(this, mechanik);
        getServer().getPluginManager().registerEvents(zugbruecke, this);
        new de.moinmornhart.stinkendreich.paper.block.Geheimgang(this, bloecke, mechanik, tore);
        new de.moinmornhart.stinkendreich.paper.block.Graben(this);
        festung = new de.moinmornhart.stinkendreich.paper.block.Festung(this, bloecke, mechanik);
        fernseher = new de.moinmornhart.stinkendreich.paper.block.Fernseher(this);
        bodyguards = new de.moinmornhart.stinkendreich.paper.butler.Bodyguards(this);
        getServer().getPluginManager().registerEvents(bodyguards, this);
        agent = new de.moinmornhart.stinkendreich.paper.ausruestung.Agent(this);
        getServer().getPluginManager().registerEvents(agent, this);
        taenzerinnen = new de.moinmornhart.stinkendreich.paper.butler.Taenzerinnen(this);
        getServer().getPluginManager().registerEvents(taenzerinnen, this);
        haie = new de.moinmornhart.stinkendreich.paper.hai.Haie(this);
        getServer().getPluginManager().registerEvents(haie, this);
        getServer().getPluginManager().registerEvents(fernseher, this);
        inkognito = new de.moinmornhart.stinkendreich.paper.block.Inkognito(this, bloecke);
        getServer().getPluginManager().registerEvents(inkognito, this);
        getServer().getPluginManager().registerEvents(new de.moinmornhart.stinkendreich.paper.block.Haushalt(this, bloecke, mechanik), this);
        unterhaltung = new de.moinmornhart.stinkendreich.paper.block.Unterhaltung(this, bloecke, mechanik);
        kameras = new de.moinmornhart.stinkendreich.paper.block.Kameras(this, bloecke, mechanik);
        getServer().getPluginManager().registerEvents(kameras, this);
        new de.moinmornhart.stinkendreich.paper.block.Aufzug(this, bloecke, mechanik, zugang);
        getServer().getPluginManager().registerEvents(bloecke, this);
        getServer().getPluginManager().registerEvents(mechanik, this);
        butler = new de.moinmornhart.stinkendreich.paper.butler.Butler(this);
        getServer().getPluginManager().registerEvents(butler, this);
        Handy handy = new Handy(this);
        getServer().getPluginManager().registerEvents(handy, this);
        // /handy öffnet das Handy, /butler <Satz> spricht mit James (wie „Butler, …“ im Chat)
        getCommand("handy").setExecutor((wer, befehl, name, args) -> {
            if (wer instanceof Player p) {
                if (args.length >= 2 && args[0].equals("bodyguards")) {
                    if (args[1].equals("heim")) {
                        bodyguards.entlassen(p);
                    } else {
                        bodyguards.rufen(p);
                    }
                } else if (args.length >= 2 && args[0].equals("liefer")) {
                    handy.bestellen(p, args[1]);                     // wie ein Knopf des Lieferservice
                } else if (args.length >= 3 && args[0].equals("smart")) {
                    smartHome.schalten(p, args[1], args[2]);      // wie ein Knopf der Smart-Home-Seite
                } else if (args.length >= 1 && args[0].equals("smart")) {
                    smartHome.seite(p);
                } else {
                    handy.oeffnen(p);
                }
            }
            return true;
        });
        getCommand("butler").setExecutor((wer, befehl, name, args) -> {
            if (wer instanceof Player p) {
                if (args.length == 0) {
                    butler.menue(p, butler.butlerVon(p));
                } else {
                    butler.befehl(p, String.join(" ", args).toLowerCase(java.util.Locale.GERMAN));
                }
            }
            return true;
        });
        fahrzeuge = new de.moinmornhart.stinkendreich.paper.auto.Fahrzeuge(this);
        getServer().getPluginManager().registerEvents(fahrzeuge, this);
        getServer().getPluginManager().registerEvents(paket, this);
        getServer().getPluginManager().registerEvents(this, this);
        VillaBefehle.registrieren(this);
        Autotest.registrieren(this);
        // Villa am Spawn der Hauptwelt (nur in neuen Welten, außer die Config sagt anders)
        World welt = Bukkit.getWorlds().getFirst();
        if (getConfig().getBoolean("villa.amSpawn", true) && villen.spawnVilla(welt) == null
                && (welt.getFullTime() < 20 * 60 || getConfig().getBoolean("villa.auchInAltenWelten", false))) {
            spawnVillaBauen(welt);
        }
        getServer().getScheduler().runTaskTimer(this, this::fortschrittZeigen, 20L, 20L);
    }

    @Override
    public void onDisable() {
        if (inkognito != null) {
            inkognito.stoppen();                        // laufende Fahrt ohne Animation fertig machen
        }
        if (zugbruecke != null) {
            zugbruecke.stoppen();                       // laufende Fahrt fertig machen, keine Blöcke verlieren
        }
        if (kameras != null) {
            kameras.stoppen();                          // Spieler aus dem Kamerablick zurückholen
        }
        if (paket != null) {
            paket.stoppen();
        }
    }

    // ------------------------------------------------------------------
    // Zugriff für die Teile des Plugins
    // ------------------------------------------------------------------

    public File jarDatei() {
        return getFile();
    }

    public Bauplan bauplan() {
        return bauplan;
    }

    public VirtuelleBloecke bloecke() {
        return bloecke;
    }

    public Villen villen() {
        return villen;
    }

    public Ausstattung ausstattung() {
        return ausstattung;
    }

    public FigurenUebersetzen figurenUebersetzen() {
        return figuren;
    }

    public de.moinmornhart.stinkendreich.paper.block.Mechanik mechanik() {
        return mechanik;
    }

    public de.moinmornhart.stinkendreich.paper.butler.Butler butler() {
        return butler;
    }

    public de.moinmornhart.stinkendreich.paper.auto.Fahrzeuge fahrzeuge() {
        return fahrzeuge;
    }

    public SmartHome smartHome() {
        return smartHome;
    }

    public de.moinmornhart.stinkendreich.paper.block.Zugang zugang() {
        return zugang;
    }

    public de.moinmornhart.stinkendreich.paper.block.Zugbruecke zugbruecke() {
        return zugbruecke;
    }

    public de.moinmornhart.stinkendreich.paper.block.Unterhaltung unterhaltung() {
        return unterhaltung;
    }

    public de.moinmornhart.stinkendreich.paper.block.Inkognito inkognito() {
        return inkognito;
    }

    public de.moinmornhart.stinkendreich.paper.butler.Bodyguards bodyguards() {
        return bodyguards;
    }

    public de.moinmornhart.stinkendreich.paper.ausruestung.Agent agent() {
        return agent;
    }

    public de.moinmornhart.stinkendreich.paper.butler.Taenzerinnen taenzerinnen() {
        return taenzerinnen;
    }

    public de.moinmornhart.stinkendreich.paper.hai.Haie haie() {
        return haie;
    }

    public de.moinmornhart.stinkendreich.paper.block.Fernseher fernseher() {
        return fernseher;
    }

    public de.moinmornhart.stinkendreich.paper.block.Festung festung() {
        return festung;
    }

    public de.moinmornhart.stinkendreich.paper.block.Kameras kameras() {
        return kameras;
    }

    public de.moinmornhart.stinkendreich.paper.block.Sicherheit sicherheit() {
        return sicherheit;
    }

    public Gaeste gaeste() {
        return gaeste;
    }

    public Ressourcenpaket paket() {
        return paket;
    }

    // ------------------------------------------------------------------
    // Villen bauen
    // ------------------------------------------------------------------

    public void spawnVillaBauen(World welt) {
        Location spawn = welt.getSpawnLocation();
        bauen(new Baustelle(this, welt, spawn.getBlockX() - bauplan.breiteX / 2, spawn.getBlockZ() - bauplan.breiteZ / 2,
                0, null, "", villa -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld().equals(welt)) {
                    p.teleport(villa.spawn(bauplan));
                }
            }
        }));
    }

    /** Baut eine Villa für einen Spieler im Raster um die Spawn-Villa. */
    public void nachbarBauen(UUID besitzer, String name) {
        Villen.Villa spawnVilla = villen.alle().stream().filter(v -> v.nr() == 0).findFirst().orElse(null);
        if (spawnVilla == null) {
            return;
        }
        int abstand = Math.max(240, getConfig().getInt("villa.abstand", 320));
        int[] platz = villen.freierPlatz(spawnVilla, abstand);
        wartende.add(besitzer);
        bauen(new Baustelle(this, spawnVilla.world(), platz[0], platz[1], villen.naechsteNummer(), besitzer, name, villa -> {
            wartende.remove(besitzer);
            Player p = Bukkit.getPlayer(besitzer);
            if (p != null) {
                p.teleport(villa.spawn(bauplan));
                p.getInventory().addItem(zugang.karte(villa));
                p.sendMessage(Component.text("Willkommen in deiner eigenen Villa! Deine Zugangskarte liegt im Inventar.", NamedTextColor.GOLD));
            }
        }));
    }

    private void bauen(Baustelle b) {
        Runnable start = () -> {
            baustelle = b;
            b.starten();
        };
        if (baustelle == null) {
            start.run();
        } else {
            warteschlange.add(start);
        }
    }

    /** Wird von der Baustelle nach dem Ausstatten gerufen: Figuren der Villa (Butler …) und nächster Bau. */
    public void figurenNachDemBau(Villen.Villa villa) {
        butler.erzeugen(villa);
        getServer().getScheduler().runTask(this, () -> {
            baustelle = null;
            Runnable naechste = warteschlange.poll();
            if (naechste != null) {
                naechste.run();
            }
        });
    }

    private void fortschrittZeigen() {
        if (baustelle == null) {
            return;
        }
        Component text = Component.text("Villa wird gebaut … " + baustelle.prozent() + " %", NamedTextColor.GOLD);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendActionBar(text);
        }
    }

    @EventHandler
    public void beimBetreten(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!getConfig().getBoolean("villa.mehrereVillen", true) || villen.vonBesitzer(p.getUniqueId()) != null
                || wartende.contains(p.getUniqueId())) {
            return;
        }
        Villen.Villa spawnVilla = villen.alle().stream().filter(v -> v.nr() == 0).findFirst().orElse(null);
        if (spawnVilla == null) {
            return;
        }
        if (spawnVilla.besitzer() == null) {
            // Der erste Spieler ist der Herr der Spawn-Villa
            villen.besitzerSetzen(spawnVilla, p.getUniqueId(), p.getName());
            p.getInventory().addItem(zugang.karte(villen.nr(0)));
            return;
        }
        p.sendMessage(Component.text("Deine eigene Villa wird gerade gebaut – gleich bringe ich dich hin.", NamedTextColor.GOLD));
        nachbarBauen(p.getUniqueId(), p.getName());
    }
}
