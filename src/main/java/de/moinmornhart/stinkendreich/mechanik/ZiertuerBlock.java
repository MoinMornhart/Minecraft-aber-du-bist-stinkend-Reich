package de.moinmornhart.stinkendreich.mechanik;

import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/**
 * Zweiteilige Tür der Villa (Prunk-, Glas-, Stahl- und Bunkertür). Sie verhält sich wie eine Tür aus dem
 * Spiel: Die Art ({@link BlockSetType}) legt Klang fest und ob sie sich von Hand öffnen lässt (Eisen: nur
 * per Redstone, also Kartenleser oder Tastenfeld). Aussehen: tools/assets/tueren.py.
 */
public class ZiertuerBlock extends DoorBlock {
    public ZiertuerBlock(BlockSetType art, Properties eigenschaften) {
        super(art, eigenschaften);
    }
}
