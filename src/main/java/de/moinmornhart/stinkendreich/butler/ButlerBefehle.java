package de.moinmornhart.stinkendreich.butler;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.Konfig;
import de.moinmornhart.stinkendreich.butler.auftrag.GehAuftrag;
import de.moinmornhart.stinkendreich.butler.auftrag.SortierAuftrag;
import de.moinmornhart.stinkendreich.netz.Netz;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Alle Wege, dem Butler etwas aufzutragen:
 * <ul>
 *   <li>{@code /butler} – Status und Hilfe</li>
 *   <li>{@code /butler rufen} – Butler herbeiholen (oder den ersten eigenen einstellen)</li>
 *   <li>{@code /butler truhe} – die angeschaute Truhe wird seine Lieferkiste</li>
 *   <li>{@code /butler <Auftrag>} – z. B. {@code /butler hol 32 eisen}</li>
 *   <li>Chat: „Butler, hol mir 32 Eisen“ (oder mit seinem Namen anreden)</li>
 *   <li>Rechtsklick auf den Butler: Menü mit Knöpfen und Textfeld</li>
 * </ul>
 */
public final class ButlerBefehle {
    private ButlerBefehle() {
    }

    public static void registrieren() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, umgebung) -> dispatcher.register(
                Commands.literal("butler")
                        .executes(k -> status(k))
                        .then(Commands.literal("rufen").executes(k -> {
                            ButlerVerwaltung.rufen(k.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("truhe").executes(ButlerBefehle::truheFestlegen))
                        .then(Commands.argument("auftrag", StringArgumentType.greedyString()).executes(k -> {
                            verarbeiten(k.getSource().getPlayerOrException(), StringArgumentType.getString(k, "auftrag"), false);
                            return 1;
                        }))));

        // Chat: Nachrichten, die mit „Butler“ oder seinem Namen beginnen
        ServerMessageEvents.CHAT_MESSAGE.register((nachricht, absender, typ) -> {
            String text = nachricht.signedContent();
            String rest = anrede(text);
            if (rest != null) {
                absender.level().getServer().execute(() -> verarbeiten(absender, rest, false));
            }
        });

        // Butler-Menü: „<Entity-Nummer>|<!Schnellbefehl oder freier Text>“
        Netz.bearbeiter("butler", 12, (spieler, pos, wert) -> {
            int trenner = wert.indexOf('|');
            if (trenner < 0) {
                return;
            }
            Entity e = spieler.level().getEntity(parseInt(wert.substring(0, trenner)));
            if (!(e instanceof ButlerEntity b)) {
                return;
            }
            if (b.besitzer() == null) {
                ButlerVerwaltung.besitzerSetzen(b, spieler);
            }
            if (!b.istBesitzer(spieler)) {
                return;
            }
            String befehl = wert.substring(trenner + 1);
            if (befehl.equals("!inventar")) {
                b.inventarZeigen(spieler);
            } else if (befehl.startsWith("!")) {
                ausfuehren(b, spieler, Sprache.verstehen(befehl.substring(1)));
            } else if (!befehl.isBlank()) {
                verarbeiten(spieler, befehl, true);
            }
        });
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Ist die Nachricht an den Butler gerichtet? Dann der Text ohne Anrede, sonst null. */
    static String anrede(String text) {
        String t = text.trim();
        for (String wort : List.of(Konfig.get().butlerAnrede, Konfig.get().butlerName)) {
            if (wort == null || wort.isBlank()) {
                continue;
            }
            String w = wort.toLowerCase(Locale.ROOT);
            String klein = t.toLowerCase(Locale.ROOT);
            if (klein.startsWith(w) && (klein.length() == w.length() || " ,:!".indexOf(klein.charAt(w.length())) >= 0)) {
                return t.substring(w.length()).replaceFirst("^[\\s,:!]+", "");
            }
        }
        return null;
    }

    /** Freier Text an den Butler: mit Claude (falls eingerichtet), sonst über die einfache Sprachsteuerung. */
    public static void verarbeiten(ServerPlayer spieler, String text, boolean ausMenue) {
        ButlerEntity b = ButlerVerwaltung.vonSpieler(spieler);
        if (b == null) {
            Kompat.nachricht(spieler, Component.translatable("stinkendreich.butler.keiner").withStyle(ChatFormatting.YELLOW), false);
            ButlerVerwaltung.rufen(spieler);
            return;
        }
        if (ButlerKI.aktiv()) {
            ButlerKI.anfragen(b, spieler, text);
            return;
        }
        ausfuehren(b, spieler, Sprache.verstehen(text));
    }

    /** Antwort des Butlers auf einen Auftrag; {@code fehler}, wenn er ihn nicht ausführen kann. */
    public record Antwort(Component text, boolean fehler) {
        static Antwort ok(String schluessel, Object... werte) {
            return new Antwort(Component.translatable(schluessel, werte), false);
        }
    }

    /** Führt einen verstandenen Auftrag aus und lässt den Butler antworten. */
    public static void ausfuehren(ButlerEntity b, ServerPlayer p, Sprache.Befehl bf) {
        b.sagen(befolgen(b, p, bf).text());
    }

    /** Führt einen Auftrag aus und gibt die Antwort zurück (ohne sie auszusprechen). */
    public static Antwort befolgen(ButlerEntity b, ServerPlayer p, Sprache.Befehl bf) {
        switch (bf.art()) {
            case HOLEN -> {
                int menge = bf.menge() > 0 ? bf.menge() : Sprache.standardMenge(bf.quelle());
                Planer.Plan plan = Planer.bestellung(b, bf.quelle(), menge, bf.inTruhe());
                if (!plan.ok()) {
                    return new Antwort(plan.fehler(), true);
                }
                b.neueAuftraege(plan.schritte());
                return Antwort.ok(bf.inTruhe() ? "stinkendreich.butler.sehrwohl.truhe" : "stinkendreich.butler.sehrwohl",
                        Math.min(menge, Planer.MAX_MENGE), Quellen.get(bf.quelle()).anzeigeStapel().getHoverName());
            }
            case KOCHEN -> {
                Planer.Plan plan = Planer.kochen(b, bf.menge() > 0 ? bf.menge() : 8, bf.inTruhe());
                if (!plan.ok()) {
                    return new Antwort(plan.fehler(), true);
                }
                b.neueAuftraege(plan.schritte());
                return Antwort.ok("stinkendreich.butler.kocht");
            }
            case SORTIEREN -> {
                b.neueAuftraege(List.of(new SortierAuftrag(p.blockPosition())));
                return Antwort.ok("stinkendreich.butler.sortiert.los");
            }
            case FOLGEN -> {
                b.setzeFolgen(true);
                return Antwort.ok("stinkendreich.butler.folgen");
            }
            case BLEIBEN -> {
                b.setzeFolgen(false);
                b.setzeZuhause(b.blockPosition());
                return Antwort.ok("stinkendreich.butler.bleiben");
            }
            case HEIM -> {
                b.setzeFolgen(false);
                b.setzeZuhause(b.heimat());
                b.neueAuftraege(List.of(new GehAuftrag(true)));
                return Antwort.ok("stinkendreich.butler.heim");
            }
            case KOMMEN -> {
                b.neueAuftraege(List.of(new GehAuftrag(false)));
                return Antwort.ok("stinkendreich.butler.kommt");
            }
            case STOPP -> {
                b.stopp();
                return Antwort.ok("stinkendreich.butler.stopp");
            }
            case INVENTAR -> {
                b.inventarZeigen(p);
                return new Antwort(inhalt(b), false);
            }
            case STATUS -> {
                return new Antwort(b.status().copy().append(b.offeneAuftraege() > 1
                        ? Component.translatable("stinkendreich.butler.danach", b.offeneAuftraege() - 1) : Component.empty()), false);
            }
            case HILFE -> {
                return Antwort.ok("stinkendreich.butler.hilfe");
            }
            default -> {
                return new Antwort(Component.translatable("stinkendreich.butler.verstehtnicht"), true);
            }
        }
    }

    /** Kurze Liste, was der Butler bei sich trägt. */
    static Component inhalt(ButlerEntity b) {
        List<ItemStack> zusammen = new ArrayList<>();
        for (int i = 0; i < b.taschen().getContainerSize(); i++) {
            ItemStack s = b.taschen().getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            boolean dabei = false;
            for (ItemStack z : zusammen) {
                if (ItemStack.isSameItemSameComponents(z, s)) {
                    z.grow(s.getCount());
                    dabei = true;
                }
            }
            if (!dabei) {
                zusammen.add(s.copy());
            }
        }
        if (zusammen.isEmpty()) {
            return Component.translatable("stinkendreich.butler.leer");
        }
        var text = Component.translatable("stinkendreich.butler.dabei").copy();
        for (int i = 0; i < zusammen.size(); i++) {
            text.append(i == 0 ? " " : ", ").append(zusammen.get(i).getCount() + "× ").append(zusammen.get(i).getHoverName());
        }
        return text;
    }

    private static int status(CommandContext<CommandSourceStack> k) {
        ServerPlayer p = k.getSource().getPlayer();
        ButlerEntity b = p == null ? null : ButlerVerwaltung.vonSpieler(p);
        if (b == null) {
            k.getSource().sendSuccess(() -> Component.translatable("stinkendreich.butler.keiner"), false);
        } else {
            b.sagen(b.status());
        }
        k.getSource().sendSuccess(() -> Component.translatable("stinkendreich.butler.hilfe").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    /** Die angeschaute Truhe wird die Lieferkiste des eigenen Butlers. */
    private static int truheFestlegen(CommandContext<CommandSourceStack> k) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = k.getSource().getPlayerOrException();
        ButlerEntity b = ButlerVerwaltung.vonSpieler(p);
        HitResult blick = p.pick(6.0, 1.0F, false);
        if (b == null || !(blick instanceof BlockHitResult bt) || blick.getType() != HitResult.Type.BLOCK
                || !(p.level().getBlockEntity(bt.getBlockPos()) instanceof Container)) {
            k.getSource().sendFailure(Component.translatable("stinkendreich.butler.truhe.keine"));
            return 0;
        }
        BlockPos pos = bt.getBlockPos();
        b.setzeLiefertruhe(pos);
        b.sagen(Component.translatable("stinkendreich.butler.truhe.ok", pos.getX(), pos.getY(), pos.getZ()));
        return 1;
    }
}
