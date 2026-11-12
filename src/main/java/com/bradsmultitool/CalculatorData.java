package com.bradsmultitool;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Experience;
import net.runelite.api.Skill;

/**
 * The skill calculator's data: for each skill, every action with its level, XP and picture, and the XP bonuses
 * (outfits, diaries, altars) with their multipliers, which can be ticked together and which actions they apply to.
 * calculator.json is made from RuneLite's own Skill Calculator (BSD-2-Clause, (c) the RuneLite contributors) by
 * scripts/update_calculator_data.py (with each action's members-only flag); the sums here work the way that calculator's do.
 */
@Slf4j
@Singleton
class CalculatorData {

    private static final String RESOURCE = "calculator.json";

    static final class Action {
        final String name;
        final int level;
        final double xp;
        /** Its item picture, or -1 (then {@link #sprite}). */
        final int icon;
        /** Its sprite (spells), or -1. */
        final int sprite;
        /** The skill's bonuses that don't apply to it. */
        final Set<String> noBonus = new HashSet<>();
        /** Members-only (RuneLite's Skill Calculator hides it on a free world). */
        boolean members;
        /** A minigame (from the training guides), timed by the hour: one action is an hour's play at its level. */
        boolean minigame;

        Action(String name, int level, double xp, int icon, int sprite) {
            this.name = name;
            this.level = level;
            this.xp = xp;
            this.icon = icon;
            this.sprite = sprite;
        }

        Action(JsonObject o) {
            this(o.get("name").getAsString(), o.get("level").getAsInt(), o.get("xp").getAsDouble(),
                o.has("icon") ? o.get("icon").getAsInt() : -1, o.has("sprite") ? o.get("sprite").getAsInt() : -1);
            members = o.has("members") && o.get("members").getAsBoolean();
            if (o.has("noBonus")) {
                for (JsonElement e : o.getAsJsonArray("noBonus")) {
                    noBonus.add(e.getAsString());
                }
            }
        }
    }

    static final class Bonus {
        final String id;
        final String name;
        /** What the XP is multiplied by (1.025 for an outfit's 2.5%, 3.5 at a gilded altar, 0.5 for the bonecrusher). */
        final double value;
        /** The other bonuses it can be ticked with. */
        final Set<String> stacks = new HashSet<>();

        Bonus(String id, String name, double value, Collection<String> stacks) {
            this.id = id;
            this.name = name;
            this.value = value;
            this.stacks.addAll(stacks);
        }

        Bonus(JsonObject o) {
            this(o.get("id").getAsString(), o.get("name").getAsString(), o.get("value").getAsDouble(), Collections.emptyList());
            for (JsonElement e : o.getAsJsonArray("stacks")) {
                stacks.add(e.getAsString());
            }
        }

