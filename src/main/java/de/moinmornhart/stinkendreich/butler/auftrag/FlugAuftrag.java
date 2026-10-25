package de.moinmornhart.stinkendreich.butler.auftrag;

import com.google.gson.JsonObject;
import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.auto.HubschrauberEntity;
import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import de.moinmornhart.stinkendreich.butler.Pilot;
import de.moinmornhart.stinkendreich.register.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * Der Butler als Pilot: setzt sich in den Hubschrauber, fliegt (falls nötig) zum Herrn und landet neben
 * ihm, wartet, bis der Herr eingestiegen ist, und fliegt ihn zum Ziel (gespeicherter Ort, Landeplatz der
 * Villa oder die Stelle, auf die der Herr geschaut hat). Ohne Ziel („hol den Hubschrauber“) holt er nur
 * den Hubschrauber und wartet am Steuer.
 */
public class FlugAuftrag extends Auftrag {
    public static final String TYP = "fliegen";
    /** So weit darf der Hubschrauber weg sein (geladen muss er auch sein). */
    private static final double SUCHE = 512.0;

    private enum Phase { WAHL, EINSTEIGEN, ANFLUG, WARTEN, FLUG }

    private final Vec3 ziel;
    private final String zielName;
    private Phase phase = Phase.WAHL;
    private HubschrauberEntity heli;
    private Pilot pilot;
    private int warten;

    public FlugAuftrag(Vec3 ziel, String zielName) {
        this.ziel = ziel;
        this.zielName = zielName;
    }

    @Override
    protected Stand tick(ButlerEntity butler) {
        ServerPlayer herr = butler.besitzerOnline();
        if (herr == null || herr.level() != butler.level()) {
            return Stand.FERTIG;
        }
        if (heli != null && heli.isRemoved()) {
            heli = null;
            phase = Phase.WAHL;
        }
        switch (phase) {
            case WAHL -> {
                heli = finden(butler, herr);
                if (heli == null) {
                    return fehlschlag(Component.translatable("stinkendreich.butler.flug.keiner"));
                }
                phase = Phase.EINSTEIGEN;
            }
            case EINSTEIGEN -> {
                if (butler.getVehicle() != heli) {
                    butler.stopRiding();
                    if (butler.distanceTo(heli) > 4) {
                        butler.teleportiereNahe(heli.blockPosition());
                    }
                    Kompat.einsteigen(butler, heli);
                    heli.level().playSound(null, heli.blockPosition(), ModSounds.ANLASSER, SoundSource.NEUTRAL, 1.0F, 0.6F);
                }
                if (sitztDrin(herr) || heli.distanceTo(herr) < 14) {
                    phase = Phase.WARTEN;
                    warten = 0;
                    if (!sitztDrin(herr)) {
                        butler.sagen(Component.translatable("stinkendreich.butler.flug.bereit"));
                    }
                } else {
                    // Zum Herrn fliegen und neben ihm landen
                    pilot = new Pilot(Pilot.landestelle(herr.level(), herr.blockPosition(), 7, 20));
                    phase = Phase.ANFLUG;
                    butler.sagen(Component.translatable("stinkendreich.butler.flug.anflug"));
                }
            }
            case ANFLUG -> {
                if (butler.getVehicle() != heli) {
                    phase = Phase.EINSTEIGEN;
                    return Stand.LAEUFT;
                }
                if (sitztDrin(herr) || pilot.fliegen(heli) == Pilot.Lage.GELANDET) {
                    phase = Phase.WARTEN;
                    warten = 0;
                    if (!sitztDrin(herr)) {
                        butler.sagen(Component.translatable("stinkendreich.butler.flug.bereit"));
                    }
                }
            }
            case WARTEN -> {
                if (butler.getVehicle() != heli) {
                    phase = Phase.EINSTEIGEN;
                    return Stand.LAEUFT;
                }
                heli.chauffeurEingabe(0, 0, heli.onGround() ? 0 : -1);
                if (sitztDrin(herr)) {
                    if (ziel == null) {
                        butler.sagen(Component.translatable("stinkendreich.butler.fahren.wohin"));
                        return Stand.FERTIG;
                    }
                    pilot = new Pilot(ziel);
                    phase = Phase.FLUG;
                } else if (++warten > 20 * 120) {
                    butler.sagen(Component.translatable("stinkendreich.butler.fahren.ausgestiegen"));
                    return Stand.FERTIG;
                }
            }
            case FLUG -> {
                if (butler.getVehicle() != heli) {
                    return Stand.FERTIG;
                }
                if (!sitztDrin(herr)) {
                    // Der Herr ist ausgestiegen (oder gesprungen): landen und warten
                    pilot = new Pilot(Pilot.landestelle(heli.level(), heli.blockPosition(), 0, 12));
                    phase = Phase.ANFLUG;
                    return Stand.LAEUFT;
                }
                if (pilot.fliegen(heli) == Pilot.Lage.GELANDET) {
                    butler.sagen(Component.translatable("stinkendreich.butler.flug.da"));
                    return Stand.FERTIG;
                }
            }
        }
        return Stand.LAEUFT;
    }

    private boolean sitztDrin(ServerPlayer herr) {
        return heli != null && herr.getVehicle() == heli;
    }

    /** Hubschrauber des Herrn oder der nächste (geladene) ohne fremde Insassen. */
    private static HubschrauberEntity finden(ButlerEntity butler, ServerPlayer herr) {
        if (herr.getVehicle() instanceof HubschrauberEntity eigener) {
            return eigener;
        }
        List<HubschrauberEntity> alle = butler.level().getEntitiesOfClass(HubschrauberEntity.class, herr.getBoundingBox().inflate(SUCHE),
                h -> h.getPassengers().stream().allMatch(e -> e == butler));
        alle.sort(Comparator.comparingDouble(h -> h.distanceToSqr(herr)));
        return alle.isEmpty() ? null : alle.get(0);
    }

    @Override
    public void abbrechen(ButlerEntity butler) {
        if (heli != null) {
            heli.chauffeurEingabe(0, 0, 0);
        }
    }

    @Override
    public Component status() {
        if (phase == Phase.FLUG && heli != null && pilot != null) {
            Vec3 p = heli.position();
            int rest = (int) Math.sqrt(Math.pow(ziel.x - p.x, 2) + Math.pow(ziel.z - p.z, 2));
            return Component.translatable("stinkendreich.butler.status.fliegt", zielName, rest);
        }
        if (phase == Phase.ANFLUG) {
            return Component.translatable("stinkendreich.butler.status.fliegtzu");
        }
        return Component.translatable(phase == Phase.WARTEN ? "stinkendreich.butler.status.wartetimwagen" : "stinkendreich.butler.status.holtwagen");
    }

    @Override
    public String typ() {
        return TYP;
    }

    @Override
    public void schreiben(JsonObject j) {
        if (ziel != null) {
            j.addProperty("x", ziel.x);
            j.addProperty("y", ziel.y);
            j.addProperty("z", ziel.z);
        }
        j.addProperty("name", zielName);
    }

    static FlugAuftrag ausJson(JsonObject j) {
        Vec3 z = j.has("x") ? new Vec3(j.get("x").getAsDouble(), j.get("y").getAsDouble(), j.get("z").getAsDouble()) : null;
        return new FlugAuftrag(z, text(j, "name", ""));
    }
}
