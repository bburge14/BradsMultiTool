package com.bradsmultitool;

import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Tempoross: alerts for a wave coming (tether to a mast or totem), lightning about to start a fire near you, the
 * double fishing spot appearing and Tempoross turning vulnerable; and what the card shows (its energy and essence,
 * the storm, your points, the fish you carry and the damage they'd do). Only while the game's Tempoross box shows.
 * The messages, ids and damage per fish come from the Tempoross Plugin Hub plugin (BSD-2-Clause, CasvM and
 * contributors); the bars are read from the game's own box. Only reads; never acts for you.
 */
@Singleton
class TemporossHelper {

    // A fire this close to you (tiles) is "near you"
    private static final int FIRE_NEAR = 6;
    // One fire alert this often at most (ticks): lightning strikes several spots at once (the hub plugin's 20s)
    private static final int FIRE_REPEAT_TICKS = 33;
    private static final Pattern PERCENT = Pattern.compile("(\\d+)%");
    private static final Pattern NUMBER = Pattern.compile("(\\d+)");
    static final int DAMAGE_RAW = 10;
    static final int DAMAGE_COOKED = 15;
    static final int DAMAGE_CRYSTAL = 10;

    private final Client client;
    private final Alerts alerts;
    private boolean inGame;
    private int lastFireTick = -100;

    @Inject
    TemporossHelper(Client client, Alerts alerts) {
        this.client = client;
        this.alerts = alerts;
    }

    boolean inGame() {
        return inGame;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        Widget hud = client.getWidget(InterfaceID.TemporossHud.ENERGY_TITLE);
        inGame = hud != null && !hud.isHidden();
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (!inGame || event.getType() != ChatMessageType.GAMEMESSAGE) {
            return;
        }
        String message = Text.removeTags(event.getMessage()).toLowerCase();
        if (message.contains("a colossal wave closes in")) {
            alerts.alert(VoiceAlertService.VoiceLine.TEMPOROSS_WAVE, null);
        } else if (message.contains("tempoross is vulnerable")) {
            alerts.alert(VoiceAlertService.VoiceLine.TEMPOROSS_VULNERABLE, null);
        }
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        // The lightning's shadow, where a fire is about to start
        if (!inGame || event.getGameObject().getId() != ObjectID.TEMPOROSS_LIGHTNING_SHADOW) {
            return;
        }
        Player you = client.getLocalPlayer();
        if (you == null || event.getGameObject().getWorldLocation().distanceTo(you.getWorldLocation()) > FIRE_NEAR) {
            return;
        }
        int now = client.getTickCount();
        if (now - lastFireTick >= FIRE_REPEAT_TICKS) {
            lastFireTick = now;
            alerts.alert(VoiceAlertService.VoiceLine.TEMPOROSS_FIRE, null);
        }
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        if (inGame && event.getNpc().getId() == NpcID.TEMPOROSS_HARPOONFISH_FISHINGSPOT_SPECIAL) {
            alerts.alert(VoiceAlertService.VoiceLine.TEMPOROSS_DOUBLE_SPOT, null);
        }
    }

    /** A bar of the game's box (Energy, Essence, Storm Intensity) in percent, or -1 if it isn't showing. */
    int bar(int titleWidget) {
        return WintertodtHelper.number(client.getWidget(titleWidget), PERCENT);
    }

    int energy() {
        return bar(InterfaceID.TemporossHud.ENERGY_TITLE);
    }

    int essence() {
        return bar(InterfaceID.TemporossHud.ESSENCE_TITLE);
    }

    int storm() {
        return bar(InterfaceID.TemporossHud.STORM_INTENSITY_TITLE);
    }

    int points() {
        return WintertodtHelper.number(client.getWidget(InterfaceID.TemporossHud.POINTS_TEXT), NUMBER);
    }

    /** Raw (with crystallised), cooked fish you carry, and the damage they'd do loaded into the cannon. */
    int[] fish() {
        ItemContainer inventory = client.getItemContainer(InventoryID.INV);
        if (inventory == null) {
            return new int[]{0, 0, 0};
        }
        int raw = inventory.count(ItemID.TEMPOROSS_RAW_HARPOONFISH);
        int cooked = inventory.count(ItemID.TEMPOROSS_HARPOONFISH);
        int crystal = inventory.count(ItemID.TEMPOROSS_CRYSTALLISED_HARPOONFISH);
        return new int[]{raw + crystal, cooked, damage(raw, cooked, crystal)};
    }

    static int damage(int raw, int cooked, int crystal) {
        return raw * DAMAGE_RAW + cooked * DAMAGE_COOKED + crystal * DAMAGE_CRYSTAL;
    }
}
