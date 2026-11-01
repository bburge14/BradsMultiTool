package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ClashTipTest {

    @Test
    public void longNotesWrap() {
        String note = "- RuneLite's Agility plugin outlines most Brimhaven Arena obstacles, so Snuffy's level colours only "
            + "show on the rest; turn off its Show Clickboxes to see Snuffy's colours on all of them";
        String wrapped = PluginClashes.wrap(note);
        assertTrue(wrapped.startsWith("</br>- RuneLite's"));
        for (String line : wrapped.substring(5).split("</br>")) {
            assertTrue(line, line.length() <= 55);
        }
        assertEquals(note, wrapped.substring(5).replace("</br>  ", " "));
    }
}
