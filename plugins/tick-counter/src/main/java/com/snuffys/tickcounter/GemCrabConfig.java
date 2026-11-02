package com.snuffys.tickcounter;

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

/** Snuffy's GemCrab Tracker: the crab HUD, outlines and alerts. */
@ConfigGroup("snuffysgemcrab")
public interface GemCrabConfig extends CommonConfig, BuffConfig {

    @ConfigSection(
        name = "About",
        description = "<div width='300'>Snuffy's GemCrab Tracker: the Gemstone Crab HUD with the burrow countdown, the radar to the next cove, outlines and alerts. Clashes (said in chat while they're on): RuneLite's Idle Notifier. Open the Guide for every feature and how to use it.",
        position = -10
    )
    String aboutSection = "aboutSection";

    @ConfigSection(
        name = "Gemstone Crab Colours",
        description = "Colours of the crab, its shell and the tunnel highlights",
        position = 21,
        closedByDefault = true
    )
    String crabColoursSection = "crabColoursSection";

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
        name = "Potion Buffs",
        description = "Buff timers on the HUD and running-out / expired alerts",
        position = 12,
        closedByDefault = true
    )
    String buffSection = "buffSection";

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
        name = "Gemstone Crab",
        description = "Crab HUD, radar, alerts and outlines",
        position = 20,
        closedByDefault = true
    )
    String crabSection = "crabSection";

    @ConfigItem(
        keyName = "enableCrabFeatures",
        name = "Crab HUD and Highlights",
        description = "<div width='300'>Master toggle for Crab HUD, Radar, and 3D highlights in the caves",
        section = crabSection,
        position = 0
    )
    default boolean enableCrabFeatures() {
        return true;
    }

    @Range(min
    =
    1,
    max
    =
    99)
    @ConfigItem(
        keyName = "crabLowHealthPercent",
        name = "About to Burrow At",
        description = "<div width='300'>Alert when the crab's health falls to this percentage or below",
        section = crabSection,
        position = 1
    )
    @Units(Units.PERCENT)
    default int crabLowHealthPercent() {
        return 5;
    }

    @Range(max
    =
    99)
    @ConfigItem(
        keyName = "crabLowStatsPercent",
        name = "Low Prayer/HP At",
        description = "<div width='300'>Show Prayer: LOW / Health: LOW on the crab card at or below this share of your level (0: off)",
        section = crabSection,
        position = 7
    )
    @Units(Units.PERCENT)
    default int crabLowStatsPercent() {
        return 25;
    }

    @ConfigItem(
        keyName = "crabGearCheck",
        name = "Gear Check",
        description = "<div width='300'>Your gear's damage bonus for the style you're fighting with, and any worn item whose damage bonus is for another style (only damage counts against the crab)",
        section = crabSection,
        position = 6
    )
    default boolean crabGearCheck() {
        return true;
    }

    @ConfigItem(
        keyName = "showCrabLastHit",
        name = "Show Last Hit",
        description = "Display continuous most recent hit dealt to Gemstone Crabs",
        section = crabSection,
        position = 8
    )
    default boolean showCrabLastHit() {
        return true;
    }

    @ConfigItem(
        keyName = "showCrabMaxHit",
        name = "Show Max Hit",
        description = "Display continuous highest hit dealt to Gemstone Crabs",
        section = crabSection,
        position = 9
    )
    default boolean showCrabMaxHit() {
        return true;
    }

    @Alpha
    @ConfigItem(
        keyName = "activeCrabColor",
        name = "Crab Colour",
        description = "Soft translucent outline for active fighting crab",
        section = crabColoursSection,
        position = 0
    )
    default Color activeCrabColor() {
        return new Color(0, 220, 255, 130);
    }

    @Alpha
    @ConfigItem(
        keyName = "mineableShellColor",
        name = "Shell Colour (Mineable)",
        description = "<div width='300'>Outline for a crab shell you can mine (you were in the top 16 damage dealers and have gems left)",
        section = crabColoursSection,
        position = 1
    )
    default Color mineableShellColor() {
        return new Color(40, 230, 80, 140);
    }

    @Alpha
    @ConfigItem(
        keyName = "depletedShellColor",
        name = "Shell Colour (Can't Mine)",
        description = "<div width='300'>Outline for a crab shell you can't mine (not in the top 16 damage dealers, or already mined)",
        section = crabColoursSection,
        position = 2
    )
    default Color depletedShellColor() {
        return new Color(255, 60, 60, 140);
    }

    @Alpha
    @ConfigItem(
        keyName = "tunnelColor",
        name = "Tunnel Colour",
        description = "<div width='300'>Soft translucent outline for cave tunnels when crab is burrowed",
        section = crabColoursSection,
        position = 3
    )
    default Color tunnelColor() {
        return new Color(40, 230, 80, 140);
    }

    @ConfigItem(
        keyName = "voiceCrabMoved",
        name = "Voice: Crab Moved",
        description = "<div width='300'>When the Gemstone Crab relocates (turning it on plays a preview)",
        section = crabSection,
        position = 4
    )
    default boolean voiceCrabMoved() {
        return true;
    }

    @ConfigItem(
        keyName = "chatCrabMoved",
        name = "Chat: Crab Moved",
        description = "Chat message when the Gemstone Crab relocates",
        section = crabSection,
        position = 5
    )
    default boolean chatCrabMoved() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceCrabLowHealth",
        name = "Voice: Crab About to Burrow",
        description = "<div width='300'>When the Gemstone Crab is low on health (turning it on plays a preview)",
        section = crabSection,
        position = 2
    )
    default boolean voiceCrabLowHealth() {
        return true;
    }

    @ConfigItem(
        keyName = "chatCrabLowHealth",
        name = "Chat: Crab About to Burrow",
        description = "Chat message when the Gemstone Crab is low on health",
        section = crabSection,
        position = 3
    )
    default boolean chatCrabLowHealth() {
        return true;
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
        return BuffConfig.super.enableBuffStatus();
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
        return BuffConfig.super.flashExpiredBuff();
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
        return BuffConfig.super.buffWarningSeconds();
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
        return BuffConfig.super.repeatBuffAlert();
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
        return BuffConfig.super.repeatBuffInterval();
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
        return BuffConfig.super.maxBuffRepeats();
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
        return BuffConfig.super.voiceBuffLow();
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
        return BuffConfig.super.chatBuffLow();
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
        return BuffConfig.super.voiceBuffExpired();
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
        return BuffConfig.super.chatBuffExpired();
    }
    // ---- End of BuffConfig's settings ----
}
