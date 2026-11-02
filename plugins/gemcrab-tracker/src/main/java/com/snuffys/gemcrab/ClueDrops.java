package com.snuffys.gemcrab;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;

/**
 * The clue drop alert every one of Snuffy's plugins has (said by one of them, PluginFamily.handlesShared): a clue
 * scroll in the drop of a monster you killed (RuneLite's NpcLootReceived), or a skilling clue (clue bottle, clue
 * geode, clue nest, scroll box) arriving in your inventory. Chat says which tier. Items are told apart by name.
 */
@Singleton
class ClueDrops {

    // A skilling clue picked up within this many ticks (3 minutes) of a monster dropping it was already announced
    private static final int PICKUP_TICKS = 300;

    private final Client client;
    private final CommonConfig config;
    private final Alerts alerts;
    private final PluginFamily family;
    private final ItemManager itemManager;

    // Skilling clues in your inventory as last seen (lower-case name -> how many); null until seen this login
    private Map<String, Integer> carried;
    // Clues announced as drops (lower-case name -> tick), so picking them up isn't said again
    private final Map<String, Integer> dropped = new HashMap<>();

    @Inject
    ClueDrops(Client client, CommonConfig config, Alerts alerts, PluginFamily family, ItemManager itemManager) {
        this.client = client;
        this.config = config;
        this.alerts = alerts;
        this.family = family;
        this.itemManager = itemManager;
    }

    /** Whether an item is a clue: a clue scroll, or a clue bottle, geode, nest or scroll box. */
    static boolean isClue(String name) {
        return name.startsWith("Clue scroll (") || isSkillingClue(name);
    }

    /** The clues skilling gives (the ones that go into your inventory rather than dropping from a monster). */
    static boolean isSkillingClue(String name) {
        return name.startsWith("Clue bottle (") || name.startsWith("Clue geode (") || name.startsWith("Clue nest (")
            || name.startsWith("Scroll box (");
    }

    /** "a hard clue scroll", "an elite clue nest", "a scroll box" (no tier in the name). */
    static String describe(String name) {
        int open = name.indexOf(" (");
        int close = name.indexOf(')', open + 1);
        String kind = (open > 0 ? name.substring(0, open) : name).toLowerCase();
        String tier = open > 0 && close > open ? name.substring(open + 2, close).toLowerCase() : null;
        String words = tier != null ? tier + " " + kind : kind;
        return ("aeiou".indexOf(words.charAt(0)) >= 0 ? "an " : "a ") + words;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() != GameState.LOGGED_IN) {
            // Your inventory is read afresh at the next login, so what you carry then isn't new
            carried = null;
            dropped.clear();
        }
    }

    @Subscribe
    public void onNpcLootReceived(NpcLootReceived event) {
        for (ItemStack item : event.getItems()) {
            String name = itemManager.getItemComposition(item.getId()).getName();
            if (isClue(name)) {
                dropped.put(name.toLowerCase(), client.getTickCount());
                String from = event.getNpc() != null && event.getNpc().getName() != null ? " from " + event.getNpc().getName() : "";
                announce(describe(name) + from);
            }
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getContainerId() != InventoryID.INV) {
            return;
        }
        Map<String, Integer> now = new HashMap<>();
        // Lower-case name -> as the game writes it
        Map<String, String> names = new HashMap<>();
        for (Item item : event.getItemContainer().getItems()) {
            if (item.getId() > 0) {
                String name = itemManager.getItemComposition(item.getId()).getName();
                if (isSkillingClue(name)) {
                    now.merge(name.toLowerCase(), item.getQuantity(), Integer::sum);
                    names.put(name.toLowerCase(), name);
                }
            }
        }
        Map<String, Integer> before = carried;
        carried = now;
        // Taken out of the bank (or the GE, a deposit box...) isn't a new clue
        if (before == null || isBanking()) {
            return;
        }
        int tick = client.getTickCount();
        for (Map.Entry<String, Integer> entry : now.entrySet()) {
            if (entry.getValue() > before.getOrDefault(entry.getKey(), 0)) {
                Integer droppedAt = dropped.remove(entry.getKey());
                if (droppedAt == null || tick - droppedAt > PICKUP_TICKS) {
                    announce(describe(names.get(entry.getKey())));
                }
            }
        }
    }

    private void announce(String what) {
        // With several of ours installed, only one says it
        if (!config.clueDropAlert() || !family.handlesShared()) {
            return;
        }
        alerts.speak(VoiceAlertService.VoiceLine.CLUE_DROP);
        alerts.chat(AlertType.CLUE_DROP, "Clue: " + what + "!");
    }

    private boolean isBanking() {
        for (int group : IdleWarnings.BANKING_INTERFACES) {
            Widget widget = client.getWidget(group, 0);
            if (widget != null && !widget.isHidden()) {
                return true;
            }
        }
        return false;
    }
}
