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
        keyName = "sendChatMessages",
        name = "Chat Messages",
        description = "Also post alerts as in-game chat messages",
        section = generalSection,
        position = 3
    )
    default boolean sendChatMessages() {
        return true;
    }

    @ConfigSection(
        name = "Potion Buffs",
        description = "Buff timers on the HUD and running-out / expired alerts",
        position = 4,
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
        keyName = "voiceBuffLow",
        name = "Voice: Running Out",
        description = "Announce shortly before your combat boost runs out (click to preview)",
        section = buffSection,
        position = 8
    )
    default boolean voiceBuffLow() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceBuffExpired",
        name = "Voice: Expired",
        description = "Announce when your combat buff has run out (click to preview)",
        section = buffSection,
        position = 9
    )
    default boolean voiceBuffExpired() {
        return true;
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
        keyName = "voiceHealthLow",
        name = "Voice Alert",
        description = "Announce when your hitpoints fall below the threshold (click to preview)",
        section = healthSection,
        position = 17
    )
    default boolean voiceHealthLow() {
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
        keyName = "voicePrayerLow",
        name = "Voice Alert",
        description = "Announce low and empty prayer (click to preview)",
        section = prayerSection,
        position = 26
    )
    default boolean voicePrayerLow() {
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

    @ConfigSection(
        name = "Idle",
        description = "Idle character and idle logout warnings",
        position = 30,
        closedByDefault = true
    )
    String idleSection = "idleSection";

    @ConfigItem(
        keyName = "characterIdleAlert",
        name = "Character Idle Alert",
        description = "Alert when your character stops doing anything (fighting, skilling, moving) for a while",
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
        description = "Seconds of doing nothing before the character idle alert",
        section = idleSection,
        position = 32
    )
    @Units(Units.SECONDS)
    default int characterIdleSeconds() {
        return 10;
    }

    @ConfigItem(
        keyName = "voiceCharacterIdle",
        name = "Voice: Character Idle",
        description = "Announce when your character has been idle (click to preview)",
        section = idleSection,
        position = 33
    )
    default boolean voiceCharacterIdle() {
        return true;
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
        description = "How many seconds before the idle logout to warn you (follows RuneLite's Logout Timer plugin if you use it)",
        section = idleSection,
        position = 34
    )
    @Units(Units.SECONDS)
    default int idleWarningSeconds() {
        return 30;
    }

    @ConfigItem(
        keyName = "voiceIdleLogout",
        name = "Voice: Logout Warning",
        description = "Announce when you are about to be logged out for inactivity (click to preview)",
        section = idleSection,
        position = 35
    )
    default boolean voiceIdleLogout() {
        return true;
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
        position = 0
    )
    default Keybind slayerToggleKey() {
        return new Keybind(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
    }

    @ConfigItem(
        keyName = "slayerItemAlert",
        name = "Missing Item Warning",
        description = "Warn when fighting a task monster without its required item (e.g. rock hammer)",
        section = slayerSection,
        position = 1
    )
    default boolean slayerItemAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "superiorAlert",
        name = "Superior Spawned",
        description = "Alert when a superior slayer monster appears",
        section = slayerSection,
        position = 2
    )
    default boolean superiorAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "taskCompleteAlert",
        name = "Task Complete",
        description = "Alert when your slayer task is finished",
        section = slayerSection,
        position = 3
    )
    default boolean taskCompleteAlert() {
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
        keyName = "voiceCrabLowHealth",
        name = "Voice: About to Burrow",
        description = "Announce when the Gemstone Crab is about to burrow (click to preview)",
        section = crabSection,
        position = 52
    )
    default boolean voiceCrabLowHealth() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceCrabMoved",
        name = "Voice: Crab Relocated",
        description = "Announce when Gemstone Crab burrows to another cove (click to preview)",
        section = crabSection,
        position = 53
    )
    default boolean voiceCrabMoved() {
        return true;
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

    @ConfigSection(
        name = "Skilling",
        description = "Hover info for trees, rocks, fishing spots and thieving, plus skilling alerts",
        position = 59,
        closedByDefault = true
    )
    String skillingSection = "skillingSection";

    @ConfigItem(
        keyName = "showSkillingHover",
        name = "Skilling Hover Info",
        description = "When hovering a tree, rock, fishing spot, stall or pickpocket target, show level, XP, tools and more",
        section = skillingSection,
        position = 0
    )
    default boolean showSkillingHover() {
        return true;
    }

    @ConfigItem(
        keyName = "showTreeTimer",
        name = "Tree Time Left",
        description = "Estimate how long the tree you are chopping has left before it falls",
        section = skillingSection,
        position = 1
    )
    default boolean showTreeTimer() {
        return true;
    }

    @ConfigItem(
        keyName = "inventoryFullAlert",
        name = "Inventory Full",
        description = "Alert when your inventory fills up while skilling",
        section = skillingSection,
        position = 2
    )
    default boolean inventoryFullAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "resourceGoneAlert",
        name = "Resource Gone",
        description = "Alert when your tree falls, rock depletes or fishing spot moves",
        section = skillingSection,
        position = 3
    )
    default boolean resourceGoneAlert() {
        return true;
    }

    @ConfigSection(
        name = "Skill Tracker",
        description = "Sidebar XP tracker",
        position = 60,
        closedByDefault = true
    )
    String trackerSection = "trackerSection";

    @ConfigItem(
        keyName = "showXpPerMinute",
        name = "Show XP/Min",
        description = "Display XP per minute alongside XP/hr inside each skill card",
        section = trackerSection,
        position = 61
    )
    default boolean showXpPerMinute() {
        return true;
    }

    @ConfigItem(
        keyName = "ignoredSkills",
        name = "Ignored Skills",
        description = "Comma-separated list of skills to filter out",
        section = trackerSection,
        position = 62
    )
    default String ignoredSkills() {
        return "";
    }
}
