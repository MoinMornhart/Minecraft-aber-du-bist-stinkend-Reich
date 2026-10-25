package de.moinmornhart.stinkendreich.butler;

import com.google.gson.JsonArray;
import de.moinmornhart.stinkendreich.villa.VillaPlatzierung;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Findet Öfen, Truhen und Werkbänke in der Nähe – zuerst die bekannten Plätze in der Villa
 * (Markierungen aus dem Bauplan), dann alles im Umkreis.
 */
public final class Umgebung {
    private Umgebung() {
    }

    /** Block-Entities im Umkreis, nach Abstand sortiert. */
    public static List<BlockPos> blockEntities(ServerLevel welt, BlockPos mitte, int radius, Predicate<BlockEntity> passt) {
        List<BlockPos> aus = new ArrayList<>();
        for (int cx = (mitte.getX() - radius) >> 4; cx <= (mitte.getX() + radius) >> 4; cx++) {
            for (int cz = (mitte.getZ() - radius) >> 4; cz <= (mitte.getZ() + radius) >> 4; cz++) {
                if (!welt.hasChunk(cx, cz)) {
                    continue;
                }
                for (var e : welt.getChunk(cx, cz).getBlockEntities().entrySet()) {
                    BlockPos p = e.getKey();
                    if (p.distSqr(mitte) <= (double) radius * radius && passt.test(e.getValue())) {
                        aus.add(p.immutable());
                    }
                }
            }
        }
        aus.sort(Comparator.comparingDouble(p -> p.distSqr(mitte)));
        return aus;
    }

    /** Blöcke im Würfel um {@code mitte}, nach Abstand sortiert (nur für kleine Radien). */
    public static List<BlockPos> bloecke(ServerLevel welt, BlockPos mitte, int radius, Predicate<BlockState> passt) {
        List<BlockPos> aus = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(mitte.offset(-radius, -4, -radius), mitte.offset(radius, 4, radius))) {
            if (passt.test(welt.getBlockState(p))) {
                aus.add(p.immutable());
            }
        }
        aus.sort(Comparator.comparingDouble(p -> p.distSqr(mitte)));
        return aus;
    }

    /** Weltpositionen einer Villa-Markierung (z. B. „ofen“, „vorrat“), leer ohne Villa. */
    public static List<BlockPos> villaMarken(Level welt, String name) {
        if (welt.dimension() != Level.OVERWORLD) {
            return List.of();
        }
        return VillaPlatzierung.villa().map(v -> {
            List<BlockPos> liste = new ArrayList<>();
            for (JsonArray a : v.plan().marke(name)) {
                liste.add(v.weltPos(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()));
            }
            return liste;
        }).orElse(List.of());
    }

    /** Villa-Markierungen plus passende Block-Entities rund um Butler und Zuhause (ohne Doppelte). */
    public static List<BlockPos> finden(ButlerEntity butler, String marke, int radius, Predicate<BlockEntity> passt) {
        ServerLevel welt = butler.welt();
        Set<BlockPos> alle = new LinkedHashSet<>();
        for (BlockPos p : villaMarken(welt, marke)) {
            BlockEntity be = welt.getBlockEntity(p);
            if (be != null && passt.test(be)) {
                alle.add(p);
            }
        }
        alle.addAll(blockEntities(welt, butler.blockPosition(), radius, passt));
        alle.addAll(blockEntities(welt, butler.zuhause(), radius, passt));
        return new ArrayList<>(alle);
    }
}
