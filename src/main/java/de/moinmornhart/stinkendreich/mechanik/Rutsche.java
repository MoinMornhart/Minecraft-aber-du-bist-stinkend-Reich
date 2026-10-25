package de.moinmornhart.stinkendreich.mechanik;

import com.google.gson.JsonArray;
import de.moinmornhart.stinkendreich.villa.VillaPlatzierung;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Wasserrutsche am Pool: Wer in der Rinne (Marke „rutsche“: Kasten und Richtung bergab) im Wasser liegt,
 * wird kräftig bergab geschoben und zur Mitte der Rinne gelenkt. Am Ende fällt man in den Pool.
 */
public final class Rutsche {
    /** Tempo in der Rinne (Blöcke pro Tick). */
    private static final double TEMPO = 0.55;

    private Rutsche() {
    }

    public static void registrieren() {
        ServerTickEvents.END_SERVER_TICK.register(Rutsche::tick);
    }

    private static void tick(MinecraftServer server) {
        var villa = VillaPlatzierung.villa().orElse(null);
        if (villa == null) {
            return;
        }
        for (JsonArray m : villa.plan().marke("rutsche")) {
            Vec3 a = Vec3.atLowerCornerOf(villa.weltPos(m.get(0).getAsDouble(), m.get(1).getAsDouble(), m.get(2).getAsDouble()));
            Vec3 b = Vec3.atLowerCornerOf(villa.weltPos(m.get(3).getAsDouble(), m.get(4).getAsDouble(), m.get(5).getAsDouble()))
                    .add(1, 1, 1);
            double dx = m.get(6).getAsDouble(), dz = m.get(7).getAsDouble();
            for (ServerPlayer p : server.overworld().players()) {
                if (!p.isInWater() || p.isSpectator() || p.getX() < a.x || p.getX() > b.x || p.getY() < a.y || p.getY() > b.y
                        || p.getZ() < a.z || p.getZ() > b.z) {
                    continue;
                }
                // Quer zur Rinne sanft zur Mitte ziehen, damit man nicht an den Glaswänden hängen bleibt
                double mitteX = (a.x + b.x) / 2, mitteZ = (a.z + b.z) / 2;
                double querX = dx == 0 ? (mitteX - p.getX()) * 0.3 : 0;
                double querZ = dz == 0 ? (mitteZ - p.getZ()) * 0.3 : 0;
                Vec3 v = p.getDeltaMovement();
                p.setDeltaMovement(dx * TEMPO + querX, Math.min(v.y, 0.0) - 0.04, dz * TEMPO + querZ);
                if (p.tickCount % 10 == 0) {
                    de.moinmornhart.stinkendreich.StinkendReich.LOGGER.info("Rutsche: schiebe {} bei {}", p.getName().getString(), p.position());
                }
                // Bewegung sofort an den Rechner des Spielers schicken (Feld heißt ab 26.3 anders)
                //? if >=26.3 {
                p.needsSync = true;
                //?} else
                /*p.hurtMarked = true;*/
            }
        }
    }
}
