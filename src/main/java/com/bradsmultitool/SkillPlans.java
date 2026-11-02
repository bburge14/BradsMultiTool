package com.bradsmultitool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;

/**
 * Skill plans as they're saved in the shared group ("bradsmultitool", RuneScape profile keys), like {@link SkillGoals}:
 * a final goal level and its milestones, each a step from one level to another with its own method ("1-20 Logs,
 * 20-50 Willow logs, 50-99 Teak logs"). The Planner's Calculator keeps the plan you're making under
 * {@code plan.<SKILL>}; Export to tracker copies it to {@code goalPlan.<SKILL>} (and sets the goal), which is what
 * the Skilling Helper reads, so neither plugin needs the other's classes. Saved as plain text, one line each:
 * {@code goal\t99}, then {@code from\tto\taction\tmethod\txp} for each milestone (the action as the Calculator names
 * it, the training guide's method it matched or empty, the XP of one action with your bonuses).
 */
final class SkillPlans {

    static final String GROUP = SkillGoals.GROUP;
    /** The plan being made in the Planner. */
    static final String PLAN_KEY = "plan.";
    /** The plan exported to the Skilling Helper's tracker. */
    static final String TRACKED_KEY = "goalPlan.";

    /** One milestone: train {@link #action} from level {@link #from} until level {@link #to}. */
    static final class Step {
        String action;
        int from;
        int to;
        /** The training guide's method it matches (TrainingData's name), or null. */
        String method;
        /** XP for one action (with the Calculator's bonuses when exported), 0 if unknown. */
        double xp;

        Step(String action, int from, int to, String method, double xp) {
            this.action = action;
            this.from = from;
            this.to = to;
            this.method = method;
            this.xp = xp;
        }

        Step copy() {
            return new Step(action, from, to, method, xp);
        }

        /** Whether you train it at this level. */
        boolean covers(int level) {
            return level >= from && level < to;
        }
    }

    /** A final goal level and the milestones on the way, in order. */
    static final class Plan {
        int goal;
        final List<Step> steps = new ArrayList<>();

        Plan(int goal) {
            this.goal = goal;
        }

        Plan copy() {
            Plan out = new Plan(goal);
            for (Step step : steps) {
                out.steps.add(step.copy());
            }
            return out;
        }

        /** The milestone you're on at this level, or null (before the first, past the last, or in a gap). */
        Step at(int level) {
            for (Step step : steps) {
                if (step.covers(level)) {
                    return step;
                }
            }
            return null;
        }

        /** The milestone you're on, else the next one ahead; null when you're past them all. */
        Step milestone(int level) {
            Step at = at(level);
            if (at != null) {
                return at;
            }
            Step next = null;
            for (Step step : steps) {
                if (step.from > level && (next == null || step.from < next.from)) {
                    next = step;
                }
            }
            return next;
        }

        /** Where the last milestone ends (the start level when there are none). */
        int end() {
            int end = 0;
            for (Step step : steps) {
                end = Math.max(end, step.to);
            }
            return end;
        }

        /** The XP to its goal level. */
        int goalXp() {
            return Experience.getXpForLevel(Math.max(1, Math.min(99, goal)));
        }
    }

    private SkillPlans() {
    }

    // ---- Saved ----

    /** The plan being made in the Planner for this skill, or null. */
    static Plan plan(ConfigManager configManager, Skill skill) {
        return decode(configManager.getRSProfileConfiguration(GROUP, PLAN_KEY + skill.name()));
    }

    static void savePlan(ConfigManager configManager, Skill skill, Plan plan) {
        if (plan == null) {
            configManager.unsetRSProfileConfiguration(GROUP, PLAN_KEY + skill.name());
        } else {
            configManager.setRSProfileConfiguration(GROUP, PLAN_KEY + skill.name(), encode(plan));
        }
    }

    // The tracker reads its plan each frame (the overlays): decoded once per saved text
    private static final Map<String, Plan> DECODED = new ConcurrentHashMap<>();

    /** The plan exported to the tracker for this skill (don't change it: it's shared), or null. */
    static Plan tracked(ConfigManager configManager, Skill skill) {
        String text = configManager.getRSProfileConfiguration(GROUP, TRACKED_KEY + skill.name());
        if (text == null || text.isEmpty()) {
            return null;
        }
        if (DECODED.size() > 64) {
            DECODED.clear();
        }
        Plan plan = DECODED.computeIfAbsent(text, t -> {
            Plan decoded = decode(t);
            return decoded != null ? decoded : new Plan(0);
        });
        return plan.steps.isEmpty() ? null : plan;
    }

    /**
     * Whether a plan is exported to the tracker for this skill with the same goal and milestones (methods and levels;
     * the XP each, which follows your bonuses, doesn't count).
     */
    static boolean isTracked(ConfigManager configManager, Skill skill, Plan plan) {
        Plan tracked = plan == null ? null : decode(configManager.getRSProfileConfiguration(GROUP, TRACKED_KEY + skill.name()));
        return tracked != null && shape(tracked).equals(shape(plan));
    }

    // The goal and each milestone's levels and action
    private static String shape(Plan plan) {
        StringBuilder out = new StringBuilder().append(plan.goal);
        for (Step step : plan.steps) {
            out.append('|').append(step.from).append('-').append(step.to).append(':').append(step.action);
        }
        return out.toString();
    }

