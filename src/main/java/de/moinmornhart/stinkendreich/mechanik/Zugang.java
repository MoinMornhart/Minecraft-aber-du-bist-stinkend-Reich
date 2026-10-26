package de.moinmornhart.stinkendreich.mechanik;

import de.moinmornhart.stinkendreich.register.ModItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Prüfungen rund um Zugangskarten und Fehlversuche an Kartenleser und Tastenfeld. */
public final class Zugang {
    private static final Map<UUID, int[]> FEHLVERSUCHE = new HashMap<>();

    private Zugang() {
    }

    /** Trägt der Spieler irgendwo im Inventar eine Zugangskarte (egal für welche Villa)? */
    public static boolean hatKarte(Player spieler) {
        return hatKarte(spieler, -1);
    }

    /**
     * Trägt der Spieler eine Zugangskarte für die Villa mit dieser Nummer (-1 = irgendeine)? Karten ohne Nummer
     * (von vor 0.4) gelten für die Spawn-Villa.
     */
    public static boolean hatKarte(Player spieler, int villa) {
        var inv = spieler.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.is(ModItems.ZUGANGSKARTE) && (villa < 0 || villaDerKarte(s) == villa)) {
                return true;
            }
        }
        return false;
    }

    /** Nummer der Villa, für die die Karte gilt (0 = Spawn-Villa, auch bei alten Karten). */
    public static int villaDerKarte(ItemStack karte) {
        var daten = karte.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (daten == null) {
            return 0;
        }
        var tag = daten.copyTag();
        //? if >=1.21.5 {
        return tag.getIntOr("stinkendreich_villa", 0);
        //?} else
        /*return tag.contains("stinkendreich_villa") ? tag.getInt("stinkendreich_villa") : 0;*/
    }

    /** Eine neue Zugangskarte für diese Villa; bei Nachbarvillen steht der Besitzer im Namen. */
    public static ItemStack karte(de.moinmornhart.stinkendreich.villa.VillaPlatzierung.VillaInfo villa) {
        ItemStack karte = new ItemStack(ModItems.ZUGANGSKARTE);
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("stinkendreich_villa", villa.nr());
        karte.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        if (!villa.besitzerName().isEmpty()) {
            karte.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    net.minecraft.network.chat.Component.translatable("stinkendreich.karte.von", villa.besitzerName())
                            .withStyle(s -> s.withItalic(false).withColor(net.minecraft.ChatFormatting.GOLD)));
        }
        return karte;
    }

    /**
     * Zählt einen Fehlversuch. Gibt true zurück, wenn es der dritte in Folge war (dann Alarm).
     */
    public static boolean fehlversuch(Player spieler) {
        int[] zaehler = FEHLVERSUCHE.computeIfAbsent(spieler.getUUID(), u -> new int[1]);
        zaehler[0]++;
        if (zaehler[0] >= 3) {
            zaehler[0] = 0;
            return true;
        }
        return false;
    }

    public static void erfolg(Player spieler) {
        FEHLVERSUCHE.remove(spieler.getUUID());
    }
}
