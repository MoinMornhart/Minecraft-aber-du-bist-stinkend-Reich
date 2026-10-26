package de.moinmornhart.stinkendreich.client.bildschirm;

import de.moinmornhart.stinkendreich.client.KompatClient;
import de.moinmornhart.stinkendreich.mechanik.SicherheitszentraleBlock;
import de.moinmornhart.stinkendreich.mechanik.VillaZustand;
import net.minecraft.client.Minecraft;
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
        // Stufe 4 (Festung) gibt es nur an der Zentrale in der Kommandozentrale im Bunker
        boolean bunker = SicherheitszentraleBlock.kommandozentrale(Minecraft.getInstance().level, pos);
        for (int stufe = 0; stufe <= (bunker ? VillaZustand.MAX_STUFE : VillaZustand.FESTUNG - 1); stufe++) {
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
