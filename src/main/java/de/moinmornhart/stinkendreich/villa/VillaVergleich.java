package de.moinmornhart.stinkendreich.villa;

import de.moinmornhart.stinkendreich.StinkendReich;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Testhilfe für den Inkognito-Modus ({@code /villa vergleich merken|pruefen}): merkt sich jeden Block und jede Figur der
 * Villa und meldet nach einer Fahrt hinunter und wieder hoch alle Unterschiede (ins Log und in den Chat).
 */
public final class VillaVergleich {
    /** So viele Blöcke über der Villa werden mitverglichen (Hubschrauber auf dem Dach). */
    private static final int LUFT_OBEN = 4;
    private static int[] bloecke;
    private static Map<String, Integer> figuren;

    private VillaVergleich() {
    }

    /** Merkt sich den jetzigen Stand; Rückgabe: Anzahl Figuren. */
    public static int merken(ServerLevel welt, VillaPlatzierung.VillaInfo villa) {
        bloecke = lesen(welt, villa, 0);
        figuren = figuren(welt, villa, 0);
        return figuren.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Vergleicht mit dem gemerkten Stand; Rückgabe: Liste der Unterschiede (leer = alles gleich). */
    public static List<String> pruefen(ServerLevel welt, VillaPlatzierung.VillaInfo villa) {
        return pruefen(welt, villa, 0);
    }

    /** Wie oben, aber mit dem Stand dy Blöcke tiefer bzw. höher (z. B. die versenkte Villa). */
    public static List<String> pruefen(ServerLevel welt, VillaPlatzierung.VillaInfo villa, int dy) {
        List<String> unterschiede = new ArrayList<>();
        if (bloecke == null) {
            unterschiede.add("Nichts gemerkt");
            return unterschiede;
        }
        int[] jetzt = lesen(welt, villa, dy);
        int bx = villa.plan().breiteX, bz = villa.plan().breiteZ, h = villa.plan().hoehe + LUFT_OBEN;
        int anzahl = 0;
        Map<String, Integer> arten = new TreeMap<>();
        for (int i = 0; i < jetzt.length; i++) {
            if (jetzt[i] != bloecke[i]) {
                int x = i / (h * bz), y = (i / bz) % h, z = i % bz;
                BlockState vorher = Block.stateById(bloecke[i]), nachher = Block.stateById(jetzt[i]);
                if (natuerlich(welt, villa.ursprung().offset(x, y + dy, z), vorher, nachher)) {
                    continue;
                }
                anzahl++;
                String art = vorher.getBlock().getDescriptionId() + " -> " + nachher.getBlock().getDescriptionId();
                arten.merge(art, 1, Integer::sum);
                if (anzahl <= 25) {
                    BlockState drueber = welt.getBlockState(villa.ursprung().offset(x, y + dy + 1, z));
                    StinkendReich.LOGGER.info("VERGLEICH Block {} {} {}: {} -> {} (darüber {})", x, y, z, vorher, nachher, drueber);
                }
            }
        }
        if (anzahl > 0) {
            unterschiede.add(anzahl + " Blöcke anders");
            arten.forEach((art, n) -> unterschiede.add("  " + n + "× " + art));
        }
        Map<String, Integer> jetztFiguren = figuren(welt, villa, dy);
        java.util.Set<String> alle = new java.util.TreeSet<>(figuren.keySet());
        alle.addAll(jetztFiguren.keySet());
        for (String f : alle) {
            int a = figuren.getOrDefault(f, 0), b = jetztFiguren.getOrDefault(f, 0);
            if (a != b && !f.equals("bat")) {
                unterschiede.add("Figur " + f + ": vorher " + a + ", jetzt " + b);
            }
        }
        unterschiede.forEach(u -> StinkendReich.LOGGER.info("VERGLEICH {}", u));
        if (unterschiede.isEmpty()) {
            StinkendReich.LOGGER.info("VERGLEICH alles gleich");
        }
        return unterschiede;
    }

    /**
     * Änderungen, die Minecraft von selbst macht und die nichts mit dem Inkognito-Modus zu tun haben: Gras unter einem
     * Block wird zu Erde, Seetang wächst, Tore und Türen stehen gerade offen oder zu.
     */
    private static boolean natuerlich(ServerLevel welt, BlockPos pos, BlockState vorher, BlockState nachher) {
        if (vorher.getBlock() == nachher.getBlock() && vorher.getBlock() instanceof de.moinmornhart.stinkendreich.mechanik.TorfluegelBlock) {
            return true;
        }
        if (vorher.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK) && nachher.is(net.minecraft.world.level.block.Blocks.DIRT)) {
            BlockState drueber = welt.getBlockState(pos.above());
            return drueber.canOcclude() || !drueber.getFluidState().isEmpty();
        }
        return (vorher.is(net.minecraft.world.level.block.Blocks.KELP) || vorher.is(net.minecraft.world.level.block.Blocks.WATER))
                && (nachher.is(net.minecraft.world.level.block.Blocks.KELP) || nachher.is(net.minecraft.world.level.block.Blocks.KELP_PLANT));
    }

    private static int[] lesen(ServerLevel welt, VillaPlatzierung.VillaInfo villa, int dy) {
        int bx = villa.plan().breiteX, bz = villa.plan().breiteZ, h = villa.plan().hoehe + LUFT_OBEN;
        int[] werte = new int[bx * h * bz];
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos u = villa.ursprung().above(dy);
        int i = 0;
        for (int x = 0; x < bx; x++) {
            for (int y = 0; y < h; y++) {
                for (int z = 0; z < bz; z++) {
                    werte[i++] = Block.getId(welt.getBlockState(p.set(u.getX() + x, u.getY() + y, u.getZ() + z)));
                }
            }
        }
        return werte;
    }

    /** Figuren nach Art und Block (Bilder nach ihrem Haken-Block), ohne Spieler. */
    private static Map<String, Integer> figuren(ServerLevel welt, VillaPlatzierung.VillaInfo villa, int dy) {
        BlockPos u = villa.ursprung().above(dy);
        AABB bereich = new AABB(u.getX(), u.getY(), u.getZ(), u.getX() + villa.plan().breiteX,
                u.getY() + villa.plan().hoehe + LUFT_OBEN, u.getZ() + villa.plan().breiteZ);
        Map<String, Integer> karte = new TreeMap<>();
        for (Entity e : welt.getEntitiesOfClass(Entity.class, bereich, e -> !(e instanceof Player))) {
            BlockPos b = e instanceof net.minecraft.world.entity.decoration.HangingEntity bild ? bild.getPos() : e.blockPosition();
            // Feststehendes mit Block vergleichen (Bilder, Rahmen, Ständer, Autos, Fernseher, Sitze …), Lebendes nur zählen
            boolean fest = !(e instanceof net.minecraft.world.entity.Mob) || e instanceof de.moinmornhart.stinkendreich.auto.AutoEntity
                    || e instanceof de.moinmornhart.stinkendreich.auto.DrehtellerEntity || e instanceof de.moinmornhart.stinkendreich.fernseher.FernseherEntity
                    || e instanceof de.moinmornhart.stinkendreich.moebel.SitzEntity || e instanceof de.moinmornhart.stinkendreich.mechanik.AufzugKabine;
            String name = net.minecraft.world.entity.EntityType.getKey(e.getType()).getPath()
                    + (fest ? " @" + (b.getX() - u.getX()) + "," + (b.getY() - u.getY()) + "," + (b.getZ() - u.getZ()) : "");
            karte.merge(name, 1, Integer::sum);
        }
        return karte;
    }
}
