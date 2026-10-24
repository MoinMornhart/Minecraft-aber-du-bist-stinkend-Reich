package de.moinmornhart.stinkendreich;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
//? if >=1.21.9
import net.minecraft.world.level.storage.LevelData;

/**
 * Bündelt alle Server-Aufrufe, die sich zwischen den Minecraft-Versionen unterscheiden.
 * Die {@code //?}-Zeilen sind Stonecutter-Bedingungen (siehe {@code KompatClient}).
 */
public final class Kompat {
    private Kompat() {
    }

    /** Unterste baubare Y-Koordinate. */
    public static int minY(LevelHeightAccessor welt) {
        //? if >=1.21.2 {
        return welt.getMinY();
        //?} else
        /*return welt.getMinBuildHeight();*/
    }

    /** Erste Y-Koordinate oberhalb der Bauhöhe (exklusiv). */
    public static int maxY(LevelHeightAccessor welt) {
        //? if >=1.21.2 {
        return welt.getMaxY() + 1;
        //?} else
        /*return welt.getMaxBuildHeight();*/
    }

    /** Aktueller Welt-Spawnpunkt. */
    public static BlockPos spawnPunkt(ServerLevel welt) {
        //? if >=1.21.9 {
        return welt.getRespawnData().pos();
        //?} else
        /*return welt.getSharedSpawnPos();*/
    }

    /** Setzt den Welt-Spawnpunkt samt Blickrichtung. */
    public static void setzeSpawnPunkt(ServerLevel welt, BlockPos pos, float blick) {
        //? if >=1.21.9 {
        welt.setRespawnData(LevelData.RespawnData.of(welt.dimension(), pos, blick, 0.0F));
        //?} else
        /*welt.setDefaultSpawnPos(pos, blick);*/
    }

    /** Verwaltet die Strukturdateien (.nbt) aus Mod und Welt. */
    public static StructureTemplateManager strukturen(MinecraftServer server) {
        //? if >=26.3 {
        return server.getStructureTemplateManager();
        //?} else
        /*return server.getStructureManager();*/
    }

    /** Befehl für die Spielregel „Spawnradius“ (heißt ab 1.21.11 anders). */
    public static String spawnRadiusBefehl(int radius) {
        //? if >=1.21.11 {
        return "gamerule minecraft:respawn_radius " + radius;
        //?} else
        /*return "gamerule spawnRadius " + radius;*/
    }

    /** Die frei vergebbaren Markierungen („Tags“) einer Entity. */
    public static java.util.Set<String> tags(net.minecraft.world.entity.Entity e) {
        //? if >=26.1 {
        return e.entityTags();
        //?} else
        /*return e.getTags();*/
    }

    /** Prüft, ob die Befehlsquelle Operator-Rechte (Stufe 2) hat. */
    public static java.util.function.Predicate<net.minecraft.commands.CommandSourceStack> istOperator() {
        //? if >=1.21.11 {
        return net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS);
        //?} else
        /*return quelle -> quelle.hasPermission(2);*/
    }

    /** Zeigt einem Spieler eine Nachricht im Chat oder über der Schnellleiste. */
    public static void nachricht(net.minecraft.world.entity.player.Player spieler, net.minecraft.network.chat.Component text, boolean ueberLeiste) {
        //? if >=26.1 {
        if (ueberLeiste) {
            spieler.sendOverlayMessage(text);
        } else {
            spieler.sendSystemMessage(text);
        }
        //?} else
        /*spieler.displayClientMessage(text, ueberLeiste);*/
    }

    /** Fügt einer Entity Schaden zu (nur auf dem Server). */
    public static void schaden(net.minecraft.world.entity.Entity ziel, ServerLevel welt, float menge) {
        //? if >=1.21.2 {
        ziel.hurtServer(welt, welt.damageSources().magic(), menge);
        //?} else
        /*ziel.hurt(welt.damageSources().magic(), menge);*/
    }

    /** Benachrichtigt die Nachbarblöcke (z. B. nach Änderung eines Redstone-Signals). */
    public static void nachbarnAktualisieren(net.minecraft.world.level.Level welt, BlockPos pos, net.minecraft.world.level.block.Block block) {
        //? if >=1.21.2 {
        welt.updateNeighborsAt(pos, block, null);
        //?} else
        /*welt.updateNeighborsAt(pos, block);*/
    }

    /** Block zu einer ID (Methode hieß bis 1.21.1 {@code get}). */
    public static net.minecraft.world.level.block.Block blockNachId(net.minecraft.resources.Identifier id) {
        //? if >=1.21.2 {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(id);
        //?} else
        /*return net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id);*/
    }

    /** Führt einen Befehl als Server aus, ohne Ausgabe im Chat. */
    public static void befehl(MinecraftServer server, String befehl) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), befehl);
    }
}
