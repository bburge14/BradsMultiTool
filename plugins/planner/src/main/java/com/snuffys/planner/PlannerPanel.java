package com.snuffys.planner;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Snuffy's Planner's sidebar tab, laid out like RuneLite's Time Tracking: a row of tabs across the top (Today,
 * Timers, Slayer, Drops, Notes; each can be hidden in the settings, and the one you pick is remembered), each with a
 * header line (title, summary, a + to add) over cards like Time Tracking's and boxes like the Loot Tracker's. A tab is
 * only rebuilt when what it shows changes; the countdowns tick each second without a rebuild.
 */
class PlannerPanel extends PluginPanel {

    /** Where the pictures come from (the game's item and skill icons in the client; files in the preview). */
    interface Icons {
        /** An item's picture, with the quantity drawn on it if asked (null when there's none). */
        BufferedImage item(int itemId, int quantity, boolean showQuantity);

        /** The id of the (Grand Exchange) item with this name, or -1. */
        int itemId(String name);

        BufferedImage skill(Skill skill, boolean small);
    }

    private static final String GROUP = PlannerChores.GROUP;
    // Which tab was open (plain config, like the old fold switches)
    private static final String TAB_KEY = "plannerTab";
    private static final int GRID_COLUMNS = 5;
    // A diseased patch's bar (a sicklier yellow than the growing orange)
    private static final Color DISEASED = new Color(190, 175, 50);

    // The daily rewards' items, for their pictures
    private static final Map<String, Integer> PRESET_ITEMS = Map.of(
        "staves", ItemID.BATTLESTAFF,
        "herbBoxes", ItemID.NZONE_HERBBOX,
        "essence", ItemID.BLANKRUNE_HIGH,
        "runes", ItemID.CHAOSRUNE,
        "sand", ItemID.BUCKET_SAND,
        "flax", ItemID.BOW_STRING,
        "bonemeal", ItemID.BUCKET_ECTOPLASM,
        "dynamite", ItemID.LOVAKENGJ_DYNAMITE_FUSED,
        "arrows", ItemID.OGRE_ARROW,
        PlannerChores.TOG, ItemID.TOG_BOWL);

    private final PlannerConfig config;
    private final ConfigManager configManager;
    private final PlannerChores chores;
    private final PlannerTimers timers;
    private final FarmingTracker farming;
    private final PlannerSlayer slayer;
    private final DropTracker drops;
    private final PlannerNotes notes;
    private final Icons icons;

    private final JPanel display = new JPanel();
    private final JPanel tabRow = new JPanel(new BorderLayout());
    private final Page today = new TodayPage();
    private final Page timerPage = new TimersPage();
    private final Page slayerPage = new SlayerPage();
    private final Page dropPage = new DropsPage();
    private final NotesPage notePage = new NotesPage();
    private final List<Page> pages = Arrays.asList(today, timerPage, slayerPage, dropPage, notePage);
    private String tabsLayout;
    private Page selected;

    // Sources whose items are folded away (clicked shut)
    private final Set<String> closedSources = new HashSet<>();
    private final Map<String, Integer> cropItems = new HashMap<>();
    private final Timer clock = new Timer(1000, e -> refresh());
    private final Timer notesSave = new Timer(1000, e -> saveNotes());
    private String notesProfile;

    PlannerPanel(PlannerConfig config, ConfigManager configManager, PlannerChores chores, PlannerTimers timers,
                 FarmingTracker farming, PlannerSlayer slayer, DropTracker drops, PlannerNotes notes, Icons icons) {
        super(false);
        this.config = config;
        this.configManager = configManager;
        this.chores = chores;
        this.timers = timers;
        this.farming = farming;
        this.slayer = slayer;
        this.drops = drops;
        this.notes = notes;
        this.icons = icons;

        setLayout(new BorderLayout());
        setBackground(PlannerUi.BACKGROUND);
        tabRow.setBackground(PlannerUi.BACKGROUND);
        tabRow.setBorder(new EmptyBorder(10, 10, 0, 10));
        display.setLayout(new BorderLayout());
        display.setBackground(PlannerUi.BACKGROUND);
        display.setBorder(new EmptyBorder(10, 10, 8, 10));
        add(tabRow, BorderLayout.NORTH);
        add(display, BorderLayout.CENTER);

        notesSave.setRepeats(false);
        clock.start();
        refresh();
    }

    void stop() {
        clock.stop();
        notesSave.stop();
        saveNotes();
    }

    /** Redraws what changed (the open tab only). On the Swing thread. */
    void refresh() {
        String profile = configManager.getRSProfileKey();
        if (profile != null && !profile.equals(notesProfile)) {
            notesProfile = profile;
            notePage.notesBox.setText(notes.notes());
            notePage.notesBox.setCaretPosition(0);
        }
        StringBuilder layout = new StringBuilder().append(profile != null);
        for (Page page : pages) {
            layout.append(page.shown() ? '1' : '0');
        }
        if (!layout.toString().equals(tabsLayout)) {
            tabsLayout = layout.toString();
            buildTabs(profile != null);
        }
        if (selected != null && profile != null) {
            selected.refresh();
        }
    }

    // ---- Tabs ----

    private void buildTabs(boolean loggedIn) {
        tabRow.removeAll();
        display.removeAll();
        selected = null;
        List<Page> shown = new ArrayList<>();
        for (Page page : pages) {
            if (page.shown()) {
                shown.add(page);
            }
        }
        if (!loggedIn || shown.isEmpty()) {
            display.add(!loggedIn ? message("Snuffy's Planner", "Log in to see your dailies, timers, Slayer task, drops and "
                + "notes. They're kept for each account.")
                : message("Nothing to show", "Every list is switched off. Turn one on in the Planner's settings."), BorderLayout.NORTH);
        } else {
            MaterialTabGroup group = new MaterialTabGroup(display);
            group.setLayout(new GridLayout(1, shown.size(), 6, 0));
            String remembered = configManager.getConfiguration(GROUP, TAB_KEY);
            MaterialTab first = null;
            MaterialTab pick = null;
            for (Page page : shown) {
                MaterialTab tab = new MaterialTab(tabIcon(page.shape), group, page.scroll);
                tab.setPreferredSize(new Dimension(30, 27));
                tab.setToolTipText(page.name);
                tab.setOnSelectEvent(() -> {
                    selected = page;
                    if (!page.id.equals(configManager.getConfiguration(GROUP, TAB_KEY))) {
                        configManager.setConfiguration(GROUP, TAB_KEY, page.id);
                    }
                    page.refresh();
                    return true;
                });
                group.addTab(tab);
                first = first == null ? tab : first;
                if (page.id.equals(remembered)) {
                    pick = tab;
                }
            }
            tabRow.add(group, BorderLayout.CENTER);
            group.select(pick != null ? pick : first);
        }
        tabRow.revalidate();
        tabRow.repaint();
        display.revalidate();
        display.repaint();
    }

