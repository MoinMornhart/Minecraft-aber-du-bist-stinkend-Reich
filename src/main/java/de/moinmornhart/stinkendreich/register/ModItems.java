package de.moinmornhart.stinkendreich.register;

import net.minecraft.world.item.Item;

/** Items der Mod. */
public final class ModItems {
    /** Zugangskarte: öffnet Kartenleser und automatische Tore der Villa. */
    public static final Item ZUGANGSKARTE = ModRegister.item("zugangskarte", Item::new, new Item.Properties().stacksTo(1));
    /** Butler-Glöckchen: ruft den eigenen Butler herbei. */
    public static final Item BUTLERGLOCKE = ModRegister.item("butlerglocke",
            de.moinmornhart.stinkendreich.butler.ButlerglockeItem::new, new Item.Properties().stacksTo(1));
    /** Das eingepackte Bonzenauto. */
    public static final Item BONZENAUTO = ModRegister.item("bonzenauto",
            de.moinmornhart.stinkendreich.auto.AutoItem::new, new Item.Properties().stacksTo(1));

    private ModItems() {
    }

    public static void init() {
        // Laden der Klasse registriert die Items
    }
}
