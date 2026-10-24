package de.moinmornhart.stinkendreich.register;

import de.moinmornhart.stinkendreich.StinkendReich;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/** Eigener Reiter „Stinkend Reich“ im Kreativ-Inventar. */
public final class ModTab {
    private ModTab() {
    }

    public static void init() {
        // Fabric hat die Kreativtab-API für 26.x umbenannt
        //? if >=26.1 {
        CreativeModeTab.Builder bauer = net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab.builder();
        //?} else
        /*CreativeModeTab.Builder bauer = net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup.builder();*/
        CreativeModeTab tab = bauer
                .title(Component.translatable("itemGroup.stinkendreich"))
                .icon(() -> new ItemStack(ModItems.ZUGANGSKARTE))
                .displayItems((parameter, ausgabe) -> ModRegister.ALLE_ITEMS.forEach(ausgabe::accept))
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, StinkendReich.id("haupt"), tab);
    }
}
