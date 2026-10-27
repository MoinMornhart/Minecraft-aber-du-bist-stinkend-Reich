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

    /** Mit Mod öffnet der eigene Handy-Bildschirm; ohne Mod (Server-Version) ein Dialogfenster vom Server. */
    private static void oeffnen(Level welt, Player spieler) {
        if (welt.isClientSide()) {
            KlientBruecke.handy.run();
        }
        //? if >=1.21.6 {
        else if (spieler instanceof net.minecraft.server.level.ServerPlayer sp
                && !de.moinmornhart.stinkendreich.netz.ModErkennung.hatMod(sp)
                && !de.moinmornhart.stinkendreich.netz.OhneMod.geradeGezeigt(sp)) {
            if (de.moinmornhart.stinkendreich.dev.Autotest.aktiv()) {
                de.moinmornhart.stinkendreich.StinkendReich.LOGGER.info("Autotest: Handy-Fenster für {} (Takt {})",
                        sp.getScoreboardName(), sp.level().getServer().getTickCount());
            }
            de.moinmornhart.stinkendreich.server.HandyDialog.oeffnen(sp, "start");
        }
        //?}
    }

    //? if >=1.21.2 {
    @Override
    public InteractionResult use(Level welt, Player spieler, InteractionHand hand) {
        oeffnen(welt, spieler);
        return InteractionResult.SUCCESS;
    }
    //?} else {
    /*@Override
    public net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> use(Level welt, Player spieler, InteractionHand hand) {
        oeffnen(welt, spieler);
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(spieler.getItemInHand(hand), welt.isClientSide());
    }
    *///?}
}
