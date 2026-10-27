package de.moinmornhart.stinkendreich.paper.block;

import com.google.gson.JsonArray;
import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import de.moinmornhart.stinkendreich.paper.villa.Ausstattung;
import de.moinmornhart.stinkendreich.paper.villa.Villen;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Haifischfalle auf dem Paper-Server (wie {@code Haifalle} in der Mod): Unter einem Boden aus dunklem Glas
 * („haifalle“) schwimmen die Haie. Ein Bewohner drückt den roten Knopf („haifalle_schalter“): Der nächste Eindringling
 * in 16 Blöcken wird mitten auf die Glasfläche versetzt, der Boden klappt für 5 Sekunden auf (Träger wird zur
 * Strukturleere, das Modell zeigt nur noch den Rand), und er fällt ins Becken.
 *
 * <p>Automatik: Greift jemand, der kein Bewohner ist, auf dem Grundstück einen Bewohner an, löst der nächste Schalter in
 * 48 Blöcken mit dem Angreifer als Opfer aus. Im Smart Home: Haifalle auf/zu.</p>
 */
public final class Haifalle implements Listener {
    private static final int OPFER_RADIUS = 16, FALLEN_RADIUS = 12, AUTOMATIK_RADIUS = 48;
    /** So lange bleibt der Boden offen (5 Sekunden); erst danach löst ein Schalter wieder aus. */
    private static final int OFFEN_TICKS = 100, PAUSE_TICKS = OFFEN_TICKS + 20;

    public enum Ergebnis { OK, KEINE_FALLE, KEIN_OPFER, LAEUFT }

    private final StinkendReichPaper plugin;
    private final VirtuelleBloecke bloecke;
    /** Wann ein Schalter zuletzt ausgelöst hat (Servertick). */
    private final Map<Block, Integer> zuletzt = new HashMap<>();

    public Haifalle(StinkendReichPaper plugin, VirtuelleBloecke bloecke, Mechanik mechanik) {
        this.plugin = plugin;
        this.bloecke = bloecke;
        mechanik.anmelden("haifalle_schalter", (p, b, z) -> {
            knopf(p, b);
            return true;
        });
    }

    /** Roter Knopf: nur Bewohner. */
    private void knopf(Player p, Block schalter) {
        Villen.Villa v = plugin.villen().bei(schalter.getLocation(), 16);
        if (!plugin.zugang().istBewohner(p, v)) {
            schalter.getWorld().playSound(schalter.getLocation(), "stinkendreich:piep_fehler", SoundCategory.BLOCKS, 0.6F, 1.0F);
            p.sendActionBar(Component.text("Die Haifalle bedienen nur Bewohner.", NamedTextColor.RED));
            return;
        }
        schalter.getWorld().playSound(schalter.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, SoundCategory.BLOCKS, 0.8F, 0.9F);
        switch (ausloesen(schalter, null)) {
            case OK -> p.sendActionBar(Component.text("Haifalle ausgelöst!", NamedTextColor.GOLD));
            case KEIN_OPFER -> {
                schalter.getWorld().playSound(schalter.getLocation(), "stinkendreich:piep_fehler", SoundCategory.BLOCKS, 0.6F, 1.0F);
                p.sendActionBar(Component.text("Kein Eindringling in der Nähe.", NamedTextColor.GRAY));
            }
            case KEINE_FALLE -> p.sendActionBar(Component.text("Kein Glasboden in der Nähe.", NamedTextColor.GRAY));
            case LAEUFT -> p.sendActionBar(Component.text("Die Falle ist gerade offen.", NamedTextColor.GRAY));
        }
    }

    /** Löst die Falle aus; {@code opfer} null = der nächste Eindringling in 16 Blöcken (Spieler vor Monstern). */
    public Ergebnis ausloesen(Block schalter, LivingEntity opfer) {
        int jetzt = Bukkit.getCurrentTick();
        Integer vorher = zuletzt.get(schalter);
        if (vorher != null && jetzt - vorher < PAUSE_TICKS) {
            return Ergebnis.LAEUFT;
        }
        List<Block> fallen = fallenUm(schalter);
        if (fallen.isEmpty()) {
            return Ergebnis.KEINE_FALLE;
        }
        Villen.Villa v = plugin.villen().bei(schalter.getLocation(), 16);
        if (opfer == null) {
            opfer = opferSuchen(schalter, v);
        }
        if (opfer == null) {
            return Ergebnis.KEIN_OPFER;
        }
        zuletzt.put(schalter, jetzt);
        // 1. Rein mit ihm: mitten auf die nächstgelegene Glasfläche
        Block ziel = flaechenMitte(fallen, schalter);
        Location von = opfer.getLocation();
        von.getWorld().spawnParticle(Particle.PORTAL, von.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.2);
        von.getWorld().playSound(von, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.BLOCKS, 1.0F, 0.8F);
        opfer.leaveVehicle();
        Location nach = ziel.getLocation().add(0.5, 1.0, 0.5);
        nach.setYaw(von.getYaw());
        nach.setPitch(von.getPitch());
        opfer.teleport(nach);
        opfer.setVelocity(new Vector());
        opfer.setFallDistance(0);
        nach.getWorld().spawnParticle(Particle.PORTAL, nach.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.2);
        nach.getWorld().playSound(nach, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.BLOCKS, 1.0F, 1.0F);
        if (opfer instanceof Player p) {
            p.sendActionBar(Component.text("Ab zu den Haien!", NamedTextColor.RED).decorate(TextDecoration.BOLD));
        }
        plugin.getLogger().info("Haifalle: " + opfer.getName() + " fällt ins Becken");
        // 2. Boden auf, nach 5 Sekunden wieder zu
        bodenAuf(schalter, fallen, ziel);
        return Ergebnis.OK;
    }

