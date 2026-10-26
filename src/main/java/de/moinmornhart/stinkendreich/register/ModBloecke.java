package de.moinmornhart.stinkendreich.register;

import de.moinmornhart.stinkendreich.Kompat;
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
import de.moinmornhart.stinkendreich.mechanik.TorfluegelBlock.Form;
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
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.MapColor;

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

    /** Torflügel: Feld eines Tores, einer Tür oder Abdeckung, das sich animiert öffnet (siehe {@link TorfluegelBlock}). */
    private static Block fluegel(String name, boolean offenAmAnfang, Form form, BlockBehaviour.Properties eigenschaften) {
        return block(name, p -> new TorfluegelBlock(p, offenAmAnfang, form), eigenschaften);
    }

    /** Grundeigenschaften der Türen; Wasser zählt Türen ohnehin als Sperre. */
    private static BlockBehaviour.Properties tuer(MapColor farbe, SoundType klang) {
        return BlockBehaviour.Properties.of().mapColor(farbe).sound(klang).noOcclusion().pushReaction(Kompat.ZERBRICHT_BEIM_SCHIEBEN);
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
    public static final Block TOR = fluegel("tor", false, Form.DUENN, technik().strength(50.0F, 1200.0F));
    public static final Block GARAGENTOR = fluegel("garagentor", false, Form.DUENN, technik().strength(50.0F, 1200.0F));
    public static final Block ROLLLADEN = fluegel("rollladen", true, Form.DUENN, technik().strength(50.0F, 1200.0F));
    public static final Block SCHIEBETUER = fluegel("schiebetuer", false, Form.DUENN, technik().sound(SoundType.GLASS).strength(50.0F, 1200.0F));
    /** Große Haustür: weiß-goldene Flügel mit Glas, die wie die Schiebetür automatisch zur Seite gleiten. */
    public static final Block PORTALTUER = fluegel("portaltuer", false, Form.DUENN, technik().sound(SoundType.WOOD).strength(50.0F, 1200.0F));
    public static final Block PANZERTUER = fluegel("panzertuer", true, Form.DUENN, technik().strength(80.0F, 3600000.0F));
    public static final Block TRESORTUER = fluegel("tresortuer", false, Form.DUENN, technik().strength(80.0F, 3600000.0F));
    public static final Block POOLABDECKUNG = fluegel("poolabdeckung", true, Form.FLACH, technik());
    public static final Block GEHEIMREGAL = fluegel("geheimregal", false, Form.VOLL,
            BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.WOOD).noOcclusion().forceSolidOn());
    public static final Block GEHEIMSCHALTER = block("geheimschalter", GeheimschalterBlock::new,
            BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.WOOD));

    // ---------- Aufzug, Tresor, Überwachung, Komfort ----------
    public static final Block AUFZUGSTAFEL = block("aufzugstafel", AufzugstafelBlock::new, technik());
    public static final Block FALLENPLATTE = block("fallenplatte", FallenplatteBlock::new,
            BlockBehaviour.Properties.of().strength(3.0F, 1200.0F).sound(SoundType.METAL).forceSolidOn());
    public static final Block GITTERFALLE = fluegel("gitterfalle", true, Form.DUENN, technik().strength(80.0F, 3600000.0F));
    public static final Block ENTSCHAERFKNOPF = block("entschaerfknopf", EntschaerfknopfBlock::new, stein());
    public static final Block KAMERA = block("kamera", KameraBlock::new, technik());
    public static final Block MONITOR = block("monitor", MonitorBlock::new, technik().lightLevel(s -> 6));
    public static final Block WHIRLPOOLDUESE = block("whirlpoolduese", WhirlpoolduseBlock::new, technik().lightLevel(s -> 10));
    public static final Block KAMINSTEUERUNG = block("kaminsteuerung", KaminsteuerungBlock::new, technik());
    /** Fährt die Zugbrücke über den Graben ein und aus (siehe {@link de.moinmornhart.stinkendreich.mechanik.Zugbruecke}). */
    public static final Block BRUECKENSCHALTER = block("brueckenschalter", de.moinmornhart.stinkendreich.mechanik.BrueckenschalterBlock::new, technik());
    /** Landmine im Rasen, nur in der Festung (Stufe 4) scharf (siehe {@link de.moinmornhart.stinkendreich.mechanik.LandmineBlock}). */
    public static final Block LANDMINE = block("landmine", de.moinmornhart.stinkendreich.mechanik.LandmineBlock::new,
            technik().noCollision().noOcclusion());
    /** Smart-Home-Hängelampe: an/aus und acht Farben über die Handy-App (siehe {@link de.moinmornhart.stinkendreich.mechanik.SmartHome}). */
    public static final Block SMARTLICHT = block("smartlicht", de.moinmornhart.stinkendreich.mechanik.SmartlichtBlock::new,
            BlockBehaviour.Properties.of().strength(0.5F).sound(SoundType.GLASS).noOcclusion()
                    .lightLevel(s -> s.getValue(Eigenschaften.LEUCHTET) ? 15 : 0));
    /** Versteckte Falltür am Ende des Notausgangs: sieht aus wie Rasen, nur mit Zugangskarte zu öffnen. */
    public static final Block GEHEIMLUKE = block("geheimluke", p -> new de.moinmornhart.stinkendreich.mechanik.GeheimlukeBlock(BlockSetType.IRON, p),
            BlockBehaviour.Properties.of().strength(50.0F, 3600000.0F).sound(SoundType.GRASS).noOcclusion()
                    .isValidSpawn((s, w, pos, typ) -> false));
    /** Abwehrgeschütz auf der Mauer, schießt in der Festung auf Monster und Eindringlinge. */
    public static final Block ABWEHRGESCHUETZ = block("abwehrgeschuetz", de.moinmornhart.stinkendreich.mechanik.AbwehrgeschuetzBlock::new,
            technik().noOcclusion().strength(50.0F, 3600000.0F));

    // ---------- Türen zum Aufklappen (wie Holz- und Eisentüren) ----------
    /** Weiß lackiert mit Goldbeschlägen, für Wohnräume. */
    public static final Block PRUNKTUER = ModRegister.tuer("prunktuer", BlockSetType.OAK, tuer(MapColor.SNOW, SoundType.WOOD).strength(3.0F));
    /** Glas im Goldrahmen, für Balkon und Dachterrasse. */
    public static final Block GLASTUER = ModRegister.tuer("glastuer", BlockSetType.CHERRY, tuer(MapColor.GOLD, SoundType.GLASS).strength(3.0F));
    /** Genietete Stahltür, lässt sich von Hand öffnen (Bunker, Technikräume). */
    public static final Block STAHLTUER = ModRegister.tuer("stahltuer", BlockSetType.COPPER,
            tuer(MapColor.METAL, SoundType.METAL).strength(5.0F).requiresCorrectToolForDrops());
    /** Bunkertür mit Schloss und Riegeln: nur per Redstone, Kartenleser oder Tastenfeld. */
    /** XXL-Kingsize-Bett (verbindet sich mit Nachbarn zu einer großen Liegefläche). */
    public static final Block KINGSIZE_BETT = ModRegister.bett("kingsize_bett", BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_RED).sound(SoundType.WOOL).strength(0.2F).noOcclusion()
            .pushReaction(Kompat.ZERBRICHT_BEIM_SCHIEBEN));
    public static final Block BUNKERTUER = ModRegister.tuer("bunkertuer", BlockSetType.IRON,
            tuer(MapColor.METAL, SoundType.METAL).strength(80.0F, 3600000.0F).requiresCorrectToolForDrops());
    /** Privattür: schwarz mit Gold, nur Bewohner öffnen und schließen sie (bleibt offen stehen). */
    public static final Block PRIVATTUER = ModRegister.tuer("privattuer", BlockSetType.OAK,
            tuer(MapColor.COLOR_BLACK, SoundType.WOOD).strength(6.0F, 1200.0F),
            de.moinmornhart.stinkendreich.mechanik.PrivattuerBlock::new);
    /** Sicherheitstür fürs Büro: Metall mit Goldkante, öffnet nur über den Kartenleser (Zugangskarte). */
    public static final Block SICHERHEITSTUER = ModRegister.tuer("sicherheitstuer", BlockSetType.IRON,
            tuer(MapColor.COLOR_GRAY, SoundType.METAL).strength(50.0F, 1200.0F).requiresCorrectToolForDrops(),
            de.moinmornhart.stinkendreich.mechanik.SicherheitstuerBlock::new);
    /** Banktür im Bunker: Tresorstahl mit goldenem Handrad, wie die Sicherheitstür nur mit Zugangskarte. */
    public static final Block BANKTUER = ModRegister.tuer("banktuer", BlockSetType.IRON,
            tuer(MapColor.METAL, SoundType.METAL).strength(80.0F, 3600000.0F).requiresCorrectToolForDrops(),
            de.moinmornhart.stinkendreich.mechanik.SicherheitstuerBlock::new);
    /** Clubtür: schwarzes Steppleder mit Goldknöpfen und lila Bullauge, öffnet von Hand. */
    public static final Block CLUBTUER = ModRegister.tuer("clubtuer", BlockSetType.DARK_OAK, tuer(MapColor.COLOR_BLACK, SoundType.WOOD).strength(3.0F));

    /** Eine gemeinsame Block-Entity für alle Mechanik-Blöcke. */
    public static final BlockEntityType<MechanikBlockEntity> MECHANIK_TYP = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            StinkendReich.id("mechanik"),
            FabricBlockEntityTypeBuilder.create(MechanikBlockEntity::new,
                    SICHERHEITSZENTRALE, LASER_SENDER, BEWEGUNGSMELDER, ALARMSIRENE, WARNLEUCHTE, KARTENLESER, TASTENFELD,
                    TUERKLINGEL, TORSTEUERUNG, GEHEIMSCHALTER, AUFZUGSTAFEL, FALLENPLATTE, ENTSCHAERFKNOPF, KAMERA, MONITOR,
                    WHIRLPOOLDUESE, KAMINSTEUERUNG, BRUECKENSCHALTER, LANDMINE, ABWEHRGESCHUETZ).build());

    private ModBloecke() {
    }

    public static void init() {
        // Laden der Klasse registriert alle Blöcke
    }
}
