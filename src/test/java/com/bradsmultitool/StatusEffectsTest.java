package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
import org.junit.Test;

/** A game tick fills the buff bar (asking the game for OVERALL's level used to throw, leaving it empty). */
public class StatusEffectsTest {

    @Test
    public void aTickListsBoostsAndDrains() {
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getGameState": return GameState.LOGGED_IN;
                    case "getVarpValue": return 0;
                    case "getRealSkillLevel":
                    case "getBoostedSkillLevel": {
                        Skill skill = (Skill) args[0];
                        if ("OVERALL".equals(skill.name())) {
                            throw new IllegalArgumentException("no level for OVERALL");
                        }
                        boolean boosted = method.getName().equals("getBoostedSkillLevel");
                        if (skill == Skill.HITPOINTS && boosted) {
                            return 121;
                        }
                        if (skill == Skill.DEFENCE && boosted) {
                            return 62;
                        }
                        return skill == Skill.HITPOINTS ? 99 : 70;
                    }
                    default: return null;
                }
            });
        StatusEffects effects = new StatusEffects(client, new PotionAlerts(null, null, null, null));
        effects.onGameTick(new GameTick());
        List<StatusEffects.Effect> list = effects.effects();
        assertEquals(2, list.size());
        assertEquals("Hitpoints boosted", list.get(0).name);
        assertEquals("+22", list.get(0).amount);
        assertEquals("Defence drained", list.get(1).name);
        assertEquals("-8", list.get(1).amount);
        assertTrue(list.get(1).debuff);
    }
}
