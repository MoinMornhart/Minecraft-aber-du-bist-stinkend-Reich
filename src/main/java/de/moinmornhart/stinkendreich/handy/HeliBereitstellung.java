package de.moinmornhart.stinkendreich.handy;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.auto.Autotyp;
import de.moinmornhart.stinkendreich.auto.HubschrauberEntity;
import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import de.moinmornhart.stinkendreich.butler.Pilot;
import de.moinmornhart.stinkendreich.register.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Für „mit dem Hubschrauber“ (Navi): Steht kein freier Hubschrauber in der Nähe (96 Blöcke), wird der nächste freie
 * (bis 512 Blöcke, etwa der auf dem Landeplatz der Villa) 40 bis 70 Blöcke vom Herrn entfernt bereitgestellt – gibt es
 * keinen, ein neuer. James steigt ein und fliegt von dort zum Herrn (siehe FlugAuftrag). Ein weit entfernter Hubschrauber
 * steht meist in einem Bereich, in dem nichts tickt; James würde dort nie losfliegen.
 */
final class HeliBereitstellung {
    private HeliBereitstellung() {
    }

    static void bereitstellen(ServerPlayer herr, ButlerEntity butler) {
        if (!(herr.level() instanceof ServerLevel welt)) {
            return;
        }
        HubschrauberEntity naechster = welt.getEntitiesOfClass(HubschrauberEntity.class, herr.getBoundingBox().inflate(512),
                        h -> h.getPassengers().stream().allMatch(e -> e == butler)).stream()
                .min(java.util.Comparator.comparingDouble(h -> h.distanceToSqr(herr))).orElse(null);
        if (naechster != null && naechster.distanceTo(herr) <= 96) {
            return;
        }
        net.minecraft.world.phys.Vec3 platz = Pilot.landestelle(welt, herr.blockPosition(), 40, 70);
        if (platz == null) {
            return;
        }
        if (naechster != null) {
            naechster.ejectPassengers();
            naechster.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            Kompat.versetzen(naechster, platz.x, platz.y, platz.z, herr.getYRot(), 0.0F);
        } else {
            HubschrauberEntity heli = (HubschrauberEntity) ModEntities.auto(Autotyp.HUBSCHRAUBER).create(welt
                    //? if >=1.21.2
                    , net.minecraft.world.entity.EntitySpawnReason.EVENT
            );
            if (heli == null) {
                return;
            }
            Kompat.versetzen(heli, platz.x, platz.y, platz.z, herr.getYRot(), 0.0F);
            welt.addFreshEntity(heli);
        }
        butler.stopRiding();
        butler.teleportiereNahe(BlockPos.containing(platz));
    }
}
