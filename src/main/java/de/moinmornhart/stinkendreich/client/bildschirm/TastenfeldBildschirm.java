package de.moinmornhart.stinkendreich.client.bildschirm;

import de.moinmornhart.stinkendreich.client.KompatClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Ziffernfeld der Passwort-Tür. Zeigt die getippten Ziffern als Sternchen.
 * „Code ändern“: erst alten Code, dann neuen Code eingeben.
 */
public class TastenfeldBildschirm extends MenueBildschirm {
    private final BlockPos pos;
    private String eingabe = "";
    private String alterCode;
    private boolean aendern;
    private StringWidget anzeige;

    public TastenfeldBildschirm(BlockPos pos) {
        super(Component.translatable("block.stinkendreich.tastenfeld"));
        this.pos = pos;
    }

    @Override
    protected void knoepfe() {
        anzeige = new StringWidget(Component.literal(""), font);
        anzeige.setY(aktuellesY());
        addRenderableWidget(anzeige);
        anzeigeAktualisieren();
        weiter(16);
        int x0 = width / 2 - 50;
        int y0 = aktuellesY();
        String[] tasten = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "OK"};
        for (int i = 0; i < tasten.length; i++) {
            String t = tasten[i];
            Button b = Button.builder(Component.literal(t), x -> taste(t))
                    .bounds(x0 + (i % 3) * 34, y0 + (i / 3) * 24, 32, 20).build();
            addRenderableWidget(b);
        }
        weiter(4 * 24 + 6);
        knopf(Component.translatable("stinkendreich.tastenfeld.aendern"), () -> {
            aendern = true;
            alterCode = null;
            eingabe = "";
            anzeigeAktualisieren();
        });
        knopf(Component.translatable("gui.cancel"), this::schliessen);
    }

    private void taste(String t) {
        switch (t) {
            case "C" -> eingabe = "";
            case "OK" -> {
                if (!aendern) {
                    KompatClient.sende("tastenfeld", pos, eingabe);
                    schliessen();
                    return;
                }
                if (alterCode == null) {
                    alterCode = eingabe;
                    eingabe = "";
                } else {
                    KompatClient.sende("tastenfeld_neu", pos, alterCode + ":" + eingabe);
                    schliessen();
                    return;
                }
            }
            default -> {
                if (eingabe.length() < 8) {
                    eingabe += t;
                }
            }
        }
        anzeigeAktualisieren();
    }

    private void anzeigeAktualisieren() {
        String hinweis = !aendern ? "stinkendreich.tastenfeld.code"
                : alterCode == null ? "stinkendreich.tastenfeld.alt" : "stinkendreich.tastenfeld.neu";
        Component text = Component.translatable(hinweis).append(": " + "*".repeat(eingabe.length()) + "_");
        anzeige.setMessage(text);
        anzeige.setWidth(font.width(text));
        anzeige.setX(width / 2 - font.width(text) / 2);
    }
}
