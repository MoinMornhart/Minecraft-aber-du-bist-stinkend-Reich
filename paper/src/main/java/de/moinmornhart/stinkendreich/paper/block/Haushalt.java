package de.moinmornhart.stinkendreich.paper.block;

import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import de.moinmornhart.stinkendreich.paper.villa.Villen;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Die kleinen Mechanismen der Villa wie in der Mod: Türklingel, Kaminschalter (Lagerfeuer bis 7 Blöcke), Whirlpool (sprudelt und dampft, taut
 * Eis auf), goldene Toilette (spülen), Regendusche mit Armatur (läuft 60 Sekunden) und Nachttisch mit Schublade.
 */
public final class Haushalt implements Listener {
    private static final int DUSCHE_LAUFZEIT = 60 * 20, ARMATUR_REICHWEITE = 4;

    private final StinkendReichPaper plugin;
    private final VirtuelleBloecke bloecke;
    private final NamespacedKey schluesselSchublade, schluesselWhirlpoolAus;
    /** Laufende Duschen: Kopf → Rest-Ticks. */
    private final Map<Block, Integer> duschen = new HashMap<>();
    private final Random zufall = new Random();
    private long tick;

    /** Die geöffnete Schublade (merkt sich ihren Block). */
    private static final class Schublade implements InventoryHolder {
        final Block block;
        Inventory inventar;

        Schublade(Block block) {
            this.block = block;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventar;
        }
    }

    public Haushalt(StinkendReichPaper plugin, VirtuelleBloecke bloecke, Mechanik mechanik) {
        this.plugin = plugin;
        this.bloecke = bloecke;
        this.schluesselSchublade = new NamespacedKey(plugin, "schublade");
        this.schluesselWhirlpoolAus = new NamespacedKey(plugin, "whirlpool_aus");
        mechanik.anmelden("tuerklingel", (p, b, z) -> {
            klingeln(p, b);
            return true;
        });
        mechanik.anmelden("kaminsteuerung", (p, b, z) -> {
            kamin(b);
            return true;
        });
        mechanik.anmelden("goldtoilette", (p, b, z) -> {
            spuelen(b);
            return true;
        });
        mechanik.anmelden("duscharmatur", (p, b, z) -> {
            b.getWorld().playSound(b.getLocation(), Sound.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.5F, 1.0F);
            Block kopf = naechsterKopf(b);
            if (kopf == null) {
                p.sendActionBar(Component.text("Kein Duschkopf in der Nähe.", NamedTextColor.GRAY));
            } else {
                Zustand k = bloecke.zustand(kopf);
                duscheSchalten(kopf, k == null || !k.ist("an", "true"));
            }
            return true;
        });
        mechanik.anmelden("whirlpoolduese", (p, b, z) -> {
            ItemDisplay f = bloecke.figur(b);
            if (f != null) {
                boolean aus = !f.getPersistentDataContainer().has(schluesselWhirlpoolAus);
                if (aus) {
                    f.getPersistentDataContainer().set(schluesselWhirlpoolAus, PersistentDataType.BYTE, (byte) 1);
                } else {
                    f.getPersistentDataContainer().remove(schluesselWhirlpoolAus);
                }
                p.sendActionBar(Component.text(aus ? "Whirlpool aus" : "Whirlpool an", NamedTextColor.AQUA));
            }
            return true;
        });
        mechanik.anmelden("nachttisch", (p, b, z) -> {
            schubladeOeffnen(p, b);
            return true;
        });
        Bukkit.getScheduler().runTaskTimer(plugin, this::takt, 2L, 2L);
    }

    // ------------------------------------------------------------------
    // Türklingel, Kamin, Toilette
    // ------------------------------------------------------------------

    /** Gong vor Ort und bei allen Bewohnern, dazu eine Nachricht. */
    private void klingeln(Player p, Block b) {
        b.getWorld().playSound(b.getLocation(), "stinkendreich:klingel", SoundCategory.BLOCKS, 1.5F, 1.0F);
        Villen.Villa v = plugin.villen().bei(b.getLocation(), 16);
        for (Player q : Bukkit.getOnlinePlayers()) {
            if (plugin.zugang().istBewohner(q, v) && q != p) {
                q.playSound(q.getLocation(), "stinkendreich:klingel", SoundCategory.BLOCKS, 0.8F, 1.0F);
                q.sendMessage(Component.text("Es klingelt am Tor: " + p.getName(), NamedTextColor.YELLOW));
            }
        }
        plugin.getLogger().info("Türklingel: " + p.getName());
    }

