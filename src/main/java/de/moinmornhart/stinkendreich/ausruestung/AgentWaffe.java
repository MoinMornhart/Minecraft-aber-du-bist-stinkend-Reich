package de.moinmornhart.stinkendreich.ausruestung;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.auto.AutoEntity;
import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import de.moinmornhart.stinkendreich.register.ModSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Die Agenten-Waffen. Alle feuern per Rechtsklick ohne Munition und treffen sofort (Strahl vom Auge in
 * Blickrichtung); Blöcke beschädigen sie nicht, Butler, Fahrzeuge und Rüstungsständer bleiben verschont.
 * <ul>
 *   <li>Goldene Pistole: 8 Schaden, 64 Blöcke weit, schnell nachladen</li>
 *   <li>Laser-Armbanduhr: 12 Schaden, setzt das Ziel 5 Sekunden in Brand</li>
 *   <li>Geldkanone: Geldregen in einem Kegel (12 Blöcke), schleudert Gegner weg, macht sie langsam und blind</li>
 * </ul>
 */
public class AgentWaffe extends Item {
    public enum Art {
        PISTOLE(8.0F, 64.0, 6), LASER(12.0F, 48.0, 25), GELDKANONE(3.0F, 12.0, 30);

        final float schaden;
        final double reichweite;
        final int nachladen;

        Art(float schaden, double reichweite, int nachladen) {
            this.schaden = schaden;
            this.reichweite = reichweite;
            this.nachladen = nachladen;
        }
    }

    private final Art art;

    public AgentWaffe(Properties eigenschaften, Art art) {
        super(eigenschaften);
        this.art = art;
    }

