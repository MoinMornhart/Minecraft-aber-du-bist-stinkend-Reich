package de.moinmornhart.stinkendreich.paper.block;

import com.google.gson.JsonArray;
import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import de.moinmornhart.stinkendreich.paper.villa.Baustelle;
import de.moinmornhart.stinkendreich.paper.villa.Villen;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Ladder;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Festung (Sicherheitsstufe 4, totaler Lockdown) wie in der Mod: Landminen im Gelände rund ums Grundstück und
 * Abwehrgeschütze auf der Mauer treffen Monster und Eindringlinge, nie Bewohner, Kartenbesitzer, das Personal oder Tiere.
 * Explosionen beschädigen keine Blöcke. Beim Einschalten fahren die Teile als Welle hoch (Geschütze und Himmelslaser
 * rund um die Villa, Minen von innen nach außen). Dazu der Notausgang: Tunnel aus dem Bunker bis zu einer versteckten
 * Falltür im Rasen, die nur mit Zugangskarte aufgeht.
 */
public final class Festung {
    private static final int MINE_PAUSE = 60;
    private static final double GESCHUETZ_REICHWEITE = 32.0;
    private static final float GESCHUETZ_SCHADEN = 9.0F;
    /** Minengürtel vor dem Außenzaun: Breite, Raster, Abstand zum Zaun. */
    private static final int GUERTEL = 40, RASTER = 8, ABSTAND_ZAUN = 3;

    private final StinkendReichPaper plugin;
    private final VirtuelleBloecke bloecke;
    private final NamespacedKey schluesselButler;
    private final Map<String, Long> minenZuletzt = new HashMap<>();
    private long tick;

    public Festung(StinkendReichPaper plugin, VirtuelleBloecke bloecke, Mechanik mechanik) {
        this.plugin = plugin;
        this.bloecke = bloecke;
        this.schluesselButler = new NamespacedKey(plugin, "butler");
        // Versteckte Falltür am Ende des Notausgangs: nur mit Zugangskarte (von oben wie von unten)
        mechanik.anmelden("geheimluke", (p, b, z) -> {
            Villen.Villa v = plugin.villen().bei(b.getLocation(), 64);
            if (!plugin.zugang().istBewohner(p, v)) {
                b.getWorld().playSound(b.getLocation(), "stinkendreich:piep_fehler", SoundCategory.BLOCKS, 0.6F, 1.0F);
                p.sendActionBar(Component.text("Keine Zugangskarte!", NamedTextColor.RED));
                return true;
            }
            boolean auf = !z.ist("open", "true");
            bloecke.setzen(b, z.mit("open", Boolean.toString(auf)));
            b.getWorld().playSound(b.getLocation(), auf ? Sound.BLOCK_WOODEN_TRAPDOOR_OPEN : Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE,
                    SoundCategory.BLOCKS, 1.0F, 1.0F);
            return true;
        });
        Bukkit.getScheduler().runTaskTimer(plugin, this::takt, 2L, 2L);
    }

    // ------------------------------------------------------------------
    // Welle beim Einschalten
    // ------------------------------------------------------------------

    /** Nach wie vielen Ticks ein Teil hochfährt: {@code umlauf} = Welle rund um die Villa, sonst von innen nach außen. */
    private long verzoegerung(Villen.Villa v, Block b, boolean umlauf) {
        double mx = v.x() + plugin.bauplan().breiteX / 2.0, mz = v.z() + plugin.bauplan().breiteZ / 2.0;
        double dx = b.getX() + 0.5 - mx, dz = b.getZ() + 0.5 - mz;
        if (umlauf) {
            return (long) ((Math.atan2(dz, dx) + Math.PI) / (2 * Math.PI) * 60);
        }
        return (long) Math.max(0, Math.min(100, (Math.sqrt(dx * dx + dz * dz) - 56) * 2.0));
    }

    /** Ist dieser Teil der Festung schon hochgefahren? */
    public boolean bereit(Villen.Villa v, Block b, boolean umlauf) {
        return v != null && plugin.sicherheit().stufe(v) >= Sicherheit.FESTUNG
                && plugin.sicherheit().festungTicks(v) >= verzoegerung(v, b, umlauf);
    }

    /** Ziel der Festung: Monster und Eindringlinge, nie Personal (James, Tänzerinnen, Fahrzeuge), Tiere oder Spieler mit Recht. */
    public boolean ziel(Entity e, Villen.Villa v) {
        if (!(e instanceof LivingEntity l) || l.isDead() || e instanceof ArmorStand || e instanceof Mannequin) {
            return false;
        }
        if (e instanceof WanderingTrader t && t.getPersistentDataContainer().has(schluesselButler)) {
            return false;
        }
        if (e instanceof Player) {
            return plugin.sicherheit().istEindringling(e, v);
        }
        return e instanceof Enemy;
    }

