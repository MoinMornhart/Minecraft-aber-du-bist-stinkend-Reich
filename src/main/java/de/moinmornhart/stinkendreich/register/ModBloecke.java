package de.moinmornhart.stinkendreich.register;

import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.mechanik.AlarmsireneBlock;
import de.moinmornhart.stinkendreich.mechanik.AufzugstafelBlock;
import de.moinmornhart.stinkendreich.mechanik.BewegungsmelderBlock;
import de.moinmornhart.stinkendreich.mechanik.Eigenschaften;
import de.moinmornhart.stinkendreich.mechanik.EntschaerfknopfBlock;
import de.moinmornhart.stinkendreich.mechanik.FallenplatteBlock;
import de.moinmornhart.stinkendreich.mechanik.GeheimschalterBlock;
import de.moinmornhart.stinkendreich.mechanik.KameraBlock;
import de.moinmornhart.stinkendreich.mechanik.KaminsteuerungBlock;
import de.moinmornhart.stinkendreich.mechanik.KartenleserBlock;
import de.moinmornhart.stinkendreich.mechanik.LaserSenderBlock;
import de.moinmornhart.stinkendreich.mechanik.LaserstrahlBlock;
import de.moinmornhart.stinkendreich.mechanik.MechanikBlockEntity;
import de.moinmornhart.stinkendreich.mechanik.MonitorBlock;
import de.moinmornhart.stinkendreich.mechanik.SicherheitszentraleBlock;
import de.moinmornhart.stinkendreich.mechanik.TastenfeldBlock;
import de.moinmornhart.stinkendreich.mechanik.TorfluegelBlock;
import de.moinmornhart.stinkendreich.mechanik.TorsteuerungBlock;
import de.moinmornhart.stinkendreich.mechanik.TuerklingelBlock;
import de.moinmornhart.stinkendreich.mechanik.WarnleuchteBlock;
import de.moinmornhart.stinkendreich.mechanik.WhirlpoolduseBlock;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import static de.moinmornhart.stinkendreich.register.ModRegister.block;

/** Alle Blöcke der Mod. */
public final class ModBloecke {
    /*
     * forceSolidOn: Für Wasser und Lava gelten diese Blöcke als fest, auch Wandplatten und offene Tore.
     * Sonst flösse Wasser hinein, spülte den Block weg und liefe weiter (z. B. durch eine offene
     * Geheimtür in den Bunker). Laufen kann man durch offene Tore trotzdem, das regelt die Form.
     */
    private static BlockBehaviour.Properties technik() {
        return BlockBehaviour.Properties.of().strength(3.0F, 1200.0F).sound(SoundType.METAL).noOcclusion().forceSolidOn();
    }

    private static BlockBehaviour.Properties stein() {
        return BlockBehaviour.Properties.of().strength(3.0F, 1200.0F).sound(SoundType.DEEPSLATE_BRICKS).forceSolidOn();
    }

    // ---------- Sicherheitssystem ----------
    public static final Block SICHERHEITSZENTRALE = block("sicherheitszentrale", SicherheitszentraleBlock::new, technik());
    public static final Block LASER_SENDER = block("laser_sender", LaserSenderBlock::new, technik().lightLevel(s -> 4));
    public static final Block LASERSTRAHL = block("laserstrahl", LaserstrahlBlock::new,
            BlockBehaviour.Properties.of().noCollision().noLootTable().noOcclusion().strength(-1.0F, 3600000.0F).lightLevel(s -> 9), false);
    public static final Block BEWEGUNGSMELDER = block("bewegungsmelder", BewegungsmelderBlock::new, technik());
    public static final Block ALARMSIRENE = block("alarmsirene", AlarmsireneBlock::new, technik());
    public static final Block WARNLEUCHTE = block("warnleuchte", WarnleuchteBlock::new,
            technik().lightLevel(s -> s.getValue(Eigenschaften.LEUCHTET) ? 15 : 0));

    // ---------- Zugang ----------
    public static final Block KARTENLESER = block("kartenleser", KartenleserBlock::new, technik());
    public static final Block TASTENFELD = block("tastenfeld", TastenfeldBlock::new, technik());
    public static final Block TUERKLINGEL = block("tuerklingel", TuerklingelBlock::new, technik());
    public static final Block TORSTEUERUNG = block("torsteuerung", TorsteuerungBlock::new, technik());
    public static final Block TOR = block("tor", p -> new TorfluegelBlock(p, false, false), technik().strength(50.0F, 1200.0F));
    public static final Block GARAGENTOR = block("garagentor", p -> new TorfluegelBlock(p, false, false), technik().strength(50.0F, 1200.0F));
    public static final Block ROLLLADEN = block("rollladen", p -> new TorfluegelBlock(p, true, false), technik().strength(50.0F, 1200.0F));
    public static final Block SCHIEBETUER = block("schiebetuer", p -> new TorfluegelBlock(p, false, false),
            technik().sound(SoundType.GLASS).strength(50.0F, 1200.0F));
    public static final Block PANZERTUER = block("panzertuer", p -> new TorfluegelBlock(p, true, false), technik().strength(80.0F, 3600000.0F));
    public static final Block POOLABDECKUNG = block("poolabdeckung", p -> new TorfluegelBlock(p, true, true), technik());
    public static final Block GEHEIMREGAL = block("geheimregal", p -> new TorfluegelBlock(p, false, false),
            BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.WOOD).noOcclusion().forceSolidOn());
    public static final Block GEHEIMSCHALTER = block("geheimschalter", GeheimschalterBlock::new,
            BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.WOOD));

    // ---------- Aufzug, Tresor, Überwachung, Komfort ----------
    public static final Block AUFZUGSTAFEL = block("aufzugstafel", AufzugstafelBlock::new, technik());
    public static final Block FALLENPLATTE = block("fallenplatte", FallenplatteBlock::new,
            BlockBehaviour.Properties.of().strength(3.0F, 1200.0F).sound(SoundType.METAL).forceSolidOn());
    public static final Block GITTERFALLE = block("gitterfalle", p -> new TorfluegelBlock(p, true, false), technik().strength(80.0F, 3600000.0F));
    public static final Block ENTSCHAERFKNOPF = block("entschaerfknopf", EntschaerfknopfBlock::new, stein());
    public static final Block KAMERA = block("kamera", KameraBlock::new, technik());
    public static final Block MONITOR = block("monitor", MonitorBlock::new, technik().lightLevel(s -> 6));
    public static final Block WHIRLPOOLDUESE = block("whirlpoolduese", WhirlpoolduseBlock::new, technik().lightLevel(s -> 10));
    public static final Block KAMINSTEUERUNG = block("kaminsteuerung", KaminsteuerungBlock::new, technik());

    /** Eine gemeinsame Block-Entity für alle Mechanik-Blöcke. */
    public static final BlockEntityType<MechanikBlockEntity> MECHANIK_TYP = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            StinkendReich.id("mechanik"),
            FabricBlockEntityTypeBuilder.create(MechanikBlockEntity::new,
                    SICHERHEITSZENTRALE, LASER_SENDER, BEWEGUNGSMELDER, ALARMSIRENE, WARNLEUCHTE, KARTENLESER, TASTENFELD,
                    TUERKLINGEL, TORSTEUERUNG, GEHEIMSCHALTER, AUFZUGSTAFEL, FALLENPLATTE, ENTSCHAERFKNOPF, KAMERA, MONITOR,
                    WHIRLPOOLDUESE, KAMINSTEUERUNG).build());

    private ModBloecke() {
    }

    public static void init() {
        // Laden der Klasse registriert alle Blöcke
    }
}
