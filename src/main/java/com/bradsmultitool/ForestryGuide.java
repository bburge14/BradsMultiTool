package com.bradsmultitool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;

/**
 * What each Forestry event wants you to click, kept as the scene changes, for {@link ForestryOverlay}: the glowing
 * roots, the sapling's ingredients (and their order once the sapling says), the two flowering bushes being pollinated,
 * the fox trap, the egg nests and the Freaky Forester, the unfinished beehive, the right ritual circle and the end of
 * the rainbow. Worked out as RuneLite's Woodcutting plugin does (BSD-2-Clause, the RuneLite contributors), so it shows
 * without that plugin on; while it's on with an event's highlight, that event is left to it. Only reads the scene and
 * chat.
 */
@Singleton
class ForestryGuide {

    final List<GameObject> roots = new ArrayList<>();
    final List<GameObject> ingredients = new ArrayList<>();
    /** The sapling's mulch in order once it's said which (first, second, third), else nulls. */
    final GameObject[] order = new GameObject[3];
    final List<NPC> flowers = new ArrayList<>();
    final List<NPC> activeFlowers = new ArrayList<>(2);
    private NPC lastFlower;
    NPC foxTrap;
    final List<GameObject> nests = new ArrayList<>();
    NPC forester;
    NPC unfinishedHive;
    final List<NPC> circles = new ArrayList<>(5);
    final List<GameObject> rainbows = new ArrayList<>();

    private final Client client;
    private final ConfigManager configManager;
    private final PluginManager pluginManager;
    private Plugin theirs;
    private long theirsCheckedAt;

    @Inject
    ForestryGuide(Client client, ConfigManager configManager, PluginManager pluginManager) {
        this.client = client;
        this.configManager = configManager;
        this.pluginManager = pluginManager;
    }

    /**
     * Whether RuneLite's Woodcutting plugin is on and highlights this ("highlightGlowingRoots"...): then it's left to
     * it, so they don't draw twice.
     */
    boolean theyHighlight(String key) {
        long now = System.currentTimeMillis();
        if (now - theirsCheckedAt > 3000) {
            theirsCheckedAt = now;
            theirs = null;
            for (Plugin plugin : pluginManager.getPlugins()) {
                if ("WoodcuttingPlugin".equals(plugin.getClass().getSimpleName()) && pluginManager.isPluginEnabled(plugin)) {
                    theirs = plugin;
                }
            }
        }
        return theirs != null && !"false".equals(configManager.getConfiguration("woodcutting", key));
    }

    /** The ritual circle to stand on: the one whose colour and shape both appear an odd number of times. */
    NPC solveCircles() {
        if (circles.size() != 5) {
            return null;
        }
        int[] ids = new int[5];
        for (int i = 0; i < 5; i++) {
            ids[i] = circles.get(i).getId();
        }
        int at = solve(ids);
        return at < 0 ? null : circles.get(at);
    }

    /** Which of five circles (their NPC ids) is the one to stand on, or -1. */
    static int solve(int[] ids) {
        int all = 0;
        for (int id : ids) {
            all ^= circleBits(id);
        }
        for (int i = 0; i < ids.length; i++) {
            int bits = circleBits(ids[i]);
            if ((bits & all) == bits) {
                return i;
            }
        }
        return -1;
    }

    // A circle's shape (A-D) and colour (1-4) as bits
    static int circleBits(int id) {
        int off = id - NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_1;
        return (16 << (off / 4)) | (1 << (off % 4));
    }

    static boolean isFlower(int id) {
        return id == NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL01 || id == NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL02
            || id == NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL03 || id == NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL04
            || id == NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL05 || id == NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL06
            || id == NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL07 || id == NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL08;
    }