    /**
     * Exports the plan to the tracker: its final goal becomes the skill's goal (as Set as goal does, keeping where
     * a goal underway started) and its milestones replace any exported before. A method picked on the tracker card
     * is cleared (the plan says what to train); a Track for goal pick stays and still comes first.
     */
    static void export(ConfigManager configManager, Skill skill, Plan plan, int xpNow) {
        SkillGoals.setGoal(configManager, skill, plan.goalXp(), xpNow);
        configManager.setRSProfileConfiguration(GROUP, TRACKED_KEY + skill.name(), encode(plan));
        configManager.unsetRSProfileConfiguration(GROUP, "goalMethod." + skill.name());
    }

    /** Removes the exported plan (the goal stays). */
    static void untrack(ConfigManager configManager, Skill skill) {
        configManager.unsetRSProfileConfiguration(GROUP, TRACKED_KEY + skill.name());
    }

    // ---- The text ----

    static String encode(Plan plan) {
        StringBuilder out = new StringBuilder("goal\t").append(plan.goal).append('\n');
        for (Step step : plan.steps) {
            out.append(step.from).append('\t').append(step.to).append('\t').append(clean(step.action)).append('\t')
                .append(step.method == null ? "" : clean(step.method)).append('\t').append(step.xp).append('\n');
        }
        return out.toString();
    }

    /** The plan in this text, or null when there's none (damaged lines are skipped). */
    static Plan decode(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        Plan plan = new Plan(0);
        for (String line : text.split("\n")) {
            String[] parts = line.split("\t", -1);
            try {
                if (parts.length >= 2 && parts[0].equals("goal")) {
                    plan.goal = Integer.parseInt(parts[1].trim());
                } else if (parts.length >= 3 && !parts[2].isEmpty()) {
                    int from = Integer.parseInt(parts[0].trim());
                    int to = Integer.parseInt(parts[1].trim());
                    String method = parts.length > 3 && !parts[3].isEmpty() ? parts[3] : null;
                    double xp = parts.length > 4 && !parts[4].isEmpty() ? Double.parseDouble(parts[4]) : 0;
                    if (from >= 1 && to > from) {
                        plan.steps.add(new Step(parts[2], from, to, method, xp));
                    }
                }
            } catch (NumberFormatException e) {
                // a damaged line: skip it
            }
        }
        if (plan.goal <= 0) {
            plan.goal = plan.end();
        }
        return plan.goal <= 0 && plan.steps.isEmpty() ? null : plan;
    }

    private static String clean(String text) {
        return text.replace('\t', ' ').replace('\n', ' ');
    }

    // ---- Matching a Calculator action to a training guide method ----

    // Words that don't tell methods apart ("Teak trees" and "Teak logs" are the same thing)
    private static final Set<String> GENERIC = new HashSet<>(Arrays.asList("logs", "log", "tree", "trees", "ore",
        "ores", "rocks", "rock", "raw", "course", "courses", "agility", "rooftop", "the", "of", "a", "on", "and", "in",
        "training", "fishing", "cooking", "making", "burning", "creating"));

    // The words of a name, singular, without generic ones
    private static Set<String> words(String name) {
        Set<String> out = new HashSet<>();
        for (String word : name.toLowerCase(Locale.ROOT).split("[^a-z0-9']+")) {
            String w = word.endsWith("s") && word.length() > 3 ? word.substring(0, word.length() - 1) : word;
            if (!w.isEmpty() && !GENERIC.contains(word) && !GENERIC.contains(w)) {
                out.add(w);
            }
        }
        return out;
    }

    /**
     * Whether a training guide's method is this action: one of its listed targets or table rows has its name (a
     * fish's with or without "Raw"), or every telling word of the method's name is in the action's ("Teak trees" for
     * Teak logs, "Hallowed Sepulchre" for its floors).
     */
    static boolean matches(TrainingData.Method method, String action) {
        String bare = action.regionMatches(true, 0, "Raw ", 0, 4) ? action.substring(4) : action;
        for (String target : method.targets) {
            if (target.equalsIgnoreCase(action) || target.equalsIgnoreCase(bare)) {
                return true;
            }
        }
        for (TrainingData.Option option : method.options) {
            if (option.name.equalsIgnoreCase(action)) {
                return true;
            }
        }
        Set<String> need = words(method.name);
        return !need.isEmpty() && words(action).containsAll(need);
    }

    /** The guide method for this action somewhere from level {@code from} to {@code to}, or null. */
    static TrainingData.Method methodFor(TrainingData training, Skill skill, String action, int from, int to) {
        for (int level = Math.max(1, from); level <= Math.max(from, Math.min(99, to - 1)); level++) {
            for (TrainingData.Method method : training.available(skill, level, m -> true)) {
                if (matches(method, action)) {
                    return method;
                }
            }
        }
        return null;
    }

    /** The step's guide method: the one saved with it, else one matching its action by name; null if none. */
    static TrainingData.Method method(TrainingData training, Skill skill, Step step) {
        TrainingData.Method named = step.method == null ? null : training.named(skill, step.method);
        return named != null ? named : methodFor(training, skill, step.action, step.from, step.to);
    }
}
