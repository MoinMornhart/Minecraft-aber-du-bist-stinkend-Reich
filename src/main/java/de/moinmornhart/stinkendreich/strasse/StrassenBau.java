package de.moinmornhart.stinkendreich.strasse;

import de.moinmornhart.stinkendreich.butler.Schutz;
import de.moinmornhart.stinkendreich.butler.SpielerBloecke;
import de.moinmornhart.stinkendreich.villa.VillaPlatzierung;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Baut eine Straße Stück für Stück (in jedem Server-Tick einige Querschnitte, mit Zeitbudget, damit der Server
 * flüssig bleibt). Die Straße ist 7 Blöcke breit: grauer Asphalt, weiße Mittellinie in Strichen, Bordstein aus
 * glattem Stein, alle 32 Blöcke eine Laterne. Sie folgt sanft dem Gelände (höchstens ein Block Höhe auf drei
 * Blöcke, Übergänge mit Stufen), schneidet durch Hügel (nur natürliche Blöcke werden abgetragen, Bäume über der
 * Fahrbahn verschwinden), schüttet kleine Senken mit Steinziegeln auf und führt als Brücke mit Geländer und Pfeilern
 * über Wasser und tiefe Täler.
 *
 * <p>Nie angerührt werden: die Villa, Blöcke mit Inhalt (Truhen …) und alles, was nicht natürlich ist (Häuser im
 * Dorf, Spielerbauten). Liegt so etwas im Weg, biegt die Straße aus; kurz vor dem Ziel endet sie dort.</p>
 */
public final class StrassenBau {
    /** Halbe Breite: Mitte ± 3 Blöcke. */
    static final int HALB = 3;
    private static final int LATERNE_ALLE = 32;
    /** So weit darf die Fahrbahn über dem Boden liegen, bevor sie zur Brücke wird (darunter wird aufgeschüttet). */
    private static final int AUFSCHUETTEN_BIS = 6;
    /** Höchstens so viele Querschnitte je Tick und so viel Zeit (Nanosekunden). */
    private static final int PRO_TICK = 8;
    private static final long BUDGET = 25_000_000L;
    /** Stärkste Kurve je Block (Grad). */
    private static final float KURVE = 4.0F;

    // Beton über die ID (die Farbblöcke heißen im Code je nach Version anders)
    private static final BlockState ASPHALT = id("gray_concrete");
    private static final BlockState ASPHALT_STUFE = Blocks.POLISHED_ANDESITE_SLAB.defaultBlockState();
    private static final BlockState LINIE = id("white_concrete");
    private static final BlockState LINIE_STUFE = Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState();
    private static final BlockState BORD = Blocks.SMOOTH_STONE.defaultBlockState();
    private static final BlockState BORD_STUFE = Blocks.SMOOTH_STONE_SLAB.defaultBlockState();
    private static final BlockState UNTERBAU = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState GELAENDER = Blocks.STONE_BRICK_WALL.defaultBlockState();
    private static final BlockState MAST = Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState();
    private static final BlockState LAMPE = Blocks.LANTERN.defaultBlockState();

    private final ServerLevel welt;
    private final String name;
    private final Vec3 ziel;
    private final int eltern, abzweig;
    private final int maxSchritte;
    private double x, z;
    private float richtung;
    private int y;
    private int schritte, letzteAenderung;
    private boolean fertig;
    /** Mittellinie (Fahrbahnblock je Schritt). */
    private final List<BlockPos> punkte = new ArrayList<>();
    /** Spalten des letzten Querschnitts (für Stufen beim Höhenwechsel). */
    private List<long[]> letzterQuerschnitt = List.of();
    private final Map<Long, int[]> bodenCache = new HashMap<>();
    private final double startAbstand;

    StrassenBau(ServerLevel welt, String name, Vec3 ziel, Vec3 start, float richtung, int eltern, int abzweig) {
        this.welt = welt;
        this.name = name;
        this.ziel = ziel;
        this.x = start.x;
        this.z = start.z;
        this.richtung = richtung;
        this.eltern = eltern;
        this.abzweig = abzweig;
        this.y = (int) Math.floor(start.y) - 1;
        this.startAbstand = Math.hypot(ziel.x - x, ziel.z - z);
        this.maxSchritte = (int) (startAbstand * 1.8) + 200;
    }

