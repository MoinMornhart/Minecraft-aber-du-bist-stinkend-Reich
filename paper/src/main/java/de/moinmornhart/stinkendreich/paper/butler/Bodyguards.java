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
 * Eisengolem: er läuft und ist unverwundbar; Minecrafts eigene Golem-KI (Zielwahl, Nahkampf, Dorf) ist abgeschaltet,
 * das Plugin steuert ihn selbst. Wie ein echter Personenschützer bleibt er beim Chef und schießt aus der Entfernung
 * (4,5 bis 24 Blöcke, Creeper immer) mit der Pistole; erst wenn ein Gegner näher als 5,5 Blöcke kommt, geht er hin und
 * schlägt zu. Zwei je Spieler, gerufen und heimgeschickt über das Handy.
 * <p>Beim Zielen dreht sich die Puppe zum Gegner und hält die Pistole mit beiden Händen im Anschlag: Sie bekommt dafür
 * eine geladene Armbrust mit dem Bild der Pistole (Minecraft zeichnet dann die Zielhaltung). Bei mehreren Gegnern nimmt
 * jeder Bodyguard einen anderen; wer in einer Wand steckt oder nicht hinterherkommt, steht gleich wieder beim Chef.
 */
public final class Bodyguards implements Listener {
    public static final int ANZAHL = 2;
    private static final double SCHUSS_AB = 4.5, SCHUSS_BIS = 24.0;
    /** Näher als das kommt ein Gegner, dann geht er zum Nahkampf hin (und bleibt dran, bis der Gegner weiter weg ist). */
    private static final double NAHKAMPF_AB = 5.5, NAHKAMPF_BIS = 8.0, REICHWEITE = 2.6;
    private static final int NACHLADEN = 18, SCHLAG_PAUSE = 20;
    /** So lange (Ticks) gilt, wer den Chef zuletzt angegriffen hat bzw. wen er angegriffen hat. */
    private static final int FEIND_MERKEN = 200;

    private final StinkendReichPaper plugin;
    private final NamespacedKey schluesselChef, schluesselPaar;
    /** Golem (Körper) → Puppe. */
    private final Map<IronGolem, Mannequin> paare = new HashMap<>();
    private final Map<IronGolem, Integer> nachladen = new HashMap<>();
    /** Golems im Nahkampf und ihre Pause bis zum nächsten Schlag. */
    private final java.util.Set<IronGolem> nahkampf = new java.util.HashSet<>();
    private final Map<IronGolem, Integer> schlagPause = new HashMap<>();
    /** Für das Erkennen, ob ein Golem feststeckt: Stelle bei der letzten Prüfung und wie oft er nicht vorankam. */
    private final Map<IronGolem, Location> letzteStelle = new HashMap<>();
    private final Map<IronGolem, Integer> festgesteckt = new HashMap<>();
    /** Pistole in der Hand: locker (normal) und im Anschlag (geladene Armbrust mit dem Bild der Pistole). */
    private final ItemStack pistoleLocker, pistoleImAnschlag;
    /** Wer den Chef zuletzt angegriffen hat bzw. wen der Chef angegriffen hat. */
    private final Map<UUID, LivingEntity> feinde = new HashMap<>();
    private final Map<UUID, Long> feindSeit = new HashMap<>();

