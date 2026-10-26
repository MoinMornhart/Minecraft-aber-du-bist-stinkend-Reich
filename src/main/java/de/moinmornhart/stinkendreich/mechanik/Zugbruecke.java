package de.moinmornhart.stinkendreich.mechanik;

import com.google.gson.JsonArray;
import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.auto.AutoEntity;
import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import de.moinmornhart.stinkendreich.villa.VillaPlatzierung;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Zugbrücke über den Wassergraben am Tor (Marke „bruecke“ aus villa.json: Kasten x0 y0 z0 x1 y1 z1 mit Fahrbahn und
 * Geländer, dann die Richtung dx dz, in die sie einfährt).
 *
 * <p><b>Bedienen:</b> Brückenschalter (innen neben der Haustür, innen am Tor, draußen vor der Einfahrt), Handy
 * („Zugbrücke ein/aus“), James („mach die Brücke zu/auf“) und {@code /villa bruecke ein|aus}. Damit schaltet man die
 * <i>Sicherung</i>: Ist sie an, ist die Brücke eingefahren und kommt nur heraus, wenn ein Bewohner oder jemand mit
 * Zugangskarte vor dem Tor steht (von innen oder außen) oder mit einem Auto in der Nähe ist, auch wenn James fährt.
 * Ist acht Sekunden lang niemand mehr da, fährt sie wieder ein. Ist die Sicherung aus, bleibt sie draußen.
 *
 * <p><b>Animation</b> mit Block-Anzeigen aus Minecraft selbst ({@code block_display}), deshalb sehen sie auch Spieler
 * ohne Mod. Einfahren: Die Warnleuchten am Tor blinken, die Geländer fahren in die Fahrbahn, die Brücke senkt sich
 * aufs Wasser, gleitet mit Dampf, Funken und Maschinengeräusch unter das Tor, und an beiden Ufern wächst eine Mauer
 * aus dem Boden, damit niemand ins Wasser fällt. Ausfahren läuft rückwärts. Bewegt werden die Anzeigen über Minecrafts
 * eigene Übergänge (interpolation_duration), die Rechner der Spieler zeichnen das flüssig.
 *
 * <p>Die echten Blöcke verschwinden zu Beginn des Einfahrens; {@link VillaZustand} merkt sie sich, beim Ausfahren kommen
 * genau diese Blöcke zurück.
 */
public final class Zugbruecke {
    /** Kennzeichen aller Anzeigen der Brücke; dazu „_0“, „_1“ … für die Höhe über der Fahrbahn und „_mauer“. */
    private static final String TAG = "stinkendreich_bruecke";
    /** Dauer der Abschnitte in Ticks. */
    private static final int GELAENDER = 16, SENKEN = 14, GLEITEN = 60, MAUER = 16;
    /** So lange muss niemand mehr da sein, bis die gesicherte Brücke wieder einfährt (8 Sekunden). */
    private static final int WARTEN_TICKS = 160;
    /** Abstand (Blöcke) vom Brückenkasten, ab dem Bewohner zu Fuß erkannt werden. */
    private static final double ZU_FUSS = 7;
    /** Abstand zur Brücke, in dem Autos mit Bewohner oder James darin zählen (z. B. beim Rausfahren aus der Garage). */
    private static final double MIT_AUTO = 60;
    /** Warnleuchten in diesem Abstand zur Brücke blinken, solange sie fährt. */
    private static final double LEUCHTEN_ABSTAND = 20;
    /** Mauer an den Ufern, solange die Brücke eingefahren ist: unten Quarz, darüber Glas (zwei Blöcke, zu hoch zum Springen). */
    private static final String[] MAUER_BLOECKE = {"minecraft:smooth_quartz", "minecraft:glass"};

    private static Lauf lauf;
    /** Spielzeit, zu der zuletzt jemand vor der Brücke war (für das Wiedereinfahren). */
    private static final java.util.Map<Integer, Long> ZULETZT_JEMAND_DA = new java.util.concurrent.ConcurrentHashMap<>();
    /** Villa, um deren Brücke es gerade geht (die Methoden unten arbeiten immer für diese eine). */
    private static int nr;

    private Zugbruecke() {
    }

    /** Ein Block: Lage, Zustand als Text (wie im setblock-Befehl) und Höhe über der Fahrbahn. */
    private record Teil(BlockPos pos, String zustand, int stufe) {
    }

