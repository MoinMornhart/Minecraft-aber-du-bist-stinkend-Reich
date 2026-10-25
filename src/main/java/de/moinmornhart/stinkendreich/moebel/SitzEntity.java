package de.moinmornhart.stinkendreich.moebel;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.register.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Unsichtbarer Sitzplatz auf einem Möbelstück (Sofa, Sessel, Strandliege, Bettbank). Rechtsklick auf das
 * Möbel setzt den Spieler darauf, Schleichen lässt ihn aufstehen; danach verschwindet der Sitz von selbst.
 * Man schaut in die Blickrichtung des Möbels und kann sich höchstens 100° zur Seite drehen.
 */
public class SitzEntity extends Mob {
    private BlockPos moebel = BlockPos.ZERO;

    public SitzEntity(EntityType<? extends SitzEntity> typ, Level welt) {
        super(typ, welt);
        setNoAi(true);
        setNoGravity(true);
        setInvisible(true);
        setSilent(true);
    }

    public static AttributeSupplier.Builder attribute() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1.0);
    }

    /**
     * Setzt den Spieler auf das Möbel bei {@code pos}.
     *
     * @param hoehe Sitzhöhe in Pixeln über der Blockunterkante
     * @param blick Richtung, in die man sitzend schaut
     */
    public static void hinsetzen(ServerLevel welt, BlockPos pos, double hoehe, Direction blick, Player spieler) {
        AABB platz = new AABB(pos).inflate(0.1);
        if (!welt.getEntitiesOfClass(SitzEntity.class, platz, s -> true).isEmpty()) {
            return;     // schon besetzt
        }
        SitzEntity sitz = ModEntities.SITZ.create(welt
                //? if >=1.21.2
                , net.minecraft.world.entity.EntitySpawnReason.TRIGGERED
        );
        if (sitz == null) {
            return;
        }
        sitz.moebel = pos.immutable();
        float grad = blick.toYRot();
        // Etwas nach hinten versetzt, damit man auf der Sitzfläche und nicht auf der Vorderkante sitzt
        Kompat.versetzen(sitz, pos.getX() + 0.5 - blick.getStepX() * 0.1, pos.getY() + hoehe / 16.0,
                pos.getZ() + 0.5 - blick.getStepZ() * 0.1, grad, 0.0F);
        sitz.setYHeadRot(grad);
        sitz.setYBodyRot(grad);
        welt.addFreshEntity(sitz);
        spieler.startRiding(sitz);
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        // Leer oder Möbel abgebaut: weg damit
        if (!level().isClientSide() && tickCount > 2
                && (!isVehicle() || level().getBlockState(moebel).isAir())) {
            ejectPassengers();
            discard();
        }
    }

    /** Der Spieler sitzt genau auf der Höhe des Sitzes. */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passagier, EntityDimensions masse, float skala) {
        return Vec3.ZERO;
    }

    @Override
    protected void positionRider(Entity passagier, Entity.MoveFunction bewegen) {
        super.positionRider(passagier, bewegen);
        blickBegrenzen(passagier);
    }

    @Override
    public void onPassengerTurned(Entity passagier) {
        blickBegrenzen(passagier);
    }

    private void blickBegrenzen(Entity passagier) {
        passagier.setYBodyRot(getYRot());
        float abweichung = Mth.wrapDegrees(passagier.getYRot() - getYRot());
        float erlaubt = Mth.clamp(abweichung, -100.0F, 100.0F);
        passagier.yRotO += erlaubt - abweichung;
        passagier.setYRot(passagier.getYRot() + erlaubt - abweichung);
        passagier.setYHeadRot(passagier.getYRot());
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double abstand) {
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
}
