package com.snuffys.multitool;

import java.awt.Color;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

/**
 * Snuffy's Planner: the sidebar tab for things on a clock and your account's to-do list. Each of its sections can
 * be hidden here; what you put in it (your own dailies, timers, to-do list, notes, drops) is saved with your
 * RuneScape profile in the shared group, so the Toolbelt and the Planner show the same.
 */
@ConfigGroup("snuffysplanner")
public interface PlannerConfig extends CommonConfig {

    @ConfigSection(
        name = "About",
        description = "<div width='300'>Snuffy's Planner: a sidebar tab with your dailies and weeklies, farming, bird house and your own timers, your Slayer task, a drop tracker, and a to-do list with notes. Clashes (a warning icon among the info boxes while they're on): RuneLite's Loot Tracker and Idle Notifier. Open the Guide for every feature and how to use it.",
        position = -10
    )
    String aboutSection = "aboutSection";

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
        description = "<div width='300'>While another plugin that clashes with this one is on: a warning icon among RuneLite's info boxes (hover it for the list). Off: no icon",
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

    // ---- The Planner's own sections ----

    @ConfigSection(
        name = "Today",
        description = "<div width='300'>The TODAY list: your own daily to-dos (examples show until you add one), and the voice line when there are some to do",
        position = 70,
        closedByDefault = true
    )
    String plannerTodaySection = "plannerTodaySection";

    @ConfigSection(
        name = "Weekly",
        description = "<div width='300'>The WEEKLY list: your own weekly to-dos (examples show until you add one)",
        position = 71,
        closedByDefault = true
    )
    String plannerWeeklySection = "plannerWeeklySection";

    @ConfigSection(
        name = "Timers",
        description = "<div width='300'>The TIMERS list: farming patches, compost bins and Hespori, bird houses, and timers of your own, with their ready alerts",
        position = 72,
        closedByDefault = true
    )
    String plannerTimersSection = "plannerTimersSection";

    @ConfigSection(
        name = "Slayer",
        description = "The SLAYER list: your task, kills left, points and streak",
        position = 73,
        closedByDefault = true
    )
    String plannerSlayerSection = "plannerSlayerSection";

    @ConfigSection(
        name = "Drops",
        description = "The DROPS list: what your kills and other loot gave you",
        position = 74,
        closedByDefault = true
    )
    String plannerDropsSection = "plannerDropsSection";

    @ConfigSection(
        name = "Notes & To-Do",
        description = "The NOTES & TO-DO list: a to-do list and a notes box",
        position = 75,
        closedByDefault = true
    )
    String plannerNotesSection = "plannerNotesSection";

    @ConfigSection(
        name = "Calculator",
        description = "<div width='300'>The CALCULATOR tab: actions to your target level with XP bonuses, time, Grand Exchange cost and what your bank gets you",
        position = 76,
        closedByDefault = true
    )
    String plannerCalculatorSection = "plannerCalculatorSection";

    // ---- Today ----

    @ConfigItem(
        keyName = "plannerShowToday",
        name = "Show Today",
        description = "<div width='300'>The TODAY list in the Planner tab, with the time to the daily reset (00:00 UTC)",
        section = plannerTodaySection,
        position = 0
    )
    default boolean plannerShowToday() {
        return true;
    }

