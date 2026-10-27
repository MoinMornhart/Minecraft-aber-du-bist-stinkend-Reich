package de.moinmornhart.stinkendreich.paper.butler;

import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Locale;
import java.util.UUID;

/**
 * James als Pilot auf dem Paper-Server (wie {@code HeliBereitstellung} und {@code FlugAuftrag} in der Mod): Er setzt
 * sich ans Steuer des Hubschraubers der Villa, holt den Herrn ab (landet neben ihm), wartet, bis er eingestiegen ist,
 * und fliegt ihn zum Ziel – nach Hause, ins nächste Dorf oder zur Villa eines anderen Spielers. Straßen braucht er
 * dafür nicht: Er steigt über alles hinweg (Reiseflughöhe 18 Blöcke über dem höchsten Punkt unter und vor ihm) und
 * landet am Ziel senkrecht. Geschwindigkeiten sind Blöcke je Tick (der Autopilot versetzt den Hubschrauber Schritt
 * für Schritt, siehe {@code Fahrzeuge.steuern}).
 */
final class Flug implements Auftrag {
    private enum Phase { ABHOLEN, EINSTEIGEN, REISE, ANGEKOMMEN }

    /** Tempo im Reiseflug (Blöcke je Tick, gut 60 km/h) und Steig- bzw. Sinkrate. */
    private static final double TEMPO = 0.9, STEIGEN = 0.35, SINKEN = 0.3;

    private final StinkendReichPaper plugin;
    private final Mob koerper;
    private final Mannequin puppe;
    private final UUID herr;
    private final ArmorStand heli;
    private final Location ziel;
    private final String zielName;
    private Phase phase;
    private Location landeplatz;
    private double tempo;
    private int warten, alter;

