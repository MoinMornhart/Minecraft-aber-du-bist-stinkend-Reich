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
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntitiesEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntityPropertiesEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineResourcePacksEvent;
import org.geysermc.geyser.api.pack.PackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.skin.Skin;
import org.geysermc.geyser.api.util.Identifier;

import java.nio.file.Path;
import java.util.List;
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

    private final Plugin plugin;
    private final Path paket;
    private final Function<UUID, Lage> lagen;
    private final Function<UUID, Skin> haeute;
    private final Predicate<UUID> schmal;
    private final CustomEntityDefinition figur;
    private final GeyserFloatEntityProperty[] eigenschaften = new GeyserFloatEntityProperty[BedrockPaket.EIGENSCHAFTEN.size()];

    GeyserAnbindung(Plugin plugin, Path paket, Function<UUID, Lage> lagen, Function<UUID, Skin> haeute, Predicate<UUID> schmal) {
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
