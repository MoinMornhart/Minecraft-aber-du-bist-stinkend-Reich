package de.moinmornhart.stinkendreich.client;

import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.client.bildschirm.AufzugBildschirm;
import de.moinmornhart.stinkendreich.client.bildschirm.KameraBildschirm;
import de.moinmornhart.stinkendreich.client.bildschirm.TastenfeldBildschirm;
import de.moinmornhart.stinkendreich.client.bildschirm.ZentraleBildschirm;
import de.moinmornhart.stinkendreich.mechanik.KlientBruecke;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Einstiegspunkt nur für den Client (Darstellung, Tasten, Bildschirme).
 */
public class StinkendReichClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        StinkendReich.LOGGER.info("Client-Teil geladen");

        // Bildschirme der Mechanismen
        KlientBruecke.zentrale = pos -> KompatClient.zeige(new ZentraleBildschirm(pos));
        KlientBruecke.tastenfeld = pos -> KompatClient.zeige(new TastenfeldBildschirm(pos));
        KlientBruecke.aufzug = pos -> KompatClient.zeige(new AufzugBildschirm(pos));
        KlientBruecke.monitor = pos -> KompatClient.zeige(new KameraBildschirm(pos));

        // Begrüßung im Chat, sobald man eine Welt betritt
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() ->
                KompatClient.chatNachricht(client, Component.translatable(
                        "stinkendreich.willkommen", StinkendReich.VERSION, StinkendReich.MINECRAFT
                ).withStyle(ChatFormatting.GOLD))));
    }
}
