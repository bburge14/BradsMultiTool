package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import org.junit.BeforeClass;
import org.junit.Test;

/** Skill plans (SkillPlans, the Calculator's plan sums) and the Calculator's members filter and prices. */
public class SkillPlanTest {

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

    private static CalculatorData.Action action(Skill skill, String name) {
        CalculatorData.Action action = CalculatorData.named(DATA.calc(skill), name);
        assertNotNull(name, action);
        return action;
    }

    private static SkillPlans.Plan woodcutting() {
        SkillPlans.Plan plan = new SkillPlans.Plan(99);
        plan.steps.add(new SkillPlans.Step("Logs", 1, 20, null, 25));
        plan.steps.add(new SkillPlans.Step("Willow logs", 20, 50, "Willow trees", 67.5));
        plan.steps.add(new SkillPlans.Step("Teak logs", 50, 99, null, 85));
        return plan;
    }

    @Test
    public void planSavesAndLoads() {
        SkillPlans.Plan plan = woodcutting();
        SkillPlans.Plan back = SkillPlans.decode(SkillPlans.encode(plan));
        assertNotNull(back);
        assertEquals(99, back.goal);
        assertEquals(3, back.steps.size());
        assertEquals("Willow logs", back.steps.get(1).action);
        assertEquals("Willow trees", back.steps.get(1).method);
        assertNull(back.steps.get(0).method);
        assertEquals(67.5, back.steps.get(1).xp, 0);
        assertEquals(SkillPlans.encode(plan), SkillPlans.encode(back));
        // Damaged lines are skipped, nothing is null
        assertNull(SkillPlans.decode(null));
        assertNull(SkillPlans.decode(""));
        SkillPlans.Plan damaged = SkillPlans.decode("goal\t70\nx\ty\tLogs\n1\t30\tOak logs\t\t37.5\n40\t40\tBad\t\t1\n");
        assertEquals(70, damaged.goal);
        assertEquals(1, damaged.steps.size());
        // The keys sit in the shared group, as the goals do
        assertEquals(SkillTracker.SHARED_GROUP, SkillPlans.GROUP);
        assertEquals("goalPlan.", SkillPlans.TRACKED_KEY);
    }

    @Test
    public void stepForALevel() {
        SkillPlans.Plan plan = woodcutting();
        assertEquals("Logs", plan.at(1).action);
        assertEquals("Logs", plan.at(19).action);
        assertEquals("Willow logs", plan.at(20).action);
        assertEquals("Teak logs", plan.at(98).action);
        assertNull(plan.at(99));
        // A gap: no step at that level, but the next one is the milestone ahead
        plan.steps.get(0).to = 10;
        assertNull(plan.at(15));
        assertSame(plan.steps.get(1), plan.milestone(15));
        assertSame(plan.steps.get(1), plan.milestone(30));
        assertNull(plan.milestone(99));
        assertEquals(99, plan.end());
        assertEquals(Experience.getXpForLevel(99), plan.goalXp());
    }

    @Test
    public void severalNamedPlansInOneText() {
        SkillPlans.Plan one = new SkillPlans.Plan(99);
        one.name = "Darts to 99";
        one.steps.add(new SkillPlans.Step("Mithril dart", 59, 67, null, 11.2));
        SkillPlans.Plan two = new SkillPlans.Plan(80);
        SkillPlans.Plan empty = new SkillPlans.Plan(70);
        empty.name = "New one";
        List<SkillPlans.Plan> back = SkillPlans.decodeAll(SkillPlans.encodeAll(java.util.Arrays.asList(one, two, empty)));
        assertEquals(3, back.size());
        assertEquals("Darts to 99", back.get(0).name);
        assertEquals(67, back.get(0).steps.get(0).to);
        assertNull(back.get(1).name);
        assertEquals(80, back.get(1).goal);
        // A plan with no milestones yet is kept
        assertEquals("New one", back.get(2).name);
        assertTrue(back.get(2).steps.isEmpty());
        // The tracker's copy reads the same with the name in it
        assertEquals(1, SkillPlans.decode(SkillPlans.encode(one)).steps.size());
    }

