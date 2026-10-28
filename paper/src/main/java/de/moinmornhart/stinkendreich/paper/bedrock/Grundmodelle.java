package de.moinmornhart.stinkendreich.paper.bedrock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Die wenigen Grundmodelle aus Minecraft, auf die unsere Modelle aufbauen (Würfel, Türen, flache Gegenstände) – selbst
 * nachgebaut, damit nichts aus dem Spiel ins Plugin kopiert wird. Für Bedrock reicht die Form; die Bildausschnitte
 * entsprechen denen von Minecraft (bei Türen nur ungefähr: gespiegelte Scharnierseite fehlt).
 */
final class Grundmodelle {
    private Grundmodelle() {
    }

    /** Grundmodell zur Kennung („minecraft:block/cube“), null wenn unbekannt. */
    static JsonObject modell(String id) {
        String json = switch (id) {
            case "minecraft:block/block", "minecraft:item/handheld" -> "{}";
            case "minecraft:block/cube" -> wuerfel("#down", "#up", "#north", "#south", "#west", "#east");
            case "minecraft:block/cube_all" -> "{\"parent\":\"minecraft:block/cube\",\"textures\":{\"down\":\"#all\",\"up\":\"#all\","
                    + "\"north\":\"#all\",\"south\":\"#all\",\"west\":\"#all\",\"east\":\"#all\",\"particle\":\"#all\"}}";
            case "minecraft:block/cube_column" -> "{\"parent\":\"minecraft:block/cube\",\"textures\":{\"down\":\"#end\",\"up\":\"#end\","
                    + "\"north\":\"#side\",\"south\":\"#side\",\"west\":\"#side\",\"east\":\"#side\"}}";
            case "minecraft:block/orientable" -> "{\"parent\":\"minecraft:block/cube\",\"textures\":{\"down\":\"#top\",\"up\":\"#top\","
                    + "\"north\":\"#front\",\"south\":\"#side\",\"west\":\"#side\",\"east\":\"#side\"}}";
            case "minecraft:block/door_bottom_left", "minecraft:block/door_bottom_left_open" -> tuer("#bottom", false);
            case "minecraft:block/door_bottom_right", "minecraft:block/door_bottom_right_open" -> tuer("#bottom", true);
            case "minecraft:block/door_top_left", "minecraft:block/door_top_left_open" -> tuer("#top", false);
            case "minecraft:block/door_top_right", "minecraft:block/door_top_right_open" -> tuer("#top", true);
            // Flacher Gegenstand: eine Scheibe mit dem Bild vorn und hinten (ohne ausgestanzten Rand)
            case "minecraft:item/generated" -> "{\"elements\":[{\"from\":[0,0,7.5],\"to\":[16,16,8.5],\"faces\":{"
                    + "\"north\":{\"uv\":[16,0,0,16],\"texture\":\"#layer0\"},\"south\":{\"uv\":[0,0,16,16],\"texture\":\"#layer0\"}}}]}";
            default -> null;
        };
        return json == null ? null : JsonParser.parseString(json).getAsJsonObject();
    }

    private static String wuerfel(String unten, String oben, String nord, String sued, String west, String ost) {
        return "{\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,16],\"faces\":{"
                + "\"down\":{\"texture\":\"" + unten + "\"},\"up\":{\"texture\":\"" + oben + "\"},"
                + "\"north\":{\"texture\":\"" + nord + "\"},\"south\":{\"texture\":\"" + sued + "\"},"
                + "\"west\":{\"texture\":\"" + west + "\"},\"east\":{\"texture\":\"" + ost + "\"}}}]}";
    }

    /** Türhälfte: 3 Pixel dicke Platte an der Westseite des Blocks (die Blockzustände drehen sie an ihren Platz). */
    private static String tuer(String textur, boolean rechts) {
        String innen = rechts ? "[16,0,0,16]" : "[0,0,16,16]";
        String aussen = rechts ? "[0,0,16,16]" : "[16,0,0,16]";
        return "{\"elements\":[{\"from\":[0,0,0],\"to\":[3,16,16],\"faces\":{"
                + "\"down\":{\"uv\":[13,0,16,16],\"texture\":\"" + textur + "\"},"
                + "\"up\":{\"uv\":[13,0,16,16],\"texture\":\"" + textur + "\"},"
                + "\"north\":{\"uv\":[3,0,0,16],\"texture\":\"" + textur + "\"},"
                + "\"south\":{\"uv\":[0,0,3,16],\"texture\":\"" + textur + "\"},"
                + "\"west\":{\"uv\":" + innen + ",\"texture\":\"" + textur + "\"},"
                + "\"east\":{\"uv\":" + aussen + ",\"texture\":\"" + textur + "\"}}}]}";
    }
}
