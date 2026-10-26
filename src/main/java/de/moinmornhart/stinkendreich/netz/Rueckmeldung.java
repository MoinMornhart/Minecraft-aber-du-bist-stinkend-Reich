package de.moinmornhart.stinkendreich.netz;

import de.moinmornhart.stinkendreich.StinkendReich;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Ein Paket vom Server zum Client: Daten für einen Bildschirm der Mod, z. B. „orte“ mit den Namen der Orte, die
 * James kennt (für das Navi im Handy), eine Zeile je Ort.
 */
public record Rueckmeldung(String art, String wert) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<Rueckmeldung> TYP = new CustomPacketPayload.Type<>(StinkendReich.id("rueckmeldung"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Rueckmeldung> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Rueckmeldung::art,
            ByteBufCodecs.STRING_UTF8, Rueckmeldung::wert,
            Rueckmeldung::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYP;
    }
}
