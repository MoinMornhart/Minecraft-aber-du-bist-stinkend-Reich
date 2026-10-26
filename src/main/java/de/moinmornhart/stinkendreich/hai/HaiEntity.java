package de.moinmornhart.stinkendreich.hai;

import de.moinmornhart.stinkendreich.bodyguard.BodyguardEntity;
import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import de.moinmornhart.stinkendreich.club.TaenzerinEntity;
import de.moinmornhart.stinkendreich.StinkendReich;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
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
 * Technisch ein Delfin, der nur herumschwimmt und zubeißt (4 Herzen je Biss): jeden Spieler im Wasser (außer im
 * Kreativmodus) und Monster. Tiere (Schildkröten im Aquarium) lässt er schwimmen. Das Personal (Butler, Mitarbeiter, Bodyguards, Tänzerinnen) und andere Haie lässt
 * er in Ruhe. Er erstickt
 * nicht, verschwindet nie und ist unverwundbar (nur /kill), damit ihn kein Eindringling aus dem Becken räumt.
 * Gezeichnet wird er mit eigenem Hai-Modell ({@code client/hai/HaiModell}).
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
        return Dolphin.createAttributes().add(Attributes.MAX_HEALTH, 30.0).add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 1.3).add(Attributes.FOLLOW_RANGE, 24.0);
    }

    /** Statt der Delfin-Ziele (Luft holen, Schätze, Spieler begleiten, spielen): nur schwimmen und zubeißen. */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new Jagd());
        goalSelector.addGoal(4, new RandomSwimmingGoal(this, 1.0, 10));
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    /** Ist diese Figur Beute? Nur im Wasser; Spieler und Monster, nie Tiere oder das Personal. */
    private boolean beute(LivingEntity e) {
        if (e == null || !e.isAlive() || !e.isInWater() || e instanceof HaiEntity
                || e instanceof ButlerEntity || e instanceof BodyguardEntity || e instanceof TaenzerinEntity) {
            return false;
        }
        if (e instanceof Player p) {
            // Wer ins Haibecken fällt, ist Beute – auch Bewohner und Gäste (Philip: „wie ein echter Angriffsmob“).
            // Nur Kreativ- und Zuschauermodus bleiben verschont (die kann ohnehin nichts verletzen).
            return !p.isCreative() && !p.isSpectator();
        }
        return e instanceof Enemy;
    }

    /**
     * Eigener Schaden „Haibiss“ (data/stinkendreich/damage_type/hai.json, skaliert nie mit dem Schwierigkeitsgrad):
     * Normale Mob-Angriffe machen auf „Friedlich“ keinen Schaden, der Hai beißt dort trotzdem zu.
     */
    private static final ResourceKey<DamageType> BISS = ResourceKey.create(Registries.DAMAGE_TYPE, StinkendReich.id("hai"));

    private DamageSource biss(ServerLevel welt) {
        return new DamageSource(welt.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(BISS), this);
    }

    private void zugebissen(ServerLevel welt, Entity ziel) {
        welt.playSound(null, ziel.blockPosition(), SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 1.0F, 0.8F);
        welt.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
                        net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState()),
                ziel.getX(), ziel.getY() + ziel.getBbHeight() / 2, ziel.getZ(), 12, 0.3, 0.3, 0.3, 0.1);
    }

    //? if >=1.21.2 {
    @Override
    public boolean doHurtTarget(ServerLevel welt, Entity ziel) {
        boolean getroffen = ziel.hurtServer(welt, biss(welt), (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
        if (getroffen) {
            zugebissen(welt, ziel);
        }
        return getroffen;
    }
    //?} else {
    /*@Override
    public boolean doHurtTarget(Entity ziel) {
        if (!(level() instanceof ServerLevel welt)) {
            return false;
        }
        boolean getroffen = ziel.hurt(biss(welt), (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
        if (getroffen) {
            zugebissen(welt, ziel);
        }
        return getroffen;
    }
    *///?}

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

    /** Haie atmen unter Wasser (Delfine müssen auftauchen): die Luft bleibt immer voll. */
    @Override
    protected void handleAirSupply(int luft) {
        setAirSupply(getMaxAirSupply());
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            setAirSupply(getMaxAirSupply());
        }
    }

    /**
     * Jagd ohne das Ziel-System von Minecraft (das lässt auf „Friedlich“ keine Spieler als Ziel zu): Alle halbe Sekunde
     * die nächste Beute im Wasser suchen, hinschwimmen und zubeißen, sobald sie in Reichweite ist (einmal je Sekunde).
     */
    private class Jagd extends Goal {
        private LivingEntity beute;
        private int pause;

        Jagd() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private LivingEntity suchen() {
            LivingEntity bestes = null;
            double beste = SUCHWEITE * SUCHWEITE;
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(SUCHWEITE), HaiEntity.this::beute)) {
                double d = e.distanceToSqr(HaiEntity.this);
                if (d < beste) {
                    beste = d;
                    bestes = e;
                }
            }
            return bestes;
        }

        @Override
        public boolean canUse() {
            if (tickCount % 10 != 0 || !isInWater()) {
                return false;
            }
            beute = suchen();
            return beute != null;
        }

        @Override
        public boolean canContinueToUse() {
            return beute != null && beute(beute) && beute.distanceToSqr(HaiEntity.this) < SUCHWEITE * SUCHWEITE * 1.5;
        }

        @Override
        public void stop() {
            beute = null;
            getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            getLookControl().setLookAt(beute, 30.0F, 30.0F);
            if (tickCount % 5 == 0) {
                getNavigation().moveTo(beute, 1.4);
            }
            if (pause > 0) {
                pause--;
                return;
            }
            // Reichweite wie beim Nahkampf: Hitboxen fast berühren (der Hai ist lang, deshalb etwas großzügiger)
            if (getBoundingBox().inflate(0.6).intersects(beute.getBoundingBox()) && level() instanceof ServerLevel welt) {
                //? if >=1.21.2 {
                doHurtTarget(welt, beute);
                //?} else
                /*doHurtTarget(beute);*/
                pause = 20;
            }
        }
    }
}
