package de.moinmornhart.stinkendreich.netz;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Bedienfenster für Spieler ohne Mod (Server-Version). Die Blöcke rufen auf dem Server {@link #zeigen} auf; die
 * Server-Version ({@code server/ServerPaket}, nur wenn Polymer installiert ist) meldet dafür Dialogfenster an.
 * Ohne Anmeldung passiert nichts, Spieler mit Mod bekommen ihren eigenen Bildschirm.
 */
public final class OhneMod {
    /** Öffnet ein Fenster für einen Spieler ohne Mod. */
    public interface Fenster {
        void oeffnen(ServerPlayer spieler, BlockPos pos);
    }

    private static final Map<String, Fenster> FENSTER = new HashMap<>();
    /** Wann (Server-Takt) ein Spieler zuletzt einen Block benutzt oder ein Fenster bekommen hat. */
    private static final Map<java.util.UUID, Integer> ZULETZT = new HashMap<>();

    private OhneMod() {
    }

    public static void anmelden(String art, Fenster fenster) {
        FENSTER.put(art, fenster);
    }

    /** Zeigt dem Spieler das Fenster {@code art}, falls er die Mod nicht hat und es eins gibt. */
    public static void zeigen(Player spieler, String art, BlockPos pos) {
        Fenster f = FENSTER.get(art);
        if (de.moinmornhart.stinkendreich.dev.Autotest.aktiv() && spieler instanceof ServerPlayer sp) {
            de.moinmornhart.stinkendreich.StinkendReich.LOGGER.info("Autotest: Fenster {} bei {} für {} (Takt {}, Fenster bekannt: {})",
                    art, pos, sp.getScoreboardName(), sp.level().getServer().getTickCount(), f != null);
        }
        if (f != null && spieler instanceof ServerPlayer sp && !ModErkennung.hatMod(sp)) {
            f.oeffnen(sp, pos);
            benutzt(sp);
        }
    }

    /** Der Spieler hat gerade einen Block benutzt (Fenster geöffnet, Tür auf …). */
    public static void benutzt(ServerPlayer spieler) {
        ZULETZT.put(spieler.getUUID(), spieler.level().getServer().getTickCount());
    }

    /**
     * Hat der Spieler gerade (in diesem oder dem letzten Takt) einen Block benutzt? Minecraft ohne Mod schickt nach
     * dem Klick auf einen Block noch „Item benutzen“ hinterher; das Handy in der Hand darf dann nicht zusätzlich
     * aufgehen (und ein gerade geöffnetes Fenster überdecken).
     */
    public static boolean geradeGezeigt(ServerPlayer spieler) {
        Integer t = ZULETZT.get(spieler.getUUID());
        return t != null && spieler.level().getServer().getTickCount() - t <= 1;
    }
}
