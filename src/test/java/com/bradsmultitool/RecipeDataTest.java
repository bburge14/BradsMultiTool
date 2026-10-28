package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Skill;
import org.junit.BeforeClass;
import org.junit.Test;

public class RecipeDataTest {

    private static final RecipeData DATA = new RecipeData();

    @BeforeClass
    public static void load() {
        DATA.load(new Gson());
    }

    @Test
    public void bestThingToMakeFromWhatYouHave() {
        Map<String, Integer> have = new HashMap<>();
        have.put("raw shrimps", 10);
        have.put("raw lobster", 5);
        // At 40 Cooking lobsters give more XP than shrimps
        assertEquals("Lobster", DATA.best(Skill.COOKING, 40, have, r -> r.xp).name);
        // At 30 only the shrimps are cookable
        assertEquals("Shrimps", DATA.best(Skill.COOKING, 30, have, r -> r.xp).name);
        assertNull(DATA.best(Skill.COOKING, 40, new HashMap<>(), r -> r.xp));
    }

    @Test
    public void firemakingLogs() {
        Map<String, Integer> have = new HashMap<>();
        have.put("oak logs", 3);
        have.put("logs", 3);
        assertEquals("Burn oak logs", DATA.best(Skill.FIREMAKING, 20, have, r -> r.xp).name);
        assertEquals(3, DATA.best(Skill.FIREMAKING, 20, have, r -> r.xp).makeable(have));
    }
}
