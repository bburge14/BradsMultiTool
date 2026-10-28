package com.bradsmultitool;

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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;

/**
 * Training methods by level from the wiki's training guides (training.json, regenerate with
 * scripts/update_training_data.py), and what each one trains on so it can be highlighted.
 */
@Slf4j
@Singleton
class TrainingData {

    private static final String RESOURCE = "training.json";

    static final class Method {
        final String name;
        final int from;
        final int to;
        /** From the guide's "Fastest experience" section (otherwise "Other methods"). */
        final boolean fastest;
        final Set<Integer> objects = new HashSet<>();
        final Set<String> npcs = new HashSet<>();

        Method(JsonObject o) {
            name = o.get("name").getAsString();
            from = o.get("from").getAsInt();
            to = o.get("to").getAsInt();
            fastest = o.get("fastest").getAsBoolean();
            if (o.has("objects")) {
                for (JsonElement e : o.getAsJsonArray("objects")) {
                    objects.add(e.getAsInt());
                }
            }
            if (o.has("npcs")) {
                for (JsonElement e : o.getAsJsonArray("npcs")) {
                    npcs.add(e.getAsString());
                }
            }
        }

        boolean covers(int level) {
            return level >= from && (level < to || to >= 99);
        }

        boolean highlightable() {
            return !objects.isEmpty() || !npcs.isEmpty();
        }
    }

    private final Map<String, List<Method>> methods = new HashMap<>();
    private final Set<Integer> allObjects = new HashSet<>();

    void load(Gson gson) {
        methods.clear();
        allObjects.clear();
        try (InputStream in = TrainingData.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log.warn("{} not found; training suggestions disabled", RESOURCE);
                return;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject root = gson.fromJson(reader, JsonObject.class);
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("skills").entrySet()) {
                    List<Method> list = new ArrayList<>();
                    for (JsonElement m : e.getValue().getAsJsonArray()) {
                        Method method = new Method(m.getAsJsonObject());
                        list.add(method);
                        allObjects.addAll(method.objects);
                    }
                    methods.put(e.getKey(), list);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load {}", RESOURCE, e);
        }
    }

    /**
     * The method to use at this level: the guide's fastest one, or with {@code alternatives} one of its
     * other methods first (often more AFK or cheaper). Falls back to whatever covers the level.
     */
    Method suggest(Skill skill, int level, boolean alternatives) {
        List<Method> list = methods.getOrDefault(skill.name(), Collections.emptyList());
        Method fallback = null;
        for (Method method : list) {
            if (!method.covers(level)) {
                continue;
            }
            if (method.fastest != alternatives) {
                return method;
            }
            if (fallback == null) {
                fallback = method;
            }
        }
        return fallback;
    }

    /** The method to switch to next on the way to {@code goalLevel}, or null if the current one lasts. */
    Method next(Skill skill, int level, int goalLevel, boolean alternatives) {
        Method current = suggest(skill, level, alternatives);
        for (int at = level + 1; at <= Math.min(goalLevel, 99); at++) {
            Method method = suggest(skill, at, alternatives);
            if (method != null && method != current) {
                return method;
            }
        }
        return null;
    }

    /** Every object id any method trains on (to know which spawned objects to keep track of). */
    Set<Integer> allObjects() {
        return allObjects;
    }
}
