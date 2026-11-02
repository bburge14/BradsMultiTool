package com.snuffys.planner;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.NPC;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;

/**
 * The Planner's DROPS list: for each monster (and, with Count Other Loot, each chest, clue casket or raid), how many
 * kills or opens, the Grand Exchange value and every item, with how often each one came (the drop rate you've seen),
 * the kills since its last valuable drop and its best drop; this session's totals beside them; and your latest drops.
 * Monster drops come from RuneLite's NpcLootReceived (the drops of monsters you killed, as the PvM HUD's loot counts
 * them); other loot from RuneLite's LootReceived, which only RuneLite's Loot Tracker posts, so only while it's on. The
 * Loot Tracker re-posts monster kills as LootReceived too, so those (and player kills) are skipped there to count each
 * once. Priced like the Loot Tracker (ItemManager, noted items as their unnoted kind). Saved with your RuneScape
 * profile; this session's totals are kept in memory only, from when the client started.
 */
@Singleton
class DropTracker {

    private static final String KEY = "planner.drops";
    private static final int RECENT_KEPT = 50;

    // Item array slots: {total quantity, total value, times dropped, item id}. Saves before drop rates had only the
    // first two; they're filled out (0: not known) when read.
    static final int QUANTITY = 0;
    static final int VALUE = 1;
    static final int TIMES = 2;
    static final int ID = 3;
    private static final int SLOTS = 4;

    // Loot that isn't a monster's, for saves from before the kind was kept
    private static final Pattern OPENED = Pattern.compile(
        "(?i).*(casket|chest|crate|box|reward|raid|barrows|chambers|theatre|tombs|wintertodt|tempoross|cache|supply|"
            + "pool|impling|herbiboar|gauntlet|zalcano|rift).*");

    /** One monster's or chest's totals. */
    static final class Source {
        int count;
        long value;
        /** Item name -> {quantity, value, times dropped, item id}, in the order first dropped. */
        Map<String, long[]> items = new LinkedHashMap<>();
        /** Kills or opens counted since times dropped were kept (all of them, except in saves from before that). */
        int rated;
        /** Kills or opens since its last valuable drop (at Valuable Drop From then). */
        int dry;
        /** Whether it has given a valuable drop since that was kept. */
        boolean valuableSeen;
        /** Its single most valuable drop (one item stack), or null. */
        Drop best;
        /** When it last gave anything (seconds), 0 if not known. */
        long last;
        /** "kill" or "open" (null in older saves: guessed from the name). */
        String kind;

        /** Whether it's a chest, casket or other loot that's opened rather than killed. */
        boolean opened(String name) {
            return kind != null ? "open".equals(kind) : name != null && OPENED.matcher(name).matches();
        }

        /** Whether its kills weren't all counted for drop rates (saved before they were kept). */
        boolean partlyRated() {
            return rated < count;
        }
    }

    /** One item from one drop. */
    static final class Drop {
        String source;
        String item;
        long quantity;
        long value;
        long at;
        /** The item's id, for its picture (0 in older saves: looked up by name). */
        int id;
    }

    /** An item stack from a drop, with its name and value worked out. */
    static final class Stack {
        final String item;
        final int id;
        final long quantity;
        final long value;

        Stack(String item, int id, long quantity, long value) {
            this.item = item;
            this.id = id;
            this.quantity = quantity;
            this.value = value;
        }
    }

    static final class Saved {
        Map<String, Source> sources = new LinkedHashMap<>();
        List<Drop> recent = new ArrayList<>();
    }

    private final ConfigManager configManager;
    private final ItemManager itemManager;
    private final PlannerConfig config;
    private final PlannerView view;
    private final Gson gson;
    // This session's totals per RuneScape profile (in memory only)
    private final Map<String, Map<String, Source>> session = new HashMap<>();