    /** Lage der Brücke in der Welt (aus der Marke). */
    private record Ort(BlockPos a, BlockPos b, int dx, int dz) {
        double mitteX() {
            return (a.getX() + b.getX() + 1) / 2.0;
        }

        double mitteZ() {
            return (a.getZ() + b.getZ() + 1) / 2.0;
        }

        /** Ganz unter dem Tor verschwinden: Länge der Brücke in Fahrtrichtung plus ein Block. */
        int strecke() {
            return (dz != 0 ? b.getZ() - a.getZ() : b.getX() - a.getX()) + 2;
        }

        AABB kasten() {
            return new AABB(a.getX(), a.getY(), a.getZ(), b.getX() + 1, b.getY() + 1, b.getZ() + 1);
        }

        /** Die beiden Ufermauern: quer zur Fahrtrichtung direkt vor und hinter der Brücke, zwischen den Geländern. */
        List<Teil> mauer() {
            List<Teil> liste = new ArrayList<>();
            for (int h = 0; h < MAUER_BLOECKE.length; h++) {
                int y = a.getY() + 1 + h;
                if (dz != 0) {
                    for (int x = a.getX() + 1; x < b.getX(); x++) {
                        liste.add(new Teil(new BlockPos(x, y, a.getZ() - 1), MAUER_BLOECKE[h], 0));
                        liste.add(new Teil(new BlockPos(x, y, b.getZ() + 1), MAUER_BLOECKE[h], 0));
                    }
                } else {
                    for (int z = a.getZ() + 1; z < b.getZ(); z++) {
                        liste.add(new Teil(new BlockPos(a.getX() - 1, y, z), MAUER_BLOECKE[h], 0));
                        liste.add(new Teil(new BlockPos(b.getX() + 1, y, z), MAUER_BLOECKE[h], 0));
                    }
                }
            }
            return liste;
        }
    }

    /** Die laufende Fahrt. */
    private static final class Lauf {
        final boolean einfahren;
        final List<Teil> teile;
        final Ort ort;
        final int stufen;
        /** Villa, deren Brücke fährt. */
        final int villa = nr;
        int tick;

        Lauf(boolean einfahren, List<Teil> teile, Ort ort) {
            this.einfahren = einfahren;
            this.teile = teile;
            this.ort = ort;
            this.stufen = ort.b().getY() - ort.a().getY() + 1;
        }
    }

    public static void registrieren() {
        ServerTickEvents.END_SERVER_TICK.register(Zugbruecke::tick);
        // Beim Beenden mitten in der Fahrt: sofort fertig machen, damit keine Blöcke fehlen und keine Anzeigen bleiben
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (lauf != null) {
                nr = lauf.villa;
                abschliessen(server.overworld());
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            lauf = null;
            ZULETZT_JEMAND_DA.clear();
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> Kompat.befehl(server, "kill @e[type=minecraft:block_display,tag=" + TAG + "]"));
    }

    /** Soll die Warnleuchte an dieser Stelle blinken, weil die Brücke in der Nähe fährt? */
    public static boolean warnen(BlockPos leuchte) {
        Lauf l = lauf;
        return l != null && leuchte.distToCenterSqr(l.ort.mitteX(), l.ort.a().getY(), l.ort.mitteZ()) < LEUCHTEN_ABSTAND * LEUCHTEN_ABSTAND;
    }

    // ---------------- Bedienen ----------------

    /** Schalter und Handy: Sicherung umschalten (nur Bewohner). */
    public static void umschalten(ServerPlayer spieler) {
        MinecraftServer server = spieler.level().getServer();
        VillaZustand zustand = VillaZustand.vonSpieler(server, spieler);
        nr = zustand.nr();
        if (!zustand.istBewohner(spieler)) {
            Kompat.nachricht(spieler, Component.translatable("stinkendreich.bruecke.nur_bewohner").withStyle(ChatFormatting.RED), true);
            return;
        }
        String fehler = sichern(server, nr, !zustand.brueckeGesichert());
        Kompat.nachricht(spieler, fehler != null ? Component.translatable(fehler).withStyle(ChatFormatting.RED)
                : Component.translatable(zustand.brueckeGesichert() ? "stinkendreich.bruecke.gesichert" : "stinkendreich.bruecke.offen")
                        .withStyle(ChatFormatting.GOLD), false);
    }

