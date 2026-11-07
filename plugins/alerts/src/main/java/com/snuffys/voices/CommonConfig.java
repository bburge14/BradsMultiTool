package com.snuffys.voices;

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

/** Settings every one of Snuffy's plugins has: voice and chat (General) and the idle warnings. */
public interface CommonConfig extends Config {

    @ConfigSection(
        name = "General",
        description = "Voice and chat settings shared by all alerts",
        position = 0
    )
    String generalSection = "generalSection";

    @ConfigItem(
        keyName = "voicePersona",
        name = "Voice Persona",
        description = "<div width='300'>Voice used for spoken alerts; changing it plays a sample. Amy, Cori: US and UK women. Alan, John: UK and US men. Random: a different one each alert",
        section = generalSection,
        position = 1
    )
    default VoiceAlertService.VoicePersona voicePersona() {
        return VoiceAlertService.VoicePersona.AMY;
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
    default int voiceVolume() {
        return 80;
    }

    @ConfigItem(
        keyName = "voiceEnabled",
        name = "Voice Alerts",
        description = "<div width='300'>Speak alerts out loud. Each alert also has its own Voice switch in its section",
        section = generalSection,
        position = 0
    )
    default boolean voiceEnabled() {
        return true;
    }

    @ConfigItem(
        keyName = "syncVoice",
        name = "Same Voice For All",
        description = "<div width='300'>Use the same voice and volume in all of Snuffy's plugins you have: changing them in one changes them in the others (that have this on), and one you install later picks them up. Turn off to give this plugin its own voice",
        section = generalSection,
        position = 3
    )
    default boolean syncVoice() {
        return true;
    }

    @ConfigItem(
        keyName = "sendChatMessages",
        name = "Chat Messages",
        description = "<div width='300'>Also post alerts as in-game chat messages. Each alert also has its own Chat switch in its section",
        section = generalSection,
        position = 4
    )
    default boolean sendChatMessages() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceOnlyUnfocused",
        name = "Speak Only When Unfocused",
        description = "<div width='300'>Only speak alerts while the RuneLite window isn't the one you're using (you're in another window); chat messages still post. Like RuneLite's own notifications, which by default don't show while you're in the client",
        section = generalSection,
        position = 5
    )
    default boolean voiceOnlyUnfocused() {
        return false;
    }

    @ConfigSection(
        name = "Idle",
        description = "Idle character, idle and six-hour logout warnings, and run energy alerts",
        position = 5,
        closedByDefault = true
    )
    String idleSection = "idleSection";

    @ConfigItem(
        keyName = "characterIdleAlert",
        name = "AFK Alert",
        description = "<div width='300'>Alert when you were training (gaining XP), your character stopped, and you haven't clicked or typed since. Banking, shops, the GE and menus never set it off",
        section = idleSection,
        position = 0
    )
    default boolean characterIdleAlert() {
        return true;
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
    default int characterIdleSeconds() {
        return 10;
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
    default int idleWarningSeconds() {
        return 30;
    }

    @ConfigItem(
        keyName = "repeatIdleAlert",
        name = "Repeat Logout Warning",
        description = "Repeat the idle logout announcement until you move",
        section = idleSection,
        position = 5
    )
    default boolean repeatIdleAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "repeatIdleInterval",
        name = "Repeat Every",
        description = "Seconds between repeat idle logout alerts",
        section = idleSection,
        position = 6
    )
    @Units(Units.SECONDS)
    default int repeatIdleInterval() {
        return 10;
    }

    @ConfigItem(
        keyName = "maxIdleRepeats",
        name = "Max Repeats",
        description = "<div width='300'>How many times to repeat the idle logout alert after the first",
        section = idleSection,
        position = 7
    )
    default int maxIdleRepeats() {
        return 2;
    }

    @ConfigItem(
        keyName = "voiceCharacterIdle",
        name = "Voice: Character Idle",
        description = "<div width='300'>When your character has gone AFK (turning it on plays a preview)",
        section = idleSection,
        position = 2
    )
    default boolean voiceCharacterIdle() {
        return true;
    }

    @ConfigItem(
        keyName = "chatCharacterIdle",
        name = "Chat: Character Idle",
        description = "Chat message when your character has gone AFK",
        section = idleSection,
        position = 3
    )
    default boolean chatCharacterIdle() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceIdleLogout",
        name = "Voice: Logout Warning",
        description = "<div width='300'>When you are about to be logged out for inactivity (turning it on plays a preview)",
        section = idleSection,
        position = 8
    )
    default boolean voiceIdleLogout() {
        return true;
    }

