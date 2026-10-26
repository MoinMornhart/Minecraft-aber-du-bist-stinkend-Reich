package de.moinmornhart.stinkendreich.netz;

import de.moinmornhart.stinkendreich.StinkendReich;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.Connection;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * Erkennt, ob ein Spieler die Mod hat: Der Client mit Mod meldet beim Verbinden, dass er das (leere) Paket
 * „hallo“ empfangen kann. Die Server-Version ({@code server/ServerPaket}) zeigt Spielern ohne Mod dann normale
 * Minecraft-Blöcke mit Ressourcenpaket, Spielern mit Mod alles wie gewohnt.
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
    }

    /** Hat der Spieler hinter dieser Verbindung die Mod? (Ohne Verbindung, etwa im Einzelspieler: ja.) */
    public static boolean hatMod(Connection verbindung) {
        if (verbindung == null || verbindung.isMemoryConnection()) {
            return true;
        }
        if (verbindung.getPacketListener() instanceof ServerGamePacketListenerImpl spiel) {
            return ServerPlayNetworking.canSend(spiel, Hallo.TYP);
        }
        if (verbindung.getPacketListener() instanceof ServerConfigurationPacketListenerImpl konfig) {
            return ServerConfigurationNetworking.canSend(konfig, Hallo.TYP);
        }
        return false;
    }
}
