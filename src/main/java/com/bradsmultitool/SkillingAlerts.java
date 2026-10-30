package com.bradsmultitool;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * The Skill Tracker's skilling alerts: inventory full and your tree, rock or fishing spot gone (only while
 * you're working it), coin pouches full, and a batch of production finished.
 */
@Singleton
class SkillingAlerts {

    @Inject
    private Alerts alerts;

    @Inject
    private SkillingData skillingData;

    // Game variables (net.runelite.api.gameval)
    // Warn when about this much protection is left (about 36 seconds)
    // Spec changes in the first ticks after login are the energy loading in; real spends cost 10%+

    private static final int CONTAINER_INVENTORY = 93;
    private static final int INVENTORY_SIZE = 28;
    // Resource alerts only count if you clicked the resource within this many ticks (3 minutes)
    private static final int RESOURCE_MEMORY_TICKS = 300;
    // Skilling alerts only while you're working the resource: chopping/mining next to it, or fishing it,
    // within the last few ticks (the animation stops the moment the resource goes)
    private static final int WORKING_GRACE_TICKS = 3;
    private static final int WORKING_DISTANCE = 3;

    private final Client client;
    private final SkillTrackerConfig config;

    // Special attack: last energy seen (-1 = none since login), and whether some was spent since the last alert
    private boolean inventoryWasFull = false;

    // The tree/rock or fishing spot you last clicked
    private int resourceObjectId = -1;
    private WorldPoint resourceLocation = null;
    private boolean resourceIsTree = false;
    private NPC resourceNpc = null;
    private int resourceClickTick = -1000;
    private int lastWorkingTick = -1000;
    private boolean coinPouchesFull = false;
    private final Map<Skill, Integer> productionXp = new EnumMap<>(Skill.class);
    private int lastProductionXpTick = -1000;
    private Skill lastProductionSkill = null;
    // XP drops in a row in the same production skill, each a few ticks apart
    private int productionDrops = 0;
    // Clicks on items, objects or NPCs during the run (a Make-X batch needs just the one that started it)
    private int productionClicks = 0;
    private boolean batchRunning = false;
    private static final Set<Skill> PRODUCTION_SKILLS = EnumSet.of(Skill.COOKING, Skill.SMITHING, Skill.CRAFTING,
        Skill.FLETCHING, Skill.HERBLORE);
    // A batch is done once there's been no XP for this many ticks and you've stopped animating
    private static final int BATCH_DONE_TICKS = 5;
    // A batch is several XP drops in the same skill on separate ticks, so one-off XP (a quest reward,
    // a lamp, a single made item) doesn't count
    private static final int BATCH_MIN_DROPS = 3;
    // Most gap between drops of one batch (anvil smithing is 5 ticks an item)
    private static final int BATCH_GAP_TICKS = 8;

    @Inject
    SkillingAlerts(Client client, SkillTrackerConfig config) {
        this.client = client;
        this.config = config;
    }