    public String name() {
        return name;
    }

    public boolean fertig() {
        return fertig;
    }

    public List<BlockPos> punkte() {
        return punkte;
    }

    int eltern() {
        return eltern;
    }

    int abzweig() {
        return abzweig;
    }

    /** Fortschritt 0 … 100 (nach Entfernung zum Ziel). */
    public int prozent() {
        if (fertig) {
            return 100;
        }
        double rest = Math.hypot(ziel.x - x, ziel.z - z);
        return (int) Math.max(0, Math.min(99, 100 - rest * 100 / Math.max(1, startAbstand)));
    }

    // ------------------------------------------------------------------
    // Ablauf
    // ------------------------------------------------------------------

    void tick() {
        long start = System.nanoTime();
        for (int i = 0; i < PRO_TICK && !fertig && System.nanoTime() - start < BUDGET; i++) {
            schritt();
        }
    }

    private void schritt() {
        double rest = Math.hypot(ziel.x - x, ziel.z - z);
        if (rest < 5 || schritte > maxSchritte) {
            fertig = true;
            return;
        }
        // Richtung: sanft zum Ziel einlenken, Hindernissen (Villa, Häuser, Spielerbauten) ausweichen
        float zum = (float) Strassennetz.gier(x, z, ziel.x, ziel.z);
        float wunsch = richtung + clamp((float) Strassennetz.wrap(zum - richtung), -KURVE, KURVE);
        Float frei = null;
        for (float abw : new float[]{0, 15, -15, 30, -30, 50, -50, 75, -75}) {
            float r = wunsch + Math.copySign(Math.min(Math.abs(abw), 90), abw);
            if (frei(r)) {
                frei = r;
                break;
            }
        }
        if (frei == null) {
            // Kurz vor dem Ziel (etwa am Dorfrand) endet die Straße hier, sonst bricht der Bau ab
            fertig = true;
            return;
        }
        // Große Ausweichschwenks nur schrittweise fahren
        richtung = richtung + clamp((float) Strassennetz.wrap(frei - richtung), -3 * KURVE, 3 * KURVE);
        x += -Math.sin(Math.toRadians(richtung));
        z += Math.cos(Math.toRadians(richtung));

        // Höhe: Mittel des Geländes der nächsten Blöcke, höchstens ein Block Wechsel auf drei Blöcke
        double summe = 0;
        int n = 0;
        for (int d = 0; d <= 8; d += 2) {
            int bx = (int) Math.floor(x - Math.sin(Math.toRadians(richtung)) * d);
            int bz = (int) Math.floor(z + Math.cos(Math.toRadians(richtung)) * d);
            int[] b = boden(bx, bz);
            summe += b[1] == 1 ? b[0] + 1 : b[0];
            n++;
        }
        int soll = (int) Math.round(summe / n);
        int alt = y;
        if (schritte - letzteAenderung >= 3 && soll != y) {
            y += soll > y ? 1 : -1;
            letzteAenderung = schritte;
        }
        querschnitt(alt);
        schritte++;
    }

