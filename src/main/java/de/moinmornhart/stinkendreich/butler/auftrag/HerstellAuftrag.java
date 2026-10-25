package de.moinmornhart.stinkendreich.butler.auftrag;

import com.google.gson.JsonObject;
import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import de.moinmornhart.stinkendreich.butler.Quellen;
import de.moinmornhart.stinkendreich.butler.Umgebung;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * Stellt Gegenstände nach Rezept her (Bretter, Stöcke, Fackeln, Truhen, Werkzeuge …).
 * Für große Rezepte geht der Butler an eine Werkbank in der Nähe, falls es eine gibt.
 */
public class HerstellAuftrag extends Auftrag {
    public static final String TYP = "herstellen";
    private static final int ARBEIT_TICKS = 20;

    private final String rezept;
    private final int mal;
    private int erledigt;

    private BlockPos werkbank;
    private boolean werkbankGesucht;
    private int arbeit;

    /** @param rezept Quelle mit Weg HERSTELLEN, @param mal wie oft das Rezept ausgeführt wird */
    public HerstellAuftrag(String rezept, int mal) {
        this.rezept = rezept;
        this.mal = mal;
    }

    @Override
    protected Stand tick(ButlerEntity butler) {
        Quellen.Quelle q = Quellen.get(rezept);
        if (q == null || q.weg() != Quellen.Weg.HERSTELLEN) {
            return fehlschlag(Component.translatable("stinkendreich.butler.unbekannt", rezept));
        }
        if (erledigt >= mal) {
            return Stand.FERTIG;
        }
        // Werkbank suchen und hingehen (ohne Werkbank in der Nähe arbeitet er an Ort und Stelle)
        if (q.werkbank() && !werkbankGesucht) {
            werkbankGesucht = true;
            List<BlockPos> tische = Umgebung.bloecke(butler.welt(), butler.blockPosition(), 16, s -> s.is(Blocks.CRAFTING_TABLE));
            werkbank = tische.isEmpty() ? null : tische.get(0);
        }
        if (werkbank != null) {
            ButlerEntity.Weg w = butler.hinkommen(werkbank, 2.2);
            if (w == ButlerEntity.Weg.UNTERWEGS) {
                return Stand.LAEUFT;
            }
            if (w == ButlerEntity.Weg.UNERREICHBAR) {
                werkbank = null;
            } else {
                butler.schauAuf(werkbank);
            }
        }
        if (++arbeit % 5 == 0) {
            Kompat.schwingen(butler);
        }
        if (arbeit < ARBEIT_TICKS) {
            return Stand.LAEUFT;
        }
        arbeit = 0;
        // Wie oft reicht es noch? Dann alles auf einmal herstellen
        int moeglich = mal - erledigt;
        for (Quellen.Zutat z : q.zutaten()) {
            Quellen.Quelle zq = Quellen.get(z.quelle());
            moeglich = Math.min(moeglich, butler.taschen().zaehlen(zq.passt()) / z.anzahl());
        }
        if (moeglich <= 0) {
            if (erledigt > 0) {
                return Stand.FERTIG;
            }
            return fehlschlag(Component.translatable("stinkendreich.butler.zutatenfehlen", q.anzeigeStapel().getHoverName()));
        }
        if (rezept.equals("bretter")) {
            // Bretter haben die Holzart des Stamms
            for (ItemStack stamm : butler.taschen().entnehmen(Quellen.get("holz").passt(), moeglich)) {
                einpacken(butler, new ItemStack(Quellen.bretterAus(stamm), stamm.getCount() * q.menge()));
            }
        } else {
            for (Quellen.Zutat z : q.zutaten()) {
                butler.taschen().entnehmen(Quellen.get(z.quelle()).passt(), z.anzahl() * moeglich);
            }
            int stueck = moeglich * q.menge();
            while (stueck > 0) {
                int teil = Math.min(stueck, q.anzeige().getDefaultMaxStackSize());
                einpacken(butler, new ItemStack(q.anzeige(), teil));
                stueck -= teil;
            }
        }
        erledigt += moeglich;
        butler.playSound(SoundEvents.VILLAGER_WORK_TOOLSMITH, 0.6F, 1.0F);
        return erledigt >= mal ? Stand.FERTIG : Stand.LAEUFT;
    }

    private static void einpacken(ButlerEntity butler, ItemStack s) {
        ItemStack rest = butler.taschen().einpacken(s);
        if (!rest.isEmpty()) {
            net.minecraft.world.level.block.Block.popResource(butler.welt(), butler.blockPosition(), rest);
        }
    }

    @Override
    public Component status() {
        Quellen.Quelle q = Quellen.get(rezept);
        Component name = q == null ? Component.literal(rezept) : q.anzeigeStapel().getHoverName();
        return Component.translatable("stinkendreich.butler.status.herstellen", name, erledigt, mal);
    }

    @Override
    public String typ() {
        return TYP;
    }

    @Override
    public void schreiben(JsonObject j) {
        j.addProperty("rezept", rezept);
        j.addProperty("mal", mal);
        j.addProperty("erledigt", erledigt);
    }

    static HerstellAuftrag ausJson(JsonObject j) {
        HerstellAuftrag a = new HerstellAuftrag(text(j, "rezept", "bretter"), zahl(j, "mal", 1));
        a.erledigt = zahl(j, "erledigt", 0);
        return a;
    }
}
