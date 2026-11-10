package com.bradsmultitool;

import static org.junit.Assert.assertEquals;

import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import org.junit.Test;

/** Every Forestry event is told by the NPCs and objects it starts with, whichever spawns first. */
public class ForestryEventsTest {

    @Test
    public void eachEventsStarts() {
        assertEquals("Rising Roots", ForestryAlerts.eventOfObject(ObjectID.GATHERING_EVENT_RISING_ROOTS));
        assertEquals("Struggling Sapling", ForestryAlerts.eventOfObject(ObjectID.GATHERING_EVENT_SAPLING_2X2));
        assertEquals("Bee Hive", ForestryAlerts.eventOfNpc(NpcID.GATHERING_EVENT_BEES_BEEKEEPER));
        assertEquals("Bee Hive", ForestryAlerts.eventOfNpc(NpcID.GATHERING_EVENT_BEES_BEEBOX_1));
        assertEquals("Pheasant Control", ForestryAlerts.eventOfNpc(NpcID.GATHERING_EVENT_PHEASANT));
        assertEquals("Poachers", ForestryAlerts.eventOfNpc(NpcID.GATHERING_EVENT_POACHERS_POACHER02_OUTDOORS));
        assertEquals("Flowering Tree", ForestryAlerts.eventOfNpc(NpcID.GATHERING_EVENT_FLOWERING_TREE_BEES));
        assertEquals("Friendly Ent", ForestryAlerts.eventOfNpc(NpcID.GATHERING_EVENT_ENT_STUMP_01));
        assertEquals("Leprechaun", ForestryAlerts.eventOfNpc(NpcID.GATHERING_EVENT_WOODCUTTING_LEPRECHAUN));
        assertEquals("Enchantment Ritual", ForestryAlerts.eventOfNpc(NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_DRYAD));
    }
}