    public Bodyguards(StinkendReichPaper plugin) {
        this.plugin = plugin;
        this.schluesselChef = new NamespacedKey(plugin, "bodyguard");
        this.schluesselPaar = new NamespacedKey(plugin, "bodyguard_paar");
        this.pistoleLocker = ItemStack.of(Material.PAPER);
        pistoleLocker.setData(DataComponentTypes.ITEM_MODEL, Key.key("stinkendreich", "goldene_pistole"));
        this.pistoleImAnschlag = ItemStack.of(Material.CROSSBOW);
        pistoleImAnschlag.setData(DataComponentTypes.ITEM_MODEL, Key.key("stinkendreich", "goldene_pistole"));
        pistoleImAnschlag.setData(DataComponentTypes.CHARGED_PROJECTILES,
                io.papermc.paper.datacomponent.item.ChargedProjectiles.chargedProjectiles(List.of(ItemStack.of(Material.ARROW))));
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
        for (int i = 0; i < da.size(); i++) {
            da.get(i).teleport(freieStelle(p, p.getYaw() + (i == 0 ? 135 : -135)));
        }
        for (int i = da.size(); i < ANZAHL; i++) {
            Location ort = freieStelle(p, p.getYaw() + (i == 0 ? 135 : -135));
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
            kiAnpassen(g);
            Mannequin m = FigurenUebersetzen.puppe(ort, "bodyguard_mensch");
            m.setInvulnerable(true);
            m.customName(Component.text("Bodyguard", NamedTextColor.GOLD));
            m.setCustomNameVisible(false);
            if (m.getAttribute(Attribute.SCALE) != null) {
                m.getAttribute(Attribute.SCALE).setBaseValue(1.45);
            }
            m.getEquipment().setItem(EquipmentSlot.HAND, pistoleLocker.clone());
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
            kiAnpassen(g);   // Ziele der KI werden nicht gespeichert, nach dem Laden sind die eigenen wieder da
            for (Entity n : g.getNearbyEntities(3, 3, 3)) {
                if (n instanceof Mannequin m && g.getUniqueId().toString().equals(m.getPersistentDataContainer().get(schluesselPaar, PersistentDataType.STRING))) {
                    paare.put(g, m);
                }
            }
        }
    }

    /**
     * Schaltet die Golem-KI von Minecraft ab, die dem Plugin dazwischenfunkt: Sie nimmt sich selbst das nächste Monster
     * (dann hatten beide Bodyguards denselben Gegner), rennt sofort hin (dann sah man ihn nie zielen) und kümmert sich
     * ums Dorf. Umherschauen bleibt.
     */
    private static void kiAnpassen(IronGolem g) {
        var ki = Bukkit.getMobGoals();
        ki.removeGoal(g, com.destroystokyo.paper.entity.ai.VanillaGoal.NEAREST_ATTACKABLE);
        ki.removeGoal(g, com.destroystokyo.paper.entity.ai.VanillaGoal.IRON_GOLEM_DEFEND_VILLAGE);
        ki.removeGoal(g, com.destroystokyo.paper.entity.ai.VanillaGoal.HURT_BY);
        ki.removeGoal(g, com.destroystokyo.paper.entity.ai.VanillaGoal.MELEE_ATTACK);
        ki.removeGoal(g, com.destroystokyo.paper.entity.ai.VanillaGoal.MOVE_TOWARDS);
        ki.removeGoal(g, com.destroystokyo.paper.entity.ai.VanillaGoal.MOVE_BACK_TO_VILLAGE);
        ki.removeGoal(g, com.destroystokyo.paper.entity.ai.VanillaGoal.GOLEM_RANDOM_STROLL_IN_VILLAGE);
        ki.removeGoal(g, com.destroystokyo.paper.entity.ai.VanillaGoal.IRON_GOLEM_OFFER_FLOWER);
    }

    @EventHandler
    public void beimLaden(EntitiesLoadEvent e) {
        e.getEntities().forEach(this::aufnehmen);
    }

