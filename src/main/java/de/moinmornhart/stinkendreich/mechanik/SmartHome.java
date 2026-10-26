package de.moinmornhart.stinkendreich.mechanik;

import com.google.gson.JsonArray;
import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.register.ModBloecke;
import de.moinmornhart.stinkendreich.villa.VillaPlatzierung;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Smart Home der Villa, gesteuert über die Smart-Home-App im Handy: Rollläden hoch/runter, Zugbrücke ein/aus, alle
 * Hängelampen ({@link SmartlichtBlock}) an/aus und in acht Farben, Disco (die Farben wandern durchs Haus) und eine
 * Automatik: Abends (Sonnenuntergang) gehen die Rollläden runter, die Lampen an und die Brücke fährt ein, morgens
 * umgekehrt. Die Einstellungen liegen im {@link VillaZustand}. Bedienen dürfen nur Bewohner.
 */
public final class SmartHome {
    /** Abend und Morgen in Tageszeit-Ticks (Sonnenuntergang, Sonnenaufgang). */
    private static final long ABEND = 12500, MORGEN = 23500;
    private static final Eigenschaften.Lichtfarbe[] DISCO = {Eigenschaften.Lichtfarbe.ROT, Eigenschaften.Lichtfarbe.PINK,
            Eigenschaften.Lichtfarbe.LILA, Eigenschaften.Lichtfarbe.BLAU, Eigenschaften.Lichtfarbe.GRUEN,
            Eigenschaften.Lichtfarbe.GOLD};

    /** Lampen der Villa (Weltpositionen, aus den Marken „smartlicht“), erst beim ersten Bedarf berechnet. */
    private static List<BlockPos> lampen;
    /** Muss der Zustand der Lampen neu gesetzt werden? */
    private static boolean lampenNeu = true;
    /** Zuletzt gesehene Tageshälfte für die Automatik (null = noch unbekannt). */
    private static Boolean warAbend;

    private SmartHome() {
    }

