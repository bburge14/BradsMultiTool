package com.bradsmultitool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

/**
 * Grand Exchange buy limits, from your own offers: every buy as it fills (the item, how many, when), each item's
 * 4-hour window from its first buy, how much of its limit (RuneLite's item data) that window has used and when it
 * resets, and whether a buy offer that isn't filling is at the limit or just priced under the market. The game keeps
 * no times for trades (its History tab has none), so the times are the fills seen here: those that came in while you
 * were logged out (or before this was on) count from when they were seen, so their reset is "by" that time, not "at".
 * Information only: nothing is bought or changed. Kept per account.
 */
@Singleton
class PlannerGeLimits {

    /** The Grand Exchange's buy limit window. */
    static final long WINDOW = 4 * 60 * 60 * 1000L;

    private static final String BUYS_KEY = "geBuys";
    private static final String SLOTS_KEY = "geSlots";
    private static final String FILLS_KEY = "geTrades";
    /** Trades older than this don't set "your price" (and aren't kept). */
    static final long PRICE_DAYS = 7;
    private static final int SLOT_COUNT = 8;
    // The offers the game sends as you log in: the ones it sends a few ticks in are still that catch-up, not new fills
    private static final int SETTLE_TICKS = 3;

    /** One fill: how many of an item, when; exact when seen as it happened (else by then, while you were away). */
    static final class Buy {
        final int item;
        final int quantity;
        final long at;
        final boolean exact;

        Buy(int item, int quantity, long at, boolean exact) {
            this.item = item;
            this.quantity = quantity;
            this.at = at;
            this.exact = exact;
        }
    }

    /** A GE slot as last seen. */
    static final class Slot {
        final int item;
        final int sold;
        final int total;
        final long price;
        final GrandExchangeOfferState state;
        /** The gp the offer has spent (a buy) or brought in (a sale) so far. */
        final long spent;

        Slot(int item, int sold, int total, long price, GrandExchangeOfferState state) {
            this(item, sold, total, price, state, 0);
        }

        Slot(int item, int sold, int total, long price, GrandExchangeOfferState state, long spent) {
            this.item = item;
            this.sold = sold;
            this.total = total;
            this.price = price;
            this.state = state;
            this.spent = spent;
        }

        boolean sell() {
            return state == GrandExchangeOfferState.SELLING || state == GrandExchangeOfferState.SOLD
                || state == GrandExchangeOfferState.CANCELLED_SELL;
        }

        boolean buying() {
            return state == GrandExchangeOfferState.BUYING;
        }

        boolean buy() {
            return state == GrandExchangeOfferState.BUYING || state == GrandExchangeOfferState.BOUGHT
                || state == GrandExchangeOfferState.CANCELLED_BUY;
        }
    }

    /** A real trade: how many of an item you bought or sold, for how much gp in all (before the sale's tax), when. */
    static final class Fill {
        final int item;
        final int quantity;
        final long gp;
        final long at;
        final boolean sold;

        Fill(int item, int quantity, long gp, long at, boolean sold) {
            this.item = item;
            this.quantity = quantity;
            this.gp = gp;
            this.at = at;
            this.sold = sold;
        }
    }

    /** An item's current 4-hour window (or, with an open buy offer, the offer with no window yet). */
    static final class Window {
        final int item;
        String name = "";
        /** Its buy limit (0: unknown). */
        int limit;
        int bought;
        /** When it resets (0: no buys in a window now). */
        long resetsAt;
        /** The window's first buy was seen as it happened (else it resets by then, maybe sooner). */
        boolean exact = true;
        /** An open or finished buy offer for it, if any. */
        Slot offer;
        /** Its Grand Exchange price now (0: unknown). */
        long price;

        Window(int item) {
            this.item = item;
        }

        boolean atLimit() {
            return limit > 0 && bought >= limit;
        }

        /** How many more it can buy now (-1: the limit isn't known). */
        int left() {
            return limit > 0 ? Math.max(0, limit - bought) : -1;
        }
    }

    // A queued offer event: handled on the next tick, once the account (and where to keep it) is known
    private static final class Seen {
        final int slot;
        final Slot offer;
        final long at;
        final boolean live;

