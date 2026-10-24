package de.moinmornhart.stinkendreich;

import de.moinmornhart.stinkendreich.dev.Autotest;
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
        Autotest.registrieren();
    }

    /** Erzeugt eine ID im Namensraum der Mod, z. B. {@code stinkendreich:villa}. */
    public static Identifier id(String pfad) {
        return Identifier.fromNamespaceAndPath(MOD_ID, pfad);
    }
}
