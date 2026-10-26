package de.moinmornhart.stinkendreich.server;

//? if >=1.21.6 {
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.CommandTemplate;
import net.minecraft.server.dialog.action.ParsedTemplate;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Das Butler-Menü (Rechtsklick auf James) für Spieler ohne Mod: dieselben Knöpfe wie der Bildschirm der Mod
 * ({@code client/bildschirm/ButlerBildschirm}), als Dialogfenster von Minecraft. Jeder Knopf schickt
 * {@code /butler knopf <Nummer> <Befehl>} an den Server.
 */
public final class ButlerDialog {
    private static final int SPALTE = 120;

    /** Knöpfe paarweise wie im Bildschirm der Mod: Text-Schlüssel und Schnellbefehl. */
    private static final String[][] KNOEPFE = {
            {"folgen", "!folg mir"}, {"bleiben", "!bleib hier"},
            {"heim", "!geh nach hause"}, {"komm", "!komm her"},
            {"eisen", "!hol 32 eisen"}, {"kohle", "!hol 32 kohle"},
            {"holz", "!hol 32 holz"}, {"diamanten", "!hol 8 diamanten"},
            {"essen", "!hol 8 essen"}, {"kochen", "!koch etwas"},
            {"fackeln", "!mach 16 fackeln"}, {"sortieren", "!sortier die truhen"},
            {"inventar", "!inventar"}, {"stopp", "!stopp"},
    };

    private ButlerDialog() {
    }

    public static void oeffnen(ServerPlayer spieler, ButlerEntity butler) {
        int id = butler.getId();
        List<ActionButton> knoepfe = new ArrayList<>();
        for (String[] k : KNOEPFE) {
            knoepfe.add(new ActionButton(new CommonButtonData(Component.translatable("stinkendreich.butler.menue." + k[0]), SPALTE),
                    Optional.of(new StaticAction(new ClickEvent.RunCommand("/butler knopf " + id + " " + k[1])))));
        }
        // Freier Auftrag: der Text aus dem Feld kommt in den Befehl
        List<Input> eingaben = List.of(new Input("text", new TextInput(2 * SPALTE + 4,
                Component.translatable("stinkendreich.butler.menue.eingabe"), true, "", 200, Optional.empty())));
        knoepfe.add(new ActionButton(new CommonButtonData(Component.translatable("stinkendreich.butler.menue.senden"), SPALTE),
                Optional.of(new CommandTemplate(ParsedTemplate.CODEC.parse(JsonOps.INSTANCE,
                        new JsonPrimitive("butler knopf " + id + " $(text)")).getOrThrow()))));
        CommonDialogData daten = new CommonDialogData(
                Component.translatable("stinkendreich.butler.menue.titel", butler.getName()), Optional.empty(), true, false,
                DialogAction.CLOSE, List.of(new PlainMessage(butler.status(), 2 * SPALTE)), eingaben);
        spieler.openDialog(Holder.direct(new MultiActionDialog(daten, knoepfe, Optional.empty(), 2)));
    }
}
//?}
