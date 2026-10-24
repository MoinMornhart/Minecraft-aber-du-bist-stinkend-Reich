package de.moinmornhart.stinkendreich.client.bildschirm;

import de.moinmornhart.stinkendreich.client.KompatClient;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Bedienfeld der Sicherheitszentrale. */
public class ZentraleBildschirm extends MenueBildschirm {
    private final BlockPos pos;

    public ZentraleBildschirm(BlockPos pos) {
        super(Component.translatable("block.stinkendreich.sicherheitszentrale"));
        this.pos = pos;
    }

    @Override
    protected void knoepfe() {
        for (int stufe = 0; stufe <= 3; stufe++) {
            int s = stufe;
            knopf(Component.translatable("stinkendreich.stufe." + s), () -> senden("stufe:" + s));
        }
        weiter(6);
        knopf(Component.translatable("stinkendreich.zentrale.alarm_aus"), () -> senden("alarm_aus"));
        knopf(Component.translatable("stinkendreich.zentrale.eintragen"), () -> senden("eintragen"));
        knopf(Component.translatable("gui.done"), this::schliessen);
    }

    private void senden(String wert) {
        KompatClient.sende("zentrale", pos, wert);
        schliessen();
    }
}
