package com.bradsmultitool;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PanelFormatTest {

    @Test
    public void shortNumbersLikeRuneLitesTracker() {
        assertEquals("9,112", BradsMultiToolPanel.shortNumber(9112));
        assertEquals("55.8K", BradsMultiToolPanel.shortNumber(55_812));
        assertEquals("1.24M", BradsMultiToolPanel.shortNumber(1_240_000));
    }
}
