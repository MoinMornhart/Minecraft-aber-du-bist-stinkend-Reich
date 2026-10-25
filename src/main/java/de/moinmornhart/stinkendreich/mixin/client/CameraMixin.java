package de.moinmornhart.stinkendreich.mixin.client;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
//? if <1.21.6 {
/*import de.moinmornhart.stinkendreich.auto.AutoEntity;
import de.moinmornhart.stinkendreich.client.auto.AutoKlient;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
*///?}

/**
 * Verfolgerkamera im Bonzenauto für Minecraft vor 1.21.6: vergrößert den Kameraabstand in der
 * Ansicht von hinten. Ab 1.21.6 liest Minecraft den Abstand selbst aus dem Fahrzeug (Attribut),
 * dann bleibt dieser Mixin leer.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    //? if <1.21.6 {
    /*@ModifyArg(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
    private float stinkendreich$autoAbstand(float abstand) {
        Entity e = ((Camera) (Object) this).getEntity();
        if (e != null && e.getVehicle() instanceof AutoEntity && AutoKlient.kameraAbstand > 0) {
            return Math.max(abstand, AutoKlient.kameraAbstand);
        }
        return abstand;
    }
    *///?}
}
