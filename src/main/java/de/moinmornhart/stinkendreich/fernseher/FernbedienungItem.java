package de.moinmornhart.stinkendreich.fernseher;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Fernbedienung in der Hand: Rechtsklick steuert den nächsten Fernseher. Mit Schleichen + Rechtsklick auf einen
 * Block legt man sie ab (z. B. auf den Nachttisch).
 */
public class FernbedienungItem extends BlockItem {
    public FernbedienungItem(Block block, Properties eigenschaften) {
        super(block, eigenschaften);
    }

    @Override
    public InteractionResult useOn(UseOnContext kontext) {
        Player spieler = kontext.getPlayer();
        if (spieler != null && !spieler.isShiftKeyDown()) {
            Fernbedienung.bedienen(kontext.getLevel(), spieler, spieler.position());
            return InteractionResult.SUCCESS;
        }
        return super.useOn(kontext);
    }

    //? if >=1.21.2 {
    @Override
    public InteractionResult use(Level welt, Player spieler, InteractionHand hand) {
        Fernbedienung.bedienen(welt, spieler, spieler.position());
        return InteractionResult.SUCCESS;
    }
    //?} else {
    /*@Override
    public net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> use(Level welt, Player spieler, InteractionHand hand) {
        Fernbedienung.bedienen(welt, spieler, spieler.position());
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(spieler.getItemInHand(hand), welt.isClientSide());
    }
    *///?}
}
