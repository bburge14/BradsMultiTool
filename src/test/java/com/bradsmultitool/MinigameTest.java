package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.Map;
import net.runelite.api.Skill;
import org.junit.BeforeClass;
import org.junit.Test;

/** The training guides' minigames are Calculator actions, timed by the hour at the wiki's rate for your level. */
public class MinigameTest {

    private static final CalculatorData DATA = new CalculatorData();
    private static final TrainingData TRAINING = new TrainingData();

    @BeforeClass
    public static void load() {
        Gson gson = new Gson();
        DATA.load(gson);
        TRAINING.load(gson);
        DATA.addMinigames(TRAINING);
        DATA.addMinigames(TRAINING);
    }

    private static CalculatorData.Action action(Skill skill, String name) {
        CalculatorData.Action found = null;
        int count = 0;
        for (CalculatorData.Action a : DATA.calc(skill).actions) {
            if (a.name.equals(name)) {
                found = a;
                count++;
            }
        }
        assertEquals("added once: " + name, 1, count);
        return found;
    }

    @Test
    public void minigamesArePlannable() {
        for (Object[] m : new Object[][] {{Skill.FIREMAKING, "Wintertodt"}, {Skill.FISHING, "Tempoross"},
            {Skill.HUNTER, "Hunters' Rumours"}, {Skill.THIEVING, "Pyramid Plunder"}, {Skill.MINING, "Volcanic Mine"}}) {
            CalculatorData.Action a = action((Skill) m[0], (String) m[1]);
            assertNotNull(a);
            assertTrue(a.minigame && a.members);
        }
    }

    @Test
    public void ratesGoByLevel() {
        TrainingData.Method todt = TRAINING.named(Skill.FIREMAKING, "Wintertodt");
        assertEquals(235000, todt.xpHourAt(99));
        // Its points pay by level: about 70/99 of 99's at 70
        assertEquals(Math.round(235000 * 70 / 99.0), todt.xpHourAt(70), 1);
        TrainingData.Method plunder = TRAINING.named(Skill.THIEVING, "Pyramid Plunder");
        assertEquals(275000, plunder.xpHourAt(91));
        assertEquals(275000, plunder.xpHourAt(99));
        TrainingData.Method tempo = TRAINING.named(Skill.FISHING, "Tempoross");
        assertEquals(32500, tempo.xpHourAt(67), 400);
        // The Planner times a plan's Wintertodt at it
        Map<String, Integer> paces = PlannerCalculator.paces(Skill.FIREMAKING, action(Skill.FIREMAKING, "Wintertodt"),
            Collections.emptyList(), 70, null, TRAINING);
        assertEquals(todt.xpHourAt(70), (int) paces.get(PlannerRates.GUIDE));
    }
}
