package de.moinmornhart.stinkendreich.paper.villa;

import com.google.gson.JsonArray;
import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import org.bukkit.GameRule;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Slab;

/**
 * Alles nach dem Setzen des Bauplans (wie {@code VillaPlatzierung.ausstatten} in der Mod): Zufahrt ins Gelände,
 * Weltspawn an der Haustür (Spawn-Villa), Butler, Fuhrpark und Hubschrauber.
 */
public final class Ausstattung {
    /** Zufahrt vor der Brücke (Bauplan-Koordinaten): Fahrbahn x 51–58, Bordsteine bei 50 und 59. */
    public static final int ZUFAHRT_X0 = 50, ZUFAHRT_X1 = 59;

    private final StinkendReichPaper plugin;

    public Ausstattung(StinkendReichPaper plugin) {
        this.plugin = plugin;
    }

    public void ausstatten(Villen.Villa villa, int y0, int rand) {
        Bauplan plan = plugin.bauplan();
        World welt = villa.world();
        zufahrtBauen(welt, villa, plan, y0, rand);
        if (villa.nr() == 0) {
            Location spawn = villa.spawn(plan);
            welt.setSpawnLocation(spawn);
            welt.setGameRule(GameRule.SPAWN_RADIUS, 0);
        }
        plugin.figurenNachDemBau(villa);
    }

    /**
     * Gepflasterte Zufahrt vom Ende der Brücke nach Süden ins Gelände. Sie steigt oder fällt höchstens einen Block
     * pro Block und endet, sobald sie das natürliche Gelände erreicht hat.
     */
    private static void zufahrtBauen(World welt, Villen.Villa villa, Bauplan plan, int y0, int rand) {
        int start = villa.z() + plan.breiteZ;
        int mitte = villa.x() + (ZUFAHRT_X0 + ZUFAHRT_X1) / 2;
        int hoehe = y0, vorher;
        for (int i = 0; i < rand + 40; i++) {
            int z = start + i;
            int natur = Baustelle.bodenHoehe(welt, mitte, z);
            if (i >= rand + 2 && natur == hoehe) {
                break;
            }
            vorher = hoehe;
            hoehe += Math.clamp(natur - hoehe, -1, 1);
            for (int x = villa.x() + ZUFAHRT_X0; x <= villa.x() + ZUFAHRT_X1; x++) {
                boolean bord = x == villa.x() + ZUFAHRT_X0 || x == villa.x() + ZUFAHRT_X1;
                for (int y = hoehe - 2; y > hoehe - 14; y--) {
                    Block b = welt.getBlockAt(x, y, z);
                    if (!b.getType().isAir() && !b.isLiquid() && !b.isReplaceable()) {
                        break;
                    }
                    b.setType(Material.STONE, false);
                }
                welt.getBlockAt(x, hoehe - 1, z).setType(bord ? Material.POLISHED_ANDESITE : Material.GRAY_CONCRETE, false);
                int himmel = Math.min(hoehe + 48, Math.max(hoehe + 5, welt.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE) + 1));
                for (int y = hoehe; y < himmel; y++) {
                    Block b = welt.getBlockAt(x, y, z);
                    if (!b.getType().isAir()) {
                        b.setType(Material.AIR, false);
                    }
                }
            }
            if (hoehe != vorher) {
                int zTief = hoehe > vorher ? z - 1 : z, yTief = Math.min(hoehe, vorher);
                for (int x = villa.x() + ZUFAHRT_X0 + 1; x < villa.x() + ZUFAHRT_X1; x++) {
                    Block b = welt.getBlockAt(x, yTief, zTief);
                    if (b.getType().isAir()) {
                        Slab platte = (Slab) Material.POLISHED_ANDESITE_SLAB.createBlockData();
                        b.setBlockData(platte, false);
                    }
                }
            }
            if (i % 6 == 3) {
                for (int x : new int[]{villa.x() + ZUFAHRT_X0 - 1, villa.x() + ZUFAHRT_X1 + 1}) {
                    welt.getBlockAt(x, hoehe - 1, z).setType(Material.POLISHED_ANDESITE, false);
                    welt.getBlockAt(x, hoehe, z).setType(Material.POLISHED_BLACKSTONE_WALL, true);
                    welt.getBlockAt(x, hoehe + 1, z).setType(Material.LANTERN, true);
                }
            }
        }
    }

    /** Weltposition einer Marke (die ersten drei Zahlen). */
    public static Location ort(Villen.Villa villa, JsonArray m) {
        return new Location(villa.world(), villa.x() + m.get(0).getAsDouble(), villa.y() + m.get(1).getAsDouble(),
                villa.z() + m.get(2).getAsDouble());
    }
}
