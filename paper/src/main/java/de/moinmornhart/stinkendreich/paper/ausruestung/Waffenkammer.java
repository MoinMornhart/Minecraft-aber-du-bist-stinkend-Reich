package de.moinmornhart.stinkendreich.paper.ausruestung;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.util.EulerAngle;

/**
 * Die Waffenkammer im Bunker (wie {@code Waffenkammer} in der Mod): sechs Rüstungsständer mit den besten Sets,
 * alles mit den höchsten Verzauberungen, Netherit mit goldenen Verzierungen. Die Ständer stehen an den Marken
 * „ruestung“ des Bauplans (x, y, z, Blickrichtung, Nummer des Sets).
 * <ol start="0">
 *   <li>Agent: Bond-Sonnenbrille, Smoking, Anzughose, Lackschuhe, Goldene Pistole und Laser-Armbanduhr</li>
 *   <li>Gott-Rüstung: Netherit, Netheritschwert, Totem</li>
 *   <li>Flieger: Elytra statt Brustpanzer, Streitkolben, Feuerwerk</li>
 *   <li>Goldprotz: Goldrüstung mit Diamant-Verzierung, Geldkanone, verzauberter Goldapfel</li>
 *   <li>Schütze: Netherit, Bogen mit Stärke V und Unendlichkeit</li>
 *   <li>Poseidon: Netherit, Dreizack mit Treue, Kanalisierung und Harpune</li>
 * </ol>
 */
public final class Waffenkammer {
    private Waffenkammer() {
    }

    /** Stellt einen ausgerüsteten Rüstungsständer auf. */
    public static void aufstellen(Agent agent, Location ort, float blick, int set) {
        ort.setYaw(blick);
        ort.setPitch(0.0F);
        ort.getWorld().spawn(ort, ArmorStand.class, s -> {
            s.setArms(true);
            s.setPersistent(true);
            ausruesten(agent, s.getEquipment(), set);
            // Mit Pistole oder Geldkanone zielt der Ständer (Arm waagerecht nach vorn), sonst zeigt der Lauf zum Boden
            if (set == 0 || set == 3) {
                s.setRightArmPose(new EulerAngle(Math.toRadians(-90), Math.toRadians(-8), 0));
            }
        });
    }

