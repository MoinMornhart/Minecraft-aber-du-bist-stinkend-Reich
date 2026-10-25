package de.moinmornhart.stinkendreich.butler;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.Konfig;
import de.moinmornhart.stinkendreich.StinkendReich;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Optional: Der Butler versteht freie Sätze mit Claude. Claude bekommt die Werkzeuge des Butlers
 * (bestellen, kochen, sortieren, folgen …), entscheidet, welche er braucht, und antwortet im
 * Namen des Butlers. Ohne API-Key in {@code config/stinkendreich.json} bleibt alles bei der
 * einfachen Sprachsteuerung ({@link Sprache}).
 *
 * <p>Der Key wird nur aus der Config gelesen und nirgends ausgegeben oder gespeichert.
 * Die Anfragen laufen in einem eigenen Hintergrund-Thread; die Werkzeuge selbst werden auf dem
 * Server-Thread ausgeführt.</p>
 */
public final class ButlerKI {
    private static final ExecutorService ARBEITER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Stinkend Reich – Butler-KI");
        t.setDaemon(true);
        return t;
    });
    /** Letzte Wortwechsel je Spieler (als Notiz für Claude, nicht als API-Verlauf). */
    private static final Map<UUID, Deque<String>> VERLAUF = new ConcurrentHashMap<>();
    private static final int VERLAUF_ZEILEN = 8;

    private ButlerKI() {
    }

    /** Ist ein API-Key eingetragen? */
    public static boolean aktiv() {
        String k = Konfig.get().anthropicApiKey;
        return k != null && !k.isBlank();
    }

    /** Schickt eine Nachricht des Spielers an Claude (im Hintergrund). */
    public static void anfragen(ButlerEntity butler, ServerPlayer spieler, String text) {
        MinecraftServer server = spieler.level().getServer();
        String lage = lage(butler, spieler);
        UUID butlerId = butler.getUUID(), spielerId = spieler.getUUID();
        Kompat.nachricht(spieler, Component.translatable("stinkendreich.butler.ki.denkt", butler.getName()).withStyle(ChatFormatting.GRAY), true);
        ARBEITER.submit(() -> Verbindung.gespraech(server, butlerId, spielerId, text, lage));
    }

    /** Was Claude über die Lage wissen muss: Tätigkeit, Taschen, Modus, Ort. */
    private static String lage(ButlerEntity b, ServerPlayer p) {
        StringBuilder s = new StringBuilder();
        s.append("Name des Butlers: ").append(b.getName().getString()).append('\n');
        s.append("Herr: ").append(p.getScoreboardName()).append('\n');
        s.append("Der Butler tut gerade: ").append(b.status().getString())
                .append(b.offeneAuftraege() > 1 ? " (danach noch " + (b.offeneAuftraege() - 1) + " Schritte)" : "").append('\n');
        s.append("Modus: ").append(b.folgt() ? "folgt dem Herrn" : "wartet zu Hause bzw. am zugewiesenen Platz").append('\n');
        s.append("Taschen: ").append(ButlerBefehle.inhalt(b).getString()).append('\n');
        s.append("Abstand zum Herrn: ").append((int) Math.sqrt(b.distanceToSqr(p))).append(" Blöcke\n");
        Deque<String> verlauf = VERLAUF.get(p.getUUID());
        if (verlauf != null && !verlauf.isEmpty()) {
            s.append("Bisheriges Gespräch (älteste zuerst):\n");
            verlauf.forEach(z -> s.append("  ").append(z).append('\n'));
        }
        return s.toString();
    }

    private static void merken(UUID spieler, String zeile) {
        Deque<String> d = VERLAUF.computeIfAbsent(spieler, k -> new ArrayDeque<>());
        synchronized (d) {
            d.addLast(zeile.length() > 300 ? zeile.substring(0, 300) + " …" : zeile);
            while (d.size() > VERLAUF_ZEILEN) {
                d.removeFirst();
            }
        }
    }

    /**
     * Alles, was die Anthropic-Bibliothek braucht, steht in dieser inneren Klasse. Sie wird erst
     * geladen, wenn tatsächlich ein Key eingetragen ist.
     */
    private static final class Verbindung {
        private static final String SYSTEM = """
                Du bist der Butler eines sehr reichen Minecraft-Spielers und wohnst mit ihm in seiner Villa. \
                Du bist höflich, ein wenig vornehm, siezt deinen Herrn und antwortest kurz (ein bis zwei Sätze) \
                in der Sprache, in der er dich anspricht.

                Was du wirklich tun kannst, steht in deinen Werkzeugen: Gegenstände besorgen (du baust dafür selbst \
                Erze ab und gräbst Tunnel, fällst Bäume, jagst Tiere, schmilzt im Ofen und stellst Dinge an der \
                Werkbank her), kochen, Truhen sortieren, folgen, warten, nach Hause gehen, herkommen, aufhören \
                und deine Taschen zeigen. Rufe für jeden Auftrag das passende Werkzeug auf, statt ihn nur zu \
                versprechen. Ein neuer Auftrag ersetzt den laufenden. Wenn dein Herr nur plaudert, antworte \
                ohne Werkzeug.

                Grenzen: Du baust nichts an der Villa ab und zerstörst nichts, was Spieler gebaut haben; du \
                greifst keine Spieler an und baust keine Gebäude. Bitten dieser Art lehnst du höflich ab. \
                Erfinde keine Fähigkeiten und keine Ergebnisse: Was ein Werkzeug zurückmeldet, ist der Stand.""";

        private static AnthropicClient client;
        private static String clientSchluessel;
        private static List<Tool> werkzeuge;

        private static synchronized AnthropicClient client() {
            String k = Konfig.get().anthropicApiKey.trim();
            if (client == null || !k.equals(clientSchluessel)) {
                client = AnthropicOkHttpClient.builder().apiKey(k).timeout(Duration.ofSeconds(90)).maxRetries(2).build();
                clientSchluessel = k;
            }
            return client;
        }

        static void gespraech(MinecraftServer server, UUID butlerId, UUID spielerId, String text, String lage) {
            List<MessageParam> nachrichten = new ArrayList<>();
            nachrichten.add(MessageParam.builder().role(MessageParam.Role.USER)
                    .content("Lage:\n" + lage + "\nNachricht deines Herrn: " + text).build());
            merken(spielerId, "Herr: " + text);
            try {
                for (int runde = 0; runde < 6; runde++) {
                    Message antwort = client().messages().create(anfrage(nachrichten));
                    StopReason grund = antwort.stopReason().orElse(StopReason.END_TURN);
                    if (grund.equals(StopReason.REFUSAL)) {
                        sagen(server, butlerId, Component.translatable("stinkendreich.butler.ki.ablehnung"));
                        return;
                    }
                    // Antwort vollständig (mit Denk-Blöcken) zurückgeben, damit der Verlauf unverändert bleibt
                    nachrichten.add(antwort.toParam());
                    StringBuilder gesagt = new StringBuilder();
                    List<ContentBlockParam> ergebnisse = new ArrayList<>();
                    for (ContentBlock block : antwort.content()) {
                        block.text().ifPresent(t -> gesagt.append(gesagt.isEmpty() ? "" : " ").append(t.text().trim()));
                        block.toolUse().ifPresent(w -> ergebnisse.add(werkzeugAusfuehren(server, butlerId, spielerId, w)));
                    }
                    if (!gesagt.toString().isBlank()) {
                        sagen(server, butlerId, Component.literal(gesagt.toString()));
                        merken(spielerId, "Butler: " + gesagt);
                    }
                    if (!grund.equals(StopReason.TOOL_USE) || ergebnisse.isEmpty()) {
                        return;
                    }
                    nachrichten.add(MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(ergebnisse).build());
                }
            } catch (AnthropicServiceException e) {
                // Nur Statuscode und Fehlerart protokollieren, nie Anfrage oder Schlüssel
                StinkendReich.LOGGER.warn("Claude-Anfrage des Butlers abgelehnt: HTTP {} {}", e.statusCode(), e.errorType().map(Object::toString).orElse(""));
                ersatz(server, butlerId, spielerId, text, "HTTP " + e.statusCode());
            } catch (AnthropicException e) {
                StinkendReich.LOGGER.warn("Claude nicht erreichbar: {}", e.getClass().getSimpleName());
                ersatz(server, butlerId, spielerId, text, e.getClass().getSimpleName());
            } catch (Exception e) {
                StinkendReich.LOGGER.error("Fehler im Butler-Gespräch", e);
                ersatz(server, butlerId, spielerId, text, e.getClass().getSimpleName());
            }
        }

        private static MessageCreateParams anfrage(List<MessageParam> nachrichten) {
            MessageCreateParams.Builder b = MessageCreateParams.builder()
                    .model(Konfig.get().butlerModell)
                    .maxTokens(8000L)
                    .system(SYSTEM)
                    .messages(nachrichten)
                    // Kurze Plauderei: wenig Nachdenken genügt und hält die Antwort schnell
                    .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                    // Lehnt das Modell ab, springt automatisch ein passendes anderes Modell ein
                    .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
            for (Tool t : werkzeuge()) {
                b.addTool(t);
            }
            return b.build();
        }

        /** Führt ein Werkzeug auf dem Server-Thread aus und meldet das Ergebnis an Claude zurück. */
        private static ContentBlockParam werkzeugAusfuehren(MinecraftServer server, UUID butlerId, UUID spielerId, ToolUseBlock aufruf) {
            Map<String, Object> eingabe;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = aufruf._input().convert(Map.class);
                eingabe = m == null ? Map.of() : m;
            } catch (Exception e) {
                eingabe = Map.of();
            }
            Map<String, Object> werte = eingabe;
            CompletableFuture<ButlerBefehle.Antwort> zukunft = new CompletableFuture<>();
            server.execute(() -> {
                try {
                    ButlerEntity b = ButlerVerwaltung.finden(server, butlerId);
                    ServerPlayer p = server.getPlayerList().getPlayer(spielerId);
                    if (b == null || p == null) {
                        zukunft.complete(new ButlerBefehle.Antwort(Component.literal("Der Butler ist gerade nicht erreichbar."), true));
                        return;
                    }
                    zukunft.complete(ButlerBefehle.befolgen(b, p, befehl(aufruf.name(), werte)));
                } catch (Exception e) {
                    zukunft.completeExceptionally(e);
                }
            });
            ButlerBefehle.Antwort a;
            try {
                a = zukunft.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                a = new ButlerBefehle.Antwort(Component.literal("Fehler: " + e.getClass().getSimpleName()), true);
            }
            return ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                    .toolUseId(aufruf.id()).content(a.text().getString()).isError(a.fehler()).build());
        }

        /** Übersetzt einen Werkzeugaufruf in einen Befehl der Sprachsteuerung. */
        private static Sprache.Befehl befehl(String werkzeug, Map<String, Object> e) {
            int anzahl = e.get("anzahl") instanceof Number n ? n.intValue() : -1;
            boolean truhe = Boolean.TRUE.equals(e.get("in_truhe"));
            return switch (werkzeug) {
                case "bestellen" -> new Sprache.Befehl(Sprache.Art.HOLEN, String.valueOf(e.get("gegenstand")), anzahl, truhe);
                case "kochen" -> new Sprache.Befehl(Sprache.Art.KOCHEN, "essen", anzahl, truhe);
                case "truhen_sortieren" -> Sprache.Befehl.von(Sprache.Art.SORTIEREN);
                case "folgen" -> Sprache.Befehl.von(Sprache.Art.FOLGEN);
                case "hier_warten" -> Sprache.Befehl.von(Sprache.Art.BLEIBEN);
                case "nach_hause" -> Sprache.Befehl.von(Sprache.Art.HEIM);
                case "herkommen" -> Sprache.Befehl.von(Sprache.Art.KOMMEN);
                case "aufhoeren" -> Sprache.Befehl.von(Sprache.Art.STOPP);
                case "taschen_zeigen" -> Sprache.Befehl.von(Sprache.Art.INVENTAR);
                case "fahren" -> new Sprache.Befehl(Sprache.Art.FAHREN, switch (String.valueOf(e.get("ziel"))) {
                    case "villa" -> Sprache.ZIEL_VILLA;
                    case "blick" -> Sprache.ZIEL_BLICK;
                    case "abholen" -> Sprache.ZIEL_ABHOLEN;
                    default -> String.valueOf(e.get("ziel"));
                }, -1, false);
                case "fliegen" -> new Sprache.Befehl(Sprache.Art.FLIEGEN, switch (String.valueOf(e.get("ziel"))) {
                    case "villa" -> Sprache.ZIEL_VILLA;
                    case "blick" -> Sprache.ZIEL_BLICK;
                    case "abholen" -> Sprache.ZIEL_ABHOLEN;
                    default -> String.valueOf(e.get("ziel"));
                }, -1, false);
                case "ort_merken" -> new Sprache.Befehl(Sprache.Art.ORT_MERKEN, String.valueOf(e.get("name")), -1, false);
                case "orte_zeigen" -> Sprache.Befehl.von(Sprache.Art.ORTE);
                default -> Sprache.Befehl.von(Sprache.Art.UNBEKANNT);
            };
        }

        private static synchronized List<Tool> werkzeuge() {
            if (werkzeuge != null) {
                return werkzeuge;
            }
            List<String> dinge = new ArrayList<>();
            StringBuilder liste = new StringBuilder();
            for (Quellen.Quelle q : Quellen.alle()) {
                if (q.weg() == Quellen.Weg.KEINER) {
                    continue;
                }
                dinge.add(q.id());
                liste.append(liste.isEmpty() ? "" : ", ").append(q.id()).append(" (")
                        .append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(q.anzeige()).getPath()).append(')');
            }
            Map<String, Object> gegenstand = new LinkedHashMap<>();
            gegenstand.put("type", "string");
            gegenstand.put("enum", dinge);
            gegenstand.put("description", "Was besorgt werden soll. Barren sind geschmolzen, rohes_* ungeschmolzen. Auswahl: " + liste);
            Map<String, Object> anzahl = Map.of("type", "integer", "description", "Wie viele Stück (1 bis 768). Ohne Angabe des Herrn: 16, bei Werkzeugen und Blöcken 1.");
            Map<String, Object> inTruhe = Map.of("type", "boolean", "description", "true = in die Lieferkiste legen, false = dem Herrn persönlich bringen");
            List<Tool> t = new ArrayList<>();
            t.add(werkzeug("bestellen", "Besorgt Gegenstände und liefert sie. Der Butler baut dafür selbst ab, fällt Bäume, "
                    + "jagt, schmilzt oder stellt her, auch über mehrere Stufen (z. B. Fackeln aus Holz und Kohle).",
                    Map.of("gegenstand", gegenstand, "anzahl", anzahl, "in_truhe", inTruhe)));
            t.add(werkzeug("kochen", "Brät rohes Essen aus den Taschen des Butlers; hat er keines, jagt er vorher Tiere. Liefert das Essen.",
                    Map.of("anzahl", anzahl, "in_truhe", inTruhe)));
            t.add(werkzeug("truhen_sortieren", "Sortiert alle Truhen und Fässer in der Nähe des Herrn.", Map.of()));
            t.add(werkzeug("folgen", "Der Butler folgt dem Herrn auf Schritt und Tritt.", Map.of()));
            t.add(werkzeug("hier_warten", "Der Butler bleibt an seiner jetzigen Stelle und wartet.", Map.of()));
            t.add(werkzeug("nach_hause", "Der Butler geht zurück an seinen Platz in der Villa und wartet dort.", Map.of()));
            t.add(werkzeug("herkommen", "Der Butler kommt sofort zum Herrn.", Map.of()));
            t.add(werkzeug("aufhoeren", "Bricht alle laufenden Aufträge ab.", Map.of()));
            t.add(werkzeug("taschen_zeigen", "Öffnet die Taschen des Butlers, damit der Herr hineinsehen und Dinge herausnehmen kann.", Map.of()));
            t.add(werkzeug("fahren", "Der Butler chauffiert den Herrn mit dem Auto: setzt sich ans Steuer, wartet, bis der Herr "
                    + "eingestiegen ist, und fährt zum Ziel. Ohne Auto in der Nähe lässt er einen Wagen vorfahren.",
                    Map.of("ziel", Map.of("type", "string", "description", "\"villa\" = nach Hause, \"blick\" = die Stelle, auf die der Herr "
                            + "gerade schaut, \"abholen\" = nur den Wagen vorfahren lassen, sonst der Name eines gespeicherten Ortes"))));
            t.add(werkzeug("fliegen", "Der Butler fliegt den Herrn mit dem Hubschrauber: holt ihn (landet neben dem Herrn), "
                    + "wartet, bis er eingestiegen ist, und fliegt zum Ziel. Nach Hause landet er auf dem Landeplatz der Villa.",
                    Map.of("ziel", Map.of("type", "string", "description", "\"villa\" = nach Hause, \"blick\" = die Stelle, auf die der Herr "
                            + "gerade schaut, \"abholen\" = nur den Hubschrauber holen, sonst der Name eines gespeicherten Ortes"))));
            t.add(werkzeug("ort_merken", "Merkt sich die Stelle, an der der Herr gerade steht, unter einem Namen (für spätere Fahrten).",
                    Map.of("name", Map.of("type", "string", "description", "Kurzer Name des Ortes, z. B. strand"))));
            t.add(werkzeug("orte_zeigen", "Nennt alle gespeicherten Orte.", Map.of()));
            werkzeuge = t;
            return t;
        }

        private static Tool werkzeug(String name, String beschreibung, Map<String, Object> felder) {
            Tool.InputSchema.Properties.Builder props = Tool.InputSchema.Properties.builder();
            felder.forEach((k, v) -> props.putAdditionalProperty(k, JsonValue.from(v)));
            return Tool.builder()
                    .name(name)
                    .description(beschreibung)
                    .strict(true)
                    .inputSchema(Tool.InputSchema.builder()
                            .properties(props.build())
                            .required(new ArrayList<>(felder.keySet()))
                            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                            .build())
                    .build();
        }

        private static void sagen(MinecraftServer server, UUID butlerId, Component text) {
            server.execute(() -> {
                ButlerEntity b = ButlerVerwaltung.finden(server, butlerId);
                if (b != null) {
                    b.sagen(text);
                }
            });
        }

        /** Ohne Verbindung zu Claude: kurz Bescheid geben und die einfache Sprachsteuerung nehmen. */
        private static void ersatz(MinecraftServer server, UUID butlerId, UUID spielerId, String text, String grund) {
            server.execute(() -> {
                ButlerEntity b = ButlerVerwaltung.finden(server, butlerId);
                ServerPlayer p = server.getPlayerList().getPlayer(spielerId);
                if (b != null && p != null) {
                    b.sagen(Component.translatable("stinkendreich.butler.ki.fehler", grund).withStyle(ChatFormatting.GRAY));
                    ButlerBefehle.ausfuehren(b, p, Sprache.verstehen(text));
                }
            });
        }
    }
}
