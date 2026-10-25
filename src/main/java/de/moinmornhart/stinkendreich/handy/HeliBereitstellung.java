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
 * Für den Anruf „mit dem Hubschrauber“: Ist in 512 Blöcken kein freier Hubschrauber geladen, steht einer in 40 bis 70
 * Blöcken Entfernung bereit, James steigt ein und fliegt von dort zum Herrn (siehe FlugAuftrag).
 */
final class HeliBereitstellung {
    private HeliBereitstellung() {
    }

    static void bereitstellen(ServerPlayer herr, ButlerEntity butler) {
        if (!(herr.level() instanceof ServerLevel welt)) {
            return;
        }
        boolean da = !welt.getEntitiesOfClass(HubschrauberEntity.class, herr.getBoundingBox().inflate(512),
                h -> h.getPassengers().stream().allMatch(e -> e == butler)).isEmpty();
        if (da) {
            return;
        }
        net.minecraft.world.phys.Vec3 platz = Pilot.landestelle(welt, herr.blockPosition(), 40, 70);
        HubschrauberEntity heli = (HubschrauberEntity) ModEntities.auto(Autotyp.HUBSCHRAUBER).create(welt
                //? if >=1.21.2
                , net.minecraft.world.entity.EntitySpawnReason.EVENT
        );
        if (heli == null || platz == null) {
            return;
        }
        Kompat.versetzen(heli, platz.x, platz.y, platz.z, herr.getYRot(), 0.0F);
        welt.addFreshEntity(heli);
        butler.stopRiding();
        butler.teleportiereNahe(BlockPos.containing(platz));
    }
}
