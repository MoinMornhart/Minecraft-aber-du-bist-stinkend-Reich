package de.moinmornhart.stinkendreich.paper;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Dialogfenster von Minecraft (ab 1.21.6) für Spieler ohne Mod: Butler-Menü, Handy, Aufzug, Tastenfeld … Jeder Knopf
 * ruft direkt Code im Plugin auf (Paper merkt sich den Rückruf).
 */
public final class Dialoge {
    /** Ein Knopf mit Text und dem, was er tut (bekommt den Spieler und die Eingaben des Fensters). */
    public record Knopf(Component text, BiConsumer<Player, DialogResponseView> aktion) {
        public static Knopf von(String text, BiConsumer<Player, DialogResponseView> aktion) {
            return new Knopf(Component.text(text), aktion);
        }
    }

    private Dialoge() {
    }

    /** Fenster mit Titel, Text, optionalen Eingaben und Knöpfen (zwei Spalten). */
    public static void zeigen(Player p, String titel, String text, List<DialogInput> eingaben, List<Knopf> knoepfe) {
        zeigen(p, titel, text, eingaben, knoepfe, 2, 150, false);
    }

    /**
     * Fenster mit frei wählbaren Spalten und Knopfbreite; {@code offenLassen}: das Fenster bleibt nach einem Klick offen
     * und jeder Knopf geht beliebig oft (Klaviatur des Flügels).
     */
    public static void zeigen(Player p, String titel, String text, List<DialogInput> eingaben, List<Knopf> knoepfe,
                              int spalten, int breite, boolean offenLassen) {
        List<ActionButton> buttons = new ArrayList<>();
        ClickCallback.Options optionen = ClickCallback.Options.builder()
                .uses(offenLassen ? ClickCallback.UNLIMITED_USES : 1).lifetime(Duration.ofMinutes(offenLassen ? 30 : 10)).build();
        for (Knopf k : knoepfe) {
            buttons.add(ActionButton.create(k.text(), null, breite, DialogAction.customClick((antwort, wer) -> {
                if (wer instanceof Player spieler) {
                    k.aktion().accept(spieler, antwort);
                }
            }, optionen)));
        }
        List<DialogBody> koerper = text == null || text.isEmpty() ? List.of()
                : List.of(DialogBody.plainMessage(Component.text(text)));
        DialogBase basis = DialogBase.builder(Component.text(titel, NamedTextColor.GOLD))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(offenLassen ? DialogBase.DialogAfterAction.NONE : DialogBase.DialogAfterAction.CLOSE)
                .body(koerper)
                .inputs(eingaben)
                .build();
        Dialog fenster = Dialog.create(f -> f.empty().base(basis).type(DialogType.multiAction(buttons).columns(spalten).build()));
        p.showDialog(fenster);
    }
}
