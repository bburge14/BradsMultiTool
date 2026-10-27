package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class WeaponStylesTest {

    @Test
    public void stabSwordThirdButtonIsSlash() {
        WeaponStyles.Style style = WeaponStyles.resolve(17, 2, false, 0);
        assertEquals(MonsterWeakness.Type.MELEE, style.type());
        assertEquals("Slash", style.label());
    }

    @Test
    public void rangedWeaponsReportAmmoType() {
        assertEquals("Rapid (Bow)", WeaponStyles.resolve(3, 1, false, 0).label());
        assertEquals("Rapid (Thrown)", WeaponStyles.resolve(19, 1, false, 0).label());
        assertEquals("Heavy", WeaponStyles.resolve(7, 0, false, 0).damageType);
        assertEquals("Heavy", WeaponStyles.resolve(5, 0, false, 0).damageType);
        assertEquals("Light", WeaponStyles.resolve(19, 3, false, 0).damageType);
    }

    @Test
    public void autocastSpellElement() {
        assertEquals(MonsterWeakness.Element.AIR, WeaponStyles.autocastElement(1));
        assertEquals(MonsterWeakness.Element.FIRE, WeaponStyles.autocastElement(12));
        assertEquals(MonsterWeakness.Element.WATER, WeaponStyles.autocastElement(14));
        assertEquals(MonsterWeakness.Element.EARTH, WeaponStyles.autocastElement(50));
        assertNull(WeaponStyles.autocastElement(0));

        WeaponStyles.Style casting = WeaponStyles.resolve(18, 4, false, 4);
        assertEquals(MonsterWeakness.Type.MAGIC, casting.type());
        assertEquals("Spell Cast (Fire Strike)", casting.label());
        assertEquals("Spell Cast (Water Wave)", WeaponStyles.resolve(18, 4, false, 14).label());
        assertEquals("Spell Cast (Earth Surge)", WeaponStyles.resolve(18, 4, false, 50).label());
    }
}
