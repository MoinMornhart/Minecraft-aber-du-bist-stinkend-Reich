package de.moinmornhart.stinkendreich.paper.butler;

import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Bergbau für James und die Mitarbeiter auf dem Paper-Server (wie {@code SammelAuftrag}, {@code Tunnel} und
 * {@code Abbau} in der Mod): vor die Villa gehen, die Umgebung nach dem Erz absuchen, einen begehbaren Treppentunnel
 * (zwei Blöcke hoch) zum nächsten Vorkommen graben, die ganze Ader abbauen und auf demselben Weg zurückkommen.
 *
 * <p>Abgebaut wird nur Natürliches außerhalb jeder Villa (Stein, Erde, Kies, Erze …); Spielerbauten und die Villa
 * bleiben unberührt. Bevor eine Wand zu Wasser oder Lava aufgeht, wird sie mit Bruchstein abgedichtet. Den Körper
 * bewegt der Wegfinder (ein unsichtbarer Händler), die sichtbare Figur holt mit dem Werkzeug aus.</p>
 */
public final class Bergbau {
    public enum Stand { LAEUFT, FERTIG, NICHTS }

    private enum Phase { RAUS, SUCHEN, TUNNEL, ABBAUEN, ZURUECK }

    /** Ein Rohstoff: Blöcke, aus denen er kommt, was als Ertrag zählt, Werkzeug, Anzeigename. */
    public record Rohstoff(String name, String anzeige, Set<Material> bloecke, Set<Material> ertrag, Material werkzeug,
                           Material geschmolzen) {
    }

    /** Die Rohstoffe, die James und die Mitarbeiter abbauen (Reihenfolge = Aufgaben der Mitarbeiter). */
    public static final Map<String, Rohstoff> ROHSTOFFE = new LinkedHashMap<>();

    static {
        erz("diamant", "Diamanten", Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, Material.DIAMOND, Material.DIAMOND_PICKAXE, null);
        erz("eisen", "Eisen", Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE, Material.RAW_IRON, Material.IRON_PICKAXE, Material.IRON_INGOT);
        erz("kohle", "Kohle", Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE, Material.COAL, Material.STONE_PICKAXE, null);
        erz("gold", "Gold", Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.RAW_GOLD, Material.IRON_PICKAXE, Material.GOLD_INGOT);
        erz("smaragd", "Smaragde", Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE, Material.EMERALD, Material.IRON_PICKAXE, null);
        erz("redstone", "Redstone", Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE, Material.REDSTONE, Material.IRON_PICKAXE, null);
        erz("lapis", "Lapislazuli", Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE, Material.LAPIS_LAZULI, Material.STONE_PICKAXE, null);
        erz("kupfer", "Kupfer", Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE, Material.RAW_COPPER, Material.STONE_PICKAXE, Material.COPPER_INGOT);
    }

    private static void erz(String name, String anzeige, Material a, Material b, Material ertrag, Material werkzeug, Material geschmolzen) {
        ROHSTOFFE.put(name, new Rohstoff(name, anzeige, Set.of(a, b), Set.of(ertrag), werkzeug, geschmolzen));
    }

    /** Erkennt den Rohstoff in einem Satz („hol mir 16 Eisen“, „Diamanten“, „Kohle“ …), sonst null. */
    public static Rohstoff erkennen(String satz) {
        String s = satz.toLowerCase(Locale.GERMAN);
        if (s.contains("diamant")) return ROHSTOFFE.get("diamant");
        if (s.contains("eisen")) return ROHSTOFFE.get("eisen");
        if (s.contains("kohle")) return ROHSTOFFE.get("kohle");
        if (s.contains("gold")) return ROHSTOFFE.get("gold");
        if (s.contains("smaragd")) return ROHSTOFFE.get("smaragd");
        if (s.contains("redstone")) return ROHSTOFFE.get("redstone");
        if (s.contains("lapis")) return ROHSTOFFE.get("lapis");
        if (s.contains("kupfer")) return ROHSTOFFE.get("kupfer");
        return null;
    }

    private static final int SUCHRADIUS = 32, JE_TICK = 24000, HOECHSTENS = 20 * 60 * 8;

    private final StinkendReichPaper plugin;
    private final Mob koerper;
    private final LivingEntity figur;
    private final Rohstoff rohstoff;
    private final int menge;
    private final Location start;
    private int gesammelt;
    private final List<ItemStack> beute = new ArrayList<>();

    private Phase phase = Phase.RAUS;

