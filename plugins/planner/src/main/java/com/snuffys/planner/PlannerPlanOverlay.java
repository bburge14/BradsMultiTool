package com.snuffys.planner;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.ProgressBarComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * The Planner's plan on the game screen, for when the Skilling Helper isn't installed (its pinned skill card shows the
 * plan otherwise): the plan of the skill you're training (the one open in the Calculator), the milestone you're on
 * with its method, a bar to its end with the XP and actions left, the next milestone, and your materials for this one
 * (green when you have them all). Compact Overlays: the plan, the bar and the actions left. Only reads your XP and
 * what you carry and bank.
 */
class PlannerPlanOverlay extends OverlayPanel {

    private static final Color TITLE = new Color(255, 185, 0);
    private static final Color BAR = new Color(70, 140, 230);
    private static final Color BAR_BACKGROUND = new Color(40, 40, 40, 200);
    private static final Color SOFT = new Color(200, 200, 200);

    private final Client client;
    private final PlannerConfig config;
    private final PlannerCalculator calc;
    private final CalculatorData data;
    private final RecipeData recipes;
    private final TrainingData training;
    @Inject
    private net.runelite.client.config.RuneLiteConfig runeLiteConfig;

    // The materials for the milestone you're on, worked out again only when what they depend on changes
    private String needsKey;
    private List<PlannerCalculator.Need> needs = Collections.emptyList();

    @Inject
    PlannerPlanOverlay(Client client, PlannerConfig config, PlannerCalculator calc, CalculatorData data, RecipeData recipes,
                       TrainingData training) {
        this.client = client;
        this.config = config;
        this.calc = calc;
        this.data = data;
        this.recipes = recipes;
        this.training = training;
        setPosition(OverlayPosition.TOP_LEFT);
        panelComponent.setPreferredSize(new Dimension(190, 0));
        addMenuEntry(net.runelite.api.MenuAction.RUNELITE_OVERLAY, "Unpin", "Plan", e -> calc.setPinnedPlanSkill(null));
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.plannerPlanOverlay() || client.getGameState() != GameState.LOGGED_IN || calc.trackerInstalled()
            || !calc.canPlan()) {
            return null;
        }
        // A pinned plan stays up; otherwise the plan of the skill you're training
        Skill pinned = calc.pinnedPlanSkill();
        Skill skill = pinned != null ? pinned : calc.lastTrained();
        SkillPlans.Plan plan = skill == null ? null : calc.plan(skill);
        if (plan == null || plan.steps.isEmpty()) {
            return null;
        }
        if (runeLiteConfig != null) {
            HudColors.theme(panelComponent, runeLiteConfig.overlayBackgroundColor());
        }
        graphics.setFont(FontManager.getRunescapeSmallFont());
        boolean full = !config.compactOverlays();
        int xp = client.getSkillExperience(skill);
        int level = Experience.getLevelForXp(xp);
        String name = plan.name != null ? plan.name : "plan";
        panelComponent.getChildren().add(TitleComponent.builder().text(skill.getName() + ": " + name).color(TITLE).build());

        SkillPlans.Step step = plan.milestone(level);
        if (step == null) {
            line("Plan done", "level " + level, HudColors.GOOD);
            return super.render(graphics);
        }
        boolean on = step.covers(level);
        int from = Experience.getXpForLevel(Math.max(1, step.from));
        int to = Experience.getXpForLevel(Math.min(99, step.to));
        if (on) {
            ProgressBarComponent bar = new ProgressBarComponent();
            bar.setMinimum(0);
            bar.setMaximum(1000);
            bar.setValue(to > from ? Math.max(0, Math.min(1000, (xp - from) * 1000L / (to - from))) : 1000);
            bar.setLabelDisplayMode(ProgressBarComponent.LabelDisplayMode.TEXT_ONLY);
            bar.setLeftLabel("Lvl " + step.from + " → " + step.to);
            bar.setRightLabel(PlannerPanel.gp(Math.max(0, to - xp)) + " left");
            bar.setForegroundColor(BAR);
            bar.setBackgroundColor(BAR_BACKGROUND);
            panelComponent.getChildren().add(bar);
        }
        if (full || !on) {
            line(on ? "Method:" : "Next:", step.action + (on ? "" : " at " + step.from), Color.WHITE);
        }
        double each = step.xp;
        if (each <= 0) {
            CalculatorData.Action action = CalculatorData.named(data.calc(skill), step.action);
            each = action == null ? 0 : action.xp;
        }
        if (on && each > 0) {
            line("Actions left:", String.format("%,d", (long) Math.ceil(Math.max(0, to - xp) / each)), Color.WHITE);
        }
        if (!full) {
            return super.render(graphics);
        }
        SkillPlans.Step next = on ? plan.milestone(step.to) : null;
        if (next != null && next != step) {
            line("Next:", next.action + " (" + next.from + " → " + next.to + ")", SOFT);
        }
        if (on) {
            for (PlannerCalculator.Need need : needs(skill, plan, step, xp)) {
                line(need.name + ":", need.tool ? (need.enough() ? "have it" : "missing")
                    : String.format("%,d / %,d", need.have, need.needed), need.enough() ? HudColors.GOOD : HudColors.BAD);
            }
        }
        return super.render(graphics);
    }

    // What the milestone you're on still needs, against what you carry and bank
    private List<PlannerCalculator.Need> needs(Skill skill, SkillPlans.Plan plan, SkillPlans.Step step, int xp) {
        Map<String, Integer> owned = calc.owned();
        String key = skill.name() + '|' + SkillPlans.encode(plan) + '|' + step.from + '|' + xp + '|' + owned.hashCode();
        if (!key.equals(needsKey)) {
            needsKey = key;
            SkillPlans.Plan only = new SkillPlans.Plan(plan.goal);
            only.steps.add(step);
            CalculatorData.Calc skillCalc = data.calc(skill);
            List<PlannerCalculator.StepRow> rows = PlannerCalculator.stepRows(skill, skillCalc,
                CalculatorData.bonuses(skillCalc, calc.bonuses(skill)), only, xp, recipes, training, name -> 0, id -> 0);
            needs = PlannerCalculator.planMaterials(skill, rows, recipes, owned);
        }
        return needs;
    }

    private void line(String left, String right, Color colour) {
        panelComponent.getChildren().add(LineComponent.builder().left(left).right(right).rightColor(colour).build());
    }
}
