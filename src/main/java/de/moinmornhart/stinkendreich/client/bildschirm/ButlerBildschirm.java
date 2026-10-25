package de.moinmornhart.stinkendreich.client.bildschirm;

import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import de.moinmornhart.stinkendreich.client.KompatClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * Menü des Butlers (Rechtsklick): zeigt, was er gerade tut, und bietet Knöpfe für die häufigsten
 * Aufträge sowie ein Textfeld für freie Aufträge („hol 20 Glas und leg es in die Truhe“).
 */
public class ButlerBildschirm extends MenueBildschirm {
    private static final int SPALTE = 98;

    private final int butlerId;
    private StringWidget statusZeile;
    private EditBox eingabe;

    public ButlerBildschirm(int butlerId) {
        super(Component.translatable("stinkendreich.butler.menue.titel", name(butlerId)));
        this.butlerId = butlerId;
    }

    private static Component name(int id) {
        Entity e = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(id);
        return e == null ? Component.literal("Butler") : e.getName();
    }

    private ButlerEntity butler() {
        Entity e = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(butlerId);
        return e instanceof ButlerEntity b ? b : null;
    }

    @Override
    protected int gesamtHoehe() {
        return 300;
    }

    @Override
    protected void knoepfe() {
        statusZeile = new StringWidget(Component.empty(), font);
        statusZeile.setY(aktuellesY());
        addRenderableWidget(statusZeile);
        statusAktualisieren();
        weiter(16);

        paar("folgen", "!folg mir", "bleiben", "!bleib hier");
        paar("heim", "!geh nach hause", "komm", "!komm her");
        paar("eisen", "!hol 32 eisen", "kohle", "!hol 32 kohle");
        paar("holz", "!hol 32 holz", "diamanten", "!hol 8 diamanten");
        paar("essen", "!hol 8 essen", "kochen", "!koch etwas");
        paar("fackeln", "!mach 16 fackeln", "sortieren", "!sortier die truhen");
        paar("inventar", "!inventar", "stopp", "!stopp");

        weiter(4);
        int x = width / 2 - BREITE / 2;
        eingabe = new EditBox(font, x, aktuellesY(), BREITE, HOEHE, Component.translatable("stinkendreich.butler.menue.eingabe"));
        eingabe.setMaxLength(200);
        eingabe.setHint(Component.translatable("stinkendreich.butler.menue.eingabe"));
        addRenderableWidget(eingabe);
        weiter(HOEHE + 4);
        knopf(Component.translatable("stinkendreich.butler.menue.senden"), () -> {
            String text = eingabe.getValue().trim();
            if (!text.isEmpty()) {
                senden(text);
            }
        });
        knopf(Component.translatable("gui.done"), this::schliessen);
    }

    /** Zwei Knöpfe nebeneinander. */
    private void paar(String links, String befehlLinks, String rechts, String befehlRechts) {
        int x = width / 2 - BREITE / 2;
        int y = aktuellesY();
        addRenderableWidget(Button.builder(Component.translatable("stinkendreich.butler.menue." + links), b -> senden(befehlLinks))
                .bounds(x, y, SPALTE, HOEHE).build());
        addRenderableWidget(Button.builder(Component.translatable("stinkendreich.butler.menue." + rechts), b -> senden(befehlRechts))
                .bounds(x + BREITE - SPALTE, y, SPALTE, HOEHE).build());
        weiter(HOEHE + 4);
    }

    private void senden(String befehl) {
        ButlerEntity b = butler();
        if (b != null) {
            KompatClient.sende("butler", b.blockPosition(), butlerId + "|" + befehl);
        }
        schliessen();
    }

    private void statusAktualisieren() {
        ButlerEntity b = butler();
        Component text = b == null ? Component.empty() : b.status();
        statusZeile.setMessage(text);
        statusZeile.setWidth(font.width(text));
        statusZeile.setX(width / 2 - font.width(text) / 2);
    }

    @Override
    public void tick() {
        super.tick();
        if (butler() == null) {
            schliessen();
            return;
        }
        statusAktualisieren();
    }
}
