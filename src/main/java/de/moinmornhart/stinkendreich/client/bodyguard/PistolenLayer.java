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
 * Golem-Modells, gehalten wie bei einem Spieler (Arm gehoben, Lauf nach vorn), passend zur großen Golem-Hand
 * vergrößert. Im Nahkampf ist sie weggesteckt.
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
     * Arm-Lage übernehmen und an die Hand rücken; danach liegt die Pistole wie in einer Spielerhand. Der Arm ist immer
     * gehoben ({@link BodyguardModell}: zum Gegner oder etwas gesenkt), der Lauf zeigt entlang des Arms nach vorn.
     */
    private void anDieHand(PoseStack pose) {
        getParentModel().getFlowerHoldingArm().translateAndRotate(pose);
        pose.translate(HAND_X, HAND_Y, 0.0F);
        // Wie bei einer Spielerhand. Der Arm ist immer gehoben (BodyguardModell), dann zeigt der Lauf entlang des Arms
        //? if >=26.3 {
        pose.rotate(Axis.XP.rotationDegrees(-90.0F));
        pose.rotate(Axis.YP.rotationDegrees(180.0F));
        //?} else {
        /*pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        *///?}
        pose.scale(GROESSE, GROESSE, GROESSE);
        pose.translate(0.0F, 0.08F, 0.0F);
    }

    //? if >=1.21.9 {
    @Override
    public void submit(PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector sammler, int licht, IronGolemRenderState zustand,
                       float gier, float neigung) {
        if (zustand.isInvisible || zustand instanceof BodyguardRenderState b && b.nahkampf) {
            return;                                   // im Nahkampf steckt die Pistole weg
        }
        pose.pushPose();
        anDieHand(pose);
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
        if (zustand.isInvisible || zustand instanceof BodyguardRenderState b && b.nahkampf) {
            return;                                   // im Nahkampf steckt die Pistole weg
        }
        pose.pushPose();
        anDieHand(pose);
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
        if (zustand.isInvisible || zustand instanceof BodyguardRenderState b && b.nahkampf) {
            return;                                   // im Nahkampf steckt die Pistole weg
        }
        pose.pushPose();
        anDieHand(pose);
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
        if (zustand.isInvisible || zustand instanceof BodyguardRenderState b && b.nahkampf) {
            return;                                   // im Nahkampf steckt die Pistole weg
        }
        pose.pushPose();
        anDieHand(pose);
        Minecraft.getInstance().getItemRenderer().renderStatic(pistole(), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, licht,
                OverlayTexture.NO_OVERLAY, pose, puffer, Minecraft.getInstance().level, 0);
        pose.popPose();
    }
    *///?} else {
    /*@Override
    public void render(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource puffer, int licht, IronGolem golem,
                       float a, float b, float c, float d, float e, float f) {
        if (golem.isInvisible() || golem instanceof de.moinmornhart.stinkendreich.bodyguard.BodyguardEntity bg && bg.nahkampf()) {
            return;                                   // im Nahkampf steckt die Pistole weg
        }
        pose.pushPose();
        anDieHand(pose);
        Minecraft.getInstance().getItemRenderer().renderStatic(golem, pistole(), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false,
                pose, puffer, golem.level(), licht, OverlayTexture.NO_OVERLAY, golem.getId());
        pose.popPose();
    }
    *///?}
}