    @ConfigItem(
        keyName = "plannerDailiesAlert",
        name = "Dailies To Do Alert",
        description = "<div width='300'>At login and at the daily reset, one voice line if anything on TODAY is left to do (nothing in chat unless Chat: Dailies is on)",
        section = plannerTodaySection,
        position = 1
    )
    default boolean plannerDailiesAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceDailies",
        name = "Voice: Dailies",
        description = "<div width='300'>Says \"You have dailies to do\" (turning it on plays a preview)",
        section = plannerTodaySection,
        position = 2
    )
    default boolean voiceDailies() {
        return true;
    }

    @ConfigItem(
        keyName = "chatDailies",
        name = "Chat: Dailies",
        description = "<div width='300'>A line in the split private chat (where friends' logins show; it fades after a while) saying how many dailies are left",
        section = plannerTodaySection,
        position = 3
    )
    default boolean chatDailies() {
        return true;
    }

    // ---- Weekly ----

    @ConfigItem(
        keyName = "plannerShowWeekly",
        name = "Show Weekly",
        description = "<div width='300'>The WEEKLY list in the Planner tab, with the time to the weekly reset (Wednesday 00:00 UTC)",
        section = plannerWeeklySection,
        position = 0
    )
    default boolean plannerShowWeekly() {
        return true;
    }

    @ConfigItem(
        keyName = "plannerWeekliesAlert",
        name = "Weeklies To Do Alert",
        description = "<div width='300'>At login and at the weekly reset, one voice line if anything on WEEKLY is left to do (nothing in chat unless Chat: Weeklies is on)",
        section = plannerWeeklySection,
        position = 1
    )
    default boolean plannerWeekliesAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceWeeklies",
        name = "Voice: Weeklies",
        description = "<div width='300'>Says \"You have weeklies to do\" (turning it on plays a preview)",
        section = plannerWeeklySection,
        position = 2
    )
    default boolean voiceWeeklies() {
        return true;
    }

    @ConfigItem(
        keyName = "chatWeeklies",
        name = "Chat: Weeklies",
        description = "<div width='300'>A line in the split private chat (it fades after a while) saying how many weeklies are left",
        section = plannerWeeklySection,
        position = 3
    )
    default boolean chatWeeklies() {
        return true;
    }

    // ---- Timers ----

    @ConfigItem(
        keyName = "plannerShowTimers",
        name = "Show Timers",
        description = "<div width='300'>The TIMERS list in the Planner tab: bird houses, your own timers (and the farming patches, below)",
        section = plannerTimersSection,
        position = 0
    )
    default boolean plannerShowTimers() {
        return true;
    }

    @ConfigItem(
        keyName = "farmingPatchList",
        name = "Farming Patch List",
        description = "<div width='300'>Every farming patch, compost bin and Hespori patch you've been to with something in it, what's in it and when it's grown (the game only tells us a patch's state while you're near it, so each is as you last saw it; a ~ means it may be out by one growth cycle until a patch has been seen growing). Right-click one to forget it",
        section = plannerTimersSection,
        position = 1
    )
    default boolean farmingPatchList() {
        return true;
    }

    @ConfigItem(
        keyName = "farmingReadyAlert",
        name = "Patches Ready Alert",
        description = "<div width='300'>Says when patches you've planted are grown, naming them in chat (once per crop)",
        section = plannerTimersSection,
        position = 2
    )
    default boolean farmingReadyAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceFarmingReady",
        name = "Voice: Patches Ready",
        description = "<div width='300'>Voice line when your farming patches are grown (turning it on plays a preview)",
        section = plannerTimersSection,
        position = 3
    )
    default boolean voiceFarmingReady() {
        return true;
    }

    @ConfigItem(
        keyName = "chatFarmingReady",
        name = "Chat: Patches Ready",
        description = "<div width='300'>A line in the chat naming the farming patches that are grown",
        section = plannerTimersSection,
        position = 4
    )
    default boolean chatFarmingReady() {
        return true;
    }

    @ConfigItem(
        keyName = "birdhouseAlert",
        name = "Bird Houses Ready Alert",
        description = "Alert when the bird houses you seeded are full (50 minutes)",
        section = plannerTimersSection,
        position = 5
    )
    default boolean birdhouseAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceBirdhouses",
        name = "Voice: Bird Houses Ready",
        description = "<div width='300'>When your bird houses are ready (turning it on plays a preview)",
        section = plannerTimersSection,
        position = 6
    )
    default boolean voiceBirdhouses() {
        return true;
    }

    @ConfigItem(
        keyName = "chatBirdhouses",
        name = "Chat: Bird Houses Ready",
        description = "<div width='300'>A line in the chat when your bird houses are ready",
        section = plannerTimersSection,
        position = 7
    )
    default boolean chatBirdhouses() {
        return true;
    }

    /** How your own timers say they're done: the voice line, or a game sound. */
    enum TimerSound {
        VOICE("Voice line", -1),
        BELL("Bell", net.runelite.api.SoundEffectID.TOWN_CRIER_BELL_DING),
        CHIME("Chime", net.runelite.api.SoundEffectID.GE_ADD_OFFER_DINGALING),
        ANVIL("Anvil", net.runelite.api.SoundEffectID.SMITH_ANVIL_TINK),
        BOOP("Boop", net.runelite.api.SoundEffectID.UI_BOOP),
        DOOR("Door", net.runelite.api.SoundEffectID.CLOSE_DOOR);

        private final String label;
        final int id;

        TimerSound(String label, int id) {
            this.label = label;
            this.id = id;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    @ConfigItem(
        keyName = "timerSound",
        name = "Ready Sound",
        description = "<div width='300'>How the Timers tab tells you something's done (farming patches, bird houses, and your timers left on Default: each timer can pick its own when you add or edit it): its voice line, or a game sound: Bell, Chime, Anvil, Boop or Door (at the game's Sound Effects volume). Picking one plays it",
        section = plannerTimersSection,
        position = 8
    )
    default TimerSound timerSound() {
        return TimerSound.VOICE;
    }

    @Range(min = 1, max = 5)
    @ConfigItem(
        keyName = "timerSoundRepeats",
        name = "Times",
        description = "<div width='300'>How many times the Ready Sound plays (a sound only; the voice line is said once)",
        section = plannerTimersSection,
        position = 9
    )
    default int timerSoundRepeats() {
        return 3;
    }

    @ConfigItem(
        keyName = "voiceTimerDone",
        name = "Voice: Timer Done",
        description = "<div width='300'>Says \"Your timer is done\" when one of your own timers ends (turning it on plays a preview)",
        section = plannerTimersSection,
        position = 10
    )
    default boolean voiceTimerDone() {
        return true;
    }

    @ConfigItem(
        keyName = "chatTimerDone",
        name = "Chat: Timer Done",
        description = "Chat message naming the timer that ended",
        section = plannerTimersSection,
        position = 11
    )
    default boolean chatTimerDone() {
        return true;
    }

    // ---- Slayer ----

    @ConfigItem(
        keyName = "plannerShowSlayer",
        name = "Show Slayer",
        description = "<div width='300'>The SLAYER list in the Planner tab: your task and kills left (and Konar's area), points and streak, read from the game",
        section = plannerSlayerSection,
        position = 0
    )
    default boolean plannerShowSlayer() {
        return true;
    }

    // ---- Drops ----

    @ConfigItem(
        keyName = "plannerShowDrops",
        name = "Show Drops",
        description = "<div width='300'>The DROPS list in the Planner tab: each monster or chest with its kills or opens and Grand Exchange value, and your recent drops. Click one for its own page: every item it has given with the drop rate you've seen, its best drop, kills since a valuable drop, and this session beside all time",
        section = plannerDropsSection,
        position = 0
    )
    default boolean plannerShowDrops() {
        return true;
    }

    @ConfigItem(
        keyName = "plannerOtherLoot",
        name = "Count Other Loot",
        description = "<div width='300'>Also count chests, clue caskets, raids and other loot that isn't a monster's drop. These come from RuneLite's Loot Tracker, so they're only counted while it's on",
        section = plannerDropsSection,
        position = 1
    )
    default boolean plannerOtherLoot() {
        return true;
    }

    @Units(" gp")
    @ConfigItem(
        keyName = "plannerValuableDrop",
        name = "Valuable Drop From",
        description = "<div width='300'>Drops worth at least this much (Grand Exchange) show in Valuable Drop Colour, and a monster's page counts its kills since one",
        section = plannerDropsSection,
        position = 2
    )
    default int plannerValuableDrop() {
        return 100_000;
    }

    @ConfigItem(
        keyName = "plannerValuableColor",
        name = "Valuable Drop Colour",
        description = "<div width='300'>The colour of valuable drops in the recent list and on a monster's page",
        section = plannerDropsSection,
        position = 3
    )
    default Color plannerValuableColor() {
        return new Color(255, 176, 0);
    }

    @Range(min = 1, max = 50)
    @ConfigItem(
        keyName = "plannerRecentDrops",
        name = "Recent Drops Shown",
        description = "How many of your latest drops the recent list shows",
        section = plannerDropsSection,
        position = 4
    )
    default int plannerRecentDrops() {
        return 10;
    }

    // ---- Notes & To-Do ----

    @ConfigItem(
        keyName = "plannerShowNotes",
        name = "Show Notes & To-Do",
        description = "<div width='300'>The NOTES & TO-DO list in the Planner tab: a to-do list (add, tick, move, delete) and a free-text notes box",
        section = plannerNotesSection,
        position = 0
    )
    default boolean plannerShowNotes() {
        return true;
    }

    // ---- Calculator ----

    @ConfigItem(
        keyName = "plannerShowCalculator",
        name = "Show Calculator",
        description = "<div width='300'>The CALCULATOR tab in the Planner: pick a skill and a target (your goal, or the next level) and see every action with its XP (with the bonuses you tick), how many to the target, the time where the wiki's training guides give an XP rate, the Grand Exchange cost and worth of what you make or gather, what you own of it, and in Banked view the XP and level your bank and inventory get you. Plan view: a plan for the skill, milestones each with their own method, built by hand or from the wiki's suggestions, with the XP, time and cost of each and in all. Set as goal sets the skill's goal like the Skilling Helper's Set Goal; with the Skilling Helper installed, Export to tracker gives it the plan. Made from RuneLite's own Skill Calculator; information only",
        section = plannerCalculatorSection,
        position = 0
    )
    default boolean plannerShowCalculator() {
        return true;
    }

    @ConfigItem(
        keyName = "plannerCalcPrices",
        name = "Show GE Prices",
        description = "<div width='300'>On the Calculator's cards and plan: what the materials cost to reach your target (red) and what the products are worth (green), with the net gp per XP, at Grand Exchange prices; in Banked view the value of what you'd use. Just figures",
        section = plannerCalculatorSection,
        position = 1
    )
    default boolean plannerCalcPrices() {
        return true;
    }

    @ConfigItem(
        keyName = "plannerCalcHideMembers",
        name = "Hide Members On Free Worlds",
        description = "<div width='300'>On a free world, leave members-only actions out of the Calculator (as RuneLite's own Skill Calculator does), with a note saying so. Logged out, everything shows",
        section = plannerCalculatorSection,
        position = 2
    )
    default boolean plannerCalcHideMembers() {
        return true;
    }

    @ConfigItem(
        keyName = "plannerPlanOverlay",
        name = "Plan Overlay",
        description = "<div width='300'>A card on the game screen for the plan of the skill you're training (the plan open in the Calculator): the milestone you're on, its method, a bar to its end with the XP and actions left, the next milestone and your materials for this one. Only when the Skilling Helper isn't installed: its pinned skill card shows the plan then",
        section = plannerCalculatorSection,
        position = 3
    )
    default boolean plannerPlanOverlay() {
        return true;
    }

    @ConfigItem(
        keyName = "chatPlanMilestone",
        name = "Chat: Milestone Done",
        description = "<div width='300'>A game chat message when you finish a milestone of your plan (the one open in the Calculator), naming the next one, and when you reach the plan's goal",
        section = plannerCalculatorSection,
        position = 4
    )
    default boolean chatPlanMilestone() {
        return true;
    }

    @ConfigItem(
        keyName = "compactOverlays",
        name = "Compact Overlays",
        description = "<div width='300'>The plan overlay shows only the essentials (the plan, the milestone's bar and the actions left). Off: everything</div>",
        section = generalSection,
        position = 90
    )
    default boolean compactOverlays() {
        return false;
    }

    // ---- Generated by scripts/generate_config_defaults.py: CommonConfig's settings as this plugin's own (don't edit) ----
    @ConfigItem(
        keyName = "voicePersona",
        name = "Voice Persona",
        description = "<div width='300'>Voice used for spoken alerts; changing it plays a sample. Amy, Cori: US and UK women. Alan, John: UK and US men. Random: a different one each alert",
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
