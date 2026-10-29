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

@ConfigGroup("bradsmultitool")
public interface BradsMultiToolConfig extends Config {

    enum DpsTrackingMode {
        PER_ENCOUNTER("Per Individual Monster"),
        PER_TARGET_TYPE("Per Monster Type (Resets on different mob)"),
        CONTINUOUS_SESSION("Continuous Session (Rolling average)");

        private final String name;
        DpsTrackingMode(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    enum TrainingFocus {
        FASTEST("Fastest XP"),
        OTHER("Other methods (often AFK)"),
        CHEAPEST("Cheapest (fewest GP per XP)"),
        MONEY("Money maker");

        private final String name;
        TrainingFocus(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    enum BestChoice {
        XP("Most XP"),
        PROFIT("Most profit (GE prices)");

        private final String name;
        BestChoice(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    @ConfigSection(
        name = "General",
        description = "Voice and chat settings shared by all alerts",
        position = 0
    )
    String generalSection = "generalSection";

    @ConfigItem(
        keyName = "voicePersona",
        name = "Voice Persona",
        description = "Voice used for spoken alerts; changing it plays a sample alert",
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
        name = "Potion Buffs",
        description = "Buff timers on the HUD and running-out / expired alerts",
        position = 6,
        closedByDefault = true
    )
    String buffSection = "buffSection";

    @ConfigItem(
        keyName = "enableBuffStatus",
        name = "Show Buffs on HUD",
        description = "Show each active potion buff with its boost and time left",
        section = buffSection,
        position = 5
    )
    default boolean enableBuffStatus() {
        return true;
    }

    @ConfigItem(
        keyName = "flashExpiredBuff",
        name = "Flash When Expired",
        description = "Flash the Buff line on the HUD when buff is expired",
        section = buffSection,
        position = 6
    )
    default boolean flashExpiredBuff() {
        return true;
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
    default int buffWarningSeconds() {
        return 30;
    }

    @ConfigItem(
        keyName = "repeatBuffAlert",
        name = "Repeat Expired Alert",
        description = "Repeat the expired buff announcement",
        section = buffSection,
        position = 10
    )
    default boolean repeatBuffAlert() {
        return false;
    }

    @ConfigItem(
        keyName = "repeatBuffInterval",
        name = "Repeat Every",
        description = "Seconds between repeat buff alerts",
        section = buffSection,
        position = 11
    )
    @Units(Units.SECONDS)
    default int repeatBuffInterval() {
        return 30;
    }

    @ConfigItem(
        keyName = "maxBuffRepeats",
        name = "Max Repeats",
        description = "How many times to repeat the expired buff alert after the first",
        section = buffSection,
        position = 12
    )
    default int maxBuffRepeats() {
        return 2;
    }

    @ConfigItem(
        keyName = "voiceBuffLow",
        name = "Voice: Potion Running Out",
        description = "Shortly before a potion boost runs out (turning it on plays a preview)",
        section = buffSection,
        position = 13
    )
    default boolean voiceBuffLow() {
        return true;
    }

    @ConfigItem(
        keyName = "chatBuffLow",
        name = "Chat: Potion Running Out",
        description = "Chat message shortly before a potion boost runs out",
        section = buffSection,
        position = 14
    )
    default boolean chatBuffLow() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceBuffExpired",
        name = "Voice: Potion Expired",
        description = "When a potion boost has run out (turning it on plays a preview)",
        section = buffSection,
        position = 15
    )
    default boolean voiceBuffExpired() {
        return true;
    }

    @ConfigItem(
        keyName = "chatBuffExpired",
        name = "Chat: Potion Expired",
        description = "Chat message when a potion boost has run out",
        section = buffSection,
        position = 16
    )
    default boolean chatBuffExpired() {
        return true;
    }

    @ConfigSection(
        name = "Health",
        description = "Low health warnings",
        position = 13,
        closedByDefault = true
    )
    String healthSection = "healthSection";

    @ConfigItem(
        keyName = "enableLowHealthAlert",
        name = "Low Health Alert",
        description = "Alert when your hitpoints fall to the threshold or below",
        section = healthSection,
        position = 14
    )
    default boolean enableLowHealthAlert() {
        return true;
    }

    @Range(min = 1, max = 99)
    @ConfigItem(
        keyName = "lowHealthPercent",
        name = "Alert Below",
        description = "Alert when hitpoints fall to this percentage of your level or below",
        section = healthSection,
        position = 15
    )
    @Units(Units.PERCENT)
    default int lowHealthPercent() {
        return 25;
    }

    @ConfigItem(
        keyName = "flashHealthOrb",
        name = "Flash Health Orb",
        description = "Flash the hitpoints orb by the minimap while health is low",
        section = healthSection,
        position = 16
    )
    default boolean flashHealthOrb() {
        return true;
    }

    @ConfigItem(
        keyName = "repeatHealthAlert",
        name = "Repeat Voice Alert",
        description = "Repeat the low health alert until you heal",
        section = healthSection,
        position = 18
    )
    default boolean repeatHealthAlert() {
        return false;
    }

    @ConfigItem(
        keyName = "repeatHealthInterval",
        name = "Repeat Every",
        description = "Seconds between low health alerts",
        section = healthSection,
        position = 19
    )
    @Units(Units.SECONDS)
    default int repeatHealthInterval() {
        return 10;
    }

    @ConfigItem(
        keyName = "maxHealthRepeats",
        name = "Max Repeats",
        description = "How many times to repeat the low health alert after the first",
        section = healthSection,
        position = 20
    )
    default int maxHealthRepeats() {
        return 2;
    }

    @ConfigItem(
        keyName = "voiceHealthLow",
        name = "Voice: Low Health",
        description = "When your health drops below the threshold (turning it on plays a preview)",
        section = healthSection,
        position = 21
    )
    default boolean voiceHealthLow() {
        return true;
    }

    @ConfigItem(
        keyName = "chatHealthLow",
        name = "Chat: Low Health",
        description = "Chat message when your health drops below the threshold",
        section = healthSection,
        position = 22
    )
    default boolean chatHealthLow() {
        return true;
    }

    @ConfigSection(
        name = "Prayer",
        description = "Low and empty prayer warnings",
        position = 21,
        closedByDefault = true
    )
    String prayerSection = "prayerSection";

    @ConfigItem(
        keyName = "enableLowPrayerAlert",
        name = "Low Prayer Alert",
        description = "Alert when prayer points fall below warning threshold",
        section = prayerSection,
        position = 22
    )
    default boolean enableLowPrayerAlert() {
        return true;
    }

    @Range(min = 1, max = 99)
    @ConfigItem(
        keyName = "lowPrayerPercent",
        name = "Alert Below",
        description = "Alert when prayer points fall to this percentage of your level or below",
        section = prayerSection,
        position = 23
    )
    @Units(Units.PERCENT)
    default int lowPrayerPercent() {
        return 25;
    }

    @ConfigItem(
        keyName = "flashPrayerOrb",
        name = "Flash Prayer Orb",
        description = "Flash the prayer orb by the minimap while prayer is low",
        section = prayerSection,
        position = 24
    )
    default boolean flashPrayerOrb() {
        return true;
    }

    @ConfigItem(
        keyName = "flashOutOfPrayerOverhead",
        name = "Overhead 'Out of Prayer' Flash",
        description = "Flash an 'OUT OF PRAYER' box above the player in the game world",
        section = prayerSection,
        position = 25
    )
    default boolean flashOutOfPrayerOverhead() {
        return true;
    }

    @ConfigItem(
        keyName = "repeatPrayerAlert",
        name = "Repeat Voice Alert",
        description = "Repeat low prayer alert until restored",
        section = prayerSection,
        position = 27
    )
    default boolean repeatPrayerAlert() {
        return false;
    }

    @ConfigItem(
        keyName = "repeatPrayerInterval",
        name = "Repeat Every",
        description = "Seconds between low prayer alerts",
        section = prayerSection,
        position = 28
    )
    @Units(Units.SECONDS)
    default int repeatPrayerInterval() {
        return 30;
    }

    @ConfigItem(
        keyName = "maxPrayerRepeats",
        name = "Max Repeats",
        description = "How many times to repeat the low prayer alert after the first",
        section = prayerSection,
        position = 29
    )
    default int maxPrayerRepeats() {
        return 2;
    }

    @ConfigItem(
        keyName = "voicePrayerLow",
        name = "Voice: Low / Out of Prayer",
        description = "When prayer is low or runs out (turning it on plays a preview)",
        section = prayerSection,
        position = 30
    )
    default boolean voicePrayerLow() {
        return true;
    }

    @ConfigItem(
        keyName = "chatPrayerLow",
        name = "Chat: Low / Out of Prayer",
        description = "Chat message when prayer is low or runs out",
        section = prayerSection,
        position = 31
    )
    default boolean chatPrayerLow() {
        return true;
    }

    @ConfigSection(
        name = "Combat Alerts",
        description = "Special attack, antifire, poison, stamina, ammo, runes and cannon alerts",
        position = 29,
        closedByDefault = true
    )
    String combatAlertsSection = "combatAlertsSection";

    @ConfigItem(
        keyName = "specReadyAlert",
        name = "Special Attack Ready",
        description = "Alert when special attack energy reaches the threshold",
        section = combatAlertsSection,
        position = 0
    )
    default boolean specReadyAlert() {
        return false;
    }

    @Range(min = 10, max = 100)
    @ConfigItem(
        keyName = "specThreshold",
        name = "Spec Alert At",
        description = "Special attack energy that counts as ready",
        section = combatAlertsSection,
        position = 1
    )
    @Units(Units.PERCENT)
    default int specThreshold() {
        return 100;
    }

    @ConfigItem(
        keyName = "antifireAlert",
        name = "Antifire Running Out",
        description = "Alert shortly before antifire / super antifire protection ends",
        section = combatAlertsSection,
        position = 2
    )
    default boolean antifireAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "poisonAlert",
        name = "Poison & Venom",
        description = "Alert when you are poisoned or venomed, and when poison protection is running out",
        section = combatAlertsSection,
        position = 3
    )
    default boolean poisonAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "staminaAlert",
        name = "Stamina Worn Off",
        description = "Alert when a stamina potion wears off",
        section = combatAlertsSection,
        position = 4
    )
    default boolean staminaAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "ammoAlert",
        name = "Low Ammo",
        description = "Alert when your ammo or thrown weapons run low while using ranged",
        section = combatAlertsSection,
        position = 5
    )
    default boolean ammoAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "ammoThreshold",
        name = "Low Ammo At",
        description = "Ammo count that counts as low",
        section = combatAlertsSection,
        position = 6
    )
    default int ammoThreshold() {
        return 50;
    }

    @ConfigItem(
        keyName = "runesAlert",
        name = "Out of Runes",
        description = "Alert when a spell fails for lack of runes",
        section = combatAlertsSection,
        position = 7
    )
    default boolean runesAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "cannonAlert",
        name = "Cannon Low",
        description = "Alert when your cannon is running low on cannonballs",
        section = combatAlertsSection,
        position = 8
    )
    default boolean cannonAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "cannonThreshold",
        name = "Cannon Low At",
        description = "Cannonballs left that counts as low",
        section = combatAlertsSection,
        position = 9
    )
    default int cannonThreshold() {
        return 10;
    }

