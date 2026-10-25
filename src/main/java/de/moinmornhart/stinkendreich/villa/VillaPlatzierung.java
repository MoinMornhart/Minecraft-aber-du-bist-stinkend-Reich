package de.moinmornhart.stinkendreich.villa;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.Konfig;
import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.butler.ButlerVerwaltung;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

/**
 * Baut die Villa beim ersten Start einer neuen Welt am Spawn:
 * Gelände ebnen, Übergang ins natürliche Gelände anblenden, Strukturdatei setzen,
 * Spawnpunkt vor den Eingang legen. Bestehende Welten bleiben unberührt.
 */
public final class VillaPlatzierung {
    /** Merkdatei im Weltordner: ob und wo die Villa gebaut wurde. */
    private static final String DATEI = StinkendReich.MOD_ID + "-villa.json";
    private static final String TAG_ANGEKOMMEN = StinkendReich.MOD_ID + ".angekommen";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Lage der Villa in der aktuell geladenen Welt, oder null. */
    private static VillaInfo aktuelleVilla;
    /** In den ersten 10 Spielminuten einer Villa-Welt werden Reste zerfallender Blätter weggeräumt. */
    private static final long AUFRAEUMEN_BIS = 20L * 60 * 10;

    private VillaPlatzierung() {
    }

