package de.moinmornhart.stinkendreich.netz;

import de.moinmornhart.stinkendreich.StinkendReich;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Ein Paket vom Client zum Server: „Spieler hat an Block {@code pos} die Aktion {@code art}
 * mit dem Wert {@code wert} ausgelöst“ (z. B. Code am Tastenfeld, Etage im Aufzug).
 */
public record Aktion(String art, BlockPos pos, String wert) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<Aktion> TYP = new CustomPacketPayload.Type<>(StinkendReich.id("aktion"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Aktion> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Aktion::art,
            BlockPos.STREAM_CODEC, Aktion::pos,
            ByteBufCodecs.STRING_UTF8, Aktion::wert,
            Aktion::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYP;
    }
}
