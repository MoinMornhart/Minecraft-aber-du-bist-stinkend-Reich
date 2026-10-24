package de.moinmornhart.stinkendreich.register;

import net.minecraft.world.item.Item;

/** Items der Mod. */
public final class ModItems {
    /** Zugangskarte: öffnet Kartenleser und automatische Tore der Villa. */
    public static final Item ZUGANGSKARTE = ModRegister.item("zugangskarte", Item::new, new Item.Properties().stacksTo(1));

    private ModItems() {
    }

    public static void init() {
        // Laden der Klasse registriert die Items
    }
}
