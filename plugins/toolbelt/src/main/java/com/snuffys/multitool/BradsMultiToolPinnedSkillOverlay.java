package com.snuffys.multitool;

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
 * progress, XP/hr, XP gained, time to level, and its goal with the suggested method. Right-click the
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
        line("Gained:", BradsMultiToolPanel.shortNumber(plugin.getSkillGained(skill)), Color.WHITE);

        int goalXp = plugin.getSkillGoal(skill);
        if (goalXp > 0 && config.showTrackerGoal()) {
            int goalStart = Math.min(plugin.getSkillGoalStart(skill), xp);
            double progress = goalXp > goalStart ? (double) (xp - goalStart) / (goalXp - goalStart) : 1;
            int goalLevel = Experience.getLevelForXp(goalXp);
            String target = goalXp == Experience.getXpForLevel(goalLevel) ? "Goal: Lvl " + goalLevel : "Goal: " + BradsMultiToolPanel.shortNumber(goalXp);
            int left = Math.max(0, goalXp - xp);
            String eta = left == 0 ? "Done!" : rate > 0 && !paused ? BradsMultiToolPanel.formatDuration(left * 3600L / rate) : BradsMultiToolPanel.shortNumber(left) + " left";
            panelComponent.getChildren().add(bar(progress, target, String.format("%.1f%%", Math.min(1, progress) * 100), eta, BradsMultiToolPanel.GOAL_FILL));
            String suggestion = suggestion(skill, level);
            if (suggestion != null) {
                panelComponent.getChildren().add(new TextBlockComponent(suggestion, SOFT, false));
            }
            // What to make or use from your items ("Burn: Yew logs (you have 200)")
            GoalLines.ItemLine item = config.showTrainingSuggestion() ? GoalLines.itemLine(plugin, skill) : null;
            if (item != null) {
                panelComponent.getChildren().add(new TextBlockComponent(item.label + " " + item.item + " " + item.have, SOFT, false));
            }
            String killRate = GoalLines.killRate(plugin, skill);
            if (killRate != null) {
                panelComponent.getChildren().add(new TextBlockComponent("Kill rate: " + killRate, SOFT, false));
            }
            Map<String, Boolean> tools = config.showTrainingSuggestion()
                ? GoalLines.tools(plugin, skill, level) : Collections.emptyMap();
            if (!tools.isEmpty()) {
                line("Needs:", String.join(", ", tools.keySet()),
                    tools.containsValue(false) ? HudColors.BAD : HudColors.GOOD);
            }
        }
        // Actions (or kills) left, as on the tracker card
        if (config.showTrackerActions() && (goalXp > 0 || !GoalLines.KILL_SKILLS.contains(skill))) {
            GoalLines.Actions count = GoalLines.actions(plugin, config, skill, xp, stepEndLevel(skill, level, goalXp));
            line(count.name + ":", count.value, Color.WHITE);
        }
        return super.render(graphics);
    }

    // Where the plan's current step ends (the suggested method changes there before the goal), or 0
    private int stepEndLevel(Skill skill, int level, int goalXp) {
        if (goalXp <= 0 || !config.showTrainingSuggestion() || plugin.getGoalTarget(skill) != null) {
            return 0;
        }
        List<TrainingAdvisor.Stage> plan = plugin.getTrainingAdvisor().plan(skill, level, Experience.getLevelForXp(goalXp));
        return plan.size() > 1 ? plan.get(0).to : 0;
    }

    // What to train for the goal: your pick, else the suggestion for your Training Focus
    private String suggestion(Skill skill, int level) {
        if (!config.showTrainingSuggestion()) {
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
        return now.label + " " + now.text + (plan.size() > 1 ? " (to " + plan.get(0).to + ")" : "");
    }

    private static ProgressBarComponent bar(double progress, String left, String centre, String right, Color fill) {
        ProgressBarComponent bar = new ProgressBarComponent();
        bar.setMinimum(0);
        bar.setMaximum(1000);
        bar.setValue(Math.max(0, Math.min(1, progress)) * 1000);
        bar.setLabelDisplayMode(ProgressBarComponent.LabelDisplayMode.TEXT_ONLY);
        bar.setLeftLabel(left);
        bar.setCenterLabel(centre);
        bar.setRightLabel(right);
        bar.setForegroundColor(fill);
        bar.setBackgroundColor(BradsMultiToolPanel.BAR_BACKGROUND);
        return bar;
    }

    private void line(String left, String right, Color color) {
        panelComponent.getChildren().add(LineComponent.builder().left(left).right(right).rightColor(color).build());
    }
}
