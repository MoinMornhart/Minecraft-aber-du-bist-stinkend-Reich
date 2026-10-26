package de.moinmornhart.stinkendreich.client.hai;

import de.moinmornhart.stinkendreich.StinkendReich;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
//? if >=1.21.2 {
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.state.DolphinRenderState;
//?} else {
/*import de.moinmornhart.stinkendreich.hai.HaiEntity;
import net.minecraft.client.model.HierarchicalModel;
*///?}

/**
 * Eigenes Hai-Modell: schlanker Rumpf, Kopf mit spitzer Schnauze und Unterkiefer voller Zähne, hohe Rückenflosse,
 * zwei Brustflossen und ein Sichelschwanz (oberer Lappen größer). Der Schwanz schlägt beim Schwimmen seitlich hin und
 * her. Vorn ist −z (wie bei allen Figuren). Textur 128 × 64 aus {@code tools/assets/hai.py}; die Werte für
 * {@code texOffs} und Größen müssen dort genauso stehen.
 */
//? if >=1.21.2 {
public class HaiModell extends EntityModel<DolphinRenderState> {
//?} else
/*public class HaiModell extends HierarchicalModel<HaiEntity> {*/
    public static final ModelLayerLocation SCHICHT = new ModelLayerLocation(StinkendReich.id("hai"), "main");
    private final ModelPart wurzel, rumpf, schwanz, schwanzflosse, kiefer;

    public HaiModell(ModelPart wurzel) {
        //? if >=1.21.2
        super(wurzel);
        this.wurzel = wurzel;
        rumpf = wurzel.getChild("rumpf");
        schwanz = rumpf.getChild("schwanz");
        schwanzflosse = schwanz.getChild("schwanzflosse");
        kiefer = rumpf.getChild("kopf").getChild("kiefer");
    }

    public static LayerDefinition schicht() {
        MeshDefinition netz = new MeshDefinition();
        PartDefinition rumpf = netz.getRoot().addOrReplaceChild("rumpf", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-5.0F, -8.0F, 0.0F, 10, 9, 16), PartPose.offset(0.0F, 21.0F, -7.0F));
        PartDefinition kopf = rumpf.addOrReplaceChild("kopf", CubeListBuilder.create().texOffs(0, 26)
                .addBox(-4.0F, -7.0F, -8.0F, 8, 7, 8), PartPose.ZERO);
        kopf.addOrReplaceChild("schnauze", CubeListBuilder.create().texOffs(34, 26)
                .addBox(-3.0F, -6.0F, -12.0F, 6, 5, 4), PartPose.ZERO);
        // Unterkiefer mit Zähnen: Drehpunkt hinten, damit das Maul beim Zubeißen aufgeht
        kopf.addOrReplaceChild("kiefer", CubeListBuilder.create().texOffs(56, 26)
                .addBox(-3.0F, 0.0F, -11.0F, 6, 2, 7), PartPose.offset(0.0F, -1.0F, -1.0F));
        rumpf.addOrReplaceChild("rueckenflosse", CubeListBuilder.create().texOffs(54, 0)
                .addBox(-0.5F, -8.0F, 0.0F, 1, 8, 6), PartPose.offsetAndRotation(0.0F, -7.0F, 4.0F, -0.55F, 0.0F, 0.0F));
        rumpf.addOrReplaceChild("flosse_links", CubeListBuilder.create().texOffs(70, 0)
                .addBox(0.0F, 0.0F, 0.0F, 9, 1, 5), PartPose.offsetAndRotation(4.0F, -1.0F, 2.0F, 0.0F, -0.4F, 0.55F));
        rumpf.addOrReplaceChild("flosse_rechts", CubeListBuilder.create().texOffs(70, 7)
                .addBox(-9.0F, 0.0F, 0.0F, 9, 1, 5), PartPose.offsetAndRotation(-4.0F, -1.0F, 2.0F, 0.0F, 0.4F, -0.55F));
        PartDefinition schwanz = rumpf.addOrReplaceChild("schwanz", CubeListBuilder.create().texOffs(0, 42)
                .addBox(-3.0F, -6.0F, 0.0F, 6, 6, 11), PartPose.offset(0.0F, 0.0F, 16.0F));
        // Sichelschwanz: großer Lappen nach oben hinten, kleiner nach unten hinten
        PartDefinition flosse = schwanz.addOrReplaceChild("schwanzflosse", CubeListBuilder.create(), PartPose.offset(0.0F, -3.0F, 10.0F));
        flosse.addOrReplaceChild("oben", CubeListBuilder.create().texOffs(36, 42)
                .addBox(-0.5F, -11.0F, 0.0F, 1, 11, 4), PartPose.rotation(-0.75F, 0.0F, 0.0F));
        flosse.addOrReplaceChild("unten", CubeListBuilder.create().texOffs(48, 42)
                .addBox(-0.5F, 0.0F, 0.0F, 1, 7, 3), PartPose.rotation(0.7F, 0.0F, 0.0F));
        return LayerDefinition.create(netz, 128, 64);
    }

    /**
     * Bewegung: Der Rumpf folgt Kopfdrehung und Neigung, der Schwanz schlägt seitlich (schneller beim Schwimmen),
     * das Maul schnappt im Takt, solange er schwimmt.
     */
    private void bewegen(float alter, float kopfY, float kopfX, boolean schwimmt) {
        rumpf.xRot = kopfX * Mth.DEG_TO_RAD;
        rumpf.yRot = kopfY * Mth.DEG_TO_RAD;
        float takt = alter * (schwimmt ? 0.45F : 0.15F);
        schwanz.yRot = 0.25F * Mth.cos(takt);
        schwanzflosse.yRot = 0.35F * Mth.cos(takt - 0.8F);
        kiefer.xRot = schwimmt ? 0.12F + 0.12F * Math.max(0.0F, Mth.sin(alter * 0.25F)) : 0.05F;
    }

    //? if >=1.21.2 {
    @Override
    public void setupAnim(DolphinRenderState zustand) {
        super.setupAnim(zustand);
        bewegen(zustand.ageInTicks, zustand.yRot, zustand.xRot, zustand.isMoving);
    }
    //?} else {
    /*@Override
    public ModelPart root() {
        return wurzel;
    }

    @Override
    public void setupAnim(HaiEntity hai, float a, float b, float alter, float kopfY, float kopfX) {
        root().getAllParts().forEach(ModelPart::resetPose);
        bewegen(alter, kopfY, kopfX, hai.getDeltaMovement().horizontalDistanceSqr() > 1.0E-7);
    }
    *///?}
}