    static boolean isIngredient(int id) {
        return id == ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_1 || id == ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_2
            || id == ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_3 || id == ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4A
            || id == ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4B || id == ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4C
            || id == ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_5;
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        GameObject o = event.getGameObject();
        int id = o.getId();
        if (id == ObjectID.GATHERING_EVENT_RISING_ROOTS || id == ObjectID.GATHERING_EVENT_RISING_ROOTS_SPECIAL) {
            roots.add(o);
        } else if (isIngredient(id)) {
            ingredients.add(o);
        } else if (id == ObjectID.GATHERING_EVENT_PHEASANT_NEST01 || id == ObjectID.GATHERING_EVENT_PHEASANT_NEST02) {
            nests.add(o);
        } else if (id == ObjectID.GATHERING_EVENT_WOODCUTTING_LEPRECHAUN_RAINBOW) {
            rainbows.add(o);
        }
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        GameObject o = event.getGameObject();
        roots.remove(o);
        nests.remove(o);
        rainbows.remove(o);
        if (ingredients.remove(o) && ingredients.isEmpty()) {
            Arrays.fill(order, null);
        }
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        NPC npc = event.getNpc();
        int id = npc.getId();
        if (isFlower(id)) {
            flowers.add(npc);
        } else if (id == NpcID.GATHERING_EVENT_POACHERS_TRAP) {
            foxTrap = npc;
        } else if (id == NpcID.GATHERING_EVENT_PHEASANT_FORESTER) {
            forester = npc;
        } else if (id == NpcID.GATHERING_EVENT_BEES_BEEBOX_1 || id == NpcID.GATHERING_EVENT_BEES_BEEBOX_2) {
            unfinishedHive = npc;
        } else if (id >= NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_1 && id <= NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_D_4) {
            circles.add(npc);
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        NPC npc = event.getNpc();
        if (flowers.remove(npc)) {
            if (activeFlowers.remove(npc)) {
                activeFlowers.clear();
            }
            if (npc == lastFlower) {
                lastFlower = null;
            }
        }
        if (foxTrap == npc) {
            foxTrap = null;
        }
        if (forester == npc) {
            forester = null;
        }
        if (unfinishedHive == npc) {
            unfinishedHive = null;
        }
        circles.remove(npc);
    }

    // The flowering bushes: the two you're pollinating between (picked pollen from one, take it to its pair)
    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        // (anyone's: the pair others pollinate is the pair)
        if (event.getActor().getAnimation() != AnimationID.HUMAN_PICKUPTABLE
            || !(event.getActor().getInteracting() instanceof NPC) || !flowers.contains(event.getActor().getInteracting())) {
            return;
        }
        NPC flower = (NPC) event.getActor().getInteracting();
        if (!activeFlowers.contains(flower)) {
            if (activeFlowers.size() == 2) {
                activeFlowers.clear();
            }
            activeFlowers.add(flower);
        }
    }

    @Subscribe
    public void onInteractingChanged(InteractingChanged event) {
        if (event.getSource() == client.getLocalPlayer() && event.getTarget() instanceof NPC
            && isFlower(((NPC) event.getTarget()).getId())) {
            lastFlower = (NPC) event.getTarget();
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.SPAM && event.getType() != ChatMessageType.GAMEMESSAGE
            && event.getType() != ChatMessageType.MESBOX) {
            return;
        }
        String msg = event.getMessage();
        // "The sapling seems to love the rotting leaves as its first ingredient": its order, as it says it
        if (msg.startsWith("The sapling seems to love")) {
            int at = msg.contains("first") ? 0 : msg.contains("second") ? 1 : msg.contains("third") ? 2 : -1;
            if (at < 0) {
                return;
            }
            for (GameObject ingredient : ingredients) {
                String name = client.getObjectDefinition(ingredient.getId()).getName().toLowerCase();
                if (msg.contains(name)) {
                    order[at] = ingredient;
                    return;
                }
            }
        }
        if (msg.equals("There are no open, unpollinated flowers on this bush yet.")
            || msg.equals("The flowers on this bush have not yet opened enough to harvest pollen.")
            || msg.contains("The bush is already fruiting and won't benefit from any more pollen.")) {
            if (activeFlowers.contains(lastFlower)) {
                activeFlowers.clear();
            }
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        switch (event.getGameState()) {
            case LOGIN_SCREEN:
            case HOPPING:
                flowers.clear();
                activeFlowers.clear();
                foxTrap = null;
                forester = null;
                unfinishedHive = null;
                circles.clear();
                // fall through: the scene's objects go on a reload too
            case LOADING:
                roots.clear();
                ingredients.clear();
                Arrays.fill(order, null);
                nests.clear();
                rainbows.clear();
                break;
            default:
                break;
        }
    }
}
