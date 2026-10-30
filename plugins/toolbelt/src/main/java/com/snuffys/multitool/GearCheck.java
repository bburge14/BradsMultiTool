package com.snuffys.multitool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

/**
 * Whether your worn gear suits the combat style you're fighting with, for the Gemstone Crab card. The crab
 * can't block, so only damage counts: the damage bonus your gear gives the style you use (melee strength,
 * ranged strength, magic damage), and the items whose damage bonus is for another style.
 */
@Singleton
class GearCheck {

    enum Style { MELEE, RANGED, MAGIC }

    /** A worn item with a damage bonus, and the style it's for. */
    static final class Piece {
        final String name;
        final Style style;

        Piece(String name, Style style) {
            this.name = name;
            this.style = style;
        }
    }

    private static final int CONTAINER_EQUIPMENT = 94;
    private static final int SLOT_AMMO = 13;

    private final Client client;
    private final ItemManager itemManager;
    // Damage bonus per style (magic as tenths of a percent), and the worn items that have one
    private final int[] totals = new int[Style.values().length];
    private List<Piece> pieces = Collections.emptyList();
    private boolean stale = true;

    @Inject
    GearCheck(Client client, ItemManager itemManager) {
        this.client = client;
        this.itemManager = itemManager;
    }

    /** The style an item's damage bonus is for, or null when it has none (any style can wear it). */
    static Style damageStyle(ItemEquipmentStats stats) {
        if (stats.getStr() > 0) {
            return Style.MELEE;
        }
        if (stats.getRstr() > 0) {
            return Style.RANGED;
        }
        if (stats.getMdmg() > 0) {
            return Style.MAGIC;
        }
        return null;
    }

    /** "Str +72", "Ranged str +45", "Magic dmg +15%": your gear's damage bonus for this style. */
    String total(Style style) {
        int value = totals[style.ordinal()];
        switch (style) {
            case RANGED: return "Ranged str " + signed(value);
            case MAGIC: return "Magic dmg " + signed(value / 10) + (value % 10 != 0 ? "." + Math.abs(value % 10) : "") + "%";
            default: return "Str " + signed(value);
        }
    }

    boolean hasDamage(Style style) {
        return totals[style.ordinal()] > 0;
    }

    /** Worn items whose damage bonus is for another style than this one. */
    List<String> offStyle(Style style) {
        List<String> names = new ArrayList<>();
        for (Piece piece : pieces) {
            if (piece.style != style) {
                names.add(piece.name);
            }
        }
        return names;
    }

    /** The style CombatStyle reports ("Melee", "Ranged", "Magic"). */
    static Style of(String discipline) {
        return "Ranged".equals(discipline) ? Style.RANGED : "Magic".equals(discipline) ? Style.MAGIC : Style.MELEE;
    }

    private static String signed(int value) {
        return (value >= 0 ? "+" : "") + value;
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getContainerId() == CONTAINER_EQUIPMENT) {
            stale = true;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (stale && client.getGameState() == GameState.LOGGED_IN) {
            stale = false;
            read();
        }
    }

    private void read() {
        java.util.Arrays.fill(totals, 0);
        List<Piece> found = new ArrayList<>();
        ItemContainer worn = client.getItemContainer(CONTAINER_EQUIPMENT);
        Item[] items = worn != null ? worn.getItems() : new Item[0];
        for (int slot = 0; slot < items.length; slot++) {
            Item item = items[slot];
            if (item == null || item.getId() <= 0) {
                continue;
            }
            ItemStats stats = itemManager.getItemStats(item.getId());
            ItemEquipmentStats equipment = stats != null ? stats.getEquipment() : null;
            if (equipment == null) {
                continue;
            }
            totals[Style.MELEE.ordinal()] += equipment.getStr();
            totals[Style.RANGED.ordinal()] += equipment.getRstr();
            totals[Style.MAGIC.ordinal()] += Math.round(equipment.getMdmg() * 10);
            // Ammo only counts for ranged (arrows in the quiver don't make melee gear off-style)
            Style style = damageStyle(equipment);
            if (style != null && slot != SLOT_AMMO) {
                found.add(new Piece(itemManager.getItemComposition(item.getId()).getName(), style));
            }
        }
        pieces = found;
    }
}
