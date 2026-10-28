package de.moinmornhart.stinkendreich.paper.bedrock;

import org.bukkit.plugin.Plugin;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.entity.custom.CustomEntityDefinition;
import org.geysermc.geyser.api.entity.data.GeyserEntityDataTypes;
import org.geysermc.geyser.api.entity.property.type.GeyserFloatEntityProperty;
import org.geysermc.geyser.api.entity.type.GeyserEntity;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.bedrock.SessionSkinApplyEvent;
import org.geysermc.geyser.api.event.java.ServerSpawnEntityEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomItemsEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntitiesEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntityPropertiesEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineResourcePacksEvent;
import org.geysermc.geyser.api.item.custom.v2.CustomItemBedrockOptions;
import org.geysermc.geyser.api.item.custom.v2.CustomItemDefinition;
import org.geysermc.geyser.api.pack.PackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.skin.Skin;
import org.geysermc.geyser.api.util.Identifier;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Die einzige Klasse, die Geyser direkt anspricht (wird nur geladen, wenn Geyser installiert ist): meldet das
 * Bedrock-Paket, die Figur {@link BedrockPaket#FIGUR} mit ihren Eigenschaften, ersetzt beim Erscheinen unsere
 * Ersatzfiguren durch diese Figur und gibt Schaufensterpuppen (James, Personal, Tänzerinnen, Bodyguards) ihre Haut.
 *
 * <p>Geyser meldet die Lebenslauf-Ereignisse beim Serverstart (nach allen Plugins); das Plugin lädt nach Geyser und
 * meldet sich in {@code onEnable} an.
 */
final class GeyserAnbindung {
    private static final Identifier FIGUR = Identifier.of(BedrockPaket.FIGUR);
    /**
     * Auf welchem Gegenstand aus dem Spiel ein eigener Gegenstand liegt (Standard: Papier wie Handy, Karte, Waffen).
     * Die Pistole der Bodyguards ist im Anschlag eine geladene Armbrust, die Agenten-Teile sind Netherit-Rüstung.
     */
    private static final Map<String, List<String>> GRUNDLAGEN = Map.of(
            "stinkendreich:mitarbeiterkiste", List.of("minecraft:chest"),
            "stinkendreich:goldene_pistole", List.of("minecraft:paper", "minecraft:crossbow"),
            "stinkendreich:bond_sonnenbrille", List.of("minecraft:netherite_helmet"),
            "stinkendreich:smoking", List.of("minecraft:netherite_chestplate"),
            "stinkendreich:anzughose", List.of("minecraft:netherite_leggings"),
            "stinkendreich:lackschuhe", List.of("minecraft:netherite_boots"));

    private final Plugin plugin;
    private final Path paket;
    private final Function<UUID, Lage> lagen;
    private final Function<UUID, Skin> haeute;
    private final Predicate<UUID> schmal;
    private final Map<String, String> symbole;
    /** Häute, die schon einmal gemeldet wurden (nur fürs Protokoll). */
    private final java.util.Set<String> gemeldet = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final CustomEntityDefinition figur;
    private final GeyserFloatEntityProperty[] eigenschaften = new GeyserFloatEntityProperty[BedrockPaket.EIGENSCHAFTEN.size()];

    GeyserAnbindung(Plugin plugin, Path paket, Map<String, String> symbole, Function<UUID, Lage> lagen,
                    Function<UUID, Skin> haeute, Predicate<UUID> schmal) {
        this.symbole = symbole;
        this.plugin = plugin;
        this.paket = paket;
        this.lagen = lagen;
        this.haeute = haeute;
        this.schmal = schmal;
        this.figur = CustomEntityDefinition.of(FIGUR);
    }

    void anmelden() {
        GeyserApi.api().eventBus().register(EventRegistrar.of(plugin), this);
    }

    boolean istBedrock(UUID spieler) {
        return GeyserApi.api().isBedrockPlayer(spieler);
    }

    @Subscribe
    public void pakete(GeyserDefineResourcePacksEvent e) {
        e.register(ResourcePack.create(PackCodec.path(paket)));
        plugin.getLogger().info("Bedrock: Paket bei Geyser angemeldet (" + paket.getFileName() + ")");
    }

    /** Flache Gegenstände mit Bild im Bedrock-Inventar (sonst wären Handy, Karte und Waffen nur Papier). */
    @Subscribe
    public void gegenstaende(GeyserDefineCustomItemsEvent e) {
        int n = 0;
        for (var s : symbole.entrySet()) {
            String modell = s.getKey(), bild = s.getValue();
            String name = modell.substring(modell.indexOf(':') + 1);
            for (String grundlage : GRUNDLAGEN.getOrDefault(modell, List.of("minecraft:paper"))) {
                String zusatz = grundlage.equals("minecraft:paper") ? "" : "_" + grundlage.substring(grundlage.indexOf(':') + 1);
                try {
                    e.register(Identifier.of(grundlage), CustomItemDefinition.builder(Identifier.of("stinkendreich", bild + zusatz), Identifier.of(modell))
                            .displayName(Character.toUpperCase(name.charAt(0)) + name.substring(1).replace('_', ' '))
                            .bedrockOptions(CustomItemBedrockOptions.builder().icon(bild).allowOffhand(true))
                            .build());
                    n++;
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Bedrock: Gegenstand " + modell + " nicht angemeldet: " + ex.getMessage());
                }
            }
        }
        plugin.getLogger().info("Bedrock: " + n + " eigene Gegenstände bei Geyser angemeldet");
    }

    @Subscribe
    public void figuren(GeyserDefineEntitiesEvent e) {
        e.register(figur);
    }

    @Subscribe
    public void eigenschaften(GeyserDefineEntityPropertiesEvent e) {
        List<String> namen = BedrockPaket.EIGENSCHAFTEN;
        for (int i = 0; i < namen.size(); i++) {
            String n = namen.get(i);
            // Drehung ±360°, Verschiebung ±512 Pixel (32 Blöcke), Maßstab 0–2 (relativ zum Gesamtmaßstab)
            float grenze = n.startsWith("r") ? 360 : n.startsWith("t") ? 512 : 2;
            float min = n.startsWith("s") ? 0 : -grenze;
            eigenschaften[i] = e.registerFloatProperty(FIGUR, Identifier.of("stinkendreich", n), min, grenze, n.startsWith("s") ? 1.0F : 0.0F);
        }
    }

    /** Unsere Ersatzfigur erscheint bei einem Bedrock-Spieler: stattdessen die eigene Figur mit Modell und Lage. */
    @Subscribe
    public void erscheinen(ServerSpawnEntityEvent e) {
        Lage lage = lagen.apply(e.uuid());
        if (lage == null) {
            return;
        }
        e.definition(figur);
        e.preSpawnConsumer(g -> setzen(g, lage));
    }

    /** Schaufensterpuppen: Haut aus dem Plugin statt der (für Geyser unerreichbaren) Haut aus dem Java-Paket. */
    @Subscribe
    public void haut(SessionSkinApplyEvent e) {
        Skin s = haeute.apply(e.uuid());
        if (s != null) {
            if (gemeldet.add(s.textureUrl())) {
                plugin.getLogger().info("Bedrock: Haut " + s.textureUrl() + " an Geyser gegeben");
            }
            e.skin(s);
            // Tänzerinnen haben schmale Arme (Bedrock: eingebautes Modell „customSlim“)
            e.geometry(schmal.test(e.uuid()) ? "geometry.humanoid.customSlim" : "geometry.humanoid.custom", "");
        }
    }

    /** Neue Lage an alle Bedrock-Spieler, die die Figur gerade sehen. */
    void aendern(UUID figurId, Lage lage) {
        for (GeyserConnection c : GeyserApi.api().onlineConnections()) {
            GeyserEntity g = c.entities().byUuid(figurId);
            if (g != null) {
                setzen(g, lage);
            }
        }
    }

    private void setzen(GeyserEntity g, Lage lage) {
        g.override(GeyserEntityDataTypes.VARIANT, lage.variante());
        g.override(GeyserEntityDataTypes.SCALE, lage.skala());
        float[] w = lage.werte();
        g.updatePropertiesBatched(b -> {
            for (int i = 0; i < eigenschaften.length; i++) {
                if (eigenschaften[i] != null) {
                    b.update(eigenschaften[i], w[i]);
                }
            }
        }, false);
    }
}
