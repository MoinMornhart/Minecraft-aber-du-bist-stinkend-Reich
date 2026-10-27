package de.moinmornhart.stinkendreich.paper.butler;

import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * James jagt (wie {@code JagdAuftrag} in der Mod): Kühe, Schweine, Schafe, Hühner und Kaninchen, bis genug rohes
 * Fleisch beisammen ist. Geschont werden Jungtiere, Tiere mit Namen oder an der Leine, Tiere auf einem Villa-Grundstück,
 * und von jeder Art bleiben in der Gegend immer mindestens zwei übrig.
 */
final class Jaeger implements Beschaffung {
    /** Fleisch als „Rohstoff“ für Lieferung und Anzeige. */
    static final Bergbau.Rohstoff FLEISCH = new Bergbau.Rohstoff("fleisch", "Fleisch", Set.of(),
            Set.of(Material.BEEF, Material.PORKCHOP, Material.MUTTON, Material.CHICKEN, Material.RABBIT), Material.IRON_SWORD, null);

    private static final Set<EntityType> BEUTE = Set.of(EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN,
            EntityType.RABBIT);
    private static final int SUCHRADIUS = 32, HOECHSTENS = 20 * 60 * 5;

    private enum Phase { RAUS, SUCHEN, JAGEN, EINSAMMELN }

    private final StinkendReichPaper plugin;
    private final Mob koerper;
    private final LivingEntity figur;
    private final int menge;
    private final Location start;
    private final Laufen laufen;
    private final List<ItemStack> beute = new ArrayList<>();
    private Phase phase = Phase.RAUS;
    private LivingEntity tier;
    private Location fundort;
    private int gesammelt, alter, phasenTicks, fehlversuche;

    Jaeger(StinkendReichPaper plugin, Mob koerper, LivingEntity figur, int menge, Location start) {
        this.plugin = plugin;
        this.koerper = koerper;
        this.figur = figur;
        this.menge = menge;
        this.start = start.clone();
        this.laufen = new Laufen(koerper);
        plugin.getLogger().info(String.format(Locale.ROOT, "Jagd: %d Fleisch, Start bei %d %d %d", menge, start.getBlockX(),
                start.getBlockY(), start.getBlockZ()));
    }

    private void phase(Phase neu) {
        if (neu != phase) {
            Location l = koerper.getLocation();
            plugin.getLogger().info(String.format(Locale.ROOT, "Jagd: %s → %s bei %d %d %d (%d/%d)", phase, neu, l.getBlockX(),
                    l.getBlockY(), l.getBlockZ(), gesammelt, menge));
            phase = neu;
            phasenTicks = 0;
        }
    }

    @Override
    public Bergbau.Rohstoff rohstoff() {
        return FLEISCH;
    }

    @Override
    public int gesammelt() {
        return gesammelt;
    }

    @Override
    public List<ItemStack> beute() {
        return beute;
    }

    @Override
    public String status() {
        return phase == Phase.RAUS ? "geht hinaus auf die Jagd" : "jagt (" + gesammelt + "/" + menge + " Fleisch)";
    }

    @Override
    public Bergbau.Stand tick() {
        if (gesammelt >= menge && phase != Phase.EINSAMMELN || ++alter > HOECHSTENS) {
            return fertig();
        }
        phasenTicks++;
        switch (phase) {
            case RAUS -> {
                if (laufen.zu(start, 2.0, 1.5, 20 * 20)) {
                    phase(Phase.SUCHEN);
                }
            }
            case SUCHEN -> {
                tier = naechstesTier();
                if (tier == null) {
                    plugin.getLogger().info("Jagd: kein Tier mehr in " + SUCHRADIUS + " Blöcken");
                    return gesammelt > 0 ? fertig() : Bergbau.Stand.NICHTS;
                }
                figur.getEquipment().setItemInMainHand(ItemStack.of(Material.IRON_SWORD));
                phase(Phase.JAGEN);
            }
            case JAGEN -> {
                if (tier == null || !tier.isValid() || tier.isDead()) {
                    phase(Phase.EINSAMMELN);
                    return Bergbau.Stand.LAEUFT;
                }
                fundort = tier.getLocation();
                koerper.lookAt(tier);
                if (koerper.getLocation().distanceSquared(fundort) < 2.4 * 2.4) {
                    koerper.getPathfinder().stopPathfinding();
                    if (phasenTicks % 12 == 0) {
                        figur.swingMainHand();
                        tier.damage(6.0, koerper);
                    }
                } else if (phasenTicks % 5 == 0) {
                    koerper.getPathfinder().moveTo(tier, 1.3);
                }
                if (phasenTicks > 20 * 40) {
                    // Nicht zu erwischen (z. B. hinter Wasser): ein anderes Tier
                    tier = null;
                    phase(Phase.SUCHEN);
                    if (++fehlversuche > 6) {
                        return gesammelt > 0 ? fertig() : Bergbau.Stand.NICHTS;
                    }
                }
            }
            case EINSAMMELN -> {
                // Kurz warten, bis die Beute fällt, dann alles in der Nähe aufheben
                if (phasenTicks < 15 || fundort == null) {
                    return Bergbau.Stand.LAEUFT;
                }
                Item naechstes = fundort.getWorld().getNearbyEntitiesByType(Item.class, fundort, 4).stream()
                        .filter(Entity::isValid)
                        .min(Comparator.comparingDouble(i -> i.getLocation().distanceSquared(koerper.getLocation())))
                        .orElse(null);
                if (naechstes == null || phasenTicks > 20 * 15) {
                    phase(Phase.SUCHEN);
                } else if (laufen.zu(naechstes.getLocation(), 1.4, 1.5, 20 * 6)) {
                    ItemStack s = naechstes.getItemStack();
                    if (FLEISCH.ertrag().contains(s.getType())) {
                        gesammelt += s.getAmount();
                    }
                    beute.add(s.clone());
                    naechstes.remove();
                    figur.swingMainHand();
                }
            }
        }
        return Bergbau.Stand.LAEUFT;
    }

    private Bergbau.Stand fertig() {
        plugin.getLogger().info("Jagd: fertig mit " + gesammelt + " Fleisch");
        return Bergbau.Stand.FERTIG;
    }

    /** Das nächste Tier, das er jagen darf. */
    private LivingEntity naechstesTier() {
        Location hier = koerper.getLocation();
        List<LivingEntity> tiere = new ArrayList<>(hier.getNearbyLivingEntities(SUCHRADIUS, 16, e -> BEUTE.contains(e.getType()) && !e.isDead()));
        return tiere.stream()
                .filter(t -> !(t instanceof Ageable a && !a.isAdult()) && t.customName() == null && !t.isLeashed()
                        && plugin.villen().bei(t.getLocation(), 2) == null)
                .filter(t -> artgenossen(tiere, t) >= 3)
                .min(Comparator.comparingDouble(t -> t.getLocation().distanceSquared(hier)))
                .orElse(null);
    }

    /** Wie viele Tiere derselben Art in der Nähe sind (einschließlich des Tiers selbst). */
    private static long artgenossen(List<LivingEntity> tiere, Entity tier) {
        return tiere.stream().filter(t -> t.getType() == tier.getType() && t.getLocation().distanceSquared(tier.getLocation()) < 24 * 24).count();
    }

    @Override
    public void abbrechen() {
        koerper.getPathfinder().stopPathfinding();
        figur.getEquipment().setItemInMainHand(null);
    }

    @Override
    public void aufraeumen() {
        figur.getEquipment().setItemInMainHand(null);
    }
}
