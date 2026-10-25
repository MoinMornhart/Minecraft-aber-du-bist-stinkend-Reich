package de.moinmornhart.stinkendreich.mechanik;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.register.ModDeko;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * Bad in Goldmünzen wie im Geldspeicher: Der Block „goldmuenzen“ hat keine feste Oberfläche. Wer hineinspringt,
 * sinkt langsam ein, wird gebremst und nimmt keinen Fallschaden.
 */
public final class Goldbad {
    private Goldbad() {
    }

    public static void registrieren() {
        ServerTickEvents.END_SERVER_TICK.register(Goldbad::tick);
    }

    private static void tick(MinecraftServer server) {
        Block muenzen = ModDeko.BLOECKE.get("goldmuenzen");
        if (muenzen == null) {
            return;
        }
        for (ServerPlayer p : server.overworld().players()) {
            if (p.isSpectator() || !(p.level().getBlockState(p.blockPosition()).is(muenzen)
                    || p.level().getBlockState(p.blockPosition().above()).is(muenzen))) {
                continue;
            }
            p.resetFallDistance();
            Vec3 v = p.getDeltaMovement();
            // Langsam einsinken, zur Seite nur gebremst; wer sich nach oben bewegt, wird nicht aufgehalten
            p.setDeltaMovement(v.x * 0.6, Math.max(v.y, -0.08), v.z * 0.6);
            Kompat.bewegungSenden(p);
        }
    }
}
