package de.moinmornhart.stinkendreich.paper.strasse;

import org.bukkit.Material;
import org.bukkit.Tag;

/**
 * Was die Natur gemacht hat (wie {@code Schutz.istNatuerlich} in der Mod): Gelände, Gestein, Erze, Pflanzen, Bäume,
 * Schnee und Eis. Nur so etwas darf der Straßenbau abtragen oder überbauen – Häuser im Dorf, Spielerbauten und alles
 * Gesetzte bleiben stehen.
 */
final class Natur {
    private Natur() {
    }

    static boolean fluessig(Material m) {
        return m == Material.WATER || m == Material.LAVA || m == Material.BUBBLE_COLUMN || m == Material.KELP
                || m == Material.KELP_PLANT || m == Material.SEAGRASS || m == Material.TALL_SEAGRASS;
    }

    static boolean natuerlich(Material m) {
        if (Tag.LOGS.isTagged(m) || Tag.LEAVES.isTagged(m) || Tag.DIRT.isTagged(m) || Tag.BASE_STONE_OVERWORLD.isTagged(m)
                || Tag.SAND.isTagged(m) || Tag.TERRACOTTA.isTagged(m) || Tag.SNOW.isTagged(m) || Tag.ICE.isTagged(m)
                || Tag.REPLACEABLE.isTagged(m) || Tag.FLOWERS.isTagged(m) || Tag.SAPLINGS.isTagged(m)
                || Tag.CORAL_BLOCKS.isTagged(m) || Tag.CORALS.isTagged(m) || Tag.CAVE_VINES.isTagged(m)
                || m.name().endsWith("_ORE")) {
            return true;
        }
        return switch (m) {
            case GRASS_BLOCK, PODZOL, MYCELIUM, ROOTED_DIRT, MUD, MOSS_BLOCK, MOSS_CARPET, PALE_MOSS_BLOCK, PALE_MOSS_CARPET,
                 GRAVEL, CLAY, CALCITE, DRIPSTONE_BLOCK, POINTED_DRIPSTONE, SMOOTH_BASALT, SANDSTONE, RED_SANDSTONE,
                 SNOW_BLOCK, OBSIDIAN, MAGMA_BLOCK, AMETHYST_BLOCK, BUDDING_AMETHYST, AMETHYST_CLUSTER, SCULK, SCULK_VEIN,
                 COBBLED_DEEPSLATE, RAW_IRON_BLOCK, RAW_COPPER_BLOCK, GLOW_LICHEN, VINE, SUGAR_CANE, CACTUS, BAMBOO,
                 SWEET_BERRY_BUSH, PUMPKIN, MELON, BROWN_MUSHROOM_BLOCK, RED_MUSHROOM_BLOCK, MUSHROOM_STEM, BEE_NEST,
                 AZALEA, FLOWERING_AZALEA, MANGROVE_ROOTS, MUDDY_MANGROVE_ROOTS, LILY_PAD, SPORE_BLOSSOM, BIG_DRIPLEAF,
                 BIG_DRIPLEAF_STEM, SMALL_DRIPLEAF, HANGING_ROOTS, BROWN_MUSHROOM, RED_MUSHROOM, SEA_PICKLE, COCOA,
                 POWDER_SNOW, SNOW -> true;
            default -> false;
        };
    }
}
