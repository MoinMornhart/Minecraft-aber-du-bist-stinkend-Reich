package de.moinmornhart.stinkendreich.mechanik;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.register.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Roter Knopf der Haifischfalle an der Wand: Ein Bewohner drückt, der nächste Eindringling landet bei den Haien
 * (siehe {@link Haifalle}). Nach 5 Sekunden schließt dieser Block den Glasboden wieder (geplanter Tick).
 */
public class HaifalleSchalterBlock extends MechanikBlock {
    public HaifalleSchalterBlock(Properties eigenschaften) {
        super(eigenschaften, Ausrichtung.WAAGERECHT, true, false);
    }

    @Override
    protected Property<?>[] grundEigenschaften() {
        return new Property<?>[]{Eigenschaften.RICHTUNG};
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState zustand, Level welt, BlockPos pos, Player spieler, BlockHitResult treffer) {
        if (welt.isClientSide() || !(welt instanceof ServerLevel sl)) {
            return InteractionResult.SUCCESS;      // der Server entscheidet
        }
        VillaZustand z = VillaZustand.get(sl.getServer());
        if (!z.istBewohner(spieler)) {
            welt.playSound(null, pos, ModSounds.PIEP_FEHLER, SoundSource.BLOCKS, 0.6F, 1.0F);
            Kompat.nachricht(spieler, Component.translatable("stinkendreich.haifalle.nur_bewohner").withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }
        welt.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.8F, 0.9F);
        Haifalle.Ergebnis e = Haifalle.ausloesen(sl, pos, null);
        switch (e) {
            case OK -> Kompat.nachricht(spieler, Component.translatable("stinkendreich.haifalle.ausgeloest").withStyle(ChatFormatting.GOLD), true);
            case KEIN_OPFER -> {
                welt.playSound(null, pos, ModSounds.PIEP_FEHLER, SoundSource.BLOCKS, 0.6F, 1.0F);
                Kompat.nachricht(spieler, Component.translatable("stinkendreich.haifalle.kein_opfer").withStyle(ChatFormatting.GRAY), true);
            }
            case KEINE_FALLE -> {
                welt.playSound(null, pos, ModSounds.PIEP_FEHLER, SoundSource.BLOCKS, 0.6F, 1.0F);
                Kompat.nachricht(spieler, Component.translatable("stinkendreich.haifalle.keine_falle").withStyle(ChatFormatting.GRAY), true);
            }
            case LAEUFT -> Kompat.nachricht(spieler, Component.translatable("stinkendreich.haifalle.laeuft").withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.SUCCESS;
    }

    /** Geplanter Tick nach dem Auslösen: Boden schließen; steckt noch jemand in der Öffnung, in einer halben Sekunde erneut. */
    @Override
    protected void tick(BlockState zustand, ServerLevel welt, BlockPos pos, RandomSource zufall) {
        super.tick(zustand, welt, pos, zufall);
        if (Haifalle.schliessen(welt, pos)) {
            welt.scheduleTick(pos, this, 10);
        }
    }
}
