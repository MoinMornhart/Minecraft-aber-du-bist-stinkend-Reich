package de.moinmornhart.stinkendreich.auto;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Drehteller in der Tiefgarage (7 Blöcke Durchmesser, liegt flach im Boden). Steht ein Auto eine Sekunde still
 * darauf, dreht er es in drei Sekunden um 180 Grad – mit dem Fahrer, so dass man vorwärts wieder hinausfährt.
 * Ein Wagen ohne Fahrer dreht sich danach langsam weiter wie im Autohaus.
 *
 * <p>Gedreht wird auf beiden Seiten gleich: der Server dreht Wagen ohne Fahrer, der Klient des Fahrers
 * dreht seinen eigenen Wagen (dessen Bewegung bestimmt sein Rechner).</p>
 */
public class DrehtellerEntity extends Mob {
    /** Grad pro Tick: 0 = steht, 3 = Wenden, 0,5 = Ausstellung. */
    private static final EntityDataAccessor<Float> TEMPO = SynchedEntityData.defineId(DrehtellerEntity.class, EntityDataSerializers.FLOAT);
    public static final double RADIUS = 3.4;
    private float rest;
    private int stillstand;
    /** Schon einmal gewendet, seit dieses Auto draufgefahren ist (dann nicht noch einmal). */
    private boolean gewendet;
    private final Map<Integer, Vec3> letztePos = new HashMap<>();

    public DrehtellerEntity(EntityType<? extends DrehtellerEntity> typ, Level welt) {
        super(typ, welt);
        setPersistenceRequired();
        setNoAi(true);
        setNoGravity(true);
        // Die Trefferbox ist klein, gezeichnet wird aber immer die ganze Scheibe (ab 1.21.2 im Renderer)
        //? if <1.21.2
        /*noCulling = true;*/
    }

    public static AttributeSupplier.Builder attribute() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 50.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder daten) {
        super.defineSynchedData(daten);
        daten.define(TEMPO, 0.0F);
    }

    @Override
    public boolean removeWhenFarAway(double abstand) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    //? if >=1.21.2 {
    @Override
    public boolean isInvulnerableTo(ServerLevel welt, DamageSource quelle) {
        return !quelle.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(welt, quelle);
    }
    //?} else {
    /*@Override
    public boolean isInvulnerableTo(DamageSource quelle) {
        return !quelle.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(quelle);
    }
    *///?}

    /** Schiebt nichts weg (sonst würde er Spieler auf der Fläche zur Seite drücken). */
    @Override
    protected void pushEntities() {
    }

    private AABB flaeche() {
        return new AABB(getX() - RADIUS, getY() - 0.5, getZ() - RADIUS, getX() + RADIUS, getY() + 2.5, getZ() + RADIUS);
    }

    private boolean drauf(Entity e) {
        double dx = e.getX() - getX(), dz = e.getZ() - getZ();
        return dx * dx + dz * dz <= RADIUS * RADIUS && Math.abs(e.getY() - getY()) < 1.0;
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        float tempo = entityData.get(TEMPO);
        if (tempo != 0) {
            setYRot(getYRot() + tempo);
            mitdrehen(tempo);
        }
        yBodyRot = getYRot();
        yBodyRotO = yRotO;
        yHeadRot = getYRot();
        if (!level().isClientSide()) {
            steuern(tempo);
        }
    }

    /** Dreht alle Autos auf der Fläche um die Mitte – auf dem Server die ohne Fahrer, beim Klienten den eigenen. */
    private void mitdrehen(float grad) {
        double w = Math.toRadians(-grad);
        double cos = Math.cos(w), sin = Math.sin(w);
        for (AutoEntity auto : level().getEntitiesOfClass(AutoEntity.class, flaeche(), this::drauf)) {
            boolean zustaendig = level().isClientSide() ? de.moinmornhart.stinkendreich.Kompat.lokalGesteuert(auto) : auto.getControllingPassenger() == null;
            if (!zustaendig) {
                continue;
            }
            double dx = auto.getX() - getX(), dz = auto.getZ() - getZ();
            auto.setPos(getX() + dx * cos - dz * sin, auto.getY(), getZ() + dx * sin + dz * cos);
            auto.setYRot(auto.getYRot() + grad);
            auto.yBodyRot = auto.getYRot();
            for (Entity p : auto.getPassengers()) {
                p.setYRot(p.getYRot() + grad);
                p.setYHeadRot(p.getYHeadRot() + grad);
            }
        }
    }

    /** Server: Wenden starten, wenn ein Auto still steht; danach Ausstellungsmodus oder anhalten. */
    private void steuern(float tempo) {
        AutoEntity auto = null;
        boolean steht = false;
        for (AutoEntity a : level().getEntitiesOfClass(AutoEntity.class, flaeche(), this::drauf)) {
            Vec3 alt = letztePos.put(a.getId(), a.position());
            steht = alt != null && alt.distanceTo(a.position()) < 0.02;
            auto = a;
            break;
        }
        if (tempo > 2) {
            // Wenden läuft: nach 180° Ruhe
            rest -= tempo;
            if (rest <= 0) {
                setzeTempo(0);
                stillstand = 0;
                gewendet = true;
            }
            return;
        }
        if (auto == null) {
            stillstand = 0;
            gewendet = false;
            letztePos.clear();
            if (tempo != 0) {
                setzeTempo(0);
            }
            return;
        }
        stillstand = steht ? stillstand + 1 : Math.min(stillstand, 0);
        if (stillstand >= 20 && auto.getControllingPassenger() != null && !gewendet) {
            rest = 180;
            setzeTempo(3.0F);
        } else if (stillstand >= 20 && tempo == 0) {
            setzeTempo(0.5F);
        } else if (auto.getControllingPassenger() != null && tempo > 0 && tempo <= 2) {
            setzeTempo(0);
        }
    }

    private void setzeTempo(float tempo) {
        entityData.set(TEMPO, tempo);
    }

    /** Für den Renderer: Drehwinkel. */
    public float winkel(float zwischen) {
        return Mth.rotLerp(zwischen, yRotO, getYRot());
    }
}
