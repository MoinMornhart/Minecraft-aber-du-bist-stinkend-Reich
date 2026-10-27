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
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import de.moinmornhart.stinkendreich.paper.auto.Fahrzeuge;

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

    @SuppressWarnings("removal")
    public void ausstatten(Villen.Villa villa, int y0, int rand) {
        Bauplan plan = plugin.bauplan();
        World welt = villa.world();
        zufahrtBauen(welt, villa, plan, y0, rand);
        if (villa.nr() == 0) {
            Location spawn = villa.spawn(plan);
            welt.setSpawnLocation(spawn);
            welt.setGameRule(GameRule.SPAWN_RADIUS, 0);
        }
        fuhrpark(villa, plan);
        anzeigen(villa, plan);
        plugin.festung().bauen(villa);                  // Minengürtel und Notausgang im Gelände
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

    /** Belegung der Stellplätze 0–9 wie in der Mod (Typ, Lackfarbe): Süd-Reihe, dann Nord-Reihe. */
    private static final Object[][] FUHRPARK = {
            {Fahrzeuge.Art.BONZENAUTO, 4}, {Fahrzeuge.Art.SUPERSPORTWAGEN, 3}, {Fahrzeuge.Art.GELAENDEWAGEN, 0},
            {Fahrzeuge.Art.SUPERSPORTWAGEN, 6}, {Fahrzeuge.Art.BONZENAUTO, 7}, {Fahrzeuge.Art.GELAENDEWAGEN, 1},
            {Fahrzeuge.Art.SUPERSPORTWAGEN, 2}, {Fahrzeuge.Art.BONZENAUTO, 0}, {Fahrzeuge.Art.BONZENAUTO, 5},
            {Fahrzeuge.Art.STRETCHLIMO, 0}};

    /** Fuhrpark in der Tiefgarage und Hubschrauber auf dem Landeplatz. */
    private void fuhrpark(Villen.Villa villa, Bauplan plan) {
        for (JsonArray platz : plan.marke("stellplatz")) {
            int stelle = platz.get(4).getAsInt();
            if (stelle < FUHRPARK.length) {
                plugin.fahrzeuge().abstellen((Fahrzeuge.Art) FUHRPARK[stelle][0], (Integer) FUHRPARK[stelle][1], ort(villa, platz),
                        platz.get(3).getAsFloat());
            }
        }
        for (JsonArray m : plan.marke("hubschrauber")) {
            plugin.fahrzeuge().abstellen(Fahrzeuge.Art.HUBSCHRAUBER, 0, ort(villa, m), m.get(3).getAsFloat());
        }
    }

    /** Fernseher (zeigen das Bild „aus“) und Drehteller als Anzeige-Figuren (wie die Server-Version der Mod). */
    private void anzeigen(Villen.Villa villa, Bauplan plan) {
        for (JsonArray m : plan.marke("fernseher")) {
            float breite = m.size() >= 6 ? m.get(4).getAsFloat() : 5.0F, hoehe = m.size() >= 6 ? m.get(5).getAsFloat() : 2.8125F;
            ItemDisplay tv = modellAufstellen(ort(villa, m), m.get(3).getAsFloat(), "polymer/fernseher",
                    new Vector3f(breite / 5.0F, hoehe / 2.8125F, 1.0F));
            plugin.fernseher().klickfelder(tv, breite, hoehe);
        }
        for (JsonArray m : plan.marke("drehteller")) {
            modellAufstellen(ort(villa, m), 0.0F, "polymer/drehteller", new Vector3f(1, 1, 1));
        }
    }

    /** Ein feststehendes Modell aus dem Ressourcenpaket (Modellraum wie bei den Fahrzeugen). */
    private static ItemDisplay modellAufstellen(Location ort, float blick, String modell, Vector3f dehnung) {
        Location l = ort.clone();
        l.setYaw(blick);
        return l.getWorld().spawn(l, ItemDisplay.class, d -> {
            ItemStack bild = ItemStack.of(Material.PAPER);
            bild.setData(DataComponentTypes.ITEM_MODEL, Key.key("stinkendreich", modell));
            d.setItemStack(bild);
            d.setPersistent(true);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(new Transformation(new Vector3f(0, 1.5F * dehnung.y, 0), new Quaternionf().rotateZ((float) Math.PI),
                    new Vector3f(4 * dehnung.x, 4 * dehnung.y, 4 * dehnung.z), new Quaternionf()));
            // Licht der Mitte statt des Fußpunkts (der steckt beim Fernseher in der Wand)
            Location mitte = l.clone().add(0, 1.5 * dehnung.y, 0);
            d.setBrightness(new org.bukkit.entity.Display.Brightness(mitte.getBlock().getLightFromBlocks(), mitte.getBlock().getLightFromSky()));
            d.setViewRange(2.0F);
        });
    }

    /** Weltposition einer Marke (die ersten drei Zahlen). */
    public static Location ort(Villen.Villa villa, JsonArray m) {
        return new Location(villa.world(), villa.x() + m.get(0).getAsDouble(), villa.y() + m.get(1).getAsDouble(),
                villa.z() + m.get(2).getAsDouble());
    }
}