    private void bodenAuf(Block schalter, List<Block> fallen, Block ziel) {
        int offen = 0;
        for (Block b : fallen) {
            Zustand z = bloecke.zustand(b);
            if (z != null && !z.ist("open", "true")) {
                bloecke.setzen(b, z.mit("open", "true"));
                offen++;
            }
        }
        plugin.getLogger().info("Haifalle: " + offen + " von " + fallen.size() + " Glasböden offen");
        Location l = ziel.getLocation().add(0.5, 0.5, 0.5);
        l.getWorld().playSound(l, Sound.BLOCK_PISTON_CONTRACT, SoundCategory.BLOCKS, 1.2F, 0.7F);
        l.getWorld().playSound(l, Sound.BLOCK_IRON_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 1.0F, 0.8F);
        l.getWorld().spawnParticle(Particle.BUBBLE_POP, l.clone().add(0, -0.5, 0), 30, 1.5, 0.2, 1.5, 0.05);
        Bukkit.getScheduler().runTaskLater(plugin, () -> spaeterSchliessen(schalter), OFFEN_TICKS);
    }

    /** Schließen; steckt noch jemand in der Öffnung, in einer halben Sekunde erneut. */
    private void spaeterSchliessen(Block schalter) {
        if (schliessen(schalter)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> spaeterSchliessen(schalter), 10L);
        }
    }

    /** Schließt die offenen Glasböden; gibt true zurück, wenn einer noch blockiert ist (jemand steckt darin). */
    public boolean schliessen(Block schalter) {
        boolean blockiert = false, geschlossen = false;
        Block mitte = null;
        for (Block b : fallenUm(schalter)) {
            Zustand z = bloecke.zustand(b);
            if (z == null || !z.ist("open", "true")) {
                continue;
            }
            if (imWeg(b)) {
                blockiert = true;
                continue;
            }
            bloecke.setzen(b, z.mit("open", "false"));
            geschlossen = true;
            mitte = b;
        }
        if (geschlossen) {
            Location l = mitte.getLocation().add(0.5, 0.5, 0.5);
            l.getWorld().playSound(l, Sound.BLOCK_PISTON_EXTEND, SoundCategory.BLOCKS, 1.2F, 0.7F);
            l.getWorld().playSound(l, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.BLOCKS, 1.0F, 0.8F);
        }
        return blockiert;
    }

    private static boolean imWeg(Block b) {
        BoundingBox feld = BoundingBox.of(b);
        for (Entity e : b.getWorld().getNearbyEntities(feld)) {
            if (e instanceof LivingEntity l && !l.isDead() && !(l instanceof Player p && p.getGameMode() == GameMode.SPECTATOR)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Smart Home und Automatik
    // ------------------------------------------------------------------

    /** Smart Home: auf = nächsten Eindringling hineinwerfen (sonst geht der Boden trotzdem auf), zu = schließen. */
    public String smartHome(Villen.Villa v, boolean auf) {
        List<JsonArray> becken = plugin.bauplan().marke("haibecken");
        if (v == null || becken.isEmpty()) {
            return "Keine Haifalle in dieser Villa.";
        }
        Block schalter = naechsterSchalter(Ausstattung.ort(v, becken.getFirst()));
        if (schalter == null) {
            return "Keine Haifalle in dieser Villa.";
        }
        if (!auf) {
            schliessen(schalter);
            return "Haifalle zu.";
        }
        Ergebnis e = ausloesen(schalter, null);
        if (e == Ergebnis.KEIN_OPFER) {
            List<Block> fallen = fallenUm(schalter);
            zuletzt.put(schalter, Bukkit.getCurrentTick());
            bodenAuf(schalter, fallen, flaechenMitte(fallen, schalter));
            e = Ergebnis.OK;
        }
        return switch (e) {
            case OK -> "Haifalle offen.";
            case LAEUFT -> "Die Falle ist gerade offen.";
            default -> "Keine Haifalle in dieser Villa.";
        };
    }

    /** Greift ein Fremder auf dem Grundstück einen Bewohner an, fällt er ins Haibecken. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beimAngriff(EntityDamageByEntityEvent e) {
        Entity taeter = e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Entity s ? s : e.getDamager();
        if (!(e.getEntity() instanceof Player bewohner) || !(taeter instanceof Player angreifer) || angreifer == bewohner
                || angreifer.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Villen.Villa v = plugin.villen().bei(bewohner.getLocation(), 0);
        if (v == null || !plugin.zugang().istBewohner(bewohner, v) || plugin.zugang().istBewohner(angreifer, v)) {
            return;
        }
        Block schalter = naechsterSchalter(angreifer.getLocation());
        if (schalter != null && ausloesen(schalter, angreifer) == Ergebnis.OK) {
            bewohner.sendActionBar(Component.text("Haifalle: " + angreifer.getName() + " ist bei den Haien.", NamedTextColor.GOLD));
        }
    }

    // ------------------------------------------------------------------
    // Hilfen
    // ------------------------------------------------------------------

    private Block naechsterSchalter(Location um) {
        Block bester = null;
        double beste = (double) AUTOMATIK_RADIUS * AUTOMATIK_RADIUS;
        for (Block b : bloecke.alle("haifalle_schalter")) {
            if (b.getWorld().equals(um.getWorld()) && b.getLocation().distanceSquared(um) < beste) {
                beste = b.getLocation().distanceSquared(um);
                bester = b;
            }
        }
        return bester;
    }

    private List<Block> fallenUm(Block schalter) {
        List<Block> liste = new ArrayList<>();
        for (Block b : bloecke.alle("haifalle")) {
            if (b.getWorld().equals(schalter.getWorld()) && b.getLocation().distanceSquared(schalter.getLocation()) <= FALLEN_RADIUS * FALLEN_RADIUS) {
                liste.add(b);
            }
        }
        return liste;
    }

    /** Mitte der zusammenhängenden Glasfläche, die dem Schalter am nächsten liegt (wie in der Mod). */
    private static Block flaechenMitte(List<Block> fallen, Block schalter) {
        Block start = fallen.getFirst();
        for (Block b : fallen) {
            if (b.getLocation().distanceSquared(schalter.getLocation()) < start.getLocation().distanceSquared(schalter.getLocation())) {
                start = b;
            }
        }
        Set<Block> alle = new HashSet<>(fallen), flaeche = new HashSet<>();
        ArrayDeque<Block> offen = new ArrayDeque<>();
        offen.add(start);
        flaeche.add(start);
        while (!offen.isEmpty()) {
            Block b = offen.poll();
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                Block n = b.getRelative(d[0], 0, d[1]);
                if (alle.contains(n) && flaeche.add(n)) {
                    offen.add(n);
                }
            }
        }
        double sx = 0, sz = 0;
        for (Block b : flaeche) {
            sx += b.getX();
            sz += b.getZ();
        }
        sx /= flaeche.size();
        sz /= flaeche.size();
        Block mitte = start;
        double beste = Double.MAX_VALUE;
        for (Block b : flaeche) {
            double d = (b.getX() - sx) * (b.getX() - sx) + (b.getZ() - sz) * (b.getZ() - sz);
            if (d < beste) {
                beste = d;
                mitte = b;
            }
        }
        return mitte;
    }

    /** Zuerst Spieler, die nicht hineindürfen, sonst ein Monster – nie Bewohner, Personal oder Haie. */
    private LivingEntity opferSuchen(Block schalter, Villen.Villa v) {
        Location mitte = schalter.getLocation().add(0.5, 0.5, 0.5);
        LivingEntity spieler = null, monster = null;
        double ds = (double) OPFER_RADIUS * OPFER_RADIUS, dm = ds;
        for (Entity e : mitte.getWorld().getNearbyEntities(mitte, OPFER_RADIUS, OPFER_RADIUS, OPFER_RADIUS)) {
            if (!(e instanceof LivingEntity l) || l.isDead() || !plugin.sicherheit().istEindringling(l, v)) {
                continue;
            }
            double d = l.getLocation().distanceSquared(mitte);
            if (l instanceof Player && d <= ds) {
                ds = d;
                spieler = l;
            } else if (!(l instanceof Player) && d <= dm) {
                dm = d;
                monster = l;
            }
        }
        return spieler != null ? spieler : monster;
    }
}
