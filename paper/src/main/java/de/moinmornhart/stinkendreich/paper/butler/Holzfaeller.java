package de.moinmornhart.stinkendreich.paper.butler;

import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * James fällt Bäume (wie {@code HolzAuftrag} in der Mod): vor die Villa gehen, natürliche Bäume suchen (Stamm auf Erde,
 * daneben echtes Laub, das von selbst zerfällt), Stamm für Stamm mit der Axt fällen und an derselben Stelle einen
 * Setzling derselben Holzart pflanzen. Gebautes Holz (ohne natürliches Laub) und alles in einer Villa bleibt stehen.
 */
final class Holzfaeller implements Beschaffung {
    /** Holz als „Rohstoff“ für Lieferung und Anzeige (Stämme aller Holzarten). */
    static final Bergbau.Rohstoff HOLZ = new Bergbau.Rohstoff("holz", "Holz", Tag.LOGS.getValues(), Tag.LOGS.getValues(),
            Material.IRON_AXE, null);

    private enum Phase { RAUS, SUCHEN, HIN, FAELLEN, PFLANZEN }

    private static final int SUCHRADIUS = 32, JE_TICK = 20000, HOECHSTENS = 20 * 60 * 6;

    private final StinkendReichPaper plugin;
    private final Mob koerper;
    private final LivingEntity figur;
    private final int menge;
    private final Location start;
    private final Laufen laufen;
    private final List<ItemStack> beute = new ArrayList<>();
    private int gesammelt, alter, faellTicks;
    private Phase phase = Phase.RAUS;
    // Suche: Säulen um den Startpunkt, in Häppchen je Tick
    private int sx, sz, sy;
    private final Set<Block> gefunden = new HashSet<>();
    private final List<Block> baeume = new ArrayList<>();
    private final Set<Block> gesperrt = new HashSet<>();
    // Aktueller Baum
    private Block fuss;
    private Material holzArt;
    private final Deque<Block> stamm = new ArrayDeque<>();
    private Block arbeit;
    private int fortschritt, dauer;

    Holzfaeller(StinkendReichPaper plugin, Mob koerper, LivingEntity figur, int menge, Location start) {
        this.plugin = plugin;
        this.koerper = koerper;
        this.figur = figur;
        this.menge = menge;
        this.start = start.clone();
        this.laufen = new Laufen(koerper);
        figur.getEquipment().setItemInMainHand(ItemStack.of(Material.IRON_AXE));
        plugin.getLogger().info(String.format(Locale.ROOT, "Holz: %d Stämme, Start bei %d %d %d", menge, start.getBlockX(),
                start.getBlockY(), start.getBlockZ()));
    }

    private void phase(Phase neu) {
        if (neu != phase) {
            Location l = koerper.getLocation();
            plugin.getLogger().info(String.format(Locale.ROOT, "Holz: %s → %s bei %d %d %d (%d/%d)", phase, neu, l.getBlockX(),
                    l.getBlockY(), l.getBlockZ(), gesammelt, menge));
            phase = neu;
        }
    }

    @Override
    public Bergbau.Rohstoff rohstoff() {
        return HOLZ;
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
        return switch (phase) {
            case RAUS -> "geht hinaus in den Wald";
            case SUCHEN -> "sucht einen Baum";
            case HIN, FAELLEN, PFLANZEN -> "fällt Bäume (" + gesammelt + "/" + menge + " Holz)";
        };
    }

    @Override
    public Bergbau.Stand tick() {
        if (++alter > HOECHSTENS && phase != Phase.PFLANZEN) {
            return fertig();                                // nach 6 Minuten mit dem, was er hat
        }
        switch (phase) {
            case RAUS -> {
                if (laufen.zu(start, 2.0, 1.5, 20 * 20)) {
                    sucheBeginnen();
                }
            }
            case SUCHEN -> {
                if (suchen()) {
                    Location hier = koerper.getLocation();
                    baeume.sort(Comparator.comparingDouble(b -> b.getLocation().distanceSquared(hier)));
                    plugin.getLogger().info("Holz: " + baeume.size() + " Bäume in " + SUCHRADIUS + " Blöcken");
                    if (!naechsterBaum()) {
                        return gesammelt > 0 ? fertig() : Bergbau.Stand.NICHTS;
                    }
                }
            }
            case HIN -> {
                if (!Tag.LOGS.isTagged(fuss.getType())) {
                    if (!naechsterBaum()) {
                        sucheBeginnen();
                    }
                } else if (laufen.zu(standort(fuss), 1.0, 1.5, 20 * 15)) {
                    stammErfassen();
                    faellTicks = 0;
                    phase(Phase.FAELLEN);
                }
            }
            case FAELLEN -> faellen();
            case PFLANZEN -> {
                pflanzen();
                if (gesammelt >= menge) {
                    return fertig();
                }
                if (!naechsterBaum()) {
                    sucheBeginnen();
                }
            }
        }
        return Bergbau.Stand.LAEUFT;
    }

