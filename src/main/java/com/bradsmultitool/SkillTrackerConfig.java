package com.bradsmultitool;

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
public interface SkillTrackerConfig extends CommonConfig {

    // Repeated from CommonConfig: RuneLite only shows sections an interface declares itself


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

    enum PieStyle {
        TILE("Tile, icon and timer"),
        PIE("Timer only");

        private final String name;
        PieStyle(String name) { this.name = name; }
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
        description = "When you have another plugin on that draws the same timer (Tree Despawn Timer, MLM Despawn Timer, Calcified Rock Despawn Timer, Daeyalt Mining Timer, Fishing Spot Timer/Tracker, Barbarian or Aerial Fishing Timers, Star AFK Timer, Salvaging AFK Timer, Fire Timer, or RuneLite's Hunter plugin for traps), hide our timer for it so they don't stack",
        section = skillingSection,
        position = 3
    )
    default boolean deferToOtherTimerPlugins() {
        return true;
    }

    @ConfigItem(
        keyName = "piesAlwaysShown",
        name = "Timers When Not Training",
        description = "Show resource timers all the time. Off: a resource's timers only show while you train its skill (trees while you chop, rocks while you mine, fires while you cook or burn logs...)",
        section = skillingSection,
        position = 3
    )
    default boolean piesAlwaysShown() {
        return false;
    }

    @ConfigItem(
        keyName = "pieStyle",
        name = "Timer Style",
        description = "Tile, icon and timer: outline the tile of each timed tree, rock or fishing spot, with a picture of what it gives and the timer over it. Timer only: just the floating timer",
        section = skillingSection,
        position = 3
    )
    default PieStyle pieStyle() {
        return PieStyle.TILE;
    }

    @Range(min
    =
    10,
    max
    =
    100)
    @Units(Units.PERCENT)
    @ConfigItem(
        keyName = "timerOpacity",
        name = "Timer Opacity",
        description = "How solid the resource timers are drawn: lower to see the tree, rock or fishing spot through them",
        section = skillingSection,
        position = 3
    )
    default int timerOpacity() {
        return 60;
    }

    @Alpha
    @ConfigItem(
        keyName = "zoneColor",
        name = "Zone Colour",
        description = "Colour of the tile outline for timed trees, rocks and fishing spots (filled in a fainter shade)",
        section = skillingSection,
        position = 3
    )
    default Color zoneColor() {
        return new Color(0, 255, 255, 220);
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
        name = "Tree Timers",
        description = "A timer on each tree being chopped that empties as it nears falling (Forestry timer; counts every player chopping)",
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
        name = "Mining Timers",
        description = "Timers on Motherlode Mine ore veins, calcified rocks, Daeyalt essence and shooting stars (the star's current layer) showing how long they have left",
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
        name = "Fishing Spot Timers",
        description = "A timer on each fishing spot that's moved since you arrived. Spots move at a random time 2.5-5 minutes after they appear, so it shows that window: green until the earliest it can move, then a red ring filling up to the latest (hover it for the times)",
        section = fishingSection,
        position = 1
    )
    default boolean showFishingSpotTimer() {
        return false;
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
        name = "Trap Timers",
        description = "A timer on each trap you lay, showing how long until it collapses (green once something is caught)",
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
        name = "Fire Timers",
        description = "A timer on each fire you cook on. Fires go out at a random time 60-119 seconds after they're lit, so it shows that window: green until the earliest it can go out, then a red ring filling up to the latest (hover it for the times)",
        section = firemakingSection,
        position = 1
    )
    default boolean showFireTimer() {
        return false;
    }

