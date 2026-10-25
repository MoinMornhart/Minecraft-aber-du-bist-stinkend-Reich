package de.moinmornhart.stinkendreich.ausruestung;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.register.ModItems;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.List;

//? if >=1.21.2 {
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.item.equipment.trim.TrimPatterns;
//?} else {
/*import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.armortrim.TrimMaterial;
import net.minecraft.world.item.armortrim.TrimMaterials;
import net.minecraft.world.item.armortrim.TrimPattern;
import net.minecraft.world.item.armortrim.TrimPatterns;
*///?}

/**
 * Die Waffenkammer im Bunker: sechs Rüstungsständer mit den besten Sets, die es gibt, alles mit den höchsten
 * Verzauberungen. Netherit bekommt goldene Verzierungen. Die Ständer stellt die Villa beim Bau auf
 * (Marke „ruestung“ im Bauplan: x, y, z, Blickrichtung, Nummer des Sets).
 * <ol start="0">
 *   <li>Agent: Bond-Sonnenbrille, Smoking, Anzughose, Lackschuhe, Goldene Pistole und Laser-Armbanduhr</li>
 *   <li>Gott-Rüstung: Netherit mit Schutz IV, Dornen III, Reparatur …, Netheritschwert, Totem</li>
 *   <li>Flieger: Elytra statt Brustpanzer, Streitkolben, Feuerwerk</li>
 *   <li>Goldprotz: verzauberte Goldrüstung mit Diamant-Verzierung, Geldkanone, verzauberter Goldapfel</li>
 *   <li>Schütze: Netherit, Bogen mit Stärke V und Unendlichkeit</li>
 *   <li>Poseidon: Netherit, Dreizack mit Treue, Kanalisierung und Harpune</li>
 * </ol>
 */
public final class Waffenkammer {
    private Waffenkammer() {
    }

    /** Stellt einen ausgerüsteten Rüstungsständer auf. */
    public static void aufstellen(ServerLevel welt, double x, double y, double z, float blick, int set) {
        // Die Liste der Arten steht seit 26.2 in EntityTypes
        //? if >=26.2 {
        ArmorStand staender = net.minecraft.world.entity.EntityTypes.ARMOR_STAND.create(welt
        //?} else
        /*ArmorStand staender = EntityType.ARMOR_STAND.create(welt*/
                //? if >=1.21.2
                , net.minecraft.world.entity.EntitySpawnReason.STRUCTURE
        );
        if (staender == null) {
            return;
        }
        Kompat.versetzen(staender, x, y, z, blick, 0.0F);
        staender.setYBodyRot(blick);
        staender.setYHeadRot(blick);
        staender.setShowArms(true);
        ausruesten(welt, staender, set);
        // Mit Pistole oder Geldkanone zielt der Ständer wie die Spielerfigur (Arm waagerecht nach vorn), sonst
        // zeigte der Lauf zum Boden
        if (AgentWaffe.zielt(staender.getItemBySlot(EquipmentSlot.MAINHAND))) {
            staender.setRightArmPose(new net.minecraft.core.Rotations(-90.0F, -8.0F, 0.0F));
        }
        welt.addFreshEntity(staender);
    }

