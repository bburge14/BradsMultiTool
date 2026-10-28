package com.bradsmultitool;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.ImageUtil;

/**
 * Slayer masters (requirements, where they are, how to get there) and where to kill each task, from
 * slayer.json (regenerate with scripts/update_slayer_data.py; OSRS Wiki).
 */
@Slf4j
@Singleton
class SlayerData {

    private static final String RESOURCE = "slayer.json";

    static final class Master {
        final String name;
        final int combat;
        final int slayer;
        final String quest;
        final String where;
        final List<String> routes = new ArrayList<>();
        final boolean wilderness;
        /** Short place name ("Tree Gnome Stronghold"). */
        final String area;
        /** Picture file in slayer/, or null. */
        final String icon;

        Master(JsonObject o) {
            area = text(o, "area");
            icon = text(o, "icon");
            name = o.get("name").getAsString();
            combat = o.get("combat").getAsInt();
            slayer = o.get("slayer").getAsInt();
            quest = text(o, "quest");
            where = text(o, "where");
            for (JsonElement e : o.getAsJsonArray("routes")) {
                routes.add(e.getAsString());
            }
            String note = text(o, "routeNote");
            if (routes.isEmpty() && note != null) {
                routes.add(Character.toUpperCase(note.charAt(0)) + note.substring(1));
            }
            wilderness = o.get("wilderness").getAsBoolean();
        }
    }

    static final class Place {
        final String name;
        final String page;
        final boolean multi;
        final boolean cannon;
        final boolean safespot;
        final String note;

        Place(JsonObject o) {
            name = o.get("name").getAsString();
            page = o.get("page").getAsString();
            multi = o.get("multi").getAsBoolean();
            cannon = o.get("cannon").getAsBoolean();
            safespot = o.get("safespot").getAsBoolean();
            note = text(o, "note");
        }

        boolean inWilderness() {
            String text = (name + " " + (note == null ? "" : note)).toLowerCase();
            return text.contains("wilderness");
        }

        /** "Multi, cannon, safespot", or "" when none apply. */
        String tags() {
            List<String> tags = new ArrayList<>();
            if (multi) {
                tags.add("Multi");
            }
            if (cannon) {
                tags.add("Cannon");
            }
            if (safespot) {
                tags.add("Safespot");
            }
            return String.join(", ", tags);
        }
    }

    static final class Task {
        final String name;
        final List<Place> places = new ArrayList<>();
        /** Picture file in slayer/, or null. */
        final String icon;

        Task(JsonObject o) {
            icon = text(o, "icon");
            name = o.get("name").getAsString();
            for (JsonElement e : o.getAsJsonArray("places")) {
                places.add(new Place(e.getAsJsonObject()));
            }
        }
    }

    private final List<Master> masters = new ArrayList<>();
    private final Map<String, Task> tasks = new HashMap<>();
    private final Map<String, List<String>> travel = new HashMap<>();
    private final Map<String, BufferedImage> pictures = new HashMap<>();

    void load(Gson gson) {
        masters.clear();
        tasks.clear();
        travel.clear();
        try (InputStream in = SlayerData.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log.warn("{} not found; Slayer directions disabled", RESOURCE);
                return;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject root = gson.fromJson(reader, JsonObject.class);
                for (JsonElement e : root.getAsJsonArray("masters")) {
                    masters.add(new Master(e.getAsJsonObject()));
                }
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("tasks").entrySet()) {
                    tasks.put(e.getKey(), new Task(e.getValue().getAsJsonObject()));
                }
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("travel").entrySet()) {
                    List<String> routes = new ArrayList<>();
                    for (JsonElement r : e.getValue().getAsJsonArray()) {
                        routes.add(r.getAsString());
                    }
                    travel.put(e.getKey(), routes);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load {}", RESOURCE, e);
        }
    }

    /**
     * The best master you can get tasks from: the highest combat requirement you meet (and Slayer level),
     * leaving out Krystilia (Wilderness only) and Mortimer (a different kind of task).
     */
    Master bestMaster(int combatLevel, int slayerLevel) {
        Master best = null;
        for (Master master : masters) {
            if (master.wilderness || master.name.startsWith("Mortimer")) {
                continue;
            }
            if (combatLevel >= master.combat && slayerLevel >= master.slayer && (best == null || master.combat > best.combat)) {
                best = master;
            }
        }
        return best;
    }

    /** The next master up from this one (higher combat requirement), or null. */
    Master nextMaster(Master current) {
        Master next = null;
        for (Master master : masters) {
            if (master.wilderness || master.name.startsWith("Mortimer") || master.combat <= (current == null ? -1 : current.combat)) {
                continue;
            }
            if (next == null || master.combat < next.combat) {
                next = master;
            }
        }
        return next;
    }

    /** Where to kill a task ("Abyssal demons", "Lesser demons"...), or null if the wiki has no page for it. */
    Task task(String taskName) {
        if (taskName == null) {
            return null;
        }
        String key = taskName.toLowerCase();
        Task task = tasks.get(key);
        if (task == null && key.endsWith("s")) {
            task = tasks.get(key.substring(0, key.length() - 1));
        }
        return task != null ? task : tasks.get(key + "s");
    }

    /** A picture from slayer/ (loaded once), or null. */
    BufferedImage picture(String file) {
        if (file == null) {
            return null;
        }
        return pictures.computeIfAbsent(file, f -> {
            try {
                return ImageUtil.loadImageResource(SlayerData.class, "slayer/" + f);
            } catch (RuntimeException e) {
                return null;
            }
        });
    }

    /** Ways to reach a place (its wiki page), or none. */
    List<String> travel(String page) {
        return travel.getOrDefault(page, Collections.emptyList());
    }

    private static String text(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }
}