    /**
     * Sicherung an (die Brücke fährt ein, sobald niemand mehr davor steht) oder aus (sie fährt aus und bleibt draußen).
     * Liefert den Text-Schlüssel eines Fehlers oder null.
     */
    public static String sichern(MinecraftServer server, int villa, boolean an) {
        nr = villa;
        if (ort().isEmpty()) {
            return "stinkendreich.bruecke.keine";
        }
        VillaZustand.get(server, nr).setzeBrueckeGesichert(an);
        if (an) {
            ZULETZT_JEMAND_DA.remove(nr);                 // gleich prüfen, nicht erst acht Sekunden warten
        }
        pruefen(server);
        return null;
    }

    /** Sicherung der Brücke an der Spawn-Villa. */
    public static String sichern(MinecraftServer server, boolean an) {
        return sichern(server, 0, an);
    }

    /** Ist die Sicherung an? */
    public static boolean gesichert(MinecraftServer server, int villa) {
        return VillaZustand.get(server, villa).brueckeGesichert();
    }

    private static Optional<Ort> ort() {
        VillaPlatzierung.VillaInfo villa = VillaPlatzierung.nr(nr);
        if (villa == null || villa.plan().marke("bruecke").isEmpty()) {
            return Optional.empty();
        }
        JsonArray m = villa.plan().marke("bruecke").get(0);
        return Optional.of(new Ort(villa.weltPos(m.get(0).getAsInt(), m.get(1).getAsInt(), m.get(2).getAsInt()),
                villa.weltPos(m.get(3).getAsInt(), m.get(4).getAsInt(), m.get(5).getAsInt()), m.get(6).getAsInt(), m.get(7).getAsInt()));
    }

    // ---------------- Automatik ----------------

    private static void tick(MinecraftServer server) {
        if (lauf != null) {
            nr = lauf.villa;
            fahren(server);
        } else if (server.getTickCount() % 10 == 0) {
            for (VillaPlatzierung.VillaInfo v : VillaPlatzierung.alle()) {
                if (!Inkognito.aktiv(server, v.nr())) {     // Villa versteckt: keine Brücke
                    nr = v.nr();
                    pruefen(server);
                    if (lauf != null) {
                        return;                           // es fährt immer nur eine Brücke zur Zeit
                    }
                }
            }
        }
    }

    /** Soll die Brücke gerade draußen oder drin sein? Fährt sie bei Bedarf aus oder ein. */
    private static void pruefen(MinecraftServer server) {
        if (lauf != null) {
            return;
        }
        Ort o = ort().orElse(null);
        ServerLevel welt = server.overworld();
        if (o == null || !welt.isLoaded(o.a()) || !welt.isLoaded(o.b())) {
            return;
        }
        VillaZustand zustand = VillaZustand.get(server, nr);
        String wer = jemandDa(welt, o, zustand);
        long jetzt = welt.getGameTime();
        if (wer != null) {
            ZULETZT_JEMAND_DA.put(nr, jetzt);
        }
        // In der Festung (Stufe 4) ist die Brücke immer gesichert: nur für Bewohner fährt sie heraus
        boolean sollDrin = (zustand.brueckeGesichert() || zustand.festung()) && wer == null
                && jetzt - ZULETZT_JEMAND_DA.getOrDefault(nr, Long.MIN_VALUE / 2) >= WARTEN_TICKS;
        if (sollDrin != zustand.brueckeEingefahren()) {
            starten(welt, o, sollDrin, wer);
        }
    }

    /**
     * Wer darf gerade über die Brücke? „spieler“ (Bewohner oder Karte, zu Fuß vor dem Tor), „auto“ (Bewohner im Auto),
     * „james“ (James am Steuer) oder null.
     */
    private static String jemandDa(ServerLevel welt, Ort o, VillaZustand zustand) {
        if (!welt.getEntitiesOfClass(Player.class, o.kasten().inflate(ZU_FUSS, 4, ZU_FUSS),
                p -> !p.isSpectator() && zustand.darfRein(p)).isEmpty()) {
            return "spieler";
        }
        String wer = null;
        for (AutoEntity wagen : welt.getEntitiesOfClass(AutoEntity.class, o.kasten().inflate(MIT_AUTO, 24, MIT_AUTO), w -> !w.art.fliegt())) {
            for (Entity e : wagen.getPassengers()) {
                if (e instanceof Player p && !p.isSpectator() && zustand.darfRein(p)) {
                    return "auto";
                }
                if (e instanceof ButlerEntity) {
                    wer = "james";
                }
            }
        }
        return wer;
    }

