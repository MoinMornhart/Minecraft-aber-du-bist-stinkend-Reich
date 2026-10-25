package de.moinmornhart.stinkendreich.musik;

import de.moinmornhart.stinkendreich.mechanik.Eigenschaften;
import de.moinmornhart.stinkendreich.mechanik.KlientBruecke;
import de.moinmornhart.stinkendreich.mechanik.MechanikBlock;
import de.moinmornhart.stinkendreich.netz.Netz;
import de.moinmornhart.stinkendreich.register.ModMusik;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Locale;

/**
 * Musikanlage (DJ-Pult): Rechtsklick öffnet das Menü mit den Reitern „Eigene Musik“ und „Spotify“.
 * Die Vorderseite mit den Reglern zeigt zum DJ, der sie aufstellt.
 */
public class MusikanlageBlock extends MechanikBlock {
    private static final VoxelShape FORM = Block.box(0, 0, 0, 16, 14, 16);
    /** Höchstlänge eines Titels im Paket (Schutz gegen Riesentexte). */
    private static final int TITEL_MAX = 200;

    public MusikanlageBlock(Properties eigenschaften) {
        super(eigenschaften, Ausrichtung.WAAGERECHT, false, false);
    }

    @Override
    protected Property<?>[] grundEigenschaften() {
        return new Property<?>[]{Eigenschaften.RICHTUNG};
    }

    @Override
    protected VoxelShape getShape(BlockState zustand, BlockGetter welt, BlockPos pos, CollisionContext kontext) {
        return FORM;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState zustand, Level welt, BlockPos pos, Player spieler, BlockHitResult treffer) {
        if (welt.isClientSide()) {
            KlientBruecke.musikanlage.accept(pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState zustand) {
        return new MusikanlageBlockEntity(pos, zustand);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level welt, BlockState zustand, BlockEntityType<T> typ) {
        if (welt.isClientSide() || typ != ModMusik.MUSIKANLAGE_TYP) {
            return null;
        }
        BlockEntityTicker<MusikanlageBlockEntity> ticker = (w, p, s, be) -> be.serverTakt();
        return (BlockEntityTicker<T>) (Object) ticker;
    }

    // Die Noten über Pult und Lautsprechern erzeugt MusikKlient im Client-Takt.

    // ---------------- Netzwerk ----------------

    /**
     * Aktionen aus dem Menü. Format: {@code befehl|wert…}
     * <ul>
     *   <li>„musik“ (nur in Reichweite des Pults): datei|index|dauer|titel, stopp, lautstaerke|n,
     *       zufall|0/1, wiederholen|AUS/ALLE/EINS, spotify|0/1|titel</li>
     *   <li>„musik_dj“ (aus jeder Entfernung, aber nur vom aktuellen DJ): datei…, stopp, spotify…
     *       – damit der nächste Titel auch startet, wenn der DJ gerade tanzen gegangen ist</li>
     * </ul>
     */
    public static void aktionenRegistrieren() {
        Netz.bearbeiter("musik", 8, (spieler, pos, wert) -> {
            if (spieler.level().getBlockEntity(pos) instanceof MusikanlageBlockEntity be) {
                ausfuehren(be, spieler, wert, true);
            }
        });
        Netz.bearbeiter("musik_dj", 0, (spieler, pos, wert) -> {
            // Nur geladene Anlagen; ein fremder Spieler darf so nichts steuern
            if (spieler.level().isLoaded(pos) && spieler.level().getBlockEntity(pos) instanceof MusikanlageBlockEntity be
                    && spieler.getUUID().equals(be.dj())) {
                ausfuehren(be, spieler, wert, false);
            }
        });
    }

    private static void ausfuehren(MusikanlageBlockEntity be, ServerPlayer spieler, String wert, boolean amPult) {
        String[] teil = wert.split("\\|", 4);
        try {
            switch (teil[0]) {
                case "datei" -> {
                    if (teil.length == 4) {
                        long dauer = Math.max(0, Math.min(20L * 60 * 60 * 3, Long.parseLong(teil[2])));
                        be.dateiStarten(spieler, Integer.parseInt(teil[1]), titelPruefen(teil[3]), dauer);
                    }
                }
                case "stopp" -> be.stoppen();
                case "spotify" -> {
                    // Der DJ meldet seinen Spotify-Stand; von fern nur, solange Spotify die Quelle ist
                    String[] s = wert.split("\\|", 3);
                    if (s.length == 3 && (amPult || MusikanlageBlockEntity.SPOTIFY.equals(be.quelle()))) {
                        be.spotifyMelden(spieler, "1".equals(s[1]), titelPruefen(s[2]));
                    }
                }
                case "lautstaerke" -> {
                    if (amPult && teil.length >= 2) {
                        be.setzeLautstaerke(Integer.parseInt(teil[1]));
                    }
                }
                case "zufall" -> {
                    if (amPult && teil.length >= 2) {
                        be.setzeZufall("1".equals(teil[1]));
                    }
                }
                case "wiederholen" -> {
                    if (amPult && teil.length >= 2) {
                        be.setzeWiederholen(MusikanlageBlockEntity.Wiederholen.valueOf(teil[1].toUpperCase(Locale.ROOT)));
                    }
                }
                default -> {
                }
            }
        } catch (IllegalArgumentException ignoriert) {
            // kaputtes Paket: nichts tun
        }
    }

    /** Kürzt und säubert einen Titel aus dem Netz (keine Steuerzeichen). */
    private static String titelPruefen(String titel) {
        String sauber = titel.replaceAll("\\p{Cntrl}", "").trim();
        return sauber.length() > TITEL_MAX ? sauber.substring(0, TITEL_MAX) : sauber;
    }
}
