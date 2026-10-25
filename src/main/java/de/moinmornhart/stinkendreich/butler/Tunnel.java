package de.moinmornhart.stinkendreich.butler;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Gräbt einen begehbaren Tunnel zu einem Ziel unter der Erde: Schritt für Schritt eine Treppe
 * (zwei Blöcke hoch) nach unten, oben oder geradeaus. Auf demselben Weg kommt der Butler auch
 * wieder heraus.
 */
public final class Tunnel {
    public enum Ergebnis { LAEUFT, NAH, BLOCKIERT }

    private final Abbau abbau = new Abbau();
    /** Blöcke, die für den aktuellen Schritt noch weg müssen. */
    private final List<BlockPos> offen = new ArrayList<>();
    /** Wohin der Butler nach dem Freiräumen tritt. */
    private BlockPos schrittZiel;
    private int schrittTicks;
    /** Letzte waagerechte Richtung (für senkrechte Strecken im Zickzack). */
    private int letzteDx = 1, letzteDz;

    /** Arbeitet einen Tick am Tunnel. {@link Ergebnis#NAH}: Ziel ist in Reichweite. */
    public Ergebnis tick(ButlerEntity butler, BlockPos ziel, Item werkzeug) {
        ServerLevel welt = butler.welt();
        // 1. Blöcke des aktuellen Schritts wegräumen
        while (!offen.isEmpty()) {
            BlockPos p = offen.get(0);
            if (!imWeg(welt, p)) {
                offen.remove(0);
                continue;
            }
            Abbau.Ergebnis e = abbau.tick(butler, p, werkzeug, s -> false);
            if (e == Abbau.Ergebnis.VERBOTEN) {
                offen.clear();
                schrittZiel = null;
                return Ergebnis.BLOCKIERT;
            }
            if (e == Abbau.Ergebnis.FERTIG) {
                offen.remove(0);
            }
            return Ergebnis.LAEUFT;
        }
        // 2. In das freie Stück treten
        if (schrittZiel != null) {
            Vec3 mitte = Vec3.atBottomCenterOf(schrittZiel);
            double dx = butler.getX() - mitte.x, dz = butler.getZ() - mitte.z;
            if (dx * dx + dz * dz < 0.09 && Math.abs(butler.getY() - mitte.y) < 0.6) {
                schrittZiel = null;
            } else {
                butler.getMoveControl().setWantedPosition(mitte.x, mitte.y, mitte.z, 1.0);
                if (++schrittTicks > 40) {
                    // klemmt: direkt hineinsetzen (die Stelle ist frei geräumt)
                    butler.teleportTo(mitte.x, mitte.y, mitte.z);
                    schrittZiel = null;
                }
                return Ergebnis.LAEUFT;
            }
        }
        // 3. Nah genug? Dann übernimmt der Abbau mit Sichtlinie
        if (butler.getEyePosition().distanceToSqr(Vec3.atCenterOf(ziel)) <= 4.0 * 4.0) {
            return Ergebnis.NAH;
        }
        // 4. Nächsten Schritt planen
        BlockPos fuss = butler.blockPosition();
        int hx = ziel.getX() - fuss.getX();
        int hz = ziel.getZ() - fuss.getZ();
        int dy = ziel.getY() - fuss.getY();
        int sx = 0, sz = 0;
        if (Math.abs(hx) >= Math.abs(hz) && hx != 0) {
            sx = Integer.signum(hx);
        } else if (hz != 0) {
            sz = Integer.signum(hz);
        } else {
            // genau darüber oder darunter: im Zickzack hin und her
            letzteDx = -letzteDx;
            letzteDz = -letzteDz;
            sx = letzteDx;
            sz = letzteDz;
        }
        if (sx != 0 || sz != 0) {
            letzteDx = sx;
            letzteDz = sz;
        }
        int sy = dy < 0 ? -1 : (dy > 1 ? 1 : 0);
        BlockPos neu = fuss.offset(sx, sy, sz);
        if (sy < 0) {
            frei(welt, neu.above(2));
        } else if (sy > 0) {
            frei(welt, fuss.above(2));
        }
        frei(welt, neu.above());
        frei(welt, neu);
        schrittZiel = neu;
        schrittTicks = 0;
        return Ergebnis.LAEUFT;
    }

    private void frei(ServerLevel welt, BlockPos p) {
        if (imWeg(welt, p)) {
            offen.add(p.immutable());
        }
    }

    private static boolean imWeg(ServerLevel welt, BlockPos p) {
        BlockState s = welt.getBlockState(p);
        return !s.getCollisionShape(welt, p).isEmpty();
    }

    /**
     * Legt ein Ziel in Reichweite frei: Steht etwas zwischen Auge und Ziel, wird das zuerst abgebaut.
     * @return Block, an dem jetzt gearbeitet werden muss (das Ziel selbst oder ein Hindernis)
     */
    public static BlockPos naechsterBlock(ButlerEntity butler, BlockPos ziel) {
        Vec3 auge = butler.getEyePosition();
        BlockHitResult treffer = butler.level().clip(new ClipContext(auge, Vec3.atCenterOf(ziel),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, butler));
        if (treffer.getType() == HitResult.Type.MISS) {
            return ziel;
        }
        return treffer.getBlockPos();
    }

    public void abbrechen(ButlerEntity butler) {
        abbau.zuruecksetzen(butler);
        offen.clear();
        schrittZiel = null;
    }
}
