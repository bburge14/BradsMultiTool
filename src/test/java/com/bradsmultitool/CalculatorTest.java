package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import org.junit.BeforeClass;
import org.junit.Test;

/** The Planner's Calculator: its data (from RuneLite's Skill Calculator) and its sums. */
public class CalculatorTest {

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
        for (CalculatorData.Action action : DATA.calc(skill).actions) {
            if (action.name.equals(name)) {
                return action;
            }
        }
        throw new AssertionError(name + " not in " + skill);
    }

    @Test
    public void dataLoads() {
        List<Skill> skills = DATA.skills();
        assertTrue(skills.size() >= 17);
        assertEquals(Skill.MINING, skills.get(0));
        assertTrue(skills.contains(Skill.WOODCUTTING));
        assertFalse(skills.contains(Skill.ATTACK));
        CalculatorData.Action oak = action(Skill.WOODCUTTING, "Oak logs");
        assertEquals(15, oak.level);
        assertEquals(37.5, oak.xp, 0);
        assertTrue(oak.icon > 0);
        assertTrue(action(Skill.MAGIC, "High Level Alchemy").sprite >= 0);
        assertNotNull(DATA.calc(Skill.PRAYER).bonus("LIT_GILDED_ALTAR"));
        for (Skill skill : skills) {
            for (CalculatorData.Action a : DATA.calc(skill).actions) {
                assertTrue(skill + " " + a.name, a.xp > 0 && a.level >= 1 && a.level <= 99 && (a.icon > 0 || a.sprite >= 0));
            }
        }
    }

    @Test
    public void countsNeeded() {
        CalculatorData.Action oak = action(Skill.WOODCUTTING, "Oak logs");
        List<CalculatorData.Bonus> none = Collections.emptyList();
        // 1,000 XP at 37.5 each: 26.67, so 27
        assertEquals(27, CalculatorData.needed(oak, none, 0, 1000));
        assertEquals(1, CalculatorData.needed(oak, none, 0, 1));
        assertEquals(0, CalculatorData.needed(oak, none, 2000, 1000));
        // 61 to 70: 435,339 XP
        int from = 302_288;
        int to = Experience.getXpForLevel(70);
        assertEquals((int) Math.ceil((to - from) / 37.5), CalculatorData.needed(oak, none, from, to));
    }

    @Test
    public void bonusMultipliers() {
        CalculatorData.Calc prayer = DATA.calc(Skill.PRAYER);
        CalculatorData.Action dragon = action(Skill.PRAYER, "Dragon bones");
        List<CalculatorData.Bonus> altar = CalculatorData.bonuses(prayer, Collections.singleton("LIT_GILDED_ALTAR"));
        assertEquals(252, CalculatorData.xpEach(dragon, altar), 0);
        // An outfit's 2.5%, rounded down to a tenth as the game does
        CalculatorData.Calc wc = DATA.calc(Skill.WOODCUTTING);
        List<CalculatorData.Bonus> outfit = CalculatorData.bonuses(wc, Collections.singleton("LUMBERJACK_OUTFIT"));
        assertEquals(38.4, CalculatorData.xpEach(action(Skill.WOODCUTTING, "Oak logs"), outfit), 1e-9);
        assertEquals("+2.5%", wc.bonus("LUMBERJACK_OUTFIT").label());
        assertEquals("x3.5", prayer.bonus("LIT_GILDED_ALTAR").label());
        assertEquals("-50%", prayer.bonus("BONECRUSHER").label());
        // Bonuses an action isn't affected by don't count (the Morytania diary's shades bonus on bones)
        List<CalculatorData.Bonus> shades = CalculatorData.bonuses(prayer, Collections.singleton("MORYTANIA_DIARY_3_SHADES"));
        assertEquals(72, CalculatorData.xpEach(dragon, shades), 0);
        // One altar at a time: ticking the Chaos Altar unticks the gilded altar; the robes stay with an altar
        Set<String> on = new HashSet<>(Arrays.asList("LIT_GILDED_ALTAR", "ZEALOT_ROBES"));
        assertEquals(new HashSet<>(Arrays.asList("CHAOS_ALTAR", "ZEALOT_ROBES")),
            CalculatorData.toggle(prayer, on, "CHAOS_ALTAR", true));
        assertEquals(Collections.singleton("ZEALOT_ROBES"), CalculatorData.toggle(prayer, on, "LIT_GILDED_ALTAR", false));
        // The Felling axe and the outfit go together
        assertEquals(2, CalculatorData.toggle(wc, Collections.singleton("LUMBERJACK_OUTFIT"), "FELLING_AXE_RATIONS", true).size());
    }

    @Test
    public void bankedLevel() {
        Map<String, Integer> have = new HashMap<>();
        have.put("raw lobster", 1000);
        CalculatorData.Calc cooking = DATA.calc(Skill.COOKING);
        int xp = Experience.getXpForLevel(60);
        double banked = PlannerCalculator.bankedXp(Skill.COOKING, cooking, Collections.emptyList(), 60, RECIPES, have);
        assertEquals(120_000, banked, 0);
        assertEquals(Experience.getLevelForXp(xp + 120_000), PlannerCalculator.bankedLevel(xp, banked));
        assertEquals(99, PlannerCalculator.bankedLevel(Experience.getXpForLevel(98), 50_000_000));
        // Each lobster row: you have 1,000 to cook
        List<PlannerCalculator.Row> rows = PlannerCalculator.rows(Skill.COOKING, cooking, Collections.emptyList(), xp,
            Experience.getXpForLevel(61), RECIPES, TRAINING, have, name -> 0);
        PlannerCalculator.Row lobster = rows.stream().filter(r -> r.action.name.equals("Lobster")).findFirst().orElse(null);
        assertNotNull(lobster);
        assertEquals(1000, lobster.have);
        // What you can do comes first, highest level first
        assertFalse(rows.get(0).locked);
        assertTrue(rows.get(rows.size() - 1).locked);
    }

    @Test
    public void costPerXpAndTime() {
        Map<String, Integer> prices = new HashMap<>();
        prices.put("dragon bones", 2000);
        CalculatorData.Calc prayer = DATA.calc(Skill.PRAYER);
        List<PlannerCalculator.Row> rows = PlannerCalculator.rows(Skill.PRAYER, prayer, Collections.emptyList(), 0, 7200,
            RECIPES, TRAINING, new HashMap<>(), name -> prices.getOrDefault(name.toLowerCase(), 0));
        PlannerCalculator.Row dragon = rows.stream().filter(r -> r.action.name.equals("Dragon bones")).findFirst().orElse(null);
        assertNotNull(dragon);
        assertEquals(-2000, dragon.profit, 0);
        assertEquals(-2000 / 72.0, dragon.perXp(), 1e-9);
        assertEquals(100, dragon.needed);

        // Teak trees in the Woodcutting guide give an XP rate, so Teak logs get a time
        int from = Experience.getXpForLevel(40);
        int to = Experience.getXpForLevel(41);
        List<PlannerCalculator.Row> wc = PlannerCalculator.rows(Skill.WOODCUTTING, DATA.calc(Skill.WOODCUTTING),
            Collections.emptyList(), from, to, RECIPES, TRAINING, new HashMap<>(), name -> 0);
        PlannerCalculator.Row teak = wc.stream().filter(r -> r.action.name.equals("Teak logs")).findFirst().orElse(null);
        assertNotNull(teak);
        assertTrue(teak.xpHour > 0);
        assertEquals((long) Math.ceil((to - from) * 3600.0 / teak.xpHour), teak.seconds);
    }

    @Test
    public void goalsAndBankKeepTheirFormat() {
        Map<String, Integer> bank = new HashMap<>();
        bank.put("teak logs", 220);
        assertEquals(bank, SkillGoals.decodeBank(SkillGoals.encodeBank(bank)));
        assertEquals(GoalItems.encodeBank(bank), SkillGoals.encodeBank(bank));
        assertEquals(SkillTracker.SHARED_GROUP, SkillGoals.GROUP);
        assertEquals(302288, PlannerCalculatorView.parse("302,288"));
        assertEquals(-1, PlannerCalculatorView.parse("abc"));
    }
}
