package com.snuffys.multitool;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;

/**
 * What each production skill can make, at what level and XP, from what materials (recipes.json, regenerate
 * with scripts/update_recipe_data.py; OSRS Wiki).
 */
@Slf4j
@Singleton
class RecipeData {

    private static final String RESOURCE = "recipes.json";

    static final class Recipe {
        final String name;
        final int level;
        final double xp;
        final String facility;
        /** Material name -> how many one needs. */
        final Map<String, Integer> materials = new LinkedHashMap<>();
        /** Tools it needs ("Knife", "Hammer", "Tinderbox"), not used up. */
        final List<String> tools = new ArrayList<>();

        Recipe(JsonObject o) {
            name = o.get("name").getAsString();
            level = o.get("level").getAsInt();
            xp = o.get("xp").getAsDouble();
            facility = o.has("facility") ? o.get("facility").getAsString() : null;
            for (Map.Entry<String, JsonElement> m : o.getAsJsonObject("materials").entrySet()) {
                materials.put(m.getKey(), m.getValue().getAsInt());
            }
            if (o.has("tools")) {
                for (JsonElement t : o.getAsJsonArray("tools")) {
                    tools.add(t.getAsString());
                }
            }
        }

        /** How many times you can make it from these item counts (lower-case names). */
        int makeable(Map<String, Integer> have) {
            int times = Integer.MAX_VALUE;
            for (Map.Entry<String, Integer> material : materials.entrySet()) {
                times = Math.min(times, have.getOrDefault(material.getKey().toLowerCase(), 0) / material.getValue());
            }
            return times == Integer.MAX_VALUE ? 0 : times;
        }
    }

    private final Map<String, List<Recipe>> recipes = new HashMap<>();

    void load(Gson gson) {
        recipes.clear();
        try (InputStream in = RecipeData.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log.warn("{} not found; goal item suggestions disabled", RESOURCE);
                return;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject root = gson.fromJson(reader, JsonObject.class);
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("skills").entrySet()) {
                    List<Recipe> list = new ArrayList<>();
                    for (JsonElement r : e.getValue().getAsJsonArray()) {
                        list.add(new Recipe(r.getAsJsonObject()));
                    }
                    recipes.put(e.getKey(), list);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load {}", RESOURCE, e);
        }
    }

    boolean covers(Skill skill) {
        return recipes.containsKey(skill.name());
    }

    /**
     * The best thing to make at this level from what you have (lower-case item name -> count), scored by
     * {@code score} (XP or profit per action); null if you can't make anything.
     */
    Recipe best(Skill skill, int level, Map<String, Integer> have, ToDoubleFunction<Recipe> score) {
        return best(skill, level, have, score, r -> true);
    }

    /** As above, considering only recipes that pass {@code allowed}. */
    Recipe best(Skill skill, int level, Map<String, Integer> have, ToDoubleFunction<Recipe> score,
                java.util.function.Predicate<Recipe> allowed) {
        Recipe best = null;
        double bestScore = 0;
        for (Recipe recipe : recipes.getOrDefault(skill.name(), Collections.emptyList())) {
            // With no inventory given, anything at your level counts (for the "Cheapest" suggestion)
            if (recipe.level > level || (have != null && recipe.makeable(have) == 0)) {
                continue;
            }
            if (!allowed.test(recipe)) {
                continue;
            }
            double s = score.applyAsDouble(recipe);
            if (best == null || s > bestScore || (s == bestScore && recipe.level > best.level)) {
                best = recipe;
                bestScore = s;
            }
        }
        return best;
    }
}