    @Test
    public void addingRemovingAndSwappingSteps() {
        SkillPlans.Plan plan = new SkillPlans.Plan(99);
        // From your level; the next starts where the last ends, or at its own level if that's later
        assertTrue(PlannerCalculator.addStep(plan, action(Skill.WOODCUTTING, "Logs"), 25, null, 1));
        assertEquals(1, plan.steps.get(0).from);
        assertEquals(99, plan.steps.get(0).to);
        // The plan already reaches 99: Willow (level 30) cuts Logs at 30
        assertTrue(PlannerCalculator.addStep(plan, action(Skill.WOODCUTTING, "Willow logs"), 67.5, null, 1));
        assertEquals(30, plan.steps.get(0).to);
        assertEquals(30, plan.steps.get(1).from);
        assertEquals(99, plan.steps.get(1).to);
        assertTrue(PlannerCalculator.addStep(plan, action(Skill.WOODCUTTING, "Teak logs"), 85, null, 1));
        assertEquals(35, plan.steps.get(1).to);
        assertEquals(3, plan.steps.size());
        // Something that can't start before the goal doesn't fit
        SkillPlans.Plan low = new SkillPlans.Plan(40);
        assertFalse(PlannerCalculator.addStep(low, action(Skill.WOODCUTTING, "Yew logs"), 175, null, 1));

        // A plan to the next level is full after one milestone: raising its goal makes room after it
        SkillPlans.Plan next = new SkillPlans.Plan(60);
        assertTrue(PlannerCalculator.addStep(next, action(Skill.WOODCUTTING, "Willow logs"), 67.5, null, 59));
        assertFalse(PlannerCalculator.addStep(next, action(Skill.WOODCUTTING, "Teak logs"), 85, null, 59));
        PlannerCalculator.raiseGoal(next, 99);
        assertEquals(60, next.steps.get(0).to);
        assertTrue(PlannerCalculator.addStep(next, action(Skill.WOODCUTTING, "Teak logs"), 85, null, 59));
        assertEquals(60, next.steps.get(1).from);
        assertEquals(99, next.steps.get(1).to);

        // A full plan: an action whose level falls inside a milestone takes over from there to where that one ended
        SkillPlans.Plan darts = new SkillPlans.Plan(99);
        darts.steps.add(new SkillPlans.Step("Mithril dart", 59, 67, null, 11.2));
        darts.steps.add(new SkillPlans.Step("Adamant dart", 67, 81, null, 15));
        darts.steps.add(new SkillPlans.Step("Rune dart", 81, 90, null, 18.8));
        darts.steps.add(new SkillPlans.Step("Amethyst dart", 90, 99, null, 21));
        CalculatorData.Action atlatl = action(Skill.FLETCHING, "Atlatl dart");
        assertEquals(74, atlatl.level);
        assertTrue(PlannerCalculator.addStep(darts, atlatl, 19, null, 59));
        assertEquals(5, darts.steps.size());
        assertEquals(74, darts.steps.get(1).to);
        assertEquals("Atlatl dart", darts.steps.get(2).action);
        assertEquals(74, darts.steps.get(2).from);
        assertEquals(81, darts.steps.get(2).to);
        assertEquals(90, darts.steps.get(4).from);
        // Typed levels go in where they say: 70-85 cuts Adamant to 70, Atlatl goes, Rune starts at 85
        PlannerCalculator.insertStep(darts, new SkillPlans.Step("Dragon dart", 70, 85, null, 25));
        assertEquals(70, darts.steps.get(1).to);
        assertEquals("Dragon dart", darts.steps.get(2).action);
        assertEquals("Rune dart", darts.steps.get(3).action);
        assertEquals(85, darts.steps.get(3).from);
        assertEquals(5, darts.steps.size());
        // Inside one: it's split around the new one
        PlannerCalculator.insertStep(darts, new SkillPlans.Step("Steel dart", 60, 62, null, 7.5));
        assertEquals(59, darts.steps.get(0).from);
        assertEquals(60, darts.steps.get(0).to);
        assertEquals("Steel dart", darts.steps.get(1).action);
        assertEquals("Mithril dart", darts.steps.get(2).action);
        assertEquals(62, darts.steps.get(2).from);
        assertEquals(67, darts.steps.get(2).to);

        PlannerCalculator.swapSteps(plan, 1, 2);
        assertEquals("Teak logs", plan.steps.get(1).action);
        assertEquals(30, plan.steps.get(1).from);
        // Removing one: the one before stretches over its levels
        PlannerCalculator.removeStep(plan, 1);
        assertEquals(2, plan.steps.size());
        assertEquals(35, plan.steps.get(0).to);
        PlannerCalculator.removeStep(plan, 0);
        assertEquals(1, plan.steps.get(0).from);
    }

