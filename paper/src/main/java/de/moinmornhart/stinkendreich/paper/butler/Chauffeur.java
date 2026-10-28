package de.moinmornhart.stinkendreich.paper.butler;

import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * James als Chauffeur auf dem Paper-Server (wie {@code Chauffeur} in der Mod): Er fährt die Stretchlimo ans Ende der
 * Zufahrt, wartet, bis der Herr eingestiegen ist, und fährt ihn die Straße entlang zur anderen Villa. Gefahren wird
 * „auf Schienen“ über die gespeicherte Mittellinie der Straße: Tempo bis gut 50 km/h, vor Kurven und auf Steigungen
 * langsamer, der Wagen schaut immer ein Stück voraus.
 */
final class Chauffeur implements Auftrag {
    private enum Phase { EINSTEIGEN, FAHRT, ANGEKOMMEN }

    /** Höchsttempo und Beschleunigung (Blöcke je Tick). */
    private static final double TEMPO = 0.75, GAS = 0.012, BREMSE = 0.03;

    private final StinkendReichPaper plugin;
    private final Mob koerper;
    private final Mannequin puppe;
    private final UUID herr;
    private final ArmorStand wagen;
    /** Mittellinie in Fahrtrichtung (x, y = Fahrbahnblock, z). */
    private final List<int[]> weg;
    private final String zielName;
    private Phase phase = Phase.EINSTEIGEN;
    /** Gefahrene Strecke entlang der Mittellinie (Index + Anteil). */
    private double s;
    private double tempo;
    private int warten;
    private float gier;

    Chauffeur(StinkendReichPaper plugin, Mob koerper, Mannequin puppe, Player herr, ArmorStand wagen, List<int[]> weg, String zielName) {
        this.plugin = plugin;
        this.koerper = koerper;
        this.puppe = puppe;
        this.herr = herr.getUniqueId();
        this.wagen = wagen;
        this.weg = weg;
        this.zielName = zielName;
        // Vorfahren: an den Anfang der Straße, in Fahrtrichtung
        gier = richtung(0);
        plugin.fahrzeuge().vorfahren(wagen, ort(0), true);
        plugin.fahrzeuge().pilotSetzen(wagen, puppe);
        if (plugin.fahrzeuge().sitztDrin(wagen, herr)) {
            phase = Phase.FAHRT;
        }
        log("Start, " + weg.size() + " Punkte bis " + zielName);
    }

    private void log(String text) {
        Location l = wagen.getLocation();
        plugin.getLogger().info(String.format(Locale.ROOT, "Chauffeur: %s (%s, Wagen bei %d %d %d)", text, phase,
                l.getBlockX(), l.getBlockY(), l.getBlockZ()));
    }

    /** Wo der Wagen steht (oben auf der Fahrbahn) bei Strecke {@code t}. */
    private Location ort(double t) {
        int i = (int) Math.floor(t);
        int[] a = weg.get(Math.min(i, weg.size() - 1)), b = weg.get(Math.min(i + 1, weg.size() - 1));
        double f = t - i;
        World w = wagen.getWorld();
        Location l = new Location(w, a[0] + 0.5 + (b[0] - a[0]) * f, a[1] + 1 + (b[1] - a[1]) * f, a[2] + 0.5 + (b[2] - a[2]) * f);
        l.setYaw(gier);
        return l;
    }

    /** Blickrichtung an der Stelle {@code i}: zum Punkt sechs weiter (glättet die Treppenstufen der Linie). */
    private float richtung(int i) {
        int[] a = weg.get(Math.min(i, weg.size() - 1)), b = weg.get(Math.min(i + 6, weg.size() - 1));
        if (a[0] == b[0] && a[2] == b[2]) {
            return gier;
        }
        return (float) Math.toDegrees(Math.atan2(-(b[0] - a[0]), b[2] - a[2]));
    }

    @Override
    public String status() {
        return switch (phase) {
            case EINSTEIGEN -> "wartet mit der Limo am Ende der Zufahrt";
            case FAHRT -> "fährt Sie " + zielName;
            case ANGEKOMMEN -> "ist angekommen";
        };
    }

    /**
     * Testhilfe für Trailer-Aufnahmen: Mit {@code STINKENDREICH_CHAUFFEUR_ALLEIN} (nur im Spieltest) fährt James auch ohne
     * Fahrgast los, und eine unsichtbare Kamera (Rüstungsständer mit Marke {@code stinkendreich_kamera}) fährt schräg
     * hinter der Limo her; an sie hängt sich der Zuschauer (aus der Limo heraus sieht man nur Dach und Säulen).
     */
    private static final boolean ALLEIN = de.moinmornhart.stinkendreich.paper.Autotest.aktiv()
            && System.getenv("STINKENDREICH_CHAUFFEUR_ALLEIN") != null;
    /** Kamera im Testmodus: Abstand hinter dem Wagen, Höhe darüber, Neigung nach unten, Nachziehen je Tick. */
    private static final double KAMERA_ABSTAND = 11, KAMERA_HOEHE = 5.5, KAMERA_FOLGEN = 0.12;
    private static final float KAMERA_NEIGUNG = 16.0F;
    private ArmorStand kamera;

