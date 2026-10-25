package de.moinmornhart.stinkendreich.fernseher;

import de.moinmornhart.stinkendreich.register.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Der XXL-Fernseher zum Aufhängen: Rechtsklick auf eine Wand hängt ihn dort mittig auf. */
public class FernseherItem extends Item {
    public FernseherItem(Properties eigenschaften) {
        super(eigenschaften);
    }

    @Override
    public InteractionResult useOn(UseOnContext kontext) {
        Direction seite = kontext.getClickedFace();
        if (!seite.getAxis().isHorizontal()) {
            return InteractionResult.PASS;
        }
        if (kontext.getLevel() instanceof ServerLevel welt) {
            FernseherEntity tv = ModEntities.FERNSEHER.create(welt
                    //? if >=1.21.2
                    , net.minecraft.world.entity.EntitySpawnReason.SPAWN_ITEM_USE
            );
            if (tv == null) {
                return InteractionResult.FAIL;
            }
            BlockPos wand = kontext.getClickedPos();
            double x = wand.getX() + 0.5 + seite.getStepX() * 0.62;
            double z = wand.getZ() + 0.5 + seite.getStepZ() * 0.62;
            de.moinmornhart.stinkendreich.Kompat.versetzen(tv, x, wand.getY() - 0.6, z, seite.toYRot(), 0.0F);
            tv.setYBodyRot(seite.toYRot());
            welt.addFreshEntity(tv);
            if (kontext.getPlayer() == null || !kontext.getPlayer().isCreative()) {
                kontext.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
