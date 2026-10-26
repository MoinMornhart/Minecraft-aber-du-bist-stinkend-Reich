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
    /** Hubschrauber: Rotorwinkel (Bogenmaß), Neigen und Rollen (Grad). */
    public float rotorWinkel, neigung, rollen;
    public int farbe;
    /** Name auf dem Nummernschild (leer = keine Schrift). */
    public String kennzeichen = "";
}