    @Test
    public void planCostTotals() {
        CalculatorData.Calc cooking = DATA.calc(Skill.COOKING);
        Map<String, Integer> prices = new HashMap<>();
        prices.put("raw lobster", 200);
        prices.put("lobster", 250);
        SkillPlans.Plan plan = new SkillPlans.Plan(50);
        plan.steps.add(new SkillPlans.Step("Lobster", 40, 50, null, 120));
        int xp = Experience.getXpForLevel(40);
        List<PlannerCalculator.StepRow> rows = PlannerCalculator.stepRows(Skill.COOKING, cooking, Collections.emptyList(), plan,
            xp, RECIPES, TRAINING, name -> prices.getOrDefault(name.toLowerCase(), 0), id -> 0);
        assertEquals(1, rows.size());
        PlannerCalculator.StepRow step = rows.get(0);
        int need = Experience.getXpForLevel(50) - xp;
        assertEquals(need, step.xp);
        int count = (int) Math.ceil(need / 120.0);
        assertEquals(count, step.row.needed);
        assertEquals(200.0 * count, step.row.cost(count), 0);
        // (sold at 250 gp, less the 2% tax: 245)
        assertEquals(245.0 * count, step.row.earn(count), 0);
        PlannerCalculator.Totals totals = PlannerCalculator.totals(plan, rows, xp);
        assertEquals(need, totals.xp);
        assertEquals(200.0 * count, totals.cost, 0);
        assertEquals(245.0 * count, totals.earn, 0);
        assertEquals(50, totals.endLevel);
        // Halfway: only what's left counts; past the end it's done
        int half = Experience.getXpForLevel(45);
        assertEquals(Experience.getXpForLevel(50) - half, PlannerCalculator.stepRows(Skill.COOKING, cooking,
            Collections.emptyList(), plan, half, RECIPES, TRAINING, n -> 0, id -> 0).get(0).xp);
        assertTrue(PlannerCalculator.stepRows(Skill.COOKING, cooking, Collections.emptyList(), plan,
            Experience.getXpForLevel(60), RECIPES, TRAINING, n -> 0, id -> 0).get(0).done);
    }

    @Test
    public void gatheringEarnsByItemId() {
        CalculatorData.Action yew = action(Skill.WOODCUTTING, "Yew logs");
        List<PlannerCalculator.Row> rows = PlannerCalculator.rows(Skill.WOODCUTTING, DATA.calc(Skill.WOODCUTTING).actions,
            Collections.emptyList(), Experience.getXpForLevel(60), Experience.getXpForLevel(61), RECIPES, TRAINING,
            new HashMap<>(), name -> 0, id -> id == yew.icon ? 300 : 0);
        PlannerCalculator.Row row = rows.stream().filter(r -> r.action == yew).findFirst().orElse(null);
        assertNotNull(row);
        assertEquals(294, row.earnEach, 0);
        assertNull(row.costEach);
        // Earnings alone give no net gp/xp (that needs a cost)
        assertNull(row.perXp());
        // Neither side is profit, so neither is coloured: the net badge beside them says which way it goes
        assertEquals("Mats 1.24M · Sells 2.10M", PlannerCalculatorView.money(1_240_000.0, 2_100_000.0, ""));
        assertEquals("Sells 950 each", PlannerCalculatorView.money(null, 950.0, " each"));
        javax.swing.JLabel loss = new javax.swing.JLabel();
        PlannerCalculatorView.net(loss, 254_000.0, 226_000.0, true);
        assertEquals("-28.0K", loss.getText());
        assertEquals(PlannerCalculatorView.COSTS, loss.getForeground());
        javax.swing.JLabel gain = new javax.swing.JLabel();
        PlannerCalculatorView.net(gain, null, 950.0, true);
        assertEquals("+950", gain.getText());
        assertNull(PlannerCalculatorView.money(null, null, ""));
    }

    @Test
    public void membersFilter() {
        CalculatorData.Calc wc = DATA.calc(Skill.WOODCUTTING);
        assertFalse(action(Skill.WOODCUTTING, "Yew logs").members);
        assertTrue(action(Skill.WOODCUTTING, "Teak logs").members);
        assertTrue(action(Skill.AGILITY, DATA.calc(Skill.AGILITY).actions.get(0).name).members);
        assertTrue(action(Skill.MAGIC, "High Level Alchemy").members == false);
        List<CalculatorData.Action> free = CalculatorData.shown(wc, false);
        assertTrue(free.stream().noneMatch(a -> a.members));
        assertTrue(free.stream().anyMatch(a -> a.name.equals("Willow logs")));
        assertEquals(wc.actions, CalculatorData.shown(wc, true));
    }

