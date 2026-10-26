package de.moinmornhart.stinkendreich.mechanik;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.netz.Netz;
import de.moinmornhart.stinkendreich.register.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Sicherheitszentrale: Stufe 0 bis 3 wählen, Alarm abschalten, Bewohner eintragen.
 * Bedienung über einen Bildschirm; nur Bewohner dürfen etwas ändern.
 */
public class SicherheitszentraleBlock extends MechanikBlock {
    public SicherheitszentraleBlock(Properties eigenschaften) {
        super(eigenschaften, Ausrichtung.WAAGERECHT, false, false);
    }

    @Override
    protected Property<?>[] grundEigenschaften() {
        return new Property<?>[]{Eigenschaften.RICHTUNG};
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState zustand, Level welt, BlockPos pos, Player spieler, BlockHitResult treffer) {
        if (welt.isClientSide()) {
            KlientBruecke.zentrale.accept(pos);
        } else {
            VillaZustand z = VillaZustand.fuer(welt.getServer(), pos);
            Kompat.nachricht(spieler, Component.translatable("stinkendreich.zentrale.status",
                    Component.translatable("stinkendreich.stufe." + z.stufe()),
                    Component.translatable(z.alarmAktiv() ? "stinkendreich.alarm.an" : "stinkendreich.alarm.aus"),
                    String.join(", ", z.bewohner().values())).withStyle(ChatFormatting.AQUA), true);   // nicht im Chat
            de.moinmornhart.stinkendreich.netz.OhneMod.zeigen(spieler, "zentrale", pos);
        }
        return InteractionResult.SUCCESS;
    }

    /** Meldet die Aktionen der Zentrale beim Netzwerk an. */
    public static void aktionenRegistrieren() {
        Netz.bearbeiter("zentrale", 8, (spieler, pos, wert) -> {
            if (!(spieler.level().getBlockState(pos).getBlock() instanceof SicherheitszentraleBlock)) {
                return;
            }
            VillaZustand z = VillaZustand.fuer(spieler.level().getServer(), pos);
            boolean darf = z.istBewohner(spieler);
            if (wert.equals("eintragen")) {
                // Neue Bewohner nur, wenn das System aus ist oder ein Bewohner sich selbst einträgt
                if (darf || z.stufe() == 0) {
                    z.bewohnerHinzufuegen(spieler);
                    Kompat.nachricht(spieler, Component.translatable("stinkendreich.zentrale.eingetragen").withStyle(ChatFormatting.GREEN), true);
                    ton(spieler, true);
                } else {
                    verweigert(spieler, z, pos);
                }
                return;
            }
            if (!darf) {
                verweigert(spieler, z, pos);
                return;
            }
            if (wert.equals("alarm_aus")) {
                z.alarmAus();
                Kompat.nachricht(spieler, Component.translatable("stinkendreich.alarm.ende").withStyle(ChatFormatting.GREEN), true);
                ton(spieler, true);
            } else if (wert.startsWith("stufe:")) {
                // Die Alarmstufe stellt nur der Eigentümer ein, nicht jeder Bewohner oder Kartenbesitzer
                if (!z.istEigentuemer(spieler)) {
                    Kompat.nachricht(spieler, Component.translatable("stinkendreich.zentrale.nur_eigentuemer").withStyle(ChatFormatting.RED), true);
                    ton(spieler, false);
                    return;
                }
                try {
                    int neu = Integer.parseInt(wert.substring(6));
                    // Die Festung (totaler Lockdown) geht nur an der Zentrale in der Kommandozentrale im Bunker
                    if (neu >= VillaZustand.FESTUNG && !kommandozentrale(spieler.level(), pos)) {
                        Kompat.nachricht(spieler, Component.translatable("stinkendreich.zentrale.nur_bunker").withStyle(ChatFormatting.RED), true);
                        ton(spieler, false);
                        return;
                    }
                    z.setzeStufe(neu);
                    ton(spieler, true);
                } catch (NumberFormatException ignoriert) {
                    // ungültiger Wert: nichts tun
                }
            }
        });
    }

    /** Steht diese Zentrale in der Kommandozentrale im Bunker (Wert {@code kommando=ja} aus dem Bauplan)? */
    public static boolean kommandozentrale(net.minecraft.world.level.BlockGetter welt, BlockPos pos) {
        MechanikBlockEntity d = daten(welt, pos);
        return d != null && "ja".equals(d.text("kommando", ""));
    }

    private static void verweigert(ServerPlayer spieler, VillaZustand z, BlockPos pos) {
        Kompat.nachricht(spieler, Component.translatable("stinkendreich.zugang.verweigert").withStyle(ChatFormatting.RED), true);
        ton(spieler, false);
        if (z.stufe() >= 1) {
            z.alarm(pos, Component.translatable("stinkendreich.alarm.grund.zentrale", spieler.getScoreboardName()));
        }
    }

    static void ton(ServerPlayer spieler, boolean gut) {
        spieler.level().playSound(null, spieler.blockPosition(), gut ? ModSounds.PIEP_OK : ModSounds.PIEP_FEHLER, SoundSource.BLOCKS, 0.8F, 1.0F);
    }
}
