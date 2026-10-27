package de.moinmornhart.stinkendreich.client.bodyguard;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.moinmornhart.stinkendreich.register.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
//? if >=1.21.11 {
import net.minecraft.client.model.animal.golem.IronGolemModel;
//?} else
/*import net.minecraft.client.model.IronGolemModel;*/
//? if >=1.21.2 {
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
//?} else
/*import net.minecraft.world.entity.animal.IronGolem;*/

/**
 * Die goldene Pistole in der rechten Hand des Bodyguards (dieselbe, mit der er schießt). Sie hängt am rechten Arm des
 * Golem-Modells und schwingt mit ihm; gehalten wie bei einem Spieler, nur passend zur großen Golem-Hand vergrößert.
 */
//? if >=1.21.2 {
public class PistolenLayer extends RenderLayer<IronGolemRenderState, IronGolemModel> {
//?} else
/*public class PistolenLayer extends RenderLayer<IronGolem, IronGolemModel<IronGolem>> {*/
    /** Mitte der Hand im Arm-Modell (Pixel / 16): Arm von x −13 bis −9, Faust bei y 21 (Arm bis 27). */
    private static final float HAND_X = -11.0F / 16.0F, HAND_Y = 21.0F / 16.0F;
    private static final float GROESSE = 1.6F;
    /** Erst beim ersten Zeichnen anlegen: Beim Erstellen der Ebene ist Minecraft noch nicht fertig geladen. */
    private ItemStack pistole;

    private ItemStack pistole() {
        if (pistole == null) {
            pistole = new ItemStack(ModItems.GOLDENE_PISTOLE);
        }
        return pistole;
    }

    //? if >=1.21.2 {
    public PistolenLayer(RenderLayerParent<IronGolemRenderState, IronGolemModel> eltern) {
    //?} else
    /*public PistolenLayer(RenderLayerParent<IronGolem, IronGolemModel<IronGolem>> eltern) {*/
        super(eltern);
    }

    /**
     * Arm-Lage übernehmen und an die Hand rücken; danach liegt die Pistole wie in einer Spielerhand. Beim Zielen ist der
     * Arm schon gehoben ({@link BodyguardModell}), dann zeigt der Lauf ohne weitere Drehung entlang des Arms.
     */
    private void anDieHand(PoseStack pose, boolean zielt) {
        getParentModel().getFlowerHoldingArm().translateAndRotate(pose);
        pose.translate(HAND_X, HAND_Y, 0.0F);
        // Wie bei einer Spielerhand, dazu 90° nach vorn: Die Pistolen-Haltung ist für einen gehobenen Arm gemacht
        // (Zielhaltung), der Golem lässt den Arm hängen – ohne die Drehung zeigte der Lauf senkrecht nach oben
        //? if >=26.3 {
        pose.rotate(Axis.XP.rotationDegrees(-90.0F));
        pose.rotate(Axis.YP.rotationDegrees(180.0F));
        if (!zielt) {
            pose.rotate(Axis.XP.rotationDegrees(90.0F));
        }
        //?} else {
        /*pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        if (!zielt) {
            pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        }
        *///?}
        pose.scale(GROESSE, GROESSE, GROESSE);
        pose.translate(0.0F, 0.08F, 0.0F);
    }

    //? if >=1.21.9 {
    @Override
    public void submit(PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector sammler, int licht, IronGolemRenderState zustand,
                       float gier, float neigung) {
        if (zustand.isInvisible) {
            return;
        }
        pose.pushPose();
        anDieHand(pose, zustand instanceof BodyguardRenderState b && b.zielt);
        net.minecraft.client.renderer.item.ItemStackRenderState bild = new net.minecraft.client.renderer.item.ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(bild, pistole(), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                Minecraft.getInstance().level, null, 0);
        bild.submit(pose, sammler, licht, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }
    //?} elif >=1.21.5 {
    /*@Override
    public void render(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource puffer, int licht, IronGolemRenderState zustand,
                       float gier, float neigung) {
        if (zustand.isInvisible) {
            return;
        }
        pose.pushPose();
        anDieHand(pose, zustand instanceof BodyguardRenderState b && b.zielt);
        net.minecraft.client.renderer.item.ItemStackRenderState bild = new net.minecraft.client.renderer.item.ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(bild, pistole(), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                Minecraft.getInstance().level, null, 0);
        bild.render(pose, puffer, licht, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
    *///?} elif >=1.21.4 {
    /*@Override
    public void render(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource puffer, int licht, IronGolemRenderState zustand,
                       float gier, float neigung) {
        if (zustand.isInvisible) {
            return;
        }
        pose.pushPose();
        anDieHand(pose, zustand instanceof BodyguardRenderState b && b.zielt);
        net.minecraft.client.renderer.item.ItemStackRenderState bild = new net.minecraft.client.renderer.item.ItemStackRenderState();
        // In 1.21.4 noch mit der Angabe „linke Hand“
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(bild, pistole(), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                false, Minecraft.getInstance().level, null, 0);
        bild.render(pose, puffer, licht, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
    *///?} elif >=1.21.2 {
    /*@Override
    public void render(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource puffer, int licht, IronGolemRenderState zustand,
                       float gier, float neigung) {
        if (zustand.isInvisible) {
            return;
        }
        pose.pushPose();
        anDieHand(pose, zustand instanceof BodyguardRenderState b && b.zielt);
        Minecraft.getInstance().getItemRenderer().renderStatic(pistole(), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, licht,
                OverlayTexture.NO_OVERLAY, pose, puffer, Minecraft.getInstance().level, 0);
        pose.popPose();
    }
    *///?} else {
    /*@Override
    public void render(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource puffer, int licht, IronGolem golem,
                       float a, float b, float c, float d, float e, float f) {
        if (golem.isInvisible()) {
            return;
        }
        pose.pushPose();
        anDieHand(pose, golem instanceof de.moinmornhart.stinkendreich.bodyguard.BodyguardEntity bg && bg.zielt());
        Minecraft.getInstance().getItemRenderer().renderStatic(golem, pistole(), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false,
                pose, puffer, golem.level(), licht, OverlayTexture.NO_OVERLAY, golem.getId());
        pose.popPose();
    }
    *///?}
}
