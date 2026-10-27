package de.moinmornhart.stinkendreich.paper.butler;

import com.google.gson.JsonArray;
import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import de.moinmornhart.stinkendreich.paper.villa.Ausstattung;
import de.moinmornhart.stinkendreich.paper.villa.Villen;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ein Auftrag für James auf dem Paper-Server („Butler, hol mir 16 Eisen“, „… 16 Holz“, „geh jagen“), wie Planer +
 * SammelAuftrag + OfenAuftrag + LieferAuftrag in der Mod: erst beschaffen ({@link Bergbau}, {@link Holzfaeller} oder
 * {@link Jaeger}), bei Eisen, Gold
 * und Kupfer danach in den echten Öfen der Villa schmelzen (Brennstoff aus dem Fass daneben), zuletzt dem Herrn bringen.
 */
final class ButlerAuftrag implements Auftrag {
    private enum Phase { ABBAU, ZUM_OFEN, SCHMELZEN, LIEFERN }

    private final StinkendReichPaper plugin;
    private final Villen.Villa villa;
    private final Mob koerper;
    private final Mannequin puppe;
    private final UUID herr;
    private final Beschaffung bergbau;
    private Phase phase = Phase.ABBAU;

    private void phase(Phase neu) {
        Location l = koerper.getLocation();
        plugin.getLogger().info(String.format(java.util.Locale.ROOT, "Butler-Auftrag: %s → %s bei %d %d %d, Fracht %d Stapel",
                phase, neu, l.getBlockX(), l.getBlockY(), l.getBlockZ(), fracht.size()));
        phase = neu;
    }
    private final List<ItemStack> fracht = new ArrayList<>();
    private final List<Block> oefen = new ArrayList<>();
    private int warten, steckt, unterwegs;
    private Location letzte;

    ButlerAuftrag(StinkendReichPaper plugin, Villen.Villa villa, Mob koerper, Mannequin puppe, UUID herr, Beschaffung beschaffung) {
        this.plugin = plugin;
        this.villa = villa;
        this.koerper = koerper;
        this.puppe = puppe;
        this.herr = herr;
        this.bergbau = beschaffung;
    }

    /**
     * Startpunkt vor der Villa: neben der gepflasterten Zufahrt (die gräbt er nicht auf) auf natürlichem Boden, ein paar
     * Blöcke außerhalb des Grundstücks.
     */
    static Location vorDerVilla(StinkendReichPaper plugin, Villen.Villa v) {
        World w = v.world();
        int z = v.z() + plugin.bauplan().breiteZ + 8;
        for (int dx = 5; dx <= 20; dx++) {
            int x = v.x() + Ausstattung.ZUFAHRT_X1 + dx;
            Block boden = w.getHighestBlockAt(x, z);
            if (org.bukkit.Tag.DIRT.isTagged(boden.getType()) || org.bukkit.Tag.BASE_STONE_OVERWORLD.isTagged(boden.getType())
                    || org.bukkit.Tag.SAND.isTagged(boden.getType()) || boden.getType() == Material.GRAVEL) {
                return new Location(w, x + 0.5, boden.getY() + 1, z + 0.5);
            }
        }
        int x = v.x() + Ausstattung.ZUFAHRT_X1 + 5;
        return new Location(w, x + 0.5, w.getHighestBlockYAt(x, z) + 1, z + 0.5);
    }

    @Override
    public String status() {
        return switch (phase) {
            case ABBAU -> bergbau.status();      // „gräbt einen Tunnel …“, „fällt Bäume …“
            case ZUM_OFEN, SCHMELZEN -> "schmilzt " + bergbau.rohstoff().anzeige() + " in der Villa";
            case LIEFERN -> "bringt Ihnen " + bergbau.rohstoff().anzeige();
        };
    }

    @Override
    public void abbrechen() {
        bergbau.abbrechen();
    }

