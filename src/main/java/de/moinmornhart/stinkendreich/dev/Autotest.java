package de.moinmornhart.stinkendreich.dev;

import de.moinmornhart.stinkendreich.StinkendReich;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Hilfen für den automatischen Test im Spiel (tools/ingame/test.sh).
 *
 * Nur aktiv, wenn die Umgebungsvariable {@code STINKENDREICH_AUTOTEST} gesetzt ist.
 * Im normalen Spiel tut diese Klasse nichts.
 */
public final class Autotest {
    /** Wert der Umgebungsvariable, z. B. "server-stop". Leer = aus. */
    public static final String MODUS = System.getenv().getOrDefault("STINKENDREICH_AUTOTEST", "");

    private static int restTicks = -1;
    /** Bereits ausgeführte Zeilen der Befehlsdatei. */
    private static int gelesen;

    private Autotest() {
    }

    public static boolean aktiv() {
        return !MODUS.isEmpty();
    }

    /** Registriert die Test-Hilfen, falls der Testmodus an ist. */
    public static void registrieren() {
        if (MODUS.contains("befehle")) {
            // Testbefehle aus einer Datei statt über getippten Chat (zuverlässig auch bei ruckelndem Spiel)
            StinkendReich.LOGGER.info("Autotest: führt Befehle aus stinkendreich-befehle.txt aus");
            ServerTickEvents.END_SERVER_TICK.register(Autotest::befehleAusDatei);
        }
        if (!MODUS.contains("server-stop")) {
            return;
        }
        StinkendReich.LOGGER.info("Autotest: Server stoppt nach dem Erzeugen der Welt");
        // Nach dem Start noch etwas laufen lassen, damit Spawn-Bereich und Villa fertig sind
        ServerLifecycleEvents.SERVER_STARTED.register(server -> restTicks = 100);
        ServerTickEvents.END_SERVER_TICK.register(Autotest::tick);
    }

    /**
     * Liest neue Zeilen aus {@code stinkendreich-befehle.txt} im Spielordner. Zeilen mit „/“ laufen
     * als Befehl des ersten Spielers, alle anderen gelten als dessen Chatnachricht (z. B. an den Butler).
     */
    private static void befehleAusDatei(MinecraftServer server) {
        if (server.getTickCount() % 5 != 0 || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        Path datei = server.getServerDirectory().resolve(StinkendReich.MOD_ID + "-befehle.txt");
        if (!Files.exists(datei)) {
            return;
        }
        try {
            List<String> zeilen = Files.readAllLines(datei, StandardCharsets.UTF_8);
            ServerPlayer spieler = server.getPlayerList().getPlayers().get(0);
            for (; gelesen < zeilen.size(); gelesen++) {
                String zeile = zeilen.get(gelesen).trim();
                if (zeile.isEmpty()) {
                    continue;
                }
                StinkendReich.LOGGER.info("Autotest-Befehl: {}", zeile);
                if (zeile.startsWith("/")) {
                    server.getCommands().performPrefixedCommand(spieler.createCommandSourceStack(), zeile.substring(1));
                } else {
                    server.getPlayerList().broadcastSystemMessage(Component.literal("<" + spieler.getScoreboardName() + "> " + zeile), false);
                    String rest = de.moinmornhart.stinkendreich.butler.ButlerBefehle.anredeTest(zeile);
                    if (rest != null) {
                        de.moinmornhart.stinkendreich.butler.ButlerBefehle.verarbeiten(spieler, rest, false);
                    }
                }
            }
        } catch (Exception e) {
            StinkendReich.LOGGER.warn("Autotest: Befehlsdatei nicht lesbar", e);
        }
    }

    private static void tick(MinecraftServer server) {
        if (restTicks < 0) {
            return;
        }
        if (restTicks-- == 0) {
            StinkendReich.LOGGER.info("Autotest: Welt erzeugt, Server wird gestoppt");
            server.halt(false);
        }
    }
}
