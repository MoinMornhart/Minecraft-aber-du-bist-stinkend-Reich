package de.moinmornhart.stinkendreich.client;

import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import de.moinmornhart.stinkendreich.dev.Autotest;
import de.moinmornhart.stinkendreich.mechanik.KlientBruecke;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Client-Hilfen für den automatischen Test (nur mit {@code STINKENDREICH_AUTOTEST=befehle}):
 * liest {@code stinkendreich-klient.txt} im Spielordner, z. B. „schliessen“ (offenen Bildschirm
 * schließen) oder „butlermenue“ (Menü des nächsten Butlers öffnen). So hängt der Test nicht davon
 * ab, wohin das Fadenkreuz zeigt.
 */
public final class AutotestKlient {
    private static int gelesen;

    private AutotestKlient() {
    }

    public static void registrieren() {
        if (!Autotest.MODUS.contains("befehle")) {
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(AutotestKlient::tick);
    }

    private static void tick(Minecraft client) {
        if (client.player == null || client.level == null || client.player.tickCount % 5 != 0) {
            return;
        }
        Path datei = client.gameDirectory.toPath().resolve(StinkendReich.MOD_ID + "-klient.txt");
        if (!Files.exists(datei)) {
            return;
        }
        try {
            List<String> zeilen = Files.readAllLines(datei, StandardCharsets.UTF_8);
            for (; gelesen < zeilen.size(); gelesen++) {
                String z = zeilen.get(gelesen).trim();
                switch (z) {
                    case "schliessen" -> KompatClient.zeige(null);
                    case "butlermenue" -> client.level.getEntitiesOfClass(ButlerEntity.class, client.player.getBoundingBox().inflate(16), b -> true)
                            .stream().min(Comparator.comparingDouble(client.player::distanceToSqr))
                            .ifPresent(b -> KlientBruecke.butler.accept(b.getId()));
                    default -> {
                    }
                }
            }
        } catch (Exception e) {
            StinkendReich.LOGGER.warn("Autotest: Klient-Datei nicht lesbar", e);
        }
    }
}
