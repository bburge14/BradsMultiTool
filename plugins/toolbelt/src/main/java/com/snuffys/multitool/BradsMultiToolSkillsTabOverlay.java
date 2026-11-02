package com.snuffys.multitool;

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
 * the Skills Progress plugin's bars; the tab itself isn't changed.
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

    @Inject
    BradsMultiToolSkillsTabOverlay(Client client, SkillTracker tracker, SkillTrackerConfig config) {
        this.client = client;
        this.tracker = tracker;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(PRIORITY_HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.skillsTabGoalBars()) {
            return null;
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
            int y = at.y + at.height - BAR_HEIGHT - 2;
            int width = at.width - INDENT * 2;
            graphics.setColor(BACKGROUND);
            graphics.fillRect(x, y, width, BAR_HEIGHT);
            graphics.setColor(BradsMultiToolPanel.GOAL_FILL);
            graphics.fillRect(x, y, (int) Math.round(width * progress), BAR_HEIGHT);
        }
        return null;
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
