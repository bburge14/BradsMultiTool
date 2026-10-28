package com.bradsmultitool;

import java.awt.Color;
import java.awt.Image;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;

/**
 * The Slayer lines for a Slayer monster, shared by the monster hover card and the PvM HUD: the Slayer level
 * it needs (red when you're below it, as you can't damage it), the item its task needs (earmuffs, nose
 * peg, rock hammer...; red when you don't have it), whether it's your task, and its Slayer XP.
 */
@Singleton
class SlayerMonsterInfo {

    private static final Color TITLE = new Color(255, 185, 0);
    private static final Color SOFT = new Color(180, 180, 180);

    private final Client client;
    private final BradsMultiToolPlugin plugin;
    private final SkillIconManager skillIcons;
    private final SlayerData slayerData;

    /** How a monster stands against your task. */
    enum Match { NOT_TASK, TASK, WRONG_PLACE }

    @Inject
    SlayerMonsterInfo(Client client, BradsMultiToolPlugin plugin, SkillIconManager skillIcons, SlayerData slayerData) {
        this.client = client;
        this.plugin = plugin;
        this.skillIcons = skillIcons;
        this.slayerData = slayerData;
    }

    /**
     * Whether this monster counts for your task: TASK, or WRONG_PLACE when Konar assigned somewhere else
     * (kills elsewhere don't count), else NOT_TASK.
     */
    Match match(NPC npc, MonsterData.Stats stats) {
        SlayerTracker tracker = plugin.getSlayerTracker();
        if (!tracker.hadTask() || stats == null || stats.slayerCategory == null) {
            return Match.NOT_TASK;
        }
        if (!tracker.isTask(stats.slayerCategory) && !tracker.isTaskMonster(npc)) {
            return Match.NOT_TASK;
        }
        List<SlayerData.Place> assigned = slayerData.assignedPlaces(slayerData.task(tracker.getTaskName()), tracker.getKonarArea());
        if (assigned.isEmpty() || npc.getWorldLocation() == null) {
            return Match.TASK;
        }
        WorldPoint at = npc.getWorldLocation();
        boolean mapped = false;
        for (SlayerData.Place place : assigned) {
            mapped |= place.area != null;
            if (place.contains(at.getX(), at.getY())) {
                return Match.TASK;
            }
        }
        // Without map pins for the assigned place we can't tell, so count it
        return mapped ? Match.WRONG_PLACE : Match.TASK;
    }

    void addTo(PanelComponent panel, NPC npc, MonsterData.Stats stats) {
        if (stats == null || !stats.isSlayerMonster()) {
            return;
        }
        panel.getChildren().add(TextBlockComponent.heading("Slayer", TITLE));
        if (stats.slayerLevel > 1) {
            boolean canHit = client.getRealSkillLevel(Skill.SLAYER) >= stats.slayerLevel;
            Image icon = skillIcons.getSkillImage(Skill.SLAYER, true);
            panel.getChildren().add(IconLineComponent.textThenIcon("Requires:", icon,
                "Level " + stats.slayerLevel + (canHit ? "" : " (can't damage)"),
                canHit ? BradsMultiToolOverlay.GOOD : BradsMultiToolOverlay.BAD));
        }
        SlayerTracker tracker = plugin.getSlayerTracker();
        SlayerTracker.Requirement needs = SlayerTracker.requirementFor(stats.slayerCategory);
        if (needs == null) {
            needs = SlayerTracker.requirementFor(npc.getName());
        }
        if (needs != null) {
            boolean has = tracker.carries(needs);
            line(panel, "Wear / Use:", needs.display + (has ? "" : " (missing)"), has ? BradsMultiToolOverlay.GOOD : BradsMultiToolOverlay.BAD);
        }
        if (stats.slayerCategory != null) {
            Match match = match(npc, stats);
            line(panel, "Task:", stats.slayerCategory + (match == Match.TASK ? " (on task)" : ""),
                match == Match.TASK ? BradsMultiToolOverlay.GOOD : SOFT);
            if (match == Match.WRONG_PLACE) {
                // Konar wants them somewhere else; kills here don't count
                panel.getChildren().add(new TextBlockComponent("Wrong place: Konar wants these in "
                    + tracker.getKonarArea() + ", kills here won't count", BradsMultiToolOverlay.BAD, false));
            } else if (match == Match.NOT_TASK && tracker.hadTask()) {
                line(panel, "Your Task:", tracker.getTaskName(), SOFT);
            }
        }
        if (stats.slayerXp > 0) {
            line(panel, "Slayer XP:", stats.slayerXp == Math.floor(stats.slayerXp)
                ? String.valueOf((long) stats.slayerXp) : String.valueOf(stats.slayerXp), Color.WHITE);
        }
    }

    private static void line(PanelComponent panel, String left, String right, Color color) {
        panel.getChildren().add(LineComponent.builder().left(left).right(right).rightColor(color).build());
    }
}
