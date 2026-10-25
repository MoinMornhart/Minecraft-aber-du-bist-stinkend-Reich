package de.moinmornhart.stinkendreich.butler;

import de.moinmornhart.stinkendreich.register.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Das Butler-Glöckchen: Einmal läuten, und der Butler kommt (oder wird eingestellt,
 * falls man noch keinen hat).
 */
public class ButlerglockeItem extends Item {
    public ButlerglockeItem(Properties eigenschaften) {
        super(eigenschaften);
    }

    private static void laeuten(Level welt, Player spieler) {
        welt.playSound(null, spieler.blockPosition(), ModSounds.GLOECKCHEN, SoundSource.PLAYERS, 0.8F, 1.0F);
        if (spieler instanceof ServerPlayer sp) {
            ButlerVerwaltung.rufen(sp);
            // Abklingzeit: bis 1.21.1 je Item, danach je Stapel
            //? if >=1.21.2 {
            sp.getCooldowns().addCooldown(sp.getMainHandItem(), 40);
            //?} else
            /*sp.getCooldowns().addCooldown(sp.getMainHandItem().getItem(), 40);*/
        }
    }

    //? if >=1.21.2 {
    @Override
    public InteractionResult use(Level welt, Player spieler, InteractionHand hand) {
        laeuten(welt, spieler);
        return InteractionResult.SUCCESS;
    }
    //?} else {
    /*@Override
    public net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> use(Level welt, Player spieler, InteractionHand hand) {
        laeuten(welt, spieler);
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(spieler.getItemInHand(hand), welt.isClientSide());
    }
    *///?}
}
