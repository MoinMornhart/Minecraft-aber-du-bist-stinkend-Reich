package de.moinmornhart.stinkendreich.mechanik;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.register.ModBloecke;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Grundlage aller Mechanik-Blöcke:
 * <ul>
 *   <li>Art der Ausrichtung: keine, waagerecht (an der Wand) oder in alle Richtungen</li>
 *   <li>optional flache Wandplatte (Kartenleser, Kamera …) statt ganzem Block</li>
 *   <li>optional Redstone-Impuls (wie ein Knopf) und ein Takt auf dem Server</li>
 * </ul>
 */
public abstract class MechanikBlock extends Block implements EntityBlock {
    public enum Ausrichtung { KEINE, WAAGERECHT, ALLE }

    private final Ausrichtung ausrichtung;
    private final boolean wandplatte;
    private final boolean impuls;

    protected MechanikBlock(Properties eigenschaften, Ausrichtung ausrichtung, boolean wandplatte, boolean impuls) {
        super(eigenschaften);
        this.ausrichtung = ausrichtung;
        this.wandplatte = wandplatte;
        this.impuls = impuls;
        BlockState s = stateDefinition.any();
        if (ausrichtung == Ausrichtung.WAAGERECHT) {
            s = s.setValue(Eigenschaften.RICHTUNG, Direction.NORTH);
        } else if (ausrichtung == Ausrichtung.ALLE) {
            s = s.setValue(Eigenschaften.RICHTUNG_ALLE, Direction.NORTH);
        }
        if (impuls) {
            s = s.setValue(Eigenschaften.AN, false);
        }
        registerDefaultState(standardZustand(s));
    }

    /** Unterklassen setzen hier Standardwerte eigener Eigenschaften. */
    protected BlockState standardZustand(BlockState s) {
        return s;
    }

    /** Unterklassen fügen hier eigene Eigenschaften hinzu. */
    protected void eigeneEigenschaften(StateDefinition.Builder<Block, BlockState> builder) {
    }

    /** Takt in Ticks für {@link #takt(ServerLevel, BlockPos, BlockState, MechanikBlockEntity)}; 0 = kein Takt. */
    public int takt() {
        return 0;
    }

    /** Wird auf dem Server alle {@link #takt()} Ticks aufgerufen. */
    public void takt(ServerLevel welt, BlockPos pos, BlockState zustand, MechanikBlockEntity daten) {
    }

    @Override
    protected final void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        // Hinweis: wird im Konstruktor von Block aufgerufen, Felder sind hier noch nicht gesetzt.
        // Deshalb fügen wir alle möglichen Grundeigenschaften über die Hilfsmethode hinzu.
        for (var e : grundEigenschaften()) {
            builder.add(e);
        }
        eigeneEigenschaften(builder);
    }

    /** Welche Grundeigenschaften dieser Block hat (unabhängig von Feldern, da früh aufgerufen). */
    protected abstract net.minecraft.world.level.block.state.properties.Property<?>[] grundEigenschaften();

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext kontext) {
        BlockState s = defaultBlockState();
        if (ausrichtung == Ausrichtung.WAAGERECHT) {
            Direction seite = kontext.getClickedFace();
            s = s.setValue(Eigenschaften.RICHTUNG, seite.getAxis().isHorizontal() ? seite : kontext.getHorizontalDirection().getOpposite());
        } else if (ausrichtung == Ausrichtung.ALLE) {
            s = s.setValue(Eigenschaften.RICHTUNG_ALLE, kontext.getClickedFace());
        }
        return s;
    }

    // ---------------- Form ----------------

    private static final VoxelShape PLATTE_N = Block.box(2, 2, 14, 14, 14, 16);
    private static final VoxelShape PLATTE_S = Block.box(2, 2, 0, 14, 14, 2);
    private static final VoxelShape PLATTE_O = Block.box(0, 2, 2, 2, 14, 14);
    private static final VoxelShape PLATTE_W = Block.box(14, 2, 2, 16, 14, 14);

    @Override
    protected VoxelShape getShape(BlockState zustand, BlockGetter welt, BlockPos pos, CollisionContext kontext) {
        if (wandplatte) {
            return switch (zustand.getValue(Eigenschaften.RICHTUNG)) {
                case SOUTH -> PLATTE_S;
                case EAST -> PLATTE_O;
                case WEST -> PLATTE_W;
                default -> PLATTE_N;
            };
        }
        return super.getShape(zustand, welt, pos, kontext);
    }

    // ---------------- Redstone-Impuls ----------------

    /** Gibt für {@code ticks} ein Redstone-Signal ab (öffnet z. B. eine Eisentür daneben). */
    protected void impulsGeben(Level welt, BlockPos pos, BlockState zustand, int ticks) {
        if (!impuls) {
            return;
        }
        welt.setBlock(pos, zustand.setValue(Eigenschaften.AN, true), 3);
        nachbarnMelden(welt, pos, zustand);
        welt.scheduleTick(pos, this, ticks);
    }

    @Override
    protected void tick(BlockState zustand, ServerLevel welt, BlockPos pos, RandomSource zufall) {
        if (impuls && zustand.getValue(Eigenschaften.AN)) {
            welt.setBlock(pos, zustand.setValue(Eigenschaften.AN, false), 3);
            nachbarnMelden(welt, pos, zustand);
        }
    }

    private void nachbarnMelden(Level welt, BlockPos pos, BlockState zustand) {
        Kompat.nachbarnAktualisieren(welt, pos, this);
        if (ausrichtung == Ausrichtung.WAAGERECHT) {
            Kompat.nachbarnAktualisieren(welt, pos.relative(zustand.getValue(Eigenschaften.RICHTUNG).getOpposite()), this);
        }
    }

    @Override
    protected boolean isSignalSource(BlockState zustand) {
        return impuls;
    }

    @Override
    protected int getSignal(BlockState zustand, BlockGetter welt, BlockPos pos, Direction richtung) {
        return impuls && zustand.getValue(Eigenschaften.AN) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState zustand, BlockGetter welt, BlockPos pos, Direction richtung) {
        if (!impuls || !zustand.getValue(Eigenschaften.AN) || ausrichtung != Ausrichtung.WAAGERECHT) {
            return 0;
        }
        return zustand.getValue(Eigenschaften.RICHTUNG) == richtung ? 15 : 0;
    }

    // ---------------- Block-Entity und Takt ----------------

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState zustand) {
        return new MechanikBlockEntity(pos, zustand);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level welt, BlockState zustand, BlockEntityType<T> typ) {
        if (welt.isClientSide() || takt() <= 0 || typ != ModBloecke.MECHANIK_TYP) {
            return null;
        }
        BlockEntityTicker<MechanikBlockEntity> ticker = (w, p, s, be) -> {
            if (++be.ticks >= takt()) {
                be.ticks = 0;
                takt((ServerLevel) w, p, s, be);
            }
        };
        return (BlockEntityTicker<T>) (Object) ticker;
    }

    /** Hilfsfunktion: Block-Entity an einer Position, falls es unsere ist. */
    public static MechanikBlockEntity daten(BlockGetter welt, BlockPos pos) {
        return welt.getBlockEntity(pos) instanceof MechanikBlockEntity m ? m : null;
    }
}