        Seen(int slot, Slot offer, long at, boolean live) {
            this.slot = slot;
            this.offer = offer;
            this.at = at;
            this.live = live;
        }
    }

    private final Client client;
    private final ConfigManager configManager;
    private final ItemManager itemManager;
    private final PlannerView view;
    private final Alerts alerts;
    private final PlannerConfig config;

    private final List<Buy> buys = new ArrayList<>();
    private final List<Fill> fills = new ArrayList<>();
    private int fillCount;
    private final Slot[] slots = new Slot[SLOT_COUNT];
    private final List<Seen> queue = new ArrayList<>();
    // Names, limits and prices, looked up on the client thread
    private final Map<Integer, String> names = new HashMap<>();
    private final Map<Integer, Integer> limits = new HashMap<>();
    private final Map<Integer, Long> prices = new HashMap<>();
    private final Set<Integer> wanted = Collections.synchronizedSet(new HashSet<>());
    // Windows already said to have reset (item and when)
    private final Set<String> told = new HashSet<>();
    private String loadedFor;
    private int loggedInTicks;
    private int ticks;

    @Inject
    PlannerGeLimits(Client client, ConfigManager configManager, ItemManager itemManager, PlannerView view, Alerts alerts,
                    PlannerConfig config) {
        this.client = client;
        this.configManager = configManager;
        this.itemManager = itemManager;
        this.view = view;
        this.alerts = alerts;
        this.config = config;
    }

