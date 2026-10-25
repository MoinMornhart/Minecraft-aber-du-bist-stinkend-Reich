package de.moinmornhart.stinkendreich.butler;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Durchsucht die geladenen Chunks rund um einen Punkt nach passenden Blöcken, ein paar Chunks
 * pro Tick, damit das Spiel nicht ruckelt. Abschnitte ohne passende Blöcke werden über die
 * Blockpalette sofort übersprungen.
 */
public final class Suche {
    private static final int CHUNKS_PRO_TICK = 6;
    private static final int MAX_TREFFER = 4000;

    private final BlockPos mitte;
    private final int radius;
    private final Predicate<BlockState> passt;
    private final List<BlockPos> treffer = new ArrayList<>();
    private final int r;
    private int index;

    public Suche(BlockPos mitte, int radius, Predicate<BlockState> passt) {
        this.mitte = mitte.immutable();
        this.radius = radius;
        this.passt = passt;
        this.r = (radius >> 4) + 1;
    }

    /** Sucht weiter; true, sobald alles durchsucht ist. */
    public boolean weiter(ServerLevel welt) {
        int seite = 2 * r + 1;
        int ende = Math.min(seite * seite, index + CHUNKS_PRO_TICK);
        int mcx = mitte.getX() >> 4, mcz = mitte.getZ() >> 4;
        for (; index < ende; index++) {
            int cx = mcx - r + index % seite;
            int cz = mcz - r + index / seite;
            if (welt.hasChunk(cx, cz)) {
                durchsuchen(welt, welt.getChunk(cx, cz));
            }
        }
        return index >= seite * seite;
    }

    private void durchsuchen(ServerLevel welt, LevelChunk chunk) {
        LevelChunkSection[] abschnitte = chunk.getSections();
        int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
        for (int i = 0; i < abschnitte.length && treffer.size() < MAX_TREFFER; i++) {
            LevelChunkSection a = abschnitte[i];
            if (a == null || a.hasOnlyAir() || !a.maybeHas(passt)) {
                continue;
            }
            int by = chunk.getSectionYFromSectionIndex(i) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (passt.test(a.getBlockState(x, y, z))) {
                            int wx = bx + x, wz = bz + z;
                            long dx = wx - mitte.getX(), dz = wz - mitte.getZ();
                            if (dx * dx + dz * dz <= (long) radius * radius) {
                                treffer.add(new BlockPos(wx, by + y, wz));
                            }
                        }
                    }
                }
            }
        }
    }

    /** Treffer sortiert nach Aufwand vom Butler aus (Höhenunterschied zählt etwas mehr). */
    public List<BlockPos> sortiert(BlockPos von) {
        List<BlockPos> liste = new ArrayList<>(treffer);
        liste.sort(Comparator.comparingDouble(p -> aufwand(von, p)));
        return liste;
    }

    public static double aufwand(BlockPos von, BlockPos p) {
        double dx = p.getX() - von.getX(), dz = p.getZ() - von.getZ();
        return Math.sqrt(dx * dx + dz * dz) + Math.abs(p.getY() - von.getY()) * 1.3;
    }
}
