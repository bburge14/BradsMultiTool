package com.bradsmultitool;

import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

/**
 * Snuffy's Planner: its sidebar tab (Today, Timers, Slayer, Drops, Notes and Calculator tabs) and what fills it: the
 * dailies and weeklies, the farming patch list and ready alert, bird houses and your own timers, the Slayer task,
 * the drop tracker, your notes and the skill calculator.
 */
@Singleton
class PlannerFeature {

    @Inject private FarmingData farmingData;
    @Inject private FarmingTracker farmingTracker;
    @Inject private BirdhouseTracker birdhouseTracker;
    @Inject private PlannerChores chores;
    @Inject private PlannerTimers timers;
    @Inject private PlannerSound plannerSound;
    @Inject private PlannerSlayer slayer;
    @Inject private DropTracker drops;
    @Inject private PlannerNotes notes;
    @Inject private PlannerView view;
    @Inject private ClientToolbar clientToolbar;
    @Inject private ConfigDefaults configDefaults;
    @Inject private PlannerConfig config;
    @Inject private ConfigManager configManager;
    @Inject private PluginIdentity identity;
    @Inject private ItemManager itemManager;
    @Inject private ItemIcons itemIcons;
    @Inject private SkillIconManager skillIcons;
    @Inject private SpriteManager spriteManager;
    @Inject private ItemPrices itemPrices;
    @Inject private PlannerCalculator calculator;
    @Inject private CalculatorData calculatorData;
    @Inject private RecipeData recipeData;
    @Inject private TrainingData trainingData;
    @Inject private LocationData locationData;
    // Your quests, for the places you can reach (read on the client thread, every few minutes)
    @Inject private QuestProgress questProgress;
    @Inject private PlannerPlanOverlay planOverlay;
    @Inject private PlannerGeLimits geLimits;
    // Where your items came from (bought, traded, got yourself), for the plans' money
    @Inject private PlannerOrigins origins;

    private NavigationButton navButton;
    private PlannerPanel panel;

    // The dailies and weeklies lines started off (they went in the chat box); now they go to the split private chat,
    // which fades, and are on: turn them on once for players who already had them saved off
    private void turnOnReminderChat() {
        if (configManager.getConfiguration(identity.configGroup, "reminderChatTurnedOn") == null) {
            configManager.setConfiguration(identity.configGroup, "reminderChatTurnedOn", true);
            configManager.setConfiguration(identity.configGroup, "chatDailies", true);
            configManager.setConfiguration(identity.configGroup, "chatWeeklies", true);
        }
    }

    void start(Lifecycle lifecycle) {
        configDefaults.add(() -> {
            turnOnReminderChat();
            PlannerConfigDefaults.store(configDefaults, config);
        });
        lifecycle.load(farmingData, farmingData::load);
        // The Calculator's data: its actions and bonuses, and the recipes and training guides for costs and times
        lifecycle.load(calculatorData, calculatorData::load);
        lifecycle.load(recipeData, recipeData::load);
        lifecycle.load(trainingData, trainingData::load);
        lifecycle.load(locationData, locationData::load);
        // (BirdhouseTracker notes when you seed a bird house, for the TIMERS line and its alert; the Skilling
        // Helper's hover card uses it too, and in the Toolbelt it's subscribed once)
        lifecycle.subscribe(questProgress, farmingTracker, birdhouseTracker, chores, timers, slayer, drops, plannerSound, calculator, geLimits, origins, this);
        // The plan on the game screen (unless it's linked to a Skilling Helper goal: its pinned card shows the plan then)
        lifecycle.overlay(planOverlay);
        // What you sell, alch and drop counts on your plan's milestones, as Record sales... does by hand
        origins.onGone(calculator::recordGone);
        chores.start();
        SwingUtilities.invokeLater(() -> {
            PlannerPanel.Icons pictures = icons();
            panel = new PlannerPanel(config, configManager, chores, timers, farmingTracker, slayer, drops, notes, geLimits, pictures,
                new PlannerCalculatorView(calculator, calculatorData, recipeData, trainingData, locationData, questProgress, pictures));
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
            origins.onGone(null);
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

    // The sidebar's pictures: the game's item sprites (loaded on the client thread, filled in when ready) and skill icons
    private PlannerPanel.Icons icons() {
        return new PlannerPanel.Icons() {
            @Override
            public BufferedImage item(int itemId, int quantity, boolean showQuantity) {
                return itemManager.getImage(itemId, quantity, showQuantity);
            }

            @Override
            public int itemId(String name) {
                return itemIcons.id(name);
            }

            @Override
            public BufferedImage skill(Skill skill, boolean small) {
                return skillIcons.getSkillImage(skill, small);
            }

            @Override
            public void sprite(int spriteId, java.util.function.Consumer<BufferedImage> then) {
                // (loaded on the client thread; drawn on the Swing thread)
                spriteManager.getSpriteAsync(spriteId, 0, image -> SwingUtilities.invokeLater(() -> then.accept(image)));
            }

            @Override
            public int price(String name) {
                return itemPrices.price(name);
            }
        };
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
