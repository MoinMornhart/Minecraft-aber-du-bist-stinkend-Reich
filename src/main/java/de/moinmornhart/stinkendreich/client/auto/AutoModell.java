package de.moinmornhart.stinkendreich.client.auto;

import de.moinmornhart.stinkendreich.StinkendReich;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
//? if >=1.21.2 {
import net.minecraft.client.model.EntityModel;
//?} else {
/*import de.moinmornhart.stinkendreich.auto.AutoEntity;
import net.minecraft.client.model.HierarchicalModel;
*///?}

/**
 * Modell des Bonzenautos: flaches Sportcabrio (4,5 × 2 Blöcke) mit langer Motorhaube,
 * Frontscheibe, zwei Sitzreihen, Heckspoiler, Scheinwerfern, Rückleuchten und vier Rädern.
 * Vorne ist im Modell -Z. Textur 256 × 256 (tools/assets/auto.py).
 */
//? if >=1.21.2 {
public class AutoModell extends EntityModel<AutoRenderState> {
//?} else
/*public class AutoModell extends HierarchicalModel<AutoEntity> {*/
    public static final ModelLayerLocation SCHICHT = new ModelLayerLocation(StinkendReich.id("bonzenauto"), "main");

    private final ModelPart wurzel;
    private final ModelPart radVL, radVR, radHL, radHR;

    public AutoModell(ModelPart wurzel) {
        //? if >=1.21.2
        super(wurzel);
        this.wurzel = wurzel;
        this.radVL = wurzel.getChild("rad_vl");
        this.radVR = wurzel.getChild("rad_vr");
        this.radHL = wurzel.getChild("rad_hl");
        this.radHR = wurzel.getChild("rad_hr");
    }

