package com.bradsmultitool;

import static org.junit.Assert.assertEquals;

import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/** Defence after a special attack lands (the wiki's rules for each weapon). */
public class MonsterEffectsTest {

    @Test
    public void defenceDrains() {
        // Dragon warhammer: 30% of current Defence on a hit, nothing on a miss
        assertEquals(140, MonsterEffects.drained(ItemID.DRAGON_WARHAMMER, 200, 200, 40, false));
        assertEquals(98, MonsterEffects.drained(ItemID.DRAGON_WARHAMMER, 140, 200, 12, false));
        assertEquals(200, MonsterEffects.drained(ItemID.DRAGON_WARHAMMER, 200, 200, 0, false));
        // Elder maul: 35%
        assertEquals(130, MonsterEffects.drained(ItemID.ELDER_MAUL, 200, 200, 30, false));
        // Bandos godsword: the damage dealt, not below 0
        assertEquals(155, MonsterEffects.drained(ItemID.BGS, 200, 200, 45, false));
        assertEquals(0, MonsterEffects.drained(ItemID.BGS, 20, 200, 45, false));
        // Arclight: 5% of base + 1, 10% + 1 against demons
        assertEquals(189, MonsterEffects.drained(ItemID.ARCLIGHT, 200, 200, 20, false));
        assertEquals(179, MonsterEffects.drained(ItemID.ARCLIGHT, 200, 200, 20, true));
        // Any other weapon: no change
        assertEquals(200, MonsterEffects.drained(ItemID.ABYSSAL_WHIP, 200, 200, 40, false));
    }
}
