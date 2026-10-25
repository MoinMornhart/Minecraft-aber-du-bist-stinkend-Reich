package de.moinmornhart.stinkendreich.client.musik;

import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.register.ModMusik;
import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Ein Titel aus dem Musikordner, abgespielt über Minecrafts Soundsystem. Der Klang ist nicht
 * räumlich (Stereo bleibt Stereo); die Lautstärke regelt {@link MusikKlient} jeden Tick nach dem
 * Abstand zur Anlage und zu den Lautsprechern. Er läuft im Regler „Jukebox/Notenblöcke“.
 */
public class MusikSound extends AbstractTickableSoundInstance implements FabricSoundInstance {
    private final Path datei;
    private final double ab;
    private final long erzeugt = System.nanoTime();
    private float ziel;

    MusikSound(Path datei, double abSekunden, float lautstaerke) {
        super(ModMusik.MUSIK, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
        this.datei = datei;
        this.ab = abSekunden;
        this.relative = true;
        this.attenuation = Attenuation.NONE;
        this.looping = false;
        this.delay = 0;
        this.x = 0;
        this.y = 0;
        this.z = 0;
        this.volume = lautstaerke;
        this.ziel = lautstaerke;
    }

    /** Neue Ziel-Lautstärke; wird weich angeglichen, damit es beim Laufen nicht knackt. */
    void zielLautstaerke(float wert) {
        ziel = Math.max(0.0F, Math.min(1.0F, wert));
    }

    void beenden() {
        stop();
    }

    /** Auch leise starten, wenn man gerade am Rand der Hörweite steht. */
    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        volume += (ziel - volume) * 0.25F;
        if (Math.abs(ziel - volume) < 0.003F) {
            volume = ziel;
        }
    }

    @Override
    public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary lader, Identifier id, boolean wiederholen) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Die Zeit bis hierher mitzählen, damit alle an derselben Stelle sind
                AudioStream strom = MusikStrom.oeffnen(datei, ab + (System.nanoTime() - erzeugt) / 1e9);
                StinkendReich.LOGGER.info("Musikstrom offen: {} ({} Hz, {} Kanäle)", datei.getFileName(),
                        (int) strom.getFormat().getSampleRate(), strom.getFormat().getChannels());
                return strom;
            } catch (IOException e) {
                StinkendReich.LOGGER.warn("Musik {} lässt sich nicht abspielen: {}", datei.getFileName(), e.getMessage());
                throw new CompletionException(e);
            }
        });
    }
}
