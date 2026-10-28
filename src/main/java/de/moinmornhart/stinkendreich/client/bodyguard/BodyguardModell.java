package de.moinmornhart.stinkendreich.client.bodyguard;

import net.minecraft.client.model.geom.ModelPart;
//? if >=1.21.11 {
import net.minecraft.client.model.animal.golem.IronGolemModel;
//?} else
/*import net.minecraft.client.model.IronGolemModel;*/
//? if >=1.21.2 {
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
//?} else {
/*import de.moinmornhart.stinkendreich.bodyguard.BodyguardEntity;
import net.minecraft.world.entity.animal.IronGolem;
*///?}

/**
 * Golem-Modell des Bodyguards: Zielt er, hebt er den rechten Arm mit der Pistole in Blickrichtung (wie ein Spieler mit
 * geladener Armbrust); sonst bewegt er sich wie ein normaler Eisengolem.
 */
//? if >=1.21.2 {
public class BodyguardModell extends IronGolemModel {
//?} else
/*public class BodyguardModell extends IronGolemModel<IronGolem> {*/
    public BodyguardModell(ModelPart wurzel) {
        super(wurzel);
    }

    //? if >=1.21.2 {
    @Override
    public void setupAnim(IronGolemRenderState zustand) {
        super.setupAnim(zustand);
        if (zustand instanceof BodyguardRenderState b && !b.nahkampf) {
            // Pistole immer in der Hand wie ein Spieler: beim Zielen waagerecht zum Gegner, sonst etwas gesenkt
            armHeben(b.zielt ? zustand.yRot : 0.0F, b.zielt ? zustand.xRot : BEREIT);
        }
    }
    //?} else {
    /*@Override
    public void setupAnim(IronGolem golem, float schritt, float schwung, float alter, float kopfGier, float kopfNeigung) {
        super.setupAnim(golem, schritt, schwung, alter, kopfGier, kopfNeigung);
        if (golem instanceof BodyguardEntity b && !b.nahkampf()) {
            armHeben(b.zielt() ? kopfGier : 0.0F, b.zielt() ? kopfNeigung : BEREIT);
        }
    }
    *///?}

    /** Neigung des Arms, wenn er nicht zielt (Grad nach unten): Pistole bereit, Lauf schräg nach vorn unten. */
    private static final float BEREIT = 25.0F;

    /** Rechter Arm waagerecht nach vorn und mit dem Kopf mitgedreht (Gier und Neigung des Kopfes in Grad). */
    private void armHeben(float gier, float neigung) {
        ModelPart arm = getFlowerHoldingArm();
        arm.xRot = (float) Math.toRadians(-90.0F + neigung);
        arm.yRot = (float) Math.toRadians(gier);
        arm.zRot = 0.0F;
    }
}
