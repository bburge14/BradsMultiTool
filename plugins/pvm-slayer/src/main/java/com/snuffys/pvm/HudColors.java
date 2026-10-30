package com.snuffys.pvm;

import java.awt.Color;

/** Text is white unless colour carries meaning: good/bad state, or a warning. Shared by every overlay. */
final class HudColors {

    static final Color GOOD = new Color(50, 220, 50);
    static final Color BAD = new Color(255, 70, 70);
    static final Color WARN = new Color(255, 150, 0);

    private HudColors() {
    }
}
