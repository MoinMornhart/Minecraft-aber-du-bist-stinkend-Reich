package de.moinmornhart.stinkendreich.fernseher;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.mechanik.KlientBruecke;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Fernbedienung in der Hand: öffnet das Menü des Fernsehers, den man anschaut, sonst des nächsten (bis 32 Blöcke).
 * Mit Mod der Bildschirm der Mod, ohne Mod (Server-Version) das Dialogfenster des Fernsehers. Liegt sie irgendwo
 * (z. B. auf dem Nachttisch), nimmt ein Rechtsklick sie in die Hand ({@link FernbedienungBlock}).
 */
public final class Fernbedienung {
    private static final double REICHWEITE = 32;
    /** Bis zu diesem Winkel zur Blickrichtung gilt ein Fernseher als „angeschaut“. */
    private static final double BLICKWINKEL = Math.cos(Math.toRadians(30));

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

    /**
     * Fernseher, den der Spieler gerade anschaut (kleinster Winkel zur Blickrichtung, höchstens 30°), sonst der
     * nächste. So steuert man im Penthouse den Fernseher vor sich und nicht den, der zufällig näher hängt.
     */
    public static FernseherEntity ziel(Level welt, Player spieler) {
        Vec3 auge = spieler.getEyePosition();
        Vec3 blick = spieler.getViewVector(1.0F);
        FernseherEntity bester = null;
        double besterWinkel = BLICKWINKEL;
        for (FernseherEntity tv : welt.getEntitiesOfClass(FernseherEntity.class, new AABB(auge, auge).inflate(REICHWEITE))) {
            Vec3 hin = tv.getBoundingBox().getCenter().subtract(auge);
            double winkel = hin.lengthSqr() < 1.0E-6 ? 1.0 : hin.normalize().dot(blick);
            if (winkel > besterWinkel) {
                besterWinkel = winkel;
                bester = tv;
            }
        }
        return bester != null ? bester : naechster(welt, spieler.position());
    }

    /** Menü des Fernsehers öffnen, den der Spieler anschaut (beide Seiten aufrufen: Client zeigt, Server hilft Spielern ohne Mod). */
    public static void bedienen(Level welt, Player spieler) {
        FernseherEntity tv = ziel(welt, spieler);
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
