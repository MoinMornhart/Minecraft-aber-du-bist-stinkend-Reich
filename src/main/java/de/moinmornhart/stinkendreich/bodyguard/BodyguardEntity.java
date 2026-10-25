package de.moinmornhart.stinkendreich.bodyguard;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
// Der Eisengolem ist mit 1.21.11 in ein eigenes Paket gezogen
//? if >=1.21.11 {
import net.minecraft.world.entity.animal.golem.IronGolem;
//?} else
/*import net.minecraft.world.entity.animal.IronGolem;*/
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
//? if >=1.21.6 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?} else {
/*import net.minecraft.nbt.CompoundTag;
*///?}

import java.util.EnumSet;
import java.util.UUID;

/**
 * Bodyguard: ein Eisengolem im Smoking mit Sonnenbrille und goldener Pistole. Er folgt seinem Chef, kann nie
 * sterben (nur /kill) und beschützt nur: Er greift Monster in der Nähe des Chefs an und jeden, der den Chef angreift.
 * Aus der Ferne schießt er mit der Pistole (Sofort-Treffer, ohne Blockschaden), aus der Nähe schlägt er zu wie ein
 * Eisengolem (wirft Gegner in die Luft). Spieler und Tiere lässt er in Ruhe.
 */
public class BodyguardEntity extends IronGolem {
    private static final String SCHLUESSEL = "stinkendreich";
    /** Ab diesem Abstand schießt er statt zuzuschlagen (Blöcke). */
    private static final double SCHUSS_AB = 4.5, SCHUSS_BIS = 24.0;
    private static final int NACHLADEN = 18;

    private UUID chef;
    private int nachladen;

    public BodyguardEntity(EntityType<? extends IronGolem> typ, Level welt) {
        super(typ, welt);
        setPersistenceRequired();
        setPlayerCreated(true);
    }

    public static AttributeSupplier.Builder attribute() {
        return IronGolem.createAttributes().add(Attributes.MOVEMENT_SPEED, 0.32).add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.1, true));
        goalSelector.addGoal(2, new FolgenZiel());
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new BeschuetzenZiel());
    }

    public void setzeChef(Player p) {
        chef = p.getUUID();
        setCustomName(Component.translatable("stinkendreich.bodyguard.name"));
    }

    public UUID chef() {
        return chef;
    }

    private ServerPlayer chefOnline() {
        return chef == null || !(level() instanceof ServerLevel sl) ? null : sl.getServer().getPlayerList().getPlayer(chef);
    }

    @Override
    public boolean removeWhenFarAway(double abstand) {
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

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        if (nachladen > 0) {
            nachladen--;
        }
        LivingEntity ziel = getTarget();
        if (ziel != null && ziel.isAlive() && nachladen == 0 && hasLineOfSight(ziel)) {
            double d = distanceTo(ziel);
            if (d > SCHUSS_AB && d < SCHUSS_BIS) {
                schiessen((ServerLevel) level(), ziel);
            }
        }
    }

    /** Ein Schuss mit der goldenen Pistole: Mündungsfeuer, Leuchtspur, Treffer. */
    private void schiessen(ServerLevel welt, LivingEntity ziel) {
        nachladen = NACHLADEN;
        getLookControl().setLookAt(ziel, 90, 90);
        Vec3 von = position().add(0, 2.0, 0).add(getLookAngle().scale(0.8));
        Vec3 nach = ziel.position().add(0, ziel.getBbHeight() * 0.6, 0);
        Vec3 schritt = nach.subtract(von).normalize().scale(0.6);
        Vec3 p = von;
        for (int i = 0; i < 48 && p.distanceToSqr(nach) > 0.5; i++) {
            welt.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            p = p.add(schritt);
        }
        welt.sendParticles(ParticleTypes.FLAME, von.x, von.y, von.z, 4, 0.05, 0.05, 0.05, 0.01);
        welt.playSound(null, blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 1.2F, 1.6F);
        ziel.hurt(damageSources().mobAttack(this), 7.0F);
    }

    // ------------------------------------------------------------------
    // Ziele
    // ------------------------------------------------------------------

    /** Bleibt beim Chef: über 6 Blöcke läuft er hinterher, über 28 Blöcke springt er zu ihm. */
    private class FolgenZiel extends Goal {
        FolgenZiel() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            ServerPlayer c = chefOnline();
            return c != null && c.level() == level() && getTarget() == null && distanceToSqr(c) > 36;
        }

        @Override
        public void tick() {
            ServerPlayer c = chefOnline();
            if (c == null) {
                return;
            }
            if (distanceToSqr(c) > 28 * 28) {
                teleportTo(c.getX() + (random.nextDouble() - 0.5) * 4, c.getY(), c.getZ() + (random.nextDouble() - 0.5) * 4);
                getNavigation().stop();
            } else if (tickCount % 10 == 0) {
                getNavigation().moveTo(c, 1.1);
            }
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }

    /** Wer den Chef angreift oder vom Chef angegriffen wird, und Monster in seiner Nähe (außer Creepern). */
    private class BeschuetzenZiel extends Goal {
        BeschuetzenZiel() {
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (tickCount % 10 != 0) {
                return false;
            }
            LivingEntity neu = suchen();
            if (neu != null && neu != getTarget()) {
                setTarget(neu);
            }
            return false;
        }

        private LivingEntity suchen() {
            ServerPlayer c = chefOnline();
            if (c == null || c.level() != level()) {
                return null;
            }
            LivingEntity angreifer = c.getLastHurtByMob();
            if (angreifer != null && angreifer.isAlive() && angreifer != BodyguardEntity.this && angreifer.distanceToSqr(c) < 32 * 32
                    && !(angreifer instanceof BodyguardEntity)) {
                return angreifer;
            }
            LivingEntity angegriffen = c.getLastHurtMob();
            if (angegriffen != null && angegriffen.isAlive() && angegriffen instanceof Enemy && angegriffen.distanceToSqr(c) < 32 * 32) {
                return angegriffen;
            }
            LivingEntity bestes = null;
            double beste = 16 * 16;
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, c.getBoundingBox().inflate(16),
                    e -> e instanceof Enemy && !(e instanceof Creeper) && e.isAlive())) {
                double d = e.distanceToSqr(c);
                if (d < beste) {
                    beste = d;
                    bestes = e;
                }
            }
            return bestes;
        }
    }

    // ------------------------------------------------------------------
    // Speichern (Chef)
    // ------------------------------------------------------------------

    //? if >=1.21.6 {
    @Override
    protected void addAdditionalSaveData(ValueOutput ausgabe) {
        super.addAdditionalSaveData(ausgabe);
        ausgabe.putString(SCHLUESSEL, chef == null ? "" : chef.toString());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput eingabe) {
        super.readAdditionalSaveData(eingabe);
        chefAusText(eingabe.getStringOr(SCHLUESSEL, ""));
    }
    //?} else if >=1.21.5 {
    /*@Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(SCHLUESSEL, chef == null ? "" : chef.toString());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        chefAusText(tag.getStringOr(SCHLUESSEL, ""));
    }
    *///?} else {
    /*@Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(SCHLUESSEL, chef == null ? "" : chef.toString());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        chefAusText(tag.getString(SCHLUESSEL));
    }
    *///?}

    private void chefAusText(String text) {
        try {
            chef = text.isEmpty() ? null : UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            chef = null;
        }
    }
}
