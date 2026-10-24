package de.moinmornhart.stinkendreich.mechanik;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.moinmornhart.stinkendreich.register.ModBloecke;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Gemeinsame Block-Entity aller Mechanismen. Speichert frei wählbare Werte (Code, Etagenname, Modus …)
 * und gibt den Takt an den Block weiter, falls dieser {@link MechanikBlock#takt()} &gt; 0 hat.
 */
public class MechanikBlockEntity extends DatenBlockEntity {
    private JsonObject werte = new JsonObject();
    /** Zähler für den Takt, nicht gespeichert. */
    int ticks;

    public MechanikBlockEntity(BlockPos pos, BlockState zustand) {
        super(ModBloecke.MECHANIK_TYP, pos, zustand);
    }

    @Override
    protected void schreiben(JsonObject daten) {
        daten.add("werte", werte);
    }

    @Override
    protected void lesen(JsonObject daten) {
        if (daten.has("werte")) {
            werte = daten.getAsJsonObject("werte");
        }
    }

    public String text(String name, String standard) {
        JsonElement e = werte.get(name);
        return e == null ? standard : e.getAsString();
    }

    public int zahl(String name, int standard) {
        JsonElement e = werte.get(name);
        return e == null ? standard : e.getAsInt();
    }

    public void setze(String name, String wert) {
        werte.addProperty(name, wert);
        geaendert();
    }

    public void setze(String name, int wert) {
        werte.addProperty(name, wert);
        geaendert();
    }

    /** Wert ändern, ohne an die Clients zu senden (für häufige Zähler). */
    public void setzeStill(String name, int wert) {
        werte.addProperty(name, wert);
        setChanged();
    }
}
