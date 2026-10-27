package de.moinmornhart.stinkendreich.bodyguard;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Bodyguard: ein Eisengolem im Smoking mit Sonnenbrille und goldener Pistole. Er folgt seinem Chef, kann nie
 * sterben (nur /kill) und beschützt nur: Er greift Monster in der Nähe des Chefs an und jeden, der den Chef angreift.
 * Aus der Ferne schießt er mit der Pistole (Sofort-Treffer, ohne Blockschaden), aus der Nähe schlägt er zu wie ein
 * Eisengolem (wirft Gegner in die Luft). Spieler und Tiere lässt er in Ruhe. Beim Schießen zielt er: Er dreht sich
 * zum Gegner und hebt den Arm mit der Pistole (der Client liest dafür {@link #zielt()}). Zwei Bodyguards teilen sich
 * die Gegner auf, und wer in einer Wand steckt oder nicht hinterherkommt, steht gleich wieder neben dem Chef.
 */
public class BodyguardEntity extends IronGolem {
    private static final String SCHLUESSEL = "stinkendreich";
    /** Ab diesem Abstand schießt er statt zuzuschlagen (Blöcke). */
    private static final double SCHUSS_AB = 4.5, SCHUSS_BIS = 24.0;
    private static final int NACHLADEN = 18;
    /** Zielt gerade auf einen Gegner (für den Arm auf dem Client). */
    private static final EntityDataAccessor<Boolean> ZIELT = SynchedEntityData.defineId(BodyguardEntity.class, EntityDataSerializers.BOOLEAN);

    private UUID chef;
    private int nachladen;
    /** Für das Erkennen, ob er feststeckt: Stelle bei der letzten Prüfung und wie oft er seitdem nicht vorankam. */
    private Vec3 letzteStelle;
    private int festgesteckt;

    public BodyguardEntity(EntityType<? extends IronGolem> typ, Level welt) {
        super(typ, welt);
        setPersistenceRequired();
        setPlayerCreated(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder daten) {
        super.defineSynchedData(daten);
        daten.define(ZIELT, false);
    }

    /** Zielt er gerade mit der Pistole (auf dem Client: Arm gehoben)? */
    public boolean zielt() {
        return entityData.get(ZIELT);
    }

    public static AttributeSupplier.Builder attribute() {
        return IronGolem.createAttributes().add(Attributes.MOVEMENT_SPEED, 0.32).add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void registerGoals() {
        // Nahkampf wie ein Eisengolem, nur nicht gegen Creeper (die erledigt er aus der Ferne mit der Pistole)
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.1, true) {
            @Override
            public boolean canUse() {
                return !(getTarget() instanceof Creeper) && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !(getTarget() instanceof Creeper) && super.canContinueToUse();
            }
        });
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
        boolean zielen = false;
        if (ziel != null && ziel.isAlive()) {
            double d = distanceTo(ziel);
            zielen = (d > SCHUSS_AB || ziel instanceof Creeper) && d < SCHUSS_BIS && hasLineOfSight(ziel);
        }
        if (zielen) {
            // Zum Gegner drehen: Kopf und Körper, damit der gehobene Arm wirklich auf ihn zeigt
            getLookControl().setLookAt(ziel, 60.0F, 60.0F);
            setYBodyRot(getYHeadRot());
            if (nachladen == 0) {
                schiessen((ServerLevel) level(), ziel);
            }
        }
        //? if >=1.21.9 {
        if (getMainHandItem().isEmpty()) {
            // Die Pistole sieht nur, wer die Mod nicht hat (Server-Version: Schaufensterpuppe); der Golem zeigt sie nicht
            setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(
                    de.moinmornhart.stinkendreich.register.ModItems.GOLDENE_PISTOLE));
            setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.0F);
        }
        //?}
        if (zielt() != zielen) {
            entityData.set(ZIELT, zielen);
            if (de.moinmornhart.stinkendreich.dev.Autotest.aktiv()) {
                de.moinmornhart.stinkendreich.StinkendReich.LOGGER.info("Autotest: Bodyguard {} {}", getId(),
                        zielen ? "zielt auf " + ziel.getType().getDescriptionId() + " " + ziel.getId() : "zielt nicht mehr");
            }
            //? if >=1.21.9
            handZeigen(zielen);
        }
        if (tickCount % 40 == 0) {
            feststeckenPruefen();
        }
    }

    /**
     * Steckt er in einer Wand oder kommt seit vier Sekunden nicht voran, obwohl der Chef weiter weg ist (z. B. hinter
     * einer Tür, die ein Golem nicht öffnen kann), steht er gleich wieder an einer freien Stelle neben dem Chef.
     */
    private void feststeckenPruefen() {
        ServerPlayer c = chefOnline();
        if (c == null || c.level() != level()) {
            return;
        }
        boolean steht = letzteStelle != null && letzteStelle.distanceToSqr(position()) < 0.25;
        boolean mussLaufen = distanceToSqr(c) > 5 * 5 && !zielt();
        festgesteckt = steht && mussLaufen ? festgesteckt + 1 : 0;
        letzteStelle = position();
        if (isInWall() || festgesteckt >= 2) {
            Vec3 frei = Bodyguards.freieStelle((ServerLevel) level(), this, c, c.getYRot() + 180);
            teleportTo(frei.x, frei.y, frei.z);
            getNavigation().stop();
            festgesteckt = 0;
        }
    }

    //? if >=1.21.9 {
    /**
     * Spieler ohne Mod sehen den Bodyguard als Schaufensterpuppe (Server-Version). Zielt er, bekommt die Puppe eine
     * geladene Armbrust mit dem Bild der Pistole in die Hand – Minecraft zeichnet dann die Zielhaltung mit beiden Armen;
     * sonst die Pistole. Spieler mit Mod sehen die Hand eines Golems nicht, für sie ändert sich nichts.
     */
    private void handZeigen(boolean zielen) {
        net.minecraft.world.item.ItemStack hand = zielen ? imAnschlag() : getMainHandItem().copy();
        ((ServerLevel) level()).getChunkSource().sendToTrackingPlayers(this, new net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket(
                getId(), List.of(com.mojang.datafixers.util.Pair.of(net.minecraft.world.entity.EquipmentSlot.MAINHAND, hand))));
    }

    private static net.minecraft.world.item.ItemStack imAnschlag() {
        net.minecraft.world.item.ItemStack s = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CROSSBOW);
        s.set(net.minecraft.core.component.DataComponents.ITEM_MODEL, de.moinmornhart.stinkendreich.StinkendReich.id("goldene_pistole"));
        List<net.minecraft.world.item.ItemStack> pfeil = List.of(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW));
        //? if >=26.1 {
        s.set(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES, net.minecraft.world.item.component.ChargedProjectiles.ofNonEmpty(pfeil));
        //?} else
        /*s.set(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES, net.minecraft.world.item.component.ChargedProjectiles.of(pfeil));*/
        return s;
    }
    //?}

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
                Vec3 frei = Bodyguards.freieStelle((ServerLevel) level(), BodyguardEntity.this, c, c.getYRot() + 180);
                teleportTo(frei.x, frei.y, frei.z);
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

    /**
     * Wer den Chef angreift oder vom Chef angegriffen wird, und Monster in seiner Nähe (Creeper zuerst). Bei mehreren
     * Monstern nimmt jeder Bodyguard ein anderes, damit nicht einer alles macht und der andere zuschaut.
     */
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
            List<LivingEntity> monster = new ArrayList<>(level().getEntitiesOfClass(LivingEntity.class,
                    c.getBoundingBox().inflate(16), e -> e instanceof Enemy && e.isAlive() && e.distanceToSqr(c) < 16 * 16));
            // Creeper zählen näher, damit sie weg sind, bevor sie zischen
            monster.sort(Comparator.comparingDouble(e -> e.distanceToSqr(c) * (e instanceof Creeper ? 0.25 : 1.0)));
            if (monster.isEmpty()) {
                return null;
            }
            // Was die anderen Bodyguards des Chefs schon im Visier haben, kommt erst danach an die Reihe
            List<LivingEntity> belegt = new ArrayList<>();
            for (BodyguardEntity b : level().getEntitiesOfClass(BodyguardEntity.class, c.getBoundingBox().inflate(48),
                    b -> b != BodyguardEntity.this && c.getUUID().equals(b.chef()))) {
                if (b.getTarget() != null) {
                    belegt.add(b.getTarget());
                }
            }
            return monster.stream().filter(e -> !belegt.contains(e)).findFirst().orElse(monster.getFirst());
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