    // A centred title over wrapped muted text (logged out, or every list switched off)
    private static JComponent message(String title, String text) {
        JPanel box = new JPanel(new BorderLayout(0, 2));
        box.setBackground(PlannerUi.BACKGROUND);
        box.setBorder(new EmptyBorder(40, 0, 0, 0));
        JLabel name = PlannerUi.bold(title);
        name.setHorizontalAlignment(SwingConstants.CENTER);
        box.add(name, BorderLayout.NORTH);
        box.add(PlannerUi.empty(text), BorderLayout.CENTER);
        return box;
    }

    // Sharp on scaled screens too: drawn at 1x and 2x
    private static ImageIcon tabIcon(PlannerUi.Shape shape) {
        Color colour = new Color(200, 200, 200);
        return new ImageIcon(new BaseMultiResolutionImage(
            PlannerUi.picture(new PlannerUi.Glyph(shape, 16, colour)),
            PlannerUi.picture(new PlannerUi.Glyph(shape, 32, colour))));
    }

    /** Package-private for the preview: shows this tab ("today", "timers", "slayer", "drops", "notes"). */
    void showTab(String id) {
        configManager.setConfiguration(GROUP, TAB_KEY, id);
        tabsLayout = null;
        refresh();
    }

    // ---- Pages ----

    /** What a tab shows: a key that changes when it must be rebuilt, and what builds it. */
    private static final class Built {
        final String key;
        final Runnable fill;

        Built(String key, Runnable fill) {
            this.key = key;
            this.fill = fill;
        }
    }

    /** One tab: a scrolling column, rebuilt when its key changes; {@link #live} parts update every second. */
    private abstract class Page {
        final String id;
        final String name;
        final PlannerUi.Shape shape;
        final PlannerUi.Column column = new PlannerUi.Column();
        final JScrollPane scroll;
        final List<Runnable> live = new ArrayList<>();
        // Where fill() adds (the whole column, but the Notes tab keeps its input and notes box)
        JPanel target = column;
        private String key;

        Page(String id, String name, PlannerUi.Shape shape) {
            this.id = id;
            this.name = name;
            this.shape = shape;
            scroll = new JScrollPane(column);
            scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.setBorder(new EmptyBorder(0, 0, 0, 0));
            scroll.setBackground(PlannerUi.BACKGROUND);
            scroll.getViewport().setBackground(PlannerUi.BACKGROUND);
            scroll.getVerticalScrollBar().setPreferredSize(new Dimension(16, 0));
            scroll.getVerticalScrollBar().setBorder(new EmptyBorder(0, 9, 0, 0));
            scroll.getVerticalScrollBar().setUnitIncrement(16);
        }

        abstract boolean shown();

        abstract Built build();

        void refresh() {
            Built built = build();
            if (!built.key.equals(key)) {
                key = built.key;
                target.removeAll();
                live.clear();
                built.fill.run();
                target.revalidate();
                target.repaint();
            }
            for (Runnable update : live) {
                update.run();
            }
        }

        void add(JComponent component) {
            target.add(component);
        }

        void card(JComponent card) {
            target.add(PlannerUi.spaced(card));
        }
    }

    // ---- Today: dailies and weeklies ----

    private final class TodayPage extends Page {
        TodayPage() {
            super("today", "Today: dailies and weeklies", PlannerUi.Shape.CHECKLIST);
        }

        @Override
        boolean shown() {
            return config.plannerShowToday() || config.plannerShowWeekly();
        }

        @Override
        Built build() {
            long now = System.currentTimeMillis() / 1000;
            boolean daily = config.plannerShowToday();
            boolean weekly = config.plannerShowWeekly();
            List<PlannerChores.Row> days = daily ? chores.rows(false) : new ArrayList<>();
            List<PlannerChores.Row> weeks = weekly ? chores.rows(true) : new ArrayList<>();
            int hiddenDays = daily ? chores.hiddenCount(false) : 0;
            int hiddenWeeks = weekly ? chores.hiddenCount(true) : 0;
            long[] kingdom = weekly && config.plannerShowKingdom() ? chores.kingdom() : null;
            String contract = weekly && config.plannerShowContract() ? chores.contract() : null;
            FarmingTracker.Record contractPatch = contract == null || "done".equals(contract) ? null : contractPatch(contract);
            String dailySummary = summary(days, PlannerChores.lastDailyReset(now) + PlannerChores.DAY - now);
            String weeklySummary = summary(weeks, PlannerChores.lastWeeklyReset(now) + PlannerChores.WEEK - now);
            StringBuilder key = new StringBuilder().append(daily).append(weekly).append(hiddenDays).append(hiddenWeeks)
                .append(dailySummary).append(weeklySummary).append('|');
            for (PlannerChores.Row row : days) {
                rowKey(key, row);
            }
            for (PlannerChores.Row row : weeks) {
                rowKey(key, row);
            }
            if (kingdom != null) {
                key.append(kingdom[0]).append(kingdom[1]).append(daysAgo(kingdom[2], now));
            }
            key.append(contract).append(contractPatch == null ? "" : patchStatus(contractPatch, now));
            return new Built(key.toString(), () -> {
                if (daily) {
                    add(PlannerUi.header("Dailies", tip(new JLabel(dailySummary), "The daily reset is at 00:00 UTC"),
                        plus("Add your own daily or weekly", () -> addChore(false))));
                    for (PlannerChores.Row row : days) {
                        card(choreCard(row));
                    }
                    if (days.isEmpty()) {
                        add(PlannerUi.empty("No dailies yet. The rewards you've unlocked show here once you're in game, "
                            + "or press + to add your own."));
                    }
                    if (hiddenDays > 0) {
                        add(PlannerUi.link("Show hidden (" + hiddenDays + ")", "Bring back the dailies you hid",
                            () -> chores.showHidden(false)));
                    }
                }
                if (weekly) {
                    JLabel summary = tip(new JLabel(weeklySummary), "The weekly reset is on Wednesday at 00:00 UTC");
                    JComponent add = plus("Add your own weekly", () -> addChore(true));
                    add(daily ? PlannerUi.section("Weeklies", summary, add) : PlannerUi.header("Weeklies", summary, add));
                    for (PlannerChores.Row row : weeks) {
                        card(choreCard(row));
                    }
                    if (kingdom != null) {
                        card(kingdomCard(kingdom, now));
                    }
                    if (contract != null) {
                        card(contractCard(contract, contractPatch, now));
                    }
                    if (weeks.isEmpty() && kingdom == null && contract == null) {
                        add(PlannerUi.empty("No weeklies yet. Press + to add your own."));
                    }
                    if (hiddenWeeks > 0) {
                        add(PlannerUi.link("Show hidden (" + hiddenWeeks + ")", "Bring back the weeklies you hid",
                            () -> chores.showHidden(true)));
                    }
                }
            });
        }

