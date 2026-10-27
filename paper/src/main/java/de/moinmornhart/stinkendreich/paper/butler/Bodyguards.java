package de.moinmornhart.stinkendreich.paper.butler;

import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import de.moinmornhart.stinkendreich.paper.villa.FigurenUebersetzen;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bodyguards wie in der Mod (BodyguardEntity), für Spieler ohne Mod gezeichnet wie in der Server-Version: großer
 * Türsteher im Smoking (Schaufensterpuppe mit Haut, 1,45-fach) mit goldener Pistole. Darunter steckt ein unsichtbarer
 * Eisengolem: er läuft, kämpft im Nahkampf und ist unverwundbar. Aus der Entfernung (4,5 bis 24 Blöcke, Creeper immer)
 * schießt er mit der Pistole. Zwei je Spieler, gerufen und heimgeschickt über das Handy.
 */
public final class Bodyguards implements Listener {
    public static final int ANZAHL = 2;
    private static final double SCHUSS_AB = 4.5, SCHUSS_BIS = 24.0;
    private static final int NACHLADEN = 18;

    private final StinkendReichPaper plugin;
    private final NamespacedKey schluesselChef, schluesselPaar;
    /** Golem (Körper) → Puppe. */
    private final Map<IronGolem, Mannequin> paare = new HashMap<>();
    private final Map<IronGolem, Integer> nachladen = new HashMap<>();
    /** Wer den Chef zuletzt angegriffen hat bzw. wen der Chef angegriffen hat. */
    private final Map<UUID, LivingEntity> feinde = new HashMap<>();

    public Bodyguards(StinkendReichPaper plugin) {
        this.plugin = plugin;
        this.schluesselChef = new NamespacedKey(plugin, "bodyguard");
        this.schluesselPaar = new NamespacedKey(plugin, "bodyguard_paar");
        Bukkit.getScheduler().runTaskTimer(plugin, this::takt, 1L, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> Bukkit.getWorlds().forEach(w -> w.getEntities().forEach(this::aufnehmen)), 1L);
    }

    private List<IronGolem> vom(Player p) {
        List<IronGolem> liste = new ArrayList<>();
        for (IronGolem g : paare.keySet()) {
            if (g.isValid() && p.getUniqueId().toString().equals(g.getPersistentDataContainer().get(schluesselChef, PersistentDataType.STRING))) {
                liste.add(g);
            }
        }
        return liste;
    }

    /** Handy: Bodyguards rufen (es kommen so viele, dass es zwei sind). */
    public void rufen(Player p) {
        List<IronGolem> da = vom(p);
        for (int i = da.size(); i < ANZAHL; i++) {
            Location ort = p.getLocation().add((i == 0 ? -1.5 : 1.5), 0, -1.5);
            ort.setYaw(p.getLocation().getYaw());
            IronGolem g = ort.getWorld().spawn(ort, IronGolem.class, k -> {
                k.setInvisible(true);
                k.setInvulnerable(true);
                k.setPersistent(true);
                k.setRemoveWhenFarAway(false);
                k.setPlayerCreated(true);
                k.setSilent(true);
                k.getPersistentDataContainer().set(schluesselChef, PersistentDataType.STRING, p.getUniqueId().toString());
            });
            Mannequin m = FigurenUebersetzen.puppe(ort, "bodyguard_mensch");
            m.setInvulnerable(true);
            m.customName(Component.text("Bodyguard", NamedTextColor.GOLD));
            m.setCustomNameVisible(false);
            if (m.getAttribute(Attribute.SCALE) != null) {
                m.getAttribute(Attribute.SCALE).setBaseValue(1.45);
            }
            ItemStack pistole = ItemStack.of(Material.PAPER);
            pistole.setData(DataComponentTypes.ITEM_MODEL, Key.key("stinkendreich", "goldene_pistole"));
            m.getEquipment().setItem(EquipmentSlot.HAND, pistole);
            m.getPersistentDataContainer().set(schluesselChef, PersistentDataType.STRING, p.getUniqueId().toString());
            m.getPersistentDataContainer().set(schluesselPaar, PersistentDataType.STRING, g.getUniqueId().toString());
            paare.put(g, m);
            ort.getWorld().spawnParticle(Particle.CLOUD, ort.clone().add(0, 1, 0), 20, 0.4, 0.8, 0.4, 0.02);
        }
        p.sendActionBar(Component.text("Ihre Bodyguards sind da, Chef.", NamedTextColor.GOLD));
        plugin.getLogger().info("Bodyguards: " + vom(p).size() + " bei " + p.getName());
    }

