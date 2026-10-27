package de.moinmornhart.stinkendreich.paper.butler;

import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Lidded;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * „Butler, sortier die Truhen“ (wie {@code SortierAuftrag} in der Mod): James geht alle Truhen und Fässer im Umkreis
 * ab, öffnet jede, legt danach gleiche Gegenstände zusammen und verteilt alles sortiert (Baublöcke, Rohstoffe, Essen,
 * Werkzeug, Sonstiges). Jede Sorte kommt in die Truhe, in der vorher am meisten davon lag. Es geht nichts verloren:
 * Zusammengelegt braucht nie mehr Platz als vorher.
 */
final class Sortierer implements Auftrag {
    private static final int RADIUS = 8;

    private final StinkendReichPaper plugin;
    private final Mob koerper;
    private final Mannequin puppe;
    private final UUID herr;
    private final Laufen laufen;
    private final List<Block> truhen = new ArrayList<>();
    private final List<Block> besucht = new ArrayList<>();
    private int index, deckel = -1;

    Sortierer(StinkendReichPaper plugin, Mob koerper, Mannequin puppe, UUID herr, Location mitte) {
        this.plugin = plugin;
        this.koerper = koerper;
        this.puppe = puppe;
        this.herr = herr;
        this.laufen = new Laufen(koerper);
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    Block b = mitte.getBlock().getRelative(dx, dy, dz);
                    if (istTruhe(b.getType())) {
                        truhen.add(b);
                    }
                }
            }
        }
        truhen.sort(Comparator.comparingInt(Block::getY).thenComparingInt(Block::getX).thenComparingInt(Block::getZ));
        plugin.getLogger().info("Sortieren: " + truhen.size() + " Truhen um " + mitte.getBlockX() + " " + mitte.getBlockY() + " " + mitte.getBlockZ());
    }

    private static boolean istTruhe(Material m) {
        return m == Material.CHEST || m == Material.TRAPPED_CHEST || m == Material.BARREL;
    }

    boolean leer() {
        return truhen.isEmpty();
    }

    @Override
    public String status() {
        return "sortiert die Truhen (" + Math.min(index + 1, truhen.size()) + "/" + truhen.size() + ")";
    }

    @Override
    public Stand tick() {
        if (index >= truhen.size()) {
            int anzahl = sortieren();
            Player p = herr == null ? null : Bukkit.getPlayer(herr);
            if (p != null) {
                Butler.sagen(p, "Erledigt: " + besucht.size() + " Truhen aufgeräumt, " + anzahl + " Gegenstände sortiert.");
            }
            plugin.getLogger().info("Sortieren: " + besucht.size() + " Truhen, " + anzahl + " Gegenstände");
            return Stand.FERTIG;
        }
        Block t = truhen.get(index);
        if (deckel >= 0) {
            // Kurz hineinschauen, dann Deckel zu und zur nächsten
            Location blick = koerper.getLocation();
            blick.setDirection(t.getLocation().add(0.5, 0.5, 0.5).toVector().subtract(koerper.getEyeLocation().toVector()));
            koerper.setRotation(blick.getYaw(), blick.getPitch());
            if (++deckel > 10) {
                deckel(t, false);
                deckel = -1;
                index++;
            }
            return Stand.LAEUFT;
        }
        if (!istTruhe(t.getType())) {
            index++;                                        // inzwischen abgebaut
        } else if (laufen.zu(t.getLocation().add(0.5, 0, 0.5), 2.3, 2.0, 20 * 15)) {
            deckel(t, true);
            puppe.swingMainHand();
            besucht.add(t);
            deckel = 0;
        }
        return Stand.LAEUFT;
    }

    private static void deckel(Block t, boolean auf) {
        if (t.getState() instanceof Lidded l) {
            if (auf) {
                l.open();
            } else {
                l.close();
            }
        }
    }

    /** Inhalt einer Truhe (bei Doppeltruhen nur diese Hälfte, sonst zählte sie doppelt). */
    private static Inventory inhalt(Block b) {
        BlockState s = b.getState();
        if (s instanceof Chest c) {
            return c.getBlockInventory();
        }
        return s instanceof Barrel f ? f.getInventory() : null;
    }

    /** Sammelt alles ein, legt zusammen und verteilt sortiert; gibt die Anzahl der Gegenstände zurück. */
    private int sortieren() {
        List<Inventory> behaelter = new ArrayList<>();
        for (Block b : besucht) {
            Inventory inv = inhalt(b);
            if (inv != null) {
                behaelter.add(inv);
            }
        }
        int n = behaelter.size();
        List<ItemStack> alles = new ArrayList<>();
        Map<Material, int[]> mengeJeTruhe = new HashMap<>();
        for (int ci = 0; ci < n; ci++) {
            Inventory inv = behaelter.get(ci);
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s != null && !s.isEmpty()) {
                    mengeJeTruhe.computeIfAbsent(s.getType(), k -> new int[n])[ci] += s.getAmount();
                    zusammenlegen(alles, s.clone());
                }
                inv.setItem(i, null);
            }
        }
        alles.sort(Comparator.comparingInt(Sortierer::gruppe)
                .thenComparing(s -> s.getType().getKey().toString())
                .thenComparingInt(s -> -s.getAmount()));
        int anzahl = 0;
        int[] belegt = new int[n];
        List<ItemStack> uebrig = new ArrayList<>();
        for (ItemStack s : alles) {
            anzahl += s.getAmount();
            int heim = stammtruhe(mengeJeTruhe.get(s.getType()));
            if (belegt[heim] < behaelter.get(heim).getSize()) {
                behaelter.get(heim).setItem(belegt[heim]++, s);
            } else {
                uebrig.add(s);
            }
        }
        for (ItemStack s : uebrig) {
            int ci = 0;
            while (ci < n && belegt[ci] >= behaelter.get(ci).getSize()) {
                ci++;
            }
            if (ci >= n) {
                // kann nicht passieren (zusammengelegt braucht nie mehr Platz), zur Sicherheit aber nichts wegwerfen
                koerper.getWorld().dropItemNaturally(koerper.getLocation(), s);
                continue;
            }
            behaelter.get(ci).setItem(belegt[ci]++, s);
        }
        return anzahl;
    }

    /** Index der Truhe, in der vorher am meisten von einer Sorte lag (bei Gleichstand die erste). */
    private static int stammtruhe(int[] mengen) {
        int beste = 0;
        for (int i = 1; i < mengen.length; i++) {
            if (mengen[i] > mengen[beste]) {
                beste = i;
            }
        }
        return beste;
    }

    /** Legt einen Stapel auf passende angefangene Stapel, den Rest als neuen Stapel ab. */
    private static void zusammenlegen(List<ItemStack> liste, ItemStack neu) {
        for (ItemStack s : liste) {
            if (neu.getAmount() <= 0) {
                return;
            }
            if (s.isSimilar(neu) && s.getAmount() < s.getMaxStackSize()) {
                int dazu = Math.min(neu.getAmount(), s.getMaxStackSize() - s.getAmount());
                s.setAmount(s.getAmount() + dazu);
                neu.setAmount(neu.getAmount() - dazu);
            }
        }
        if (neu.getAmount() > 0) {
            liste.add(neu);
        }
    }

    /** Sortiergruppe: 0 Baublöcke, 1 Rohstoffe, 2 Essen, 3 Werkzeug/Rüstung, 4 Sonstiges. */
    private static int gruppe(ItemStack s) {
        Material m = s.getType();
        if (m.isEdible()) {
            return 2;
        }
        if (m.getMaxDurability() > 0) {
            return 3;
        }
        if (m.isBlock()) {
            return 0;
        }
        if (m.isFuel() || s.getMaxStackSize() == 64) {
            return 1;
        }
        return 4;
    }

    @Override
    public void abbrechen() {
        if (deckel >= 0 && index < truhen.size()) {
            deckel(truhen.get(index), false);
        }
    }
}