    @ConfigItem(
        keyName = "showCampfireTimer",
        name = "Campfire Timers",
        description = "A timer on each Forester's Campfire showing how long it has left: set by the log that made it, topped up by each log you add, corrected when you Check it",
        section = firemakingSection,
        position = 2
    )
    default boolean showCampfireTimer() {
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
        name = "Shipwreck Timers",
        description = "A timer on each Sailing shipwreck you saw surface, showing how long it has left before it sinks",
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

    // ---- Generated by scripts/generate_config_defaults.py: CommonConfig's settings as this plugin's own (don't edit) ----
    @ConfigItem(
        keyName = "voicePersona",
        name = "Voice Persona",
        description = "Voice used for spoken alerts; changing it plays a sample. Amy, Cori: US and UK women. Alan, Ryan, John: UK and US men. Arctic: a Scottish man. L2 Arctic: a man with a Mandarin Chinese accent. Wise Old Man, TzHaar, Ghost: characters. Random: a different one each alert",
        section = generalSection,
        position = 1
    )
    @Override
    default VoiceAlertService.VoicePersona voicePersona() {
        return CommonConfig.super.voicePersona();
    }

    @Range(max
    =
    100)
    @ConfigItem(
        keyName = "voiceVolume",
        name = "Voice Volume",
        description = "Volume of spoken alerts (0-100)",
        section = generalSection,
        position = 2
    )
    @Override
    default int voiceVolume() {
        return CommonConfig.super.voiceVolume();
    }

    @ConfigItem(
        keyName = "voiceEnabled",
        name = "Voice Alerts",
        description = "Speak alerts out loud. Each alert also has its own Voice switch in its section",
        section = generalSection,
        position = 0
    )
    @Override
    default boolean voiceEnabled() {
        return CommonConfig.super.voiceEnabled();
    }

    @ConfigItem(
        keyName = "syncVoice",
        name = "Same Voice For All",
        description = "Use the same voice and volume in all of Snuffy's plugins you have: changing them in one changes them in the others (that have this on), and one you install later picks them up. Turn off to give this plugin its own voice",
        section = generalSection,
        position = 4
    )
    @Override
    default boolean syncVoice() {
        return CommonConfig.super.syncVoice();
    }

    @ConfigItem(
        keyName = "sendChatMessages",
        name = "Chat Messages",
        description = "Also post alerts as in-game chat messages. Each alert also has its own Chat switch in its section",
        section = generalSection,
        position = 3
    )
    @Override
    default boolean sendChatMessages() {
        return CommonConfig.super.sendChatMessages();
    }

    @ConfigItem(
        keyName = "characterIdleAlert",
        name = "AFK Alert",
        description = "Alert when you were training (gaining XP), your character stopped, and you haven't clicked or typed since. Banking, shops, the GE and menus never set it off",
        section = idleSection,
        position = 31
    )
    @Override
    default boolean characterIdleAlert() {
        return CommonConfig.super.characterIdleAlert();
    }

    @Range(min
    =
    3,
    max
    =
    120)
    @ConfigItem(
        keyName = "characterIdleSeconds",
        name = "Idle Alert After",
        description = "Seconds of your character doing nothing, with no clicks or key presses, before the AFK alert",
        section = idleSection,
        position = 32
    )
    @Units(Units.SECONDS)
    @Override
    default int characterIdleSeconds() {
        return CommonConfig.super.characterIdleSeconds();
    }

    @Range(min
    =
    5,
    max
    =
    295)
    @ConfigItem(
        keyName = "idleWarningSeconds",
        name = "Warn Before Logout",
        description = "How many seconds before the idle logout to warn you. Out of combat that's the normal idle timeout (follows RuneLite's Logout Timer plugin); in combat the game only logs you out after 20 minutes without a click or key press",
        section = idleSection,
        position = 34
    )
    @Units(Units.SECONDS)
    @Override
    default int idleWarningSeconds() {
        return CommonConfig.super.idleWarningSeconds();
    }

    @ConfigItem(
        keyName = "repeatIdleAlert",
        name = "Repeat Logout Warning",
        description = "Repeat the idle logout announcement until you move",
        section = idleSection,
        position = 36
    )
    @Override
    default boolean repeatIdleAlert() {
        return CommonConfig.super.repeatIdleAlert();
    }

    @ConfigItem(
        keyName = "repeatIdleInterval",
        name = "Repeat Every",
        description = "Seconds between repeat idle logout alerts",
        section = idleSection,
        position = 37
    )
    @Units(Units.SECONDS)
    @Override
    default int repeatIdleInterval() {
        return CommonConfig.super.repeatIdleInterval();
    }

    @ConfigItem(
        keyName = "maxIdleRepeats",
        name = "Max Repeats",
        description = "How many times to repeat the idle logout alert after the first",
        section = idleSection,
        position = 38
    )
    @Override
    default int maxIdleRepeats() {
        return CommonConfig.super.maxIdleRepeats();
    }

    @ConfigItem(
        keyName = "voiceCharacterIdle",
        name = "Voice: Character Idle",
        description = "When your character has gone AFK (turning it on plays a preview)",
        section = idleSection,
        position = 39
    )
    @Override
    default boolean voiceCharacterIdle() {
        return CommonConfig.super.voiceCharacterIdle();
    }

    @ConfigItem(
        keyName = "chatCharacterIdle",
        name = "Chat: Character Idle",
        description = "Chat message when your character has gone AFK",
        section = idleSection,
        position = 40
    )
    @Override
    default boolean chatCharacterIdle() {
        return CommonConfig.super.chatCharacterIdle();
    }

    @ConfigItem(
        keyName = "voiceIdleLogout",
        name = "Voice: Logout Warning",
        description = "When you are about to be logged out for inactivity (turning it on plays a preview)",
        section = idleSection,
        position = 41
    )
    @Override
    default boolean voiceIdleLogout() {
        return CommonConfig.super.voiceIdleLogout();
    }

    @ConfigItem(
        keyName = "chatIdleLogout",
        name = "Chat: Logout Warning",
        description = "Chat message when you are about to be logged out for inactivity",
        section = idleSection,
        position = 42
    )
    @Override
    default boolean chatIdleLogout() {
        return CommonConfig.super.chatIdleLogout();
    }
    // ---- End of CommonConfig's settings ----
}
