package de.moinmornhart.stinkendreich.fernseher;

import de.moinmornhart.stinkendreich.mechanik.KlientBruecke;
import de.moinmornhart.stinkendreich.netz.Netz;
import de.moinmornhart.stinkendreich.netz.OhneMod;
import de.moinmornhart.stinkendreich.register.ModItems;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
//? if >=1.21.6 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?} else {
/*import net.minecraft.nbt.CompoundTag;
*///?}

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XXL-Fernseher (5 × 2,8 Blöcke) an der Wand. Rechtsklick öffnet das Menü für einen YouTube-Link.
 * Gespeichert und an alle Spieler verteilt werden nur die Video-Kennung (11 Zeichen) und der Stand der Wiedergabe
 * (läuft seit Spielzeit X / angehalten bei Position Y). Jeder Klient zeigt daraus selbst Vorschaubild oder Video
 * (siehe {@code FernseherVideos}), so sehen alle dieselbe Stelle. Auf Wunsch läuft das Video im Browser des PCs.
 * Schleichen + Rechtsklick im Kreativmodus baut ihn ab.
 */
public class FernseherEntity extends Mob {
    private static final EntityDataAccessor<String> VIDEO = SynchedEntityData.defineId(FernseherEntity.class, EntityDataSerializers.STRING);
    /** Wiedergabe: "" = keine, "L&lt;Tick&gt;" = läuft (Position 0 war bei diesem Spielzeit-Tick), "P&lt;Ticks&gt;" = angehalten. */
    private static final EntityDataAccessor<String> WIEDERGABE = SynchedEntityData.defineId(FernseherEntity.class, EntityDataSerializers.STRING);
    private static final Pattern KENNUNG = Pattern.compile("(?:v=|youtu\\.be/|shorts/|embed/|live/)([A-Za-z0-9_-]{11})");
    private static final Pattern NUR_KENNUNG = Pattern.compile("[A-Za-z0-9_-]{11}");

