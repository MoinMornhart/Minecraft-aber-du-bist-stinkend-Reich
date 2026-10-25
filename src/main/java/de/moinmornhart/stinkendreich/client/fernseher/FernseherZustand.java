package de.moinmornhart.stinkendreich.client.fernseher;

//? if >=1.21.2
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Was der Renderer pro Bild vom Fernseher braucht (ab 1.21.2 getrennt von der Entity). */
//? if >=1.21.2 {
public class FernseherZustand extends LivingEntityRenderState {
//?} else
/*public class FernseherZustand {*/
    public String video = "";
}
