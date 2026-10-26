package de.moinmornhart.stinkendreich.server;

//? if >=1.21.9 {
import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.mechanik.Eigenschaften;
import de.moinmornhart.stinkendreich.mechanik.KameraBlock;
import de.moinmornhart.stinkendreich.netz.Netz;
import de.moinmornhart.stinkendreich.netz.OhneMod;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Überwachungskameras am Monitor für Spieler ohne Mod. Der Monitor zeigt eine Liste der Kameras; nach der Wahl
 * schaut der Spieler durch eine unsichtbare Hilfsfigur vor der Kamera (der Server schaltet die Kamera des Clients
 * um, der Spieler selbst bleibt am Monitor stehen). Springen wechselt zur nächsten Kamera, Schleichen beendet.
 */
final class KameraBlick {
    /** Wie weit um den Monitor nach Kameras gesucht wird (in Chunks, wie im Bildschirm der Mod). */
    private static final int SUCHRADIUS_CHUNKS = 6;

    private static final class Blick {
        final List<BlockPos> kameras;
        int nummer;
        ElementHolder halter;
        ItemDisplayElement figur;
        /** Erst nach ein paar Takten umschalten, wenn der Client die Hilfsfigur kennt. */
        int warten = 2;
        boolean sprangVorher = true;
        int takte;

        Blick(List<BlockPos> kameras, int nummer) {
            this.kameras = kameras;
            this.nummer = nummer;
        }
    }

    private static final Map<UUID, Blick> BLICKE = new HashMap<>();

    private KameraBlick() {
    }

    static void registrieren() {
        OhneMod.anmelden("monitor", KameraBlick::auswahl);
        Netz.bearbeiter("kamera", 8, (spieler, monitor, wert) -> {
            List<BlockPos> kameras = kameras((ServerLevel) spieler.level(), monitor);
            try {
                int nr = Integer.parseInt(wert.trim());
                if (nr >= 0 && nr < kameras.size()) {
                    starten(spieler, kameras, nr);
                }
            } catch (NumberFormatException ignoriert) {
                // ungültige Nummer: nichts tun
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> BLICKE.entrySet().removeIf(e -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(e.getKey());
            if (sp == null || sp.isRemoved()) {
                e.getValue().halter.destroy();
                return true;
            }
            return !takt(sp, e.getValue());
        }));
    }

    /** Alle Kameras um den Monitor, sortiert nach Höhe und Lage (gleiche Reihenfolge wie im Bildschirm der Mod). */
    private static List<BlockPos> kameras(ServerLevel welt, BlockPos monitor) {
        List<BlockPos> liste = new ArrayList<>();
        int cx = monitor.getX() >> 4, cz = monitor.getZ() >> 4;
        for (int x = cx - SUCHRADIUS_CHUNKS; x <= cx + SUCHRADIUS_CHUNKS; x++) {
            for (int z = cz - SUCHRADIUS_CHUNKS; z <= cz + SUCHRADIUS_CHUNKS; z++) {
                LevelChunk chunk = welt.getChunkSource().getChunkNow(x, z);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be.getBlockState().getBlock() instanceof KameraBlock) {
                        liste.add(be.getBlockPos());
                    }
                }
            }
        }
        liste.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        return liste;
    }

    private static void auswahl(ServerPlayer spieler, BlockPos monitor) {
        List<BlockPos> kameras = kameras((ServerLevel) spieler.level(), monitor);
        if (kameras.isEmpty()) {
            Kompat.nachricht(spieler, Component.translatable("stinkendreich.ohnemod.keine_kamera"), true);
            return;
        }
        List<ActionButton> knoepfe = new ArrayList<>();
        for (int i = 0; i < kameras.size(); i++) {
            knoepfe.add(Bedienfelder.knopf(Component.translatable("stinkendreich.ohnemod.kamera", i + 1), 98,
                    Bedienfelder.aktion("kamera", monitor, String.valueOf(i))));
        }
        Bedienfelder.zeigen(spieler, Component.translatable("block.stinkendreich.monitor"),
                List.of(Bedienfelder.text(Component.translatable("stinkendreich.ohnemod.kamera_hinweis"))), List.of(), knoepfe, 3,
                DialogAction.CLOSE);
    }

    private static void starten(ServerPlayer spieler, List<BlockPos> kameras, int nummer) {
        Blick alt = BLICKE.remove(spieler.getUUID());
        if (alt != null) {
            alt.halter.destroy();
        }
        Blick b = new Blick(kameras, nummer);
        figurSetzen(spieler, b);
        BLICKE.put(spieler.getUUID(), b);
    }

    /** Neue Hilfsfigur vor der aktuellen Kamera; der Client schaltet ein paar Takte später auf sie um. */
    private static void figurSetzen(ServerPlayer spieler, Blick b) {
        if (b.halter != null) {
            b.halter.destroy();
        }
        ServerLevel welt = (ServerLevel) spieler.level();
        b.figur = new ItemDisplayElement();
        b.figur.setInvisible(true);
        b.halter = new ElementHolder();
        b.halter.addElement(b.figur);
        ChunkAttachment.ofTicking(b.halter, welt, ort(welt, b.kameras.get(b.nummer), b.figur));
        b.halter.startWatching(spieler.connection);
        b.warten = 2;
    }

    /** Stellt die Hilfsfigur einen halben Block vor die Kamera, leicht nach unten geneigt; gibt den Ort zurück. */
    private static Vec3 ort(ServerLevel welt, BlockPos k, ItemDisplayElement figur) {
        var zustand = welt.getBlockState(k);
        Direction vorne = zustand.hasProperty(Eigenschaften.RICHTUNG) ? zustand.getValue(Eigenschaften.RICHTUNG) : Direction.NORTH;
        figur.setYaw(vorne.toYRot());
        figur.setPitch(25.0F);
        return new Vec3(k.getX() + 0.5 + vorne.getStepX() * 0.6, k.getY() + 0.4, k.getZ() + 0.5 + vorne.getStepZ() * 0.6);
    }

    /** Ein Takt des Kamerablicks; false = beendet. */
    private static boolean takt(ServerPlayer sp, Blick b) {
        var eingabe = sp.getLastClientInput();
        if (b.warten > 0 && --b.warten == 0) {
            sp.connection.send(PolymerKompat.kamera(b.figur.getEntityId()));
        }
        if (eingabe.shift()) {
            sp.connection.send(PolymerKompat.kamera(sp.getId()));
            b.halter.destroy();
            return false;
        }
        // Springen: nächste Kamera (nur beim Drücken, nicht solange gehalten)
        if (eingabe.jump() && !b.sprangVorher && b.warten == 0) {
            b.nummer = (b.nummer + 1) % b.kameras.size();
            figurSetzen(sp, b);
        }
        b.sprangVorher = eingabe.jump();
        if (b.takte++ % 40 == 0) {
            Kompat.nachricht(sp, Component.translatable("stinkendreich.ohnemod.kamera", (b.nummer + 1) + "/" + b.kameras.size())
                    .append(" · ").append(Component.translatable("stinkendreich.ohnemod.kamera_ende")).withStyle(ChatFormatting.GRAY), true);
        }
        return true;
    }
}
//?}
