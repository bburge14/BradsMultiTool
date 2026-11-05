package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/** A supply milestone whose supplies you have all of is passed over: the goal goes on to the next one. */
public class SuppliesGotTest {

    private static SkillPlans.Plan plan() {
        SkillPlans.Plan plan = new SkillPlans.Plan(65);
        SkillPlans.Step maple = new SkillPlans.Step("Maple logs", 46, 56, null, 100);
        maple.supplies.add(new SkillPlans.Supply("FIREMAKING", "Maple logs", 1550));
        SkillPlans.Step pine = new SkillPlans.Step("Arctic pine logs", 58, 59, null, 40);
        pine.supplies.add(new SkillPlans.Supply("FIREMAKING", "Arctic pine logs", 600));
        SkillPlans.Step yew = new SkillPlans.Step("Yew logs", 59, 65, null, 175);
        plan.steps.add(maple);
        plan.steps.add(pine);
        plan.steps.add(yew);
        return plan;
    }

    @Test
    public void nothingGotIsTheUsualMilestone() {
        SkillPlans.Plan plan = plan();
        assertSame(plan.steps.get(1), plan.milestone(57, Collections.emptySet()));
        assertSame(plan.steps.get(1), plan.milestone(58, null));
    }

    @Test
    public void gotAllSkipsToTheNext() {
        SkillPlans.Plan plan = plan();
        Set<String> got = new HashSet<>();
        got.add("arctic pine logs");
        // In the gap before it, and on its levels: the next milestone
        assertSame(plan.steps.get(2), plan.milestone(57, got));
        assertSame(plan.steps.get(2), plan.milestone(58, got));
        // Before it: still the one you're on
        assertEquals("Maple logs", plan.milestone(50, got).action);
    }
}
