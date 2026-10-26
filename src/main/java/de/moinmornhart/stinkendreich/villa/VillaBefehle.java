package de.moinmornhart.stinkendreich.villa;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.mechanik.VillaZustand;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import de.moinmornhart.stinkendreich.StinkendReich;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Befehle rund um die Villa:
 * <ul>
 *   <li>{@code /villa} – zurück vor den Eingang der Villa (für alle)</li>
 *   <li>{@code /villa info} – Lage der Villa anzeigen</li>
 *   <li>{@code /villa ort <x> <y> <z> [blick] [neigung]} – zu einer Stelle im Bauplan (nur Operatoren, für Tests)</li>
 *   <li>{@code /villa stufe <0-3>} – Sicherheitsstufe setzen (nur Operatoren)</li>
 *   <li>{@code /villa lecks} – findet Wasser, das nicht im Bauplan steht (nur Operatoren, für Tests)</li>
 *   <li>{@code /villa bruecke ein|aus} – Sicherung der Zugbrücke an oder aus (nur Operatoren; sonst Schalter, Handy, James)</li>
 * </ul>
 */
public final class VillaBefehle {
    private VillaBefehle() {
    }

    public static void registrieren() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, umgebung) -> dispatcher.register(
                Commands.literal("villa")
                        .executes(VillaBefehle::nachHause)
                        .then(Commands.literal("info").executes(VillaBefehle::info))
                        .then(Commands.literal("stufe").requires(Kompat.istOperator())
                                .then(Commands.argument("stufe", IntegerArgumentType.integer(0, VillaZustand.MAX_STUFE))
                                        .executes(k -> {
                                            VillaZustand.get(k.getSource().getServer()).setzeStufe(IntegerArgumentType.getInteger(k, "stufe"));
                                            return 1;
                                        })))
                        .then(Commands.literal("lecks").requires(Kompat.istOperator()).executes(VillaBefehle::lecks))
                        .then(Commands.literal("bruecke").requires(Kompat.istOperator())
                                .then(Commands.literal("ein").executes(k -> bruecke(k, true)))
                                .then(Commands.literal("aus").executes(k -> bruecke(k, false))))
                        .then(Commands.literal("minentest").requires(Kompat.istOperator()).executes(VillaBefehle::minentest))
                        .then(Commands.literal("luke").requires(Kompat.istOperator()).executes(VillaBefehle::luke))
                        .then(Commands.literal("ort").requires(Kompat.istOperator())
                                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                        .executes(k -> ort(k, 180.0F, 0.0F))
                                                        .then(Commands.argument("blick", FloatArgumentType.floatArg())
                                                                .executes(k -> ort(k, FloatArgumentType.getFloat(k, "blick"), 0.0F))
                                                                .then(Commands.argument("neigung", FloatArgumentType.floatArg(-90, 90))
                                                                        .executes(k -> ort(k, FloatArgumentType.getFloat(k, "blick"),
                                                                                FloatArgumentType.getFloat(k, "neigung")))))))))));
    }

    /** Sicherung der Zugbrücke an (sie fährt ein, sobald niemand davor steht) oder aus; meldet, warum es nicht geht. */
    private static int bruecke(CommandContext<CommandSourceStack> k, boolean einfahren) {
        String fehler = de.moinmornhart.stinkendreich.mechanik.Zugbruecke.sichern(k.getSource().getServer(), einfahren);
        if (fehler != null) {
            k.getSource().sendFailure(Component.translatable(fehler));
            return 0;
        }
        return 1;
    }

    private static int nachHause(CommandContext<CommandSourceStack> k) throws CommandSyntaxException {
        Optional<VillaPlatzierung.VillaInfo> villa = VillaPlatzierung.villa();
        if (villa.isEmpty()) {
            k.getSource().sendFailure(Component.translatable("stinkendreich.villa.keine"));
            return 0;
        }
        BlockPos p = villa.get().spawn();
        teleport(k.getSource(), p.getX() + 0.5, p.getY(), p.getZ() + 0.5, villa.get().spawnBlick(), 0.0F);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> k) {
        Optional<VillaPlatzierung.VillaInfo> villa = VillaPlatzierung.villa();
        if (villa.isEmpty()) {
            k.getSource().sendFailure(Component.translatable("stinkendreich.villa.keine"));
            return 0;
        }
        BlockPos u = villa.get().ursprung();
        BlockPos s = villa.get().spawn();
        k.getSource().sendSuccess(() -> Component.translatable("stinkendreich.villa.info",
                u.getX(), u.getY(), u.getZ(), s.getX(), s.getY(), s.getZ()), false);
        return 1;
    }

    private static int ort(CommandContext<CommandSourceStack> k, float blick, float neigung) throws CommandSyntaxException {
        Optional<VillaPlatzierung.VillaInfo> villa = VillaPlatzierung.villa();
        if (villa.isEmpty()) {
            k.getSource().sendFailure(Component.translatable("stinkendreich.villa.keine"));
            return 0;
        }
        BlockPos u = villa.get().ursprung();
        teleport(k.getSource(),
                u.getX() + DoubleArgumentType.getDouble(k, "x"),
                u.getY() + DoubleArgumentType.getDouble(k, "y"),
                u.getZ() + DoubleArgumentType.getDouble(k, "z"), blick, neigung);
        return 1;
    }

    /** Testhilfe: stellt einen reglosen Diener (Monster) auf die nächste Landmine bis 32 Blöcke um den Spieler. */
    private static int minentest(CommandContext<CommandSourceStack> k) {
        ServerLevel welt = k.getSource().getLevel();
        BlockPos mitte = BlockPos.containing(k.getSource().getPosition());
        BlockPos beste = null;
        for (BlockPos q : BlockPos.betweenClosed(mitte.offset(-32, -24, -32), mitte.offset(32, 24, 32))) {
            if (welt.getBlockState(q).is(de.moinmornhart.stinkendreich.register.ModBloecke.LANDMINE)
                    && (beste == null || q.distSqr(mitte) < beste.distSqr(mitte))) {
                beste = q.immutable();
            }
        }
        if (beste == null) {
            k.getSource().sendFailure(Component.literal("Keine Mine in der Nähe"));
            return 0;
        }
        // Kamera 7 Blöcke südlich und 4 höher, Blick auf die Mine
        if (k.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
            Kompat.versetzen(sp, beste.getX() + 0.5, beste.getY() + 4.0, beste.getZ() + 7.5, 180.0F, 30.0F);
        }
        Kompat.befehl(k.getSource().getServer(), "summon minecraft:vindicator " + (beste.getX() + 0.5) + " " + beste.getY()
                + " " + (beste.getZ() + 0.5) + " {NoAI:1b}");
        StinkendReich.LOGGER.info("Minentest: Diener auf der Mine bei {}", beste);
        return 1;
    }

    /** Testhilfe: stellt den Spieler neben die versteckte Falltür des Notausgangs (Blick auf die Luke). */
    private static int luke(CommandContext<CommandSourceStack> k) throws CommandSyntaxException {
        Optional<VillaPlatzierung.VillaInfo> villa = VillaPlatzierung.villa();
        if (villa.isEmpty() || VillaDaten.get().marke("notausgang").isEmpty()) {
            k.getSource().sendFailure(Component.translatable("stinkendreich.villa.keine"));
            return 0;
        }
        com.google.gson.JsonArray m = VillaDaten.get().marke("notausgang").get(0);
        BlockPos u = villa.get().ursprung();
        int x = u.getX() + m.get(0).getAsInt() - m.get(3).getAsInt(), z = u.getZ() + m.get(2).getAsInt();
        ServerLevel welt = k.getSource().getLevel();
        for (int y = Kompat.maxY(welt) - 1; y > Kompat.minY(welt); y--) {
            if (welt.getBlockState(new BlockPos(x, y, z)).is(de.moinmornhart.stinkendreich.register.ModBloecke.GEHEIMLUKE)) {
                teleport(k.getSource(), x + 3.5, y + 1.0, z + 0.5, 90.0F, 35.0F);
                StinkendReich.LOGGER.info("Luke des Notausgangs bei {} {} {}", x, y, z);
                return 1;
            }
        }
        k.getSource().sendFailure(Component.literal("Keine Luke gefunden"));
        return 0;
    }

    /**
     * Vergleicht alles Wasser auf dem Grundstück mit dem Bauplan. Wasser an Stellen, an denen der
     * Plan keins vorsieht, ist durch ein Leck geflossen; zusammenhängende Stellen werden zu Gruppen
     * zusammengefasst und mit Bauplan-Koordinaten gemeldet (Chat und Protokoll).
     */
    private static int lecks(CommandContext<CommandSourceStack> k) {
        Optional<VillaPlatzierung.VillaInfo> villa = VillaPlatzierung.villa();
        if (villa.isEmpty()) {
            k.getSource().sendFailure(Component.translatable("stinkendreich.villa.keine"));
            return 0;
        }
        MinecraftServer server = k.getSource().getServer();
        ServerLevel welt = server.overworld();
        BlockPos u = villa.get().ursprung();
        VillaDaten plan = villa.get().plan();
        Set<BlockPos> geplant = new HashSet<>();
        Kompat.strukturen(server).get(StinkendReich.id("villa/villa")).ifPresent(vorlage -> {
            for (StructureTemplate.StructureBlockInfo info : vorlage.filterBlocks(u, new StructurePlaceSettings(), Blocks.WATER)) {
                geplant.add(info.pos().immutable());
            }
        });
        Set<BlockPos> fremd = new HashSet<>();
        for (BlockPos p : BlockPos.betweenClosed(u, u.offset(plan.breiteX - 1, plan.hoehe - 1, plan.breiteZ - 1))) {
            if (welt.getBlockState(p).is(Blocks.WATER) && !geplant.contains(p)) {
                fremd.add(p.immutable());
            }
        }
        // Zusammenhängende Gruppen bilden, größte zuerst
        List<List<BlockPos>> gruppen = new ArrayList<>();
        Set<BlockPos> gesehen = new HashSet<>();
        for (BlockPos start : fremd) {
            if (!gesehen.add(start)) {
                continue;
            }
            List<BlockPos> gruppe = new ArrayList<>();
            ArrayDeque<BlockPos> offen = new ArrayDeque<>(List.of(start));
            while (!offen.isEmpty()) {
                BlockPos p = offen.poll();
                gruppe.add(p);
                for (Direction d : Direction.values()) {
                    BlockPos n = p.relative(d);
                    if (fremd.contains(n) && gesehen.add(n)) {
                        offen.add(n);
                    }
                }
            }
            gruppen.add(gruppe);
        }
        gruppen.sort(Comparator.comparingInt(g -> -g.size()));
        k.getSource().sendSuccess(() -> Component.literal("Lecks: " + fremd.size() + " Wasserblöcke außerhalb des Plans in "
                + gruppen.size() + " Gruppen (geplant: " + geplant.size() + ")"), false);
        for (List<BlockPos> g : gruppen.subList(0, Math.min(12, gruppen.size()))) {
            BlockPos oben = g.stream().max(Comparator.comparingInt(BlockPos::getY)).orElseThrow();
            int minY = g.stream().mapToInt(BlockPos::getY).min().orElse(0) - u.getY();
            String text = String.format(Locale.ROOT, "  %d Blöcke, höchster bei %d %d %d (Plan), tiefster y %d",
                    g.size(), oben.getX() - u.getX(), oben.getY() - u.getY(), oben.getZ() - u.getZ(), minY);
            k.getSource().sendSuccess(() -> Component.literal(text), false);
            StinkendReich.LOGGER.info("Leck{}", text);
        }
        return fremd.size();
    }

    private static void teleport(CommandSourceStack quelle, double x, double y, double z, float blick, float neigung)
            throws CommandSyntaxException {
        ServerPlayer spieler = quelle.getPlayerOrException();
        Kompat.befehl(quelle.getServer(), String.format(Locale.ROOT, "tp %s %.2f %.2f %.2f %.1f %.1f",
                spieler.getScoreboardName(), x, y, z, blick, neigung));
    }
}
