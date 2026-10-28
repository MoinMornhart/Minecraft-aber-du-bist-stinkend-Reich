package de.moinmornhart.stinkendreich.mixin;

import de.moinmornhart.stinkendreich.villa.VillaPlatzierung;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Kein Eis auf dem Gelände einer Villa: In kalten Gegenden friert Minecraft offenes Wasser zu – den Wassergraben, den
 * Pool, das Haibecken. Auf dem Grundstück (samt Graben) sagt das Biom deshalb immer „friert nicht“. Eis, das schon
 * da ist, taut der {@link de.moinmornhart.stinkendreich.mechanik.Graben} wieder auf.
 */
@Mixin(Biome.class)
public abstract class KeinEisMixin {
    @Inject(method = "shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Z)Z", at = @At("HEAD"), cancellable = true)
    private void stinkendreich$keinEisBeiDerVilla(LevelReader welt, BlockPos pos, boolean randNoetig, CallbackInfoReturnable<Boolean> cir) {
        if (welt instanceof ServerLevel s && s.dimension() == Level.OVERWORLD && VillaPlatzierung.bei(pos) != null) {
            cir.setReturnValue(false);
        }
    }
}
