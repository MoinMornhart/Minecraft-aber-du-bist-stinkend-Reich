package de.moinmornhart.stinkendreich.paper.ausruestung;

import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.UseCooldown;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Die Agenten-Ausrüstung auf dem Paper-Server (wie {@code AgentRuestung}, {@code AgentKraefte} und {@code AgentWaffe}
 * in der Mod). Die Teile sind Gegenstände aus dem Spiel mit dem Modell der Mod (Netherit-Rüstung mit dem
 * Ausrüstungsmodell {@code stinkendreich:agent}, Papier für die Waffen); woran das Plugin sie erkennt, steht im
 * PDC-Feld {@code agent}.
 * <ul>
 *   <li>Bond-Sonnenbrille: Nachtsicht, Gegner in 24 Blöcken leuchten</li>
 *   <li>Smoking: Resistenz II, Feuerresistenz · Anzughose: Schnelligkeit II · Lackschuhe: Sprungkraft II, kein Fallschaden</li>
 *   <li>ganzes Set: Stärke II, Regeneration</li>
 *   <li>Goldene Pistole, Laser-Armbanduhr, Geldkanone: Rechtsklick, Sofort-Treffer ohne Blockschaden</li>
 * </ul>
 */
public final class Agent implements Listener {
    /** Die vier Teile des Anzugs: Item-Name, Platz, Rüstungsgegenstand als Grundlage, Rüstungswert, Haltbarkeit, Kraft. */
    public enum Teil {
        KOPF("bond_sonnenbrille", "Bond-Sonnenbrille", EquipmentSlot.HEAD, Material.NETHERITE_HELMET, 4, 11,
                "Nachtsicht · Agentenblick: Gegner leuchten durch Wände", "stinkendreich.agent.brille"),
        BRUST("smoking", "Smoking", EquipmentSlot.CHEST, Material.NETHERITE_CHESTPLATE, 9, 16,
                "Resistenz II · Feuerfest", "stinkendreich.agent.smoking"),
        BEINE("anzughose", "Anzughose", EquipmentSlot.LEGS, Material.NETHERITE_LEGGINGS, 7, 15,
                "Schnelligkeit II", "stinkendreich.agent.hose"),
        FUESSE("lackschuhe", "Lackschuhe", EquipmentSlot.FEET, Material.NETHERITE_BOOTS, 4, 13,
                "Sprungkraft II · kein Fallschaden", "stinkendreich.agent.schuhe");

        final String name, anzeige, kraft, kraftSchluessel;
        final EquipmentSlot platz;
        final Material grundlage;
        final int ruestung, haltbarkeit;

        Teil(String name, String anzeige, EquipmentSlot platz, Material grundlage, int ruestung, int haltbarkeit,
             String kraft, String kraftSchluessel) {
            this.name = name;
            this.anzeige = anzeige;
            this.platz = platz;
            this.grundlage = grundlage;
            this.ruestung = ruestung;
            this.haltbarkeit = haltbarkeit;
            this.kraft = kraft;
            this.kraftSchluessel = kraftSchluessel;
        }
    }

    /** Die drei Waffen: Schaden, Reichweite, Nachladen in Ticks (wie in der Mod). */
    public enum Waffe {
        PISTOLE("goldene_pistole", "Goldene Pistole", 8.0, 64.0, 6,
                "Rechtsklick: Schuss (8 Schaden, 64 Blöcke)", "stinkendreich.agent.pistole"),
        LASER("laseruhr", "Laser-Armbanduhr", 12.0, 48.0, 25,
                "Rechtsklick: Laserstrahl (12 Schaden, setzt in Brand)", "stinkendreich.agent.laser"),
        GELDKANONE("geldkanone", "Geldkanone", 3.0, 12.0, 30,
                "Rechtsklick: Geldregen – blendet und bremst Gegner", "stinkendreich.agent.geld");

        final String name, anzeige, text, textSchluessel;
        final double schaden, reichweite;
        final int nachladen;

        Waffe(String name, String anzeige, double schaden, double reichweite, int nachladen, String text, String textSchluessel) {
            this.name = name;
            this.anzeige = anzeige;
            this.schaden = schaden;
            this.reichweite = reichweite;
            this.nachladen = nachladen;
            this.text = text;
            this.textSchluessel = textSchluessel;
        }

