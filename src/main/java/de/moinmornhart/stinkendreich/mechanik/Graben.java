package de.moinmornhart.stinkendreich.mechanik;

import com.google.gson.JsonArray;
import de.moinmornhart.stinkendreich.Konfig;
import de.moinmornhart.stinkendreich.villa.VillaPlatzierung;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Lava-Graben: Nur im Lockdown (Stufe 2 und 3) und in der Festung (Stufe 4) füllt sich der Graben rund um das
 * Grundstück mit Lava, sonst ist Wasser darin. Getauscht wird in zwei Schritten: erst alles leeren, im nächsten Tick neu füllen. So berühren
 * sich Wasser und Lava nie (das gäbe Obsidian). Blöcke, die jemand in den Graben gebaut hat, bleiben.
 * Ein- und ausschalten: Config „lavaGraben“. Eis im Graben (aus der Zeit vor 0.4.3 oder von Frostläufer-Stiefeln)
 * taut er wieder auf; neues Eis entsteht auf dem Grundstück nicht mehr ({@code KeinEisMixin}).
 */
public final class Graben {
    /** So oft wird nachgesehen, ob jemand zu Hause ist (Ticks). */
    private static final int PRUEFEN_ALLE = 40;

    /** Graben je Villa: Felder des Rings, ob gerade Lava drin ist, und ob er im nächsten Tick neu gefüllt wird. */
    private static final class Ring {
        List<BlockPos> felder;
        /** Oberste Lage des Rings (nur dort friert Wasser). */
        List<BlockPos> oben;
        Boolean istLava;
        Boolean fuellenMitLava;
    }

    private static final java.util.Map<Integer, Ring> RINGE = new java.util.concurrent.ConcurrentHashMap<>();

    private Graben() {
    }

    public static void registrieren() {
        ServerTickEvents.END_SERVER_TICK.register(Graben::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> RINGE.clear());
    }

    private static void tick(MinecraftServer server) {
        for (VillaPlatzierung.VillaInfo villa : VillaPlatzierung.alle()) {
            if (!Inkognito.aktiv(server, villa.nr())) {                // Villa versteckt: kein Graben
                tick(server, villa, RINGE.computeIfAbsent(villa.nr(), n -> new Ring()));
            }
        }
    }

    private static void tick(MinecraftServer server, VillaPlatzierung.VillaInfo villa, Ring r) {
        ServerLevel welt = server.overworld();
        if (r.fuellenMitLava != null) {
            fuellen(welt, r, r.fuellenMitLava);
            return;
        }
        if (server.getTickCount() % PRUEFEN_ALLE != 0 || villa.plan().marke("graben").isEmpty()) {
            return;
        }
        if (r.felder == null) {
            r.felder = felderBerechnen(villa);
            int y1 = r.felder.stream().mapToInt(BlockPos::getY).max().orElse(0);
            r.oben = r.felder.stream().filter(p -> p.getY() == y1).toList();
        }
        if (r.felder.isEmpty() || !welt.isLoaded(r.felder.get(0)) || !welt.isLoaded(r.felder.get(r.felder.size() - 1))) {
            return;
        }
        VillaZustand zustand = VillaZustand.get(server, villa.nr());
        boolean soll = Konfig.get().lavaGraben
                && zustand.stufe() >= 2;                 // nur im Lockdown und in der Festung (Philip, 26.10.)
        if (r.istLava == null) {
            r.istLava = welt.getBlockState(r.felder.get(r.felder.size() / 2)).is(Blocks.LAVA);
        }
        if (!r.istLava) {
            enteisen(welt, r);
        }
        if (soll != r.istLava) {
            leeren(welt, r);
            r.fuellenMitLava = soll;
            zustand.anBewohner(Component.translatable(soll ? "stinkendreich.graben.lava" : "stinkendreich.graben.wasser")
                    .withStyle(soll ? ChatFormatting.GOLD : ChatFormatting.AQUA), true);
        }
    }

    /** Eis auf dem Wasser des Grabens wieder zu Wasser machen. */
    private static void enteisen(ServerLevel welt, Ring r) {
        int n = 0;
        for (BlockPos p : r.oben) {
            BlockState b = welt.getBlockState(p);
            if (b.is(Blocks.ICE) || b.is(Blocks.FROSTED_ICE)) {
                welt.setBlock(p, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
                n++;
            }
        }
        if (n > 0) {
            de.moinmornhart.stinkendreich.StinkendReich.LOGGER.info("Graben: {} Eisblöcke aufgetaut", n);
        }
    }

    /** Schritt 1: Wasser und Lava aus dem Graben nehmen. */
    private static void leeren(ServerLevel welt, Ring r) {
        for (BlockPos p : r.felder) {
            BlockState b = welt.getBlockState(p);
            if (b.is(Blocks.WATER) || b.is(Blocks.LAVA)) {
                welt.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Schritt 2: leere Felder mit Lava oder Wasser füllen. */
    private static void fuellen(ServerLevel welt, Ring r, boolean lava) {
        BlockState fluessig = (lava ? Blocks.LAVA : Blocks.WATER).defaultBlockState();
        for (BlockPos p : r.felder) {
            if (welt.getBlockState(p).isAir()) {
                welt.setBlock(p, fluessig, Block.UPDATE_CLIENTS);
            }
        }
        r.istLava = lava;
        r.fuellenMitLava = null;
    }

    /** Ring aus der Marke „graben“: Kasten (x0 y0 z0 x1 y1 z1) und Breite des Rings nach innen. */
    private static List<BlockPos> felderBerechnen(VillaPlatzierung.VillaInfo villa) {
        JsonArray m = villa.plan().marke("graben").get(0);
        int x0 = m.get(0).getAsInt(), y0 = m.get(1).getAsInt(), z0 = m.get(2).getAsInt();
        int x1 = m.get(3).getAsInt(), y1 = m.get(4).getAsInt(), z1 = m.get(5).getAsInt(), breite = m.get(6).getAsInt();
        List<BlockPos> liste = new ArrayList<>();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (Math.min(Math.min(x - x0, z - z0), Math.min(x1 - x, z1 - z)) >= breite) {
                    continue;
                }
                for (int y = y0; y <= y1; y++) {
                    liste.add(villa.weltPos(x, y, z));
                }
            }
        }
        return liste;
    }
}
