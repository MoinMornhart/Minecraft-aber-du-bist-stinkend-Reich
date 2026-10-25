package de.moinmornhart.stinkendreich.butler.auftrag;

import com.google.gson.JsonObject;
import de.moinmornhart.stinkendreich.Konfig;
import de.moinmornhart.stinkendreich.butler.Abbau;
import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import de.moinmornhart.stinkendreich.butler.Rohstoffe;
import de.moinmornhart.stinkendreich.butler.Schutz;
import de.moinmornhart.stinkendreich.butler.Suche;
import de.moinmornhart.stinkendreich.butler.Tunnel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Baut eine bestimmte Menge eines Rohstoffs ab (Erz, Stein, Erde, Sand …).
 * Ablauf: aus der Villa hinausgehen, Umgebung absuchen, zum nächsten Vorkommen laufen oder einen
 * Tunnel graben, freilegen, abbauen – und dabei die ganze Erzader mitnehmen.
 */
public class SammelAuftrag extends Auftrag {
    public static final String TYP = "sammeln";

    private enum Phase { RAUS, SUCHEN, HIN, TUNNEL, ABBAUEN }

    private final String rohstoffId;
    private final int menge;
    private int gesammelt;

    // Laufzeit (nicht gespeichert)
    private Phase phase = Phase.RAUS;
    private Suche suche;
    private int suchRadius;
    private List<BlockPos> kandidaten = new ArrayList<>();
    private final Set<BlockPos> gesperrt = new HashSet<>();
    private final Deque<BlockPos> ader = new ArrayDeque<>();
    private BlockPos ziel;
    private int zielTicks;
    private final Abbau abbau = new Abbau();
    private final Tunnel tunnel = new Tunnel();

    public SammelAuftrag(String rohstoffId, int menge) {
        this.rohstoffId = rohstoffId;
        this.menge = menge;
    }

    private Rohstoffe.Rohstoff rohstoff() {
        return Rohstoffe.get(rohstoffId);
    }

    @Override
    protected Stand tick(ButlerEntity butler) {
        Rohstoffe.Rohstoff r = rohstoff();
        if (r == null) {
            return fehlschlag(Component.translatable("stinkendreich.butler.unbekannt", rohstoffId));
        }
        if (gesammelt >= menge) {
            return Stand.FERTIG;
        }
        if (butler.taschen().freiePlaetze() == 0) {
            butler.sagen(Component.translatable("stinkendreich.butler.voll"));
            return Stand.FERTIG;
        }
        ServerLevel welt = butler.welt();
        switch (phase) {
            case RAUS -> {
                Stand s = Ausgang.hinaus(butler);
                if (s != Stand.LAEUFT) {
                    phase = Phase.SUCHEN;
                }
            }
            case SUCHEN -> {
                if (suche == null) {
                    suchRadius = suchRadius == 0 ? Math.min(32, Konfig.get().butlerSuchradius) : suchRadius;
                    suche = new Suche(butler.blockPosition(), suchRadius, r.block());
                }
                if (suche.weiter(welt)) {
                    kandidaten = new ArrayList<>(suche.sortiert(butler.blockPosition()));
                    kandidaten.removeIf(p -> gesperrt.contains(p) || !Schutz.darfAbbauen(welt, p, welt.getBlockState(p)));
                    suche = null;
                    if (kandidaten.isEmpty()) {
                        if (suchRadius < Konfig.get().butlerSuchradius) {
                            suchRadius = Konfig.get().butlerSuchradius;
                            return Stand.LAEUFT;
                        }
                        return nichtsGefunden(butler);
                    }
                    naechstesZiel(butler);
                }
            }
            case HIN -> {
                if (!r.block().test(welt.getBlockState(ziel))) {
                    naechstesZiel(butler);
                    return Stand.LAEUFT;
                }
                if (butler.inReichweite(ziel) && Tunnel.naechsterBlock(butler, ziel).equals(ziel)) {
                    phase = Phase.ABBAUEN;
                    butler.wegVergessen();
                    return Stand.LAEUFT;
                }
                ButlerEntity.Weg w = butler.gehZu(ziel, 2.5);
                if (w == ButlerEntity.Weg.DA || w == ButlerEntity.Weg.UNERREICHBAR) {
                    // Liegt es im Fels oder ist kein Weg frei: Tunnel graben
                    phase = Phase.TUNNEL;
                    butler.wegVergessen();
                }
            }
            case TUNNEL -> {
                Tunnel.Ergebnis e = tunnel.tick(butler, ziel, r.werkzeug());
                if (e == Tunnel.Ergebnis.NAH) {
                    phase = Phase.ABBAUEN;
                } else if (e == Tunnel.Ergebnis.BLOCKIERT) {
                    sperren(butler);
                }
            }
            case ABBAUEN -> {
                if (!r.block().test(welt.getBlockState(ziel))) {
                    naechstesZiel(butler);
                    return Stand.LAEUFT;
                }
                if (!butler.inReichweite(ziel)) {
                    phase = Phase.TUNNEL;
                    return Stand.LAEUFT;
                }
                BlockPos block = Tunnel.naechsterBlock(butler, ziel);
                Abbau.Ergebnis e = abbau.tick(butler, block, r.werkzeug(), r.ertrag());
                if (e == Abbau.Ergebnis.VERBOTEN) {
                    sperren(butler);
                } else if (e == Abbau.Ergebnis.FERTIG && block.equals(ziel)) {
                    gesammelt += abbau.zuletztGezaehlt();
                    aderMerken(butler, ziel);
                    naechstesZiel(butler);
                }
            }
        }
        // Hängt er zu lange an einem Ziel fest, nimmt er ein anderes
        if (ziel != null && ++zielTicks > 20 * 120) {
            sperren(butler);
        }
        return Stand.LAEUFT;
    }

