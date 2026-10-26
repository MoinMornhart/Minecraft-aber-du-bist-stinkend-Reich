package de.moinmornhart.stinkendreich.mechanik;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.StinkendReich;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gemeinsamer Zustand des Sicherheitssystems einer Welt: Sicherheitsstufe, Bewohner, Alarm,
 * entschärfte Tresorfallen. Wird im Weltordner als {@code stinkendreich-zustand.json} gespeichert.
 *
 * <p>Stufen: 0 = aus, 1 = Alarm bei Eindringlingen, 2 = zusätzlich Lockdown (Rollläden, Tor zu),
 * 3 = zusätzlich Laser, 4 = Festung (totaler Lockdown): Zugbrücke eingefahren, Minen scharf, Abwehrgeschütze
 * schießen auf Monster und Eindringlinge. Stufe 4 lässt sich nur an der Zentrale im Bunker einschalten.</p>
 */
public final class VillaZustand {
    public static final int MAX_STUFE = 4;
    /** Ab dieser Stufe ist die Villa eine Festung (siehe {@link Festung}). */
    public static final int FESTUNG = 4;
    private static final String DATEI = StinkendReich.MOD_ID + "-zustand.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static VillaZustand aktuell;

    private int stufe = 1;
    private final Map<UUID, String> bewohner = new LinkedHashMap<>();
    private long alarmBis;
    /** Spielzeit, zu der die Festung eingeschaltet wurde (für die Animation; nicht gespeichert, 0 = schon lange an). */
    private long festungSeit;
    /** Einstellungen der Smart-Home-App (Rollläden, Licht, Farbe, Disco, Automatik), siehe {@link SmartHome}. */
    private JsonObject smartHome = new JsonObject();
    private long tresorEntschaerftBis;
    /** Wann welcher Entschärfknopf zuletzt gedrückt wurde (nicht gespeichert). */
    private final Map<BlockPos, Long> entschaerfDruecke = new HashMap<>();
    /** Zugbrücke gesichert (eingefahren, nur für Bewohner und Kartenbesitzer heraus)? Siehe {@link Zugbruecke}. */
    private boolean brueckeGesichert;
    /** Zugbrücke eingefahren? Dann stehen in {@link #brueckeBloecke} die Blöcke, die beim Ausfahren zurückkommen. */
    private boolean brueckeEingefahren;
    /** Blöcke der eingefahrenen Brücke: „x y z|Blockzustand“ in Weltkoordinaten (siehe {@link Zugbruecke}). */
    private final java.util.List<String> brueckeBloecke = new java.util.ArrayList<>();
    private Path pfad;
    private MinecraftServer server;

    private VillaZustand() {
    }

