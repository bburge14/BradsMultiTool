package com.snuffys.skilltracker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * On the game's Skills tab: a thin gold bar along the bottom of each skill you have a goal in, filled by how far
 * you are from where you set the goal to reaching it (the goal bar on the tracker card). Drawn over the tab, like
 * the Skills Progress plugin's bars; the tab itself isn't changed. With the Plugin Hub's Skills Tab Progress Bars on
 * (it draws level progress, and its own goal bar, along the bottom), ours sits just above its bars instead of on
 * them, by its own height settings.
 */
@Singleton
public class BradsMultiToolSkillsTabOverlay extends Overlay {

    private static final int BAR_HEIGHT = 3;
    // The skill stone's border, so the bar sits inside it
    private static final int INDENT = 4;
    private static final Color BACKGROUND = new Color(0, 0, 0, 170);

    private final Client client;
    private final SkillTracker tracker;
    private final SkillTrackerConfig config;
    private final net.runelite.client.plugins.PluginManager pluginManager;
    private final net.runelite.client.config.ConfigManager configManager;
    // How much of the bottom Skills Tab Progress Bars uses (pixels), looked at every couple of seconds
    private int theirHeight;
    private long theirCheckedAt;

    // The Plugin Hub's Skills Tab Progress Bars (only read: whether it's on, and its bar settings)
    private static final String THEIR_PLUGIN = "SkillsTabProgressBarsPlugin";
    private static final String THEIR_GROUP = "skillstabprogressbars";
    private static final int THEIR_MAX_HEIGHT = 30;

    @Inject
    BradsMultiToolSkillsTabOverlay(Client client, SkillTracker tracker, SkillTrackerConfig config,
                                   net.runelite.client.plugins.PluginManager pluginManager,
                                   net.runelite.client.config.ConfigManager configManager) {
        this.client = client;
        this.tracker = tracker;
        this.config = config;
        this.pluginManager = pluginManager;
        this.configManager = configManager;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(PRIORITY_HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.skillsTabGoalBars()) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (now - theirCheckedAt > 2000) {
            theirCheckedAt = now;
            theirHeight = theirBarsHeight();
        }
        for (Skill skill : tracker.getGoalSkills()) {
            int goal = tracker.getSkillGoal(skill);
            int component = component(skill);
            Widget stone = component == -1 ? null : client.getWidget(component);
            if (goal <= 0 || stone == null || stone.isHidden()) {
                continue;
            }
            Rectangle at = stone.getBounds();
            if (at == null || at.width <= INDENT * 2) {
                continue;
            }
            int xp = client.getSkillExperience(skill);
            double progress = progress(xp, tracker.getSkillGoalStart(skill), goal);
            int x = at.x + INDENT;
            // Our bar at the bottom edge, or just above Skills Tab Progress Bars' bars (with a pixel between)
            int y = at.y + at.height - BAR_HEIGHT - (theirHeight > 0 ? theirHeight + 1 : 2);
            int width = at.width - INDENT * 2;
            graphics.setColor(BACKGROUND);
            graphics.fillRect(x, y, width, BAR_HEIGHT);
            graphics.setColor(BradsMultiToolPanel.GOAL_FILL);
            graphics.fillRect(x, y, (int) Math.round(width * progress), BAR_HEIGHT);
        }
        return null;
    }

    // The height Skills Tab Progress Bars' bars take at the bottom of a skill: its bar height, twice that with its
    // goal bars on (halved, as it does, when that would pass half the skill); 0 when it's off
    private int theirBarsHeight() {
        boolean on = false;
        for (net.runelite.client.plugins.Plugin plugin : pluginManager.getPlugins()) {
            if (plugin.getClass().getSimpleName().equals(THEIR_PLUGIN) && pluginManager.isPluginEnabled(plugin)) {
                on = true;
                break;
            }
        }
        if (!on) {
            return 0;
        }
        return theirHeight(number(configManager.getConfiguration(THEIR_GROUP, "barHeight"), 2),
            "true".equals(configManager.getConfiguration(THEIR_GROUP, "showGoals")));
    }

    static int theirHeight(int barHeight, boolean goals) {
        if (!goals) {
            return barHeight;
        }
        return barHeight > THEIR_MAX_HEIGHT / 2 ? (barHeight / 2) * 2 : barHeight * 2;
    }

    private static int number(String value, int otherwise) {
        try {
            return value == null ? otherwise : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return otherwise;
        }
    }

    /** How far from where the goal was set (start) to the goal you are, 0 to 1 (as the tracker's goal bar). */
    static double progress(int xp, int start, int goal) {
        int from = Math.min(start, xp);
        if (goal <= from) {
            return 1;
        }
        return Math.max(0, Math.min(1, (double) (xp - from) / (goal - from)));
    }

    /** The Skills tab's stone for a skill (InterfaceID.Stats), or -1. */
    static int component(Skill skill) {
        switch (skill) {
            case ATTACK: return InterfaceID.Stats.ATTACK;
            case STRENGTH: return InterfaceID.Stats.STRENGTH;
            case DEFENCE: return InterfaceID.Stats.DEFENCE;
            case RANGED: return InterfaceID.Stats.RANGED;
            case PRAYER: return InterfaceID.Stats.PRAYER;
            case MAGIC: return InterfaceID.Stats.MAGIC;
            case RUNECRAFT: return InterfaceID.Stats.RUNECRAFT;
            case CONSTRUCTION: return InterfaceID.Stats.CONSTRUCTION;
            case HITPOINTS: return InterfaceID.Stats.HITPOINTS;
            case AGILITY: return InterfaceID.Stats.AGILITY;
            case HERBLORE: return InterfaceID.Stats.HERBLORE;
            case THIEVING: return InterfaceID.Stats.THIEVING;
            case CRAFTING: return InterfaceID.Stats.CRAFTING;
            case FLETCHING: return InterfaceID.Stats.FLETCHING;
            case SLAYER: return InterfaceID.Stats.SLAYER;
            case HUNTER: return InterfaceID.Stats.HUNTER;
            case MINING: return InterfaceID.Stats.MINING;
            case SMITHING: return InterfaceID.Stats.SMITHING;
            case FISHING: return InterfaceID.Stats.FISHING;
            case COOKING: return InterfaceID.Stats.COOKING;
            case FIREMAKING: return InterfaceID.Stats.FIREMAKING;
            case WOODCUTTING: return InterfaceID.Stats.WOODCUTTING;
            case FARMING: return InterfaceID.Stats.FARMING;
            case SAILING: return InterfaceID.Stats.SAILING;
            default: return -1;
        }
    }
}
