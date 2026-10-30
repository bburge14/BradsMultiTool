package com.snuffys.tickcounter;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/**
 * Reads spell names and icons straight from the game's spellbook interface, so icons always
 * match what the player sees, on any spellbook.
 */
class SpellbookReader {

    private static final int SPELLBOOK_GROUP = 218;
    private static final int SPELLBOOK_MAX_CHILD = 300;
    // Combat tab autocast buttons (InterfaceID.CombatInterface.AUTOCAST_NORMAL / AUTOCAST_DEFENSIVE)
    private static final int AUTOCAST_NORMAL = 38862876;
    private static final int AUTOCAST_DEFENSIVE = 38862871;

    private final Map<String, Integer> spriteByName = new HashMap<>();
    private final Map<Integer, String> nameBySprite = new HashMap<>();
    private int lastRefreshTick = -1;

    /** Rescans the spellbook at most once per game tick. Must run on the client thread. */
    void refresh(Client client) {
        int tick = client.getTickCount();
        if (tick == lastRefreshTick) {
            return;
        }
        lastRefreshTick = tick;

        spriteByName.clear();
        nameBySprite.clear();
        for (int child = 0; child < SPELLBOOK_MAX_CHILD; child++) {
            Widget widget = client.getWidget((SPELLBOOK_GROUP << 16) | child);
            if (widget == null || widget.getSpriteId() <= 0 || widget.getName() == null) {
                continue;
            }
            String name = Text.removeTags(widget.getName()).trim();
            if (!name.isEmpty()) {
                spriteByName.put(name.toLowerCase(), widget.getSpriteId());
                nameBySprite.putIfAbsent(widget.getSpriteId(), name);
            }
        }
    }

    /** Spellbook icon sprite for a spell name, or -1 if it isn't in the current spellbook. */
    int spriteFor(String spellName) {
        Integer sprite = spellName != null ? spriteByName.get(spellName.toLowerCase()) : null;
        return sprite != null ? sprite : -1;
    }

    /**
     * Name of the spell shown in the combat tab's autocast slot, matched against the spellbook icons,
     * or null if it can't be read.
     */
    String autocastSpell(Client client, boolean defensive) {
        Widget slot = client.getWidget(defensive ? AUTOCAST_DEFENSIVE : AUTOCAST_NORMAL);
        if (slot == null) {
            return null;
        }

        Deque<Widget> pending = new ArrayDeque<>();
        pending.add(slot);
        while (!pending.isEmpty()) {
            Widget widget = pending.poll();
            String name = nameBySprite.get(widget.getSpriteId());
            if (name != null) {
                return name;
            }
            addAll(pending, widget.getChildren());
            addAll(pending, widget.getStaticChildren());
            addAll(pending, widget.getDynamicChildren());
            addAll(pending, widget.getNestedChildren());
        }
        return null;
    }

    private static void addAll(Deque<Widget> pending, Widget[] widgets) {
        if (widgets == null) {
            return;
        }
        for (Widget widget : widgets) {
            if (widget != null) {
                pending.add(widget);
            }
        }
    }
}
