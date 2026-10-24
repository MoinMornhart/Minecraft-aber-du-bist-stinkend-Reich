package de.moinmornhart.stinkendreich.client;

import de.moinmornhart.stinkendreich.StinkendReich;
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

        // Begrüßung im Chat, sobald man eine Welt betritt
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() ->
                KompatClient.chatNachricht(client, Component.translatable(
                        "stinkendreich.willkommen", StinkendReich.VERSION, StinkendReich.MINECRAFT
                ).withStyle(ChatFormatting.GOLD))));
    }
}
