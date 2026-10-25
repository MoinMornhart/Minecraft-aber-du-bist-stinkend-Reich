package de.moinmornhart.stinkendreich.ausruestung;

import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.register.ModRegister;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

//? if >=1.21.2 {
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
//?} else {
/*import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
*///?}

/**
 * Der Agenten-Anzug: Bond-Sonnenbrille, Smoking, Anzughose und Lackschuhe. Ein eigenes Rüstungsmaterial
 * (etwas stärker als Netherit) mit eigener Textur am Körper (siehe tools/assets/ausruestung.py); die Kräfte
 * verteilt {@link AgentKraefte}.
 *
 * <p>Rüstung wird in jeder Minecraft-Version anders angelegt: bis 1.21.1 als registriertes Material mit
 * Texturschichten, 1.21.2 bis 1.21.4 als {@code ArmorItem} mit Ausrüstungsmodell, ab 1.21.5 als normales Item
 * mit Rüstungseigenschaft.</p>
 */
public final class AgentRuestung {
    /** Die vier Teile des Anzugs. */
    public enum Teil {
        KOPF(EquipmentSlot.HEAD, "stinkendreich.agent.brille"),
        BRUST(EquipmentSlot.CHEST, "stinkendreich.agent.smoking"),
        BEINE(EquipmentSlot.LEGS, "stinkendreich.agent.hose"),
        FUESSE(EquipmentSlot.FEET, "stinkendreich.agent.schuhe");

        public final EquipmentSlot platz;
        final String kraft;

        Teil(EquipmentSlot platz, String kraft) {
            this.platz = platz;
            this.kraft = kraft;
        }
    }

    /** Haltbarkeit (wie bei Vanilla mal Grundwert je Teil), Verzauberbarkeit, Härte, Rückstoßfestigkeit. */
    private static final int HALTBARKEIT = 45, VERZAUBERBAR = 25;
    private static final float HAERTE = 4.0F, RUECKSTOSS = 0.15F;

    //? if >=1.21.2 {
    private static final ArmorMaterial MATERIAL = new ArmorMaterial(HALTBARKEIT,
            Map.of(ArmorType.BOOTS, 4, ArmorType.LEGGINGS, 7, ArmorType.CHESTPLATE, 9, ArmorType.HELMET, 4, ArmorType.BODY, 12),
            VERZAUBERBAR, SoundEvents.ARMOR_EQUIP_NETHERITE, HAERTE, RUECKSTOSS,
            TagKey.create(Registries.ITEM, StinkendReich.id("repariert_agentenanzug")), aussehen());
    //?} else {
    /*private static final Holder<ArmorMaterial> MATERIAL = Registry.registerForHolder(BuiltInRegistries.ARMOR_MATERIAL,
            StinkendReich.id("agent"), new ArmorMaterial(
                    Map.of(ArmorItem.Type.BOOTS, 4, ArmorItem.Type.LEGGINGS, 7, ArmorItem.Type.CHESTPLATE, 9, ArmorItem.Type.HELMET, 4, ArmorItem.Type.BODY, 12),
                    VERZAUBERBAR, SoundEvents.ARMOR_EQUIP_NETHERITE, () -> Ingredient.of(Items.GOLD_INGOT),
                    List.of(new ArmorMaterial.Layer(StinkendReich.id("agent"))), HAERTE, RUECKSTOSS));
    *///?}

    /** Name des Ausrüstungsmodells (assets/stinkendreich/equipment/agent.json bzw. models/equipment/agent.json). */
    //? if >=1.21.4 {
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.item.equipment.EquipmentAsset> aussehen() {
        return net.minecraft.resources.ResourceKey.create(net.minecraft.world.item.equipment.EquipmentAssets.ROOT_ID, StinkendReich.id("agent"));
    }
    //?} else if >=1.21.2 {
    /*private static net.minecraft.resources.Identifier aussehen() {
        return StinkendReich.id("agent");
    }
    *///?}

    private AgentRuestung() {
    }

    /** Registriert ein Teil des Anzugs. */
    public static Item teil(String name, Teil teil) {
        Item.Properties p = new Item.Properties().rarity(Rarity.EPIC).component(DataComponents.LORE, beschreibung(teil));
        //? if >=1.21.5 {
        return ModRegister.item(name, e -> new Item(e.humanoidArmor(MATERIAL, typ(teil))), p);
        //?} else if >=1.21.2 {
        /*return ModRegister.item(name, e -> new net.minecraft.world.item.ArmorItem(MATERIAL, typ(teil), e), p);
        *///?} else {
        /*return ModRegister.item(name, e -> new ArmorItem(MATERIAL, typ(teil), e), p.durability(typ(teil).getDurability(HALTBARKEIT)));
        *///?}
    }

    //? if >=1.21.2 {
    private static ArmorType typ(Teil teil) {
        return switch (teil) {
            case KOPF -> ArmorType.HELMET;
            case BRUST -> ArmorType.CHESTPLATE;
            case BEINE -> ArmorType.LEGGINGS;
            case FUESSE -> ArmorType.BOOTS;
        };
    }
    //?} else {
    /*private static ArmorItem.Type typ(Teil teil) {
        return switch (teil) {
            case KOPF -> ArmorItem.Type.HELMET;
            case BRUST -> ArmorItem.Type.CHESTPLATE;
            case BEINE -> ArmorItem.Type.LEGGINGS;
            case FUESSE -> ArmorItem.Type.BOOTS;
        };
    }
    *///?}

    /** Beschreibung unter dem Namen: die Kraft des Teils und was das ganze Set kann. */
    private static ItemLore beschreibung(Teil teil) {
        List<Component> zeilen = new ArrayList<>();
        zeilen.add(Component.translatable(teil.kraft).withStyle(s -> s.withColor(ChatFormatting.GOLD).withItalic(false)));
        zeilen.add(Component.translatable("stinkendreich.agent.set").withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false)));
        return new ItemLore(zeilen);
    }

    /** Beschreibung für eine Waffe. */
    public static ItemLore beschreibung(String schluessel) {
        return new ItemLore(List.of(Component.translatable(schluessel).withStyle(s -> s.withColor(ChatFormatting.GOLD).withItalic(false))));
    }

    /** Trägt die Figur dieses Teil des Agenten-Anzugs? */
    public static boolean traegt(LivingEntity e, Teil teil) {
        return e.getItemBySlot(teil.platz).is(item(teil));
    }

    /** Trägt die Figur den ganzen Anzug? */
    public static boolean ganzesSet(LivingEntity e) {
        for (Teil t : Teil.values()) {
            if (!traegt(e, t)) {
                return false;
            }
        }
        return true;
    }

    public static Item item(Teil teil) {
        return switch (teil) {
            case KOPF -> de.moinmornhart.stinkendreich.register.ModItems.BOND_SONNENBRILLE;
            case BRUST -> de.moinmornhart.stinkendreich.register.ModItems.SMOKING;
            case BEINE -> de.moinmornhart.stinkendreich.register.ModItems.ANZUGHOSE;
            case FUESSE -> de.moinmornhart.stinkendreich.register.ModItems.LACKSCHUHE;
        };
    }
}
