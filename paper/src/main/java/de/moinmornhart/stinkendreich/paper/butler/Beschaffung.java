package de.moinmornhart.stinkendreich.paper.butler;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Etwas, das James draußen beschafft, bevor er es liefert: Erz ({@link Bergbau}) oder Holz ({@link Holzfaeller}). */
interface Beschaffung {
    Bergbau.Stand tick();

    Bergbau.Rohstoff rohstoff();

    int gesammelt();

    /** Was er eingepackt hat. */
    List<ItemStack> beute();

    String status();

    void abbrechen();

    /** Am Ende: Werkzeug weg. */
    void aufraeumen();
}