    // ---- From the game (client thread) ----

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() != GameState.LOGGED_IN) {
            loggedInTicks = 0;
        }
    }

    @Subscribe
    public void onRuneScapeProfileChanged(RuneScapeProfileChanged event) {
        loadedFor = null;
    }

    @Subscribe
    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event) {
        GrandExchangeOffer offer = event.getOffer();
        if (offer == null || event.getSlot() < 0 || event.getSlot() >= SLOT_COUNT) {
            return;
        }
        // Live: in the game a few ticks already (a fill as it happens), not the catch-up the game sends as you log in
        boolean live = client.getGameState() == GameState.LOGGED_IN && loggedInTicks >= SETTLE_TICKS;
        queue.add(new Seen(event.getSlot(), new Slot(offer.getItemId(), offer.getQuantitySold(), offer.getTotalQuantity(),
            offer.getPrice(), offer.getState(), offer.getSpent()), System.currentTimeMillis(), live));
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        loggedInTicks++;
        String profile = configManager.getRSProfileKey();
        if (profile == null) {
            return;
        }
        boolean changed = false;
        if (!profile.equals(loadedFor)) {
            load();
            loadedFor = profile;
            changed = true;
        }
        if (!queue.isEmpty()) {
            for (Seen seen : new ArrayList<>(queue)) {
                changed |= handle(seen);
            }
            queue.clear();
            save();
        }
        // Names, limits and prices for what's shown (and what the plan asked about)
        for (Integer id : itemsShown()) {
            changed |= lookUp(id, false);
        }
        for (Integer id : new ArrayList<>(wanted)) {
            changed |= lookUp(id, false);
            wanted.remove(id);
        }
        if (++ticks % 100 == 0) {
            // Prices move: once a minute
            for (Integer id : itemsShown()) {
                lookUp(id, true);
            }
            changed = true;
        }
        if (ticks % 5 == 0) {
            changed |= sayResets(System.currentTimeMillis());
        }
        if (changed) {
            view.update();
        }
    }

    // A slot's change: a buy that filled more is a purchase, at its time (by then, when it came in while you were away)
    private synchronized boolean handle(Seen seen) {
        Slot before = slots[seen.slot];
        Slot now = seen.offer;
        if (now.state == GrandExchangeOfferState.EMPTY) {
            if (!seen.live) {
                // (as you log in the game can send a slot empty before its real offer: not a change)
                return false;
            }
            slots[seen.slot] = null;
            return before != null;
        }
        boolean added = false;
        if (now.buy() || now.sell()) {
            // The same offer as before (same item and side, not gone back): only what filled since
            boolean same = before != null && before.item == now.item && before.buy() == now.buy() && before.sold <= now.sold
                && before.spent <= now.spent;
            int filled = now.sold - (same ? before.sold : 0);
            long gp = now.spent - (same ? before.spent : 0);
            if (filled > 0) {
                if (now.buy()) {
                    buys.add(new Buy(now.item, filled, seen.at, seen.live));
                }
                // What it really cost or brought in (a fill seen while away still has its real gp)
                if (gp > 0) {
                    fills.add(new Fill(now.item, filled, gp, seen.at, now.sell()));
                    fillCount++;
                }
                added = true;
            }
        }
        slots[seen.slot] = now;
        return added || before == null || before.sold != now.sold || before.state != now.state;
    }

    private synchronized boolean lookUp(int id, boolean refreshPrice) {
        boolean changed = false;
        if (!names.containsKey(id)) {
            names.put(id, itemManager.getItemComposition(id).getName());
            changed = true;
        }
        if (!limits.containsKey(id)) {
            ItemStats stats = itemManager.getItemStats(id);
            limits.put(id, stats == null ? 0 : stats.getGeLimit());
            changed = true;
        }
        if (refreshPrice || !prices.containsKey(id)) {
            prices.put(id, itemManager.getItemPrice(id));
            changed = true;
        }
        return changed;
    }

    // "Shark: buy limit reset, you can buy 10,000 again": for an item whose window ended with its limit used, or with a
    // buy offer still open for it
    private synchronized boolean sayResets(long now) {
        boolean any = false;
        for (Window window : windows(buys, now, slots, true).values()) {
            // (only recent resets: one from hours ago, before you logged in, isn't news)
            if (window.resetsAt <= 0 || window.resetsAt > now || window.resetsAt < now - 10 * 60 * 1000L) {
                continue;
            }
            String key = window.item + "@" + window.resetsAt;
            if (told.add(key)) {
                int limit = limits.getOrDefault(window.item, 0);
                boolean used = limit > 0 && window.bought >= limit;
                if (used || (window.offer != null && window.offer.buying())) {
                    alerts.chat("chatGeLimitReset", names.getOrDefault(window.item, "An item") + ": buy limit reset"
                        + (limit > 0 ? ", you can buy " + String.format("%,d", limit) + " again" : ""));
                }
                any = true;
            }
        }
        prune(now);
        return any;
    }

    // ---- The sums ----

    /**
     * Each item's window now: from its first buy, 4 hours; a buy at or after a window's end starts the next. Items with
     * a buy offer in a slot but no window yet are there too, without one.
     */
    static Map<Integer, Window> windows(List<Buy> buys, long now, Slot[] slots) {
        return windows(buys, now, slots, false);
    }

    /** As above; with {@code ended}, each item's latest window even when it's over (for saying it reset). */
    static Map<Integer, Window> windows(List<Buy> buys, long now, Slot[] slots, boolean ended) {
        Map<Integer, List<Buy>> byItem = new LinkedHashMap<>();
        for (Buy buy : buys) {
            byItem.computeIfAbsent(buy.item, k -> new ArrayList<>()).add(buy);
        }
        Map<Integer, Window> out = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Buy>> entry : byItem.entrySet()) {
            List<Buy> list = entry.getValue();
            list.sort((a, b) -> Long.compare(a.at, b.at));
            long start = -1;
            int bought = 0;
            boolean exact = true;
            for (Buy buy : list) {
                if (start < 0 || buy.at >= start + WINDOW) {
                    start = buy.at;
                    bought = 0;
                    exact = buy.exact;
                }
                bought += buy.quantity;
            }
            if (start >= 0 && (ended || now < start + WINDOW)) {
                Window window = new Window(entry.getKey());
                window.bought = bought;
                window.resetsAt = start + WINDOW;
                window.exact = exact;
                out.put(entry.getKey(), window);
            }
        }
        for (Slot slot : slots) {
            if (slot != null && slot.buy()) {
                Window window = out.computeIfAbsent(slot.item, Window::new);
                // (an open offer over a finished one for the same item)
                if (window.offer == null || slot.buying()) {
                    window.offer = slot;
                }
            }
        }
        return out;
    }

    /** For the sidebar (Swing thread): each item's window and offer, the ones at their limit first, then by reset. */
    synchronized List<Window> windows() {
        List<Window> out = new ArrayList<>(windows(buys, System.currentTimeMillis(), slots).values());
        for (Window window : out) {
            window.name = names.getOrDefault(window.item, "Item " + window.item);
            window.limit = limits.getOrDefault(window.item, 0);
            window.price = prices.getOrDefault(window.item, 0L);
        }
        out.sort((a, b) -> a.atLimit() != b.atLimit() ? (a.atLimit() ? -1 : 1)
            : Long.compare(a.resetsAt == 0 ? Long.MAX_VALUE : a.resetsAt, b.resetsAt == 0 ? Long.MAX_VALUE : b.resetsAt));
        return out;
    }

    /** An item's buy limit (0 when unknown, or not looked up yet: it's looked up on the next tick, then redrawn). */
    int limit(int itemId) {
        Integer limit = limits.get(itemId);
        if (limit == null && itemId > 0) {
            wanted.add(itemId);
        }
        return limit == null ? 0 : limit;
    }

    /** How many limits are known (the Calculator redraws when one it asked for comes in). */
    int known() {
        return limits.size();
    }

    /** What's left of an item's limit in its window now (-1: no window, its whole limit). */
    synchronized int leftNow(int itemId) {
        Window window = windows(buys, System.currentTimeMillis(), new Slot[0]).get(itemId);
        int limit = limits.getOrDefault(itemId, 0);
        return window == null || limit <= 0 ? -1 : Math.max(0, limit - window.bought);
    }

    private synchronized Set<Integer> itemsShown() {
        Set<Integer> out = new HashSet<>();
        for (Buy buy : buys) {
            out.add(buy.item);
        }
        for (Slot slot : slots) {
            if (slot != null && (slot.buy() || slot.sell())) {
                out.add(slot.item);
            }
        }
        for (Fill fill : fills) {
            out.add(fill.item);
        }
        return out;
    }

    // Buys older than two windows can't matter any more
    private void prune(long now) {
        buys.removeIf(buy -> buy.at < now - 2 * WINDOW);
        fills.removeIf(fill -> fill.at < now - PRICE_DAYS * 24 * 60 * 60 * 1000L);
        while (fills.size() > 500) {
            fills.remove(0);
        }
    }

    /** For the preview: buys, slots and each item's name, limit and price. */
    synchronized void setSample(List<Buy> sample, Slot[] sampleSlots, Map<Integer, String> sampleNames,
                                Map<Integer, Integer> sampleLimits, Map<Integer, Long> samplePrices) {
        buys.clear();
        buys.addAll(sample);
        System.arraycopy(sampleSlots, 0, slots, 0, Math.min(SLOT_COUNT, sampleSlots.length));
        names.putAll(sampleNames);
        limits.putAll(sampleLimits);
        prices.putAll(samplePrices);
    }

    // ---- Kept per account ----

    private synchronized void load() {
        buys.clear();
        for (int i = 0; i < SLOT_COUNT; i++) {
            slots[i] = null;
        }
        String saved = configManager.getRSProfileConfiguration(PlannerChores.GROUP, BUYS_KEY);
        for (String part : saved == null ? new String[0] : saved.split(";")) {
            String[] f = part.split(":");
            try {
                if (f.length == 4) {
                    buys.add(new Buy(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Long.parseLong(f[2]), "1".equals(f[3])));
                }
            } catch (NumberFormatException e) {
                // a broken entry: skipped
            }
        }
        String slotsSaved = configManager.getRSProfileConfiguration(PlannerChores.GROUP, SLOTS_KEY);
        for (String part : slotsSaved == null ? new String[0] : slotsSaved.split(";")) {
            String[] f = part.split(":");
            try {
                if (f.length == 6 || f.length == 7) {
                    int slot = Integer.parseInt(f[0]);
                    if (slot >= 0 && slot < SLOT_COUNT) {
                        slots[slot] = new Slot(Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]),
                            Long.parseLong(f[4]), GrandExchangeOfferState.valueOf(f[5]), f.length == 7 ? Long.parseLong(f[6]) : 0);
                    }
                }
            } catch (IllegalArgumentException e) {
                // a broken entry: skipped
            }
        }
        fills.clear();
        String tradesSaved = configManager.getRSProfileConfiguration(PlannerChores.GROUP, FILLS_KEY);
        for (String part : tradesSaved == null ? new String[0] : tradesSaved.split(";")) {
            String[] f = part.split(":");
            try {
                if (f.length == 5) {
                    fills.add(new Fill(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Long.parseLong(f[2]), Long.parseLong(f[3]),
                        "s".equals(f[4])));
                }
            } catch (NumberFormatException e) {
                // a broken entry: skipped
            }
        }
        fillCount++;
        prune(System.currentTimeMillis());
    }

    private synchronized void save() {
        if (configManager.getRSProfileKey() == null) {
            return;
        }
        StringBuilder out = new StringBuilder();
        for (Buy buy : buys) {
            out.append(buy.item).append(':').append(buy.quantity).append(':').append(buy.at).append(':')
                .append(buy.exact ? 1 : 0).append(';');
        }
        configManager.setRSProfileConfiguration(PlannerChores.GROUP, BUYS_KEY, out.toString());
        StringBuilder slotsOut = new StringBuilder();
        for (int i = 0; i < SLOT_COUNT; i++) {
            Slot slot = slots[i];
            if (slot != null) {
                slotsOut.append(i).append(':').append(slot.item).append(':').append(slot.sold).append(':').append(slot.total)
                    .append(':').append(slot.price).append(':').append(slot.state.name()).append(':').append(slot.spent).append(';');
            }
        }
        configManager.setRSProfileConfiguration(PlannerChores.GROUP, SLOTS_KEY, slotsOut.toString());
        StringBuilder tradesOut = new StringBuilder();
        for (Fill fill : fills) {
            tradesOut.append(fill.item).append(':').append(fill.quantity).append(':').append(fill.gp).append(':').append(fill.at)
                .append(':').append(fill.sold ? 's' : 'b').append(';');
        }
        configManager.setRSProfileConfiguration(PlannerChores.GROUP, FILLS_KEY, tradesOut.toString());
    }

    // ---- Your own prices and trades (for the plans) ----

    /** Changes when a trade is recorded (the plans redraw). */
    int tradesVersion() {
        return fillCount;
    }

    /**
     * Your own price for an item, by name: what your trades of it in the last week averaged (each fill by how many),
     * your sales when you've sold it (what a plan's product goes for), else your buys; null when you haven't traded it.
     */
    synchronized Long yourPrice(String name) {
        int id = idOf(name);
        if (id <= 0) {
            return null;
        }
        Long sold = average(id, true);
        return sold != null ? sold : average(id, false);
    }

    /** As above, but only your buys (true: only your sales). */
    synchronized Long yourPrice(String name, boolean sold) {
        int id = idOf(name);
        return id <= 0 ? null : average(id, sold);
    }

    private Long average(int id, boolean sold) {
        long since = System.currentTimeMillis() - PRICE_DAYS * 24 * 60 * 60 * 1000L;
        long gp = 0;
        long count = 0;
        for (Fill fill : fills) {
            if (fill.item == id && fill.sold == sold && fill.at >= since) {
                gp += fill.gp;
                count += fill.quantity;
            }
        }
        return count == 0 ? null : Math.round((double) gp / count);
    }

    /**
     * What you really spent buying and got selling these items (by name) since then: {spent, brought in (after the
     * sale tax)}.
     */
    synchronized long[] traded(java.util.Collection<String> itemNames, long since) {
        Set<Integer> ids = new HashSet<>();
        for (String name : itemNames) {
            int id = idOf(name);
            if (id > 0) {
                ids.add(id);
            }
        }
        long spent = 0;
        long got = 0;
        for (Fill fill : fills) {
            if (fill.at >= since && ids.contains(fill.item)) {
                if (fill.sold) {
                    got += fill.gp - Math.min((long) Math.floor(fill.gp * 0.02), 5_000_000L * fill.quantity);
                } else {
                    spent += fill.gp;
                }
            }
        }
        return new long[] {spent, got};
    }

    // An item's id from its name (the items you've traded or offered, whose names were looked up)
    private int idOf(String name) {
        if (name == null) {
            return -1;
        }
        for (Map.Entry<Integer, String> entry : names.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        return -1;
    }

    /** For tests and the preview: trades as if seen. */
    synchronized void addSampleTrade(int item, String name, int quantity, long gp, long at, boolean sold) {
        names.put(item, name);
        fills.add(new Fill(item, quantity, gp, at, sold));
        fillCount++;
    }
}