    Flug(StinkendReichPaper plugin, Mob koerper, Mannequin puppe, Player herr, ArmorStand heli, Location ziel, String zielName) {
        this.plugin = plugin;
        this.koerper = koerper;
        this.puppe = puppe;
        this.herr = herr.getUniqueId();
        this.heli = heli;
        this.ziel = ziel.clone();
        this.zielName = zielName;
        plugin.fahrzeuge().pilotSetzen(heli, puppe);
        if (plugin.fahrzeuge().sitztDrin(heli, herr)) {
            phase = Phase.REISE;
        } else {
            phase = Phase.ABHOLEN;
            landeplatz = landeplatzBei(herr.getLocation());
            // Steht der Hubschrauber weit weg (Chunks dort laufen nicht), kommt er aus der Nähe angeflogen
            Location h = heli.getLocation();
            if (!h.getWorld().equals(landeplatz.getWorld()) || h.distanceSquared(landeplatz) > 96 * 96) {
                Vector weg = h.getWorld().equals(landeplatz.getWorld())
                        ? h.toVector().subtract(landeplatz.toVector()).setY(0) : new Vector(1, 0, 0);
                Location start = landeplatz.clone().add(weg.normalize().multiply(64));
                start.setY(Math.max(landeplatz.getY(), hoechsterPunkt(start)) + 18);
                start.setYaw(h.getYaw());
                heli.teleport(start, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
            }
        }
        log("Start, Ziel " + zielName + " bei " + ziel.getBlockX() + " " + ziel.getBlockY() + " " + ziel.getBlockZ());
    }

    private void log(String text) {
        Location h = heli.getLocation();
        plugin.getLogger().info(String.format(Locale.ROOT, "Flug: %s (%s, Hubschrauber bei %d %d %d)", text, phase,
                h.getBlockX(), h.getBlockY(), h.getBlockZ()));
    }

    private void phase(Phase neu) {
        phase = neu;
        tempo = 0;
        log("Phase " + neu);
    }

    @Override
    public String status() {
        return switch (phase) {
            case ABHOLEN -> "fliegt mit dem Hubschrauber zu Ihnen";
            case EINSTEIGEN -> "wartet im Hubschrauber auf Sie";
            case REISE -> "fliegt Sie " + zielName;
            case ANGEKOMMEN -> "ist gelandet";
        };
    }

    @Override
    public Stand tick() {
        Player p = Bukkit.getPlayer(herr);
        if (!heli.isValid() || p == null) {
            abbrechen();
            return Stand.FERTIG;
        }
        // Der unsichtbare Körper reist mit (sonst bleibt er zurück und James springt aus dem Sitz zu ihm)
        koerper.teleport(puppe.getLocation());
        if (++alter % 100 == 0) {
            log("unterwegs");
        }
        switch (phase) {
            case ABHOLEN -> {
                if (fliegen(landeplatz)) {
                    Butler.sagen(p, "Ihr Hubschrauber ist da. Bitte steigen Sie ein (Rechtsklick).");
                    phase(Phase.EINSTEIGEN);
                }
            }
            case EINSTEIGEN -> {
                plugin.fahrzeuge().steuern(heli, new Vector(), heli.getYaw());
                if (plugin.fahrzeuge().sitztDrin(heli, p)) {
                    Butler.sagen(p, "Willkommen an Bord. Wir fliegen " + zielName + ".");
                    phase(Phase.REISE);
                } else if (++warten > 20 * 90) {
                    Butler.sagen(p, "Ich warte nicht länger – der Hubschrauber steht für Sie bereit.");
                    abbrechen();
                    return Stand.FERTIG;
                }
            }
            case REISE -> {
                if (fliegen(ziel)) {
                    Butler.sagen(p, "Wir sind da. Ich wünsche einen angenehmen Aufenthalt.");
                    phase(Phase.ANGEKOMMEN);
                }
            }
            case ANGEKOMMEN -> {
                // Kurz stehen bleiben, dann steigt James aus und kommt mit
                plugin.fahrzeuge().steuern(heli, new Vector(), heli.getYaw());
                if (++warten > 30) {
                    abbrechen();
                    return Stand.FERTIG;
                }
            }
        }
        return Stand.LAEUFT;
    }

    /** Ein Tick Flug zur Stelle: steigen, hinfliegen, senkrecht landen. True, wenn gelandet. */
    private boolean fliegen(Location stelle) {
        Location h = heli.getLocation();
        double dx = stelle.getX() - h.getX(), dz = stelle.getZ() - h.getZ();
        double weit = Math.hypot(dx, dz);
        float gier = h.getYaw();
        Vector v;
        if (weit > 1.2) {
            // Reiseflughöhe über dem höchsten Punkt unter und vor ihm; erst steigen, dann Fahrt aufnehmen
            Location vorn = h.clone().add(dx / weit * 12, 0, dz / weit * 12);
            double reise = Math.max(Math.max(hoechsterPunkt(h), hoechsterPunkt(vorn)), stelle.getY()) + 18;
            double hoch = Math.clamp((reise - h.getY()) * 0.1, -SINKEN, STEIGEN);
            float soll = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float dreh = wrap(soll - gier);
            gier += Math.clamp(dreh, -4.0F, 4.0F);
            double wunsch = h.getY() < reise - 8 || Math.abs(dreh) > 45 ? 0.08 : Math.min(TEMPO, 0.12 + weit * 0.05);
            tempo += Math.clamp(wunsch - tempo, -0.04, 0.025);
            double r = Math.toRadians(gier);
            v = new Vector(-Math.sin(r) * tempo, hoch, Math.cos(r) * tempo);
        } else {
            // Über dem Ziel: senkrecht runter bis auf den Boden der Landestelle
            double rest = h.getY() - stelle.getY();
            if (rest < 0.06) {
                plugin.fahrzeuge().steuern(heli, new Vector(dx, -rest, dz), gier);
                return true;
            }
            v = new Vector(dx * 0.2, -Math.min(rest, Math.clamp(rest * 0.08, 0.06, SINKEN)), dz * 0.2);
        }
        plugin.fahrzeuge().steuern(heli, v, gier);
        return false;
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

    /** Höchster fester Punkt (mit Laub) an dieser Stelle; ungeladene Chunks zählen als Meereshöhe + 40. */
    private static double hoechsterPunkt(Location l) {
        World w = l.getWorld();
        if (!w.isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) {
            return w.getSeaLevel() + 40;
        }
        return w.getHighestBlockYAt(l.getBlockX(), l.getBlockZ()) + 1;
    }

    /**
     * Freier, ebener Landeplatz 6–10 Blöcke neben dem Herrn: Boden aus Gras, Erde, Sand oder Stein (kein Laub, kein
     * Wasser), 3 × 3 höchstens einen Block uneben. Sonst direkt neben ihm.
     */
    static Location landeplatzBei(Location bei) {
        World w = bei.getWorld();
        for (int r = 6; r <= 10; r += 2) {
            for (int i = 0; i < 12; i++) {
                double winkel = i * Math.PI / 6;
                int x = bei.getBlockX() + (int) Math.round(Math.cos(winkel) * r);
                int z = bei.getBlockZ() + (int) Math.round(Math.sin(winkel) * r);
                if (!w.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                Block boden = w.getHighestBlockAt(x, z);
                if (!geeignet(boden.getType())) {
                    continue;
                }
                boolean eben = true;
                for (int ax = -1; ax <= 1 && eben; ax++) {
                    for (int az = -1; az <= 1 && eben; az++) {
                        eben = Math.abs(w.getHighestBlockYAt(x + ax, z + az) - boden.getY()) <= 1;
                    }
                }
                if (eben && Math.abs(boden.getY() - bei.getY()) < 6) {
                    return new Location(w, x + 0.5, boden.getY() + 1, z + 0.5);
                }
            }
        }
        Location l = bei.clone().add(4, 0, 0);
        l.setY(w.getHighestBlockYAt(l) + 1);
        return l;
    }

    private static boolean geeignet(Material m) {
        return !Tag.LEAVES.isTagged(m) && m != Material.WATER && m != Material.LAVA && m.isSolid();
    }

    @Override
    public void abbrechen() {
        plugin.fahrzeuge().steuern(heli, new Vector(), heli.getYaw());
        if (puppe.isInsideVehicle()) {
            puppe.leaveVehicle();
        }
        Player p = Bukkit.getPlayer(herr);
        if (p != null && phase == Phase.ANGEKOMMEN) {
            p.sendActionBar(Component.text("Schleichen: aussteigen"));
        }
    }
}
