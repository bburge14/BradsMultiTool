package com.bradsmultitool;

import static org.junit.Assert.assertEquals;

import java.awt.Color;
import org.junit.Test;

public class CombatLevelColorTest {

    @Test
    public void matchesTheGameMenuColours() {
        assertEquals(new Color(0xffff00), BradsMultiToolOverlay.combatLevelColor(80, 80));
        assertEquals(new Color(0x00ff00), BradsMultiToolOverlay.combatLevelColor(100, 21));
        assertEquals(new Color(0xff0000), BradsMultiToolOverlay.combatLevelColor(80, 160));
        assertEquals(new Color(0xffb000), BradsMultiToolOverlay.combatLevelColor(80, 82));
        assertEquals(new Color(0xc0ff00), BradsMultiToolOverlay.combatLevelColor(80, 78));
        assertEquals(Color.WHITE, BradsMultiToolOverlay.combatLevelColor(80, 0));
    }
}