    public static void registrieren() {
        ServerLifecycleEvents.SERVER_STARTED.register(VillaZustand::laden);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            if (aktuell != null) {
                aktuell.speichern();
            }
            aktuell = null;
        });
        // Der erste Spieler einer Welt wird automatisch Bewohner (Besitzer)
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            VillaZustand z = get(server);
            if (z.bewohner.isEmpty()) {
                z.bewohnerHinzufuegen(handler.getPlayer());
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (aktuell != null && aktuell.alarmBis > 0 && jetzt(s) > aktuell.alarmBis) {
                aktuell.alarmBis = 0;
                aktuell.anBewohner(Component.translatable("stinkendreich.alarm.ende").withStyle(ChatFormatting.GREEN), true);
            }
        });
    }

    public static VillaZustand get(MinecraftServer server) {
        if (aktuell == null || aktuell.server != server) {
            laden(server);
        }
        return aktuell;
    }

    private static void laden(MinecraftServer server) {
        VillaZustand z = new VillaZustand();
        z.server = server;
        z.pfad = server.getWorldPath(LevelResource.ROOT).resolve(DATEI);
        try {
            if (Files.exists(z.pfad)) {
                JsonObject j = GSON.fromJson(Files.readString(z.pfad, StandardCharsets.UTF_8), JsonObject.class);
                z.stufe = j.has("stufe") ? j.get("stufe").getAsInt() : 1;
                z.brueckeGesichert = j.has("bruecke_gesichert") && j.get("bruecke_gesichert").getAsBoolean();
                z.brueckeEingefahren = j.has("bruecke_eingefahren") && j.get("bruecke_eingefahren").getAsBoolean();
                if (j.has("smarthome")) {
                    z.smartHome = j.getAsJsonObject("smarthome");
                }
                if (j.has("bruecke_bloecke")) {
                    j.getAsJsonArray("bruecke_bloecke").forEach(e -> z.brueckeBloecke.add(e.getAsString()));
                }
                if (j.has("bewohner")) {
                    for (var e : j.getAsJsonArray("bewohner")) {
                        JsonObject b = e.getAsJsonObject();
                        z.bewohner.put(UUID.fromString(b.get("uuid").getAsString()), b.get("name").getAsString());
                    }
                }
            }
        } catch (Exception e) {
            StinkendReich.LOGGER.error("Sicherheitszustand konnte nicht gelesen werden", e);
        }
        aktuell = z;
    }

    private void speichern() {
        try {
            JsonObject j = new JsonObject();
            j.addProperty("stufe", stufe);
            JsonArray liste = new JsonArray();
            bewohner.forEach((uuid, name) -> {
                JsonObject b = new JsonObject();
                b.addProperty("uuid", uuid.toString());
                b.addProperty("name", name);
                liste.add(b);
            });
            j.add("bewohner", liste);
            j.addProperty("bruecke_gesichert", brueckeGesichert);
            j.addProperty("bruecke_eingefahren", brueckeEingefahren);
            JsonArray bloecke = new JsonArray();
            brueckeBloecke.forEach(bloecke::add);
            j.add("bruecke_bloecke", bloecke);
            j.add("smarthome", smartHome);
            Files.writeString(pfad, GSON.toJson(j), StandardCharsets.UTF_8);
        } catch (Exception e) {
            StinkendReich.LOGGER.error("Sicherheitszustand konnte nicht gespeichert werden", e);
        }
    }

    private static long jetzt(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    // ---------------- Stufe ----------------

    public int stufe() {
        return stufe;
    }

    /** Totaler Lockdown: Minen und Abwehrgeschütze scharf, Zugbrücke bleibt für Fremde eingefahren. */
    public boolean festung() {
        return stufe >= FESTUNG;
    }

    public void setzeStufe(int neu) {
        boolean warFestung = festung();
        stufe = Math.max(0, Math.min(MAX_STUFE, neu));
        speichern();
        if (festung() && !warFestung) {
            // Festung: Sirenen und Warnleuchten im ganzen Haus, Geschütze, Laser und Minen fahren nacheinander hoch
            // (siehe Festung.verzoegerung). Nur über der Hotbar, nichts im Chat.
            festungSeit = jetzt(server);
            alarmBis = festungSeit + 20 * 12;
            anBewohner(Component.translatable("stinkendreich.festung.an").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
            return;
        }
        anBewohner(Component.translatable("stinkendreich.stufe.gesetzt",
                Component.translatable("stinkendreich.stufe." + stufe)).withStyle(ChatFormatting.AQUA), true);
    }

    /** Ein Schalter der Smart-Home-App (Standard, wenn noch nie gesetzt). */
    public boolean smart(String name, boolean standard) {
        return smartHome.has(name) ? smartHome.get(name).getAsBoolean() : standard;
    }

    public String smartText(String name, String standard) {
        return smartHome.has(name) ? smartHome.get(name).getAsString() : standard;
    }

    public void setzeSmart(String name, boolean wert) {
        smartHome.addProperty(name, wert);
        speichern();
    }

    public void setzeSmart(String name, String wert) {
        smartHome.addProperty(name, wert);
        speichern();
    }

    /** Ticks seit dem Einschalten der Festung (groß, wenn sie schon vor dem Laden der Welt an war). */
    public long festungTicks() {
        return festungSeit == 0 ? Long.MAX_VALUE / 2 : jetzt(server) - festungSeit;
    }

    // ---------------- Bewohner ----------------

    /**
     * Darf der Spieler alles, was Bewohner dürfen? Eingetragene Bewohner und jeder mit Zugangskarte im Inventar
     * (Philip: „jeder, der eine Zugangskarte hat, ist als Bewohner“). Nur die Alarmstufe stellt der Eigentümer ein.
     */
    public boolean istBewohner(Player spieler) {
        return bewohner.isEmpty() || bewohner.containsKey(spieler.getUUID()) || Zugang.hatKarte(spieler);
    }

    /** Eigentümer der Villa: der erste Bewohner (der erste Spieler, der die Welt betreten hat). */
    public boolean istEigentuemer(Player spieler) {
        return bewohner.isEmpty() || bewohner.keySet().iterator().next().equals(spieler.getUUID());
    }

    public void bewohnerHinzufuegen(Player spieler) {
        bewohner.put(spieler.getUUID(), spieler.getScoreboardName());
        speichern();
    }

    public Map<UUID, String> bewohner() {
        return bewohner;
    }

    /**
     * Ist diese Entity ein Eindringling? Feindliche Monster immer, Spieler nur,
     * wenn sie keine Bewohner sind und keine Zugangskarte dabeihaben.
     */
    public boolean istEindringling(Entity e) {
        if (e instanceof Player p) {
            return !p.isSpectator() && !p.isCreative() && !istBewohner(p);
        }
        return e instanceof Enemy;
    }

    // ---------------- Alarm ----------------

    public boolean alarmAktiv() {
        return alarmBis > 0;
    }

    /** Löst Alarm aus (hält 30 Sekunden, verlängert sich bei weiteren Auslösungen). */
    public void alarm(BlockPos ort, Component grund) {
        boolean neu = alarmBis == 0;
        alarmBis = jetzt(server) + 20 * 30;
        if (neu) {
            StinkendReich.LOGGER.info("Alarm bei {}: {}", ort.toShortString(), grund.getString());
            // In der Festung nur über der Hotbar (Philip: „im Chat soll nichts kommen“)
            anBewohner(Component.translatable("stinkendreich.alarm.start", grund, ort.getX(), ort.getY(), ort.getZ())
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), festung());
        }
    }

    public void alarmAus() {
        alarmBis = 0;
    }

    // ---------------- Tresor ----------------

    public boolean tresorEntschaerft() {
        return jetzt(server) < tresorEntschaerftBis;
    }

    /**
     * Ein Entschärfknopf wurde gedrückt. Erst wenn {@code benoetigt} verschiedene Knöpfe innerhalb
     * von 15 Sekunden gedrückt wurden, sind die Fallen für 60 Sekunden entschärft.
     *
     * @return wie viele Knöpfe noch fehlen (0 = entschärft)
     */
    public int entschaerfknopf(BlockPos knopf, int benoetigt) {
        long t = jetzt(server);
        entschaerfDruecke.put(knopf.immutable(), t);
        entschaerfDruecke.values().removeIf(zeit -> t - zeit > 20 * 15);
        int fehlt = Math.max(0, benoetigt - entschaerfDruecke.size());
        if (fehlt == 0) {
            tresorEntschaerftBis = t + 20 * 60;
            entschaerfDruecke.clear();
        }
        return fehlt;
    }

    // ---------------- Zugbrücke ----------------

    public boolean brueckeGesichert() {
        return brueckeGesichert;
    }

    public void setzeBrueckeGesichert(boolean an) {
        brueckeGesichert = an;
        speichern();
    }

    public boolean brueckeEingefahren() {
        return brueckeEingefahren;
    }

    public java.util.List<String> brueckeBloecke() {
        return java.util.Collections.unmodifiableList(brueckeBloecke);
    }

    /** Merkt sich, ob die Brücke eingefahren ist, und (beim Einfahren) ihre Blöcke; speichert sofort. */
    public void setzeBruecke(boolean eingefahren, java.util.List<String> bloecke) {
        brueckeEingefahren = eingefahren;
        brueckeBloecke.clear();
        brueckeBloecke.addAll(bloecke);
        speichern();
    }

    // ---------------- Nachrichten ----------------

    /** Schickt allen Bewohnern, die gerade online sind, eine Nachricht. */
    public void anBewohner(Component text, boolean ueberLeiste) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (istBewohner(p)) {
                Kompat.nachricht(p, text, ueberLeiste);
            }
        }
    }
}
