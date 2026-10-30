package com.snuffys.multitool;

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

/** Snuffy's PvM/Slayer: buffs, health, prayer, combat alerts, the PvM HUD and Slayer. */
@ConfigGroup("snuffyspvm")
public interface PvmConfig extends CommonConfig {

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

    enum DpsTrackingMode {
        PER_ENCOUNTER("Per Monster"),
        PER_TARGET_TYPE("Per Type"),
        CONTINUOUS_SESSION("Session");

        private final String name;
        DpsTrackingMode(String name) { this.name = name; }
        @Override public String toString() { return name; }
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

    @Range(min = 10, max = 48)
    @ConfigItem(
        keyName = "outOfPrayerTextSize",
        name = "Out of Prayer Size",
        description = "How big the flashing OUT OF PRAYER message over your head is (16 is the game's bold text size)",
        section = prayerSection,
        position = 26
    )
    default int outOfPrayerTextSize() {
        return 16;
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
        name = "DPS Mode",
        description = "Per Monster: each monster you fight from scratch. Per Type: keeps going while you fight the same kind of monster, resets on a different one. Session: a rolling average over the whole session",
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
}
