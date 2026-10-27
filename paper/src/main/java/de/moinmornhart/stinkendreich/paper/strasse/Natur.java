package de.moinmornhart.stinkendreich.paper.strasse;

import org.bukkit.Material;
import org.bukkit.Tag;

import java.util.EnumMap;
import java.util.Map;

/**
 * Was die Natur gemacht hat (wie {@code Schutz.istNatuerlich} in der Mod): Gelände, Gestein, Erze, Pflanzen, Bäume,
 * Schnee und Eis. Nur so etwas darf der Straßenbau abtragen oder überbauen – Häuser im Dorf, Spielerbauten und alles
 * Gesetzte bleiben stehen. Dazu das Gegenstück für die Platzwahl neuer Villen: Was ist sicher gebaut?
 */
public final class Natur {
    /** Namensteile von Blöcken, die nur Spieler (oder Dörfer und andere Bauwerke) setzen. */
    private static final String[] GEBAUT = {"PLANKS", "_STAIRS", "_SLAB", "FENCE", "_WALL", "GLASS", "WOOL", "CARPET",
            "_BED", "DOOR", "SIGN", "BANNER", "TORCH", "LANTERN", "BRICK", "CONCRETE", "GLAZED", "CRAFTING_TABLE",
            "FURNACE", "SMOKER", "CHEST", "BARREL", "RAIL", "POLISHED_", "SMOOTH_STONE", "CUT_", "CHISELED_", "COBBLESTONE",
            "BOOKSHELF", "ANVIL", "HOPPER", "LADDER", "STRIPPED_", "BUTTON", "PRESSURE_PLATE", "LEVER", "REDSTONE",
            "PISTON", "OBSERVER", "DISPENSER", "DROPPER", "BELL", "CAULDRON", "POTTED_", "CANDLE", "CAMPFIRE", "COMPOSTER",
            "LECTERN", "LOOM", "STONECUTTER", "GRINDSTONE", "SMITHING_TABLE", "CARTOGRAPHY_TABLE", "FLETCHING_TABLE",
            "BEEHIVE", "SCAFFOLDING", "HAY_BLOCK", "TARGET", "IRON_BLOCK", "GOLD_BLOCK", "DIAMOND_BLOCK", "EMERALD_BLOCK",
            "NETHERITE_BLOCK", "QUARTZ", "PURPUR", "PRISMARINE", "SEA_LANTERN", "SHULKER_BOX", "ENCHANTING_TABLE",
            "BREWING_STAND", "BEACON", "JUKEBOX", "NOTE_BLOCK", "FARMLAND", "DIRT_PATH", "TERRACOTTA", "IRON_BARS",
            "CHAIN", "COPPER_", "_TILES", "GRATE", "BULB", "CRAFTER", "DECORATED_POT", "SPAWNER"};
    /** Eindeutige Spuren von Spielern unter der Erde (in Minen und Höhlen von Minecraft gibt es sie nicht). */
    private static final String[] SPUR = {"TORCH", "CRAFTING_TABLE", "FURNACE", "SMOKER", "_BED", "SIGN", "BARREL",
            "LANTERN", "SHULKER_BOX", "ENCHANTING_TABLE", "BREWING_STAND"};

    private Natur() {
    }

    /** Antworten je Blockart (die Platzprüfung fragt hunderttausende Blöcke ab; nur vom Haupt-Thread benutzt). */
    private static final Map<Material, Boolean> GEBAUT_MERK = new EnumMap<>(Material.class);
    private static final Map<Material, Boolean> SPUR_MERK = new EnumMap<>(Material.class);

    /** Ein Block, den Spieler (oder ein Dorf) gesetzt haben – nie Gelände, Pflanzen, Wasser oder Luft. */
    public static boolean gebaut(Material m) {
        return !m.isAir() && GEBAUT_MERK.computeIfAbsent(m, k -> !fluessig(k) && !natuerlich(k) && enthaelt(k.name(), GEBAUT));
    }

    /** Ein Block unter der Erde, der sicher von einem Spieler stammt (Fackel, Werkbank, Ofen, Bett …). */
    public static boolean spielerSpur(Material m) {
        return !m.isAir() && SPUR_MERK.computeIfAbsent(m, k -> !natuerlich(k) && enthaelt(k.name(), SPUR));
    }

    private static boolean enthaelt(String name, String[] teile) {
        for (String t : teile) {
            if (name.contains(t)) {
                return true;
            }
        }
        return false;
    }

    static boolean fluessig(Material m) {
        return m == Material.WATER || m == Material.LAVA || m == Material.BUBBLE_COLUMN || m == Material.KELP
                || m == Material.KELP_PLANT || m == Material.SEAGRASS || m == Material.TALL_SEAGRASS;
    }

    public static boolean natuerlich(Material m) {
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
