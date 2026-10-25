package de.moinmornhart.stinkendreich.mixin;

import de.moinmornhart.stinkendreich.butler.SpielerBloecke;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Meldet jeden Block, den ein Spieler setzt, an {@link SpielerBloecke}. So weiß der Butler,
 * was gebaut wurde, und lässt es in Ruhe.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "placeBlock", at = @At("RETURN"))
    private void stinkendreich$gesetztMerken(BlockPlaceContext kontext, BlockState zustand, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && kontext.getPlayer() != null && !kontext.getLevel().isClientSide()) {
            SpielerBloecke.merken(kontext.getLevel(), kontext.getClickedPos());
        }
    }
}
