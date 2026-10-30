package com.snuffys.skilltracker;

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

/** Snuffy's Skilling Helper: the tracker, goals and suggestions, and the skilling helpers. */
@ConfigGroup("snuffysskillinghelper")
public interface SkillTrackerConfig extends CommonConfig, TickConfig {

    // Repeated from CommonConfig: RuneLite only shows sections an interface declares itself


    @ConfigSection(
        name = "Tick Counter",
        description = "Counts game ticks over your head or in a box you can move, with an optional metronome",
        position = 85,
        closedByDefault = true
    )
    String tickSection = "tickSection";

    @ConfigSection(
        name = "General",
        description = "Voice and chat settings shared by all alerts",
        position = 0
    )
    String generalSection = "generalSection";

    @ConfigSection(
        name = "Idle",
        description = "Idle character and idle logout warnings",
        position = 30,
        closedByDefault = true
    )
    String idleSection = "idleSection";

    enum TrainingFocus {
        FASTEST("Fastest"),
        OTHER("AFK / Other"),
        CHEAPEST("Cheapest"),
        MONEY("Money Maker");

        private final String name;
        TrainingFocus(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    enum BestChoice {
        XP("Most XP"),
        PROFIT("Most Profit");

        private final String name;
        BestChoice(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    @ConfigSection(
        name = "Skilling",
        description = "Hover cards and alerts shared by every skill",
        position = 59,
        closedByDefault = true
    )
    String skillingSection = "skillingSection";

    @ConfigItem(
        keyName = "showSkillingHover",
        name = "Skilling Hover Info",
        description = "When hovering a tree, rock, fishing spot, stall or pickpocket target, show level, XP, tools and more",
        section = skillingSection,
        position = 1
    )
    default boolean showSkillingHover() {
        return true;
    }

    @ConfigItem(
        keyName = "showSkillingRates",
        name = "XP Rates on Hover",
        description = "Show the wiki's XP/hr and your own rate on the hover card while you're training that skill",
        section = skillingSection,
        position = 2
    )
    default boolean showSkillingRates() {
        return true;
    }

    @ConfigItem(
        keyName = "deferToOtherTimerPlugins",
        name = "Use Other Timer Plugins",
        description = "When you have another plugin on that draws the same timer (Tree Despawn Timer, MLM Despawn Timer, Calcified Rock Despawn Timer, Daeyalt Mining Timer, Fishing Spot Timer/Tracker, Barbarian or Aerial Fishing Timers, Star AFK Timer, Salvaging AFK Timer, Fire Timer, or RuneLite's Hunter plugin for traps), hide our pie for it so they don't stack",
        section = skillingSection,
        position = 3
    )
    default boolean deferToOtherTimerPlugins() {
        return true;
    }

    @ConfigItem(
        keyName = "piesAlwaysShown",
        name = "Pies When Not Training",
        description = "Show timer pies all the time. Off: a resource's pies only show while you train its skill (trees while you chop, rocks while you mine, fires while you cook or burn logs...)",
        section = skillingSection,
        position = 3
    )
    default boolean piesAlwaysShown() {
        return false;
    }

    @ConfigItem(
        keyName = "bestChoice",
        name = "Best Choice By",
        description = "How cards pick the \"best\" thing to make from what you carry (e.g. the anvil card): Most XP, or Most Profit at Grand Exchange prices",
        section = skillingSection,
        position = 4
    )
    default BestChoice bestChoice() {
        return BestChoice.XP;
    }

    @ConfigItem(
        keyName = "skillingWhenNotRetaliating",
        name = "Skill Cards With Auto Retaliate Off",
        description = "With auto retaliate off, hovering an NPC you can also skill on (e.g. pickpocket) shows the skilling card instead of the monster card, even when Attack is the left-click option",
        section = skillingSection,
        position = 5
    )
    default boolean skillingWhenNotRetaliating() {
        return true;
    }

    @ConfigItem(
        keyName = "inventoryFullAlert",
        name = "Inventory Full",
        description = "Alert when your inventory fills up while skilling",
        section = skillingSection,
        position = 6
    )
    default boolean inventoryFullAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceInventoryFull",
        name = "Voice: Inventory Full",
        description = "When your inventory fills while skilling (turning it on plays a preview)",
        section = skillingSection,
        position = 7
    )
    default boolean voiceInventoryFull() {
        return true;
    }

    @ConfigItem(
        keyName = "chatInventoryFull",
        name = "Chat: Inventory Full",
        description = "Chat message when your inventory fills while skilling",
        section = skillingSection,
        position = 8
    )
    default boolean chatInventoryFull() {
        return true;
    }

    @ConfigItem(
        keyName = "resourceGoneAlert",
        name = "Resource Gone",
        description = "Alert when your tree falls, rock depletes or fishing spot moves",
        section = skillingSection,
        position = 9
    )
    default boolean resourceGoneAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "batchCompleteAlert",
        name = "Batch Complete Alert",
        description = "Alert when a run of Cooking, Smithing, Crafting, Fletching or Herblore finishes (you stop gaining XP and your character stops)",
        section = skillingSection,
        position = 10
    )
    default boolean batchCompleteAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceBatchComplete",
        name = "Voice: Batch Complete",
        description = "When your production batch finishes (turning it on plays a preview)",
        section = skillingSection,
        position = 11
    )
    default boolean voiceBatchComplete() {
        return true;
    }

