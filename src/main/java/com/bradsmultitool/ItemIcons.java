package com.bradsmultitool;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;

/** Item pictures by item name, for the cards and the skilling tiles. Names are looked up once in the GE's item list. */
@Singleton
class ItemIcons {

    private final ItemManager itemManager;
    private final Map<String, Integer> ids = new HashMap<>();

    @Inject
    ItemIcons(ItemManager itemManager) {
        this.itemManager = itemManager;
    }

    /** The picture of the item with this name, or null when it isn't on the GE (untradeable) or there's no name. */
    BufferedImage get(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        int id = ids.computeIfAbsent(name.toLowerCase(), lower -> {
            for (ItemPrice price : itemManager.search(lower)) {
                if (price.getName().equalsIgnoreCase(lower)) {
                    return price.getId();
                }
            }
            return -1;
        });
        return id > 0 ? itemManager.getImage(id) : null;
    }
}
