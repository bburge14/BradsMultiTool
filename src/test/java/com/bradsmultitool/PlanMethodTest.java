package com.bradsmultitool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.List;
import net.runelite.api.Skill;
import org.junit.BeforeClass;
import org.junit.Test;

/** A plan milestone in the goal's terms: what to train on and the tools it needs, from its action. */
public class PlanMethodTest {
    private static TrainingAdvisor advisor;

    @BeforeClass
    public static void load() {
        Gson gson = new Gson();
        TrainingData training = new TrainingData();
        training.load(gson);
        SkillingData skilling = new SkillingData();
        skilling.load(gson);
        SkillHelperData helpers = new SkillHelperData();
        helpers.load(gson);
        advisor = new TrainingAdvisor(null, null, null, training, null, null, null, skilling, helpers, null);
    }

    private static List<String> tools(Skill skill, String action) {
        TrainingData.Method method = advisor.planMethod(skill, new SkillPlans.Step(action, 46, 58, null, 0));
        assertNotNull(action, method);
        return advisor.toolsFor(skill, method);
    }

    @Test
    public void mapleLogsNeedAnAxe() {
        TrainingData.Method method = advisor.planMethod(Skill.WOODCUTTING, new SkillPlans.Step("Maple logs", 46, 58, null, 0));
        assertFalse(method.objects.isEmpty());
        assertEquals("[Axe]", tools(Skill.WOODCUTTING, "Maple logs").toString());
        assertEquals("[Axe]", tools(Skill.WOODCUTTING, "Teak logs").toString());
        assertEquals("[Pickaxe]", tools(Skill.MINING, "Iron ore").toString());
    }

    @Test
    public void fishBringTheirGear() {
        List<String> shark = tools(Skill.FISHING, "Raw shark");
        assertTrue(shark.toString(), shark.stream().anyMatch(t -> t.toLowerCase().contains("harpoon")));
    }

    @Test
    public void betweenMilestonesItsTheNextOne() {
        SkillPlans.Plan plan = new SkillPlans.Plan(60);
        plan.steps.add(new SkillPlans.Step("Willow logs", 30, 45, null, 0));
        plan.steps.add(new SkillPlans.Step("Maple logs", 48, 60, null, 0));
        assertEquals("Maple logs", plan.milestone(46).action);
    }
}