    @ConfigItem(
        keyName = "chatBatchComplete",
        name = "Chat: Batch Complete",
        description = "Chat message when your production batch finishes",
        section = skillingSection,
        position = 12
    )
    default boolean chatBatchComplete() {
        return true;
    }

    @ConfigSection(
        name = "Woodcutting",
        description = "Tree timers and alerts",
        position = 60,
        closedByDefault = true
    )
    String woodcuttingSection = "woodcuttingSection";

    @ConfigItem(
        keyName = "showTreeTimer",
        name = "Tree Timer Pies",
        description = "A pie on each tree being chopped that empties as it nears falling (Forestry timer; counts every player chopping)",
        section = woodcuttingSection,
        position = 1
    )
    default boolean showTreeTimer() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceTreeGone",
        name = "Voice: Tree Fallen",
        description = "When the tree you are chopping falls (turning it on plays a preview)",
        section = woodcuttingSection,
        position = 2
    )
    default boolean voiceTreeGone() {
        return true;
    }

    @ConfigItem(
        keyName = "chatTreeGone",
        name = "Chat: Tree Fallen",
        description = "Chat message when the tree you are chopping falls",
        section = woodcuttingSection,
        position = 3
    )
    default boolean chatTreeGone() {
        return true;
    }

    @ConfigSection(
        name = "Mining",
        description = "Mining timers and alerts",
        position = 61,
        closedByDefault = true
    )
    String miningSection = "miningSection";

