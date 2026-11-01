package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.BeforeClass;
import org.junit.Test;

public class ArenaRouteTest {

    private static final SkillHelperData DATA = new SkillHelperData();

    @BeforeClass
    public static void load() {
        DATA.load(new Gson());
    }

    @Test
    public void obstaclesAndDispensers() {
        SkillHelperData.ArenaObstacle spikes = DATA.arenaObstacle(3582);
        assertNotNull(spikes);
        assertEquals("Floor spikes", spikes.name);
        assertEquals(20, spikes.level);
        assertEquals(24, spikes.xp, 0.01);
        assertEquals(4, spikes.ticks);
        assertTrue(DATA.isArenaDispenser(3608));
        assertEquals("Pillar", DATA.arenaObstacle(3579).name);
    }

    @Test
    public void colourByLevel() {
        SkillHelperData.ArenaObstacle spikes = DATA.arenaObstacle(3582);
        assertEquals(ArenaRoute.Status.TOO_LOW, ArenaRoute.status(spikes, 19));
        assertEquals(ArenaRoute.Status.MAY_FAIL, ArenaRoute.status(spikes, 30)); // fails until 50
        assertEquals(ArenaRoute.Status.SAFE, ArenaRoute.status(spikes, 50));
        assertEquals(ArenaRoute.Status.SAFE, ArenaRoute.status(DATA.arenaObstacle(3566), 1)); // rope swing
        SkillHelperData.ArenaObstacle blades = DATA.arenaObstacle(3580);
        // The success chart: 154/256 at 40 rising to certain at 99
        assertEquals(ArenaRoute.Status.MAY_FAIL, ArenaRoute.status(blades, 70));
        assertEquals(1, blades.successChance(99), 1e-9);
        assertTrue(blades.successChance(40) < blades.successChance(70));
        assertEquals(ArenaRoute.Status.MAY_FAIL, ArenaRoute.status(DATA.arenaObstacle(3570), 98)); // plank
        assertEquals(ArenaRoute.Status.SAFE, ArenaRoute.status(DATA.arenaObstacle(3570), 99));
    }

    @Test
    public void routeAvoidsWhatYouCantDo() {
        // A square of pillars: the quick way (floor spikes, then a rope swing) needs 20 Agility
        WorldPoint a = new WorldPoint(2761, 9546, 0);
        WorldPoint b = new WorldPoint(2772, 9546, 0);
        WorldPoint c = new WorldPoint(2772, 9557, 0);
        WorldPoint d = new WorldPoint(2761, 9557, 0);
        ArenaRoute.Link spikes = new ArenaRoute.Link(a, b, DATA.arenaObstacle(3582));
        ArenaRoute.Link swing = new ArenaRoute.Link(b, c, DATA.arenaObstacle(3566));
        ArenaRoute.Link bars = new ArenaRoute.Link(a, d, DATA.arenaObstacle(3563));
        ArenaRoute.Link wall = new ArenaRoute.Link(d, c, DATA.arenaObstacle(3565));
        List<ArenaRoute.Link> links = Arrays.asList(spikes, swing, bars, wall);
        for (int level : new int[] {10, 60}) {
            for (ArenaRoute.Link link : links) {
                link.cost = ArenaRoute.cost(link.obstacle, level);
            }
            List<ArenaRoute.Link> route = ArenaRoute.shortest(links, a, c);
            assertEquals(level < 20 ? Arrays.asList(bars, wall) : Arrays.asList(spikes, swing), route);
        }
    }

    @Test
    public void pillarGrid() {
        // The wiki's pins: floor spikes at 2761,9573 sit between the pillars at 2761,9568 and 2761,9579; a
        // pressure pad at 2799,9579 between 2794,9579 and 2805,9579
        int[] grid = DATA.arenaGrid();
        assertEquals(5, grid.length);
        WorldPoint[] spikes = ArenaRoute.gap(new WorldPoint(2761, 9573, 3), grid);
        assertEquals(new WorldPoint(2761, 9568, 0), spikes[0]);
        assertEquals(new WorldPoint(2761, 9579, 0), spikes[1]);
        WorldPoint[] pad = ArenaRoute.gap(new WorldPoint(2799, 9579, 0), grid);
        assertEquals(new WorldPoint(2794, 9579, 0), pad[0]);
        assertEquals(new WorldPoint(2805, 9579, 0), pad[1]);
        // The middle pillars have no dispenser but are still pillars
        assertEquals(new WorldPoint(2783, 9568, 0), ArenaRoute.nearestPillar(new WorldPoint(2785, 9566, 3), grid));
        // On a pillar itself, no gap
        assertEquals(null, ArenaRoute.gap(new WorldPoint(2783, 9568, 0), grid));
    }
}
