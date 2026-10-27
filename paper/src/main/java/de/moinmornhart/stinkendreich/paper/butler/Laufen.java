package de.moinmornhart.stinkendreich.paper.butler;

import org.bukkit.Location;
import org.bukkit.entity.Mob;

/**
 * Bringt den unsichtbaren Körper von James an eine Stelle: der Wegfinder läuft, kommt er 8 Sekunden nicht voran oder
 * dauert der Weg länger als die Frist, springt er hin (er hat ja einen Schlüssel für jede Tür).
 */
final class Laufen {
    private final Mob koerper;
    private int ticks, steckt;
    private Location letzte;

    Laufen(Mob koerper) {
        this.koerper = koerper;
    }

    /**
     * Ein Tick Weg; true, wenn er waagerecht näher als {@code genau} und höchstens {@code hoehe} Blöcke darüber oder
     * darunter steht. {@code frist} in Ticks.
     */
    boolean zu(Location ziel, double genau, double hoehe, int frist) {
        Location hier = koerper.getLocation();
        double dx = hier.getX() - ziel.getX(), dz = hier.getZ() - ziel.getZ();
        if (hier.getWorld().equals(ziel.getWorld()) && dx * dx + dz * dz < genau * genau && Math.abs(hier.getY() - ziel.getY()) <= hoehe) {
            ticks = 0;
            steckt = 0;
            koerper.getPathfinder().stopPathfinding();      // sonst läuft er den alten Weg weiter
            return true;
        }
        if (++ticks > frist) {
            springen(ziel);
            return false;
        }
        if (ticks % 10 == 1) {
            koerper.getPathfinder().moveTo(ziel, 1.0);
            if (letzte != null && letzte.getWorld().equals(hier.getWorld()) && letzte.distanceSquared(hier) < 0.04) {
                if (++steckt > 16) {
                    springen(ziel);
                }
            } else {
                steckt = 0;
            }
            letzte = hier;
        }
        return false;
    }

    private void springen(Location ziel) {
        Location l = ziel.clone();
        l.setYaw(koerper.getLocation().getYaw());
        koerper.getPathfinder().stopPathfinding();
        koerper.teleport(l);
        ticks = 0;
        steckt = 0;
    }
}
