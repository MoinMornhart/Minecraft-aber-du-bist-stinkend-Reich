package de.moinmornhart.stinkendreich;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Welt von einem Paper-Server auf einem Fabric-Server weiterspielen: Paper legt die Welt-Einstellungen
 * ({@code world_gen_settings.dat}) unter {@code <welt>/dimensions/minecraft/overworld/data/minecraft/} ab, Minecraft
 * erwartet sie unter {@code <welt>/data/minecraft/}. Fehlt sie dort, bricht der Server mit „Overworld settings missing“
 * ab. Beim Start eines Servers (vor dem Laden der Welt) kopiert die Mod die Datei deshalb an die richtige Stelle.
 * Nur ab 26.1 nötig (vorher lagen die Einstellungen in level.dat); in älteren Welten gibt es den Ordner nicht.
 */
final class WeltVonPaper {
    private WeltVonPaper() {
    }

    static void reparieren() {
        try {
            Path eigenschaften = Path.of("server.properties");
            String name = "world";
            if (Files.exists(eigenschaften)) {
                Properties p = new Properties();
                try (InputStream in = Files.newInputStream(eigenschaften)) {
                    p.load(in);
                }
                name = p.getProperty("level-name", "world").trim();
            }
            Path welt = Path.of(name);
            Path soll = welt.resolve("data/minecraft/world_gen_settings.dat");
            Path paper = welt.resolve("dimensions/minecraft/overworld/data/minecraft/world_gen_settings.dat");
            if (!Files.exists(soll) && Files.exists(paper)) {
                Files.createDirectories(soll.getParent());
                Files.copy(paper, soll);
                StinkendReich.LOGGER.info("Welt von einem Paper-Server erkannt: Welt-Einstellungen nach {} kopiert", soll);
            }
        } catch (IOException | RuntimeException e) {
            StinkendReich.LOGGER.warn("Welt von Paper: Welt-Einstellungen konnten nicht kopiert werden", e);
        }
    }
}
