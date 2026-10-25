package de.moinmornhart.stinkendreich.register;

import de.moinmornhart.stinkendreich.auto.AutoItem;
import de.moinmornhart.stinkendreich.auto.Autotyp;
import net.minecraft.world.item.Item;

/** Items der Mod. */
public final class ModItems {
    /** Zugangskarte: öffnet Kartenleser und automatische Tore der Villa. */
    public static final Item ZUGANGSKARTE = ModRegister.item("zugangskarte", Item::new, new Item.Properties().stacksTo(1));
    /** Butler-Glöckchen: ruft den eigenen Butler herbei. */
    public static final Item BUTLERGLOCKE = ModRegister.item("butlerglocke",
            de.moinmornhart.stinkendreich.butler.ButlerglockeItem::new, new Item.Properties().stacksTo(1));
    /** Die eingepackten Fahrzeuge (Rechtsklick auf den Boden stellt sie ab). */
    public static final Item BONZENAUTO = autoItem(Autotyp.BONZENAUTO);
    public static final Item GELAENDEWAGEN = autoItem(Autotyp.GELAENDEWAGEN);
    public static final Item SUPERSPORTWAGEN = autoItem(Autotyp.SUPERSPORTWAGEN);
    public static final Item STRETCHLIMO = autoItem(Autotyp.STRETCHLIMO);

    /** XXL-Fernseher zum Aufhängen. */
    public static final Item FERNSEHER = ModRegister.item("fernseher",
            de.moinmornhart.stinkendreich.fernseher.FernseherItem::new, new Item.Properties().stacksTo(1));

    private static Item autoItem(Autotyp art) {
        return ModRegister.item(art.name, p -> new AutoItem(p, art), new Item.Properties().stacksTo(1));
    }

    /** Item zu einem Fahrzeug. */
    public static Item auto(Autotyp art) {
        return switch (art) {
            case BONZENAUTO -> BONZENAUTO;
            case GELAENDEWAGEN -> GELAENDEWAGEN;
            case SUPERSPORTWAGEN -> SUPERSPORTWAGEN;
            case STRETCHLIMO -> STRETCHLIMO;
        };
    }

    private ModItems() {
    }

    public static void init() {
        // Laden der Klasse registriert die Items
    }
}
