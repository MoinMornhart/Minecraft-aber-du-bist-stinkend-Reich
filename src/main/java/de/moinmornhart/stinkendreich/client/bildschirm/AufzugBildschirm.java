package de.moinmornhart.stinkendreich.client.bildschirm;

import de.moinmornhart.stinkendreich.client.KompatClient;
import de.moinmornhart.stinkendreich.mechanik.AufzugstafelBlock;
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
                knopf(Component.literal(name), () -> {
                    KompatClient.sende("aufzug", pos, String.valueOf(ziel.asLong()));
                    schliessen();
                });
            }
        }
        weiter(6);
        knopf(Component.translatable("gui.cancel"), this::schliessen);
    }
}
