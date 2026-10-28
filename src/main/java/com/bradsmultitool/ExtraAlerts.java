package com.bradsmultitool;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Smaller one-shot alerts: special attack, antifire, poison, stamina, ammo, runes, cannon, superior,
 * slayer task complete, inventory full and resource depletion. Timer units follow RuneLite's Timers plugin.
 */
@Singleton
class ExtraAlerts {

    // Game variables (net.runelite.api.gameval)
    private static final int VARP_SPEC_ENERGY = 300;       // VarPlayerID.SA_ENERGY, 0-1000
    private static final int VARP_POISON = 102;            // VarPlayerID.POISON
    private static final int VARP_CANNONBALLS = 3;         // cannonballs loaded
    private static final int VARP_SLAYER_COUNT = 394;      // VarPlayerID.SLAYER_COUNT
    private static final int VARBIT_ANTIFIRE = 3981;       // counts down in 30-tick steps
    private static final int VARBIT_SUPER_ANTIFIRE = 6101; // counts down in 20-tick steps
    private static final int VARBIT_STAMINA_ACTIVE = 25;
    private static final int VENOM_THRESHOLD = 1_000_000;
    // Warn when about this much protection is left (about 36 seconds)
    private static final int WARN_TICKS = 60;
    // Spec changes in the first ticks after login are the energy loading in; real spends cost 10%+
    private static final int SETTLE_TICKS = 5;
    private static final int MIN_SPEND_PERCENT = 10;

    private static final int CONTAINER_INVENTORY = 93;
    private static final int CONTAINER_EQUIPMENT = 94;
    private static final int SLOT_WEAPON = 3;
    private static final int SLOT_AMMO = 13;
    private static final int INVENTORY_SIZE = 28;
    // Resource alerts only count if you clicked the resource within this many ticks (3 minutes)
    private static final int RESOURCE_MEMORY_TICKS = 300;

    private final Client client;
    private final BradsMultiToolConfig config;
    private final BradsMultiToolPlugin plugin;

    // Special attack: last energy seen (-1 = none since login), and whether some was spent since the last alert
    private int lastSpecPercent = -1;
    private boolean specSpent = false;
    private int loginTick = 0;
    private boolean antifireAlerted = false;
    private boolean antipoisonAlerted = false;
    private boolean ammoAlerted = false;
    private boolean cannonAlerted = false;
    private boolean inventoryWasFull = false;
    private int lastRunesAlertTick = -100;

    // The tree/rock or fishing spot you last clicked
    private int resourceObjectId = -1;
    private String resourceName = null;
    private WorldPoint resourceLocation = null;
    private boolean resourceIsTree = false;
    private NPC resourceNpc = null;
    private int resourceClickTick = -1000;

    @Inject
    ExtraAlerts(Client client, BradsMultiToolConfig config, BradsMultiToolPlugin plugin) {
        this.client = client;
        this.config = config;
        this.plugin = plugin;
    }

    private void alert(VoiceAlertService.VoiceLine line, String chat) {
        plugin.speak(line);
        plugin.sendGameChatMessage(chat);
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        int value = event.getValue();
        switch (event.getVarpId()) {
            case VARP_SPEC_ENERGY:
                checkSpec(value / 10);
                return;
            case VARP_POISON:
                checkPoison(value);
                return;
            case VARP_CANNONBALLS:
                checkCannon(value);
                return;
            case VARP_SLAYER_COUNT:
                if (value == 0 && config.taskCompleteAlert() && plugin.getSlayerTracker().hadTask()) {
                    alert(VoiceAlertService.VoiceLine.TASK_COMPLETE, "Slayer task complete!");
                }
                return;
            default:
                break;
        }

        switch (event.getVarbitId()) {
            case VARBIT_ANTIFIRE:
                checkAntifire(value * 30);
                break;
            case VARBIT_SUPER_ANTIFIRE:
                checkAntifire(value * 20);
                break;
            case VARBIT_STAMINA_ACTIVE:
                if (value == 0 && config.staminaAlert()) {
                    alert(VoiceAlertService.VoiceLine.STAMINA_EXPIRED, "Your stamina potion has worn off.");
                }
                break;
            default:
                break;
        }
    }

    /**
     * Announces special attack only after you've spent some and it has recharged to the threshold.
     * Energy loading in at login or in the lobby never counts: only a real drop (a spend) during play
     * arms the alert.
     */
    private void checkSpec(int percent) {
        boolean inGame = client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null
            && client.getTickCount() - loginTick > SETTLE_TICKS;
        int previous = lastSpecPercent;
        lastSpecPercent = percent;
        if (!inGame || previous < 0) {
            return;
        }
        if (previous - percent >= MIN_SPEND_PERCENT) {
            specSpent = true;
        }
        if (specSpent && percent >= config.specThreshold()) {
            specSpent = false;
            if (config.specReadyAlert()) {
                alert(VoiceAlertService.VoiceLine.SPEC_READY, "Special attack ready (" + percent + "%).");
            }
        }
    }

    private void checkAntifire(int ticksLeft) {
        if (ticksLeft > WARN_TICKS) {
            antifireAlerted = false;
        } else if (ticksLeft > 0 && !antifireAlerted) {
            antifireAlerted = true;
            if (config.antifireAlert()) {
                alert(VoiceAlertService.VoiceLine.ANTIFIRE_LOW, "Your antifire protection is running out.");
            }
        }
    }