    @Override
    public Stand tick() {
        switch (phase) {
            case ABBAU -> {
                Bergbau.Stand s = bergbau.tick();
                if (s == Bergbau.Stand.LAEUFT) {
                    return Stand.LAEUFT;
                }
                bergbau.aufraeumen();
                fracht.addAll(bergbau.beute());
                Player p = herr();
                if (s == Bergbau.Stand.NICHTS || bergbau.gesammelt() == 0) {
                    if (p != null) {
                        Butler.sagen(p, bergbau.rohstoff() == Holzfaeller.HOLZ
                                ? "Verzeihung, in der Nähe steht kein Baum, den ich fällen darf."
                                : bergbau.rohstoff() == Jaeger.FLEISCH
                                ? "Verzeihung, in der Nähe gibt es keine Tiere, die ich jagen darf."
                                : "Verzeihung, ich habe in der Nähe kein " + bergbau.rohstoff().anzeige() + " gefunden.");
                    }
                    phase(Phase.LIEFERN);
                } else if (bergbau.rohstoff().geschmolzen() != null && ofenSuchen()) {
                    phase(Phase.ZUM_OFEN);
                } else {
                    phase(Phase.LIEFERN);
                }
            }
            case ZUM_OFEN -> {
                Block ofen = oefen.getFirst();
                Block vor = ofen.getBlockData() instanceof org.bukkit.block.data.Directional d ? ofen.getRelative(d.getFacing()) : ofen.getRelative(0, 1, 0);
                if (gehen(vor.getLocation().add(0.5, 0, 0.5), 2.5)) {
                    if (einlegen()) {
                        phase(Phase.SCHMELZEN);
                        warten = 0;
                    } else {
                        phase(Phase.LIEFERN);      // kein Brennstoff: rohes Erz bringen
                    }
                }
            }
            case SCHMELZEN -> {
                if (++warten % 20 == 0) {
                    puppe.swingMainHand();
                }
                if (warten % 20 == 0 && fertigGeschmolzen() || warten > 20 * 60 * 4) {
                    entnehmen();
                    phase(Phase.LIEFERN);
                }
            }
            case LIEFERN -> {
                Player p = herr();
                if (p == null || !p.getWorld().equals(koerper.getWorld())) {
                    return Stand.LAEUFT;            // wartet, bis der Herr wieder da ist
                }
                if (fracht.isEmpty()) {
                    return Stand.FERTIG;
                }
                if (p.getLocation().distanceSquared(koerper.getLocation()) > 40 * 40) {
                    koerper.getPathfinder().stopPathfinding();
                    koerper.teleport(p.getLocation().clone().subtract(p.getLocation().getDirection().setY(0).normalize().multiply(1.5)));
                }
                if (gehen(p.getLocation(), 2.5)) {
                    uebergeben(p);
                    return Stand.FERTIG;
                }
            }
        }
        return Stand.LAEUFT;
    }

    private Player herr() {
        return herr == null ? null : Bukkit.getPlayer(herr);
    }

