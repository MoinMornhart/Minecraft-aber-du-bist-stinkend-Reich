package de.moinmornhart.stinkendreich.paper.block;

import de.moinmornhart.stinkendreich.paper.Dialoge;
import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Überwachungskameras am Monitor wie in der Server-Version der Mod: Der Monitor zeigt eine Liste der Kameras; nach der
 * Wahl schaut der Spieler durch eine unsichtbare Hilfsfigur vor der Kamera (dafür kurz Zuschauer-Modus, der Körper
 * bleibt am Monitor). Springen wechselt zur nächsten Kamera, Schleichen beendet den Blick.
 */
public final class Kameras implements Listener {
    private static final int SUCHRADIUS = 96;

    /** Ein laufender Kamerablick. */
    private static final class Blick {
        final List<Block> kameras;
        final GameMode modus;
        final Location zurueck;
        int nummer, takte, warten;
        boolean sprangVorher;
        ItemDisplay figur;

        Blick(List<Block> kameras, int nummer, GameMode modus, Location zurueck) {
            this.kameras = kameras;
            this.nummer = nummer;
            this.modus = modus;
            this.zurueck = zurueck;
        }
    }

    private final StinkendReichPaper plugin;
    private final VirtuelleBloecke bloecke;
    private final Map<UUID, Blick> blicke = new HashMap<>();
    /** Merkt sich Spielmodus und Ort, falls der Spieler mitten im Blick den Server verlässt. */
    private final NamespacedKey schluesselZurueck;

    public Kameras(StinkendReichPaper plugin, VirtuelleBloecke bloecke, Mechanik mechanik) {
        this.plugin = plugin;
        this.bloecke = bloecke;
        this.schluesselZurueck = new NamespacedKey(plugin, "kamera_zurueck");
        mechanik.anmelden("monitor", (p, b, z) -> {
            auswahl(p, b);
            return true;
        });
        Bukkit.getScheduler().runTaskTimer(plugin, this::takt, 1L, 1L);
    }

    /** Alle Kameras um den Monitor, sortiert nach Höhe und Lage (wie in der Mod). */
    private List<Block> kameras(Block monitor) {
        List<Block> liste = new ArrayList<>();
        for (Block b : bloecke.alle("kamera")) {
            if (b.getWorld().equals(monitor.getWorld()) && b.getLocation().distanceSquared(monitor.getLocation()) < SUCHRADIUS * SUCHRADIUS) {
                liste.add(b);
            }
        }
        liste.sort(Comparator.comparingInt(Block::getY).thenComparingInt(Block::getX).thenComparingInt(Block::getZ));
        return liste;
    }

    public void auswahl(Player p, Block monitor) {
        List<Block> liste = kameras(monitor);
        if (liste.isEmpty()) {
            p.sendActionBar(Component.text("Keine Kamera in der Nähe.", NamedTextColor.GRAY));
            return;
        }
        List<Dialoge.Knopf> k = new ArrayList<>();
        for (int i = 0; i < liste.size(); i++) {
            int nr = i;
            k.add(Dialoge.Knopf.von("Kamera " + (i + 1), (s, a) -> starten(s, liste, nr)));
        }
        Dialoge.zeigen(p, "Überwachung", "Kamera wählen. Springen: nächste Kamera, Schleichen: zurück.", List.of(), k);
    }

    /** Startet den Blick durch eine Kamera (auch für Tests). */
    public void starten(Player p, List<Block> liste, int nummer) {
        Blick alt = blicke.remove(p.getUniqueId());
        Blick b = alt != null ? new Blick(liste, nummer, alt.modus, alt.zurueck) : new Blick(liste, nummer, p.getGameMode(), p.getLocation());
        if (alt != null && alt.figur != null) {
            alt.figur.remove();
        }
        p.getPersistentDataContainer().set(schluesselZurueck, PersistentDataType.STRING, b.modus.name() + "|" + ortText(b.zurueck));
        p.setGameMode(GameMode.SPECTATOR);
        figurSetzen(p, b);
        blicke.put(p.getUniqueId(), b);
    }

    public void startenAmMonitor(Player p, Block monitor, int nummer) {
        List<Block> liste = kameras(monitor);
        if (!liste.isEmpty()) {
            starten(p, liste, Math.floorMod(nummer, liste.size()));
        }
    }