    @ConfigItem(
        keyName = "voiceSpecReady",
        name = "Voice: Special Attack Ready",
        description = "When your special attack is back up (turning it on plays a preview)",
        section = combatAlertsSection,
        position = 10
    )
    default boolean voiceSpecReady() {
        return true;
    }

    @ConfigItem(
        keyName = "chatSpecReady",
        name = "Chat: Special Attack Ready",
        description = "Chat message when your special attack is back up",
        section = combatAlertsSection,
        position = 11
    )
    default boolean chatSpecReady() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceAntifire",
        name = "Voice: Antifire Running Out",
        description = "When antifire protection is about to end (turning it on plays a preview)",
        section = combatAlertsSection,
        position = 12
    )
    default boolean voiceAntifire() {
        return true;
    }

    @ConfigItem(
        keyName = "chatAntifire",
        name = "Chat: Antifire Running Out",
        description = "Chat message when antifire protection is about to end",
        section = combatAlertsSection,
        position = 13
    )
    default boolean chatAntifire() {
        return true;
    }

    @ConfigItem(
        keyName = "voicePoison",
        name = "Voice: Poison / Venom",
        description = "When you are poisoned or venomed, or poison protection is ending (turning it on plays a preview)",
        section = combatAlertsSection,
        position = 14
    )
    default boolean voicePoison() {
        return true;
    }

    @ConfigItem(
        keyName = "chatPoison",
        name = "Chat: Poison / Venom",
        description = "Chat message when you are poisoned or venomed, or poison protection is ending",
        section = combatAlertsSection,
        position = 15
    )
    default boolean chatPoison() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceStamina",
        name = "Voice: Stamina Worn Off",
        description = "When a stamina potion wears off (turning it on plays a preview)",
        section = combatAlertsSection,
        position = 16
    )
    default boolean voiceStamina() {
        return true;
    }

    @ConfigItem(
        keyName = "chatStamina",
        name = "Chat: Stamina Worn Off",
        description = "Chat message when a stamina potion wears off",
        section = combatAlertsSection,
        position = 17
    )
    default boolean chatStamina() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceAmmo",
        name = "Voice: Low Ammo",
        description = "When your ammo runs low (turning it on plays a preview)",
        section = combatAlertsSection,
        position = 18
    )
    default boolean voiceAmmo() {
        return true;
    }

    @ConfigItem(
        keyName = "chatAmmo",
        name = "Chat: Low Ammo",
        description = "Chat message when your ammo runs low",
        section = combatAlertsSection,
        position = 19
    )
    default boolean chatAmmo() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceRunes",
        name = "Voice: Out of Runes",
        description = "When you can't cast for lack of runes (turning it on plays a preview)",
        section = combatAlertsSection,
        position = 20
    )
    default boolean voiceRunes() {
        return true;
    }

    @ConfigItem(
        keyName = "chatRunes",
        name = "Chat: Out of Runes",
        description = "Chat message when you can't cast for lack of runes",
        section = combatAlertsSection,
        position = 21
    )
    default boolean chatRunes() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceCannon",
        name = "Voice: Cannon Low",
        description = "When your cannon is low on cannonballs (turning it on plays a preview)",
        section = combatAlertsSection,
        position = 22
    )
    default boolean voiceCannon() {
        return true;
    }

    @ConfigItem(
        keyName = "chatCannon",
        name = "Chat: Cannon Low",
        description = "Chat message when your cannon is low on cannonballs",
        section = combatAlertsSection,
        position = 23
    )
    default boolean chatCannon() {
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

    @ConfigSection(
        name = "PvM HUD",
        description = "Combat HUD and monster hover info for non-crab monsters",
        position = 39,
        closedByDefault = true
    )
    String pvmSection = "pvmSection";

    @ConfigItem(
        keyName = "enablePvmOverlay",
        name = "Enable PvM HUD",
        description = "Show combat HUD (DPS, target HP bar, aggro state, weaknesses) when attacking any monster",
        section = pvmSection,
        position = 40
    )
    default boolean enablePvmOverlay() {
        return true;
    }

    @ConfigItem(
        keyName = "showHoverInfo",
        name = "Monster Hover Info",
        description = "When hovering an attackable monster, show its level, health, weakness, attack style and  aggression next to the cursor (hidden for the monster type you're already fighting)",
        section = pvmSection,
        position = 41
    )
    default boolean showHoverInfo() {
        return true;
    }

    @ConfigItem(
        keyName = "dpsMode",
        name = "DPS Tracking Mode",
        description = "Per Individual Target vs Per Monster Type vs Continuous Session",
        section = pvmSection,
        position = 42
    )
    default DpsTrackingMode dpsMode() {
        return DpsTrackingMode.PER_TARGET_TYPE;
    }

    @ConfigItem(
        keyName = "pvmLingerSeconds",
        name = "Linger After Combat",
        description = "Seconds the PvM HUD and DPS history remain visible after combat ends",
        section = pvmSection,
        position = 43
    )
    @Units(Units.SECONDS)
    default int pvmLingerSeconds() {
        return 15;
    }

    @ConfigItem(
        keyName = "showPvmWeakness",
        name = "Show Weakness",
        description = "Show style, elemental, or slayer bane weakness on the PvM overlay",
        section = pvmSection,
        position = 44
    )
    default boolean showPvmWeakness() {
        return true;
    }

    @ConfigItem(
        keyName = "showPvmDps",
        name = "Show DPS",
        description = "Display live or rolling session DPS on the PvM overlay",
        section = pvmSection,
        position = 45
    )
    default boolean showPvmDps() {
        return true;
    }

    @ConfigItem(
        keyName = "showPvmLastHit",
        name = "Show Last Hit",
        description = "Display continuous most recent hit dealt to PvM monsters",
        section = pvmSection,
        position = 46
    )
    default boolean showPvmLastHit() {
        return true;
    }

    @ConfigItem(
        keyName = "showPvmMaxHit",
        name = "Show Max Hit",
        description = "Display continuous highest hit dealt to PvM monsters",
        section = pvmSection,
        position = 47
    )
    default boolean showPvmMaxHit() {
        return true;
    }

    @ConfigItem(
        keyName = "showPvmAggro",
        name = "Show Aggro Status",
        description = "Show whether the monster is aggressive and its tolerance timer",
        section = pvmSection,
        position = 48
    )
    default boolean showPvmAggro() {
        return true;
    }

    @ConfigSection(
        name = "Slayer",
        description = "Task tracker, required items, superior and task-complete alerts",
        position = 48,
        closedByDefault = true
    )
    String slayerSection = "slayerSection";

    @ConfigItem(
        keyName = "showSlayerTask",
        name = "Show Task Tracker",
        description = "Show your task, kills left, kill rate and time to finish",
        section = slayerSection,
        position = 0
    )
    default boolean showSlayerTask() {
        return true;
    }

    @ConfigItem(
        keyName = "slayerToggleKey",
        name = "Show/Hide Hotkey",
        description = "Press to show or hide the slayer task card",
        section = slayerSection,
        position = 1
    )
    default Keybind slayerToggleKey() {
        return new Keybind(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
    }

    @ConfigItem(
        keyName = "showSlayerDirections",
        name = "Where To Go",
        description = "On a task, list where to kill it (multicombat, cannon, safespot) and how to get to the first place",
        section = slayerSection,
        position = 5
    )
    default boolean showSlayerDirections() {
        return true;
    }

    @ConfigItem(
        keyName = "showSlayerMasterCard",
        name = "Master Card Without a Task",
        description = "With no task, show the best Slayer master for your levels, where they are and how to get there",
        section = slayerSection,
        position = 9
    )
    default boolean showSlayerMasterCard() {
        return true;
    }

    @ConfigItem(
        keyName = "slayerPictures",
        name = "Pictures",
        description = "Show the monster's picture on a task, and the Slayer master's chathead with no task",
        section = slayerSection,
        position = 2
    )
    default boolean slayerPictures() {
        return true;
    }

    @ConfigItem(
        keyName = "slayerKillRate",
        name = "Kill Rate and Finish Time",
        description = "Kills per hour and roughly when you'll finish the task",
        section = slayerSection,
        position = 3
    )
    default boolean slayerKillRate() {
        return true;
    }

    @ConfigItem(
        keyName = "slayerRequiredItem",
        name = "Required Item",
        description = "The item the task needs (red when you don't have it)",
        section = slayerSection,
        position = 4
    )
    default boolean slayerRequiredItem() {
        return true;
    }

    @ConfigItem(
        keyName = "slayerTravelRoutes",
        name = "How To Get There",
        description = "The fastest ways to the first place to kill your task, or to the Slayer master",
        section = slayerSection,
        position = 7
    )
    default boolean slayerTravelRoutes() {
        return true;
    }

    @ConfigItem(
        keyName = "slayerNextMaster",
        name = "Next Master",
        description = "With no task, the next Slayer master you'll unlock and what they need",
        section = slayerSection,
        position = 10
    )
    default boolean slayerNextMaster() {
        return true;
    }

    @Range(min = 1, max = 8)
    @ConfigItem(
        keyName = "slayerPlacesShown",
        name = "Places Shown",
        description = "How many places to kill your task to list",
        section = slayerSection,
        position = 6
    )
    default int slayerPlacesShown() {
        return 3;
    }

    @Range(min = 1, max = 5)
    @ConfigItem(
        keyName = "slayerRoutesShown",
        name = "Routes Shown",
        description = "How many ways to get there to list",
        section = slayerSection,
        position = 8
    )
    default int slayerRoutesShown() {
        return 2;
    }

    @ConfigItem(
        keyName = "showSlayerMonsterInfo",
        name = "Slayer Info on Monsters",
        description = "On the monster hover card and PvM HUD for Slayer monsters: the Slayer level needed (red if you can't damage it yet), the gear its task needs (earmuffs, nose peg, rock hammer...; red if you don't have it), whether it's your task, and Slayer XP",
        section = slayerSection,
        position = 11
    )
    default boolean showSlayerMonsterInfo() {
        return true;
    }

    @ConfigItem(
        keyName = "highlightTaskMonsters",
        name = "Highlight Task Monsters",
        description = "Outline the monsters that count for your task. On a Konar task, ones outside her assigned area aren't outlined (kills there don't count)",
        section = slayerSection,
        position = 12
    )
    default boolean highlightTaskMonsters() {
        return true;
    }

    @Alpha
    @ConfigItem(
        keyName = "taskMonsterColor",
        name = "Task Monster Colour",
        description = "Outline colour for task monsters",
        section = slayerSection,
        position = 13
    )
    default Color taskMonsterColor() {
        return new Color(255, 120, 0, 200);
    }

    @ConfigItem(
        keyName = "slayerItemAlert",
        name = "Missing Item Warning",
        description = "Warn when fighting a task monster without its required item (e.g. rock hammer)",
        section = slayerSection,
        position = 11
    )
    default boolean slayerItemAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "superiorAlert",
        name = "Superior Spawned",
        description = "Alert when a superior slayer monster appears",
        section = slayerSection,
        position = 12
    )
    default boolean superiorAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "taskCompleteAlert",
        name = "Task Complete",
        description = "Alert when your slayer task is finished",
        section = slayerSection,
        position = 13
    )
    default boolean taskCompleteAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceSlayerItem",
        name = "Voice: Missing Slayer Item",
        description = "When fighting a task monster without its required item (turning it on plays a preview)",
        section = slayerSection,
        position = 14
    )
    default boolean voiceSlayerItem() {
        return true;
    }

    @ConfigItem(
        keyName = "chatSlayerItem",
        name = "Chat: Missing Slayer Item",
        description = "Chat message when fighting a task monster without its required item",
        section = slayerSection,
        position = 15
    )
    default boolean chatSlayerItem() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceSuperior",
        name = "Voice: Superior Spawned",
        description = "When a superior slayer monster appears (turning it on plays a preview)",
        section = slayerSection,
        position = 16
    )
    default boolean voiceSuperior() {
        return true;
    }

    @ConfigItem(
        keyName = "chatSuperior",
        name = "Chat: Superior Spawned",
        description = "Chat message when a superior slayer monster appears",
        section = slayerSection,
        position = 17
    )
    default boolean chatSuperior() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceTaskComplete",
        name = "Voice: Task Complete",
        description = "When your slayer task is done (turning it on plays a preview)",
        section = slayerSection,
        position = 18
    )
    default boolean voiceTaskComplete() {
        return true;
    }

    @ConfigItem(
        keyName = "chatTaskComplete",
        name = "Chat: Task Complete",
        description = "Chat message when your slayer task is done",
        section = slayerSection,
        position = 19
    )
    default boolean chatTaskComplete() {
        return true;
    }

    @ConfigSection(
        name = "Gemstone Crab",
        description = "Crab HUD, radar, alerts and outlines",
        position = 49,
        closedByDefault = true
    )
    String crabSection = "crabSection";

    @ConfigItem(
        keyName = "enableCrabFeatures",
        name = "Enable Crab Features",
        description = "Master toggle for Crab HUD, Radar, and 3D highlights in the caves",
        section = crabSection,
        position = 50
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
        name = "Low Health Alert",
        description = "Alert when the crab's health falls to this percentage or below",
        section = crabSection,
        position = 51
    )
    @Units(Units.PERCENT)
    default int crabLowHealthPercent() {
        return 5;
    }

    @ConfigItem(
        keyName = "showCrabLastHit",
        name = "Show Last Hit",
        description = "Display continuous most recent hit dealt to Gemstone Crabs",
        section = crabSection,
        position = 54
    )
    default boolean showCrabLastHit() {
        return true;
    }

    @ConfigItem(
        keyName = "showCrabMaxHit",
        name = "Show Max Hit",
        description = "Display continuous highest hit dealt to Gemstone Crabs",
        section = crabSection,
        position = 55
    )
    default boolean showCrabMaxHit() {
        return true;
    }

    @Alpha
    @ConfigItem(
        keyName = "activeCrabColor",
        name = "Active Crab Color",
        description = "Soft translucent outline for active fighting crab",
        section = crabSection,
        position = 56
    )
    default Color activeCrabColor() {
        return new Color(0, 220, 255, 130);
    }

    @Alpha
    @ConfigItem(
        keyName = "mineableShellColor",
        name = "Mineable Shell Color",
        description = "Outline for a crab shell you can mine (you were in the top 16 damage dealers and have gems left)",
        section = crabSection,
        position = 57
    )
    default Color mineableShellColor() {
        return new Color(40, 230, 80, 140);
    }

    @Alpha
    @ConfigItem(
        keyName = "depletedShellColor",
        name = "Unmineable Shell Color",
        description = "Outline for a crab shell you can't mine (not in the top 16 damage dealers, or already mined)",
        section = crabSection,
        position = 58
    )
    default Color depletedShellColor() {
        return new Color(255, 60, 60, 140);
    }

    @Alpha
    @ConfigItem(
        keyName = "tunnelColor",
        name = "Tunnel Color",
        description = "Soft translucent outline for cave tunnels when crab is burrowed",
        section = crabSection,
        position = 59
    )
    default Color tunnelColor() {
        return new Color(40, 230, 80, 140);
    }

    @ConfigItem(
        keyName = "voiceCrabMoved",
        name = "Voice: Crab Moved",
        description = "When the Gemstone Crab relocates (turning it on plays a preview)",
        section = crabSection,
        position = 60
    )
    default boolean voiceCrabMoved() {
        return true;
    }

    @ConfigItem(
        keyName = "chatCrabMoved",
        name = "Chat: Crab Moved",
        description = "Chat message when the Gemstone Crab relocates",
        section = crabSection,
        position = 61
    )
    default boolean chatCrabMoved() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceCrabLowHealth",
        name = "Voice: Crab About to Burrow",
        description = "When the Gemstone Crab is low on health (turning it on plays a preview)",
        section = crabSection,
        position = 62
    )
    default boolean voiceCrabLowHealth() {
        return true;
    }

    @ConfigItem(
        keyName = "chatCrabLowHealth",
        name = "Chat: Crab About to Burrow",
        description = "Chat message when the Gemstone Crab is low on health",
        section = crabSection,
        position = 63
    )
    default boolean chatCrabLowHealth() {
        return true;
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
        description = "How cards pick the \"best\" thing to make from what you carry (e.g. the anvil card): most XP, or most profit at Grand Exchange prices",
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
        name = "Default Training Focus",
        description = "What goal suggestions aim for unless a skill has its own (right-click a skill in the tracker > Suggest, or pick it when setting a goal): the wiki guide's fastest XP, its other methods (often more AFK), the cheapest way (fewest GP per XP when making things; gathering is free), or the best money maker you can do. Slayer always suggests the best Slayer master",
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

    @Range(min = 1, max = 60)
    @ConfigItem(
        keyName = "autoPauseMinutes",
        name = "Auto-Pause After",
        description = "Minutes without XP in a skill before it auto-pauses",
        section = trackerSection,
        position = 75
    )
    @Units(Units.MINUTES)
    default int autoPauseMinutes() {
        return 5;
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
