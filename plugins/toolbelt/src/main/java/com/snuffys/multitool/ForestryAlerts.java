package com.snuffys.multitool;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

/**
 * Woodcutting alerts: a Forestry event starting near you (named in chat), and a bird's nest falling (a clue nest
 * says so). Only while you're training Woodcutting. Events are told apart by the objects and NPCs they bring
 * (the same ids RuneLite's Woodcutting plugin uses, BSD-2-Clause). Only reads the scene and chat; RuneLite's
 * Woodcutting plugin outlines what each event wants you to click.
 */
@Singleton
class ForestryAlerts {

    // One alert per event this often at most (ticks): an event brings several objects or NPCs at once
    private static final int REPEAT_TICKS = 100;
    // Only events this close to you (tiles): not another group's across the scene
    private static final int NEAR = 15;

    private final Client client;
    private final SkillTracker plugin;
    private final Alerts alerts;
    private final ItemManager itemManager;
    private final Map<String, Integer> lastSaid = new HashMap<>();
    // A clue nest landed this tick (the nest message follows)
    private int clueNestTick = -1;

    @Inject
    ForestryAlerts(Client client, SkillTracker plugin, Alerts alerts, ItemManager itemManager) {
        this.client = client;
        this.plugin = plugin;
        this.alerts = alerts;
        this.itemManager = itemManager;
    }

    /** The Forestry event this object starts, or null. */
    static String eventOfObject(int id) {
        switch (id) {
            case ObjectID.GATHERING_EVENT_RISING_ROOTS:
            case ObjectID.GATHERING_EVENT_RISING_ROOTS_SPECIAL:
                return "Rising Roots";
            case ObjectID.GATHERING_EVENT_SAPLING_1X1:
            case ObjectID.GATHERING_EVENT_SAPLING_WITHERING_1X1:
            case ObjectID.GATHERING_EVENT_SAPLING_2X2:
            case ObjectID.GATHERING_EVENT_SAPLING_WITHERING_2X2:
            case ObjectID.GATHERING_EVENT_SAPLING_3X3:
            case ObjectID.GATHERING_EVENT_SAPLING_WITHERING_3X3:
                return "Struggling Sapling";
            default:
                return null;
        }
    }

    /** The Forestry event this NPC starts, or null. */
    static String eventOfNpc(int id) {
        switch (id) {
            case NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL01:
            case NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL02:
            case NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL03:
            case NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL04:
            case NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL05:
            case NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL06:
            case NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL07:
            case NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL08:
                return "Flowering Tree";
            case NpcID.GATHERING_EVENT_WOODCUTTING_LEPRECHAUN:
                return "Leprechaun";
            case NpcID.GATHERING_EVENT_POACHERS_FOX_OUTDOORS:
            case NpcID.GATHERING_EVENT_POACHERS_FOX_INDOORS:
                return "Poachers";
            case NpcID.GATHERING_EVENT_PHEASANT_FORESTER:
                return "Pheasant Control";
            case NpcID.GATHERING_EVENT_BEES_BEEBOX_4:
                return "Bee Hive";
            case NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_DRYAD:
                return "Enchantment Ritual";
            case NpcID.GATHERING_EVENT_ENTLINGS_NPC_01:
                return "Friendly Ent";
            default:
                return null;
        }
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        started(eventOfObject(event.getGameObject().getId()), event.getGameObject().getWorldLocation());
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        started(eventOfNpc(event.getNpc().getId()), event.getNpc().getWorldLocation());
    }

    // An event near you that hasn't been seen for a while: the event keeps spawning objects and NPCs while it runs
    // (new roots, entlings, foxes), and coming back into view or a scene reload spawns them again, so each spawn
    // only refreshes when it was last seen
    private void started(String event, net.runelite.api.coords.WorldPoint at) {
        net.runelite.api.Player you = client.getLocalPlayer();
        if (event == null || you == null || at == null || at.distanceTo2D(you.getWorldLocation()) > NEAR
            || !plugin.trainedRecently(Skill.WOODCUTTING)) {
            return;
        }
        int now = client.getTickCount();
        Integer last = lastSaid.put(event, now);
        if (last != null && now - last < REPEAT_TICKS) {
            return;
        }
        alerts.alert(VoiceAlertService.VoiceLine.FORESTRY_EVENT, event);
    }

    @Subscribe
    public void onItemSpawned(ItemSpawned event) {
        String name = itemManager.getItemComposition(event.getItem().getId()).getName();
        if (name != null && name.toLowerCase().startsWith("clue nest")) {
            clueNestTick = client.getTickCount();
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) {
            return;
        }
        if (Text.removeTags(event.getMessage()).contains("A bird's nest falls out of the tree")) {
            alerts.alert(VoiceAlertService.VoiceLine.BIRD_NEST,
                client.getTickCount() - clueNestTick <= 1 ? "a clue nest" : null);
        }
    }
}
