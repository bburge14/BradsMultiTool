package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

public class SkillingDataTest {

    private static final SkillingData DATA = new SkillingData();

    @BeforeClass
    public static void load() {
        DATA.load(new Gson());
    }

    @Test
    public void treesHaveLevelXpAndForestryTimer() {
        SkillingData.Resource willow = DATA.object(10819, null);
        assertNotNull(willow);
        assertEquals("Willow tree", willow.name);
        assertEquals(30, willow.level);
        assertEquals(67.5, willow.xp, 0.001);
        assertEquals(50, willow.chopTicks);
    }

    @Test
    public void rocksAndStalls() {
        assertEquals(35.0, DATA.object(11364, null).xp, 0.001); // Iron rocks
        SkillingData.Resource silk = DATA.object(11729, null);
        assertEquals("Silk stall", silk.name);
        assertEquals(20, silk.level);
    }

    @Test
    public void everyStallIncludingRenamedOnes() {
        // Draynor's wine stall is "Market stall" in game; the wiki lists it as Wine stall
        SkillingData.Resource wine = DATA.object(14011, null);
        assertEquals("Wine stall", wine.name);
        assertEquals(22, wine.level);
        assertEquals("Wine and jugs", wine.loot);
        assertEquals("Gems", DATA.object(30280, null).loot); // Mor Ul Rek gem counter
    }

    @Test
    public void fishingToolChecks() {
        assertTrue(SkillingData.hasFishingTool("Harpoon", Arrays.asList("dragon harpoon")));
        assertTrue(!SkillingData.hasFishingTool("Bait", Arrays.asList("fly fishing rod", "fishing bait")));
        assertTrue(SkillingData.hasFishingTool("Bait", Arrays.asList("fishing rod", "fishing bait")));
        assertTrue(!SkillingData.hasFishingTool("Small Net", Arrays.asList("lobster pot")));
    }

    @Test
    public void regularTreesGiveOneLog() {
        assertTrue(DATA.object(1276, null).oneLog);
        assertTrue(!DATA.object(10819, null).oneLog);
    }

    @Test
    public void unlistedObjectIdFallsBackToName() {
        assertEquals("Oak tree", DATA.object(-5, "Oak tree").name);
        assertNull(DATA.object(-5, "Bank booth"));
    }

    @Test
    public void pickpocketByNpcName() {
        SkillingData.Pickpocket man = DATA.pickpocket("Man");
        assertEquals(1, man.level);
        assertEquals(8.0, man.xp, 0.001);
        assertEquals(85, man.ardyCloakLevel);
        assertNull(DATA.pickpocket("Guard dog"));
    }

    @Test
    public void fishingAndTools() {
        assertTrue(SkillingData.isFishingOption("Harpoon"));
        List<String> seaNet = Arrays.asList("Net", "Bait");
        List<String> river = Arrays.asList("Lure", "Bait");
        assertEquals("Herring", SkillingData.fishFor("Bait", seaNet).get(1).name);
        assertEquals(1, SkillingData.fishFor("Bait", seaNet).size() - 1);
        assertEquals("Pike", SkillingData.fishFor("Bait", river).get(0).name);
        assertEquals("Shark", SkillingData.fishFor("Harpoon", Arrays.asList("Big Net", "Harpoon")).get(0).name);
        assertEquals("Swordfish", SkillingData.fishFor("Harpoon", Arrays.asList("Cage", "Harpoon")).get(1).name);
        assertEquals("Monkfish", SkillingData.fishFor("Net", Arrays.asList("Net", "Harpoon")).get(0).name);

        List<SkillingData.Tool> axes = SkillingData.toolsFor("Woodcutting");
        assertEquals("Rune axe", SkillingData.bestToolForLevel(axes, 55).name);
        assertEquals("Dragon axe", SkillingData.bestToolForLevel(axes, 61).name);
        List<String> owned = Arrays.asList("rune pickaxe", "mithril axe", "lobster");
        assertEquals("Mithril axe", SkillingData.bestOwnedTool(axes, owned).name);
        assertEquals("Rune pickaxe", SkillingData.bestOwnedTool(SkillingData.toolsFor("Mining"), owned).name);
    }
}
