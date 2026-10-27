package de.moinmornhart.stinkendreich.paper.butler;

import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import de.moinmornhart.stinkendreich.paper.villa.Villen;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Die Show-Tänzerinnen im Geheimclub auf dem Paper-Server (wie {@code TaenzerinEntity} in der Mod). Sie sind
 * Schaufensterpuppen mit dem Kostüm als Haut; das Plugin bewegt sie jeden Tick selbst:
 * <ul>
 *   <li>{@code stange}: kreist langsam um die Tanzstange (rechte Hand zur Stange, eine Runde in 15 Sekunden) und geht
 *   dabei leicht auf und ab,</li>
 *   <li>{@code laufen}: schlendert durch den Club (höchstens 5 Blöcke vom Startpunkt) und tanzt, wenn sie steht
 *   (wiegt sich, bewegt die Arme, wippt im Takt).</li>
 * </ul>
 * Art, Kostüm und Anker stehen im PDC ({@code taenzerin} = „Art|Kostüm“, {@code anker} = „x|y|z“). Bewegt wird nur,
 * wenn ein Spieler in der Nähe ist und die Villa nicht gerade versinkt (Inkognito).
 */
public final class Taenzerinnen implements Listener {
    private static final double STANGE_RADIUS = 0.45, LAUF_RADIUS = 5.0, TEMPO = 0.1, SICHTWEITE = 48.0;
    /** Drehung um die Stange pro Tick in Grad. */
    private static final double STANGE_TEMPO = 1.2;

    private final StinkendReichPaper plugin;
    private final NamespacedKey schluesselArt, schluesselAnker;
    private final Map<Mannequin, Tanz> taenzerinnen = new HashMap<>();

    /** Was eine Tänzerin gerade tut. */
    private static final class Tanz {
        boolean stange;
        double ax, ay, az, winkel;
        Location ziel;
        int warten, takt;
        float blick;
    }

