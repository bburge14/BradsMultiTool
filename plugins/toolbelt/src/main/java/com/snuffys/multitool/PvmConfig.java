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
public interface PvmConfig extends CommonConfig, BuffConfig {

    @ConfigSection(
        name = "About",
        description = "<div width='300'>Snuffy's PvM/Slayer: the PvM HUD and monster hover card, buff bar and potion timers, health and prayer warnings, combat alerts, and the Slayer card (its hotkey shows or hides it) with task monster outlines. Clashes (said in chat while they're on): RuneLite's Slayer plugin with its task highlight on (you'd see two outlines), and RuneLite's Idle Notifier. Open the Guide for every feature and how to use it.",
        position = -10
    )
    String aboutSection = "aboutSection";

    @ConfigSection(
        name = "Supply Alerts",
        description = "Running low on ammo, runes or cannonballs",
        position = 16,
        closedByDefault = true
    )
    String supplyAlertsSection = "supplyAlertsSection";

    @ConfigSection(
        name = "Slayer Alerts",
        description = "<div width='300'>Alerts for your Slayer task: missing item, superiors, halfway, nearly done, complete, next task a milestone, and kill goals reached",
        position = 19,
        closedByDefault = true
    )
    String slayerAlertsSection = "slayerAlertsSection";

    @ConfigSection(
        name = "Slayer Monsters",
        description = "<div width='300'>Slayer details on monsters, outlines on your task's monsters, and the finish-off marker",
        position = 18,
        closedByDefault = true
    )
    String slayerMonstersSection = "slayerMonstersSection";

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

    // Repeated from CommonConfig and BuffConfig: RuneLite only shows sections an interface declares itself


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

    enum BarLayout {
        HORIZONTAL("Horizontal"),
        VERTICAL("Vertical");

        private final String label;

