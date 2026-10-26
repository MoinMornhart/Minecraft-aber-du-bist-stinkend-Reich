package de.moinmornhart.stinkendreich.mitarbeiter;

import com.google.gson.JsonObject;
import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import de.moinmornhart.stinkendreich.butler.Planer;
import de.moinmornhart.stinkendreich.butler.Quellen;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Mitarbeiter aus der Mitarbeiterkiste: arbeitet mit denselben Fähigkeiten wie James (Tunnel graben, abbauen,
 * Bäume fällen), aber immer an seiner einen Aufgabe – Diamanten, Eisen, Kohle, Gold, Smaragde, Redstone, Lapis oder
 * Holz. Jede Ladung (16 Stück) bringt er in seine Kiste; ist sie voll, macht er Pause. Rechtsklick des Chefs
 * wechselt die Aufgabe, Schleichen + Rechtsklick zeigt seine Taschen. Unverwundbar wie James.
 */
public class MitarbeiterEntity extends ButlerEntity {
    /** Aufgaben (Quellen des Butlers) und ihre Reihenfolge beim Wechseln. */
    public static final List<String> AUFGABEN = List.of("diamant", "rohes_eisen", "kohle", "rohes_gold", "smaragd", "redstone",
            "lapis", "holz");
    private static final String[] NAMEN = {"Ede", "Gustav", "Karl", "Heinz", "Otto", "Fritz", "Willi", "Paul"};
    private static final int LADUNG = 16;

    private String aufgabe = "diamant";
    private BlockPos kiste;
    private int pause;

    public MitarbeiterEntity(EntityType<? extends ButlerEntity> typ, Level welt) {
        super(typ, welt);
    }

    @Override
    public boolean istButler() {
        return false;
    }

    /** Beim Aufstellen: Chef, Kiste und Aufgabe festlegen. */
    public void einstellen(ServerPlayer chef, BlockPos kiste, String aufgabe, int nummer) {
        besitzerEintragen(chef.getUUID(), chef.getScoreboardName());
        this.kiste = kiste.immutable();
        this.aufgabe = aufgabe;
        setzeZuhause(kiste);
        setzeHeimat(kiste);
        setzeLiefertruhe(kiste);
        setCustomName(Component.literal(NAMEN[Math.floorMod(nummer, NAMEN.length)]));
    }

    public String aufgabe() {
        return aufgabe;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() || kiste == null) {
            return;
        }
        if (pause > 0) {
            pause--;
            return;
        }
        if (tickCount % 40 == 0 && untaetig()) {
            if (kisteVoll()) {
                pause = 20 * 30;
                return;
            }
            Planer.Plan plan = Planer.bestellung(this, aufgabe, LADUNG, true);
            if (plan.ok()) {
                neueAuftraege(plan.schritte());
            } else {
                pause = 20 * 60;
            }
        }
    }

    private boolean kisteVoll() {
        if (!(level().getBlockEntity(kiste) instanceof Container c)) {
            return false;
        }
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (c.getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected InteractionResult mobInteract(Player spieler, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        ServerPlayer sp = (ServerPlayer) spieler;
        if (!istBesitzer(sp)) {
            Kompat.nachricht(sp, Component.translatable("stinkendreich.mitarbeiter.fremd", getName()), true);
        } else if (spieler.isShiftKeyDown()) {
            inventarZeigen(sp);
        } else {
            // Nächste Aufgabe; was er gerade tut, bricht er ab
            aufgabe = AUFGABEN.get((AUFGABEN.indexOf(aufgabe) + 1) % AUFGABEN.size());
            stopp();
            pause = 0;
            Kompat.nachricht(sp, Component.translatable("stinkendreich.mitarbeiter.aufgabe", getName(),
                    Quellen.get(aufgabe).anzeigeStapel().getHoverName()).withStyle(ChatFormatting.GOLD), true);
        }
        return InteractionResult.SUCCESS;
    }

    /** Mitarbeiter reden nicht im Chat, nur kurz über der Schnellleiste, wenn der Chef in der Nähe ist. */
    @Override
    public void sagen(Component text) {
        ServerPlayer p = besitzerOnline();
        if (p != null && p.distanceToSqr(this) < 32 * 32) {
            Kompat.nachricht(p, Component.literal("[" + getName().getString() + "] ").withStyle(ChatFormatting.YELLOW).append(text), true);
        }
    }

    @Override
    protected void zusatzSchreiben(JsonObject j) {
        j.addProperty("aufgabe", aufgabe);
        if (kiste != null) {
            j.add("kiste", posJson(kiste));
        }
    }

    @Override
    protected void zusatzLesen(JsonObject j) {
        if (j.has("aufgabe") && AUFGABEN.contains(j.get("aufgabe").getAsString())) {
            aufgabe = j.get("aufgabe").getAsString();
        }
        kiste = j.has("kiste") ? posAusJson(j.getAsJsonArray("kiste")) : null;
    }
}
