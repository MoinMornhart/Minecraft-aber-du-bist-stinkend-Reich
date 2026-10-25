package de.moinmornhart.stinkendreich.butler;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.Konfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.Predicate;

/**
 * Baut einen Block so ab, wie es ein Spieler tun würde: Risse werden sichtbar, der Butler holt
 * mit dem Werkzeug aus, danach fällt der Ertrag in seine Taschen. Bevor er eine Wand zu Wasser
 * oder Lava öffnet, dichtet er sie mit Bruchstein ab.
 */
public final class Abbau {
    public enum Ergebnis { LAEUFT, FERTIG, VERBOTEN }

    private BlockPos ziel;
    private int fortschritt;
    private int dauer;
    /** Anzahl der Gegenstände, die beim letzten fertigen Abbau eingepackt wurden und zu {@code zaehlt} passten. */
    private int zuletztGezaehlt;

    /**
     * Arbeitet einen Tick am Block.
     *
     * @param werkzeug Werkzeug in der Hand (bestimmt Tempo und Ertrag)
     * @param zaehlt   welche Gegenstände als Ertrag gezählt werden (siehe {@link #zuletztGezaehlt()})
     */
    public Ergebnis tick(ButlerEntity butler, BlockPos pos, Item werkzeug, Predicate<ItemStack> zaehlt) {
        ServerLevel welt = butler.welt();
        BlockState zustand = welt.getBlockState(pos);
        if (zustand.isAir() || (zustand.getCollisionShape(welt, pos).isEmpty() && !zaehlt.test(new ItemStack(zustand.getBlock().asItem())))) {
            // Nichts (mehr) im Weg; Gras und Blumen ohne Wert räumt er einfach mit weg
            if (!zustand.isAir() && zustand.getFluidState().isEmpty() && Schutz.darfAbbauen(welt, pos, zustand)) {
                welt.destroyBlock(pos, false, butler);
            }
            zuruecksetzen(butler);
            zuletztGezaehlt = 0;
            return Ergebnis.FERTIG;
        }
        if (!Schutz.darfAbbauen(welt, pos, zustand)) {
            zuruecksetzen(butler);
            return Ergebnis.VERBOTEN;
        }
        butler.werkzeug(werkzeug);
        butler.schauAuf(pos);
        if (!pos.equals(ziel)) {
            zuruecksetzen(butler);
            ziel = pos.immutable();
            fortschritt = 0;
            dauer = dauer(welt, pos, zustand, werkzeug);
            abdichten(butler, pos);
        }
        fortschritt++;
        if (fortschritt % 5 == 1) {
            Kompat.schwingen(butler);
        }
        welt.destroyBlockProgress(butler.getId(), pos, Math.min(9, fortschritt * 10 / Math.max(1, dauer)));
        if (fortschritt < dauer) {
            return Ergebnis.LAEUFT;
        }
        // Fertig: Ertrag berechnen, Block entfernen, einpacken
        List<ItemStack> ertrag = Block.getDrops(zustand, welt, pos, null, butler, new ItemStack(werkzeug));
        welt.destroyBlockProgress(butler.getId(), pos, -1);
        welt.destroyBlock(pos, false, butler);
        ziel = null;
        zuletztGezaehlt = einpacken(butler, pos, ertrag, zaehlt);
        return Ergebnis.FERTIG;
    }

    /** Wie viele gezählte Gegenstände der zuletzt fertig abgebaute Block brachte. */
    public int zuletztGezaehlt() {
        return zuletztGezaehlt;
    }

    /** Entfernt sichtbare Risse, z. B. beim Abbrechen. */
    public void zuruecksetzen(ButlerEntity butler) {
        if (ziel != null) {
            butler.welt().destroyBlockProgress(butler.getId(), ziel, -1);
            ziel = null;
        }
    }

    /**
     * Packt den Ertrag ein. Schutt (Bruchstein, Erde, Kies …) wirft der Butler weg, außer er
     * wurde ausdrücklich bestellt. Was nicht mehr in die Taschen passt, fällt auf den Boden.
     */
    public static int einpacken(ButlerEntity butler, BlockPos pos, List<ItemStack> ertrag, Predicate<ItemStack> zaehlt) {
        int gezaehlt = 0;
        for (ItemStack s : ertrag) {
            boolean gewollt = zaehlt.test(s);
            if (!gewollt && s.getItem() instanceof BlockItem) {
                continue; // Schutt
            }
            if (gewollt) {
                gezaehlt += s.getCount();
            }
            ItemStack rest = butler.taschen().einpacken(s);
            if (!rest.isEmpty()) {
                Block.popResource(butler.welt(), pos, rest);
            }
        }
        return gezaehlt;
    }

    /** Abbaudauer in Ticks wie bei einem Spieler mit diesem Werkzeug. */
    private static int dauer(ServerLevel welt, BlockPos pos, BlockState zustand, Item werkzeug) {
        float haerte = zustand.getDestroySpeed(welt, pos);
        if (haerte <= 0) {
            return 1;
        }
        float tempo = Math.max(1.0F, new ItemStack(werkzeug).getDestroySpeed(zustand));
        double ticks = 30.0 * haerte / tempo / Math.max(0.1, Konfig.get().butlerTempo);
        return Math.max(2, (int) Math.ceil(ticks));
    }

    /** Verschließt Wasser und Lava rund um den Block, bevor er geöffnet wird. */
    private static void abdichten(ButlerEntity butler, BlockPos pos) {
        ServerLevel welt = butler.welt();
        BlockPos fuss = butler.blockPosition();
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (n.equals(fuss) || n.equals(fuss.above())) {
                continue;
            }
            BlockState s = welt.getBlockState(n);
            if (!s.getFluidState().isEmpty() && !Schutz.inDerVilla(welt, n) && !SpielerBloecke.istGesetzt(welt, n)) {
                welt.setBlock(n, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }
}
