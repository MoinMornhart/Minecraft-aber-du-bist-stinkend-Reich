package de.moinmornhart.stinkendreich.client.club;

//? if >=1.21.2
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** Was Modell und Renderer pro Bild von der Tänzerin brauchen (ab 1.21.2 getrennt von der Entity). */
//? if >=1.21.2 {
public class TaenzerinZustand extends HumanoidRenderState {
//?} else
/*public class TaenzerinZustand {*/
    /** 0 = läuft, 1 = tanzt frei, 2 = tanzt an der Stange (siehe TaenzerinEntity). */
    public int tanz;
    public int variante;
}
