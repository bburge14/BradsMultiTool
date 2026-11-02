package com.bradsmultitool;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * kills or opens, the Grand Exchange value and the items; and your latest drops. Monster drops come from
 * RuneLite's NpcLootReceived (the drops of monsters you killed, as the PvM HUD's loot counts them); other loot from
 * RuneLite's LootReceived, which only RuneLite's Loot Tracker posts, so only while it's on. The Loot Tracker
 * re-posts monster kills as LootReceived too, so those (and player kills) are skipped there to count each once.
 * Priced like the Loot Tracker (ItemManager, noted items as their unnoted kind). Saved with your RuneScape profile.
 */
@Singleton
class DropTracker {

    private static final String KEY = "planner.drops";
    private static final int RECENT_KEPT = 50;

    /** One monster's or chest's totals. */
    static final class Source {
        int count;
        long value;
        /** Item name -> {quantity, value}, in the order first dropped. */
        Map<String, long[]> items = new LinkedHashMap<>();
    }

    /** One item from one drop. */
    static final class Drop {
        String source;
        String item;
        long quantity;
        long value;
        long at;
    }

    private static final class Saved {
        Map<String, Source> sources = new LinkedHashMap<>();
        List<Drop> recent = new ArrayList<>();
    }

    private final ConfigManager configManager;
    private final ItemManager itemManager;
    private final PlannerConfig config;
    private final PlannerView view;
    private final Gson gson;

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
        record(name == null ? "Unknown monster" : name, 1, event.getItems());
    }

    @Subscribe
    public void onLootReceived(LootReceived event) {
        // Monster kills come as NpcLootReceived above; player kills aren't kept
        if (!config.plannerOtherLoot() || event.getType() == LootRecordType.NPC || event.getType() == LootRecordType.PLAYER) {
            return;
        }
        record(event.getName() == null ? "Other loot" : event.getName(), Math.max(1, event.getAmount()), event.getItems());
    }

    // On the client thread (loot events are), so item names can be read
    private synchronized void record(String sourceName, int count, Collection<ItemStack> items) {
        if (configManager.getRSProfileKey() == null) {
            return;
        }
        Saved saved = saved();
        Source source = saved.sources.computeIfAbsent(sourceName, k -> new Source());
        source.count += count;
        long now = System.currentTimeMillis() / 1000;
        for (ItemStack stack : items) {
            int id = itemManager.canonicalize(stack.getId());
            String item = itemManager.getItemComposition(id).getName();
            long value = (long) itemManager.getItemPrice(id) * stack.getQuantity();
            source.value += value;
            long[] total = source.items.computeIfAbsent(item, k -> new long[2]);
            total[0] += stack.getQuantity();
            total[1] += value;
            Drop drop = new Drop();
            drop.source = sourceName;
            drop.item = item;
            drop.quantity = stack.getQuantity();
            drop.value = value;
            drop.at = now;
            saved.recent.add(0, drop);
        }
        while (saved.recent.size() > RECENT_KEPT) {
            saved.recent.remove(saved.recent.size() - 1);
        }
        save(saved);
    }

    /** Each monster or chest with its totals, most valuable first. */
    synchronized List<Map.Entry<String, Source>> sources() {
        List<Map.Entry<String, Source>> out = new ArrayList<>(saved().sources.entrySet());
        out.sort((a, b) -> Long.compare(b.getValue().value, a.getValue().value));
        return out;
    }

    synchronized List<Drop> recent() {
        return saved().recent;
    }

    /** Clears one source (and its recent drops). */
    synchronized void reset(String source) {
        Saved saved = saved();
        saved.sources.remove(source);
        saved.recent.removeIf(drop -> source.equals(drop.source));
        save(saved);
    }

    synchronized void resetAll() {
        save(new Saved());
    }

    private Saved saved() {
        String json = configManager.getRSProfileKey() == null ? null : configManager.getRSProfileConfiguration(PlannerChores.GROUP, KEY);
        try {
            Saved saved = json == null ? null : gson.fromJson(json, Saved.class);
            if (saved != null && saved.sources != null && saved.recent != null) {
                saved.recent.removeIf(drop -> drop == null || drop.source == null || drop.item == null);
                saved.sources.values().removeIf(source -> source == null || source.items == null);
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
