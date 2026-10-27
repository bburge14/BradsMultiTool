package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import org.junit.BeforeClass;
import org.junit.Test;

public class MonsterDataTest {

    private static final MonsterData DATA = new MonsterData();

    @BeforeClass
    public static void load() {
        DATA.load(new Gson());
    }

    private static MonsterWeakness weaknessOf(int npcId) {
        MonsterData.Stats stats = DATA.get(npcId);
        assertNotNull("missing NPC " + npcId, stats);
        return MonsterData.weaknessOf(stats);
    }

    @Test
    public void guardIsWeakToMagic() {
        assertEquals(22, DATA.get(3254).hitpoints);
        assertEquals(MonsterWeakness.Type.MAGIC, weaknessOf(3254).type);
        assertEquals("Magic (any element)", weaknessOf(3254).label());
    }

    @Test
    public void fireGiantIsWeakToWater() {
        MonsterWeakness weakness = weaknessOf(2075);
        assertEquals(MonsterWeakness.Type.MAGIC, weakness.type);
        assertEquals(MonsterWeakness.Element.WATER, weakness.element);
        assertEquals("Water", weakness.label());
        assertTrue(DATA.get(2075).aggressive);
    }

    @Test
    public void greaterDemonHasDemonbaneNote() {
        assertEquals("Demonbane", weaknessOf(2025).note);
    }

    @Test
    public void missingWikiStatsGiveUnknownRatherThanAGuess() {
        assertEquals(MonsterWeakness.Type.UNKNOWN, weaknessOf(10841).type);
    }

    @Test
    public void unknownNpcIsMissing() {
        assertEquals(null, DATA.get(-1));
    }
}