    private void takt() {
        paare.entrySet().removeIf(e -> !e.getKey().isValid() || !e.getValue().isValid());
        letzteStelle.keySet().removeIf(g -> !paare.containsKey(g));
        festgesteckt.keySet().removeIf(g -> !paare.containsKey(g));
        nahkampf.removeIf(g -> !paare.containsKey(g));
        schlagPause.keySet().removeIf(g -> !paare.containsKey(g));
        long tick = Bukkit.getCurrentTick();
        for (Map.Entry<IronGolem, Mannequin> e : paare.entrySet()) {
            IronGolem g = e.getKey();
            Mannequin m = e.getValue();
            String chefId = g.getPersistentDataContainer().get(schluesselChef, PersistentDataType.STRING);
            Player chef = chefId == null ? null : Bukkit.getPlayer(UUID.fromString(chefId));
            boolean daheim = chef != null && chef.getWorld().equals(g.getWorld());
            if (daheim && tick % 10 == 0) {
                ziel(g, chef, tick);
            }
            if (daheim) {
                nahkampf(g, m, tick);
            }
            if (daheim && tick % 10 == 0) {
                folgen(g, chef);
            }
            if (daheim && tick % 40 == 0) {
                feststeckenPruefen(g, chef);
            }
            // Gegner in Schussweite und Sicht? Dann zielt er (Puppe dreht sich hin, Pistole im Anschlag)
            LivingEntity ziel = daheim ? (g.getTarget() != null ? g.getTarget() : naechsterCreeper(chef)) : null;
            boolean zielen = false;
            if (ziel != null && ziel.isValid() && !ziel.isDead()) {
                double d = ziel.getLocation().distance(g.getLocation());
                zielen = (d > SCHUSS_AB || ziel instanceof Creeper) && d < SCHUSS_BIS && freieSicht(g, ziel);
            }
            puppeNachfuehren(g, m, zielen ? ziel : null);
            if (tick % 20 == 0 && de.moinmornhart.stinkendreich.paper.Autotest.aktiv()) {
                // Testprotokoll: was jeder Bodyguard gerade tut
                Location o = g.getLocation();
                plugin.getLogger().info(String.format("Autotest: Bodyguard %d bei %d %d %d Ziel %s Abstand %s Chef %s" + (nahkampf.contains(g) ? " Nahkampf" : ""),
                        g.getEntityId(), o.getBlockX(), o.getBlockY(), o.getBlockZ(), ziel == null ? "-" : ziel.getEntityId(),
                        ziel == null ? "-" : String.format("%.1f", ziel.getLocation().distance(o)),
                        daheim ? String.format("%.1f", chef.getLocation().distance(o)) : "-"));
            }
            int rest = nachladen.getOrDefault(g, 0);
            if (rest > 0) {
                nachladen.put(g, rest - 1);
            } else if (zielen) {
                schiessen(g, m, ziel);
            }
            if (!daheim && g.getTarget() != null) {
                g.setTarget(null);
            }
        }
    }

    /**
     * Die Puppe steht dort, wo der unsichtbare Golem läuft. Zielt er, schaut sie den Gegner an (Kopf und Körper, mit
     * Neigung) und hält die Pistole im Anschlag, sonst schaut sie wie der Golem und hält die Pistole locker.
     */
    private void puppeNachfuehren(IronGolem g, Mannequin m, LivingEntity ziel) {
        Location soll = g.getLocation();
        if (ziel != null) {
            Location auge = soll.clone().add(0, m.getEyeHeight(), 0);
            soll.setDirection(ziel.getLocation().add(0, ziel.getHeight() * 0.6, 0).toVector().subtract(auge.toVector()));
        } else {
            soll.setPitch(0);
        }
        Location ist = m.getLocation();
        if (ist.distanceSquared(soll) > 0.0001 || Math.abs(ist.getYaw() - soll.getYaw()) > 1 || Math.abs(ist.getPitch() - soll.getPitch()) > 1) {
            m.teleport(soll);
            m.setBodyYaw(ziel != null ? soll.getYaw() : g.getBodyYaw());
        }
        ItemStack hand = m.getEquipment().getItem(EquipmentSlot.HAND);
        boolean imAnschlag = hand.getType() == Material.CROSSBOW;
        if (imAnschlag != (ziel != null)) {
            if (de.moinmornhart.stinkendreich.paper.Autotest.aktiv()) {
                plugin.getLogger().info("Autotest: Bodyguard " + g.getEntityId() + (ziel != null
                        ? " zielt auf " + ziel.getType().getKey().getKey() + " " + ziel.getEntityId() : " zielt nicht mehr"));
            }
            m.getEquipment().setItem(EquipmentSlot.HAND, (ziel != null ? pistoleImAnschlag : pistoleLocker).clone());
        }
    }

