package de.moinmornhart.stinkendreich.mechanik;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.netz.Netz;
import de.moinmornhart.stinkendreich.register.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Tastenfeld (Passwort-Tür): Code eintippen, bei richtigem Code gibt es 5 Sekunden lang ein
 * Redstone-Signal. Standard-Code ist 1234, Bewohner können ihn ändern. Drei falsche Codes lösen Alarm aus.
 */
public class TastenfeldBlock extends MechanikBlock {
    public static final String STANDARD_CODE = "1234";

    public TastenfeldBlock(Properties eigenschaften) {
        super(eigenschaften, Ausrichtung.WAAGERECHT, true, true);
    }

    @Override
    protected Property<?>[] grundEigenschaften() {
        return new Property<?>[]{Eigenschaften.RICHTUNG, Eigenschaften.AN};
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState zustand, Level welt, BlockPos pos, Player spieler, BlockHitResult treffer) {
        if (welt.isClientSide()) {
            KlientBruecke.tastenfeld.accept(pos);
        }
        return InteractionResult.SUCCESS;
    }

    public static void aktionenRegistrieren() {
        // Code eingeben
        Netz.bearbeiter("tastenfeld", 8, (spieler, pos, code) -> {
            Level welt = spieler.level();
            BlockState zustand = welt.getBlockState(pos);
            MechanikBlockEntity daten = daten(welt, pos);
            if (!(zustand.getBlock() instanceof TastenfeldBlock block) || daten == null) {
                return;
            }
            if (code.equals(daten.text("code", STANDARD_CODE))) {
                Zugang.erfolg(spieler);
                block.impulsGeben(welt, pos, zustand, 100);
                welt.playSound(null, pos, ModSounds.PIEP_OK, SoundSource.BLOCKS, 1.0F, 1.2F);
                Kompat.nachricht(spieler, Component.translatable("stinkendreich.zugang.code").withStyle(ChatFormatting.GREEN), true);
            } else {
                welt.playSound(null, pos, ModSounds.PIEP_FEHLER, SoundSource.BLOCKS, 1.0F, 1.0F);
                Kompat.nachricht(spieler, Component.translatable("stinkendreich.zugang.falscher_code").withStyle(ChatFormatting.RED), true);
                VillaZustand z = VillaZustand.get(welt.getServer());
                if (Zugang.fehlversuch(spieler) && z.stufe() >= 1) {
                    z.alarm(pos, Component.translatable("stinkendreich.alarm.grund.code", spieler.getScoreboardName()));
                }
            }
        });
        // Code ändern: Wert "alterCode:neuerCode", nur für Bewohner
        Netz.bearbeiter("tastenfeld_neu", 8, (spieler, pos, wert) -> {
            MechanikBlockEntity daten = daten(spieler.level(), pos);
            String[] teile = wert.split(":", 2);
            boolean gueltig = teile.length == 2 && teile[1].matches("\\d{4,8}");
            if (daten == null || !gueltig || !VillaZustand.get(spieler.level().getServer()).istBewohner(spieler)
                    || !teile[0].equals(daten.text("code", STANDARD_CODE))) {
                Kompat.nachricht(spieler, Component.translatable("stinkendreich.zugang.code_nicht_geaendert").withStyle(ChatFormatting.RED), true);
                return;
            }
            daten.setze("code", teile[1]);
            Kompat.nachricht(spieler, Component.translatable("stinkendreich.zugang.code_geaendert").withStyle(ChatFormatting.GREEN), true);
        });
    }
}
