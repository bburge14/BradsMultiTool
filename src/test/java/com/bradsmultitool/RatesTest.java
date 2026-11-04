package com.bradsmultitool;

import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import org.junit.BeforeClass;
import org.junit.Test;

/** The Planner's estimated XP rates: nearly every action gets one, and they land near the wiki's where both exist. */
public class RatesTest {

    private static final CalculatorData DATA = new CalculatorData();
    private static final RecipeData RECIPES = new RecipeData();
    private static final TrainingData TRAINING = new TrainingData();

    @BeforeClass
    public static void load() {
        Gson gson = new Gson();
        DATA.load(gson);
        RECIPES.load(gson);
        TRAINING.load(gson);
    }

    private static List<PlannerCalculator.Row> rows(Skill skill, TrainingData training) {
        return PlannerCalculator.rows(skill, DATA.calc(skill), Collections.emptyList(), Experience.getXpForLevel(99) - 1000,
            Experience.getXpForLevel(99), RECIPES, training, Collections.emptyMap(), name -> 0);
    }

    @Test
    public void nearlyEveryActionHasARate() {
        int all = 0;
        int timed = 0;
        for (Skill skill : DATA.skills()) {
            if (skill == Skill.FARMING) {
                continue;
            }
            for (PlannerCalculator.Row row : rows(skill, TRAINING)) {
                all++;
                if (row.seconds > 0) {
                    timed++;
                }
                assertTrue(skill + " " + row.action.name + " " + row.xpHour, row.xpHour < 3_000_000);
            }
        }
        assertTrue(timed + " of " + all, timed >= all * 0.95);
    }

    @Test
    public void estimatesLandNearTheWikisRates() {
        List<Double> ratios = new ArrayList<>();
        StringBuilder far = new StringBuilder();
        for (Skill skill : DATA.skills()) {
            List<PlannerCalculator.Row> wiki = rows(skill, TRAINING);
            // (a guide rate that covers a whole family, "Ensouled heads" or "Hallowed Sepulchre", is the best of them,
            // not each one's: compare only the ones a guide gives for at most a few actions)
            java.util.Map<Integer, Integer> shared = new java.util.HashMap<>();
            for (PlannerCalculator.Row guided : wiki) {
                if (guided.xpHour > 0 && !guided.estimated) {
                    shared.merge(guided.xpHour, 1, Integer::sum);
                }
            }
            for (PlannerCalculator.Row guided : wiki) {
                if (guided.xpHour <= 0 || guided.estimated || shared.get(guided.xpHour) > 3) {
                    continue;
                }
                // (the guides mostly give the efficient way: compare with the fastest pace)
                double estimate = 0;
                for (PlannerRates.Way way : PlannerRates.ways(skill, guided.action,
                    PlannerCalculator.recipesFor(skill, guided.action, RECIPES))) {
                    estimate = Math.max(estimate, way.perHour * guided.xpEach);
                }
                if (estimate <= 0) {
                    continue;
                }
                double ratio = estimate / guided.xpHour;
                ratios.add(ratio);
                if (ratio < 0.6 || ratio > 1.6) {
                    far.append(String.format("%n%s %s: wiki %,d, estimate %,.0f", skill, guided.action.name, guided.xpHour, estimate));
                }
            }
        }
        Collections.sort(ratios);
        int close = 0;
        for (double r : ratios) {
            if (r >= 0.6 && r <= 1.6) {
                close++;
            }
        }
        System.out.println("Estimates vs the wiki: " + ratios.size() + " compared, " + close + " within 0.6-1.6x, median "
            + ratios.get(ratios.size() / 2) + far);
        assertTrue(ratios.size() > 25);
        double median = ratios.get(ratios.size() / 2);
        assertTrue("median " + median, median > 0.75 && median < 1.33);
        assertTrue(close + " of " + ratios.size() + far, close >= ratios.size() * 0.8);
    }

    @Test
    public void fasterWaysAndPaces() {
        CalculatorData.Action karambwan = null;
        for (CalculatorData.Action a : DATA.calc(Skill.COOKING).actions) {
            if (a.name.equals("Cooked karambwan")) {
                karambwan = a;
            }
        }
        List<PlannerRates.Way> ways = PlannerRates.ways(Skill.COOKING, karambwan, Collections.emptyList());
        assertTrue(ways.size() == 2 && ways.get(0).name.equals(PlannerRates.NORMAL) && ways.get(1).name.equals("1-tick"));
        assertTrue(ways.get(1).perHour > ways.get(0).perHour * 3);
        // A milestone at the 1-tick pace takes its time from it
        SkillPlans.Plan plan = new SkillPlans.Plan(40);
        SkillPlans.Step step = new SkillPlans.Step("Cooked karambwan", 30, 40, null, 0);
        plan.steps.add(step);
        int xp = Experience.getXpForLevel(30);
        PlannerCalculator.StepRow normal = PlannerCalculator.stepRows(Skill.COOKING, DATA.calc(Skill.COOKING),
            Collections.emptyList(), plan, xp, RECIPES, TRAINING, n -> 0, id -> 0).get(0);
        step.pace = "1-tick";
        PlannerCalculator.StepRow fast = PlannerCalculator.stepRows(Skill.COOKING, DATA.calc(Skill.COOKING),
            Collections.emptyList(), plan, xp, RECIPES, TRAINING, n -> 0, id -> 0).get(0);
        assertTrue(fast.row.estimated && "1-tick".equals(fast.row.pace));
        assertTrue(fast.row.seconds * 3 < normal.row.seconds);
        // and the pace is saved with the plan
        SkillPlans.Plan back = SkillPlans.decode(SkillPlans.encode(plan));
        assertTrue("1-tick".equals(back.steps.get(0).pace) && back.steps.get(0).note == null);
    }

    @Test
    public void suggestionsFillAroundYourMilestones() {
        SkillPlans.Plan mine = new SkillPlans.Plan(60);
        mine.steps.add(new SkillPlans.Step("Mine", 20, 30, null, 0));
        mine.steps.add(new SkillPlans.Step("Mine 2", 40, 50, null, 0));
        SkillPlans.Plan suggested = new SkillPlans.Plan(70);
        suggested.steps.add(new SkillPlans.Step("A", 10, 45, null, 0));
        suggested.steps.add(new SkillPlans.Step("B", 45, 70, null, 0));
        SkillPlans.Plan out = PlannerCalculator.keepAndFill(suggested, mine);
        StringBuilder shape = new StringBuilder();
        for (SkillPlans.Step step : out.steps) {
            shape.append(step.action).append(' ').append(step.from).append('-').append(step.to).append(';');
        }
        assertTrue(shape.toString(), shape.toString().equals("A 10-20;Mine 20-30;A 30-40;Mine 2 40-50;B 50-70;"));
        assertTrue(PlannerCalculator.added(out, mine) == 3 && out.goal == 70);
    }
}
