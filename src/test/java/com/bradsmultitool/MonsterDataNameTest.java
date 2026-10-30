package com.bradsmultitool;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.google.gson.Gson;
import org.junit.Test;

public class MonsterDataNameTest {

    @Test
    public void unknownIdFallsBackToName() {
        MonsterData data = new MonsterData();
        data.load(new Gson());
        MonsterData.Stats banshee = data.get(414);
        assertNotNull(banshee);
        // An id the wiki doesn't list, with a known name, gets that monster's stats
        assertSame(banshee, data.get(-1, "Banshee"));
        assertNull(data.get(-1, "Not a monster"));
    }
}