    private Bergbau.Stand fertig() {
        arbeitAufhoeren();
        plugin.getLogger().info("Holz: fertig mit " + gesammelt + " Stämmen");
        return Bergbau.Stand.FERTIG;
    }

    // ------------------------------------------------------------------
    // Suche
    // ------------------------------------------------------------------

    private void sucheBeginnen() {
        sx = -SUCHRADIUS;
        sz = -SUCHRADIUS;
        sy = start.getBlockY() + 12;
        gefunden.clear();
        baeume.clear();
        phase(Phase.SUCHEN);
    }

    /** Sucht Stämme in einer Schicht über und unter dem Startpunkt; true, wenn fertig. */
    private boolean suchen() {
        World w = start.getWorld();
        int geprueft = 0;
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
            if (Tag.LOGS.isTagged(b.getType())) {
                Block f = stammFuss(b);
                if (f != null && !gesperrt.contains(f) && gefunden.add(f) && istNatuerlicherBaum(f)) {
                    baeume.add(f);
                }
            }
            geprueft++;
            if (--sy < start.getBlockY() - 10) {
                naechsteSaeule();
            }
        }
        return false;
    }

    private void naechsteSaeule() {
        sy = start.getBlockY() + 12;
        if (++sz > SUCHRADIUS) {
            sz = -SUCHRADIUS;
            sx++;
        }
    }

    private boolean naechsterBaum() {
        Location hier = koerper.getLocation();
        baeume.sort(Comparator.comparingDouble(b -> b.getLocation().distanceSquared(hier)));
        while (!baeume.isEmpty()) {
            Block f = baeume.removeFirst();
            if (!gesperrt.contains(f) && Tag.LOGS.isTagged(f.getType())) {
                fuss = f;
                holzArt = f.getType();
                phase(Phase.HIN);
                return true;
            }
        }
        return false;
    }

    /** Freier Platz direkt neben dem Stamm (zwei Blöcke Luft, fester Boden), sonst auf dem Boden davor. */
    private static Location standort(Block fuss) {
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) {
            for (int dy = 0; dy >= -1; dy--) {
                Block b = fuss.getRelative(d[0], dy, d[1]);
                if (b.isPassable() && b.getRelative(BlockFace.UP).isPassable() && b.getRelative(BlockFace.DOWN).getType().isSolid()) {
                    return b.getLocation().add(0.5, 0, 0.5);
                }
            }
        }
        Block b = fuss.getRelative(1, 0, 0);
        return b.getWorld().getHighestBlockAt(b.getX(), b.getZ()).getLocation().add(0.5, 1, 0.5);
    }

    /** Unterster Stammblock (darunter Erde, Gras, Moos …), sonst null. */
    private static Block stammFuss(Block b) {
        int schritte = 0;
        while (Tag.LOGS.isTagged(b.getRelative(BlockFace.DOWN).getType()) && schritte++ < 32) {
            b = b.getRelative(BlockFace.DOWN);
        }
        Material unten = b.getRelative(BlockFace.DOWN).getType();
        return Tag.DIRT.isTagged(unten) || unten == Material.GRASS_BLOCK || unten == Material.PODZOL ? b : null;
    }

    /** Ein echter Baum: außerhalb jeder Villa und mit natürlichem Laub an der Krone. */
    private boolean istNatuerlicherBaum(Block fuss) {
        if (plugin.villen().bei(fuss.getLocation(), 4) != null) {
            return false;
        }
        Block oben = fuss;
        int hoehe = 0;
        while (Tag.LOGS.isTagged(oben.getRelative(BlockFace.UP).getType()) && hoehe++ < 32) {
            oben = oben.getRelative(BlockFace.UP);
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (oben.getRelative(dx, dy, dz).getBlockData() instanceof Leaves l && !l.isPersistent()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Fällen und Pflanzen
    // ------------------------------------------------------------------

    /** Alle zusammenhängenden Stammblöcke ab dem Fuß, von unten nach oben (auch schräge Äste). */
    private void stammErfassen() {
        stamm.clear();
        List<Block> alle = new ArrayList<>();
        Deque<Block> offen = new ArrayDeque<>();
        Set<Block> gesehen = new HashSet<>();
        offen.add(fuss);
        gesehen.add(fuss);
        while (!offen.isEmpty() && alle.size() < 200) {
            Block b = offen.poll();
            if (!Tag.LOGS.isTagged(b.getType()) || plugin.villen().bei(b.getLocation(), 2) != null) {
                continue;
            }
            alle.add(b);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block n = b.getRelative(dx, dy, dz);
                        if (gesehen.add(n)) {
                            offen.add(n);
                        }
                    }
                }
            }
        }
        alle.sort(Comparator.comparingInt(Block::getY));
        stamm.addAll(alle);
    }

    private void faellen() {
        if (stamm.isEmpty()) {
            arbeitAufhoeren();
            phase(Phase.PFLANZEN);
            return;
        }
        Block b = stamm.peekFirst();
        if (!Tag.LOGS.isTagged(b.getType())) {
            stamm.poll();
            return;
        }
        if (nah(b)) {
            if (abbauTick(b)) {
                stamm.poll();
            }
        } else if (++faellTicks % 3 == 0) {
            // Der Rest des Stamms ist außer Reichweite: der angesägte Baum fällt Stück für Stück
            umfallen(stamm.poll());
        }
    }

    /** Ein Stammstück fällt (ohne Hinsehen): Ertrag einpacken, Block weg. */
    private void umfallen(Block b) {
        if (Tag.LOGS.isTagged(b.getType())) {
            einpacken(b);
            Bergbau.zerbrechen(b);
        }
    }

    private boolean abbauTick(Block b) {
        if (!b.equals(arbeit)) {
            arbeitAufhoeren();
            arbeit = b;
            fortschritt = 0;
            // Eisenaxt: Stamm (Härte 2) in einer halben Sekunde, wie ein Spieler mit Effizienz
            dauer = Math.max(4, (int) Math.ceil(30.0 * b.getType().getHardness() / 6.0));
        }
        Location blick = koerper.getLocation();
        blick.setDirection(b.getLocation().add(0.5, 0.5, 0.5).toVector().subtract(koerper.getEyeLocation().toVector()));
        koerper.setRotation(blick.getYaw(), blick.getPitch());
        if (++fortschritt % 5 == 1) {
            figur.swingMainHand();
        }
        Bergbau.risse(b, Math.min(0.95F, (float) fortschritt / dauer), koerper.getEntityId());
        if (fortschritt < dauer) {
            return false;
        }
        Bergbau.risse(b, 0.0F, koerper.getEntityId());
        einpacken(b);
        Bergbau.zerbrechen(b);
        arbeit = null;
        return true;
    }

    private void einpacken(Block b) {
        for (ItemStack s : b.getDrops(ItemStack.of(Material.IRON_AXE))) {
            if (Tag.LOGS.isTagged(s.getType())) {
                gesammelt += s.getAmount();
            }
            beute.add(s);
        }
    }

    private void arbeitAufhoeren() {
        if (arbeit != null) {
            Bergbau.risse(arbeit, 0.0F, koerper.getEntityId());
            arbeit = null;
        }
    }

    private boolean nah(Block b) {
        return koerper.getEyeLocation().distanceSquared(b.getLocation().add(0.5, 0.5, 0.5)) <= 4.5 * 4.5;
    }

    /** Pflanzt an der alten Stelle einen Setzling derselben Holzart (Karmesin- und Wirrstiele haben keinen). */
    private void pflanzen() {
        if (fuss == null || holzArt == null || !fuss.getType().isAir()) {
            return;
        }
        String art = holzArt.name().replace("STRIPPED_", "").replace("_LOG", "").replace("_WOOD", "");
        Material setzling = Material.matchMaterial(art.equals("MANGROVE") ? "MANGROVE_PROPAGULE" : art + "_SAPLING");
        Material unten = fuss.getRelative(BlockFace.DOWN).getType();
        if (setzling != null && (Tag.DIRT.isTagged(unten) || unten == Material.GRASS_BLOCK || unten == Material.PODZOL)) {
            fuss.setType(setzling);
            figur.swingMainHand();
            fuss.getWorld().playSound(fuss.getLocation(), Sound.BLOCK_GRASS_PLACE, SoundCategory.BLOCKS, 1.0F, 1.0F);
        }
        fuss = null;
    }

    @Override
    public void abbrechen() {
        arbeitAufhoeren();
        figur.getEquipment().setItemInMainHand(null);
    }

    @Override
    public void aufraeumen() {
        figur.getEquipment().setItemInMainHand(null);
    }
}
