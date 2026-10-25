package de.moinmornhart.stinkendreich.bodyguard;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.register.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

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
        for (BodyguardEntity b : da) {
            b.teleportTo(p.getX() + (welt.getRandom().nextDouble() - 0.5) * 4, p.getY(), p.getZ() + (welt.getRandom().nextDouble() - 0.5) * 4);
        }
        for (int i = da.size(); i < ANZAHL; i++) {
            BodyguardEntity b = ModEntities.BODYGUARD.create(welt
                    //? if >=1.21.2
                    , net.minecraft.world.entity.EntitySpawnReason.EVENT
            );
            if (b == null) {
                continue;
            }
            double w = Math.toRadians(p.getYRot() + (i == 0 ? 135 : -135));
            BlockPos platz = BlockPos.containing(p.getX() - Math.sin(w) * 2.5, p.getY(), p.getZ() + Math.cos(w) * 2.5);
            Kompat.versetzen(b, platz.getX() + 0.5, platz.getY(), platz.getZ() + 0.5, p.getYRot(), 0.0F);
            b.setzeChef(p);
            welt.addFreshEntity(b);
            welt.sendParticles(ParticleTypes.CLOUD, b.getX(), b.getY() + 1.5, b.getZ(), 20, 0.6, 1.0, 0.6, 0.02);
        }
        Kompat.nachricht(p, Component.translatable("stinkendreich.bodyguard.da").withStyle(ChatFormatting.GOLD), false);
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
