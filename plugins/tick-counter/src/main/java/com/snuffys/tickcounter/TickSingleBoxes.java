package com.snuffys.tickcounter;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;

/** The tick boxes as separate overlays (one per tick, up to the longest cycle), for Move Boxes Separately. */
@Singleton
class TickSingleBoxes {

    static final int MOST = 10;

    final List<BradsMultiToolTickSingleBoxOverlay> overlays = new ArrayList<>();

    @Inject
    TickSingleBoxes(Client client, TickConfig config, TickCounter ticks) {
        for (int tick = 1; tick <= MOST; tick++) {
            overlays.add(new BradsMultiToolTickSingleBoxOverlay(client, config, ticks, tick));
        }
    }
}