    @Override
    public Stand tick() {
        Player p = Bukkit.getPlayer(herr);
        if (!wagen.isValid() || p == null) {
            abbrechen();
            return Stand.FERTIG;
        }
        koerper.teleport(puppe.getLocation());
        if (ALLEIN) {
            kameraNachfuehren();
        }
        switch (phase) {
            case EINSTEIGEN -> {
                // Testmodus: drei Sekunden stehen bleiben, damit sich die Kamera an James hängen kann
                if (ALLEIN ? warten >= 60 : plugin.fahrzeuge().sitztDrin(wagen, p)) {
                    Butler.sagen(p, "Bitte anschnallen. Wir fahren " + zielName + ".");
                    phase = Phase.FAHRT;
                    log("losgefahren");
                } else if (++warten > 20 * 120) {
                    Butler.sagen(p, "Ich warte nicht länger – die Limo steht am Ende der Zufahrt bereit.");
                    abbrechen();
                    return Stand.FERTIG;
                } else if (warten == 1) {
                    Butler.sagen(p, "Die Limo steht am Ende der Zufahrt bereit. Bitte steigen Sie hinten ein (Rechtsklick).");
                }
            }
            case FAHRT -> {
                if (!ALLEIN && !plugin.fahrzeuge().sitztDrin(wagen, p)) {
                    Butler.sagen(p, "Sie sind ausgestiegen – ich halte hier.");
                    abbrechen();
                    return Stand.FERTIG;
                }
                fahren();
                if (s >= weg.size() - 1.001) {
                    Butler.sagen(p, "Wir sind da. Ich wünsche einen angenehmen Besuch.");
                    phase = Phase.ANGEKOMMEN;
                    warten = 0;
                    log("angekommen");
                } else if ((int) s % 100 == 0 && (int) (s - tempo) % 100 != 0) {
                    log("unterwegs");
                }
            }
            case ANGEKOMMEN -> {
                if (++warten > 30) {
                    abbrechen();
                    return Stand.FERTIG;
                }
            }
        }
        return Stand.LAEUFT;
    }

    /** Ein Tick Fahrt: Tempo nach Kurve und Steigung voraus, Wagen weiter entlang der Linie. */
    private void fahren() {
        int i = (int) s;
        // Wie stark knickt die Straße in den nächsten 12 Punkten ab, wie steil wird es?
        float knick = Math.abs(wrap(richtung(Math.min(i + 12, weg.size() - 1)) - richtung(i)));
        int dy = Math.abs(weg.get(Math.min(i + 8, weg.size() - 1))[1] - weg.get(i)[1]);
        double rest = weg.size() - 1 - s;
        double soll = TEMPO * (1.0 - Math.min(0.6, knick / 90.0)) * (dy >= 3 ? 0.7 : 1.0);
        soll = Math.min(soll, 0.08 + rest * 0.04);        // vor dem Ziel ausrollen
        tempo += Math.clamp(soll - tempo, -BREMSE, GAS);
        s = Math.min(weg.size() - 1, s + Math.max(0.02, tempo));
        // Blick sanft in Fahrtrichtung nachführen
        gier += Math.clamp(wrap(richtung((int) s) - gier), -6.0F, 6.0F);
        plugin.fahrzeuge().vorfahren(wagen, ort(s), true);
    }

    private static float wrap(float w) {
        w %= 360.0F;
        if (w > 180.0F) {
            w -= 360.0F;
        } else if (w < -180.0F) {
            w += 360.0F;
        }
        return w;
    }

    /** Testmodus: Kamera weich hinter den Wagen ziehen (nie in einen Hang hinein), Blick in Fahrtrichtung. */
    private void kameraNachfuehren() {
        Location w = wagen.getLocation();
        double rad = Math.toRadians(gier);
        Location ziel = w.clone().add(Math.sin(rad) * KAMERA_ABSTAND, KAMERA_HOEHE, -Math.cos(rad) * KAMERA_ABSTAND);
        for (int i = 0; i < 8 && !ziel.getBlock().isPassable(); i++) {
            ziel.add(0, 1, 0);
        }
        if (kamera == null || !kamera.isValid()) {
            ziel.setYaw(gier);
            ziel.setPitch(KAMERA_NEIGUNG);
            kamera = w.getWorld().spawn(ziel, ArmorStand.class, a -> {
                a.setInvisible(true);
                a.setMarker(true);
                a.setGravity(false);
                a.setPersistent(false);
                a.addScoreboardTag("stinkendreich_kamera");
            });
            return;
        }
        Location k = kamera.getLocation();
        Location neu = k.clone().add(ziel.toVector().subtract(k.toVector()).multiply(KAMERA_FOLGEN));
        neu.setYaw(k.getYaw() + wrap(gier - k.getYaw()) * (float) KAMERA_FOLGEN);
        neu.setPitch(KAMERA_NEIGUNG);
        kamera.teleport(neu);
    }

    @Override
    public void abbrechen() {
        if (kamera != null) {
            kamera.remove();
        }
        plugin.fahrzeuge().vorfahren(wagen, wagen.getLocation(), false);   // Schwerkraft wieder an
        if (puppe.isInsideVehicle()) {
            puppe.leaveVehicle();
        }
    }
}
