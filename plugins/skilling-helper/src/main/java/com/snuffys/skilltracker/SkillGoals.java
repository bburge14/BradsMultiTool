package com.snuffys.skilltracker;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;

/**
 * The skill goals and the remembered bank as they're saved in the shared group ("bradsmultitool", RuneScape profile
 * keys), read and written the same way by the Skilling Helper's tracker and the Planner's Calculator, so a goal set in
 * one shows in the other (and either plugin alone has it all). Keys: {@code goal.<SKILL>} (the goal in XP),
 * {@code goalStart.<SKILL>} (your XP when you set it, for the goal bar) and {@code goalBank} (the bank as last seen).
 */
final class SkillGoals {

    static final String GROUP = "bradsmultitool";
    static final String BANK_KEY = "goalBank";

    private SkillGoals() {
    }

    /** The skill's goal in XP, or 0 for none. */
    static int goal(ConfigManager configManager, Skill skill) {
        return number(configManager.getRSProfileConfiguration(GROUP, "goal." + skill.name()));
    }

    /** Your XP in the skill when its goal was set (for the goal bar), or 0 if unknown. */
    static int goalStart(ConfigManager configManager, Skill skill) {
        return number(configManager.getRSProfileConfiguration(GROUP, "goalStart." + skill.name()));
    }

    /**
     * Sets the skill's goal (0 clears it, with its start, Track for goal pick and exported plan). Changing a goal that's still
     * underway keeps where it started (the goal bar's progress); otherwise it starts from {@code xpNow}.
     */
    static void setGoal(ConfigManager configManager, Skill skill, int goalXp, int xpNow) {
        if (goalXp <= 0) {
            configManager.unsetRSProfileConfiguration(GROUP, "goal." + skill.name());
            configManager.unsetRSProfileConfiguration(GROUP, "goalStart." + skill.name());
            configManager.unsetRSProfileConfiguration(GROUP, "goalTarget." + skill.name());
            // and the plan exported to the tracker (SkillPlans), whose final goal it was
            configManager.unsetRSProfileConfiguration(GROUP, "goalPlan." + skill.name());
            return;
        }
        boolean underway = goal(configManager, skill) > xpNow && goalStart(configManager, skill) > 0;
        configManager.setRSProfileConfiguration(GROUP, "goal." + skill.name(), goalXp);
        if (!underway) {
            configManager.setRSProfileConfiguration(GROUP, "goalStart." + skill.name(), xpNow);
        }
    }

    /** The bank as last seen (lower-case item name -> how many), empty if it hasn't been opened. */
    static Map<String, Integer> savedBank(ConfigManager configManager) {
        return decodeBank(configManager.getRSProfileConfiguration(GROUP, BANK_KEY));
    }

    static void saveBank(ConfigManager configManager, Map<String, Integer> bank) {
        configManager.setRSProfileConfiguration(GROUP, BANK_KEY, encodeBank(bank));
    }

    /** "logs\t250\nraw lobster\t1200": each item (lower-case name) and how many. */
    static String encodeBank(Map<String, Integer> items) {
        StringBuilder out = new StringBuilder();
        items.forEach((name, count) -> out.append(name.replace('\t', ' ').replace('\n', ' ')).append('\t').append(count).append('\n'));
        return out.toString();
    }

    static Map<String, Integer> decodeBank(String text) {
        Map<String, Integer> out = new HashMap<>();
        if (text == null) {
            return out;
        }
        for (String line : text.split("\n")) {
            int tab = line.lastIndexOf('\t');
            if (tab > 0) {
                try {
                    out.put(line.substring(0, tab), Integer.parseInt(line.substring(tab + 1).trim()));
                } catch (NumberFormatException e) {
                    // a damaged line: skip it
                }
            }
        }
        return out;
    }

    private static int number(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
