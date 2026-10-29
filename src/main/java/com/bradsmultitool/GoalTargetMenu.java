package com.bradsmultitool;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Skill;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Adds "Track for goal" to the right-click menu of a Hunter creature, pickpocket NPC, fishing spot, tree,
 * rock or stall when its skill has a goal, so you can outline what you'd rather train on than the
 * suggestion. Choosing it again ("Untrack") goes back to the suggestion. It only changes what we outline.
 */
@Singleton
class GoalTargetMenu {

    private static final String TRACK = "Track for goal";
    private static final String UNTRACK = "Untrack for goal";

    private final Client client;
    private final SkillTracker plugin;
    private final SkillTrackerConfig config;
    private final SkillingData skilling;
    private final SkillHelperData helpers;

    @Inject
    GoalTargetMenu(Client client, SkillTracker plugin, SkillTrackerConfig config, SkillingData skilling,
                   SkillHelperData helpers) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.skilling = skilling;
        this.helpers = helpers;
    }

    // Next to Examine, like RuneLite's own NPC and object tagging
    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        if (!config.highlightSuggestedTraining()) {
            return;
        }
        MenuEntry entry = event.getMenuEntry();
        Skill skill = null;
        String key = null;
        if (entry.getType() == MenuAction.EXAMINE_NPC && entry.getNpc() != null && entry.getNpc().getName() != null) {
            NPC npc = entry.getNpc();
            String name = npc.getName().toLowerCase();
            skill = npcSkill(npc);
            key = "npc:" + name;
        } else if (entry.getType() == MenuAction.EXAMINE_OBJECT) {
            SkillingData.Resource resource = skilling.object(entry.getIdentifier(), Text.removeTags(entry.getTarget()));
            if (resource != null) {
                skill = skill(resource.skill);
                key = "object:" + resource.name;
            }
        }
        if (skill == null || plugin.getSkillGoal(skill) <= client.getSkillExperience(skill)) {
            return;
        }
        final Skill goalSkill = skill;
        final String target = key;
        boolean tracked = target.equals(plugin.getGoalTarget(goalSkill));
        client.getMenu().createMenuEntry(-1)
            .setOption(tracked ? UNTRACK : TRACK)
            .setTarget(event.getTarget())
            .setType(MenuAction.RUNELITE)
            .onClick(e -> plugin.setGoalTarget(goalSkill, tracked ? null : target));
    }

    // Hunter creature, pickpocket target or fishing spot
    private Skill npcSkill(NPC npc) {
        if (helpers.creature(npc.getName()) != null) {
            return Skill.HUNTER;
        }
        if (skilling.pickpocket(npc.getName()) != null) {
            return Skill.THIEVING;
        }
        NPCComposition comp = npc.getTransformedComposition() != null ? npc.getTransformedComposition() : npc.getComposition();
        if (comp != null) {
            for (String action : comp.getActions()) {
                if (action != null && SkillingData.isFishingOption(action)) {
                    return Skill.FISHING;
                }
            }
        }
        return null;
    }

    private static Skill skill(String name) {
        try {
            return Skill.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
