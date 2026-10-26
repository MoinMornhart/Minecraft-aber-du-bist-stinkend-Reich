package de.moinmornhart.stinkendreich.mechanik;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.register.ModSounds;
import de.moinmornhart.stinkendreich.villa.VillaPlatzierung;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.AABB;

/**
 * Inkognito-Modus: Die Villa verschwindet, falls der Bewohner als Geheimagent untertauchen muss. Über die
 * Smart-Home-App fährt das Haus Lage für Lage von oben nach unten in den Boden – mit Staubwolken und Kolbengeräuschen –,
 * bis nur noch eine Wiese übrig ist. Beim Hochfahren baut es sich Lage für Lage von unten wieder auf, mit allen Möbeln,
 * Truhen-Inhalten, Autos und Figuren. Der Bunker unter der Erde bleibt (der Notausgang führt weiter hinein).
 * <p>
 * Gespeichert wird die Villa als Strukturdateien im Weltordner (generated/stinkendreich/structures/inkognito/…):
 * eine Datei je Lage (nur Blöcke, für die Animation) und eine mit allem samt Figuren, die am Ende einmal gesetzt wird.
 * So übersteht das Versteck auch einen Neustart.
 */
public final class Inkognito {
    /** Lagen unterhalb der Grasschicht, die mitgespeichert werden (Graben, Pool): darin wird nur Flüssiges zu Erde. */
    private static final int UNTER_GRAS = 3;
    /** Ticks je Lage (Tempo der Animation). */
    private static final int TICKS_JE_LAGE = 4;

    /** Laufende Animation: true = verschwinden, false = auftauchen, null = keine. */
    private static Boolean runter;
    /** Nächste Lage der Animation (Bauplan-Höhe). */
    private static int lage;

    private Inkognito() {
    }

    public static void registrieren() {
        ServerTickEvents.END_SERVER_TICK.register(Inkognito::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> runter = null);
    }

    /** Ist die Villa versteckt oder fährt sie gerade? Dann ruhen Graben, Zugbrücke und Smart Home. */
    public static boolean aktiv(MinecraftServer server) {
        return runter != null || VillaZustand.get(server).smart("inkognito", false);
    }

    /** Befehl aus der Smart-Home-App oder von /villa inkognito: an = verschwinden, aus = auftauchen. */
    public static String schalten(MinecraftServer server, boolean an) {
        var villa = VillaPlatzierung.villa().orElse(null);
        if (villa == null) {
            return "stinkendreich.villa.keine";
        }
        if (runter != null) {
            return "stinkendreich.inkognito.laeuft";
        }
        VillaZustand z = VillaZustand.get(server);
        if (an == z.smart("inkognito", false)) {
            return null;                                         // ist schon so
        }
        ServerLevel welt = server.overworld();
        int unten = villa.plan().bodenY - UNTER_GRAS;
        if (an) {
            z.setzeStufe(0);                                     // keine Laser, Minen oder Lava im Versteck
            speichern(welt, villa);
            runter = true;
            lage = villa.plan().hoehe - 1;
        } else {
            // Wer auf der Wiese steht, kommt vor das Tor, damit niemand in den Wänden steckt
            for (ServerPlayer p : welt.players()) {
                if (bereich(villa).contains(p.position())) {
                    BlockPos s = villa.spawn();
                    p.teleportTo(s.getX() + 0.5, s.getY(), s.getZ() + 0.5);
                }
            }
            runter = false;
            lage = unten;
        }
        z.setzeSmart("inkognito", an);
        welt.playSound(null, villa.weltPos(villa.plan().breiteX / 2.0, villa.plan().bodenY, villa.plan().breiteZ / 2.0),
                SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 4.0F, an ? 0.6F : 1.2F);
        return null;
    }

