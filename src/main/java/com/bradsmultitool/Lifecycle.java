package com.bradsmultitool;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Starts and stops what a plugin's features use. Features share some pieces (alerts, the combat style,
 * monster data), so each piece is subscribed, added or loaded only once, however many features ask, and
 * everything is undone when the plugin stops.
 */
@Singleton
class Lifecycle {

    private final EventBus eventBus;
    private final OverlayManager overlayManager;
    private final Gson gson;
    private final Set<Object> subscribed = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Object> overlays = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Object> loaded = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<Runnable> onStop = new ArrayList<>();

    @Inject
    Lifecycle(EventBus eventBus, OverlayManager overlayManager, Gson gson) {
        this.eventBus = eventBus;
        this.overlayManager = overlayManager;
        this.gson = gson;
    }

    /** Puts these on the event bus (each once). */
    void subscribe(Object... listeners) {
        for (Object listener : listeners) {
            if (subscribed.add(listener)) {
                eventBus.register(listener);
            }
        }
    }

    /** Shows these overlays (each once). */
    void overlay(Overlay... list) {
        for (Overlay overlay : list) {
            if (overlays.add(overlay)) {
                overlayManager.add(overlay);
            }
        }
    }

    /** Loads a bundled data file once (each data class's load(Gson)). */
    <T> void load(T data, Consumer<Gson> loader) {
        if (loaded.add(data)) {
            loader.accept(gson);
        }
    }

    /** Runs when the plugin stops, before everything is unsubscribed (latest first). */
    void onStop(Runnable action) {
        onStop.add(action);
    }

    void stopAll() {
        for (int i = onStop.size() - 1; i >= 0; i--) {
            onStop.get(i).run();
        }
        onStop.clear();
        for (Object overlay : overlays) {
            overlayManager.remove((Overlay) overlay);
        }
        overlays.clear();
        for (Object listener : subscribed) {
            eventBus.unregister(listener);
        }
        subscribed.clear();
        loaded.clear();
    }
}
