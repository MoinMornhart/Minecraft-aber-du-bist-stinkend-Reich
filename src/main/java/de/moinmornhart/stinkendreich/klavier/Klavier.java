package de.moinmornhart.stinkendreich.klavier;

import de.moinmornhart.stinkendreich.netz.Netz;
import de.moinmornhart.stinkendreich.register.ModDeko;
import de.moinmornhart.stinkendreich.register.ModSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Spielt den Konzertflügel: einzelne Töne aus dem Spielfenster („t|MIDI-Nummer“) und ganze Stücke („l|Name“,
 * „s“ hält an). Alle Spieler in der Nähe hören es (der Ton kommt vom Flügel). Die Töne sind fünf selbst erzeugte
 * Grundtöne C2 … C6; jede andere Taste spielt den nächsten Grundton mit verschobener Tonhöhe.
 */
public final class Klavier {
    /** Tastenbereich des Spielfensters (C3 … C6). */
    public static final int TIEFSTE = 48, HOECHSTE = 84;
    /** Lautstärke 2: im ganzen Ballsaal zu hören (rund 32 Blöcke weit). */
    private static final float LAUTSTAERKE = 2.0F;

    /** Laufende Stücke je Flügel. */
    private static final Map<BlockPos, Vortrag> VORTRAEGE = new HashMap<>();

    private record Vortrag(ServerLevel welt, int[][] noten, int[] stand) {
    }

    private Klavier() {
    }

    public static void registrieren() {
        Netz.bearbeiter("klavier", 10, (spieler, pos, wert) -> {
            if (!(spieler.level() instanceof ServerLevel welt) || !istFluegel(welt, pos)) {
                return;
            }
            if (wert.startsWith("t|")) {
                try {
                    spielen(welt, pos, Integer.parseInt(wert.substring(2)));
                } catch (NumberFormatException e) {
                    // kaputte Nachricht: nichts spielen
                }
            } else if (wert.startsWith("l|")) {
                int[][] stueck = Lieder.stueck(wert.substring(2));
                if (stueck != null) {
                    VORTRAEGE.put(pos.immutable(), new Vortrag(welt, stueck, new int[]{0, 0}));
                }
            } else if (wert.equals("s")) {
                VORTRAEGE.remove(pos);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(Klavier::tick);
    }

    /** Ein Ton am Flügel (MIDI-Nummer, 60 = C4), mit einer Musiknote über dem Deckel. */
    public static void spielen(ServerLevel welt, BlockPos pos, int note) {
        int n = Math.max(24, Math.min(96, note));
        int grund = Math.max(0, Math.min(4, Math.round((n - 36) / 12.0F)));
        float hoehe = (float) Math.pow(2.0, (n - (36 + grund * 12)) / 12.0);
        welt.playSound(null, pos, ModSounds.KLAVIER[grund], SoundSource.RECORDS, LAUTSTAERKE, Math.max(0.5F, Math.min(2.0F, hoehe)));
        // Musiknote: Farbe wie beim Notenblock (0 … 1 über zwei Oktaven)
        welt.sendParticles(ParticleTypes.NOTE, pos.getX() + 0.5, pos.getY() + 1.6, pos.getZ() + 0.5, 0,
                ((n - TIEFSTE) % 24) / 24.0, 0.0, 0.0, 1.0);
    }

    private static void tick(MinecraftServer server) {
        Iterator<Map.Entry<BlockPos, Vortrag>> it = VORTRAEGE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Vortrag> e = it.next();
            Vortrag v = e.getValue();
            if (!istFluegel(v.welt(), e.getKey())) {
                it.remove();
                continue;
            }
            int zeit = v.stand()[0]++;
            while (v.stand()[1] < v.noten().length && v.noten()[v.stand()[1]][0] <= zeit) {
                spielen(v.welt(), e.getKey(), v.noten()[v.stand()[1]][1]);
                v.stand()[1]++;
            }
            if (v.stand()[1] >= v.noten().length && zeit > v.noten()[v.noten().length - 1][0] + 40) {
                it.remove();
            }
        }
    }

    private static boolean istFluegel(BlockGetter welt, BlockPos pos) {
        Block b = welt.getBlockState(pos).getBlock();
        return b == ModDeko.BLOECKE.get("konzertfluegel");
    }

    /** Der mittlere Block des Flügels zu einem angeklickten Teil (sonst die Stelle selbst). */
    public static BlockPos mitte(BlockGetter welt, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 0, 1))) {
            if (istFluegel(welt, p)) {
                return p.immutable();
            }
        }
        return pos.immutable();
    }
}
