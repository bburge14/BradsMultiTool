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

/**
 * The all-in-one Snuffy's Toolbelt: every plugin's settings in one group ("bradsmultitool", kept so saved
 * settings and goals carry over). RuneLite only shows sections an interface declares itself, so they are all
 * repeated here (MultiToolConfigTest checks none is missing).
 */
@ConfigGroup("bradsmultitool")
public interface BradsMultiToolConfig extends SkillTrackerConfig, PvmConfig, GemCrabConfig, TickConfig, PlannerConfig {

    @ConfigSection(
        name = "About",
        description = "<div width='300'>Snuffy's Toolbelt: every one of Snuffy's plugins in one (don't run it with the separate ones). Clashes (said in chat while they're on): the timer plugins Tree Despawn Timer, MLM Despawn Timer, Calcified Rock Despawn Timer, Daeyalt Mining Timer, Fishing Spot Timer, Fishing Spot Tracker, Barbarian Fishing, Aerial Fishing Timers, Star AFK Timer, Salvaging AFK Timer, Fire Timer, and RuneLite's Hunter and Mining plugins (our matching timers hide for them); Hunter Rumours and Skills Progress; RuneLite's Slayer plugin with its task highlight on; RuneLite's Metronome; RuneLite's Idle Notifier; RuneLite's Daily Task Indicator and Loot Tracker (the Planner). Open the Guide for every feature and how to use it.",
        position = -10
    )
    String aboutSection = "aboutSection";

    @ConfigSection(
        name = "Planner: Today",
        description = "<div width='300'>The Planner's TODAY list: daily rewards to collect (from the game) and your own daily to-dos, and the voice line when there are some to do",
        position = 70,
        closedByDefault = true
    )
    String plannerTodaySection = "plannerTodaySection";

    @ConfigSection(
        name = "Planner: Weekly",
        description = "<div width='300'>The Planner's WEEKLY list: Tears of Guthix, your own weekly to-dos, the Kingdom of Miscellania and the farming contract",
        position = 71,
        closedByDefault = true
    )
    String plannerWeeklySection = "plannerWeeklySection";

    @ConfigSection(
        name = "Planner: Timers",
        description = "<div width='300'>The Planner's TIMERS list: farming patches, compost bins and Hespori, bird houses, and timers of your own, with their ready alerts",
        position = 72,
        closedByDefault = true
    )
    String plannerTimersSection = "plannerTimersSection";

    @ConfigSection(
        name = "Planner: Slayer",
        description = "The Planner's SLAYER list: your task, kills left, points and streak",
        position = 73,
        closedByDefault = true
    )
    String plannerSlayerSection = "plannerSlayerSection";

    @ConfigSection(
        name = "Planner: Drops",
        description = "The Planner's DROPS list: what your kills and other loot gave you",
        position = 74,
        closedByDefault = true
    )
    String plannerDropsSection = "plannerDropsSection";

    @ConfigSection(
        name = "Planner: Notes & To-Do",
        description = "The Planner's NOTES & TO-DO list: a to-do list and a notes box",
        position = 75,
        closedByDefault = true
    )
    String plannerNotesSection = "plannerNotesSection";

    @ConfigSection(
        name = "Tick: Bar",
        description = "The smooth tick bar that fills over each tick (move it with Alt + drag)",
        position = 62,
        closedByDefault = true
    )
    String tickBarSection = "tickBarSection";

    @ConfigSection(
        name = "GemCrab: Colours",
        description = "Colours of the crab, its shell and the tunnel highlights",
        position = 21,
        closedByDefault = true
    )
    String crabColoursSection = "crabColoursSection";

    @ConfigSection(
        name = "PvM: Supply Alerts",
        description = "Running low on ammo, runes or cannonballs",
        position = 16,
        closedByDefault = true
    )
    String supplyAlertsSection = "supplyAlertsSection";

    @ConfigSection(
        name = "Tick: Click Timing",
        description = "How long after the tick each click lands, its sounds, and the ideal click zone",
        position = 64,
        closedByDefault = true
    )
    String clickTimingSection = "clickTimingSection";

    @ConfigSection(
        name = "Tick: Metronome",
        description = "A sound on every tick, early by Play Ahead so you can click on it",
        position = 63,
        closedByDefault = true
    )
    String metronomeSection = "metronomeSection";

    @ConfigSection(
        name = "Tick: Boxes",
        description = "The tick boxes, in a row or moved separately, with half-tick boxes",
        position = 61,
        closedByDefault = true
    )
    String tickBoxesSection = "tickBoxesSection";

