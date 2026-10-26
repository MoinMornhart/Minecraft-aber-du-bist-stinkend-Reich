package de.moinmornhart.stinkendreich.client;

import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.client.aufzug.AufzugModell;
import de.moinmornhart.stinkendreich.client.aufzug.AufzugMusik;
import de.moinmornhart.stinkendreich.client.aufzug.AufzugRenderer;
import de.moinmornhart.stinkendreich.client.auto.AutoKlient;
import de.moinmornhart.stinkendreich.client.fernseher.FernseherBildschirm;
import de.moinmornhart.stinkendreich.client.fernseher.FernseherModell;
import de.moinmornhart.stinkendreich.client.fernseher.FernseherRenderer;
import de.moinmornhart.stinkendreich.auto.Autotyp;
import de.moinmornhart.stinkendreich.client.fahrzeug.FahrzeugModell;
import de.moinmornhart.stinkendreich.client.auto.AutoRenderer;
import de.moinmornhart.stinkendreich.client.bildschirm.AufzugBildschirm;
import de.moinmornhart.stinkendreich.client.update.UpdateKlient;
import de.moinmornhart.stinkendreich.client.bildschirm.ButlerBildschirm;
import de.moinmornhart.stinkendreich.client.bildschirm.MusikBildschirm;
import de.moinmornhart.stinkendreich.client.musik.MusikKlient;
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
        KlientBruecke.musikanlage = pos -> KompatClient.zeige(new MusikBildschirm(pos));

        // Versteckte Falltür: Rasen in der Grasfarbe der Umgebung, damit man sie nicht sieht
        //? if >=26.1 {
        net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry.register(
                java.util.List.of(net.minecraft.client.color.block.BlockTintSources.grass()), ModBloecke.GEHEIMLUKE);
        //?} else {
        /*net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.BLOCK.register((zustand, welt, pos, index) ->
                welt != null && pos != null ? net.minecraft.client.renderer.BiomeColors.getAverageGrassColor(welt, pos)
                        : net.minecraft.world.level.GrassColor.getDefaultColor(), ModBloecke.GEHEIMLUKE);
        *///?}

        // Türen und Tore mit durchsichtigen Stellen
        KompatClient.durchsichtig(ModBloecke.HAIFALLE);
        KompatClient.durchsichtig(ModBloecke.TOR, ModBloecke.GITTERFALLE, ModBloecke.SCHIEBETUER, ModBloecke.PRUNKTUER,
                ModBloecke.GLASTUER, ModBloecke.STAHLTUER, ModBloecke.BUNKERTUER, ModBloecke.PRIVATTUER,
                ModBloecke.SICHERHEITSTUER, ModBloecke.CLUBTUER, ModBloecke.BANKTUER, ModBloecke.PORTALTUER);
        KompatClient.durchsichtig(de.moinmornhart.stinkendreich.register.ModDeko.BLOECKE.values().toArray(new net.minecraft.world.level.block.Block[0]));

        // Butler: eigenes Modell (Fabric hat die Klasse dafür in 26.x umbenannt) und Renderer
        //? if >=26.1 {
        net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(ButlerModell.SCHICHT, ButlerModell::schicht);
        //?} else
        /*net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(ButlerModell.SCHICHT, ButlerModell::schicht);*/
        EntityRendererRegistry.register(ModEntities.BUTLER, ButlerRenderer::new);
        // Fahrzeuge: Modell aus dem Baukasten, Renderer je Typ, Tasten, Tacho, Motorgeräusch
        for (Autotyp art : Autotyp.values()) {
            //? if >=26.1 {
            net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(FahrzeugModell.schicht(art.name), () -> FahrzeugModell.schichtAusDatei(art.name));
            //?} else
            /*net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(FahrzeugModell.schicht(art.name), () -> FahrzeugModell.schichtAusDatei(art.name));*/
            EntityRendererRegistry.register(ModEntities.auto(art), k -> new AutoRenderer(k, art));
        }
        // Aufzugskabine: Modell, Renderer, Fahrstuhlmusik
        //? if >=26.1 {
        net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(AufzugModell.SCHICHT, AufzugModell::schicht);
        //?} else
        /*net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(AufzugModell.SCHICHT, AufzugModell::schicht);*/
        EntityRendererRegistry.register(ModEntities.AUFZUG, AufzugRenderer::new);
        AufzugMusik.registrieren();
        // XXL-Fernseher
        //? if >=26.1 {
        net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(FernseherModell.SCHICHT, FernseherModell::schicht);
        //?} else
        /*net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(FernseherModell.SCHICHT, FernseherModell::schicht);*/
        EntityRendererRegistry.register(ModEntities.FERNSEHER, FernseherRenderer::new);
        KlientBruecke.fernseher = id -> KompatClient.zeige(new FernseherBildschirm(id));
        de.moinmornhart.stinkendreich.client.fernseher.FernseherVideos.registrieren();
        // Konzertflügel: Spielfenster mit Klaviatur
        KlientBruecke.klavier = pos -> KompatClient.zeige(new de.moinmornhart.stinkendreich.client.klavier.KlavierBildschirm(pos));
        // Sitzplätze auf Möbeln sind unsichtbar
        EntityRendererRegistry.register(ModEntities.SITZ, net.minecraft.client.renderer.entity.NoopRenderer::new);
        // Drehteller in der Tiefgarage
        //? if >=26.1 {
        net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(
                de.moinmornhart.stinkendreich.client.drehteller.DrehtellerModell.SCHICHT, de.moinmornhart.stinkendreich.client.drehteller.DrehtellerModell::schicht);
        //?} else
        /*net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(de.moinmornhart.stinkendreich.client.drehteller.DrehtellerModell.SCHICHT, de.moinmornhart.stinkendreich.client.drehteller.DrehtellerModell::schicht);*/
        EntityRendererRegistry.register(ModEntities.DREHTELLER, de.moinmornhart.stinkendreich.client.drehteller.DrehtellerRenderer::new);
        // Show-Tänzerinnen im Geheimclub
        //? if >=26.1 {
        net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(
                de.moinmornhart.stinkendreich.client.club.TaenzerinModell.SCHICHT, de.moinmornhart.stinkendreich.client.club.TaenzerinModell::schicht);
        //?} else
        /*net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(de.moinmornhart.stinkendreich.client.club.TaenzerinModell.SCHICHT, de.moinmornhart.stinkendreich.client.club.TaenzerinModell::schicht);*/
        EntityRendererRegistry.register(ModEntities.HAI, de.moinmornhart.stinkendreich.client.hai.HaiRenderer::new);
        EntityRendererRegistry.register(ModEntities.TAENZERIN, de.moinmornhart.stinkendreich.client.club.TaenzerinRenderer::new);
        EntityRendererRegistry.register(ModEntities.MITARBEITER, de.moinmornhart.stinkendreich.client.mitarbeiter.MitarbeiterRenderer::new);
        EntityRendererRegistry.register(ModEntities.BODYGUARD, de.moinmornhart.stinkendreich.client.bodyguard.BodyguardRenderer::new);
        KlientBruecke.handy = () -> KompatClient.zeige(new de.moinmornhart.stinkendreich.client.handy.HandyBildschirm());
        AutoKlient.registrieren();
        UpdateKlient.registrieren();
        // Dem Server zeigen, dass dieser Client die Mod hat (für die Server-Version, siehe ModErkennung)
        net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking.registerGlobalReceiver(
                de.moinmornhart.stinkendreich.netz.ModErkennung.Hallo.TYP, (paket, kontext) -> { });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                de.moinmornhart.stinkendreich.netz.ModErkennung.Hallo.TYP, (paket, kontext) -> { });
        // Daten vom Server für Bildschirme der Mod (z. B. die Orte, die James kennt, für das Handy-Navi)
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                de.moinmornhart.stinkendreich.netz.Rueckmeldung.TYP, (paket, kontext) -> kontext.client().execute(() -> {
                    if (paket.art().equals("orte")) {
                        de.moinmornhart.stinkendreich.client.handy.HandyBildschirm.orteEmpfangen(paket.wert());
                    } else if (paket.art().equals("smart")) {
                        de.moinmornhart.stinkendreich.client.handy.HandyBildschirm.smartEmpfangen(paket.wert());
                    }
                }));
        // Musikanlage: eigene Dateien abspielen, Spotify-Fernbedienung
        MusikKlient.registrieren();
        AutotestKlient.registrieren();

        // Begrüßung im Chat, sobald man eine Welt betritt
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() ->
                KompatClient.chatNachricht(client, Component.translatable(
                        "stinkendreich.willkommen", StinkendReich.VERSION, StinkendReich.MINECRAFT
                ).withStyle(ChatFormatting.GOLD))));
    }
}