    /** Phase wechseln (mit Zeile im Serverprotokoll, damit man den Weg nachvollziehen kann). */
    private void phase(Phase neu) {
        if (neu != phase) {
            Location l = koerper.getLocation();
            plugin.getLogger().info(String.format(Locale.ROOT, "Bergbau: %s → %s bei %d %d %d%s", phase, neu, l.getBlockX(), l.getBlockY(),
                    l.getBlockZ(), ziel == null ? "" : " (Ziel " + ziel.getX() + " " + ziel.getY() + " " + ziel.getZ() + ")"));
            phase = neu;
        }
    }
    private int alter;
    // Suche
    private List<Block> kandidaten = new ArrayList<>();
    private int sx, sz, sy;
    private final Set<Block> gesperrt = new HashSet<>();
    private final Deque<Block> ader = new ArrayDeque<>();
    private Block ziel;
    private int zielTicks;
    // Tunnel
    private final Deque<Block> offen = new ArrayDeque<>();
    private Location schritt;
    private int schrittTicks, letzteDx = 1, letzteDz;
    private final Deque<Location> weg = new ArrayDeque<>();
    // Abbau
    private Block arbeit;
    private int fortschritt, dauer;

    public Bergbau(StinkendReichPaper plugin, Mob koerper, LivingEntity figur, Rohstoff rohstoff, int menge, Location start) {
        this.plugin = plugin;
        this.koerper = koerper;
        this.figur = figur;
        this.rohstoff = rohstoff;
        this.menge = menge;
        this.start = start.clone();
        figur.getEquipment().setItemInMainHand(ItemStack.of(rohstoff.werkzeug()));
        plugin.getLogger().info(String.format(Locale.ROOT, "Bergbau: %d %s, Start bei %d %d %d", menge, rohstoff.anzeige(),
                start.getBlockX(), start.getBlockY(), start.getBlockZ()));
    }

    public Rohstoff rohstoff() {
        return rohstoff;
    }

    public int gesammelt() {
        return gesammelt;
    }

    /** Was er unterwegs eingepackt hat (Erz und andere Funde, kein Schutt). */
    public List<ItemStack> beute() {
        return beute;
    }

    public String status() {
        return switch (phase) {
            case RAUS -> "geht hinaus";
            case SUCHEN -> "sucht " + rohstoff.anzeige();
            case TUNNEL -> "gräbt einen Tunnel (" + gesammelt + "/" + menge + ")";
            case ABBAUEN -> "baut " + rohstoff.anzeige() + " ab (" + gesammelt + "/" + menge + ")";
            case ZURUECK -> "kommt zurück (" + gesammelt + " " + rohstoff.anzeige() + ")";
        };
    }

    /** Ein Tick Arbeit. */
    public Stand tick() {
        alter++;
        if (alter > HOECHSTENS && phase != Phase.ZURUECK) {
            zurueck();                                      // nach 8 Minuten mit dem, was er hat
        }
        switch (phase) {
            case RAUS -> {
                if (gehen(start, 2.0)) {
                    phase(Phase.SUCHEN);
                    sx = -SUCHRADIUS;
                    sz = -SUCHRADIUS;
                    sy = start.getBlockY() + 8;
                    kandidaten = new ArrayList<>();
                }
            }
            case SUCHEN -> {
                if (suchen()) {
                    Location hier = koerper.getLocation();
                    kandidaten.sort(Comparator.comparingDouble(b -> b.getLocation().distanceSquared(hier)));
                    if (kandidaten.size() > 400) {
                        kandidaten = new ArrayList<>(kandidaten.subList(0, 400));
                    }
                    plugin.getLogger().info("Bergbau: " + kandidaten.size() + " Vorkommen " + rohstoff.anzeige() + " in "
                            + SUCHRADIUS + " Blöcken");
                    if (kandidaten.isEmpty()) {
                        return Stand.NICHTS;
                    }
                    naechstesZiel();
                }
            }
            case TUNNEL -> tunnel();
            case ABBAUEN -> abbauen();
            case ZURUECK -> {
                if (schritt == null || gehen(schritt, 0.5)) {
                    schritt = weg.pollLast();
                    if (weg.size() % 20 == 0 && schritt != null) {
                        plugin.getLogger().info("Bergbau: Rückweg, noch " + weg.size() + " Schritte");
                    }
                    if (schritt == null) {
                        return gehen(start, 2.0) ? Stand.FERTIG : Stand.LAEUFT;
                    }
                }
            }
        }
        if (ziel != null && (phase == Phase.TUNNEL || phase == Phase.ABBAUEN) && ++zielTicks > 20 * 90) {
            sperren();                                      // hängt zu lange an einem Ziel: ein anderes nehmen
        }
        return Stand.LAEUFT;
    }

