package com.snuffys.skilltracker;

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
        /** Quests (or miniquests) the guide says it requires. */
        final List<String> quests = new ArrayList<>();
        /** Other skills' levels it requires (skill name -> level). */
        final Map<String, Integer> skills = new HashMap<>();
        /** For "Questing": the quests the guide recommends for their XP. */
        final List<String> rewards = new ArrayList<>();
        /** What it trains on, when the name doesn't say ("Fly fishing": Trout, Salmon). */
        final List<String> targets = new ArrayList<>();
        /** The guide's first sentences about it, or null. */
        final String about;
        /** Another non-combat skill's guide lists it too (it trains both). */
        boolean shared;
        /** The guide's table of what to make or use by level (Herblore's potions), or empty. */
        final List<Option> options = new ArrayList<>();

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
            if (o.has("quests")) {
                for (JsonElement e : o.getAsJsonArray("quests")) {
                    quests.add(e.getAsString());
                }
            }
            if (o.has("rewards")) {
                for (JsonElement e : o.getAsJsonArray("rewards")) {
                    rewards.add(e.getAsString());
                }
            }
            if (o.has("skills")) {
                for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("skills").entrySet()) {
                    skills.put(e.getKey(), e.getValue().getAsInt());
                }
            }
            if (o.has("targets")) {
                for (JsonElement e : o.getAsJsonArray("targets")) {
                    targets.add(e.getAsString());
                }
            }
            about = o.has("about") ? o.get("about").getAsString() : null;
            if (o.has("options")) {
                for (JsonElement e : o.getAsJsonArray("options")) {
                    options.add(new Option(e.getAsJsonObject()));
                }
            }
        }

        /**
         * The guide's pick at this level from its table, or null: the highest-level row you can do (the tables are
         * the guide's progression), the most XP among rows at that level.
         */
        Option bestOption(int level) {
            Option best = null;
            for (Option option : options) {
                if (option.level <= level && (best == null || option.level > best.level
                    || option.level == best.level && option.xp > best.xp)) {
                    best = option;
                }
            }
            return best;
        }

        boolean covers(int level) {
            return level >= from && (level < to || to >= 99);
        }

        boolean isQuesting() {
            return name.toLowerCase().startsWith("quest");
        }

        boolean highlightable() {
            return !objects.isEmpty() || !npcs.isEmpty();
        }
    }

    /** One row of a method's table: from this level, make this ("Strength potion") from these, for this XP. */
    static final class Option {
        final int level;
        final String name;
        /** XP each, or the table's XP per hour where it lists that instead (then only for ranking). */
        final double xp;
        final List<String> with = new ArrayList<>();

        Option(JsonObject o) {
            level = o.get("level").getAsInt();
            name = o.get("name").getAsString();
            xp = o.get("xp").getAsDouble();
            if (o.has("with")) {
                for (JsonElement e : o.getAsJsonArray("with")) {
                    with.add(e.getAsString());
                }
            }
        }

        /**
         * XP for one action, when the table gives that: tables that list ingredients (Herblore's potions,
         * Construction's furniture) give XP each; others' numbers can be bars or XP per hour.
         */
        double xpEach() {
            return !with.isEmpty() && xp < 2000 ? xp : 0;
        }
    }

    private final Map<String, List<Method>> methods = new HashMap<>();
    private final Set<Integer> allObjects = new HashSet<>();

    private static final Set<String> COMBAT = new HashSet<>(java.util.Arrays.asList(
        "ATTACK", "STRENGTH", "DEFENCE", "HITPOINTS", "RANGED", "MAGIC", "PRAYER"));

    // A method another non-combat skill's guide also lists trains both (Drift net fishing: Fishing and Hunter);
    // it's only the fastest for one of them if you're training both, so the skill's own methods go first
    private void markShared() {
        Map<String, Set<String>> skillsOf = new HashMap<>();
        methods.forEach((skill, list) -> {
            if (!COMBAT.contains(skill)) {
                list.forEach(m -> skillsOf.computeIfAbsent(m.name.toLowerCase(), k -> new HashSet<>()).add(skill));
            }
        });
        methods.forEach((skill, list) -> list.forEach(m ->
            m.shared = !COMBAT.contains(skill) && skillsOf.getOrDefault(m.name.toLowerCase(), Collections.emptySet()).size() > 1));
    }

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
                markShared();
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
        return suggest(skill, level, alternatives, m -> true);
    }

    /** As above, only among methods you can do ({@code canDo}: quests and other skills' levels). */
    // The skill's own methods before shared ones, then the latest-starting
    private static boolean better(Method method, Method than) {
        if (method.shared != than.shared) {
            return !method.shared;
        }
        return method.from > than.from;
    }

    Method suggest(Skill skill, int level, boolean alternatives, java.util.function.Predicate<Method> canDo) {
        List<Method> list = methods.getOrDefault(skill.name(), Collections.emptyList());
        Method chosen = null;
        Method fallback = null;
        Method questing = null;
        for (Method method : list) {
            if (!method.covers(level) || !canDo.test(method)) {
                continue;
            }
            // The guides open with "Questing"; suggest a method you can repeat, and questing only if there's none
            if (method.isQuesting()) {
                questing = questing == null ? method : questing;
                continue;
            }
            // The guides list methods by the level they start at, each taking over from the one before: the
            // latest-starting one that covers your level ("Barbarian Fishing" from 58 over "Drift net" from 47)
            if (method.fastest != alternatives) {
                if (chosen == null || better(method, chosen)) {
                    chosen = method;
                }
            } else if (fallback == null || method.from > fallback.from) {
                fallback = method;
            }
        }
        return chosen != null ? chosen : fallback != null ? fallback : questing;
    }

    /** The guide's questing method for this level (quests worth doing for their XP), or null. */
    Method questing(Skill skill, int level) {
        for (Method method : methods.getOrDefault(skill.name(), Collections.emptyList())) {
            if (method.isQuesting() && method.covers(level) && !method.rewards.isEmpty()) {
                return method;
            }
        }
        return null;
    }

    /** The method to switch to next on the way to {@code goalLevel}, or null if the current one lasts. */
    Method next(Skill skill, int level, int goalLevel, boolean alternatives) {
        Method current = suggest(skill, level, alternatives);
        for (int at = level + 1; at <= Math.min(goalLevel, 99); at++) {
            Method method = suggest(skill, at, alternatives);
            if (method != null && method != current && !method.isQuesting()) {
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