    // ------------------------------------------------------------------
    // Takt: Minen alle 4 Ticks, Geschütze alle 10
    // ------------------------------------------------------------------

    private void takt() {
        tick += 2;
        if (tick % 4 == 0) {
            for (Block b : bloecke.alle("landmine")) {
                mine(b);
            }
        }
        if (tick % 10 == 0) {
            for (Block b : bloecke.alle("abwehrgeschuetz")) {
                geschuetz(b);
            }
        }
    }

    private void mine(Block b) {
        Zustand z = bloecke.zustand(b);
        Villen.Villa v = plugin.villen().bei(b.getLocation(), GUERTEL + 8);
        if (z == null) {
            return;
        }
        boolean scharf = bereit(v, b, false);
        if (!z.ist("lit", Boolean.toString(scharf))) {
            bloecke.setzen(b, z.mit("lit", Boolean.toString(scharf)));      // rotes Licht = scharf
        }
        String k = b.getX() + " " + b.getY() + " " + b.getZ();
        if (!scharf || tick - minenZuletzt.getOrDefault(k, -1000L) < MINE_PAUSE) {
            return;
        }
        BoundingBox feld = new BoundingBox(b.getX() - 0.1, b.getY(), b.getZ() - 0.1, b.getX() + 1.1, b.getY() + 0.5, b.getZ() + 1.1);
        List<Entity> opfer = new ArrayList<>();
        for (Entity e : b.getWorld().getNearbyEntities(feld)) {
            if (ziel(e, v)) {
                opfer.add(e);
            }
        }
        if (opfer.isEmpty()) {
            return;
        }
        minenZuletzt.put(k, tick);
        explosion(b.getLocation().add(0.5, 0, 0.5), v);
        for (Entity e : opfer) {
            if (e instanceof Player p) {
                plugin.sicherheit().alarm(v, b, p.getName() + " ist auf eine Mine getreten");
                break;
            }
        }
    }

    /** Feuer, Rauch und Knall, Schaden nur für Ziele in 4 Blöcken, keine Blockschäden. */
    private void explosion(Location mitte, Villen.Villa v) {
        World w = mitte.getWorld();
        w.spawnParticle(Particle.EXPLOSION_EMITTER, mitte.clone().add(0, 0.3, 0), 1);
        w.spawnParticle(Particle.EXPLOSION, mitte.clone().add(0, 0.6, 0), 8, 1.0, 0.6, 1.0, 0);
        w.spawnParticle(Particle.LARGE_SMOKE, mitte.clone().add(0, 0.5, 0), 30, 0.9, 0.7, 0.9, 0.05);
        w.spawnParticle(Particle.FLAME, mitte.clone().add(0, 0.3, 0), 25, 0.7, 0.3, 0.7, 0.1);
        w.playSound(mitte, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 3.0F, 0.9F);
        for (Entity e : w.getNearbyEntities(mitte, 4, 3, 4)) {
            double abstand = e.getLocation().distance(mitte);
            if (abstand > 4.0 || !ziel(e, v)) {
                continue;
            }
            ((LivingEntity) e).damage(60.0 * (1.0 - abstand / 5.0));
            Vector weg = e.getLocation().toVector().subtract(mitte.toVector());
            weg = weg.lengthSquared() < 1.0E-4 ? new Vector() : weg.normalize().multiply(1.2);
            e.setVelocity(weg.add(new Vector(0, 0.9, 0)));
        }
        plugin.getLogger().info("Festung: Mine bei " + mitte.getBlockX() + " " + mitte.getBlockY() + " " + mitte.getBlockZ());
    }

