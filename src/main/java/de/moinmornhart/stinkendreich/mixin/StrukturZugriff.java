package de.moinmornhart.stinkendreich.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import java.util.Optional;

/**
 * Zugriff auf die Figurenliste einer Strukturvorlage und auf Minecrafts eigenes „Figur aus Daten bauen“ (für den
 * Inkognito-Modus: alle Bilder, Fische, Autos usw. werden gespeichert und am Ziel genau so wieder aufgestellt).
 */
@Mixin(StructureTemplate.class)
public interface StrukturZugriff {
    @Accessor("entityInfoList")
    List<StructureTemplate.StructureEntityInfo> stinkendreich$figuren();

    //? if >=1.21.6 {
    @Invoker("createEntityIgnoreException")
    static Optional<Entity> stinkendreich$figurBauen(net.minecraft.util.ProblemReporter meldungen, ServerLevelAccessor welt, CompoundTag daten) {
        throw new AssertionError();
    }
    //?} else {
    /*@Invoker("createEntityIgnoreException")
    static Optional<Entity> stinkendreich$figurBauen(ServerLevelAccessor welt, CompoundTag daten) {
        throw new AssertionError();
    }
    *///?}
}