        private void rowKey(StringBuilder key, PlannerChores.Row row) {
            key.append(row.id).append(row.name).append(row.note).append(row.done).append(row.detail).append(';');
        }
    }

    // "3 left \u00b7 resets in 10h 37m", "All done \u00b7 resets in 2d 4h"
    private static String summary(List<PlannerChores.Row> rows, long untilReset) {
        int left = 0;
        for (PlannerChores.Row row : rows) {
            left += row.done ? 0 : 1;
        }
        String reset = "resets in " + formatDuration(untilReset);
        if (rows.isEmpty()) {
            return reset;
        }
        return (left > 0 ? left + " left" : "All done") + " \u00b7 " + reset;
    }

    private JComponent choreCard(PlannerChores.Row row) {
        String second = row.detail != null ? row.detail : row.custom
            ? (row.note != null ? row.note : "Your own " + (row.weekly ? "weekly" : "daily")) : shortNote(row.note);
        PlannerUi.Card card = new PlannerUi.Card(row.name, second, null).east(new PlannerUi.Tick(row.done));
        Integer item = PRESET_ITEMS.get(row.id);
        float alpha = row.done ? 0.35f : 1f;
        if (item != null) {
            image(card.icon, icons.item(item, 1, false), alpha);
        } else {
            image(card.icon, PlannerUi.picture(new PlannerUi.Glyph(PlannerUi.Shape.NOTE, 18, PlannerUi.MUTED)), alpha);
        }
        if (row.done) {
            card.dim();
        }
        String how = row.custom ? "Your own: click to tick it; it unticks at the " + (row.weekly ? "weekly" : "daily") + " reset"
            : row.fromGame ? "Ticks itself as you collect it (or click to tick it)" : "Click to tick it";
        JPopupMenu menu = new JPopupMenu();
        menu.add(item(row.done ? "Untick" : "Tick", () -> chores.setDone(row.id, !row.done)));
        menu.add(item("Edit...", () -> editChore(row)));
        menu.add(item(row.custom ? "Delete" : "Hide", () -> chores.remove(row.id)));
        return card.interactive(() -> chores.setDone(row.id, !row.done), menu,
            "<html><b>" + PlannerUi.esc(row.name) + "</b>" + (row.note != null ? "<br>" + PlannerUi.esc(row.note) : "")
                + "<br><font color='#a0a0a0'>" + how + ". Right-click to edit or " + (row.custom ? "delete" : "hide")
                + ".</font></html>");
    }

    // "Zaff in Varrock (Varrock easy diary)" -> "Zaff in Varrock"; "Nightmare Zone: 9,500..." -> "Nightmare Zone"
    static String shortNote(String note) {
        if (note == null) {
            return "";
        }
        int cut = note.length();
        for (String mark : new String[]{" (", ": "}) {
            int at = note.indexOf(mark);
            if (at > 0) {
                cut = Math.min(cut, at);
            }
        }
        return note.substring(0, cut);
    }

    private JComponent kingdomCard(long[] kingdom, long now) {
        int percent = PlannerChores.approvalPercent(kingdom[0]);
        Color colour = percent < 50 ? HudColors.WARN : PlannerUi.READY;
        PlannerUi.Card card = new PlannerUi.Card("Kingdom of Miscellania", "Coffer " + gp(kingdom[1]) + " gp",
            percent + "% approval");
        card.right.setForeground(colour);
        card.bar(new PlannerUi.Bar(percent / 100.0, colour));
        image(card.icon, icons.item(ItemID.COINS, 10_000, false), 1f);
        return card.interactive(null, null, "<html>Approval " + percent + "% and coffer " + String.format("%,d", kingdom[1])
            + " gp, " + daysAgo(kingdom[2], now) + ".<br><font color='#a0a0a0'>Approval drops each day you "
            + "don't help out in the kingdom.</font></html>");
    }

    private JComponent contractCard(String contract, FarmingTracker.Record patch, long now) {
        boolean done = "done".equals(contract);
        String status = done ? "Done" : patch == null ? "not planted" : patchStatus(patch, now);
        PlannerUi.Card card = new PlannerUi.Card("Farming contract", done ? "Claim from Guinevere" : contract, status);
        card.right.setForeground(statusColour(done ? "ready" : status));
        if (patch != null) {
            card.bar(patchBar(patch, now));
        }
        BufferedImage picture = done ? null : cropPicture(contract);
        image(card.icon, picture != null ? picture : icons.skill(Skill.FARMING, false), 1f);
        return card.interactive(null, null, "Guinevere's contract at the Farming Guild; the patch as you last saw it");
    }

    // The contract crop's patch at the Farming Guild, as last seen in Timers
    private FarmingTracker.Record contractPatch(String crop) {
        for (FarmingTracker.Record patch : farming.records()) {
            if (patch.title.startsWith("Farming Guild") && patch.crop.equalsIgnoreCase(crop)) {
                return patch;
            }
        }
        return null;
    }

    private void addChore(boolean weeklyList) {
        JTextField name = new JTextField();
        JTextField note = new JTextField();
        JComboBox<String> when = new JComboBox<>(new String[]{"Daily", "Weekly"});
        when.setSelectedIndex(weeklyList ? 1 : 0);
        if (form("Add a daily or weekly", new String[]{"Name", "Resets", "Note (optional)"}, name, when, note)
            && !name.getText().trim().isEmpty()) {
            chores.add(name.getText().trim(), when.getSelectedIndex() == 1, note.getText());
        }
    }

    private void editChore(PlannerChores.Row row) {
        JTextField name = new JTextField(row.name);
        JTextField note = new JTextField(row.note == null ? "" : row.note);
        if (form("Edit " + row.name, new String[]{"Name", "Note"}, name, note) && !name.getText().trim().isEmpty()) {
            chores.edit(row.id, name.getText().trim(), note.getText());
        }
    }

    // ---- Timers: farming patches, bird houses and your own ----

    private final class TimersPage extends Page {
        TimersPage() {
            super("timers", "Timers: farming, bird houses and your own", PlannerUi.Shape.CLOCK);
        }

        @Override
        boolean shown() {
            return config.plannerShowTimers();
        }