    private void alert(VoiceAlertService.VoiceLine line, String detail) {
        alerts.alert(line, detail);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING) {
            resourceObjectId = -1;
            resourceNpc = null;
            productionXp.clear();
            productionDrops = 0;
            batchRunning = false;
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getContainerId() == CONTAINER_INVENTORY) {
            checkInventoryFull(event.getItemContainer());
            checkCoinPouches(event.getItemContainer());
        }
    }

    // Coin pouches stack to 28, or 56 / 84 / 140 with the Ardougne medium / hard / elite diary
    private void checkCoinPouches(ItemContainer inventory) {
        int pouches = 0;
        for (Item item : inventory.getItems()) {
            if (item.getId() > 0 && "Coin pouch".equals(client.getItemDefinition(item.getId()).getName())) {
                pouches += item.getQuantity();
            }
        }
        int cap = client.getVarbitValue(VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE) == 1 ? 140
            : client.getVarbitValue(VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE) == 1 ? 84
            : client.getVarbitValue(VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE) == 1 ? 56 : 28;
        boolean full = pouches >= cap;
        if (full && !coinPouchesFull && config.coinPouchAlert()) {
            alert(VoiceAlertService.VoiceLine.COIN_POUCH_FULL, pouches + " pouches");
        }
        coinPouchesFull = full;
    }

    // Batch complete: you were gaining XP in a production skill and both the XP and your character stopped
    @Subscribe
    public void onStatChanged(StatChanged event) {
        Skill skill = event.getSkill();
        Integer before = productionXp.put(skill, event.getXp());
        if (PRODUCTION_SKILLS.contains(skill) && before != null && event.getXp() > before && alerts.ready()) {
            int tick = client.getTickCount();
            if (tick == lastProductionXpTick && skill == lastProductionSkill) {
                return;
            }
            boolean continues = skill == lastProductionSkill && tick - lastProductionXpTick <= BATCH_GAP_TICKS;
            productionDrops = continues ? productionDrops + 1 : 1;
            if (!continues) {
                productionClicks = 0;
            }
            lastProductionSkill = skill;
            lastProductionXpTick = tick;
            batchRunning = productionDrops >= BATCH_MIN_DROPS;
        }
    }

    // Using an item, or clicking an object, NPC or inventory item (not walking or opening tabs)
    private static boolean isActionClick(MenuAction action) {
        switch (action) {
            case WIDGET_TARGET_ON_GAME_OBJECT:
            case WIDGET_TARGET_ON_NPC:
            case WIDGET_TARGET_ON_WIDGET:
            case GAME_OBJECT_FIRST_OPTION:
            case GAME_OBJECT_SECOND_OPTION:
            case GAME_OBJECT_THIRD_OPTION:
            case GAME_OBJECT_FOURTH_OPTION:
            case GAME_OBJECT_FIFTH_OPTION:
            case NPC_FIRST_OPTION:
            case NPC_SECOND_OPTION:
            case NPC_THIRD_OPTION:
            case NPC_FOURTH_OPTION:
            case NPC_FIFTH_OPTION:
                return true;
            default:
                return false;
        }
    }

    private void checkBatchComplete() {
        Player player = client.getLocalPlayer();
        if (!batchRunning || player == null || client.getTickCount() - lastProductionXpTick < BATCH_DONE_TICKS
            || player.getAnimation() != -1) {
            return;
        }
        batchRunning = false;
        // Clicking every item (1-ticking, or making them one at a time) isn't a batch the game ran for you
        boolean byHand = productionClicks >= productionDrops / 2;
        productionDrops = 0;
        productionClicks = 0;
        if (config.batchCompleteAlert() && !byHand) {
            alert(VoiceAlertService.VoiceLine.BATCH_COMPLETE, null);
        }
    }

    private void checkInventoryFull(ItemContainer inventory) {
        int used = 0;
        for (Item item : inventory.getItems()) {
            if (item.getId() > 0) {
                used++;
            }
        }
        boolean full = used >= INVENTORY_SIZE;
        if (full && !inventoryWasFull && isWorking() && config.inventoryFullAlert()) {
            alert(VoiceAlertService.VoiceLine.INVENTORY_FULL, null);
        }
        inventoryWasFull = full;
    }

    // Note each tick whether you're working the resource you last clicked
    @Subscribe
    public void onGameTick(GameTick event) {
        checkBatchComplete();
        Player player = client.getLocalPlayer();
        if (player == null || client.getTickCount() - resourceClickTick > RESOURCE_MEMORY_TICKS) {
            return;
        }
        boolean working;
        if (resourceNpc != null) {
            working = player.getInteracting() == resourceNpc;
        } else {
            int animation = player.getAnimation();
            working = resourceObjectId >= 0 && resourceLocation != null
                && (ResourceTimers.CHOP_ANIMATIONS.contains(animation) || ResourceTimers.MINE_ANIMATIONS.contains(animation))
                && player.getWorldLocation().distanceTo(resourceLocation) <= WORKING_DISTANCE;
        }
        if (working) {
            lastWorkingTick = client.getTickCount();
        }
    }

    private boolean isWorking() {
        return client.getTickCount() - lastWorkingTick <= WORKING_GRACE_TICKS;
    }

    // Remember which tree, rock or fishing spot was clicked so its disappearance can be announced
    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (productionDrops > 0 && isActionClick(event.getMenuAction())) {
            productionClicks++;
        }
        String option = Text.removeTags(event.getMenuOption()).toLowerCase();
        NPC npc = event.getMenuEntry().getNpc();
        if (npc != null) {
            if (SkillingData.isFishingOption(option)) {
                resourceNpc = npc;
                resourceObjectId = -1;
                resourceClickTick = client.getTickCount();
            }
            return;
        }
        boolean tree = option.startsWith("chop");
        boolean rock = option.equals("mine");
        if (tree || rock) {
            resourceObjectId = event.getId();
            resourceLocation = WorldPoint.fromScene(client.getTopLevelWorldView(), event.getParam0(), event.getParam1(),
                client.getTopLevelWorldView().getPlane());
            resourceIsTree = tree;
            resourceNpc = null;
            resourceClickTick = client.getTickCount();
        }
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        GameObject object = event.getGameObject();
        if (resourceObjectId < 0 || object.getId() != resourceObjectId
            || !object.getWorldLocation().equals(resourceLocation) || !isWorking()) {
            return;
        }
        resourceObjectId = -1;
        // Not for level 1 trees and rocks: they go after a log or an ore anyway (regular trees, copper, tin...)
        SkillingData.Resource resource = skillingData.object(object.getId(), client.getObjectDefinition(object.getId()).getName());
        if (resource != null && (resource.level <= 1 || resource.oneLog)) {
            return;
        }
        if (config.resourceGoneAlert()) {
            if (resourceIsTree) {
                alert(VoiceAlertService.VoiceLine.TREE_GONE, null);
            } else {
                alert(VoiceAlertService.VoiceLine.ROCK_DEPLETED, null);
            }
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        if (event.getNpc() != resourceNpc || !isWorking()) {
            return;
        }
        resourceNpc = null;
        if (config.resourceGoneAlert()) {
            alert(VoiceAlertService.VoiceLine.FISHING_SPOT_MOVED, null);
        }
    }
}
