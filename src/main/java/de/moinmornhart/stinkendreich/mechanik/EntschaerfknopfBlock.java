package de.moinmornhart.stinkendreich.mechanik;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.Konfig;
import de.moinmornhart.stinkendreich.register.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Versteckter Entschärfknopf: sieht aus wie ein normaler Wandblock. Werden genug verschiedene
 * Knöpfe kurz hintereinander gedrückt (Standard: 2 innerhalb von 15 Sekunden), sind die
 * Tresorfallen 60 Sekunden lang entschärft.
 */
public class EntschaerfknopfBlock extends MechanikBlock {
    public EntschaerfknopfBlock(Properties eigenschaften) {
        super(eigenschaften, Ausrichtung.KEINE, false, false);
    }

    @Override
    protected Property<?>[] grundEigenschaften() {
        return new Property<?>[0];
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState zustand, Level welt, BlockPos pos, Player spieler, BlockHitResult treffer) {
        if (!welt.isClientSide()) {
            int fehlt = VillaZustand.fuer(welt.getServer(), pos).entschaerfknopf(pos, Konfig.get().tresorKnoepfe);
            welt.playSound(null, pos, ModSounds.PIEP_OK, SoundSource.BLOCKS, 0.3F, 0.6F);
            Kompat.nachricht(spieler, fehlt == 0
                    ? Component.translatable("stinkendreich.tresor.entschaerft").withStyle(ChatFormatting.GREEN)
                    : Component.translatable("stinkendreich.tresor.knopf", fehlt).withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.SUCCESS;
    }
}
