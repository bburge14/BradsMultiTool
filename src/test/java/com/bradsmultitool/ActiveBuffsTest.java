package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;
import org.junit.Test;

public class ActiveBuffsTest {

    private static Map<Skill, Integer> boosts(int atk, int str, int def, int rng, int mag) {
        Map<Skill, Integer> m = new EnumMap<>(Skill.class);
        m.put(Skill.ATTACK, atk);
        m.put(Skill.STRENGTH, str);
        m.put(Skill.DEFENCE, def);
        m.put(Skill.RANGED, rng);
        m.put(Skill.MAGIC, mag);
        return m;
    }

    @Test
    public void superCombatAndRangingAreSeparateBuffsWithTheirOwnTimes() {
        BuffTimer timer = new BuffTimer();
        timer.onDecay(0);
        List<ActiveBuffs.Buff> buffs = ActiveBuffs.detect(boosts(16, 16, 16, 12, 0),
            Collections.emptyMap(), timer, 40, 0L, false);

        assertEquals(2, buffs.size());
        assertEquals("Super Combat", buffs.get(0).name);
        assertEquals(16, buffs.get(0).boost);
        assertEquals((60 + 15 * 100) * 600L, buffs.get(0).endMillis);
        assertEquals("Ranging", buffs.get(1).name);
        assertEquals((60 + 11 * 100) * 600L, buffs.get(1).endMillis);
        assertTrue(buffs.get(1).exact);
    }

    @Test
    public void divineStatsSharingAnEndTimeAreOnePotion() {
        Map<Skill, Long> divine = new EnumMap<>(Skill.class);
        divine.put(Skill.ATTACK, 300_000L);
        divine.put(Skill.STRENGTH, 300_100L);
        divine.put(Skill.DEFENCE, 300_000L);
        List<ActiveBuffs.Buff> buffs = ActiveBuffs.detect(boosts(19, 19, 19, 0, 7), divine,
            new BuffTimer(), 0, 1_000L, false);

        assertEquals(2, buffs.size());
        assertEquals("Divine Super Combat", buffs.get(0).name);
        assertEquals(299_000L, buffs.get(0).millisLeft(1_000L));
        assertEquals("Magic", buffs.get(1).name);
    }

    @Test
    public void mixedPotionsSplitIntoKnownKinds() {
        List<ActiveBuffs.Buff> buffs = ActiveBuffs.detect(boosts(5, 0, 10, 12, 0),
            Collections.emptyMap(), new BuffTimer(), 0, 0L, false);
        assertEquals("Bastion", buffs.get(0).name);
        assertEquals("Attack", buffs.get(1).name);
    }
}
