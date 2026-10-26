package de.moinmornhart.stinkendreich.hai;

import de.moinmornhart.stinkendreich.bodyguard.BodyguardEntity;
import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import de.moinmornhart.stinkendreich.club.TaenzerinEntity;
import de.moinmornhart.stinkendreich.mechanik.VillaZustand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
// Der Delfin ist mit 1.21.11 in ein eigenes Paket gezogen
//? if >=1.21.11 {
import net.minecraft.world.entity.animal.dolphin.Dolphin;
//?} else
/*import net.minecraft.world.entity.animal.Dolphin;*/
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.EnumSet;

/**
 * Hai im Becken unter dem Wohnzimmer (Haifischfalle, siehe {@link de.moinmornhart.stinkendreich.mechanik.Haifalle}).
 * Ein Delfin, der nur herumschwimmt und angreift: Spieler, die keine Bewohner sind, und Monster, aber nur im Wasser.
 * Bewohner, das Personal (Butler, Mitarbeiter, Bodyguards, Tänzerinnen) und andere Haie lässt er in Ruhe. Er erstickt
 * nicht, verschwindet nie und ist unverwundbar (nur /kill), damit ihn kein Eindringling aus dem Becken räumt.
 * Gezeichnet wird er als grauer, größerer Delfin ({@code client/hai/HaiRenderer}).
 */
public class HaiEntity extends Dolphin {
    /** Wie weit er nach Beute sucht (Blöcke). */
    private static final double SUCHWEITE = 16.0;

    public HaiEntity(EntityType<? extends Dolphin> typ, Level welt) {
        super(typ, welt);
        setPersistenceRequired();
        setCanPickUpLoot(false);       // Delfine spielen mit Gegenständen, Haie nicht
    }

    public static AttributeSupplier.Builder attribute() {
        return Dolphin.createAttributes().add(Attributes.MAX_HEALTH, 30.0).add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 1.3).add(Attributes.FOLLOW_RANGE, 24.0);
    }

    /** Statt der Delfin-Ziele (Luft holen, Schätze, Spieler begleiten, spielen): nur schwimmen und zubeißen. */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.4, true));
        goalSelector.addGoal(4, new RandomSwimmingGoal(this, 1.0, 10));
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new BeuteZiel());
    }

    /** Ist diese Figur Beute? Nur im Wasser; Eindringlinge und Monster, nie Bewohner oder Personal. */
    private boolean beute(LivingEntity e) {
        if (e == null || !e.isAlive() || !e.isInWater() || e instanceof HaiEntity
                || e instanceof ButlerEntity || e instanceof BodyguardEntity || e instanceof TaenzerinEntity) {
            return false;
        }
        if (e instanceof Player p) {
            return level() instanceof ServerLevel sl && VillaZustand.get(sl.getServer()).istEindringling(p);
        }
        return e instanceof Enemy;
    }

    @Override
    public boolean removeWhenFarAway(double abstand) {
        return false;
    }

    /** Fische füttern und Ähnliches gibt es nicht. */
    @Override
    protected InteractionResult mobInteract(Player spieler, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    // Unverwundbar, nur /kill und die Leere wirken
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

    /** Haie atmen unter Wasser (Delfine müssen auftauchen). */
    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    protected void handleAirSupply(int luft) {
        setAirSupply(getMaxAirSupply());
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            setAirSupply(getMaxAirSupply());
            // Ziel loslassen, sobald es keine Beute mehr ist (aus dem Wasser, Bewohner geworden …)
            LivingEntity ziel = getTarget();
            if (ziel != null && !beute(ziel)) {
                setTarget(null);
            }
        }
    }

    /** Sucht alle halbe Sekunde die nächste Beute im Wasser. */
    private class BeuteZiel extends Goal {
        BeuteZiel() {
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (tickCount % 10 != 0 || !isInWater()) {
                return false;
            }
            LivingEntity bestes = null;
            double beste = SUCHWEITE * SUCHWEITE;
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(SUCHWEITE), HaiEntity.this::beute)) {
                double d = e.distanceToSqr(HaiEntity.this);
                if (d < beste) {
                    beste = d;
                    bestes = e;
                }
            }
            if (bestes != null && bestes != getTarget()) {
                setTarget(bestes);
            }
            return false;
        }
    }
}
