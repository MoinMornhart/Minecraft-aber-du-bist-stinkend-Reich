package de.moinmornhart.stinkendreich.paper.strasse;

import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import org.bukkit.World;
import org.bukkit.entity.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Räumt liegengebliebene Gegenstände an den Straßen weg (wie {@code Strassenkehrer} in der Mod): Die Straße schneidet
 * Bäume und Hänge an, danach zerfällt das Laub und schwebende Pflanzen fallen ab – wer die Straße entlangfährt, hatte
 * bald die Taschen voll. Weg kommt nur, was niemand geworfen hat, mindestens 5 s liegt, höchstens {@link #ABSTAND}
 * Blöcke neben einem Wegpunkt und nicht auf dem Grundstück einer Villa.
 */
final class Strassenkehrer {
    static final int ALLE = 40;
    private static final int MINDESTALTER = 100;
    private static final double ABSTAND = 11;
    private static final double HOEHE = 10;

    private final StinkendReichPaper plugin;
    private final Map<Long, List<int[]>> raster = new HashMap<>();
    private int punkteImRaster = -1;

    Strassenkehrer(StinkendReichPaper plugin) {
        this.plugin = plugin;
    }

    void kehren(World welt, List<List<int[]>> strecken) {
        int anzahl = 0;
        for (List<int[]> s : strecken) {
            anzahl += s.size();
        }
        if (anzahl == 0 || welt == null) {
            return;
        }
        if (anzahl != punkteImRaster) {
            raster.clear();
            for (List<int[]> s : strecken) {
                for (int[] p : s) {
                    raster.computeIfAbsent(chunk(p[0] >> 4, p[2] >> 4), k -> new ArrayList<>()).add(p);
                }
            }
            punkteImRaster = anzahl;
        }
        int weg = 0;
        for (Item item : welt.getEntitiesByClass(Item.class)) {
            if (item.isValid() && item.getThrower() == null && item.getTicksLived() >= MINDESTALTER && anderStrasse(item)
                    && plugin.villen().bei(item.getLocation(), 0) == null) {
                item.remove();
                weg++;
            }
        }
        if (weg > 0 && System.getenv("STINKENDREICH_AUTOTEST") != null) {
            plugin.getLogger().info("Autotest: Straßenkehrer hat " + weg + " Gegenstände weggeräumt");
        }
    }

    private boolean anderStrasse(Item item) {
        double ix = item.getLocation().getX(), iy = item.getLocation().getY(), iz = item.getLocation().getZ();
        int cx = (int) Math.floor(ix) >> 4, cz = (int) Math.floor(iz) >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                List<int[]> pkt = raster.get(chunk(cx + dx, cz + dz));
                if (pkt == null) {
                    continue;
                }
                for (int[] p : pkt) {
                    double x = ix - (p[0] + 0.5), z = iz - (p[2] + 0.5);
                    if (x * x + z * z <= ABSTAND * ABSTAND && Math.abs(iy - p[1]) <= HOEHE) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static long chunk(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }
}
