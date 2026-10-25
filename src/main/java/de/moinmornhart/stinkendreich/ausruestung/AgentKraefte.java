package de.moinmornhart.stinkendreich.ausruestung;

import de.moinmornhart.stinkendreich.ausruestung.AgentRuestung.Teil;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Die Kräfte des Agenten-Anzugs. Zweimal pro Sekunde bekommt jeder Spieler die Wirkungen der Teile, die er
 * trägt (kurz, damit sie nach dem Ausziehen schnell enden; ohne Partikel):
 * <ul>
 *   <li>Bond-Sonnenbrille: Nachtsicht, Gegner in 24 Blöcken leuchten durch Wände</li>
 *   <li>Smoking: Resistenz II, Feuerresistenz</li>
 *   <li>Anzughose: Schnelligkeit II</li>
 *   <li>Lackschuhe: Sprungkraft II, kein Fallschaden</li>
 *   <li>ganzes Set („Lizenz zum Töten“): Stärke II, Regeneration</li>
 * </ul>
 */
public final class AgentKraefte {
    /** Dauer der Wirkungen in Ticks (Nachtsicht länger, sonst flackert der Bildschirm). */
    private static final int DAUER = 40, NACHTSICHT = 300;
    private static final double AGENTENBLICK = 24.0;

    // Die Wirkungen heißen ab 1.21.5 anders
    //? if >=1.21.5 {
    public static final Holder<MobEffect> TEMPO = MobEffects.SPEED, STAERKE = MobEffects.STRENGTH, SPRUNG = MobEffects.JUMP_BOOST,
            RESISTENZ = MobEffects.RESISTANCE, LANGSAM = MobEffects.SLOWNESS;
    //?} else
    /*public static final Holder<MobEffect> TEMPO = MobEffects.MOVEMENT_SPEED, STAERKE = MobEffects.DAMAGE_BOOST, SPRUNG = MobEffects.JUMP, RESISTENZ = MobEffects.DAMAGE_RESISTANCE, LANGSAM = MobEffects.MOVEMENT_SLOWDOWN;*/

    private AgentKraefte() {
    }

    public static void registrieren() {
        ServerTickEvents.END_SERVER_TICK.register(AgentKraefte::tick);
        // Lackschuhe: kein Fallschaden
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((wesen, quelle, menge) ->
                !(quelle.is(DamageTypeTags.IS_FALL) && AgentRuestung.traegt(wesen, Teil.FUESSE)));
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) {
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator()) {
                continue;
            }
            if (AgentRuestung.traegt(p, Teil.KOPF)) {
                wirkung(p, MobEffects.NIGHT_VISION, 0, NACHTSICHT);
                agentenblick(p);
            } else {
                entfernen(p, MobEffects.NIGHT_VISION);
            }
            if (AgentRuestung.traegt(p, Teil.BRUST)) {
                wirkung(p, RESISTENZ, 1, DAUER);
                wirkung(p, MobEffects.FIRE_RESISTANCE, 0, DAUER);
            }
            if (AgentRuestung.traegt(p, Teil.BEINE)) {
                wirkung(p, TEMPO, 1, DAUER);
            }
            if (AgentRuestung.traegt(p, Teil.FUESSE)) {
                wirkung(p, SPRUNG, 1, DAUER);
            }
            if (AgentRuestung.ganzesSet(p)) {
                wirkung(p, STAERKE, 1, DAUER);
                // Regeneration braucht über 50 Ticks Restzeit, sonst heilt sie auf Stufe I nie
                wirkung(p, MobEffects.REGENERATION, 0, 80);
            }
        }
    }

    /** Wirkung ohne Partikel (als „Umgebungswirkung“ markiert, damit sie sich von Tränken unterscheidet). */
    private static void wirkung(LivingEntity e, Holder<MobEffect> art, int stufe, int dauer) {
        e.addEffect(new MobEffectInstance(art, dauer, stufe, true, false, true));
    }

    /** Entfernt eine Wirkung des Anzugs, aber keine aus einem Trank. */
    private static void entfernen(LivingEntity e, Holder<MobEffect> art) {
        MobEffectInstance w = e.getEffect(art);
        if (w != null && w.isAmbient() && !w.isVisible()) {
            e.removeEffect(art);
        }
    }

    /** Gegner in der Nähe leuchten kurz auf (durch Wände sichtbar). */
    private static void agentenblick(ServerPlayer p) {
        for (LivingEntity gegner : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(AGENTENBLICK),
                e -> e instanceof Enemy && e.isAlive())) {
            gegner.addEffect(new MobEffectInstance(MobEffects.GLOWING, DAUER, 0, true, false, false));
        }
    }
}
