package de.moinmornhart.stinkendreich.client.auto;

import de.moinmornhart.stinkendreich.Konfig;
import de.moinmornhart.stinkendreich.auto.AutoEntity;
import de.moinmornhart.stinkendreich.register.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Motorgeräusch eines Autos mit Fahrer: läuft in Schleife, Tonhöhe und Lautstärke folgen dem Tempo. */
public class MotorGeraeusch extends AbstractTickableSoundInstance {
    private final AutoEntity auto;

    public MotorGeraeusch(AutoEntity auto) {
        super(auto.art.fliegt() ? ModSounds.ROTOR : ModSounds.MOTOR, SoundSource.NEUTRAL, RandomSource.create());
        this.auto = auto;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.35F;
        this.pitch = 0.6F;
        nachfuehren();
    }

    public AutoEntity auto() {
        return auto;
    }

    private void nachfuehren() {
        x = auto.getX();
        y = auto.getY() + 0.5;
        z = auto.getZ();
    }

    @Override
    public void tick() {
        if (auto.isRemoved() || (auto.getControllingPassenger() == null
                && !(auto instanceof de.moinmornhart.stinkendreich.auto.HubschrauberEntity h && h.rotor() > 0.05F))) {
            stop();
            return;
        }
        nachfuehren();
        if (auto instanceof de.moinmornhart.stinkendreich.auto.HubschrauberEntity h) {
            // Rotor: Tonhöhe und Lautstärke folgen der Drehzahl, im Flug etwas höher
            pitch = 0.5F + 0.5F * h.rotor() + 0.15F * (float) Math.min(1.0, auto.kmh() / Math.max(30.0, Konfig.get().hubschrauberTempo));
            volume = 0.15F + 0.75F * h.rotor();
            return;
        }
        float anteil = (float) Math.min(1.0, auto.kmh() / Math.max(30.0, Konfig.get().autoHoechstTempo));
        pitch = 0.55F + 1.05F * anteil;
        volume = 0.3F + 0.45F * anteil;
    }
}
