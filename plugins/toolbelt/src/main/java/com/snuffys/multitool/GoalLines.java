package com.snuffys.multitool;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Experience;
import net.runelite.api.Skill;

/**
 * The parts of a skill's goal shown both on its tracker card and on the pinned overlay: how many actions
 * (or kills) are left, and what to make or use from your items.
 */
final class GoalLines {

    /** Skills whose actions are counted as kills (an XP drop there is a hit, not an action). */
    static final Set<Skill> KILL_SKILLS = EnumSet.of(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE,
        Skill.HITPOINTS, Skill.RANGED);

    /** "Actions" / "Kills", the count ("~1.2K", "--") and what it counts to (for a tooltip). */
    static final class Actions {
        final String name;
        final String value;
        final String tip;

        Actions(String name, String value, String tip) {
            this.name = name;
            this.value = value;
            this.tip = tip;
        }
    }

    /** "Cook:" "Raw karambwan" "(you have 1,240)". */
    static final class ItemLine {
        final String label;
        final String item;
        final String have;

        ItemLine(String label, String item, String have) {
            this.label = label;
            this.item = item;
            this.have = have;
        }
    }

    private GoalLines() {
    }

    /**
     * Actions left until the next level, or (with Actions Until Goal) until the goal or the end of the plan's
     * step you're on ({@code stepEndLevel}, 0 if the method doesn't change before the goal). Per action: your
     * last XP drop, else an estimate from the goal's method; combat skills count kills of the monster.
     */
    static Actions actions(SkillTracker plugin, SkillTrackerConfig config, Skill skill, int xp, int stepEndLevel) {
        int level = Experience.getLevelForXp(xp);
        int toNextLevel = level >= Experience.MAX_REAL_LEVEL ? 0 : Experience.getXpForLevel(level + 1) - xp;
        int goalXp = plugin.getSkillGoal(skill);
        boolean toGoal = config.trackerActionsToGoal() && goalXp > xp;
        int remaining = toGoal ? goalXp - xp : toNextLevel;
        String until = toGoal ? "until your goal" : "until the next level";
        if (toGoal && stepEndLevel > 0) {
            int stepEndXp = Experience.getXpForLevel(stepEndLevel);
            if (stepEndXp > xp && stepEndXp < goalXp) {
                remaining = stepEndXp - xp;
                until = "until level " + stepEndLevel + ", where the suggested method changes";
            }
        }
        // The average of your last XP drops: one method can give different XP each time (trout and salmon)
        double perAction = plugin.getAverageXpDrop(skill);
        String prefix = "";
        String name = "Actions";
        String tip = "Actions " + until;
        int drops = plugin.getAverageXpDropCount(skill);
        if (perAction > 0 && drops > 1) {
            prefix = "~";
            tip += " (at " + String.format("%,.1f", perAction) + " XP each, the average of your last " + drops + " XP drops)";
        }
        if (KILL_SKILLS.contains(skill)) {
            TrainingAdvisor.KillEstimate kill = goalXp > xp ? plugin.getTrainingAdvisor().killEstimate(skill) : null;
            perAction = kill != null ? kill.xpEach : 0;
            prefix = "~";
            name = "Kills";
            tip = kill != null ? "Kills of " + kill.monster + " " + until + " (" + BradsMultiToolPanel.shortNumber((int) kill.xpEach)
                + " " + skill.getName() + " XP each with your combat style)" : "Kills " + until;
        } else if (perAction <= 0 && goalXp > xp) {
            perAction = plugin.getTrainingAdvisor().estimatedXpEach(skill, level);
            prefix = "~";
            tip += " (estimated from the suggested method until your first XP drop)";
        }
        String value = perAction > 0 && remaining > 0 ? prefix + BradsMultiToolPanel.shortNumber((int) Math.ceil(remaining / perAction)) : "--";
        // The hover names what the count is to, and with a goal gives the other count too
        if (perAction > 0 && goalXp > xp && toNextLevel > 0) {
            String noun = name.toLowerCase();
            String nextCount = prefix + String.format("%,d", (int) Math.ceil(toNextLevel / perAction));
            String goalCount = prefix + String.format("%,d", (int) Math.ceil((goalXp - xp) / perAction));
            String shown = toGoal ? until : "until the next level";
            tip = "<html>" + tip + "<br>Next level: " + nextCount + " " + noun + (toGoal ? "" : " (shown)")
                + "<br>Your goal: " + goalCount + " " + noun + (toGoal && remaining == goalXp - xp ? " (shown)" : "")
                + (toGoal && remaining != goalXp - xp ? "<br>Shown: " + shown : "") + "</html>";
        }
        return new Actions(name, value, tip);
    }

