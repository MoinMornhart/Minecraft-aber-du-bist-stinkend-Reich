package de.moinmornhart.stinkendreich.client.auto;

//? if >=1.21.2
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Was der Renderer pro Bild vom Auto braucht (ab 1.21.2 getrennt von der Entity, davor ungenutzt). */
//? if >=1.21.2 {
public class AutoRenderState extends LivingEntityRenderState {
//?} else
/*public class AutoRenderState {*/
    public float radWinkel;
    public float lenkWinkel;
    public int farbe;
}