        /** "+2.5%", "x3.5", "-50%". */
        String label() {
            if (value >= 2) {
                double rounded = Math.round(value * 100) / 100.0;
                return "x" + (rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded));
            }
            double percent = Math.round((value - 1) * 10_000) / 100.0;
            String text = percent == Math.rint(percent) ? String.valueOf((long) percent) : String.valueOf(percent);
            return (percent > 0 ? "+" : "") + text + "%";
        }
    }

    static final class Calc {
        final List<Action> actions = new ArrayList<>();
        final List<Bonus> bonuses = new ArrayList<>();

        Bonus bonus(String id) {
            for (Bonus bonus : bonuses) {
                if (bonus.id.equals(id)) {
                    return bonus;
                }
            }
            return null;
        }
    }

    private final Map<Skill, Calc> skills = new EnumMap<>(Skill.class);

    void load(Gson gson) {
        skills.clear();
        try (InputStream in = CalculatorData.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log.warn("{} not found; the Calculator is empty", RESOURCE);
                return;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject root = gson.fromJson(reader, JsonObject.class);
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("skills").entrySet()) {
                    Skill skill;
                    try {
                        skill = Skill.valueOf(e.getKey());
                    } catch (IllegalArgumentException unknown) {
                        continue;
                    }
                    Calc calc = new Calc();
                    JsonObject o = e.getValue().getAsJsonObject();
                    for (JsonElement b : o.getAsJsonArray("bonuses")) {
                        calc.bonuses.add(new Bonus(b.getAsJsonObject()));
                    }
                    for (JsonElement a : o.getAsJsonArray("actions")) {
                        calc.actions.add(new Action(a.getAsJsonObject()));
                    }
                    skills.put(skill, calc);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load {}", RESOURCE, e);
        }
    }

    /** The skills it has actions for, in the Skills tab's order. */
    List<Skill> skills() {
        List<Skill> out = new ArrayList<>();
        for (Skill skill : SKILLS_TAB) {
            if (skills.containsKey(skill)) {
                out.add(skill);
            }
        }
        return out;
    }

    /** The actions to show: all of them, or on a free world ({@code members} false) only the free ones. */
    static List<Action> shown(Calc calc, boolean members) {
        if (members) {
            return calc.actions;
        }
        List<Action> out = new ArrayList<>();
        for (Action action : calc.actions) {
            if (!action.members) {
                out.add(action);
            }
        }
        return out;
    }

    /** The action of this name (any case), or null. */
    static Action named(Calc calc, String name) {
        for (Action action : calc.actions) {
            if (action.name.equalsIgnoreCase(name)) {
                return action;
            }
        }
        return null;
    }

    /** The skill's actions and bonuses (empty when there are none). */
    /**
     * The training guides' minigames with the wiki's rates (Wintertodt, Tempoross...) as actions of their skills, so a
     * plan can have one: an action is an hour's play (its XP that at its first level), no bonuses, members.
     */
    void addMinigames(TrainingData training) {
        for (Map.Entry<Skill, Calc> e : skills.entrySet()) {
            for (TrainingData.Method method : training.methods(e.getKey())) {
                if (!method.minigame || method.xpHourAt(method.from) <= 0) {
                    continue;
                }
                Calc calc = e.getValue();
                boolean there = false;
                for (Action a : calc.actions) {
                    there |= a.name.equalsIgnoreCase(method.name);
                }
                if (there) {
                    continue;
                }
                Action action = new Action(method.name, method.from, method.xpHourAt(method.from), -1, -1);
                action.members = true;
                action.minigame = true;
                for (Bonus bonus : calc.bonuses) {
                    action.noBonus.add(bonus.id);
                }
                calc.actions.add(action);
            }
        }
    }

    Calc calc(Skill skill) {
        Calc calc = skills.get(skill);
        return calc != null ? calc : new Calc();
    }

    // The Skills tab, row by row
    private static final Skill[] SKILLS_TAB = {
        Skill.ATTACK, Skill.HITPOINTS, Skill.MINING, Skill.STRENGTH, Skill.AGILITY, Skill.SMITHING, Skill.DEFENCE,
        Skill.HERBLORE, Skill.FISHING, Skill.RANGED, Skill.THIEVING, Skill.COOKING, Skill.PRAYER, Skill.CRAFTING,
        Skill.FIREMAKING, Skill.MAGIC, Skill.FLETCHING, Skill.WOODCUTTING, Skill.RUNECRAFT, Skill.SLAYER, Skill.FARMING,
        Skill.CONSTRUCTION, Skill.HUNTER, Skill.SAILING};

    // ---- The sums (as RuneLite's Skill Calculator does them: XP in tenths, rounded down) ----

    /** The bonuses' combined multiplier for this action (those that don't apply to it are left out). */
    static double multiplier(Action action, Collection<Bonus> on) {
        double bonus = 1;
        for (Bonus b : on) {
            if (!action.noBonus.contains(b.id)) {
                bonus *= b.value;
            }
        }
        return bonus;
    }

    /** XP for one action with these bonuses, in tenths of an XP (the game keeps XP to one decimal place). */
    static int tenths(Action action, Collection<Bonus> on) {
        // (in floats, as there, so the same numbers come out)
        float bonus = 1f;
        for (Bonus b : on) {
            if (!action.noBonus.contains(b.id)) {
                bonus *= (float) b.value;
            }
        }
        return (int) Math.floor((float) action.xp * 10f * bonus);
    }

    /** XP for one action with these bonuses. */
    static double xpEach(Action action, Collection<Bonus> on) {
        return tenths(action, on) / 10.0;
    }

    /** How many times to do it to get from {@code xp} to {@code target} (0 when already there). */
    static int needed(Action action, Collection<Bonus> on, int xp, int target) {
        int each = tenths(action, on);
        long need = (long) Math.max(0, target - xp) * 10;
        if (need <= 0 || each <= 0) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, (need - 1) / each + 1);
    }

    /**
     * The bonuses ticked after ticking (or unticking) {@code bonus}: ticking one unticks any already on that it can't
     * be combined with (one altar at a time, one shark lure).
     */
    static Set<String> toggle(Calc calc, Set<String> on, String bonus, boolean tick) {
        Set<String> out = new LinkedHashSet<>(on);
        out.removeIf(id -> calc.bonus(id) == null);
        if (!tick) {
            out.remove(bonus);
            return out;
        }
        Bonus picked = calc.bonus(bonus);
        if (picked == null) {
            return out;
        }
        out.removeIf(id -> !id.equals(bonus) && !calc.bonus(id).stacks.contains(bonus));
        out.add(bonus);
        return out;
    }

    /** The ticked bonuses' objects. */
    static List<Bonus> bonuses(Calc calc, Set<String> on) {
        List<Bonus> out = new ArrayList<>();
        for (Bonus bonus : calc.bonuses) {
            if (on.contains(bonus.id)) {
                out.add(bonus);
            }
        }
        return out;
    }

    /** The level {@code xp} reaches (1 to 99, or up to 126 virtual levels when asked). */
    static int level(int xp, boolean virtual) {
        int level = Experience.getLevelForXp(Math.max(0, Math.min(Experience.MAX_SKILL_XP, xp)));
        return virtual ? level : Math.min(99, level);
    }
}