    private static void ausruesten(ServerLevel welt, ArmorStand s, int set) {
        Verzauberer v = new Verzauberer(welt);
        switch (set) {
            case 0 -> {
                anziehen(s, new ItemStack(ModItems.BOND_SONNENBRILLE), new ItemStack(ModItems.SMOKING),
                        new ItemStack(ModItems.ANZUGHOSE), new ItemStack(ModItems.LACKSCHUHE));
                s.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.GOLDENE_PISTOLE));
                s.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(ModItems.LASERUHR));
            }
            case 1 -> {
                anziehen(s, v.helm(Items.NETHERITE_HELMET), v.brust(Items.NETHERITE_CHESTPLATE),
                        v.hose(Items.NETHERITE_LEGGINGS), v.stiefel(Items.NETHERITE_BOOTS));
                verzieren(welt, s, TrimMaterials.GOLD, TrimPatterns.SILENCE);
                s.setItemSlot(EquipmentSlot.MAINHAND, v.mit(new ItemStack(Items.NETHERITE_SWORD), Enchantments.SHARPNESS, 5,
                        Enchantments.LOOTING, 3, Enchantments.FIRE_ASPECT, 2, Enchantments.SWEEPING_EDGE, 3, Enchantments.KNOCKBACK, 2,
                        Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));
                s.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            }
            case 2 -> {
                anziehen(s, v.helm(Items.NETHERITE_HELMET), v.mit(new ItemStack(Items.ELYTRA), Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1),
                        v.hose(Items.NETHERITE_LEGGINGS), v.stiefel(Items.NETHERITE_BOOTS));
                verzieren(welt, s, TrimMaterials.GOLD, TrimPatterns.RIB);
                s.setItemSlot(EquipmentSlot.MAINHAND, v.mit(new ItemStack(Items.MACE), Enchantments.DENSITY, 5, Enchantments.WIND_BURST, 3,
                        Enchantments.FIRE_ASPECT, 2, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));
                s.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.FIREWORK_ROCKET, 64));
            }
            case 3 -> {
                anziehen(s, v.helm(Items.GOLDEN_HELMET), v.brust(Items.GOLDEN_CHESTPLATE), v.hose(Items.GOLDEN_LEGGINGS), v.stiefel(Items.GOLDEN_BOOTS));
                verzieren(welt, s, TrimMaterials.DIAMOND, TrimPatterns.WILD);
                s.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.GELDKANONE));
                s.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
            }
            case 4 -> {
                anziehen(s, v.helm(Items.NETHERITE_HELMET), v.brust(Items.NETHERITE_CHESTPLATE),
                        v.hose(Items.NETHERITE_LEGGINGS), v.stiefel(Items.NETHERITE_BOOTS));
                verzieren(welt, s, TrimMaterials.GOLD, TrimPatterns.EYE);
                s.setItemSlot(EquipmentSlot.MAINHAND, v.mit(new ItemStack(Items.BOW), Enchantments.POWER, 5, Enchantments.PUNCH, 2,
                        Enchantments.FLAME, 1, Enchantments.INFINITY, 1, Enchantments.UNBREAKING, 3));
                s.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SPECTRAL_ARROW, 64));
            }
            default -> {
                anziehen(s, v.helm(Items.NETHERITE_HELMET), v.brust(Items.NETHERITE_CHESTPLATE),
                        v.hose(Items.NETHERITE_LEGGINGS), v.stiefel(Items.NETHERITE_BOOTS));
                verzieren(welt, s, TrimMaterials.GOLD, TrimPatterns.COAST);
                s.setItemSlot(EquipmentSlot.MAINHAND, v.mit(new ItemStack(Items.TRIDENT), Enchantments.LOYALTY, 3, Enchantments.CHANNELING, 1,
                        Enchantments.IMPALING, 5, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));
                s.setItemSlot(EquipmentSlot.OFFHAND, v.mit(new ItemStack(Items.SHIELD), Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));
            }
        }
    }

    private static void anziehen(ArmorStand s, ItemStack kopf, ItemStack brust, ItemStack beine, ItemStack fuesse) {
        s.setItemSlot(EquipmentSlot.HEAD, kopf);
        s.setItemSlot(EquipmentSlot.CHEST, brust);
        s.setItemSlot(EquipmentSlot.LEGS, beine);
        s.setItemSlot(EquipmentSlot.FEET, fuesse);
    }

    /** Verzierung (Rüstungsbesatz) auf alle Rüstungsteile des Ständers (Elytra bekommt keine). */
    private static void verzieren(ServerLevel welt, ArmorStand s, ResourceKey<TrimMaterial> material, ResourceKey<TrimPattern> muster) {
        HolderLookup.Provider r = welt.registryAccess();
        ArmorTrim besatz = new ArmorTrim(r.lookupOrThrow(Registries.TRIM_MATERIAL).getOrThrow(material),
                r.lookupOrThrow(Registries.TRIM_PATTERN).getOrThrow(muster));
        for (EquipmentSlot platz : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            ItemStack teil = s.getItemBySlot(platz);
            if (!teil.isEmpty() && !teil.is(Items.ELYTRA)) {
                teil.set(DataComponents.TRIM, besatz);
            }
        }
    }

    /** Verzaubert Gegenstände mit den höchsten Stufen (Verzauberungen sind ab 1.21 Daten der Welt). */
    private static final class Verzauberer {
        private final HolderLookup.RegistryLookup<Enchantment> alle;

        Verzauberer(ServerLevel welt) {
            alle = welt.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        }

        /** Paare aus Verzauberung und Stufe, z. B. {@code mit(stapel, SHARPNESS, 5, LOOTING, 3)}. */
        ItemStack mit(ItemStack stapel, Object... paare) {
            for (int i = 0; i + 1 < paare.length; i += 2) {
                @SuppressWarnings("unchecked")
                ResourceKey<Enchantment> art = (ResourceKey<Enchantment>) paare[i];
                Holder<Enchantment> h = alle.getOrThrow(art);
                stapel.enchant(h, (Integer) paare[i + 1]);
            }
            return stapel;
        }

        private ItemStack schutz(Item item, Object... weitere) {
            ItemStack s = mit(new ItemStack(item), Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1,
                    Enchantments.THORNS, 3);
            return mit(s, weitere);
        }

        ItemStack helm(Item item) {
            return schutz(item, Enchantments.RESPIRATION, 3, Enchantments.AQUA_AFFINITY, 1);
        }

        ItemStack brust(Item item) {
            return schutz(item);
        }

        ItemStack hose(Item item) {
            return schutz(item, Enchantments.SWIFT_SNEAK, 3);
        }

        ItemStack stiefel(Item item) {
            return schutz(item, Enchantments.FEATHER_FALLING, 4, Enchantments.DEPTH_STRIDER, 3, Enchantments.SOUL_SPEED, 3);
        }
    }
}