        /** Hält man diese Waffe zum Zielen (Rüstungsständer heben dann den Arm)? Die Laseruhr nicht. */
        public boolean zielt() {
            return this != LASER;
        }
    }

    /** Wirkungsdauer in Ticks (Nachtsicht länger, sonst flackert der Bildschirm); Reichweite des Agentenblicks. */
    private static final int DAUER = 40, NACHTSICHT = 300;
    private static final double AGENTENBLICK = 24.0;
    /** Härte und Rückstoßfestigkeit des Anzugs (etwas mehr als Netherit), Haltbarkeit = 45 × Grundwert. */
    private static final double HAERTE = 4.0, RUECKSTOSS = 0.15;
    private static final int HALTBARKEIT = 45;

    private final StinkendReichPaper plugin;
    private final NamespacedKey schluessel;
    /** Figuren des Plugins, die keine Waffe trifft (Butler-Körper, Bodyguards). */
    private final List<NamespacedKey> geschuetzt;

    public Agent(StinkendReichPaper plugin) {
        this.plugin = plugin;
        this.schluessel = new NamespacedKey(plugin, "agent");
        this.geschuetzt = List.of(new NamespacedKey(plugin, "butler"), new NamespacedKey(plugin, "bodyguard"));
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::kraefte, 10L, 10L);
    }

    // ------------------------------------------------------------------
    // Gegenstände
    // ------------------------------------------------------------------

    /**
     * Gegenstand nach seinem Namen in der Mod ({@code smoking}, {@code goldene_pistole} …), z. B. für die Truhen der
     * Villa; {@code null}, wenn der Name kein Teil der Agenten-Ausrüstung ist.
     */
    public ItemStack item(String name) {
        for (Teil t : Teil.values()) {
            if (t.name.equals(name)) {
                return teil(t);
            }
        }
        for (Waffe w : Waffe.values()) {
            if (w.name.equals(name)) {
                return waffe(w);
            }
        }
        return null;
    }

    /** Ein Teil des Anzugs: Netherit-Rüstung mit Modell, Ausrüstungsmodell und Werten der Mod. */
    @SuppressWarnings("UnstableApiUsage")
    public ItemStack teil(Teil t) {
        ItemStack s = ItemStack.of(t.grundlage);
        grunddaten(s, t.name, t.anzeige, List.of(
                zeile(t.kraftSchluessel, t.kraft, NamedTextColor.GOLD),
                zeile("stinkendreich.agent.set", "Ganzes Set (Lizenz zum Töten): Stärke II, Regeneration", NamedTextColor.GRAY)));
        s.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(t.platz)
                .assetId(Key.key("stinkendreich", "agent"))
                .equipSound(Key.key("minecraft", "item.armor.equip_netherite"))
                .build());
        EquipmentSlotGroup gruppe = t.platz.getGroup();
        s.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.itemAttributes()
                .addModifier(Attribute.ARMOR, modifikator("ruestung_" + t.name, t.ruestung), gruppe)
                .addModifier(Attribute.ARMOR_TOUGHNESS, modifikator("haerte_" + t.name, HAERTE), gruppe)
                .addModifier(Attribute.KNOCKBACK_RESISTANCE, modifikator("rueckstoss_" + t.name, RUECKSTOSS), gruppe)
                .build());
        s.setData(DataComponentTypes.MAX_DAMAGE, HALTBARKEIT * t.haltbarkeit);
        return s;
    }

    /** Eine Waffe: Papier mit dem Modell der Mod und eigener Nachladezeit (Anzeige über dem Gegenstand). */
    @SuppressWarnings("UnstableApiUsage")
    public ItemStack waffe(Waffe w) {
        ItemStack s = ItemStack.of(Material.PAPER);
        grunddaten(s, w.name, w.anzeige, List.of(zeile(w.textSchluessel, w.text, NamedTextColor.GOLD)));
        s.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        s.setData(DataComponentTypes.USE_COOLDOWN, UseCooldown.useCooldown(w.nachladen / 20.0F)
                .cooldownGroup(Key.key("stinkendreich", w.name)).build());
        return s;
    }

    @SuppressWarnings("UnstableApiUsage")
    private void grunddaten(ItemStack s, String name, String anzeige, List<Component> beschreibung) {
        s.setData(DataComponentTypes.ITEM_MODEL, Key.key("stinkendreich", name));
        s.setData(DataComponentTypes.ITEM_NAME, Component.translatable("item.stinkendreich." + name, anzeige));
        s.setData(DataComponentTypes.RARITY, ItemRarity.EPIC);
        s.setData(DataComponentTypes.LORE, ItemLore.lore(beschreibung));
        s.editPersistentDataContainer(pdc -> pdc.set(schluessel, PersistentDataType.STRING, name));
    }

    /** Zeile der Beschreibung (Text aus dem Ressourcenpaket, sonst Deutsch). */
    private static Component zeile(String schluessel, String text, NamedTextColor farbe) {
        return Component.translatable(schluessel, text).color(farbe).decoration(TextDecoration.ITALIC, false);
    }

    private AttributeModifier modifikator(String name, double wert) {
        return new AttributeModifier(new NamespacedKey(plugin, name), wert, AttributeModifier.Operation.ADD_NUMBER);
    }

    /** Name des Agenten-Teils in diesem Stapel oder {@code null}. */
    private String art(ItemStack s) {
        return s == null || s.isEmpty() ? null : s.getPersistentDataContainer().get(schluessel, PersistentDataType.STRING);
    }

    /** Trägt die Figur dieses Teil des Anzugs? */
    public boolean traegt(LivingEntity e, Teil t) {
        return e.getEquipment() != null && t.name.equals(art(e.getEquipment().getItem(t.platz)));
    }

    private Waffe waffeIn(ItemStack s) {
        String a = art(s);
        if (a != null) {
            for (Waffe w : Waffe.values()) {
                if (w.name.equals(a)) {
                    return w;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Kräfte des Anzugs (zweimal pro Sekunde)
    // ------------------------------------------------------------------

    private void kraefte() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            boolean set = true;
            for (Teil t : Teil.values()) {
                set &= traegt(p, t);
            }
            if (traegt(p, Teil.KOPF)) {
                wirkung(p, PotionEffectType.NIGHT_VISION, 0, NACHTSICHT);
                agentenblick(p);
            } else {
                PotionEffect w = p.getPotionEffect(PotionEffectType.NIGHT_VISION);
                if (w != null && w.isAmbient() && !w.hasParticles()) {
                    p.removePotionEffect(PotionEffectType.NIGHT_VISION);     // nur die des Anzugs, keine aus Tränken
                }
            }
            if (traegt(p, Teil.BRUST)) {
                wirkung(p, PotionEffectType.RESISTANCE, 1, DAUER);
                wirkung(p, PotionEffectType.FIRE_RESISTANCE, 0, DAUER);
            }
            if (traegt(p, Teil.BEINE)) {
                wirkung(p, PotionEffectType.SPEED, 1, DAUER);
            }
            if (traegt(p, Teil.FUESSE)) {
                wirkung(p, PotionEffectType.JUMP_BOOST, 1, DAUER);
            }
            if (set) {
                wirkung(p, PotionEffectType.STRENGTH, 1, DAUER);
                // Regeneration braucht über 50 Ticks Restzeit, sonst heilt sie auf Stufe I nie
                wirkung(p, PotionEffectType.REGENERATION, 0, 80);
            }
        }
    }

    /** Wirkung ohne Partikel (als „Umgebungswirkung“ markiert, damit sie sich von Tränken unterscheidet). */
    private static void wirkung(Player p, PotionEffectType art, int stufe, int dauer) {
        p.addPotionEffect(new PotionEffect(art, dauer, stufe, true, false, true));
    }

    /** Gegner in der Nähe leuchten kurz auf (durch Wände sichtbar). */
    private static void agentenblick(Player p) {
        for (Entity e : p.getNearbyEntities(AGENTENBLICK, AGENTENBLICK, AGENTENBLICK)) {
            if (e instanceof Enemy gegner && !gegner.isDead()) {
                gegner.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, DAUER, 0, true, false, false));
            }
        }
    }

    /** Lackschuhe: kein Fallschaden. */
    @EventHandler(ignoreCancelled = true)
    public void beimSchaden(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && e.getEntity() instanceof LivingEntity l && traegt(l, Teil.FUESSE)) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // Waffen
    // ------------------------------------------------------------------

    @EventHandler
    public void beimKlick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Waffe w = waffeIn(e.getItem());
        Player p = e.getPlayer();
        // Wie in der Mod: Türen, Truhen, Knöpfe … gehen vor (außer beim Schleichen)
        if (w == null || e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && e.getClickedBlock().getType().isInteractable() && !p.isSneaking()) {
            return;
        }
        Key gruppe = Key.key("stinkendreich", w.name);
        // Ein Rechtsklick kann zwei Ereignisse auslösen (Block und Luft) – die Nachladezeit lässt nur eins durch
        if (p.getCooldown(gruppe) > 0) {
            return;
        }
        p.setCooldown(gruppe, w.nachladen);
        feuern(p, w);
    }

    private void feuern(Player p, Waffe w) {
        World welt = p.getWorld();
        Location auge = p.getEyeLocation();
        Vector blick = auge.getDirection();
        // Mündung: etwas vor und unter dem Auge, auf der Seite der Hand
        Vector rechts = new Vector(-blick.getZ(), 0, blick.getX()).normalize().multiply(0.3);
        Location muendung = auge.clone().add(blick.clone().multiply(0.6)).add(rechts).add(0, -0.25, 0);
        switch (w) {
            case PISTOLE -> {
                ton(p, "schuss", 1.1F);
                Treffer t = strahl(p, auge, blick, w.reichweite);
                linie(muendung, t.ende, Particle.WAX_ON, null, 0.5);              // goldene Leuchtspur
                welt.spawnParticle(Particle.ELECTRIC_SPARK, t.ende, 8, 0.1, 0.1, 0.1, 0.2);
                if (t.ziel != null) {
                    treffen(t.ziel, p, w.schaden);
                    stossen(t.ziel, blick, 0.3);
                }
            }
            case LASER -> {
                ton(p, "laser", 1.0F);
                Treffer t = strahl(p, auge, blick, w.reichweite);
                linie(muendung, t.ende, Particle.DUST, new Particle.DustOptions(Color.RED, 1.0F), 0.25);
                welt.spawnParticle(Particle.LAVA, t.ende, 3, 0.1, 0.1, 0.1, 0.0);
                welt.spawnParticle(Particle.SMOKE, t.ende, 6, 0.1, 0.1, 0.1, 0.02);
                if (t.ziel != null) {
                    treffen(t.ziel, p, w.schaden);
                    t.ziel.setFireTicks(Math.max(t.ziel.getFireTicks(), 100));
                }
            }
            case GELDKANONE -> {
                ton(p, "geldregen", 1.0F);
                geldregen(p, muendung, blick, w);
            }
        }
    }

    /** Geldscheine (grün) und Goldmünzen fliegen in einem Kegel; Gegner darin werden weggeschleudert. */
    private void geldregen(Player p, Location muendung, Vector blick, Waffe w) {
        ThreadLocalRandom zufall = ThreadLocalRandom.current();
        World welt = p.getWorld();
        ItemStack schein = ItemStack.of(Material.EMERALD), muenze = ItemStack.of(Material.GOLD_NUGGET);
        for (int i = 0; i < 40; i++) {
            Vector r = blick.clone().add(new Vector((zufall.nextDouble() - 0.5) * 0.5, (zufall.nextDouble() - 0.5) * 0.4 + 0.1,
                    (zufall.nextDouble() - 0.5) * 0.5)).normalize();
            // Anzahl 0: die drei Werte sind dann die Flugrichtung
            welt.spawnParticle(Particle.ITEM, muendung, 0, r.getX(), r.getY(), r.getZ(), 0.5 + zufall.nextDouble() * 0.6,
                    i % 2 == 0 ? schein : muenze);
        }
        for (int i = 0; i < 70; i++) {
            Vector r = blick.clone().add(new Vector((zufall.nextDouble() - 0.5) * 0.7, (zufall.nextDouble() - 0.5) * 0.7,
                    (zufall.nextDouble() - 0.5) * 0.7)).normalize();
            Location ort = muendung.clone().add(r.multiply(0.5 + zufall.nextDouble() * w.reichweite));
            welt.spawnParticle(i % 3 == 0 ? Particle.WAX_ON : Particle.HAPPY_VILLAGER, ort, 1, 0.05, 0.05, 0.05, 0.0);
        }
        Location auge = p.getEyeLocation();
        double grenze = Math.cos(Math.toRadians(25));
        for (Entity e : p.getNearbyEntities(w.reichweite, w.reichweite, w.reichweite)) {
            if (!(e instanceof LivingEntity l) || !zielbar(l, p)) {
                continue;
            }
            Vector zu = l.getBoundingBox().getCenter().subtract(auge.toVector());
            double abstand = zu.length();
            if (abstand > w.reichweite || zu.normalize().dot(blick) < grenze) {
                continue;
            }
            treffen(l, p, w.schaden);
            stossen(l, blick, 1.6 * (1.0 - abstand / w.reichweite) + 0.4);
            l.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 2));
            l.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
        }
    }

    private record Treffer(Location ende, LivingEntity ziel) {
    }

    /** Sofort-Treffer: erster Block oder erste Figur auf der Linie vom Auge in Blickrichtung. */
    private Treffer strahl(Player p, Location auge, Vector blick, double reichweite) {
        RayTraceResult r = p.getWorld().rayTrace(auge, blick, reichweite, FluidCollisionMode.NEVER, true, 0.3,
                e -> e instanceof LivingEntity l && zielbar(l, p));
        if (r == null) {
            return new Treffer(auge.clone().add(blick.clone().multiply(reichweite)), null);
        }
        Location ende = r.getHitPosition().toLocation(p.getWorld());
        return new Treffer(ende, r.getHitEntity() instanceof LivingEntity l ? l : null);
    }

    /** Lebewesen, die getroffen werden dürfen (nicht der Schütze, Butler, Bodyguards, Fahrzeuge, Ständer, Puppen). */
    private boolean zielbar(LivingEntity e, Player schuetze) {
        if (e == schuetze || e.isDead() || e instanceof ArmorStand || e instanceof Mannequin
                || e instanceof Player sp && sp.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return false;
        }
        for (NamespacedKey k : geschuetzt) {
            if (e.getPersistentDataContainer().has(k)) {
                return false;
            }
        }
        return true;
    }

    /** Schaden ohne Schutzzeit, damit jeder Schuss zählt (die Pistole lädt schneller nach als die Schutzzeit). */
    private static void treffen(LivingEntity ziel, Player schuetze, double schaden) {
        ziel.setNoDamageTicks(0);
        ziel.damage(schaden, schuetze);
    }

    private static void stossen(LivingEntity e, Vector richtung, double staerke) {
        e.setVelocity(e.getVelocity().add(new Vector(richtung.getX() * staerke, 0.25 * staerke, richtung.getZ() * staerke)));
    }

    private static void linie(Location von, Location bis, Particle teilchen, Object daten, double schritt) {
        Vector weg = bis.toVector().subtract(von.toVector());
        int n = Math.clamp((int) (weg.length() / schritt), 1, 300);
        for (int i = 0; i <= n; i++) {
            Location ort = von.clone().add(weg.clone().multiply((double) i / n));
            von.getWorld().spawnParticle(teilchen, ort, 1, 0, 0, 0, 0, daten);
        }
    }

    private static void ton(Player p, String name, float hoehe) {
        p.getWorld().playSound(p.getLocation(), "stinkendreich:" + name, SoundCategory.PLAYERS, 1.0F,
                hoehe * (0.95F + ThreadLocalRandom.current().nextFloat() * 0.1F));
    }

    /** Für Befehle: alle Teile und Waffen auf einmal (Testhilfe {@code /villa agent}). */
    public List<ItemStack> alles() {
        List<ItemStack> l = new ArrayList<>();
        for (Teil t : Teil.values()) {
            l.add(teil(t));
        }
        for (Waffe w : Waffe.values()) {
            l.add(waffe(w));
        }
        return l;
    }

    /** Testhilfe {@code /villa agent an}: Anzug anziehen, Pistole, Laseruhr und Geldkanone auf die Plätze 1–3. */
    public void anziehen(Player p) {
        p.getInventory().setHelmet(teil(Teil.KOPF));
        p.getInventory().setChestplate(teil(Teil.BRUST));
        p.getInventory().setLeggings(teil(Teil.BEINE));
        p.getInventory().setBoots(teil(Teil.FUESSE));
        p.getInventory().setItem(0, waffe(Waffe.PISTOLE));
        p.getInventory().setItem(1, waffe(Waffe.LASER));
        p.getInventory().setItem(2, waffe(Waffe.GELDKANONE));
        p.getInventory().setHeldItemSlot(0);
    }
}