    @Inject
    DropTracker(ConfigManager configManager, ItemManager itemManager, PlannerConfig config, PlannerView view, Gson gson) {
        this.configManager = configManager;
        this.itemManager = itemManager;
        this.config = config;
        this.view = view;
        this.gson = gson;
    }

    @Subscribe
    public void onNpcLootReceived(NpcLootReceived event) {
        NPC npc = event.getNpc();
        String name = npc == null ? null : npc.getName();
        record(name == null ? "Unknown monster" : name, false, 1, event.getItems());
    }

    @Subscribe
    public void onLootReceived(LootReceived event) {
        // Monster kills come as NpcLootReceived above; player kills aren't kept
        if (!config.plannerOtherLoot() || event.getType() == LootRecordType.NPC || event.getType() == LootRecordType.PLAYER) {
            return;
        }
        record(event.getName() == null ? "Other loot" : event.getName(), true, Math.max(1, event.getAmount()), event.getItems());
    }

    // On the client thread (loot events are), so item names can be read
    private void record(String sourceName, boolean opened, int count, Collection<ItemStack> items) {
        if (configManager.getRSProfileKey() == null) {
            return;
        }
        List<Stack> stacks = new ArrayList<>();
        for (ItemStack stack : items) {
            int id = itemManager.canonicalize(stack.getId());
            String item = itemManager.getItemComposition(id).getName();
            stacks.add(new Stack(item, id, stack.getQuantity(), (long) itemManager.getItemPrice(id) * stack.getQuantity()));
        }
        record(sourceName, opened, count, stacks, System.currentTimeMillis() / 1000);
    }

    /** Package-private for the tests: adds one kill's (or {@code count} opens') items. */
    synchronized void record(String sourceName, boolean opened, int count, List<Stack> stacks, long now) {
        String profile = configManager.getRSProfileKey();
        if (profile == null) {
            return;
        }
        long valuable = config.plannerValuableDrop();
        Saved saved = saved();
        add(saved.sources.computeIfAbsent(sourceName, k -> new Source()), opened, count, stacks, valuable, now);
        add(session.computeIfAbsent(profile, k -> new LinkedHashMap<>()).computeIfAbsent(sourceName, k -> new Source()),
            opened, count, stacks, valuable, now);
        for (Stack stack : stacks) {
            Drop drop = drop(sourceName, stack, now);
            saved.recent.add(0, drop);
        }
        while (saved.recent.size() > RECENT_KEPT) {
            saved.recent.remove(saved.recent.size() - 1);
        }
        save(saved);
    }

    /** Adds one drop to a source's totals. */
    static void add(Source source, boolean opened, int count, List<Stack> stacks, long valuable, long now) {
        source.count += count;
        source.rated += count;
        source.kind = opened ? "open" : "kill";
        boolean big = false;
        // The same item twice in one drop counts as dropped once
        Map<String, Boolean> seen = new HashMap<>();
        for (Stack stack : stacks) {
            source.value += stack.value;
            long[] total = slots(source.items, stack.item);
            total[QUANTITY] += stack.quantity;
            total[VALUE] += stack.value;
            if (seen.put(stack.item, true) == null) {
                total[TIMES]++;
            }
            if (stack.id > 0) {
                total[ID] = stack.id;
            }
            big |= stack.value >= valuable;
            if (source.best == null || stack.value > source.best.value) {
                source.best = drop(null, stack, now);
            }
        }
        if (!stacks.isEmpty()) {
            source.last = now;
        }
        if (big) {
            source.dry = 0;
            source.valuableSeen = true;
        } else {
            source.dry += count;
        }
    }

    // An item's array, made long enough
    private static long[] slots(Map<String, long[]> items, String item) {
        long[] total = items.get(item);
        if (total == null || total.length < SLOTS) {
            long[] longer = new long[SLOTS];
            if (total != null) {
                System.arraycopy(total, 0, longer, 0, total.length);
            }
            items.put(item, longer);
            total = longer;
        }
        return total;
    }