        @Override
        Built build() {
            long now = System.currentTimeMillis() / 1000;
            List<FarmingTracker.Record> patches = config.farmingPatchList() ? farming.records() : new ArrayList<>();
            long birdhouses = timers.birdhousesFullAt();
            List<PlannerTimers.Timer> list = timers.timers();
            StringBuilder key = new StringBuilder();
            int ready = 0;
            for (FarmingTracker.Record patch : patches) {
                String status = patchStatus(patch, now);
                ready += "ready".equals(status) ? 1 : 0;
                key.append(patch.key).append(patch.crop).append(status).append(';');
            }
            String birdStatus = birdhouses > 0 ? birdhouseStatus(birdhouses, now) : "";
            ready += "ready".equals(birdStatus) ? 1 : 0;
            key.append(birdStatus).append('|');
            int running = 0;
            for (PlannerTimers.Timer timer : list) {
                boolean on = timer.running(now);
                boolean ended = timer.ended(now);
                running += on ? 1 : 0;
                ready += ended ? 1 : 0;
                key.append(timer.id).append(timer.name).append(timer.seconds).append(on).append(ended).append(';');
            }
            List<String> parts = new ArrayList<>();
            if (ready > 0) {
                parts.add(ready + " ready");
            }
            if (running > 0) {
                parts.add(running + " running");
            }
            String summary = String.join(" \u00b7 ", parts);
            key.append(summary);
            return new Built(key.toString(), () -> {
                add(PlannerUi.header("Timers", new JLabel(summary), plus("Add a timer", PlannerPanel.this::addTimer)));
                if (patches.isEmpty() && birdhouses == 0 && list.isEmpty()) {
                    add(PlannerUi.empty("No timers yet. Visit a farming patch, seed your bird houses or press + to add "
                        + "one."));
                    return;
                }
                if (birdhouses > 0 || !patches.isEmpty()) {
                    add(PlannerUi.group("Farming", ""));
                    if (birdhouses > 0) {
                        card(birdhouseCard(birdhouses, now));
                    }
                    for (FarmingTracker.Record patch : patches) {
                        card(patchCard(patch, now));
                    }
                }
                if (!list.isEmpty()) {
                    add(PlannerUi.group("Your timers", list.size() == 1 ? "1 timer" : list.size() + " timers"));
                    for (PlannerTimers.Timer timer : list) {
                        card(timerCard(timer, now, this));
                    }
                }
            });
        }
    }

    private static String birdhouseStatus(long fullAt, long now) {
        return now >= fullAt ? "ready" : "in " + formatDuration(fullAt - now);
    }

    // "ready", "in 1h 20m" (with a ~ while it may be out by a growth cycle), "diseased", "dead"
    static String patchStatus(FarmingTracker.Record patch, long now) {
        if (patch.ready(now)) {
            return "ready";
        }
        if (FarmingData.DISEASED.equals(patch.state)) {
            return "diseased";
        }
        if (FarmingData.DEAD.equals(patch.state)) {
            return "dead";
        }
        if (FarmingData.GROWING.equals(patch.state) && patch.grownAt > 0) {
            // To the minute (the list redraws when it changes)
            long minutes = Math.max(0, (patch.grownAt - now + 59) / 60);
            String when = minutes >= 60 ? "in " + minutes / 60 + "h " + minutes % 60 + "m" : "in " + minutes + "m";
            return (patch.slack > 0 ? "~" : "") + when;
        }
        return patch.state.toLowerCase();
    }

    private static Color statusColour(String status) {
        switch (status) {
            case "ready":
                return PlannerUi.READY;
            case "dead":
                return ColorScheme.PROGRESS_ERROR_COLOR;
            case "diseased":
                return HudColors.WARN;
            default:
                return PlannerUi.MUTED;
        }
    }

    private PlannerUi.Bar patchBar(FarmingTracker.Record patch, long now) {
        String status = patchStatus(patch, now);
        if ("ready".equals(status)) {
            return new PlannerUi.Bar(1, PlannerUi.READY);
        }
        if ("dead".equals(status)) {
            return new PlannerUi.Bar(1, ColorScheme.PROGRESS_ERROR_COLOR);
        }
        long total = farming.growthSeconds(patch);
        double done = total > 0 && patch.grownAt > 0 ? 1 - (double) (patch.grownAt - now) / total : 0;
        return new PlannerUi.Bar(Math.max(0.02, done), "diseased".equals(status) ? DISEASED : PlannerUi.GROWING);
    }

    // One patch: where, the crop and when; right-click to forget it
    private JComponent patchCard(FarmingTracker.Record patch, long now) {
        String status = patchStatus(patch, now);
        PlannerUi.Card card = new PlannerUi.Card(patch.title, patch.crop, status).bar(patchBar(patch, now));
        card.right.setForeground(statusColour(status));
        BufferedImage picture = cropPicture(patch.crop);
        image(card.icon, picture != null ? picture : icons.skill(Skill.FARMING, false), 1f);
        JPopupMenu menu = new JPopupMenu();
        menu.add(item("Forget this patch", () -> farming.forget(patch.key)));
        return card.interactive(null, menu, "<html>" + PlannerUi.esc(patch.title) + ": " + PlannerUi.esc(patch.crop)
            + " (as you last saw it)" + (patch.slack > 0 ? "<br><font color='#a0a0a0'>~ until one of your patches has been "
            + "seen growing, the time may be out by a growth cycle</font>" : "") + "<br><font color='#a0a0a0'>Right-click to "
            + "forget it.</font></html>");
    }

    private JComponent birdhouseCard(long fullAt, long now) {
        String status = birdhouseStatus(fullAt, now);
        double done = 1 - (double) Math.max(0, fullAt - now) / BirdhouseTracker.FULL_AFTER_SECONDS;
        boolean ready = "ready".equals(status);
        PlannerUi.Card card = new PlannerUi.Card("Bird houses", "Fossil Island", status)
            .bar(new PlannerUi.Bar(Math.max(0.02, done), ready ? PlannerUi.READY : PlannerUi.GROWING));
        card.right.setForeground(statusColour(status));
        image(card.icon, icons.item(ItemID.BIRDHOUSE_NORMAL, 1, false), 1f);
        return card.interactive(null, null, "Your Fossil Island bird houses: full 50 minutes after you seed them");
    }

