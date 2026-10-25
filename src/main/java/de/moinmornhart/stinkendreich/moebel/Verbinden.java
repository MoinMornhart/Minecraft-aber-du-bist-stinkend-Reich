package de.moinmornhart.stinkendreich.moebel;

import de.moinmornhart.stinkendreich.mechanik.Eigenschaften.Spalte;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

/**
 * Möbel, die sich mit Nachbarn gleicher Art zu einem Stück verbinden (Sofa, Kingsize-Bett, Bettbank):
 * Aus Sicht von jemandem, der in Blickrichtung des Möbels schaut, ist {@link Spalte#LINKS} das linke
 * Endstück (mit linker Armlehne bzw. Bettkante), {@link Spalte#RECHTS} das rechte, {@link Spalte#MITTE}
 * ein Mittelstück und {@link Spalte#EINZELN} ein einzelnes Stück mit beiden Kanten.
 */
public final class Verbinden {
    private Verbinden() {
    }

    /**
     * @param blick     Richtung, in die das Möbel schaut (Sofa: Sitzrichtung; Bett: vom Fuß zum Kopf)
     * @param gleich    ob ein Nachbarzustand zum selben Möbel gehört
     */
    public static Spalte spalte(BlockGetter welt, BlockPos pos, Direction blick, Predicate<BlockState> gleich) {
        boolean links = gleich.test(welt.getBlockState(pos.relative(blick.getCounterClockWise())));
        boolean rechts = gleich.test(welt.getBlockState(pos.relative(blick.getClockWise())));
        if (links && rechts) {
            return Spalte.MITTE;
        }
        if (rechts) {
            return Spalte.LINKS;
        }
        if (links) {
            return Spalte.RECHTS;
        }
        return Spalte.EINZELN;
    }
}