    public FernseherEntity(EntityType<? extends FernseherEntity> typ, Level welt) {
        super(typ, welt);
        setPersistenceRequired();
        setNoAi(true);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder attribute() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    /** Nimmt die Aktion „fernseher“ vom Menü entgegen: „&lt;Entity-ID&gt;|&lt;Befehl&gt;“ (siehe {@link #befehl}). */
    public static void registrieren() {
        Netz.bearbeiter("fernseher", 10, (spieler, pos, wert) -> {
            int trenner = wert.indexOf('|');
            if (trenner < 0) {
                return;
            }
            try {
                if (spieler.level().getEntity(Integer.parseInt(wert.substring(0, trenner))) instanceof FernseherEntity tv
                        && tv.distanceToSqr(spieler) < 16 * 16) {
                    tv.befehl(wert.substring(trenner + 1));
                }
            } catch (NumberFormatException ignoriert) {
                // ungültige Nachricht
            }
        });
    }

    /** Video-Kennung aus einem YouTube-Link (watch?v=, youtu.be/, shorts/ …) oder "" wenn keiner. */
    public static String kennung(String link) {
        String t = link == null ? "" : link.trim();
        if (NUR_KENNUNG.matcher(t).matches()) {
            return t;
        }
        Matcher m = KENNUNG.matcher(t);
        return t.contains("youtu") && m.find() ? m.group(1) : "";
    }

    public String video() {
        return entityData.get(VIDEO);
    }

    /**
     * Befehl aus dem Menü: „!abspielen|Link“ (Link leer = aktuelles Video von vorn), „!pause“, „!weiter“,
     * „!spulen|Sekunden“ (auch negativ), „!stopp“. Alles andere ist ein Link: Vorschaubild zeigen, nicht abspielen
     * (leer = ausschalten).
     */
    public void befehl(String befehl) {
        long jetzt = level().getGameTime();
        String[] teile = befehl.split("\\|", 2);
        String rest = teile.length > 1 ? teile[1] : "";
        switch (teile[0]) {
            case "!abspielen" -> {
                String k = kennung(rest);
                if (k.isEmpty()) {
                    k = video();
                }
                if (!k.isEmpty()) {
                    setzeVideo(k);
                    entityData.set(WIEDERGABE, "L" + jetzt);
                }
            }
            case "!pause" -> {
                if (laeuft()) {
                    entityData.set(WIEDERGABE, "P" + positionTicks(jetzt));
                }
            }
            case "!weiter" -> {
                if (angehalten()) {
                    entityData.set(WIEDERGABE, "L" + (jetzt - positionTicks(jetzt)));
                }
            }
            case "!spulen" -> {
                try {
                    long neu = Math.max(0, positionTicks(jetzt) + Long.parseLong(rest.trim()) * 20);
                    if (laeuft()) {
                        entityData.set(WIEDERGABE, "L" + (jetzt - neu));
                    } else if (angehalten()) {
                        entityData.set(WIEDERGABE, "P" + neu);
                    }
                } catch (NumberFormatException ignoriert) {
                    // ungültige Sekundenzahl
                }
            }
            case "!stopp" -> entityData.set(WIEDERGABE, "");
            default -> {
                setzeVideo(kennung(befehl));
                entityData.set(WIEDERGABE, "");
            }
        }
    }

    public String wiedergabe() {
        return entityData.get(WIEDERGABE);
    }

    /** Läuft gerade ein Video? */
    public boolean laeuft() {
        return wiedergabe().startsWith("L") && !video().isEmpty();
    }

    /** Ist ein Video angehalten (Standbild)? */
    public boolean angehalten() {
        return wiedergabe().startsWith("P") && !video().isEmpty();
    }

    /** Abspielposition in Spielticks zur angegebenen Spielzeit (0, wenn nichts läuft). */
    public long positionTicks(long spielzeit) {
        String w = wiedergabe();
        try {
            if (w.startsWith("L")) {
                return Math.max(0, spielzeit - Long.parseLong(w.substring(1)));
            }
            if (w.startsWith("P")) {
                return Math.max(0, Long.parseLong(w.substring(1)));
            }
        } catch (NumberFormatException ignoriert) {
            // beschädigter Wert: wie „keine Wiedergabe“
        }
        return 0;
    }

    public void setzeVideo(String kennung) {
        entityData.set(VIDEO, kennung == null ? "" : kennung);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder daten) {
        super.defineSynchedData(daten);
        daten.define(VIDEO, "");
        daten.define(WIEDERGABE, "");
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        yBodyRot = getYRot();
        yHeadRot = getYRot();
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
    protected InteractionResult mobInteract(Player spieler, InteractionHand hand) {
        if (spieler.isShiftKeyDown() && spieler.isCreative()) {
            if (!level().isClientSide()) {
                Block.popResource(level(), blockPosition(), new ItemStack(ModItems.FERNSEHER));
                discard();
            }
            return InteractionResult.SUCCESS;
        }
        if (level().isClientSide()) {
            KlientBruecke.fernseher.accept(getId());
        } else {
            // Spieler ohne Mod (Server-Version): das Menü als Dialogfenster
            OhneMod.zeigen(spieler, "fernseher", blockPosition());
        }
        return InteractionResult.SUCCESS;
    }

    //? if >=1.21.6 {
    @Override
    protected void addAdditionalSaveData(ValueOutput ausgabe) {
        super.addAdditionalSaveData(ausgabe);
        ausgabe.putString("stinkendreich_video", video());
        ausgabe.putString("stinkendreich_wiedergabe", wiedergabe());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput eingabe) {
        super.readAdditionalSaveData(eingabe);
        setzeVideo(kennung(eingabe.getStringOr("stinkendreich_video", "")));
        entityData.set(WIEDERGABE, eingabe.getStringOr("stinkendreich_wiedergabe", ""));
    }
    //?} else if >=1.21.5 {
    /*@Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("stinkendreich_video", video());
        tag.putString("stinkendreich_wiedergabe", wiedergabe());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setzeVideo(kennung(tag.getStringOr("stinkendreich_video", "")));
        entityData.set(WIEDERGABE, tag.getStringOr("stinkendreich_wiedergabe", ""));
    }
    *///?} else {
    /*@Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("stinkendreich_video", video());
        tag.putString("stinkendreich_wiedergabe", wiedergabe());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setzeVideo(kennung(tag.getString("stinkendreich_video")));
        entityData.set(WIEDERGABE, tag.getString("stinkendreich_wiedergabe"));
    }
    *///?}
}
