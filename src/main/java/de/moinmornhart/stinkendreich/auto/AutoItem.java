package de.moinmornhart.stinkendreich.auto;

import de.moinmornhart.stinkendreich.register.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Ein eingepacktes Fahrzeug (Bonzenauto, Geländewagen, Supersportwagen, Stretch-Limo). Rechtsklick auf den Boden stellt es dort ab
 * (Schleichen + Rechtsklick mit leerer Hand packt es wieder ein).
 */
public class AutoItem extends Item {
    private final Autotyp art;

    public AutoItem(Properties eigenschaften, Autotyp art) {
        super(eigenschaften);
        this.art = art;
    }

    @Override
    public InteractionResult useOn(UseOnContext kontext) {
        if (kontext.getLevel() instanceof ServerLevel welt) {
            BlockPos pos = kontext.getClickedPos().relative(kontext.getClickedFace());
            AutoEntity auto = ModEntities.auto(art).create(welt
                    //? if >=1.21.2
                    , net.minecraft.world.entity.EntitySpawnReason.SPAWN_ITEM_USE
            );
            if (auto == null) {
                return InteractionResult.FAIL;
            }
            float blick = kontext.getPlayer() != null ? kontext.getPlayer().getYRot() : 0.0F;
            auto.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            auto.setYRot(blick);
            auto.setYHeadRot(blick);
            auto.yBodyRot = blick;
            welt.addFreshEntity(auto);
            if (kontext.getPlayer() == null || !kontext.getPlayer().isCreative()) {
                kontext.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
