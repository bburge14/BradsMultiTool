package com.bradsmultitool;

import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;

/**
 * Asks the Planner's sidebar to redraw (from any thread): the trackers call {@link #update()} when what they show
 * changes, and the panel, while it's there, does the drawing on the Swing thread, once however many ask at once.
 */
@Singleton
class PlannerView {

    private final AtomicBoolean queued = new AtomicBoolean();
    private volatile Runnable panel;

    /** The panel's redraw (null when the panel is taken away). */
    void setPanel(Runnable redraw) {
        panel = redraw;
    }

    void update() {
        Runnable redraw = panel;
        if (redraw != null && queued.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(() -> {
                queued.set(false);
                redraw.run();
            });
        }
    }
}
