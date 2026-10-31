package com.snuffys.multitool;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Notices what you're actually training a goal skill on (the fishing spot and the way you fish it, the tree,
 * rock or stall, who you pickpocket, the creature you hunt, the monster you fight) when it isn't the suggestion
 * or your "Track for goal" pick, and after a few XP drops from it offers to track it instead: a line on the
 * skill's tracker card with Track / Dismiss, and a chat message. Only reads your clicks and XP; it never acts.
 */
@Singleton
class MethodDetector {

    /** XP drops from the same thing before it's offered (hits for combat skills). */
    static final int DROPS_BEFORE_OFFER = 5;
    static final int HITS_BEFORE_OFFER = 15;
    /** A click older than this (ticks) no longer explains an XP drop. */
    private static final int CLICK_MEMORY_TICKS = 100;

    private final Client client;
    private final SkillTracker plugin;
    private final SkillingData skilling;
    private final SkillHelperData helpers;
    private final CombatStyle combatStyle;
    private final PluginIdentity identity;

    // What you last clicked that trains a skill, and when
    private Skill clickedSkill;
    private String clickedTarget;
    private NPC clickedSpot;
    private int clickTick = -1000;

    private final Map<Skill, Integer> lastXp = new EnumMap<>(Skill.class);
    // Drops in a row from the same thing, per skill
    private final Map<Skill, String> streakTarget = new EnumMap<>(Skill.class);
    private final Map<Skill, Integer> streak = new EnumMap<>(Skill.class);
    // What's on offer per skill, and what you've dismissed this session
    // (read by the tracker panel on Swing's thread)
    private final Map<Skill, String> offers = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<String> dismissed = new HashSet<>();

    @Inject
    MethodDetector(Client client, SkillTracker plugin, SkillingData skilling, SkillHelperData helpers,
                   CombatStyle combatStyle, PluginIdentity identity) {
        this.client = client;
        this.plugin = plugin;
        this.skilling = skilling;
        this.helpers = helpers;
        this.combatStyle = combatStyle;
        this.identity = identity;
    }

    /** What's offered for this skill's goal (a target, see {@link GoalTarget}), or null. */
    String offer(Skill skill) {
        return offers.get(skill);
    }

    void accept(Skill skill) {
        String target = offers.remove(skill);
        if (target != null) {
            plugin.setGoalTarget(skill, target);
        }
    }

