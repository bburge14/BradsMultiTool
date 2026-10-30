package com.bradsmultitool;

import net.runelite.api.gameval.DBTableID.CombatInterfaceWeaponCategory.Row;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class WeaponStylesTest {

    @Test
    public void stabSwordThirdButtonIsSlash() {
        WeaponStyles.Style style = WeaponStyles.resolve(Row.COMBAT_INTERFACE_STABSWORD, 2, false, 0, null);
        assertEquals(MonsterWeakness.Type.MELEE, style.type());
        assertEquals("Slash", style.label());
    }

    @Test
    public void rangedWeaponsReportAmmoType() {
        assertEquals("Rapid (Bow)", WeaponStyles.resolve(Row.COMBAT_INTERFACE_BOW, 1, false, 0, null).label());
        assertEquals("Rapid (Thrown)", WeaponStyles.resolve(Row.COMBAT_INTERFACE_THROWN, 1, false, 0, null).label());
        assertEquals("Heavy", WeaponStyles.resolve(Row.COMBAT_INTERFACE_GRENADE, 0, false, 0, null).damageType);
        assertEquals("Heavy", WeaponStyles.resolve(Row.COMBAT_INTERFACE_CROSSBOW, 0, false, 0, null).damageType);
        assertEquals("Light", WeaponStyles.resolve(Row.COMBAT_INTERFACE_THROWN, 3, false, 0, null).damageType);
    }

    @Test
    public void thrownWeaponsAreNamedFromTheEquippedItem() {
        WeaponStyles.Style thrown = WeaponStyles.resolve(Row.COMBAT_INTERFACE_THROWN, 1, false, 0, null);
        assertEquals("Rapid (Dart)", WeaponStyles.withEquippedWeapon(thrown, "Dragon dart").label());
        assertEquals("Rapid (Knife)", WeaponStyles.withEquippedWeapon(thrown, "Rune knife(p++)").label());
        assertEquals("Rapid (Thrownaxe)", WeaponStyles.withEquippedWeapon(thrown, "Morrigan's throwing axe").label());
        assertEquals("Rapid (Blowpipe)", WeaponStyles.withEquippedWeapon(thrown, "Toxic blowpipe").label());
        assertEquals("Rapid (Toktz-xil-ul)", WeaponStyles.withEquippedWeapon(thrown, "Toktz-xil-ul").label());
        assertEquals("Rapid (Tonalztics)", WeaponStyles.withEquippedWeapon(thrown, "Tonalztics of ralos").label());
        assertEquals("Light", WeaponStyles.withEquippedWeapon(thrown, "Rune thrownaxe").damageType);

        WeaponStyles.Style bow = WeaponStyles.resolve(Row.COMBAT_INTERFACE_BOW, 1, false, 0, null);
        assertEquals("Rapid (Bow)", WeaponStyles.withEquippedWeapon(bow, "Magic shortbow").label());
    }

    @Test
    public void manualCastsAreNamedAndElementalSpellsGetTheirElement() {
        WeaponStyles.Style fireBolt = WeaponStyles.manualCast("Fire Bolt");
        assertEquals(MonsterWeakness.Type.MAGIC, fireBolt.type());
        assertEquals(MonsterWeakness.Element.FIRE, fireBolt.element);
        assertEquals("Fire Bolt", fireBolt.label());
        assertEquals(MonsterWeakness.Element.AIR, WeaponStyles.manualCast("Wind Surge").element);

        WeaponStyles.Style barrage = WeaponStyles.manualCast("Ice Barrage");
        assertNull(barrage.element);
        assertEquals("Ice Barrage", barrage.label());
        assertNull(WeaponStyles.manualCast("Fire Bloom").element);
        assertEquals("Ice Barrage (Def)", WeaponStyles.spellStyle("Ice Barrage", true).label());
        assertEquals(MonsterWeakness.Element.WATER, WeaponStyles.spellStyle("Water Surge", false).element);
    }

    @Test
    public void autocastSpellElement() {
        assertEquals(MonsterWeakness.Element.AIR, WeaponStyles.autocastElement(1));
        assertEquals(MonsterWeakness.Element.FIRE, WeaponStyles.autocastElement(12));
        assertEquals(MonsterWeakness.Element.WATER, WeaponStyles.autocastElement(14));
        assertEquals(MonsterWeakness.Element.EARTH, WeaponStyles.autocastElement(50));
        assertNull(WeaponStyles.autocastElement(0));

        WeaponStyles.Style casting = WeaponStyles.resolve(Row.COMBAT_INTERFACE_STAFF, 4, false, 4, null);
        assertEquals(MonsterWeakness.Type.MAGIC, casting.type());
        assertEquals("Fire Strike", casting.label());
        assertEquals("Water Wave", WeaponStyles.resolve(Row.COMBAT_INTERFACE_STAFF, 4, false, 14, null).label());
        assertEquals("Earth Surge", WeaponStyles.resolve(Row.COMBAT_INTERFACE_STAFF, 4, false, 50, null).label());
        assertEquals("Wind Strike", WeaponStyles.resolve(Row.COMBAT_INTERFACE_STAFF, 4, false, 1, null).label());
        assertEquals("Fire Wave (Def)", WeaponStyles.resolve(Row.COMBAT_INTERFACE_STAFF, 4, true, 16, null).label());
    }

    @Test
    public void godswordStylesAreMelee() {
        for (int row : new int[]{Row.COMBAT_INTERFACE_HEAVYSWORD, Row.COMBAT_INTERFACE_HEAVYSWORD_LARGE}) {
            for (int button = 0; button < 4; button++) {
                assertEquals(MonsterWeakness.Type.MELEE, WeaponStyles.resolve(row, button, false, 0, null).type());
            }
            assertEquals("Smash (Crush)", WeaponStyles.resolve(row, 2, false, 0, null).label());
        }
    }

    @Test
    public void unknownWeaponTypesUseTheGamesStyleName() {
        assertEquals(MonsterWeakness.Type.RANGED, WeaponStyles.resolve(-1, 1, false, 0, "Rapid").type());
        assertEquals(MonsterWeakness.Type.MAGIC, WeaponStyles.resolve(-1, 0, false, 0, "Casting").type());
        assertEquals("Aggressive", WeaponStyles.resolve(-1, 1, false, 0, "AGGRESSIVE").label());
        assertEquals(MonsterWeakness.Type.MELEE,
            WeaponStyles.resolve(Row.COMBAT_INTERFACE_SLASHFLAIL, 1, false, 0, "Rapid").type());
        assertEquals("Unknown", WeaponStyles.resolve(-1, 1, false, 0, null).buttonName);
    }
}
