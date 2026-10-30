package com.snuffys.skilltracker;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.ObjectComposition;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Farming patches: what's in a patch and how far along it is, decoded from the game's patch varbits.
 * <ul>
 * <li>farming_patches.json: every patch (region + varbit) and how each varbit value maps to a crop, its
 * condition and growth stage. Converted from RuneLite's Time Tracking plugin (BSD-2-Clause) by
 * scripts/update_farming_patches.py.</li>
 * <li>farming.json: each crop's Farming level, protection payment and growth time from the OSRS Wiki
 * (scripts/update_farming_data.py).</li>
 * </ul>
 * Compost is tracked from the same chat messages RuneLite's Time Tracking uses.
 */
@Slf4j
@Singleton
class FarmingData {

    private static final String CROPS_RESOURCE = "farming.json";
    private static final String PATCHES_RESOURCE = "farming_patches.json";
    private static final String CONFIG_GROUP = "bradsmultitool";

    static final String WEEDS = "WEEDS";
    static final String SCARECROW = "SCARECROW";
    static final String GROWING = "GROWING";
    static final String HARVESTABLE = "HARVESTABLE";
    static final String DISEASED = "DISEASED";
    static final String DEAD = "DEAD";

    /** Wiki info for a crop. */
    static final class Crop {
        final String name;
        final int level;
        final String payment;
        final String growth;

        Crop(JsonObject o) {
            name = o.get("name").getAsString();
            level = o.get("level").getAsInt();
            payment = text(o, "payment");
            growth = text(o, "growth");
        }

        private static String text(JsonObject o, String key) {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
        }
    }

    /** A crop as the patch varbits know it: which patch type it grows in, minutes per stage, stage count. */
    static final class Produce {
        final String id;
        final String name;
        final String implementation;
        final int tickrate;
        final int stages;

        Produce(String id, String name, String implementation, int tickrate, int stages) {
            this.id = id;
            this.name = name;
            this.implementation = implementation;
            this.tickrate = tickrate;
            this.stages = stages;
        }
    }

    /** A patch in the world: which region and varbit hold its state and what kind of patch it is. */
    static final class Patch {
        final String name;
        final String region;
        final int regionId;
        final int varbit;
        final String implementation;

        Patch(String name, String region, int regionId, int varbit, String implementation) {
            this.name = name;
            this.region = region;
            this.regionId = regionId;
            this.varbit = varbit;
            this.implementation = implementation;
        }

        String key() {
            return regionId + "." + varbit;
        }

        /** "Herb patch", "Allotment (North)". */
        String title() {
            String type = typeName(implementation);
            return name == null || name.isEmpty() ? type : type + " (" + name + ")";
        }
    }

    /** What's in a patch right now. */
    static final class State {
        final Produce produce;
        final String cropState;
        /** 0-based growth stage; the crop has {@code produce.stages} (the last is fully grown). */
        final int stage;
        final boolean watered;

        State(Produce produce, String cropState, int stage, boolean watered) {
            this.produce = produce;
            this.cropState = cropState;
            this.stage = stage;
            this.watered = watered;
        }

        boolean isEmpty() {
            return WEEDS.equals(produce.id) || SCARECROW.equals(produce.id);
        }
    }

    // One varbit range of a patch type: values low..high mean this crop, condition and stage (a * value + b)
    private static final class Rule {
        final int low;
        final int high;
        final String produce;
        final String cropState;
        final int a;
        final int b;

        Rule(JsonArray r) {
            low = r.get(0).getAsInt();
            high = r.get(1).getAsInt();
            produce = r.get(2).getAsString();
            cropState = r.get(3).getAsString();
            a = r.get(4).getAsInt();
            b = r.get(5).getAsInt();
        }
    }

    // Time Tracking's compost messages
    private static final Pattern COMPOST_USED = Pattern.compile("You treat the .+ with (ultra|super|)compost\\.");
    private static final Pattern FERTILE_SOIL = Pattern.compile("^The .+ has been treated with (ultra|super|)compost");
    private static final Pattern ALREADY_TREATED = Pattern.compile(
        "This .+ has already been (?:treated|fertilised) with (ultra|super|)compost");
    private static final Pattern INSPECT = Pattern.compile(
        "This is an? .+\\. The soil has (?:been treated with (ultra|super|)compost|not been treated)");
    static final Set<String> WATERABLE = Set.of("ALLOTMENT", "FLOWER", "HOPS");

