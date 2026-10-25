package de.moinmornhart.stinkendreich.butler;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;

import java.util.Map;

/**
 * Was der Butler in Öfen schmelzen oder braten kann. Die Tabelle steht hier fest, damit
 * sie in allen Minecraft-Versionen gleich funktioniert (die Rezept-Schnittstelle ändert sich oft);
 * das eigentliche Schmelzen erledigt der Ofen im Spiel selbst.
 */
public final class Schmelzen {
    private static final Map<Item, Item> ERGEBNIS = Map.ofEntries(
            Map.entry(Items.RAW_IRON, Items.IRON_INGOT),
            Map.entry(Items.RAW_GOLD, Items.GOLD_INGOT),
            Map.entry(Items.RAW_COPPER, Items.COPPER_INGOT),
            Map.entry(Items.COBBLESTONE, Items.STONE),
            Map.entry(Items.COBBLED_DEEPSLATE, Items.DEEPSLATE),
            Map.entry(Items.SAND, Items.GLASS),
            Map.entry(Items.CLAY_BALL, Items.BRICK),
            Map.entry(Items.BEEF, Items.COOKED_BEEF),
            Map.entry(Items.PORKCHOP, Items.COOKED_PORKCHOP),
            Map.entry(Items.CHICKEN, Items.COOKED_CHICKEN),
            Map.entry(Items.MUTTON, Items.COOKED_MUTTON),
            Map.entry(Items.RABBIT, Items.COOKED_RABBIT),
            Map.entry(Items.COD, Items.COOKED_COD),
            Map.entry(Items.SALMON, Items.COOKED_SALMON),
            Map.entry(Items.POTATO, Items.BAKED_POTATO),
            Map.entry(Items.KELP, Items.DRIED_KELP));

    private Schmelzen() {
    }

    /** Ergebnis im Ofen, oder null, wenn sich der Gegenstand nicht schmelzen lässt. */
    public static Item ergebnis(ItemStack roh) {
        if (roh.isEmpty()) {
            return null;
        }
        if (roh.is(ItemTags.LOGS_THAT_BURN)) {
            return Items.CHARCOAL;
        }
        return ERGEBNIS.get(roh.getItem());
    }

    /** Rohes Essen, das sich braten lässt (Fleisch, Fisch, Kartoffeln). */
    public static boolean istRohesEssen(ItemStack s) {
        Item e = ERGEBNIS.get(s.getItem());
        return e != null && new ItemStack(e).has(DataComponents.FOOD);
    }

    /** Fertiges, bekömmliches Essen (gebraten, gebacken oder sonst essbar und nicht roh). */
    public static boolean istFertigesEssen(ItemStack s) {
        return s.has(DataComponents.FOOD) && !istRohesEssen(s) && !s.is(Items.ROTTEN_FLESH) && !s.is(Items.SPIDER_EYE)
                && !s.is(Items.POISONOUS_POTATO) && !s.is(Items.PUFFERFISH) && !s.is(Items.CHORUS_FRUIT)
                && !s.is(Items.SUSPICIOUS_STEW);
    }

    /**
     * Brennwert in halben Schmelzvorgängen (Kohle = 16, also 8 Gegenstände). 0 = kein Brennstoff.
     */
    public static int brennwert(ItemStack s) {
        if (s.is(Items.COAL) || s.is(Items.CHARCOAL)) {
            return 16;
        }
        if (s.is(Items.COAL_BLOCK)) {
            return 160;
        }
        if (s.is(ItemTags.LOGS_THAT_BURN) || s.is(ItemTags.PLANKS)) {
            return 3;
        }
        if (s.is(Items.STICK)) {
            return 1;
        }
        return 0;
    }

    /** Passt der Ofen zum Gegenstand? Räucherofen nur für Essen, Hochofen nur für Erze. */
    public static boolean passt(AbstractFurnaceBlockEntity ofen, ItemStack roh) {
        if (ofen instanceof SmokerBlockEntity) {
            return istRohesEssen(roh);
        }
        if (ofen instanceof BlastFurnaceBlockEntity) {
            return roh.is(Items.RAW_IRON) || roh.is(Items.RAW_GOLD) || roh.is(Items.RAW_COPPER);
        }
        return ergebnis(roh) != null;
    }
}
