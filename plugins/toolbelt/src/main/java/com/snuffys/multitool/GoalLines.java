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
        /** What it counts to ("lvl 60", "goal"), or null (supplies: the name says it). */
        final String target;

        Actions(String name, String value, String tip) {
            this(name, value, tip, null);
        }

        Actions(String name, String value, String tip, String target) {
            this.name = name;
            this.value = value;
            this.tip = tip;
            this.target = target;
        }

        /** "Actions to lvl 60" (the pinned skill's full-width line). */
        String longName() {
            return target == null ? name : name + " to " + target;
        }

        /** "1,026 (60)" (the sidebar card's half-width cell). */
        String shortValue() {
            return target == null || "--".equals(value) ? value
                : value + " (" + (target.startsWith("lvl ") ? target.substring(4) : target) + ")";
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

    /** A plan milestone's bar: its name, how far through (0 to 1), the right-hand text and its tooltip. */
    static final class StepBar {
        final String label;
        final double progress;
        final String right;
        final String tip;

        StepBar(String label, double progress, String right, String tip) {
            this.label = label;
            this.progress = progress;
            this.right = right;
            this.tip = tip;
        }
    }

    /**
     * The bar for the plan milestone you're on. A supply milestone is the supplies, not levels: filled by how many you
     * have of what the other plan wants, and done when you have them all, whatever level that is. Any other runs from
     * its start level to its end level.
     */
    static StepBar stepBar(SkillTracker plugin, Skill skill, SkillPlans.Step step, int xp, int rate, boolean paused) {
        SkillPlans.SupplyState supply = plugin.getSupplyState(skill, step);
        if (supply != null && supply.wanted > 0) {
            String consumer = step.supplies.get(0).skill;
            try {
                consumer = Skill.valueOf(consumer).getName();
            } catch (IllegalArgumentException e) {
                // (as saved)
            }
            String right = supply.toGet <= 0 ? "Done!" : String.format("%,d", supply.toGet) + " to get";
            String tip = "Your plan's milestone: " + step.action + " for your " + consumer + " plan<br>You have "
                + String.format("%,d", Math.min(supply.have, supply.wanted)) + " of the " + String.format("%,d", supply.wanted)
                + " it wants (bank and inventory)<br>It ends when you have them all, whatever level that is";
            return new StepBar("Supplies", supply.progress(), right, tip);
        }
        int start = Experience.getXpForLevel(Math.max(1, step.from));
        int end = plugin.getMilestoneEndXp(skill, step);
        double progress = end > start ? (double) (xp - start) / (end - start) : 1;
        int left = Math.max(0, end - xp);
        String right = left == 0 ? "Done!" : rate > 0 && !paused ? BradsMultiToolPanel.formatDuration(left * 3600L / rate)
            : BradsMultiToolPanel.shortNumber(left) + " xp left";
        return new StepBar("Step: Lvl " + step.to, Math.max(0, Math.min(1, progress)), right,
            "Your plan's milestone: level " + step.to + " with " + step.action + " (from " + step.from + ")");
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
        // With a skill plan, the count goes to the end of the milestone you're on (the near goal)
        SkillPlans.Step milestone = plugin.getPlanMilestone(skill, level);
        boolean toGoal = (config.trackerActionsToGoal() || milestone != null) && goalXp > xp;
        int remaining = toGoal ? goalXp - xp : toNextLevel;
        String until = toGoal ? "until your goal" : "until the next level";
        // A supply milestone counts its supplies still to get (what it's for), not the actions to its level
        // (switching every few seconds with the actions to the next level, as the other counts do)
        SkillPlans.SupplyState supply = toGoal ? plugin.getSupplyState(skill, milestone) : null;
        boolean nextPhase = (System.currentTimeMillis() / 5000) % 2 == 1;
        if (supply != null && supply.toGet > 0) {
            if (!nextPhase || toNextLevel <= 0) {
                String consumer = milestone.supplies.get(0).skill;
                try {
                    consumer = Skill.valueOf(consumer).getName();
                } catch (IllegalArgumentException e) {
                    // (as saved)
                }
                return new Actions("To get", String.format("%,d", supply.toGet), "<html>" + milestone.action + " still to get for your "
                    + consumer + " plan (what's in your bank and inventory counted)<br>The Planner works this out as you go: buy or "
                    + "bank some and it goes down<br>Switches with the actions to the next level</html>");
            }
            // The other half of the time: the actions to the next level
            toGoal = false;
            remaining = toNextLevel;
            until = "until the next level";
        }
        if (toGoal && milestone != null) {
            int milestoneXp = plugin.getMilestoneEndXp(skill, milestone);
            if (milestoneXp > xp && milestoneXp < goalXp) {
                remaining = milestoneXp - xp;
                until = "until level " + milestone.to + ", your plan's milestone";
            }
        } else if (toGoal && stepEndLevel > 0) {
            int stepEndXp = Experience.getXpForLevel(stepEndLevel);
            if (stepEndXp > xp && stepEndXp < goalXp) {
                remaining = stepEndXp - xp;
                until = "until level " + stepEndLevel + ", where the suggested method changes";
            }
        }
        // Fishing: the average of your last XP drops (one spot catches different fish: trout and salmon). Anything
        // else: your usual drop, so a one-off (a pillar tagged between floor spikes) doesn't throw the count off
        boolean averaged = skill == Skill.FISHING;
        double perAction = averaged ? plugin.getAverageXpDrop(skill) : plugin.getUsualXpDrop(skill);
        String prefix = "";
        String name = "Actions";
        String tip = "Actions " + until;
        int drops = plugin.getAverageXpDropCount(skill);
        if (perAction > 0 && drops > 1 && averaged) {
            prefix = "~";
            tip += " (at " + String.format("%,.1f", perAction) + " XP each, the average of your last " + drops + " XP drops)";
        } else if (perAction > 0) {
            tip += " (at " + String.format("%,.0f", perAction) + " XP each, your usual XP drop)";
        }
        if (KILL_SKILLS.contains(skill)) {
            TrainingAdvisor.KillEstimate kill = goalXp > xp ? plugin.getTrainingAdvisor().killEstimate(skill) : null;
            perAction = kill != null ? kill.xpEach : 0;
            prefix = "~";
            name = "Kills";
            tip = kill != null ? "Kills of " + kill.monster + " " + until + " (" + BradsMultiToolPanel.shortNumber((int) kill.xpEach)
                + " " + skill.getName() + " XP each, " + (kill.seen ? "what your recent kills of it gave" : "its hitpoints with your "
                + "combat style, until you've killed it twice") + ")" : "Kills " + until + " (once you've killed it twice)";
            if (kill != null && kill.seen) {
                prefix = "";
            }
        } else if (perAction <= 0 && goalXp > xp) {
            perAction = plugin.getTrainingAdvisor().estimatedXpEach(skill, level);
            prefix = "~";
            String from = plugin.getGoalTarget(skill) != null ? "your pick (" + plugin.describeGoalTarget(skill) + ")"
                : plugin.getGoalItems().suggestion(skill) != null ? "what you'd make from your items"
                : "the suggested method";
            tip += " (estimated from " + from + " until your first XP drop)";
        }
        // What it counts to, in its name ("Actions to lvl 60", "Kills to goal"); when that isn't the next level, it
        // switches every few seconds between that and the next level, saying which
        String target = until.startsWith("until your goal") ? "goal" : until.startsWith("until level ")
            ? "lvl " + until.substring("until level ".length()).split("[ ,]")[0] : "lvl " + Math.min(Experience.MAX_REAL_LEVEL, level + 1);
        int counted = remaining;
        if (remaining != toNextLevel && toNextLevel > 0 && (System.currentTimeMillis() / 5000) % 2 == 1) {
            counted = toNextLevel;
            target = "lvl " + (level + 1);
        }
        String value = perAction > 0 && counted > 0 ? prefix + BradsMultiToolPanel.shortNumber((int) Math.ceil(counted / perAction)) : "--";
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
        return new Actions(name, value, tip, target);
    }

    /**
     * Where to do the suggested method: where you've been training it, when that's one of its places, else the best
     * of them ("Woodcutting Guild (17)"); null when there are none. (The rest are in the Planner's Where.)
     */
    static String where(SkillTracker plugin, Skill skill, TrainingAdvisor.Suggestion suggestion) {
        if (suggestion == null || suggestion.method == null || suggestion.method.places.isEmpty()) {
            return null;
        }
        List<String> places = suggestion.method.places;
        String trained = plugin == null ? null : plugin.getTrainedAt(skill);
        if (trained != null) {
            for (String place : places) {
                if (place.equals(trained) || place.startsWith(trained + " (") || place.startsWith(trained + ":")) {
                    return place;
                }
            }
        }
        return places.get(0);
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
        } else {
            names.addAll(plugin.getTrainingAdvisor().goalTools(skill, level));
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
