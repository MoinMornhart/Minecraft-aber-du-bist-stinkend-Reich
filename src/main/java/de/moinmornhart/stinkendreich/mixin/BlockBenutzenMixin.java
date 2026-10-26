package de.moinmornhart.stinkendreich.mixin;

import de.moinmornhart.stinkendreich.netz.OhneMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Merkt sich, wann ein Spieler zuletzt erfolgreich einen Block benutzt hat. Minecraft ohne Mod hält unsere Blöcke für
 * Barrieren und schickt nach dem Klick zusätzlich „Item benutzen“; das goldene Handy klappt dann nicht mit auf
 * (siehe {@link OhneMod#geradeGezeigt}).
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class BlockBenutzenMixin {
    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void stinkendreich$benutztMerken(ServerPlayer spieler, Level welt, ItemStack stapel, InteractionHand hand,
                                             BlockHitResult treffer, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue().consumesAction()) {
            OhneMod.benutzt(spieler);
        }
    }
}
