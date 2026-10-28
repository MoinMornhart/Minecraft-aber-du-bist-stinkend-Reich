package de.moinmornhart.stinkendreich.strasse;

import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.villa.VillaPlatzierung;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Räumt liegengebliebene Gegenstände an den Straßen weg. Die Straße schneidet Bäume, Hänge und Pflanzen an; danach
 * zerfällt das Laub (Setzlinge, Stöcke, Äpfel), schwebende Blumen und Gras fallen ab – und wer die Straße entlangfährt,
 * hatte bald die Taschen voll davon.
 *
 * <p>Weggeräumt wird nur, was niemand geworfen hat ({@link ItemEntity#getOwner()} ist leer), mindestens
 * {@link #MINDESTALTER} Ticks liegt (eigene Abbau-Beute hebt man vorher auf) und höchstens {@link #ABSTAND} Blöcke
 * neben einem Wegpunkt einer Straße liegt – nie auf dem Grundstück einer Villa.</p>
 */
final class Strassenkehrer {
    /** Wie oft gekehrt wird (Ticks). */
    static final int ALLE = 40;
    /** So lange muss ein Gegenstand schon liegen (Ticks). */
    private static final int MINDESTALTER = 100;
    /** Abstand zur Mittellinie (Blöcke, waagerecht); Straße 4,5 breit je Seite + Baumkronen daneben. */
    private static final double ABSTAND = 11;
    private static final double HOEHE = 10;

    /** Wegpunkte je Chunk; neu aufgebaut, sobald sich die Zahl der Punkte ändert. */
    private final Map<Long, List<BlockPos>> raster = new HashMap<>();
    private int punkteImRaster = -1;

    void kehren(ServerLevel welt, List<List<BlockPos>> strecken) {
        int anzahl = 0;
        for (List<BlockPos> s : strecken) {
            anzahl += s.size();
        }
        if (anzahl == 0) {
            return;
        }
        if (anzahl != punkteImRaster) {
            raster.clear();
            for (List<BlockPos> s : strecken) {
                for (BlockPos p : s) {
                    raster.computeIfAbsent(chunk(p.getX() >> 4, p.getZ() >> 4), k -> new ArrayList<>()).add(p);
                }
            }
            punkteImRaster = anzahl;
        }
        List<ItemEntity> weg = new ArrayList<>();
        for (Entity e : welt.getAllEntities()) {
            if (e instanceof ItemEntity item && item.isAlive() && item.getOwner() == null && item.getAge() >= MINDESTALTER
                    && anderStrasse(item) && !aufEinemGrundstueck(item)) {
                weg.add(item);
            }
        }
        for (ItemEntity item : weg) {
            item.discard();
        }
        if (!weg.isEmpty() && de.moinmornhart.stinkendreich.dev.Autotest.aktiv()) {
            StinkendReich.LOGGER.info("Autotest: Straßenkehrer hat {} Gegenstände weggeräumt", weg.size());
        }
    }

    /** Liegt der Gegenstand neben einem Wegpunkt (gleicher oder benachbarter Chunk)? */
    private boolean anderStrasse(ItemEntity item) {
        int cx = item.blockPosition().getX() >> 4, cz = item.blockPosition().getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                List<BlockPos> pkt = raster.get(chunk(cx + dx, cz + dz));
                if (pkt == null) {
                    continue;
                }
                for (BlockPos p : pkt) {
                    double x = item.getX() - (p.getX() + 0.5), z = item.getZ() - (p.getZ() + 0.5);
                    if (x * x + z * z <= ABSTAND * ABSTAND && Math.abs(item.getY() - p.getY()) <= HOEHE) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Auf dem Grundstück einer Villa (nicht nur in der Nähe) bleibt alles liegen. */
    private static boolean aufEinemGrundstueck(ItemEntity item) {
        for (VillaPlatzierung.VillaInfo v : VillaPlatzierung.alle()) {
            if (v.inDerNaehe(item.blockPosition(), 0)) {
                return true;
            }
        }
        return false;
    }

    private static long chunk(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }
}