    private JComponent timerCard(PlannerTimers.Timer timer, long now, Page page) {
        boolean running = timer.running(now);
        boolean ended = timer.ended(now);
        PlannerUi.IconButton toggle = new PlannerUi.IconButton(new PlannerUi.Glyph(running ? PlannerUi.Shape.STOP
            : ended ? PlannerUi.Shape.AGAIN : PlannerUi.Shape.PLAY, 14, Color.WHITE),
            running ? "Stop" : ended ? "Start again" : "Start", () -> {
                if (running) {
                    timers.stop(timer.id);
                } else {
                    timers.start(timer.id);
                }
            });
        PlannerUi.Bar bar = new PlannerUi.Bar(0, PlannerUi.GROWING);
        PlannerUi.Card card = new PlannerUi.Card(timer.name, running ? "Running" : ended ? "Done" : "Stopped",
            PlannerTimers.clock(timer.seconds)).east(toggle).bar(bar);
        image(card.icon, PlannerUi.picture(new PlannerUi.Glyph(PlannerUi.Shape.CLOCK, 20, PlannerUi.MUTED)), 1f);
        if (ended) {
            card.left.setForeground(PlannerUi.READY);
            card.right.setText("0:00");
            bar.set(1, PlannerUi.READY);
        } else if (running) {
            card.right.setForeground(Color.WHITE);
            page.live.add(() -> {
                long at = System.currentTimeMillis() / 1000;
                long left = timer.left(at);
                card.right.setText(PlannerTimers.clock(left));
                bar.set(timer.seconds > 0 ? 1 - (double) left / timer.seconds : 1, PlannerUi.GROWING);
            });
        }
        JPopupMenu menu = new JPopupMenu();
        menu.add(item("Edit...", () -> editTimer(timer)));
        if (ended || running) {
            menu.add(item("Reset", () -> timers.stop(timer.id)));
        }
        menu.add(item("Delete", () -> timers.delete(timer.id)));
        return card.interactive(null, menu, "<html>" + PlannerUi.esc(timer.name) + ": " + PlannerTimers.clock(timer.seconds)
            + "<br><font color='#a0a0a0'>Right-click to change or delete it.</font></html>");
    }

    private void addTimer() {
        JTextField name = new JTextField();
        JTextField length = new JTextField();
        if (form("Add a timer", new String[]{"Name", "Minutes (or h:mm)"}, name, length)) {
            long seconds = PlannerTimers.parseLength(length.getText());
            if (name.getText().trim().isEmpty() || seconds <= 0) {
                JOptionPane.showMessageDialog(this, "Give it a name and a length: minutes (90) or hours:minutes (1:30).");
                return;
            }
            timers.add(name.getText().trim(), seconds);
        }
    }

    private void editTimer(PlannerTimers.Timer timer) {
        JTextField name = new JTextField(timer.name);
        long minutes = timer.seconds / 60;
        JTextField length = new JTextField(minutes >= 60 ? minutes / 60 + ":" + String.format("%02d", minutes % 60) : String.valueOf(minutes));
        if (form("Edit " + timer.name, new String[]{"Name", "Minutes (or h:mm)"}, name, length)) {
            long seconds = PlannerTimers.parseLength(length.getText());
            if (!name.getText().trim().isEmpty() && seconds > 0) {
                timers.edit(timer.id, name.getText().trim(), seconds);
            }
        }
    }

    // A crop's picture: the item of that name, or its grimy herb, logs or seed
    private BufferedImage cropPicture(String crop) {
        if (crop == null) {
            return null;
        }
        int id = cropItems.computeIfAbsent(crop, name -> {
            String lower = name.toLowerCase();
            for (String guess : new String[]{name, "Grimy " + lower + " weed", "Grimy " + lower + " leaf", "Grimy " + lower,
                name + " logs", name + " seed", name + " tree seed", name + " sapling"}) {
                int found = icons.itemId(guess);
                if (found > 0) {
                    return found;
                }
            }
            return -1;
        });
        return id > 0 ? icons.item(id, 1, false) : null;
    }

    // ---- Slayer ----

    private final class SlayerPage extends Page {
        SlayerPage() {
            super("slayer", "Slayer: your task, points and streak", PlannerUi.Shape.SKULL);
        }

        @Override
        boolean shown() {
            return config.plannerShowSlayer();
        }

        @Override
        Built build() {
            PlannerSlayer.Task task = slayer.task();
            String key = task == null ? "none" : task.encode();
            return new Built(key, () -> {
                String summary = task == null ? "" : task.remaining > 0 ? task.remaining + " left" : "No task";
                add(PlannerUi.header("Slayer", new JLabel(summary)));
                if (task == null) {
                    add(PlannerUi.empty("Your Slayer task shows here once you're in game."));
                    return;
                }
                card(taskCard(task));
                JPanel tiles = new JPanel(new GridLayout(1, 2, PlannerUi.GAP, 0));
                tiles.setBackground(PlannerUi.BACKGROUND);
                tiles.add(tile("Points", String.format("%,d", task.points), "Slayer reward points"));
                tiles.add(tile("Streak", String.format("%,d", task.streak) + (task.streak == 1 ? " task" : " tasks"),
                    "Tasks done in a row (Krystilia and Mortimer keep their own)"));
                card(tiles);
            });
        }
    }

    private JComponent taskCard(PlannerSlayer.Task task) {
        boolean on = task.remaining > 0;
        PlannerUi.Card card;
        if (on) {
            int killed = Math.max(0, task.assigned - task.remaining);
            String second = task.area != null ? task.area : task.assigned > 0 ? killed + " of " + task.assigned + " killed" : "Your task";
            card = new PlannerUi.Card(task.name == null ? "Your task" : task.name, second, task.remaining + " left");
            card.right.setForeground(Color.WHITE);
            if (task.assigned > 0) {
                card.bar(new PlannerUi.Bar((double) killed / task.assigned, PlannerUi.GROWING));
            }
        } else {
            card = new PlannerUi.Card("No task", "Get one from a Slayer master", "");
        }
        image(card.icon, icons.skill(Skill.SLAYER, false), on ? 1f : 0.5f);
        String tip = on ? "<html>" + PlannerUi.esc(task.name == null ? "Your task" : task.name) + ": " + task.remaining
            + (task.assigned > 0 ? " of " + task.assigned : "") + " left" + (task.area != null ? "<br>In " + PlannerUi.esc(task.area)
            : "") + "<br><font color='#a0a0a0'>From the game, as RuneLite's Slayer plugin reads it.</font></html>"
            : "Get a task from a Slayer master";
        return card.interactive(null, null, tip);
    }

    private static JComponent tile(String name, String value, String tooltip) {
        JPanel tile = new JPanel(new GridLayout(2, 1));
        tile.setBackground(PlannerUi.CARD);
        tile.setBorder(new EmptyBorder(5, 8, 5, 8));
        tile.add(PlannerUi.label(name, PlannerUi.MUTED));
        JLabel number = new JLabel(value);
        number.setFont(FontManager.getRunescapeFont());
        number.setForeground(Color.WHITE);
        tile.add(number);
        PlannerUi.tips(tile, tooltip);
        return tile;
    }

