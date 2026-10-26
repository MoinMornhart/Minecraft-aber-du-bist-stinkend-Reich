package de.moinmornhart.stinkendreich.fernseher;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.mechanik.KlientBruecke;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Fernbedienung: öffnet das Menü des nächsten Fernsehers (bis 32 Blöcke), egal ob man sie in der Hand hält oder sie
 * irgendwo liegt (z. B. auf dem Nachttisch). Mit Mod der Bildschirm der Mod, ohne Mod (Server-Version) das
 * Dialogfenster des Fernsehers.
 */
public final class Fernbedienung {
    private static final double REICHWEITE = 32;

    private Fernbedienung() {
    }

    /** Nächster Fernseher um {@code von} oder null. */
    public static FernseherEntity naechster(Level welt, Vec3 von) {
        FernseherEntity bester = null;
        for (FernseherEntity tv : welt.getEntitiesOfClass(FernseherEntity.class, new AABB(von, von).inflate(REICHWEITE))) {
            if (bester == null || tv.distanceToSqr(von) < bester.distanceToSqr(von)) {
                bester = tv;
            }
        }
        return bester;
    }

    /** Menü des nächsten Fernsehers öffnen (beide Seiten aufrufen: Client zeigt, Server hilft Spielern ohne Mod). */
    public static void bedienen(Level welt, Player spieler, Vec3 von) {
        FernseherEntity tv = naechster(welt, von);
        if (welt.isClientSide()) {
            if (tv != null) {
                KlientBruecke.fernseher.accept(tv.getId());
            }
            return;
        }
        if (tv == null) {
            Kompat.nachricht(spieler, Component.translatable("stinkendreich.fernbedienung.keiner"), true);
            return;
        }
        if (spieler instanceof net.minecraft.server.level.ServerPlayer sp) {
            de.moinmornhart.stinkendreich.netz.OhneMod.zeigen(sp, "fernseher", tv.blockPosition());
        }
    }
}