    @Test
    public void suggestedPlanFromTheGuides() {
        CalculatorData.Calc wc = DATA.calc(Skill.WOODCUTTING);
        SkillPlans.Plan plan = PlannerCalculator.suggestedPlan(Skill.WOODCUTTING, wc.actions, Collections.emptyList(), TRAINING,
            1, 99, false);
        assertFalse(plan.steps.isEmpty());
        assertEquals(1, plan.steps.get(0).from);
        assertEquals(99, plan.steps.get(plan.steps.size() - 1).to);
        // Milestones meet end to end, each a different action, each one you can do from its start
        for (int i = 0; i < plan.steps.size(); i++) {
            SkillPlans.Step step = plan.steps.get(i);
            assertTrue(step.to > step.from);
            assertTrue(step.action, action(Skill.WOODCUTTING, step.action).level <= step.from);
            if (i > 0) {
                assertEquals(plan.steps.get(i - 1).to, step.from);
                assertFalse(plan.steps.get(i - 1).action.equals(step.action));
            }
        }
        // A step's guide method: the one saved, else one matching its name
        SkillPlans.Step teak = new SkillPlans.Step("Teak logs", 35, 60, null, 85);
        TrainingData.Method method = SkillPlans.method(TRAINING, Skill.WOODCUTTING, teak);
        assertNotNull(method);
        assertTrue(SkillPlans.matches(method, "Teak logs"));
        assertTrue(PlannerCalculator.matches(method, "Teak logs"));
    }

    @Test
    public void milestonesTickOffAsYouGo() {
        CalculatorData.Calc fletching = DATA.calc(Skill.FLETCHING);
        SkillPlans.Plan plan = new SkillPlans.Plan(90);
        plan.steps.add(new SkillPlans.Step("Adamant dart", 67, 81, null, 15));
        plan.steps.add(new SkillPlans.Step("Rune dart", 81, 90, null, 18.8));
        int at70 = Experience.getXpForLevel(70);
        List<PlannerCalculator.StepRow> before = PlannerCalculator.stepRows(Skill.FLETCHING, fletching, Collections.emptyList(),
            plan, at70, RECIPES, TRAINING, n -> 0, id -> 0);
        // 3,000 darts later: 3,000 fewer to go, and they count as done
        List<PlannerCalculator.StepRow> after = PlannerCalculator.stepRows(Skill.FLETCHING, fletching, Collections.emptyList(),
            plan, at70 + 45_000, RECIPES, TRAINING, n -> 0, id -> 0);
        assertEquals(before.get(0).row.needed - 3000, after.get(0).row.needed);
        assertEquals(before.get(0).made() + 3000, after.get(0).made());
        assertEquals(before.get(0).total, after.get(0).total);
        assertTrue(after.get(0).progress > before.get(0).progress);
        assertEquals(0, after.get(1).progress, 0);
        // Past 81: the first is done (and full), and passing it says so, with the next
        int at81 = Experience.getXpForLevel(81);
        PlannerCalculator.StepRow done = PlannerCalculator.stepRows(Skill.FLETCHING, fletching, Collections.emptyList(), plan,
            at81, RECIPES, TRAINING, n -> 0, id -> 0).get(0);
        assertTrue(done.done);
        assertEquals(1, done.progress, 0);
        List<String> said = PlannerCalculator.milestoneMessages(Skill.FLETCHING, plan, at81 - 10, at81 + 10);
        assertEquals(1, said.size());
        assertTrue(said.get(0), said.get(0).contains("Adamant dart") && said.get(0).contains("Next: Rune dart to 90"));
        assertTrue(PlannerCalculator.milestoneMessages(Skill.FLETCHING, plan, at81 + 10, at81 + 20).isEmpty());
        List<String> end = PlannerCalculator.milestoneMessages(Skill.FLETCHING, plan, Experience.getXpForLevel(90) - 1,
            Experience.getXpForLevel(90));
        assertEquals(1, end.size());
        assertTrue(end.get(0), end.get(0).contains("plan complete"));
    }

