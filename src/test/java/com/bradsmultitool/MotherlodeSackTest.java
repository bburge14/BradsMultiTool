package com.bradsmultitool;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MotherlodeSackTest {

    @Test
    public void fullWhenYourPayDirtFillsIt() {
        assertTrue(MotherlodeSack.full(80, 28, 108)); // exactly fills it
        assertFalse(MotherlodeSack.full(79, 28, 108)); // one to spare
        assertFalse(MotherlodeSack.full(108, 0, 108)); // nothing carried
        assertFalse(MotherlodeSack.full(150, 28, 189)); // upgraded sack: 11 spare
    }
}
