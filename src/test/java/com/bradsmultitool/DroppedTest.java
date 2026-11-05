package com.bradsmultitool;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** What a milestone's products went to, dropped included, survives saving. */
public class DroppedTest {

    @Test
    public void droppedIsSaved() {
        SkillPlans.Plan plan = new SkillPlans.Plan(60);
        SkillPlans.Step step = new SkillPlans.Step("Willow logs", 30, 60, null, 67.5);
        step.sold = 100;
        step.dropped = 250;
        plan.steps.add(step);
        SkillPlans.Plan back = SkillPlans.decode(SkillPlans.encode(plan));
        assertEquals(250, back.steps.get(0).dropped);
        assertEquals(100, back.steps.get(0).sold);
    }
}