    // ---- Drops ----

    private final class DropsPage extends Page {
        DropsPage() {
            super("drops", "Drops: loot by monster", PlannerUi.Shape.BAG);
        }

        @Override
        boolean shown() {
            return config.plannerShowDrops();
        }

        @Override
        Built build() {
            List<Map.Entry<String, DropTracker.Source>> sources = drops.sources();
            List<DropTracker.Drop> recent = drops.recent();
            int shownRecent = Math.min(recent.size(), config.plannerRecentDrops());
            long valuable = config.plannerValuableDrop();
            Color highlight = config.plannerValuableColor();
            long total = 0;
            StringBuilder key = new StringBuilder().append(closedSources).append(valuable).append(highlight).append(shownRecent).append('|');
            for (Map.Entry<String, DropTracker.Source> source : sources) {
                total += source.getValue().value;
                key.append(source.getKey()).append(source.getValue().count).append(source.getValue().value).append(';');
            }
            for (int i = 0; i < shownRecent; i++) {
                DropTracker.Drop drop = recent.get(i);
                key.append(drop.at).append(drop.item).append(drop.quantity).append(';');
            }
            long sum = total;
            return new Built(key.toString(), () -> {
                String summary = sources.isEmpty() ? "" : gp(sum) + " gp \u00b7 " + sources.size() + (sources.size() == 1 ? " source" : " sources");
                JLabel label = tip(new JLabel(summary), "Grand Exchange value of everything tracked");
                if (sources.isEmpty()) {
                    add(PlannerUi.header("Drops", label));
                    add(PlannerUi.empty("No drops yet. What your kills drop shows here, with its Grand Exchange value."));
                    return;
                }
                add(PlannerUi.header("Drops", label, new PlannerUi.IconButton(new PlannerUi.Glyph(PlannerUi.Shape.TRASH, 14,
                    PlannerUi.MUTED), "Reset all drops", () -> {
                        if (JOptionPane.showConfirmDialog(this.column, "Clear every drop you've tracked on this account?",
                            "Reset all drops", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                            closedSources.clear();
                            drops.resetAll();
                        }
                    })));
                for (Map.Entry<String, DropTracker.Source> entry : sources) {
                    add(PlannerUi.fullWidth(sourceBox(entry.getKey(), entry.getValue(), valuable, highlight)));
                }
                if (shownRecent > 0) {
                    add(PlannerUi.group("Recent", ""));
                    for (int i = 0; i < shownRecent; i++) {
                        card(recentCard(recent.get(i), valuable, highlight));
                    }
                }
            });
        }
    }

