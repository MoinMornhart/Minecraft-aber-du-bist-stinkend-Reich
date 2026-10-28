package de.moinmornhart.stinkendreich.mechanik;

import de.moinmornhart.stinkendreich.register.ModItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Prüfungen rund um Zugangskarten und Fehlversuche an Kartenleser und Tastenfeld. */
public final class Zugang {
    private static final Map<UUID, int[]> FEHLVERSUCHE = new HashMap<>();

    private Zugang() {
    }

    /** Trägt der Spieler irgendwo im Inventar eine Zugangskarte (egal für welche Villa)? */
    public static boolean hatKarte(Player spieler) {
        return hatKarte(spieler, -1);
    }

    /**
     * Trägt der Spieler eine Zugangskarte für die Villa mit dieser Nummer (-1 = irgendeine)? Karten ohne Nummer
     * (von vor 0.4) gelten für die Spawn-Villa.
     */
    public static boolean hatKarte(Player spieler, int villa) {
        var inv = spieler.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.is(ModItems.ZUGANGSKARTE) && (villa < 0 || villaDerKarte(s) == villa)) {
                return true;
            }
        }
        return false;
    }

    /** Nummer der Villa, für die die Karte gilt (0 = Spawn-Villa, auch bei alten Karten). */
    public static int villaDerKarte(ItemStack karte) {
        var daten = karte.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (daten == null) {
            return 0;
        }
        var tag = daten.copyTag();
        //? if >=1.21.5 {
        return tag.getIntOr("stinkendreich_villa", 0);
        //?} else
        /*return tag.contains("stinkendreich_villa") ? tag.getInt("stinkendreich_villa") : 0;*/
    }

    /** Eine neue Zugangskarte für diese Villa; bei Nachbarvillen steht der Besitzer im Namen. */
    public static ItemStack karte(de.moinmornhart.stinkendreich.villa.VillaPlatzierung.VillaInfo villa) {
        return karte(null, villa);
    }

    /**
     * Eine neue Zugangskarte für diese Villa, personalisiert (Philip: „die Zutrittskarte ist noch nicht personalisiert auf
     * das Haus“): Nummer der Villa in den Daten, im Namen „Villa von …“ – bei Nachbarvillen der Besitzer, bei der
     * Spawn-Villa ihr erster Bewohner (dafür braucht es den Server; ohne ihn nur der Besitzer).
     */
    public static ItemStack karte(net.minecraft.server.MinecraftServer server, de.moinmornhart.stinkendreich.villa.VillaPlatzierung.VillaInfo villa) {
        ItemStack karte = new ItemStack(ModItems.ZUGANGSKARTE);
        personalisieren(karte, villa.nr(), server == null ? villa.besitzerName()
                : de.moinmornhart.stinkendreich.villa.VillaPlatzierung.herrName(server, villa));
        return karte;
    }

    /** Nummer und Namen in eine Karte schreiben (Name nur, wenn er bekannt ist). */
    private static void personalisieren(ItemStack karte, int nr, String herr) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("stinkendreich_villa", nr);
        karte.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        if (herr != null && !herr.isEmpty()) {
            karte.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    net.minecraft.network.chat.Component.translatable("stinkendreich.karte.von", herr)
                            .withStyle(s -> s.withItalic(false).withColor(net.minecraft.ChatFormatting.GOLD)));
        }
    }

    /**
     * Zugangskarten in den Truhen, Fässern und Schubladen einer frisch gebauten Villa auf sie ausstellen. Aus dem Bauplan
     * kommen sie ohne Nummer – und Karten ohne Nummer gelten für die Spawn-Villa: Auf einem Server öffnete eine Karte aus
     * der Truhe einer Nachbarvilla bisher die Villa des ersten Spielers.
     */
    public static int truhenkartenAusstellen(net.minecraft.server.level.ServerLevel welt,
                                             de.moinmornhart.stinkendreich.villa.VillaPlatzierung.VillaInfo villa, String herr) {
        int anzahl = 0;
        BlockPos u = villa.ursprung();
        for (int cx = u.getX() >> 4; cx <= (u.getX() + villa.plan().breiteX) >> 4; cx++) {
            for (int cz = u.getZ() >> 4; cz <= (u.getZ() + villa.plan().breiteZ) >> 4; cz++) {
                for (var be : welt.getChunk(cx, cz).getBlockEntities().values()) {
                    if (be instanceof net.minecraft.world.Container behaelter && villa.inDerNaehe(be.getBlockPos(), 0)) {
                        for (int i = 0; i < behaelter.getContainerSize(); i++) {
                            ItemStack s = behaelter.getItem(i);
                            if (!s.isEmpty() && s.is(ModItems.ZUGANGSKARTE)) {
                                personalisieren(s, villa.nr(), herr);
                                anzahl++;
                            }
                        }
                        be.setChanged();
                    }
                }
            }
        }
        return anzahl;
    }

    /**
     * Beim Betreten: Karten im Inventar, die noch keinen Namen haben, bekommen den Namen des Herrn ihrer Villa (die
     * Karte der Spawn-Villa entstand, bevor sie einen Bewohner hatte).
     */
    public static void namenNachtragen(net.minecraft.server.level.ServerPlayer spieler, net.minecraft.server.MinecraftServer server) {
        var inv = spieler.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !s.is(ModItems.ZUGANGSKARTE) || s.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
                continue;
            }
            var villa = de.moinmornhart.stinkendreich.villa.VillaPlatzierung.nr(villaDerKarte(s));
            if (villa != null) {
                personalisieren(s, villa.nr(), de.moinmornhart.stinkendreich.villa.VillaPlatzierung.herrName(server, villa));
            }
        }
    }

    /**
     * Zählt einen Fehlversuch. Gibt true zurück, wenn es der dritte in Folge war (dann Alarm).
     */
    public static boolean fehlversuch(Player spieler) {
        int[] zaehler = FEHLVERSUCHE.computeIfAbsent(spieler.getUUID(), u -> new int[1]);
        zaehler[0]++;
        if (zaehler[0] >= 3) {
            zaehler[0] = 0;
            return true;
        }
        return false;
    }

    public static void erfolg(Player spieler) {
        FEHLVERSUCHE.remove(spieler.getUUID());
    }
}
