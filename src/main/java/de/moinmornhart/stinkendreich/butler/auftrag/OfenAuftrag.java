package de.moinmornhart.stinkendreich.butler.auftrag;

import com.google.gson.JsonObject;
import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import de.moinmornhart.stinkendreich.butler.Quellen;
import de.moinmornhart.stinkendreich.butler.Schmelzen;
import de.moinmornhart.stinkendreich.butler.Taschen;
import de.moinmornhart.stinkendreich.butler.Umgebung;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Schmilzt oder brät Gegenstände in echten Öfen: geht hin, legt Rohware und Brennstoff ein,
 * wartet daneben und nimmt das Ergebnis wieder heraus. Nutzt bis zu drei Öfen gleichzeitig
 * (Hochofen für Erze und Räucherofen für Essen sind doppelt so schnell).
 */
public class OfenAuftrag extends Auftrag {
    public static final String TYP = "ofen";
    private static final int SLOT_EIN = 0, SLOT_BRENN = 1, SLOT_AUS = 2;
    private static final int MAX_OEFEN = 3;

    private enum Phase { SUCHEN, BRENNSTOFF, BELADEN, WARTEN, ENTNEHMEN }

    private final String eingang;
    private final int menge;
    private int fertig;

    private Phase phase = Phase.SUCHEN;
    private final List<Platz> plaetze = new ArrayList<>();
    private int index;
    private int warteTicks;
    private int brennstoffTruhe;
    private Item ergebnis;

    /** Ein belegter Ofen: wie viel wir hineingelegt haben und was vorher schon im Ausgang lag. */
    private static final class Platz {
        final BlockPos pos;
        int menge;
        int vorher;

        Platz(BlockPos pos) {
            this.pos = pos;
        }
    }

    /** @param eingang Quelle, die geschmolzen wird (z. B. „rohes_eisen“), @param menge Anzahl */
    public OfenAuftrag(String eingang, int menge) {
        this.eingang = eingang;
        this.menge = menge;
    }

    private Quellen.Quelle quelle() {
        return Quellen.get(eingang);
    }

    @Override
    protected Stand tick(ButlerEntity butler) {
        Quellen.Quelle q = quelle();
        if (q == null) {
            return fehlschlag(Component.translatable("stinkendreich.butler.unbekannt", eingang));
        }
        ServerLevel welt = butler.welt();
        switch (phase) {
            case SUCHEN -> {
                int rest = Math.min(menge - fertig, butler.taschen().zaehlen(q.passt()));
                if (rest <= 0) {
                    return Stand.FERTIG;
                }
                ItemStack probe = probe(butler, q);
                ergebnis = Schmelzen.ergebnis(probe);
                if (ergebnis == null) {
                    return fehlschlag(Component.translatable("stinkendreich.butler.nichtschmelzbar", probe.getHoverName()));
                }
                List<BlockPos> oefen = Umgebung.finden(butler, "ofen", 24, be -> be instanceof AbstractFurnaceBlockEntity o
                        && Schmelzen.passt(o, probe) && frei(o, probe));
                // Schnelle Spezialöfen zuerst
                oefen.sort(Comparator.comparingInt(p -> welt.getBlockEntity(p) instanceof BlastFurnaceBlockEntity
                        || welt.getBlockEntity(p) instanceof SmokerBlockEntity ? 0 : 1));
                if (oefen.isEmpty()) {
                    return fehlschlag(Component.translatable("stinkendreich.butler.keinofen"));
                }
                plaetze.clear();
                for (BlockPos p : oefen.subList(0, Math.min(MAX_OEFEN, oefen.size()))) {
                    plaetze.add(new Platz(p));
                }
                // Brennstoff reicht? Sonst erst aus dem Vorrat der Villa holen
                // (je Ofen ein Stück Kohle Reserve, weil sie nur ganz eingelegt werden kann)
                if (brennwert(butler, q) < bedarf(rest)) {
                    phase = Phase.BRENNSTOFF;
                    brennstoffTruhe = 0;
                } else {
                    phase = Phase.BELADEN;
                }
                index = 0;
            }
            case BRENNSTOFF -> {
                List<BlockPos> truhen = Umgebung.villaMarken(welt, "brennstoff");
                int rest = Math.min(menge - fertig, butler.taschen().zaehlen(q.passt()));
                if (brennstoffTruhe >= truhen.size() || brennwert(butler, q) >= bedarf(rest)) {
                    phase = Phase.BELADEN;
                    index = 0;
                    return Stand.LAEUFT;
                }
                BlockPos t = truhen.get(brennstoffTruhe);
                ButlerEntity.Weg w = butler.hinkommen(t, 2.2);
                if (w == ButlerEntity.Weg.UNERREICHBAR) {
                    brennstoffTruhe++;
                } else if (w == ButlerEntity.Weg.DA) {
                    if (welt.getBlockEntity(t) instanceof Container c) {
                        int fehlt = (bedarf(rest) - brennwert(butler, q) + 15) / 16;
                        for (ItemStack s : Taschen.entnehmen(c, st -> st.is(net.minecraft.world.item.Items.COAL)
                                || st.is(net.minecraft.world.item.Items.CHARCOAL), fehlt)) {
                            butler.taschen().einpacken(s);
                        }
                        Kompat.schwingen(butler);
                    }
                    brennstoffTruhe++;
                }
            }
            case BELADEN -> {
                if (index >= plaetze.size()) {
                    if (plaetze.stream().allMatch(p -> p.menge == 0)) {
                        return fehlschlag(Component.translatable("stinkendreich.butler.keinbrennstoff"));
                    }
                    phase = Phase.WARTEN;
                    warteTicks = 0;
                    return Stand.LAEUFT;
                }
                Platz platz = plaetze.get(index);
                ButlerEntity.Weg w = butler.hinkommen(platz.pos, 2.2);
                if (w == ButlerEntity.Weg.UNERREICHBAR) {
                    index++;
                } else if (w == ButlerEntity.Weg.DA) {
                    beladen(butler, q, platz);
                    index++;
                }
            }
            case WARTEN -> {
                warteTicks++;
                butler.schauAuf(plaetze.get(0).pos);
                if (warteTicks % 20 != 0) {
                    return Stand.LAEUFT;
                }
                boolean alleFertig = true;
                int dauer = 0;
                for (Platz p : plaetze) {
                    if (p.menge > 0 && welt.getBlockEntity(p.pos) instanceof AbstractFurnaceBlockEntity o) {
                        if (!o.getItem(SLOT_EIN).isEmpty() && q.passt().test(o.getItem(SLOT_EIN))) {
                            alleFertig = false;
                        }
                        int jeStueck = o instanceof BlastFurnaceBlockEntity || o instanceof SmokerBlockEntity ? 100 : 200;
                        dauer = Math.max(dauer, p.menge * jeStueck);
                    }
                }
                if (alleFertig || warteTicks > dauer + 20 * 30) {
                    phase = Phase.ENTNEHMEN;
                    index = 0;
                }
            }
            case ENTNEHMEN -> {
                if (index >= plaetze.size()) {
                    phase = Phase.SUCHEN;
                    return fertig >= menge ? Stand.FERTIG : Stand.LAEUFT;
                }
                Platz platz = plaetze.get(index);
                if (platz.menge == 0) {
                    index++;
                    return Stand.LAEUFT;
                }
                ButlerEntity.Weg w = butler.hinkommen(platz.pos, 2.2);
                if (w == ButlerEntity.Weg.DA || w == ButlerEntity.Weg.UNERREICHBAR) {
                    entnehmen(butler, q, platz);
                    index++;
                }
            }
        }
        return Stand.LAEUFT;
    }

