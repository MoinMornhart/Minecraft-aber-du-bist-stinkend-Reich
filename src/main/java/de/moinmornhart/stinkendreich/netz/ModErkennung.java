package de.moinmornhart.stinkendreich.netz;

import de.moinmornhart.stinkendreich.StinkendReich;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.Connection;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Erkennt, ob ein Spieler die Mod hat: Der Client mit Mod meldet beim Verbinden, dass er das (leere) Paket
 * „hallo“ empfangen kann. Die Server-Version ({@code server/ServerPaket}) zeigt Spielern ohne Mod dann normale
 * Minecraft-Blöcke mit Ressourcenpaket, Spielern mit Mod alles wie gewohnt.
 * <p>
 * Das Ergebnis wird schon beim Anmelden (Konfigurationsphase) festgehalten: Am Anfang der Spielphase kennt der
 * Server die Kanäle des Clients noch nicht, und eine Figur, die dann als Ersatz und gleich danach mit echten
 * Mod-Daten käme, würde die Verbindung abbrechen.
 */
public final class ModErkennung {
    /** Leeres Paket; es wird nie wirklich gebraucht, nur angemeldet. */
    public record Hallo() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Hallo> TYP = new CustomPacketPayload.Type<>(StinkendReich.id("hallo"));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYP;
        }
    }

    /** Hat der Spieler (UUID) die Mod? Festgehalten beim Anmelden, gelöscht beim Verlassen. */
    private static final Map<UUID, Boolean> MIT_MOD = new ConcurrentHashMap<>();

    private ModErkennung() {
    }

    /** Beide Seiten: Pakettyp für die Verbindungs- und die Spielphase anmelden. */
    public static void registrieren() {
        //? if >=26.1 {
        PayloadTypeRegistry.clientboundConfiguration().register(Hallo.TYP, StreamCodec.unit(new Hallo()));
        PayloadTypeRegistry.clientboundPlay().register(Hallo.TYP, StreamCodec.unit(new Hallo()));
        //?} else {
        /*PayloadTypeRegistry.configurationS2C().register(Hallo.TYP, StreamCodec.unit(new Hallo()));
        PayloadTypeRegistry.playS2C().register(Hallo.TYP, StreamCodec.unit(new Hallo()));
        *///?}
        // Beim Anmelden kennt der Server die Kanäle der Konfigurationsphase schon
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            boolean mitMod = ServerConfigurationNetworking.canSend(handler, Hallo.TYP);
            MIT_MOD.put(profilId(handler), mitMod);
            StinkendReich.LOGGER.info("Spieler {} hat die Mod: {}", profilId(handler), mitMod);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> MIT_MOD.remove(handler.player.getUUID()));
    }

    private static UUID profilId(ServerConfigurationPacketListenerImpl handler) {
        //? if >=1.21.9 {
        return handler.getOwner().id();
        //?} else
        /*return handler.getOwner().getId();*/
    }

    /**
     * Hat dieser Spieler die Mod? Im reinen Einzelspiel immer. Eine geöffnete Einzelspielerwelt (LAN, NoRisk „Hosted
     * Worlds“) ist ein Server wie jeder andere: Freunde ohne Mod brauchen dort die Übersetzung genauso.
     */
    public static boolean hatMod(net.minecraft.server.level.ServerPlayer spieler) {
        net.minecraft.server.MinecraftServer server = spieler.level().getServer();
        if (server.isSingleplayer() && !server.isPublished()) {
            return true;
        }
        Boolean gemerkt = MIT_MOD.get(spieler.getUUID());
        return gemerkt != null ? gemerkt : ServerPlayNetworking.canSend(spieler, Hallo.TYP);
    }

    /** Hat der Spieler hinter dieser Verbindung die Mod? (Speicherverbindung, also Einzelspieler: ja.) */
    public static boolean hatMod(Connection verbindung) {
        if (verbindung == null || verbindung.isMemoryConnection()) {
            return true;
        }
        if (verbindung.getPacketListener() instanceof ServerGamePacketListenerImpl spiel) {
            return hatMod(spiel.player);
        }
        if (verbindung.getPacketListener() instanceof ServerConfigurationPacketListenerImpl konfig) {
            Boolean gemerkt = MIT_MOD.get(profilId(konfig));
            return gemerkt != null ? gemerkt : ServerConfigurationNetworking.canSend(konfig, Hallo.TYP);
        }
        return false;
    }
}
