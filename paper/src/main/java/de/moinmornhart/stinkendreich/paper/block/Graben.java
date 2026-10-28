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
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lava-Graben wie in der Mod: Im Lockdown (Stufe 2 und 3) füllt sich der Wassergraben rund ums Grundstück mit Lava,
 * sonst ist Wasser darin. Getauscht wird in zwei Schritten (erst leeren, im nächsten Tick füllen), damit sich Wasser und
 * Lava nie berühren (das gäbe Obsidian). Blöcke, die jemand in den Graben gebaut hat, bleiben.
 *
 * <p>Kein Eis auf dem Gelände einer Villa: In kalten Gegenden friert Minecraft offenes Wasser zu (Graben, Pool,
 * Haibecken). Das wird auf dem Grundstück verhindert, und Eis, das schon im Graben liegt, taut wieder auf.
 */
public final class Graben implements Listener {
    private static final int PRUEFEN_ALLE = 40;

    /** Graben je Villa: Felder des Rings, ob Lava drin ist, und ob er im nächsten Tick neu gefüllt wird. */
    private static final class Ring {
        List<Block> felder;
        /** Oberste Lage des Rings (nur dort friert Wasser). */
        List<Block> oben;
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
            if (tick % PRUEFEN_ALLE != 0 || v.world() == null || plugin.bauplan().ersteMarke("graben") == null
                    || plugin.inkognito().aktiv(v)) {
                continue;
            }
            if (r.felder == null) {
                r.felder = felder(v);
                int y1 = r.felder.stream().mapToInt(Block::getY).max().orElse(0);
                r.oben = r.felder.stream().filter(b -> b.getY() == y1).toList();
            }
            if (r.felder.isEmpty() || !geladen(r.felder.getFirst()) || !geladen(r.felder.getLast())) {
                continue;
            }
            boolean soll = plugin.getConfig().getBoolean("villa.lavaGraben", true) && plugin.sicherheit().stufe(v) >= 2;
            if (r.istLava == null) {
                r.istLava = r.felder.get(r.felder.size() / 2).getType() == Material.LAVA;
            }
            if (!r.istLava) {
                for (Block b : r.oben) {
                    if (b.getType() == Material.ICE || b.getType() == Material.FROSTED_ICE) {
                        b.setType(Material.WATER, true);
                    }
                }
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

    /** Wasser friert auf dem Gelände einer Villa nicht zu. */
    @EventHandler(ignoreCancelled = true)
    public void beimFrieren(BlockFormEvent e) {
        if (e.getNewState().getType() == Material.ICE && plugin.villen().bei(e.getBlock().getLocation(), 72) != null) {
            e.setCancelled(true);
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
