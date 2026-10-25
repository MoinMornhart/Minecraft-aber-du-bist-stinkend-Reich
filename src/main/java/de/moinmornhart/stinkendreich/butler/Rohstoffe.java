package de.moinmornhart.stinkendreich.butler;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Alles, was der Butler in der Welt abbauen kann: welche Blöcke er sucht, was dabei herausfällt
 * und mit welchem Werkzeug er arbeitet (das Werkzeug ist nur zum Ansehen und nutzt sich nicht ab).
 */
public final class Rohstoffe {
    /** Wie der Butler an den Rohstoff kommt. */
    public enum Art {
        /** Unter Tage: Erz suchen, notfalls einen Tunnel graben. */
        ERZ,
        /** Oberfläche oder Untergrund: Stein, Erde, Sand … */
        BODEN,
        /** Stämme natürlicher Bäume fällen. */
        HOLZ
    }

    /**
     * @param id      interner Name, z. B. „eisen“
     * @param block   welche Blöcke abgebaut werden
     * @param ertrag  woran der Ertrag im Inventar zu erkennen ist
     * @param anzeige Gegenstand für Texte („Rohes Eisen“)
     * @param werkzeug Werkzeug in der Hand (bestimmt auch, was herausfällt)
     */
    public record Rohstoff(String id, Art art, Predicate<BlockState> block, Predicate<ItemStack> ertrag, Item anzeige, Item werkzeug) {
    }

    private static final Map<String, Rohstoff> ALLE = new LinkedHashMap<>();

    static {
        erz("eisen", s -> s.is(BlockTags.IRON_ORES), Items.RAW_IRON, Items.IRON_PICKAXE);
        erz("gold", s -> s.is(BlockTags.GOLD_ORES) && !s.is(Blocks.NETHER_GOLD_ORE), Items.RAW_GOLD, Items.IRON_PICKAXE);
        erz("kupfer", s -> s.is(BlockTags.COPPER_ORES), Items.RAW_COPPER, Items.IRON_PICKAXE);
        erz("kohle", s -> s.is(Blocks.COAL_ORE) || s.is(Blocks.DEEPSLATE_COAL_ORE), Items.COAL, Items.IRON_PICKAXE);
        erz("diamant", s -> s.is(Blocks.DIAMOND_ORE) || s.is(Blocks.DEEPSLATE_DIAMOND_ORE), Items.DIAMOND, Items.DIAMOND_PICKAXE);
        erz("smaragd", s -> s.is(Blocks.EMERALD_ORE) || s.is(Blocks.DEEPSLATE_EMERALD_ORE), Items.EMERALD, Items.IRON_PICKAXE);
        erz("redstone", s -> s.is(Blocks.REDSTONE_ORE) || s.is(Blocks.DEEPSLATE_REDSTONE_ORE), Items.REDSTONE, Items.IRON_PICKAXE);
        erz("lapis", s -> s.is(Blocks.LAPIS_ORE) || s.is(Blocks.DEEPSLATE_LAPIS_ORE), Items.LAPIS_LAZULI, Items.IRON_PICKAXE);
        erz("quarz", s -> s.is(Blocks.NETHER_QUARTZ_ORE), Items.QUARTZ, Items.IRON_PICKAXE);
        boden("bruchstein", s -> s.is(Blocks.STONE), Items.COBBLESTONE, Items.IRON_PICKAXE);
        boden("tiefenschiefer", s -> s.is(Blocks.DEEPSLATE), Items.COBBLED_DEEPSLATE, Items.IRON_PICKAXE);
        boden("erde", s -> s.is(Blocks.DIRT) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.COARSE_DIRT), Items.DIRT, Items.IRON_SHOVEL);
        boden("sand", s -> s.is(Blocks.SAND), Items.SAND, Items.IRON_SHOVEL);
        boden("kies", s -> s.is(Blocks.GRAVEL), Items.GRAVEL, Items.IRON_SHOVEL);
        boden("ton", s -> s.is(Blocks.CLAY), Items.CLAY_BALL, Items.IRON_SHOVEL);
        ALLE.put("holz", new Rohstoff("holz", Art.HOLZ, s -> s.is(BlockTags.LOGS), st -> st.is(ItemTags.LOGS), Items.OAK_LOG, Items.IRON_AXE));
    }

    private Rohstoffe() {
    }

    private static void erz(String id, Predicate<BlockState> block, Item ertrag, Item werkzeug) {
        ALLE.put(id, new Rohstoff(id, Art.ERZ, block, st -> st.is(ertrag), ertrag, werkzeug));
    }

    private static void boden(String id, Predicate<BlockState> block, Item ertrag, Item werkzeug) {
        ALLE.put(id, new Rohstoff(id, Art.BODEN, block, st -> st.is(ertrag), ertrag, werkzeug));
    }

    public static Rohstoff get(String id) {
        return ALLE.get(id);
    }
}
