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
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Optional;

/**
 * Befehle rund um die Villa:
 * <ul>
 *   <li>{@code /villa} – zurück vor den Eingang der Villa (für alle)</li>
 *   <li>{@code /villa info} – Lage der Villa anzeigen</li>
 *   <li>{@code /villa ort <x> <y> <z> [blick] [neigung]} – zu einer Stelle im Bauplan (nur Operatoren, für Tests)</li>
 *   <li>{@code /villa stufe <0-3>} – Sicherheitsstufe setzen (nur Operatoren)</li>
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

    private static void teleport(CommandSourceStack quelle, double x, double y, double z, float blick, float neigung)
            throws CommandSyntaxException {
        ServerPlayer spieler = quelle.getPlayerOrException();
        Kompat.befehl(quelle.getServer(), String.format(Locale.ROOT, "tp %s %.2f %.2f %.2f %.1f %.1f",
                spieler.getScoreboardName(), x, y, z, blick, neigung));
    }
}
