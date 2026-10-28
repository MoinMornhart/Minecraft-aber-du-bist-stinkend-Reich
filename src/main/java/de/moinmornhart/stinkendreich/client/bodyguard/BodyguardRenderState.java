package de.moinmornhart.stinkendreich.client.bodyguard;

//? if >=1.21.2 {
/** Zeichenzustand des Bodyguards: wie beim Eisengolem, dazu ob er gerade mit der Pistole zielt oder im Nahkampf ist. */
public class BodyguardRenderState extends net.minecraft.client.renderer.entity.state.IronGolemRenderState {
    public boolean zielt;
    /** Im Nahkampf: Pistole weggesteckt. */
    public boolean nahkampf;
}
//?}
