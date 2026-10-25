package de.moinmornhart.stinkendreich.client.fernseher;

import de.moinmornhart.stinkendreich.client.KompatClient;
import de.moinmornhart.stinkendreich.client.bildschirm.MenueBildschirm;
import de.moinmornhart.stinkendreich.fernseher.FernseherEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.net.URI;

/**
 * Menü des Fernsehers: YouTube-Link einfügen (Strg + V), auf dem Fernseher anzeigen, auf dem PC abspielen
 * oder ausschalten. Gültig sind Links wie youtube.com/watch?v=…, youtu.be/…, youtube.com/shorts/… .
 */
public class FernseherBildschirm extends MenueBildschirm {
    private final int tvId;
    private EditBox eingabe;

    public FernseherBildschirm(int tvId) {
        super(Component.translatable("stinkendreich.fernseher.titel"));
        this.tvId = tvId;
    }

    private FernseherEntity tv() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getEntity(tvId) instanceof FernseherEntity t ? t : null;
    }

    @Override
    protected void knoepfe() {
        FernseherEntity tv = tv();
        String jetzt = tv == null ? "" : tv.video();
        if (!jetzt.isEmpty()) {
            String titel = FernseherBild.titel(jetzt);
            textzeile(Component.translatable("stinkendreich.fernseher.laeuft", titel.isEmpty() ? jetzt : titel));
        }
        eingabe = new EditBox(font, width / 2 - BREITE / 2, aktuellesY(), BREITE, HOEHE,
                Component.translatable("stinkendreich.fernseher.link"));
        eingabe.setMaxLength(300);
        eingabe.setHint(Component.translatable("stinkendreich.fernseher.link"));
        addRenderableWidget(eingabe);
        setInitialFocus(eingabe);
        weiter(HOEHE + 4);
        knopf(Component.translatable("stinkendreich.fernseher.anzeigen"), () -> {
            String kennung = FernseherEntity.kennung(eingabe.getValue());
            if (kennung.isEmpty()) {
                eingabe.setValue("");
                eingabe.setHint(Component.translatable("stinkendreich.fernseher.ungueltig"));
                return;
            }
            senden(kennung);
            schliessen();
        });
        knopf(Component.translatable("stinkendreich.fernseher.abspielen"), () -> {
            String kennung = FernseherEntity.kennung(eingabe.getValue());
            if (kennung.isEmpty() && tv() != null) {
                kennung = tv().video();
            }
            if (!kennung.isEmpty()) {
                if (!kennung.equals(tv() == null ? "" : tv().video())) {
                    senden(kennung);
                }
                KompatClient.oeffneLink(URI.create("https://www.youtube.com/watch?v=" + kennung));
                schliessen();
            }
        });
        knopf(Component.translatable("stinkendreich.fernseher.aus"), () -> {
            senden("");
            schliessen();
        });
        textzeile(Component.translatable("stinkendreich.fernseher.hinweis"));
        knopf(Component.translatable("gui.done"), this::schliessen);
    }

    private void senden(String kennung) {
        FernseherEntity tv = tv();
        if (tv != null) {
            KompatClient.sende("fernseher", tv.blockPosition(), tv.getId() + "|" + kennung);
        }
    }
}