    private void zurueck() {
        arbeitAufhoeren();
        offen.clear();
        schritt = null;
        phase(Phase.ZURUECK);
    }

    // ------------------------------------------------------------------
    // Suche: Säulen um den Startpunkt, in Häppchen je Tick
    // ------------------------------------------------------------------

    private boolean suchen() {
        World w = start.getWorld();
        int geprueft = 0, unten = w.getMinHeight();
        while (geprueft < JE_TICK) {
            if (sx > SUCHRADIUS) {
                return true;
            }
            int x = start.getBlockX() + sx, z = start.getBlockZ() + sz;
            if (!w.isChunkLoaded(x >> 4, z >> 4)) {
                naechsteSaeule();
                continue;
            }
            Block b = w.getBlockAt(x, sy, z);
            if (rohstoff.bloecke().contains(b.getType()) && darfAbbauen(b)) {
                kandidaten.add(b);
            }
            geprueft++;
            if (--sy < unten) {
                naechsteSaeule();
            }
        }
        return false;
    }

    private void naechsteSaeule() {
        sy = start.getBlockY() + 8;
        if (++sz > SUCHRADIUS) {
            sz = -SUCHRADIUS;
            sx++;
        }
    }

    private void naechstesZiel() {
        arbeitAufhoeren();
        offen.clear();
        schritt = null;
        zielTicks = 0;
        if (gesammelt >= menge) {
            zurueck();
            return;
        }
        while (!ader.isEmpty()) {
            Block b = ader.poll();
            if (rohstoff.bloecke().contains(b.getType())) {
                ziel = b;
                phase(nah(b) ? Phase.ABBAUEN : Phase.TUNNEL);
                return;
            }
        }
        Location hier = koerper.getLocation();
        kandidaten.sort(Comparator.comparingDouble(b -> b.getLocation().distanceSquared(hier)));
        while (!kandidaten.isEmpty()) {
            Block b = kandidaten.removeFirst();
            if (!gesperrt.contains(b) && rohstoff.bloecke().contains(b.getType())) {
                ziel = b;
                phase(Phase.TUNNEL);
                return;
            }
        }
        // Nichts mehr da: mit dem zurück, was er hat
        ziel = null;
        zurueck();
    }

    private void sperren() {
        if (ziel != null) {
            gesperrt.add(ziel);
        }
        naechstesZiel();
    }

    // ------------------------------------------------------------------
    // Tunnel: Treppe (zwei Blöcke hoch) zum Ziel
    // ------------------------------------------------------------------

    private void tunnel() {
        // 1. Blöcke des aktuellen Schritts wegräumen
        while (!offen.isEmpty()) {
            Block b = offen.peekFirst();
            if (!imWeg(b)) {
                offen.pollFirst();
                continue;
            }
            if (!darfAbbauen(b)) {
                sperren();
                return;
            }
            if (abbauTick(b)) {
                offen.pollFirst();
            }
            return;
        }
        // 2. In das freie Stück treten
        if (schritt != null) {
            if (!gehen(schritt, 0.35)) {
                return;
            }
            weg.add(schritt);
            schritt = null;
        }
        // 3. Nah genug: abbauen
        if (nah(ziel)) {
            phase(Phase.ABBAUEN);
            return;
        }
        // 4. Nächsten Schritt planen (waagerecht zum Ziel, dazu eine Stufe hoch oder runter)
        Block fuss = koerper.getLocation().getBlock();
        int hx = ziel.getX() - fuss.getX(), hz = ziel.getZ() - fuss.getZ(), dy = ziel.getY() - fuss.getY();
        int rx = 0, rz = 0;
        if (Math.abs(hx) >= Math.abs(hz) && hx != 0) {
            rx = Integer.signum(hx);
        } else if (hz != 0) {
            rz = Integer.signum(hz);
        } else {
            letzteDx = -letzteDx;                           // genau darüber oder darunter: Zickzack
            letzteDz = -letzteDz;
            rx = letzteDx;
            rz = letzteDz;
        }
        letzteDx = rx;
        letzteDz = rz;
        int ry = dy < 0 ? -1 : (dy > 1 ? 1 : 0);
        Block neu = fuss.getRelative(rx, ry, rz);
        if (ry < 0) {
            frei(neu.getRelative(0, 2, 0));
        } else if (ry > 0) {
            frei(fuss.getRelative(0, 2, 0));
        }
        frei(neu.getRelative(0, 1, 0));
        frei(neu);
        schritt = neu.getLocation().add(0.5, 0, 0.5);
        schrittTicks = 0;
    }