    private static void ausruesten(Agent agent, EntityEquipment a, int set) {
        switch (set) {
            case 0 -> {
                anziehen(a, agent.teil(Agent.Teil.KOPF), agent.teil(Agent.Teil.BRUST), agent.teil(Agent.Teil.BEINE),
                        agent.teil(Agent.Teil.FUESSE));
                a.setItemInMainHand(agent.waffe(Agent.Waffe.PISTOLE));
                a.setItemInOffHand(agent.waffe(Agent.Waffe.LASER));
            }
            case 1 -> {
                anziehen(a, helm(Material.NETHERITE_HELMET), brust(Material.NETHERITE_CHESTPLATE),
                        hose(Material.NETHERITE_LEGGINGS), stiefel(Material.NETHERITE_BOOTS));
                verzieren(a, TrimMaterial.GOLD, TrimPattern.SILENCE);
                a.setItemInMainHand(mit(ItemStack.of(Material.NETHERITE_SWORD), Enchantment.SHARPNESS, 5, Enchantment.LOOTING, 3,
                        Enchantment.FIRE_ASPECT, 2, Enchantment.SWEEPING_EDGE, 3, Enchantment.KNOCKBACK, 2,
                        Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
                a.setItemInOffHand(ItemStack.of(Material.TOTEM_OF_UNDYING));
            }
            case 2 -> {
                anziehen(a, helm(Material.NETHERITE_HELMET), mit(ItemStack.of(Material.ELYTRA), Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1),
                        hose(Material.NETHERITE_LEGGINGS), stiefel(Material.NETHERITE_BOOTS));
                verzieren(a, TrimMaterial.GOLD, TrimPattern.RIB);
                a.setItemInMainHand(mit(ItemStack.of(Material.MACE), Enchantment.DENSITY, 5, Enchantment.WIND_BURST, 3,
                        Enchantment.FIRE_ASPECT, 2, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
                a.setItemInOffHand(ItemStack.of(Material.FIREWORK_ROCKET, 64));
            }
            case 3 -> {
                anziehen(a, helm(Material.GOLDEN_HELMET), brust(Material.GOLDEN_CHESTPLATE), hose(Material.GOLDEN_LEGGINGS),
                        stiefel(Material.GOLDEN_BOOTS));
                verzieren(a, TrimMaterial.DIAMOND, TrimPattern.WILD);
                a.setItemInMainHand(agent.waffe(Agent.Waffe.GELDKANONE));
                a.setItemInOffHand(ItemStack.of(Material.ENCHANTED_GOLDEN_APPLE));
            }
            case 4 -> {
                anziehen(a, helm(Material.NETHERITE_HELMET), brust(Material.NETHERITE_CHESTPLATE),
                        hose(Material.NETHERITE_LEGGINGS), stiefel(Material.NETHERITE_BOOTS));
                verzieren(a, TrimMaterial.GOLD, TrimPattern.EYE);
                a.setItemInMainHand(mit(ItemStack.of(Material.BOW), Enchantment.POWER, 5, Enchantment.PUNCH, 2,
                        Enchantment.FLAME, 1, Enchantment.INFINITY, 1, Enchantment.UNBREAKING, 3));
                a.setItemInOffHand(ItemStack.of(Material.SPECTRAL_ARROW, 64));
            }
            default -> {
                anziehen(a, helm(Material.NETHERITE_HELMET), brust(Material.NETHERITE_CHESTPLATE),
                        hose(Material.NETHERITE_LEGGINGS), stiefel(Material.NETHERITE_BOOTS));
                verzieren(a, TrimMaterial.GOLD, TrimPattern.COAST);
                a.setItemInMainHand(mit(ItemStack.of(Material.TRIDENT), Enchantment.LOYALTY, 3, Enchantment.CHANNELING, 1,
                        Enchantment.IMPALING, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
                a.setItemInOffHand(mit(ItemStack.of(Material.SHIELD), Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
            }
        }
    }

    private static void anziehen(EntityEquipment a, ItemStack kopf, ItemStack brust, ItemStack beine, ItemStack fuesse) {
        a.setHelmet(kopf);
        a.setChestplate(brust);
        a.setLeggings(beine);
        a.setBoots(fuesse);
    }

    /** Verzierung auf alle Rüstungsteile des Ständers (Elytra und Agenten-Anzug bekommen keine). */
    private static void verzieren(EntityEquipment a, TrimMaterial material, TrimPattern muster) {
        ArmorTrim besatz = new ArmorTrim(material, muster);
        a.setHelmet(verziert(a.getHelmet(), besatz));
        a.setChestplate(verziert(a.getChestplate(), besatz));
        a.setLeggings(verziert(a.getLeggings(), besatz));
        a.setBoots(verziert(a.getBoots(), besatz));
    }

    private static ItemStack verziert(ItemStack teil, ArmorTrim besatz) {
        if (teil != null && teil.getType() != Material.ELYTRA) {
            teil.editMeta(ArmorMeta.class, m -> m.setTrim(besatz));
        }
        return teil;
    }

    /** Paare aus Verzauberung und Stufe, z. B. {@code mit(stapel, SHARPNESS, 5, LOOTING, 3)}. */
    private static ItemStack mit(ItemStack stapel, Object... paare) {
        for (int i = 0; i + 1 < paare.length; i += 2) {
            stapel.addUnsafeEnchantment((Enchantment) paare[i], (Integer) paare[i + 1]);
        }
        return stapel;
    }

    private static ItemStack schutz(Material item, Object... weitere) {
        ItemStack s = mit(ItemStack.of(item), Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1,
                Enchantment.THORNS, 3);
        return mit(s, weitere);
    }

    private static ItemStack helm(Material item) {
        return schutz(item, Enchantment.RESPIRATION, 3, Enchantment.AQUA_AFFINITY, 1);
    }

    private static ItemStack brust(Material item) {
        return schutz(item);
    }

    private static ItemStack hose(Material item) {
        return schutz(item, Enchantment.SWIFT_SNEAK, 3);
    }

    private static ItemStack stiefel(Material item) {
        return schutz(item, Enchantment.FEATHER_FALLING, 4, Enchantment.DEPTH_STRIDER, 3, Enchantment.SOUL_SPEED, 3);
    }
}