    @ConfigSection(
        name = "Tick: Hotkeys",
        description = "<div width='300'>Keys to reset the count, show or hide the tick overlays and change Ticks Per Cycle (none set at first)",
        position = 65,
        closedByDefault = true
    )
    String tickHotkeySection = "tickHotkeySection";

    @ConfigSection(
        name = "Slayer: Alerts",
        description = "<div width='300'>Alerts for your Slayer task: missing item, superiors, halfway, nearly done, complete, next task a milestone, and kill goals reached",
        position = 19,
        closedByDefault = true
    )
    String slayerAlertsSection = "slayerAlertsSection";

    @ConfigSection(
        name = "Slayer: Monsters",
        description = "<div width='300'>Slayer details on monsters, outlines on your task's monsters, and the finish-off marker",
        position = 18,
        closedByDefault = true
    )
    String slayerMonstersSection = "slayerMonstersSection";

    @ConfigSection(
        name = "Skilling: Alerts",
        description = "Inventory full, resource gone and batch complete alerts",
        position = 34,
        closedByDefault = true
    )
    String skillingAlertsSection = "skillingAlertsSection";

    @ConfigSection(
        name = "Skilling: Resource Timers",
        description = "How resource timers look, and stepping aside for other timer plugins; each skill's section turns its timers on",
        position = 33,
        closedByDefault = true
    )
    String resourceTimersSection = "resourceTimersSection";

