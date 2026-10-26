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

    /** Wie ein Klick auf die nächste Aufzugstafel und die Wahl der Haltestelle mit diesem Namen. */
    private static void aufzug(Minecraft client, String ziel) {
        net.minecraft.core.BlockPos mitte = client.player.blockPosition();
        net.minecraft.core.BlockPos tafel = null;
        for (net.minecraft.core.BlockPos p : net.minecraft.core.BlockPos.betweenClosed(mitte.offset(-5, -3, -5), mitte.offset(5, 3, 5))) {
            if (client.level.getBlockState(p).getBlock() instanceof de.moinmornhart.stinkendreich.mechanik.AufzugstafelBlock
                    && (tafel == null || p.distSqr(mitte) < tafel.distSqr(mitte))) {
                tafel = p.immutable();
            }
        }
        if (tafel == null) {
            StinkendReich.LOGGER.warn("Autotest: keine Aufzugstafel in der Nähe");
            return;
        }
        for (net.minecraft.core.BlockPos halt : de.moinmornhart.stinkendreich.mechanik.AufzugstafelBlock.haltestellen(client.level, tafel)) {
            if (de.moinmornhart.stinkendreich.mechanik.AufzugstafelBlock.name(client.level, halt).equalsIgnoreCase(ziel)) {
                KompatClient.sende("aufzug", tafel, String.valueOf(halt.asLong()));
                StinkendReich.LOGGER.info("Autotest: Aufzug nach {} bestellt", ziel);
                return;
            }
        }
        StinkendReich.LOGGER.warn("Autotest: Haltestelle {} nicht gefunden", ziel);
    }

    private static java.util.Optional<de.moinmornhart.stinkendreich.fernseher.FernseherEntity> naechsterFernseher(Minecraft client) {
        return client.level.getEntitiesOfClass(de.moinmornhart.stinkendreich.fernseher.FernseherEntity.class,
                        client.player.getBoundingBox().inflate(16), t -> true)
                .stream().min(Comparator.comparingDouble(client.player::distanceToSqr));
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
                    case "fernsehermenue" -> naechsterFernseher(client).ifPresent(tv -> KlientBruecke.fernseher.accept(tv.getId()));
                    default -> {
                        if (z.startsWith("aufzug ")) {
                            aufzug(client, z.substring(7).trim());
                        } else if (z.startsWith("fernseher ")) {
                            // wie ein Knopf im Fernseher-Menü, z. B. „fernseher !abspielen|dQw4w9WgXcQ“
                            String befehl = z.substring(10).trim();
                            naechsterFernseher(client).ifPresent(tv -> KompatClient.sende("fernseher", tv.blockPosition(), tv.getId() + "|" + befehl));
                        }
                    }
                }
            }
        } catch (Exception e) {
            StinkendReich.LOGGER.warn("Autotest: Klient-Datei nicht lesbar", e);
        }
    }
}
