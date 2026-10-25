package de.moinmornhart.stinkendreich.mechanik;

import de.moinmornhart.stinkendreich.register.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/** Goldene Toilette: Rechtsklick spült (Rauschen, Strudel aus Wasserspritzern im Becken). */
public class ToiletteBlock extends DekoBlock {
    public ToiletteBlock(Properties eigenschaften, List<double[]> kaesten, boolean fest, double sitz) {
        super(eigenschaften, kaesten, fest, sitz);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState zustand, Level welt, BlockPos pos, Player spieler, BlockHitResult treffer) {
        if (welt instanceof ServerLevel server) {
            server.playSound(null, pos, ModSounds.TOILETTE, SoundSource.BLOCKS, 1.0F, 1.0F);
            // Strudel: Spritzer im Kreis über dem Becken
            for (int i = 0; i < 16; i++) {
                double w = i * Math.PI / 8;
                server.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5 + Math.cos(w) * 0.22, pos.getY() + 0.5,
                        pos.getZ() + 0.45 + Math.sin(w) * 0.22, 2, 0.02, 0.02, 0.02, 0.0);
            }
            server.sendParticles(ParticleTypes.BUBBLE_POP, pos.getX() + 0.5, pos.getY() + 0.45, pos.getZ() + 0.45, 6, 0.1, 0.02, 0.1, 0.0);
        }
        return InteractionResult.SUCCESS;
    }
}
