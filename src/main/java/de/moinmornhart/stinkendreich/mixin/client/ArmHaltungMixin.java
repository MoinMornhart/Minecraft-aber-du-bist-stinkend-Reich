package de.moinmornhart.stinkendreich.mixin.client;

import de.moinmornhart.stinkendreich.ausruestung.AgentWaffe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import net.minecraft.world.InteractionHand;
//? if >=1.21.9 {
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?} else if >=1.21.4 {
/*import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?} else if >=1.21.2 {
/*import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?} else {
/*import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

/**
 * Zielhaltung mit den Agenten-Waffen: Hält die Spielerfigur eine Pistole oder die Geldkanone, hebt sie den Arm wie
 * mit einer geladenen Armbrust (ein Arm zeigt in Blickrichtung, der andere stützt). So liegt der Griff wie ein
 * Schwertgriff in der Faust und die Mündung zeigt nach vorn. Nur das Aussehen von außen, die eigene Sicht bleibt.
 * Ab 1.21.9 heißt der Renderer AvatarRenderer (auch für Schaufensterpuppen); 1.21.2–1.21.3 kennen den Gegenstand
 * beim Wählen der Haltung nicht mehr, dort wird das Merkmal „hält eine geladene Armbrust“ gesetzt.
 */
//? if >=1.21.9 {
@Mixin(AvatarRenderer.class)
//?} else
/*@Mixin(PlayerRenderer.class)*/
public abstract class ArmHaltungMixin {
    //? if >=1.21.9 {
    @Inject(method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
            at = @At("HEAD"), cancellable = true)
    private static void stinkendreich$zielen(Avatar figur, ItemStack stapel, InteractionHand hand,
                                              CallbackInfoReturnable<HumanoidModel.ArmPose> rueckgabe) {
        if (AgentWaffe.zielt(stapel)) {
            rueckgabe.setReturnValue(HumanoidModel.ArmPose.CROSSBOW_HOLD);
        }
    }
    //?} else if >=1.21.4 {
    /*@Inject(method = "getArmPose(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
            at = @At("HEAD"), cancellable = true)
    private static void stinkendreich$zielen(Player spieler, ItemStack stapel, InteractionHand hand,
                                              CallbackInfoReturnable<HumanoidModel.ArmPose> rueckgabe) {
        if (AgentWaffe.zielt(stapel)) {
            rueckgabe.setReturnValue(HumanoidModel.ArmPose.CROSSBOW_HOLD);
        }
    }
    *///?} else if >=1.21.2 {
    /*@Inject(method = "extractHandState", at = @At("TAIL"))
    private void stinkendreich$zielen(AbstractClientPlayer spieler, PlayerRenderState.HandState zustand, InteractionHand hand,
                                      CallbackInfo info) {
        if (AgentWaffe.zielt(spieler.getItemInHand(hand))) {
            zustand.holdsChargedCrossbow = true;
        }
    }
    *///?} else {
    /*@Inject(method = "getArmPose(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
            at = @At("HEAD"), cancellable = true)
    private static void stinkendreich$zielen(AbstractClientPlayer spieler, InteractionHand hand,
                                              CallbackInfoReturnable<HumanoidModel.ArmPose> rueckgabe) {
        if (AgentWaffe.zielt(spieler.getItemInHand(hand))) {
            rueckgabe.setReturnValue(HumanoidModel.ArmPose.CROSSBOW_HOLD);
        }
    }
    *///?}
}