    @Test
    public void insertingInsideAMilestoneSplitsIt() {
        // Adamant 67-74, Onyx 74-99: Atlatl 74-81 goes in, Onyx carries on from 81
        SkillPlans.Plan plan = new SkillPlans.Plan(99);
        plan.steps.add(new SkillPlans.Step("Adamant dart", 67, 74, null, 15));
        plan.steps.add(new SkillPlans.Step("Onyx bolts", 74, 99, null, 9.4));
        PlannerCalculator.insertStep(plan, new SkillPlans.Step("Atlatl dart", 74, 81, null, 12));
        assertEquals(3, plan.steps.size());
        assertEquals("Adamant dart", plan.steps.get(0).action);
        assertEquals(74, plan.steps.get(0).to);
        assertEquals("Atlatl dart", plan.steps.get(1).action);
        assertEquals(81, plan.steps.get(1).to);
        assertEquals("Onyx bolts", plan.steps.get(2).action);
        assertEquals(81, plan.steps.get(2).from);
        assertEquals(99, plan.steps.get(2).to);
        // In the middle of one: split around it
        PlannerCalculator.insertStep(plan, new SkillPlans.Step("Rune dart", 85, 90, null, 18.8));
        assertEquals(5, plan.steps.size());
        assertEquals(85, plan.steps.get(2).to);
        assertEquals("Rune dart", plan.steps.get(3).action);
        assertEquals("Onyx bolts", plan.steps.get(4).action);
        assertEquals(90, plan.steps.get(4).from);
    }

    @Test
    public void geTaxIsTwoPercentRoundedDownAndCapped() {
        assertEquals(49, PlannerCalculator.afterTax(49), 0);
        assertEquals(49, PlannerCalculator.afterTax(50), 0);
        assertEquals(294, PlannerCalculator.afterTax(300), 0);
        // at most 5M an item
        assertEquals(1_495_000_000, PlannerCalculator.afterTax(1_500_000_000), 0);
    }

    @Test
    public void moneyPlanCyclesWithinTheBudget() {
        // Lobsters 40-50: raw lobsters at 200 each, cooked sell at 250 (245 after tax): a gain, so 1M covers it in rounds
        CalculatorData.Calc cooking = DATA.calc(Skill.COOKING);
        SkillPlans.Plan plan = new SkillPlans.Plan(50);
        plan.steps.add(new SkillPlans.Step("Lobster", 40, 50, null, 120));
        Map<String, Integer> prices = new HashMap<>();
        prices.put("raw lobster", 200);
        prices.put("lobster", 250);
        int xp = Experience.getXpForLevel(40);
        List<PlannerCalculator.StepRow> rows = PlannerCalculator.stepRows(Skill.COOKING, cooking, Collections.emptyList(), plan,
            xp, RECIPES, TRAINING, name -> prices.getOrDefault(name.toLowerCase(), 0), id -> 0);
        long count = rows.get(0).row.needed;
        Map<String, Integer> owned = new HashMap<>();
        owned.put("raw lobster", 100);
        PlannerCalculator.MoneyPlan money = PlannerCalculator.moneyPlan(Skill.COOKING, rows, RECIPES, owned, 100_000,
            name -> prices.getOrDefault(name.toLowerCase(), 0), name -> 0);
        PlannerCalculator.MoneyStep step = money.steps.get(0);
        // The 100 you own aren't bought
        assertEquals(count - 100, step.buys.get(0).quantity);
        assertEquals((count - 100) * 200, step.cost);
        // A round at a time: no round needs more than the budget
        assertTrue(step.perRound <= 100_000);
        assertEquals((step.cost + 99_999) / 100_000, step.rounds);
        assertTrue(money.runsOut == null);
        assertEquals(100_000 - step.loss(), money.end());
        // A buy limit of 1,000 every 4 hours makes it more rounds, and takes time
        PlannerCalculator.MoneyPlan limited = PlannerCalculator.moneyPlan(Skill.COOKING, rows, RECIPES, owned, 100_000_000,
            name -> prices.getOrDefault(name.toLowerCase(), 0), name -> 1000);
        assertEquals((count - 100 + 999) / 1000, limited.steps.get(0).rounds);
        assertEquals((limited.steps.get(0).rounds - 1) * 4, limited.limitHours);
    }

