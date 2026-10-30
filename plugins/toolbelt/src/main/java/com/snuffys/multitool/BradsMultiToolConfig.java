package com.snuffys.multitool;

import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

/**
 * The all-in-one Snuffy's Toolbelt: every plugin's settings in one group ("bradsmultitool", kept so saved
 * settings and goals carry over). RuneLite only shows sections an interface declares itself, so they are all
 * repeated here (MultiToolConfigTest checks none is missing).
 */
@ConfigGroup("bradsmultitool")
public interface BradsMultiToolConfig extends SkillTrackerConfig, PvmConfig, GemCrabConfig {

    @ConfigSection(
        name = "General",
        description = "Voice and chat settings shared by all alerts",
        position = 0
    )
    String generalSection = "generalSection";

    @ConfigSection(
        name = "Potion Buffs",
        description = "Buff timers on the HUD and running-out / expired alerts",
        position = 6,
        closedByDefault = true
    )
    String buffSection = "buffSection";

    @ConfigSection(
        name = "Health",
        description = "Low health warnings",
        position = 13,
        closedByDefault = true
    )
    String healthSection = "healthSection";

    @ConfigSection(
        name = "Prayer",
        description = "Low and empty prayer warnings",
        position = 21,
        closedByDefault = true
    )
    String prayerSection = "prayerSection";

    @ConfigSection(
        name = "Combat Alerts",
        description = "Special attack, antifire, poison, stamina, ammo, runes and cannon alerts",
        position = 29,
        closedByDefault = true
    )
    String combatAlertsSection = "combatAlertsSection";

    @ConfigSection(
        name = "Idle",
        description = "Idle character and idle logout warnings",
        position = 30,
        closedByDefault = true
    )
    String idleSection = "idleSection";

    @ConfigSection(
        name = "PvM HUD",
        description = "Combat HUD and monster hover info for non-crab monsters",
        position = 39,
        closedByDefault = true
    )
    String pvmSection = "pvmSection";

    @ConfigSection(
        name = "Slayer",
        description = "Task tracker, required items, superior and task-complete alerts",
        position = 48,
        closedByDefault = true
    )
    String slayerSection = "slayerSection";

    @ConfigSection(
        name = "Gemstone Crab",
        description = "Crab HUD, radar, alerts and outlines",
        position = 49,
        closedByDefault = true
    )
    String crabSection = "crabSection";

    @ConfigSection(
        name = "Skilling",
        description = "Hover cards and alerts shared by every skill",
        position = 59,
        closedByDefault = true
    )
    String skillingSection = "skillingSection";

    @ConfigSection(
        name = "Woodcutting",
        description = "Tree timers and alerts",
        position = 60,
        closedByDefault = true
    )
    String woodcuttingSection = "woodcuttingSection";

    @ConfigSection(
        name = "Mining",
        description = "Mining timers and alerts",
        position = 61,
        closedByDefault = true
    )
    String miningSection = "miningSection";

    @ConfigSection(
        name = "Fishing",
        description = "Fishing spot timers and alerts",
        position = 62,
        closedByDefault = true
    )
    String fishingSection = "fishingSection";

    @ConfigSection(
        name = "Thieving",
        description = "Thieving alerts",
        position = 63,
        closedByDefault = true
    )
    String thievingSection = "thievingSection";

    @ConfigSection(
        name = "Farming",
        description = "Farming patch info",
        position = 64,
        closedByDefault = true
    )
    String farmingSection = "farmingSection";

    @ConfigSection(
        name = "Hunter",
        description = "Hunter creature info, trap and bird house timers",
        position = 65,
        closedByDefault = true
    )
    String hunterSection = "hunterSection";

    @ConfigSection(
        name = "Firemaking",
        description = "Fire timers",
        position = 66,
        closedByDefault = true
    )
    String firemakingSection = "firemakingSection";

    @ConfigSection(
        name = "Runecraft",
        description = "Altar info",
        position = 67,
        closedByDefault = true
    )
    String runecraftSection = "runecraftSection";

    @ConfigSection(
        name = "Agility",
        description = "Course and shortcut info",
        position = 68,
        closedByDefault = true
    )
    String agilitySection = "agilitySection";

    @ConfigSection(
        name = "Cooking",
        description = "Burn levels",
        position = 69,
        closedByDefault = true
    )
    String cookingSection = "cookingSection";

    @ConfigSection(
        name = "Sailing",
        description = "Sailing timers",
        position = 70,
        closedByDefault = true
    )
    String sailingSection = "sailingSection";

    @ConfigSection(
        name = "Smithing",
        description = "Anvil and furnace cards",
        position = 71,
        closedByDefault = true
    )
    String smithingSection = "smithingSection";

    @ConfigSection(
        name = "Skill Tracker",
        description = "Sidebar XP tracker",
        position = 80,
        closedByDefault = true
    )
    String trackerSection = "trackerSection";
}
