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
            ServerTickEvents.END_SERVER_TICK.register(Autotest::kamerafahrt);
        }
        //? if >=26.3 {
        if (MODUS.contains("paperdaten")) {
            // Blockdaten für das Paper-Plugin (paper/src/main/resources/bloecke.json)
            ServerLifecycleEvents.SERVER_STARTED.register(PaperDaten::schreiben);
        }
        //?}
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
                if (zeile.startsWith("fahrt ")) {
                    fahrtStarten(zeile.substring(6));
                } else if (zeile.equals("offline")) {
                    // Geöffnete Einzelspielerwelt ohne Anmeldung bei Mojang (Test wie NoRisk „Hosted Worlds“/LAN:
                    // in der Testumgebung gibt es keine echten Konten); danach „/publish …“
                    server.setUsesAuthentication(false);
                }
                //? if >=1.21.2 {
                else if (zeile.startsWith("eingabe")) {
                    // Tasten des Spielers so setzen, als hätte sein Minecraft sie geschickt (Test der Server-Version,
                    // z. B. „eingabe w a“; ohne Buchstaben alle los)
                    String t = " " + zeile.substring(7).trim() + " ";
                    spieler.setLastClientInput(new net.minecraft.world.entity.player.Input(t.contains(" w "), t.contains(" s "),
                            t.contains(" a "), t.contains(" d "), t.contains(" leer "), t.contains(" schleich "), t.contains(" strg ")));
                }
                //?}
                else if (zeile.startsWith("/")) {
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

    // ---------- Kamerafahrt fürs Video ----------

    /** Wegpunkte der laufenden Fahrt (x, y, z, Gier, Neigung relativ zur Villa) oder null. */
    private static double[][] fahrt;
    /** Strecke bis zu jedem Wegpunkt (Blöcke) und Gesamtdauer in Ticks. */
    private static double[] bisPunkt;
    private static int fahrtTick, fahrtDauer;

    /**
     * Startet eine flüssige Kamerafahrt: {@code fahrt <sekunden> | x y z gier neigung | x y z gier neigung | …}.
     * Die Kamera fliegt auf einer weichen Kurve (Catmull-Rom) mit gleichmäßigem Tempo durch alle Punkte,
     * am Anfang und Ende sanft beschleunigt und gebremst. Koordinaten wie bei {@code /villa ort}.
     */
    private static void fahrtStarten(String text) {
        String[] teile = text.split("\\|");
        int n = teile.length - 1;
        double[][] punkte = new double[n][];
        for (int i = 0; i < n; i++) {
            String[] w = teile[i + 1].trim().split("\\s+");
            punkte[i] = new double[]{Double.parseDouble(w[0]), Double.parseDouble(w[1]), Double.parseDouble(w[2]),
                    Double.parseDouble(w[3]), Double.parseDouble(w[4])};
            // Blickrichtung ohne Sprünge über ±180 Grad (sonst dreht die Kamera einmal ganz herum)
            if (i > 0) {
                punkte[i][3] = punkte[i - 1][3] + net.minecraft.util.Mth.wrapDegrees(punkte[i][3] - punkte[i - 1][3]);
            }
        }
        bisPunkt = new double[n];
        for (int i = 1; i < n; i++) {
            double dx = punkte[i][0] - punkte[i - 1][0], dy = punkte[i][1] - punkte[i - 1][1], dz = punkte[i][2] - punkte[i - 1][2];
            // Reine Drehungen auf der Stelle zählen auch als Weg, damit sie Zeit bekommen
            double drehung = Math.abs(punkte[i][3] - punkte[i - 1][3]) / 20.0;
            bisPunkt[i] = bisPunkt[i - 1] + Math.max(Math.sqrt(dx * dx + dy * dy + dz * dz), drehung) + 0.01;
        }
        fahrt = punkte;
        fahrtTick = 0;
        fahrtDauer = Math.max(1, (int) Math.round(Double.parseDouble(teile[0].trim()) * 20));
    }

    private static void kamerafahrt(MinecraftServer server) {
        if (fahrt == null || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        var villa = de.moinmornhart.stinkendreich.villa.VillaPlatzierung.villa();
        if (villa.isEmpty()) {
            fahrt = null;
            return;
        }
        double t = Math.min(1.0, (double) fahrtTick / fahrtDauer);
        double sanft = (1 - Math.cos(Math.PI * t)) / 2;            // langsam los, langsam an
        double weg = sanft * bisPunkt[bisPunkt.length - 1];
        int i = 0;
        while (i < fahrt.length - 2 && weg > bisPunkt[i + 1]) {
            i++;
        }
        double u = fahrt.length == 1 ? 0 : (weg - bisPunkt[i]) / (bisPunkt[i + 1] - bisPunkt[i]);
        double[] p0 = fahrt[Math.max(0, i - 1)], p1 = fahrt[i], p2 = fahrt[Math.min(fahrt.length - 1, i + 1)],
                p3 = fahrt[Math.min(fahrt.length - 1, i + 2)];
        double[] wert = new double[5];
        for (int k = 0; k < 5; k++) {
            wert[k] = catmullRom(p0[k], p1[k], p2[k], p3[k], u);
        }
        var u0 = villa.get().ursprung();
        ServerPlayer spieler = server.getPlayerList().getPlayers().get(0);
        de.moinmornhart.stinkendreich.Kompat.befehl(server, String.format(java.util.Locale.ROOT, "tp %s %.3f %.3f %.3f %.2f %.2f",
                spieler.getScoreboardName(), u0.getX() + wert[0], u0.getY() + wert[1], u0.getZ() + wert[2], wert[3],
                net.minecraft.util.Mth.clamp(wert[4], -90, 90)));
        if (fahrtTick++ >= fahrtDauer) {
            fahrt = null;
            // Das Szenario wartet mit „bis … Fahrt fertig“ auf diese Zeile
            StinkendReich.LOGGER.info("Autotest: Fahrt fertig");
        }
    }

    private static double catmullRom(double a, double b, double c, double d, double u) {
        return 0.5 * (2 * b + (-a + c) * u + (2 * a - 5 * b + 4 * c - d) * u * u + (-a + 3 * b - 3 * c + d) * u * u * u);
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
