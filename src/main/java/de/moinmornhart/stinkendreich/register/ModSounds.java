package de.moinmornhart.stinkendreich.register;

import net.minecraft.sounds.SoundEvent;

/** Eigene Geräusche der Mod (selbst erzeugt, siehe tools/assets/sounds.py). */
public final class ModSounds {
    public static final SoundEvent PIEP_OK = ModRegister.sound("piep_ok");
    public static final SoundEvent PIEP_FEHLER = ModRegister.sound("piep_fehler");
    public static final SoundEvent SIRENE = ModRegister.sound("sirene");
    public static final SoundEvent GONG = ModRegister.sound("gong");
    public static final SoundEvent KLINGEL = ModRegister.sound("klingel");
    public static final SoundEvent TRESOR = ModRegister.sound("tresor");
    public static final SoundEvent GLOECKCHEN = ModRegister.sound("gloeckchen");
    // Bonzenauto
    public static final SoundEvent MOTOR = ModRegister.sound("motor");
    public static final SoundEvent ROTOR = ModRegister.sound("rotor");
    public static final SoundEvent ANLASSER = ModRegister.sound("anlasser");
    public static final SoundEvent HUPE = ModRegister.sound("hupe");
    public static final SoundEvent REIFEN = ModRegister.sound("reifen");
    public static final SoundEvent CRASH = ModRegister.sound("crash");
    // Aufzug
    public static final SoundEvent AUFZUG_MUSIK = ModRegister.sound("aufzug_musik");

    private ModSounds() {
    }

    public static void init() {
        // Laden der Klasse registriert die Geräusche
    }
}
