package de.moinmornhart.stinkendreich.butler;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Alle Dinge, die man beim Butler bestellen kann, und wie er sie beschafft:
 * abbauen, schmelzen, herstellen (werken), jagen oder aus dem Vorrat holen.
 */
public final class Quellen {
    /** Wie der Butler an eine Quelle kommt. */
    public enum Weg {
        /** In der Welt abbauen ({@link Quelle#rohstoff()}). */
        ABBAUEN,
        /** Aus einer anderen Quelle im Ofen schmelzen ({@link Quelle#eingang()}). */
        SCHMELZEN,
        /** Nach einem Rezept herstellen ({@link Quelle#zutaten()}). */
        HERSTELLEN,
        /** Tiere jagen (rohes Fleisch). */
        JAGEN,
        /** Fertiges Essen: aus dem Vorrat, sonst jagen und braten. */
        ESSEN,
        /** Kann der Butler nicht selbst beschaffen. */
        KEINER
    }

    /** Zutat eines Rezepts: Quelle und Anzahl je Herstellung. */
    public record Zutat(String quelle, int anzahl) {
    }

    /**
     * @param id       interner Name
     * @param passt    erkennt passende Gegenstände im Inventar
     * @param anzeige  Gegenstand für Texte
     * @param weg      Beschaffungsweg
     * @param rohstoff bei ABBAUEN: Rohstoff-ID
     * @param eingang  bei SCHMELZEN: Quelle, die geschmolzen wird
     * @param zutaten  bei HERSTELLEN: Zutaten je Herstellung
     * @param menge    bei HERSTELLEN: Ergebnis je Herstellung
     * @param werkbank bei HERSTELLEN: braucht eine Werkbank (3x3)
     */
    public record Quelle(String id, Predicate<ItemStack> passt, Item anzeige, Weg weg, String rohstoff, String eingang,
                         List<Zutat> zutaten, int menge, boolean werkbank) {
        public ItemStack anzeigeStapel() {
            return new ItemStack(anzeige);
        }
    }

    private static final Map<String, Quelle> ALLE = new LinkedHashMap<>();

