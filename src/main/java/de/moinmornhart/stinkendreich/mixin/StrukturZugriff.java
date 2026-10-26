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

    /** Die Blocklisten der Vorlage (die Villa hat genau eine), für das Bauen in Scheiben. */
    @Accessor("palettes")
    List<StructureTemplate.Palette> stinkendreich$paletten();

    /** Minecrafts eigenes Aufstellen der Figuren einer Vorlage (genau wie beim normalen Setzen der Struktur). */
    //? if >=1.21.6 {
    @Invoker("placeEntities")
    void stinkendreich$figurenAufstellen(ServerLevelAccessor welt, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Mirror spiegel,
                                         net.minecraft.world.level.block.Rotation drehung, net.minecraft.core.BlockPos drehpunkt,
                                         net.minecraft.world.level.levelgen.structure.BoundingBox bereich, boolean fertigmachen,
                                         net.minecraft.util.ProblemReporter meldungen);
    //?} else {
    /*@Invoker("placeEntities")
    void stinkendreich$figurenAufstellen(ServerLevelAccessor welt, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Mirror spiegel,
                                         net.minecraft.world.level.block.Rotation drehung, net.minecraft.core.BlockPos drehpunkt,
                                         net.minecraft.world.level.levelgen.structure.BoundingBox bereich, boolean fertigmachen);
    *///?}

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
