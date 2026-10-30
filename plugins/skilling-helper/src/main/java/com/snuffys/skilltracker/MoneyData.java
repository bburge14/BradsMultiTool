package com.snuffys.skilltracker;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;

/**
 * The wiki's money making guides (money.json, regenerate with scripts/update_money_data.py): what each
 * trains, its skill requirements and its estimated profit, for the "Money maker" training suggestion.
 */
@Slf4j
@Singleton
class MoneyData {

    private static final String RESOURCE = "money.json";

    static final class Guide {
        final String name;
        final Set<String> skills = new HashSet<>();
        final Map<String, Integer> reqs = new HashMap<>();
        final long profit;
        /** Profit per run (farming runs) rather than per hour. */
        final boolean perRun;

        Guide(JsonObject o) {
            name = o.get("name").getAsString();
            for (JsonElement e : o.getAsJsonArray("skills")) {
                skills.add(e.getAsString());
            }
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("reqs").entrySet()) {
                reqs.put(e.getKey(), e.getValue().getAsInt());
            }
            profit = o.get("profit").getAsLong();
            perRun = o.has("perRun") && o.get("perRun").getAsBoolean();
        }

        /** "~880K/hr" or "~186K per run". */
        String profitText() {
            return "~" + BradsMultiToolPanel.shortNumber(profit) + (perRun ? " per run" : "/hr");
        }
    }

    private final List<Guide> guides = new ArrayList<>();

    void load(Gson gson) {
        guides.clear();
        try (InputStream in = MoneyData.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log.warn("{} not found; money maker suggestions disabled", RESOURCE);
                return;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                for (JsonElement e : gson.fromJson(reader, JsonObject.class).getAsJsonArray("guides")) {
                    guides.add(new Guide(e.getAsJsonObject()));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load {}", RESOURCE, e);
        }
    }

    /**
     * The most profitable guide that trains this skill and whose requirements you meet, with this skill
     * at {@code level} and the rest at your levels ({@code levels} by skill name). Per-hour guides win over
     * per-run ones only by their numbers, which the wiki keeps in the same units within a skill.
     */
    Guide best(Skill skill, int level, ToIntFunction<String> levels) {
        Guide best = null;
        for (Guide guide : guides) {
            if (!guide.skills.contains(skill.name()) || !meets(guide, skill, level, levels)) {
                continue;
            }
            if (best == null || guide.profit > best.profit) {
                best = guide;
            }
        }
        return best;
    }

    private static boolean meets(Guide guide, Skill skill, int level, ToIntFunction<String> levels) {
        for (Map.Entry<String, Integer> req : guide.reqs.entrySet()) {
            int have = req.getKey().equals(skill.name()) ? level : levels.applyAsInt(req.getKey());
            if (have < req.getValue()) {
                return false;
            }
        }
        return true;
    }
}