    public static void registrieren() {
        ServerLifecycleEvents.SERVER_STARTED.register(VillaPlatzierung::beimStart);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> aktuelleVilla = null);
        ServerTickEvents.END_SERVER_TICK.register(VillaPlatzierung::aufraeumen);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> beimBetreten(handler.getPlayer(), server));
    }

    /** Die Villa der laufenden Welt (für Butler, Mechanismen usw.), falls vorhanden. */
    public static Optional<VillaInfo> villa() {
        return Optional.ofNullable(aktuelleVilla);
    }

    private static void beimStart(MinecraftServer server) {
        Path datei = server.getWorldPath(LevelResource.ROOT).resolve(DATEI);
        try {
            if (Files.exists(datei)) {
                aktuelleVilla = VillaInfo.ausJson(GSON.fromJson(Files.readString(datei, StandardCharsets.UTF_8), JsonObject.class));
                return;
            }
            ServerLevel welt = server.overworld();
            JsonObject info = new JsonObject();
            if (welt.getGameTime() > 0 || !Konfig.get().villaBauen) {
                // Bestehende Welt (oder abgeschaltet): nichts verändern, nur merken
                info.addProperty("gebaut", false);
                info.addProperty("grund", welt.getGameTime() > 0 ? "bestehende Welt" : "in der Config abgeschaltet");
                StinkendReich.LOGGER.info("Villa wird nicht gebaut ({})", info.get("grund").getAsString());
            } else {
                long start = System.currentTimeMillis();
                aktuelleVilla = bauen(server, welt);
                info = aktuelleVilla.alsJson();
                StinkendReich.LOGGER.info("Villa gebaut bei {} in {} ms", aktuelleVilla.ursprung(), System.currentTimeMillis() - start);
            }
            Files.writeString(datei, GSON.toJson(info), StandardCharsets.UTF_8);
        } catch (Exception e) {
            StinkendReich.LOGGER.error("Villa konnte nicht gebaut werden", e);
        }
    }

    /** Beim allerersten Betreten einer Welt mit Villa: Spieler vor den Eingang stellen. */
    private static void beimBetreten(ServerPlayer spieler, MinecraftServer server) {
        if (aktuelleVilla == null || !Konfig.get().villaSpawnSetzen || Kompat.tags(spieler).contains(TAG_ANGEKOMMEN)) {
            return;
        }
        spieler.addTag(TAG_ANGEKOMMEN);
        // Willkommensgeschenk: eine Zugangskarte für Tore und Kartenleser
        spieler.getInventory().add(new net.minecraft.world.item.ItemStack(de.moinmornhart.stinkendreich.register.ModItems.ZUGANGSKARTE));
        BlockPos p = aktuelleVilla.spawn();
        Kompat.befehl(server, String.format(java.util.Locale.ROOT, "tp %s %.1f %d %.1f %.1f 0",
                spieler.getScoreboardName(), p.getX() + 0.5, p.getY(), p.getZ() + 0.5, aktuelleVilla.spawnBlick()));
    }

    // ------------------------------------------------------------------
    // Bauen
    // ------------------------------------------------------------------

    private static VillaInfo bauen(MinecraftServer server, ServerLevel welt) {
        VillaDaten plan = VillaDaten.get();
        BlockPos spawn = Kompat.spawnPunkt(welt);
        int x0 = spawn.getX() - plan.breiteX / 2;
        int z0 = spawn.getZ() - plan.breiteZ / 2;
        int rand = Math.max(4, Konfig.get().villaAnblendung);

        // Alle betroffenen Chunks laden (bzw. erzeugen)
        for (int cx = (x0 - rand) >> 4; cx <= (x0 + plan.breiteX + rand) >> 4; cx++) {
            for (int cz = (z0 - rand) >> 4; cz <= (z0 + plan.breiteZ + rand) >> 4; cz++) {
                welt.getChunk(cx, cz);
            }
        }

        // Zielhöhe: Median der Geländehöhe auf dem Grundstück
        int[] hoehen = new int[plan.breiteX * plan.breiteZ];
        int i = 0;
        for (int x = 0; x < plan.breiteX; x++) {
            for (int z = 0; z < plan.breiteZ; z++) {
                hoehen[i++] = bodenHoehe(welt, x0 + x, z0 + z);
            }
        }
        Arrays.sort(hoehen);
        int y0 = Math.max(hoehen[hoehen.length / 2], welt.getSeaLevel() + 1);
        y0 = Mth.clamp(y0, Kompat.minY(welt) + plan.bodenY + 2, Kompat.maxY(welt) - (plan.hoehe - plan.bodenY) - 2);

        gelaendeFormen(welt, x0, z0, plan.breiteX, plan.breiteZ, y0, rand);
        verwaisteBlaetterEntfernen(welt, x0, z0, plan.breiteX, plan.breiteZ, rand + 8);
        tiereEntfernen(welt, x0, y0, z0, plan);

        // Strukturdatei setzen: Grasschicht der Datei liegt auf y0 - 1
        BlockPos ursprung = new BlockPos(x0, y0 - 1 - plan.bodenY, z0);
        StructureTemplate vorlage = Kompat.strukturen(server).get(StinkendReich.id("villa/villa"))
                .orElseThrow(() -> new IllegalStateException("Strukturdatei villa/villa.nbt fehlt"));
        vorlage.placeInWorld(welt, ursprung, ursprung, new StructurePlaceSettings(), welt.getRandom(), Block.UPDATE_CLIENTS);

        VillaInfo villa = new VillaInfo(ursprung, plan);
        Kompat.setzeSpawnPunkt(welt, villa.spawn(), villa.spawnBlick());
        Kompat.befehl(server, Kompat.spawnRadiusBefehl(0));
        // Der Butler wartet in der Halle auf seinen Herrn
        JsonArray b = plan.ersteMarke("butler");
        ButlerVerwaltung.villaButlerErzeugen(welt, villa.weltPos(b.get(0).getAsDouble(), b.get(1).getAsDouble(), b.get(2).getAsDouble()));
        // Das Bonzenauto steht in der Garage, mit der Schnauze zum Tor
        JsonArray a = plan.ersteMarke("auto");
        de.moinmornhart.stinkendreich.auto.AutoEntity auto = de.moinmornhart.stinkendreich.register.ModEntities.AUTO.create(welt
                //? if >=1.21.2
                , net.minecraft.world.entity.EntitySpawnReason.STRUCTURE
        );
        if (auto != null) {
            auto.teleportTo(ursprung.getX() + a.get(0).getAsDouble(), ursprung.getY() + a.get(1).getAsDouble(), ursprung.getZ() + a.get(2).getAsDouble());
            auto.setYRot(0.0F);
            auto.setYHeadRot(0.0F);
            auto.yBodyRot = 0.0F;
            auto.setzeFarbe(4);
            welt.addFreshEntity(auto);
        }
        return villa;
    }

    /** Höhe des natürlichen Bodens (erste Luft über Erde/Stein), ohne Bäume und Pflanzen. */
    private static int bodenHoehe(ServerLevel welt, int x, int z) {
        int y = welt.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        int min = Kompat.minY(welt);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y, z);
        while (y > min) {
            p.setY(y - 1);
            BlockState s = welt.getBlockState(p);
            if (!istBewuchs(s) && s.getFluidState().isEmpty()) {
                break;
            }
            y--;
        }
        return y;
    }

    private static boolean istBewuchs(BlockState s) {
        return s.isAir() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.canBeReplaced()
                || s.is(BlockTags.FLOWERS) || s.is(Blocks.BAMBOO) || s.is(Blocks.CACTUS) || s.is(Blocks.SUGAR_CANE)
                || s.is(Blocks.BROWN_MUSHROOM_BLOCK) || s.is(Blocks.RED_MUSHROOM_BLOCK) || s.is(Blocks.MUSHROOM_STEM);
    }

    /**
     * Ebnet das Grundstück auf y0 und blendet ringsum über {@code rand} Blöcke weich ins Gelände über.
     * Bäume und Pflanzen auf der Fläche werden entfernt; Wasser außerhalb des Grundstücks bleibt Wasser.
     */
    private static void gelaendeFormen(ServerLevel welt, int x0, int z0, int bx, int bz, int y0, int rand) {
        BlockState gras = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState erde = Blocks.DIRT.defaultBlockState();
        BlockState stein = Blocks.STONE.defaultBlockState();
        BlockState luft = Blocks.AIR.defaultBlockState();
        BlockState wasser = Blocks.WATER.defaultBlockState();
        int flags = Block.UPDATE_CLIENTS;
        int meer = welt.getSeaLevel();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();

        for (int x = x0 - rand; x < x0 + bx + rand; x++) {
            for (int z = z0 - rand; z < z0 + bz + rand; z++) {
                // Abstand zum Grundstücksrand (0 = innen)
                int dx = Math.max(Math.max(x0 - x, x - (x0 + bx - 1)), 0);
                int dz = Math.max(Math.max(z0 - z, z - (z0 + bz - 1)), 0);
                double abstand = Math.sqrt(dx * dx + dz * dz);
                if (abstand >= rand) {
                    continue;
                }
                int boden = bodenHoehe(welt, x, z);
                double t = abstand / rand;
                double weich = t * t * (3 - 2 * t);
                int ziel = (int) Math.round(y0 + (boden - y0) * weich);
                int oben = Math.max(welt.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), ziel) + 1;

                // Auffüllen bis zur Zielhöhe
                for (int y = Math.min(boden, ziel) - 4; y < ziel; y++) {
                    p.set(x, y, z);
                    BlockState alt = welt.getBlockState(p);
                    BlockState neu = y == ziel - 1 ? gras : (y >= ziel - 4 ? erde : stein);
                    if (y < boden && !alt.isAir() && alt.getFluidState().isEmpty() && y < ziel - 1) {
                        continue; // vorhandenen Untergrund behalten
                    }
                    if (alt != neu) {
                        welt.setBlock(p, neu, flags);
                    }
                }
                // Alles darüber räumen (Bäume, Hügel); unter dem Meeresspiegel außerhalb bleibt Wasser
                for (int y = ziel; y <= oben; y++) {
                    p.set(x, y, z);
                    BlockState alt = welt.getBlockState(p);
                    if (alt.isAir()) {
                        continue;
                    }
                    boolean nass = abstand > 0 && y < meer && !alt.getFluidState().isEmpty();
                    welt.setBlock(p, nass ? wasser : luft, flags);
                }
            }
        }
    }

    /**
     * Blätter gefällter Bäume am Rand zerfallen noch eine Weile und lassen Stöcke, Setzlinge
     * und Äpfel fallen. Die werden in den ersten Minuten auf dem Grundstück weggeräumt.
     */
    private static void aufraeumen(MinecraftServer server) {
        long zeit = server.overworld().getGameTime();
        if (aktuelleVilla == null || zeit > AUFRAEUMEN_BIS || zeit % 20 != 0) {
            return;
        }
        BlockPos u = aktuelleVilla.ursprung();
        VillaDaten plan = aktuelleVilla.plan();
        int r = Konfig.get().villaAnblendung + 8;
        AABB bereich = new AABB(u.getX() - r, u.getY(), u.getZ() - r,
                u.getX() + plan.breiteX + r, u.getY() + plan.hoehe + 40, u.getZ() + plan.breiteZ + r);
        for (ItemEntity e : server.overworld().getEntitiesOfClass(ItemEntity.class, bereich, i -> true)) {
            var stapel = e.getItem();
            if (stapel.is(Items.STICK) || stapel.is(ItemTags.SAPLINGS) || stapel.is(Items.APPLE) || stapel.is(Items.WHEAT_SEEDS)) {
                e.discard();
            }
        }
    }

    /**
     * Blätter, deren Baumstamm beim Ebnen entfernt wurde, würden noch minutenlang zerfallen.
     * Alle nicht festen Blätter ohne Stamm in der Nähe werden deshalb gleich entfernt.
     */
    private static void verwaisteBlaetterEntfernen(ServerLevel welt, int x0, int z0, int bx, int bz, int rand) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState luft = Blocks.AIR.defaultBlockState();
        for (int x = x0 - rand; x < x0 + bx + rand; x++) {
            for (int z = z0 - rand; z < z0 + bz + rand; z++) {
                int oben = welt.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                for (int y = oben - 1; y > oben - 24; y--) {
                    p.set(x, y, z);
                    BlockState s = welt.getBlockState(p);
                    if (s.is(BlockTags.LEAVES) && !(s.hasProperty(LeavesBlock.PERSISTENT) && s.getValue(LeavesBlock.PERSISTENT))
                            && !stammInDerNaehe(welt, p)) {
                        welt.setBlock(p, luft, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    private static boolean stammInDerNaehe(ServerLevel welt, BlockPos mitte) {
        for (BlockPos q : BlockPos.betweenClosed(mitte.offset(-4, -4, -4), mitte.offset(4, 4, 4))) {
            if (welt.getBlockState(q).is(BlockTags.LOGS)) {
                return true;
            }
        }
        return false;
    }

    /** Entfernt Tiere, die nach dem Bauen in Wänden oder im Graben stecken würden. */
    private static void tiereEntfernen(ServerLevel welt, int x0, int y0, int z0, VillaDaten plan) {
        AABB bereich = new AABB(x0, y0 - plan.bodenY - 1, z0, x0 + plan.breiteX, y0 + plan.hoehe, z0 + plan.breiteZ);
        for (Entity e : welt.getEntitiesOfClass(Mob.class, bereich, m -> true)) {
            e.discard();
        }
    }

    /** Lage der gebauten Villa in einer Welt. */
    public record VillaInfo(BlockPos ursprung, VillaDaten plan) {
        public BlockPos weltPos(double lx, double ly, double lz) {
            return ursprung.offset((int) Math.floor(lx), (int) Math.floor(ly), (int) Math.floor(lz));
        }

        public BlockPos spawn() {
            JsonArray m = plan.ersteMarke("spawn");
            return weltPos(m.get(0).getAsDouble(), m.get(1).getAsDouble(), m.get(2).getAsDouble());
        }

        public float spawnBlick() {
            return plan.ersteMarke("spawn").get(3).getAsFloat();
        }

        /** Liegt die Position innerhalb des Grundstücks (inkl. Bunker)? */
        public boolean enthaelt(BlockPos pos) {
            return pos.getX() >= ursprung.getX() && pos.getX() < ursprung.getX() + plan.breiteX
                    && pos.getZ() >= ursprung.getZ() && pos.getZ() < ursprung.getZ() + plan.breiteZ
                    && pos.getY() >= ursprung.getY() && pos.getY() < ursprung.getY() + plan.hoehe;
        }

        JsonObject alsJson() {
            JsonObject j = new JsonObject();
            j.addProperty("gebaut", true);
            JsonArray u = new JsonArray();
            u.add(ursprung.getX());
            u.add(ursprung.getY());
            u.add(ursprung.getZ());
            j.add("ursprung", u);
            return j;
        }

        static VillaInfo ausJson(JsonObject j) {
            if (!j.has("gebaut") || !j.get("gebaut").getAsBoolean()) {
                return null;
            }
            JsonArray u = j.getAsJsonArray("ursprung");
            return new VillaInfo(new BlockPos(u.get(0).getAsInt(), u.get(1).getAsInt(), u.get(2).getAsInt()), VillaDaten.get());
        }
    }
}