    private final Client client;
    private final ConfigManager configManager;
    private final Map<String, Crop> crops = new HashMap<>();
    private final Map<String, Produce> produce = new HashMap<>();
    private final Map<String, List<Rule>> rules = new HashMap<>();
    // Patches by the region they're in (its own region first, then the neighbouring regions that also send it)
    private final Map<Integer, List<Patch>> primary = new HashMap<>();
    private final Map<Integer, List<Patch>> nearby = new HashMap<>();
    private final List<Patch> patches = new ArrayList<>();
    private Patch lastClicked;
    private int lastClickedTick = -100;

    @Inject
    FarmingData(Client client, ConfigManager configManager) {
        this.client = client;
        this.configManager = configManager;
    }

    void load(Gson gson) {
        crops.clear();
        produce.clear();
        rules.clear();
        primary.clear();
        nearby.clear();
        patches.clear();
        JsonObject cropData = read(gson, CROPS_RESOURCE);
        if (cropData != null) {
            for (Map.Entry<String, JsonElement> e : cropData.getAsJsonObject("crops").entrySet()) {
                crops.put(e.getKey(), new Crop(e.getValue().getAsJsonObject()));
            }
        }
        JsonObject patchData = read(gson, PATCHES_RESOURCE);
        if (patchData == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> e : patchData.getAsJsonObject("produce").entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            produce.put(e.getKey(), new Produce(e.getKey(), o.get("name").getAsString(),
                o.get("patch").isJsonNull() ? null : o.get("patch").getAsString(),
                o.get("tickrate").getAsInt(), o.get("stages").getAsInt()));
        }
        for (Map.Entry<String, JsonElement> e : patchData.getAsJsonObject("implementations").entrySet()) {
            List<Rule> list = new ArrayList<>();
            for (JsonElement r : e.getValue().getAsJsonArray()) {
                list.add(new Rule(r.getAsJsonArray()));
            }
            rules.put(e.getKey(), list);
        }
        for (JsonElement r : patchData.getAsJsonArray("regions")) {
            JsonObject region = r.getAsJsonObject();
            String name = region.get("name").getAsString();
            int regionId = region.get("regionId").getAsInt();
            for (JsonElement p : region.getAsJsonArray("patches")) {
                JsonObject o = p.getAsJsonObject();
                Patch patch = new Patch(o.get("name").getAsString(), name, regionId, o.get("varbit").getAsInt(),
                    o.get("implementation").getAsString());
                patches.add(patch);
                primary.computeIfAbsent(regionId, k -> new ArrayList<>()).add(patch);
                for (JsonElement extra : region.getAsJsonArray("extraRegions")) {
                    nearby.computeIfAbsent(extra.getAsInt(), k -> new ArrayList<>()).add(patch);
                }
            }
        }
    }

    private static JsonObject read(Gson gson, String resource) {
        try (InputStream in = FarmingData.class.getResourceAsStream(resource)) {
            if (in == null) {
                log.warn("{} not found; farming hover info disabled", resource);
                return null;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return gson.fromJson(reader, JsonObject.class);
            }
        } catch (Exception e) {
            log.warn("Failed to load {}", resource, e);
            return null;
        }
    }

    Crop crop(String name) {
        return name == null ? null : crops.get(name.toLowerCase());
    }

    /** Crops that grow in this kind of patch, lowest level first. */
    List<Crop> cropsFor(String implementation) {
        List<Crop> out = new ArrayList<>();
        for (Produce p : produce.values()) {
            if (implementation.equals(p.implementation)) {
                Crop crop = crop(p.name);
                if (crop != null && !out.contains(crop)) {
                    out.add(crop);
                }
            }
        }
        out.sort((a, b) -> Integer.compare(a.level, b.level));
        return out;
    }

    /** The patch whose state lives in {@code varbit}, at this location, or null. */
    Patch patch(int varbit, WorldPoint location) {
        if (varbit < 0 || location == null) {
            return null;
        }
        int region = location.getRegionID();
        for (Map<Integer, List<Patch>> byRegion : List.of(primary, nearby)) {
            for (Patch patch : byRegion.getOrDefault(region, List.of())) {
                if (patch.varbit == varbit) {
                    return patch;
                }
            }
        }
        return null;
    }

    State state(Patch patch) {
        return decode(patch.implementation, client.getVarbitValue(patch.varbit));
    }

    /** What a patch of this type holds when its varbit is {@code value}, or null if unknown. */
    State decode(String implementation, int value) {
        for (Rule rule : rules.getOrDefault(implementation, List.of())) {
            if (value >= rule.low && value <= rule.high) {
                Produce crop = produce.get(rule.produce);
                if (crop == null) {
                    return null;
                }
                return new State(crop, rule.cropState, rule.a * value + rule.b, isWatered(implementation, value, crop, rule.cropState));
            }
        }
        return null;
    }

