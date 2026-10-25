package de.moinmornhart.stinkendreich.register;

import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.musik.LautsprecherBlock;
import de.moinmornhart.stinkendreich.musik.MusikanlageBlock;
import de.moinmornhart.stinkendreich.musik.MusikanlageBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import static de.moinmornhart.stinkendreich.register.ModRegister.block;

/** Blöcke, Block-Entity und Klang des Musiksystems (DJ-Pult und Lautsprecher). */
public final class ModMusik {
    private static BlockBehaviour.Properties geraet() {
        return BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion();
    }

    public static final Block MUSIKANLAGE = block("musikanlage", MusikanlageBlock::new, geraet().lightLevel(s -> 5));
    public static final Block LAUTSPRECHER = block("lautsprecher", LautsprecherBlock::new, geraet());

    public static final BlockEntityType<MusikanlageBlockEntity> MUSIKANLAGE_TYP = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            StinkendReich.id("musikanlage"),
            FabricBlockEntityTypeBuilder.create(MusikanlageBlockEntity::new, MUSIKANLAGE).build());

    /**
     * Platzhalter-Klang für die Musik. In sounds.json steht er als Strom mit dem leeren Fabric-Klang;
     * die eigentliche Datei liefert {@code MusikSound.getAudioStream} vom Rechner des Spielers.
     */
    public static final SoundEvent MUSIK = ModRegister.sound("musik");

    private ModMusik() {
    }

    public static void init() {
        // Laden der Klasse registriert alles
    }
}
