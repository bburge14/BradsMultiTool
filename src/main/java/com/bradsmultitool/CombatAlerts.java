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
 * PvM & Slayer's one-shot alerts: special attack, antifire, poison and venom, stamina, ammo, runes, cannon,
 * superior and Slayer task complete. Timer units follow RuneLite's Timers plugin.
 */
@Singleton
class CombatAlerts {

    @Inject
    private CombatStyle combatStyle;

    @Inject
    private Alerts alerts;

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

    private static final int CONTAINER_EQUIPMENT = 94;
    private static final int SLOT_WEAPON = 3;
    private static final int SLOT_AMMO = 13;
    // Resource alerts only count if you clicked the resource within this many ticks (3 minutes)
    // Skilling alerts only while you're working the resource: chopping/mining next to it, or fishing it,
    // within the last few ticks (the animation stops the moment the resource goes)

    private final Client client;
    private final PvmConfig config;
    private final PvmTracker plugin;

    // Special attack: last energy seen (-1 = none since login), and whether some was spent since the last alert
    private int lastSpecPercent = -1;
    private boolean specSpent = false;
    private int loginTick = 0;
    private boolean antifireAlerted = false;
    private boolean antipoisonAlerted = false;
    private boolean ammoAlerted = false;
    private boolean cannonAlerted = false;
    private int lastRunesAlertTick = -100;

    // The tree/rock or fishing spot you last clicked
    // XP drops in a row in the same production skill, each a few ticks apart
    // Clicks on items, objects or NPCs during the run (a Make-X batch needs just the one that started it)
    // A batch is done once there's been no XP for this many ticks and you've stopped animating
    // A batch is several XP drops in the same skill on separate ticks, so one-off XP (a quest reward,
    // a lamp, a single made item) doesn't count
    // Most gap between drops of one batch (anvil smithing is 5 ticks an item)

    @Inject
    CombatAlerts(Client client, PvmConfig config, PvmTracker plugin) {
        this.client = client;
        this.config = config;
        this.plugin = plugin;
    }

    private void alert(VoiceAlertService.VoiceLine line, String detail) {
        alerts.alert(line, detail);
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
                    alert(VoiceAlertService.VoiceLine.TASK_COMPLETE, plugin.getSlayerTracker().getTaskName());
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
                    alert(VoiceAlertService.VoiceLine.STAMINA_EXPIRED, null);
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
                alert(VoiceAlertService.VoiceLine.SPEC_READY, percent + "%");
            }
        }
    }

    private void checkAntifire(int ticksLeft) {
        if (ticksLeft > WARN_TICKS) {
            antifireAlerted = false;
        } else if (ticksLeft > 0 && !antifireAlerted) {
            antifireAlerted = true;
            if (config.antifireAlert()) {
                alert(VoiceAlertService.VoiceLine.ANTIFIRE_LOW, null);
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
            alert(VoiceAlertService.VoiceLine.VENOMED, null);
        } else if (value > 0 && value < VENOM_THRESHOLD && previous <= 0) {
            alert(VoiceAlertService.VoiceLine.POISONED, null);
        }

        int protectionTicks = value < 0 ? -value * 30 : 0;
        if (protectionTicks > WARN_TICKS) {
            antipoisonAlerted = false;
        } else if (protectionTicks > 0 && !antipoisonAlerted && previous < value) {
            antipoisonAlerted = true;
            alert(VoiceAlertService.VoiceLine.ANTIPOISON_LOW, null);
        }
    }

    private void checkCannon(int cannonballs) {
        if (cannonballs > config.cannonThreshold()) {
            cannonAlerted = false;
        } else if (cannonballs > 0 && !cannonAlerted) {
            cannonAlerted = true;
            if (config.cannonAlert()) {
                alert(VoiceAlertService.VoiceLine.CANNON_LOW, cannonballs + " cannonballs left");
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
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) {
            return;
        }
        String message = Text.removeTags(event.getMessage());
        if (message.contains("A superior foe has appeared") && config.superiorAlert()) {
            alert(VoiceAlertService.VoiceLine.SUPERIOR_SPAWNED, null);
        } else if (message.contains("do not have enough") && message.contains("runes") && config.runesAlert()
            && client.getTickCount() - lastRunesAlertTick > 16) {
            lastRunesAlertTick = client.getTickCount();
            alert(VoiceAlertService.VoiceLine.OUT_OF_RUNES, null);
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getContainerId() == CONTAINER_EQUIPMENT) {
            checkAmmo(event.getItemContainer());
        }
    }

    // Thrown weapons stack in the weapon slot; arrows, bolts and javelins live in the ammo slot
    private void checkAmmo(ItemContainer equipment) {
        WeaponStyles.Style style = combatStyle.getCurrentStyle();
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
                alert(VoiceAlertService.VoiceLine.AMMO_LOW, count + " left");
            }
        }
    }

}
