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
 * Lava-Graben: Ist die Alarmanlage an (Stufe 1 oder höher) und ein Bewohner zu Hause (irgendwo auf dem Grundstück
 * innerhalb des Grabens, also auch im Garten, oder in einem der Bereiche „haus“ aus villa.json), füllt sich der
 * Graben rund um das Grundstück mit Lava. Im Lockdown (Stufe 2 und 3) ist immer Lava darin, auch wenn niemand zu
 * Hause ist. Sonst ist Wasser darin. Getauscht wird in zwei Schritten: erst alles leeren, im nächsten Tick neu füllen. So berühren
 * sich Wasser und Lava nie (das gäbe Obsidian). Blöcke, die jemand in den Graben gebaut hat, bleiben.
 * Ein- und ausschalten: Config „lavaGraben“.
 */
public final class Graben {
    /** So oft wird nachgesehen, ob jemand zu Hause ist (Ticks). */
    private static final int PRUEFEN_ALLE = 40;

    /** Alle Felder des Grabens in Weltkoordinaten (beim ersten Gebrauch berechnet). */
    private static List<BlockPos> felder;
    /** Was gerade im Graben ist: true = Lava, false = Wasser, null = noch nicht nachgesehen. */
    private static Boolean istLava;
    /** Leeren ist erledigt, im nächsten Tick wird hiermit gefüllt (null = kein Tausch unterwegs). */
    private static Boolean fuellenMitLava;

    private Graben() {
    }

    public static void registrieren() {
        ServerTickEvents.END_SERVER_TICK.register(Graben::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            felder = null;
            istLava = null;
            fuellenMitLava = null;
        });
    }

    private static void tick(MinecraftServer server) {
        ServerLevel welt = server.overworld();
        if (fuellenMitLava != null) {
            fuellen(welt, fuellenMitLava);
            return;
        }
        if (server.getTickCount() % PRUEFEN_ALLE != 0) {
            return;
        }
        var villa = VillaPlatzierung.villa().orElse(null);
        if (villa == null || villa.plan().marke("graben").isEmpty()) {
            return;
        }
        if (felder == null) {
            felder = felderBerechnen(villa);
        }
        if (felder.isEmpty() || !welt.isLoaded(felder.get(0)) || !welt.isLoaded(felder.get(felder.size() - 1))) {
            return;
        }
        VillaZustand zustand = VillaZustand.get(server);
        boolean soll = Konfig.get().lavaGraben
                && (zustand.stufe() >= 2 || zustand.stufe() >= 1 && bewohnerZuHause(welt, villa, zustand));
        if (istLava == null) {
            istLava = welt.getBlockState(felder.get(felder.size() / 2)).is(Blocks.LAVA);
        }
        if (soll != istLava) {
            leeren(welt);
            fuellenMitLava = soll;
            zustand.anBewohner(Component.translatable(soll ? "stinkendreich.graben.lava" : "stinkendreich.graben.wasser")
                    .withStyle(soll ? ChatFormatting.GOLD : ChatFormatting.AQUA), true);
        }
    }

    /** Schritt 1: Wasser und Lava aus dem Graben nehmen. */
    private static void leeren(ServerLevel welt) {
        for (BlockPos p : felder) {
            BlockState b = welt.getBlockState(p);
            if (b.is(Blocks.WATER) || b.is(Blocks.LAVA)) {
                welt.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Schritt 2: leere Felder mit Lava oder Wasser füllen. */
    private static void fuellen(ServerLevel welt, boolean lava) {
        BlockState fluessig = (lava ? Blocks.LAVA : Blocks.WATER).defaultBlockState();
        for (BlockPos p : felder) {
            if (welt.getBlockState(p).isAir()) {
                welt.setBlock(p, fluessig, Block.UPDATE_CLIENTS);
            }
        }
        istLava = lava;
        fuellenMitLava = null;
    }

    private static boolean bewohnerZuHause(ServerLevel welt, VillaPlatzierung.VillaInfo villa, VillaZustand zustand) {
        // Grundstück: alles innerhalb des Grabenrings, vom Keller bis hoch über das Dach
        JsonArray g = villa.plan().marke("graben").get(0);
        int breite = g.get(6).getAsInt();
        BlockPos innenA = villa.weltPos(g.get(0).getAsInt() + breite, 0, g.get(2).getAsInt() + breite);
        BlockPos innenB = villa.weltPos(g.get(3).getAsInt() - breite, 90, g.get(5).getAsInt() - breite);
        for (ServerPlayer p : welt.players()) {
            if (!zustand.istBewohner(p)) {
                continue;
            }
            BlockPos s = p.blockPosition();
            if (s.getX() >= innenA.getX() && s.getX() <= innenB.getX() && s.getY() >= innenA.getY() && s.getY() <= innenB.getY()
                    && s.getZ() >= innenA.getZ() && s.getZ() <= innenB.getZ()) {
                return true;
            }
            for (JsonArray m : villa.plan().marke("haus")) {
                BlockPos a = villa.weltPos(m.get(0).getAsDouble(), m.get(1).getAsDouble(), m.get(2).getAsDouble());
                BlockPos b = villa.weltPos(m.get(3).getAsDouble(), m.get(4).getAsDouble(), m.get(5).getAsDouble());
                if (s.getX() >= a.getX() && s.getX() <= b.getX() && s.getY() >= a.getY() && s.getY() <= b.getY()
                        && s.getZ() >= a.getZ() && s.getZ() <= b.getZ()) {
                    return true;
                }
            }
        }
        return false;
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
