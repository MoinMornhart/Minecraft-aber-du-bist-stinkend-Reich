package de.moinmornhart.stinkendreich.dev;

import de.moinmornhart.stinkendreich.StinkendReich;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

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

    private Autotest() {
    }

    public static boolean aktiv() {
        return !MODUS.isEmpty();
    }

    /** Registriert die Test-Hilfen, falls der Testmodus an ist. */
    public static void registrieren() {
        if (!MODUS.contains("server-stop")) {
            return;
        }
        StinkendReich.LOGGER.info("Autotest: Server stoppt nach dem Erzeugen der Welt");
        // Nach dem Start noch etwas laufen lassen, damit Spawn-Bereich und Villa fertig sind
        ServerLifecycleEvents.SERVER_STARTED.register(server -> restTicks = 100);
        ServerTickEvents.END_SERVER_TICK.register(Autotest::tick);
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
