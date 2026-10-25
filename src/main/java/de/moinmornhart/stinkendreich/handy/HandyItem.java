package de.moinmornhart.stinkendreich.handy;

import de.moinmornhart.stinkendreich.mechanik.KlientBruecke;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Das goldene Handy: Rechtsklick öffnet die Apps (James anrufen, Navigation, Lieferservice, Mitarbeiter …). */
public class HandyItem extends Item {
    public HandyItem(Properties eigenschaften) {
        super(eigenschaften);
    }

    private static void oeffnen(Level welt) {
        if (welt.isClientSide()) {
            KlientBruecke.handy.run();
        }
    }

    //? if >=1.21.2 {
    @Override
    public InteractionResult use(Level welt, Player spieler, InteractionHand hand) {
        oeffnen(welt);
        return InteractionResult.SUCCESS;
    }
    //?} else {
    /*@Override
    public net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> use(Level welt, Player spieler, InteractionHand hand) {
        oeffnen(welt);
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(spieler.getItemInHand(hand), welt.isClientSide());
    }
    *///?}
}
