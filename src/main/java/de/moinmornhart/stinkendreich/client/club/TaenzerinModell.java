package de.moinmornhart.stinkendreich.client.club;

import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.client.MenschenNetz;
import de.moinmornhart.stinkendreich.club.TaenzerinEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Modell der Show-Tänzerin: Menschengestalt im Layout einer Spieler-Haut mit schmalen Armen ({@link MenschenNetz};
 * die zweite Schicht trägt die langen Haare und das Glitzer-Top), dazu ein Oberteil vorn, das etwas absteht, und
 * Federschmuck auf dem Kopf. Beim Tanzen bewegen sich Arme, Beine und Hüfte im Takt:
 * an der Stange hält die rechte Hand die Stange über dem Kopf, frei tanzt sie mit beiden Armen in der Luft.
 * Textur 64×64, Aufteilung siehe tools/assets/taenzerin.py (dieselbe Haut nutzt auch die Schaufensterpuppe ohne Mod).
 */
//? if >=1.21.2 {
public class TaenzerinModell extends HumanoidModel<TaenzerinZustand> {
//?} else
/*public class TaenzerinModell extends HumanoidModel<TaenzerinEntity> {*/
    public static final ModelLayerLocation SCHICHT = new ModelLayerLocation(StinkendReich.id("taenzerin"), "main");

    public TaenzerinModell(ModelPart wurzel) {
        super(wurzel);
    }

    public static LayerDefinition schicht() {
        MeshDefinition netz = MenschenNetz.erstellen(true);
        PartDefinition wurzel = netz.getRoot();
        // Oberteil vorn: steht unten etwas weiter ab als oben (Fläche 24,0 der Haut, ganz aus Pailletten)
        wurzel.getChild("body").addOrReplaceChild("oberteil", CubeListBuilder.create().texOffs(24, 0)
                .addBox(-3.0F, 0.0F, -1.0F, 6.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 2.6F, -1.9F, -0.22F, 0.0F, 0.0F));
        // Federschmuck oben auf dem Kopf (Fläche 56,16)
        wurzel.getChild("head").addOrReplaceChild("feder", CubeListBuilder.create().texOffs(56, 16)
                .addBox(-1.0F, -13.0F, -1.0F, 2.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.15F, 0.0F, 0.0F));
        return LayerDefinition.create(netz, 64, 64);
    }

    //? if >=1.21.2 {
    @Override
    public void setupAnim(TaenzerinZustand zustand) {
        super.setupAnim(zustand);
        tanzen(zustand.tanz, zustand.ageInTicks);
    }
    //?} else {
    /*@Override
    public void setupAnim(TaenzerinEntity taenzerin, float schritt, float schrittWeite, float alter, float kopfY, float kopfX) {
        super.setupAnim(taenzerin, schritt, schrittWeite, alter, kopfY, kopfX);
        tanzen(taenzerin.tanz(), alter);
    }
    *///?}

    /** Tanzbewegung über die Grundhaltung legen (Winkel in Bogenmaß, t in Ticks). */
    private void tanzen(int tanz, float t) {
        float takt = Mth.sin(t * 0.18F);
        float halb = Mth.sin(t * 0.09F);
        if (tanz == TaenzerinEntity.STANGE) {
            // Rechte Hand oben an der Stange, linker Arm schwingt seitlich, rechtes Bein um die Stange gelegt
            rightArm.xRot = -2.75F + 0.12F * halb;
            rightArm.yRot = 0.0F;
            rightArm.zRot = 0.15F;
            leftArm.xRot = -0.7F + 0.45F * takt;
            leftArm.yRot = 0.0F;
            leftArm.zRot = -1.0F - 0.35F * takt;
            rightLeg.xRot = -0.55F + 0.3F * halb;
            rightLeg.zRot = 0.12F;
            leftLeg.xRot = 0.12F * takt;
            leftLeg.zRot = -0.05F;
            body.zRot = 0.06F * halb;
            head.zRot = 0.1F * halb;
        } else if (tanz == TaenzerinEntity.FREI) {
            // Beide Arme in der Luft, im Takt gegeneinander, Hüfte und Knie wippen mit
            rightArm.xRot = -2.6F + 0.3F * takt;
            rightArm.zRot = 0.3F + 0.2F * takt;
            leftArm.xRot = -2.6F - 0.3F * takt;
            leftArm.zRot = -0.3F + 0.2F * takt;
            rightLeg.xRot = 0.18F * takt;
            leftLeg.xRot = -0.18F * takt;
            body.zRot = 0.07F * takt;
            head.zRot = 0.08F * takt;
        }
    }
}
