package de.moinmornhart.stinkendreich.butler.auftrag;

import com.google.gson.JsonObject;
import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.Konfig;
import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import de.moinmornhart.stinkendreich.butler.Schmelzen;
import de.moinmornhart.stinkendreich.butler.Umgebung;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Räumt alle Truhen und Fässer in der Nähe auf: geht jede ab, legt gleiche Gegenstände zusammen
 * und verteilt alles sortiert (Baublöcke, Rohstoffe, Essen, Werkzeug, Sonstiges), jede Sorte in
 * ihre Stammtruhe. Es geht nichts verloren: Zusammenlegen braucht nie mehr Platz als vorher.
 */
public class SortierAuftrag extends Auftrag {
    public static final String TYP = "sortieren";

    private final BlockPos mitte;
    private List<BlockPos> truhen;
    private int index;
    private int deckelTicks = -1;
    private final List<BlockPos> besucht = new ArrayList<>();

    public SortierAuftrag(BlockPos mitte) {
        this.mitte = mitte.immutable();
    }

    private static boolean istTruhe(BlockEntity be) {
        return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity;
    }

    @Override
    protected Stand tick(ButlerEntity butler) {
        if (truhen == null) {
            int r = Konfig.get().butlerSortierRadius;
            truhen = Umgebung.blockEntities(butler.welt(), mitte, r, SortierAuftrag::istTruhe);
            truhen.removeIf(p -> Math.abs(p.getY() - mitte.getY()) > 3);
            truhen.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
            if (truhen.isEmpty()) {
                return fehlschlag(Component.translatable("stinkendreich.butler.keinetruhe"));
            }
        }
        if (index >= truhen.size()) {
            int anzahl = sortieren(butler);
            butler.sagen(Component.translatable("stinkendreich.butler.sortiert", besucht.size(), anzahl));
            return Stand.FERTIG;
        }
        BlockPos t = truhen.get(index);
        if (deckelTicks >= 0) {
            butler.schauAuf(t);
            if (++deckelTicks > 10) {
                LieferAuftrag.deckel(butler, t, false);
                deckelTicks = -1;
                index++;
            }
            return Stand.LAEUFT;
        }
        ButlerEntity.Weg w = butler.hinkommen(t, 2.3);
        if (w == ButlerEntity.Weg.UNERREICHBAR) {
            index++;
        } else if (w == ButlerEntity.Weg.DA) {
            LieferAuftrag.deckel(butler, t, true);
            Kompat.schwingen(butler);
            besucht.add(t);
            deckelTicks = 0;
        }
        return Stand.LAEUFT;
    }

    /**
     * Sammelt den Inhalt aller besuchten Truhen, fasst gleiche Gegenstände zusammen und verteilt
     * neu: Jede Sorte kommt in die Truhe, in der vorher am meisten davon lag (so bleibt die Erde
     * in der Erdtruhe), sortiert nach Gruppe und Name. Nur was dort keinen Platz mehr hat, wandert
     * in die nächste Truhe mit freien Plätzen.
     */
    private int sortieren(ButlerEntity butler) {
        List<Container> behaelter = new ArrayList<>();
        for (BlockPos p : besucht) {
            if (butler.welt().getBlockEntity(p) instanceof Container c && istTruhe((BlockEntity) c)) {
                behaelter.add(c);
            }
        }
        int n = behaelter.size();
        List<ItemStack> alles = new ArrayList<>();
        Map<Item, int[]> mengeJeTruhe = new HashMap<>();
        for (int ci = 0; ci < n; ci++) {
            Container c = behaelter.get(ci);
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (!s.isEmpty()) {
                    mengeJeTruhe.computeIfAbsent(s.getItem(), k -> new int[n])[ci] += s.getCount();
                    zusammenlegen(alles, s.copy());
                }
                c.setItem(i, ItemStack.EMPTY);
            }
        }
        alles.sort(Comparator.comparingInt(SortierAuftrag::gruppe)
                .thenComparing(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString())
                .thenComparingInt(s -> -s.getCount()));
        int anzahl = 0;
        int[] belegt = new int[n];
        List<ItemStack> uebrig = new ArrayList<>();
        for (ItemStack s : alles) {
            anzahl += s.getCount();
            int heim = stammtruhe(mengeJeTruhe.get(s.getItem()));
            if (belegt[heim] < behaelter.get(heim).getContainerSize()) {
                behaelter.get(heim).setItem(belegt[heim]++, s);
            } else {
                uebrig.add(s);
            }
        }
        for (ItemStack s : uebrig) {
            int ci = 0;
            while (ci < n && belegt[ci] >= behaelter.get(ci).getContainerSize()) {
                ci++;
            }
            if (ci >= n) {
                // kann nicht passieren (zusammengelegt braucht nie mehr Platz), zur Sicherheit aber nichts wegwerfen
                net.minecraft.world.level.block.Block.popResource(butler.welt(), butler.blockPosition(), s);
                continue;
            }
            behaelter.get(ci).setItem(belegt[ci]++, s);
        }
        behaelter.forEach(Container::setChanged);
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

    /** Legt einen Stapel auf passende angefangene Stapel, den Rest als neue Stapel ab. */
    private static void zusammenlegen(List<ItemStack> liste, ItemStack neu) {
        for (ItemStack s : liste) {
            if (neu.isEmpty()) {
                return;
            }
            if (ItemStack.isSameItemSameComponents(s, neu) && s.getCount() < s.getMaxStackSize()) {
                int dazu = Math.min(neu.getCount(), s.getMaxStackSize() - s.getCount());
                s.grow(dazu);
                neu.shrink(dazu);
            }
        }
        if (!neu.isEmpty()) {
            liste.add(neu);
        }
    }

    /** Sortiergruppe: 0 Baublöcke, 1 Rohstoffe, 2 Essen, 3 Werkzeug/Rüstung, 4 Sonstiges. */
    private static int gruppe(ItemStack s) {
        if (s.has(DataComponents.FOOD) || Schmelzen.istRohesEssen(s)) {
            return 2;
        }
        if (s.isDamageableItem()) {
            return 3;
        }
        if (s.getItem() instanceof BlockItem) {
            return 0;
        }
        if (Schmelzen.ergebnis(s) != null || Schmelzen.brennwert(s) > 0 || s.getMaxStackSize() == 64) {
            return 1;
        }
        return 4;
    }

    @Override
    public void abbrechen(ButlerEntity butler) {
        if (deckelTicks >= 0 && truhen != null && index < truhen.size()) {
            LieferAuftrag.deckel(butler, truhen.get(index), false);
        }
    }

    @Override
    public Component status() {
        return Component.translatable("stinkendreich.butler.status.sortiert", Math.min(index + 1, truhen == null ? 1 : truhen.size()),
                truhen == null ? 0 : truhen.size());
    }

    @Override
    public String typ() {
        return TYP;
    }

    @Override
    public void schreiben(JsonObject j) {
        j.add("mitte", ButlerEntity.posJson(mitte));
    }

    static SortierAuftrag ausJson(JsonObject j) {
        return new SortierAuftrag(ButlerEntity.posAusJson(j.getAsJsonArray("mitte")));
    }
}
