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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Privattür (z. B. vom Foyer in die Tiefgarage): Nur Bewohner der Villa öffnen und schließen sie per
 * Rechtsklick, alle anderen hören einen Fehlerton. Sie bleibt offen stehen, bis ein Bewohner sie wieder
 * schließt. Der Butler kommt trotzdem durch (er öffnet Türen wie ein Dorfbewohner).
 */
public class PrivattuerBlock extends ZiertuerBlock {
    public PrivattuerBlock(BlockSetType art, Properties eigenschaften) {
        super(art, eigenschaften);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState zustand, Level welt, BlockPos pos, Player spieler, BlockHitResult treffer) {
        if (welt.isClientSide()) {
            return InteractionResult.SUCCESS;      // der Server entscheidet, ob die Tür aufgeht
        }
        if (!VillaZustand.get(welt.getServer()).istBewohner(spieler)) {
            welt.playSound(null, pos, ModSounds.PIEP_FEHLER, SoundSource.BLOCKS, 1.0F, 1.0F);
            Kompat.nachricht(spieler, Component.translatable("stinkendreich.privattuer.verweigert").withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }
        // Wie eine Holztür umschalten; die andere Hälfte zieht über die Formaktualisierung nach
        BlockState neu = zustand.cycle(OPEN);
        welt.setBlock(pos, neu, 10);
        welt.playSound(null, pos, neu.getValue(OPEN) ? type().doorOpen() : type().doorClose(), SoundSource.BLOCKS, 1.0F,
                welt.getRandom().nextFloat() * 0.1F + 0.9F);
        return InteractionResult.SUCCESS;
    }
}