    @Test
    public void moneyPlanSaysWhereTheBudgetRunsOut() {
        // Something that loses: sharks bought at 1,000 and sold at 500
        CalculatorData.Calc cooking = DATA.calc(Skill.COOKING);
        SkillPlans.Plan plan = new SkillPlans.Plan(85);
        plan.steps.add(new SkillPlans.Step("Shark", 80, 85, null, 210));
        Map<String, Integer> prices = new HashMap<>();
        prices.put("raw shark", 1000);
        prices.put("shark", 500);
        List<PlannerCalculator.StepRow> rows = PlannerCalculator.stepRows(Skill.COOKING, cooking, Collections.emptyList(), plan,
            Experience.getXpForLevel(80), RECIPES, TRAINING, name -> prices.getOrDefault(name.toLowerCase(), 0), id -> 0);
        PlannerCalculator.MoneyPlan money = PlannerCalculator.moneyPlan(Skill.COOKING, rows, RECIPES, new HashMap<>(), 10_000,
            name -> prices.getOrDefault(name.toLowerCase(), 0), name -> 0);
        assertNotNull(money.runsOut);
        assertTrue(money.runsOut.runsOut);
        // The budget it needs carries it; a bit less doesn't
        long needed = PlannerCalculator.budgetNeeded(Skill.COOKING, rows, RECIPES, new HashMap<>(),
            name -> prices.getOrDefault(name.toLowerCase(), 0), name -> 0);
        assertNull(PlannerCalculator.moneyPlan(Skill.COOKING, rows, RECIPES, new HashMap<>(), needed,
            name -> prices.getOrDefault(name.toLowerCase(), 0), name -> 0).runsOut);
        assertNotNull(PlannerCalculator.moneyPlan(Skill.COOKING, rows, RECIPES, new HashMap<>(), needed * 9 / 10,
            name -> prices.getOrDefault(name.toLowerCase(), 0), name -> 0).runsOut);
    }

    @Test
    public void budgetAmountsAreRead() {
        assertEquals(50_000_000, PlannerCalculatorView.parseGp("50m"));
        assertEquals(1_200_000_000, PlannerCalculatorView.parseGp("1.2B"));
        assertEquals(750_000, PlannerCalculatorView.parseGp("750k"));
        assertEquals(1_000_000, PlannerCalculatorView.parseGp("1,000,000 gp"));
        assertEquals(0, PlannerCalculatorView.parseGp(" "));
        assertEquals(-1, PlannerCalculatorView.parseGp("lots"));
    }

    @Test
    public void materialsKnowWhereToGetThemYourself() {
        // Logs from Woodcutting (not Firemaking, which burns them), fish from Fishing, bars made in Smithing
        PlannerCalculator.Source logs = PlannerCalculator.sourceOf("Yew logs", Skill.FIREMAKING, DATA, RECIPES);
        assertNotNull(logs);
        assertEquals(Skill.WOODCUTTING, logs.skill);
        assertTrue(logs.gathered);
        assertEquals(Skill.FISHING, PlannerCalculator.sourceOf("Raw shark", Skill.COOKING, DATA, RECIPES).skill);
        PlannerCalculator.Source bars = PlannerCalculator.sourceOf("Adamantite bar", Skill.CRAFTING, DATA, RECIPES);
        assertNotNull(bars);
        assertEquals(Skill.SMITHING, bars.skill);
        assertFalse(bars.gathered);
    }

    @Test
    public void gatheredMaterialsAreNotBought() {
        CalculatorData.Calc cooking = DATA.calc(Skill.COOKING);
        SkillPlans.Plan plan = new SkillPlans.Plan(50);
        plan.steps.add(new SkillPlans.Step("Lobster", 40, 50, null, 120));
        Map<String, Integer> prices = new HashMap<>();
        prices.put("raw lobster", 200);
        prices.put("lobster", 250);
        List<PlannerCalculator.StepRow> rows = PlannerCalculator.stepRows(Skill.COOKING, cooking, Collections.emptyList(), plan,
            Experience.getXpForLevel(40), RECIPES, TRAINING, name -> prices.getOrDefault(name.toLowerCase(), 0), id -> 0);
        PlannerCalculator.MoneyPlan money = PlannerCalculator.moneyPlan(Skill.COOKING, rows, RECIPES, new HashMap<>(), 1_000_000,
            name -> prices.getOrDefault(name.toLowerCase(), 0), name -> 0, Collections.singleton("raw lobster"));
        assertTrue(money.steps.get(0).buys.isEmpty());
        assertEquals(0, money.steps.get(0).cost);
        // ...so selling them is all gain
        assertTrue(money.end() > 1_000_000);
    }
}
