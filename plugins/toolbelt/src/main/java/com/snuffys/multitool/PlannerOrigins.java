package com.snuffys.multitool;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;

/**
 * Where your items came from, so a plan's money is right: how many of each you bought and what you paid (on the GE,
 * from {@link PlannerGeLimits}; in trades with other players, from the trade screen when it's accepted), and how many
 * you got yourself (it turned up in your inventory with XP the same tick: cut, mined, caught, made). What you tell it
 * by hand counts the same. Only reads what the game shows; only while RuneLite runs with the Planner on.
 */
@Singleton
class PlannerOrigins {

    private static final String KEY = "plannerOrigins";
    /** The other player's side of the trade screen. */
    private static final int TRADE_OTHER = InventoryID.TRADEOFFER | 0x8000;

    /** One item's record: bought (how many, gp in all) and got yourself. */
    static final class Origin {
        long bought;
        long paid;
        long gathered;

        /** How many it knows the origin of. */
        long known() {
            return bought + gathered;
        }

        /** What you paid for each you bought, on average (0 if none). */
        double paidEach() {
            return bought <= 0 ? 0 : (double) paid / bought;
        }
    }

    private final Client client;
    private final ConfigManager configManager;
    private final ItemManager itemManager;
    private final PlannerView view;

    private final Map<Integer, Origin> origins = new HashMap<>();
    private String loadedFor;
    private boolean dirty;
    private int version;

    // The inventory as last seen, and the tick XP was last gained (what turns up with XP is got yourself)
    private Map<Integer, Integer> inventory;
    private int tick;
    private int xpTick = -10;
    private final Map<Skill, Integer> seenXp = new HashMap<>();
    // The trade screen's two sides as last seen (recorded when it's accepted)
    private Map<Integer, Integer> tradeMine = new HashMap<>();
    private Map<Integer, Integer> tradeTheirs = new HashMap<>();

    @Inject
    PlannerOrigins(Client client, ConfigManager configManager, ItemManager itemManager, PlannerView view) {
        this.client = client;
        this.configManager = configManager;
        this.itemManager = itemManager;
        this.view = view;
    }

    // ---- From the game (client thread) ----