    public static LayerDefinition schicht() {
        MeshDefinition netz = new MeshDefinition();
        PartDefinition w = netz.getRoot();
        // Karosserie: Ursprung auf dem Boden (y = 24), negative y-Werte gehen nach oben
        PartDefinition k = w.addOrReplaceChild("karosserie", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-15.0F, -8.0F, -36.0F, 30.0F, 5.0F, 72.0F)      // Unterbau
                .texOffs(0, 80).addBox(-15.0F, -11.0F, -36.0F, 30.0F, 3.0F, 26.0F)    // Motorhaube
                .texOffs(114, 80).addBox(-15.0F, -12.0F, -10.0F, 2.0F, 4.0F, 34.0F)   // Türen links
                .texOffs(114, 120).addBox(13.0F, -12.0F, -10.0F, 2.0F, 4.0F, 34.0F)   // Türen rechts
                .texOffs(0, 112).addBox(-15.0F, -12.0F, 24.0F, 30.0F, 4.0F, 12.0F)    // Heck
                .texOffs(0, 160).addBox(-13.0F, -9.0F, -9.0F, 26.0F, 1.0F, 32.0F),    // Innenboden
                PartPose.offset(0.0F, 24.0F, 0.0F));
        k.addOrReplaceChild("scheibe", CubeListBuilder.create().texOffs(120, 160)
                .addBox(-13.0F, -6.0F, 0.0F, 26.0F, 6.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -11.0F, -10.0F, -0.5F, 0.0F, 0.0F));
        k.addOrReplaceChild("sitze", CubeListBuilder.create()
                .texOffs(180, 160).addBox(-11.0F, -12.0F, 4.0F, 9.0F, 4.0F, 2.0F)     // Lehne vorne links
                .texOffs(180, 160).addBox(2.0F, -12.0F, 4.0F, 9.0F, 4.0F, 2.0F)       // Lehne vorne rechts
                .texOffs(180, 160).addBox(-11.0F, -12.0F, 20.0F, 9.0F, 4.0F, 2.0F)    // Lehne hinten links
                .texOffs(180, 160).addBox(2.0F, -12.0F, 20.0F, 9.0F, 4.0F, 2.0F)      // Lehne hinten rechts
                .texOffs(180, 170).addBox(-11.0F, -10.0F, -3.0F, 9.0F, 1.0F, 7.0F)     // Sitzflächen
                .texOffs(180, 170).addBox(2.0F, -10.0F, -3.0F, 9.0F, 1.0F, 7.0F)
                .texOffs(180, 170).addBox(-11.0F, -10.0F, 13.0F, 9.0F, 1.0F, 7.0F)
                .texOffs(180, 170).addBox(2.0F, -10.0F, 13.0F, 9.0F, 1.0F, 7.0F), PartPose.ZERO);
        k.addOrReplaceChild("lenkrad", CubeListBuilder.create().texOffs(216, 160)
                .addBox(4.0F, -14.0F, -6.0F, 5.0F, 4.0F, 1.0F), PartPose.ZERO);
        k.addOrReplaceChild("spoiler", CubeListBuilder.create()
                .texOffs(0, 196).addBox(-15.0F, -17.0F, 30.0F, 30.0F, 1.0F, 5.0F)
                .texOffs(80, 196).addBox(-10.0F, -16.0F, 31.0F, 2.0F, 4.0F, 2.0F)
                .texOffs(80, 196).addBox(8.0F, -16.0F, 31.0F, 2.0F, 4.0F, 2.0F), PartPose.ZERO);
        k.addOrReplaceChild("lichter", CubeListBuilder.create()
                .texOffs(0, 206).addBox(-14.0F, -10.0F, -36.6F, 7.0F, 2.0F, 1.0F)    // Scheinwerfer
                .texOffs(0, 206).addBox(7.0F, -10.0F, -36.6F, 7.0F, 2.0F, 1.0F)
                .texOffs(20, 206).addBox(-14.0F, -11.0F, 35.6F, 8.0F, 2.0F, 1.0F)    // Rückleuchten
                .texOffs(20, 206).addBox(6.0F, -11.0F, 35.6F, 8.0F, 2.0F, 1.0F)
                .texOffs(42, 206).addBox(-6.0F, -8.0F, -36.5F, 12.0F, 3.0F, 1.0F)    // Kühlergrill
                .texOffs(70, 206).addBox(-17.0F, -13.0F, -9.0F, 2.0F, 1.0F, 2.0F)    // Spiegel
                .texOffs(70, 206).addBox(15.0F, -13.0F, -9.0F, 2.0F, 1.0F, 2.0F), PartPose.ZERO);
        // Räder (Durchmesser 10 Pixel), drehen sich um die x-Achse, vorne gelenkt
        CubeListBuilder rad = CubeListBuilder.create().texOffs(0, 216).addBox(-2.0F, -5.0F, -5.0F, 4.0F, 10.0F, 10.0F);
        w.addOrReplaceChild("rad_vl", rad, PartPose.offset(-14.0F, 19.0F, -22.0F));
        w.addOrReplaceChild("rad_vr", rad, PartPose.offset(14.0F, 19.0F, -22.0F));
        w.addOrReplaceChild("rad_hl", rad, PartPose.offset(-14.0F, 19.0F, 23.0F));
        w.addOrReplaceChild("rad_hr", rad, PartPose.offset(14.0F, 19.0F, 23.0F));
        return LayerDefinition.create(netz, 256, 256);
    }

    /** Räder drehen und Vorderräder einschlagen. */
    private void raeder(float drehung, float lenkung) {
        radVL.xRot = radVR.xRot = radHL.xRot = radHR.xRot = -drehung;
        radVL.yRot = radVR.yRot = lenkung;
    }

    //? if >=1.21.2 {
    @Override
    public void setupAnim(AutoRenderState zustand) {
        super.setupAnim(zustand);
        raeder(zustand.radWinkel, zustand.lenkWinkel);
    }
    //?} else {
    /*@Override
    public ModelPart root() {
        return wurzel;
    }

    @Override
    public void setupAnim(AutoEntity auto, float a, float b, float alter, float kopfY, float kopfX) {
        float zwischen = alter - (float) auto.tickCount;
        raeder(net.minecraft.util.Mth.lerp(zwischen, auto.radWinkelAlt, auto.radWinkel), auto.lenkWinkel);
    }
    *///?}
}
