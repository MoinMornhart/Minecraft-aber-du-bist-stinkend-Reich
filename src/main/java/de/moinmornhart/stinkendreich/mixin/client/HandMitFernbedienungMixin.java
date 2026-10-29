package de.moinmornhart.stinkendreich.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import de.moinmornhart.stinkendreich.fernseher.FernbedienungItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26.3 {
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
//?} else if >=1.21.9 {
/*import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
*///?} else {
/*import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
*///?}

/**
 * Fernbedienung in der eigenen Hand (Philip: „man hält die noch nicht richtig in der Hand, die Hand ist komplett weg“).
 * Minecraft zeigt in der Ich-Sicht nur den Gegenstand, ohne Arm. Für die Fernbedienung wird vorher der Arm gezeichnet
 * – genau wie mit leerer Hand –, die Fernbedienung liegt dann über die Lage im Item-Modell in dieser Faust.
 * Ab 26.3 heißt der Zeichner FirstPersonHandsAndItemsRenderer, ab 1.21.9 sammelt er Zeichenaufträge statt Puffern.
 */
//? if >=26.3 {
@Mixin(FirstPersonHandsAndItemsRenderer.class)
//?} else
/*@Mixin(ItemInHandRenderer.class)*/
public abstract class HandMitFernbedienungMixin {
    //? if >=26.3 {
    @Shadow
    private void renderPlayerArm(PoseStack lage, SubmitNodeCollector auftraege, int licht, float ausholen, float schwung,
                                 HumanoidArm arm, PlayerRenderState spieler) {
    }

    @Inject(method = "submitArmWithItem", at = @At("HEAD"))
    private void stinkendreich$handZeichnen(PlayerRenderState spieler, FirstPersonHandsAndItemsRenderState haende, float teilTick,
                                            float neigung, InteractionHand hand, float schwung, ItemStack stapel, float ausholen,
                                            PoseStack lage, SubmitNodeCollector auftraege, int licht, CallbackInfo ci) {
        if (!(stapel.getItem() instanceof FernbedienungItem) || haende.isScoping || spieler.avatarRenderState == null
                || spieler.avatarRenderState.isInvisible) {
            return;
        }
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? spieler.avatarRenderState.mainArm : spieler.avatarRenderState.mainArm.getOpposite();
        lage.pushPose();
        renderPlayerArm(lage, auftraege, licht, ausholen, schwung, arm, spieler);
        lage.popPose();
    }
    //?} else if >=1.21.9 {
    /*@Shadow
    private void renderPlayerArm(PoseStack lage, SubmitNodeCollector auftraege, int licht, float ausholen, float schwung, HumanoidArm arm) {
    }

    // Heißt ab 26.2 submitArmWithItem (eine der beiden Methoden gibt es immer)
    @Inject(method = {"renderArmWithItem", "submitArmWithItem"}, at = @At("HEAD"))
    private void stinkendreich$handZeichnen(AbstractClientPlayer spieler, float teilTick, float neigung, InteractionHand hand,
                                            float schwung, ItemStack stapel, float ausholen, PoseStack lage,
                                            SubmitNodeCollector auftraege, int licht, CallbackInfo ci) {
        if (!(stapel.getItem() instanceof FernbedienungItem) || spieler.isInvisible() || spieler.isScoping()) {
            return;
        }
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? spieler.getMainArm() : spieler.getMainArm().getOpposite();
        lage.pushPose();
        renderPlayerArm(lage, auftraege, licht, ausholen, schwung, arm);
        lage.popPose();
    }
    *///?} else {
    /*@Shadow
    private void renderPlayerArm(PoseStack lage, MultiBufferSource puffer, int licht, float ausholen, float schwung, HumanoidArm arm) {
    }

    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void stinkendreich$handZeichnen(AbstractClientPlayer spieler, float teilTick, float neigung, InteractionHand hand,
                                            float schwung, ItemStack stapel, float ausholen, PoseStack lage,
                                            MultiBufferSource puffer, int licht, CallbackInfo ci) {
        if (!(stapel.getItem() instanceof FernbedienungItem) || spieler.isInvisible() || spieler.isScoping()) {
            return;
        }
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? spieler.getMainArm() : spieler.getMainArm().getOpposite();
        lage.pushPose();
        renderPlayerArm(lage, puffer, licht, ausholen, schwung, arm);
        lage.popPose();
    }
    *///?}
}
