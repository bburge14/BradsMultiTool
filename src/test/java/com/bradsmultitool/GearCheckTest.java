package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import net.runelite.client.game.ItemEquipmentStats;
import org.junit.Test;

public class GearCheckTest {

    @Test
    public void anItemsStyleIsTheDamageItGives() {
        // Berserker ring: strength
        assertEquals(GearCheck.Style.MELEE, GearCheck.damageStyle(ItemEquipmentStats.builder().str(4).build()));
        // Necklace of anguish: ranged strength (its accuracy doesn't matter at the crab)
        assertEquals(GearCheck.Style.RANGED, GearCheck.damageStyle(ItemEquipmentStats.builder().arange(15).rstr(5).build()));
        // Occult necklace: magic damage
        assertEquals(GearCheck.Style.MAGIC, GearCheck.damageStyle(ItemEquipmentStats.builder().amagic(12).mdmg(5).build()));
        // A d'hide body: ranged attack only, no damage, so any style can wear it
        assertNull(GearCheck.damageStyle(ItemEquipmentStats.builder().arange(30).amagic(-15).build()));
    }

    @Test
    public void stylesFromCombatStyle() {
        assertEquals(GearCheck.Style.RANGED, GearCheck.of("Ranged"));
        assertEquals(GearCheck.Style.MAGIC, GearCheck.of("Magic"));
        assertEquals(GearCheck.Style.MELEE, GearCheck.of("Melee"));
    }
}
