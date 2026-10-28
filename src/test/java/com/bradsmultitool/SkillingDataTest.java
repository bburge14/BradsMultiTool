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
        assertEquals(4.8, silk.respawnSeconds, 0.001);
    }

    @Test
    public void respawnTimes() {
        SkillingData.Resource tree = DATA.object(1276, null);
        assertTrue(tree.oneLog);
        assertEquals(36, tree.respawnSeconds, 0.001);
        assertEquals(60, tree.respawnMaxSeconds, 0.001);
        assertEquals(720, DATA.object(-5, "Runite rocks").respawnSeconds, 0.001);
        assertEquals(5.4, DATA.object(11364, null).respawnSeconds, 0.001);
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
        assertEquals("Shark", SkillingData.fishFor("harpoon").get(2).name);

        List<SkillingData.Tool> axes = SkillingData.toolsFor("Woodcutting");
        assertEquals("Rune axe", SkillingData.bestToolForLevel(axes, 55).name);
        assertEquals("Dragon axe", SkillingData.bestToolForLevel(axes, 61).name);
        List<String> owned = Arrays.asList("rune pickaxe", "mithril axe", "lobster");
        assertEquals("Mithril axe", SkillingData.bestOwnedTool(axes, owned).name);
        assertEquals("Rune pickaxe", SkillingData.bestOwnedTool(SkillingData.toolsFor("Mining"), owned).name);
    }
}
