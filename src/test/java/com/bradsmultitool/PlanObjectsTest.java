package com.bradsmultitool;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.Set;
import org.junit.Test;

/** A plan milestone with no guide method still finds its trees and rocks (for the outlines and the tools). */
public class PlanObjectsTest {
    @Test
    public void mapleLogsAreMapleTrees() {
        SkillingData skilling = new SkillingData();
        skilling.load(new Gson());
        Set<Integer> maples = skilling.objectIds("Woodcutting", n -> MethodDetector.sameThing(n, "Maple logs"));
        assertFalse(maples.isEmpty());
        for (int id : maples) {
            assertTrue(skilling.object(id, null).name.toLowerCase().contains("maple"));
        }
        assertFalse(skilling.objectIds("Mining", n -> MethodDetector.sameThing(n, "Iron ore")).isEmpty());
    }
}
