package de.moinmornhart.stinkendreich.mixin;

import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.netz.ModErkennung;
import de.moinmornhart.stinkendreich.netz.OhneMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Rechtsklicks auf Blöcke für Spieler ohne Mod (Server-Version). Ihr Minecraft kennt unsere kleinen Möbel nur als
 * unsichtbare ganze Blöcke; ein Klick auf die Lampe vor der Sicherheitszentrale landet dann bei der Lampe. Der
 * Server rechnet deshalb mit den echten Formen nach, worauf der Spieler zeigt, und leitet den Klick dorthin um.
 * Außerdem merkt er sich erfolgreiche Klicks: Minecraft ohne Mod schickt danach noch „Item benutzen“, und das
 * goldene Handy soll dann nicht zusätzlich aufgehen (siehe {@link OhneMod#geradeGezeigt}).
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class BlockBenutzenMixin {
    @Unique
    private boolean stinkendreich$umgeleitet;

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void stinkendreich$echtesZiel(ServerPlayer spieler, Level welt, ItemStack stapel, InteractionHand hand,
                                         BlockHitResult treffer, CallbackInfoReturnable<InteractionResult> cir) {
        if (stinkendreich$umgeleitet || ModErkennung.hatMod(spieler)) {
            return;
        }
        Vec3 auge = spieler.getEyePosition();
        Vec3 ende = auge.add(spieler.getViewVector(1.0F).scale(spieler.blockInteractionRange() + 1.0));
        BlockHitResult echt = welt.clip(new ClipContext(auge, ende, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, spieler));
        BlockPos geklickt = treffer.getBlockPos();
        if (de.moinmornhart.stinkendreich.dev.Autotest.aktiv()) {
            StinkendReich.LOGGER.info("Autotest: Blockklick {} ({}) – Strahl trifft {} ({}, innen {})", geklickt,
                    welt.getBlockState(geklickt).getBlock(), echt.getBlockPos(), welt.getBlockState(echt.getBlockPos()).getBlock(), echt.isInside());
        }
        // Steht der Spieler mit dem Kopf in einem unserer Blöcke (z. B. in der offenen Aufzugstür), trifft der Strahl
        // diesen zuerst („innen“); dann gilt, was sein Minecraft angeklickt hat
        if (echt.getType() != HitResult.Type.BLOCK || echt.isInside() || echt.getBlockPos().equals(geklickt)
                || !(istUnserer(welt, geklickt) || istUnserer(welt, echt.getBlockPos()))) {
            return;
        }
        stinkendreich$umgeleitet = true;
        try {
            ServerPlayerGameMode modus = (ServerPlayerGameMode) (Object) this;
            // Angeklickt ist einer unserer Blöcke, der genaue Strahl geht aber daran vorbei auf einen fremden (die
            // Aufzugstafel ist nur ein dünnes Schild an der Wand): Erst darf der angeklickte Block antworten, nur wenn
            // er nichts tut (Deko-Lampe vor einer Truhe), geht der Klick an den Block dahinter
            if (istUnserer(welt, geklickt) && !istUnserer(welt, echt.getBlockPos())) {
                InteractionResult eigen = modus.useItemOn(spieler, welt, stapel, hand, treffer);
                if (eigen.consumesAction()) {
                    cir.setReturnValue(eigen);
                    return;
                }
            }
            cir.setReturnValue(modus.useItemOn(spieler, welt, stapel, hand, echt));
        } finally {
            stinkendreich$umgeleitet = false;
        }
    }

    @Unique
    private static boolean istUnserer(Level welt, BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(welt.getBlockState(pos).getBlock()).getNamespace().equals(StinkendReich.MOD_ID);
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void stinkendreich$benutztMerken(ServerPlayer spieler, Level welt, ItemStack stapel, InteractionHand hand,
                                             BlockHitResult treffer, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue().consumesAction()) {
            OhneMod.benutzt(spieler);
        }
    }
}
