package com.player2.playerengine.blockknowledge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Version-locked vanilla block acquisition reference, independent of LLM selection.
 * Item and world registries remain authoritative at execution time (including mods).
 * A listed loot method is NOT a guarantee that the item is currently accessible.
 */
public final class BlockKnowledge {
    private static final String RESOURCE = "/playerengine/minecraft_1_21_1_blocks.json.gz";
    private static volatile Map<String, Entry> cache;

    private BlockKnowledge() {}

    public record Route(String method, String source, int score, String requirement, int yield, List<String> ingredients) {
        public String summary() {
            String inputs = ingredients.isEmpty() ? "" : " using " + String.join(" + ", ingredients);
            String req = requirement.isBlank() ? "" : " [" + requirement + "]";
            return method + " " + source + inputs + " (yield " + yield + ")" + req;
        }
    }
    public record Entry(String id, String displayName, String itemForm, boolean obtainable, String harvestTool,
                        List<Route> routes, String note) {}

    public static Map<String, Entry> all() {
        Map<String, Entry> current = cache;
        if (current != null) return current;
        synchronized (BlockKnowledge.class) {
            if (cache != null) return cache;
            try (InputStream raw = BlockKnowledge.class.getResourceAsStream(RESOURCE)) {
                if (raw == null) throw new IllegalStateException("Block knowledge resource absent: " + RESOURCE);
                try (InputStreamReader reader = new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.UTF_8)) {
                    JsonObject db = JsonParser.parseReader(reader).getAsJsonObject();
                    if (!"1.21.1".equals(string(db, "minecraftVersion"))) {
                        throw new IllegalStateException("Wrong block knowledge version");
                    }
                    Map<String, Entry> parsed = new HashMap<>();
                    for (Map.Entry<String, JsonElement> b : db.getAsJsonObject("blocks").entrySet()) {
                        parsed.put(b.getKey(), parse(b.getKey(), b.getValue().getAsJsonObject()));
                    }
                    if (parsed.size() != 1060) throw new IllegalStateException("Incomplete block knowledge: " + parsed.size());
                    cache = Map.copyOf(parsed);
                    return cache;
                }
            } catch (Exception e) {
                throw new IllegalStateException("Could not load block knowledge: " + e.getMessage(), e);
            }
        }
    }

    public static Entry get(String name) {
        if (name == null || name.isBlank()) return null;
        String id = name.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (!id.contains(":")) id = "minecraft:" + id;
        return all().get(id);
    }

    public static Entry byItem(String name) {
        if (name == null) return null;
        String id = name.trim().toLowerCase(Locale.ROOT);
        if (!id.contains(":")) id = "minecraft:" + id;
        Entry own = get(id);
        if (own != null && id.equals(own.itemForm())) return own;
        for (Entry e : all().values()) if (id.equals(e.itemForm())) return e;
        return null;
    }

    public static List<String> suggestions(String name) {
        if (name == null) return List.of();
        String key = name.toLowerCase(Locale.ROOT).replace(' ', '_').replace("minecraft:", "");
        ArrayList<String> result = new ArrayList<>();
        for (Entry e : all().values()) {
            if (e.id().contains(key) || e.displayName().toLowerCase(Locale.ROOT).replace(' ', '_').contains(key)) result.add(e.id());
        }
        result.sort(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()));
        return result.subList(0, Math.min(6, result.size()));
    }

    /** Rank for intent/required capabilities; world search and inventory change the score at runtime. */
    public static List<Route> ranked(Entry entry, Set<String> inventory, Set<String> observedBlocks) {
        if (entry == null) return List.of();
        Set<String> items = inventory == null ? Set.of() : inventory;
        Set<String> known = observedBlocks == null ? Set.of() : observedBlocks;
        List<Route> ranked = new ArrayList<>();
        for (Route r : entry.routes()) {
            int score = r.score();
            if (r.method().startsWith("craft") && !r.ingredients().isEmpty()) {
                boolean allHave = true;
                for (String ing : r.ingredients()) if (!items.contains(ing)) {allHave = false; break;}
                score += allHave ? -16 : 10;
            }
            if (r.method().equals("mine")) score += known.contains(r.source()) ? -25 : 15;
            if (r.requirement().contains("silk_touch") && !items.contains("enchantment:silk_touch")) score += 120;
            ranked.add(new Route(r.method(), r.source(), score, r.requirement(), r.yield(), r.ingredients()));
        }
        ranked.sort(Comparator.comparingInt(Route::score).thenComparing(Route::method).thenComparing(Route::source));
        return List.copyOf(ranked);
    }

    private static Entry parse(String id, JsonObject b) {
        List<Route> routes = new ArrayList<>();
        JsonArray recipes = array(b, "craftedFrom");
        for (JsonElement element : recipes) {
            JsonObject recipe = element.getAsJsonObject();
            String kind = string(recipe, "type");
            String station = string(recipe, "station");
            ArrayList<String> ingredients = new ArrayList<>();
            if (recipe.has("ingredients") && recipe.get("ingredients").isJsonArray()) {
                for (JsonElement input : recipe.getAsJsonArray("ingredients")) ingredients.add(ingredient(input.getAsJsonObject()));
            } else if (recipe.has("ingredient") && recipe.get("ingredient").isJsonObject()) {
                ingredients.add(ingredient(recipe.getAsJsonObject("ingredient")));
            } else if (recipe.has("pattern") && recipe.has("key")) {
                JsonObject key = recipe.getAsJsonObject("key");
                for (JsonElement line : recipe.getAsJsonArray("pattern")) {
                    for (char c : line.getAsString().toCharArray()) {
                        if (c != ' ' && key.has(Character.toString(c))) {
                            ingredients.add(ingredient(key.getAsJsonObject(Character.toString(c))));
                        }
                    }
                }
            }
            // Don't claim special/dynamic recipes are executable without a matching Minecraft recipe.
            routes.add(new Route("craft/" + kind, station, 26, "", Math.max(1, integer(recipe, "resultCount", 1)), List.copyOf(ingredients)));
        }
        JsonObject obtained = b.has("obtainedFrom") && b.get("obtainedFrom").isJsonObject() ? b.getAsJsonObject("obtainedFrom") : new JsonObject();
        for (Map.Entry<String, JsonElement> group : obtained.entrySet()) {
            if (!group.getValue().isJsonArray()) continue;
            for (JsonElement el : group.getValue().getAsJsonArray()) {
                if (!el.isJsonObject()) continue;
                JsonObject method = el.getAsJsonObject();
                String requirement = conditions(method);
                String source = string(method, "source");
                if (source.isBlank()) source = string(method, "entity");
                if (source.isBlank()) source = string(method, "table");
                if (source.isBlank()) source = group.getKey();
                String kind = group.getKey().equals("blockDrops") ? "mine" : group.getKey();
                int score = switch (kind) {
                    case "mine" -> 22;
                    case "shearing" -> 35;
                    case "entityDrops" -> 52;
                    case "gameplay" -> 55;
                    case "chestLoot" -> 78;
                    default -> 72;
                };
                routes.add(new Route(kind, source, score, requirement, 1, List.of()));
            }
        }
        routes.sort(Comparator.comparingInt(Route::score));
        String item = b.has("itemForm") && !b.get("itemForm").isJsonNull() ? b.get("itemForm").getAsString() : null;
        return new Entry(id, string(b, "displayName"), item, b.has("obtainable") && b.get("obtainable").getAsBoolean(),
                string(b, "harvestTool"), List.copyOf(routes), string(b, "acquisitionNote"));
    }

    private static String ingredient(JsonObject input) {
        if (input.has("items") && input.get("items").isJsonArray()) {
            JsonArray possibilities = input.getAsJsonArray("items");
            if (!possibilities.isEmpty()) return possibilities.get(0).getAsString();
        }
        String tag = string(input, "tag");
        return tag.isBlank() ? "unknown_ingredient" : "#" + tag;
    }
    private static String conditions(JsonObject obj) {
        if (!obj.has("conditions") || !obj.get("conditions").isJsonArray()) return "";
        ArrayList<String> tokens = new ArrayList<>();
        for (JsonElement e : obj.getAsJsonArray("conditions")) if (e.isJsonPrimitive()) tokens.add(e.getAsString());
        return String.join(", ", tokens);
    }
    private static String string(JsonObject obj, String key) {return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : "";}
    private static int integer(JsonObject obj, String key, int fallback) {try {return obj.has(key) ? obj.get(key).getAsInt() : fallback;} catch(Exception e){return fallback;}}
    private static JsonArray array(JsonObject obj, String key) {return obj.has(key) && obj.get(key).isJsonArray() ? obj.getAsJsonArray(key) : new JsonArray();}
}