    /**
     * Steckt der Golem in einer Wand oder kommt seit vier Sekunden nicht voran, obwohl der Chef weiter weg ist (z. B.
     * hinter einer Tür, die ein Golem nicht öffnen kann), steht er gleich wieder an einer freien Stelle beim Chef.
     */
    private void feststeckenPruefen(IronGolem g, Player chef) {
        Location jetzt = g.getLocation();
        Location vorher = letzteStelle.put(g, jetzt);
        boolean steht = vorher != null && vorher.getWorld().equals(jetzt.getWorld()) && vorher.distanceSquared(jetzt) < 0.25;
        boolean mussLaufen = jetzt.distanceSquared(chef.getLocation()) > 5 * 5 && !zieltGerade(g) && !nahkampf.contains(g);
        int mal = steht && mussLaufen ? festgesteckt.getOrDefault(g, 0) + 1 : 0;
        boolean inDerWand = jetzt.getBlock().isSolid() || jetzt.clone().add(0, 2, 0).getBlock().isSolid();
        if (inDerWand || mal >= 2) {
            g.teleport(freieStelle(chef, chef.getYaw() + 180));
            g.getPathfinder().stopPathfinding();
            mal = 0;
        }
        festgesteckt.put(g, mal);
    }

    /**
     * Freie Stelle für einen Golem neben dem Chef (1,4 breit, 2,7 hoch, fester Boden darunter): erst schräg hinter ihm
     * im gewünschten Winkel, dann ringsum und etwas weiter weg. Notfalls die Stelle des Chefs selbst.
     */
    private static Location freieStelle(Player chef, float winkel) {
        Location c = chef.getLocation();
        for (double r : new double[]{2.5, 3.5, 1.8, 4.5}) {
            for (int i = 0; i < 8; i++) {
                double w = Math.toRadians(winkel + i * 45);
                int x = (int) Math.floor(c.getX() - Math.sin(w) * r), z = (int) Math.floor(c.getZ() + Math.cos(w) * r);
                for (int dy : new int[]{0, 1, -1, 2, -2}) {
                    int y = c.getBlockY() + dy;
                    if (platzFuerGolem(c.getWorld(), x, y, z)) {
                        Location l = new Location(c.getWorld(), x + 0.5, y, z + 0.5, c.getYaw(), 0);
                        return l;
                    }
                }
            }
        }
        return c.clone();
    }