    private Stand nichtsGefunden(ButlerEntity butler) {
        if (gesammelt > 0) {
            butler.sagen(Component.translatable("stinkendreich.butler.nur", gesammelt, rohstoff().anzeige().getDefaultInstance().getHoverName()));
            return Stand.FERTIG;
        }
        return fehlschlag(Component.translatable("stinkendreich.butler.nichtgefunden", rohstoff().anzeige().getDefaultInstance().getHoverName()));
    }

    /** Merkt sich benachbarte Blöcke derselben Ader (werden zuerst abgebaut). */
    private void aderMerken(ButlerEntity butler, BlockPos mitte) {
        ServerLevel welt = butler.welt();
        for (BlockPos p : BlockPos.betweenClosed(mitte.offset(-1, -1, -1), mitte.offset(1, 1, 1))) {
            if (rohstoff().block().test(welt.getBlockState(p)) && !ader.contains(p) && !gesperrt.contains(p)
                    && Schutz.darfAbbauen(welt, p, welt.getBlockState(p))) {
                ader.add(p.immutable());
            }
        }
    }

    private void naechstesZiel(ButlerEntity butler) {
        ServerLevel welt = butler.welt();
        abbau.zuruecksetzen(butler);
        tunnel.abbrechen(butler);
        zielTicks = 0;
        while (!ader.isEmpty()) {
            BlockPos p = ader.poll();
            if (rohstoff().block().test(welt.getBlockState(p))) {
                ziel = p;
                phase = butler.inReichweite(p) ? Phase.ABBAUEN : Phase.TUNNEL;
                return;
            }
        }
        // Immer das Vorkommen, das von seinem jetzigen Standort aus am nächsten liegt
        // (die Liste ist nach dem Startpunkt der Suche sortiert, er ist inzwischen weitergewandert)
        BlockPos hier = butler.blockPosition();
        kandidaten.sort(java.util.Comparator.<BlockPos>comparingDouble(p -> p.distSqr(hier)));
        while (!kandidaten.isEmpty()) {
            BlockPos p = kandidaten.remove(0);
            if (!gesperrt.contains(p) && rohstoff().block().test(welt.getBlockState(p))) {
                ziel = p;
                // Freiliegende Vorkommen (Höhle, Oberfläche) zu Fuß erreichen, sonst gleich graben
                phase = freiliegend(welt, p) ? Phase.HIN : Phase.TUNNEL;
                return;
            }
        }
        ziel = null;
        phase = Phase.SUCHEN;
    }

    private static boolean freiliegend(ServerLevel welt, BlockPos p) {
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            BlockPos n = p.relative(d);
            if (welt.getBlockState(n).getCollisionShape(welt, n).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void sperren(ButlerEntity butler) {
        if (ziel != null) {
            gesperrt.add(ziel);
        }
        naechstesZiel(butler);
    }

    @Override
    public void abbrechen(ButlerEntity butler) {
        abbau.zuruecksetzen(butler);
        tunnel.abbrechen(butler);
    }

    @Override
    public Component status() {
        Rohstoffe.Rohstoff r = rohstoff();
        Component name = r == null ? Component.literal(rohstoffId) : r.anzeige().getDefaultInstance().getHoverName();
        String schluessel = phase == Phase.TUNNEL ? "stinkendreich.butler.status.tunnel"
                : phase == Phase.SUCHEN ? "stinkendreich.butler.status.sucht" : "stinkendreich.butler.status.baut";
        return Component.translatable(schluessel, name, gesammelt, menge);
    }

    @Override
    public String typ() {
        return TYP;
    }

    @Override
    public void schreiben(JsonObject j) {
        j.addProperty("rohstoff", rohstoffId);
        j.addProperty("menge", menge);
        j.addProperty("gesammelt", gesammelt);
    }

    static SammelAuftrag ausJson(JsonObject j) {
        SammelAuftrag a = new SammelAuftrag(text(j, "rohstoff", "bruchstein"), zahl(j, "menge", 1));
        a.gesammelt = zahl(j, "gesammelt", 0);
        return a;
    }
}