    @ConfigSection(
        name = "Skilling: Goals",
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
    @Override
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
    @Override
    default boolean showClashes() {
        return true;
    }

    @ConfigSection(
        name = "Tick: Counter",
        description = "<div width='300'>Counts game ticks over your head or in a box you can move, with an optional metronome",
        position = 60,
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
        name = "PvM: Potion Buffs",
        description = "Buff timers on the HUD and running-out / expired alerts",
        position = 12,
        closedByDefault = true
    )
    String buffSection = "buffSection";

    @ConfigSection(
        name = "PvM: Health",
        description = "Low health warnings",
        position = 13,
        closedByDefault = true
    )
    String healthSection = "healthSection";

    @ConfigSection(
        name = "PvM: Prayer",
        description = "Low and empty prayer warnings",
        position = 14,
        closedByDefault = true
    )
    String prayerSection = "prayerSection";

    @ConfigSection(
        name = "PvM: Combat Alerts",
        description = "<div width='300'>Special attack, antifire, poison, stamina, wrong protection prayer and unpotted alerts",
        position = 15,
        closedByDefault = true
    )
    String combatAlertsSection = "combatAlertsSection";

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

    @ConfigSection(
        name = "PvM: Buff Bar",
        description = "<div width='300'>Your buffs and debuffs as a row of icons with timers, like RS3's",
        position = 11,
        closedByDefault = true
    )
    String buffBarSection = "buffBarSection";

    @ConfigSection(
        name = "PvM: HUD",
        description = "Combat HUD and monster hover info for non-crab monsters",
        position = 10,
        closedByDefault = true
    )
    String pvmSection = "pvmSection";

    @ConfigSection(
        name = "Slayer: Card",
        description = "<div width='300'>The Slayer card: your task, kills left, points and streak, supplies used, kill goals, where to go and how to get there, or the master to visit without a task",
        position = 17,
        closedByDefault = true
    )
    String slayerSection = "slayerSection";

    @ConfigSection(
        name = "GemCrab: Crab",
        description = "Crab HUD, radar, alerts and outlines",
        position = 20,
        closedByDefault = true
    )
    String crabSection = "crabSection";

    @ConfigSection(
        name = "Skilling: Hover Cards",
        description = "Hover cards for trees, rocks, fishing spots, stalls and other skilling things",
        position = 32,
        closedByDefault = true
    )
    String skillingSection = "skillingSection";

    @ConfigSection(
        name = "Skilling: Woodcutting",
        description = "Tree timers and alerts",
        position = 40,
        closedByDefault = true
    )
    String woodcuttingSection = "woodcuttingSection";

    @ConfigSection(
        name = "Skilling: Mining",
        description = "Mining timers and alerts",
        position = 41,
        closedByDefault = true
    )
    String miningSection = "miningSection";

    @ConfigSection(
        name = "Skilling: Fishing",
        description = "Fishing spot timers and alerts",
        position = 42,
        closedByDefault = true
    )
    String fishingSection = "fishingSection";

    @ConfigSection(
        name = "Skilling: Hunter",
        description = "Hunter creature info, trap and bird house timers, Hunters' Rumours",
        position = 45,
        closedByDefault = true
    )
    String hunterSection = "hunterSection";

    @ConfigSection(
        name = "Skilling: Agility",
        description = "Agility: obstacle hover, the course card, the Brimhaven Arena card and the run energy alert",
        position = 44,
        closedByDefault = true
    )
    String agilitySection = "agilitySection";

    @ConfigSection(
        name = "Skilling: Other Skills",
        description = "Thieving, Farming, Firemaking, Runecraft, Cooking, Sailing (with its boat alerts) and Smithing (each has its own section in Snuffy's Skilling Helper)",
        position = 46,
        closedByDefault = true
    )
    String otherSkillsSection = "otherSkillsSection";

    @ConfigSection(
        name = "Skilling: Minigames",
        description = "Wintertodt, Tempoross and Guardians of the Rift cards and alerts",
        position = 47,
        closedByDefault = true
    )
    String minigamesSection = "minigamesSection";

    @ConfigSection(
        name = "Skilling: Tracker",
        description = "Sidebar XP tracker",
        position = 30,
        closedByDefault = true
    )
    String trackerSection = "trackerSection";

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
        return SkillTrackerConfig.super.voicePersona();
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
        return SkillTrackerConfig.super.voiceVolume();
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
        return SkillTrackerConfig.super.voiceEnabled();
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
        return SkillTrackerConfig.super.syncVoice();
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
        return SkillTrackerConfig.super.sendChatMessages();
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
        return SkillTrackerConfig.super.voiceOnlyUnfocused();
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
        return SkillTrackerConfig.super.characterIdleAlert();
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
        return SkillTrackerConfig.super.characterIdleSeconds();
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
        return SkillTrackerConfig.super.idleWarningSeconds();
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
        return SkillTrackerConfig.super.repeatIdleAlert();
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
        return SkillTrackerConfig.super.repeatIdleInterval();
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
        return SkillTrackerConfig.super.maxIdleRepeats();
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
        return SkillTrackerConfig.super.voiceCharacterIdle();
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
        return SkillTrackerConfig.super.chatCharacterIdle();
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
        return SkillTrackerConfig.super.voiceIdleLogout();
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
        return SkillTrackerConfig.super.chatIdleLogout();
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
        return SkillTrackerConfig.super.sixHourWarning();
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
        return SkillTrackerConfig.super.voiceSixHour();
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
        return SkillTrackerConfig.super.chatSixHour();
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
        return SkillTrackerConfig.super.runEnergyLowAt();
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
        return SkillTrackerConfig.super.voiceRunLow();
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
        return SkillTrackerConfig.super.chatRunLow();
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
        return SkillTrackerConfig.super.runEnergyRestoredAt();
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
        return SkillTrackerConfig.super.voiceRunRestored();
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
        return SkillTrackerConfig.super.chatRunRestored();
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
        return SkillTrackerConfig.super.clueDropAlert();
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
        return SkillTrackerConfig.super.voiceClueDrop();
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
        return SkillTrackerConfig.super.chatClueDrop();
    }
    // ---- End of CommonConfig's settings ----

    // ---- Generated by scripts/generate_config_defaults.py: BuffConfig's settings as this plugin's own (don't edit) ----
    @ConfigItem(
        keyName = "enableBuffStatus",
        name = "Show Buffs on HUD",
        description = "<div width='300'>Show each active potion buff with its boost and time left (on the PvM HUD and the crab card)",
        section = buffSection,
        position = 0
    )
    @Override
    default boolean enableBuffStatus() {
        return PvmConfig.super.enableBuffStatus();
    }

    @ConfigItem(
        keyName = "flashExpiredBuff",
        name = "Flash When Expired",
        description = "Flash the Buff line on the HUD when buff is expired",
        section = buffSection,
        position = 2
    )
    @Override
    default boolean flashExpiredBuff() {
        return PvmConfig.super.flashExpiredBuff();
    }

    @Range(max
    =
    600)
    @ConfigItem(
        keyName = "buffWarningSeconds",
        name = "Warn Before Ending",
        description = "Warn this many seconds before a buff runs out (0 = off)",
        section = buffSection,
        position = 1
    )
    @Units(Units.SECONDS)
    @Override
    default int buffWarningSeconds() {
        return PvmConfig.super.buffWarningSeconds();
    }

