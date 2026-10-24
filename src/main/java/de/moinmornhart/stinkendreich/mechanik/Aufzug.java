package de.moinmornhart.stinkendreich.mechanik;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.netz.Netz;
import de.moinmornhart.stinkendreich.register.ModSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Fährt Spieler sanft von einer Aufzugstafel zur anderen (0,4 Blöcke pro Tick).
 * Mitfahrer in der Kabine fahren mit.
 */
public final class Aufzug {
    private static final double TEMPO = 0.4;

    private record Fahrt(UUID spieler, double x, double z, double zielY, float blick) {
    }

    private static final List<Fahrt> FAHRTEN = new ArrayList<>();
    /** Wo stand der Spieler gerade? (Y pro Fahrt, nicht gespeichert) */
    private static final java.util.Map<UUID, Double> HOEHE = new java.util.HashMap<>();

    private Aufzug() {
    }

    public static void registrieren() {
        ServerTickEvents.END_SERVER_TICK.register(Aufzug::tick);
        Netz.bearbeiter("aufzug", 6, (spieler, start, wert) -> {
            try {
                BlockPos ziel = BlockPos.of(Long.parseLong(wert));
                if (ziel.getX() != start.getX() || ziel.getZ() != start.getZ()
                        || !(spieler.level().getBlockState(ziel).getBlock() instanceof AufzugstafelBlock)
                        || !(spieler.level().getBlockState(start).getBlock() instanceof AufzugstafelBlock)) {
                    return;
                }
                starten(spieler, start, ziel);
            } catch (NumberFormatException ignoriert) {
                // ungültiges Ziel
            }
        });
    }

    private static void starten(ServerPlayer spieler, BlockPos start, BlockPos ziel) {
        Direction vorne = spieler.level().getBlockState(ziel).getValue(Eigenschaften.RICHTUNG);
        // Die Kabine liegt vor der Tafel; die Füße auf Höhe der Tafel
        double x = ziel.getX() + 0.5 + vorne.getStepX();
        double z = ziel.getZ() + 0.5 + vorne.getStepZ();
        AABB kabine = new AABB(start.relative(vorne)).inflate(1.0, 1.5, 1.0);
        for (ServerPlayer p : spieler.level().getEntitiesOfClass(ServerPlayer.class, kabine, p -> true)) {
            FAHRTEN.removeIf(f -> f.spieler().equals(p.getUUID()));
            FAHRTEN.add(new Fahrt(p.getUUID(), x, z, ziel.getY(), p.getYRot()));
            HOEHE.put(p.getUUID(), p.getY());
        }
        if (FAHRTEN.stream().noneMatch(f -> f.spieler().equals(spieler.getUUID()))) {
            FAHRTEN.add(new Fahrt(spieler.getUUID(), x, z, ziel.getY(), spieler.getYRot()));
            HOEHE.put(spieler.getUUID(), spieler.getY());
        }
        spieler.level().playSound(null, start, ModSounds.GONG, SoundSource.BLOCKS, 0.8F, 1.2F);
    }

    private static void tick(MinecraftServer server) {
        Iterator<Fahrt> it = FAHRTEN.iterator();
        while (it.hasNext()) {
            Fahrt f = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(f.spieler());
            if (p == null) {
                it.remove();
                continue;
            }
            double y = HOEHE.getOrDefault(f.spieler(), p.getY());
            double rest = f.zielY() - y;
            boolean angekommen = Math.abs(rest) <= TEMPO;
            y = angekommen ? f.zielY() : y + Math.signum(rest) * TEMPO;
            HOEHE.put(f.spieler(), y);
            p.fallDistance = 0;
            Kompat.befehl(server, String.format(Locale.ROOT, "tp %s %.3f %.3f %.3f", p.getScoreboardName(), f.x(), y, f.z()));
            if (angekommen) {
                it.remove();
                HOEHE.remove(f.spieler());
                p.level().playSound(null, p.blockPosition(), ModSounds.GONG, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }
    }
}
