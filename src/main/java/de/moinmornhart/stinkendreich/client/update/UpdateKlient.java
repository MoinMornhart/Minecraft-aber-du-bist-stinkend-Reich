package de.moinmornhart.stinkendreich.client.update;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.client.KompatClient;
import de.moinmornhart.stinkendreich.update.UpdatePruefer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Update-Hinweis im Spiel: Gibt es eine neuere Version für diese Minecraft-Version, erscheint
 * beim Betreten einer Welt eine Nachricht mit „[Herunterladen]“ (spielt das Update beim Beenden
 * ein) und „[Release-Seite]“.
 */
public final class UpdateKlient {
    private static final String BEFEHL = "stinkendreich-update";
    private static boolean gemeldet;

    private UpdateKlient() {
    }

    public static void registrieren() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> UpdatePruefer.pruefen());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> melden(client)));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, kontext) -> dispatcher.register(
                LiteralArgumentBuilder.<FabricClientCommandSource>literal(BEFEHL).executes(k -> {
                    Minecraft client = Minecraft.getInstance();
                    KompatClient.chatNachricht(client, Component.translatable("stinkendreich.update.laedt").withStyle(ChatFormatting.GRAY));
                    UpdatePruefer.herunterladen().thenAccept(schluessel -> client.execute(() -> KompatClient.chatNachricht(client,
                            Component.translatable(schluessel, UpdatePruefer.gefunden().map(UpdatePruefer.Neu::version).orElse(""))
                                    .withStyle(ChatFormatting.GOLD))));
                    return 1;
                })));
    }

    private static void melden(Minecraft client) {
        if (gemeldet || UpdatePruefer.heruntergeladen()) {
            return;
        }
        UpdatePruefer.gefunden().ifPresent(neu -> {
            gemeldet = true;
            Component laden = Component.translatable("stinkendreich.update.knopf.laden").withStyle(s -> s
                    .withColor(ChatFormatting.GREEN).withUnderlined(true).withClickEvent(KompatClient.klickBefehl("/" + BEFEHL)));
            Component seite = Component.translatable("stinkendreich.update.knopf.seite").withStyle(s -> s
                    .withColor(ChatFormatting.AQUA).withUnderlined(true).withClickEvent(KompatClient.klickLink(neu.seite())));
            KompatClient.chatNachricht(client, Component.translatable("stinkendreich.update.neu", neu.version(), StinkendReich.MINECRAFT)
                    .withStyle(ChatFormatting.GOLD).append(" ").append(laden).append(" ").append(seite));
        });
    }
}
