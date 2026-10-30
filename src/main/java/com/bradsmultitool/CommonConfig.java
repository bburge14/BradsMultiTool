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
        description = "Voice used for spoken alerts; changing it plays a sample. Amy, Cori: US and UK women. Alan, Ryan, John: UK and US men. Arctic: a Scottish man. L2 Arctic: a man with a Mandarin Chinese accent. Wise Old Man, TzHaar, Ghost: characters. Random: a different one each alert",
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
        description = "Speak alerts out loud. Each alert also has its own Voice switch in its section",
        section = generalSection,
        position = 0
    )
    default boolean voiceEnabled() {
        return true;
    }

    @ConfigItem(
        keyName = "sendChatMessages",
        name = "Chat Messages",
        description = "Also post alerts as in-game chat messages. Each alert also has its own Chat switch in its section",
        section = generalSection,
        position = 3
    )
    default boolean sendChatMessages() {
        return true;
    }

    @ConfigSection(
        name = "Idle",
        description = "Idle character and idle logout warnings",
        position = 30,
        closedByDefault = true
    )
    String idleSection = "idleSection";

    @ConfigItem(
        keyName = "characterIdleAlert",
        name = "AFK Alert",
        description = "Alert when you were training (gaining XP), your character stopped, and you haven't clicked or typed since. Banking, shops, the GE and menus never set it off",
        section = idleSection,
        position = 31
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
        name = "Idle Alert After",
        description = "Seconds of your character doing nothing, with no clicks or key presses, before the AFK alert",
        section = idleSection,
        position = 32
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
        description = "How many seconds before the idle logout to warn you. Out of combat that's the normal idle timeout (follows RuneLite's Logout Timer plugin); in combat the game only logs you out after 20 minutes without a click or key press",
        section = idleSection,
        position = 34
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
        position = 36
    )
    default boolean repeatIdleAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "repeatIdleInterval",
        name = "Repeat Every",
        description = "Seconds between repeat idle logout alerts",
        section = idleSection,
        position = 37
    )
    @Units(Units.SECONDS)
    default int repeatIdleInterval() {
        return 10;
    }

    @ConfigItem(
        keyName = "maxIdleRepeats",
        name = "Max Repeats",
        description = "How many times to repeat the idle logout alert after the first",
        section = idleSection,
        position = 38
    )
    default int maxIdleRepeats() {
        return 2;
    }

    @ConfigItem(
        keyName = "voiceCharacterIdle",
        name = "Voice: Character Idle",
        description = "When your character has gone AFK (turning it on plays a preview)",
        section = idleSection,
        position = 39
    )
    default boolean voiceCharacterIdle() {
        return true;
    }

    @ConfigItem(
        keyName = "chatCharacterIdle",
        name = "Chat: Character Idle",
        description = "Chat message when your character has gone AFK",
        section = idleSection,
        position = 40
    )
    default boolean chatCharacterIdle() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceIdleLogout",
        name = "Voice: Logout Warning",
        description = "When you are about to be logged out for inactivity (turning it on plays a preview)",
        section = idleSection,
        position = 41
    )
    default boolean voiceIdleLogout() {
        return true;
    }

    @ConfigItem(
        keyName = "chatIdleLogout",
        name = "Chat: Logout Warning",
        description = "Chat message when you are about to be logged out for inactivity",
        section = idleSection,
        position = 42
    )
    default boolean chatIdleLogout() {
        return true;
    }
}