    /**
     * Allotments and flowers keep watered growth stages in their own block of states (64-127), hops from
     * 126 up; other patches can't be watered.
     */
    private static boolean isWatered(String implementation, int value, Produce crop, String cropState) {
        if (!WATERABLE.contains(implementation) || !GROWING.equals(cropState)
            || WEEDS.equals(crop.id) || SCARECROW.equals(crop.id)) {
            return false;
        }
        return "HOPS".equals(implementation) ? value >= 126 : value >= 64 && value < 128;
    }

    /**
     * Seconds until fully grown: crops grow one stage per growth cycle, and cycles tick over on the clock
     * (every 5, 10, 20, 40... minutes), so this is an estimate to within a few minutes.
     */
    static long secondsUntilGrown(State state, long nowSeconds) {
        int stagesLeft = state.produce.stages - 1 - state.stage;
        int cycle = state.produce.tickrate * 60;
        if (stagesLeft <= 0 || cycle <= 0) {
            return 0;
        }
        long toNextCycle = cycle - nowSeconds % cycle;
        return toNextCycle + (long) (stagesLeft - 1) * cycle;
    }

    // "HARDWOOD_TREE" -> "Hardwood tree patch"
    static String typeName(String implementation) {
        switch (implementation) {
            case "ALLOTMENT": return "Allotment";
            case "GRAPES": return "Grapevine";
            case "COMPOST": return "Compost bin";
            case "BIG_COMPOST": return "Big compost bin";
            case "CORAL": return "Coral nursery";
            default:
                String words = implementation.replace('_', ' ').toLowerCase();
                return Character.toUpperCase(words.charAt(0)) + words.substring(1) + " patch";
        }
    }

    // ---- Compost ----

    // Remember which patch you last worked on, so the compost message that follows can be pinned to it
    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (!isObjectAction(event.getMenuAction())) {
            return;
        }
        ObjectComposition composition = client.getObjectDefinition(event.getId());
        WorldView view = client.getTopLevelWorldView();
        Patch patch = composition == null ? null : patch(composition.getVarbitId(),
            WorldPoint.fromScene(view, event.getParam0(), event.getParam1(), view.getPlane()));
        if (patch != null) {
            lastClicked = patch;
            lastClickedTick = client.getTickCount();
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() == ChatMessageType.GAMEMESSAGE || event.getType() == ChatMessageType.SPAM) {
            onChatMessage(Text.removeTags(event.getMessage()));
        }
    }

    static boolean isObjectAction(MenuAction type) {
        switch (type) {
            case GAME_OBJECT_FIRST_OPTION:
            case GAME_OBJECT_SECOND_OPTION:
            case GAME_OBJECT_THIRD_OPTION:
            case GAME_OBJECT_FOURTH_OPTION:
            case GAME_OBJECT_FIFTH_OPTION:
            case WIDGET_TARGET_ON_GAME_OBJECT:
                return true;
            default:
                return false;
        }
    }

    /** Compost on the patch: "Compost", "Supercompost", "Ultracompost", or null for none / unknown. */
    String compost(Patch patch) {
        return configManager.getRSProfileConfiguration(CONFIG_GROUP, "compost." + patch.key());
    }

    /** Reads compost from the patch you just worked on or inspected. */
    void onChatMessage(String message) {
        if (lastClicked == null || client.getTickCount() - lastClickedTick > 50) {
            return;
        }
        for (Pattern pattern : new Pattern[]{COMPOST_USED, FERTILE_SOIL, ALREADY_TREATED, INSPECT}) {
            Matcher m = pattern.matcher(message);
            if (m.find()) {
                String type = m.group(1);
                setCompost(lastClicked, type == null ? null : compostName(type));
                return;
            }
        }
    }

    /** Compost is used up with the crop: clear it once the patch is back to weeds or empty. */
    void clearCompostIfEmpty(Patch patch, State state) {
        if (state != null && state.isEmpty() && compost(patch) != null) {
            setCompost(patch, null);
        }
    }

    private void setCompost(Patch patch, String compost) {
        if (compost == null) {
            configManager.unsetRSProfileConfiguration(CONFIG_GROUP, "compost." + patch.key());
        } else {
            configManager.setRSProfileConfiguration(CONFIG_GROUP, "compost." + patch.key(), compost);
        }
    }

    private static String compostName(String type) {
        switch (type) {
            case "ultra": return "Ultracompost";
            case "super": return "Supercompost";
            default: return "Compost";
        }
    }

    Collection<Patch> patches() {
        return patches;
    }
}
