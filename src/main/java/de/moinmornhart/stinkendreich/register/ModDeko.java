package de.moinmornhart.stinkendreich.register;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.mechanik.DekoBlock;
import de.moinmornhart.stinkendreich.moebel.VerbundDekoBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registriert alle Deko-Blöcke aus {@code deko_bloecke.json} (liegt in der .jar, erzeugt von
 * tools/assets/deko.py). So stehen Aussehen und Form eines Möbelstücks an einer Stelle.
 */
public final class ModDeko {
    /** Name → Block, in der Reihenfolge der Datei. */
    public static final Map<String, Block> BLOECKE = new LinkedHashMap<>();

    private ModDeko() {
    }

    public static void init() {
        try (InputStream ein = ModDeko.class.getResourceAsStream("/deko_bloecke.json")) {
            if (ein == null) {
                StinkendReich.LOGGER.error("deko_bloecke.json fehlt in der .jar");
                return;
            }
            JsonArray liste = JsonParser.parseReader(new InputStreamReader(ein, StandardCharsets.UTF_8)).getAsJsonArray();
            for (JsonElement e : liste) {
                JsonObject o = e.getAsJsonObject();
                String name = o.get("name").getAsString();
                List<double[]> kaesten = new ArrayList<>();
                for (JsonElement k : o.getAsJsonArray("form")) {
                    JsonArray a = k.getAsJsonArray();
                    double[] w = new double[6];
                    for (int i = 0; i < 6; i++) {
                        w[i] = a.get(i).getAsDouble();
                    }
                    kaesten.add(w);
                }
                boolean fest = !o.has("fest") || o.get("fest").getAsBoolean();
                int licht = o.has("licht") ? o.get("licht").getAsInt() : 0;
                BlockBehaviour.Properties p = BlockBehaviour.Properties.of()
                        .strength(o.has("haerte") ? o.get("haerte").getAsFloat() : 1.5F, 6.0F)
                        .sound(klang(o.has("klang") ? o.get("klang").getAsString() : "stein"))
                        .noOcclusion()
                        .lightLevel(s -> licht);
                if (!fest) {
                    p = p.noCollision();
                }
                double sitz = o.has("sitz") ? o.get("sitz").getAsDouble() : -1;
                boolean verbund = o.has("verbund") && o.get("verbund").getAsBoolean();
                boolean spuelen = o.has("spuelen") && o.get("spuelen").getAsBoolean();
                boolean klavier = o.has("klavier") && o.get("klavier").getAsBoolean();
                if (o.has("fernbedienung") && o.get("fernbedienung").getAsBoolean()) {
                    // Fernbedienung: eigenes Item (in der Hand steuert sie den Fernseher)
                    BLOECKE.put(name, ModRegister.block(name, q -> new de.moinmornhart.stinkendreich.fernseher.FernbedienungBlock(q, kaesten, fest, sitz),
                            p, de.moinmornhart.stinkendreich.fernseher.FernbedienungItem::new));
                    continue;
                }
                BLOECKE.put(name, ModRegister.block(name, q -> verbund ? new VerbundDekoBlock(q, kaesten, fest, sitz)
                        : spuelen ? new de.moinmornhart.stinkendreich.mechanik.ToiletteBlock(q, kaesten, fest, sitz)
                        : klavier ? new de.moinmornhart.stinkendreich.klavier.KlavierBlock(q, kaesten, fest, sitz)
                        : new DekoBlock(q, kaesten, fest, sitz), p));
            }
            StinkendReich.LOGGER.info("{} Deko-Blöcke registriert", BLOECKE.size());
        } catch (Exception e) {
            StinkendReich.LOGGER.error("Deko-Blöcke konnten nicht geladen werden", e);
        }
    }

    private static SoundType klang(String name) {
        return switch (name) {
            case "metall" -> SoundType.METAL;
            case "glas" -> SoundType.GLASS;
            case "holz" -> SoundType.WOOD;
            case "wolle" -> SoundType.WOOL;
            case "kette" -> SoundType.CHAIN;
            default -> SoundType.STONE;
        };
    }
}
