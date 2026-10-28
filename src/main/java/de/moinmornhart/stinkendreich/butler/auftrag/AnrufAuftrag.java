package de.moinmornhart.stinkendreich.butler.auftrag;

import com.google.gson.JsonObject;
import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.auto.AutoEntity;
import de.moinmornhart.stinkendreich.auto.Autotyp;
import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import de.moinmornhart.stinkendreich.butler.Chauffeur;
import de.moinmornhart.stinkendreich.handy.Lieferservice;
import de.moinmornhart.stinkendreich.register.ModEntities;
import de.moinmornhart.stinkendreich.register.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Anruf auf dem Handy: James kommt mit der Stretch-Limo. Nach ein paar Sekunden steht der Wagen in 25 bis 40 Blöcken
 * Entfernung, James fährt ihn vor, hupt und hält neben dem Herrn. Bringt er eine Lieferung mit (Lieferservice),
 * steht sie danach neben dem Wagen. Danach wartet er am Steuer (der nächste Auftrag ist meist eine {@link FahrAuftrag}).
 *
 * <p>Den Wagen nimmt er so: eine freie Limo, die irgendwo geladen ist, sonst eine neue aus dem Fuhrpark.</p>
 */
public class AnrufAuftrag extends Auftrag {
    public static final String TYP = "anruf";
    /** So lange dauert es, bis der Wagen um die Ecke biegt (Ticks). */
    private static final int WARTEZEIT = 80;
    /** Spätestens dann steht er neben dem Herrn (Ticks Fahrt). */
    private static final int HOECHSTENS = 20 * 45;

    private enum Phase { KLINGELN, ANFAHRT, DA }

    /** Was er mitbringt (Lieferservice) oder leer. */
    private final String lieferung;
    private Phase phase = Phase.KLINGELN;
    private AutoEntity auto;
    private Chauffeur chauffeur;
    private int fahrt;

    public AnrufAuftrag(String lieferung) {
        this.lieferung = lieferung == null ? "" : lieferung;
    }

    @Override
    protected Stand tick(ButlerEntity butler) {
        ServerPlayer herr = butler.besitzerOnline();
        if (herr == null || !(herr.level() instanceof ServerLevel welt)) {
            return Stand.FERTIG;
        }
        switch (phase) {
            case KLINGELN -> {
                if (ticks == 1) {
                    // Die Limo aus der Garage holen (ihr Stellplatz wird geladen), statt jedes Mal eine neue zu rufen
                    de.moinmornhart.stinkendreich.auto.Garage.vorladen(welt, herr.getUUID(), Autotyp.STRETCHLIMO);
                    butler.sagen(Component.translatable(lieferung.isEmpty() ? "stinkendreich.handy.unterwegs" : "stinkendreich.handy.lieferung.unterwegs"));
                }
                if (ticks < WARTEZEIT) {
                    return Stand.LAEUFT;
                }
                BlockPos platz = Chauffeur.parkplatz(welt, herr.blockPosition(), 25, 40);
                if (platz == null) {
                    platz = Chauffeur.parkplatz(welt, herr.blockPosition(), 4, 24);
                }
                if (platz == null) {
                    return fehlschlag(Component.translatable("stinkendreich.butler.fahren.keinwagen"));
                }
                auto = wagen(welt, herr);
                if (auto == null) {
                    return fehlschlag(Component.translatable("stinkendreich.butler.fahren.keinwagen"));
                }
                float blick = (float) Math.toDegrees(Math.atan2(-(herr.getX() - (platz.getX() + 0.5)), herr.getZ() - (platz.getZ() + 0.5)));
                if (auto.level() != welt) {
                    return fehlschlag(Component.translatable("stinkendreich.butler.fahren.keinwagen"));
                }
                Kompat.versetzen(auto, platz.getX() + 0.5, platz.getY(), platz.getZ() + 0.5, blick, 0.0F);
                auto.setYBodyRot(blick);
                butler.stopRiding();
                butler.teleportiereNahe(platz);
                Kompat.einsteigen(butler, auto);
                welt.playSound(null, auto.blockPosition(), ModSounds.ANLASSER, SoundSource.NEUTRAL, 1.0F, 1.0F);
                chauffeur = new Chauffeur(List.of(herr.position()), 6.0);
                phase = Phase.ANFAHRT;
            }
            case ANFAHRT -> {
                if (auto == null || auto.isRemoved()) {
                    return Stand.FERTIG;
                }
                fahrt++;
                Chauffeur.Lage lage = butler.getVehicle() == auto ? chauffeur.fahren(auto) : Chauffeur.Lage.FESTGEFAHREN;
                if (lage == Chauffeur.Lage.FESTGEFAHREN || fahrt > HOECHSTENS) {
                    // Notfalls direkt neben den Herrn stellen
                    BlockPos nah = Chauffeur.parkplatz(welt, herr.blockPosition(), 3, 10);
                    if (nah != null) {
                        Kompat.versetzen(auto, nah.getX() + 0.5, nah.getY(), nah.getZ() + 0.5, auto.getYRot(), 0.0F);
                    }
                    lage = Chauffeur.Lage.DA;
                }
                if (lage == Chauffeur.Lage.DA) {
                    auto.chauffeurEingabe(0, 0);
                    phase = Phase.DA;
                }
            }
            case DA -> {
                welt.playSound(null, auto.blockPosition(), ModSounds.HUPE, SoundSource.NEUTRAL, 2.0F, 1.0F);
                if (!lieferung.isEmpty()) {
                    Component was = Lieferservice.liefern(welt, herr, lieferung, auto.position());
                    butler.sagen(Component.translatable("stinkendreich.handy.lieferung.da", was));
                } else {
                    butler.sagen(Component.translatable("stinkendreich.handy.da", auto.getName()));
                }
                return Stand.FERTIG;
            }
        }
        return Stand.LAEUFT;
    }

    /** Freie Limo irgendwo in der Welt (geladen; darin sitzen höchstens James und der Herr), sonst eine neue. */
    private static AutoEntity wagen(ServerLevel welt, ServerPlayer herr) {
        AutoEntity beste = null;
        for (var e : welt.getAllEntities()) {
            if (e instanceof AutoEntity a && a.art == Autotyp.STRETCHLIMO && !a.isRemoved()
                    && a.getPassengers().stream().allMatch(p -> p instanceof ButlerEntity || p == herr)
                    && (beste == null || a.distanceToSqr(herr) < beste.distanceToSqr(herr))) {
                beste = a;
            }
        }
        if (beste != null) {
            return beste;
        }
        AutoEntity neu = ModEntities.auto(Autotyp.STRETCHLIMO).create(welt
                //? if >=1.21.2
                , net.minecraft.world.entity.EntitySpawnReason.EVENT
        );
        if (neu != null) {
            neu.teleportTo(herr.getX(), herr.getY(), herr.getZ());
            welt.addFreshEntity(neu);
        }
        return neu;
    }

    @Override
    public void abbrechen(ButlerEntity butler) {
        if (auto != null) {
            auto.chauffeurEingabe(0, 0);
        }
    }

    @Override
    public Component status() {
        return Component.translatable(phase == Phase.KLINGELN ? "stinkendreich.handy.status.unterwegs" : "stinkendreich.handy.status.faehrtvor");
    }

    @Override
    public String typ() {
        return TYP;
    }

    @Override
    public void schreiben(JsonObject j) {
        j.addProperty("lieferung", lieferung);
    }

    static AnrufAuftrag ausJson(JsonObject j) {
        return new AnrufAuftrag(text(j, "lieferung", ""));
    }
}