    void dismiss(Skill skill) {
        String target = offers.remove(skill);
        if (target != null) {
            dismissed.add(skill.name() + "|" + target);
            plugin.updatePanelUI();
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING) {
            clear();
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (event.getMenuAction() == MenuAction.RUNELITE) {
            return;
        }
        String option = Text.removeTags(event.getMenuOption()).toLowerCase();
        NPC npc = event.getMenuEntry().getNpc();
        Skill skill = null;
        String target = null;
        NPC spot = null;
        if (npc != null && npc.getName() != null) {
            String name = npc.getName();
            if (SkillingData.isFishingOption(option) && helpers.fishingSpot(npc.getId()) != null) {
                skill = Skill.FISHING;
                spot = npc;
            } else if (option.equals("pickpocket")) {
                skill = Skill.THIEVING;
                target = GoalTarget.NPC + name.toLowerCase();
            } else if (helpers.creature(name) != null) {
                skill = Skill.HUNTER;
                target = GoalTarget.NPC + name.toLowerCase();
            }
        } else if (event.getMenuAction().name().startsWith("GAME_OBJECT")) {
            SkillingData.Resource resource = skilling.object(event.getId(), Text.removeTags(event.getMenuTarget()));
            skill = resource != null ? skill(resource.skill) : null;
            target = skill != null ? GoalTarget.OBJECT + resource.name : null;
        }
        if (skill != null) {
            clickedSkill = skill;
            clickedTarget = target;
            clickedSpot = spot;
            clickTick = client.getTickCount();
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged event) {
        Skill skill = event.getSkill();
        Integer before = lastXp.put(skill, event.getXp());
        int drop = before == null ? 0 : event.getXp() - before;
        if (drop <= 0 || plugin.getSkillGoal(skill) <= event.getXp()) {
            return;
        }
        String target = null;
        int needed = DROPS_BEFORE_OFFER;
        if (GoalLines.KILL_SKILLS.contains(skill) || skill == Skill.MAGIC) {
            String monster = combatStyle.getLastTargetName();
            target = monster == null || monster.isEmpty() ? null : GoalTarget.NPC + monster.toLowerCase();
            needed = HITS_BEFORE_OFFER;
        } else if (skill == clickedSkill && client.getTickCount() - clickTick <= CLICK_MEMORY_TICKS) {
            target = skill == Skill.FISHING ? fishingTarget(drop) : clickedTarget;
            // Still working what you clicked: AFK methods go minutes between clicks
            clickTick = client.getTickCount();
        }
        if (target == null) {
            return;
        }
        int count = target.equals(streakTarget.get(skill)) ? streak.getOrDefault(skill, 0) + 1 : 1;
        streakTarget.put(skill, target);
        streak.put(skill, count);
        if (count >= needed) {
            maybeOffer(skill, target);
        }
    }

    // The way you're fishing the spot you clicked: the option whose catch gives this much XP (within a few %,
    // for the angler outfit)
    private String fishingTarget(int drop) {
        if (clickedSpot == null || clickedSpot.getName() == null) {
            return null;
        }
        SkillHelperData.FishingSpot spot = helpers.fishingSpot(clickedSpot.getId());
        if (spot == null) {
            return null;
        }
        for (Map.Entry<String, List<SkillHelperData.Catch>> option : spot.options.entrySet()) {
            for (SkillHelperData.Catch c : option.getValue()) {
                if (c.xp > 0 && Math.abs(drop - c.xp) <= Math.max(1, c.xp * 0.03)) {
                    return GoalTarget.fish(clickedSpot.getName(), option.getKey(), clickedSpot.getId());
                }
            }
        }
        return null;
    }

    private void maybeOffer(Skill skill, String target) {
        if (target.equals(plugin.getGoalTarget(skill)) || target.equals(offers.get(skill))
            || dismissed.contains(skill.name() + "|" + target)
            || (plugin.getGoalTarget(skill) == null && suggested(skill, target))) {
            return;
        }
        offers.put(skill, target);
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=00ffff>[" + identity.chatName + "]</col> "
            + skill.getName() + " goal: you're training on " + plugin.describeTarget(skill, target)
            + ". Track it instead from the " + skill.getName() + " card in the tracker panel.", null);
        plugin.updatePanelUI();
    }

    // Whether the suggestion already covers it (so there's nothing to offer)
    private boolean suggested(Skill skill, String target) {
        TrainingAdvisor.Suggestion suggestion = plugin.getTrainingAdvisor().suggest(skill, client.getRealSkillLevel(skill));
        TrainingData.Method method = suggestion != null ? suggestion.method : null;
        if (method == null) {
            return false;
        }
        String name = GoalTarget.parts(target)[0];
        if (target.startsWith(GoalTarget.FISH)) {
            List<SkillHelperData.Catch> catches = plugin.getTrainingAdvisor().fishCatches(target);
            if (catches.isEmpty()) {
                return false;
            }
            for (SkillHelperData.Catch c : catches) {
                String fish = TrainingAdvisor.catchNames(java.util.Collections.singletonList(c));
                if (method.targets.stream().noneMatch(t -> t.equalsIgnoreCase(fish))) {
                    return false;
                }
            }
            return true;
        }
        if (target.startsWith(GoalTarget.OBJECT)) {
            for (int id : method.objects) {
                SkillingData.Resource resource = skilling.object(id, null);
                if (resource != null && resource.name.equalsIgnoreCase(name)) {
                    return true;
                }
            }
            return false;
        }
        return method.npcs.stream().anyMatch(n -> n.equalsIgnoreCase(name))
            || method.name.replaceAll("s$", "").equalsIgnoreCase(name);
    }

    private static Skill skill(String name) {
        try {
            return Skill.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Forget what's been seen (logging out, a reset). */
    void clear() {
        lastXp.clear();
        streak.clear();
        streakTarget.clear();
        offers.clear();
        dismissed.clear();
        clickedSkill = null;
    }

    Map<Skill, String> offers() {
        return new HashMap<>(offers);
    }
}