    /** Ist die Richtung frei (die nächsten zehn Blöcke kein geschützter Block in Fahrbahnhöhe)? */
    private boolean frei(float r) {
        double rx = -Math.sin(Math.toRadians(r)), rz = Math.cos(Math.toRadians(r));
        for (int d = 2; d <= 10; d += 2) {
            for (int o = -HALB; o <= HALB; o += HALB) {
                int bx = (int) Math.floor(x + rx * d + rz * o), bz = (int) Math.floor(z + rz * d - rx * o);
                for (int h = 0; h <= 4; h++) {
                    if (geschuetzt(new BlockPos(bx, y + h, bz))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Ein Querschnitt der Straße an der aktuellen Stelle; {@code alteHoehe} für die Stufe beim Höhenwechsel. */
    private void querschnitt(int alteHoehe) {
        double rx = -Math.sin(Math.toRadians(richtung)), rz = Math.cos(Math.toRadians(richtung));
        List<long[]> spalten = new ArrayList<>();
        BlockPos mitte = BlockPos.containing(x, y, z);
        punkte.add(mitte);
        boolean strich = (schritte / 3) % 2 == 0;
        // Breiter abtasten als die Straße (halbe Schritte), damit in Kurven keine Löcher bleiben
        for (double o = -HALB - 1; o <= HALB + 1; o += 0.5) {
            int bx = (int) Math.floor(x + rz * o), bz = (int) Math.floor(z - rx * o);
            int ganz = (int) Math.round(o);
            long schluessel = BlockPos.asLong(bx, 0, bz);
            if (spalten.stream().anyMatch(s -> s[0] == schluessel)) {
                continue;
            }
            spalten.add(new long[]{schluessel, ganz});
            spalte(bx, bz, ganz, strich);
        }
        // Höhenwechsel: halbe Stufen auf der tieferen Seite
        if (y > alteHoehe) {
            for (long[] s : letzterQuerschnitt) {
                stufe(BlockPos.getX(s[0]), alteHoehe + 1, BlockPos.getZ(s[0]), (int) s[1]);
            }
        } else if (y < alteHoehe) {
            for (long[] s : spalten) {
                stufe(BlockPos.getX(s[0]), y + 1, BlockPos.getZ(s[0]), (int) s[1]);
            }
        }
        letzterQuerschnitt = spalten;
        // Laterne am Rand, abwechselnd links und rechts
        if (schritte % LATERNE_ALLE == LATERNE_ALLE / 2) {
            int seite = (schritte / LATERNE_ALLE) % 2 == 0 ? 1 : -1;
            int lx = (int) Math.floor(x + rz * (HALB + 1) * seite), lz = (int) Math.floor(z - rx * (HALB + 1) * seite);
            laterne(lx, lz);
        }
    }

    /** Eine Spalte: Fahrbahn (o = Abstand von der Mitte, ±4 = Seitenstreifen), darunter Unterbau, darüber frei. */
    private void spalte(int bx, int bz, int o, boolean strich) {
        int[] b = boden(bx, bz);
        int grund = b[0];
        boolean wasser = b[1] == 1;
        boolean fahrbahn = Math.abs(o) <= HALB;
        boolean bruecke = wasser || y - grund > AUFSCHUETTEN_BIS;
        if (fahrbahn) {
            BlockState belag = Math.abs(o) == HALB ? BORD : (o == 0 && strich ? LINIE : ASPHALT);
            setzen(new BlockPos(bx, y, bz), belag);
            if (bruecke) {
                // Geländer am Rand, Pfeiler alle acht Blöcke bis zum Grund
                if (Math.abs(o) == HALB) {
                    setzen(new BlockPos(bx, y + 1, bz), GELAENDER);
                    if (schritte % 8 == 0) {
                        for (int h = y - 1; h >= Math.max(grund - (wasser ? 8 : 0), y - 64); h--) {
                            if (!setzen(new BlockPos(bx, h, bz), UNTERBAU)) {
                                break;
                            }
                        }
                    }
                }
            } else {
                for (int h = y - 1; h > grund; h--) {
                    setzen(new BlockPos(bx, h, bz), UNTERBAU);
                }
                // Böschung: Grasblock statt Erde, wenn der Boden auf Höhe der Fahrbahn lag
            }
        } else if (!bruecke && grund >= y) {
            // Seitenstreifen: Gelände auf Fahrbahnhöhe abtragen, oben Gras
            for (int h = grund; h > y; h--) {
                abtragen(new BlockPos(bx, h, bz));
            }
            BlockPos oben = new BlockPos(bx, y, bz);
            if (Schutz.istNatuerlich(welt.getBlockState(oben)) && !geschuetzt(oben)) {
                setzen(oben, Blocks.GRASS_BLOCK.defaultBlockState());
            }
        }
        // Darüber freiräumen (Bäume über der Fahrbahn ganz)
        for (int h = y + 1 + (fahrbahn && bruecke && Math.abs(o) == HALB ? 1 : 0); h <= y + 6; h++) {
            abtragen(new BlockPos(bx, h, bz));
        }
        if (fahrbahn) {
            for (int h = y + 7; h <= y + 20; h++) {
                BlockPos p = new BlockPos(bx, h, bz);
                BlockState s = welt.getBlockState(p);
                if (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS)) {
                    abtragen(p);
                }
            }
        }
    }

    /** Halbe Stufe aus dem Material der Spalte (für sanfte Höhenwechsel). */
    private void stufe(int bx, int sy, int bz, int o) {
        BlockState s = Math.abs(o) > HALB ? null : Math.abs(o) == HALB ? BORD_STUFE : (o == 0 ? LINIE_STUFE : ASPHALT_STUFE);
        BlockPos p = new BlockPos(bx, sy, bz);
        if (s != null && welt.getBlockState(p).isAir()) {
            setzen(p, s.setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        }
    }

    private void laterne(int bx, int bz) {
        BlockPos fuss = new BlockPos(bx, y, bz);
        if (geschuetzt(fuss)) {
            return;
        }
        setzen(fuss, BORD);
        for (int h = y - 1; h > boden(bx, bz)[0] && h > y - 8; h--) {
            setzen(new BlockPos(bx, h, bz), UNTERBAU);
        }
        for (int h = 1; h <= 3; h++) {
            setzen(fuss.above(h), MAST);
        }
        setzen(fuss.above(4), LAMPE);
    }

    // ------------------------------------------------------------------
    // Blöcke
    // ------------------------------------------------------------------

    /**
     * Höhe des Bodens in der Spalte: oberster fester Block ohne Blätter, Stämme und Pflanzen. [0] = Höhe,
     * [1] = 1, wenn dort Wasser oder Lava ist (dann ist [0] die Oberfläche).
     */
    private int[] boden(int bx, int bz) {
        return bodenCache.computeIfAbsent(BlockPos.asLong(bx, 0, bz), k -> {
            int top = welt.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) - 1;
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(bx, top, bz);
            for (int h = top; h > top - 48; h--) {
                m.setY(h);
                BlockState s = welt.getBlockState(m);
                if (!s.getFluidState().isEmpty() && !s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)) {
                    return new int[]{h, 1};
                }
                if (s.isAir() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.canBeReplaced() || s.getCollisionShape(welt, m).isEmpty()) {
                    continue;
                }
                return new int[]{h, 0};
            }
            return new int[]{top, 0};
        });
    }

    /** Darf hier nichts verändert werden? Villa, Blöcke mit Inhalt, Gesetztes und alles Gebaute. */
    private boolean geschuetzt(BlockPos p) {
        if (VillaPlatzierung.villa().map(v -> v.enthaelt(p)).orElse(false) || SpielerBloecke.istGesetzt(welt, p)
                || welt.getBlockEntity(p) != null) {
            return true;
        }
        BlockState s = welt.getBlockState(p);
        return !s.isAir() && s.getFluidState().isEmpty() && !Schutz.istNatuerlich(s) && !istStrasse(s);
    }

    /** Teil einer (anderen) Straße: darf überbaut werden (Abzweigungen, Kreuzungen). */
    private static boolean istStrasse(BlockState s) {
        return s.is(ASPHALT.getBlock()) || s.is(LINIE.getBlock()) || s.is(BORD.getBlock()) || s.is(UNTERBAU.getBlock())
                || s.is(GELAENDER.getBlock()) || s.is(ASPHALT_STUFE.getBlock()) || s.is(LINIE_STUFE.getBlock())
                || s.is(BORD_STUFE.getBlock()) || s.is(MAST.getBlock()) || s.is(LAMPE.getBlock());
    }

    /** Setzt einen Block, wenn die Stelle nicht geschützt ist. */
    private boolean setzen(BlockPos p, BlockState s) {
        if (geschuetzt(p)) {
            return false;
        }
        welt.setBlock(p, s, Block.UPDATE_CLIENTS);
        return true;
    }

    /** Räumt einen natürlichen Block weg (Wasser bleibt, die Straße führt dann als Brücke darüber). */
    private void abtragen(BlockPos p) {
        BlockState s = welt.getBlockState(p);
        if (s.isAir() || !s.getFluidState().isEmpty() || geschuetzt(p) || istStrasse(s)) {
            return;
        }
        welt.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private static BlockState id(String name) {
        return de.moinmornhart.stinkendreich.Kompat.blockNachId(net.minecraft.resources.Identifier.withDefaultNamespace(name)).defaultBlockState();
    }

    private static float clamp(float v, float a, float b) {
        return Math.max(a, Math.min(b, v));
    }

    // ------------------------------------------------------------------
    // Kreisel
    // ------------------------------------------------------------------

    /**
     * Kreisel vor dem Tor: Fahrbahn als Ring (gestrichelte Linie in der Mitte), Bordstein innen und außen, Insel mit
     * Rasen, Blumen und einer goldenen Säule mit Licht, dazu die Zufahrt nach Norden bis an die Brücke der Villa.
     */
    static void kreiselBauen(ServerLevel welt, Vec3 mitte) {
        int y = (int) Math.floor(mitte.y);
        StrassenBau b = new StrassenBau(welt, "Kreisel", mitte, mitte, 0, -1, 0);
        b.y = y;
        int r = (int) Math.ceil(Strassennetz.AUSSEN) + 2;
        int mx = (int) Math.floor(mitte.x), mz = (int) Math.floor(mitte.z);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int bx = mx + dx, bz = mz + dz;
                double d = Math.hypot(bx + 0.5 - mitte.x, bz + 0.5 - mitte.z);
                BlockPos p = new BlockPos(bx, y, bz);
                if (d > Strassennetz.AUSSEN + 1.5) {
                    continue;
                }
                int[] boden = b.boden(bx, bz);
                for (int h = y - 1; h > boden[0] && h > y - AUFSCHUETTEN_BIS; h--) {
                    b.setzen(new BlockPos(bx, h, bz), UNTERBAU);
                }
                for (int h = y + 1; h <= y + 6; h++) {
                    b.abtragen(new BlockPos(bx, h, bz));
                }
                if (d > Strassennetz.AUSSEN + 0.5) {
                    b.setzen(p, Blocks.GRASS_BLOCK.defaultBlockState());
                } else if (d > Strassennetz.AUSSEN - 0.5 || (d > Strassennetz.INSEL - 0.5 && d <= Strassennetz.INSEL + 0.5)) {
                    b.setzen(p, BORD);
                } else if (d > Strassennetz.INSEL + 0.5) {
                    // Gestrichelte Linie zwischen den beiden Spuren
                    double winkel = Math.toDegrees(Math.atan2(bz + 0.5 - mitte.z, bx + 0.5 - mitte.x));
                    boolean linie = Math.abs(d - Strassennetz.SPUR) < 0.5 && ((int) Math.floor((winkel + 180) / 12)) % 2 == 0;
                    b.setzen(p, linie ? LINIE : ASPHALT);
                } else {
                    b.setzen(p, Blocks.GRASS_BLOCK.defaultBlockState());
                    if (d > 1.5 && (dx + dz) % 2 == 0) {
                        b.setzen(p.above(), ((dx * 7 + dz * 3) & 3) == 0 ? Blocks.POPPY.defaultBlockState()
                                : ((dx * 7 + dz * 3) & 3) == 1 ? Blocks.CORNFLOWER.defaultBlockState() : Blocks.OXEYE_DAISY.defaultBlockState());
                    }
                }
            }
        }
        // Goldene Säule mit Licht in der Mitte der Insel
        BlockPos m = BlockPos.containing(mitte.x, y, mitte.z);
        for (BlockPos q : new BlockPos[]{m, m.west(), m.north(), m.west().north()}) {
            b.setzen(q, Blocks.SMOOTH_QUARTZ.defaultBlockState());
            for (int h = 1; h <= 3; h++) {
                b.setzen(q.above(h), h == 3 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.GOLD_BLOCK.defaultBlockState());
            }
        }
        // Zufahrt nach Norden bis an die Brücke (8 breit wie die Brücke, mittig auf der Achse)
        VillaPlatzierung.villa().ifPresent(v -> {
            int vonZ = v.ursprung().getZ() + v.plan().breiteZ;
            int bisZ = (int) Math.floor(mitte.z - Strassennetz.AUSSEN) + 1;
            for (int bz = vonZ; bz <= bisZ; bz++) {
                for (int ox = -4; ox <= 3; ox++) {
                    int bx = (int) Math.floor(mitte.x) + ox;
                    BlockPos p = new BlockPos(bx, y, bz);
                    int[] boden = b.boden(bx, bz);
                    for (int h = y - 1; h > boden[0] && h > y - AUFSCHUETTEN_BIS; h--) {
                        b.setzen(new BlockPos(bx, h, bz), UNTERBAU);
                    }
                    for (int h = y + 1; h <= y + 6; h++) {
                        b.abtragen(new BlockPos(bx, h, bz));
                    }
                    b.setzen(p, ox == -4 || ox == 3 ? BORD : ASPHALT);
                }
            }
        });
    }
}
