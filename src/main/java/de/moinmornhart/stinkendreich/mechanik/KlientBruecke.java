package de.moinmornhart.stinkendreich.mechanik;

import net.minecraft.core.BlockPos;

import java.util.function.Consumer;

/**
 * Verbindung zu den Bildschirmen, die es nur auf dem Client gibt. Der Client-Teil der Mod
 * setzt diese Felder beim Start; auf einem reinen Server bleiben sie leer.
 */
public final class KlientBruecke {
    public static Consumer<BlockPos> zentrale = p -> { };
    public static Consumer<BlockPos> tastenfeld = p -> { };
    public static Consumer<BlockPos> aufzug = p -> { };
    public static Consumer<BlockPos> monitor = p -> { };

    private KlientBruecke() {
    }
}
