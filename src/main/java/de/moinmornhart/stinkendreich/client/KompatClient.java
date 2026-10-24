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

    /** Schickt eine Aktion an den Server. */
    public static void sende(String art, BlockPos pos, String wert) {
        ClientPlayNetworking.send(new Aktion(art, pos, wert));
    }
}