    private void geschuetz(Block b) {
        Zustand z = bloecke.zustand(b);
        Villen.Villa v = plugin.villen().bei(b.getLocation(), 16);
        if (z == null) {
            return;
        }
        boolean aktiv = bereit(v, b, true);
        if (!z.ist("powered", Boolean.toString(aktiv))) {
            // Ausfahren bzw. Einfahren: Kopf mit den Läufen hebt sich aus dem Sockel
            bloecke.setzen(b, z.mit("powered", Boolean.toString(aktiv)));
            b.getWorld().playSound(b.getLocation(), aktiv ? Sound.BLOCK_PISTON_EXTEND : Sound.BLOCK_PISTON_CONTRACT, SoundCategory.BLOCKS, 1.2F, 0.7F);
            b.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, b.getX() + 0.5, b.getY() + 0.8, b.getZ() + 0.5, 4, 0.2, 0.1, 0.2, 0.02);
            return;
        }
        if (aktiv) {
            schiessen(b, v);
        }
    }

    /** Nächstes Ziel bis 32 Blöcke mit freier Sicht: roter Laserstrahl, Schaden und Feuer. */
    private void schiessen(Block turm, Villen.Villa v) {
        World w = turm.getWorld();
        Location von = turm.getLocation().add(0.5, 1.15, 0.5);
        LivingEntity bestes = null;
        double besterAbstand = GESCHUETZ_REICHWEITE * GESCHUETZ_REICHWEITE;
        for (Entity e : w.getNearbyEntities(von, GESCHUETZ_REICHWEITE, GESCHUETZ_REICHWEITE, GESCHUETZ_REICHWEITE)) {
            if (!ziel(e, v)) {
                continue;
            }
            Location auf = e.getBoundingBox().getCenter().toLocation(w);
            double d = auf.distanceSquared(von);
            if (d >= besterAbstand || !freieSicht(von, auf)) {
                continue;
            }
            bestes = (LivingEntity) e;
            besterAbstand = d;
        }
        if (bestes == null) {
            return;
        }
        Location auf = bestes.getBoundingBox().getCenter().toLocation(w);
        Vector weg = auf.toVector().subtract(von.toVector());
        int n = Math.max(1, Math.min(200, (int) (weg.length() / 0.35)));
        Particle.DustOptions rot = new Particle.DustOptions(Color.RED, 1.0F);
        for (int i = 0; i <= n; i++) {
            Location p = von.clone().add(weg.clone().multiply((double) i / n));
            w.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, rot);
        }
        w.spawnParticle(Particle.ELECTRIC_SPARK, auf, 10, 0.2, 0.2, 0.2, 0.1);
        w.playSound(turm.getLocation(), "stinkendreich:laser", SoundCategory.BLOCKS, 1.6F, 0.8F);
        bestes.damage(GESCHUETZ_SCHADEN);
        bestes.setFireTicks(60);
    }

    private static boolean freieSicht(Location von, Location bis) {
        Vector richtung = bis.toVector().subtract(von.toVector());
        double laenge = richtung.length();
        return laenge < 0.01 || von.getWorld().rayTraceBlocks(von, richtung.normalize(), laenge, FluidCollisionMode.NEVER, true) == null;
    }

    // ------------------------------------------------------------------
    // Bau im Gelände (beim Bau einer neuen Villa)
    // ------------------------------------------------------------------

    /**
     * Minen im Raster rund ums Grundstück (bis 40 Blöcke weit, nicht auf der Zufahrt, nicht in Wasser oder Bäumen) und
     * der Notausgang. Die Chunks werden im Hintergrund geladen, gebaut wird, sobald sie da sind.
     */
    public void bauen(Villen.Villa v) {
        World w = v.world();
        int bx = plugin.bauplan().breiteX, bz = plugin.bauplan().breiteZ;
        Map<Long, List<int[]>> jeChunk = new HashMap<>();
        for (int lx = -GUERTEL; lx < bx + GUERTEL; lx += RASTER) {
            int versatz = Math.floorMod(lx / RASTER, 2) * (RASTER / 2);
            for (int lz = -GUERTEL + versatz; lz < bz + GUERTEL; lz += RASTER) {
                boolean drin = lx > -ABSTAND_ZAUN && lx < bx + ABSTAND_ZAUN && lz > -ABSTAND_ZAUN && lz < bz + ABSTAND_ZAUN;
                boolean zufahrt = lz >= bz && lx >= de.moinmornhart.stinkendreich.paper.villa.Ausstattung.ZUFAHRT_X0 - 3
                        && lx <= de.moinmornhart.stinkendreich.paper.villa.Ausstattung.ZUFAHRT_X1 + 3;
                if (drin || zufahrt) {
                    continue;
                }
                int wx = v.x() + lx, wz = v.z() + lz;
                jeChunk.computeIfAbsent(((long) (wx >> 4) << 32) | ((wz >> 4) & 0xffffffffL), k -> new ArrayList<>()).add(new int[]{wx, wz});
            }
        }
        int[] anzahl = {0, jeChunk.size()};
        jeChunk.forEach((schluessel, stellen) -> w.getChunkAtAsync((int) (schluessel >> 32), (int) (long) schluessel, true).thenAccept(c -> {
            for (int[] s : stellen) {
                int y = Baustelle.bodenHoehe(w, s[0], s[1]);
                Block hier = w.getBlockAt(s[0], y, s[1]), unten = hier.getRelative(BlockFace.DOWN);
                if (!unten.getType().isSolid() || unten.isLiquid() || !(hier.getType().isAir() || hier.isReplaceable()) || hier.isLiquid()) {
                    continue;
                }
                bloecke.setzen(hier, Zustand.lesen("stinkendreich:landmine[lit=false]"));
                anzahl[0]++;
            }
            if (--anzahl[1] == 0) {
                plugin.getLogger().info("Festung: " + anzahl[0] + " Minen um Villa " + v.nr());
            }
        }));
        notausgang(v);
    }

    /**
     * Verlängert den Fluchttunnel aus dem Bunker (Marke „notausgang“: Rand x, Höhe, z, Länge) unter dem Gelände nach
     * Westen; am Ende ein Schacht mit Leiter bis zu einer versteckten Falltür im Rasen (nur mit Zugangskarte).
     */
    private void notausgang(Villen.Villa v) {
        JsonArray m = plugin.bauplan().ersteMarke("notausgang");
        if (m == null) {
            return;
        }
        int rand = m.get(0).getAsInt(), y = m.get(1).getAsInt(), z = m.get(2).getAsInt(), laenge = m.get(3).getAsInt();
        int ende = rand - laenge;
        World w = v.world();
        List<java.util.concurrent.CompletableFuture<?>> chunks = new ArrayList<>();
        for (int lx = ende - 4; lx <= rand; lx += 16) {
            for (int dz = -4; dz <= 5; dz += 9) {
                chunks.add(w.getChunkAtAsync((v.x() + lx) >> 4, (v.z() + z + dz) >> 4, true));
            }
        }
        chunks.add(w.getChunkAtAsync((v.x() + rand) >> 4, (v.z() + z) >> 4, true));
        java.util.concurrent.CompletableFuture.allOf(chunks.toArray(new java.util.concurrent.CompletableFuture[0])).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> notausgangBauen(v, rand, y, z, ende)));
    }

    private void notausgangBauen(Villen.Villa v, int rand, int y, int z, int ende) {
        World w = v.world();
        BlockData wand = Material.POLISHED_DEEPSLATE.createBlockData(), boden = Material.POLISHED_ANDESITE.createBlockData();
        BlockData licht = Material.VERDANT_FROGLIGHT.createBlockData(), luft = Material.AIR.createBlockData();
        for (int lx = rand - 1; lx > ende; lx--) {
            for (int dy = -1; dy <= 3; dy++) {
                for (int dz = -1; dz <= 2; dz++) {
                    boolean innen = dy >= 0 && dy <= 2 && dz >= 0 && dz <= 1;
                    BlockData s = innen ? luft : (dy == -1 && (dz == 0 || dz == 1) ? boden : wand);
                    if (innen && dy == 2 && dz == 0 && Math.floorMod(lx, 5) == 0) {
                        s = licht;                                  // grünes Notlicht
                    }
                    v.block(lx, y + dy, z + dz).setBlockData(s, false);
                }
            }
        }
        // Luke bündig in der Oberfläche; rundherum ein Stück Wiese genau auf dieser Höhe
        int wx = v.x() + ende, wz = v.z() + z;
        int oben = Math.max(Baustelle.bodenHoehe(w, wx, wz) - 1 - v.y(), y + 4);
        BlockData erde = Material.DIRT.createBlockData(), rasen = Material.GRASS_BLOCK.createBlockData();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 4; dz++) {
                int grund = Baustelle.bodenHoehe(w, wx + dx, wz + dz) - 1 - v.y();
                for (int ly = Math.min(grund, oben - 3); ly < oben; ly++) {
                    v.block(ende + dx, ly, z + dz).setBlockData(erde, false);
                }
                v.block(ende + dx, oben, z + dz).setBlockData(rasen, false);
                for (int ly = oben + 1; ly <= Math.max(grund, oben) + 1; ly++) {
                    v.block(ende + dx, ly, z + dz).setBlockData(luft, false);
                }
            }
        }
        Ladder leiter = (Ladder) Material.LADDER.createBlockData();
        leiter.setFacing(BlockFace.EAST);
        for (int ly = y - 1; ly < oben; ly++) {
            BlockData wd = ly < oben - 1 ? wand : erde;
            for (int dz = -1; dz <= 2; dz++) {
                v.block(ende - 1, ly, z + dz).setBlockData(wd, false);
                if (ly > y + 2 || dz < 0 || dz > 1) {
                    v.block(ende + 1, ly, z + dz).setBlockData(wd, false);
                }
            }
            v.block(ende, ly, z - 1).setBlockData(wd, false);
            v.block(ende, ly, z + 2).setBlockData(wd, false);
            v.block(ende, ly, z + 1).setBlockData(ly <= y + 2 && ly >= y ? luft : wd, false);
            v.block(ende, ly, z).setBlockData(ly == y - 1 ? boden : leiter, false);
        }
        bloecke.setzen(v.block(ende, oben, z),
                Zustand.lesen("stinkendreich:geheimluke[facing=east,half=top,open=false,powered=false,waterlogged=false]"));
        plugin.getLogger().info("Festung: Notausgang von Villa " + v.nr() + " bei " + (v.x() + ende) + " " + (v.y() + oben) + " " + (v.z() + z));
    }
}