    private void frei(Block b) {
        if (imWeg(b)) {
            offen.add(b);
        }
    }

    private static boolean imWeg(Block b) {
        return !b.isPassable();
    }

    private boolean nah(Block b) {
        return koerper.getEyeLocation().distanceSquared(b.getLocation().add(0.5, 0.5, 0.5)) <= 16.0;
    }

    // ------------------------------------------------------------------
    // Abbau
    // ------------------------------------------------------------------

    private void abbauen() {
        if (!rohstoff.bloecke().contains(ziel.getType())) {
            naechstesZiel();
            return;
        }
        if (!nah(ziel)) {
            phase(Phase.TUNNEL);
            return;
        }
        // Steht etwas zwischen Auge und Ziel, kommt das zuerst weg
        Block block = ziel;
        Location auge = koerper.getEyeLocation();
        Vector richtung = ziel.getLocation().add(0.5, 0.5, 0.5).toVector().subtract(auge.toVector());
        RayTraceResult t = auge.getWorld().rayTraceBlocks(auge, richtung, richtung.length() + 0.5,
                org.bukkit.FluidCollisionMode.NEVER, true);
        if (t != null && t.getHitBlock() != null && !t.getHitBlock().equals(ziel)) {
            block = t.getHitBlock();
            if (!darfAbbauen(block)) {
                sperren();
                return;
            }
        }
        if (abbauTick(block) && block.equals(ziel)) {
            aderMerken(ziel);
            naechstesZiel();
        }
    }

    /** Ein Tick an einem Block; true, wenn er weg ist. */
    private boolean abbauTick(Block b) {
        if (!b.equals(arbeit)) {
            arbeitAufhoeren();
            arbeit = b;
            fortschritt = 0;
            dauer = dauer(b);
            abdichten(b);
        }
        Location blick = koerper.getLocation();
        blick.setDirection(b.getLocation().add(0.5, 0.5, 0.5).toVector().subtract(koerper.getEyeLocation().toVector()));
        koerper.setRotation(blick.getYaw(), blick.getPitch());
        fortschritt++;
        if (fortschritt % 5 == 1) {
            figur.swingMainHand();
        }
        risse(b, Math.min(0.95F, (float) fortschritt / Math.max(1, dauer)));
        if (fortschritt < dauer) {
            return false;
        }
        // Fertig: Ertrag einpacken (Schutt bleibt liegen), Block entfernen
        risse(b, 0.0F);
        for (ItemStack s : b.getDrops(ItemStack.of(rohstoff.werkzeug()))) {
            if (rohstoff.ertrag().contains(s.getType())) {
                gesammelt += s.getAmount();
                beute.add(s);
            } else if (!s.getType().isBlock()) {
                beute.add(s);                               // Funde wie Feuerstein behält er
            }
        }
        World w = b.getWorld();
        w.playSound(b.getLocation(), b.getBlockData().getSoundGroup().getBreakSound(), SoundCategory.BLOCKS, 1.0F, 0.8F);
        w.spawnParticle(Particle.BLOCK, b.getLocation().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0.1, b.getBlockData());
        b.setType(Material.AIR);
        arbeit = null;
        return true;
    }

    private void arbeitAufhoeren() {
        if (arbeit != null) {
            risse(arbeit, 0.0F);
            arbeit = null;
        }
    }