    /** Handy: Bodyguards heimschicken. */
    public void entlassen(Player p) {
        for (IronGolem g : vom(p)) {
            Mannequin m = paare.remove(g);
            g.getWorld().spawnParticle(Particle.CLOUD, g.getLocation().add(0, 1, 0), 20, 0.4, 0.8, 0.4, 0.02);
            g.remove();
            if (m != null) {
                m.remove();
            }
        }
        p.sendActionBar(Component.text("Die Bodyguards gehen nach Hause.", NamedTextColor.GOLD));
    }

    // ------------------------------------------------------------------

    private void aufnehmen(Entity e) {
        String paar = e.getPersistentDataContainer().get(schluesselPaar, PersistentDataType.STRING);
        if (e instanceof Mannequin m && paar != null && Bukkit.getEntity(UUID.fromString(paar)) instanceof IronGolem g) {
            paare.put(g, m);
        } else if (e instanceof IronGolem g && e.getPersistentDataContainer().has(schluesselChef)) {
            for (Entity n : g.getNearbyEntities(3, 3, 3)) {
                if (n instanceof Mannequin m && g.getUniqueId().toString().equals(m.getPersistentDataContainer().get(schluesselPaar, PersistentDataType.STRING))) {
                    paare.put(g, m);
                }
            }
        }
    }

    @EventHandler
    public void beimLaden(EntitiesLoadEvent e) {
        e.getEntities().forEach(this::aufnehmen);
    }

    private void takt() {
        paare.entrySet().removeIf(e -> !e.getKey().isValid() || !e.getValue().isValid());
        long tick = Bukkit.getCurrentTick();
        for (Map.Entry<IronGolem, Mannequin> e : paare.entrySet()) {
            IronGolem g = e.getKey();
            Mannequin m = e.getValue();
            Location l = g.getLocation();
            if (m.getLocation().distanceSquared(l) > 0.0001 || Math.abs(m.getYaw() - l.getYaw()) > 1) {
                m.teleport(l);
                m.setBodyYaw(g.getBodyYaw());
            }
            String chefId = g.getPersistentDataContainer().get(schluesselChef, PersistentDataType.STRING);
            Player chef = chefId == null ? null : Bukkit.getPlayer(UUID.fromString(chefId));
            if (chef == null || !chef.getWorld().equals(g.getWorld())) {
                continue;
            }
            if (tick % 10 == 0) {
                ziel(g, chef);
                folgen(g, chef);
            }
            int rest = nachladen.getOrDefault(g, 0);
            if (rest > 0) {
                nachladen.put(g, rest - 1);
                continue;
            }
            LivingEntity ziel = g.getTarget() != null ? g.getTarget() : naechsterCreeper(chef);
            if (ziel != null && ziel.isValid() && !ziel.isDead()) {
                double d = ziel.getLocation().distance(l);
                if ((d > SCHUSS_AB || ziel instanceof Creeper) && d < SCHUSS_BIS && freieSicht(g, ziel)) {
                    schiessen(g, m, ziel);
                }
            }
        }
    }

    /** Wer den Chef angreift oder vom Chef angegriffen wird, sonst das nächste Monster (Creeper schießt er nur). */
    private void ziel(IronGolem g, Player chef) {
        LivingEntity jetzt = g.getTarget();
        if (jetzt != null && jetzt.isValid() && !jetzt.isDead() && !(jetzt instanceof Creeper)) {
            return;
        }
        LivingEntity feind = feinde.get(chef.getUniqueId());
        if (feind != null && feind.isValid() && !feind.isDead() && feind.getLocation().distanceSquared(chef.getLocation()) < 32 * 32
                && !(feind instanceof Creeper)) {
            g.setTarget(feind);
            return;
        }
        LivingEntity best = null;
        double d = 16 * 16;
        for (Entity e : chef.getNearbyEntities(16, 8, 16)) {
            if (e instanceof Enemy && e instanceof LivingEntity le && !(e instanceof Creeper) && !le.isDead()
                    && e.getLocation().distanceSquared(chef.getLocation()) < d) {
                d = e.getLocation().distanceSquared(chef.getLocation());
                best = le;
            }
        }
        g.setTarget(best);
    }

