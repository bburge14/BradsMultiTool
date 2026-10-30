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
        assertEquals("Magic", weaknessOf(3254).label());
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
        assertEquals(java.util.Collections.singletonList("Slash"), DATA.get(2025).attackStyles);
    }

    @Test
    public void missingWikiStatsGiveUnknownRatherThanAGuess() {
        assertEquals(MonsterWeakness.Type.UNKNOWN, weaknessOf(10841).type);
    }

    @Test
    public void unknownNpcIsMissing() {
        assertEquals(null, DATA.get(-1));
    }

    // Every monster that needs a Slayer level has it (from the wiki's infobox), so the HUD and hover card show
    // the Slayer section for it; also brand-new ones the wiki has no NPC id for yet, matched by name
    @Test
    public void slayerLevelMonstersHaveTheirLevel() {
        assertEquals(15, DATA.statsFor("Banshee").slayerLevel);
        assertEquals(85, DATA.statsFor("Abyssal demon").slayerLevel);
        assertEquals(91, DATA.statsFor("Cerberus").slayerLevel);
        assertEquals(95, DATA.statsFor("Alchemical Hydra").slayerLevel);
        assertEquals(79, DATA.statsFor("Abyssal arbalest").slayerLevel);
        assertTrue(DATA.statsFor("Abyssal arbalest").needsSlayerLevel());
        // A task monster with no Slayer level: assigned, but anyone can kill it
        MonsterData.Stats hellhound = DATA.statsFor("Hellhound");
        assertEquals("Hellhounds", hellhound.slayerCategory);
        assertTrue(!hellhound.needsSlayerLevel());
    }

    // The Slayer card's Pray line, from the wiki attack styles of the task's monsters
    @Test
    public void protectionPrayerForTasks() {
        assertEquals(java.util.Collections.singletonList("Melee"), DATA.protectionPrayers(c -> c.equalsIgnoreCase("Fire giants")));
        assertEquals(java.util.Collections.singletonList("Magic"), DATA.protectionPrayers(c -> c.equalsIgnoreCase("Cave kraken")));
        assertEquals(java.util.Collections.singletonList("Missiles"), DATA.protectionPrayers(c -> c.equalsIgnoreCase("Smoke devils")));
        // Mixed with no style used by most of them: nothing rather than a guess
        assertTrue(DATA.protectionPrayers(c -> c.equalsIgnoreCase("Spiritual creatures")).isEmpty());
        assertEquals(null, MonsterData.protectionFor("Crush (100% prayer penetration)"));
    }
}
