package de.moinmornhart.stinkendreich.paper.block;

import com.google.gson.JsonArray;
import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import de.moinmornhart.stinkendreich.paper.villa.Villen;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lava-Graben wie in der Mod: Im Lockdown (Stufe 2 und 3) füllt sich der Wassergraben rund ums Grundstück mit Lava,
 * sonst ist Wasser darin. Getauscht wird in zwei Schritten (erst leeren, im nächsten Tick füllen), damit sich Wasser und
 * Lava nie berühren (das gäbe Obsidian). Blöcke, die jemand in den Graben gebaut hat, bleiben.
 */
public final class Graben {
    private static final int PRUEFEN_ALLE = 40;

    /** Graben je Villa: Felder des Rings, ob Lava drin ist, und ob er im nächsten Tick neu gefüllt wird. */
    private static final class Ring {
        List<Block> felder;
        Boolean istLava;
        Boolean fuellenMitLava;
    }

    private final StinkendReichPaper plugin;
    private final Map<Integer, Ring> ringe = new HashMap<>();
    private long tick;

    public Graben(StinkendReichPaper plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::takt, 1L, 1L);
    }

    private void takt() {
        tick++;
        for (Villen.Villa v : plugin.villen().alle()) {
            Ring r = ringe.computeIfAbsent(v.nr(), n -> new Ring());
            if (r.fuellenMitLava != null) {
                fuellen(r, r.fuellenMitLava);
                continue;
            }
            if (tick % PRUEFEN_ALLE != 0 || v.world() == null || plugin.bauplan().ersteMarke("graben") == null) {
                continue;
            }
            if (r.felder == null) {
                r.felder = felder(v);
            }
            if (r.felder.isEmpty() || !geladen(r.felder.getFirst()) || !geladen(r.felder.getLast())) {
                continue;
            }
            boolean soll = plugin.getConfig().getBoolean("villa.lavaGraben", true) && plugin.sicherheit().stufe(v) >= 2;
            if (r.istLava == null) {
                r.istLava = r.felder.get(r.felder.size() / 2).getType() == Material.LAVA;
            }
            if (soll != r.istLava) {
                for (Block b : r.felder) {
                    if (b.getType() == Material.WATER || b.getType() == Material.LAVA) {
                        b.setType(Material.AIR, false);
                    }
                }
                r.fuellenMitLava = soll;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (plugin.zugang().istBewohner(p, v) && v.inDerNaehe(plugin.bauplan(), p.getLocation(), 64)) {
                        p.sendActionBar(Component.text(soll ? "Der Graben füllt sich mit Lava." : "Im Graben ist wieder Wasser.",
                                soll ? NamedTextColor.GOLD : NamedTextColor.AQUA));
                    }
                }
                plugin.getLogger().info("Graben: Villa " + v.nr() + (soll ? " Lava" : " Wasser"));
            }
        }
    }

    private static boolean geladen(Block b) {
        return b.getWorld().isChunkLoaded(b.getX() >> 4, b.getZ() >> 4);
    }

    /** Leere Felder mit Lava oder Wasser füllen (ohne Fließen anzustoßen). */
    private static void fuellen(Ring r, boolean lava) {
        for (Block b : r.felder) {
            if (b.getType().isAir()) {
                b.setType(lava ? Material.LAVA : Material.WATER, false);
            }
        }
        r.istLava = lava;
        r.fuellenMitLava = null;
    }

    /** Ring aus der Marke „graben“: Kasten (x0 y0 z0 x1 y1 z1) und Breite des Rings nach innen. */
    private List<Block> felder(Villen.Villa v) {
        JsonArray m = plugin.bauplan().ersteMarke("graben");
        int x0 = m.get(0).getAsInt(), y0 = m.get(1).getAsInt(), z0 = m.get(2).getAsInt();
        int x1 = m.get(3).getAsInt(), y1 = m.get(4).getAsInt(), z1 = m.get(5).getAsInt(), breite = m.get(6).getAsInt();
        List<Block> liste = new ArrayList<>();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (Math.min(Math.min(x - x0, z - z0), Math.min(x1 - x, z1 - z)) >= breite) {
                    continue;
                }
                for (int y = y0; y <= y1; y++) {
                    liste.add(v.block(x, y, z));
                }
            }
        }
        return liste;
    }
}