    public Taenzerinnen(StinkendReichPaper plugin) {
        this.plugin = plugin;
        this.schluesselArt = new NamespacedKey(plugin, "taenzerin");
        this.schluesselAnker = new NamespacedKey(plugin, "anker");
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::takt, 1L, 1L);
    }

    /** Neue Tänzerin aus dem Bauplan („stange|2“ oder „laufen|0“): Daten merken und mitbewegen. */
    public void einrichten(Mannequin m, String art) {
        Location l = m.getLocation();
        m.getPersistentDataContainer().set(schluesselArt, PersistentDataType.STRING, art);
        m.getPersistentDataContainer().set(schluesselAnker, PersistentDataType.STRING, l.getX() + "|" + l.getY() + "|" + l.getZ());
        m.setGravity(false);
        aufnehmen(m);
    }

    @EventHandler
    public void beimLaden(EntitiesLoadEvent e) {
        for (Entity n : e.getEntities()) {
            if (n instanceof Mannequin m) {
                aufnehmen(m);
            }
        }
    }

    private void aufnehmen(Mannequin m) {
        String art = m.getPersistentDataContainer().get(schluesselArt, PersistentDataType.STRING);
        String anker = m.getPersistentDataContainer().get(schluesselAnker, PersistentDataType.STRING);
        if (art == null || anker == null) {
            return;
        }
        String[] a = anker.split("\\|");
        Tanz t = new Tanz();
        t.stange = !art.startsWith("laufen");
        t.ax = Double.parseDouble(a[0]);
        t.ay = Double.parseDouble(a[1]);
        t.az = Double.parseDouble(a[2]);
        t.blick = m.getLocation().getYaw();
        t.warten = ThreadLocalRandom.current().nextInt(60);
        taenzerinnen.put(m, t);
    }

    private void takt() {
        taenzerinnen.keySet().removeIf(m -> !m.isValid());
        for (Map.Entry<Mannequin, Tanz> e : taenzerinnen.entrySet()) {
            Mannequin m = e.getKey();
            Tanz t = e.getValue();
            Location l = m.getLocation();
            if (!zuschauer(l)) {
                continue;
            }
            Villen.Villa v = plugin.villen().bei(l, 0);
            if (v != null && plugin.inkognito().aktiv(v)) {
                continue;                                   // die Villa fährt gerade: nicht dazwischenfunken
            }
            // Nach einer Inkognito-Fahrt steht die ganze Villa um ganze Blöcke höher oder tiefer: Anker mitnehmen
            if (Math.abs(l.getY() - t.ay) > 2.5) {
                t.ay += Math.round(l.getY() - t.ay);
                m.getPersistentDataContainer().set(schluesselAnker, PersistentDataType.STRING, t.ax + "|" + t.ay + "|" + t.az);
                t.ziel = null;
            }
            t.takt++;
            if (t.stange) {
                anDerStange(m, t);
            } else {
                schlendern(m, t);
            }
        }
    }

    /** Ist ein Spieler nah genug, dass sich das Bewegen lohnt? */
    private static boolean zuschauer(Location l) {
        for (Player p : l.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(l) < SICHTWEITE * SICHTWEITE) {
                return true;
            }
        }
        return false;
    }

    /** Kreist um die Stange; Blick in Laufrichtung, dann liegt die Mitte rechts von ihr. */
    private static void anDerStange(Mannequin m, Tanz t) {
        t.winkel = (t.winkel + STANGE_TEMPO) % 360.0;
        double bogen = Math.toRadians(t.winkel);
        // Leichtes Auf und Ab, als würde sie an der Stange hochgehen und wieder herunter
        double hoehe = 0.12 * (1.0 - Math.cos(bogen * 2.0));
        setzen(m, new Location(m.getWorld(), t.ax + Math.cos(bogen) * STANGE_RADIUS, t.ay + hoehe,
                t.az + Math.sin(bogen) * STANGE_RADIUS), (float) t.winkel);
        if (t.takt % 30 == 0) {
            m.swingOffHand();                               // freie Hand schwingt im Takt
        }
    }

    /** Läuft zu einem Ziel in der Nähe des Ankers; dazwischen bleibt sie stehen und tanzt. */
    private void schlendern(Mannequin m, Tanz t) {
        Location l = m.getLocation();
        if (t.ziel == null) {
            if (--t.warten <= 0) {
                t.ziel = neuesZiel(m, t);
                t.warten = 60 + ThreadLocalRandom.current().nextInt(140);
                m.setPose(Pose.STANDING);
            } else {
                frei(m, t, l);
            }
            return;
        }
        double dx = t.ziel.getX() - l.getX(), dz = t.ziel.getZ() - l.getZ();
        double weg = Math.sqrt(dx * dx + dz * dz);
        if (weg < 0.15) {
            t.ziel = null;
            return;
        }
        double s = Math.min(TEMPO, weg) / weg;
        Location naechster = new Location(l.getWorld(), l.getX() + dx * s, t.ay, l.getZ() + dz * s);
        if (!frei(naechster.getBlock())) {
            t.ziel = null;                                  // etwas im Weg: stehen bleiben und tanzen
            return;
        }
        t.blick = (float) Math.toDegrees(Math.atan2(-dx, dz));
        setzen(m, naechster, t.blick);
    }

    /** Tanz im Stehen: wiegt sich, schaut zum nächsten Spieler, Arme abwechselnd, wippt alle zwei Sekunden. */
    private static void frei(Mannequin m, Tanz t, Location l) {
        Player naechster = null;
        double best = 64.0;
        for (Player p : l.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(l);
            if (d < best) {
                best = d;
                naechster = p;
            }
        }
        float basis = t.blick;
        if (naechster != null) {
            double dx = naechster.getX() - l.getX(), dz = naechster.getZ() - l.getZ();
            basis = (float) Math.toDegrees(Math.atan2(-dx, dz));
        }
        float wiegen = (float) (25.0 * Math.sin(t.takt * 0.12));
        setzen(m, new Location(l.getWorld(), l.getX(), t.ay, l.getZ()), basis + wiegen);
        if (t.takt % 12 == 0) {
            if (t.takt % 24 == 0) {
                m.swingMainHand();
            } else {
                m.swingOffHand();
            }
        }
        m.setPose(t.takt % 40 < 6 ? Pose.SNEAKING : Pose.STANDING);
    }

    /** Ein Platz im Umkreis des Ankers, an dem Füße und Kopf frei sind und Boden darunter ist. */
    private static Location neuesZiel(Mannequin m, Tanz t) {
        ThreadLocalRandom zufall = ThreadLocalRandom.current();
        for (int versuch = 0; versuch < 8; versuch++) {
            Location ziel = new Location(m.getWorld(), t.ax + (zufall.nextDouble() * 2 - 1) * LAUF_RADIUS, t.ay,
                    t.az + (zufall.nextDouble() * 2 - 1) * LAUF_RADIUS);
            if (frei(ziel.getBlock())) {
                return ziel;
            }
        }
        return null;
    }

    private static boolean frei(Block fuesse) {
        return fuesse.isPassable() && fuesse.getRelative(0, 1, 0).isPassable() && !fuesse.getRelative(0, -1, 0).isPassable();
    }

    private static void setzen(Mannequin m, Location ort, float blick) {
        ort.setYaw(blick);
        ort.setPitch(0.0F);
        m.teleport(ort);
        m.setBodyYaw(blick);
    }
}
