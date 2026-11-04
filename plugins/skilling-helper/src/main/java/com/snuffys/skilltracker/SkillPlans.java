package com.snuffys.skilltracker;

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
 * {@code plans.<SKILL>} (several, each with a name; {@code planOpen.<SKILL>} says which is open, and an older
 * single {@code plan.<SKILL>} becomes the first); Export to tracker copies one to {@code goalPlan.<SKILL>} (and sets the goal), which is what
 * the Skilling Helper reads, so neither plugin needs the other's classes. Saved as plain text, one line each:
 * {@code goal\t99}, then {@code from\tto\taction\tmethod\txp} for each milestone (the action as the Calculator names
 * it, the training guide's method it matched or empty, the XP of one action with your bonuses).
 */
final class SkillPlans {

    static final String GROUP = SkillGoals.GROUP;
    /** The single plan older versions kept (read once, as the first of the list). */
    static final String PLAN_KEY = "plan.";
    /** The skill's saved plans. */
    static final String PLANS_KEY = "plans.";
    /** Which of them is open (its index). */
    static final String OPEN_KEY = "planOpen.";
    /** The plan exported to the Skilling Helper's tracker. */
    static final String TRACKED_KEY = "goalPlan.";

    /** Some of a milestone's products set aside for another skill's plan (yew logs for Firemaking): not sold. */
    static final class Supply {
        final String skill;
        final String item;
        long quantity;

        Supply(String skill, String item, long quantity) {
            this.skill = skill;
            this.item = item;
            this.quantity = quantity;
        }
    }

    /** One milestone: train {@link #action} from level {@link #from} until level {@link #to}. */
    static final class Step {
        String action;
        int from;
        int to;
        /** The training guide's method it matches (TrainingData's name), or null. */
        String method;
        /** XP for one action (with the Calculator's bonuses when exported), 0 if unknown. */
        double xp;
        /** What of its products is kept for other skills' plans (none: all of it's sold). */
        final List<Supply> supplies = new ArrayList<>();
        /** Why it's there, when it was added for something ("to reach 60 for the Firemaking supply"), or null. */
        String note;
        /**
         * The pace its time is worked out at ({@link PlannerRates}: "Normal", "1-tick", "Wiki guide"...), or null for
         * the wiki guide's rate where there is one, else the normal pace.
         */
        String pace;

        Step(String action, int from, int to, String method, double xp) {
            this.action = action;
            this.from = from;
            this.to = to;
            this.method = method;
            this.xp = xp;
        }

        Step copy() {
            Step copy = new Step(action, from, to, method, xp);
            copy.note = note;
            copy.pace = pace;
            for (Supply supply : supplies) {
                copy.supplies.add(new Supply(supply.skill, supply.item, supply.quantity));
            }
            return copy;
        }

        /** How many of its products are kept for other plans, in all. */
        long supplied() {
            long total = 0;
            for (Supply supply : supplies) {
                total += supply.quantity;
            }
            return total;
        }

        /** Whether you train it at this level. */
        boolean covers(int level) {
            return level >= from && level < to;
        }
    }

    /** A final goal level and the milestones on the way, in order. */
    static final class Plan {
        int goal;
        /** What you called it ("Darts to 99"), or null for an unnamed one. */
        String name;
        final List<Step> steps = new ArrayList<>();
        /** Made from the Skilling Helper's goal: that goal's XP (0: not made from it) and the method it was set to then
         *  (null: its suggestions), to tell when the goal changes after. */
        int fromGoal;
        String fromPick;
        /**
         * The plan you sent to the tracker (Export to tracker): your edits to it are a draft until you send them
         * again. Copies don't carry it (a duplicate is a new plan).
         */
        boolean sent;

        Plan(int goal) {
            this.goal = goal;
        }

        Plan copy() {
            Plan out = new Plan(goal);
            out.name = name;
            out.fromGoal = fromGoal;
            out.fromPick = fromPick;
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

    /** The skill's saved plans, in the order made (an older single plan as the first). */
    static List<Plan> plans(ConfigManager configManager, Skill skill) {
        String text = configManager.getRSProfileConfiguration(GROUP, PLANS_KEY + skill.name());
        if (text == null) {
            List<Plan> out = new ArrayList<>();
            Plan old = decode(configManager.getRSProfileConfiguration(GROUP, PLAN_KEY + skill.name()));
            if (old != null) {
                out.add(old);
            }
            return out;
        }
        return decodeAll(text);
    }

    /** Which plan is open (an index into {@link #plans}, kept in range; -1 when there are none). */
    static int open(ConfigManager configManager, Skill skill, int count) {
        if (count <= 0) {
            return -1;
        }
        try {
            String saved = configManager.getRSProfileConfiguration(GROUP, OPEN_KEY + skill.name());
            int index = saved == null ? 0 : Integer.parseInt(saved);
            return Math.max(0, Math.min(count - 1, index));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Saves the skill's plans and which is open (the older single plan's key goes: it's in the list now). */
    static void savePlans(ConfigManager configManager, Skill skill, List<Plan> plans, int open) {
        if (plans.isEmpty()) {
            configManager.unsetRSProfileConfiguration(GROUP, PLANS_KEY + skill.name());
            configManager.unsetRSProfileConfiguration(GROUP, OPEN_KEY + skill.name());
        } else {
            configManager.setRSProfileConfiguration(GROUP, PLANS_KEY + skill.name(), encodeAll(plans));
            configManager.setRSProfileConfiguration(GROUP, OPEN_KEY + skill.name(), Math.max(0, Math.min(plans.size() - 1, open)));
        }
        configManager.unsetRSProfileConfiguration(GROUP, PLAN_KEY + skill.name());
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
        if (plan.name != null && !plan.name.trim().isEmpty()) {
            out.append("name\t").append(clean(plan.name.trim())).append('\n');
        }
        if (plan.sent) {
            out.append("sent\t1\n");
        }
        if (plan.fromGoal > 0) {
            out.append("from\t").append(plan.fromGoal).append('\t').append(plan.fromPick == null ? "" : clean(plan.fromPick)).append('\n');
        }
        for (Step step : plan.steps) {
            out.append(step.from).append('\t').append(step.to).append('\t').append(clean(step.action)).append('\t')
                .append(step.method == null ? "" : clean(step.method)).append('\t').append(step.xp);
            // (its supplies for other plans, when it has any: "FIREMAKING|Yew logs|30000;...", then its note)
            if (!step.supplies.isEmpty() || step.note != null || step.pace != null) {
                out.append('\t');
                for (Supply supply : step.supplies) {
                    out.append(supply.skill).append('|').append(clean(supply.item).replace("|", " ").replace(";", " "))
                        .append('|').append(supply.quantity).append(';');
                }
                if (step.note != null || step.pace != null) {
                    out.append('\t').append(step.note == null ? "" : clean(step.note));
                }
                if (step.pace != null) {
                    out.append('\t').append(clean(step.pace));
                }
            }
            out.append('\n');
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
                } else if (parts.length >= 2 && parts[0].equals("name")) {
                    plan.name = parts[1].trim().isEmpty() ? null : parts[1].trim();
                } else if (parts.length >= 2 && parts[0].equals("sent")) {
                    plan.sent = "1".equals(parts[1].trim());
                } else if (parts.length >= 2 && parts[0].equals("from")) {
                    plan.fromGoal = Integer.parseInt(parts[1].trim());
                    plan.fromPick = parts.length > 2 && !parts[2].trim().isEmpty() ? parts[2].trim() : null;
                } else if (parts.length >= 3 && !parts[2].isEmpty()) {
                    int from = Integer.parseInt(parts[0].trim());
                    int to = Integer.parseInt(parts[1].trim());
                    String method = parts.length > 3 && !parts[3].isEmpty() ? parts[3] : null;
                    double xp = parts.length > 4 && !parts[4].isEmpty() ? Double.parseDouble(parts[4]) : 0;
                    if (from >= 1 && to > from) {
                        Step step = new Step(parts[2], from, to, method, xp);
                        for (String supply : parts.length > 5 ? parts[5].split(";") : new String[0]) {
                            String[] bits = supply.split("\\|");
                            if (bits.length == 3) {
                                step.supplies.add(new Supply(bits[0], bits[1], Long.parseLong(bits[2].trim())));
                            }
                        }
                        if (parts.length > 6 && !parts[6].trim().isEmpty()) {
                            step.note = parts[6].trim();
                        }
                        if (parts.length > 7 && !parts[7].trim().isEmpty()) {
                            step.pace = parts[7].trim();
                        }
                        plan.steps.add(step);
                    }
                }
            } catch (NumberFormatException e) {
                // a damaged line: skip it
            }
        }
        if (plan.goal <= 0) {
            plan.goal = plan.end();
        }
        return plan.goal <= 0 && plan.steps.isEmpty() && plan.name == null ? null : plan;
    }

    // Several plans in one text: each plan's lines, then a line "=" (no plan's line is ever just that)
    static String encodeAll(List<Plan> plans) {
        StringBuilder out = new StringBuilder();
        for (Plan plan : plans) {
            out.append(encode(plan)).append("=\n");
        }
        return out.toString();
    }

    static List<Plan> decodeAll(String text) {
        List<Plan> out = new ArrayList<>();
        StringBuilder one = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (line.equals("=")) {
                Plan plan = decode(one.toString());
                // (a new plan with no milestones yet still counts)
                out.add(plan != null ? plan : new Plan(0));
                one.setLength(0);
            } else {
                one.append(line).append('\n');
            }
        }
        out.removeIf(plan -> plan.goal <= 0 && plan.steps.isEmpty() && plan.name == null);
        return out;
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