    /** 3 × 3 Blöcke Grundfläche, drei Blöcke hoch frei, darunter fester Boden. */
    private static boolean platzFuerGolem(org.bukkit.World w, int x, int y, int z) {
        if (!w.getBlockAt(x, y - 1, z).isSolid()) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy < 3; dy++) {
                    if (!w.getBlockAt(x + dx, y + dy, z + dz).isPassable()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Wer den Chef angreift oder vom Chef angegriffen wird, sonst das nächste Monster beim Chef (Creeper schießt er nur).
     * Sein Ziel behält er, solange es noch da ist; bei mehreren Monstern nimmt jeder Bodyguard eines, das der andere noch
     * nicht im Visier hat. Ist niemand mehr da, lässt er ab.
     */
    private void ziel(IronGolem g, Player chef, long tick) {
        LivingEntity feind = feinde.get(chef.getUniqueId());
        if (feind != null && feind.isValid() && !feind.isDead() && !(feind instanceof Creeper)
                && tick - feindSeit.getOrDefault(chef.getUniqueId(), 0L) < FEIND_MERKEN
                && feind.getLocation().distanceSquared(chef.getLocation()) < 32 * 32) {
            g.setTarget(feind);
            return;
        }
        LivingEntity jetzt = g.getTarget();
        List<LivingEntity> belegt = new ArrayList<>();
        boolean geteilt = false;
        for (IronGolem anderer : vom(chef)) {
            if (anderer != g && anderer.getTarget() != null) {
                belegt.add(anderer.getTarget());
                // Haben zwei dasselbe Ziel, sucht sich nur einer (der mit der größeren Nummer) ein anderes
                geteilt |= anderer.getTarget().equals(jetzt) && g.getEntityId() > anderer.getEntityId();
            }
        }
        LivingEntity best = null, frei = null;
        boolean jetztDa = false;
        double d = 16 * 16, dFrei = 16 * 16;
        for (Entity e : chef.getNearbyEntities(16, 8, 16)) {
            if (e instanceof Enemy && e instanceof LivingEntity le && !(e instanceof Creeper) && !le.isDead()) {
                double abstand = e.getLocation().distanceSquared(chef.getLocation());
                if (abstand >= 16 * 16) {
                    continue;
                }
                jetztDa |= le.equals(jetzt);
                if (abstand < d) {
                    d = abstand;
                    best = le;
                }
                if (abstand < dFrei && !belegt.contains(le)) {
                    dFrei = abstand;
                    frei = le;
                }
            }
        }
        if (jetztDa && !(geteilt && frei != null)) {
            return;
        }
        g.setTarget(frei != null ? frei : best);
    }

    /**
     * Nahkampf wie ein Eisengolem, nur gegen Gegner in der Nähe (nie Creeper): hingehen, zuschlagen (der Golem wirft den
     * Gegner hoch, die Puppe holt aus). Weiter weg bleibt er beim Chef und schießt.
     */
    private void nahkampf(IronGolem g, Mannequin m, long tick) {
        LivingEntity z = g.getTarget();
        boolean dran = false;
        if (z != null && z.isValid() && !z.isDead() && !(z instanceof Creeper) && z.getWorld().equals(g.getWorld())) {
            double d = z.getLocation().distance(g.getLocation());
            dran = d < (nahkampf.contains(g) ? NAHKAMPF_BIS : NAHKAMPF_AB);
            if (dran) {
                if (tick % 10 == 0) {
                    g.getPathfinder().moveTo(z, 1.15);
                }
                g.lookAt(z);
                int pause = schlagPause.getOrDefault(g, 0);
                if (pause > 0) {
                    schlagPause.put(g, pause - 1);
                } else if (d < REICHWEITE + z.getWidth() / 2) {
                    g.attack(z);
                    m.swingMainHand();
                    schlagPause.put(g, SCHLAG_PAUSE);
                }
            }
        }
        if (!dran && nahkampf.remove(g)) {
            g.getPathfinder().stopPathfinding();
        } else if (dran) {
            nahkampf.add(g);
        }
    }

    /** Hält er die Pistole gerade im Anschlag (Puppe mit der geladenen Armbrust)? */
    private boolean zieltGerade(IronGolem g) {
        Mannequin m = paare.get(g);
        return m != null && m.getEquipment().getItem(EquipmentSlot.HAND).getType() == Material.CROSSBOW;
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

    /**
     * Bleibt beim Chef: über 6 Blöcke läuft er hinterher (beim Zielen erst über 12, damit er in Ruhe schießen kann), über
     * 28 Blöcke steht er gleich an einer freien Stelle neben ihm. Im Nahkampf läuft er zum Gegner, nicht zum Chef.
     */
    private void folgen(IronGolem g, Player chef) {
        double d = g.getLocation().distance(chef.getLocation());
        if (d > 28) {
            g.teleport(freieStelle(chef, chef.getLocation().getYaw() + 180));
            g.getPathfinder().stopPathfinding();
            nahkampf.remove(g);
        } else if (!nahkampf.contains(g) && d > (zieltGerade(g) ? 12 : 6)) {
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
        // Kein Ausholen mit der Hand: das bräche die Zielhaltung der Puppe für einen Moment ab
        // Mündung: von der Brust der Puppe (Zielhaltung mit beiden Armen) eine Armlänge mit Pistole Richtung Gegner
        Location brust = g.getLocation().add(0, 2.0, 0);
        Location nach = ziel.getLocation().add(0, ziel.getHeight() * 0.6, 0);
        Location von = brust.clone().add(nach.toVector().subtract(brust.toVector()).normalize().multiply(1.35));
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
            feindSeit.put(chef.getUniqueId(), (long) Bukkit.getCurrentTick());
        } else if (taeter instanceof Player chef && e.getEntity() instanceof LivingEntity le && e.getEntity() instanceof Enemy) {
            feinde.put(chef.getUniqueId(), le);
            feindSeit.put(chef.getUniqueId(), (long) Bukkit.getCurrentTick());
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
