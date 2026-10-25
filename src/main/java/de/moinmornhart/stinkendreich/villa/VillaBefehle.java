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