    /** Zündet alle Lagerfeuer im Umkreis von 7 Blöcken an oder löscht sie. */
    private static void kamin(Block b) {
        Boolean an = null;
        for (int dx = -7; dx <= 7; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -7; dz <= 7; dz++) {
                    Block q = b.getRelative(dx, dy, dz);
                    if ((q.getType() == Material.CAMPFIRE || q.getType() == Material.SOUL_CAMPFIRE) && q.getBlockData() instanceof Lightable feuer) {
                        if (an == null) {
                            an = !feuer.isLit();
                        }
                        feuer.setLit(an);
                        q.setBlockData(feuer);
                    }
                }
            }
        }
        b.getWorld().playSound(b.getLocation(), an != null && an ? Sound.ITEM_FIRECHARGE_USE : Sound.BLOCK_FIRE_EXTINGUISH,
                SoundCategory.BLOCKS, 0.6F, 1.0F);
    }

    /** Rauschen und ein Strudel aus Wasserspritzern im Becken. */
    private static void spuelen(Block b) {
        World w = b.getWorld();
        w.playSound(b.getLocation(), "stinkendreich:toilette", SoundCategory.BLOCKS, 1.0F, 1.0F);
        for (int i = 0; i < 16; i++) {
            double wi = i * Math.PI / 8;
            w.spawnParticle(Particle.SPLASH, b.getX() + 0.5 + Math.cos(wi) * 0.22, b.getY() + 0.5, b.getZ() + 0.45 + Math.sin(wi) * 0.22,
                    2, 0.02, 0.02, 0.02, 0.0);
        }
        w.spawnParticle(Particle.BUBBLE_POP, b.getX() + 0.5, b.getY() + 0.45, b.getZ() + 0.45, 6, 0.1, 0.02, 0.1, 0.0);
    }

    // ------------------------------------------------------------------
    // Dusche
    // ------------------------------------------------------------------

    private Block naechsterKopf(Block armatur) {
        Block best = null;
        double abstand = Double.MAX_VALUE;
        for (int dx = -ARMATUR_REICHWEITE; dx <= ARMATUR_REICHWEITE; dx++) {
            for (int dy = -ARMATUR_REICHWEITE; dy <= ARMATUR_REICHWEITE; dy++) {
                for (int dz = -ARMATUR_REICHWEITE; dz <= ARMATUR_REICHWEITE; dz++) {
                    Block q = armatur.getRelative(dx, dy, dz);
                    Zustand z = bloecke.zustand(q);
                    if (z != null && z.name().equals("duschkopf") && dx * dx + dy * dy + dz * dz < abstand) {
                        abstand = dx * dx + dy * dy + dz * dz;
                        best = q;
                    }
                }
            }
        }
        return best;
    }

    /** Dreht die Dusche an oder ab und stellt die Griffe der Armaturen in der Nähe passend. */
    private void duscheSchalten(Block kopf, boolean an) {
        Zustand z = bloecke.zustand(kopf);
        if (z == null) {
            return;
        }
        if (!z.ist("an", Boolean.toString(an))) {
            bloecke.setzen(kopf, z.mit("an", Boolean.toString(an)));
        }
        if (an) {
            duschen.put(kopf, DUSCHE_LAUFZEIT);
            kopf.getWorld().playSound(kopf.getLocation(), "stinkendreich:dusche", SoundCategory.BLOCKS, 0.8F, 1.0F);
        } else {
            duschen.remove(kopf);
        }
        for (int dx = -ARMATUR_REICHWEITE; dx <= ARMATUR_REICHWEITE; dx++) {
            for (int dy = -ARMATUR_REICHWEITE; dy <= ARMATUR_REICHWEITE; dy++) {
                for (int dz = -ARMATUR_REICHWEITE; dz <= ARMATUR_REICHWEITE; dz++) {
                    Block q = kopf.getRelative(dx, dy, dz);
                    Zustand a = bloecke.zustand(q);
                    if (a != null && a.name().equals("duscharmatur") && !a.ist("an", Boolean.toString(an)) && kopf.equals(naechsterKopf(q))) {
                        bloecke.setzen(q, a.mit("an", Boolean.toString(an)));
                    }
                }
            }
        }
    }

    /** Regen unter dem Kopf bis zum Boden, Rauschen, wer darunter steht, wird nass (Feuer geht aus). */
    private void duschen() {
        for (Map.Entry<Block, Integer> e : Map.copyOf(duschen).entrySet()) {
            Block kopf = e.getKey();
            int rest = e.getValue() - 2;
            if (rest <= 0 || bloecke.zustand(kopf) == null) {
                duscheSchalten(kopf, false);
                kopf.getWorld().playSound(kopf.getLocation(), Sound.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.4F, 0.8F);
                continue;
            }
            duschen.put(kopf, rest);
            World w = kopf.getWorld();
            double oben = kopf.getY() + 10.0 / 16.0 - 0.03, mx = kopf.getX() + 0.5, mz = kopf.getZ() + 0.5;
            int boden = kopf.getY() - 1;
            while (boden > kopf.getY() - 8 && w.getBlockAt(kopf.getX(), boden, kopf.getZ()).isPassable()) {
                boden--;
            }
            for (int i = 0; i < 7; i++) {
                w.spawnParticle(Particle.FALLING_WATER, mx + (zufall.nextDouble() * 2 - 1) * 0.4, oben, mz + (zufall.nextDouble() * 2 - 1) * 0.4,
                        1, 0, 0, 0, 0);
            }
            w.spawnParticle(Particle.SPLASH, mx, boden + 1.05, mz, 4, 0.3, 0.0, 0.3, 0.0);
            if ((DUSCHE_LAUFZEIT - rest) % 40 == 0) {
                w.playSound(kopf.getLocation(), "stinkendreich:dusche", SoundCategory.BLOCKS, 0.8F, 1.0F);
            }
            for (Entity ent : w.getNearbyEntities(new Location(w, mx, (oben + boden + 1) / 2, mz), 0.6, (oben - boden) / 2, 0.6)) {
                if (ent.getFireTicks() > 0) {
                    ent.setFireTicks(0);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Whirlpool
    // ------------------------------------------------------------------

    private void whirlpool(Block d) {
        World w = d.getWorld();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = 1; dy <= 2; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    Block q = d.getRelative(dx, dy, dz);
                    if (q.getType() == Material.ICE || q.getType() == Material.FROSTED_ICE) {
                        q.setType(Material.WATER);
                    } else if (q.getType() == Material.SNOW && q.getRelative(BlockFace.DOWN).getType() == Material.WATER) {
                        q.setType(Material.AIR);
                    }
                }
            }
        }
        w.spawnParticle(Particle.CLOUD, d.getX() + 0.5, d.getY() + 2.1, d.getZ() + 0.5, 5, 2.2, 0.05, 1.8, 0.015);
        ItemDisplay f = bloecke.figur(d);
        if (f != null && f.getPersistentDataContainer().has(schluesselWhirlpoolAus)) {
            return;
        }
        w.spawnParticle(Particle.BUBBLE, d.getX() + 0.5, d.getY() + 1.1, d.getZ() + 0.5, 12, 1.2, 0.2, 1.2, 0.05);
        w.spawnParticle(Particle.BUBBLE_POP, d.getX() + 0.5, d.getY() + 2.0, d.getZ() + 0.5, 6, 1.2, 0.1, 1.2, 0.02);
    }

    // ------------------------------------------------------------------
    // Nachttisch mit Schublade
    // ------------------------------------------------------------------

    private void schubladeOeffnen(Player p, Block b) {
        ItemDisplay f = bloecke.figur(b);
        if (f == null) {
            return;
        }
        Schublade s = new Schublade(b);
        s.inventar = Bukkit.createInventory(s, 9, Component.text("Schublade"));
        byte[] inhalt = f.getPersistentDataContainer().get(schluesselSchublade, PersistentDataType.BYTE_ARRAY);
        if (inhalt != null) {
            ItemStack[] dinge = ItemStack.deserializeItemsFromBytes(inhalt);
            for (int i = 0; i < Math.min(9, dinge.length); i++) {
                s.inventar.setItem(i, dinge[i]);
            }
        }
        b.getWorld().playSound(b.getLocation(), Sound.BLOCK_BARREL_OPEN, SoundCategory.BLOCKS, 0.6F, 1.4F);
        p.openInventory(s.inventar);
    }

    @EventHandler
    public void beimSchliessen(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof Schublade s)) {
            return;
        }
        ItemDisplay f = bloecke.figur(s.block);
        if (f != null) {
            ItemStack[] dinge = new ItemStack[9];
            for (int i = 0; i < 9; i++) {
                ItemStack it = s.inventar.getItem(i);
                dinge[i] = it == null ? ItemStack.empty() : it;
            }
            f.getPersistentDataContainer().set(schluesselSchublade, PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(dinge));
        }
        s.block.getWorld().playSound(s.block.getLocation(), Sound.BLOCK_BARREL_CLOSE, SoundCategory.BLOCKS, 0.6F, 1.4F);
    }

    // ------------------------------------------------------------------

    private void takt() {
        tick += 2;
        duschen();
        if (tick % 4 == 0) {
            for (Block d : bloecke.alle("whirlpoolduese")) {
                whirlpool(d);
            }
        }
    }
}
