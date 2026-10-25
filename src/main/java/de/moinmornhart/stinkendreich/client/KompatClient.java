package de.moinmornhart.stinkendreich.client;

import de.moinmornhart.stinkendreich.netz.Aktion;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Bündelt alle Client-Aufrufe, die sich zwischen den Minecraft-Versionen unterscheiden.
 *
 * Die Zeilen mit {@code //?} sind Stonecutter-Bedingungen: Beim Bauen für eine Version
 * bleibt nur der passende Zweig aktiv, die anderen werden auskommentiert.
 */
public final class KompatClient {
    private KompatClient() {
    }

    /** Zeigt eine Nachricht nur lokal im Chat an. */
    public static void chatNachricht(Minecraft client, Component text) {
        //? if >=26.2 {
        client.gui.hud.getChat().addClientSystemMessage(text);
        //?} else if >=26.1 {
        /*client.gui.getChat().addClientSystemMessage(text);
        *///?} else {
        /*client.gui.getChat().addMessage(text);
        *///?}
    }

    /** Öffnet einen Bildschirm (ab 26.2 über die Gui-Klasse); null schließt ihn. */
    public static void zeige(Screen bildschirm) {
        Minecraft client = Minecraft.getInstance();
        //? if >=26.2 {
        client.gui.setScreen(bildschirm);
        //?} else
        /*client.setScreen(bildschirm);*/
    }

    /** Klick im Chat führt einen Befehl aus (ab 1.21.5 eigene Klassen je Klickart). */
    public static net.minecraft.network.chat.ClickEvent klickBefehl(String befehl) {
        //? if >=1.21.5 {
        return new net.minecraft.network.chat.ClickEvent.RunCommand(befehl);
        //?} else
        /*return new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, befehl);*/
    }

    /** Klick im Chat öffnet eine Internetseite. */
    public static net.minecraft.network.chat.ClickEvent klickLink(String adresse) {
        //? if >=1.21.5 {
        return new net.minecraft.network.chat.ClickEvent.OpenUrl(java.net.URI.create(adresse));
        //?} else
        /*return new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.OPEN_URL, adresse);*/
    }

    /** Öffnet eine Internetseite im Browser (26.3: Blaze3D, ab 1.21.11: Util liegt in net.minecraft.util). */
    public static void oeffneLink(java.net.URI adresse) {
        //? if >=26.3 {
        com.mojang.blaze3d.Blaze3D.openUri(adresse);
        //?} else if >=1.21.11 {
        /*net.minecraft.util.Util.getPlatform().openUri(adresse);
        *///?} else {
        /*net.minecraft.Util.getPlatform().openUri(adresse);
        *///?}
    }

    /** Öffnet einen Ordner im Dateimanager des Systems. */
    public static void oeffneOrdner(java.nio.file.Path ordner) {
        //? if >=26.3 {
        com.mojang.blaze3d.Blaze3D.openPath(ordner);
        //?} else if >=1.21.11 {
        /*net.minecraft.util.Util.getPlatform().openPath(ordner);
        *///?} else {
        /*net.minecraft.Util.getPlatform().openPath(ordner);
        *///?}
    }

    /** Schickt eine Aktion an den Server. */
    public static void sende(String art, BlockPos pos, String wert) {
        ClientPlayNetworking.send(new Aktion(art, pos, wert));
    }
}
