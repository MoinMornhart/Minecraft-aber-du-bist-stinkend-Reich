package de.moinmornhart.stinkendreich.mechanik;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.register.ModBloecke;
import de.moinmornhart.stinkendreich.register.ModSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Magischer Eingang: Wer sich vor ein Geheimregal (z. B. am Eingang zum Geheimclub) stellt und kurz schleicht,
 * öffnet es – mit Funkeln, Zauberklang und Portal-Teilchen. Erlaubt ist das Bewohnern und Gästen mit
 * Zugangskarte (bei Stufe 0 allen). Nach 10 Sekunden schließt sich das Regal von selbst, sobald niemand mehr im
 * Durchgang steht. Der Schalter im Regal daneben funktioniert weiterhin.
 */
public final class Geheimgang {
    /** So oft wird nachgesehen (Ticks). */
    private static final int TAKT = 4;
    /** So viele Takte muss man schleichen, bis sich das Regal öffnet (gut eine halbe Sekunde). */
    private static final int SCHLEICHEN_TAKTE = 3;
    /** So lange bleibt ein magisch geöffnetes Regal mindestens offen (Ticks). */
    private static final int OFFEN_TICKS = 200;

    /** Wie lange jeder Spieler schon vor einem Regal schleicht (Takte). */
    private static final Map<UUID, Integer> schleichen = new HashMap<>();
    /** Magisch geöffnete Regale (ein Feld je Tür) und der Tick, an dem sie aufgingen. */
    private static final Map<BlockPos, Integer> offen = new HashMap<>();

    private Geheimgang() {
    }

    public static void registrieren() {
        ServerTickEvents.END_SERVER_TICK.register(Geheimgang::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            schleichen.clear();
            offen.clear();
        });
    }

    private static void tick(MinecraftServer server) {
        int jetzt = server.getTickCount();
        if (jetzt % TAKT != 0) {
            return;
        }
        ServerLevel welt = server.overworld();
        for (ServerPlayer p : welt.players()) {
            if (!p.isShiftKeyDown() || p.isSpectator()) {
                schleichen.remove(p.getUUID());
                continue;
            }
            BlockPos regal = regalVor(welt, p);
            if (regal == null) {
                schleichen.remove(p.getUUID());
                continue;
            }
            int takte = schleichen.merge(p.getUUID(), 1, Integer::sum);
            double cx = regal.getX() + 0.5, cy = regal.getY() + 0.5, cz = regal.getZ() + 0.5;
            if (takte < SCHLEICHEN_TAKTE) {
                // Erstes Funkeln: Zeichen fließen aus den Büchern zum Spieler
                welt.sendParticles(ParticleTypes.ENCHANT, cx, cy + 0.5, cz, 25, 0.6, 0.8, 0.6, 0.6);
                continue;
            }
            schleichen.remove(p.getUUID());
            VillaZustand z = VillaZustand.get(server);
            if (z.stufe() > 0 && !z.istBewohner(p) && !Zugang.hatKarte(p)) {
                Kompat.nachricht(p, Component.translatable("stinkendreich.geheimgang.nichts").withStyle(ChatFormatting.GRAY), true);
                continue;
            }
            if (TorsteuerungBlock.umschalten(welt, regal, 4, ModBloecke.GEHEIMREGAL, true)) {
                offen.put(regal.immutable(), jetzt);
                welt.sendParticles(ParticleTypes.ENCHANT, cx, cy + 0.5, cz, 120, 0.8, 1.2, 0.8, 1.0);
                welt.sendParticles(ParticleTypes.PORTAL, cx, cy + 0.5, cz, 80, 0.6, 1.0, 0.6, 0.4);
                welt.sendParticles(ParticleTypes.WITCH, cx, cy + 1.0, cz, 20, 0.6, 0.8, 0.6, 0.05);
                welt.playSound(null, regal, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.2F, 0.9F);
                welt.playSound(null, regal, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.5F, 0.7F);
                welt.playSound(null, regal, ModSounds.TRESOR, SoundSource.BLOCKS, 0.6F, 1.4F);
                Kompat.nachricht(p, Component.translatable("stinkendreich.geheimgang.offen").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
        }
        zuMachen(welt, jetzt);
    }

    /** Ein geschlossenes Geheimregal direkt vor dem Spieler (bis 2 Blöcke weit, Kopf- bis Fußhöhe) oder null. */
    private static BlockPos regalVor(ServerLevel welt, Player p) {
        BlockPos fuss = p.blockPosition();
        BlockPos naechstes = null;
        double best = Double.MAX_VALUE;
        for (BlockPos q : BlockPos.betweenClosed(fuss.offset(-2, 0, -2), fuss.offset(2, 2, 2))) {
            BlockState s = welt.getBlockState(q);
            if (s.is(ModBloecke.GEHEIMREGAL) && !s.getValue(Eigenschaften.OFFEN)) {
                double d = q.distToCenterSqr(p.getX(), p.getY() + 1.0, p.getZ());
                if (d < best) {
                    best = d;
                    naechstes = q.immutable();
                }
            }
        }
        return naechstes;
    }

    /** Schließt magisch geöffnete Regale nach Ablauf der Zeit, wenn niemand mehr im Durchgang steht. */
    private static void zuMachen(ServerLevel welt, int jetzt) {
        for (Iterator<Map.Entry<BlockPos, Integer>> it = offen.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockPos, Integer> e = it.next();
            BlockPos pos = e.getKey();
            if (jetzt - e.getValue() < OFFEN_TICKS || !welt.isLoaded(pos)) {
                continue;
            }
            if (!welt.getEntitiesOfClass(Player.class, new AABB(pos).inflate(1.5, 1.0, 1.5), q -> !q.isSpectator()).isEmpty()) {
                continue;
            }
            if (TorsteuerungBlock.umschalten(welt, pos, 4, ModBloecke.GEHEIMREGAL, false)) {
                welt.playSound(null, pos, ModSounds.TRESOR, SoundSource.BLOCKS, 0.6F, 1.2F);
                welt.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 40, 0.6, 1.0, 0.6, 0.6);
            }
            it.remove();
        }
    }
}
