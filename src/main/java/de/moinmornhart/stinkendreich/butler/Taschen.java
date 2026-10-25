package de.moinmornhart.stinkendreich.butler;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Die Taschen des Butlers (27 Plätze wie eine Truhe) mit Hilfen zum Zählen, Einpacken und Entnehmen.
 */
public class Taschen extends SimpleContainer {
    public static final int PLAETZE = 27;

    public Taschen() {
        super(PLAETZE);
    }

    /** Anzahl passender Gegenstände. */
    public int zaehlen(Predicate<ItemStack> passt) {
        return zaehlen(this, passt);
    }

    public static int zaehlen(Container c, Predicate<ItemStack> passt) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty() && passt.test(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** Packt einen Stapel ein; gibt zurück, was nicht mehr hineinpasst. */
    public ItemStack einpacken(ItemStack stapel) {
        return einlegen(this, stapel);
    }

    /** Legt einen Stapel in einen Behälter: erst auf passende Stapel, dann in freie Plätze. */
    public static ItemStack einlegen(Container c, ItemStack stapel) {
        ItemStack rest = stapel.copy();
        for (int i = 0; i < c.getContainerSize() && !rest.isEmpty(); i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, rest) && s.getCount() < s.getMaxStackSize()) {
                int dazu = Math.min(rest.getCount(), s.getMaxStackSize() - s.getCount());
                s.grow(dazu);
                rest.shrink(dazu);
            }
        }
        for (int i = 0; i < c.getContainerSize() && !rest.isEmpty(); i++) {
            if (c.getItem(i).isEmpty()) {
                int menge = Math.min(rest.getCount(), rest.getMaxStackSize());
                c.setItem(i, rest.copyWithCount(menge));
                rest.shrink(menge);
            }
        }
        c.setChanged();
        return rest;
    }

    /** Nimmt bis zu {@code anzahl} passende Gegenstände heraus. */
    public List<ItemStack> entnehmen(Predicate<ItemStack> passt, int anzahl) {
        return entnehmen(this, passt, anzahl);
    }

    public static List<ItemStack> entnehmen(Container c, Predicate<ItemStack> passt, int anzahl) {
        List<ItemStack> aus = new ArrayList<>();
        for (int i = 0; i < c.getContainerSize() && anzahl > 0; i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty() && passt.test(s)) {
                ItemStack teil = s.split(Math.min(anzahl, s.getCount()));
                anzahl -= teil.getCount();
                aus.add(teil);
            }
        }
        c.setChanged();
        return aus;
    }

    /** Freie Plätze. */
    public int freiePlaetze() {
        int n = 0;
        for (int i = 0; i < getContainerSize(); i++) {
            if (getItem(i).isEmpty()) {
                n++;
            }
        }
        return n;
    }
}
