package de.moinmornhart.stinkendreich.client.bildschirm;

import de.moinmornhart.stinkendreich.client.KompatClient;
import de.moinmornhart.stinkendreich.mechanik.AufzugstafelBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Etagenwahl im Aufzug: ein Knopf pro Haltestelle, von oben nach unten. */
public class AufzugBildschirm extends MenueBildschirm {
    private final BlockPos pos;

    public AufzugBildschirm(BlockPos pos) {
        super(Component.translatable("block.stinkendreich.aufzugstafel"));
        this.pos = pos;
    }

    @Override
    protected void knoepfe() {
        var welt = Minecraft.getInstance().level;
        List<BlockPos> halte = AufzugstafelBlock.haltestellen(welt, pos);
        for (int i = halte.size() - 1; i >= 0; i--) {
            BlockPos ziel = halte.get(i);
            String name = AufzugstafelBlock.name(welt, ziel);
            if (ziel.equals(pos)) {
                knopf(Component.literal("● " + name), () -> { }).active = false;
            } else {
                // Haltestellen nur mit Zugangskarte (Bunker) tragen einen Hinweis; geprüft wird auf dem Server
                Component text = AufzugstafelBlock.nurMitKarte(welt, ziel)
                        ? Component.literal(name + " ").append(Component.translatable("stinkendreich.aufzug.mit_karte")
                                .withStyle(ChatFormatting.GOLD))
                        : Component.literal(name);
                knopf(text, () -> {
                    KompatClient.sende("aufzug", pos, String.valueOf(ziel.asLong()));
                    schliessen();
                });
            }
        }
        weiter(6);
        knopf(Component.translatable("gui.cancel"), this::schliessen);
    }
}
