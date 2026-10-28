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
 * goal in (see {@link TrainingData}).
 */
@Singleton
public class BradsMultiToolTrainingOverlay extends Overlay {

    private static final int OUTLINE_WIDTH = 2;
    private static final int OUTLINE_FEATHER = 4;

    private final Client client;
    private final BradsMultiToolPlugin plugin;
    private final BradsMultiToolConfig config;
    private final TrainingData training;
    private final ModelOutlineRenderer outlines;
    // Spawned objects any method trains on, by place (looked up against the current suggestions each frame)
    private final Map<WorldPoint, GameObject> objects = new HashMap<>();

    @Inject
    BradsMultiToolTrainingOverlay(Client client, BradsMultiToolPlugin plugin, BradsMultiToolConfig config,
                                  TrainingData training, ModelOutlineRenderer outlines) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.training = training;
        this.outlines = outlines;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        GameObject object = event.getGameObject();
        if (training.allObjects().contains(object.getId())) {
            objects.put(object.getWorldLocation(), object);
        }
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        objects.remove(event.getGameObject().getWorldLocation(), event.getGameObject());
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOADING || event.getGameState() == GameState.LOGIN_SCREEN) {
            objects.clear();
        }
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.highlightSuggestedTraining() || client.getGameState() != GameState.LOGGED_IN) {
            return null;
        }
        Set<Integer> ids = new HashSet<>();
        Set<String> npcNames = new HashSet<>();
        for (Skill skill : plugin.getGoalSkills()) {
            int xp = client.getSkillExperience(skill);
            if (plugin.getSkillGoal(skill) <= xp) {
                continue;
            }
            TrainingData.Method method = training.suggest(skill, client.getRealSkillLevel(skill), config.trainingAlternatives());
            if (method != null) {
                ids.addAll(method.objects);
                npcNames.addAll(method.npcs);
            }
        }
        if (ids.isEmpty() && npcNames.isEmpty()) {
            return null;
        }
        Color color = config.trainingHighlightColor();
        for (GameObject object : objects.values()) {
            if (ids.contains(object.getId())) {
                outlines.drawOutline(object, OUTLINE_WIDTH, color, OUTLINE_FEATHER);
            }
        }
        if (!npcNames.isEmpty()) {
            for (NPC npc : client.getTopLevelWorldView().npcs()) {
                if (npc.getName() != null && npcNames.contains(npc.getName().toLowerCase())) {
                    outlines.drawOutline(npc, OUTLINE_WIDTH, color, OUTLINE_FEATHER);
                }
            }
        }
        return null;
    }
}
