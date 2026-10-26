package de.moinmornhart.stinkendreich.moebel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21.6 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?} else {
/*import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
*///?}

/**
 * Schublade eines Möbels (z. B. Nachttisch): neun Plätze, geöffnet wie eine kleine Truhe (eine Reihe). Wird das
 * Möbel abgebaut, fällt der Inhalt heraus.
 */
public class SchubladeBlockEntity extends RandomizableContainerBlockEntity {
    /** Wird beim Registrieren des Nachttischs gesetzt (siehe ModDeko). */
    public static BlockEntityType<SchubladeBlockEntity> TYP;

    private NonNullList<ItemStack> inhalt = NonNullList.withSize(9, ItemStack.EMPTY);

    public SchubladeBlockEntity(BlockPos pos, BlockState zustand) {
        super(TYP, pos, zustand);
    }

    @Override
    public int getContainerSize() {
        return 9;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return inhalt;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> neu) {
        inhalt = neu;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.stinkendreich.schublade");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventar) {
        return new ChestMenu(net.minecraft.world.inventory.MenuType.GENERIC_9x1, id, inventar, this, 1);
    }

    //? if >=1.21.6 {
    @Override
    protected void saveAdditional(ValueOutput ausgabe) {
        super.saveAdditional(ausgabe);
        ContainerHelper.saveAllItems(ausgabe, inhalt);
    }

    @Override
    protected void loadAdditional(ValueInput eingabe) {
        super.loadAdditional(eingabe);
        inhalt = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(eingabe, inhalt);
    }
    //?} else {
    /*@Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider p) {
        super.saveAdditional(tag, p);
        ContainerHelper.saveAllItems(tag, inhalt, p);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider p) {
        super.loadAdditional(tag, p);
        inhalt = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, inhalt, p);
    }
    *///?}
}