    private static Drop drop(String source, Stack stack, long now) {
        Drop drop = new Drop();
        drop.source = source;
        drop.item = stack.item;
        drop.id = stack.id;
        drop.quantity = stack.quantity;
        drop.value = stack.value;
        drop.at = now;
        return drop;
    }

    /**
     * The drop rate seen: kills (or opens) per time it came, or -1 when it isn't known (counted before the plugin
     * kept how often).
     */
    static double rate(Source source, long[] item) {
        if (item.length <= TIMES || item[TIMES] <= 0 || source.rated <= 0) {
            return -1;
        }
        return Math.max(1.0, (double) source.rated / item[TIMES]);
    }

    /** "1 in 45", "1 in 2.5", "every kill" (or "every open"); "?" when not known. */
    static String rateText(Source source, long[] item, boolean opened) {
        double rate = rate(source, item);
        if (rate < 0) {
            return "?";
        }
        if (item[TIMES] >= source.rated) {
            return opened ? "every open" : "every kill";
        }
        return "1 in " + (rate < 9.95 ? String.format(java.util.Locale.ROOT, "%.1f", rate).replace(".0", "") : String.format("%,d", Math.round(rate)));
    }

    /** Each monster or chest with its totals, most valuable first. */
    synchronized List<Map.Entry<String, Source>> sources() {
        List<Map.Entry<String, Source>> out = new ArrayList<>(saved().sources.entrySet());
        out.sort((a, b) -> Long.compare(b.getValue().value, a.getValue().value));
        return out;
    }

    /** One monster's or chest's all-time totals, or null. */
    synchronized Source source(String name) {
        return saved().sources.get(name);
    }

    /** One monster's or chest's totals this session (since the client started), or null. */
    synchronized Source sessionSource(String name) {
        String profile = configManager.getRSProfileKey();
        Map<String, Source> sources = profile == null ? null : session.get(profile);
        Source source = sources == null ? null : sources.get(name);
        // A copy, as the client thread adds to it
        return source == null ? null : gson.fromJson(gson.toJson(source), Source.class);
    }

    synchronized List<Drop> recent() {
        return saved().recent;
    }

    /** Clears one source (and its recent drops and this session's count). */
    synchronized void reset(String source) {
        Saved saved = saved();
        saved.sources.remove(source);
        saved.recent.removeIf(drop -> source.equals(drop.source));
        Map<String, Source> sources = session.get(configManager.getRSProfileKey());
        if (sources != null) {
            sources.remove(source);
        }
        save(saved);
    }

    synchronized void resetAll() {
        session.remove(configManager.getRSProfileKey());
        save(new Saved());
    }

    private Saved saved() {
        String json = configManager.getRSProfileKey() == null ? null : configManager.getRSProfileConfiguration(PlannerChores.GROUP, KEY);
        return parse(gson, json);
    }

    /** Package-private for the tests: reads what was saved (older saves too), or an empty list when it can't. */
    static Saved parse(Gson gson, String json) {
        try {
            Saved saved = json == null ? null : gson.fromJson(json, Saved.class);
            if (saved != null && saved.sources != null && saved.recent != null) {
                saved.recent.removeIf(drop -> drop == null || drop.source == null || drop.item == null);
                saved.sources.values().removeIf(source -> source == null || source.items == null);
                for (Source source : saved.sources.values()) {
                    source.items.values().removeIf(item -> item == null || item.length < 2);
                    for (String item : new ArrayList<>(source.items.keySet())) {
                        slots(source.items, item);
                    }
                }
                return saved;
            }
        } catch (JsonSyntaxException e) {
            // Unreadable: start again
        }
        return new Saved();
    }

    private void save(Saved saved) {
        if (configManager.getRSProfileKey() != null) {
            configManager.setRSProfileConfiguration(PlannerChores.GROUP, KEY, gson.toJson(saved));
        }
        view.update();
    }
}
