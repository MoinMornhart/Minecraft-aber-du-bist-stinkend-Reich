package de.moinmornhart.stinkendreich.handy;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.butler.ButlerBefehle;
import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import de.moinmornhart.stinkendreich.butler.ButlerVerwaltung;
import de.moinmornhart.stinkendreich.butler.auftrag.AnrufAuftrag;
import de.moinmornhart.stinkendreich.butler.auftrag.FahrAuftrag;
import de.moinmornhart.stinkendreich.butler.auftrag.FlugAuftrag;
import de.moinmornhart.stinkendreich.netz.Netz;
import de.moinmornhart.stinkendreich.register.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Was das Handy auf dem Server auslöst (Paket „handy“, Wert „Aktion|Angabe“):
 * <ul>
 *   <li>{@code anruf|limo} – James kommt mit der Stretch-Limo und wartet am Steuer</li>
 *   <li>{@code anruf|heli} – James kommt mit dem Hubschrauber und landet neben dir</li>
 *   <li>{@code fahr|Ziel} – James holt dich mit der Limo ab und fährt dich hin (Villa, Dorf, Endportal …)</li>
 *   <li>{@code liefer|Was} – Lieferservice: James bringt Tiere oder Waren (siehe {@link Lieferservice})</li>
 *   <li>{@code auftrag|Text} – ein Auftrag wie im Chat („hol 32 Eisen“)</li>
 *   <li>{@code mitarbeiter} – eine Mitarbeiterkiste kommt ins Inventar</li>
 *   <li>{@code bodyguards|rufen} / {@code bodyguards|heim} – Leibwächter rufen oder heimschicken</li>
 * </ul>
 * Geht nur, wenn man ein Handy dabeihat.
 */
public final class Handy {
    private Handy() {
    }

    public static void registrieren() {
        Netz.bearbeiter("handy", 0, (spieler, pos, wert) -> ausfuehren(spieler, wert));
    }

    private static boolean hatHandy(ServerPlayer p) {
        return p.getMainHandItem().is(ModItems.HANDY) || p.getOffhandItem().is(ModItems.HANDY);
    }

    static void ausfuehren(ServerPlayer p, String wert) {
        if (!hatHandy(p)) {
            return;
        }
        String[] teile = wert.split("\\|", 2);
        String was = teile[0], angabe = teile.length > 1 ? teile[1] : "";
        p.level().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.6F, 1.6F);
        switch (was) {
            case "mitarbeiter" -> {
                ItemStack kiste = new ItemStack(ModItems.MITARBEITERKISTE);
                if (!p.getInventory().add(kiste)) {
                    net.minecraft.world.level.block.Block.popResource(p.level(), p.blockPosition(), kiste);
                }
                Kompat.nachricht(p, Component.translatable("stinkendreich.handy.mitarbeiter.da").withStyle(ChatFormatting.GOLD), false);
                return;
            }
            case "bodyguards" -> {
                if (angabe.equals("heim")) {
                    de.moinmornhart.stinkendreich.bodyguard.Bodyguards.entlassen(p);
                } else {
                    de.moinmornhart.stinkendreich.bodyguard.Bodyguards.rufen(p);
                }
                return;
            }
            default -> {
            }
        }
        ButlerEntity b = butler(p);
        if (b == null) {
            return;
        }
        switch (was) {
            case "anruf" -> {
                if (angabe.equals("heli")) {
                    HeliBereitstellung.bereitstellen(p, b);
                    b.neueAuftraege(List.of(new FlugAuftrag(null, "")));
                } else {
                    b.neueAuftraege(List.of(new AnrufAuftrag(""), new FahrAuftrag(null, "")));
                }
            }
            case "fahr" -> {
                ButlerBefehle.FahrZiel z = ButlerBefehle.fahrZiel(b, p, angabe, false);
                if (z.antwort().fehler()) {
                    b.sagen(z.antwort().text());
                    return;
                }
                b.neueAuftraege(List.of(new AnrufAuftrag(""), new FahrAuftrag(z.punkt(), z.name())));
                b.sagen(z.antwort().text());
            }
            case "liefer" -> {
                if (Lieferservice.ANGEBOT.contains(angabe)) {
                    b.neueAuftraege(List.of(new AnrufAuftrag(angabe), new FahrAuftrag(null, "")));
                }
            }
            case "auftrag" -> ButlerBefehle.verarbeiten(p, angabe, true);
            default -> {
            }
        }
    }

    /** Der Butler des Spielers; hat er noch keinen, wird er jetzt eingestellt. */
    private static ButlerEntity butler(ServerPlayer p) {
        ButlerEntity b = ButlerVerwaltung.vonSpieler(p);
        if (b == null) {
            ButlerVerwaltung.rufen(p);
            b = ButlerVerwaltung.vonSpieler(p);
        }
        return b;
    }
}
