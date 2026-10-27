package de.moinmornhart.stinkendreich.paper;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Testhilfe wie in der Mod (tools/ingame/test.sh): Ist {@code STINKENDREICH_AUTOTEST} gesetzt und enthält „befehle“,
 * führt das Plugin neue Zeilen aus {@code stinkendreich-befehle.txt} im Serverordner aus – mit „/“ als Befehl des
 * ersten Spielers, sonst als dessen Chatnachricht. Im normalen Betrieb tut diese Klasse nichts.
 */
final class Autotest {
    private static int gelesen;

    private Autotest() {
    }

    static void registrieren(StinkendReichPaper plugin) {
        String modus = System.getenv().getOrDefault("STINKENDREICH_AUTOTEST", "");
        if (!modus.contains("befehle")) {
            return;
        }
        plugin.getLogger().info("Autotest: führt Befehle aus stinkendreich-befehle.txt aus");
        Path datei = Path.of("stinkendreich-befehle.txt");
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty() || !Files.exists(datei)) {
                return;
            }
            try {
                List<String> zeilen = Files.readAllLines(datei, StandardCharsets.UTF_8);
                Player spieler = Bukkit.getOnlinePlayers().iterator().next();
                for (; gelesen < zeilen.size(); gelesen++) {
                    String zeile = zeilen.get(gelesen).trim();
                    if (zeile.isEmpty()) {
                        continue;
                    }
                    plugin.getLogger().info("Autotest-Befehl: " + zeile);
                    if (zeile.startsWith("/")) {
                        spieler.performCommand(zeile.substring(1));
                    } else {
                        spieler.chat(zeile);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Autotest: " + e.getMessage());
            }
        }, 5L, 5L);
    }
}
