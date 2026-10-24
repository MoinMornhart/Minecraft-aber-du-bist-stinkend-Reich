package de.moinmornhart.stinkendreich.mechanik;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Rote Warnleuchte: leuchtet, solange Alarm herrscht. */
public class WarnleuchteBlock extends MechanikBlock {
    public WarnleuchteBlock(Properties eigenschaften) {
        super(eigenschaften, Ausrichtung.KEINE, false, false);
    }

    @Override
    protected Property<?>[] grundEigenschaften() {
        return new Property<?>[]{Eigenschaften.LEUCHTET};
    }

    @Override
    protected BlockState standardZustand(BlockState s) {
        return s.setValue(Eigenschaften.LEUCHTET, false);
    }

    @Override
    public int takt() {
        return 10;
    }

    @Override
    public void takt(ServerLevel welt, BlockPos pos, BlockState zustand, MechanikBlockEntity daten) {
        boolean alarm = VillaZustand.get(welt.getServer()).alarmAktiv();
        // Blinken: im Alarm abwechselnd an und aus
        boolean an = alarm && (welt.getGameTime() / 10) % 2 == 0;
        if (an != zustand.getValue(Eigenschaften.LEUCHTET)) {
            welt.setBlock(pos, zustand.setValue(Eigenschaften.LEUCHTET, an), 3);
        }
    }
}
