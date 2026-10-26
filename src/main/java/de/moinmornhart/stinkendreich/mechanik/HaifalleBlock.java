package de.moinmornhart.stinkendreich.mechanik;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Boden der Haifischfalle: dunkles Rauchglas mit Goldrand über dem Haibecken. Zu ist er ein voller, begehbarer Block,
 * durch den man die Haie sieht; offen ({@code open=true}) bleibt nur ein dünner Goldrand ohne Kollision, und wer
 * darauf stand, fällt ins Becken. Geöffnet und geschlossen wird er vom {@link HaifalleSchalterBlock} (siehe
 * {@link Haifalle}); falls der Schalter fehlt, schließt er sich nach einer Weile selbst.
 */
public class HaifalleBlock extends Block {
    public HaifalleBlock(Properties eigenschaften) {
        super(eigenschaften);
        registerDefaultState(stateDefinition.any().setValue(Eigenschaften.OFFEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(Eigenschaften.OFFEN);
    }

    @Override
    protected VoxelShape getShape(BlockState zustand, BlockGetter welt, BlockPos pos, CollisionContext kontext) {
        return zustand.getValue(Eigenschaften.OFFEN) ? Shapes.empty() : Shapes.block();
    }

    /** Wie bei Glas: Zwischen zwei geschlossenen Feldern wird keine Innenfläche gezeichnet. */
    @Override
    protected boolean skipRendering(BlockState zustand, BlockState nachbar, Direction richtung) {
        return nachbar.is(this) && !zustand.getValue(Eigenschaften.OFFEN) && !nachbar.getValue(Eigenschaften.OFFEN)
                || super.skipRendering(zustand, nachbar, richtung);
    }

    /** Sicherung: noch offen und niemand im Weg → leise schließen; sonst gleich noch einmal versuchen. */
    @Override
    protected void tick(BlockState zustand, ServerLevel welt, BlockPos pos, RandomSource zufall) {
        if (!zustand.getValue(Eigenschaften.OFFEN)) {
            return;
        }
        if (Haifalle.imWeg(welt, pos)) {
            welt.scheduleTick(pos, this, 20);
            return;
        }
        welt.setBlock(pos, zustand.setValue(Eigenschaften.OFFEN, false), 3);
    }
}
