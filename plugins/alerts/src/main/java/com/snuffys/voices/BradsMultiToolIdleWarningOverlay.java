package com.snuffys.voices;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class BradsMultiToolIdleWarningOverlay extends Overlay {

    private final Client client;
    private final IdleWarnings idleWarnings;
    private final PanelComponent panelComponent = new PanelComponent();

    @Inject
    public BradsMultiToolIdleWarningOverlay(Client client, IdleWarnings idleWarnings) {
        this.client = client;
        this.idleWarnings = idleWarnings;
        setPosition(OverlayPosition.TOP_CENTER);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
        setPriority(PRIORITY_HIGH);
        panelComponent.setPreferredSize(new Dimension(170, 0));
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (client.getGameState() != GameState.LOGGED_IN || !idleWarnings.isLogoutImminent()) {
            return null;
        }

        boolean flash = (System.currentTimeMillis() / 400) % 2 == 0;
        panelComponent.getChildren().clear();
        panelComponent.setBackgroundColor(flash ? new Color(120, 0, 0, 200) : new Color(40, 0, 0, 200));

        panelComponent.getChildren().add(TitleComponent.builder()
            .text("IDLE LOGOUT WARNING")
            .color(Color.WHITE)
            .build());

        int remaining = idleWarnings.getSecondsUntilIdleLogout();
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Logout in:")
            .right(String.format("%d:%02d", remaining / 60, remaining % 60))
            .build());

        return panelComponent.render(graphics);
    }
}
