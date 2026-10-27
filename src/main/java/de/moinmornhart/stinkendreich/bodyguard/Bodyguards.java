package de.moinmornhart.stinkendreich.bodyguard;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.register.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Bodyguards rufen (zwei Stück, kommen zum Chef) und heimschicken (verschwinden mit einer Rauchwolke). */
public final class Bodyguards {
    /** So viele Leibwächter hat jeder. */
    public static final int ANZAHL = 2;

    private Bodyguards() {
    }

    private static List<BodyguardEntity> vom(ServerPlayer p) {
        List<BodyguardEntity> liste = new ArrayList<>();
        if (p.level() instanceof ServerLevel welt) {
            for (var e : welt.getAllEntities()) {
                if (e instanceof BodyguardEntity b && p.getUUID().equals(b.chef()) && b.isAlive()) {
                    liste.add(b);
                }
            }
        }
        return liste;
    }

    public static void rufen(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel welt)) {
            return;
        }
        List<BodyguardEntity> da = vom(p);
        for (int i = 0; i < da.size(); i++) {
            Vec3 frei = freieStelle(welt, da.get(i), p, p.getYRot() + (i == 0 ? 135 : -135));
            da.get(i).teleportTo(frei.x, frei.y, frei.z);
        }
        for (int i = da.size(); i < ANZAHL; i++) {
            BodyguardEntity b = ModEntities.BODYGUARD.create(welt
                    //? if >=1.21.2
                    , net.minecraft.world.entity.EntitySpawnReason.EVENT
            );
            if (b == null) {
                continue;
            }
            Vec3 frei = freieStelle(welt, b, p, p.getYRot() + (i == 0 ? 135 : -135));
            Kompat.versetzen(b, frei.x, frei.y, frei.z, p.getYRot(), 0.0F);
            b.setzeChef(p);
            welt.addFreshEntity(b);
            welt.sendParticles(ParticleTypes.CLOUD, b.getX(), b.getY() + 1.5, b.getZ(), 20, 0.6, 1.0, 0.6, 0.02);
        }
        Kompat.nachricht(p, Component.translatable("stinkendreich.bodyguard.da").withStyle(ChatFormatting.GOLD), false);
    }

    /**
     * Freie Stelle für einen Golem neben dem Chef (1,4 breit, 2,7 hoch, fester Boden darunter): erst schräg hinter ihm
     * im gewünschten Winkel, dann ringsum und etwas weiter weg. Notfalls die Stelle des Chefs selbst.
     */
    static Vec3 freieStelle(ServerLevel welt, BodyguardEntity b, Player chef, float winkel) {
        for (double r : new double[]{2.5, 3.5, 1.8, 4.5}) {
            for (int i = 0; i < 8; i++) {
                double w = Math.toRadians(winkel + i * 45);
                double x = Math.floor(chef.getX() - Math.sin(w) * r) + 0.5, z = Math.floor(chef.getZ() + Math.cos(w) * r) + 0.5;
                for (int dy : new int[]{0, 1, -1, 2, -2}) {
                    double y = Math.floor(chef.getY()) + dy;
                    BlockPos boden = BlockPos.containing(x, y - 1, z);
                    if (!welt.getBlockState(boden).getCollisionShape(welt, boden).isEmpty()
                            && welt.noCollision(b, b.getBoundingBox().move(x - b.getX(), y - b.getY(), z - b.getZ()))) {
                        return new Vec3(x, y, z);
                    }
                }
            }
        }
        return chef.position();
    }

    public static void entlassen(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel welt)) {
            return;
        }
        for (BodyguardEntity b : vom(p)) {
            welt.sendParticles(ParticleTypes.CLOUD, b.getX(), b.getY() + 1.5, b.getZ(), 20, 0.6, 1.0, 0.6, 0.02);
            b.discard();
        }
        Kompat.nachricht(p, Component.translatable("stinkendreich.bodyguard.weg").withStyle(ChatFormatting.GRAY), false);
    }
}
