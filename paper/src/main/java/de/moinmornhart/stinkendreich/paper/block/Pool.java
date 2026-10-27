package de.moinmornhart.stinkendreich.paper.block;

import com.google.gson.JsonArray;
import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import de.moinmornhart.stinkendreich.paper.villa.Villen;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

/**
 * Wasserrutsche und Goldmünzen-Becken auf dem Paper-Server (wie {@code Rutsche} und {@code Goldbad} in der Mod).
 * <ul>
 *   <li>Rutsche (Marke „rutsche“: Kasten und Richtung bergab): Wer in der Rinne im Wasser liegt, wird kräftig bergab
 *   geschoben und zur Mitte gezogen; am Ende fällt man in den Pool.</li>
 *   <li>Goldmünzen: In der Mod kletterbar wie ein Gerüst. Hier sind sie eine Strukturleere ohne Oberfläche, deshalb
 *   bewegt das Plugin selbst: langsam einsinken, mit Schleichen stehen bleiben, mit der Leertaste hoch. Fallschaden gibt
 *   es darin nie.</li>
 * </ul>
 */
public final class Pool implements Listener {
    /** Tempo in der Rinne (Blöcke pro Tick) und im Münzbad. */
    private static final double RUTSCHE_TEMPO = 0.55, SINKEN = -0.12, KLETTERN = 0.2;

    private final StinkendReichPaper plugin;
    private final VirtuelleBloecke bloecke;

    public Pool(StinkendReichPaper plugin, VirtuelleBloecke bloecke) {
        this.plugin = plugin;
        this.bloecke = bloecke;
        Bukkit.getScheduler().runTaskTimer(plugin, this::takt, 1L, 1L);
    }

    private void takt() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (inMuenzen(p)) {
                goldbad(p);
            } else if (p.isInWater()) {
                rutsche(p);
            }
        }
    }

    // ------------------------------------------------------------------
    // Rutsche
    // ------------------------------------------------------------------

    private void rutsche(Player p) {
        Villen.Villa v = plugin.villen().bei(p.getLocation(), 0);
        if (v == null || plugin.inkognito().aktiv(v) || plugin.inkognito().versteckt(v)) {
            return;                                         // versenkt liegt die Rutsche nicht an ihrer Marke
        }
        Location l = p.getLocation();
        for (JsonArray m : plugin.bauplan().marke("rutsche")) {
            double ax = v.x() + m.get(0).getAsDouble(), ay = v.y() + m.get(1).getAsDouble(), az = v.z() + m.get(2).getAsDouble();
            double bx = v.x() + m.get(3).getAsDouble() + 1, by = v.y() + m.get(4).getAsDouble() + 1, bz = v.z() + m.get(5).getAsDouble() + 1;
            if (l.getX() < ax || l.getX() > bx || l.getY() < ay || l.getY() > by || l.getZ() < az || l.getZ() > bz) {
                continue;
            }
            double dx = m.get(6).getAsDouble(), dz = m.get(7).getAsDouble();
            // Quer zur Rinne sanft zur Mitte ziehen, damit man nicht an den Glaswänden hängen bleibt
            double querX = dx == 0 ? ((ax + bx) / 2 - l.getX()) * 0.3 : 0;
            double querZ = dz == 0 ? ((az + bz) / 2 - l.getZ()) * 0.3 : 0;
            Vector alt = p.getVelocity();
            p.setVelocity(new Vector(dx * RUTSCHE_TEMPO + querX, Math.min(alt.getY(), 0.0) - 0.04, dz * RUTSCHE_TEMPO + querZ));
            return;
        }
    }

    // ------------------------------------------------------------------
    // Goldmünzen
    // ------------------------------------------------------------------

    private boolean muenzen(Block b) {
        Zustand z = bloecke.zustand(b);
        return z != null && z.name().equals("goldmuenzen");
    }

    private boolean inMuenzen(Player p) {
        Block fuesse = p.getLocation().getBlock();
        return muenzen(fuesse) || muenzen(fuesse.getRelative(0, 1, 0));
    }

    private static void goldbad(Player p) {
        p.setFallDistance(0);
        Vector v = p.getVelocity();
        double hoch;
        if (p.getCurrentInput().isJump()) {
            hoch = KLETTERN;                                // Leertaste: wieder hoch
        } else if (p.isSneaking()) {
            hoch = 0.0;                                     // Schleichen: stehen bleiben
        } else {
            hoch = Math.max(v.getY(), SINKEN);              // langsam einsinken
        }
        if (Math.abs(hoch - v.getY()) > 1.0E-3) {
            p.setVelocity(new Vector(v.getX() * 0.6, hoch, v.getZ() * 0.6));
        }
    }

    /** Wer in die Münzen springt, nimmt keinen Fallschaden (auch beim Aufprall im selben Tick). */
    @EventHandler(ignoreCancelled = true)
    public void beimSchaden(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && e.getEntity() instanceof Player p && inMuenzen(p)) {
            e.setCancelled(true);
        }
    }
}