    private void figurSetzen(Player p, Blick b) {
        if (b.figur != null) {
            b.figur.remove();
        }
        Block k = b.kameras.get(b.nummer);
        Zustand z = bloecke.zustand(k);
        BlockFace vorne = z == null || z.wert("facing") == null ? BlockFace.NORTH
                : BlockFace.valueOf(z.wert("facing").toUpperCase(java.util.Locale.ROOT));
        Location ort = new Location(k.getWorld(), k.getX() + 0.5 + vorne.getModX() * 0.6, k.getY() + 0.4, k.getZ() + 0.5 + vorne.getModZ() * 0.6,
                switch (vorne) {
                    case SOUTH -> 0.0F;
                    case WEST -> 90.0F;
                    case EAST -> 270.0F;
                    default -> 180.0F;
                }, 25.0F);
        b.figur = k.getWorld().spawn(ort, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setTeleportDuration(0);
        });
        p.teleport(ort);
        b.warten = 3;
    }

    private void takt() {
        for (Map.Entry<UUID, Blick> e : Map.copyOf(blicke).entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            Blick b = e.getValue();
            if (p == null) {
                continue;
            }
            if (b.warten > 0) {
                if (--b.warten == 0) {
                    p.setSpectatorTarget(b.figur);
                }
                continue;
            }
            // Schleichen beendet (Minecraft löst dann auch die Kamera vom Ziel)
            if (p.getCurrentInput().isSneak() || p.getSpectatorTarget() == null || p.getGameMode() != GameMode.SPECTATOR) {
                beenden(p, b);
                continue;
            }
            boolean springt = p.getCurrentInput().isJump();
            if (springt && !b.sprangVorher) {
                b.nummer = (b.nummer + 1) % b.kameras.size();
                p.setSpectatorTarget(null);
                figurSetzen(p, b);
            }
            b.sprangVorher = springt;
            if (b.takte++ % 40 == 0) {
                p.sendActionBar(Component.text("Kamera " + (b.nummer + 1) + "/" + b.kameras.size() + " · Springen: nächste · Schleichen: zurück",
                        NamedTextColor.GRAY));
            }
        }
    }

    private void beenden(Player p, Blick b) {
        blicke.remove(p.getUniqueId());
        if (b.figur != null) {
            b.figur.remove();
        }
        p.setSpectatorTarget(null);
        p.teleport(b.zurueck);
        p.setGameMode(b.modus);
        p.getPersistentDataContainer().remove(schluesselZurueck);
    }

    @EventHandler
    public void beimVerlassen(PlayerQuitEvent e) {
        Blick b = blicke.get(e.getPlayer().getUniqueId());
        if (b != null) {
            beenden(e.getPlayer(), b);
        }
    }

    /** Server mitten im Blick abgestürzt: beim nächsten Betreten Spielmodus und Ort zurück. */
    @EventHandler
    public void beimBetreten(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String text = p.getPersistentDataContainer().get(schluesselZurueck, PersistentDataType.STRING);
        if (text == null) {
            return;
        }
        p.getPersistentDataContainer().remove(schluesselZurueck);
        String[] t = text.split("\\|");
        try {
            String[] o = t[1].split(" ");
            p.teleport(new Location(Bukkit.getWorld(UUID.fromString(o[0])), Double.parseDouble(o[1]), Double.parseDouble(o[2]),
                    Double.parseDouble(o[3]), Float.parseFloat(o[4]), Float.parseFloat(o[5])));
            p.setGameMode(GameMode.valueOf(t[0]));
        } catch (Exception ignoriert) {
            p.setGameMode(Bukkit.getDefaultGameMode());
        }
    }

    private static String ortText(Location l) {
        return l.getWorld().getUID() + " " + l.getX() + " " + l.getY() + " " + l.getZ() + " " + l.getYaw() + " " + l.getPitch();
    }

    /** Beim Beenden des Servers alle zurückholen. */
    public void stoppen() {
        for (Map.Entry<UUID, Blick> e : Map.copyOf(blicke).entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                beenden(p, e.getValue());
            }
        }
    }
}