    /** Brennwert (halbe Schmelzvorgänge), der für {@code stueck} Gegenstände in den gewählten Öfen nötig ist. */
    private int bedarf(int stueck) {
        return stueck * 2 + 16 * Math.max(1, plaetze.size());
    }

    /** Ein Beispiel-Gegenstand der Rohware aus den Taschen. */
    private static ItemStack probe(ButlerEntity butler, Quellen.Quelle q) {
        for (int i = 0; i < butler.taschen().getContainerSize(); i++) {
            ItemStack s = butler.taschen().getItem(i);
            if (!s.isEmpty() && q.passt().test(s)) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Ist der Ofen frei (leer oder schon mit derselben Ware) und der Ausgang nicht mit Fremdem belegt? */
    private static boolean frei(AbstractFurnaceBlockEntity o, ItemStack probe) {
        ItemStack ein = o.getItem(SLOT_EIN), aus = o.getItem(SLOT_AUS);
        Item erwartet = Schmelzen.ergebnis(probe);
        return (ein.isEmpty() || ItemStack.isSameItemSameComponents(ein, probe))
                && (aus.isEmpty() || (aus.is(erwartet) && aus.getCount() < aus.getMaxStackSize() - 16));
    }

    /** Brennwert aller Brennstoffe in den Taschen (die Rohware selbst zählt nicht). */
    private static int brennwert(ButlerEntity butler, Quellen.Quelle q) {
        int w = 0;
        for (int i = 0; i < butler.taschen().getContainerSize(); i++) {
            ItemStack s = butler.taschen().getItem(i);
            if (!s.isEmpty() && !q.passt().test(s)) {
                w += Schmelzen.brennwert(s) * s.getCount();
            }
        }
        return w;
    }

    private void beladen(ButlerEntity butler, Quellen.Quelle q, Platz platz) {
        if (!(butler.welt().getBlockEntity(platz.pos) instanceof AbstractFurnaceBlockEntity o)) {
            return;
        }
        int rest = Math.min(menge - fertig - plaetze.stream().mapToInt(p -> p.menge).sum(), butler.taschen().zaehlen(q.passt()));
        int offeneOefen = plaetze.size() - index;
        int portion = Math.min(64, (rest + offeneOefen - 1) / Math.max(1, offeneOefen));
        ItemStack ein = o.getItem(SLOT_EIN);
        if (!ein.isEmpty()) {
            portion = Math.min(portion, ein.getMaxStackSize() - ein.getCount());
        }
        // Brennstoff: Kohle bevorzugt, sonst Bretter oder Stämme (nicht die Rohware selbst)
        ItemStack brenn = o.getItem(SLOT_BRENN);
        int vorhanden = brenn.isEmpty() ? 0 : Schmelzen.brennwert(brenn) * brenn.getCount();
        int bedarf = Math.max(0, portion * 2 - vorhanden);
        if (bedarf > 0) {
            ItemStack art = brennstoffArt(butler, q, brenn);
            if (art.isEmpty()) {
                portion = Math.min(portion, vorhanden / 2);
            } else {
                int wert = Schmelzen.brennwert(art);
                int stueck = Math.min((bedarf + wert - 1) / wert, art.getMaxStackSize() - brenn.getCount());
                int bekommen = 0;
                for (ItemStack s : butler.taschen().entnehmen(st -> ItemStack.isSameItemSameComponents(st, art), stueck)) {
                    bekommen += s.getCount();
                }
                o.setItem(SLOT_BRENN, art.copyWithCount(brenn.getCount() + bekommen));
                portion = Math.min(portion, (vorhanden + bekommen * wert) / 2);
            }
        }
        if (portion <= 0) {
            return;
        }
        int gelegt = 0;
        ItemStack neu = ein.copy();
        for (ItemStack s : butler.taschen().entnehmen(q.passt(), portion)) {
            if (neu.isEmpty()) {
                neu = s.copy();
            } else {
                neu.grow(s.getCount());
            }
            gelegt += s.getCount();
        }
        o.setItem(SLOT_EIN, neu);
        o.setChanged();
        platz.menge = gelegt;
        platz.vorher = o.getItem(SLOT_AUS).getCount();
        Kompat.schwingen(butler);
        butler.playSound(SoundEvents.ITEM_PICKUP, 0.5F, 0.8F);
    }

    /** Bester Brennstoff aus den Taschen, passend zu dem, was schon im Ofen liegt. */
    private static ItemStack brennstoffArt(ButlerEntity butler, Quellen.Quelle q, ItemStack imOfen) {
        ItemStack bester = ItemStack.EMPTY;
        for (int i = 0; i < butler.taschen().getContainerSize(); i++) {
            ItemStack s = butler.taschen().getItem(i);
            if (s.isEmpty() || q.passt().test(s) || Schmelzen.brennwert(s) == 0) {
                continue;
            }
            if (!imOfen.isEmpty() && !ItemStack.isSameItemSameComponents(s, imOfen)) {
                continue;
            }
            if (bester.isEmpty() || Schmelzen.brennwert(s) > Schmelzen.brennwert(bester)) {
                bester = s.copyWithCount(1);
            }
        }
        return bester;
    }

    private void entnehmen(ButlerEntity butler, Quellen.Quelle q, Platz platz) {
        BlockEntity be = butler.welt().getBlockEntity(platz.pos);
        if (!(be instanceof AbstractFurnaceBlockEntity o)) {
            platz.menge = 0;
            return;
        }
        ItemStack aus = o.getItem(SLOT_AUS);
        int unser = Math.max(0, aus.getCount() - platz.vorher);
        if (unser > 0 && aus.is(ergebnis)) {
            ItemStack genommen = aus.split(unser);
            fertig += genommen.getCount();
            ItemStack rest = butler.taschen().einpacken(genommen);
            if (!rest.isEmpty()) {
                net.minecraft.world.level.block.Block.popResource(butler.welt(), butler.blockPosition(), rest);
            }
        }
        // Nicht Geschmolzenes (z. B. weil der Brennstoff ausging) wieder mitnehmen
        ItemStack ein = o.getItem(SLOT_EIN);
        if (!ein.isEmpty() && q.passt().test(ein)) {
            ItemStack rest = butler.taschen().einpacken(ein.copy());
            o.setItem(SLOT_EIN, rest);
        }
        o.setChanged();
        platz.menge = 0;
        Kompat.schwingen(butler);
        butler.playSound(SoundEvents.ITEM_PICKUP, 0.5F, 1.2F);
    }

    @Override
    public Component status() {
        Quellen.Quelle q = quelle();
        Component name = ergebnis != null ? ergebnis.getDefaultInstance().getHoverName()
                : q != null ? q.anzeigeStapel().getHoverName() : Component.literal(eingang);
        String schluessel = phase == Phase.WARTEN ? "stinkendreich.butler.status.ofenwarten" : "stinkendreich.butler.status.ofen";
        return Component.translatable(schluessel, name, fertig, menge);
    }

    @Override
    public String typ() {
        return TYP;
    }

    @Override
    public void schreiben(JsonObject j) {
        j.addProperty("eingang", eingang);
        j.addProperty("menge", menge);
        j.addProperty("fertig", fertig);
    }

    static OfenAuftrag ausJson(JsonObject j) {
        OfenAuftrag a = new OfenAuftrag(text(j, "eingang", "rohes_eisen"), zahl(j, "menge", 1));
        a.fertig = zahl(j, "fertig", 0);
        return a;
    }
}
