package de.moinmornhart.stinkendreich.client.fernseher;

import de.moinmornhart.stinkendreich.Konfig;
import de.moinmornhart.stinkendreich.register.ModMusik;
import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

import java.util.concurrent.CompletableFuture;

/**
 * Ton eines Videos: kommt räumlich aus dem Fernseher (wird mit dem Abstand leiser, Hörweite 32 Blöcke laut
 * sounds.json) und läuft im Regler „Jukebox/Notenblöcke“. Die Daten liefert {@link VideoAbspieler}.
 */
final class VideoTon extends AbstractTickableSoundInstance implements FabricSoundInstance {
    private final VideoAbspieler abspieler;

    VideoTon(VideoAbspieler abspieler, double x, double y, double z) {
        super(ModMusik.FERNSEHER_TON, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
        this.abspieler = abspieler;
        this.x = x;
        this.y = y;
        this.z = z;
        this.attenuation = Attenuation.LINEAR;
        this.relative = false;
        this.looping = false;
        this.delay = 0;
        this.volume = (float) Math.max(0.0, Math.min(1.0, Konfig.get().fernseherLautstaerke));
    }

    void beenden() {
        stop();
    }

    /** Auch starten, wenn man gerade außer Hörweite steht (sonst fehlt der Ton, wenn man näher kommt). */
    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (abspieler.geschlossen()) {
            stop();
        }
    }

    @Override
    public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary lader, Identifier id, boolean wiederholen) {
        return CompletableFuture.completedFuture(abspieler.tonStrom());
    }
}