    private int lastPoisonValue = 0;

    // Positive = poisoned (venom from 1,000,000 up); negative = protection, 30 ticks per step
    private void checkPoison(int value) {
        int previous = lastPoisonValue;
        lastPoisonValue = value;
        if (!config.poisonAlert()) {
            return;
        }
        if (value >= VENOM_THRESHOLD && previous < VENOM_THRESHOLD) {
            alert(VoiceAlertService.VoiceLine.VENOMED, "You have been venomed!");
        } else if (value > 0 && value < VENOM_THRESHOLD && previous <= 0) {
            alert(VoiceAlertService.VoiceLine.POISONED, "You have been poisoned!");
        }

        int protectionTicks = value < 0 ? -value * 30 : 0;
        if (protectionTicks > WARN_TICKS) {
            antipoisonAlerted = false;
        } else if (protectionTicks > 0 && !antipoisonAlerted && previous < value) {
            antipoisonAlerted = true;
            alert(VoiceAlertService.VoiceLine.ANTIPOISON_LOW, "Your poison protection is running out.");
        }
    }

    private void checkCannon(int cannonballs) {
        if (cannonballs > config.cannonThreshold()) {
            cannonAlerted = false;
        } else if (cannonballs > 0 && !cannonAlerted) {
            cannonAlerted = true;
            if (config.cannonAlert()) {
                alert(VoiceAlertService.VoiceLine.CANNON_LOW, "Your cannon has " + cannonballs + " cannonballs left.");
            }
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGGED_IN) {
            loginTick = client.getTickCount();
        }
        if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING) {
            lastSpecPercent = -1;
            specSpent = false;
            lastPoisonValue = 0;
            resourceObjectId = -1;
            resourceNpc = null;
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) {
            return;
        }
        String message = Text.removeTags(event.getMessage());
        if (message.contains("A superior foe has appeared") && config.superiorAlert()) {
            alert(VoiceAlertService.VoiceLine.SUPERIOR_SPAWNED, "A superior foe has appeared!");
        } else if (message.contains("do not have enough") && message.contains("runes") && config.runesAlert()
            && client.getTickCount() - lastRunesAlertTick > 16) {
            lastRunesAlertTick = client.getTickCount();
            alert(VoiceAlertService.VoiceLine.OUT_OF_RUNES, "You're out of runes for that spell.");
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getContainerId() == CONTAINER_EQUIPMENT) {
            checkAmmo(event.getItemContainer());
        } else if (event.getContainerId() == CONTAINER_INVENTORY) {
            checkInventoryFull(event.getItemContainer());
        }
    }

    // Thrown weapons stack in the weapon slot; arrows, bolts and javelins live in the ammo slot
    private void checkAmmo(ItemContainer equipment) {
        WeaponStyles.Style style = plugin.getCurrentStyle();
        if (style == null || style.type() != MonsterWeakness.Type.RANGED) {
            return;
        }
        Item weapon = equipment.getItem(SLOT_WEAPON);
        Item ammo = equipment.getItem(SLOT_AMMO);
        int count;
        if (weapon != null && weapon.getQuantity() > 1) {
            count = weapon.getQuantity();
        } else if (ammo != null && ammo.getId() > 0) {
            count = ammo.getQuantity();
        } else {
            return;
        }
        if (count > config.ammoThreshold()) {
            ammoAlerted = false;
        } else if (!ammoAlerted) {
            ammoAlerted = true;
            if (config.ammoAlert()) {
                alert(VoiceAlertService.VoiceLine.AMMO_LOW, "Ammo running low (" + count + " left).");
            }
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
        boolean skilling = client.getTickCount() - resourceClickTick <= RESOURCE_MEMORY_TICKS;
        if (full && !inventoryWasFull && skilling && config.inventoryFullAlert()) {
            alert(VoiceAlertService.VoiceLine.INVENTORY_FULL, "Your inventory is full.");
        }
        inventoryWasFull = full;
    }

    // Remember which tree, rock or fishing spot was clicked so its disappearance can be announced
    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
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
            resourceName = Text.removeTags(event.getMenuTarget());
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
            || !object.getWorldLocation().equals(resourceLocation)
            || client.getTickCount() - resourceClickTick > RESOURCE_MEMORY_TICKS) {
            return;
        }
        resourceObjectId = -1;
        if (config.resourceGoneAlert()) {
            if (resourceIsTree) {
                alert(VoiceAlertService.VoiceLine.TREE_GONE, "Your tree has fallen.");
            } else {
                alert(VoiceAlertService.VoiceLine.ROCK_DEPLETED, "Your rock is depleted.");
            }
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        if (event.getNpc() != resourceNpc || client.getTickCount() - resourceClickTick > RESOURCE_MEMORY_TICKS) {
            return;
        }
        resourceNpc = null;
        if (config.resourceGoneAlert()) {
            alert(VoiceAlertService.VoiceLine.FISHING_SPOT_MOVED, "Your fishing spot has moved.");
        }
    }

    /** Object id and location of the tree last clicked, for the tree timer; null when not chopping a tree. */
    WorldPoint lastTreeLocation() {
        return resourceIsTree && resourceObjectId >= 0 ? resourceLocation : null;
    }

    int lastResourceObjectId() {
        return resourceObjectId;
    }

    String lastResourceName() {
        return resourceName;
    }
}