    private void feuern(Level welt, Player spieler, ItemStack stapel) {
        //? if >=1.21.2 {
        spieler.getCooldowns().addCooldown(stapel, art.nachladen);
        //?} else
        /*spieler.getCooldowns().addCooldown(stapel.getItem(), art.nachladen);*/
        if (!(welt instanceof ServerLevel sl)) {
            return;
        }
        Vec3 auge = spieler.getEyePosition();
        Vec3 blick = spieler.getViewVector(1.0F);
        // Mündung: etwas vor und unter dem Auge, auf der Seite der Hand
        Vec3 rechts = new Vec3(-blick.z, 0, blick.x).normalize().scale(0.3);
        Vec3 muendung = auge.add(blick.scale(0.6)).add(rechts).add(0, -0.25, 0);
        switch (art) {
            case PISTOLE -> {
                ton(sl, spieler, ModSounds.SCHUSS, 1.1F);
                Treffer t = strahl(sl, spieler, auge, blick, art.reichweite);
                linie(sl, muendung, t.ende, ParticleTypes.WAX_ON, 0.5);   // goldene Leuchtspur
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, t.ende.x, t.ende.y, t.ende.z, 8, 0.1, 0.1, 0.1, 0.2);
                if (t.ziel != null) {
                    Kompat.treffen(t.ziel, sl, sl.damageSources().playerAttack(spieler), art.schaden);
                    stossen(t.ziel, blick, 0.3);
                }
            }
            case LASER -> {
                ton(sl, spieler, ModSounds.LASER, 1.0F);
                Treffer t = strahl(sl, spieler, auge, blick, art.reichweite);
                linie(sl, muendung, t.ende, DustParticleOptions.REDSTONE, 0.25);
                sl.sendParticles(ParticleTypes.LAVA, t.ende.x, t.ende.y, t.ende.z, 3, 0.1, 0.1, 0.1, 0.0);
                sl.sendParticles(ParticleTypes.SMOKE, t.ende.x, t.ende.y, t.ende.z, 6, 0.1, 0.1, 0.1, 0.02);
                if (t.ziel != null) {
                    Kompat.treffen(t.ziel, sl, sl.damageSources().playerAttack(spieler), art.schaden);
                    t.ziel.igniteForSeconds(5.0F);
                }
            }
            case GELDKANONE -> {
                ton(sl, spieler, ModSounds.GELDREGEN, 1.0F);
                geldregen(sl, spieler, muendung, blick);
            }
        }
    }

    /** Geldscheine (grün) und Goldmünzen fliegen in einem Kegel; Gegner darin werden weggeschleudert. */
    private void geldregen(ServerLevel sl, Player spieler, Vec3 muendung, Vec3 blick) {
        var zufall = sl.getRandom();
        // Geldscheine (Smaragde) und Goldmünzen fliegen aus dem Rohr
        ParticleOptions schein = geldstueck(Items.EMERALD), muenze = geldstueck(Items.GOLD_NUGGET);
        for (int i = 0; i < 40; i++) {
            Vec3 r = blick.add((zufall.nextDouble() - 0.5) * 0.5, (zufall.nextDouble() - 0.5) * 0.4 + 0.1, (zufall.nextDouble() - 0.5) * 0.5).normalize();
            double tempo = 0.5 + zufall.nextDouble() * 0.6;
            // Anzahl 0: die drei Werte sind dann die Flugrichtung
            sl.sendParticles(i % 2 == 0 ? schein : muenze, muendung.x, muendung.y, muendung.z, 0, r.x, r.y, r.z, tempo);
        }
        for (int i = 0; i < 70; i++) {
            Vec3 r = blick.add((zufall.nextDouble() - 0.5) * 0.7, (zufall.nextDouble() - 0.5) * 0.7, (zufall.nextDouble() - 0.5) * 0.7).normalize();
            Vec3 p = muendung.add(r.scale(0.5 + zufall.nextDouble() * art.reichweite));
            sl.sendParticles(i % 3 == 0 ? ParticleTypes.WAX_ON : ParticleTypes.HAPPY_VILLAGER, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
        AABB raum = spieler.getBoundingBox().inflate(art.reichweite);
        for (Entity e : sl.getEntities(spieler, raum, AgentWaffe::zielbar)) {
            Vec3 zu = e.getBoundingBox().getCenter().subtract(spieler.getEyePosition());
            double abstand = zu.length();
            if (abstand > art.reichweite || zu.normalize().dot(blick) < Math.cos(Math.toRadians(25))) {
                continue;
            }
            Kompat.treffen(e, sl, sl.damageSources().playerAttack(spieler), art.schaden);
            stossen(e, blick, 1.6 * (1.0 - abstand / art.reichweite) + 0.4);
            if (e instanceof LivingEntity l) {
                l.addEffect(new MobEffectInstance(AgentKraefte.LANGSAM, 80, 2));
                l.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
            }
        }
    }

    /** Teilchen eines Gegenstands (ab 26.1 aus dem Item statt aus einem Stapel). */
    private static ParticleOptions geldstueck(Item item) {
        //? if >=26.1 {
        return new ItemParticleOption(ParticleTypes.ITEM, item);
        //?} else
        /*return new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(item));*/
    }

    private record Treffer(Vec3 ende, Entity ziel) {
    }

    /** Sofort-Treffer: erster Block oder erste Figur auf der Linie vom Auge in Blickrichtung. */
    private static Treffer strahl(ServerLevel sl, Player spieler, Vec3 auge, Vec3 blick, double reichweite) {
        Vec3 ende = auge.add(blick.scale(reichweite));
        HitResult block = sl.clip(new ClipContext(auge, ende, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, spieler));
        if (block.getType() != HitResult.Type.MISS) {
            ende = block.getLocation();
        }
        EntityHitResult figur = ProjectileUtil.getEntityHitResult(sl, spieler, auge, ende, new AABB(auge, ende).inflate(1.0),
                AgentWaffe::zielbar, 0.3F);
        return figur != null ? new Treffer(figur.getLocation(), figur.getEntity()) : new Treffer(ende, null);
    }

    /** Lebewesen, die getroffen werden dürfen (keine Butler, Fahrzeuge, Rüstungsständer, Zuschauer). */
    private static boolean zielbar(Entity e) {
        return e instanceof LivingEntity && e.isAlive() && e.isPickable() && !e.isSpectator()
                && !(e instanceof ButlerEntity) && !(e instanceof AutoEntity) && !(e instanceof ArmorStand);
    }

    private static void linie(ServerLevel sl, Vec3 von, Vec3 bis, ParticleOptions teilchen, double schritt) {
        Vec3 weg = bis.subtract(von);
        int n = Mth.clamp((int) (weg.length() / schritt), 1, 300);
        for (int i = 0; i <= n; i++) {
            Vec3 p = von.add(weg.scale((double) i / n));
            sl.sendParticles(teilchen, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    private static void stossen(Entity e, Vec3 richtung, double staerke) {
        e.push(richtung.x * staerke, 0.25 * staerke, richtung.z * staerke);
        // Bewegung sofort an die Rechner schicken (Feld heißt ab 26.3 anders)
        //? if >=26.3 {
        e.needsSync = true;
        //?} else
        /*e.hurtMarked = true;*/
    }

    private static void ton(ServerLevel sl, Player spieler, SoundEvent ton, float hoehe) {
        sl.playSound(null, spieler.getX(), spieler.getY(), spieler.getZ(), ton, SoundSource.PLAYERS, 1.0F,
                hoehe * (0.95F + sl.getRandom().nextFloat() * 0.1F));
    }

    //? if >=1.21.2 {
    @Override
    public InteractionResult use(Level welt, Player spieler, InteractionHand hand) {
        feuern(welt, spieler, spieler.getItemInHand(hand));
        return InteractionResult.SUCCESS;
    }
    //?} else {
    /*@Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level welt, Player spieler, InteractionHand hand) {
        feuern(welt, spieler, spieler.getItemInHand(hand));
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(spieler.getItemInHand(hand), welt.isClientSide());
    }
    *///?}
}
