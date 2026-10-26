package de.moinmornhart.stinkendreich.mechanik;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.register.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Versteckte Falltür am Ende des Notausgangs, 50 Blöcke neben der Villa im Gelände: sieht aus wie Rasen und geht
 * nur mit Zugangskarte auf und zu (von unten wie von oben). Redstone öffnet sie nicht.
 */
public class GeheimlukeBlock extends TrapDoorBlock {
    public GeheimlukeBlock(BlockSetType art, Properties eigenschaften) {
        super(art, eigenschaften);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState zustand, Level welt, BlockPos pos, Player spieler, BlockHitResult treffer) {
        if (welt.isClientSide()) {
            return InteractionResult.SUCCESS;      // der Server entscheidet
        }
        if (!Zugang.hatKarte(spieler)) {
            welt.playSound(null, pos, ModSounds.PIEP_FEHLER, SoundSource.BLOCKS, 0.6F, 1.0F);
            Kompat.nachricht(spieler, Component.translatable("stinkendreich.zugang.keine_karte").withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }
        BlockState neu = zustand.cycle(OPEN);
        welt.setBlock(pos, neu, 2);
        welt.playSound(null, pos, neu.getValue(OPEN) ? getType().trapdoorOpen() : getType().trapdoorClose(), SoundSource.BLOCKS,
                1.0F, welt.getRandom().nextFloat() * 0.1F + 0.9F);
        return InteractionResult.SUCCESS;
    }

    /** Redstone öffnet die Luke nicht (nur die Zugangskarte). */
    //? if >=1.21.2 {
    @Override
    protected void neighborChanged(BlockState zustand, Level welt, BlockPos pos, net.minecraft.world.level.block.Block nachbar,
                                   net.minecraft.world.level.redstone.Orientation richtung, boolean verschoben) {
    }
    //?} else {
    /*@Override
    protected void neighborChanged(BlockState zustand, Level welt, BlockPos pos, net.minecraft.world.level.block.Block nachbar,
                                   BlockPos von, boolean verschoben) {
    }
    *///?}
}
