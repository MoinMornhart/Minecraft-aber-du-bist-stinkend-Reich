package de.moinmornhart.stinkendreich.client.bildschirm;

import de.moinmornhart.stinkendreich.client.KompatClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Einfacher Bildschirm mit Titel und senkrechter Knopfliste. Zeichnet nichts selbst,
 * damit er in allen Minecraft-Versionen gleich funktioniert (nur Widgets).
 */
public abstract class MenueBildschirm extends Screen {
    protected static final int BREITE = 200;
    protected static final int HOEHE = 20;
    private int naechstesY;

    protected MenueBildschirm(Component titel) {
        super(titel);
    }

    @Override
    protected void init() {
        naechstesY = Math.max(10, height / 2 - gesamtHoehe() / 2);
        StringWidget titelText = new StringWidget(getTitle(), font);
        titelText.setX(width / 2 - font.width(getTitle()) / 2);
        titelText.setY(naechstesY);
        addRenderableWidget(titelText);
        naechstesY += 18;
        knoepfe();
    }

    /** Ungefähre Höhe des Inhalts in Pixeln, damit er senkrecht mittig steht. */
    protected int gesamtHoehe() {
        return 200;
    }

    /** Unterklassen fügen hier ihre Knöpfe mit {@link #knopf} hinzu. */
    protected abstract void knoepfe();

    protected Button knopf(Component text, Runnable aktion) {
        Button b = Button.builder(text, x -> aktion.run()).bounds(width / 2 - BREITE / 2, naechstesY, BREITE, HOEHE).build();
        naechstesY += HOEHE + 4;
        return addRenderableWidget(b);
    }

    protected void textzeile(Component text) {
        StringWidget w = new StringWidget(text, font);
        w.setX(width / 2 - font.width(text) / 2);
        w.setY(naechstesY + 4);
        addRenderableWidget(w);
        naechstesY += 16;
    }

    protected int aktuellesY() {
        return naechstesY;
    }

    protected void weiter(int pixel) {
        naechstesY += pixel;
    }

    protected void schliessen() {
        KompatClient.zeige(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
