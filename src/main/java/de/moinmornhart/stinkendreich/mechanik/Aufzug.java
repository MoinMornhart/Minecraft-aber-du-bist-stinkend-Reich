package de.moinmornhart.stinkendreich.mechanik;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.netz.Netz;
import de.moinmornhart.stinkendreich.register.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Bestellt die Aufzugskabine. Die Tafel vor dem Schacht schickt Start- und Zielhaltestelle; die Kabine
 * ({@link AufzugKabine}) kommt, nimmt alle vor der Tür mit und fährt zum Ziel.
 *
 * <p>Wo die Kabine steht, sagen Werte in der Block-Entity der Tafel (vom Bauplan gesetzt):
 * {@code kabine_dx}, {@code kabine_dz} = Mitte der Kabine relativ zur Mitte des Tafelblocks,
 * {@code kabine_dy} = Fußhöhe relativ zur Tafel. Ohne Werte liegt der Schacht direkt hinter der Tafel.</p>
 */
public final class Aufzug {
    private Aufzug() {
    }

    public static void registrieren() {
        Netz.bearbeiter("aufzug", 6, (spieler, start, wert) -> {
            try {
                BlockPos ziel = BlockPos.of(Long.parseLong(wert));
                if (ziel.getX() != start.getX() || ziel.getZ() != start.getZ()
                        || !(spieler.level().getBlockState(ziel).getBlock() instanceof AufzugstafelBlock)
                        || !(spieler.level().getBlockState(start).getBlock() instanceof AufzugstafelBlock)) {
                    return;
                }
                bestellen(spieler, start, ziel);
            } catch (NumberFormatException ignoriert) {
                // ungültiges Ziel
            }
        });
    }

    /** Lage der Kabine zu einer Tafel. */
    private record Schacht(double x, double z, double fuss, Direction tuer) {
    }

    private static Schacht schacht(ServerLevel welt, BlockPos tafel) {
        Direction vorne = welt.getBlockState(tafel).getValue(Eigenschaften.RICHTUNG);
        MechanikBlockEntity d = MechanikBlock.daten(welt, tafel);
        double dx = -vorne.getStepX() * 1.5, dz = -vorne.getStepZ() * 1.5, dy = -1;
        if (d != null) {
            dx = zahl(d.text("kabine_dx", ""), dx);
            dz = zahl(d.text("kabine_dz", ""), dz);
            dy = zahl(d.text("kabine_dy", ""), dy);
        }
        return new Schacht(tafel.getX() + 0.5 + dx, tafel.getZ() + 0.5 + dz, tafel.getY() + dy, vorne);
    }

    private static double zahl(String text, double sonst) {
        try {
            return text.isEmpty() ? sonst : Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return sonst;
        }
    }

    private static void bestellen(ServerPlayer spieler, BlockPos start, BlockPos ziel) {
        ServerLevel welt = (ServerLevel) spieler.level();
        Schacht von = schacht(welt, start);
        Schacht nach = schacht(welt, ziel);
        AABB saeule = new AABB(von.x() - 1.2, Kompat.minY(welt), von.z() - 1.2, von.x() + 1.2, Kompat.maxY(welt), von.z() + 1.2);
        List<AufzugKabine> kabinen = welt.getEntitiesOfClass(AufzugKabine.class, saeule, k -> true);
        AufzugKabine kabine;
        if (kabinen.isEmpty()) {
            kabine = ModEntities.AUFZUG.create(welt
                    //? if >=1.21.2
                    , net.minecraft.world.entity.EntitySpawnReason.TRIGGERED
            );
            if (kabine == null) {
                return;
            }
            kabine.aufstellen(von.x(), von.fuss(), von.z(), von.tuer());
            welt.addFreshEntity(kabine);
        } else {
            kabine = kabinen.get(0);
            // Doppelte Kabinen (z. B. nach /summon) aufräumen
            for (int i = 1; i < kabinen.size(); i++) {
                kabinen.get(i).discard();
            }
        }
        kabine.bestellen(von.fuss(), nach.fuss());
    }
}