    private void uebergeben(Player p) {
        int anzahl = 0;
        for (ItemStack s : fracht) {
            if (s.getType() == bergbau.rohstoff().geschmolzen() || bergbau.rohstoff().ertrag().contains(s.getType())) {
                anzahl += s.getAmount();
            }
            for (ItemStack rest : p.getInventory().addItem(s).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), rest);
            }
        }
        fracht.clear();
        puppe.swingMainHand();
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.8F, 1.0F);
        Butler.sagen(p, anzahl > 0 ? "Bitte sehr: " + anzahl + " " + bergbau.rohstoff().anzeige() + "." : "Ich bin zurück.");
        plugin.getLogger().info("Butler: " + anzahl + " " + bergbau.rohstoff().anzeige() + " an " + p.getName() + " geliefert");
    }

    // ------------------------------------------------------------------
    // Öfen der Villa
    // ------------------------------------------------------------------

    /** Die Öfen der Villa (Marken „ofen“), in denen Erz schmilzt. */
    private boolean ofenSuchen() {
        oefen.clear();
        for (JsonArray m : plugin.bauplan().marke("ofen")) {
            Block b = Ausstattung.ort(villa, m).getBlock();
            // Erz schmelzen nur Ofen und Hochofen (der Räucherofen nimmt nur Essen)
            if (b.getType() == Material.FURNACE || b.getType() == Material.BLAST_FURNACE) {
                oefen.add(b);
            }
        }
        return !oefen.isEmpty();
    }

    /** Erz gleichmäßig auf die Öfen verteilen, Brennstoff aus dem Fass dazu (1 Kohle je 8 Stück). */
    private boolean einlegen() {
        List<ItemStack> erz = new ArrayList<>();
        int menge = 0;
        for (ItemStack s : fracht) {
            if (bergbau.rohstoff().ertrag().contains(s.getType())) {
                erz.add(s);
                menge += s.getAmount();
            }
        }
        if (menge == 0) {
            return false;
        }
        int je = (int) Math.ceil((double) menge / oefen.size());
        int brennstoff = 0;
        for (int i = 0; i < oefen.size() && menge > 0; i++) {
            brennstoff += (int) Math.ceil(Math.min(je, menge) / 8.0);
            menge -= Math.min(je, menge);
        }
        if (!brennstoffNehmen(brennstoff)) {
            return false;
        }
        fracht.removeAll(erz);
        Material roh = erz.getFirst().getType();
        int rest = erz.stream().mapToInt(ItemStack::getAmount).sum();
        for (Block b : oefen) {
            if (rest <= 0) {
                break;
            }
            int n = Math.min(je, rest);
            FurnaceInventory inv = ((Furnace) b.getState()).getInventory();
            inv.setSmelting(ItemStack.of(roh, n));
            inv.setFuel(ItemStack.of(Material.COAL, (int) Math.ceil(n / 8.0)));
            rest -= n;
        }
        koerper.getWorld().playSound(koerper.getLocation(), Sound.BLOCK_FURNACE_FIRE_CRACKLE, SoundCategory.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    /** Nimmt Kohle aus den Brennstoff-Fässern der Villa (Marken „brennstoff“). */
    private boolean brennstoffNehmen(int anzahl) {
        List<Inventory> faesser = new ArrayList<>();
        int da = 0;
        for (JsonArray m : plugin.bauplan().marke("brennstoff")) {
            if (Ausstattung.ort(villa, m).getBlock().getState() instanceof Container c) {
                faesser.add(c.getInventory());
                for (ItemStack s : c.getInventory().getContents()) {
                    if (s != null && (s.getType() == Material.COAL || s.getType() == Material.CHARCOAL)) {
                        da += s.getAmount();
                    }
                }
            }
        }
        if (da < anzahl) {
            return false;
        }
        for (Inventory inv : faesser) {
            for (Material m : new Material[]{Material.COAL, Material.CHARCOAL}) {
                if (anzahl > 0) {
                    // removeItem gibt zurück, was nicht da war
                    anzahl = inv.removeItem(ItemStack.of(m, anzahl)).values().stream().mapToInt(ItemStack::getAmount).sum();
                }
            }
        }
        return true;
    }

    private boolean fertigGeschmolzen() {
        for (Block b : oefen) {
            if (b.getState() instanceof Furnace f && f.getInventory().getSmelting() != null && !f.getInventory().getSmelting().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void entnehmen() {
        for (Block b : oefen) {
            if (b.getState() instanceof Furnace f) {
                ItemStack fertig = f.getInventory().getResult();
                if (fertig != null && !fertig.isEmpty()) {
                    fracht.add(fertig.clone());
                    f.getInventory().setResult(null);
                }
                ItemStack roh = f.getInventory().getSmelting();
                if (roh != null && !roh.isEmpty()) {
                    fracht.add(roh.clone());              // was nicht fertig wurde, kommt roh mit
                    f.getInventory().setSmelting(null);
                }
            }
        }
    }

    // ------------------------------------------------------------------

    private boolean gehen(Location ziel, double genau) {
        Location hier = koerper.getLocation();
        if (hier.getWorld().equals(ziel.getWorld()) && hier.distanceSquared(ziel) < genau * genau) {
            steckt = 0;
            unterwegs = 0;
            koerper.getPathfinder().stopPathfinding();
            return true;
        }
        // Höchstens 20 Sekunden für einen Weg (z. B. außen um die Mauer herum): dann direkt hin, er hat ja einen Schlüssel
        if (++unterwegs > 20 * 20) {
            koerper.getPathfinder().stopPathfinding();
            koerper.teleport(ziel);
            unterwegs = 0;
            return false;
        }
        if (Bukkit.getCurrentTick() % 10 == 0) {
            koerper.getPathfinder().moveTo(ziel, 1.0);
            if (letzte != null && letzte.getWorld().equals(hier.getWorld()) && letzte.distanceSquared(hier) < 0.04) {
                if (++steckt > 16) {                        // 8 Sekunden ohne Fortschritt (Tür der Mod): hinspringen
                    koerper.getPathfinder().stopPathfinding();
                    koerper.teleport(ziel);
                    steckt = 0;
                }
            } else {
                steckt = 0;
            }
            letzte = hier;
        }
        return false;
    }
}
