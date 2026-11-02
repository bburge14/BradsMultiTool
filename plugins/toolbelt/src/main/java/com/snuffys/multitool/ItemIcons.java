package com.snuffys.multitool;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;

/** Item pictures by item name, for the cards and the skilling tiles. Names are looked up once in the GE's item list. */
@Singleton
class ItemIcons {

    private final ItemManager itemManager;
    // (the overlays ask on the client thread, the Planner's sidebar on the Swing thread)
    private final Map<String, Integer> ids = new ConcurrentHashMap<>();

    @Inject
    ItemIcons(ItemManager itemManager) {
        this.itemManager = itemManager;
    }

    /** The picture of the item with this name, or null when it isn't on the GE (untradeable) or there's no name. */
    BufferedImage get(String name) {
        int id = id(name);
        return id > 0 ? itemManager.getImage(id) : null;
    }

    /** The id of the GE item with this name (any case), or -1 when there's none. */
    int id(String name) {
        if (name == null || name.isEmpty()) {
            return -1;
        }
        return ids.computeIfAbsent(name.toLowerCase(), lower -> {
            for (ItemPrice price : itemManager.search(lower)) {
                if (price.getName().equalsIgnoreCase(lower)) {
                    return price.getId();
                }
            }
            return -1;
        });
    }
}
