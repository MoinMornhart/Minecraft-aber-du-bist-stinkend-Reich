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
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Kartenleser neben einer Tür: Mit Zugangskarte (oder bei Stufe 0) gibt er für 3 Sekunden ein
 * Redstone-Signal und öffnet so die Eisentür daneben. Sicherheits- und Banktüren öffnet er direkt, sie bleiben
 * offen, bis man sie schließt ({@link SicherheitstuerBlock}). Drei Fehlversuche lösen Alarm aus.
 */
public class KartenleserBlock extends MechanikBlock {
    public KartenleserBlock(Properties eigenschaften) {
        super(eigenschaften, Ausrichtung.WAAGERECHT, true, true);
    }

    @Override
    protected Property<?>[] grundEigenschaften() {
        return new Property<?>[]{Eigenschaften.RICHTUNG, Eigenschaften.AN};
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState zustand, Level welt, BlockPos pos, Player spieler, BlockHitResult treffer) {
        if (welt.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        VillaZustand z = VillaZustand.fuer(welt.getServer(), pos);
        if (z.stufe() == 0 || Zugang.hatKarte(spieler) || z.istGast(spieler)) {
            Zugang.erfolg(spieler);
            // Sicherheits- und Banktüren gehen direkt auf und bleiben offen; alle anderen Türen öffnet ein Impuls
            if (!SicherheitstuerBlock.oeffnenBei(welt, pos, spieler)) {
                impulsGeben(welt, pos, zustand, 60);
            }
            welt.playSound(null, pos, ModSounds.PIEP_OK, SoundSource.BLOCKS, 1.0F, 1.0F);
            Kompat.nachricht(spieler, Component.translatable("stinkendreich.zugang.karte").withStyle(ChatFormatting.GREEN), true);
        } else {
            welt.playSound(null, pos, ModSounds.PIEP_FEHLER, SoundSource.BLOCKS, 1.0F, 1.0F);
            Kompat.nachricht(spieler, Component.translatable("stinkendreich.zugang.keine_karte").withStyle(ChatFormatting.RED), true);
            if (Zugang.fehlversuch(spieler)) {
                z.alarm(pos, Component.translatable("stinkendreich.alarm.grund.karte", spieler.getScoreboardName()));
            }
        }
        return InteractionResult.SUCCESS;
    }
}