    @ConfigItem(
        keyName = "chatIdleLogout",
        name = "Chat: Logout Warning",
        description = "<div width='300'>Chat message when you are about to be logged out for inactivity",
        section = idleSection,
        position = 9
    )
    default boolean chatIdleLogout() {
        return true;
    }

    @ConfigItem(
        keyName = "sixHourWarning",
        name = "Six-Hour Logout Warning",
        description = "<div width='300'>The game logs you out after six hours online: warn twenty minutes before (from when you logged in or hopped)",
        section = idleSection,
        position = 10
    )
    default boolean sixHourWarning() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceSixHour",
        name = "Voice: Six-Hour Logout",
        description = "<div width='300'>Voice line twenty minutes before the six-hour logout (turning it on plays a preview)",
        section = idleSection,
        position = 11
    )
    default boolean voiceSixHour() {
        return true;
    }

    @ConfigItem(
        keyName = "chatSixHour",
        name = "Chat: Six-Hour Logout",
        description = "<div width='300'>Chat message twenty minutes before the six-hour logout",
        section = idleSection,
        position = 12
    )
    default boolean chatSixHour() {
        return true;
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
    default int runEnergyLowAt() {
        return 0;
    }

    @ConfigItem(
        keyName = "voiceRunLow",
        name = "Voice: Run Energy Low",
        description = "<div width='300'>Says when your run energy drops below Run Energy Low Below (turning it on plays a preview)",
        section = idleSection,
        position = 14
    )
    default boolean voiceRunLow() {
        return true;
    }

    @ConfigItem(
        keyName = "chatRunLow",
        name = "Chat: Run Energy Low",
        description = "<div width='300'>Chat message when your run energy drops below Run Energy Low Below",
        section = idleSection,
        position = 15
    )
    default boolean chatRunLow() {
        return true;
    }

    @Range(max
    =
    100)
    @Units(Units.PERCENT)
    @ConfigItem(
        keyName = "runEnergyRestoredAt",
        name = "Run Restored At",
        description = "<div width='300'>Alert when your run energy is back up to this, after it had dropped at least 10 below it (100: full again; 0: off)",
        section = idleSection,
        position = 16
    )
    default int runEnergyRestoredAt() {
        return 0;
    }

    @ConfigItem(
        keyName = "voiceRunRestored",
        name = "Voice: Run Energy Restored",
        description = "<div width='300'>Says when your run energy is back up to Run Energy Restored At (turning it on plays a preview)",
        section = idleSection,
        position = 17
    )
    default boolean voiceRunRestored() {
        return true;
    }

    @ConfigItem(
        keyName = "chatRunRestored",
        name = "Chat: Run Energy Restored",
        description = "<div width='300'>Chat message when your run energy is back up to Run Energy Restored At",
        section = idleSection,
        position = 18
    )
    default boolean chatRunRestored() {
        return true;
    }

    @ConfigSection(
        name = "Reminders",
        description = "Clue drop alerts",
        position = 6,
        closedByDefault = true
    )
    String remindersSection = "remindersSection";

    @ConfigItem(
        keyName = "clueDropAlert",
        name = "Clue Drop Alert",
        description = "<div width='300'>When a clue lands for you: a clue scroll in a monster's drop, or a clue bottle, geode, nest or scroll box from skilling going into your inventory. Chat says which tier",
        section = remindersSection,
        position = 0
    )
    default boolean clueDropAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceClueDrop",
        name = "Voice: Clue Drop",
        description = "Voice line when a clue drops (turning it on plays a preview)",
        section = remindersSection,
        position = 1
    )
    default boolean voiceClueDrop() {
        return true;
    }

    @ConfigItem(
        keyName = "chatClueDrop",
        name = "Chat: Clue Drop",
        description = "Chat message when a clue drops, with its tier",
        section = remindersSection,
        position = 2
    )
    default boolean chatClueDrop() {
        return true;
    }
}
