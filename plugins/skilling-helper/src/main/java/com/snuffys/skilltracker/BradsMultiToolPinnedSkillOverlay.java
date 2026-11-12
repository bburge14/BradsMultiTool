package com.snuffys.skilltracker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.ProgressBarComponent;

/**
 * One skill from the tracker pinned to the game screen (right-click a tracker card > Pin to screen): level
 * progress, XP/hr, XP gained, time to level, and its goal (with a skill plan's milestone) and the suggested method. Right-click the
 * overlay > Unpin to remove it. Only one skill is pinned at a time.
 */
@Singleton
public class BradsMultiToolPinnedSkillOverlay extends OverlayPanel {

    private static final Color TITLE = new Color(255, 185, 0);
    private static final Color SOFT = new Color(190, 190, 190);

    private final Client client;
    private final SkillTracker plugin;
    private final SkillTrackerConfig config;
    private final SkillIconManager skillIcons;

    @Inject
    BradsMultiToolPinnedSkillOverlay(Client client, SkillTracker plugin, SkillTrackerConfig config,
                                     SkillIconManager skillIcons) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.skillIcons = skillIcons;
        setPosition(OverlayPosition.TOP_LEFT);
        panelComponent.setPreferredSize(new Dimension(180, 0));
        addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Unpin", "Pinned skill", e -> plugin.setPinnedSkill(null));
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        Skill skill = plugin.getPinnedSkill();
        if (skill == null || client.getGameState() != GameState.LOGGED_IN) {
            return null;
        }
        graphics.setFont(FontManager.getRunescapeSmallFont());
        int xp = client.getSkillExperience(skill);
        int level = Experience.getLevelForXp(xp);
        int rate = plugin.getSkillXpPerHour(skill);
        boolean paused = plugin.isSkillPaused(skill);

        panelComponent.getChildren().add(new PortraitHeaderComponent(skillIcons.getSkillImage(skill, false),
            skill.getName(), TITLE, "Level " + level + "  (" + String.format("%,d", xp) + " XP)"));

        if (level < Experience.MAX_REAL_LEVEL) {
            int start = Experience.getXpForLevel(level);
            int end = Experience.getXpForLevel(level + 1);
            double progress = (double) (xp - start) / (end - start);
            panelComponent.getChildren().add(bar(progress, "Lvl " + level, String.format("%.1f%%", progress * 100), "Lvl " + (level + 1),
                progress < 1 / 3.0 ? BradsMultiToolPanel.BAR_LOW : progress < 2 / 3.0 ? BradsMultiToolPanel.BAR_MID : BradsMultiToolPanel.BAR_HIGH));
            String ttl = rate > 0 && !paused ? BradsMultiToolPanel.formatDuration((end - xp) * 3600L / rate) : "--";
            line("TTL:", ttl, Color.WHITE);
        }
        line("XP/hr:", plugin.isSkillAutoPaused(skill) ? "Idle" : paused ? "Paused" : BradsMultiToolPanel.shortNumber(rate), Color.WHITE);
        // Compact Cards: the bars, XP/hr and time to level only
        boolean full = !config.compactOverlays();
        if (full) {
            line("Gained:", BradsMultiToolPanel.shortNumber(plugin.getSkillGained(skill)), Color.WHITE);
        }
        // Logs, fish, ore or food this session, with how many an hour (Show Items Gathered)
        if (full && config.showTrackerItems()) {
            for (String[] items : plugin.itemLines(skill)) {
                line(items[0] + ":", items[1], Color.WHITE);
            }
        }
        // Hunter: your Hunters' Rumour (Rumour Card)
        if (skill == Skill.HUNTER) {
            for (String[] rumour : plugin.getHunterRumours().lines()) {
                panelComponent.getChildren().add(new TextBlockComponent(rumour[0] + " " + rumour[1], SOFT, false));
            }
        }

