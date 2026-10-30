package com.snuffys.tickcounter;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The tick bar: fills smoothly from left to right over each tick (drawn every frame, so it moves rather than
 * flashes) and snaps back when the next tick comes, with the ideal click zone (green), the late part (orange)
 * and your last click marked on it. Under it, with Click
 * Timing on, a summary of your last clicks. Moved like any overlay (Alt + drag).
 */
@Singleton
public class BradsMultiToolTickBarOverlay extends Overlay {

    private static final int HEIGHT = 14;
    private static final Color BACKGROUND = new Color(20, 20, 20, 170);
    private static final Color LATE_ZONE = new Color(255, 140, 0, 90);
    private static final Color IDEAL_ZONE = new Color(50, 220, 50, 70);

    private final Client client;
    private final TickConfig config;
    private final TickCounter ticks;

    @Inject
    public BradsMultiToolTickBarOverlay(Client client, TickConfig config, TickCounter ticks) {
        this.client = client;
        this.config = config;
        this.ticks = ticks;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        boolean bar = config.tickBar();
        boolean summary = config.clickTiming();
        if (client.getGameState() != GameState.LOGGED_IN || (!bar && !summary) || ticks.getCount() <= 0) {
            return null;
        }
        int width = config.tickBarWidth();
        int y = 0;
        if (bar) {
            long since = System.currentTimeMillis() - ticks.getLastTickMillis();
            double progress = Math.min(1, since / (double) BradsMultiToolTickBoxOverlay.TICK_MILLIS);
            graphics.setColor(BACKGROUND);
            graphics.fillRect(0, 0, width, HEIGHT);
            Color fill = BradsMultiToolTickBoxOverlay.tickColor(config, ticks.getCount());
            graphics.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), Math.min(fill.getAlpha(), 150)));
            graphics.fillRect(0, 0, (int) (width * progress), HEIGHT);
            if (ticks.timingClicks()) {
                // On top of the fill so they always show: the ideal zone (green, edged), and from Late After on,
                // clicks count as late (orange)
                int idealFrom = x(width, config.clickIdealFrom());
                int idealTo = x(width, Math.min(config.clickIdealTo(), config.clickLateMs()));
                if (idealTo > idealFrom) {
                    graphics.setColor(IDEAL_ZONE);
                    graphics.fillRect(idealFrom, 0, idealTo - idealFrom, HEIGHT);
                    graphics.setColor(HudColors.GOOD);
                    graphics.drawRect(idealFrom, 0, idealTo - idealFrom - 1, HEIGHT - 1);
                }
                int lateX = x(width, config.clickLateMs());
                graphics.setColor(LATE_ZONE);
                graphics.fillRect(lateX, 0, width - lateX, HEIGHT);
            }
            if (ticks.timingClicks() && ticks.getLastClickMillis() >= ticks.getLastTickMillis()) {
                int clickX = x(width, ticks.getLastClickOffset());
                graphics.setColor(ticks.clickColor(ticks.getLastClickOffset()));
                graphics.fillRect(Math.min(width - 2, clickX), 0, 2, HEIGHT);
            }
            graphics.setColor(Color.BLACK);
            graphics.drawRect(0, 0, width - 1, HEIGHT - 1);
            y = HEIGHT;
        }
        if (summary) {
            String text = summary();
            graphics.setFont(FontManager.getRunescapeSmallFont());
            FontMetrics metrics = graphics.getFontMetrics();
            width = Math.max(width, metrics.stringWidth(text) + 4);
            y += metrics.getHeight();
            graphics.setColor(Color.BLACK);
            graphics.drawString(text, 3, y - metrics.getDescent() + 1);
            graphics.setColor(Color.WHITE);
            graphics.drawString(text, 2, y - metrics.getDescent());
        }
        return new Dimension(width, y);
    }

    /** Where this many ms into the tick is on a bar this wide. */
    private static int x(int width, int millis) {
        return (int) (width * Math.min(1, millis / (double) BradsMultiToolTickBoxOverlay.TICK_MILLIS));
    }

    /** "Last 50: 44 on time (30 ideal), 4 late, 2 missed, avg 310ms" */
    private String summary() {
        int onTime = 0;
        int ideal = 0;
        int late = 0;
        int missed = 0;
        long total = 0;
        for (int offset : ticks.recent()) {
            if (offset < 0) {
                missed++;
            } else {
                total += offset;
                if (ticks.isLate(offset)) {
                    late++;
                } else {
                    onTime++;
                }
                if (ticks.isIdeal(offset)) {
                    ideal++;
                }
            }
        }
        int clicks = onTime + late;
        if (clicks + missed == 0) {
            return "Click timing: waiting for a click";
        }
        StringBuilder text = new StringBuilder("Last ").append(clicks + missed).append(": ")
            .append(onTime).append(" on time (").append(ideal).append(" ideal), ").append(late).append(" late");
        if (config.clickEveryTick()) {
            text.append(", ").append(missed).append(" missed");
        }
        if (clicks > 0) {
            text.append(", avg ").append(total / clicks).append("ms");
        }
        return text.toString();
    }
}
