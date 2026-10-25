package de.moinmornhart.stinkendreich;

import de.moinmornhart.stinkendreich.auto.AutoSteuerung;
import de.moinmornhart.stinkendreich.butler.ButlerBefehle;
import de.moinmornhart.stinkendreich.butler.ButlerVerwaltung;
import de.moinmornhart.stinkendreich.butler.SpielerBloecke;
import de.moinmornhart.stinkendreich.dev.Autotest;
import de.moinmornhart.stinkendreich.mechanik.Aufzug;
import de.moinmornhart.stinkendreich.mechanik.SicherheitszentraleBlock;
import de.moinmornhart.stinkendreich.mechanik.TastenfeldBlock;
import de.moinmornhart.stinkendreich.mechanik.VillaZustand;
import de.moinmornhart.stinkendreich.netz.Netz;
import de.moinmornhart.stinkendreich.register.ModBloecke;
import de.moinmornhart.stinkendreich.register.ModEntities;
import de.moinmornhart.stinkendreich.register.ModItems;
import de.moinmornhart.stinkendreich.register.ModSounds;
import de.moinmornhart.stinkendreich.register.ModTab;
import de.moinmornhart.stinkendreich.villa.VillaBefehle;
import de.moinmornhart.stinkendreich.villa.VillaPlatzierung;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Einstiegspunkt der Mod „Minecraft, aber du bist stinkend reich“ (Server und Client).
 */
public class StinkendReich implements ModInitializer {
    public static final String MOD_ID = "stinkendreich";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Mod-Version, wird von Stonecutter beim Bauen eingesetzt. */
    public static final String VERSION = /*$ mod_version*/ "0.1.0";
    /** Minecraft-Version, für die diese .jar gebaut wurde. */
    public static final String MINECRAFT = /*$ minecraft*/ "26.3";

    @Override
    public void onInitialize() {
        LOGGER.info("Stinkend Reich {} für Minecraft {} startet", VERSION, MINECRAFT);
        Konfig.laden();
        ModSounds.init();
        ModItems.init();
        ModBloecke.init();
        ModEntities.init();
        ModTab.init();
        Netz.registrieren();
        VillaZustand.registrieren();
        Aufzug.registrieren();
        SicherheitszentraleBlock.aktionenRegistrieren();
        TastenfeldBlock.aktionenRegistrieren();
        SpielerBloecke.registrieren();
        // Vor der Villa, damit die Butler-Liste geladen ist, wenn die Villa ihren Butler einstellt
        ButlerVerwaltung.registrieren();
        VillaPlatzierung.registrieren();
        VillaBefehle.registrieren();
        ButlerBefehle.registrieren();
        AutoSteuerung.registrieren();
        Autotest.registrieren();
    }

    /** Erzeugt eine ID im Namensraum der Mod, z. B. {@code stinkendreich:villa}. */
    public static Identifier id(String pfad) {
        return Identifier.fromNamespaceAndPath(MOD_ID, pfad);
    }
}
