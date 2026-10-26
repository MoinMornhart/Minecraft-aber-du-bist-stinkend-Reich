package de.moinmornhart.stinkendreich.mixin;

import de.moinmornhart.stinkendreich.netz.OhneMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Wie {@link BlockBenutzenMixin}, für Figuren: Nach einem Rechtsklick auf James, ein Auto oder eine andere Figur
 * schickt Minecraft ohne Mod noch „Item benutzen“ hinterher; das goldene Handy bleibt dann zu.
 * (Die Methode hat je nach Version zwei oder drei Parameter, deshalb ohne Parameter abgefangen.)
 */
@Mixin(Player.class)
public abstract class FigurBenutzenMixin {
    @Inject(method = "interactOn", at = @At("RETURN"))
    private void stinkendreich$benutztMerken(CallbackInfoReturnable<InteractionResult> cir) {
        if ((Object) this instanceof ServerPlayer spieler && cir.getReturnValue().consumesAction()) {
            OhneMod.benutzt(spieler);
        }
    }
}