    // ---------------- Ablauf ----------------

    /** Beginnt das Ein- oder Ausfahren: Anzeigen setzen, echte Blöcke wegnehmen bzw. merken. */
    private static void starten(ServerLevel welt, Ort o, boolean einfahren, String wer) {
        MinecraftServer server = welt.getServer();
        VillaZustand zustand = VillaZustand.get(server, nr);
        Kompat.befehl(server, "kill @e[type=minecraft:block_display,tag=" + TAG + "]");
        List<Teil> teile = new ArrayList<>();
        if (einfahren) {
            List<String> gemerkt = new ArrayList<>();
            for (BlockPos p : BlockPos.betweenClosed(o.a(), o.b())) {
                BlockState s = welt.getBlockState(p);
                if (s.isAir() || s.getBlock() instanceof LiquidBlock) {
                    continue;
                }
                String text = BlockStateParser.serialize(s);
                teile.add(new Teil(p.immutable(), text, p.getY() - o.a().getY()));
                gemerkt.add(p.getX() + " " + p.getY() + " " + p.getZ() + "|" + text);
            }
            if (teile.isEmpty()) {
                return;                                   // keine Brücke da (abgebaut?): nichts zu tun
            }
            // Gleich merken: Wird der Server mitten in der Fahrt beendet, fehlen die Blöcke sonst für immer
            zustand.setzeBruecke(true, gemerkt);
        } else {
            for (String eintrag : zustand.brueckeBloecke()) {
                Teil t = teilAusText(eintrag, o.a().getY());
                if (t != null) {
                    teile.add(t);
                }
            }
            if (teile.isEmpty()) {
                zustand.setzeBruecke(false, List.of());
                return;
            }
        }
        lauf = new Lauf(einfahren, teile, o);
        // Anzeigen an die echten Blöcke setzen (beim Ausfahren unter dem Tor versteckt), dann die Blöcke wegnehmen
        for (Teil t : teile) {
            anzeigeSetzen(server, t, TAG + "_" + t.stufe(), einfahren ? new double[]{0, 0, 0} : versteckt(o, t.stufe()));
        }
        if (einfahren) {
            for (Teil t : teile) {
                welt.setBlock(t.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        } else {
            // Die Ufermauern versinken zuerst wieder im Boden
            for (Teil t : o.mauer()) {
                if (BlockStateParser.serialize(welt.getBlockState(t.pos())).equals(t.zustand())) {
                    welt.setBlock(t.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    anzeigeSetzen(server, t, TAG + "_mauer", new double[]{0, 0, 0});
                }
            }
        }
        ton(server, o, "block.beacon.activate", 1.0F, 1.0F);
        ton(server, o, einfahren ? "block.piston.contract" : "block.piston.extend", 0.8F, 0.7F);
        String text = einfahren ? "stinkendreich.bruecke.einfahren" : "james".equals(wer) ? "stinkendreich.bruecke.james"
                : wer != null ? "stinkendreich.bruecke.fuer_dich" : "stinkendreich.bruecke.ausfahren";
        melden(welt, o, Component.translatable(text).withStyle(ChatFormatting.GOLD));
    }

    private static void fahren(MinecraftServer server) {
        Lauf l = lauf;
        ServerLevel welt = server.overworld();
        Ort o = l.ort;
        if (!welt.isLoaded(o.a()) || !welt.isLoaded(o.b())) {
            return;                                       // warten, bis jemand in der Nähe ist
        }
        int t = l.tick++;
        if (l.einfahren) {
            // Geländer einfahren, absenken, unters Tor gleiten, dann wachsen die Ufermauern
            int t1 = 2, t2 = t1 + GELAENDER + 2, t3 = t2 + SENKEN + 2, t4 = t3 + GLEITEN + 2, t5 = t4 + 2, ende = t5 + MAUER + 2;
            if (t == t1) {
                bewegen(server, l, 1, GELAENDER);
                ton(server, o, "block.iron_trapdoor.close", 1.0F, 0.7F);
            } else if (t == t2) {
                bewegen(server, l, 2, SENKEN);
                ton(server, o, "block.piston.contract", 1.0F, 0.6F);
                spritzer(welt, o);
            } else if (t == t3) {
                bewegen(server, l, 3, GLEITEN);
                ton(server, o, "block.piston.contract", 1.0F, 0.5F);
            } else if (t == t4) {
                // Die Brücke steckt jetzt unter dem Tor: ihre Anzeigen weg, die Mauern kommen aus dem Boden
                for (int s = 0; s < l.stufen; s++) {
                    Kompat.befehl(server, "kill @e[type=minecraft:block_display,tag=" + TAG + "_" + s + "]");
                }
                for (Teil m : o.mauer()) {
                    if (welt.getBlockState(m.pos()).isAir()) {
                        anzeigeSetzen(server, m, TAG + "_mauer", new double[]{0, -MAUER_BLOECKE.length, 0});
                    }
                }
            } else if (t == t5) {
                mauerBewegen(server, 0, MAUER);
                ton(server, o, "block.piston.extend", 1.0F, 0.8F);
                staub(welt, o);
            }
            if (t > t3 && t < t4) {
                gleiten(server, welt, l, t - t3);
            }
            if (t >= ende) {
                abschliessen(welt);
            }
        } else {
            // Mauern versenken, unter dem Tor hervorgleiten, anheben, Geländer ausfahren
            int t1 = 2, t2 = t1 + MAUER + 2, t3 = t2 + GLEITEN + 2, t4 = t3 + SENKEN + 2, ende = t4 + GELAENDER + 2;
            if (t == t1) {
                mauerBewegen(server, -MAUER_BLOECKE.length, MAUER);
                ton(server, o, "block.piston.contract", 1.0F, 0.8F);
                staub(welt, o);
            } else if (t == t2) {
                Kompat.befehl(server, "kill @e[type=minecraft:block_display,tag=" + TAG + "_mauer]");
                bewegen(server, l, 2, GLEITEN);
                ton(server, o, "block.piston.extend", 1.0F, 0.5F);
            } else if (t == t3) {
                bewegen(server, l, 1, SENKEN);
                ton(server, o, "block.piston.extend", 1.0F, 0.6F);
                spritzer(welt, o);
            } else if (t == t4) {
                bewegen(server, l, 0, GELAENDER);
                ton(server, o, "block.iron_trapdoor.open", 1.0F, 0.7F);
            }
            if (t > t2 && t < t3) {
                gleiten(server, welt, l, t - t2);
            }
            if (t >= ende) {
                abschliessen(welt);
            }
        }
    }

    /** Ende der Fahrt: echte Blöcke setzen (Brücke bzw. Mauern), Anzeigen weg, Zustand speichern. */
    private static void abschliessen(ServerLevel welt) {
        Lauf l = lauf;
        lauf = null;
        MinecraftServer server = welt.getServer();
        VillaZustand zustand = VillaZustand.get(server, nr);
        Ort o = l.ort;
        if (l.einfahren) {
            for (Teil m : o.mauer()) {
                if (welt.getBlockState(m.pos()).isAir()) {
                    setblock(server, m);
                }
            }
        } else {
            for (Teil t : l.teile) {
                setblock(server, t);
            }
            zustand.setzeBruecke(false, List.of());
            nachObenSchieben(welt, o);
        }
        Kompat.befehl(server, "kill @e[type=minecraft:block_display,tag=" + TAG + "]");
        ton(server, o, "block.anvil.land", 0.4F, 0.7F);
        ton(server, o, "block.beacon.deactivate", 0.8F, 1.2F);
        melden(welt, o, Component.translatable(l.einfahren ? "stinkendreich.bruecke.eingefahren" : "stinkendreich.bruecke.ausgefahren")
                .withStyle(ChatFormatting.GREEN));
    }

    /** Wer in den zurückkehrenden Blöcken steckt (z. B. aus dem Graben), kommt oben auf die Brücke. */
    private static void nachObenSchieben(ServerLevel welt, Ort o) {
        for (Entity e : welt.getEntitiesOfClass(Entity.class, o.kasten().expandTowards(0, -2, 0), e -> !(e instanceof Display) && !e.isPassenger())) {
            if (!welt.noCollision(e)) {
                Kompat.versetzen(e, e.getX(), o.a().getY() + 1, e.getZ(), e.getYRot(), e.getXRot());
            }
        }
    }

    // ---------------- Anzeigen ----------------

    private static void anzeigeSetzen(MinecraftServer server, Teil t, String tag, double[] start) {
        Kompat.befehl(server, String.format(Locale.ROOT,
                "summon minecraft:block_display %d.0 %d.0 %d.0 {Tags:[\"%s\",\"%s\"],block_state:%s,view_range:2.0f,transformation:%s}",
                t.pos().getX(), t.pos().getY(), t.pos().getZ(), TAG, tag, anzeigeZustand(t.zustand()),
                trafo(tag.endsWith("_0") || tag.endsWith("_mauer"), start[0], start[1], start[2])));
    }

    /**
     * Setzt die Anzeigen der Brücke auf eine Lage und lässt sie in {@code dauer} Ticks dorthin gleiten. Lagen: 0 = wie
     * die echte Brücke, 1 = Geländer in der Fahrbahn, 2 = alles einen Block tiefer (auf dem Wasser), 3 = unter dem Tor.
     */
    private static void bewegen(MinecraftServer server, Lauf l, int lage, int dauer) {
        for (int stufe = 0; stufe < l.stufen; stufe++) {
            double[] v = switch (lage) {
                case 0 -> new double[]{0, 0, 0};
                case 1 -> new double[]{0, -stufe, 0};
                case 2 -> new double[]{0, -stufe - 1, 0};
                default -> versteckt(l.ort, stufe);
            };
            Kompat.befehl(server, String.format(Locale.ROOT,
                    "execute as @e[type=minecraft:block_display,tag=%s_%d] run data merge entity @s {start_interpolation:0,interpolation_duration:%d,transformation:%s}",
                    TAG, stufe, dauer, trafo(stufe == 0, v[0], v[1], v[2])));
        }
    }

    private static void mauerBewegen(MinecraftServer server, double y, int dauer) {
        Kompat.befehl(server, String.format(Locale.ROOT,
                "execute as @e[type=minecraft:block_display,tag=%s_mauer] run data merge entity @s {start_interpolation:0,interpolation_duration:%d,transformation:%s}",
                TAG, dauer, trafo(true, 0, y, 0)));
    }

    /** Verschiebung eines Teils, wenn die Brücke ganz unter dem Tor steckt. */
    private static double[] versteckt(Ort o, int stufe) {
        return new double[]{o.dx() * o.strecke(), -stufe - 1, o.dz() * o.strecke()};
    }

    /**
     * Umrechnung für die Anzeige: etwas kleiner gezeichnet (Fahrbahn und Mauer 99,8 %, Geländer 99 %) und mittig
     * verschoben, damit nichts mit den Nachbarflächen flimmert und die eingefahrenen Geländer ganz in der Fahrbahn
     * verschwinden.
     */
    private static String trafo(boolean gross, double x, double y, double z) {
        double s = gross ? 0.998 : 0.99, rand = (1 - s) / 2;
        return String.format(Locale.ROOT,
                "{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[%.4ff,%.4ff,%.4ff],scale:[%.4ff,%.4ff,%.4ff]}",
                rand + x, rand + y, rand + z, s, s, s);
    }

    // ---------------- Effekte ----------------

    /** Dampf und Funken an der Mauer, durch die die Brücke gleitet, dazu das Mahlen der Maschine. */
    private static void gleiten(MinecraftServer server, ServerLevel welt, Lauf l, int schritt) {
        Ort o = l.ort;
        if (schritt % 8 == 1) {
            ton(server, o, "block.grindstone.use", 0.6F, 0.5F);
        }
        if (schritt % 2 == 0) {
            double nahtX = o.dx() < 0 ? o.a().getX() : o.dx() > 0 ? o.b().getX() + 1 : o.mitteX();
            double nahtZ = o.dz() < 0 ? o.a().getZ() : o.dz() > 0 ? o.b().getZ() + 1 : o.mitteZ();
            double breiteX = o.dx() == 0 ? (o.b().getX() - o.a().getX() + 1) / 2.5 : 0.1;
            double breiteZ = o.dz() == 0 ? (o.b().getZ() - o.a().getZ() + 1) / 2.5 : 0.1;
            double y = o.a().getY() - 0.4;
            welt.sendParticles(ParticleTypes.CLOUD, nahtX, y, nahtZ, 5, breiteX, 0.25, breiteZ, 0.02);
            welt.sendParticles(ParticleTypes.ELECTRIC_SPARK, nahtX, y + 0.2, nahtZ, 4, breiteX, 0.2, breiteZ, 0.3);
        }
    }

    /** Wasser spritzt, wenn die Brücke aufs Wasser sinkt oder aus ihm auftaucht. */
    private static void spritzer(ServerLevel welt, Ort o) {
        double bx = (o.b().getX() - o.a().getX() + 1) / 2.5, bz = (o.b().getZ() - o.a().getZ() + 1) / 2.5;
        welt.sendParticles(ParticleTypes.SPLASH, o.mitteX(), o.a().getY() + 0.1, o.mitteZ(), 120, bx, 0.1, bz, 0.2);
        welt.sendParticles(ParticleTypes.CLOUD, o.mitteX(), o.a().getY() + 0.3, o.mitteZ(), 25, bx, 0.2, bz, 0.02);
    }

    /** Staub an den Ufermauern, wenn sie aus dem Boden wachsen oder versinken. */
    private static void staub(ServerLevel welt, Ort o) {
        for (Teil m : o.mauer()) {
            if (m.pos().getY() == o.a().getY() + 1 && m.pos().getX() % 2 == 0) {
                welt.sendParticles(ParticleTypes.CLOUD, m.pos().getX() + 0.5, m.pos().getY() + 0.1, m.pos().getZ() + 0.5, 3, 0.3, 0.05, 0.3, 0.01);
            }
        }
    }

    private static void ton(MinecraftServer server, Ort o, String ton, float lautstaerke, float hoehe) {
        Kompat.befehl(server, String.format(Locale.ROOT, "playsound minecraft:%s block @a %.1f %d %.1f %.1f %.2f",
                ton, o.mitteX(), o.a().getY(), o.mitteZ(), lautstaerke * 2.0F, hoehe));
    }

    /** Nachricht über der Leiste an Bewohner in der Nähe der Brücke (nicht an alle im Haus). */
    private static void melden(ServerLevel welt, Ort o, Component text) {
        VillaZustand zustand = VillaZustand.get(welt.getServer(), nr);
        for (ServerPlayer p : welt.players()) {
            if (zustand.istBewohner(p) && p.distanceToSqr(o.mitteX(), o.a().getY(), o.mitteZ()) < 80 * 80) {
                Kompat.nachricht(p, text, true);
            }
        }
    }

    // ---------------- Hilfen ----------------

    private static void setblock(MinecraftServer server, Teil t) {
        Kompat.befehl(server, "setblock " + t.pos().getX() + " " + t.pos().getY() + " " + t.pos().getZ() + " " + t.zustand());
    }

    /** „x y z|Zustand“ aus dem Speicher zurück in ein Teil. */
    private static Teil teilAusText(String eintrag, int y0) {
        String[] teile = eintrag.split("\\|", 2);
        String[] xyz = teile[0].split(" ");
        if (teile.length < 2 || xyz.length < 3) {
            return null;
        }
        BlockPos p = new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
        return new Teil(p, teile[1], p.getY() - y0);
    }

    /**
     * Blockzustand für die Anzeige aus dem Text des setblock-Befehls: „minecraft:quartz_slab[type=bottom]“ wird zu
     * {Name:"minecraft:quartz_slab",Properties:{type:"bottom"}}; ab 26.3 heißen die Felder id und properties.
     */
    static String anzeigeZustand(String zustand) {
        //? if >=26.3 {
        String name = "id", eigenschaften = "properties";
        //?} else
        /*String name = "Name", eigenschaften = "Properties";*/
        int klammer = zustand.indexOf('[');
        StringBuilder sb = new StringBuilder("{").append(name).append(":\"").append(klammer < 0 ? zustand : zustand.substring(0, klammer)).append('"');
        if (klammer >= 0 && zustand.endsWith("]")) {
            sb.append(',').append(eigenschaften).append(":{");
            String[] paare = zustand.substring(klammer + 1, zustand.length() - 1).split(",");
            for (int i = 0; i < paare.length; i++) {
                String[] kv = paare[i].split("=", 2);
                if (kv.length == 2) {
                    sb.append(i > 0 ? "," : "").append(kv[0]).append(":\"").append(kv[1]).append('"');
                }
            }
            sb.append('}');
        }
        return sb.append('}').toString();
    }
}
