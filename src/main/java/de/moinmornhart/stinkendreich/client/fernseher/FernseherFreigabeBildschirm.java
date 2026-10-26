package de.moinmornhart.stinkendreich.client.fernseher;

import de.moinmornhart.stinkendreich.Konfig;
import de.moinmornhart.stinkendreich.client.KompatClient;
import de.moinmornhart.stinkendreich.client.bildschirm.MenueBildschirm;
import de.moinmornhart.stinkendreich.fernseher.FernseherEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Fragt einmal, ob die Mod die Hilfsprogramme für Videos (yt-dlp, FFmpeg, Deno) herunterladen darf. Die Antwort
 * steht danach in der Config ({@code fernseherHilfsprogrammeLaden}); „Nein“ zeigt nur das Vorschaubild.
 */
public class FernseherFreigabeBildschirm extends MenueBildschirm {
    private final int tvId;
    private final String kennung;

    public FernseherFreigabeBildschirm(int tvId, String kennung) {
        super(Component.translatable("stinkendreich.fernseher.freigabe.titel"));
        this.tvId = tvId;
        this.kennung = kennung;
    }

    @Override
    protected int gesamtHoehe() {
        return 150;
    }

    @Override
    protected void knoepfe() {
        textzeile(Component.translatable("stinkendreich.fernseher.freigabe.zeile1"));
        textzeile(Component.translatable("stinkendreich.fernseher.freigabe.zeile2"));
        textzeile(Component.translatable("stinkendreich.fernseher.freigabe.zeile3"));
        weiter(6);
        knopf(Component.translatable("stinkendreich.fernseher.freigabe.ja"), () -> {
            Konfig.get().fernseherHilfsprogrammeLaden = true;
            Konfig.speichern();
            VideoWerkzeuge.laden();
            senden("!abspielen|" + kennung);
            schliessen();
        });
        knopf(Component.translatable("stinkendreich.fernseher.freigabe.nein"), () -> {
            senden(kennung);
            schliessen();
        });
    }

    private void senden(String befehl) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getEntity(tvId) instanceof FernseherEntity tv) {
            KompatClient.sende("fernseher", tv.blockPosition(), tv.getId() + "|" + befehl);
        }
    }
}
