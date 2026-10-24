package de.moinmornhart.stinkendreich.client;

import net.minecraft.client.Minecraft;
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
}
