package de.moinmornhart.stinkendreich.client.musik;

import de.moinmornhart.stinkendreich.Konfig;
import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.client.KompatClient;
import de.moinmornhart.stinkendreich.mechanik.Eigenschaften;
import de.moinmornhart.stinkendreich.musik.MusikZustand;
import de.moinmornhart.stinkendreich.musik.MusikanlageBlockEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Spielt die Musik der Anlagen in der Nähe ab, sobald man die passende Datei hat, und regelt die
 * Lautstärke nach dem Abstand. Wer DJ einer Anlage ist, startet am Titelende den nächsten Titel.
 */
public final class MusikKlient {
    /** Laufende Wiedergabe einer Anlage; {@code sound == null}: Datei fehlt bei uns. */
    private record Wiedergabe(int lauf, MusikSound sound) {
    }

    private static final Map<BlockPos, Wiedergabe> LAUFEND = new HashMap<>();
    /** Für welchen Lauf je Anlage der DJ schon den nächsten Titel geschickt hat. */
    private static final Map<BlockPos, Integer> WEITER_GESCHICKT = new HashMap<>();

    private MusikKlient() {
    }

    public static void registrieren() {
        ClientTickEvents.END_CLIENT_TICK.register(MusikKlient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            allesStoppen();
            SpotifyKlient.weltVerlassen();
        }));
    }

    private static void tick(Minecraft client) {
        if (client.level == null || client.player == null) {
            if (!LAUFEND.isEmpty()) {
                allesStoppen();
            }
            return;
        }
        Set<BlockPos> gesehen = new HashSet<>();
        for (BlockPos pos : MusikZustand.anlagen(client.level)) {
            if (client.level.getBlockEntity(pos) instanceof MusikanlageBlockEntity be) {
                gesehen.add(pos);
                abspielen(client, pos, be);
                djAufgabe(client, pos, be);
                noten(client, pos, be);
            }
        }
        // Abgebaute oder entladene Anlagen verstummen
        LAUFEND.entrySet().removeIf(e -> {
            if (!gesehen.contains(e.getKey())) {
                stoppen(client, e.getValue());
                return true;
            }
            return false;
        });
        SpotifyKlient.tick(client);
    }

    private static void abspielen(Minecraft client, BlockPos pos, MusikanlageBlockEntity be) {
        Wiedergabe w = LAUFEND.get(pos);
        double abstand = abstand(client, pos, be);
        boolean soll = be.spielt() && MusikanlageBlockEntity.DATEI.equals(be.quelle())
                && abstand <= Konfig.get().musikRadius + 4;
        if (!soll) {
            if (w != null) {
                stoppen(client, w);
                LAUFEND.remove(pos);
            }
            return;
        }
        float lautstaerke = (float) (be.lautstaerke() / 100.0 * abfall(abstand));
        if (w == null || w.lauf() != be.lauf()) {
            if (w != null) {
                stoppen(client, w);
            }
            Path datei = MusikDateien.finde(be.titel());
            double ab = Math.max(0, (client.level.getGameTime() - be.start()) / 20.0);
            MusikSound sound = null;
            if (datei != null && (be.dauer() <= 0 || ab < be.dauer() / 20.0 - 0.5)) {
                sound = new MusikSound(datei, ab, lautstaerke);
                client.getSoundManager().play(sound);
                StinkendReich.LOGGER.info("Musik startet: {} ab {} s", be.titel(), String.format(java.util.Locale.ROOT, "%.1f", ab));
            } else if (datei == null) {
                StinkendReich.LOGGER.info("Musik {} fehlt im eigenen Musikordner", be.titel());
            }
            LAUFEND.put(pos, new Wiedergabe(be.lauf(), sound));
            return;
        }
        if (w.sound() != null) {
            w.sound().zielLautstaerke(lautstaerke);
        }
    }

    /**
     * Solange eine Anlage spielt (auch Spotify), steigen über dem Pult und vor jedem Lautsprecher
     * bunte Noten auf. Regelmäßig im Client-Takt statt per Zufall, damit man sie sicher sieht.
     */
    private static void noten(Minecraft client, BlockPos pos, MusikanlageBlockEntity be) {
        if (!be.spielt() || Math.floorMod(client.level.getGameTime() + pos.hashCode(), 6) != 0
                || client.player.distanceToSqr(Vec3.atCenterOf(pos)) > 48 * 48) {
            return;
        }
        RandomSource zufall = client.level.getRandom();
        note(client, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, zufall);
        for (BlockPos box : be.lautsprecher()) {
            BlockState s = client.level.getBlockState(box);
            Direction vorne = s.hasProperty(Eigenschaften.RICHTUNG) ? s.getValue(Eigenschaften.RICHTUNG) : Direction.NORTH;
            note(client, box.getX() + 0.5 + vorne.getStepX() * 0.65, box.getY() + 0.5, box.getZ() + 0.5 + vorne.getStepZ() * 0.65, zufall);
        }
    }

    private static void note(Minecraft client, double x, double y, double z, RandomSource zufall) {
        // Beim Noten-Partikel bestimmt der erste Geschwindigkeitswert die Farbe (0–1)
        client.level.addParticle(ParticleTypes.NOTE, x + (zufall.nextDouble() - 0.5) * 0.4, y + zufall.nextDouble() * 0.3,
                z + (zufall.nextDouble() - 0.5) * 0.4, zufall.nextInt(25) / 24.0, 0.0, 0.0);
    }

    /** Abstand der Ohren zur Anlage oder zum nächsten ihrer Lautsprecher (Blöcke). */
    private static double abstand(Minecraft client, BlockPos pos, MusikanlageBlockEntity be) {
        Vec3 ohr = client.player.getEyePosition();
        double d = ohr.distanceToSqr(Vec3.atCenterOf(pos));
        for (BlockPos box : be.lautsprecher()) {
            d = Math.min(d, ohr.distanceToSqr(Vec3.atCenterOf(box)));
        }
        return Math.sqrt(d);
    }

    /** Volle Lautstärke im inneren Viertel der Hörweite, nach außen gleichmäßig leiser. */
    private static double abfall(double abstand) {
        double r = Math.max(1, Konfig.get().musikRadius);
        double voll = r * 0.25;
        return abstand <= voll ? 1.0 : Math.max(0.0, 1.0 - (abstand - voll) / (r - voll));
    }

    /** Der DJ startet am Ende des Titels den nächsten (aus seinem eigenen Musikordner). */
    private static void djAufgabe(Minecraft client, BlockPos pos, MusikanlageBlockEntity be) {
        if (!be.spielt() || !MusikanlageBlockEntity.DATEI.equals(be.quelle()) || be.dauer() <= 0
                || !client.player.getUUID().equals(be.dj()) || client.level.getGameTime() < be.start() + be.dauer()) {
            return;
        }
        if (Objects.equals(WEITER_GESCHICKT.get(pos), be.lauf())) {
            return;
        }
        WEITER_GESCHICKT.put(pos, be.lauf());
        wechseln(pos, be, 1, true, "musik_dj");
    }

    // ---------------- Steuerung aus dem Menü ----------------

    /** Startet einen bestimmten Titel; man wird damit DJ der Anlage. */
    public static void starten(BlockPos pos, int index, String titel) {
        starten(pos, index, titel, "musik");
    }

    /** Play-Knopf: aktuellen Titel (oder den ersten) neu starten. */
    public static void abspielen(BlockPos pos, MusikanlageBlockEntity be) {
        wechseln(pos, be, 0, false, "musik");
    }

    public static void weiter(BlockPos pos, MusikanlageBlockEntity be) {
        wechseln(pos, be, 1, false, "musik");
    }

    /** Wie bei echten Playern: nach drei Sekunden Laufzeit zurück an den Anfang, sonst voriger Titel. */
    public static void zurueck(BlockPos pos, MusikanlageBlockEntity be) {
        Minecraft client = Minecraft.getInstance();
        boolean schonWeit = be.spielt() && MusikanlageBlockEntity.DATEI.equals(be.quelle()) && client.level != null
                && client.level.getGameTime() - be.start() > 60;
        wechseln(pos, be, schonWeit ? 0 : -1, false, "musik");
    }

    private static void wechseln(BlockPos pos, MusikanlageBlockEntity be, int richtung, boolean automatisch, String art) {
        List<String> liste = MusikDateien.liste();
        int aktuell = liste.indexOf(be.titel());
        if (aktuell < 0) {
            aktuell = Math.min(be.index(), liste.size() - 1);
        }
        int i = MusikDateien.naechster(liste.size(), aktuell, richtung, be.zufall(), be.wiederholen(), automatisch);
        if (i < 0) {
            KompatClient.sende(art, pos, "stopp");
            return;
        }
        starten(pos, i, liste.get(i), art);
    }

    /** Länge im Hintergrund bestimmen (MP3 muss dafür einmal durchgezählt werden), dann starten. */
    private static void starten(BlockPos pos, int index, String titel, String art) {
        Path datei = MusikDateien.finde(titel);
        if (datei == null) {
            return;
        }
        CompletableFuture.supplyAsync(() -> MusikDateien.dauerTicks(datei)).thenAccept(dauer ->
                Minecraft.getInstance().execute(() -> KompatClient.sende(art, pos, "datei|" + index + "|" + dauer + "|" + titel)));
    }

    public static void stoppen(BlockPos pos) {
        KompatClient.sende("musik", pos, "stopp");
    }

    private static void stoppen(Minecraft client, Wiedergabe w) {
        if (w.sound() != null) {
            w.sound().beenden();
            client.getSoundManager().stop(w.sound());
        }
    }

    private static void allesStoppen() {
        Minecraft client = Minecraft.getInstance();
        LAUFEND.values().forEach(w -> stoppen(client, w));
        LAUFEND.clear();
        WEITER_GESCHICKT.clear();
    }

    /** Hört man an dieser Anlage gerade die Datei? (false = sie fehlt im eigenen Ordner) */
    public static boolean hoerbar(BlockPos pos) {
        Wiedergabe w = LAUFEND.get(pos);
        return w != null && w.sound() != null;
    }
}
