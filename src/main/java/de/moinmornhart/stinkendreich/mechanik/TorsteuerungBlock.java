package de.moinmornhart.stinkendreich.mechanik;

import de.moinmornhart.stinkendreich.register.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Steuert alle Torflügel eines Typs im Umkreis. Modi (in der Block-Entity, vom Bauplan gesetzt):
 * <ul>
 *   <li>{@code tor} – öffnet für Bewohner und Kartenträger in der Nähe; bei Stufe 0 immer offen</li>
 *   <li>{@code rollladen} – ab Stufe 2 geschlossen (Lockdown)</li>
 *   <li>{@code schiebetuer} – öffnet, wenn jemand davorsteht (ab Stufe 2 nur für Berechtigte)</li>
 *   <li>{@code schalter} – wechselt bei Rechtsklick oder Redstone-Signal</li>
 *   <li>{@code redstone} – offen, solange ein Redstone-Signal anliegt (z. B. vom Tastenfeld)</li>
 *   <li>{@code redstone_invers} – geschlossen, solange ein Signal anliegt (z. B. Hebel der Poolabdeckung)</li>
 * </ul>
 * Die Flügel werden nur umgeschaltet, wenn sich der gewünschte Zustand ändert (und zur Sicherheit
 * etwa alle 10 Sekunden), damit auch große Flächen wie Rollläden keine Last erzeugen.
 */
public class TorsteuerungBlock extends MechanikBlock {
    public TorsteuerungBlock(Properties eigenschaften) {
        super(eigenschaften, Ausrichtung.KEINE, false, false);
    }

    @Override
    protected Property<?>[] grundEigenschaften() {
        return new Property<?>[0];
    }

    @Override
    public int takt() {
        return 5;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState zustand, Level welt, BlockPos pos, Player spieler, BlockHitResult treffer) {
        MechanikBlockEntity d = daten(welt, pos);
        if (!welt.isClientSide() && d != null && d.text("modus", "schalter").equals("schalter")) {
            d.setze("offen", d.zahl("offen", 0) == 1 ? 0 : 1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void takt(ServerLevel welt, BlockPos pos, BlockState zustand, MechanikBlockEntity d) {
        VillaZustand z = VillaZustand.get(welt.getServer());
        String modus = d.text("modus", "schalter");
        int radius = d.zahl("radius", 6);
        boolean offen;
        switch (modus) {
            case "tor" -> offen = z.stufe() == 0 || berechtigteInDerNaehe(welt, pos, radius + 4, z);
            case "rollladen" -> offen = z.stufe() < 2;
            case "redstone" -> offen = welt.hasNeighborSignal(pos);
            case "redstone_invers" -> offen = !welt.hasNeighborSignal(pos);
            case "schiebetuer" -> {
                AABB nah = new AABB(pos).inflate(radius);
                offen = z.stufe() < 2
                        ? !welt.getEntitiesOfClass(Player.class, nah, p -> !p.isSpectator()).isEmpty()
                                || !welt.getEntitiesOfClass(ButlerEntity.class, nah, b -> true).isEmpty()
                        : berechtigteInDerNaehe(welt, pos, radius, z);
            }
            default -> {
                // Schalter: Redstone-Flanke wechselt den Zustand
                boolean signal = welt.hasNeighborSignal(pos);
                if (signal && d.zahl("signal", 0) == 0) {
                    d.setze("offen", d.zahl("offen", 0) == 1 ? 0 : 1);
                }
                if (signal != (d.zahl("signal", 0) == 1)) {
                    d.setze("signal", signal ? 1 : 0);
                }
                offen = d.zahl("offen", 0) == 1;
            }
        }
        int soll = offen ? 1 : 0;
        int pruefung = d.zahl("pruefung", 0) + 1;
        if (soll == d.zahl("zuletzt", -1) && pruefung < 40) {
            d.setzeStill("pruefung", pruefung);
            return;
        }
        d.setzeStill("pruefung", 0);
        d.setze("zuletzt", soll);
        Block ziel = de.moinmornhart.stinkendreich.Kompat.blockNachId(Identifier.parse(d.text("ziel", "stinkendreich:tor")));
        if (umschalten(welt, pos, radius, ziel, offen)) {
            welt.playSound(null, pos, ModSounds.TRESOR, SoundSource.BLOCKS, 0.6F, offen ? 1.3F : 1.0F);
        }
    }

    private static boolean berechtigteInDerNaehe(ServerLevel welt, BlockPos pos, int radius, VillaZustand z) {
        AABB nah = new AABB(pos).inflate(radius);
        // Bewohner, Gäste mit Zugangskarte und der Butler dürfen durch
        return !welt.getEntitiesOfClass(Player.class, nah, p -> !p.isSpectator() && (z.istBewohner(p) || Zugang.hatKarte(p))).isEmpty()
                || !welt.getEntitiesOfClass(ButlerEntity.class, nah, b -> true).isEmpty();
    }

    /** Setzt alle Flügel des Typs im Umkreis; true, wenn sich etwas geändert hat. */
    public static boolean umschalten(ServerLevel welt, BlockPos mitte, int radius, Block typ, boolean offen) {
        boolean geaendert = false;
        for (BlockPos p : BlockPos.betweenClosed(mitte.offset(-radius, -radius, -radius), mitte.offset(radius, radius, radius))) {
            BlockState s = welt.getBlockState(p);
            if (s.is(typ) && s.getValue(Eigenschaften.OFFEN) != offen) {
                welt.setBlock(p, s.setValue(Eigenschaften.OFFEN, offen), 3);
                geaendert = true;
            }
        }
        return geaendert;
    }
}
