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
    /** Öffnet das Menü der Musikanlage. */
    public static Consumer<BlockPos> musikanlage = p -> { };
    /** Öffnet das Butler-Menü; bekommt die Entity-Nummer des Butlers. */
    public static java.util.function.IntConsumer butler = id -> { };
    /** Spielfenster des Konzertflügels öffnen (Position des mittleren Blocks). */
    public static Consumer<BlockPos> klavier = p -> { };
    /** Das Handy öffnen. */
    public static Runnable handy = () -> { };
    /** Fernseher-Menü öffnen (Entity-ID). */
    public static java.util.function.IntConsumer fernseher = id -> { };
    /** Hält der Fahrer gerade die Handbremse (Sprungtaste)? */
    public static java.util.function.BooleanSupplier handbremse = () -> false;
    /** Hubschrauber: Sinken (Strg, die Sprinttaste). */
    public static java.util.function.BooleanSupplier sinken = () -> false;

    private KlientBruecke() {
    }
}