    private static LivingEntity naechsterCreeper(Player chef) {
        LivingEntity best = null;
        double d = 16 * 16;
        for (Entity e : chef.getNearbyEntities(16, 8, 16)) {
            if (e instanceof Creeper c && !c.isDead() && e.getLocation().distanceSquared(chef.getLocation()) < d) {
                d = e.getLocation().distanceSquared(chef.getLocation());
                best = c;
            }
        }
        return best;
    }

    /** Bleibt beim Chef: über 6 Blöcke läuft er hinterher, über 28 Blöcke steht er gleich neben ihm. */
    private static void folgen(IronGolem g, Player chef) {
        double d = g.getLocation().distance(chef.getLocation());
        if (d > 28) {
            Location neben = chef.getLocation().add((Math.random() - 0.5) * 4, 0, (Math.random() - 0.5) * 4);
            g.teleport(neben);
            g.getPathfinder().stopPathfinding();
        } else if (g.getTarget() == null && d > 6) {
            g.getPathfinder().moveTo(chef.getLocation(), 1.1);
        }
    }

    private static boolean freieSicht(LivingEntity von, LivingEntity bis) {
        return von.hasLineOfSight(bis);
    }

    /** Ein Schuss mit der goldenen Pistole: Mündungsfeuer, Leuchtspur, Treffer. */
    private void schiessen(IronGolem g, Mannequin m, LivingEntity ziel) {
        nachladen.put(g, NACHLADEN);
        Location blick = g.getLocation();
        Vector richtung = ziel.getLocation().toVector().subtract(blick.toVector());
        blick.setDirection(richtung);
        g.setRotation(blick.getYaw(), 0);
        m.swingMainHand();
        Location von = g.getLocation().add(0, 2.0, 0).add(blick.getDirection().setY(0).normalize().multiply(0.8));
        Location nach = ziel.getLocation().add(0, ziel.getHeight() * 0.6, 0);
        Vector schritt = nach.toVector().subtract(von.toVector()).normalize().multiply(0.6);
        Location p = von.clone();
        for (int i = 0; i < 48 && p.distanceSquared(nach) > 0.5; i++) {
            p.getWorld().spawnParticle(Particle.CRIT, p, 1, 0, 0, 0, 0);
            p.add(schritt);
        }
        von.getWorld().spawnParticle(Particle.FLAME, von, 4, 0.05, 0.05, 0.05, 0.01);
        von.getWorld().playSound(von, Sound.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.HOSTILE, 1.2F, 1.6F);
        ziel.damage(7.0, g);
    }

    // ------------------------------------------------------------------
    // Unverwundbar, merken, wer den Chef angreift
    // ------------------------------------------------------------------

    @EventHandler
    public void beimSchaden(EntityDamageEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(schluesselChef) && e.getCause() != EntityDamageEvent.DamageCause.KILL) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void beimAngriff(EntityDamageByEntityEvent e) {
        Entity taeter = e.getDamager();
        if (taeter instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Entity schuetze) {
            taeter = schuetze;
        }
        if (e.getEntity() instanceof Player chef && taeter instanceof LivingEntity le && !taeter.getPersistentDataContainer().has(schluesselChef)) {
            feinde.put(chef.getUniqueId(), le);
        } else if (taeter instanceof Player chef && e.getEntity() instanceof LivingEntity le && e.getEntity() instanceof Enemy) {
            feinde.put(chef.getUniqueId(), le);
        }
    }

    /** Bodyguards greifen nie ihren eigenen Chef an. */
    @EventHandler(ignoreCancelled = true)
    public void beimZielen(EntityTargetEvent e) {
        if (e.getTarget() instanceof Player p
                && p.getUniqueId().toString().equals(e.getEntity().getPersistentDataContainer().get(schluesselChef, PersistentDataType.STRING))) {
            e.setCancelled(true);
        }
    }

    public String uebersicht() {
        return paare.size() + " Bodyguards";
    }
}