    private static AABB bereich(VillaPlatzierung.VillaInfo villa) {
        BlockPos a = villa.weltPos(0, villa.plan().bodenY - UNTER_GRAS, 0);
        BlockPos b = villa.weltPos(villa.plan().breiteX, villa.plan().hoehe, villa.plan().breiteZ);
        return new AABB(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
    }

    private static Identifier datei(String name) {
        return StinkendReich.id("inkognito/" + name);
    }

    /** Speichert die Villa (alle Lagen einzeln ohne Figuren, dazu das Ganze mit Figuren) und nimmt die Figuren weg. */
    private static void speichern(ServerLevel welt, VillaPlatzierung.VillaInfo villa) {
        StructureTemplateManager dateien = Kompat.strukturen(welt.getServer());
        int unten = villa.plan().bodenY - UNTER_GRAS, x = villa.plan().breiteX, z = villa.plan().breiteZ;
        for (int y = unten; y < villa.plan().hoehe; y++) {
            erfassen(welt, dateien, "lage_" + y, villa.weltPos(0, y, 0), new Vec3i(x, 1, z), false);
        }
        erfassen(welt, dateien, "alles", villa.weltPos(0, unten, 0), new Vec3i(x, villa.plan().hoehe - unten, z), true);
        for (Entity e : welt.getEntitiesOfClass(Entity.class, bereich(villa), e -> !(e instanceof Player))) {
            e.discard();                                         // stecken jetzt in der Datei „alles“
        }
    }

    private static void erfassen(ServerLevel welt, StructureTemplateManager dateien, String name, BlockPos ecke, Vec3i groesse,
                                 boolean figuren) {
        StructureTemplate vorlage = dateien.getOrCreate(datei(name));
        //? if >=1.21.6 {
        vorlage.fillFromWorld(welt, ecke, groesse, figuren, java.util.List.of(Blocks.STRUCTURE_VOID));
        //?} else
        /*vorlage.fillFromWorld(welt, ecke, groesse, figuren, Blocks.STRUCTURE_VOID);*/
        dateien.save(datei(name));
    }

    private static void tick(MinecraftServer server) {
        if (runter == null || server.getTickCount() % TICKS_JE_LAGE != 0) {
            return;
        }
        var villa = VillaPlatzierung.villa().orElse(null);
        if (villa == null) {
            runter = null;
            return;
        }
        ServerLevel welt = server.overworld();
        int unten = villa.plan().bodenY - UNTER_GRAS;
        if (runter) {
            lageWeg(welt, villa, lage);
            if (--lage < unten) {
                runter = null;
                welt.playSound(null, villa.spawn(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 4.0F, 0.8F);
            }
        } else {
            lageHer(welt, villa, lage);
            if (++lage >= villa.plan().hoehe) {
                // Zum Schluss alles einmal komplett mit Möbel-Figuren, Autos, Fernsehern und Personal
                StructurePlaceSettings mitFiguren = new StructurePlaceSettings();
                Kompat.strukturen(server).get(datei("alles")).ifPresent(v -> v.placeInWorld(welt, villa.weltPos(0, unten, 0),
                        villa.weltPos(0, unten, 0), mitFiguren, welt.getRandom(), 2));
                runter = null;
                welt.playSound(null, villa.spawn(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 4.0F, 1.0F);
            }
        }
    }

    /** Eine Lage verschwinden lassen: oben Luft, auf Grashöhe Wiese, darunter nur Flüssiges zu Erde. */
    private static void lageWeg(ServerLevel welt, VillaPlatzierung.VillaInfo villa, int y) {
        int boden = villa.plan().bodenY;
        BlockState luft = Blocks.AIR.defaultBlockState();
        int geaendert = 0;
        for (int x = 0; x < villa.plan().breiteX; x++) {
            for (int z = 0; z < villa.plan().breiteZ; z++) {
                BlockPos p = villa.weltPos(x, y, z);
                BlockState alt = welt.getBlockState(p);
                BlockState neu;
                if (y > boden) {
                    if (alt.isAir()) {
                        continue;
                    }
                    neu = luft;
                } else if (y == boden) {
                    neu = Blocks.GRASS_BLOCK.defaultBlockState();
                } else {
                    if (alt.getFluidState().isEmpty()) {
                        continue;
                    }
                    neu = Blocks.DIRT.defaultBlockState();
                }
                if (welt.getBlockEntity(p) instanceof Clearable inhalt) {
                    inhalt.clearContent();                       // Inhalte stecken in der Datei, nichts fällt heraus
                }
                welt.setBlock(p, neu, 2 | 16 | 32);              // an Clients, ohne Nachbar-Updates, ohne Drops
                if (++geaendert % 9 == 0 && welt.getRandom().nextInt(3) == 0) {
                    welt.sendParticles(ParticleTypes.CLOUD, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 2, 0.4, 0.3, 0.4, 0.02);
                }
            }
        }
        if (geaendert > 0) {
            BlockPos mitte = villa.weltPos(villa.plan().breiteX / 2.0, y, villa.plan().breiteZ / 2.0);
            welt.playSound(null, mitte, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 3.0F, 0.5F + welt.getRandom().nextFloat() * 0.2F);
            if (y % 3 == 0) {
                welt.playSound(null, mitte, ModSounds.LASER, SoundSource.BLOCKS, 1.5F, 0.5F);
            }
        }
    }

    /** Eine Lage wieder auftauchen lassen (aus ihrer Datei, ohne Figuren) mit Funkeln. */
    private static void lageHer(ServerLevel welt, VillaPlatzierung.VillaInfo villa, int y) {
        BlockPos ecke = villa.weltPos(0, y, 0);
        var vorlage = Kompat.strukturen(welt.getServer()).get(datei("lage_" + y));
        if (vorlage.isEmpty()) {
            return;
        }
        StructurePlaceSettings ohneFiguren = new StructurePlaceSettings().setIgnoreEntities(true);
        vorlage.get().placeInWorld(welt, ecke, ecke, ohneFiguren, welt.getRandom(), 2 | 16);
        for (int i = 0; i < 40; i++) {
            double x = ecke.getX() + welt.getRandom().nextDouble() * villa.plan().breiteX;
            double z = ecke.getZ() + welt.getRandom().nextDouble() * villa.plan().breiteZ;
            welt.sendParticles(ParticleTypes.END_ROD, x, ecke.getY() + 1.1, z, 1, 0, 0.2, 0, 0.02);
        }
        BlockPos mitte = villa.weltPos(villa.plan().breiteX / 2.0, y, villa.plan().breiteZ / 2.0);
        welt.playSound(null, mitte, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 3.0F, 0.6F + welt.getRandom().nextFloat() * 0.2F);
    }

    /** Overlay-Hinweis für den Spieler (nicht im Chat). */
    static void melden(ServerPlayer p, boolean an) {
        Kompat.nachricht(p, Component.translatable(an ? "stinkendreich.inkognito.an" : "stinkendreich.inkognito.aus")
                .withStyle(ChatFormatting.DARK_AQUA), true);
    }
}
