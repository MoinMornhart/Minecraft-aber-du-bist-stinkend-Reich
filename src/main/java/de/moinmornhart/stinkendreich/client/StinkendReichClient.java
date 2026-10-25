package de.moinmornhart.stinkendreich.client;

import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.client.auto.AutoKlient;
import de.moinmornhart.stinkendreich.client.auto.AutoModell;
import de.moinmornhart.stinkendreich.client.auto.AutoRenderer;
import de.moinmornhart.stinkendreich.client.bildschirm.AufzugBildschirm;
import de.moinmornhart.stinkendreich.client.update.UpdateKlient;
import de.moinmornhart.stinkendreich.client.bildschirm.ButlerBildschirm;
import de.moinmornhart.stinkendreich.client.butler.ButlerModell;
import de.moinmornhart.stinkendreich.client.butler.ButlerRenderer;
import de.moinmornhart.stinkendreich.register.ModBloecke;
import de.moinmornhart.stinkendreich.register.ModEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
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
        KlientBruecke.butler = id -> KompatClient.zeige(new ButlerBildschirm(id));

        // Türen und Tore mit durchsichtigen Stellen
        KompatClient.durchsichtig(ModBloecke.TOR, ModBloecke.GITTERFALLE, ModBloecke.SCHIEBETUER, ModBloecke.PRUNKTUER,
                ModBloecke.GLASTUER, ModBloecke.STAHLTUER, ModBloecke.BUNKERTUER);

        // Butler: eigenes Modell (Fabric hat die Klasse dafür in 26.x umbenannt) und Renderer
        //? if >=26.1 {
        net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(ButlerModell.SCHICHT, ButlerModell::schicht);
        //?} else
        /*net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(ButlerModell.SCHICHT, ButlerModell::schicht);*/
        EntityRendererRegistry.register(ModEntities.BUTLER, ButlerRenderer::new);
        // Bonzenauto: Modell, Renderer, Tasten, Tacho, Motorgeräusch
        //? if >=26.1 {
        net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(AutoModell.SCHICHT, AutoModell::schicht);
        //?} else
        /*net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(AutoModell.SCHICHT, AutoModell::schicht);*/
        EntityRendererRegistry.register(ModEntities.AUTO, AutoRenderer::new);
        AutoKlient.registrieren();
        UpdateKlient.registrieren();
        AutotestKlient.registrieren();

        // Begrüßung im Chat, sobald man eine Welt betritt
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() ->
                KompatClient.chatNachricht(client, Component.translatable(
                        "stinkendreich.willkommen", StinkendReich.VERSION, StinkendReich.MINECRAFT
                ).withStyle(ChatFormatting.GOLD))));
    }
}
