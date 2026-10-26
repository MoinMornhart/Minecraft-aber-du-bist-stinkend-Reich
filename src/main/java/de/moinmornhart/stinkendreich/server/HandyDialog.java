package de.moinmornhart.stinkendreich.server;

//? if >=1.21.10 {
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import de.moinmornhart.stinkendreich.butler.ButlerVerwaltung;
import de.moinmornhart.stinkendreich.handy.Lieferservice;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.action.CommandTemplate;
import net.minecraft.server.dialog.action.ParsedTemplate;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Das goldene Handy für Spieler ohne Mod: dieselben Apps wie der Handy-Bildschirm der Mod, aber als Dialogfenster,
 * das Minecraft selbst mitbringt (seit 1.21.6). Jeder Knopf schickt den Befehl {@code /handy …} an den Server
 * (siehe {@code handy.Handy}); Seitenwechsel öffnen einfach den nächsten Dialog.
 */
public final class HandyDialog {
    private static final int BREITE = 200;

    private HandyDialog() {
    }

    /** Öffnet eine Seite des Handys: start, navi, lieferung oder auftrag. */
    public static void oeffnen(ServerPlayer spieler, String seite) {
        List<ActionButton> knoepfe = new ArrayList<>();
        List<Input> eingaben = new ArrayList<>();
        String titel = "start";
        switch (seite) {
            case "navi" -> {
                titel = "navi";
                knopf(knoepfe, "navi.villa", "fahr|#villa");
                for (String ziel : List.of("dorf", "endportal", "netherportal", "mine")) {
                    knopf(knoepfe, "navi." + ziel, "fahr|" + ziel);
                }
                ButlerEntity b = ButlerVerwaltung.vonSpieler(spieler);
                if (b != null) {
                    for (String ort : b.orte().keySet()) {
                        knoepfe.add(new ActionButton(new CommonButtonData(Component.literal(ort), BREITE),
                                Optional.of(befehl("fahr|" + ort))));
                    }
                }
                zurueck(knoepfe);
            }
            case "lieferung" -> {
                titel = "lieferung";
                for (String was : Lieferservice.ANGEBOT) {
                    knopf(knoepfe, "liefer." + was, "liefer|" + was);
                }
                zurueck(knoepfe);
            }
            case "auftrag" -> {
                titel = "auftrag";
                for (String a : List.of("eisen", "diamanten", "holz", "essen", "sortieren", "heim")) {
                    knopf(knoepfe, "auftrag." + a, "auftrag|" + Component.translatable("stinkendreich.handy.auftrag." + a + ".text").getString());
                }
                // Eigener Auftrag: Textfeld, der Knopf setzt den Text in den Befehl ein
                eingaben.add(new Input("text", new TextInput(BREITE, Component.translatable("stinkendreich.handy.auftrag.frei"),
                        true, "", 120, Optional.empty())));
                knoepfe.add(new ActionButton(new CommonButtonData(Component.translatable("stinkendreich.handy.auftrag.senden"), BREITE),
                        Optional.of(new CommandTemplate(ParsedTemplate.CODEC.parse(JsonOps.INSTANCE,
                                new JsonPrimitive("handy auftrag|$(text)")).getOrThrow()))));
                zurueck(knoepfe);
            }
            default -> {
                knopf(knoepfe, "anruf_limo", "anruf|limo");
                knopf(knoepfe, "anruf_heli", "anruf|heli");
                knopf(knoepfe, "navi", "seite|navi");
                knopf(knoepfe, "lieferung", "seite|lieferung");
                knopf(knoepfe, "auftraege", "seite|auftrag");
                knopf(knoepfe, "mitarbeiter", "mitarbeiter");
                knopf(knoepfe, "bodyguards", "bodyguards|rufen");
                knopf(knoepfe, "bodyguards_heim", "bodyguards|heim");
            }
        }
        CommonDialogData daten = new CommonDialogData(Component.translatable("stinkendreich.handy.titel." + titel),
                Optional.empty(), true, false, DialogAction.CLOSE, List.of(), eingaben);
        spieler.openDialog(Holder.direct(new MultiActionDialog(daten, knoepfe, Optional.empty(), 1)));
    }

    private static void knopf(List<ActionButton> knoepfe, String text, String aktion) {
        knoepfe.add(new ActionButton(new CommonButtonData(Component.translatable("stinkendreich.handy." + text), BREITE),
                Optional.of(befehl(aktion))));
    }

    private static void zurueck(List<ActionButton> knoepfe) {
        knopf(knoepfe, "zurueck", "seite|start");
    }

    private static Action befehl(String aktion) {
        return new StaticAction(new ClickEvent.RunCommand("/handy " + aktion));
    }
}
//?}
