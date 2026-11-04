package com.bradsmultitool;

import com.google.gson.Gson;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.laf.RuneLiteLAF;

/**
 * Renders Snuffy's Planner's sidebar offline, with sample data in each tab, to check its layout:
 * {@code ./gradlew plannerPreview -Dout=planner} writes planner-today.png, planner-timers.png, planner-slayer.png,
 * planner-drops.png (the list), planner-drops-monster.png (Vorkath's page), planner-drops-session.png (its items this
 * session), planner-drops-casket.png (a source saved before drop rates were kept), planner-drops-scrolled.png,
 * planner-notes.png, planner-calculator.png, planner-empty.png (each tab of a new account) and planner-loggedout.png,
 * at 2x. {@code ./gradlew calculatorPreview -Dout=calc} writes only the Calculator's: calc-woodcutting.png (with a goal
 * and a bonus), calc-cooking.png (costs, at the sidebar's height), calc-cooking-banked.png (Banked view),
 * calc-prayer.png (an altar's bonus, searched), calc-magic.png, calc-plan.png (a plan with four milestones and its
 * totals), calc-fishing.png (what the catch is worth), calc-free-world.png (members-only actions hidden) and
 * calc-plan-alone.png (the plan without the Skilling Helper installed).
 * Item pictures come from the OSRS Wiki (cached in build/planner-icons; without a connection they're left out).
 */
public class PlannerPreview {

    private static final int SCALE = 2;
    private static final int WIDTH = PluginPanel.PANEL_WIDTH + PluginPanel.SCROLLBAR_WIDTH;
    private static final String GROUP = PlannerChores.GROUP;

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "build/planner";
        boolean calculatorOnly = args.length > 1 && "calculator".equals(args[1]);
        File icons = new File(System.getProperty("icons", "build/planner-icons"));
        icons.mkdirs();
        SwingUtilities.invokeAndWait(RuneLiteLAF::setup);

