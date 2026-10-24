package de.moinmornhart.stinkendreich.mechanik;

import de.moinmornhart.stinkendreich.register.ModBloecke;
import de.moinmornhart.stinkendreich.register.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Sieht aus wie ein Bücherregal. Rechtsklick öffnet oder schließt die Geheimtür
 * (Geheimregal-Flügel im Umkreis von 4 Blöcken).
 */
public class GeheimschalterBlock extends MechanikBlock {
    public GeheimschalterBlock(Properties eigenschaften) {
        super(eigenschaften, Ausrichtung.KEINE, false, false);
    }

    @Override
    protected Property<?>[] grundEigenschaften() {
        return new Property<?>[0];
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState zustand, Level welt, BlockPos pos, Player spieler, BlockHitResult treffer) {
        if (welt instanceof ServerLevel server) {
            MechanikBlockEntity d = daten(welt, pos);
            boolean offen = d == null || d.zahl("offen", 0) == 0;
            if (d != null) {
                d.setze("offen", offen ? 1 : 0);
            }
            TorsteuerungBlock.umschalten(server, pos, 4, ModBloecke.GEHEIMREGAL, offen);
            welt.playSound(null, pos, ModSounds.TRESOR, SoundSource.BLOCKS, 0.6F, 1.4F);
        }
        return InteractionResult.SUCCESS;
    }
}