    @ConfigItem(
        keyName = "repeatBuffAlert",
        name = "Repeat Expired Alert",
        description = "Repeat the expired buff announcement",
        section = buffSection,
        position = 3
    )
    @Override
    default boolean repeatBuffAlert() {
        return PvmConfig.super.repeatBuffAlert();
    }

    @ConfigItem(
        keyName = "repeatBuffInterval",
        name = "Repeat Every",
        description = "Seconds between repeat buff alerts",
        section = buffSection,
        position = 4
    )
    @Units(Units.SECONDS)
    @Override
    default int repeatBuffInterval() {
        return PvmConfig.super.repeatBuffInterval();
    }

    @ConfigItem(
        keyName = "maxBuffRepeats",
        name = "Max Repeats",
        description = "<div width='300'>How many times to repeat the expired buff alert after the first",
        section = buffSection,
        position = 5
    )
    @Override
    default int maxBuffRepeats() {
        return PvmConfig.super.maxBuffRepeats();
    }

    @ConfigItem(
        keyName = "voiceBuffLow",
        name = "Voice: Potion Running Out",
        description = "<div width='300'>Shortly before a potion boost runs out (turning it on plays a preview)",
        section = buffSection,
        position = 6
    )
    @Override
    default boolean voiceBuffLow() {
        return PvmConfig.super.voiceBuffLow();
    }

    @ConfigItem(
        keyName = "chatBuffLow",
        name = "Chat: Potion Running Out",
        description = "Chat message shortly before a potion boost runs out",
        section = buffSection,
        position = 7
    )
    @Override
    default boolean chatBuffLow() {
        return PvmConfig.super.chatBuffLow();
    }

    @ConfigItem(
        keyName = "voiceBuffExpired",
        name = "Voice: Potion Expired",
        description = "<div width='300'>When a potion boost has run out (turning it on plays a preview)",
        section = buffSection,
        position = 8
    )
    @Override
    default boolean voiceBuffExpired() {
        return PvmConfig.super.voiceBuffExpired();
    }

    @ConfigItem(
        keyName = "chatBuffExpired",
        name = "Chat: Potion Expired",
        description = "Chat message when a potion boost has run out",
        section = buffSection,
        position = 9
    )
    @Override
    default boolean chatBuffExpired() {
        return PvmConfig.super.chatBuffExpired();
    }
    // ---- End of BuffConfig's settings ----

    // ---- The Toolbelt keeps the smaller skills together in Other Skills (the Skilling Helper gives each a section)

