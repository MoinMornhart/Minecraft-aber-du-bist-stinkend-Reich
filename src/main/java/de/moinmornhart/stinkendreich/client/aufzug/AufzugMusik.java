package de.moinmornhart.stinkendreich.client.aufzug;

import de.moinmornhart.stinkendreich.mechanik.AufzugKabine;
import de.moinmornhart.stinkendreich.register.ModSounds;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Fahrstuhlmusik: läuft, solange man in der Kabine sitzt (Lautstärke-Regler „Musikblöcke“),
 * und blendet beim Aussteigen aus.
 */
public class AufzugMusik extends AbstractTickableSoundInstance {
    private static AufzugMusik laufend;
    private final AufzugKabine kabine;
    private int ausblenden = -1;

    private AufzugMusik(AufzugKabine kabine) {
        super(ModSounds.AUFZUG_MUSIK, SoundSource.RECORDS, RandomSource.create());
        this.kabine = kabine;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.45F;
        this.relative = true;
    }

    public static void registrieren() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean drin = client.player != null && client.player.getVehicle() instanceof AufzugKabine;
            if (drin && (laufend == null || laufend.isStopped() || laufend.ausblenden >= 0)) {
                laufend = new AufzugMusik((AufzugKabine) client.player.getVehicle());
                Minecraft.getInstance().getSoundManager().play(laufend);
            }
        });
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean drin = mc.player != null && mc.player.getVehicle() == kabine && !kabine.isRemoved();
        if (!drin && ausblenden < 0) {
            ausblenden = 10;
        }
        if (ausblenden >= 0) {
            volume = 0.45F * ausblenden / 10.0F;
            if (ausblenden-- == 0) {
                stop();
            }
        }
    }
}
