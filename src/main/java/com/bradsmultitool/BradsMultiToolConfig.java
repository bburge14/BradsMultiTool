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
public interface BradsMultiToolConfig extends SkillTrackerConfig, PvmConfig, GemCrabConfig, TickConfig {


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
        description = "Speak alerts out loud. Each alert also has its own Voice switch in its section",
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
        description = "Use the same voice and volume in all of Snuffy's plugins you have: changing them in one changes them in the others (that have this on), and one you install later picks them up. Turn off to give this plugin its own voice",
        section = generalSection,
        position = 4
    )
    @Override
    default boolean syncVoice() {
        return SkillTrackerConfig.super.syncVoice();
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
        return SkillTrackerConfig.super.sendChatMessages();
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
        name = "Idle Alert After",
        description = "Seconds of your character doing nothing, with no clicks or key presses, before the AFK alert",
        section = idleSection,
        position = 32
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
        description = "How many seconds before the idle logout to warn you. Out of combat that's the normal idle timeout (follows RuneLite's Logout Timer plugin); in combat the game only logs you out after 20 minutes without a click or key press",
        section = idleSection,
        position = 34
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
        position = 36
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
        position = 37
    )
    @Units(Units.SECONDS)
    @Override
    default int repeatIdleInterval() {
        return SkillTrackerConfig.super.repeatIdleInterval();
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
        return SkillTrackerConfig.super.maxIdleRepeats();
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
        return SkillTrackerConfig.super.voiceCharacterIdle();
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
        return SkillTrackerConfig.super.chatCharacterIdle();
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
        return SkillTrackerConfig.super.voiceIdleLogout();
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
        return SkillTrackerConfig.super.chatIdleLogout();
    }
    // ---- End of CommonConfig's settings ----

    // ---- Generated by scripts/generate_config_defaults.py: BuffConfig's settings as this plugin's own (don't edit) ----
    @ConfigItem(
        keyName = "enableBuffStatus",
        name = "Show Buffs on HUD",
        description = "Show each active potion buff with its boost and time left (on the PvM HUD and the crab card)",
        section = buffSection,
        position = 5
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
        position = 6
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
        position = 7
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
        position = 10
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
        position = 11
    )
    @Units(Units.SECONDS)
    @Override
    default int repeatBuffInterval() {
        return PvmConfig.super.repeatBuffInterval();
    }

    @ConfigItem(
        keyName = "maxBuffRepeats",
        name = "Max Repeats",
        description = "How many times to repeat the expired buff alert after the first",
        section = buffSection,
        position = 12
    )
    @Override
    default int maxBuffRepeats() {
        return PvmConfig.super.maxBuffRepeats();
    }

    @ConfigItem(
        keyName = "voiceBuffLow",
        name = "Voice: Potion Running Out",
        description = "Shortly before a potion boost runs out (turning it on plays a preview)",
        section = buffSection,
        position = 13
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
        position = 14
    )
    @Override
    default boolean chatBuffLow() {
        return PvmConfig.super.chatBuffLow();
    }

    @ConfigItem(
        keyName = "voiceBuffExpired",
        name = "Voice: Potion Expired",
        description = "When a potion boost has run out (turning it on plays a preview)",
        section = buffSection,
        position = 15
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
        position = 16
    )
    @Override
    default boolean chatBuffExpired() {
        return PvmConfig.super.chatBuffExpired();
    }
    // ---- End of BuffConfig's settings ----
}