    /** Where to do the suggested method: "Prifddinas (+4 more)", or null when the guide doesn't say. */
    static String where(TrainingAdvisor.Suggestion suggestion) {
        if (suggestion == null || suggestion.method == null || suggestion.method.places.isEmpty()) {
            return null;
        }
        List<String> places = suggestion.method.places;
        return places.get(0) + (places.size() > 1 ? " (+" + (places.size() - 1) + " more)" : "");
    }

    /**
     * For production skills and Prayer: the best thing to make or use from your inventory and bank, or null.
     * Cooking, Firemaking and Prayer name the item you use ("Cook: Raw karambwan", "Burn: Yew logs", "Offer: Dragon bones").
     */
    static ItemLine itemLine(SkillTracker plugin, Skill skill) {
        RecipeData.Recipe make = plugin.getGoalItems().suggestion(skill);
        if (make == null) {
            return null;
        }
        int count = plugin.getGoalItems().count(skill);
        String have = "(you have " + (count > 0 ? String.format("%,d", count) : "none") + ")";
        String material = make.materials.size() == 1 ? make.materials.keySet().iterator().next() : null;
        if (material != null) {
            switch (skill) {
                case COOKING:
                    return new ItemLine("Cook:", material, have);
                case FIREMAKING:
                    return new ItemLine("Burn:", material, have);
                case PRAYER:
                    // By how you train it: "Bury:", "Offer: Dragon bones (Gilded altar)", "Scatter:" for ashes
                    PrayerMethod method = plugin.getGoalItems().prayerMethod(make);
                    if (method == null) {
                        return new ItemLine(make.name.startsWith("Scatter ") ? "Scatter:" : "Use:", material, have);
                    }
                    return new ItemLine(method.verb, method == PrayerMethod.BURY ? material : material + " (" + method.label + ")", have);
                default:
                    break;
            }
        }
        return new ItemLine("Make:", make.name, have);
    }

    /**
     * Tools the goal's method needs, each with whether you have it (carried, worn or banked): the item line's
     * recipe ("Knife" to fletch), else the suggestion ("Axe" for trees, "Tinderbox" to burn logs). Empty if none.
     */
    static Map<String, Boolean> tools(SkillTracker plugin, Skill skill, int level) {
        List<String> names = new ArrayList<>();
        if (plugin.getGoalItems().suggestion(skill) != null) {
            names.addAll(plugin.getGoalItems().tools(skill));
        } else if (plugin.getGoalTarget(skill) == null) {
            TrainingAdvisor.Suggestion suggestion = plugin.getTrainingAdvisor().suggest(skill, level);
            if (suggestion != null) {
                names.addAll(suggestion.tools);
            }
        }
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (String name : names) {
            out.put(name, plugin.getGoalItems().owns(name));
        }
        return out;
    }

    /**
     * For a combat goal: how fast you're killing your Track for goal pick (else what you killed last), e.g.
     * "Sand Crab: 52/hr (1:09 each)"; null until you've killed it twice in the last hour.
     */
    static String killRate(SkillTracker plugin, Skill skill) {
        if (!KILL_SKILLS.contains(skill) || plugin.getSkillGoal(skill) <= plugin.getCurrentSkillXp(skill)) {
            return null;
        }
        KillTracker kills = plugin.getKillTracker();
        String target = plugin.getGoalTarget(skill);
        String monster = target != null && target.startsWith("npc:") ? SkillTracker.goalTargetName(target) : null;
        KillTracker.Pace pace = kills.pace(monster);
        if (pace == null) {
            monster = kills.lastKilled();
            pace = kills.pace(monster);
        }
        if (pace == null) {
            return null;
        }
        long seconds = Math.round(pace.millisEach / 1000.0);
        return monster + ": " + pace.perHour + "/hr (" + seconds / 60 + ":" + String.format("%02d", seconds % 60) + " each)";
    }
}
