package de.moinmornhart.stinkendreich.mechanik;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21.6 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?}

/**
 * Grundlage für alle Block-Entities der Mod. Die Daten werden als ein JSON-Text gespeichert;
 * so bleibt das Speichern in allen Minecraft-Versionen gleich, obwohl Mojang die NBT-Schnittstelle
 * in 1.21.5 und 1.21.6 umgebaut hat.
 */
public abstract class DatenBlockEntity extends BlockEntity {
    private static final String SCHLUESSEL = "stinkendreich";

    protected DatenBlockEntity(BlockEntityType<?> typ, BlockPos pos, BlockState zustand) {
        super(typ, pos, zustand);
    }

    /** Eigene Werte in das JSON-Objekt schreiben. */
    protected abstract void schreiben(JsonObject daten);

    /** Eigene Werte aus dem JSON-Objekt lesen (fehlende Werte: Standard behalten). */
    protected abstract void lesen(JsonObject daten);

    private String alsText() {
        JsonObject j = new JsonObject();
        schreiben(j);
        return j.toString();
    }

    private void ausText(String text) {
        if (text != null && !text.isEmpty()) {
            try {
                lesen(JsonParser.parseString(text).getAsJsonObject());
            } catch (Exception ignoriert) {
                // kaputte Daten: Standardwerte behalten
            }
        }
    }

    /** Nach Änderungen aufrufen: speichert und schickt die Daten an die Clients. */
    protected void geaendert() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // Abgleich mit dem Client (z. B. Etagennamen am Aufzug)
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider p) {
        return saveWithoutMetadata(p);
    }

    //? if >=1.21.6 {
    @Override
    protected void saveAdditional(ValueOutput ausgabe) {
        super.saveAdditional(ausgabe);
        ausgabe.putString(SCHLUESSEL, alsText());
    }

    @Override
    protected void loadAdditional(ValueInput eingabe) {
        super.loadAdditional(eingabe);
        ausText(eingabe.getStringOr(SCHLUESSEL, ""));
    }
    //?} else if >=1.21.5 {
    /*@Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider p) {
        super.saveAdditional(tag, p);
        tag.putString(SCHLUESSEL, alsText());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider p) {
        super.loadAdditional(tag, p);
        ausText(tag.getStringOr(SCHLUESSEL, ""));
    }
    *///?} else {
    /*@Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider p) {
        super.saveAdditional(tag, p);
        tag.putString(SCHLUESSEL, alsText());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider p) {
        super.loadAdditional(tag, p);
        ausText(tag.getString(SCHLUESSEL));
    }
    *///?}
}
