package de.moinmornhart.stinkendreich.mechanik;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import de.moinmornhart.stinkendreich.register.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
//? if >=1.21.6 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?} else {
/*import net.minecraft.nbt.CompoundTag;
*///?}

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Die gläserne Aufzugskabine (2 × 2 Blöcke, Goldrahmen, Samtbank). Sie wohnt im Schacht und fährt
 * sanft an und ab (Geschwindigkeit folgt einer S-Kurve). Wer vor der Tür steht, wenn sie hält und eine
 * Fahrt bestellt ist, steigt ein und sitzt auf der Bank; am Ziel steigen alle vor der Tür aus.
 *
 * <p>Die Fahrt läuft auf Server und Klient gleich ab: Beide kennen Start, Ziel und die vergangenen Ticks
 * und rechnen die Höhe selbst aus. Darum ruckelt nichts, auch nicht für die Mitfahrer.</p>
 *
 * <p>Die Böden der Haltestellen liegen im Schacht. Fährt die Kabine durch, werden sie kurz
 * herausgenommen und gleich danach wieder eingesetzt.</p>
 */
public class AufzugKabine extends Mob {
    private static final EntityDataAccessor<Float> START_Y = SynchedEntityData.defineId(AufzugKabine.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ZIEL_Y = SynchedEntityData.defineId(AufzugKabine.class, EntityDataSerializers.FLOAT);
    /** Ticks seit Abfahrt; -1 = steht. */
    private static final EntityDataAccessor<Integer> FAHRT = SynchedEntityData.defineId(AufzugKabine.class, EntityDataSerializers.INT);
    private static final String SCHLUESSEL = "stinkendreich";
    public static final int SITZE = 3;
    /** Ticks Halt an einer Haltestelle, bevor es weitergeht bzw. alle aussteigen. */
    private static final int HALT_TICKS = 16;
    /** So lange (Ticks) wartet die Kabine beim Abholen auf Fahrgäste; kommt keiner, bleibt sie stehen. */
    private static final int ABHOLEN_TICKS = 20 * 6;

    /** Ein bestellter Halt: dort wird eingestiegen (abholen) oder ausgestiegen. */
    private record Halt(double y, boolean abholen) {
    }

    private final Deque<Halt> halte = new ArrayDeque<>();
    /** Durch die Kabine herausgenommene Blöcke (Haltestellenböden), werden nach der Durchfahrt zurückgesetzt. */
    private final Map<BlockPos, BlockState> entfernt = new HashMap<>();
    private Direction tuer = Direction.SOUTH;
    private int wartet;
    /** Ticks, die die Kabine beim Abholen schon auf Fahrgäste gewartet hat. */
    private int abholWarten;
    /** Ticks seit Abfahrt, auf jeder Seite selbst gezählt (-1 = steht). */
    private int fahrtZaehler = -1;

    public AufzugKabine(EntityType<? extends AufzugKabine> typ, Level welt) {
        super(typ, welt);
        setPersistenceRequired();
        setNoAi(true);
        setNoGravity(true);
        noPhysics = true;
    }

    public static AttributeSupplier.Builder attribute() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 100.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder daten) {
        super.defineSynchedData(daten);
        daten.define(START_Y, 0.0F);
        daten.define(ZIEL_Y, 0.0F);
        daten.define(FAHRT, -1);
    }

    // ------------------------------------------------------------------
    // Grundverhalten: unverwundbar, nicht schiebbar, nicht anklickbar
    // ------------------------------------------------------------------

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

    /** Die Kabine schiebt niemanden weg (sonst ruckelt man beim Hineinlaufen hin und her). */
    @Override
    protected void pushEntities() {
    }

    /** Durchklickbar: Klicks gehen an die Tafeln und Wände dahinter. */
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

    @Override
    protected boolean canAddPassenger(Entity passagier) {
        return getPassengers().size() < SITZE;
    }

