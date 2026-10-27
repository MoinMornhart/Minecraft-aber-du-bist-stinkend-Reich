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
    private de.moinmornhart.stinkendreich.paper.auto.Fahrzeuge fahrzeuge;
    private de.moinmornhart.stinkendreich.paper.block.Zugang zugang;
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
        mechanik = new de.moinmornhart.stinkendreich.paper.block.Mechanik(this, bloecke);
        new de.moinmornhart.stinkendreich.paper.block.Tore(this, bloecke, mechanik);
        zugang = new de.moinmornhart.stinkendreich.paper.block.Zugang(this, bloecke, mechanik);
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
                handy.oeffnen(p);
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