    static {
        // Abbauen
        abbau("rohes_eisen", Items.RAW_IRON, "eisen");
        abbau("rohes_gold", Items.RAW_GOLD, "gold");
        abbau("rohes_kupfer", Items.RAW_COPPER, "kupfer");
        neu(new Quelle("kohle", s -> s.is(Items.COAL) || s.is(Items.CHARCOAL), Items.COAL, Weg.ABBAUEN, "kohle", null, List.of(), 1, false));
        abbau("diamant", Items.DIAMOND, "diamant");
        abbau("smaragd", Items.EMERALD, "smaragd");
        abbau("redstone", Items.REDSTONE, "redstone");
        abbau("lapis", Items.LAPIS_LAZULI, "lapis");
        abbau("quarz", Items.QUARTZ, "quarz");
        neu(new Quelle("bruchstein", s -> s.is(ItemTags.STONE_CRAFTING_MATERIALS), Items.COBBLESTONE, Weg.ABBAUEN, "bruchstein", null, List.of(), 1, false));
        abbau("tiefenschiefer", Items.COBBLED_DEEPSLATE, "tiefenschiefer");
        abbau("erde", Items.DIRT, "erde");
        abbau("sand", Items.SAND, "sand");
        abbau("kies", Items.GRAVEL, "kies");
        abbau("ton", Items.CLAY_BALL, "ton");
        neu(new Quelle("holz", s -> s.is(ItemTags.LOGS), Items.OAK_LOG, Weg.ABBAUEN, "holz", null, List.of(), 1, false));

        // Schmelzen
        schmelz("eisenbarren", Items.IRON_INGOT, "rohes_eisen");
        schmelz("goldbarren", Items.GOLD_INGOT, "rohes_gold");
        schmelz("kupferbarren", Items.COPPER_INGOT, "rohes_kupfer");
        schmelz("stein", Items.STONE, "bruchstein");
        schmelz("glas", Items.GLASS, "sand");
        schmelz("ziegel", Items.BRICK, "ton");
        schmelz("holzkohle", Items.CHARCOAL, "holz");

        // Herstellen (Rezepte wie im Spiel)
        neu(new Quelle("bretter", s -> s.is(ItemTags.PLANKS), Items.OAK_PLANKS, Weg.HERSTELLEN, null, null,
                List.of(new Zutat("holz", 1)), 4, false));
        rezept("stoecke", Items.STICK, 4, false, new Zutat("bretter", 2));
        rezept("fackeln", Items.TORCH, 4, false, new Zutat("stoecke", 1), new Zutat("kohle", 1));
        rezept("werkbank", Items.CRAFTING_TABLE, 1, false, new Zutat("bretter", 4));
        rezept("truhe", Items.CHEST, 1, true, new Zutat("bretter", 8));
        rezept("ofen", Items.FURNACE, 1, true, new Zutat("bruchstein", 8));
        rezept("leiter", Items.LADDER, 3, true, new Zutat("stoecke", 7));
        rezept("eimer", Items.BUCKET, 1, true, new Zutat("eisenbarren", 3));
        rezept("eisenblock", Items.IRON_BLOCK, 1, true, new Zutat("eisenbarren", 9));
        rezept("goldblock", Items.GOLD_BLOCK, 1, true, new Zutat("goldbarren", 9));
        rezept("diamantblock", Items.DIAMOND_BLOCK, 1, true, new Zutat("diamant", 9));
        rezept("smaragdblock", Items.EMERALD_BLOCK, 1, true, new Zutat("smaragd", 9));
        rezept("eisenspitzhacke", Items.IRON_PICKAXE, 1, true, new Zutat("eisenbarren", 3), new Zutat("stoecke", 2));
        rezept("diamantspitzhacke", Items.DIAMOND_PICKAXE, 1, true, new Zutat("diamant", 3), new Zutat("stoecke", 2));
        rezept("eisenschwert", Items.IRON_SWORD, 1, true, new Zutat("eisenbarren", 2), new Zutat("stoecke", 1));
        rezept("diamantschwert", Items.DIAMOND_SWORD, 1, true, new Zutat("diamant", 2), new Zutat("stoecke", 1));
        rezept("eisenaxt", Items.IRON_AXE, 1, true, new Zutat("eisenbarren", 3), new Zutat("stoecke", 2));
        rezept("brot", Items.BREAD, 1, true, new Zutat("weizen", 3));
        rezept("schienen", Items.RAIL, 16, true, new Zutat("eisenbarren", 6), new Zutat("stoecke", 1));

        // Essen
        neu(new Quelle("rohes_fleisch", Schmelzen::istRohesEssen, Items.BEEF, Weg.JAGEN, null, null, List.of(), 1, false));
        neu(new Quelle("essen", Schmelzen::istFertigesEssen, Items.COOKED_BEEF, Weg.ESSEN, null, null, List.of(), 1, false));
        neu(new Quelle("weizen", s -> s.is(Items.WHEAT), Items.WHEAT, Weg.KEINER, null, null, List.of(), 1, false));
    }

    private Quellen() {
    }

    private static void neu(Quelle q) {
        ALLE.put(q.id(), q);
    }

    private static void abbau(String id, Item item, String rohstoff) {
        neu(new Quelle(id, s -> s.is(item), item, Weg.ABBAUEN, rohstoff, null, List.of(), 1, false));
    }

    private static void schmelz(String id, Item item, String eingang) {
        neu(new Quelle(id, s -> s.is(item), item, Weg.SCHMELZEN, null, eingang, List.of(), 1, false));
    }

    private static void rezept(String id, Item item, int menge, boolean werkbank, Zutat... zutaten) {
        neu(new Quelle(id, s -> s.is(item), item, Weg.HERSTELLEN, null, null, List.of(zutaten), menge, werkbank));
    }

    public static Quelle get(String id) {
        return ALLE.get(id);
    }

    public static Collection<Quelle> alle() {
        return ALLE.values();
    }

    /** Bretter passend zum Holz (Eichenstamm → Eichenbretter). Fremde Hölzer werden zu Eichenbrettern. */
    public static Item bretterAus(ItemStack stamm) {
        String pfad = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stamm.getItem()).getPath();
        String art = pfad.replace("stripped_", "").replace("_log", "").replace("_wood", "")
                .replace("_stem", "").replace("_hyphae", "");
        if (pfad.contains("bamboo")) {
            art = "bamboo";
        }
        var id = net.minecraft.resources.Identifier.withDefaultNamespace(art + "_planks");
        //? if >=1.21.2 {
        Item bretter = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id);
        //?} else
        /*Item bretter = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);*/
        return bretter == Items.AIR ? Items.OAK_PLANKS : bretter;
    }
}
