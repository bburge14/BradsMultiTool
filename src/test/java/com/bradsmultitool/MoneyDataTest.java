package com.bradsmultitool;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import net.runelite.api.Skill;
import org.junit.BeforeClass;
import org.junit.Test;

public class MoneyDataTest {

    private static final MoneyData DATA = new MoneyData();

    @BeforeClass
    public static void load() {
        DATA.load(new Gson());
    }

    @Test
    public void bestGuideYouCanDo() {
        // Everything else maxed: the best Woodcutting money maker at 99 needs some Woodcutting level
        MoneyData.Guide high = DATA.best(Skill.WOODCUTTING, 99, s -> 99);
        assertNotNull(high);
        assertTrue(high.skills.contains("WOODCUTTING"));
        MoneyData.Guide low = DATA.best(Skill.WOODCUTTING, 60, s -> 99);
        assertNotNull(low);
        assertTrue(low.reqs.getOrDefault("WOODCUTTING", 0) <= 60);
        assertTrue(low.profit <= high.profit);
        // Requirements in other skills count too
        MoneyData.Guide noAgility = DATA.best(Skill.FARMING, 99, s -> s.equals("AGILITY") ? 1 : 99);
        if (noAgility != null) {
            assertTrue(noAgility.reqs.getOrDefault("AGILITY", 0) <= 1);
        }
        assertNull(DATA.best(Skill.PRAYER, 99, s -> 99));
    }
}
