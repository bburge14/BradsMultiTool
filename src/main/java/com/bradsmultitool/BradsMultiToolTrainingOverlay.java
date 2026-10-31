package com.bradsmultitool;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/**
 * Outlines the trees, rocks, stalls or NPCs of the suggested training method for each skill you have a
 * goal in (see {@link TrainingData}), or what you picked instead with "Track for goal" ({@link GoalTargetMenu}).
 */
@Singleton
public class BradsMultiToolTrainingOverlay extends Overlay {

    private static final int OUTLINE_WIDTH = 2;
    private static final int OUTLINE_FEATHER = 4;

    private final Client client;
    private final SkillTracker plugin;
    private final SkillTrackerConfig config;
    private final TrainingData training;
    private final ModelOutlineRenderer outlines;
    private final SkillingData skilling;
    private final TrainingAdvisor advisor;
    // Spawned objects any method trains on (or any skilling resource you could pick instead), by place (looked up against the current suggestions each frame)
    private final Map<WorldPoint, GameObject> objects = new HashMap<>();
    // Each tracked object's resource ("Tree", "Teak tree"): many objects share a resource under other ids
    private final Map<GameObject, String> resourceOf = new HashMap<>();

    private final SkillHelperData helpers;

    @Inject
    BradsMultiToolTrainingOverlay(Client client, SkillTracker plugin, SkillTrackerConfig config,
                                  TrainingData training, ModelOutlineRenderer outlines, SkillingData skilling,
                                  TrainingAdvisor advisor, SkillHelperData helpers) {
        this.helpers = helpers;
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.training = training;
        this.outlines = outlines;
        this.skilling = skilling;
        this.advisor = advisor;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        GameObject object = event.getGameObject();
        // By id, else by the object's name, like the timer pies (regular trees have many ids)
        SkillingData.Resource resource = skilling.object(object.getId(), objectName(object.getId()));
        if (training.allObjects().contains(object.getId()) || resource != null) {
            GameObject previous = objects.put(object.getWorldLocation(), object);
            if (previous != null) {
                resourceOf.remove(previous);
            }
            if (resource != null) {
                resourceOf.put(object, resource.name);
            }
        }
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        objects.remove(event.getGameObject().getWorldLocation(), event.getGameObject());
        resourceOf.remove(event.getGameObject());
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOADING || event.getGameState() == GameState.LOGIN_SCREEN) {
            objects.clear();
            resourceOf.clear();
        }
    }

    private String objectName(int id) {
        ObjectComposition composition = client.getObjectDefinition(id);
        if (composition != null && composition.getImpostorIds() != null && composition.getImpostor() != null) {
            composition = composition.getImpostor();
        }
        return composition != null ? composition.getName() : null;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.highlightSuggestedTraining() || client.getGameState() != GameState.LOGGED_IN) {
            return null;
        }
        Set<Integer> ids = new HashSet<>();
        Set<String> npcNames = new HashSet<>();
        Set<String> resources = new HashSet<>();
        // Fishing spots picked with the way you fish them ("lure"): only spots that offer it
        java.util.Map<String, Set<String>> fishOptions = new java.util.HashMap<>();
        for (Skill skill : plugin.getGoalSkills()) {
            int xp = client.getSkillExperience(skill);
            if (plugin.getSkillGoal(skill) <= xp) {
                continue;
            }
            // What you picked with "Track for goal" wins over the suggestion
            String target = plugin.getGoalTarget(skill);
            if (target != null) {
                if (target.startsWith(GoalTarget.FISH)) {
                    String[] parts = GoalTarget.parts(target);
                    npcNames.add(parts[0]);
                    fishOptions.computeIfAbsent(parts[0], k -> new HashSet<>()).add(parts.length > 1 ? parts[1] : "");
                } else if (GoalTarget.npcName(target) != null) {
                    npcNames.add(GoalTarget.npcName(target));
                } else if (target.startsWith("object:")) {
                    resources.add(target.substring(7));
                }
                continue;
            }
            TrainingAdvisor.Suggestion suggestion = advisor.suggest(skill, client.getRealSkillLevel(skill));
            TrainingData.Method method = suggestion != null ? suggestion.method : null;
            if (method != null) {
                ids.addAll(method.objects);
                // and every other object of the same resource ("Regular trees" -> every Tree)
                for (int id : method.objects) {
                    SkillingData.Resource resource = skilling.object(id, null);
                    if (resource != null) {
                        resources.add(resource.name);
                    }
                }
                npcNames.addAll(method.npcs);
            }
        }
        if (ids.isEmpty() && npcNames.isEmpty() && resources.isEmpty()) {
            return null;
        }
        Color color = config.trainingHighlightColor();
        for (GameObject object : objects.values()) {
            String resource = resourceOf.get(object);
            if (ids.contains(object.getId()) || (resource != null && resources.contains(resource))) {
                outlines.drawOutline(object, OUTLINE_WIDTH, color, OUTLINE_FEATHER);
            }
        }
        if (!npcNames.isEmpty()) {
            for (NPC npc : client.getTopLevelWorldView().npcs()) {
                String name = npc.getName() != null ? npc.getName().toLowerCase() : null;
                if (name != null && npcNames.contains(name) && offers(npc, fishOptions.get(name))) {
                    outlines.drawOutline(npc, OUTLINE_WIDTH, color, OUTLINE_FEATHER);
                }
            }
        }
        return null;
    }

    // Whether a fishing spot offers one of the ways you picked to fish it ("fly fishing"; always true when it
    // isn't a fishing pick)
    private boolean offers(NPC npc, Set<String> options) {
        if (options == null) {
            return true;
        }
        SkillHelperData.FishingSpot spot = helpers.fishingSpot(npc.getId());
        if (spot == null) {
            return false;
        }
        for (String option : options) {
            if (!TrainingAdvisor.catchesFor(spot, option).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
