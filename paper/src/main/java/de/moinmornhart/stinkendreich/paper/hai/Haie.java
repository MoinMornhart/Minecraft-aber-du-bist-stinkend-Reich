package de.moinmornhart.stinkendreich.paper.hai;

import com.destroystokyo.paper.entity.ai.VanillaGoal;
import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Display;
import org.bukkit.entity.Dolphin;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Haie auf dem Paper-Server (wie {@code HaiEntity} in der Mod). Den Körper gibt ein unsichtbarer Delfin (schwimmt,
 * bleibt im Wasser); darüber zeigen zwei Anzeige-Figuren das Hai-Modell der Mod: Rumpf und Schwanz, der beim Schwimmen
 * seitlich schlägt (Modelle aus {@code tools/paper/hai_modell.py}).
 *
 * <p>Jagd ohne das Ziel-System von Minecraft (das erlaubt auf „Friedlich“ keine Spieler als Ziel): alle halbe Sekunde
 * die nächste Beute im Wasser suchen (Spieler außer Kreativ/Zuschauer, Monster), hinschwimmen, einmal je Sekunde
 * zubeißen. Der Biss hat keinen Verursacher, damit er nicht mit dem Schwierigkeitsgrad schwächer wird (in der Mod die
 * Schadensart {@code stinkendreich:hai}); die Todesnachricht setzt {@link #beimTod}.</p>
 */
public final class Haie implements Listener {
    private static final double SUCHWEITE = 16.0, SCHADEN = 8.0;
    /** Mitte des Rumpfs über den Füßen und Drehpunkt des Schwanzes von dort (Blöcke, aus hai_modell.py). */
    private static final float MITTE = 7.0F / 16.0F, SCHWANZ_Y = -4.0F / 16.0F, SCHWANZ_Z = -9.0F / 16.0F;

    private final StinkendReichPaper plugin;
    private final NamespacedKey schluesselHai, schluesselTeil;
    private final Map<Dolphin, Hai> haie = new HashMap<>();
    /** Wann wer zuletzt gebissen wurde (für die Todesnachricht). */
    private final Map<UUID, Integer> gebissen = new HashMap<>();

    private static final class Hai {
        ItemDisplay koerper, schwanz;
        LivingEntity beute;
        int pause, alter;
    }

    public Haie(StinkendReichPaper plugin) {
        this.plugin = plugin;
        this.schluesselHai = new NamespacedKey(plugin, "hai");
        this.schluesselTeil = new NamespacedKey(plugin, "hai_teil");
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::takt, 1L, 1L);
    }

    /** Ein Delfin aus dem Bauplan wird zum Hai. */
    public void einrichten(Dolphin d) {
        d.getPersistentDataContainer().set(schluesselHai, PersistentDataType.BYTE, (byte) 1);
        d.setInvisible(true);
        d.setSilent(true);
        d.setRemoveWhenFarAway(false);
        d.setPersistent(true);
        d.customName(Component.translatable("entity.stinkendreich.hai", "Hai"));
        d.setCustomNameVisible(false);
        wert(d, Attribute.MAX_HEALTH, 30.0);
        d.setHealth(30.0);
        wert(d, Attribute.MOVEMENT_SPEED, 1.3);
        aufnehmen(d);
    }

    private static void wert(LivingEntity e, Attribute a, double w) {
        if (e.getAttribute(a) != null) {
            e.getAttribute(a).setBaseValue(w);
        }
    }

    @EventHandler
    public void beimLaden(EntitiesLoadEvent e) {
        for (Entity n : e.getEntities()) {
            if (n instanceof Dolphin d && d.getPersistentDataContainer().has(schluesselHai)) {
                aufnehmen(d);
            } else if (n instanceof ItemDisplay a && a.getPersistentDataContainer().has(schluesselTeil) && !a.isPersistent()) {
                a.remove();                                 // Rest aus einer alten Sitzung (wird neu angelegt)
            }
        }
    }

    /** Delfin-Spiele weg (Springen, Spieler begleiten, Schätze …), Modell anlegen. */
    private void aufnehmen(Dolphin d) {
        if (haie.containsKey(d)) {
            return;
        }
        for (var ziel : java.util.List.of(VanillaGoal.DOLPHIN_JUMP, VanillaGoal.DOLPHIN_SWIM_WITH_PLAYER, VanillaGoal.DOLPHIN_PLAY_WITH_ITEMS,
                VanillaGoal.DOLPHIN_MOVE_TO_ITEM, VanillaGoal.DOLPHIN_SWIM_TO_TREASURE)) {
            Bukkit.getMobGoals().removeGoal(d, ziel);
        }
        Hai h = new Hai();
        h.koerper = anzeige(d, "hai");
        h.schwanz = anzeige(d, "hai_schwanz");
        haie.put(d, h);
    }

    /** Anzeige-Figur mit einem der beiden Hai-Modelle; wird nicht gespeichert (entsteht beim Laden neu). */
    private ItemDisplay anzeige(Dolphin d, String modell) {
        Location l = d.getLocation();
        l.setPitch(0);
        return d.getWorld().spawn(l, ItemDisplay.class, a -> {
            ItemStack bild = ItemStack.of(Material.PAPER);
            bild.setData(DataComponentTypes.ITEM_MODEL, Key.key("stinkendreich", "polymer/" + modell));
            a.setItemStack(bild);
            a.setPersistent(false);
            a.getPersistentDataContainer().set(schluesselTeil, PersistentDataType.STRING, d.getUniqueId().toString());
            a.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            a.setTeleportDuration(2);
            a.setInterpolationDuration(2);
            a.setViewRange(1.0F);
            a.setBrightness(null);
        });
    }

    // ------------------------------------------------------------------
    // Jeden Tick: Modell nachziehen, jagen
    // ------------------------------------------------------------------

    private void takt() {
        haie.entrySet().removeIf(e -> {
            if (e.getKey().isValid()) {
                return false;
            }
            e.getValue().koerper.remove();
            e.getValue().schwanz.remove();
            return true;
        });
        for (Map.Entry<Dolphin, Hai> e : haie.entrySet()) {
            Dolphin d = e.getKey();
            Hai h = e.getValue();
            h.alter++;
            d.setRemainingAir(d.getMaximumAir());           // Haie müssen nicht auftauchen
            if (!h.koerper.isValid() || !h.schwanz.isValid()) {
                h.koerper.remove();
                h.schwanz.remove();
                h.koerper = anzeige(d, "hai");
                h.schwanz = anzeige(d, "hai_schwanz");
            }
            boolean schwimmt = d.getVelocity().lengthSquared() > 1.0E-4;
            modell(d, h, schwimmt);
            jagen(d, h);
        }
    }

    /**
     * Beide Anzeigen stehen an den Füßen des Delfins und schauen in seine Richtung; Neigung und Schwanzschlag stecken
     * in der Transformation (Rumpf neigt sich um seine Mitte, der Schwanz hängt am Ende des Rumpfs).
     */
    private static void modell(Dolphin d, Hai h, boolean schwimmt) {
        Location l = d.getLocation();
        float neigung = (float) Math.toRadians(Math.clamp(l.getPitch(), -45.0F, 45.0F));
        l.setPitch(0);
        for (ItemDisplay a : new ItemDisplay[]{h.koerper, h.schwanz}) {
            if (a.getLocation().distanceSquared(l) > 1.0E-4 || Math.abs(a.getYaw() - l.getYaw()) > 0.5F) {
                a.teleport(l);
            }
        }
        if (h.alter % 2 != 0) {
            return;
        }
        Quaternionf neigen = new Quaternionf().rotateX(neigung);
        Vector3f mitte = new Vector3f(0, MITTE, 0);
        transformation(h.koerper, mitte, new Quaternionf(neigen).rotateZ((float) Math.PI));
        float takt = h.alter * (schwimmt ? 0.45F : 0.15F);
        float schlag = 0.35F * (float) Math.cos(takt);
        Vector3f gelenk = new Vector3f(0, SCHWANZ_Y, SCHWANZ_Z).rotate(neigen).add(mitte);
        transformation(h.schwanz, gelenk, new Quaternionf(neigen).rotateY(schlag).rotateZ((float) Math.PI));
    }

    private static void transformation(ItemDisplay a, Vector3f verschiebung, Quaternionf drehung) {
        a.setInterpolationDelay(0);
        a.setTransformation(new Transformation(verschiebung, drehung, new Vector3f(4, 4, 4), new Quaternionf()));
    }

    private void jagen(Dolphin d, Hai h) {
        if (h.pause > 0) {
            h.pause--;
        }
        if (h.alter % 10 == 0) {
            h.beute = d.isInWater() ? suchen(d) : null;
        }
        LivingEntity b = h.beute;
        if (b == null || !beute(b) || b.getLocation().distanceSquared(d.getLocation()) > SUCHWEITE * SUCHWEITE * 1.5) {
            h.beute = null;
            return;
        }
        if (h.alter % 5 == 0) {
            d.getPathfinder().moveTo(b, 1.4);
        }
        d.lookAt(b);
        // Reichweite wie beim Nahkampf: Hitboxen fast berühren (der Hai ist lang, deshalb etwas großzügiger)
        if (h.pause == 0 && d.getBoundingBox().expand(0.6).overlaps(b.getBoundingBox())) {
            beissen(d, b);
            h.pause = 20;
        }
    }

    /** Ist diese Figur Beute? Nur im Wasser; Spieler (außer Kreativ und Zuschauer) und Monster. */
    private boolean beute(LivingEntity e) {
        if (!e.isValid() || e.isDead() || !e.isInWater() || e.getPersistentDataContainer().has(schluesselHai)) {
            return false;
        }
        if (e instanceof Player p) {
            return p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR;
        }
        return e instanceof Enemy;
    }

    private LivingEntity suchen(Dolphin d) {
        LivingEntity bestes = null;
        double beste = SUCHWEITE * SUCHWEITE;
        for (Entity n : d.getNearbyEntities(SUCHWEITE, SUCHWEITE, SUCHWEITE)) {
            if (n instanceof LivingEntity l && beute(l)) {
                double abstand = l.getLocation().distanceSquared(d.getLocation());
                if (abstand < beste) {
                    beste = abstand;
                    bestes = l;
                }
            }
        }
        return bestes;
    }

    private void beissen(Dolphin d, LivingEntity ziel) {
        if (ziel instanceof Player p) {
            gebissen.put(p.getUniqueId(), Bukkit.getCurrentTick());
            p.damage(SCHADEN, DamageSource.builder(DamageType.GENERIC).build());
        } else {
            ziel.damage(SCHADEN, d);
        }
        ziel.getWorld().playSound(ziel.getLocation(), Sound.ENTITY_EVOKER_FANGS_ATTACK, SoundCategory.HOSTILE, 1.0F, 0.8F);
        ziel.getWorld().spawnParticle(Particle.BLOCK, ziel.getLocation().add(0, ziel.getHeight() / 2, 0), 12, 0.3, 0.3, 0.3, 0.1,
                Material.REDSTONE_BLOCK.createBlockData());
    }

    /** Todesnachricht wie in der Mod („… wurde von einem Hai gefressen“), wenn der letzte Biss gerade eben war. */
    @EventHandler
    public void beimTod(PlayerDeathEvent e) {
        Integer wann = gebissen.remove(e.getPlayer().getUniqueId());
        if (wann != null && Bukkit.getCurrentTick() - wann < 40) {
            e.deathMessage(Component.translatable("death.attack.stinkendreich.hai", "%1$s wurde von einem Hai gefressen",
                    e.getPlayer().displayName()));
        }
    }

    /** Für Tests: Anzahl der Haie mit Modell. */
    public int anzahl() {
        return haie.size();
    }

    /** Neuer Hai an einer Stelle (Testhilfe {@code /villa hai}). */
    public void aussetzen(Location ort) {
        einrichten(ort.getWorld().spawn(ort, Dolphin.class));
    }

    /** Anzeige-Figuren gehören zum Hai, nicht zur Villa (z. B. für Inkognito). */
    public boolean istModell(Entity e) {
        return e instanceof Display && e.getPersistentDataContainer().has(schluesselTeil);
    }
}