    public static void registrieren() {
        ServerTickEvents.END_SERVER_TICK.register(SmartHome::tick);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            lampen = null;
            lampenNeu = true;
            warAbend = null;
        });
    }

    /** Sind die Rollläden per App oder Automatik heruntergefahren? (Die Torsteuerung fragt das ab.) */
    public static boolean rolllaedenZu(VillaZustand z) {
        return z.smart("rollo_zu", false);
    }

    /**
     * Befehl aus der App: „rollo|zu/auf“, „licht|an/aus“, „farbe|rot …“, „disco|an/aus“, „bruecke|zu/auf“,
     * „automatik|an/aus“.
     */
    public static void befehl(ServerPlayer p, String angabe) {
        MinecraftServer server = p.level().getServer();
        VillaZustand z = VillaZustand.get(server);
        if (!z.istBewohner(p)) {
            Kompat.nachricht(p, Component.translatable("stinkendreich.smarthome.nur_bewohner").withStyle(ChatFormatting.RED), true);
            return;
        }
        String[] t = angabe.split("\\|", 2);
        String was = t[0], wert = t.length > 1 ? t[1] : "";
        boolean an = wert.equals("an") || wert.equals("zu");
        switch (was) {
            case "rollo" -> z.setzeSmart("rollo_zu", an);
            case "licht" -> {
                z.setzeSmart("licht", an);
                if (!an) {
                    z.setzeSmart("disco", false);
                }
            }
            case "farbe" -> {
                if (Eigenschaften.Lichtfarbe.von(wert) == null) {
                    return;
                }
                z.setzeSmart("farbe", wert);
                z.setzeSmart("licht", true);
                z.setzeSmart("disco", false);
            }
            case "disco" -> {
                z.setzeSmart("disco", an);
                if (an) {
                    z.setzeSmart("licht", true);
                }
            }
            case "bruecke" -> {
                String fehler = Zugbruecke.sichern(server, an);
                if (fehler != null) {
                    Kompat.nachricht(p, Component.translatable(fehler).withStyle(ChatFormatting.RED), true);
                    return;
                }
            }
            case "automatik" -> z.setzeSmart("automatik", an);
            default -> {
                return;
            }
        }
        lampenNeu = true;
        Kompat.nachricht(p, Component.translatable("stinkendreich.smarthome." + was + "." + (an ? "an" : "aus"),
                Component.translatable("stinkendreich.smarthome.farbe." + wert)).withStyle(ChatFormatting.AQUA), true);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0 || VillaPlatzierung.villa().isEmpty()) {
            return;
        }
        VillaZustand z = VillaZustand.get(server);
        ServerLevel welt = server.overworld();
        automatik(welt, z);
        boolean disco = z.smart("disco", false) && z.smart("licht", true);
        if (lampenNeu || disco) {
            lampenNeu = false;
            lampenSetzen(welt, z, disco ? (int) (server.getTickCount() / 10) : -1);
        }
    }

    /** Abends Rollläden runter, Licht an, Brücke ein; morgens umgekehrt (nur beim Wechsel, damit die App gilt). */
    private static void automatik(ServerLevel welt, VillaZustand z) {
        //? if >=26.1 {
        long zeit = Math.floorMod(welt.getDefaultClockTime(), 24000L);
        //?} else
        /*long zeit = Math.floorMod(welt.getDayTime(), 24000L);*/
        boolean abend = zeit >= ABEND && zeit < MORGEN;
        if (warAbend == null) {
            warAbend = abend;
            return;
        }
        if (abend == warAbend) {
            return;
        }
        warAbend = abend;
        if (!z.smart("automatik", true)) {
            return;
        }
        z.setzeSmart("rollo_zu", abend);
        z.setzeSmart("licht", abend);
        if (abend) {
            if (!z.brueckeGesichert() && Zugbruecke.sichern(welt.getServer(), true) == null) {
                z.setzeSmart("bruecke_auto", true);                // morgens wieder ausfahren
            }
        } else if (z.smart("bruecke_auto", false)) {
            Zugbruecke.sichern(welt.getServer(), false);
            z.setzeSmart("bruecke_auto", false);
        }
        lampenNeu = true;
        z.anBewohner(Component.translatable(abend ? "stinkendreich.smarthome.abend" : "stinkendreich.smarthome.morgen")
                .withStyle(ChatFormatting.GOLD), true);
    }

    /** Alle geladenen Lampen auf den eingestellten Zustand setzen; {@code disco} ≥ 0 = Schritt der Farbwelle. */
    private static void lampenSetzen(ServerLevel welt, VillaZustand z, int disco) {
        boolean an = z.smart("licht", true);
        Eigenschaften.Lichtfarbe farbe = Eigenschaften.Lichtfarbe.von(z.smartText("farbe", "warm"));
        if (farbe == null) {
            farbe = Eigenschaften.Lichtfarbe.WARM;
        }
        for (BlockPos pos : lampen()) {
            if (!welt.isLoaded(pos)) {
                continue;
            }
            BlockState s = welt.getBlockState(pos);
            if (!s.is(ModBloecke.SMARTLICHT)) {
                continue;
            }
            Eigenschaften.Lichtfarbe f = disco >= 0 ? DISCO[Math.floorMod(disco + (pos.getX() + pos.getZ()) / 3, DISCO.length)] : farbe;
            BlockState neu = s.setValue(Eigenschaften.LEUCHTET, an).setValue(Eigenschaften.LICHTFARBE, f);
            if (neu != s) {
                welt.setBlock(pos, neu, 2);
            }
        }
    }

    private static List<BlockPos> lampen() {
        if (lampen == null) {
            lampen = new ArrayList<>();
            VillaPlatzierung.villa().ifPresent(v -> {
                for (JsonArray m : v.plan().marke("smartlicht")) {
                    lampen.add(v.weltPos(m.get(0).getAsDouble(), m.get(1).getAsDouble(), m.get(2).getAsDouble()));
                }
            });
        }
        return lampen;
    }
}
