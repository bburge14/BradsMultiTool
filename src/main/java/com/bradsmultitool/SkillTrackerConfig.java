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

    @ConfigSection(
        name = "About",
        description = "<div width='300'>Snuffy's Skilling Helper: the XP tracker in the sidebar (set goals with Set Goal; right-click a skill card for its Method, pin and goal options), skilling hover cards, resource timers and alerts. Clashes (said in chat while they're on): Tree Despawn Timer, MLM Despawn Timer, Calcified Rock Despawn Timer, Daeyalt Mining Timer, Fishing Spot Timer, Fishing Spot Tracker, Barbarian Fishing, Aerial Fishing Timers, Star AFK Timer, Salvaging AFK Timer, Fire Timer, RuneLite's Hunter and Mining plugins (our matching timers hide for them), Hunter Rumours (it shows the same rumour card), and RuneLite's Idle Notifier. Open the Guide for every feature and how to use it.",
        position = -10
    )
    String aboutSection = "aboutSection";

    @ConfigSection(
        name = "Skilling Alerts",
        description = "Inventory full, resource gone and batch complete alerts",
        position = 34,
        closedByDefault = true
    )
    String skillingAlertsSection = "skillingAlertsSection";

    @ConfigSection(
        name = "Resource Timers",
        description = "How resource timers look, and stepping aside for other timer plugins; each skill's section turns its timers on",
        position = 33,
        closedByDefault = true
    )
    String resourceTimersSection = "resourceTimersSection";

    @ConfigSection(
        name = "Goals",
        description = "Goals and their suggested training: the method, its outlines, tools and items, and clearing reached goals",
        position = 31,
        closedByDefault = true
    )
    String goalsSection = "goalsSection";

    @ConfigItem(
        keyName = "openGuide",
        name = "Open the Guide",
        description = "<div width='300'>Opens the feature guide in your browser (then switches itself back off)",
        section = aboutSection,
        position = 0
    )
    default boolean openGuide() {
        return false;
    }

    @ConfigItem(
        keyName = "showClashes",
        name = "Clash Warnings",
        description = "<div width='300'>While another plugin that clashes with this one is on: a warning icon among RuneLite's info boxes (hover it for the list) and a chat line once a login. Off: neither",
        section = aboutSection,
        position = 1
    )
    default boolean showClashes() {
        return true;
    }

    // Repeated from CommonConfig: RuneLite only shows sections an interface declares itself

    @ConfigSection(
        name = "General",
        description = "Voice and chat settings shared by all alerts",
        position = 0
    )
    String generalSection = "generalSection";

    @ConfigSection(
        name = "Idle",
        description = "Idle character, idle and six-hour logout warnings, and run energy alerts",
        position = 5,
        closedByDefault = true
    )
    String idleSection = "idleSection";

    @ConfigSection(
        name = "Reminders",
        description = "Clue drop alerts",
        position = 6,
        closedByDefault = true
    )
    String remindersSection = "remindersSection";

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

    /** When a kind of resource timer shows: always, only while you train its skill, or never. */
    enum TimerShow {
        ALWAYS("Always"),
        TRAINING("While training"),
        OFF("Off");

        private final String name;
        TimerShow(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    enum PieStyle {
        TILE("Tile and icon"),
        PIE("Floating timer");

        private final String name;
        PieStyle(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    @ConfigSection(
        name = "Skilling Hover Cards",
        description = "Hover cards for trees, rocks, fishing spots, stalls and other skilling things",
        position = 32,
        closedByDefault = true
    )
    String skillingSection = "skillingSection";

    @ConfigItem(
        keyName = "showSkillingHover",
        name = "Show Hover Cards",
        description = "<div width='300'>When hovering a tree, rock, fishing spot, stall or pickpocket target, show level, XP, tools and more",
        section = skillingSection,
        position = 0
    )
    default boolean showSkillingHover() {
        return true;
    }

    @ConfigItem(
        keyName = "showSkillingRates",
        name = "XP Rates on Hover",
        description = "<div width='300'>Show the wiki's XP/hr and your own rate on the hover card while you're training that skill",
        section = skillingSection,
        position = 1
    )
    default boolean showSkillingRates() {
        return true;
    }

    @ConfigItem(
        keyName = "deferToOtherTimerPlugins",
        name = "Use Other Timer Plugins",
        description = "<div width='300'>When you have another plugin on that draws the same timer (Tree Despawn Timer, MLM Despawn Timer, Calcified Rock Despawn Timer, Daeyalt Mining Timer, Fishing Spot Timer/Tracker, Barbarian or Aerial Fishing Timers, Star AFK Timer, Salvaging AFK Timer, Fire Timer; RuneLite's Hunter plugin for traps, its Mining plugin for depleted rocks and Daeyalt, its Fishing plugin's Minnow overlay for minnow spots), hide our timer for it so they don't stack",
        section = resourceTimersSection,
        position = 5
    )
    default boolean deferToOtherTimerPlugins() {
        return true;
    }

    @ConfigItem(
        keyName = "pieStyle",
        name = "Timer Style",
        description = "<div width='300'>Tile and icon: outline the tile of each timed tree, rock, fishing spot (and the rest), with a picture of what it gives; the outline is the timer, going green, yellow, red (pulsing once it's due). Traps, fires and campfires keep a pie timer over their picture. Floating timer: a pie timer over each instead",
        section = resourceTimersSection,
        position = 0
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
        description = "<div width='300'>How solid the resource timers are drawn (the tile outlines, or the floating timers): lower to see through them",
        section = resourceTimersSection,
        position = 1
    )
    default int timerOpacity() {
        return 60;
    }

    @ConfigItem(
        keyName = "timerText",
        name = "Timer Text",
        description = "<div width='300'>The time above each resource timer: how long it has left (a star: its layer's health), or for fishing spots and fires, which go at a random time, how long ago they appeared",
        section = resourceTimersSection,
        position = 2
    )
    default boolean timerText() {
        return true;
    }

    @ConfigItem(
        keyName = "timerSkillIcon",
        name = "Timer Skill Icon",
        description = "<div width='300'>The skill's icon above each resource timer (Woodcutting on trees, Fishing on spots...)",
        section = resourceTimersSection,
        position = 3
    )
    default boolean timerSkillIcon() {
        return true;
    }

    @Alpha
    @ConfigItem(
        keyName = "zoneColor",
        name = "Zone Colour",
        description = "<div width='300'>Colour of the tile outline for fishing spots that have no timer (the timed ones take their timer's colour)",
        section = resourceTimersSection,
        position = 4
    )
    default Color zoneColor() {
        return new Color(0, 255, 255, 220);
    }

    @ConfigItem(
        keyName = "bestChoice",
        name = "Best Choice By",
        description = "<div width='300'>How cards pick the \"best\" thing to make from what you carry (e.g. the anvil card): Most XP, or Most Profit at Grand Exchange prices",
        section = skillingSection,
        position = 2
    )
    default BestChoice bestChoice() {
        return BestChoice.XP;
    }

    @ConfigItem(
        keyName = "skillingWhenNotRetaliating",
        name = "Skill Cards With Auto Retaliate Off",
        description = "<div width='300'>With auto retaliate off, hovering an NPC you can also skill on (e.g. pickpocket) shows the skilling card instead of the monster card, even when Attack is the left-click option",
        section = skillingSection,
        position = 3
    )
    default boolean skillingWhenNotRetaliating() {
        return true;
    }

    @ConfigItem(
        keyName = "inventoryFullAlert",
        name = "Inventory Full",
        description = "Alert when your inventory fills up while skilling",
        section = skillingAlertsSection,
        position = 0
    )
    default boolean inventoryFullAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceInventoryFull",
        name = "Voice: Inventory Full",
        description = "<div width='300'>When your inventory fills while skilling (turning it on plays a preview)",
        section = skillingAlertsSection,
        position = 1
    )
    default boolean voiceInventoryFull() {
        return true;
    }

    @ConfigItem(
        keyName = "chatInventoryFull",
        name = "Chat: Inventory Full",
        description = "Chat message when your inventory fills while skilling",
        section = skillingAlertsSection,
        position = 2
    )
    default boolean chatInventoryFull() {
        return true;
    }

    @ConfigItem(
        keyName = "resourceGoneAlert",
        name = "Resource Gone",
        description = "<div width='300'>Alert when your tree falls, rock depletes or fishing spot moves",
        section = skillingAlertsSection,
        position = 3
    )
    default boolean resourceGoneAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "batchCompleteAlert",
        name = "Batch Complete",
        description = "<div width='300'>Alert when a run of Cooking, Smithing, Crafting, Fletching or Herblore finishes (you stop gaining XP and your character stops)",
        section = skillingAlertsSection,
        position = 4
    )
    default boolean batchCompleteAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceBatchComplete",
        name = "Voice: Batch Complete",
        description = "<div width='300'>When your production batch finishes (turning it on plays a preview)",
        section = skillingAlertsSection,
        position = 5
    )
    default boolean voiceBatchComplete() {
        return true;
    }

    @ConfigItem(
        keyName = "chatBatchComplete",
        name = "Chat: Batch Complete",
        description = "Chat message when your production batch finishes",
        section = skillingAlertsSection,
        position = 6
    )
    default boolean chatBatchComplete() {
        return true;
    }

    @ConfigSection(
        name = "Woodcutting",
        description = "Tree timers and alerts",
        position = 40,
        closedByDefault = true
    )
    String woodcuttingSection = "woodcuttingSection";

    @ConfigItem(
        keyName = "treeTimers",
        name = "Tree Timers",
        description = "<div width='300'>A timer on each tree being chopped that empties as it nears falling (Forestry timer; counts every player chopping) Always: as soon as you're near; While training: once you're training the skill (an XP drop in the last two minutes); Off: never.",
        section = woodcuttingSection,
        position = 0
    )
    default TimerShow showTreeTimer() {
        return TimerShow.TRAINING;
    }

    @ConfigItem(
        keyName = "voiceTreeGone",
        name = "Voice: Tree Fallen",
        description = "<div width='300'>When the tree you are chopping falls (turning it on plays a preview)",
        section = woodcuttingSection,
        position = 1
    )
    default boolean voiceTreeGone() {
        return true;
    }

    @ConfigItem(
        keyName = "chatTreeGone",
        name = "Chat: Tree Fallen",
        description = "Chat message when the tree you are chopping falls",
        section = woodcuttingSection,
        position = 2
    )
    default boolean chatTreeGone() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceForestryEvent",
        name = "Voice: Forestry Event",
        description = "<div width='300'>When a Forestry event starts near you while you're chopping (turning it on plays a preview)",
        section = woodcuttingSection,
        position = 3
    )
    default boolean voiceForestryEvent() {
        return true;
    }

    @ConfigItem(
        keyName = "chatForestryEvent",
        name = "Chat: Forestry Event",
        description = "<div width='300'>Chat message naming the Forestry event that started near you",
        section = woodcuttingSection,
        position = 4
    )
    default boolean chatForestryEvent() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceBirdNest",
        name = "Voice: Bird Nest",
        description = "<div width='300'>When a bird's nest falls out of the tree (turning it on plays a preview)",
        section = woodcuttingSection,
        position = 5
    )
    default boolean voiceBirdNest() {
        return true;
    }

    @ConfigItem(
        keyName = "chatBirdNest",
        name = "Chat: Bird Nest",
        description = "<div width='300'>Chat message when a bird's nest falls (and when it's a clue nest)",
        section = woodcuttingSection,
        position = 6
    )
    default boolean chatBirdNest() {
        return true;
    }

    @ConfigSection(
        name = "Mining",
        description = "Mining timers and alerts",
        position = 41,
        closedByDefault = true
    )
    String miningSection = "miningSection";

    @ConfigItem(
        keyName = "miningTimers",
        name = "Mining Timers",
        description = "<div width='300'>Timers on Motherlode Mine ore veins, calcified rocks, Daeyalt essence and shooting stars (the star's current layer) showing how long they have left Always: as soon as you're near; While training: once you're training the skill (an XP drop in the last two minutes); Off: never.",
        section = miningSection,
        position = 0
    )
    default TimerShow showMiningTimer() {
        return TimerShow.TRAINING;
    }

    @ConfigItem(
        keyName = "voiceRockDepletedEach",
        name = "Voice: Rock Depleted",
        description = "<div width='300'>Off by default: most rocks go after one ore, so it would speak after nearly every one. When the rock you are mining is depleted (turning it on plays a preview)",
        section = miningSection,
        position = 1
    )
    default boolean voiceRockDepletedEach() {
        return false;
    }

    @ConfigItem(
        keyName = "chatRockDepleted",
        name = "Chat: Rock Depleted",
        description = "Chat message when the rock you are mining is depleted",
        section = miningSection,
        position = 2
    )
    default boolean chatRockDepleted() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceSackFull",
        name = "Voice: Motherlode Sack Full",
        description = "<div width='300'>In the Motherlode Mine, when the pay-dirt you carry fills the sack, so you empty it before mining more (turning it on plays a preview)",
        section = miningSection,
        position = 3
    )
    default boolean voiceSackFull() {
        return true;
    }

    @ConfigItem(
        keyName = "chatSackFull",
        name = "Chat: Motherlode Sack Full",
        description = "<div width='300'>Chat message when the pay-dirt you carry fills the Motherlode sack",
        section = miningSection,
        position = 4
    )
    default boolean chatSackFull() {
        return true;
    }

    @ConfigSection(
        name = "Fishing",
        description = "Fishing spot timers and alerts",
        position = 42,
        closedByDefault = true
    )
    String fishingSection = "fishingSection";

    @ConfigItem(
        keyName = "fishingSpotTimers",
        name = "Fishing Spot Timers",
        description = "<div width='300'>A timer on each fishing spot that's moved since you arrived (one that was already there gets one once it moves: when it appeared can't be known). Spots move at a random time 2.5-5 minutes after they appear, so it shows that window: green until the earliest it can move, then a red ring filling up to the latest (hover it for the times) Always: as soon as you're near; While training: once you're training the skill (an XP drop in the last two minutes); Off: never.",
        section = fishingSection,
        position = 0
    )
    default TimerShow showFishingSpotTimer() {
        return TimerShow.TRAINING;
    }

    @ConfigItem(
        keyName = "fishingMarkNewest",
        name = "Mark Newest Spot",
        description = "<div width='300'>Off by default. A gold ring around the Fishing icon of the spot that appeared most recently: spots move at a random time, so the newest one is the least likely to move soon, the best one to fish",
        section = fishingSection,
        position = 2
    )
    default boolean fishingNewestSpot() {
        return false;
    }

    @Alpha
    @ConfigItem(
        keyName = "fishingActiveColor",
        name = "Your Spot Colour",
        description = "<div width='300'>The tile outline (or, with Timer only, the timer's border) of the spot you're fishing",
        section = fishingSection,
        position = 1
    )
    default Color fishingActiveColor() {
        return new Color(0, 255, 0, 230);
    }

    @ConfigItem(
        keyName = "voiceFishingSpot",
        name = "Voice: Fishing Spot Moved",
        description = "When your fishing spot moves (turning it on plays a preview)",
        section = fishingSection,
        position = 3
    )
    default boolean voiceFishingSpot() {
        return true;
    }

    @ConfigItem(
        keyName = "chatFishingSpot",
        name = "Chat: Fishing Spot Moved",
        description = "Chat message when your fishing spot moves",
        section = fishingSection,
        position = 4
    )
    default boolean chatFishingSpot() {
        return true;
    }

    @ConfigSection(
        name = "Thieving",
        description = "Thieving alerts",
        position = 43,
        closedByDefault = true
    )
    String thievingSection = "thievingSection";

    @ConfigItem(
        keyName = "coinPouchAlert",
        name = "Coin Pouch Alert",
        description = "<div width='300'>Alert when your coin pouches reach the most you can hold (28, more with the Ardougne Diary)",
        section = thievingSection,
        position = 0
    )
    default boolean coinPouchAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceCoinPouch",
        name = "Voice: Coin Pouches Full",
        description = "<div width='300'>When your coin pouches are full (turning it on plays a preview)",
        section = thievingSection,
        position = 1
    )
    default boolean voiceCoinPouch() {
        return true;
    }

    @ConfigItem(
        keyName = "chatCoinPouch",
        name = "Chat: Coin Pouches Full",
        description = "Chat message when your coin pouches are full",
        section = thievingSection,
        position = 2
    )
    default boolean chatCoinPouch() {
        return true;
    }

    @ConfigSection(
        name = "Farming",
        description = "Farming patch info",
        position = 44,
        closedByDefault = true
    )
    String farmingSection = "farmingSection";

    @ConfigItem(
        keyName = "showFarmingHover",
        name = "Farming Patch Hover",
        description = "<div width='300'>Hovering a farming patch shows what's growing, its stage, roughly when it's grown, compost, watering, protection and what you can plant",
        section = farmingSection,
        position = 1
    )
    default boolean showFarmingHover() {
        return true;
    }

    @ConfigSection(
        name = "Hunter",
        description = "Hunter creature info, trap and bird house timers, Hunters' Rumours",
        position = 45,
        closedByDefault = true
    )
    String hunterSection = "hunterSection";

    @ConfigItem(
        keyName = "showHunterHover",
        name = "Creature Hover",
        description = "<div width='300'>Hovering a Hunter creature shows its level, XP and the trap it needs",
        section = hunterSection,
        position = 0
    )
    default boolean showHunterHover() {
        return true;
    }

    @ConfigItem(
        keyName = "trapTimers",
        name = "Trap Timers",
        description = "<div width='300'>A timer on each trap you lay, showing how long until it collapses (green once something is caught; a full pie in Failed Trap Colour once it springs empty). Always: as soon as you're near; While training: once you're training the skill (an XP drop in the last two minutes); Off: never.",
        section = hunterSection,
        position = 1
    )
    default TimerShow showTrapTimer() {
        return TimerShow.TRAINING;
    }

    @ConfigItem(
        keyName = "showBirdhouseHover",
        name = "Bird House Hover",
        description = "Hovering a bird house shows its type and when it is ready",
        section = hunterSection,
        position = 2
    )
    default boolean showBirdhouseHover() {
        return true;
    }

    @ConfigItem(
        keyName = "rumourCard",
        name = "Rumour Card",
        description = "<div width='300'>Your Hunters' Rumour in the tracker (and on the pinned Hunter skill): the creature, the guild hunter, catches so far out of the catch the rare part is guaranteed by (fewer in the guild hunter outfit), and where to find it. Read from the hunters' dialogue and the Quetzal whistle's Rumour message, so talk to your hunter or check the whistle once",
        section = hunterSection,
        position = 6
    )
    default boolean rumourCard() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceRumourComplete",
        name = "Voice: Rumour Complete",
        description = "<div width='300'>When your rumour creature's rare part drops (turning it on plays a preview)",
        section = hunterSection,
        position = 7
    )
    default boolean voiceRumourComplete() {
        return true;
    }

    @ConfigItem(
        keyName = "chatRumourComplete",
        name = "Chat: Rumour Complete",
        description = "<div width='300'>Chat message when your rumour creature's rare part drops, with how many you caught",
        section = hunterSection,
        position = 8
    )
    default boolean chatRumourComplete() {
        return true;
    }

    @Alpha
    @ConfigItem(
        keyName = "failedTrapColor",
        name = "Failed Trap Colour",
        description = "<div width='300'>The colour of a trap's timer once it has sprung with nothing in it (it needs resetting), so it stands out from a trap running out",
        section = hunterSection,
        position = 9
    )
    default Color failedTrapColor() {
        return new Color(190, 110, 255);
    }

    @ConfigSection(
        name = "Firemaking",
        description = "Fire timers",
        position = 46,
        closedByDefault = true
    )
    String firemakingSection = "firemakingSection";

    @ConfigItem(
        keyName = "fireTimers",
        name = "Fire Timers",
        description = "<div width='300'>A timer on each fire you cook on. Fires go out at a random time 60-119 seconds after they're lit, so it shows that window: green until the earliest it can go out, then a red ring filling up to the latest (hover it for the times) Always: as soon as you're near; While training: once you're training the skill (an XP drop in the last two minutes); Off: never.",
        section = firemakingSection,
        position = 0
    )
    default TimerShow showFireTimer() {
        return TimerShow.OFF;
    }

    @ConfigItem(
        keyName = "campfireTimers",
        name = "Campfire Timers",
        description = "<div width='300'>A timer on each Forester's Campfire showing how long it has left: set by the log that made it, topped up by each log you add, corrected when you Check it Always: as soon as you're near; While training: once you're training the skill (an XP drop in the last two minutes); Off: never.",
        section = firemakingSection,
        position = 1
    )
    default TimerShow showCampfireTimer() {
        return TimerShow.TRAINING;
    }

    @ConfigSection(
        name = "Runecraft",
        description = "Altar info",
        position = 47,
        closedByDefault = true
    )
    String runecraftSection = "runecraftSection";

    @ConfigItem(
        keyName = "showRunecraftHover",
        name = "Altar Hover",
        description = "<div width='300'>Hovering an altar shows the rune, level, XP and how many runes you make per essence",
        section = runecraftSection,
        position = 1
    )
    default boolean showRunecraftHover() {
        return true;
    }

    @ConfigSection(
        name = "Agility",
        description = "Course and shortcut info",
        position = 48,
        closedByDefault = true
    )
    String agilitySection = "agilitySection";

    @ConfigItem(
        keyName = "showAgilityHover",
        name = "Obstacle Hover",
        description = "<div width='300'>Hovering a course obstacle or shortcut shows its Agility level, the course XP per lap and laps to your next level",
        section = agilitySection,
        position = 0
    )
    default boolean showAgilityHover() {
        return true;
    }

    @ConfigItem(
        keyName = "agilityCourseCard",
        name = "Course Card",
        description = "<div width='300'>While you run an Agility course: laps this session, laps and XP per hour, average lap time, laps to your next level and goal, and marks of grace picked up",
        section = agilitySection,
        position = 1
    )
    default boolean agilityCourseCard() {
        return true;
    }

    @ConfigItem(
        keyName = "agilityArenaCard",
        name = "Brimhaven Arena Card",
        description = "<div width='300'>In the Brimhaven Agility Arena: how long until the ticket pillar moves (it moves every minute), and the tickets you've earned this session and per hour",
        section = agilitySection,
        position = 2
    )
    default boolean agilityArenaCard() {
        return true;
    }

    @Range(min
    =
    1,
    max
    =
    30)
    @Units(Units.SECONDS)
    @ConfigItem(
        keyName = "arenaWarnSeconds",
        name = "Pillar Warning At",
        description = "<div width='300'>How long before the pillar moves to warn you (and turn the card red)",
        section = agilitySection,
        position = 3
    )
    default int arenaWarnSeconds() {
        return 10;
    }

    @ConfigItem(
        keyName = "voiceArenaPillar",
        name = "Voice: Pillar About to Move",
        description = "<div width='300'>Says when the Brimhaven pillar is about to move (turning it on plays a preview)",
        section = agilitySection,
        position = 4
    )
    default boolean voiceArenaPillar() {
        return true;
    }

    @ConfigItem(
        keyName = "chatArenaPillar",
        name = "Chat: Pillar About to Move",
        description = "<div width='300'>Chat message when the Brimhaven pillar is about to move",
        section = agilitySection,
        position = 5
    )
    default boolean chatArenaPillar() {
        return true;
    }

    @ConfigItem(
        keyName = "arenaObstacleColours",
        name = "Arena Obstacle Colours",
        description = "<div width='300'>In the Brimhaven Agility Arena, outlines each obstacle by your Agility level: green you can do it safely, yellow you can do it but may fail, red your level is too low. Hover one for its XP, time and fail chance",
        section = agilitySection,
        position = 6
    )
    default boolean arenaObstacleColours() {
        return true;
    }

    @ConfigItem(
        keyName = "arenaRoute",
        name = "Arena Route",
        description = "<div width='300'>Shows the quickest way to the tagged pillar using only obstacles you can do, counting each obstacle's time and how likely you are to fail it",
        section = agilitySection,
        position = 7
    )
    default boolean arenaRoute() {
        return true;
    }

    @ConfigItem(
        keyName = "arenaRouteMinimap",
        name = "Arena Route on Minimap",
        description = "<div width='300'>Draws the Arena Route on the minimap too, with a dot on the tagged pillar",
        section = agilitySection,
        position = 8
    )
    default boolean arenaRouteMinimap() {
        return true;
    }

    @ConfigItem(
        keyName = "arenaDebug",
        name = "Arena Route Readout",
        description = "<div width='300'>Adds what the route sees to the arena card: pillars and obstacles found, links between pillars, and the route's steps. For checking the route works",
        section = agilitySection,
        position = 9
    )
    default boolean arenaDebug() {
        return false;
    }

    @ConfigItem(
        keyName = "oneObstacleTraining",
        name = "One Obstacle Training",
        description = "<div width='300'>When you cross the same obstacle over and over instead of running a course (the Brimhaven Arena's floor spikes back and forth, or any Agility shortcut): a card counts your reps, reps and XP per hour, and reps to your next level and goal, and outlines it. In the arena the route and pillar warning step aside until you stop",
        section = agilitySection,
        position = 10
    )
    default boolean oneObstacleTraining() {
        return true;
    }

    @Range(max
    =
    100)
    @Units(Units.PERCENT)
    @ConfigItem(
        keyName = "runEnergyBelow",
        name = "Run Energy Alert Below",
        description = "<div width='300'>While you're training Agility, alert when your run energy drops below this (0: off). Quiet while the Idle section's Run Energy Low Below is on (that one alerts anywhere, Agility included)",
        section = agilitySection,
        position = 11
    )
    default int runEnergyBelow() {
        return 15;
    }

    @ConfigItem(
        keyName = "voiceRunEnergy",
        name = "Voice: Run Energy Low",
        description = "<div width='300'>Says when your run energy is low while training Agility (turning it on plays a preview)",
        section = agilitySection,
        position = 12
    )
    default boolean voiceRunEnergy() {
        return true;
    }

    @ConfigItem(
        keyName = "chatRunEnergy",
        name = "Chat: Run Energy Low",
        description = "<div width='300'>Chat message when your run energy is low while training Agility",
        section = agilitySection,
        position = 13
    )
    default boolean chatRunEnergy() {
        return true;
    }

    @ConfigSection(
        name = "Cooking",
        description = "Burn levels",
        position = 49,
        closedByDefault = true
    )
    String cookingSection = "cookingSection";

    @ConfigItem(
        keyName = "showCookingHover",
        name = "Range / Fire Hover",
        description = "<div width='300'>Hovering a range or fire shows each raw food you are carrying: its level, XP and burn level",
        section = cookingSection,
        position = 1
    )
    default boolean showCookingHover() {
        return true;
    }

    @ConfigSection(
        name = "Sailing",
        description = "Sailing timers, hover cards and boat alerts",
        position = 50,
        closedByDefault = true
    )
    String sailingSection = "sailingSection";

    @ConfigItem(
        keyName = "shipwreckTimers",
        name = "Shipwreck Timers",
        description = "<div width='300'>A timer on each Sailing shipwreck you saw surface, showing how long it has left before it sinks Always: as soon as you're near; While training: once you're training the skill (an XP drop in the last two minutes); Off: never.",
        section = sailingSection,
        position = 1
    )
    default TimerShow showShipwreckTimer() {
        return TimerShow.TRAINING;
    }

    @ConfigItem(
        keyName = "showSailingHover",
        name = "Sailing Hover",
        description = "<div width='300'>Hover cards for sea charting spots (task, level, XP), shipwrecks (level, salvage XP, best hook for you), trawling shoals (Fishing level, fish, depth, bait), sea monsters (HP, safe keel, bounty parts) and your current duck",
        section = sailingSection,
        position = 0
    )
    default boolean showSailingHover() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceBoatHealth",
        name = "Voice: Boat Health Low",
        description = "<div width='300'>On your boat, when its hitpoints drop below Boat Health Low At, once until it's repaired (turning it on plays a preview)",
        section = sailingSection,
        position = 2
    )
    default boolean voiceBoatHealth() {
        return true;
    }

    @ConfigItem(
        keyName = "chatBoatHealth",
        name = "Chat: Boat Health Low",
        description = "Chat message when your boat's hitpoints are low",
        section = sailingSection,
        position = 3
    )
    default boolean chatBoatHealth() {
        return true;
    }

    @Range(min = 1, max = 99)
    @Units(Units.PERCENT)
    @ConfigItem(
        keyName = "boatHealthPercent",
        name = "Boat Health Low At",
        description = "<div width='300'>Boat hitpoints (percent of its most) below which Boat Health Low warns you",
        section = sailingSection,
        position = 4
    )
    default int boatHealthPercent() {
        return 30;
    }

    @ConfigItem(
        keyName = "voiceSailTrim",
        name = "Voice: Trim Sails",
        description = "<div width='300'>On your boat, when a gust opens the window to trim the sails for a burst of speed (turning it on plays a preview)",
        section = sailingSection,
        position = 5
    )
    default boolean voiceSailTrim() {
        return true;
    }

    @ConfigItem(
        keyName = "chatSailTrim",
        name = "Chat: Trim Sails",
        description = "Chat message when you can trim the sails",
        section = sailingSection,
        position = 6
    )
    default boolean chatSailTrim() {
        return true;
    }

    @ConfigSection(
        name = "Smithing",
        description = "Anvil and furnace cards",
        position = 51,
        closedByDefault = true
    )
    String smithingSection = "smithingSection";

    @ConfigItem(
        keyName = "showSmithingHover",
        name = "Anvil / Furnace Hover",
        description = "<div width='300'>Hovering an anvil or furnace shows what you can make from the bars or ores you are carrying",
        section = smithingSection,
        position = 1
    )
    default boolean showSmithingHover() {
        return true;
    }

    @ConfigSection(
        name = "Minigames",
        description = "Wintertodt, Tempoross and Guardians of the Rift cards and alerts",
        position = 52,
        closedByDefault = true
    )
    String minigamesSection = "minigamesSection";

    @ConfigItem(
        keyName = "wintertodtCard",
        name = "Wintertodt Card",
        description = "<div width='300'>At the Wintertodt, a small card with your warmth, the Wintertodt's energy, your points this round, the roots and kindling you carry and when the next round starts",
        section = minigamesSection,
        position = 0
    )
    default boolean wintertodtCard() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceWintertodtRound",
        name = "Voice: Round Starting",
        description = "<div width='300'>At the Wintertodt, five seconds before the next round starts (turning it on plays a preview)",
        section = minigamesSection,
        position = 1
    )
    default boolean voiceWintertodtRound() {
        return true;
    }

    @ConfigItem(
        keyName = "chatWintertodtRound",
        name = "Chat: Round Starting",
        description = "Chat message when the Wintertodt round is about to start",
        section = minigamesSection,
        position = 2
    )
    default boolean chatWintertodtRound() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceWintertodtBrazier",
        name = "Voice: Brazier Out / Broken",
        description = "<div width='300'>At the Wintertodt, when the brazier you were using goes out or breaks (turning it on plays a preview)",
        section = minigamesSection,
        position = 3
    )
    default boolean voiceWintertodtBrazier() {
        return true;
    }

    @ConfigItem(
        keyName = "chatWintertodtBrazier",
        name = "Chat: Brazier Out / Broken",
        description = "Chat message when your Wintertodt brazier goes out or breaks",
        section = minigamesSection,
        position = 4
    )
    default boolean chatWintertodtBrazier() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceWintertodtRoots",
        name = "Voice: Out of Roots",
        description = "<div width='300'>At the Wintertodt, when you run out of bruma roots while fletching or feeding the brazier (turning it on plays a preview)",
        section = minigamesSection,
        position = 5
    )
    default boolean voiceWintertodtRoots() {
        return true;
    }

    @ConfigItem(
        keyName = "chatWintertodtRoots",
        name = "Chat: Out of Roots",
        description = "Chat message when you run out of bruma roots",
        section = minigamesSection,
        position = 6
    )
    default boolean chatWintertodtRoots() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceWintertodtHit",
        name = "Voice: Cold Stopped You",
        description = "<div width='300'>At the Wintertodt, when the cold or snowfall hits you and stops what you were doing: fletching, feeding, lighting or fixing; chopping carries on (turning it on plays a preview)",
        section = minigamesSection,
        position = 7
    )
    default boolean voiceWintertodtHit() {
        return true;
    }

    @ConfigItem(
        keyName = "chatWintertodtHit",
        name = "Chat: Cold Stopped You",
        description = "<div width='300'>Chat message when the cold or snowfall stops what you were doing at the Wintertodt",
        section = minigamesSection,
        position = 8
    )
    default boolean chatWintertodtHit() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceWintertodtWarmth",
        name = "Voice: Warmth Low",
        description = "<div width='300'>At the Wintertodt, when your warmth drops below Warmth Low At, so you eat or step back (turning it on plays a preview)",
        section = minigamesSection,
        position = 9
    )
    default boolean voiceWintertodtWarmth() {
        return true;
    }

    @ConfigItem(
        keyName = "chatWintertodtWarmth",
        name = "Chat: Warmth Low",
        description = "Chat message when your Wintertodt warmth is low",
        section = minigamesSection,
        position = 10
    )
    default boolean chatWintertodtWarmth() {
        return true;
    }

    @Range(min = 1, max = 99)
    @Units(Units.PERCENT)
    @ConfigItem(
        keyName = "wintertodtWarmthPercent",
        name = "Warmth Low At",
        description = "<div width='300'>Warmth (percent) below which Warmth Low warns you, and the card shows your warmth in red",
        section = minigamesSection,
        position = 11
    )
    default int wintertodtWarmthPercent() {
        return 30;
    }

    @ConfigItem(
        keyName = "temporossCard",
        name = "Tempoross Card",
        description = "<div width='300'>During Tempoross, a small card with its energy and essence, the storm, your points, the raw and cooked fish you carry and the damage they'd do",
        section = minigamesSection,
        position = 12
    )
    default boolean temporossCard() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceTemporossWave",
        name = "Voice: Wave Incoming",
        description = "<div width='300'>During Tempoross, when a colossal wave is coming: tether yourself to a mast or totem (turning it on plays a preview)",
        section = minigamesSection,
        position = 13
    )
    default boolean voiceTemporossWave() {
        return true;
    }

    @ConfigItem(
        keyName = "chatTemporossWave",
        name = "Chat: Wave Incoming",
        description = "Chat message when a wave is coming at Tempoross",
        section = minigamesSection,
        position = 14
    )
    default boolean chatTemporossWave() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceTemporossFire",
        name = "Voice: Fire Near You",
        description = "<div width='300'>During Tempoross, when lightning is about to start a fire within a few tiles of you (turning it on plays a preview)",
        section = minigamesSection,
        position = 15
    )
    default boolean voiceTemporossFire() {
        return true;
    }

    @ConfigItem(
        keyName = "chatTemporossFire",
        name = "Chat: Fire Near You",
        description = "<div width='300'>Chat message when a fire is about to start near you at Tempoross",
        section = minigamesSection,
        position = 16
    )
    default boolean chatTemporossFire() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceTemporossDoubleSpot",
        name = "Voice: Double Fishing Spot",
        description = "<div width='300'>During Tempoross, when the double harpoonfish spot appears (turning it on plays a preview)",
        section = minigamesSection,
        position = 17
    )
    default boolean voiceTemporossDoubleSpot() {
        return true;
    }

    @ConfigItem(
        keyName = "chatTemporossDoubleSpot",
        name = "Chat: Double Fishing Spot",
        description = "<div width='300'>Chat message when the double fishing spot appears at Tempoross",
        section = minigamesSection,
        position = 18
    )
    default boolean chatTemporossDoubleSpot() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceTemporossVulnerable",
        name = "Voice: Vulnerable",
        description = "<div width='300'>During Tempoross, when its energy is gone and it can be attacked, starting the next phase (turning it on plays a preview)",
        section = minigamesSection,
        position = 19
    )
    default boolean voiceTemporossVulnerable() {
        return true;
    }

    @ConfigItem(
        keyName = "chatTemporossVulnerable",
        name = "Chat: Vulnerable",
        description = "Chat message when Tempoross is vulnerable",
        section = minigamesSection,
        position = 20
    )
    default boolean chatTemporossVulnerable() {
        return true;
    }

    @ConfigItem(
        keyName = "riftCard",
        name = "Guardians of the Rift Card",
        description = "<div width='300'>In Guardians of the Rift, a small card with the Great Guardian's power, your energy, guardians built, the two altars open now with the Runecraft level each needs (red when yours is too low) and where the portal is",
        section = minigamesSection,
        position = 21
    )
    default boolean riftCard() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceRiftStarting",
        name = "Voice: Game Starting",
        description = "<div width='300'>In Guardians of the Rift, ten seconds before the rift opens and the game starts (turning it on plays a preview)",
        section = minigamesSection,
        position = 22
    )
    default boolean voiceRiftStarting() {
        return true;
    }

    @ConfigItem(
        keyName = "chatRiftStarting",
        name = "Chat: Game Starting",
        description = "<div width='300'>Chat message when the Guardians of the Rift game is about to start",
        section = minigamesSection,
        position = 23
    )
    default boolean chatRiftStarting() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceRiftPortal",
        name = "Voice: Portal Opened",
        description = "<div width='300'>In Guardians of the Rift, when the portal to the huge guardian remains opens, with where it is in chat; not while you're in the remains (turning it on plays a preview)",
        section = minigamesSection,
        position = 24
    )
    default boolean voiceRiftPortal() {
        return true;
    }

    @ConfigItem(
        keyName = "chatRiftPortal",
        name = "Chat: Portal Opened",
        description = "Chat message when the Guardians of the Rift portal opens",
        section = minigamesSection,
        position = 25
    )
    default boolean chatRiftPortal() {
        return true;
    }

    @ConfigSection(
        name = "Skill Tracker",
        description = "Sidebar XP tracker",
        position = 30,
        closedByDefault = true
    )
    String trackerSection = "trackerSection";

    @ConfigItem(
        keyName = "showTrackerProgressBar",
        name = "Show Progress Bar",
        description = "The level progress bar at the top of each skill card",
        section = trackerSection,
        position = 0
    )
    default boolean showTrackerProgressBar() {
        return true;
    }

    @ConfigItem(
        keyName = "showTrackerXpGained",
        name = "Show XP Gained",
        description = "XP gained this session",
        section = trackerSection,
        position = 1
    )
    default boolean showTrackerXpGained() {
        return true;
    }

    @ConfigItem(
        keyName = "showTrackerXpToLevel",
        name = "Show XP to Level",
        description = "XP left until the next level",
        section = trackerSection,
        position = 2
    )
    default boolean showTrackerXpToLevel() {
        return true;
    }

    @ConfigItem(
        keyName = "showTrackerActions",
        name = "Show Actions Left",
        description = "<div width='300'>How many more of your last action until the next level (not shown for combat skills)",
        section = trackerSection,
        position = 3
    )
    default boolean showTrackerActions() {
        return true;
    }

    @ConfigItem(
        keyName = "trackerActionsToGoal",
        name = "Actions Until Goal",
        description = "<div width='300'>When a skill has a goal, count actions left until the goal instead of until the next level. When the suggested method changes before the goal, it counts to the end of the step you're on (hover the count to see what it counts to)",
        section = goalsSection,
        position = 1
    )
    default boolean trackerActionsToGoal() {
        return false;
    }

    @ConfigItem(
        keyName = "showTrackerXpPerHour",
        name = "Show XP/hr",
        description = "XP per hour",
        section = trackerSection,
        position = 4
    )
    default boolean showTrackerXpPerHour() {
        return true;
    }

    @ConfigItem(
        keyName = "showXpPerMinute",
        name = "Show XP/min",
        description = "XP per minute",
        section = trackerSection,
        position = 5
    )
    default boolean showXpPerMinute() {
        return true;
    }

    @ConfigItem(
        keyName = "showTrackerTimeToLevel",
        name = "Show Time to Level",
        description = "Estimated time until the next level at your current rate",
        section = trackerSection,
        position = 6
    )
    default boolean showTrackerTimeToLevel() {
        return true;
    }

    @ConfigItem(
        keyName = "showTrackerGoal",
        name = "Show Goal",
        description = "<div width='300'>Your goal for the skill (right-click a skill card to set one): XP left and time to reach it",
        section = goalsSection,
        position = 0
    )
    default boolean showTrackerGoal() {
        return true;
    }

    @ConfigItem(
        keyName = "showTrainingSuggestion",
        name = "Show Suggested Method",
        description = "<div width='300'>On a skill with a goal, show the wiki training guide's method for your level and what to switch to on the way",
        section = goalsSection,
        position = 2
    )
    default boolean showTrainingSuggestion() {
        return true;
    }

    @ConfigItem(
        keyName = "trainingFocus",
        name = "Training Focus",
        description = "<div width='300'>What goal suggestions aim for, unless a skill has its own (right-click a skill in the tracker > Suggest, or pick it when setting a goal): the wiki guide's fastest XP, its other methods (often more AFK), the cheapest way (fewest GP per XP when making things; gathering is free), or the best money maker you can do. Slayer always suggests the best Slayer master",
        section = goalsSection,
        position = 3
    )
    default TrainingFocus trainingFocus() {
        return TrainingFocus.FASTEST;
    }

    @ConfigItem(
        keyName = "highlightSuggestedTraining",
        name = "Highlight Suggested Method",
        description = "<div width='300'>Outline the trees, rocks, stalls or NPCs of the suggested method for each skill you have a goal in",
        section = goalsSection,
        position = 4
    )
    default boolean highlightSuggestedTraining() {
        return true;
    }

    @ConfigItem(
        keyName = "highlightGoalItems",
        name = "Highlight Goal Items",
        description = "<div width='300'>For Cooking, Smithing, Fletching, Crafting, Herblore, Firemaking and other goals, outline in your inventory and bank the materials of the best thing you can make from them (hover for which goal)",
        section = goalsSection,
        position = 5
    )
    default boolean highlightGoalItems() {
        return true;
    }

    @ConfigItem(
        keyName = "hideOutlinesWhileWorking",
        name = "Hide Outlines While Working",
        description = "<div width='300'>The fishing spot, tree or rock you're working drops its goal outline (and a fishing spot its plain tile outline) while you work it; the others keep theirs, and it comes back a few seconds after you stop. Timer outlines stay",
        section = goalsSection,
        position = 7
    )
    default boolean hideOutlinesWhileWorking() {
        return true;
    }

    @Alpha
    @ConfigItem(
        keyName = "trainingHighlightColor",
        name = "Highlight Colour",
        description = "Outline colour for the suggested method and goal items",
        section = goalsSection,
        position = 6
    )
    default Color trainingHighlightColor() {
        return new Color(80, 200, 255, 200);
    }

    @ConfigItem(
        keyName = "autoPauseSkills",
        name = "Auto-Pause Idle Skills",
        description = "<div width='300'>Stop a skill's XP/hr clock when you haven't gained XP in it for a while; it resumes on your next XP",
        section = trackerSection,
        position = 7
    )
    default boolean autoPauseSkills() {
        return true;
    }

    @Range(min = 10, max = 3600)
    @ConfigItem(
        keyName = "autoPauseSeconds",
        name = "Auto-Pause After",
        description = "<div width='300'>Seconds without XP in a skill before it auto-pauses (10 seconds to an hour)",
        section = trackerSection,
        position = 8
    )
    @Units(Units.SECONDS)
    default int autoPauseSeconds() {
        return 10;
    }

    @Range(max
    =
    240)
    @Units(Units.MINUTES)
    @ConfigItem(
        keyName = "dropIdleSkillsAfter",
        name = "Drop Idle Skills After",
        description = "<div width='300'>A skill without a goal leaves the tracker after this long without XP (0: never), and comes back with its session numbers when you gain XP in it again. Skills with a goal and the pinned skill always stay",
        section = trackerSection,
        position = 9
    )
    default int dropIdleSkillsAfter() {
        return 30;
    }

    @ConfigItem(
        keyName = "clearReachedGoals",
        name = "Clear Goals When Reached",
        description = "<div width='300'>A goal clears itself once you reach it (a chat line says so), leaving its skill free for a new one. Off: it stays, showing Done!",
        section = goalsSection,
        position = 8
    )
    default boolean clearReachedGoals() {
        return true;
    }

    @ConfigItem(
        keyName = "showBankedXp",
        name = "Show Banked XP",
        description = "<div width='300'>On a production skill's goal (Cooking, Herblore, Prayer...): the XP in what's in your bank and inventory at your level, the most XP things first. Just a figure: it doesn't change the suggestion. Shows once you've opened your bank",
        section = goalsSection,
        position = 9
    )
    default boolean showBankedXp() {
        return true;
    }

    @ConfigItem(
        keyName = "skillsTabGoalBars",
        name = "Skills Tab Goal Bars",
        description = "<div width='300'>A thin gold bar along the bottom of each skill with a goal on the game's Skills tab, filling up as you get closer to the goal",
        section = goalsSection,
        position = 10
    )
    default boolean skillsTabGoalBars() {
        return true;
    }

    @ConfigItem(
        keyName = "showTrackerItems",
        name = "Show Items Gathered",
        description = "<div width='300'>How many logs, fish and ore you've got and food you've cooked (and burnt) this session, and how many an hour, on the skill's card and the pinned skill. Counted from the game's chat messages",
        section = trackerSection,
        position = 13
    )
    default boolean showTrackerItems() {
        return true;
    }

    @Range(max = 240)
    @Units(Units.MINUTES)
    @ConfigItem(
        keyName = "keepSessionMinutes",
        name = "Keep Session After Restart",
        description = "<div width='300'>When you restart the client and log back in to the same account within this long of logging out (or of your last XP, if the client closed without logging out), the tracker carries on with that session's XP, rates and items instead of starting again (0: always start again)",
        section = trackerSection,
        position = 14
    )
    default int keepSessionMinutes() {
        return 30;
    }

    @ConfigItem(
        keyName = "showMaterialsValue",
        name = "Show Materials Value",
        description = "<div width='300'>On a production skill's goal: the Grand Exchange value of the materials counted in Banked XP (in your bank and inventory). Just a figure. Shows once you've opened your bank",
        section = goalsSection,
        position = 10
    )
    default boolean showMaterialsValue() {
        return true;
    }

    @ConfigItem(
        keyName = "ignoredSkills",
        name = "Ignored Skills",
        description = "Comma-separated list of skills to filter out",
        section = trackerSection,
        position = 10
    )
    default String ignoredSkills() {
        return "";
    }

    @ConfigItem(
        keyName = "voiceLevelUp",
        name = "Voice: Level Up",
        description = "<div width='300'>Say the skill and your new level when you level up (turning it on plays a preview)",
        section = trackerSection,
        position = 11
    )
    default boolean voiceLevelUp() {
        return true;
    }

    @ConfigItem(
        keyName = "chatLevelUp",
        name = "Chat: Level Up",
        description = "<div width='300'>Chat message when you level up (off by default; the game already congratulates you)",
        section = trackerSection,
        position = 12
    )
    default boolean chatLevelUp() {
        return false;
    }

    // ---- Generated by scripts/generate_config_defaults.py: CommonConfig's settings as this plugin's own (don't edit) ----
    @ConfigItem(
        keyName = "voicePersona",
        name = "Voice Persona",
        description = "<div width='300'>Voice used for spoken alerts; changing it plays a sample. Amy, Cori: US and UK women. Alan, Ryan, John: UK and US men. Arctic: a Scottish man. L2 Arctic: a man with a Mandarin Chinese accent. Wise Old Man, TzHaar, Ghost: characters. Random: a different one each alert",
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
        description = "<div width='300'>Speak alerts out loud. Each alert also has its own Voice switch in its section",
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
        description = "<div width='300'>Use the same voice and volume in all of Snuffy's plugins you have: changing them in one changes them in the others (that have this on), and one you install later picks them up. Turn off to give this plugin its own voice",
        section = generalSection,
        position = 3
    )
    @Override
    default boolean syncVoice() {
        return CommonConfig.super.syncVoice();
    }

    @ConfigItem(
        keyName = "sendChatMessages",
        name = "Chat Messages",
        description = "<div width='300'>Also post alerts as in-game chat messages. Each alert also has its own Chat switch in its section",
        section = generalSection,
        position = 4
    )
    @Override
    default boolean sendChatMessages() {
        return CommonConfig.super.sendChatMessages();
    }

    @ConfigItem(
        keyName = "voiceOnlyUnfocused",
        name = "Speak Only When Unfocused",
        description = "<div width='300'>Only speak alerts while the RuneLite window isn't the one you're using (you're in another window); chat messages still post. Like RuneLite's own notifications, which by default don't show while you're in the client",
        section = generalSection,
        position = 5
    )
    @Override
    default boolean voiceOnlyUnfocused() {
        return CommonConfig.super.voiceOnlyUnfocused();
    }

    @ConfigItem(
        keyName = "characterIdleAlert",
        name = "AFK Alert",
        description = "<div width='300'>Alert when you were training (gaining XP), your character stopped, and you haven't clicked or typed since. Banking, shops, the GE and menus never set it off",
        section = idleSection,
        position = 0
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
        name = "AFK Alert After",
        description = "<div width='300'>Seconds of your character doing nothing, with no clicks or key presses, before the AFK alert",
        section = idleSection,
        position = 1
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
        description = "<div width='300'>How many seconds before the idle logout to warn you. Out of combat that's the normal idle timeout (follows RuneLite's Logout Timer plugin); in combat the game only logs you out after 20 minutes without a click or key press",
        section = idleSection,
        position = 4
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
        position = 5
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
        position = 6
    )
    @Units(Units.SECONDS)
    @Override
    default int repeatIdleInterval() {
        return CommonConfig.super.repeatIdleInterval();
    }

    @ConfigItem(
        keyName = "maxIdleRepeats",
        name = "Max Repeats",
        description = "<div width='300'>How many times to repeat the idle logout alert after the first",
        section = idleSection,
        position = 7
    )
    @Override
    default int maxIdleRepeats() {
        return CommonConfig.super.maxIdleRepeats();
    }

    @ConfigItem(
        keyName = "voiceCharacterIdle",
        name = "Voice: Character Idle",
        description = "<div width='300'>When your character has gone AFK (turning it on plays a preview)",
        section = idleSection,
        position = 2
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
        position = 3
    )
    @Override
    default boolean chatCharacterIdle() {
        return CommonConfig.super.chatCharacterIdle();
    }

    @ConfigItem(
        keyName = "voiceIdleLogout",
        name = "Voice: Logout Warning",
        description = "<div width='300'>When you are about to be logged out for inactivity (turning it on plays a preview)",
        section = idleSection,
        position = 8
    )
    @Override
    default boolean voiceIdleLogout() {
        return CommonConfig.super.voiceIdleLogout();
    }

    @ConfigItem(
        keyName = "chatIdleLogout",
        name = "Chat: Logout Warning",
        description = "<div width='300'>Chat message when you are about to be logged out for inactivity",
        section = idleSection,
        position = 9
    )
    @Override
    default boolean chatIdleLogout() {
        return CommonConfig.super.chatIdleLogout();
    }

    @ConfigItem(
        keyName = "sixHourWarning",
        name = "Six-Hour Logout Warning",
        description = "<div width='300'>The game logs you out after six hours online: warn twenty minutes before (from when you logged in or hopped)",
        section = idleSection,
        position = 10
    )
    @Override
    default boolean sixHourWarning() {
        return CommonConfig.super.sixHourWarning();
    }

    @ConfigItem(
        keyName = "voiceSixHour",
        name = "Voice: Six-Hour Logout",
        description = "<div width='300'>Voice line twenty minutes before the six-hour logout (turning it on plays a preview)",
        section = idleSection,
        position = 11
    )
    @Override
    default boolean voiceSixHour() {
        return CommonConfig.super.voiceSixHour();
    }

    @ConfigItem(
        keyName = "chatSixHour",
        name = "Chat: Six-Hour Logout",
        description = "<div width='300'>Chat message twenty minutes before the six-hour logout",
        section = idleSection,
        position = 12
    )
    @Override
    default boolean chatSixHour() {
        return CommonConfig.super.chatSixHour();
    }

    @Range(max
    =
    100)
    @Units(Units.PERCENT)
    @ConfigItem(
        keyName = "runEnergyLowAt",
        name = "Run Energy Low Below",
        description = "<div width='300'>Alert anywhere when your run energy drops below this (0: off). While it's on, the Agility section's own run energy alert stays quiet so you don't hear both",
        section = idleSection,
        position = 13
    )
    @Override
    default int runEnergyLowAt() {
        return CommonConfig.super.runEnergyLowAt();
    }

    @ConfigItem(
        keyName = "voiceRunLow",
        name = "Voice: Run Energy Low",
        description = "<div width='300'>Says when your run energy drops below Run Energy Low Below (turning it on plays a preview)",
        section = idleSection,
        position = 14
    )
    @Override
    default boolean voiceRunLow() {
        return CommonConfig.super.voiceRunLow();
    }

    @ConfigItem(
        keyName = "chatRunLow",
        name = "Chat: Run Energy Low",
        description = "<div width='300'>Chat message when your run energy drops below Run Energy Low Below",
        section = idleSection,
        position = 15
    )
    @Override
    default boolean chatRunLow() {
        return CommonConfig.super.chatRunLow();
    }

    @Range(max
    =
    100)
    @Units(Units.PERCENT)
    @ConfigItem(
        keyName = "runEnergyRestoredAt",
        name = "Run Energy Restored At",
        description = "<div width='300'>Alert when your run energy is back up to this, after it had dropped at least 10 below it (100: full again; 0: off)",
        section = idleSection,
        position = 16
    )
    @Override
    default int runEnergyRestoredAt() {
        return CommonConfig.super.runEnergyRestoredAt();
    }

    @ConfigItem(
        keyName = "voiceRunRestored",
        name = "Voice: Run Energy Restored",
        description = "<div width='300'>Says when your run energy is back up to Run Energy Restored At (turning it on plays a preview)",
        section = idleSection,
        position = 17
    )
    @Override
    default boolean voiceRunRestored() {
        return CommonConfig.super.voiceRunRestored();
    }

    @ConfigItem(
        keyName = "chatRunRestored",
        name = "Chat: Run Energy Restored",
        description = "<div width='300'>Chat message when your run energy is back up to Run Energy Restored At",
        section = idleSection,
        position = 18
    )
    @Override
    default boolean chatRunRestored() {
        return CommonConfig.super.chatRunRestored();
    }

    @ConfigItem(
        keyName = "clueDropAlert",
        name = "Clue Drop Alert",
        description = "<div width='300'>When a clue lands for you: a clue scroll in a monster's drop, or a clue bottle, geode, nest or scroll box from skilling going into your inventory. Chat says which tier",
        section = remindersSection,
        position = 0
    )
    @Override
    default boolean clueDropAlert() {
        return CommonConfig.super.clueDropAlert();
    }

    @ConfigItem(
        keyName = "voiceClueDrop",
        name = "Voice: Clue Drop",
        description = "Voice line when a clue drops (turning it on plays a preview)",
        section = remindersSection,
        position = 1
    )
    @Override
    default boolean voiceClueDrop() {
        return CommonConfig.super.voiceClueDrop();
    }

    @ConfigItem(
        keyName = "chatClueDrop",
        name = "Chat: Clue Drop",
        description = "Chat message when a clue drops, with its tier",
        section = remindersSection,
        position = 2
    )
    @Override
    default boolean chatClueDrop() {
        return CommonConfig.super.chatClueDrop();
    }
    // ---- End of CommonConfig's settings ----
}