    // Like the Loot Tracker's boxes: a title bar (name, kills, value) over a grid of the items; click to fold
    private JComponent sourceBox(String name, DropTracker.Source source, long valuable, Color highlight) {
        boolean open = !closedSources.contains(name);
        JPanel box = new JPanel(new BorderLayout(0, 1));
        box.setBackground(PlannerUi.BACKGROUND);
        box.setBorder(new EmptyBorder(0, 0, PlannerUi.GAP + 1, 0));

        JPanel title = new JPanel();
        title.setLayout(new BoxLayout(title, BoxLayout.X_AXIS));
        Color bar = ColorScheme.DARKER_GRAY_COLOR.darker();
        title.setBackground(bar);
        title.setBorder(new EmptyBorder(7, 7, 7, 7));
        JLabel nameLabel = PlannerUi.label(name, open ? Color.WHITE : PlannerUi.MUTED);
        nameLabel.setMinimumSize(new Dimension(1, nameLabel.getPreferredSize().height));
        JLabel kills = PlannerUi.label("x " + String.format("%,d", source.count), open ? ColorScheme.LIGHT_GRAY_COLOR : PlannerUi.FAINT);
        JLabel price = PlannerUi.label(gp(source.value) + " gp", open ? ColorScheme.LIGHT_GRAY_COLOR : PlannerUi.FAINT);
        title.add(nameLabel);
        title.add(Box.createRigidArea(new Dimension(5, 0)));
        title.add(kills);
        title.add(Box.createHorizontalGlue());
        title.add(Box.createRigidArea(new Dimension(5, 0)));
        title.add(price);
        PlannerUi.tips(title, "<html>" + PlannerUi.esc(name) + ": " + String.format("%,d", source.count)
            + (source.count == 1 ? " kill or open" : " kills or opens") + ", " + String.format("%,d", source.value) + " gp"
            + (source.count > 1 ? " (" + gp(source.value / source.count) + " each)" : "")
            + "<br><font color='#a0a0a0'>Click to " + (open ? "fold" : "show") + " the items; right-click to reset.</font></html>");
        title.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    if (!closedSources.remove(name)) {
                        closedSources.add(name);
                    }
                    refresh();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                title.setBackground(PlannerUi.CARD_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                java.awt.Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), title);
                if (!new java.awt.Rectangle(title.getSize()).contains(p)) {
                    title.setBackground(bar);
                }
            }
        };
        JPopupMenu menu = new JPopupMenu();
        menu.add(item("Reset " + name, () -> {
            closedSources.remove(name);
            drops.reset(name);
        }));
        PlannerUi.everywhere(title, c -> {
            c.addMouseListener(mouse);
            if (c != title) {
                ((JComponent) c).setInheritsPopupMenu(true);
            }
        });
        title.setComponentPopupMenu(menu);
        box.add(title, BorderLayout.NORTH);

        if (open) {
            List<Map.Entry<String, long[]>> items = new ArrayList<>(source.items.entrySet());
            items.sort((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]));
            int rows = (items.size() + GRID_COLUMNS - 1) / GRID_COLUMNS;
            JPanel grid = new JPanel(new GridLayout(rows, GRID_COLUMNS, 1, 1));
            grid.setBackground(PlannerUi.BACKGROUND);
            for (int i = 0; i < rows * GRID_COLUMNS; i++) {
                JPanel slot = new JPanel(new BorderLayout());
                slot.setBackground(PlannerUi.CARD);
                slot.setPreferredSize(new Dimension(PlannerUi.ICON_W, PlannerUi.ICON_H + 4));
                if (i < items.size()) {
                    Map.Entry<String, long[]> item = items.get(i);
                    long quantity = item.getValue()[0];
                    long value = item.getValue()[1];
                    JLabel picture = new JLabel();
                    picture.setHorizontalAlignment(SwingConstants.CENTER);
                    image(picture, itemPicture(item.getKey(), quantity, true), 1f);
                    slot.add(picture, BorderLayout.CENTER);
                    if (value >= valuable) {
                        slot.setBackground(blend(highlight, PlannerUi.CARD, 0.22));
                        slot.setBorder(BorderFactory.createLineBorder(blend(highlight, PlannerUi.CARD, 0.7)));
                    }
                    PlannerUi.tips(slot, "<html>" + PlannerUi.esc(item.getKey()) + " x " + String.format("%,d", quantity)
                        + "<br>GE: " + gp(value) + (quantity > 1 && value > 0 ? " (" + gp(value / quantity) + " ea)" : "") + "</html>");
                }
                grid.add(slot);
            }
            box.add(grid, BorderLayout.CENTER);
        }
        return box;
    }

    private JComponent recentCard(DropTracker.Drop drop, long valuable, Color highlight) {
        boolean big = drop.value >= valuable;
        PlannerUi.Card card = new PlannerUi.Card(drop.item + (drop.quantity > 1 ? " x " + String.format("%,d", drop.quantity) : ""),
            drop.source, gp(drop.value) + " gp");
        if (big) {
            card.title.setForeground(highlight);
            card.right.setForeground(highlight);
        }
        image(card.icon, itemPicture(drop.item, drop.quantity, false), 1f);
        return card.interactive(null, null, drop.source + ": " + drop.item + " x " + String.format("%,d", drop.quantity) + ", "
            + String.format("%,d", drop.value) + " gp");
    }

    // An item's picture by its name (with the quantity on it), or a plain tile with its initials
    private BufferedImage itemPicture(String name, long quantity, boolean showQuantity) {
        int id = "Coins".equalsIgnoreCase(name) ? ItemID.COINS : icons.itemId(name);
        int amount = (int) Math.min(Integer.MAX_VALUE, Math.max(1, quantity));
        BufferedImage picture = id > 0 ? icons.item(id, amount, showQuantity && amount > 1) : null;
        return picture != null ? picture : initials(name, showQuantity ? amount : 1);
    }

    private static BufferedImage initials(String name, int quantity) {
        BufferedImage out = new BufferedImage(PlannerUi.ICON_W, PlannerUi.ICON_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(60, 60, 60));
        g.fillRoundRect(6, 4, 24, 24, 6, 6);
        g.setFont(FontManager.getRunescapeSmallFont());
        String text = name == null || name.isEmpty() ? "?" : name.substring(0, Math.min(2, name.length()));
        FontMetrics metrics = g.getFontMetrics();
        g.setColor(new Color(200, 200, 200));
        g.drawString(text, 18 - metrics.stringWidth(text) / 2, 20);
        if (quantity > 1) {
            g.setColor(Color.YELLOW);
            g.drawString(quantity >= 100_000 ? quantity / 1000 + "K" : String.valueOf(quantity), 0, 10);
        }
        g.dispose();
        return out;
    }

    private static Color blend(Color a, Color b, double amount) {
        return new Color((int) (a.getRed() * amount + b.getRed() * (1 - amount)), (int) (a.getGreen() * amount + b.getGreen() * (1 - amount)),
            (int) (a.getBlue() * amount + b.getBlue() * (1 - amount)));
    }

    // ---- Notes and to-do ----

    /** Its input and notes box stay put while the to-do list between them is rebuilt, so typing isn't interrupted. */
    private final class NotesPage extends Page {
        final JLabel todoSummary = new JLabel();
        final Hinted todoField = new Hinted("Add a to-do and press Enter");
        final JPanel todos = new JPanel();
        final HintedArea notesBox = new HintedArea("Anything you want to remember on this account. Saved as you type.");

        NotesPage() {
            super("notes", "Notes and to-do", PlannerUi.Shape.NOTE);
            todos.setLayout(new BoxLayout(todos, BoxLayout.Y_AXIS));
            todos.setBackground(PlannerUi.BACKGROUND);
            todos.setAlignmentX(LEFT_ALIGNMENT);
            target = todos;

            column.add(PlannerUi.header("To-do", todoSummary));
            column.add(PlannerUi.spaced(todoInput()));
            column.add(todos);
            column.add(PlannerUi.section("Notes", tip(new JLabel("saved as you type"), "Kept for this account")));
            column.add(notesInput());
        }

        @Override
        boolean shown() {
            return config.plannerShowNotes();
        }

        @Override
        Built build() {
            List<PlannerNotes.Todo> list = notes.todos();
            StringBuilder key = new StringBuilder();
            for (PlannerNotes.Todo todo : list) {
                key.append(todo.text).append(todo.done).append(';');
            }
            return new Built(key.toString(), () -> {
                int left = 0;
                for (PlannerNotes.Todo todo : list) {
                    left += todo.done ? 0 : 1;
                }
                todoSummary.setText(list.isEmpty() ? "" : left > 0 ? left + " to do" : "All done");
                for (int i = 0; i < list.size(); i++) {
                    card(todoCard(i, list.get(i), list.size()));
                }
                if (list.isEmpty()) {
                    add(PlannerUi.empty("Nothing to do yet. Type one above and press Enter."));
                }
                if (list.size() > left) {
                    add(PlannerUi.link("Clear ticked (" + (list.size() - left) + ")", "Take the ticked ones off the list",
                        notes::clearDone));
                }
                todos.setMaximumSize(new Dimension(Integer.MAX_VALUE, todos.getPreferredSize().height));
            });
        }

        private JComponent todoInput() {
            JPanel row = new JPanel(new BorderLayout());
            row.setBackground(PlannerUi.CARD);
            row.setBorder(PlannerUi.line(PlannerUi.CARD));
            todoField.setOpaque(false);
            todoField.setForeground(Color.WHITE);
            todoField.setCaretColor(Color.WHITE);
            todoField.setFont(FontManager.getRunescapeSmallFont());
            todoField.setBorder(new EmptyBorder(6, 8, 6, 4));
            todoField.setToolTipText("A new to-do: type it and press Enter");
            todoField.addActionListener(e -> addTodo());
            focusBorder(todoField, row);
            row.add(todoField, BorderLayout.CENTER);
            JPanel button = new JPanel(new java.awt.GridBagLayout());
            button.setOpaque(false);
            button.setBorder(new EmptyBorder(0, 0, 0, 4));
            button.add(plus("Add this to-do (or press Enter)", this::addTodo));
            row.add(button, BorderLayout.EAST);
            return row;
        }

        private JComponent notesInput() {
            notesBox.setLineWrap(true);
            notesBox.setWrapStyleWord(true);
            notesBox.setRows(8);
            notesBox.setFont(FontManager.getRunescapeSmallFont());
            notesBox.setOpaque(false);
            notesBox.setForeground(Color.WHITE);
            notesBox.setCaretColor(Color.WHITE);
            notesBox.setBorder(new EmptyBorder(6, 8, 6, 8));
            notesBox.setToolTipText("Notes for this account (saved as you type)");
            notesBox.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    notesSave.restart();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    notesSave.restart();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    notesSave.restart();
                }
            });
            notesBox.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    saveNotes();
                }
            });
            JPanel box = new JPanel(new BorderLayout());
            box.setBackground(PlannerUi.CARD);
            box.setBorder(PlannerUi.line(new Color(58, 58, 58)));
            box.add(notesBox, BorderLayout.CENTER);
            focusBorder(notesBox, box);
            box.setAlignmentX(LEFT_ALIGNMENT);
            // (grows with what you write; the tab scrolls)
            box.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
            return box;
        }

        private void addTodo() {
            String text = todoField.getText().trim();
            if (!text.isEmpty()) {
                notes.add(text);
                todoField.setText("");
            }
            todoField.requestFocusInWindow();
        }
    }

    // The box's outline brightens while you type in it
    private static void focusBorder(JComponent field, JPanel box) {
        javax.swing.border.Border normal = box.getBorder();
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                box.setBorder(PlannerUi.line(ColorScheme.MEDIUM_GRAY_COLOR));
            }

            @Override
            public void focusLost(FocusEvent e) {
                box.setBorder(normal);
            }
        });
    }

    private JComponent todoCard(int index, PlannerNotes.Todo todo, int count) {
        PlannerUi.Card card = new PlannerUi.Card(PlannerUi.wrap(todo.text, PlannerUi.TODO_WRAP, false, todo.done), "", null)
            .single().noIcon()
            .east(new PlannerUi.Tick(todo.done));
        if (todo.done) {
            card.dim();
        }
        JPopupMenu menu = new JPopupMenu();
        menu.add(item(todo.done ? "Untick" : "Tick", () -> notes.setDone(index, !todo.done)));
        menu.add(item("Edit...", () -> {
            JTextField field = new JTextField(todo.text);
            if (form("Edit to-do", new String[]{"To-do"}, field) && !field.getText().trim().isEmpty()) {
                notes.edit(index, field.getText().trim());
            }
        }));
        if (index > 0) {
            menu.add(item("Move up", () -> notes.move(index, -1)));
        }
        if (index < count - 1) {
            menu.add(item("Move down", () -> notes.move(index, 1)));
        }
        menu.add(item("Delete", () -> notes.delete(index)));
        return card.interactive(() -> notes.setDone(index, !todo.done), menu, "Click to tick; right-click to edit, move or delete");
    }

    private void saveNotes() {
        if (notesProfile != null && notesProfile.equals(configManager.getRSProfileKey())) {
            notes.setNotes(notePage.notesBox.getText());
        }
    }

    /** A text field that shows a grey hint while it's empty. */
    private static final class Hinted extends JTextField {
        private final String hint;

        Hinted(String hint) {
            this.hint = hint;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty()) {
                paintHint(this, g, hint, getInsets().left, getInsets().top);
            }
        }
    }

    /** A text box that shows a grey hint while it's empty. */
    private static final class HintedArea extends JTextArea {
        private final String hint;

        HintedArea(String hint) {
            this.hint = hint;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty()) {
                paintHint(this, g, hint, getInsets().left, getInsets().top);
            }
        }
    }

    // The hint, wrapped to the field's width
    private static void paintHint(JComponent field, Graphics graphics, String hint, int x, int top) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        Font font = field.getFont();
        g.setFont(font);
        g.setColor(PlannerUi.FAINT);
        FontMetrics metrics = g.getFontMetrics();
        int width = field.getWidth() - x - field.getInsets().right;
        int y = top + metrics.getAscent();
        if (field instanceof JTextField) {
            y = (field.getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
        }
        StringBuilder line = new StringBuilder();
        for (String word : hint.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (metrics.stringWidth(next) > width && line.length() > 0) {
                g.drawString(line.toString(), x, y);
                y += metrics.getHeight();
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        g.drawString(line.toString(), x, y);
        g.dispose();
    }

    // ---- Small pieces ----

    private static PlannerUi.IconButton plus(String tooltip, Runnable action) {
        return new PlannerUi.IconButton(new PlannerUi.Glyph(PlannerUi.Shape.PLUS, 14, ColorScheme.LIGHT_GRAY_COLOR), tooltip, action);
    }

    private static JLabel tip(JLabel label, String tooltip) {
        label.setToolTipText(tooltip);
        return label;
    }

    // Shows a picture on an item-sized label, faded if asked; item pictures from the game fill in once loaded
    private static void image(JLabel label, BufferedImage picture, float alpha) {
        if (picture == null) {
            label.setIcon(null);
            return;
        }
        label.setIcon(new ImageIcon(PlannerUi.fit(picture, alpha)));
        if (picture instanceof AsyncBufferedImage) {
            ((AsyncBufferedImage) picture).onLoaded(() -> SwingUtilities.invokeLater(() -> {
                label.setIcon(new ImageIcon(PlannerUi.fit(picture, alpha)));
                label.repaint();
            }));
        }
    }

    private static JMenuItem item(String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(e -> action.run());
        return item;
    }

    // A small dialog of labelled fields; true when OK was pressed
    private boolean form(String title, String[] labels, JComponent... fields) {
        JPanel form = new JPanel(new GridLayout(0, 1, 2, 2));
        for (int i = 0; i < fields.length; i++) {
            form.add(new JLabel(labels[i]));
            form.add(fields[i]);
        }
        return JOptionPane.showConfirmDialog(this, form, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
            == JOptionPane.OK_OPTION;
    }

    /** "2d 4h", "5h 12m", "12m". */
    static String formatDuration(long seconds) {
        long minutes = Math.max(0, (seconds + 59) / 60);
        long days = minutes / (60 * 24);
        long hours = minutes / 60 % 24;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        return hours > 0 ? hours + "h " + minutes % 60 + "m" : minutes + "m";
    }

    private static String daysAgo(long at, long now) {
        long days = (now - at) / PlannerChores.DAY;
        return days <= 0 ? "seen today" : days == 1 ? "seen yesterday" : "seen " + days + " days ago";
    }

    /** "950", "12.5K", "1.25M", "2.1B". */
    static String gp(long gp) {
        if (gp >= 1_000_000_000L) {
            return String.format("%.1fB", gp / 1_000_000_000.0);
        }
        if (gp >= 1_000_000) {
            return String.format("%.2fM", gp / 1_000_000.0);
        }
        if (gp >= 100_000) {
            return String.format("%.0fK", gp / 1_000.0);
        }
        if (gp >= 1_000) {
            return String.format("%.1fK", gp / 1_000.0);
        }
        return String.valueOf(gp);
    }
}
