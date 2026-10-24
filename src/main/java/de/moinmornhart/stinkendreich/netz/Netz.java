package de.moinmornhart.stinkendreich.netz;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;

/** Verteilt eingehende {@link Aktion}-Pakete an die zuständigen Mechanismen. */
public final class Netz {
    /** Bearbeitet eine Aktion auf dem Server. */
    public interface Bearbeiter {
        void ausfuehren(ServerPlayer spieler, BlockPos pos, String wert);
    }

    private record Eintrag(Bearbeiter bearbeiter, double reichweite) {
    }

    private static final Map<String, Eintrag> BEARBEITER = new HashMap<>();

    private Netz() {
    }

    public static void registrieren() {
        // Fabric hat die Methode für 26.x umbenannt
        //? if >=26.1 {
        PayloadTypeRegistry.serverboundPlay().register(Aktion.TYP, Aktion.CODEC);
        //?} else
        /*PayloadTypeRegistry.playC2S().register(Aktion.TYP, Aktion.CODEC);*/
        ServerPlayNetworking.registerGlobalReceiver(Aktion.TYP, (aktion, kontext) -> kontext.server().execute(() -> {
            Eintrag e = BEARBEITER.get(aktion.art());
            ServerPlayer spieler = kontext.player();
            // Nur Aktionen in Reichweite annehmen (Schutz gegen manipulierte Pakete)
            if (e != null && (e.reichweite() <= 0 || spieler.distanceToSqr(aktion.pos().getX() + 0.5,
                    aktion.pos().getY() + 0.5, aktion.pos().getZ() + 0.5) <= e.reichweite() * e.reichweite())) {
                e.bearbeiter().ausfuehren(spieler, aktion.pos(), aktion.wert());
            }
        }));
    }

    /** Meldet einen Bearbeiter an; {@code reichweite} in Blöcken (0 = ohne Prüfung). */
    public static void bearbeiter(String art, double reichweite, Bearbeiter bearbeiter) {
        BEARBEITER.put(art, new Eintrag(bearbeiter, reichweite));
    }
}
