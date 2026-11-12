package com.bradsmultitool;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** What you're training matches a plan milestone's action when it's the same thing by another name. */
public class SameThingTest {
    @Test
    public void treesRocksAndFish() {
        assertTrue(MethodDetector.sameThing("Maple tree", "Maple logs"));
        assertTrue(MethodDetector.sameThing("Teak", "Teak logs"));
        assertTrue(MethodDetector.sameThing("Iron rocks", "Iron ore"));
        assertTrue(MethodDetector.sameThing("Yew", "Yew logs"));
        assertFalse(MethodDetector.sameThing("Willow tree", "Maple logs"));
        assertFalse(MethodDetector.sameThing("Oak tree", "Logs"));
        assertFalse(MethodDetector.sameThing("Tree", "Oak logs"));
    }
}
