package de.moinmornhart.stinkendreich.netz;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;

/** Verteilt eingehende {@link Aktion}-Pakete an die zuständigen Mechanismen. */
public final class Netz {
    /** Bearbeitet eine Aktion auf dem Server. */
    public interface Bearbeiter {
        void ausfuehren(ServerPlayer spieler, BlockPos pos, String wert);
    }

    private record Eintrag(Bearbeiter bearbeiter, double reichweite) {
    }

    private static final Map<String, Eintrag> BEARBEITER = new HashMap<>();

    private Netz() {
    }

    public static void registrieren() {
        // Fabric hat die Methode für 26.x umbenannt
        //? if >=26.1 {
        PayloadTypeRegistry.serverboundPlay().register(Aktion.TYP, Aktion.CODEC);
        //?} else
        /*PayloadTypeRegistry.playC2S().register(Aktion.TYP, Aktion.CODEC);*/
        ServerPlayNetworking.registerGlobalReceiver(Aktion.TYP, (aktion, kontext) -> kontext.server().execute(() ->
                ausfuehren(kontext.player(), aktion.art(), aktion.pos(), aktion.wert())));

        // Dieselben Aktionen als Befehl: so lösen die Dialogfenster für Spieler ohne Mod (Server-Version) sie aus,
        // z. B. „/stinkendreich knopf zentrale 10 64 -3 stufe:2“
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, umgebung) -> dispatcher.register(
                Commands.literal("stinkendreich").then(Commands.literal("knopf")
                        .then(Commands.argument("art", StringArgumentType.word())
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(k -> {
                                            // leeres Eingabefeld im Dialog
                                            ausfuehren(k.getSource().getPlayerOrException(), StringArgumentType.getString(k, "art"),
                                                    BlockPosArgument.getBlockPos(k, "pos"), "");
                                            return 1;
                                        })
                                        .then(Commands.argument("wert", StringArgumentType.greedyString()).executes(k -> {
                                            ausfuehren(k.getSource().getPlayerOrException(), StringArgumentType.getString(k, "art"),
                                                    BlockPosArgument.getBlockPos(k, "pos"), StringArgumentType.getString(k, "wert"));
                                            return 1;
                                        })))))));
    }

    /** Führt eine Aktion aus, aber nur in Reichweite (Schutz gegen manipulierte Pakete und Befehle). */
    public static void ausfuehren(ServerPlayer spieler, String art, BlockPos pos, String wert) {
        Eintrag e = BEARBEITER.get(art);
        if (e != null && (e.reichweite() <= 0 || spieler.distanceToSqr(pos.getX() + 0.5,
                pos.getY() + 0.5, pos.getZ() + 0.5) <= e.reichweite() * e.reichweite())) {
            e.bearbeiter().ausfuehren(spieler, pos, wert);
        }
    }

    /** Meldet einen Bearbeiter an; {@code reichweite} in Blöcken (0 = ohne Prüfung). */
    public static void bearbeiter(String art, double reichweite, Bearbeiter bearbeiter) {
        BEARBEITER.put(art, new Eintrag(bearbeiter, reichweite));
    }
}