    /** Risse im Block für alle Spieler in der Nähe (wie beim Abbauen eines Spielers). */
    private void risse(Block b, float stand) {
        Location l = b.getLocation();
        for (Player p : b.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(l) < 48 * 48) {
                p.sendBlockDamage(l, stand, koerper.getEntityId());
            }
        }
    }

    /** Abbaudauer in Ticks wie bei einem Spieler mit diesem Werkzeug. */
    private int dauer(Block b) {
        float haerte = b.getType().getHardness();
        if (haerte <= 0) {
            return 2;
        }
        float tempo = 1.0F;
        if (Tag.MINEABLE_PICKAXE.isTagged(b.getType())) {
            tempo = switch (rohstoff.werkzeug()) {
                case DIAMOND_PICKAXE -> 8.0F;
                case IRON_PICKAXE -> 6.0F;
                default -> 4.0F;
            };
        } else if (Tag.MINEABLE_SHOVEL.isTagged(b.getType())) {
            tempo = 4.0F;
        }
        return Math.max(2, (int) Math.ceil(30.0 * haerte / tempo));
    }

    /** Verschließt Wasser und Lava rund um den Block, bevor er geöffnet wird (nicht dort, wo der Körper steht). */
    private void abdichten(Block b) {
        Block fuss = koerper.getLocation().getBlock(), kopf = fuss.getRelative(BlockFace.UP);
        for (BlockFace f : new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block n = b.getRelative(f);
            if (n.equals(fuss) || n.equals(kopf)) {
                continue;
            }
            if ((n.getType() == Material.WATER || n.getType() == Material.LAVA) && plugin.villen().bei(n.getLocation(), 0) == null) {
                n.setType(Material.COBBLESTONE);
            }
        }
    }

    /** Merkt sich benachbarte Blöcke derselben Ader (werden zuerst abgebaut). */
    private void aderMerken(Block mitte) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block b = mitte.getRelative(dx, dy, dz);
                    if (rohstoff.bloecke().contains(b.getType()) && !ader.contains(b) && !gesperrt.contains(b) && darfAbbauen(b)) {
                        ader.add(b);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Gehen und Schutz
    // ------------------------------------------------------------------

    /** Läuft zu einer Stelle; true, wenn er da ist. Kommt er nicht voran, tritt er hinein (der Weg ist frei geräumt). */
    private boolean gehen(Location ziel, double genau) {
        Location hier = koerper.getLocation();
        double dx = hier.getX() - ziel.getX(), dz = hier.getZ() - ziel.getZ();
        if (dx * dx + dz * dz < genau * genau && Math.abs(hier.getY() - ziel.getY()) < 1.2) {
            schrittTicks = 0;
            koerper.getPathfinder().stopPathfinding();      // sonst läuft er den alten Weg weiter
            return true;
        }
        if (schrittTicks % 10 == 0) {
            koerper.getPathfinder().moveTo(ziel, 1.0);
        }
        if (++schrittTicks > (genau > 1 ? 160 : 30)) {
            if (genau > 1) {
                plugin.getLogger().info(String.format(Locale.ROOT, "Bergbau: kommt nicht voran, springt nach %d %d %d",
                        ziel.getBlockX(), ziel.getBlockY(), ziel.getBlockZ()));
            }
            Location l = ziel.clone();
            l.setYaw(hier.getYaw());
            koerper.getPathfinder().stopPathfinding();
            koerper.teleport(l);
            schrittTicks = 0;
            return true;
        }
        return false;
    }

    /** Natürliche Blöcke außerhalb jeder Villa (keine Spielerbauten, nichts von der Villa). */
    private boolean darfAbbauen(Block b) {
        Material m = b.getType();
        if (plugin.villen().bei(b.getLocation(), 2) != null) {
            return false;
        }
        return m.name().endsWith("_ORE") || Tag.BASE_STONE_OVERWORLD.isTagged(m) || Tag.DIRT.isTagged(m)
                || Tag.SAND.isTagged(m) || Tag.TERRACOTTA.isTagged(m) || Tag.SNOW.isTagged(m) || Tag.LEAVES.isTagged(m)
                || Tag.REPLACEABLE.isTagged(m) || switch (m) {
            case GRAVEL, CLAY, GRASS_BLOCK, PODZOL, MYCELIUM, ROOTED_DIRT, MUD, MOSS_BLOCK, CALCITE, DRIPSTONE_BLOCK,
                 POINTED_DRIPSTONE, SMOOTH_BASALT, SANDSTONE, RED_SANDSTONE, ICE, PACKED_ICE, SNOW_BLOCK, RAW_IRON_BLOCK,
                 RAW_COPPER_BLOCK, OBSIDIAN, MAGMA_BLOCK, GLOW_LICHEN, SCULK, SCULK_VEIN, AMETHYST_BLOCK, BUDDING_AMETHYST,
                 COBBLED_DEEPSLATE -> true;
            default -> false;
        };
    }

    /** Bricht ab (z. B. neuer Befehl): Risse weg, Werkzeug weg. */
    public void abbrechen() {
        arbeitAufhoeren();
        figur.getEquipment().setItemInMainHand(null);
    }

    /** Werkzeug wieder weg (am Ende). */
    public void aufraeumen() {
        figur.getEquipment().setItemInMainHand(null);
    }
}
