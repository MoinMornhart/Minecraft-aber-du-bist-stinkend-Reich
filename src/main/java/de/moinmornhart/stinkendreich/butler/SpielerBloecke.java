package de.moinmornhart.stinkendreich.butler;

import de.moinmornhart.stinkendreich.StinkendReich;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Merkt sich jeden Block, den ein Spieler gesetzt hat. Der Butler baut solche Blöcke nie ab.
 * Gespeichert wird im Weltordner als {@code stinkendreich-spielerbloecke.dat}
 * (je Dimension eine Liste von Positionen).
 */
public final class SpielerBloecke {
    private static final String DATEI = StinkendReich.MOD_ID + "-spielerbloecke.dat";
    /** Positionen je Dimension (z. B. „minecraft:overworld“). */
    private static final Map<String, LongOpenHashSet> GESETZT = new HashMap<>();
    private static boolean geaendert;
    private static Path pfad;

    private SpielerBloecke() {
    }

    public static void registrieren() {
        ServerLifecycleEvents.SERVER_STARTED.register(SpielerBloecke::laden);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> speichern());
        // Alle 5 Minuten sichern, falls sich etwas geändert hat
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (geaendert && s.getTickCount() % 6000 == 0) {
                speichern();
            }
        });
        // Baut ein Spieler einen Block selbst ab, ist die Stelle wieder frei
        PlayerBlockBreakEvents.AFTER.register((welt, spieler, pos, zustand, be) -> vergessen(welt, pos));
    }

    private static String schluessel(Level welt) {
        return de.moinmornhart.stinkendreich.Kompat.schluesselId(welt.dimension());
    }

    /** Wird vom Mixin in {@code BlockItem} aufgerufen, wenn ein Spieler einen Block setzt. */
    public static void merken(Level welt, BlockPos pos) {
        if (GESETZT.computeIfAbsent(schluessel(welt), k -> new LongOpenHashSet()).add(pos.asLong())) {
            geaendert = true;
        }
    }

    public static void vergessen(Level welt, BlockPos pos) {
        LongOpenHashSet satz = GESETZT.get(schluessel(welt));
        if (satz != null && satz.remove(pos.asLong())) {
            geaendert = true;
        }
    }

    public static boolean istGesetzt(Level welt, BlockPos pos) {
        LongOpenHashSet satz = GESETZT.get(schluessel(welt));
        return satz != null && satz.contains(pos.asLong());
    }

    private static void laden(MinecraftServer server) {
        GESETZT.clear();
        geaendert = false;
        pfad = server.getWorldPath(LevelResource.ROOT).resolve(DATEI);
        if (!Files.exists(pfad)) {
            return;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(pfad)))) {
            int dimensionen = in.readInt();
            for (int d = 0; d < dimensionen; d++) {
                String name = in.readUTF();
                int anzahl = in.readInt();
                LongOpenHashSet satz = new LongOpenHashSet(anzahl);
                for (int i = 0; i < anzahl; i++) {
                    satz.add(in.readLong());
                }
                GESETZT.put(name, satz);
            }
        } catch (Exception e) {
            StinkendReich.LOGGER.error("Liste der Spielerblöcke konnte nicht gelesen werden", e);
        }
    }

    private static void speichern() {
        if (pfad == null || !geaendert) {
            return;
        }
        try {
            Path neu = pfad.resolveSibling(DATEI + ".neu");
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(neu)))) {
                out.writeInt(GESETZT.size());
                for (Map.Entry<String, LongOpenHashSet> e : GESETZT.entrySet()) {
                    out.writeUTF(e.getKey());
                    out.writeInt(e.getValue().size());
                    for (long l : e.getValue()) {
                        out.writeLong(l);
                    }
                }
            }
            Files.move(neu, pfad, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            geaendert = false;
        } catch (Exception e) {
            StinkendReich.LOGGER.error("Liste der Spielerblöcke konnte nicht gespeichert werden", e);
        }
    }
}
