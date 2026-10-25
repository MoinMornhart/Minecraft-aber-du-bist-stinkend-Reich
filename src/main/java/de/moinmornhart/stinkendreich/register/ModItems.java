package de.moinmornhart.stinkendreich.register;

import de.moinmornhart.stinkendreich.auto.AutoItem;
import de.moinmornhart.stinkendreich.auto.Autotyp;
import de.moinmornhart.stinkendreich.ausruestung.AgentRuestung;
import de.moinmornhart.stinkendreich.ausruestung.AgentWaffe;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

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
    public static final Item HUBSCHRAUBER = autoItem(Autotyp.HUBSCHRAUBER);

    /** XXL-Fernseher zum Aufhängen. */
    public static final Item FERNSEHER = ModRegister.item("fernseher",
            de.moinmornhart.stinkendreich.fernseher.FernseherItem::new, new Item.Properties().stacksTo(1));

    /** Agenten-Anzug (Kräfte siehe AgentKraefte). */
    public static final Item BOND_SONNENBRILLE = AgentRuestung.teil("bond_sonnenbrille", AgentRuestung.Teil.KOPF);
    public static final Item SMOKING = AgentRuestung.teil("smoking", AgentRuestung.Teil.BRUST);
    public static final Item ANZUGHOSE = AgentRuestung.teil("anzughose", AgentRuestung.Teil.BEINE);
    public static final Item LACKSCHUHE = AgentRuestung.teil("lackschuhe", AgentRuestung.Teil.FUESSE);
    /** Agenten-Waffen. */
    public static final Item GOLDENE_PISTOLE = waffe("goldene_pistole", AgentWaffe.Art.PISTOLE, "stinkendreich.agent.pistole");
    public static final Item LASERUHR = waffe("laseruhr", AgentWaffe.Art.LASER, "stinkendreich.agent.laser");
    public static final Item GELDKANONE = waffe("geldkanone", AgentWaffe.Art.GELDKANONE, "stinkendreich.agent.geld");

    private static Item waffe(String name, AgentWaffe.Art art, String beschreibung) {
        return ModRegister.item(name, p -> new AgentWaffe(p, art), new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
                .component(DataComponents.LORE, AgentRuestung.beschreibung(beschreibung)));
    }

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
            case HUBSCHRAUBER -> HUBSCHRAUBER;
        };
    }

    private ModItems() {
    }

    public static void init() {
        // Laden der Klasse registriert die Items
    }
}
