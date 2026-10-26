package de.moinmornhart.stinkendreich.villa;

import com.google.gson.JsonArray;
import de.moinmornhart.stinkendreich.register.ModBloecke;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Teile der Festung, die außerhalb des Bauplans im Gelände liegen und deshalb beim Bau der Welt gesetzt werden:
 * der Minengürtel rund um das Grundstück und das Ende des Notausgangs, eine versteckte Falltür 50 Blöcke neben der
 * Villa. Bestehende Welten bleiben unberührt.
 */
final class Festungsbau {
    /** Breite des Minengürtels vor dem Außenzaun und Abstand der Minen. */
    private static final int GUERTEL = 40, RASTER = 8, ABSTAND_ZAUN = 3;

    private Festungsbau() {
    }

    /**
     * Minen im Raster auf dem Gelände rund um das Grundstück (bis 40 Blöcke weit), nicht auf der Zufahrt, nicht in
     * Wasser, Bäumen oder auf Pflanzenstielen. Scharf sind sie nur in der Festung (Stufe 4).
     */
    static int minenfeld(ServerLevel welt, BlockPos ursprung, VillaDaten plan, int zufahrtX0, int zufahrtX1) {
        BlockState mine = ModBloecke.LANDMINE.defaultBlockState();
        int anzahl = 0;
        for (int lx = -GUERTEL; lx < plan.breiteX + GUERTEL; lx += RASTER) {
            int versatz = Math.floorMod(lx / RASTER, 2) * (RASTER / 2);
            for (int lz = -GUERTEL + versatz; lz < plan.breiteZ + GUERTEL; lz += RASTER) {
                boolean drin = lx > -ABSTAND_ZAUN && lx < plan.breiteX + ABSTAND_ZAUN
                        && lz > -ABSTAND_ZAUN && lz < plan.breiteZ + ABSTAND_ZAUN;
                boolean zufahrt = lz >= plan.breiteZ && lx >= zufahrtX0 - 3 && lx <= zufahrtX1 + 3;
                if (drin || zufahrt) {
                    continue;
                }
                int wx = ursprung.getX() + lx, wz = ursprung.getZ() + lz;
                int y = VillaPlatzierung.bodenHoehe(welt, wx, wz);
                BlockPos pos = new BlockPos(wx, y, wz);
                BlockState unten = welt.getBlockState(pos.below());
                BlockState hier = welt.getBlockState(pos);
                if (!unten.isFaceSturdy(welt, pos.below(), Direction.UP) || !unten.getFluidState().isEmpty()
                        || !(hier.isAir() || hier.canBeReplaced()) || !hier.getFluidState().isEmpty()) {
                    continue;
                }
                welt.setBlock(pos, mine, Block.UPDATE_CLIENTS);
                anzahl++;
            }
        }
        return anzahl;
    }

    /**
     * Verlängert den Fluchttunnel aus dem Bunker (Marke „notausgang“: Rand x, Höhe, z, Länge) unter dem Gelände nach
     * Westen und baut am Ende einen Schacht mit Leiter bis zu einer versteckten Falltür im Boden. Rundum Tiefenschiefer,
     * damit kein Wasser aus Höhlen hereinläuft. Gibt die Position der Falltür zurück.
     */
    static BlockPos notausgang(ServerLevel welt, BlockPos ursprung, VillaDaten plan) {
        if (plan.marke("notausgang").isEmpty()) {
            return null;
        }
        JsonArray m = plan.marke("notausgang").get(0);
        int rand = m.get(0).getAsInt(), y = m.get(1).getAsInt(), z = m.get(2).getAsInt(), laenge = m.get(3).getAsInt();
        BlockState wand = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockState boden = Blocks.POLISHED_ANDESITE.defaultBlockState();
        BlockState licht = Blocks.VERDANT_FROGLIGHT.defaultBlockState();
        BlockState luft = Blocks.AIR.defaultBlockState();
        int ende = rand - laenge;                                   // Schacht (Strukturkoordinaten)
        for (int lx = rand - 1; lx > ende; lx--) {
            for (int dy = -1; dy <= 3; dy++) {
                for (int dz = -1; dz <= 2; dz++) {
                    boolean innen = dy >= 0 && dy <= 2 && dz >= 0 && dz <= 1;
                    BlockState s = innen ? luft : (dy == -1 && (dz == 0 || dz == 1) ? boden : wand);
                    if (innen && dy == 2 && dz == 0 && Math.floorMod(lx, 5) == 0) {
                        s = licht;                                  // grünes Notlicht
                    }
                    setze(welt, ursprung, lx, y + dy, z + dz, s);
                }
            }
        }
        // Schacht am Ende: Leiter an der Westwand hoch bis knapp unter die Oberfläche
        // Höhe der Luke: tiefste Stelle rund um den Schacht, damit am Hang nichts aus dem Boden ragt
        int oben = Integer.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 2; dz++) {
                int grund = VillaPlatzierung.bodenHoehe(welt, ursprung.getX() + ende + dx, ursprung.getZ() + z + dz);
                oben = Math.min(oben, grund - 1 - ursprung.getY());           // oberster fester Block
            }
        }
        oben = Math.max(oben, y + 4);
        int wx = ursprung.getX() + ende, wz = ursprung.getZ() + z;
        int schachtOben = VillaPlatzierung.bodenHoehe(welt, wx, wz) - 1 - ursprung.getY();
        BlockState leiter = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
        for (int ly = y - 1; ly < oben; ly++) {
            for (int dz = -1; dz <= 2; dz++) {
                setze(welt, ursprung, ende - 1, ly, z + dz, wand);
                if (ly > y + 2 || dz < 0 || dz > 1) {
                    setze(welt, ursprung, ende + 1, ly, z + dz, wand);     // über und neben dem Tunnelende zu
                }
            }
            setze(welt, ursprung, ende, ly, z - 1, wand);
            setze(welt, ursprung, ende, ly, z + 2, wand);
            setze(welt, ursprung, ende, ly, z + 1, ly <= y + 2 && ly >= y ? luft : wand);
            setze(welt, ursprung, ende, ly, z, ly == y - 1 ? boden : leiter);
        }
        // Versteckte Falltür bündig im Boden, darüber frei
        BlockState luke = ModBloecke.GEHEIMLUKE.defaultBlockState()
                .setValue(TrapDoorBlock.FACING, Direction.EAST).setValue(TrapDoorBlock.HALF, Half.TOP);
        setze(welt, ursprung, ende, oben, z, luke);
        // Über der Luke frei bis zur Oberfläche dieser Stelle (kleine Mulde im Gelände)
        for (int ly = oben + 1; ly <= Math.max(oben + 1, schachtOben); ly++) {
            setze(welt, ursprung, ende, ly, z, luft);
        }
        return ursprung.offset(ende, oben, z);
    }

    private static void setze(ServerLevel welt, BlockPos ursprung, int lx, int ly, int lz, BlockState s) {
        welt.setBlock(ursprung.offset(lx, ly, lz), s, Block.UPDATE_CLIENTS);
    }
}
