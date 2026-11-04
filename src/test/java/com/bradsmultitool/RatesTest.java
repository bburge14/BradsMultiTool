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

    @Test
    public void alchingCountsWhenItPaysMore() {
        PlannerCalculator.Selling before = PlannerCalculator.selling;
        try {
            // Rune platebody: worth more alched (39,000 less a 100 gp rune) than its 38,000 on the GE
            PlannerCalculator.selling = new PlannerCalculator.Selling(PlannerConfig.SellBy.BEST,
                name -> name.equals("Rune platebody") ? 39_000 : 0, 100);
            java.util.Map<String, Integer> prices = new java.util.HashMap<>();
            prices.put("Rune platebody", 38_000);
            prices.put("Runite bar", 12_000);
            CalculatorData.Action body = null;
            for (CalculatorData.Action a : DATA.calc(Skill.SMITHING).actions) {
                if (a.name.equals("Rune platebody")) {
                    body = a;
                }
            }
            PlannerCalculator.Row row = PlannerCalculator.rows(Skill.SMITHING, Collections.singletonList(body),
                Collections.emptyList(), Experience.getXpForLevel(99) - 10_000, Experience.getXpForLevel(99), RECIPES, TRAINING,
                Collections.emptyMap(), name -> prices.getOrDefault(name, 0), id -> 0).get(0);
            assertTrue(row.alchWins);
            assertTrue(row.earnEach == 38_900);
            assertTrue(row.geEach == 38_000 - 760);
            // GE only: the alch value doesn't count
            PlannerCalculator.selling = new PlannerCalculator.Selling(PlannerConfig.SellBy.GE, name -> 39_000, 100);
            row = PlannerCalculator.rows(Skill.SMITHING, Collections.singletonList(body), Collections.emptyList(),
                Experience.getXpForLevel(99) - 10_000, Experience.getXpForLevel(99), RECIPES, TRAINING, Collections.emptyMap(),
                name -> prices.getOrDefault(name, 0), id -> 0).get(0);
            assertTrue(!row.alchWins && row.earnEach == 38_000 - 760);
        } finally {
            PlannerCalculator.selling = before;
        }
        // What you sold and alched is saved with the milestone
        SkillPlans.Plan plan = new SkillPlans.Plan(99);
        SkillPlans.Step step = new SkillPlans.Step("Rune platebody", 90, 99, null, 0);
        step.sold = 120;
        step.alched = 45;
        plan.steps.add(step);
        SkillPlans.Step back = SkillPlans.decode(SkillPlans.encode(plan)).steps.get(0);
        assertTrue(back.sold == 120 && back.alched == 45 && back.pace == null && back.note == null);
        step.soldEach = 245.5;
        step.sellBy = "ALCH";
        back = SkillPlans.decode(SkillPlans.encode(plan)).steps.get(0);
        assertTrue(back.soldEach == 245.5 && back.alchedEach == 0 && "ALCH".equals(back.sellBy) && back.sold == 120);
    }
}
