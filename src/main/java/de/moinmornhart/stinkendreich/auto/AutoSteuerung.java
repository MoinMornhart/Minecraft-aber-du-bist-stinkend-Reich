package de.moinmornhart.stinkendreich.auto;

import de.moinmornhart.stinkendreich.netz.Netz;
import net.minecraft.world.entity.Entity;

/**
 * Server-Seite der Autotasten: Hupe (H) und Scheinwerfer (L). Nur der Fahrer darf sie benutzen.
 */
public final class AutoSteuerung {
    private AutoSteuerung() {
    }

    public static void registrieren() {
        Netz.bearbeiter("auto", 0, (spieler, pos, wert) -> {
            Entity fahrzeug = spieler.getVehicle();
            if (!(fahrzeug instanceof AutoEntity auto) || auto.getControllingPassenger() != spieler) {
                return;
            }
            switch (wert) {
                case "hupe" -> auto.hupen();
                case "licht" -> auto.lichtUmschalten();
                default -> {
                }
            }
        });
    }
}