    @Subscribe
    public void onRuneScapeProfileChanged(RuneScapeProfileChanged event) {
        loadedFor = null;
        inventory = null;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() != GameState.LOGGED_IN) {
            // (logging in reports the whole inventory and every skill: not something you just got)
            inventory = null;
            seenXp.clear();
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged event) {
        Integer before = seenXp.put(event.getSkill(), event.getXp());
        if (before != null && event.getXp() > before && event.getSkill() != Skill.HITPOINTS) {
            xpTick = tick;
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        int id = event.getContainerId();
        if (id == InventoryID.INV) {
            Map<Integer, Integer> now = counts(event.getItemContainer());
            if (inventory != null && tick - xpTick <= 1) {
                // Turned up with XP (this tick or the last): got yourself
                for (Map.Entry<Integer, Integer> e : now.entrySet()) {
                    int more = e.getValue() - inventory.getOrDefault(e.getKey(), 0);
                    if (more > 0 && e.getKey() != ItemID.COINS) {
                        gathered(e.getKey(), more);
                    }
                }
            }
            inventory = now;
        } else if (id == InventoryID.TRADEOFFER) {
            tradeMine = counts(event.getItemContainer());
        } else if (id == TRADE_OTHER) {
            tradeTheirs = counts(event.getItemContainer());
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() == ChatMessageType.TRADE && "Accepted trade.".equals(event.getMessage())) {
            traded(tradeMine, tradeTheirs);
            tradeMine = new HashMap<>();
            tradeTheirs = new HashMap<>();
            // (what the trade gave you isn't something you got yourself)
            inventory = null;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        tick++;
        if (inventory == null && client.getGameState() == GameState.LOGGED_IN) {
            ItemContainer inv = client.getItemContainer(InventoryID.INV);
            if (inv != null) {
                inventory = counts(inv);
            }
        }
        if (dirty) {
            dirty = false;
            save();
            view.update();
        }
    }

    // A trade with another player: what you got for what you gave (coins, and the GE worth of any items), shared out
    // over what you got by its GE worth
    private void traded(Map<Integer, Integer> mine, Map<Integer, Integer> theirs) {
        if (theirs.isEmpty()) {
            return;
        }
        long gave = 0;
        for (Map.Entry<Integer, Integer> e : mine.entrySet()) {
            gave += e.getKey() == ItemID.COINS ? e.getValue() : (long) worth(e.getKey()) * e.getValue();
        }
        long worthGot = 0;
        for (Map.Entry<Integer, Integer> e : theirs.entrySet()) {
            if (e.getKey() != ItemID.COINS) {
                worthGot += (long) worth(e.getKey()) * e.getValue();
            }
        }
        for (Map.Entry<Integer, Integer> e : theirs.entrySet()) {
            if (e.getKey() == ItemID.COINS) {
                continue;
            }
            long share = worthGot > 0 ? Math.round((double) gave * worth(e.getKey()) * e.getValue() / worthGot)
                : gave / Math.max(1, theirs.size());
            bought(e.getKey(), e.getValue(), share);
        }
    }

    private int worth(int itemId) {
        try {
            return (int) Math.max(0, Math.min(Integer.MAX_VALUE, itemManager.getItemPrice(itemId)));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    // The item ids (noted as unnoted) and how many
    private Map<Integer, Integer> counts(ItemContainer container) {
        Map<Integer, Integer> out = new HashMap<>();
        if (container == null) {
            return out;
        }
        for (Item item : container.getItems()) {
            if (item == null || item.getId() <= 0 || item.getQuantity() <= 0) {
                continue;
            }
            out.merge(itemManager.canonicalize(item.getId()), item.getQuantity(), Integer::sum);
        }
        return out;
    }

    // ---- Recording ----

    /** You bought some (the GE, a trade, or by hand): how many, and what you paid for them in all. */
    synchronized void bought(int itemId, long quantity, long gp) {
        if (itemId <= 0 || quantity <= 0) {
            return;
        }
        loadIfNeeded();
        // (ids come unnoted: the GE's are, the trade screen's and the inventory's are canonicalized here, by hand from the name)
        Origin o = origins.computeIfAbsent(itemId, k -> new Origin());
        o.bought += quantity;
        o.paid += Math.max(0, gp);
        changed();
    }

    /** You got some yourself (gathered or made, or by hand). */
    synchronized void gathered(int itemId, long quantity) {
        if (itemId <= 0 || quantity <= 0) {
            return;
        }
        loadIfNeeded();
        origins.computeIfAbsent(itemId, k -> new Origin()).gathered += quantity;
        changed();
    }

    /** Clears an item's record (by hand, to start it again). */
    synchronized void forget(int itemId) {
        loadIfNeeded();
        if (origins.remove(itemId) != null) {
            changed();
        }
    }

    private void changed() {
        version++;
        dirty = true;
    }

    // ---- Reading ----

    /** An item's record (a copy), or null when nothing's known of it. */
    synchronized Origin of(int itemId) {
        loadIfNeeded();
        Origin o = origins.get(itemId);
        if (o == null) {
            return null;
        }
        Origin copy = new Origin();
        copy.bought = o.bought;
        copy.paid = o.paid;
        copy.gathered = o.gathered;
        return copy;
    }

    /** Changes when anything's recorded (the plans redraw). */
    int version() {
        return version;
    }

    /**
     * What {@code have} of an item you own cost you: the ones it knows came from somewhere at what they cost (bought at
     * what you paid on average, got yourself free, in the proportion you got them), the rest at {@code priceEach} (it
     * doesn't know: what they're worth). {@code [0]}: gp; {@code [1]}: how many of them were bought; {@code [2]}: got
     * yourself; {@code [3]}: not known.
     */
    static long[] ownedCost(Origin origin, long have, int priceEach) {
        if (have <= 0) {
            return new long[4];
        }
        long known = origin == null ? 0 : Math.min(have, origin.known());
        long bought = known <= 0 ? 0 : Math.round((double) known * origin.bought / origin.known());
        long gathered = known - bought;
        long unknown = have - known;
        long gp = Math.round(bought * (origin == null ? 0 : origin.paidEach())) + unknown * (long) priceEach;
        return new long[] {gp, bought, gathered, unknown};
    }

    // ---- Saved (per account): "id:bought:paid:gathered,..." ----

    private void loadIfNeeded() {
        String profile = configManager.getRSProfileKey();
        if (profile == null || profile.equals(loadedFor)) {
            return;
        }
        loadedFor = profile;
        origins.clear();
        String saved = configManager.getRSProfileConfiguration(PlannerChores.GROUP, KEY);
        if (saved == null) {
            return;
        }
        for (String part : saved.split(",")) {
            String[] f = part.split(":");
            if (f.length < 4) {
                continue;
            }
            try {
                Origin o = new Origin();
                o.bought = Long.parseLong(f[1]);
                o.paid = Long.parseLong(f[2]);
                o.gathered = Long.parseLong(f[3]);
                origins.put(Integer.parseInt(f[0]), o);
            } catch (NumberFormatException e) {
                // (skipped)
            }
        }
    }

    private synchronized void save() {
        if (configManager.getRSProfileKey() == null) {
            return;
        }
        StringBuilder out = new StringBuilder();
        for (Map.Entry<Integer, Origin> e : origins.entrySet()) {
            if (out.length() > 0) {
                out.append(',');
            }
            Origin o = e.getValue();
            out.append(e.getKey()).append(':').append(o.bought).append(':').append(o.paid).append(':').append(o.gathered);
        }
        configManager.setRSProfileConfiguration(PlannerChores.GROUP, KEY, out.toString());
    }
}
