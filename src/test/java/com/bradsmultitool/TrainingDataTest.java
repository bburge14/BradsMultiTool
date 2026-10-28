package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import net.runelite.api.Skill;
import org.junit.BeforeClass;
import org.junit.Test;

public class TrainingDataTest {

    private static final TrainingData DATA = new TrainingData();

    @BeforeClass
    public static void load() {
        DATA.load(new Gson());
    }

    @Test
    public void fastestMethodForYourLevel() {
        TrainingData.Method oaks = DATA.suggest(Skill.WOODCUTTING, 20, false);
        assertEquals("Oak trees", oaks.name);
        assertTrue(oaks.fastest);
        assertFalse(oaks.objects.isEmpty()); // highlightable
        assertEquals("Teak trees", DATA.next(Skill.WOODCUTTING, 20, 50, false).name);
        assertNull(DATA.next(Skill.WOODCUTTING, 40, 50, false)); // teaks all the way
    }

    @Test
    public void everySkillHasASuggestion() {
        for (Skill skill : Skill.values()) {
            // Slayer suggests a Slayer master instead (SlayerData)
            if (skill == Skill.SLAYER || "OVERALL".equals(skill.name())) {
                continue;
            }
            for (int level : new int[]{1, 30, 60, 90}) {
                TrainingData.Method method = DATA.suggest(skill, level, false);
                if (method == null) {
                    method = DATA.next(skill, level, 99, false);
                }
                assertTrue(skill + " at " + level, method != null);
            }
        }
    }

    @Test
    public void questingOnlyWhenNothingElse() {
        assertEquals("Copper/Tin ore", DATA.suggest(Skill.MINING, 5, false).name);
        assertEquals("Questing", DATA.suggest(Skill.ATTACK, 10, false).name);
        assertEquals("Gemstone Crab", DATA.next(Skill.ATTACK, 10, 50, false).name);
        assertTrue(DATA.suggest(Skill.PRAYER, 40, false) != null);
    }

    @Test
    public void alternativesAndNpcs() {
        TrainingData.Method knights = DATA.suggest(Skill.THIEVING, 60, true);
        assertFalse(knights.fastest);
        TrainingData.Method men = DATA.suggest(Skill.THIEVING, 2, true);
        assertTrue(men.npcs.contains("man"));
    }
}
