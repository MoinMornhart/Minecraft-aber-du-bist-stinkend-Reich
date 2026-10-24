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

    /** Trägt der Spieler irgendwo im Inventar eine Zugangskarte? */
    public static boolean hatKarte(Player spieler) {
        var inv = spieler.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.is(ModItems.ZUGANGSKARTE)) {
                return true;
            }
        }
        return false;
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
