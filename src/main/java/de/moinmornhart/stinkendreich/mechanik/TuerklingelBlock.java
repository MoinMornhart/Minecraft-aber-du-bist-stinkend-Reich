package de.moinmornhart.stinkendreich.mechanik;

import de.moinmornhart.stinkendreich.register.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;

/** Türklingel am Tor: Gong vor Ort und bei allen Bewohnern, dazu eine Nachricht. */
public class TuerklingelBlock extends MechanikBlock {
    public TuerklingelBlock(Properties eigenschaften) {
        super(eigenschaften, Ausrichtung.WAAGERECHT, true, false);
    }

    @Override
    protected Property<?>[] grundEigenschaften() {
        return new Property<?>[]{Eigenschaften.RICHTUNG};
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState zustand, Level welt, BlockPos pos, Player spieler, BlockHitResult treffer) {
        if (!welt.isClientSide()) {
            welt.playSound(null, pos, ModSounds.KLINGEL, SoundSource.BLOCKS, 1.5F, 1.0F);
            VillaZustand z = VillaZustand.get(welt.getServer());
            for (ServerPlayer p : welt.getServer().getPlayerList().getPlayers()) {
                if (z.istBewohner(p) && p != spieler) {
                    p.level().playSound(null, p.blockPosition(), ModSounds.KLINGEL, SoundSource.BLOCKS, 0.8F, 1.0F);
                }
            }
            z.anBewohner(Component.translatable("stinkendreich.klingel", spieler.getScoreboardName()).withStyle(ChatFormatting.YELLOW), false);
        }
        return InteractionResult.SUCCESS;
    }
}