    @ConfigItem(
        keyName = "coinPouchAlert",
        name = "Thieving: Coin Pouch Alert",
        description = "<div width='300'>Alert when your coin pouches reach the most you can hold (28, more with the Ardougne Diary)",
        section = otherSkillsSection,
        position = 0
    )
    @Override
    default boolean coinPouchAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceCoinPouch",
        name = "Thieving: Voice: Coin Pouches Full",
        description = "<div width='300'>When your coin pouches are full (turning it on plays a preview)",
        section = otherSkillsSection,
        position = 1
    )
    @Override
    default boolean voiceCoinPouch() {
        return true;
    }

    @ConfigItem(
        keyName = "chatCoinPouch",
        name = "Thieving: Chat: Coin Pouches Full",
        description = "Chat message when your coin pouches are full",
        section = otherSkillsSection,
        position = 2
    )
    @Override
    default boolean chatCoinPouch() {
        return true;
    }

    @ConfigItem(
        keyName = "showFarmingHover",
        name = "Farming: Patch Hover",
        description = "<div width='300'>Hovering a farming patch shows what's growing, its stage, roughly when it's grown, compost, watering, protection and what you can plant",
        section = otherSkillsSection,
        position = 3
    )
    @Override
    default boolean showFarmingHover() {
        return true;
    }

    @ConfigItem(
        keyName = "fireTimers",
        name = "Firemaking: Fire Timers",
        description = "<div width='300'>A timer on each fire you cook on. Fires go out at a random time 60-119 seconds after they're lit, so it shows that window: green until the earliest it can go out, then a red ring filling up to the latest (hover it for the times) Always: as soon as you're near; While training: once you're training the skill (an XP drop in the last two minutes); Off: never.",
        section = otherSkillsSection,
        position = 8
    )
    @Override
    default SkillTrackerConfig.TimerShow showFireTimer() {
        return SkillTrackerConfig.TimerShow.OFF;
    }

    @ConfigItem(
        keyName = "campfireTimers",
        name = "Firemaking: Campfire Timers",
        description = "<div width='300'>A timer on each Forester's Campfire showing how long it has left: set by the log that made it, topped up by each log you add, corrected when you Check it Always: as soon as you're near; While training: once you're training the skill (an XP drop in the last two minutes); Off: never.",
        section = otherSkillsSection,
        position = 9
    )
    @Override
    default SkillTrackerConfig.TimerShow showCampfireTimer() {
        return SkillTrackerConfig.TimerShow.TRAINING;
    }

    @ConfigItem(
        keyName = "showRunecraftHover",
        name = "Runecraft: Altar Hover",
        description = "<div width='300'>Hovering an altar shows the rune, level, XP and how many runes you make per essence",
        section = otherSkillsSection,
        position = 10
    )
    @Override
    default boolean showRunecraftHover() {
        return true;
    }

    @ConfigItem(
        keyName = "showCookingHover",
        name = "Cooking: Range / Fire Hover",
        description = "<div width='300'>Hovering a range or fire shows each raw food you are carrying: its level, XP and burn level",
        section = otherSkillsSection,
        position = 12
    )
    @Override
    default boolean showCookingHover() {
        return true;
    }

    @ConfigItem(
        keyName = "showSailingHover",
        name = "Sailing: Hover",
        description = "<div width='300'>Hover cards for sea charting spots (task, level, XP), shipwrecks (level, salvage XP, best hook for you), trawling shoals (Fishing level, fish, depth, bait), sea monsters (HP, safe keel, bounty parts) and your current duck",
        section = otherSkillsSection,
        position = 13
    )
    @Override
    default boolean showSailingHover() {
        return true;
    }

    @ConfigItem(
        keyName = "shipwreckTimers",
        name = "Sailing: Shipwreck Timers",
        description = "<div width='300'>A timer on each Sailing shipwreck you saw surface, showing how long it has left before it sinks Always: as soon as you're near; While training: once you're training the skill (an XP drop in the last two minutes); Off: never.",
        section = otherSkillsSection,
        position = 14
    )
    @Override
    default SkillTrackerConfig.TimerShow showShipwreckTimer() {
        return SkillTrackerConfig.TimerShow.TRAINING;
    }

    @ConfigItem(
        keyName = "voiceBoatHealth",
        name = "Sailing: Voice: Boat Health Low",
        description = "<div width='300'>On your boat, when its hitpoints drop below Boat Health Low At, once until it's repaired (turning it on plays a preview)",
        section = otherSkillsSection,
        position = 15
    )
    @Override
    default boolean voiceBoatHealth() {
        return true;
    }

    @ConfigItem(
        keyName = "chatBoatHealth",
        name = "Sailing: Chat: Boat Health Low",
        description = "Chat message when your boat's hitpoints are low",
        section = otherSkillsSection,
        position = 16
    )
    @Override
    default boolean chatBoatHealth() {
        return true;
    }

    @Range(min = 1, max = 99)
    @Units(Units.PERCENT)
    @ConfigItem(
        keyName = "boatHealthPercent",
        name = "Sailing: Boat Health Low At",
        description = "<div width='300'>Boat hitpoints (percent of its most) below which Boat Health Low warns you",
        section = otherSkillsSection,
        position = 17
    )
    @Override
    default int boatHealthPercent() {
        return 30;
    }

    @ConfigItem(
        keyName = "voiceSailTrim",
        name = "Sailing: Voice: Trim Sails",
        description = "<div width='300'>On your boat, when a gust opens the window to trim the sails for a burst of speed (turning it on plays a preview)",
        section = otherSkillsSection,
        position = 18
    )
    @Override
    default boolean voiceSailTrim() {
        return true;
    }

    @ConfigItem(
        keyName = "chatSailTrim",
        name = "Sailing: Chat: Trim Sails",
        description = "Chat message when you can trim the sails",
        section = otherSkillsSection,
        position = 19
    )
    @Override
    default boolean chatSailTrim() {
        return true;
    }

    @ConfigItem(
        keyName = "showSmithingHover",
        name = "Smithing: Anvil / Furnace Hover",
        description = "<div width='300'>Hovering an anvil or furnace shows what you can make from the bars or ores you are carrying",
        section = otherSkillsSection,
        position = 20
    )
    @Override
    default boolean showSmithingHover() {
        return true;
    }
}