    @ConfigItem(
        keyName = "showMiningTimer",
        name = "Mining Timer Pies",
        description = "Pies on Motherlode Mine ore veins, calcified rocks, Daeyalt essence and shooting stars (the star's current layer) showing how long they have left",
        section = miningSection,
        position = 1
    )
    default boolean showMiningTimer() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceRockDepleted",
        name = "Voice: Rock Depleted",
        description = "When the rock you are mining is depleted (turning it on plays a preview)",
        section = miningSection,
        position = 2
    )
    default boolean voiceRockDepleted() {
        return true;
    }

    @ConfigItem(
        keyName = "chatRockDepleted",
        name = "Chat: Rock Depleted",
        description = "Chat message when the rock you are mining is depleted",
        section = miningSection,
        position = 3
    )
    default boolean chatRockDepleted() {
        return true;
    }

    @ConfigSection(
        name = "Fishing",
        description = "Fishing spot timers and alerts",
        position = 62,
        closedByDefault = true
    )
    String fishingSection = "fishingSection";

    @ConfigItem(
        keyName = "showFishingSpotTimer",
        name = "Fishing Spot Pies",
        description = "A pie on each fishing spot that's moved since you arrived, showing how soon it may move again (2.5-5 minutes)",
        section = fishingSection,
        position = 1
    )
    default boolean showFishingSpotTimer() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceFishingSpot",
        name = "Voice: Fishing Spot Moved",
        description = "When your fishing spot moves (turning it on plays a preview)",
        section = fishingSection,
        position = 2
    )
    default boolean voiceFishingSpot() {
        return true;
    }

    @ConfigItem(
        keyName = "chatFishingSpot",
        name = "Chat: Fishing Spot Moved",
        description = "Chat message when your fishing spot moves",
        section = fishingSection,
        position = 3
    )
    default boolean chatFishingSpot() {
        return true;
    }

    @ConfigSection(
        name = "Thieving",
        description = "Thieving alerts",
        position = 63,
        closedByDefault = true
    )
    String thievingSection = "thievingSection";

    @ConfigItem(
        keyName = "coinPouchAlert",
        name = "Coin Pouch Alert",
        description = "Alert when your coin pouches reach the most you can hold (28, more with the Ardougne Diary)",
        section = thievingSection,
        position = 1
    )
    default boolean coinPouchAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceCoinPouch",
        name = "Voice: Coin Pouches Full",
        description = "When your coin pouches are full (turning it on plays a preview)",
        section = thievingSection,
        position = 2
    )
    default boolean voiceCoinPouch() {
        return true;
    }

    @ConfigItem(
        keyName = "chatCoinPouch",
        name = "Chat: Coin Pouches Full",
        description = "Chat message when your coin pouches are full",
        section = thievingSection,
        position = 3
    )
    default boolean chatCoinPouch() {
        return true;
    }

    @ConfigSection(
        name = "Farming",
        description = "Farming patch info",
        position = 64,
        closedByDefault = true
    )
    String farmingSection = "farmingSection";

    @ConfigItem(
        keyName = "showFarmingHover",
        name = "Farming Patch Hover",
        description = "Hovering a farming patch shows what's growing, its stage, roughly when it's grown, compost, watering, protection and what you can plant",
        section = farmingSection,
        position = 1
    )
    default boolean showFarmingHover() {
        return true;
    }

    @ConfigSection(
        name = "Hunter",
        description = "Hunter creature info, trap and bird house timers",
        position = 65,
        closedByDefault = true
    )
    String hunterSection = "hunterSection";

    @ConfigItem(
        keyName = "showHunterHover",
        name = "Creature Hover",
        description = "Hovering a Hunter creature shows its level, XP and the trap it needs",
        section = hunterSection,
        position = 1
    )
    default boolean showHunterHover() {
        return true;
    }

    @ConfigItem(
        keyName = "showTrapTimer",
        name = "Trap Timer Pies",
        description = "A pie on each trap you lay, showing how long until it collapses (green once something is caught)",
        section = hunterSection,
        position = 2
    )
    default boolean showTrapTimer() {
        return true;
    }

    @ConfigItem(
        keyName = "showBirdhouseHover",
        name = "Bird House Hover",
        description = "Hovering a bird house shows its type and when it is ready",
        section = hunterSection,
        position = 3
    )
    default boolean showBirdhouseHover() {
        return true;
    }

    @ConfigItem(
        keyName = "birdhouseAlert",
        name = "Bird Houses Ready Alert",
        description = "Alert when the bird houses you seeded are full (50 minutes)",
        section = hunterSection,
        position = 4
    )
    default boolean birdhouseAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceBirdhouses",
        name = "Voice: Bird Houses Ready",
        description = "When your bird houses are ready (turning it on plays a preview)",
        section = hunterSection,
        position = 5
    )
    default boolean voiceBirdhouses() {
        return true;
    }

    @ConfigItem(
        keyName = "chatBirdhouses",
        name = "Chat: Bird Houses Ready",
        description = "Chat message when your bird houses are ready",
        section = hunterSection,
        position = 6
    )
    default boolean chatBirdhouses() {
        return true;
    }

    @ConfigSection(
        name = "Firemaking",
        description = "Fire timers",
        position = 66,
        closedByDefault = true
    )
    String firemakingSection = "firemakingSection";

    @ConfigItem(
        keyName = "showFireTimer",
        name = "Fire Timer Pies",
        description = "A pie on each fire lit nearby, showing how long it may burn (1-2 minutes)",
        section = firemakingSection,
        position = 1
    )
    default boolean showFireTimer() {
        return true;
    }

    @ConfigSection(
        name = "Runecraft",
        description = "Altar info",
        position = 67,
        closedByDefault = true
    )
    String runecraftSection = "runecraftSection";

    @ConfigItem(
        keyName = "showRunecraftHover",
        name = "Altar Hover",
        description = "Hovering an altar shows the rune, level, XP and how many runes you make per essence",
        section = runecraftSection,
        position = 1
    )
    default boolean showRunecraftHover() {
        return true;
    }

    @ConfigSection(
        name = "Agility",
        description = "Course and shortcut info",
        position = 68,
        closedByDefault = true
    )
    String agilitySection = "agilitySection";

    @ConfigItem(
        keyName = "showAgilityHover",
        name = "Obstacle Hover",
        description = "Hovering a course obstacle or shortcut shows its Agility level, the course XP per lap and laps to your next level",
        section = agilitySection,
        position = 1
    )
    default boolean showAgilityHover() {
        return true;
    }

    @ConfigSection(
        name = "Cooking",
        description = "Burn levels",
        position = 69,
        closedByDefault = true
    )
    String cookingSection = "cookingSection";

    @ConfigItem(
        keyName = "showCookingHover",
        name = "Range / Fire Hover",
        description = "Hovering a range or fire shows each raw food you are carrying: its level, XP and burn level",
        section = cookingSection,
        position = 1
    )
    default boolean showCookingHover() {
        return true;
    }

    @ConfigSection(
        name = "Sailing",
        description = "Sailing timers",
        position = 70,
        closedByDefault = true
    )
    String sailingSection = "sailingSection";

    @ConfigItem(
        keyName = "showShipwreckTimer",
        name = "Shipwreck Pies",
        description = "A pie on each Sailing shipwreck you saw surface, showing how long it has left before it sinks",
        section = sailingSection,
        position = 1
    )
    default boolean showShipwreckTimer() {
        return true;
    }

    @ConfigItem(
        keyName = "showSailingHover",
        name = "Sailing Hover",
        description = "Hover cards for sea charting spots (task, level, XP), shipwrecks (level, salvage XP, best hook for you), trawling shoals (Fishing level, fish, depth, bait), sea monsters (HP, safe keel, bounty parts) and your current duck",
        section = sailingSection,
        position = 2
    )
    default boolean showSailingHover() {
        return true;
    }

    @ConfigSection(
        name = "Smithing",
        description = "Anvil and furnace cards",
        position = 71,
        closedByDefault = true
    )
    String smithingSection = "smithingSection";

    @ConfigItem(
        keyName = "showSmithingHover",
        name = "Anvil / Furnace Hover",
        description = "Hovering an anvil or furnace shows what you can make from the bars or ores you are carrying",
        section = smithingSection,
        position = 1
    )
    default boolean showSmithingHover() {
        return true;
    }

    @ConfigSection(
        name = "Skill Tracker",
        description = "Sidebar XP tracker",
        position = 80,
        closedByDefault = true
    )
    String trackerSection = "trackerSection";

    @ConfigItem(
        keyName = "showTrackerProgressBar",
        name = "Show Progress Bar",
        description = "The level progress bar at the top of each skill card",
        section = trackerSection,
        position = 60
    )
    default boolean showTrackerProgressBar() {
        return true;
    }

    @ConfigItem(
        keyName = "showTrackerXpGained",
        name = "Show XP Gained",
        description = "XP gained this session",
        section = trackerSection,
        position = 61
    )
    default boolean showTrackerXpGained() {
        return true;
    }

    @ConfigItem(
        keyName = "showTrackerXpToLevel",
        name = "Show XP to Level",
        description = "XP left until the next level",
        section = trackerSection,
        position = 62
    )
    default boolean showTrackerXpToLevel() {
        return true;
    }

    @ConfigItem(
        keyName = "showTrackerActions",
        name = "Show Actions Left",
        description = "How many more of your last action until the next level (not shown for combat skills)",
        section = trackerSection,
        position = 63
    )
    default boolean showTrackerActions() {
        return true;
    }

    @ConfigItem(
        keyName = "trackerActionsToGoal",
        name = "Actions Until Goal",
        description = "When a skill has a goal, count actions left until the goal instead of until the next level. When the suggested method changes before the goal, it counts to the end of the step you're on (hover the count to see what it counts to)",
        section = trackerSection,
        position = 64
    )
    default boolean trackerActionsToGoal() {
        return false;
    }

    @ConfigItem(
        keyName = "showTrackerXpPerHour",
        name = "Show XP/Hr",
        description = "XP per hour",
        section = trackerSection,
        position = 65
    )
    default boolean showTrackerXpPerHour() {
        return true;
    }

    @ConfigItem(
        keyName = "showXpPerMinute",
        name = "Show XP/Min",
        description = "XP per minute",
        section = trackerSection,
        position = 66
    )
    default boolean showXpPerMinute() {
        return true;
    }

    @ConfigItem(
        keyName = "showTrackerTimeToLevel",
        name = "Show Time to Level",
        description = "Estimated time until the next level at your current rate",
        section = trackerSection,
        position = 67
    )
    default boolean showTrackerTimeToLevel() {
        return true;
    }

    @ConfigItem(
        keyName = "showTrackerGoal",
        name = "Show Goal",
        description = "Your goal for the skill (right-click a skill card to set one): XP left and time to reach it",
        section = trackerSection,
        position = 68
    )
    default boolean showTrackerGoal() {
        return true;
    }

    @ConfigItem(
        keyName = "showTrainingSuggestion",
        name = "Suggested Method",
        description = "On a skill with a goal, show the wiki training guide's method for your level and what to switch to on the way",
        section = trackerSection,
        position = 69
    )
    default boolean showTrainingSuggestion() {
        return true;
    }

    @ConfigItem(
        keyName = "trainingFocus",
        name = "Training Focus",
        description = "What goal suggestions aim for, unless a skill has its own (right-click a skill in the tracker > Suggest, or pick it when setting a goal): the wiki guide's fastest XP, its other methods (often more AFK), the cheapest way (fewest GP per XP when making things; gathering is free), or the best money maker you can do. Slayer always suggests the best Slayer master",
        section = trackerSection,
        position = 70
    )
    default TrainingFocus trainingFocus() {
        return TrainingFocus.FASTEST;
    }

    @ConfigItem(
        keyName = "highlightSuggestedTraining",
        name = "Highlight Suggested Method",
        description = "Outline the trees, rocks, stalls or NPCs of the suggested method for each skill you have a goal in",
        section = trackerSection,
        position = 71
    )
    default boolean highlightSuggestedTraining() {
        return true;
    }

    @ConfigItem(
        keyName = "highlightGoalItems",
        name = "Highlight Goal Items",
        description = "For Cooking, Smithing, Fletching, Crafting, Herblore, Firemaking and other goals, outline in your inventory and bank the materials of the best thing you can make from them (hover for which goal)",
        section = trackerSection,
        position = 72
    )
    default boolean highlightGoalItems() {
        return true;
    }

    @Alpha
    @ConfigItem(
        keyName = "trainingHighlightColor",
        name = "Highlight Colour",
        description = "Outline colour for the suggested method and goal items",
        section = trackerSection,
        position = 73
    )
    default Color trainingHighlightColor() {
        return new Color(80, 200, 255, 200);
    }

    @ConfigItem(
        keyName = "autoPauseSkills",
        name = "Auto-Pause Idle Skills",
        description = "Stop a skill's XP/hr clock when you haven't gained XP in it for a while; it resumes on your next XP",
        section = trackerSection,
        position = 74
    )
    default boolean autoPauseSkills() {
        return true;
    }

    @Range(min = 10, max = 3600)
    @ConfigItem(
        keyName = "autoPauseSeconds",
        name = "Auto-Pause After",
        description = "Seconds without XP in a skill before it auto-pauses (10 seconds to an hour)",
        section = trackerSection,
        position = 75
    )
    @Units(Units.SECONDS)
    default int autoPauseSeconds() {
        return 300;
    }

    @ConfigItem(
        keyName = "ignoredSkills",
        name = "Ignored Skills",
        description = "Comma-separated list of skills to filter out",
        section = trackerSection,
        position = 76
    )
    default String ignoredSkills() {
        return "";
    }

    @ConfigItem(
        keyName = "voiceLevelUp",
        name = "Voice: Level Up",
        description = "Say the skill and your new level when you level up (turning it on plays a preview)",
        section = trackerSection,
        position = 77
    )
    default boolean voiceLevelUp() {
        return true;
    }

    @ConfigItem(
        keyName = "chatLevelUp",
        name = "Chat: Level Up",
        description = "Chat message when you level up (off by default; the game already congratulates you)",
        section = trackerSection,
        position = 78
    )
    default boolean chatLevelUp() {
        return false;
    }
}
