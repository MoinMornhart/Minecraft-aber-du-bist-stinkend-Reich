package de.moinmornhart.stinkendreich.butler;

import de.moinmornhart.stinkendreich.auto.HubschrauberEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Der Butler als Pilot: steigt senkrecht auf, fliegt in Reisehöhe (20 Blöcke über dem höchsten Gelände
 * auf den nächsten 64 Blöcken des Weges) zum Ziel, bremst ab und landet senkrecht. Ohne Wegnetz, denn in
 * der Luft ist kaum etwas im Weg; nur in geladenen Chunks wird das Gelände geprüft.
 *
 * <p>Läuft nur auf dem Server; gibt seine Eingaben über {@code chauffeurEingabe(gas, lenk, hoch)} an den
 * Hubschrauber.</p>
 */
public final class Pilot {
    private static final int UEBER_GELAENDE = 20, VORAUS = 64;

    private Vec3 ziel;
    private double reiseHoehe = Double.NaN;
    private int ticks, landeTicks;
    private boolean landet;
    /** Landestelle schon geprüft (trocken und eben, erst in der Nähe, wo die Chunks geladen sind)? */
    private boolean geprueft;

    public enum Lage { FLIEGT, GELANDET }

    /** @param ziel Landepunkt (Höhe egal, er landet auf dem Boden darunter) */
    public Pilot(Vec3 ziel) {
        this.ziel = ziel;
    }

    public Vec3 ziel() {
        return ziel;
    }

    /** Ein Tick am Steuerknüppel. */
    public Lage fliegen(HubschrauberEntity heli) {
        ticks++;
        Level welt = heli.level();
        Vec3 pos = heli.position();
        double dx = ziel.x - pos.x, dz = ziel.z - pos.z;
        double abstand = Math.sqrt(dx * dx + dz * dz);
        if (!geprueft && abstand < 48) {
            // Nicht ins Wasser oder an einen Hang: trockene, ebene Stelle in der Nähe des Ziels
            geprueft = true;
            ziel = landestelle(welt, BlockPos.containing(ziel), 0, 16);
            return Lage.FLIEGT;
        }
        if (ticks % 40 == 1 || Double.isNaN(reiseHoehe)) {
            reiseHoehe = reiseHoehe(welt, pos, dx, dz, abstand);
        }
        float fehler = Mth.wrapDegrees((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F - heli.getYRot());
        double boden = welt.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(pos.x), Mth.floor(pos.z));

        if (landet || abstand < 6.0) {
            // Landeanflug: langsam genau über das Ziel, dort anhalten, dann senkrecht hinunter
            landet = true;
            landeTicks++;
            // Steht er (spätestens nach 30 Sekunden irgendwo), gilt er als gelandet
            if (heli.onGround() && (abstand < 3.0 || landeTicks > 600)) {
                heli.chauffeurEingabe(0, 0, 0);
                return Lage.GELANDET;
            }
            double max = heli.hoechstTempo();
            double soll = abstand < 0.8 ? 0 : Math.min(0.25, abstand * 0.06);
            if (Math.abs(fehler) > 25 && abstand >= 0.8) {
                soll = 0;   // erst zum Ziel drehen
            }
            double gas = Mth.clamp((soll - heli.tempo()) / max * 4.0 + soll / max, -1, 1);
            double drehen = abstand < 0.8 ? 0 : Mth.clamp(-fehler / 30.0F, -1, 1);
            boolean ueberDemZiel = (abstand < 1.5 && Math.abs(heli.tempo()) < 0.08) || landeTicks > 600;
            heli.chauffeurEingabe(gas, drehen, ueberDemZiel ? -1 : 0);
            return Lage.FLIEGT;
        }
        // Höhe halten bzw. erreichen
        double hoch = pos.y < reiseHoehe - 1.5 ? 1 : (pos.y > reiseHoehe + 2.5 ? -1 : 0);
        // Erst nach oben, bevor es vorwärts geht (nicht in Bäume oder die Villa)
        boolean frei = pos.y > boden + 8 || pos.y > reiseHoehe - 4;
        double gas = 0;
        if (frei && Math.abs(fehler) < 25) {
            gas = Mth.clamp(abstand / 70.0, 0.12, 1.0);
        } else if (frei && Math.abs(fehler) < 60) {
            gas = 0.15;
        }
        heli.chauffeurEingabe(gas, Mth.clamp(-fehler / 25.0F, -1, 1), hoch);
        return Lage.FLIEGT;
    }

    /** Reisehöhe: höchstes Gelände auf den nächsten 64 Blöcken des Weges (geladene Chunks) plus 20. */
    private double reiseHoehe(Level welt, Vec3 pos, double dx, double dz, double abstand) {
        double hoechste = welt.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(pos.x), Mth.floor(pos.z));
        double weit = Math.min(VORAUS, abstand);
        for (double d = 4; d <= weit; d += 4) {
            int x = Mth.floor(pos.x + dx / abstand * d), z = Mth.floor(pos.z + dz / abstand * d);
            if (welt.hasChunkAt(new BlockPos(x, 0, z))) {
                hoechste = Math.max(hoechste, welt.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z));
            }
        }
        return hoechste + UEBER_GELAENDE;
    }

    /**
     * Landeplatz nahe {@code mitte}: höchster Block der Spalte (kein Wasser, keine Lava), auf einer Fläche,
     * die ungefähr eben ist. Sucht in Ringen bis {@code radius} Blöcke.
     */
    public static Vec3 landestelle(Level welt, BlockPos mitte, int vonRadius, int radius) {
        for (int r = vonRadius; r <= radius; r += 2) {
            int schritte = Math.max(1, r * 6);
            for (int i = 0; i < schritte; i++) {
                double w = Math.PI * 2 * i / schritte;
                int x = mitte.getX() + (int) Math.round(Math.cos(w) * r);
                int z = mitte.getZ() + (int) Math.round(Math.sin(w) * r);
                int y = welt.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                if (eben(welt, x, y, z)) {
                    return new Vec3(x + 0.5, y, z + 0.5);
                }
            }
        }
        return Vec3.atBottomCenterOf(mitte);
    }

    private static boolean eben(Level welt, int x, int y, int z) {
        for (int ox = -2; ox <= 2; ox += 2) {
            for (int oz = -2; oz <= 2; oz += 2) {
                int h = welt.getHeight(Heightmap.Types.MOTION_BLOCKING, x + ox, z + oz);
                BlockPos unten = new BlockPos(x + ox, h - 1, z + oz);
                if (Math.abs(h - y) > 1 || !welt.getFluidState(unten).isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }
}
