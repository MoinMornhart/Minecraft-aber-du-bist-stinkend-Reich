package de.moinmornhart.stinkendreich.mechanik;

import de.moinmornhart.stinkendreich.register.ModDeko;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

/**
 * Bad in Goldmünzen wie im Geldspeicher: Der Block „goldmuenzen“ hat keine feste Oberfläche und ist kletterbar
 * (Block-Tag minecraft:climbable, wie ein Gerüst): Wer hineinspringt, sinkt langsam ein, mit Schleichen bleibt man
 * stehen, mit der Leertaste kommt man wieder hoch. Hier sorgt der Server nur dafür, dass niemand Fallschaden nimmt
 * (die Bewegung selbst steuert das Spiel des Spielers).
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
        }
    }
}
