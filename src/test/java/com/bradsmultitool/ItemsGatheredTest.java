package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Skill;
import org.junit.Test;

public class ItemsGatheredTest {

    private static ItemsGathered.Kind spam(String message) {
        return ItemsGathered.of(ChatMessageType.SPAM, message);
    }

    @Test
    public void skillingMessages() {
        assertEquals(ItemsGathered.Kind.LOGS, spam("You get some oak logs."));
        assertEquals(ItemsGathered.Kind.LOGS, spam("You get some logs."));
        assertEquals(ItemsGathered.Kind.FISH, spam("You catch a trout."));
        assertEquals(ItemsGathered.Kind.FISH, spam("You catch some shrimps."));
        assertEquals(ItemsGathered.Kind.ORE, spam("You manage to mine some copper."));
        assertEquals(ItemsGathered.Kind.ORE, ItemsGathered.of(ChatMessageType.GAMEMESSAGE, "You manage to mine some iron."));
        assertEquals(ItemsGathered.Kind.GEMS, spam("You just found a sapphire!"));
        assertEquals(ItemsGathered.Kind.COOKED, spam("You successfully cook a lobster."));
        assertEquals(ItemsGathered.Kind.BURNT, spam("You accidentally burn the lobster."));
        assertNull(spam("You swing your pick at the rock."));
        assertNull(ItemsGathered.of(ChatMessageType.PUBLICCHAT, "You get some logs."));
        assertEquals(Skill.COOKING, ItemsGathered.Kind.BURNT.skill);
    }

    @Test
    public void countsAndRates() {
        // 342 logs for 23,000 XP at 70,000 XP/hr: 1,040 an hour
        assertEquals("342 (1,040/hr)", ItemsGathered.countAndRate(342, 23_000, 70_000));
        assertEquals("5", ItemsGathered.countAndRate(5, 0, 0)); // no rate before there's an XP rate
        assertEquals("Steals", ItemsGathered.actionNoun(net.runelite.api.Skill.THIEVING));
        assertEquals(null, ItemsGathered.actionNoun(net.runelite.api.Skill.ATTACK));
    }

    @Test
    public void sessionsKeptAsText() {
        SkillTracker.SavedSession session = new SkillTracker.SavedSession(1_000_000L);
        session.skills.put(Skill.WOODCUTTING, new long[]{12_000, 3000, 175, 999_000});
        session.items.put(ItemsGathered.Kind.LOGS, 342);
        SkillTracker.SavedSession back = SkillTracker.decode(SkillTracker.encode(session));
        assertEquals(1_000_000L, back.savedAt);
        assertEquals(12_000, back.skills.get(Skill.WOODCUTTING)[0]);
        assertEquals(175, back.skills.get(Skill.WOODCUTTING)[2]);
        assertEquals(342, (int) back.items.get(ItemsGathered.Kind.LOGS));
        assertNull(SkillTracker.decode("2;whatever"));
        assertNull(SkillTracker.decode("1;notanumber"));
        assertNull(SkillTracker.decode(null));
        assertTrue(SkillTracker.recentEnough(0, 29 * 60_000L, 30));
        assertFalse(SkillTracker.recentEnough(0, 31 * 60_000L, 30));
        assertFalse(SkillTracker.recentEnough(0, 1000, 0)); // turned off
        assertFalse(SkillTracker.recentEnough(5000, 1000, 30)); // saved "in the future" (clock changed)
    }

    @Test
    public void skillsTabGoalProgress() {
        assertEquals(0.5, BradsMultiToolSkillsTabOverlay.progress(150, 100, 200), 0.001);
        assertEquals(1, BradsMultiToolSkillsTabOverlay.progress(250, 100, 200), 0.001);
        assertEquals(0, BradsMultiToolSkillsTabOverlay.progress(50, 100, 200), 0.001); // start after you: from you
        // Skills Tab Progress Bars' height at the bottom: its bar, doubled with its goal bars (halved past 15 each)
        assertEquals(2, BradsMultiToolSkillsTabOverlay.theirHeight(2, false));
        assertEquals(4, BradsMultiToolSkillsTabOverlay.theirHeight(2, true));
        assertEquals(20, BradsMultiToolSkillsTabOverlay.theirHeight(20, true));
        assertTrue(BradsMultiToolSkillsTabOverlay.component(Skill.SAILING) > 0);
    }
}
