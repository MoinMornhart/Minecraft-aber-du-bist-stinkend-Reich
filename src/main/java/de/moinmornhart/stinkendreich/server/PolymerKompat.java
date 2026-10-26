package de.moinmornhart.stinkendreich.server;

//? if >=1.21.6 {
import io.netty.buffer.Unpooled;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
//? if >=26.1 {
import eu.pb4.polymer.virtualentity.api.data.DisplayEntityData;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
//?} else {
/*import eu.pb4.polymer.virtualentity.api.tracker.DisplayTrackedData;
import xyz.nucleoid.packettweaker.PacketContext;
*///?}

/**
 * Was sich zwischen den Polymer-Versionen unterscheidet, an einer Stelle: Polymer 0.14/0.15 (Minecraft 1.21.9 bis
 * 1.21.11) hat den Paket-Kontext aus „packet-tweaker“ und die Anzeige-Datenfelder unter einem anderen Namen als
 * Polymer 0.16+ (26.x). Pakete für Kamera und Mitfahrer baut diese Klasse selbst (gleich in allen Versionen).
 */
final class PolymerKompat {
    // Anzeige-Datenfelder: in Polymer 0.14 mit Vector3f/Quaternionf, ab 0.15 mit Vector3fc/Quaternionfc, deshalb als
    // fertige Datenwerte statt als Felder
    //? if >=26.1 {
    static SynchedEntityData.DataValue<?> skalierung(Vector3f v) {
        return SynchedEntityData.DataValue.create(DisplayEntityData.SCALE, v);
    }

    static SynchedEntityData.DataValue<?> drehungLinks(Quaternionf q) {
        return SynchedEntityData.DataValue.create(DisplayEntityData.LEFT_ROTATION, q);
    }

    static SynchedEntityData.DataValue<?> verschiebung(Vector3f v) {
        return SynchedEntityData.DataValue.create(DisplayEntityData.TRANSLATION, v);
    }

    static SynchedEntityData.DataValue<?> teleportDauer(int takte) {
        return SynchedEntityData.DataValue.create(DisplayEntityData.TELEPORTATION_DURATION, takte);
    }

    static SynchedEntityData.DataValue<?> sichtweite(float weite) {
        return SynchedEntityData.DataValue.create(DisplayEntityData.VIEW_RANGE, weite);
    }

    static SynchedEntityData.DataValue<?> helligkeit(int licht) {
        return SynchedEntityData.DataValue.create(DisplayEntityData.BRIGHTNESS, licht);
    }

    static SynchedEntityData.DataValue<?> item(ItemStack stapel) {
        return SynchedEntityData.DataValue.create(DisplayEntityData.Item.ITEM, stapel);
    }

    static SynchedEntityData.DataValue<?> itemArt(byte art) {
        return SynchedEntityData.DataValue.create(DisplayEntityData.Item.ITEM_DISPLAY, art);
    }
    //?} else {
    /*static SynchedEntityData.DataValue<?> skalierung(Vector3f v) {
        return SynchedEntityData.DataValue.create(DisplayTrackedData.SCALE, v);
    }

    static SynchedEntityData.DataValue<?> drehungLinks(Quaternionf q) {
        return SynchedEntityData.DataValue.create(DisplayTrackedData.LEFT_ROTATION, q);
    }

    static SynchedEntityData.DataValue<?> verschiebung(Vector3f v) {
        return SynchedEntityData.DataValue.create(DisplayTrackedData.TRANSLATION, v);
    }

    static SynchedEntityData.DataValue<?> teleportDauer(int takte) {
        return SynchedEntityData.DataValue.create(DisplayTrackedData.TELEPORTATION_DURATION, takte);
    }

    static SynchedEntityData.DataValue<?> sichtweite(float weite) {
        return SynchedEntityData.DataValue.create(DisplayTrackedData.VIEW_RANGE, weite);
    }

    static SynchedEntityData.DataValue<?> helligkeit(int licht) {
        return SynchedEntityData.DataValue.create(DisplayTrackedData.BRIGHTNESS, licht);
    }

    static SynchedEntityData.DataValue<?> item(ItemStack stapel) {
        return SynchedEntityData.DataValue.create(DisplayTrackedData.Item.ITEM, stapel);
    }

    static SynchedEntityData.DataValue<?> itemArt(byte art) {
        return SynchedEntityData.DataValue.create(DisplayTrackedData.Item.ITEM_DISPLAY, art);
    }
    *///?}

    private PolymerKompat() {
    }

    /** Die Verbindung zum Empfänger eines Pakets (null, wenn es keinen gibt). */
    static Connection verbindung(PacketContext kontext) {
        //? if >=26.1 {
        return kontext.get(PacketContext.CONNECTION);
        //?} else
        /*return kontext.getClientConnection();*/
    }

    /** Paket: Die Kamera des Clients schaut durch die Figur mit dieser Nummer. */
    static ClientboundSetCameraPacket kamera(int figur) {
        FriendlyByteBuf puffer = new FriendlyByteBuf(Unpooled.buffer());
        puffer.writeVarInt(figur);
        return ClientboundSetCameraPacket.STREAM_CODEC.decode(puffer);
    }

    /** Paket: Diese Figuren sitzen auf dem Fahrzeug mit der Nummer {@code fahrzeug} (leer = niemand). */
    static ClientboundSetPassengersPacket mitfahrer(int fahrzeug, int[] mitfahrer) {
        FriendlyByteBuf puffer = new FriendlyByteBuf(Unpooled.buffer());
        puffer.writeVarInt(fahrzeug);
        puffer.writeVarIntArray(mitfahrer);
        return ClientboundSetPassengersPacket.STREAM_CODEC.decode(puffer);
    }
}
//?}
