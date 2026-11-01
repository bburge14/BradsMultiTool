package com.bradsmultitool;

import java.awt.Color;

/** Text is white unless colour carries meaning: good/bad state, or a warning. Shared by every overlay. */
final class HudColors {

    static final Color GOOD = new Color(50, 220, 50);
    static final Color BAD = new Color(255, 70, 70);
    static final Color WARN = new Color(255, 150, 0);

    private HudColors() {
    }

    /**
     * Gives one of our own panels the overlay background colour picked in RuneLite's settings (what its built-in
     * panels get), so our cards match the rest of the overlays.
     */
    static void theme(net.runelite.client.ui.overlay.components.PanelComponent panel, java.awt.Color preferred) {
        if (preferred != null) {
            panel.setBackgroundColor(preferred);
        }
    }
}