    /** Sitzplätze auf der Samtbank an der Rückwand, Blick zur Tür. */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passagier, EntityDimensions masse, float skala) {
        int platz = Math.max(0, getPassengers().indexOf(passagier));
        double seite = (platz - 1) * 0.55;
        Direction quer = tuer.getClockWise();
        return new Vec3(quer.getStepX() * seite - tuer.getStepX() * 0.45, 0.12, quer.getStepZ() * seite - tuer.getStepZ() * 0.45);
    }

    /** Mitfahrer schauen zur Tür und können sich höchstens 100° zur Seite umsehen. */
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
        float vorne = tuer.toYRot();
        passagier.setYBodyRot(vorne);
        float abweichung = Mth.wrapDegrees(passagier.getYRot() - vorne);
        float erlaubt = Mth.clamp(abweichung, -100.0F, 100.0F);
        passagier.yRotO += erlaubt - abweichung;
        passagier.setYRot(passagier.getYRot() + erlaubt - abweichung);
        passagier.setYHeadRot(passagier.getYRot());
    }

    /** Aussteigen (auch mit Schleichen mitten in der Fahrt): vor die Tür der nächsten bzw. Ziel-Haltestelle. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passagier) {
        double y = faehrt() ? entityData.get(ZIEL_Y) : getY();
        return ausstieg(y);
    }

    // ------------------------------------------------------------------
    // Fahrt
    // ------------------------------------------------------------------

    public boolean faehrt() {
        return entityData.get(FAHRT) >= 0;
    }

    public Direction tuer() {
        return tuer;
    }

    /** Fahrtdauer in Ticks: mindestens 2 Sekunden, Spitze gut 7 Blöcke pro Sekunde. */
    public static int dauer(double start, double ziel) {
        return (int) Math.round(Math.max(40, Math.abs(ziel - start) / 0.28 + 24));
    }

    /** Höhe nach {@code t} Ticks: sanft anfahren und abbremsen (S-Kurve). */
    public static double hoehe(double start, double ziel, int t) {
        double u = Mth.clamp(t / (double) dauer(start, ziel), 0.0, 1.0);
        return start + (ziel - start) * u * u * (3 - 2 * u);
    }

    /** Wo man an einer Haltestelle steht, wenn man aussteigt: gut einen Block vor der Tür. */
    public Vec3 ausstieg(double y) {
        return new Vec3(getX() + tuer.getStepX() * 2.4, y, getZ() + tuer.getStepZ() * 2.4);
    }

    /** Bereich vor der Tür, aus dem beim Halt eingestiegen wird. */
    private AABB vorDerTuer() {
        Vec3 mitte = new Vec3(getX() + tuer.getStepX() * 2.0, getY() + 1.0, getZ() + tuer.getStepZ() * 2.0);
        return new AABB(mitte, mitte).inflate(tuer.getAxis() == Direction.Axis.X ? 1.6 : 2.2, 1.2,
                tuer.getAxis() == Direction.Axis.Z ? 1.6 : 2.2);
    }

    /** Neue Kabine an einer Haltestelle (Mitte der Kabine, Füße auf Bodenhöhe). */
    public void aufstellen(double x, double y, double z, Direction tuer) {
        this.tuer = tuer;
        de.moinmornhart.stinkendreich.Kompat.versetzen(this, x, y, z, tuer.toYRot(), 0.0F);
        setYBodyRot(tuer.toYRot());
        setYHeadRot(tuer.toYRot());
    }

    /**
     * Bestellung vom Ruf-Tableau: zur Haltestelle {@code abholY} kommen, dort alle vor der Tür mitnehmen
     * und nach {@code zielY} fahren.
     */
    public void bestellen(double abholY, double zielY) {
        halte.clear();
        halte.add(new Halt(abholY, true));
        if (Math.abs(zielY - abholY) > 0.5) {
            halte.add(new Halt(zielY, false));
        }
        if (!faehrt() && Math.abs(getY() - abholY) < 0.5) {
            // Steht schon da: gleich einsteigen lassen
            wartet = 4;
        } else if (!faehrt()) {
            losfahren(abholY);
        }
    }

    private void losfahren(double zielY) {
        entityData.set(START_Y, (float) getY());
        entityData.set(ZIEL_Y, (float) zielY);
        entityData.set(FAHRT, 0);
        fahrtZaehler = 0;
        level().playSound(null, blockPosition(), ModSounds.GONG, SoundSource.BLOCKS, 0.5F, 1.25F);
    }

    @Override
    public void tick() {
        noPhysics = true;
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        setYRot(tuer.toYRot());
        setYBodyRot(tuer.toYRot());

        int gemeldet = entityData.get(FAHRT);
        if (level().isClientSide()) {
            // Der Klient zählt selbst weiter; der Server meldet Abfahrt, Ende und alle zwei Sekunden den Stand
            if (gemeldet < 0) {
                fahrtZaehler = -1;
                return;
            }
            fahrtZaehler = fahrtZaehler < 0 || Math.abs(fahrtZaehler - gemeldet) > 6 ? gemeldet : fahrtZaehler + 1;
            setPos(getX(), hoehe(entityData.get(START_Y), entityData.get(ZIEL_Y), fahrtZaehler), getZ());
            return;
        }

        if (fahrtZaehler >= 0) {
            fahrtZaehler++;
            double start = entityData.get(START_Y);
            double ziel = entityData.get(ZIEL_Y);
            setPos(getX(), hoehe(start, ziel, fahrtZaehler), getZ());
            bodenFreiraeumen();
            if (fahrtZaehler >= dauer(start, ziel)) {
                setPos(getX(), ziel, getZ());
                fahrtZaehler = -1;
                entityData.set(FAHRT, -1);
                bodenFreiraeumen();
                level().playSound(null, blockPosition(), ModSounds.GONG, SoundSource.BLOCKS, 1.0F, 1.0F);
                wartet = HALT_TICKS;
            } else if (fahrtZaehler % 40 == 0) {
                entityData.set(FAHRT, fahrtZaehler);
            }
            return;
        }

        if (wartet > 0 && --wartet == 0) {
            halten();
        }
    }

    /** An einer Haltestelle: aussteigen bzw. einsteigen lassen, dann zum nächsten Halt. */
    private void halten() {
        Halt h = halte.peekFirst();
        if (h == null || Math.abs(h.y() - getY()) > 0.5) {
            // Ziel ohne passenden Halt (z. B. nach einem Neustart): alle steigen aus
            allesAussteigen();
            if (h != null) {
                losfahren(h.y());
            }
            return;
        }
        if (h.abholen()) {
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, vorDerTuer(),
                    e -> (e instanceof Player p && !p.isSpectator()) || e instanceof ButlerEntity)) {
                if (!e.isPassenger() && canAddPassenger(e)) {
                    e.startRiding(this);
                }
            }
            if (getPassengers().isEmpty()) {
                // Noch keiner da: kurz warten; kommt niemand, fährt die Kabine nicht leer los
                if (++abholWarten < ABHOLEN_TICKS) {
                    wartet = 1;
                } else {
                    halte.clear();
                    abholWarten = 0;
                }
                return;
            }
            abholWarten = 0;
            halte.pollFirst();
        } else {
            halte.pollFirst();
            allesAussteigen();
        }
        Halt naechster = halte.peekFirst();
        if (naechster != null) {
            losfahren(naechster.y());
        }
    }

    /** Alle steigen aus und stehen mit Blick nach vorn vor der Tür (nebeneinander, wie sie saßen). */
    private void allesAussteigen() {
        Direction quer = tuer.getClockWise();
        int platz = 0;
        for (Entity e : java.util.List.copyOf(getPassengers())) {
            e.stopRiding();
            double seite = (platz++ - 1) * 0.7;
            Vec3 p = ausstieg(getY()).add(quer.getStepX() * seite, 0, quer.getStepZ() * seite);
            if (e instanceof net.minecraft.server.level.ServerPlayer spieler && level() instanceof ServerLevel welt) {
                de.moinmornhart.stinkendreich.Kompat.befehl(welt.getServer(), String.format(java.util.Locale.ROOT,
                        "tp %s %.3f %.3f %.3f %.1f 0", spieler.getScoreboardName(), p.x, p.y, p.z, tuer.toYRot()));
            } else {
                de.moinmornhart.stinkendreich.Kompat.versetzen(e, p.x, p.y, p.z, tuer.toYRot(), 0.0F);
            }
        }
    }

    /**
     * Nimmt Blöcke im Schacht heraus, durch die die Kabine gerade fährt (Böden der anderen Haltestellen),
     * und setzt alle wieder ein, die sie nicht mehr berührt.
     */
    private void bodenFreiraeumen() {
        double unten = getY();
        double oben = getY() + getBbHeight();
        // Wieder einsetzen
        var it = entfernt.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            int y = e.getKey().getY();
            if (y + 1 <= unten + 0.01 || y >= oben) {
                if (level().getBlockState(e.getKey()).isAir()) {
                    level().setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS);
                    // Wer im Schacht steht, kommt auf den Boden statt hineinzustecken
                    for (Entity im : level().getEntities(this, new AABB(e.getKey()), x -> !x.isPassenger() && x != this)) {
                        im.teleportTo(im.getX(), e.getKey().getY() + 1.0, im.getZ());
                    }
                }
                it.remove();
            }
        }
        // Herausnehmen: alles in der Kabinengrundfläche, was zwischen Kabinenboden und -decke liegt
        int y0 = Mth.floor(unten + 0.01);
        int y1 = Mth.floor(oben - 0.01);
        double halb = getBbWidth() / 2 - 0.05;
        for (BlockPos p : BlockPos.betweenClosed(Mth.floor(getX() - halb), y0, Mth.floor(getZ() - halb),
                Mth.floor(getX() + halb), y1, Mth.floor(getZ() + halb))) {
            BlockState s = level().getBlockState(p);
            if (!s.isAir() && !(s.getBlock() instanceof AufzugstafelBlock) && !entfernt.containsKey(p)) {
                // Wer auf diesem Boden im Schacht steht, wird vor die Tür gestellt, statt hinunterzufallen
                AABB darueber = new AABB(p).expandTowards(0, 2, 0);
                for (Entity steht : level().getEntities(this, darueber, x -> !x.isPassenger() && x != this && !(x instanceof AufzugKabine))) {
                    Vec3 raus = ausstieg(p.getY() + 1.0);
                    steht.teleportTo(raus.x, raus.y, raus.z);
                    steht.resetFallDistance();
                }
                entfernt.put(p.immutable(), s);
                level().setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public void remove(RemovalReason grund) {
        // Böden nie verloren gehen lassen
        if (!level().isClientSide()) {
            entfernt.forEach((p, s) -> level().setBlock(p, s, Block.UPDATE_CLIENTS));
            entfernt.clear();
        }
        super.remove(grund);
    }

    // ------------------------------------------------------------------
    // Speichern
    // ------------------------------------------------------------------

    private String datenAlsText() {
        JsonObject j = new JsonObject();
        j.addProperty("tuer", tuer.getName());
        JsonArray liste = new JsonArray();
        entfernt.forEach((p, s) -> {
            JsonObject e = new JsonObject();
            e.addProperty("pos", p.asLong());
            e.addProperty("block", Block.getId(s));
            liste.add(e);
        });
        j.add("entfernt", liste);
        // Eine laufende Fahrt endet nach dem Laden am Ziel
        if (faehrt()) {
            j.addProperty("ziel", entityData.get(ZIEL_Y));
        }
        return j.toString();
    }

    private void datenAusText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        try {
            JsonObject j = JsonParser.parseString(text).getAsJsonObject();
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (d.getName().equals(j.get("tuer").getAsString())) {
                    tuer = d;
                }
            }
            if (j.has("ziel")) {
                setPos(getX(), j.get("ziel").getAsDouble(), getZ());
            }
            for (var e : j.getAsJsonArray("entfernt")) {
                JsonObject o = e.getAsJsonObject();
                entfernt.put(BlockPos.of(o.get("pos").getAsLong()), Block.stateById(o.get("block").getAsInt()));
            }
        } catch (Exception ignoriert) {
            // kaputte Daten: Standardwerte behalten
        }
    }

    //? if >=1.21.6 {
    @Override
    protected void addAdditionalSaveData(ValueOutput ausgabe) {
        super.addAdditionalSaveData(ausgabe);
        ausgabe.putString(SCHLUESSEL, datenAlsText());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput eingabe) {
        super.readAdditionalSaveData(eingabe);
        datenAusText(eingabe.getStringOr(SCHLUESSEL, ""));
    }
    //?} else if >=1.21.5 {
    /*@Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(SCHLUESSEL, datenAlsText());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        datenAusText(tag.getStringOr(SCHLUESSEL, ""));
    }
    *///?} else {
    /*@Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(SCHLUESSEL, datenAlsText());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        datenAusText(tag.getString(SCHLUESSEL));
    }
    *///?}
}