        BarLayout(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

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
        position = 12,
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

    @ConfigItem(
        keyName = "enableLowHealthAlert",
        name = "Low Health Alert",
        description = "Alert when your hitpoints fall to the threshold or below",
        section = healthSection,
        position = 0
    )
    default boolean enableLowHealthAlert() {
        return true;
    }

    @Range(min = 1, max = 99)
    @ConfigItem(
        keyName = "lowHealthPercent",
        name = "Alert Below",
        description = "<div width='300'>Alert when hitpoints fall to this percentage of your level or below",
        section = healthSection,
        position = 1
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
        position = 2
    )
    default boolean flashHealthOrb() {
        return true;
    }

    @ConfigItem(
        keyName = "repeatHealthAlert",
        name = "Repeat Voice Alert",
        description = "Repeat the low health alert until you heal",
        section = healthSection,
        position = 3
    )
    default boolean repeatHealthAlert() {
        return false;
    }

    @ConfigItem(
        keyName = "repeatHealthInterval",
        name = "Repeat Every",
        description = "Seconds between low health alerts",
        section = healthSection,
        position = 4
    )
    @Units(Units.SECONDS)
    default int repeatHealthInterval() {
        return 10;
    }

    @ConfigItem(
        keyName = "maxHealthRepeats",
        name = "Max Repeats",
        description = "<div width='300'>How many times to repeat the low health alert after the first",
        section = healthSection,
        position = 5
    )
    default int maxHealthRepeats() {
        return 2;
    }

    @ConfigItem(
        keyName = "voiceHealthLow",
        name = "Voice: Low Health",
        description = "<div width='300'>When your health drops below the threshold (turning it on plays a preview)",
        section = healthSection,
        position = 6
    )
    default boolean voiceHealthLow() {
        return true;
    }

    @ConfigItem(
        keyName = "chatHealthLow",
        name = "Chat: Low Health",
        description = "Chat message when your health drops below the threshold",
        section = healthSection,
        position = 7
    )
    default boolean chatHealthLow() {
        return true;
    }

    @ConfigSection(
        name = "Prayer",
        description = "Low and empty prayer warnings",
        position = 14,
        closedByDefault = true
    )
    String prayerSection = "prayerSection";

    @ConfigItem(
        keyName = "enableLowPrayerAlert",
        name = "Low Prayer Alert",
        description = "Alert when prayer points fall below warning threshold",
        section = prayerSection,
        position = 0
    )
    default boolean enableLowPrayerAlert() {
        return true;
    }

    @Range(min = 1, max = 99)
    @ConfigItem(
        keyName = "lowPrayerPercent",
        name = "Alert Below",
        description = "<div width='300'>Alert when prayer points fall to this percentage of your level or below",
        section = prayerSection,
        position = 1
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
        position = 2
    )
    default boolean flashPrayerOrb() {
        return true;
    }

    @ConfigItem(
        keyName = "flashOutOfPrayerOverhead",
        name = "'Out of Prayer' Over Head",
        description = "<div width='300'>Flash an 'OUT OF PRAYER' box above the player in the game world",
        section = prayerSection,
        position = 3
    )
    default boolean flashOutOfPrayerOverhead() {
        return true;
    }

    @Range(min = 10, max = 48)
    @ConfigItem(
        keyName = "outOfPrayerTextSize",
        name = "'Out of Prayer' Text Size",
        description = "<div width='300'>How big the flashing OUT OF PRAYER message over your head is (16 is the game's bold text size)",
        section = prayerSection,
        position = 4
    )
    default int outOfPrayerTextSize() {
        return 16;
    }

    @ConfigItem(
        keyName = "repeatPrayerAlert",
        name = "Repeat Voice Alert",
        description = "Repeat low prayer alert until restored",
        section = prayerSection,
        position = 5
    )
    default boolean repeatPrayerAlert() {
        return false;
    }

    @ConfigItem(
        keyName = "repeatPrayerInterval",
        name = "Repeat Every",
        description = "Seconds between low prayer alerts",
        section = prayerSection,
        position = 6
    )
    @Units(Units.SECONDS)
    default int repeatPrayerInterval() {
        return 30;
    }

    @ConfigItem(
        keyName = "maxPrayerRepeats",
        name = "Max Repeats",
        description = "<div width='300'>How many times to repeat the low prayer alert after the first",
        section = prayerSection,
        position = 7
    )
    default int maxPrayerRepeats() {
        return 2;
    }

    @ConfigItem(
        keyName = "voicePrayerLow",
        name = "Voice: Low / Out of Prayer",
        description = "<div width='300'>When prayer is low or runs out (turning it on plays a preview)",
        section = prayerSection,
        position = 8
    )
    default boolean voicePrayerLow() {
        return true;
    }

    @ConfigItem(
        keyName = "chatPrayerLow",
        name = "Chat: Low / Out of Prayer",
        description = "Chat message when prayer is low or runs out",
        section = prayerSection,
        position = 9
    )
    default boolean chatPrayerLow() {
        return true;
    }

    @ConfigSection(
        name = "Combat Alerts",
        description = "<div width='300'>Special attack, antifire, poison, stamina, wrong protection prayer and unpotted alerts",
        position = 15,
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
        description = "<div width='300'>Alert shortly before antifire / super antifire protection ends",
        section = combatAlertsSection,
        position = 4
    )
    default boolean antifireAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "poisonAlert",
        name = "Poison and Venom",
        description = "<div width='300'>Alert when you are poisoned or venomed, and when poison protection is running out",
        section = combatAlertsSection,
        position = 7
    )
    default boolean poisonAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "staminaAlert",
        name = "Stamina Worn Off",
        description = "Alert when a stamina potion wears off",
        section = combatAlertsSection,
        position = 10
    )
    default boolean staminaAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "ammoAlert",
        name = "Low Ammo",
        description = "<div width='300'>Alert when your ammo or thrown weapons run low while using ranged",
        section = supplyAlertsSection,
        position = 0
    )
    default boolean ammoAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "ammoThreshold",
        name = "Low Ammo At",
        description = "Ammo count that counts as low",
        section = supplyAlertsSection,
        position = 1
    )
    default int ammoThreshold() {
        return 50;
    }

    @ConfigItem(
        keyName = "runesAlert",
        name = "Out of Runes",
        description = "Alert when a spell fails for lack of runes",
        section = supplyAlertsSection,
        position = 4
    )
    default boolean runesAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "cannonAlert",
        name = "Cannon Low",
        description = "Alert when your cannon is running low on cannonballs",
        section = supplyAlertsSection,
        position = 7
    )
    default boolean cannonAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "cannonThreshold",
        name = "Cannon Low At",
        description = "Cannonballs left that counts as low",
        section = supplyAlertsSection,
        position = 8
    )
    default int cannonThreshold() {
        return 10;
    }

    @ConfigItem(
        keyName = "voiceSpecReady",
        name = "Voice: Special Attack Ready",
        description = "<div width='300'>When your special attack is back up (turning it on plays a preview)",
        section = combatAlertsSection,
        position = 2
    )
    default boolean voiceSpecReady() {
        return true;
    }

    @ConfigItem(
        keyName = "chatSpecReady",
        name = "Chat: Special Attack Ready",
        description = "Chat message when your special attack is back up",
        section = combatAlertsSection,
        position = 3
    )
    default boolean chatSpecReady() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceAntifire",
        name = "Voice: Antifire Running Out",
        description = "<div width='300'>When antifire protection is about to end (turning it on plays a preview)",
        section = combatAlertsSection,
        position = 5
    )
    default boolean voiceAntifire() {
        return true;
    }

    @ConfigItem(
        keyName = "chatAntifire",
        name = "Chat: Antifire Running Out",
        description = "Chat message when antifire protection is about to end",
        section = combatAlertsSection,
        position = 6
    )
    default boolean chatAntifire() {
        return true;
    }

    @ConfigItem(
        keyName = "voicePoison",
        name = "Voice: Poison / Venom",
        description = "<div width='300'>When you are poisoned or venomed, or poison protection is ending (turning it on plays a preview)",
        section = combatAlertsSection,
        position = 8
    )
    default boolean voicePoison() {
        return true;
    }

    @ConfigItem(
        keyName = "chatPoison",
        name = "Chat: Poison / Venom",
        description = "<div width='300'>Chat message when you are poisoned or venomed, or poison protection is ending",
        section = combatAlertsSection,
        position = 9
    )
    default boolean chatPoison() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceStamina",
        name = "Voice: Stamina Worn Off",
        description = "<div width='300'>When a stamina potion wears off (turning it on plays a preview)",
        section = combatAlertsSection,
        position = 11
    )
    default boolean voiceStamina() {
        return true;
    }

    @ConfigItem(
        keyName = "chatStamina",
        name = "Chat: Stamina Worn Off",
        description = "Chat message when a stamina potion wears off",
        section = combatAlertsSection,
        position = 12
    )
    default boolean chatStamina() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceAmmo",
        name = "Voice: Low Ammo",
        description = "When your ammo runs low (turning it on plays a preview)",
        section = supplyAlertsSection,
        position = 2
    )
    default boolean voiceAmmo() {
        return true;
    }

    @ConfigItem(
        keyName = "chatAmmo",
        name = "Chat: Low Ammo",
        description = "Chat message when your ammo runs low",
        section = supplyAlertsSection,
        position = 3
    )
    default boolean chatAmmo() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceRunes",
        name = "Voice: Out of Runes",
        description = "<div width='300'>When you can't cast for lack of runes (turning it on plays a preview)",
        section = supplyAlertsSection,
        position = 5
    )
    default boolean voiceRunes() {
        return true;
    }

    @ConfigItem(
        keyName = "chatRunes",
        name = "Chat: Out of Runes",
        description = "Chat message when you can't cast for lack of runes",
        section = supplyAlertsSection,
        position = 6
    )
    default boolean chatRunes() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceCannon",
        name = "Voice: Cannon Low",
        description = "<div width='300'>When your cannon is low on cannonballs (turning it on plays a preview)",
        section = supplyAlertsSection,
        position = 9
    )
    default boolean voiceCannon() {
        return true;
    }

    @ConfigItem(
        keyName = "chatCannon",
        name = "Chat: Cannon Low",
        description = "Chat message when your cannon is low on cannonballs",
        section = supplyAlertsSection,
        position = 10
    )
    default boolean chatCannon() {
        return true;
    }

    @ConfigSection(
        name = "Buff Bar",
        description = "<div width='300'>Your buffs and debuffs as a row of icons with timers, like RS3's",
        position = 11,
        closedByDefault = true
    )
    String buffBarSection = "buffBarSection";

    @ConfigSection(
        name = "PvM HUD",
        description = "Combat HUD and monster hover info for non-crab monsters",
        position = 10,
        closedByDefault = true
    )
    String pvmSection = "pvmSection";

    @ConfigItem(
        keyName = "enablePvmOverlay",
        name = "Show PvM HUD",
        description = "<div width='300'>Show combat HUD (DPS, target HP bar, aggro state, weaknesses) when attacking any monster",
        section = pvmSection,
        position = 0
    )
    default boolean enablePvmOverlay() {
        return true;
    }

    @ConfigItem(
        keyName = "showHoverInfo",
        name = "Monster Hover Card",
        description = "<div width='300'>When hovering an attackable monster, show its level, health, weakness, attack style and  aggression next to the cursor (hidden for the monster type you're already fighting)",
        section = pvmSection,
        position = 1
    )
    default boolean showHoverInfo() {
        return true;
    }

    @ConfigItem(
        keyName = "dpsMode",
        name = "DPS Mode",
        description = "<div width='300'>Per Monster: each monster you fight from scratch. Per Type: keeps going while you fight the same kind of monster, resets on a different one. Session: a rolling average over the whole session",
        section = pvmSection,
        position = 3
    )
    default DpsTrackingMode dpsMode() {
        return DpsTrackingMode.PER_TARGET_TYPE;
    }

    @ConfigItem(
        keyName = "pvmLingerSeconds",
        name = "Linger After Combat",
        description = "<div width='300'>Seconds the PvM HUD and DPS history remain visible after combat ends",
        section = pvmSection,
        position = 2
    )
    @Units(Units.SECONDS)
    default int pvmLingerSeconds() {
        return 15;
    }

    @ConfigItem(
        keyName = "showPvmWeakness",
        name = "Show Weakness",
        description = "<div width='300'>Show style, elemental, or slayer bane weakness on the PvM overlay",
        section = pvmSection,
        position = 4
    )
    default boolean showPvmWeakness() {
        return true;
    }

    @ConfigItem(
        keyName = "showPvmDps",
        name = "Show DPS",
        description = "Display live or rolling session DPS on the PvM overlay",
        section = pvmSection,
        position = 5
    )
    default boolean showPvmDps() {
        return true;
    }

    @ConfigItem(
        keyName = "showPvmLastHit",
        name = "Show Last Hit",
        description = "Display continuous most recent hit dealt to PvM monsters",
        section = pvmSection,
        position = 6
    )
    default boolean showPvmLastHit() {
        return true;
    }

    @ConfigItem(
        keyName = "showPvmMaxHit",
        name = "Show Max Hit",
        description = "Display continuous highest hit dealt to PvM monsters",
        section = pvmSection,
        position = 7
    )
    default boolean showPvmMaxHit() {
        return true;
    }

    @ConfigItem(
        keyName = "showPvmAggro",
        name = "Show Aggression",
        description = "<div width='300'>Show whether the monster is aggressive and its tolerance timer",
        section = pvmSection,
        position = 8
    )
    default boolean showPvmAggro() {
        return true;
    }

    @ConfigSection(
        name = "Slayer Card",
        description = "<div width='300'>The Slayer card: your task, kills left, points and streak, supplies used, kill goals, where to go and how to get there, or the master to visit without a task",
        position = 17,
        closedByDefault = true
    )
    String slayerSection = "slayerSection";

    @ConfigItem(
        keyName = "showSlayerTask",
        name = "Show Slayer Card",
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
        description = "<div width='300'>On a task, list where to kill it (multicombat, cannon, safespot) and how to get to the first place",
        section = slayerSection,
        position = 6
    )
    default boolean showSlayerDirections() {
        return true;
    }

    @ConfigItem(
        keyName = "showSlayerMasterCard",
        name = "Master Card Without a Task",
        description = "<div width='300'>With no task, show the best Slayer master for your levels, where they are and how to get there",
        section = slayerSection,
        position = 10
    )
    default boolean showSlayerMasterCard() {
        return true;
    }

    @ConfigItem(
        keyName = "slayerPictures",
        name = "Pictures",
        description = "<div width='300'>Show the monster's picture on a task, and the Slayer master's chathead with no task",
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
        keyName = "slayerPrayer",
        name = "Protection Prayer",
        description = "<div width='300'>The protection prayer for the task, from how its monsters attack (wiki attack styles): shown when most of them use the same kind",
        section = slayerSection,
        position = 4
    )
    default boolean slayerPrayer() {
        return true;
    }

    @ConfigItem(
        keyName = "slayerRequiredItem",
        name = "Required Item",
        description = "The item the task needs (red when you don't have it)",
        section = slayerSection,
        position = 5
    )
    default boolean slayerRequiredItem() {
        return true;
    }

    @ConfigItem(
        keyName = "slayerTravelRoutes",
        name = "How To Get There",
        description = "<div width='300'>The fastest ways to the first place to kill your task, or to the Slayer master",
        section = slayerSection,
        position = 8
    )
    default boolean slayerTravelRoutes() {
        return true;
    }

    @ConfigItem(
        keyName = "slayerNextMaster",
        name = "Next Master",
        description = "<div width='300'>With no task, the next Slayer master you'll unlock and what they need",
        section = slayerSection,
        position = 11
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
        position = 7
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
        position = 9
    )
    default int slayerRoutesShown() {
        return 2;
    }

    @ConfigItem(
        keyName = "showSlayerMonsterInfo",
        name = "Slayer Info on Monsters",
        description = "<div width='300'>On the monster hover card and PvM HUD for Slayer monsters: the Slayer level needed (red if you can't damage it yet), the gear its task needs (earmuffs, nose peg, rock hammer...; red if you don't have it), whether it's your task, and Slayer XP",
        section = slayerMonstersSection,
        position = 0
    )
    default boolean showSlayerMonsterInfo() {
        return true;
    }

    @ConfigItem(
        keyName = "highlightTaskMonsters",
        name = "Highlight Task Monsters",
        description = "<div width='300'>Outline the monsters that count for your task. On a Konar task, ones outside her assigned area aren't outlined (kills there don't count)",
        section = slayerMonstersSection,
        position = 1
    )
    default boolean highlightTaskMonsters() {
        return true;
    }

    @Alpha
    @ConfigItem(
        keyName = "taskMonsterColor",
        name = "Task Monster Colour",
        description = "Outline colour for task monsters",
        section = slayerMonstersSection,
        position = 2
    )
    default Color taskMonsterColor() {
        return new Color(255, 120, 0, 200);
    }

    @ConfigItem(
        keyName = "slayerItemAlert",
        name = "Missing Slayer Item",
        description = "<div width='300'>Warn when fighting a task monster without its required item (e.g. rock hammer)",
        section = slayerAlertsSection,
        position = 0
    )
    default boolean slayerItemAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "superiorAlert",
        name = "Superior Spawned",
        description = "Alert when a superior slayer monster appears",
        section = slayerAlertsSection,
        position = 3
    )
    default boolean superiorAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "taskCompleteAlert",
        name = "Task Complete",
        description = "Alert when your slayer task is finished",
        section = slayerAlertsSection,
        position = 12
    )
    default boolean taskCompleteAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceSlayerItem",
        name = "Voice: Missing Slayer Item",
        description = "<div width='300'>When fighting a task monster without its required item (turning it on plays a preview)",
        section = slayerAlertsSection,
        position = 1
    )
    default boolean voiceSlayerItem() {
        return true;
    }

    @ConfigItem(
        keyName = "chatSlayerItem",
        name = "Chat: Missing Slayer Item",
        description = "<div width='300'>Chat message when fighting a task monster without its required item",
        section = slayerAlertsSection,
        position = 2
    )
    default boolean chatSlayerItem() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceSuperior",
        name = "Voice: Superior Spawned",
        description = "<div width='300'>When a superior slayer monster appears (turning it on plays a preview)",
        section = slayerAlertsSection,
        position = 4
    )
    default boolean voiceSuperior() {
        return true;
    }

    @ConfigItem(
        keyName = "chatSuperior",
        name = "Chat: Superior Spawned",
        description = "Chat message when a superior slayer monster appears",
        section = slayerAlertsSection,
        position = 5
    )
    default boolean chatSuperior() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceTaskComplete",
        name = "Voice: Task Complete",
        description = "<div width='300'>When your slayer task is done (turning it on plays a preview)",
        section = slayerAlertsSection,
        position = 13
    )
    default boolean voiceTaskComplete() {
        return true;
    }

    @ConfigItem(
        keyName = "chatTaskComplete",
        name = "Chat: Task Complete",
        description = "Chat message when your slayer task is done",
        section = slayerAlertsSection,
        position = 14
    )
    default boolean chatTaskComplete() {
        return true;
    }

    @ConfigItem(
        keyName = "taskHalfwayAlert",
        name = "Task Halfway",
        description = "Alert when you've done half of your slayer task",
        section = slayerAlertsSection,
        position = 6
    )
    default boolean taskHalfwayAlert() {
        return true;
    }

    @Range(max
    =
    100)
    @ConfigItem(
        keyName = "taskNearlyDoneAt",
        name = "Task Nearly Done At",
        description = "<div width='300'>Alert when this many kills are left on your slayer task (0: off)",
        section = slayerAlertsSection,
        position = 9
    )
    default int taskNearlyDoneAt() {
        return 10;
    }

    @ConfigItem(
        keyName = "voiceTaskHalfway",
        name = "Voice: Task Halfway",
        description = "<div width='300'>When half your slayer task is done (turning it on plays a preview)",
        section = slayerAlertsSection,
        position = 7
    )
    default boolean voiceTaskHalfway() {
        return true;
    }

    @ConfigItem(
        keyName = "chatTaskHalfway",
        name = "Chat: Task Halfway",
        description = "Chat message when half your slayer task is done",
        section = slayerAlertsSection,
        position = 8
    )
    default boolean chatTaskHalfway() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceTaskNearlyDone",
        name = "Voice: Task Nearly Done",
        description = "<div width='300'>When your slayer task is down to Task Nearly Done At (turning it on plays a preview)",
        section = slayerAlertsSection,
        position = 10
    )
    default boolean voiceTaskNearlyDone() {
        return true;
    }

    @ConfigItem(
        keyName = "chatTaskNearlyDone",
        name = "Chat: Task Nearly Done",
        description = "<div width='300'>Chat message when your slayer task is down to Task Nearly Done At",
        section = slayerAlertsSection,
        position = 11
    )
    default boolean chatTaskNearlyDone() {
        return true;
    }

    @ConfigItem(
        keyName = "slayerPointsStreak",
        name = "Points and Streak",
        description = "<div width='300'>Your Slayer points and task streak on the card, and a note when your next task is a milestone (10th, 50th, 100th, 250th, 1000th) so you can pick the master that gives the most points",
        section = slayerSection,
        position = 12
    )
    default boolean slayerPointsStreak() {
        return true;
    }

    @ConfigItem(
        keyName = "slayerSupplies",
        name = "Supplies Used",
        description = "<div width='300'>Food eaten and potion doses drunk on this task (kept if you log out and back in on the same task)",
        section = slayerSection,
        position = 13
    )
    default boolean slayerSupplies() {
        return true;
    }

    @ConfigItem(
        keyName = "killGoals",
        name = "Kill Goals",
        description = "<div width='300'>Right-click a monster > Set kill goal and type how many to kill. Progress shows on the monster hover card and under Kill Goals on this card; the card's right-click menu clears finished ones. Saved with your RuneScape profile",
        section = slayerSection,
        position = 14
    )
    default boolean killGoals() {
        return true;
    }

    @ConfigItem(
        keyName = "finishOffMarker",
        name = "Finish-Off Marker",
        description = "<div width='300'>The item's picture over a lizard, gargoyle, rockslug or zygomite once it's low enough to finish with an ice cooler, rock hammer, bag of salt or fungicide. While RuneLite's Slayer plugin marks your task monsters itself (its Show Monster Weakness), it's left to that one",
        section = slayerMonstersSection,
        position = 3
    )
    default boolean finishOffMarker() {
        return true;
    }

    @ConfigItem(
        keyName = "slayerMilestoneAlert",
        name = "Next Task Is a Milestone",
        description = "<div width='300'>Alert when you finish a task and the next one is a 10th, 50th, 100th, 250th or 1000th (bonus points), so you can pick your master",
        section = slayerAlertsSection,
        position = 15
    )
    default boolean slayerMilestoneAlert() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceSlayerMilestone",
        name = "Voice: Next Task Milestone",
        description = "<div width='300'>When your next slayer task is a milestone (turning it on plays a preview)",
        section = slayerAlertsSection,
        position = 16
    )
    default boolean voiceSlayerMilestone() {
        return true;
    }

    @ConfigItem(
        keyName = "chatSlayerMilestone",
        name = "Chat: Next Task Milestone",
        description = "Chat message when your next slayer task is a milestone",
        section = slayerAlertsSection,
        position = 17
    )
    default boolean chatSlayerMilestone() {
        return true;
    }

    @ConfigItem(
        keyName = "voiceKillGoal",
        name = "Voice: Kill Goal Reached",
        description = "<div width='300'>When you reach a kill goal you set on a monster (turning it on plays a preview)",
        section = slayerAlertsSection,
        position = 18
    )
    default boolean voiceKillGoal() {
        return true;
    }

    @ConfigItem(
        keyName = "chatKillGoal",
        name = "Chat: Kill Goal Reached",
        description = "Chat message when you reach a kill goal",
        section = slayerAlertsSection,
        position = 19
    )
    default boolean chatKillGoal() {
        return true;
    }

    @ConfigItem(
        keyName = "wrongPrayerAlert",
        name = "Wrong Protection Prayer",
        description = "<div width='300'>Alert when a monster attacking you only attacks one way (from the wiki) and your protection prayer is a different one. Once per monster; nothing about when it attacks next. With no protection prayer on it stays quiet",
        section = combatAlertsSection,
        position = 13
    )
    default boolean wrongPrayerAlert() {
        return false;
    }

    @ConfigItem(
        keyName = "voiceWrongPrayer",
        name = "Voice: Wrong Prayer",
        description = "<div width='300'>When your protection prayer doesn't cover a monster's attacks (turning it on plays a preview)",
        section = combatAlertsSection,
        position = 14
    )
    default boolean voiceWrongPrayer() {
        return true;
    }

    @ConfigItem(
        keyName = "chatWrongPrayer",
        name = "Chat: Wrong Prayer",
        description = "<div width='300'>Chat message when your protection prayer doesn't cover a monster's attacks",
        section = combatAlertsSection,
        position = 15
    )
    default boolean chatWrongPrayer() {
        return true;
    }

    @ConfigItem(
        keyName = "unpottedAlert",
        name = "Unpotted Reminder",
        description = "<div width='300'>Alert when you start a fight with no combat boost (see Boosts That Count); at most once every two minutes",
        section = combatAlertsSection,
        position = 16
    )
    default boolean unpottedAlert() {
        return false;
    }

    @ConfigItem(
        keyName = "unpottedBoosts",
        name = "Boosts That Count",
        description = "<div width='300'>Any Combat Stat: any of Attack, Strength, Defence, Ranged or Magic above your level. Your Style's: Attack or Strength for melee, Ranged for ranged, Magic for magic",
        section = combatAlertsSection,
        position = 17
    )
    default UnpottedBoosts unpottedBoosts() {
        return UnpottedBoosts.ANY_COMBAT;
    }

    @ConfigItem(
        keyName = "voiceUnpotted",
        name = "Voice: Unpotted",
        description = "<div width='300'>When you start a fight with no combat boost (turning it on plays a preview)",
        section = combatAlertsSection,
        position = 18
    )
    default boolean voiceUnpotted() {
        return true;
    }

    @ConfigItem(
        keyName = "chatUnpotted",
        name = "Chat: Unpotted",
        description = "Chat message when you start a fight with no combat boost",
        section = combatAlertsSection,
        position = 19
    )
    default boolean chatUnpotted() {
        return true;
    }

    @ConfigItem(
        keyName = "showMonsterMaxHit",
        name = "Show Max Hit and Speed",
        description = "<div width='300'>On the monster hover card: the wiki's max hit for each of its attacks and how often it attacks (game ticks and seconds)",
        section = pvmSection,
        position = 10
    )
    default boolean showMonsterMaxHit() {
        return true;
    }

    /** Which boosts count for the unpotted reminder. */
    enum UnpottedBoosts {
        ANY_COMBAT("Any Combat Stat"),
        YOUR_STYLE("Your Style's");

        private final String label;

        UnpottedBoosts(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    @ConfigItem(
        keyName = "showBuffBar",
        name = "Show Buff Bar",
        description = "<div width='300'>Your buffs (potion boosts, overload, antifire, stamina, poison protection, prayer regeneration, vengeance, staff of the dead's special, the imbued heart's cooldown, boosted stats) and debuffs (poison, venom, frozen, teleblocked, drained stats) as icons with timers; hover one for its name. Move it with Alt + drag. With it on, the PvM HUD leaves the potion rows to it",
        section = buffBarSection,
        position = 0
    )
    default boolean showBuffBar() {
        return true;
    }

    @ConfigItem(
        keyName = "buffBarDebuffs",
        name = "Show Debuffs",
        description = "Also show poison, venom and drained stats (red border)",
        section = buffBarSection,
        position = 1
    )
    default boolean buffBarDebuffs() {
        return true;
    }

    @ConfigItem(
        keyName = "buffBarLayout",
        name = "Layout",
        description = "Icons in a row or a column",
        section = buffBarSection,
        position = 2
    )
    default BarLayout buffBarLayout() {
        return BarLayout.HORIZONTAL;
    }

    @Range(min
    =
    20,
    max
    =
    48)
    @ConfigItem(
        keyName = "buffBarIconSize",
        name = "Icon Size",
        description = "Size of each icon",
        section = buffBarSection,
        position = 3
    )
    default int buffBarIconSize() {
        return 32;
    }

    @ConfigItem(
        keyName = "showMonsterEffects",
        name = "Show Monster Effects",
        description = "<div width='300'>On the HUD: what's on the monster you fight (poisoned, venomed, burning, frozen with time left) and its Defence after your special attacks lower it (Dragon warhammer, Elder maul, Bandos godsword, Arclight)",
        section = pvmSection,
        position = 9
    )
    default boolean showMonsterEffects() {
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
