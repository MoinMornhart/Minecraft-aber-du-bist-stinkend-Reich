package de.moinmornhart.stinkendreich.register;

import de.moinmornhart.stinkendreich.StinkendReich;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import de.moinmornhart.stinkendreich.mechanik.ZiertuerBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Hilfen zum Registrieren von Blöcken, Items und Sounds. Gleicht die Unterschiede aus:
 * Ab 1.21.2 brauchen Block- und Item-Eigenschaften ihre ID schon vor dem Erzeugen.
 */
public final class ModRegister {
    /** Alle Items der Mod in Reihenfolge, für den Kreativ-Tab. */
    public static final List<Item> ALLE_ITEMS = new ArrayList<>();

    private ModRegister() {
    }

    public static <B extends Block> B block(String name, Function<BlockBehaviour.Properties, B> fabrik, BlockBehaviour.Properties eigenschaften) {
        return block(name, fabrik, eigenschaften, true);
    }

    /** Registriert einen Block und – falls gewünscht – das passende Item. */
    public static <B extends Block> B block(String name, Function<BlockBehaviour.Properties, B> fabrik,
                                            BlockBehaviour.Properties eigenschaften, boolean mitItem) {
        Identifier id = StinkendReich.id(name);
        //? if >=1.21.2
        eigenschaften.setId(ResourceKey.create(Registries.BLOCK, id));
        B block = Registry.register(BuiltInRegistries.BLOCK, id, fabrik.apply(eigenschaften));
        if (mitItem) {
            Item.Properties ip = new Item.Properties();
            //? if >=1.21.2
            ip = ip.setId(ResourceKey.create(Registries.ITEM, id)).useBlockDescriptionPrefix();
            ALLE_ITEMS.add(Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(block, ip)));
        }
        return block;
    }

    /** Registriert eine zweiteilige Tür samt Item, das beide Hälften setzt. */
    public static Block tuer(String name, BlockSetType art, BlockBehaviour.Properties eigenschaften) {
        return tuer(name, art, eigenschaften, ZiertuerBlock::new);
    }

    /** Wie {@link #tuer(String, BlockSetType, BlockBehaviour.Properties)}, aber mit eigener Türklasse. */
    public static Block tuer(String name, BlockSetType art, BlockBehaviour.Properties eigenschaften,
                             java.util.function.BiFunction<BlockSetType, BlockBehaviour.Properties, Block> fabrik) {
        Identifier id = StinkendReich.id(name);
        //? if >=1.21.2
        eigenschaften.setId(ResourceKey.create(Registries.BLOCK, id));
        Block block = Registry.register(BuiltInRegistries.BLOCK, id, fabrik.apply(art, eigenschaften));
        Item.Properties ip = new Item.Properties();
        //? if >=1.21.2
        ip = ip.setId(ResourceKey.create(Registries.ITEM, id)).useBlockDescriptionPrefix();
        ALLE_ITEMS.add(Registry.register(BuiltInRegistries.ITEM, id, new DoubleHighBlockItem(block, ip)));
        return block;
    }

    /** XXL-Kingsize-Bett: Block mit Bett-Item (setzt Fuß- und Kopfteil). */
    public static Block bett(String name, BlockBehaviour.Properties eigenschaften) {
        Identifier id = StinkendReich.id(name);
        //? if >=1.21.2
        eigenschaften.setId(ResourceKey.create(Registries.BLOCK, id));
        Block block = Registry.register(BuiltInRegistries.BLOCK, id, new de.moinmornhart.stinkendreich.moebel.KingsizeBettBlock(eigenschaften));
        Item.Properties ip = new Item.Properties().stacksTo(1);
        //? if >=1.21.2
        ip = ip.setId(ResourceKey.create(Registries.ITEM, id)).useBlockDescriptionPrefix();
        // Ab 26.3 gibt es kein eigenes Bett-Item mehr, das normale Block-Item setzt beide Hälften
        //? if >=26.3 {
        ALLE_ITEMS.add(Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(block, ip)));
        //?} else
        /*ALLE_ITEMS.add(Registry.register(BuiltInRegistries.ITEM, id, new net.minecraft.world.item.BedItem(block, ip)));*/
        return block;
    }

    /** Registriert ein einfaches Item. */
    public static <I extends Item> I item(String name, Function<Item.Properties, I> fabrik, Item.Properties eigenschaften) {
        Identifier id = StinkendReich.id(name);
        //? if >=1.21.2
        eigenschaften = eigenschaften.setId(ResourceKey.create(Registries.ITEM, id));
        I item = Registry.register(BuiltInRegistries.ITEM, id, fabrik.apply(eigenschaften));
        ALLE_ITEMS.add(item);
        return item;
    }

    public static SoundEvent sound(String name) {
        Identifier id = StinkendReich.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }
}
