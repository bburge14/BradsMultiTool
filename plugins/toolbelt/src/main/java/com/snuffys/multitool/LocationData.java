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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Where to train: for each tree, rock, stall, creature and pickpocket, the places it is, best first (the training
 * guides' picks with why, then the rest by how many there are), each with how many, members or not, and what you need
 * to use it. From locations.json (OSRS Wiki, CC BY-NC-SA 3.0: scripts/update_training_locations.py).
 */
@Slf4j
@Singleton
class LocationData {

    private static final String RESOURCE = "locations.json";

    /** One place a thing is. */
    static final class Place {
        final String place;
        /** How many of it there (0: the wiki doesn't count them). */
        final int count;
        final boolean members;
        /** What you need to use it, from the wiki ("Players must partially complete Legends' Quest"), or null. */
        final String note;
        /** Why the training guides pick it ("The wiki's 2-tick spot"), or null when it isn't one of their picks. */
        final String best;
        /** The quests you need to reach it (Song of the Elves for Prifddinas); started: only begun. */
        final List<Need> quests = new ArrayList<>();

        Place(JsonObject o) {
            place = o.get("place").getAsString();
            count = o.has("count") ? o.get("count").getAsInt() : 0;
            members = o.has("members") && o.get("members").getAsBoolean();
            note = o.has("note") ? o.get("note").getAsString() : null;
            best = o.has("best") ? o.get("best").getAsString() : null;
            if (o.has("quests")) {
                for (JsonElement e : o.getAsJsonArray("quests")) {
                    JsonObject q = e.getAsJsonObject();
                    quests.add(new Need(q.get("quest").getAsString(), q.has("started") && q.get("started").getAsBoolean()));
                }
            }
        }

        /** The quests you haven't done (or started, where that's enough) to reach it; empty: you can. */
        List<String> missing(QuestProgress progress) {
            List<String> out = new ArrayList<>();
            if (progress == null) {
                return out;
            }
            for (Need need : quests) {
                if (need.started ? !progress.isStarted(need.quest) : !progress.isFinished(need.quest)) {
                    out.add(need.started ? need.quest + " (started)" : need.quest);
                }
            }
            return out;
        }

        /** "Isle of Souls (3)". */
        String label() {
            return count > 0 ? place + " (" + count + ")" : place;
        }
    }

    /** A quest a place needs. */
    static final class Need {
        final String quest;
        final boolean started;

        Need(String quest, boolean started) {
            this.quest = quest;
            this.started = started;
        }
    }

    /**
     * The places with the ones you can reach first (in their order: the guides' picks, then the most), then the rest;
     * a place you can't reach yet says which quests it needs.
     */
    static List<Place> reachableFirst(List<Place> places, QuestProgress progress) {
        if (progress == null) {
            return places;
        }
        List<Place> out = new ArrayList<>();
        List<Place> later = new ArrayList<>();
        for (Place place : places) {
            (place.missing(progress).isEmpty() ? out : later).add(place);
        }
        out.addAll(later);
        return out;
    }

    private static final class Thing {
        final String name;
        final String skill;
        final List<Place> places = new ArrayList<>();

        Thing(JsonObject o) {
            name = o.get("name").getAsString();
            skill = o.has("skill") ? o.get("skill").getAsString() : "";
            for (JsonElement e : o.getAsJsonArray("places")) {
                places.add(new Place(e.getAsJsonObject()));
            }
        }
    }

    private final Map<String, Thing> things = new LinkedHashMap<>();

    void load(Gson gson) {
        things.clear();
        try (InputStream in = LocationData.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log.warn("{} not found; no training locations", RESOURCE);
                return;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject root = gson.fromJson(reader, JsonObject.class);
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("things").entrySet()) {
                    things.put(e.getKey(), new Thing(e.getValue().getAsJsonObject()));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load {}", RESOURCE, e);
        }
    }

    /** The places of a thing by its name ("Teak tree", "Master Farmer"), best first; empty when it's not known. */
    List<Place> of(String name) {
        Thing thing = name == null ? null : things.get(name.toLowerCase(Locale.ROOT));
        return thing == null ? Collections.emptyList() : thing.places;
    }

    /**
     * The places for a skill's action ("Teak logs" in Woodcutting: the teak trees' places; "Master Farmer" in
     * Thieving), best first; empty when nothing matches.
     */
    List<Place> forAction(String skill, String action) {
        List<Place> exact = of(action);
        if (!exact.isEmpty()) {
            return exact;
        }
        // (several pages can match, "Iron rocks" and "Iron rocks (Abandoned Mine)": the plain one, else the one with most)
        Thing best = null;
        for (Thing thing : things.values()) {
            if (!thing.skill.equalsIgnoreCase(skill) || !sameThing(thing.name, action)) {
                continue;
            }
            boolean plain = stem(thing.name).equals(stem(action));
            boolean bestPlain = best != null && stem(best.name).equals(stem(action));
            if (best == null || plain && !bestPlain || plain == bestPlain && thing.places.size() > best.places.size()) {
                best = thing;
            }
        }
        return best == null ? Collections.emptyList() : best.places;
    }

    /**
     * Whether a tree, rock, spot or creature is what an action gets ("Maple tree" and "Maple logs", "Iron rocks" and
     * "Iron ore").
     */
    static boolean sameThing(String target, String action) {
        String a = stem(target);
        String b = stem(action);
        return !a.isEmpty() && !b.isEmpty() && (a.equals(b) || a.startsWith(b + " ") || b.startsWith(a + " "));
    }

    private static String stem(String text) {
        String t = text == null ? "" : text.toLowerCase(Locale.ROOT).trim();
        t = t.replaceAll("^raw ", "");
        t = t.replaceAll(" (logs?|trees?|rocks?|ore|deposit|stall|spot)$", "");
        t = t.replaceAll("s$", "");
        return t.trim();
    }
}