        int goalXp = plugin.getSkillGoal(skill);
        if (goalXp > 0 && config.showTrackerGoal()) {
            int goalStart = Math.min(plugin.getSkillGoalStart(skill), xp);
            double progress = goalXp > goalStart ? (double) (xp - goalStart) / (goalXp - goalStart) : 1;
            int goalLevel = Experience.getLevelForXp(goalXp);
            String target = "Goal: " + plugin.getGoalLabel(skill, goalXp);
            int left = Math.max(0, goalXp - xp);
            String eta = left == 0 ? "Done!" : rate > 0 && !paused ? BradsMultiToolPanel.formatDuration(left * 3600L / rate) : BradsMultiToolPanel.shortNumber(left) + " xp left";
            // (a supplies goal counts what's still to get, as its step does)
            SkillPlans.SupplyState supplies = plugin.getGoalSupplies(skill, goalXp);
            if (supplies != null && left > 0 && (rate <= 0 || paused)) {
                eta = String.format("%,d", supplies.toGet) + " to get";
            }
            panelComponent.getChildren().add(bar(progress, target, String.format("%.1f%%", Math.min(1, progress) * 100), eta, BradsMultiToolPanel.GOAL_FILL));
            // Your skill plan's milestone (exported from the Planner): its own bar, from its start to its end
            SkillPlans.Step milestone = left > 0 ? plugin.getPlanMilestone(skill, level) : null;
            if (milestone != null) {
                GoalLines.StepBar step = GoalLines.stepBar(plugin, skill, milestone, xp, rate, paused);
                // (a little room between it and the goal bar, as on the sidebar card)
                panelComponent.getChildren().add(new Spacer(2));
                panelComponent.getChildren().add(bar(step.progress, step.label, String.format("%.1f%%", step.progress * 100),
                    step.right, BradsMultiToolPanel.MILESTONE_FILL));
            }
            String suggestion = suggestion(skill, level);
            if (suggestion != null) {
                panelComponent.getChildren().add(new TextBlockComponent(suggestion, SOFT, false));
                String where = plugin.getGoalTarget(skill) == null ? GoalLines.where(plugin, skill, currentSuggestion(skill, level)) : null;
                if (where != null) {
                    panelComponent.getChildren().add(new TextBlockComponent("Where: " + where, SOFT, false));
                }
            }
            // What to make or use from your items ("Burn: Yew logs (you have 200)")
            GoalLines.ItemLine item = full && config.showTrainingSuggestion() ? GoalLines.itemLine(plugin, skill) : null;
            if (item != null) {
                panelComponent.getChildren().add(new TextBlockComponent(item.label + " " + item.item + " " + item.have, SOFT, false));
            }
            double banked = plugin.getGoalItems().bankedXp(skill);
            if (full && banked >= 1 && config.showBankedXp()) {
                line("Banked:", BradsMultiToolPanel.shortNumber((long) banked) + " XP"
                    + BradsMultiToolPanel.bankedLevel(xp, banked).replace(" (to level ", " (lvl ").replace(")", ")"), Color.WHITE);
            }
            long materials = plugin.getGoalItems().materialsValue(skill);
            if (full && materials > 0 && config.showMaterialsValue()) {
                line("Materials worth:", BradsMultiToolPanel.shortNumber(materials) + " gp", Color.WHITE);
            }
            String killRate = GoalLines.killRate(plugin, skill);
            if (killRate != null) {
                panelComponent.getChildren().add(new TextBlockComponent("Kill rate: " + killRate, SOFT, false));
            }
            Map<String, Boolean> tools = full && config.showTrainingSuggestion()
                ? GoalLines.tools(plugin, skill, level) : Collections.emptyMap();
            if (!tools.isEmpty()) {
                // One line across the card, wrapped like the Tracking line (long item names don't fit the right column)
                panelComponent.getChildren().add(new TextBlockComponent("Needs: " + String.join(", ", tools.keySet()),
                    tools.containsValue(false) ? HudColors.BAD : HudColors.GOOD, false));
            }
        }
        // Actions (or kills) left, as on the tracker card
        if (full && config.showTrackerActions() && (goalXp > 0 || !GoalLines.KILL_SKILLS.contains(skill))) {
            GoalLines.Actions count = GoalLines.actions(plugin, config, skill, xp, stepEndLevel(skill, level, goalXp));
            line(count.longName() + ":", count.value, Color.WHITE);
        }
        return super.render(graphics);
    }

    // Where the plan's current step ends (the suggested method changes there before the goal), or 0
    private int stepEndLevel(Skill skill, int level, int goalXp) {
        if (goalXp <= 0 || !config.showTrainingSuggestion() || config.compactOverlays() || plugin.getGoalTarget(skill) != null) {
            return 0;
        }
        List<TrainingAdvisor.Stage> plan = plugin.getTrainingAdvisor().plan(skill, level, Experience.getLevelForXp(goalXp));
        return plan.size() > 1 ? plan.get(0).to : 0;
    }

    private TrainingAdvisor.Suggestion currentSuggestion(Skill skill, int level) {
        List<TrainingAdvisor.Stage> plan = plugin.getTrainingAdvisor().plan(skill, level,
            Experience.getLevelForXp(plugin.getSkillGoal(skill)));
        return plan.isEmpty() ? null : plan.get(0).suggestion;
    }

    // What to train for the goal: your pick, else the suggestion for your Training Focus
    private String suggestion(Skill skill, int level) {
        if (!config.showTrainingSuggestion() || config.compactOverlays()) {
            return null;
        }
        String target = plugin.getGoalTarget(skill);
        if (target != null) {
            return "Tracking: " + plugin.describeGoalTarget(skill);
        }
        // The current leg of the plan, and the level it lasts to when the method changes before the goal
        List<TrainingAdvisor.Stage> plan = plugin.getTrainingAdvisor().plan(skill, level,
            Experience.getLevelForXp(plugin.getSkillGoal(skill)));
        if (plan.isEmpty()) {
            return null;
        }
        TrainingAdvisor.Suggestion now = plan.get(0).suggestion;
        if (now.planStep != null) {
            return "Plan: " + now.text + (now.planStep.supplies.isEmpty() ? " (to " + now.planStep.to + ")" : " (supplies)");
        }
        return now.label + " " + now.text + (plan.size() > 1 ? " (to " + plan.get(0).to + ")" : "");
    }

    // Blank room of a set height between two parts of the panel
    private static final class Spacer implements net.runelite.client.ui.overlay.components.LayoutableRenderableEntity {
        private final int height;
        private final java.awt.Rectangle bounds = new java.awt.Rectangle();

        Spacer(int height) {
            this.height = height;
        }

        @Override
        public Dimension render(Graphics2D graphics) {
            bounds.setSize(0, height);
            return new Dimension(0, height);
        }

        @Override
        public java.awt.Rectangle getBounds() {
            return bounds;
        }

        @Override
        public void setPreferredLocation(java.awt.Point position) {
            bounds.setLocation(position);
        }

        @Override
        public void setPreferredSize(Dimension dimension) {
        }
    }

    private static ProgressBarComponent bar(double progress, String left, String centre, String right, Color fill) {
        ProgressBarComponent bar = new ProgressBarComponent();
        bar.setMinimum(0);
        bar.setMaximum(1000);
        bar.setValue(Math.max(0, Math.min(1, progress)) * 1000);
        bar.setLabelDisplayMode(ProgressBarComponent.LabelDisplayMode.TEXT_ONLY);
        bar.setLeftLabel(left);
        // (the overlay's bar puts its middle text in the middle whatever: a long left label ("Goal: Supplies") would run
        // into it, so then it's left out, as the sidebar's bar does)
        bar.setCenterLabel(left.length() > 12 || left.length() + right.length() > 20 ? "" : centre);
        bar.setRightLabel(right);
        bar.setForegroundColor(fill);
        bar.setBackgroundColor(BradsMultiToolPanel.BAR_BACKGROUND);
        return bar;
    }

    private void line(String left, String right, Color color) {
        panelComponent.getChildren().add(LineComponent.builder().left(left).right(right).rightColor(color).build());
    }
}