        Sample full = new Sample("preview");
        full.fill();
        Sample fresh = new Sample("fresh");
        WikiIcons pictures = new WikiIcons(icons);
        String[] tabs = {"today", "timers", "slayer", "drops", "notes", "calculator"};
        if (calculatorOnly) {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    PlannerPanel panel = full.panel(pictures);
                    panel.showCalculator(Skill.WOODCUTTING, PlannerCalculator.Mode.ACTIONS, "");
                    write(render(panel, 0), out + "-woodcutting.png");
                    panel.showCalculator(Skill.COOKING, PlannerCalculator.Mode.ACTIONS, "");
                    write(render(panel, 760), out + "-cooking.png");
                    panel.showCalculator(Skill.COOKING, PlannerCalculator.Mode.BANKED, "");
                    write(render(panel, 0), out + "-cooking-banked.png");
                    panel.showCalculator(Skill.PRAYER, PlannerCalculator.Mode.ACTIONS, "dragon");
                    write(render(panel, 0), out + "-prayer.png");
                    panel.showCalculator(Skill.MAGIC, PlannerCalculator.Mode.ACTIONS, "alchemy");
                    write(render(panel, 0), out + "-magic.png");
                    // A Woodcutting plan with four milestones and its totals, exported to the tracker
                    panel.showCalculator(Skill.WOODCUTTING, PlannerCalculator.Mode.PLAN, "");
                    write(render(panel, 0), out + "-plan.png");
                    // Choosing a better method for its third milestone: the best picks first
                    panel.showCalculatorPicking(Skill.WOODCUTTING, 2);
                    write(render(panel, 760), out + "-plan-pick.png");
                    // The Cooking plan with its Shark milestone opened to its breakdown
                    panel.showCalculatorBreakdown(Skill.COOKING, 1);
                    write(render(panel, 0), out + "-plan-cooking.png");
                    // Fishing's Plan view: the raw sharks the Cooking plan gets from it
                    panel.showCalculator(Skill.FISHING, PlannerCalculator.Mode.PLAN, "");
                    write(render(panel, 0), out + "-supplies.png");
                    // Fishing's prices (what the catch is worth) at the sidebar's height
                    panel.showCalculator(Skill.FISHING, PlannerCalculator.Mode.ACTIONS, "");
                    write(render(panel, 760), out + "-fishing.png");
                    // On a free world: members-only actions left out, with a note
                    full.calculator.setSampleWorld(false, true);
                    panel.showCalculator(Skill.COOKING, PlannerCalculator.Mode.ACTIONS, "");
                    write(render(panel, 760), out + "-free-world.png");
                    // Without the Skilling Helper: the plan can't be exported
                    full.calculator.setSampleWorld(true, false);
                    panel.showCalculator(Skill.WOODCUTTING, PlannerCalculator.Mode.PLAN, "");
                    write(render(panel, 0), out + "-plan-alone.png");
                    // The plan overlay (no Skilling Helper): training Woodcutting at 61, full and compact
                    full.calculator.setSampleTrained(Skill.WOODCUTTING);
                    write(planOverlay(full, false), out + "-plan-overlay.png");
                    full.configManager.setConfiguration("snuffysplanner", "compactOverlays", true);
                    write(planOverlay(full, true), out + "-plan-overlay-compact.png");
                    full.configManager.unsetConfiguration("snuffysplanner", "compactOverlays");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            System.exit(0);
        }
        SwingUtilities.invokeAndWait(() -> {
            try {
                PlannerPanel panel = full.panel(pictures);
                for (String tab : tabs) {
                    if (tab.equals("calculator")) {
                        panel.showCalculator(Skill.WOODCUTTING, PlannerCalculator.Mode.ACTIONS, "");
                    } else {
                        panel.showTab(tab);
                    }
                    write(render(panel, 0), out + "-" + tab + ".png");
                }
                // A monster's own page, its items this session, and a casket saved before drop rates were kept
                panel.showDropSource("Vorkath");
                write(render(panel, 0), out + "-drops-monster.png");
                panel.showDropSessionItems();
                write(render(panel, 0), out + "-drops-session.png");
                panel.showDropSource("Reward casket (hard)");
                write(render(panel, 0), out + "-drops-casket.png");
                // The sidebar's usual height: the list scrolls
                panel.showDropSource(null);
                write(render(panel, 560), out + "-drops-scrolled.png");

                PlannerPanel empty = fresh.panel(pictures);
                BufferedImage[] each = new BufferedImage[tabs.length];
                int height = 0;
                for (int i = 0; i < tabs.length; i++) {
                    empty.showTab(tabs[i]);
                    each[i] = render(empty, 0);
                    height = Math.max(height, each[i].getHeight());
                }
                BufferedImage row = new BufferedImage(each.length * (WIDTH + 8) * SCALE, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = row.createGraphics();
                g.setColor(new Color(20, 20, 20));
                g.fillRect(0, 0, row.getWidth(), row.getHeight());
                for (int i = 0; i < each.length; i++) {
                    g.drawImage(each[i], i * (WIDTH + 8) * SCALE, 0, null);
                }
                g.dispose();
                write(row, out + "-empty.png");

                fresh.loggedIn(false);
                empty.refresh();
                write(render(empty, 300), out + "-loggedout.png");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        System.exit(0);
    }

    // The Planner's plan overlay drawn on a game-coloured background, with a client that's logged in at the sample's XP
    private static BufferedImage planOverlay(Sample sample, boolean compact) {
        net.runelite.api.Client client = (net.runelite.api.Client) java.lang.reflect.Proxy.newProxyInstance(
            PlannerPreview.class.getClassLoader(), new Class<?>[]{net.runelite.api.Client.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getGameState":
                        return net.runelite.api.GameState.LOGGED_IN;
                    case "getSkillExperience":
                        return sample.calculator.xp((Skill) args[0]);
                    default:
                        Class<?> type = method.getReturnType();
                        return type == boolean.class ? false : type == int.class ? 0 : type == long.class ? 0L : null;
                }
            });
        PlannerPlanOverlay overlay = new PlannerPlanOverlay(client, sample.config, sample.calculator, sample.calculatorData,
            sample.recipes, sample.training);
        BufferedImage image = new BufferedImage(420, compact ? 140 : 260, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = image.createGraphics();
        g.scale(2, 2);
        g.setColor(new java.awt.Color(90, 80, 60));
        g.fillRect(0, 0, 210, 130);
        g.translate(5, 5);
        overlay.render(g);
        g.dispose();
        return image;
    }

    private static void write(BufferedImage image, String path) throws java.io.IOException {
        ImageIO.write(image, "png", new File(path));
        System.out.println("Wrote " + path);
    }

    // At the sidebar's width; height 0 means tall enough for everything
    private static BufferedImage render(PluginPanel panel, int height) {
        panel.setSize(WIDTH, height > 0 ? height : 2000);
        layout(panel);
        layout(panel);
        if (height <= 0) {
            height = Math.max(240, panel.getPreferredSize().height + 2);
            panel.setSize(WIDTH, height);
            layout(panel);
            layout(panel);
        }
        BufferedImage out = new BufferedImage(WIDTH * SCALE, height * SCALE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.scale(SCALE, SCALE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        panel.printAll(g);
        g.dispose();
        return out;
    }

    private static void layout(Component c) {
        if (c instanceof Container) {
            c.invalidate();
            ((Container) c).doLayout();
            for (Component child : ((Container) c).getComponents()) {
                layout(child);
            }
        }
    }

    /** An account's saved Planner data, written in the trackers' own formats. */
    private static final class Sample {
        final ConfigManager configManager;
        final PlannerConfig config;
        final PlannerView view = new PlannerView();
        final Gson gson = new Gson();
        final FarmingData farmingData;
        final PlannerChores chores;
        final PlannerTimers timers;
        final FarmingTracker farming;
        final PlannerSlayer slayer;
        final DropTracker drops;
        final PlannerNotes notes;
        final PlannerCalculator calculator;
        final PlannerGeLimits geLimits;
        final CalculatorData calculatorData = new CalculatorData();
        final RecipeData recipes = new RecipeData();
        final TrainingData training = new TrainingData();
        final String profile;

        Sample(String profile) throws Exception {
            this.profile = profile;
            configManager = SettingsPanelTest.freshConfigManager(new EventBus());
            // Where the account's (RuneScape profile's) settings go
            java.lang.reflect.Constructor<?> data = Class.forName("net.runelite.client.config.ConfigData").getDeclaredConstructor(File.class);
            data.setAccessible(true);
            File file = File.createTempFile("snuffys-planner-preview", ".properties");
            file.deleteOnExit();
            Field rsData = ConfigManager.class.getDeclaredField("rsProfileConfigProfile");
            rsData.setAccessible(true);
            rsData.set(configManager, data.newInstance(file));
            loggedIn(true);
            config = configManager.getConfig(PlannerConfig.class);
            farmingData = new FarmingData(null, configManager);
            farmingData.load(gson);
            chores = new PlannerChores(null, configManager, config, null, view, gson);
            timers = new PlannerTimers(configManager, config, null, new BirdhouseTracker(null, configManager), view, gson);
            farming = new FarmingTracker(null, configManager, farmingData, config, null, view);
            slayer = new PlannerSlayer(null, configManager, null, view);
            drops = new DropTracker(configManager, null, config, view, gson);
            notes = new PlannerNotes(configManager, view, gson);
            geLimits = new PlannerGeLimits(null, configManager, null, view, null, config);
            calculator = new PlannerCalculator(null, configManager, null, view, config, null, null, geLimits);
            calculatorData.load(gson);
            recipes.load(gson);
            training.load(gson);
        }

        void loggedIn(boolean in) {
            try {
                Field key = ConfigManager.class.getDeclaredField("rsProfileKey");
                key.setAccessible(true);
                key.set(configManager, in ? "rsprofile." + profile : null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        PlannerPanel panel(PlannerPanel.Icons icons) {
            if (icons instanceof WikiIcons) {
                ((WikiIcons) icons).add(calculatorData);
            }
            return new PlannerPanel(config, configManager, chores, timers, farming, slayer, drops, notes, geLimits, icons,
                new PlannerCalculatorView(calculator, calculatorData, recipes, training, null, null, icons));
        }

        void set(String key, Object value) {
            configManager.setRSProfileConfiguration(GROUP, key, value);
        }

        void fill() {
            long now = System.currentTimeMillis() / 1000;
            // GE buy limits: adamant dart tips at their limit (an offer still open), feathers under it but priced low,
            // and mithril bars bought while away
            long ms = now * 1000;
            int tips = net.runelite.api.gameval.ItemID.ADAMANT_DART_TIP;
            int feather = net.runelite.api.gameval.ItemID.FEATHER;
            int bars = net.runelite.api.gameval.ItemID.MITHRIL_BAR;
            java.util.Map<Integer, String> geNames = new java.util.HashMap<>();
            geNames.put(tips, "Adamant dart tip");
            geNames.put(feather, "Feather");
            geNames.put(bars, "Mithril bar");
            java.util.Map<Integer, Integer> geLimitsOf = new java.util.HashMap<>();
            geLimitsOf.put(tips, 7000);
            geLimitsOf.put(feather, 30000);
            geLimitsOf.put(bars, 10000);
            java.util.Map<Integer, Long> gePrices = new java.util.HashMap<>();
            gePrices.put(tips, 182L);
            gePrices.put(feather, 3L);
            gePrices.put(bars, 410L);
            PlannerGeLimits.Slot[] geSlots = new PlannerGeLimits.Slot[8];
            geSlots[0] = new PlannerGeLimits.Slot(tips, 7000, 20000, 185, net.runelite.api.GrandExchangeOfferState.BUYING);
            geSlots[1] = new PlannerGeLimits.Slot(feather, 12000, 40000, 2, net.runelite.api.GrandExchangeOfferState.BUYING);
            geLimits.setSample(java.util.Arrays.asList(
                new PlannerGeLimits.Buy(tips, 4000, ms - 72 * 60000L, true),
                new PlannerGeLimits.Buy(tips, 3000, ms - 40 * 60000L, true),
                new PlannerGeLimits.Buy(feather, 12000, ms - 15 * 60000L, true),
                new PlannerGeLimits.Buy(bars, 2500, ms - 130 * 60000L, false)), geSlots, geNames, geLimitsOf, gePrices);
            // TODAY: dailies and weeklies of your own (from the preset tasks, with their pictures), one ticked
            chores.add("Battlestaves", false, "Zaff in Varrock (Varrock easy diary)", net.runelite.api.gameval.ItemID.BATTLESTAFF);
            chores.add("Herb run", false, "Catherby, Ardougne, Falador and the Farming Guild",
                net.runelite.api.gameval.ItemID.UNIDENTIFIED_RANARR);
            String herbRun = chores.rows(false).get(chores.rows(false).size() - 1).id;
            chores.setDone(herbRun, true);
            chores.add("Buckets of sand", false, "Bert in Yanille", net.runelite.api.gameval.ItemID.BUCKET_SAND);
            chores.add("Tears of Guthix", true, "Juna in the Lumbridge Swamp caves", net.runelite.api.gameval.ItemID.TOG_BOWL);
            chores.add("Penguin hunting", true, "Spy on the penguins for Larry", net.runelite.api.gameval.ItemID.PENGUIN_MASK);

            // TIMERS: patches as last seen, bird houses seeded 20 minutes ago, three of your own timers
            patch("farmPatch.11061.4774", "Catherby: Herb patch", "Ranarr", FarmingData.GROWING, now + 25 * 60);
            patch("farmPatch.4922.4774", "Farming Guild: Herb patch", "Avantoe", FarmingData.GROWING, now + 52 * 60);
            patch("farmPatch.12083.4771", "Falador: Allotment (North)", "Watermelon", FarmingData.HARVESTABLE, 0);
            patch("farmPatch.9781.4771", "Tree Gnome Stronghold: Fruit tree patch", "Palm", FarmingData.GROWING, now + 9 * 3600);
            patch("farmPatch.13878.4772", "Port Phasmatys: Herb patch", "Snapdragon", FarmingData.DISEASED, now + 40 * 60);
            for (int spot = 0; spot < 4; spot++) {
                set("birdhouse." + spot, now - 20 * 60);
            }
            set("planner.timers", "{\"timers\":["
                + "{\"id\":\"t1\",\"name\":\"Tithe Farm\",\"seconds\":3000,\"endsAt\":" + (now + 1234) + ",\"alerted\":false},"
                + "{\"id\":\"t2\",\"name\":\"Hespori\",\"seconds\":5400,\"endsAt\":0,\"alerted\":false},"
                + "{\"id\":\"t3\",\"name\":\"Battlestaff restock\",\"seconds\":600,\"endsAt\":" + (now - 30) + ",\"alerted\":true}]}");

            // SLAYER
            set("planner.slayer", new PlannerSlayer.Task("Abyssal demons", 87, 150, null, 1240, 42).encode());

            // DROPS: earlier kills as saved (the casket in the format from before drop rates), then this session's
            DropTracker.Saved saved = new DropTracker.Saved();
            DropTracker.Source vorkath = source(saved, "Vorkath", "kill", 42, 3, true, now - 2 * PlannerChores.DAY,
                "Superior dragon bones", 84, 1134000, 42, "Blue dragonhide", 700, 1214000, 28, "Dragonbone necklace", 1, 2610000, 1,
                "Runite ore", 60, 660000, 6, "Wrath rune", 1500, 450000, 10, "Dragon bolts (unf)", 300, 840000, 4,
                "Coins", 41000, 41000, 11, "Vorkath's head", 1, 0, 1);
            vorkath.best = best("Dragonbone necklace", 1, 2610000, now - 2 * PlannerChores.DAY);
            DropTracker.Source demon = source(saved, "Abyssal demon", "kill", 210, 37, true, now - 3600,
                "Abyssal whip", 1, 1450000, 1, "Law rune", 450, 63000, 45, "Rune med helm", 2, 38700, 2, "Coins", 52000, 52000, 96,
                "Pure essence", 1650, 26400, 33, "Ensouled abyssal head", 3, 0, 3);
            demon.best = best("Abyssal whip", 1, 1450000, now - 4 * 3600);
            DropTracker.Source casket = new DropTracker.Source();
            casket.count = 3;
            casket.items.put("Rune platelegs", new long[]{1, 38200});
            casket.items.put("Purple sweets", new long[]{18, 9000});
            casket.items.put("Coins", new long[]{84000, 84000});
            casket.value = 131200;
            saved.sources.put("Reward casket (hard)", casket);
            saved.recent.add(recent("Abyssal demon", "Abyssal whip", 1, 1450000, now - 4 * 3600));
            saved.recent.add(0, recent("Abyssal demon", "Law rune", 15, 2100, now - 4 * 3600));
            set("planner.drops", gson.toJson(saved));
            kill("Vorkath", now - 900, "Superior dragon bones", 2, 27000, "Blue dragonhide", 25, 43000);
            kill("Vorkath", now - 500, "Superior dragon bones", 2, 27000, "Runite ore", 5, 55000);
            kill("Vorkath", now - 60, "Superior dragon bones", 2, 27000, "Dragon bolts (unf)", 30, 84000, "Wrath rune", 40, 12000);

            // NOTES
            notes.add("Buy 2,000 cannonballs");
            notes.add("Finish the Kandarin hard diary: the last two tasks are in the Seers' Village bank");
            notes.add("Charge the Trident of the swamp");
            notes.add("Hand in the Farming contract");
            notes.setDone(3, true);
            notes.setNotes("Spec weapon swap: DWH on Vorkath, then the Arclight.\n\nWorld 330 for the blast furnace.");

            // CALCULATOR: the account's levels, a Woodcutting goal (level 70, set at level 58) and a bonus ticked, an
            // altar for Prayer, and what's in the bank and inventory
            Map<Skill, Integer> xp = new java.util.EnumMap<>(Skill.class);
            for (Skill skill : Skill.values()) {
                xp.put(skill, net.runelite.api.Experience.getXpForLevel(50) + 2_000);
            }
            xp.put(Skill.WOODCUTTING, 302_288);
            xp.put(Skill.COOKING, 1_210_421);
            xp.put(Skill.PRAYER, 737_627);
            xp.put(Skill.MAGIC, 1_986_068);
            set("goal.WOODCUTTING", net.runelite.api.Experience.getXpForLevel(70));
            set("goalStart.WOODCUTTING", net.runelite.api.Experience.getXpForLevel(58));
            calculator.setBonuses(Skill.WOODCUTTING, java.util.Collections.singleton("LUMBERJACK_OUTFIT"));
            calculator.setBonuses(Skill.PRAYER, java.util.Collections.singleton("LIT_GILDED_ALTAR"));
            Map<String, Integer> bank = new HashMap<>();
            bank.put("coins", 18_500_000);
            // The Cooking plan's budget, and trades since: raw sharks bought, cooked ones sold
            calculator.setPlanBudget(Skill.COOKING, 20_000_000);
            // The Cooking plan's raw sharks caught yourself (a supply in the Fishing plan view)
            calculator.setGathers(Skill.COOKING, "Raw shark", true);
            long soon = System.currentTimeMillis() + 1000;
            geLimits.addSampleTrade(net.runelite.api.gameval.ItemID.RAW_SHARK, "Raw shark", 2000, 1_480_000, soon, false);
            geLimits.addSampleTrade(net.runelite.api.gameval.ItemID.SHARK, "Shark", 1500, 1_170_000, soon, true);
            bank.put("raw lobster", 1_240);
            bank.put("raw swordfish", 455);
            bank.put("raw monkfish", 310);
            bank.put("raw shark", 96);
            bank.put("raw karambwan", 620);
            bank.put("dragon bones", 140);
            bank.put("teak logs", 220);
            Map<String, Integer> inventory = new HashMap<>();
            inventory.put("raw lobster", 28);
            calculator.setSample(xp, inventory, bank);
            calculator.setSampleWorld(true, true);
            // A Woodcutting plan from level 61 (to 99): four milestones, exported to the tracker
            SkillPlans.Plan plan = new SkillPlans.Plan(99);
            plan.steps.add(new SkillPlans.Step("Teak logs", 35, 65, "Teak trees", 85));
            plan.steps.add(new SkillPlans.Step("Yew logs", 65, 75, null, 175));
            plan.steps.add(new SkillPlans.Step("Magic logs", 75, 90, null, 250));
            plan.steps.add(new SkillPlans.Step("Redwood logs", 90, 99, null, 380));
            plan.name = "Redwoods to 99";
            // A second Woodcutting plan, not exported
            SkillPlans.Plan afk = new SkillPlans.Plan(90);
            afk.name = "AFK teaks";
            afk.steps.add(new SkillPlans.Step("Teak logs", 35, 90, "Teak trees", 85));
            SkillPlans.savePlans(configManager, Skill.WOODCUTTING, java.util.Arrays.asList(plan, afk), 0);
            SkillPlans.export(configManager, Skill.WOODCUTTING, plan, 302_288);
            // A Cooking plan (not exported): costs and earnings
            SkillPlans.Plan cooking = new SkillPlans.Plan(90);
            cooking.steps.add(new SkillPlans.Step("Lobster", 75, 80, null, 120));
            cooking.steps.add(new SkillPlans.Step("Shark", 80, 85, null, 210));
            cooking.steps.add(new SkillPlans.Step("Manta ray", 91, 92, null, 216.3));
            cooking.steps.get(2).from = 85;
            cooking.steps.get(2).to = 90;
            SkillPlans.savePlans(configManager, Skill.COOKING, java.util.Collections.singletonList(cooking), 0);
        }

        private void patch(String key, String title, String crop, String state, long grownAt) {
            set(key, new FarmingTracker.Record(key, title, crop, state, grownAt, false, 2, 0).encode());
        }


        // A source as saved: its kills, kills since a valuable drop, when it last dropped, and items as
        // name, quantity, value, times dropped
        private static DropTracker.Source source(DropTracker.Saved saved, String name, String kind, int count, int dry,
                                                 boolean valuableSeen, long last, Object... items) {
            DropTracker.Source source = new DropTracker.Source();
            source.count = count;
            source.rated = count;
            source.kind = kind;
            source.dry = dry;
            source.valuableSeen = valuableSeen;
            source.last = last;
            for (int i = 0; i < items.length; i += 4) {
                long value = ((Number) items[i + 2]).longValue();
                source.items.put((String) items[i], new long[]{((Number) items[i + 1]).longValue(), value,
                    ((Number) items[i + 3]).longValue(), 0});
                source.value += value;
            }
            saved.sources.put(name, source);
            return source;
        }

        private static DropTracker.Drop best(String item, long quantity, long value, long at) {
            return recent(null, item, quantity, value, at);
        }

        private static DropTracker.Drop recent(String source, String item, long quantity, long value, long at) {
            DropTracker.Drop drop = new DropTracker.Drop();
            drop.source = source;
            drop.item = item;
            drop.quantity = quantity;
            drop.value = value;
            drop.at = at;
            return drop;
        }

        // One kill this session (items as name, quantity, value), as the tracker records it
        private void kill(String source, long at, Object... items) {
            java.util.List<DropTracker.Stack> stacks = new java.util.ArrayList<>();
            for (int i = 0; i < items.length; i += 3) {
                stacks.add(new DropTracker.Stack((String) items[i], 0, ((Number) items[i + 1]).longValue(),
                    ((Number) items[i + 2]).longValue()));
            }
            drops.record(source, false, 1, stacks, at);
        }
    }

    /**
     * Item pictures from the OSRS Wiki by item name (what ItemManager draws in the client), centred on the game's
     * 36x32 item canvas with the quantity drawn on top like the game does; skill icons from the client's resources.
     */
    private static final class WikiIcons implements PlannerPanel.Icons {
        private static final Map<Integer, String> NAMES = new HashMap<>();
        private final File cache;
        private final SkillIconManager skills = new SkillIconManager();
        private final Map<String, Integer> ids = new HashMap<>();
        private final Map<Integer, String> byId = new HashMap<>(NAMES);

        static {
            NAMES.put(ItemID.BATTLESTAFF, "Battlestaff");
            NAMES.put(ItemID.NZONE_HERBBOX, "Herb box");
            NAMES.put(ItemID.BLANKRUNE_HIGH, "Pure essence");
            NAMES.put(ItemID.CHAOSRUNE, "Chaos rune");
            NAMES.put(ItemID.BUCKET_SAND, "Bucket of sand");
            NAMES.put(ItemID.BOW_STRING, "Bow string");
            NAMES.put(ItemID.BUCKET_ECTOPLASM, "Bucket of slime");
            NAMES.put(ItemID.LOVAKENGJ_DYNAMITE_FUSED, "Dynamite");
            NAMES.put(ItemID.OGRE_ARROW, "Ogre arrow");
            NAMES.put(ItemID.TOG_BOWL, "Stone bowl");
            NAMES.put(ItemID.BIRDHOUSE_NORMAL, "Bird house");
            NAMES.put(ItemID.TRAIL_REWARD_CASKET_HARD, "Reward casket (hard)");
        }

        WikiIcons(File cache) {
            this.cache = cache;
            // The tradeable names the sample uses (as the GE search finds them in the client)
            String[] tradeable = {"Grimy ranarr weed", "Avantoe", "Watermelon", "Palm tree seed", "Snapdragon",
                "Superior dragon bones", "Dragonbone necklace", "Runite ore", "Wrath rune", "Blue dragonhide",
                "Abyssal whip", "Law rune", "Rune med helm", "Pure essence", "Ensouled abyssal head", "Rune platelegs",
                "Purple sweets", "Dragon bolts (unf)", "Vorkath's head"};
            int next = 900_000;
            for (String name : tradeable) {
                ids.put(name.toLowerCase(), next);
                byId.put(next++, name);
            }
        }

        @Override
        public BufferedImage item(int itemId, int quantity, boolean showQuantity) {
            String name = itemId == ItemID.COINS ? coins(quantity) : byId.get(itemId);
            BufferedImage picture = name == null ? null : load(name);
            if (picture == null) {
                return null;
            }
            BufferedImage out = new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.drawImage(picture, (36 - picture.getWidth()) / 2, (32 - picture.getHeight()) / 2 + (showQuantity ? 2 : 0), null);
            if (showQuantity) {
                Font font = FontManager.getRunescapeSmallFont();
                g.setFont(font);
                String text = quantity >= 10_000_000 ? quantity / 1_000_000 + "M" : quantity >= 100_000 ? quantity / 1000 + "K"
                    : String.valueOf(quantity);
                g.setColor(Color.BLACK);
                g.drawString(text, 1, 11);
                g.setColor(quantity >= 10_000_000 ? new Color(0, 255, 128) : quantity >= 100_000 ? Color.WHITE : Color.YELLOW);
                g.drawString(text, 0, 10);
            }
            g.dispose();
            return out;
        }

        private static String coins(int quantity) {
            int[] steps = {10000, 1000, 250, 100, 25, 5, 4, 3, 2};
            for (int step : steps) {
                if (quantity >= step) {
                    return "Coins " + step;
                }
            }
            return "Coins 1";
        }

        // GE prices for the sample's items (in the client these come from ItemManager)
        private static final Map<String, Integer> PRICES = new HashMap<>();

        static {
            String[] prices = {"raw lobster", "182", "lobster", "176", "raw swordfish", "330", "swordfish", "305",
                "raw monkfish", "240", "monkfish", "268", "raw shark", "760", "shark", "780", "raw karambwan", "410",
                "cooked karambwan", "365", "raw tuna", "60", "tuna", "92", "raw anglerfish", "1580", "anglerfish", "1620",
                "raw trout", "34", "trout", "28", "raw salmon", "52", "salmon", "41", "dragon bones", "2280",
                "superior dragon bones", "13500", "babydragon bones", "1740", "nature rune", "95", "fire rune", "5",
                "teak logs", "64", "yew logs", "262", "magic logs", "1012", "redwood logs", "364", "maple logs", "18", "willow logs", "24", "oak logs", "58", "logs", "48", "mahogany logs", "352", "raw shark", "760", "raw monkfish", "240", "raw manta ray", "1500", "manta ray", "1460", "raw sea turtle", "1080", "sea turtle", "1050"};
            for (int i = 0; i < prices.length; i += 2) {
                PRICES.put(prices[i], Integer.parseInt(prices[i + 1]));
            }
        }

        @Override
        public int price(String name) {
            return PRICES.getOrDefault(name.toLowerCase(), 0);
        }

        /** The calculator's actions' pictures: the item of the action's name. */
        void add(CalculatorData data) {
            for (Skill skill : data.skills()) {
                for (CalculatorData.Action action : data.calc(skill).actions) {
                    if (action.icon > 0) {
                        byId.putIfAbsent(action.icon, action.name);
                    }
                }
            }
        }

        @Override
        public int itemId(String name) {
            Integer id = name == null ? null : ids.get(name.toLowerCase());
            return id == null ? -1 : id;
        }

        @Override
        public BufferedImage skill(Skill skill, boolean small) {
            return skills.getSkillImage(skill, small);
        }

        private BufferedImage load(String name) {
            String file = name.replace(' ', '_') + ".png";
            File local = new File(cache, file.replace("'", ""));
            try {
                if (!local.exists()) {
                    for (String attempt : new String[]{file, file.replace(".png", "_5.png")}) {
                        URL url = new URL("https://oldschool.runescape.wiki/images/" + URLEncoder.encode(attempt, StandardCharsets.UTF_8.name()));
                        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                        connection.setRequestProperty("User-Agent", "Snuffy's Planner preview");
                        connection.setConnectTimeout(5000);
                        connection.setReadTimeout(5000);
                        if (connection.getResponseCode() == 200) {
                            try (InputStream in = connection.getInputStream()) {
                                Files.copy(in, local.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            }
                            break;
                        }
                    }
                }
                return local.exists() ? ImageIO.read(local) : null;
            } catch (java.io.IOException e) {
                return null;
            }
        }
    }
}
