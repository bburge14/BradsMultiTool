package com.snuffys.planner;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

/**
 * Snuffy's Planner: its sidebar tab (TODAY, WEEKLY, TIMERS, SLAYER, DROPS, NOTES & TO-DO) and what fills it: the
 * dailies and weeklies, the farming patch list and ready alert, bird houses and your own timers, the Slayer task,
 * the drop tracker and your notes.
 */
@Singleton
class PlannerFeature {

    @Inject private FarmingData farmingData;
    @Inject private FarmingTracker farmingTracker;
    @Inject private BirdhouseTracker birdhouseTracker;
    @Inject private PlannerChores chores;
    @Inject private PlannerTimers timers;
    @Inject private PlannerSlayer slayer;
    @Inject private DropTracker drops;
    @Inject private PlannerNotes notes;
    @Inject private PlannerView view;
    @Inject private ClientToolbar clientToolbar;
    @Inject private ConfigDefaults configDefaults;
    @Inject private PlannerConfig config;
    @Inject private ConfigManager configManager;
    @Inject private PluginIdentity identity;

    private NavigationButton navButton;
    private PlannerPanel panel;

    void start(Lifecycle lifecycle) {
        configDefaults.add(() -> PlannerConfigDefaults.store(configDefaults, config));
        lifecycle.load(farmingData, farmingData::load);
        // (BirdhouseTracker notes when you seed a bird house, for the TIMERS line and its alert; the Skilling
        // Helper's hover card uses it too, and in the Toolbelt it's subscribed once)
        lifecycle.subscribe(farmingTracker, birdhouseTracker, chores, timers, slayer, drops, this);
        chores.start();
        SwingUtilities.invokeLater(() -> {
            panel = new PlannerPanel(config, configManager, chores, timers, farmingTracker, slayer, drops, notes);
            navButton = NavigationButton.builder()
                .tooltip("Snuffy's Planner")
                // The Planner's logo, made by update_icons.py
                .icon(ImageUtil.loadImageResource(PlannerFeature.class, "icons/planner.png"))
                .priority(6)
                .panel(panel)
                .build();
            clientToolbar.addNavigation(navButton);
            view.setPanel(panel::refresh);
        });
        lifecycle.onStop(() -> {
            view.setPanel(null);
            SwingUtilities.invokeLater(() -> {
                if (navButton != null) {
                    clientToolbar.removeNavigation(navButton);
                    navButton = null;
                }
                if (panel != null) {
                    panel.stop();
                    panel = null;
                }
            });
        });
    }

    // A new account or RuneLite profile, or a Planner setting changed: redraw
    @Subscribe
    public void onRuneScapeProfileChanged(RuneScapeProfileChanged event) {
        view.update();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (identity.configGroup.equals(event.getGroup())) {
            view.update();
        }
    }
}
