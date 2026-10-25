package de.moinmornhart.stinkendreich.strasse;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.List;

/**
 * Feste Ziele des Straßennetzes, die man beim Namen nennen kann: das nächste Dorf, das Endportal (Festung), ein
 * Netherportal (zuerst ein selbst gebautes in der Nähe, sonst eine Portalruine) und die Mine (verlassener
 * Minenschacht). Gesucht wird von der Villa aus; das Ergebnis ist eine Stelle an der Oberfläche.
 */
public final class StrassenZiele {
    /** Ein gefundenes Ziel: fester Name (für das Netz) und Stelle an der Oberfläche. */
    public record Ziel(String name, BlockPos pos) {
    }

    /** Erkannte Namen je Ziel (nach Sprache.normalisieren). */
    private static final List<String[]> NAMEN = List.of(
            new String[]{"dorf", "dorf", "doerfer", "village", "siedlung"},
            new String[]{"endportal", "endportal", "end portal", "ende", "festung", "stronghold", "end"},
            new String[]{"netherportal", "netherportal", "nether portal", "nether", "portal"},
            new String[]{"mine", "mine", "bergwerk", "minenschacht", "mineshaft", "stollen"});

    private StrassenZiele() {
    }

    /** Fester Zielname zu einem gesprochenen Namen („zum Dorf“ → „dorf“) oder null. */
    public static String fester(String name) {
        String n = name.trim();
        for (String[] eintrag : NAMEN) {
            for (int i = 1; i < eintrag.length; i++) {
                if (n.equals(eintrag[i]) || n.endsWith(" " + eintrag[i])) {
                    return eintrag[0];
                }
            }
        }
        return null;
    }

    /** Sucht das Ziel (fester Name) von {@code von} aus; null, wenn es in Reichweite keins gibt. */
    public static Ziel finden(ServerLevel welt, String fester, BlockPos von) {
        BlockPos p = switch (fester) {
            case "dorf" -> struktur(welt, StructureTags.VILLAGE, von, 100);
            case "endportal" -> struktur(welt, StructureTags.EYE_OF_ENDER_LOCATED, von, 100);
            case "mine" -> struktur(welt, StructureTags.MINESHAFT, von, 50);
            case "netherportal" -> {
                var eigenes = welt.getPoiManager().findClosest(h -> h.is(PoiTypes.NETHER_PORTAL), von, 256, PoiManager.Occupancy.ANY);
                yield eigenes.orElseGet(() -> struktur(welt, StructureTags.RUINED_PORTAL, von, 60));
            }
            default -> null;
        };
        if (p == null) {
            return null;
        }
        // An die Oberfläche (die Fundstelle kann tief im Boden liegen, z. B. bei Mine und Festung)
        int y = welt.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ());
        if (fester.equals("netherportal") && p.getY() > y - 3) {
            y = p.getY();
        }
        return new Ziel(fester, new BlockPos(p.getX(), y, p.getZ()));
    }

    private static BlockPos struktur(ServerLevel welt, TagKey<Structure> art, BlockPos von, int radiusInChunks) {
        try {
            return welt.findNearestMapStructure(art, von, radiusInChunks, false);
        } catch (Exception e) {
            return null;
        }
    }
}
