package com.snuffys.multitool;

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
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
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

        /** A game sprite (the Calculator's spells), given to {@code then} on the Swing thread once loaded. */
        default void sprite(int spriteId, java.util.function.Consumer<BufferedImage> then) {
        }

        /** An item's Grand Exchange price by name, 0 when it has none. */
        default int price(String name) {
            return 0;
        }
    }

    private static final String GROUP = PlannerChores.GROUP;
    // Which tab was open (plain config, like the old fold switches)
    private static final String TAB_KEY = "plannerTab";
    // A diseased patch's bar (a sicklier yellow than the growing orange)
    private static final Color DISEASED = new Color(190, 175, 50);


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
    private final CalculatorPage calcPage;
    private final List<Page> pages;
    private String tabsLayout;
    private Page selected;

    private final Map<String, Integer> cropItems = new HashMap<>();
    private final Timer clock = new Timer(1000, e -> refresh());
    // Running countdowns redrawn four times a second from the real clock (cheap: only their numbers and bars), so
    // each second ticks over when it should instead of drifting against the once-a-second refresh
    private final Timer countdowns = new Timer(250, e -> {
        if (selected != null) {
            selected.live.forEach(Runnable::run);
        }
    });
    private final Timer notesSave = new Timer(1000, e -> saveNotes());
    private String notesProfile;

    private final PlannerGeLimits geLimits;

    PlannerPanel(PlannerConfig config, ConfigManager configManager, PlannerChores chores, PlannerTimers timers,
                 FarmingTracker farming, PlannerSlayer slayer, DropTracker drops, PlannerNotes notes, PlannerGeLimits geLimits,
                 Icons icons, PlannerCalculatorView calculator) {
        super(false);
        this.geLimits = geLimits;
        this.config = config;
        this.configManager = configManager;
        this.chores = chores;
        this.timers = timers;
        this.farming = farming;
        this.slayer = slayer;
        this.drops = drops;
        this.notes = notes;
        this.icons = icons;
        calcPage = new CalculatorPage(calculator);
        pages = Arrays.asList(today, timerPage, slayerPage, dropPage, notePage, calcPage);

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
        countdowns.start();
        refresh();
    }

    void stop() {
        calcPage.view.stop();
        clock.stop();
        countdowns.stop();
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
            display.add(!loggedIn ? message("Snuffy's Planner", "Log in to see your dailies, timers, Slayer task, drops, "
                + "notes and calculator. They're kept for each account.")
                : message("Nothing to show", "Every list is switched off. Turn one on in the Planner's settings."), BorderLayout.NORTH);
        } else {
            MaterialTabGroup group = new MaterialTabGroup(display);
            group.setLayout(new GridLayout(1, shown.size(), 6, 0));
            String remembered = configManager.getConfiguration(GROUP, TAB_KEY);
            MaterialTab first = null;
            MaterialTab pick = null;
            for (Page page : shown) {
                MaterialTab tab = new MaterialTab(tabIcon(page.shape), group, page.body);
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

    /** Package-private for the preview: shows this tab ("today", "timers", "slayer", "drops", "notes", "calculator"). */
    void showTab(String id) {
        configManager.setConfiguration(GROUP, TAB_KEY, id);
        tabsLayout = null;
        refresh();
    }

    /** Package-private for the preview: the Calculator on this skill, in this view, with this search. */
    void showCalculator(Skill skill, PlannerCalculator.Mode mode, String search) {
        calcPage.view.preview(skill, mode, search);
        showTab("calculator");
    }

    /** Package-private for the preview: the Calculator choosing a method for this milestone of the open plan. */
    void showCalculatorPicking(Skill skill, int milestone) {
        calcPage.view.preview(skill, PlannerCalculator.Mode.PLAN, "");
        calcPage.view.previewPicking(milestone);
        showTab("calculator");
    }

    /** Package-private for the preview: opens the Drops tab on this monster's or chest's page (null: the list). */
    void showDropSource(String source) {
        showTab("drops");
        ((DropsPage) dropPage).open(source);
    }

    /** Package-private for the preview: the open drops page lists this session's items. */
    void showDropSessionItems() {
        ((DropsPage) dropPage).sessionItems = true;
        dropPage.refresh();
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
        // What the tab shows: the scrolling column (the Calculator adds its plan bar under it)
        JComponent body;
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
            body = scroll;
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
            String dailySummary = summary(days, PlannerChores.lastDailyReset(now) + PlannerChores.DAY - now);
            String weeklySummary = summary(weeks, PlannerChores.lastWeeklyReset(now) + PlannerChores.WEEK - now);
            StringBuilder key = new StringBuilder().append(daily).append(weekly)
                .append(dailySummary).append(weeklySummary).append('|');
            for (PlannerChores.Row row : days) {
                rowKey(key, row);
            }
            for (PlannerChores.Row row : weeks) {
                rowKey(key, row);
            }
            return new Built(key.toString(), () -> {
                if (daily) {
                    add(PlannerUi.header("Dailies", tip(new JLabel(dailySummary), "The daily reset is at 00:00 UTC"),
                        plus("Add a daily", () -> addChore(false, null))));
                    for (PlannerChores.Row row : days) {
                        card(choreCard(row));
                    }
                    if (days.isEmpty()) {
                        examples(false);
                    }
                }
                if (weekly) {
                    JLabel summary = tip(new JLabel(weeklySummary), "The weekly reset is on Wednesday at 00:00 UTC");
                    JComponent add = plus("Add a weekly", () -> addChore(true, null));
                    add(daily ? PlannerUi.section("Weeklies", summary, add) : PlannerUi.header("Weeklies", summary, add));
                    for (PlannerChores.Row row : weeks) {
                        card(choreCard(row));
                    }
                    if (weeks.isEmpty()) {
                        examples(true);
                    }
                }
            });
        }

        // Nothing of yours on a list yet: a few preset tasks as examples (greyed, not tracked); click one to add it
        private void examples(boolean weekly) {
            add(PlannerUi.empty("No " + (weekly ? "weeklies" : "dailies") + " yet: press + to add your own (or start from a "
                + "preset). They untick at the " + (weekly ? "weekly" : "daily") + " reset. For example:"));
            int shown = 0;
            for (PlannerChores.Template template : PlannerChores.TEMPLATES) {
                if (template.weekly == weekly && shown < 4) {
                    card(exampleCard(template));
                    shown++;
                }
            }
        }

        private void rowKey(StringBuilder key, PlannerChores.Row row) {
            key.append(row.id).append(row.name).append(row.note).append(row.done).append(row.icon).append(';');
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

    private JComponent exampleCard(PlannerChores.Template template) {
        PlannerUi.Card card = new PlannerUi.Card(template.name, shortNote(template.note), "example");
        image(card.icon, icons.item(template.icon, 1, false), 0.4f);
        card.dim();
        return card.interactive(() -> addChore(template.weekly, template), null, "<html>An example, not tracked: "
            + PlannerUi.esc(template.name) + "<br><font color='#a0a0a0'>Click to add it (or + for your own)</font></html>");
    }

    private JComponent choreCard(PlannerChores.Row row) {
        String second = row.note != null ? shortNote(row.note) : "Your " + (row.weekly ? "weekly" : "daily");
        PlannerUi.Card card = new PlannerUi.Card(row.name, second, null).east(new PlannerUi.Tick(row.done));
        float alpha = row.done ? 0.35f : 1f;
        if (row.icon > 0) {
            image(card.icon, icons.item(row.icon, 1, false), alpha);
        } else {
            image(card.icon, PlannerUi.picture(new PlannerUi.Glyph(PlannerUi.Shape.NOTE, 18, PlannerUi.MUTED)), alpha);
        }
        if (row.done) {
            card.dim();
        }
        JPopupMenu menu = new JPopupMenu();
        menu.add(item(row.done ? "Untick" : "Tick", () -> chores.setDone(row.id, !row.done)));
        menu.add(item("Edit...", () -> editChore(row)));
        menu.add(item("Delete", () -> chores.remove(row.id)));
        return card.interactive(() -> chores.setDone(row.id, !row.done), menu,
            "<html><b>" + PlannerUi.esc(row.name) + "</b>" + (row.note != null ? "<br>" + PlannerUi.esc(row.note) : "")
                + "<br><font color='#a0a0a0'>Click to tick it; it unticks at the " + (row.weekly ? "weekly" : "daily")
                + " reset. Right-click to edit or delete.</font></html>");
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

    // Adding one: start from a preset task (its name, note and picture filled in) or your own; pick its picture
    private void addChore(boolean weeklyList, PlannerChores.Template start) {
        JComboBox<Object> preset = new JComboBox<>();
        preset.addItem("Your own");
        for (PlannerChores.Template template : PlannerChores.TEMPLATES) {
            preset.addItem(template);
        }
        JTextField name = new JTextField();
        JTextField note = new JTextField();
        JComboBox<String> when = new JComboBox<>(new String[]{"Daily", "Weekly"});
        when.setSelectedIndex(weeklyList ? 1 : 0);
        JComboBox<Integer> icon = iconChoice(0);
        preset.setRenderer(templateRenderer());
        preset.addActionListener(e -> {
            Object picked = preset.getSelectedItem();
            if (picked instanceof PlannerChores.Template) {
                PlannerChores.Template template = (PlannerChores.Template) picked;
                name.setText(template.name);
                note.setText(template.note);
                when.setSelectedIndex(template.weekly ? 1 : 0);
                icon.setSelectedItem(template.icon);
            }
        });
        if (start != null) {
            preset.setSelectedItem(start);
        }
        if (form(weeklyList ? "Add a weekly" : "Add a daily", new String[]{"Start from", "Name", "Resets", "Note (optional)",
            "Picture"}, preset, name, when, note, icon) && !name.getText().trim().isEmpty()) {
            chores.add(name.getText().trim(), when.getSelectedIndex() == 1, note.getText(), (Integer) icon.getSelectedItem());
        }
    }

    private void editChore(PlannerChores.Row row) {
        JTextField name = new JTextField(row.name);
        JTextField note = new JTextField(row.note == null ? "" : row.note);
        JComboBox<Integer> icon = iconChoice(row.icon);
        if (form("Edit " + row.name, new String[]{"Name", "Note", "Picture"}, name, note, icon)
            && !name.getText().trim().isEmpty()) {
            chores.edit(row.id, name.getText().trim(), note.getText(), (Integer) icon.getSelectedItem());
        }
    }

    // The pictures to pick from: none, then each preset task's (shown as the item)
    private JComboBox<Integer> iconChoice(int current) {
        JComboBox<Integer> box = new JComboBox<>();
        box.addItem(0);
        for (PlannerChores.Template template : PlannerChores.TEMPLATES) {
            if (((javax.swing.DefaultComboBoxModel<Integer>) box.getModel()).getIndexOf(template.icon) < 0) {
                box.addItem(template.icon);
            }
        }
        if (current > 0 && ((javax.swing.DefaultComboBoxModel<Integer>) box.getModel()).getIndexOf(current) < 0) {
            box.addItem(current);
        }
        box.setSelectedItem(current);
        box.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                                                                  boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, "", index, selected, focus);
                int id = value == null ? 0 : (Integer) value;
                label.setText(id > 0 ? iconName(id) : "None");
                label.setIcon(id > 0 && icons.item(id, 1, false) != null ? new ImageIcon(icons.item(id, 1, false)) : null);
                return label;
            }
        });
        return box;
    }

    // A preset task's picture's name (the task it's from)
    private static String iconName(int id) {
        for (PlannerChores.Template template : PlannerChores.TEMPLATES) {
            if (template.icon == id) {
                return template.name;
            }
        }
        return "Picture";
    }

    // Preset tasks in the list with their pictures and when they reset
    private javax.swing.ListCellRenderer<Object> templateRenderer() {
        return new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                                                                  boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof PlannerChores.Template) {
                    PlannerChores.Template template = (PlannerChores.Template) value;
                    label.setText(template.name + (template.weekly ? " (weekly)" : " (daily)"));
                    BufferedImage picture = icons.item(template.icon, 1, false);
                    label.setIcon(picture != null ? new ImageIcon(picture) : null);
                } else {
                    label.setIcon(null);
                }
                return label;
            }
        };
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
            // Grand Exchange buy limits: each item bought in the last 4 hours, and each buy offer (to the minute)
            long nowMillis = System.currentTimeMillis();
            List<PlannerGeLimits.Window> limits = config.plannerGeLimits() && geLimits != null ? geLimits.windows()
                : new ArrayList<>();
            int atLimit = 0;
            for (PlannerGeLimits.Window w : limits) {
                atLimit += w.atLimit() ? 1 : 0;
                key.append(w.item).append(w.name).append(w.bought).append('/').append(w.limit).append('@').append(w.resetsAt)
                    .append(w.price);
                if (w.offer != null) {
                    key.append(w.offer.state).append(w.offer.sold).append('/').append(w.offer.total).append(w.offer.price);
                }
                key.append(';');
            }
            key.append(nowMillis / 60000);
            String summary = String.join(" \u00b7 ", parts);
            key.append(summary);
            int limitCount = atLimit;
            return new Built(key.toString(), () -> {
                add(PlannerUi.header("Timers", new JLabel(summary), plus("Add a timer", PlannerPanel.this::addTimer)));
                if (!limits.isEmpty()) {
                    add(PlannerUi.group("GE buy limits", limitCount > 0 ? limitCount + " at the limit" : "4-hour windows"));
                    for (PlannerGeLimits.Window w : limits) {
                        card(limitCard(w, nowMillis));
                    }
                }
                if (patches.isEmpty() && birdhouses == 0 && list.isEmpty()) {
                    if (!limits.isEmpty()) {
                        return;
                    }
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

    // An item's buy limit: how much of it this window has used and when it resets; for a buy offer, whether it's the
    // limit or the price keeping it from filling
    private JComponent limitCard(PlannerGeLimits.Window w, long now) {
        String used = w.limit > 0 ? String.format("%,d / %,d bought", w.bought, w.limit)
            : String.format("%,d bought", w.bought) + (w.bought > 0 ? " (limit unknown)" : "");
        PlannerUi.Card card = new PlannerUi.Card(w.name, used, "");
        // The reset time in the corner ("by" when the window's first buy came in while you were away)
        card.corner.setText(w.resetsAt > 0 ? (w.exact ? "" : "by ") + clock(w.resetsAt) : "");
        PlannerGeLimits.Slot offer = w.offer;
        String until = w.resetsAt > now ? formatDuration((w.resetsAt - now + 59999) / 60000 * 60) : "";
        String verdict;
        Color tone;
        if (w.atLimit()) {
            verdict = "At the limit: more " + (w.exact ? "in " : "within ") + until;
            tone = COSTS_RED;
        } else if (offer != null && offer.buying() && offer.sold < offer.total) {
            if (w.price > 0 && offer.price < w.price) {
                verdict = "Under the limit: it's your price";
                tone = HudColors.WARN;
            } else if (w.limit > 0) {
                verdict = String.format("Under the limit: %,d more can fill", w.left());
                tone = PlannerUi.READY;
            } else {
                verdict = "Buying (limit unknown)";
                tone = PlannerUi.MUTED;
            }
        } else {
            verdict = w.limit > 0 ? String.format("%,d more can be bought now", w.left()) : "";
            tone = PlannerUi.MUTED;
        }
        card.third(verdict, "");
        card.lower.setForeground(tone);
        if (offer != null) {
            String state = offer.buying() ? "Buying" : offer.state == net.runelite.api.GrandExchangeOfferState.BOUGHT ? "Bought"
                : "Cancelled";
            card.fourth(String.format("%s %,d/%,d", state, offer.sold, offer.total),
                offer.buying() && w.price > 0 && offer.price < w.price ? gp(offer.price) + " vs " + gp(w.price) : "@ " + gp(offer.price));
            if (offer.buying() && w.price > 0 && offer.price < w.price) {
                card.lastRight.setForeground(HudColors.WARN);
            }
        }
        if (w.limit > 0) {
            card.bar(new PlannerUi.Bar(Math.max(0.02, Math.min(1, (double) w.bought / w.limit)),
                w.atLimit() ? COSTS_RED : PlannerUi.GROWING));
        }
        image(card.icon, icons.item(w.item, 1, false), 1f);
        StringBuilder tip = new StringBuilder("<html><b>").append(PlannerUi.esc(w.name)).append("</b>");
        if (w.limit > 0) {
            tip.append("<br>Buy limit: ").append(String.format("%,d", w.limit)).append(" every 4 hours");
        }
        if (w.resetsAt > 0) {
            tip.append("<br>Bought in this window: ").append(String.format("%,d", w.bought)).append("; it ")
                .append(w.exact ? "resets at " : "resets by ").append(clock(w.resetsAt)).append(" (4 hours from its first buy)");
            if (!w.exact) {
                tip.append("<br><font color='#a0a0a0'>Its first buy filled while you were away (or before this was on), so "
                    + "it may reset sooner</font>");
            }
        }
        if (offer != null) {
            tip.append("<br>Your offer: ").append(String.format("%,d", offer.total)).append(" at ").append(String.format("%,d", offer.price))
                .append(" gp each, ").append(String.format("%,d", offer.sold)).append(" filled");
        }
        if (w.price > 0) {
            tip.append("<br>Grand Exchange price now: ").append(String.format("%,d", w.price)).append(" gp");
        }
        tip.append("<br><font color='#a0a0a0'>From your offers as they fill (the game keeps no times for trades)</font></html>");
        return card.interactive(null, null, tip.toString());
    }

    private static final Color COSTS_RED = new Color(230, 110, 100);

    // "3:42 PM", today's clock
    private static String clock(long millis) {
        return java.time.format.DateTimeFormatter.ofPattern("h:mm a").format(java.time.LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(millis), java.time.ZoneId.systemDefault()));
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
                long left = timer.leftNow(System.currentTimeMillis());
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
            + "<br>When done: " + timers.soundOf(timer) + "<br><font color='#a0a0a0'>Right-click to change or delete it.</font></html>");
    }

    private void addTimer() {
        JTextField name = new JTextField();
        JTextField length = new JTextField("00:00:00");
        javax.swing.JComboBox<String> alert = alertChoice(null);
        javax.swing.JSpinner times = new javax.swing.JSpinner(new javax.swing.SpinnerNumberModel(config.timerSoundRepeats(), 1, 5, 1));
        if (form("Add a timer", new String[]{"Name", "Length (HH:MM:SS)", "When it's done", "Times (a sound)"},
            name, length, alert, times)) {
            long seconds = PlannerTimers.parseLength(length.getText());
            if (name.getText().trim().isEmpty() || seconds <= 0) {
                JOptionPane.showMessageDialog(this, "Give it a name and a length: HH:MM:SS (01:30:00), MM:SS (04:30) or minutes (90).");
                return;
            }
            timers.add(name.getText().trim(), seconds, chosenSound(alert), (Integer) times.getValue());
        }
    }

    private void editTimer(PlannerTimers.Timer timer) {
        JTextField name = new JTextField(timer.name);
        JTextField length = new JTextField(PlannerTimers.hms(timer.seconds));
        javax.swing.JComboBox<String> alert = alertChoice(timer.sound);
        javax.swing.JSpinner times = new javax.swing.JSpinner(new javax.swing.SpinnerNumberModel(
            timer.repeats > 0 ? timer.repeats : config.timerSoundRepeats(), 1, 5, 1));
        if (form("Edit " + timer.name, new String[]{"Name", "Length (HH:MM:SS)", "When it's done", "Times (a sound)"},
            name, length, alert, times)) {
            long seconds = PlannerTimers.parseLength(length.getText());
            if (!name.getText().trim().isEmpty() && seconds > 0) {
                timers.edit(timer.id, name.getText().trim(), seconds, chosenSound(alert), (Integer) times.getValue());
            }
        }
    }

    // The timer's alert: the Ready Sound setting, the voice line or a game sound; picking one plays it
    private javax.swing.JComboBox<String> alertChoice(String current) {
        javax.swing.JComboBox<String> box = new javax.swing.JComboBox<>();
        box.addItem("Default (" + config.timerSound() + ")");
        for (PlannerConfig.TimerSound sound : PlannerConfig.TimerSound.values()) {
            box.addItem(sound.toString());
        }
        int index = 0;
        PlannerConfig.TimerSound[] all = PlannerConfig.TimerSound.values();
        for (int i = 0; i < all.length; i++) {
            if (all[i].name().equals(current)) {
                index = i + 1;
            }
        }
        box.setSelectedIndex(index);
        box.addActionListener(e -> {
            int picked = box.getSelectedIndex();
            timers.preview(picked <= 0 ? config.timerSound() : all[picked - 1]);
        });
        return box;
    }

    private static String chosenSound(javax.swing.JComboBox<String> box) {
        int picked = box.getSelectedIndex();
        return picked <= 0 ? null : PlannerConfig.TimerSound.values()[picked - 1].name();
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

    /**
     * A list of sources (monster or chest cards, most valuable first) over your recent drops; click one (or one of its
     * recent drops) for its own page: its numbers, this session beside all time, its best drop and every item it has
     * given with how often.
     */
    private final class DropsPage extends Page {
        // The monster or chest whose page is open (null: the list)
        private String open;
        // Its page lists this session's items rather than all time's
        private boolean sessionItems;

        DropsPage() {
            super("drops", "Drops: loot by monster", PlannerUi.Shape.BAG);
        }

        @Override
        boolean shown() {
            return config.plannerShowDrops();
        }

        void open(String source) {
            open = source;
            sessionItems = false;
            refresh();
            SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(0));
        }

        @Override
        Built build() {
            long valuable = config.plannerValuableDrop();
            Color highlight = config.plannerValuableColor();
            if (open != null) {
                DropTracker.Source all = drops.source(open);
                if (all != null) {
                    return sourcePage(open, all, drops.sessionSource(open), valuable, highlight);
                }
                open = null;
            }
            List<Map.Entry<String, DropTracker.Source>> sources = drops.sources();
            List<DropTracker.Drop> recent = drops.recent();
            int shownRecent = Math.min(recent.size(), config.plannerRecentDrops());
            long total = 0;
            StringBuilder key = new StringBuilder("list|").append(valuable).append(highlight).append(shownRecent).append('|');
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
                String summary = sources.isEmpty() ? "" : gp(sum) + " gp · " + sources.size() + (sources.size() == 1 ? " source" : " sources");
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
                            open = null;
                            drops.resetAll();
                        }
                    })));
                for (Map.Entry<String, DropTracker.Source> entry : sources) {
                    card(sourceCard(entry.getKey(), entry.getValue()));
                }
                if (shownRecent > 0) {
                    add(PlannerUi.group("Recent", ""));
                    for (int i = 0; i < shownRecent; i++) {
                        card(recentCard(recent.get(i), valuable, highlight));
                    }
                }
            });
        }

        // One monster's or chest's own page
        private Built sourcePage(String name, DropTracker.Source all, DropTracker.Source now, long valuable, Color highlight) {
            DropTracker.Source shown = sessionItems ? now : all;
            long minute = System.currentTimeMillis() / 60_000;
            StringBuilder key = new StringBuilder("page|").append(name).append(valuable).append(highlight).append(sessionItems)
                .append(minute).append('|').append(all.count).append(all.value).append(all.dry).append(all.items.size());
            if (now != null) {
                key.append('|').append(now.count).append(now.value);
            }
            return new Built(key.toString(), () -> {
                boolean opened = all.opened(name);
                String kills = opened ? "Opens" : "Kills";
                // (no summary: the name gets the room, and the value is in the tiles)
                add(PlannerUi.backHeader(name, "Back to all drops", () -> open(null), new JLabel(""),
                    new PlannerUi.IconButton(new PlannerUi.Glyph(PlannerUi.Shape.TRASH, 14, PlannerUi.MUTED), "Reset " + name,
                        () -> resetSource(name))));

                JPanel tiles = new JPanel(new GridLayout(2, 2, PlannerUi.GAP, PlannerUi.GAP));
                tiles.setBackground(PlannerUi.BACKGROUND);
                tiles.add(tile(kills, String.format("%,d", all.count), (opened ? "Times opened" : "Kills") + " counted"));
                tiles.add(tile("Value", gp(all.value) + " gp", String.format("%,d", all.value) + " gp, Grand Exchange"));
                tiles.add(tile(opened ? "Per open" : "Per kill", gp(all.count > 0 ? all.value / all.count : 0) + " gp",
                    "Average Grand Exchange value " + (opened ? "an open" : "a kill")));
                String dryTip = "<html>" + (opened ? "Opens" : "Kills") + " since a drop worth " + gp(valuable) + " or more (Valuable Drop From)"
                    + (all.partlyRated() ? "<br><font color='#a0a0a0'>Counted from when the Planner began keeping it.</font>" : "")
                    + "</html>";
                String unit = opened ? all.dry == 1 ? " open" : " opens" : all.dry == 1 ? " kill" : " kills";
                // (saved before this was kept and none since: not known)
                boolean unknown = !all.valuableSeen && all.partlyRated();
                tiles.add(tile(all.valuableSeen || unknown ? "Since valuable" : "No valuable yet",
                    unknown ? "-" : String.format("%,d", all.dry) + unit, dryTip));
                card(tiles);

                if (all.best != null) {
                    add(PlannerUi.group("Best drop", ""));
                    card(bestCard(all.best, valuable, highlight));
                }

                add(PlannerUi.group("Session vs all time", ""));
                card(compare(all, now, opened));

                List<Map.Entry<String, long[]>> items = shown == null ? new ArrayList<>() : new ArrayList<>(shown.items.entrySet());
                items.sort((a, b) -> Long.compare(b.getValue()[DropTracker.VALUE], a.getValue()[DropTracker.VALUE]));
                JLabel toggle = PlannerUi.linkLabel(sessionItems ? "Show all time" : "Show this session",
                    sessionItems ? "List every item it has given" : "List what it has given this session", () -> {
                        sessionItems = !sessionItems;
                        refresh();
                    });
                add(PlannerUi.group(sessionItems ? "Items this session" : "Items", "", toggle));
                if (items.isEmpty()) {
                    add(PlannerUi.empty("Nothing from " + name + " yet this session."));
                }
                for (Map.Entry<String, long[]> item : items) {
                    card(itemRow(item.getKey(), item.getValue(), shown, opened, valuable, highlight));
                }
            });
        }

        private void resetSource(String name) {
            if (JOptionPane.showConfirmDialog(this.column, "Clear everything tracked for " + name + "?", "Reset " + name,
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                open = null;
                drops.reset(name);
            }
        }

        // A monster or chest on the list: its picture, kills and value
        private JComponent sourceCard(String name, DropTracker.Source source) {
            boolean opened = source.opened(name);
            String count = String.format("%,d", source.count) + (opened ? source.count == 1 ? " open" : " opens"
                : source.count == 1 ? " kill" : " kills");
            PlannerUi.Card card = new PlannerUi.Card(name, count + (source.count > 1 ? " · " + gp(source.value / source.count) + " ea" : ""),
                gp(source.value) + " gp");
            card.right.setForeground(Color.WHITE);
            image(card.icon, sourcePicture(name), 1f);
            JPopupMenu menu = new JPopupMenu();
            menu.add(item("Open " + name, () -> open(name)));
            menu.add(item("Reset " + name + "...", () -> resetSource(name)));
            return card.interactive(() -> open(name), menu, "<html>" + PlannerUi.esc(name) + ": " + count + ", "
                + String.format("%,d", source.value) + " gp" + (source.count > 1 ? " (" + gp(source.value / source.count)
                + (opened ? " an open)" : " a kill)") : "") + "<br><font color='#a0a0a0'>Click for its drops.</font></html>");
        }

        private JComponent recentCard(DropTracker.Drop drop, long valuable, Color highlight) {
            boolean big = drop.value >= valuable;
            PlannerUi.Card card = new PlannerUi.Card(drop.item + (drop.quantity > 1 ? " x " + String.format("%,d", drop.quantity) : ""),
                drop.source, gp(drop.value) + " gp");
            if (big) {
                card.title.setForeground(highlight);
                card.right.setForeground(highlight);
            }
            image(card.icon, itemPicture(drop.item, drop.id, drop.quantity, false), 1f);
            return card.interactive(() -> open(drop.source), null, "<html>" + PlannerUi.esc(drop.source) + ": "
                + PlannerUi.esc(drop.item) + " x " + String.format("%,d", drop.quantity) + ", " + String.format("%,d", drop.value)
                + " gp<br><font color='#a0a0a0'>Click for " + PlannerUi.esc(drop.source) + "'s drops.</font></html>");
        }
    }

    // Its single most valuable drop, with when
    private JComponent bestCard(DropTracker.Drop best, long valuable, Color highlight) {
        PlannerUi.Card card = new PlannerUi.Card(best.item + (best.quantity > 1 ? " x " + String.format("%,d", best.quantity) : ""),
            ago(best.at, System.currentTimeMillis() / 1000), gp(best.value) + " gp");
        if (best.value >= valuable) {
            card.title.setForeground(highlight);
            card.right.setForeground(highlight);
        }
        image(card.icon, itemPicture(best.item, best.id, best.quantity, false), 1f);
        return card.interactive(null, null, best.item + " x " + String.format("%,d", best.quantity) + ": "
            + String.format("%,d", best.value) + " gp, its most valuable single drop");
    }

    // This session beside all time: kills, value, per kill
    private static JComponent compare(DropTracker.Source all, DropTracker.Source session, boolean opened) {
        JPanel table = new JPanel(new GridLayout(4, 3, 4, 2));
        table.setBackground(PlannerUi.CARD);
        table.setBorder(new EmptyBorder(5, 8, 5, 8));
        long[] now = session == null ? new long[2] : new long[]{session.count, session.value};
        long[] ever = {all.count, all.value};
        table.add(PlannerUi.label("", PlannerUi.MUTED));
        table.add(right(PlannerUi.label("Session", PlannerUi.MUTED)));
        table.add(right(PlannerUi.label("All time", PlannerUi.MUTED)));
        String[] rows = {opened ? "Opens" : "Kills", "Value", opened ? "Per open" : "Per kill"};
        for (int row = 0; row < rows.length; row++) {
            table.add(PlannerUi.label(rows[row], ColorScheme.LIGHT_GRAY_COLOR));
            for (long[] figures : new long[][]{now, ever}) {
                String text = row == 0 ? String.format("%,d", figures[0]) : row == 1 ? gp(figures[1])
                    : figures[0] > 0 ? gp(figures[1] / figures[0]) : "-";
                table.add(right(PlannerUi.label(text, Color.WHITE)));
            }
        }
        PlannerUi.tips(table, "<html>This session: since the client started (kept until you close it).<br>All time: saved "
            + "with this account.</html>");
        return table;
    }

    private static JLabel right(JLabel label) {
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        return label;
    }

    // One item a monster has given: its picture, name, how often (the drop rate seen), how many and its value
    private JComponent itemRow(String name, long[] item, DropTracker.Source source, boolean opened, long valuable, Color highlight) {
        long quantity = item[DropTracker.QUANTITY];
        long value = item[DropTracker.VALUE];
        long times = item[DropTracker.TIMES];
        // Valuable as one drop of it is (or, in older saves, as one of it is)
        long each = times > 0 ? value / times : quantity > 0 ? value / quantity : value;
        boolean big = each >= valuable;
        String rate = DropTracker.rateText(source, item, opened);
        PlannerUi.Card card = new PlannerUi.Card(name, "x " + String.format("%,d", quantity)
            + (times > 0 ? " · " + String.format("%,d", times) + (times == 1 ? " time" : " times") : ""), gp(value));
        card.corner.setText(rate);
        card.corner.setForeground(big ? highlight : ColorScheme.LIGHT_GRAY_COLOR);
        if (big) {
            card.title.setForeground(highlight);
            card.right.setForeground(highlight);
            card.setBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, highlight));
        }
        image(card.icon, itemPicture(name, (int) item[DropTracker.ID], quantity, false), 1f);
        String kills = opened ? "opens" : "kills";
        String how = times > 0 ? "<br>Dropped in " + String.format("%,d", times) + " of " + String.format("%,d", source.rated) + " " + kills
            + " (" + rate + ")" + (source.partlyRated() ? "<br><font color='#a0a0a0'>Counted from when the Planner began keeping "
            + "drop rates.</font>" : "")
            : "<br><font color='#a0a0a0'>How often isn't known yet: counted before the Planner kept drop rates.</font>";
        return card.interactive(null, null, "<html>" + PlannerUi.esc(name) + " x " + String.format("%,d", quantity) + how
            + "<br>GE: " + String.format("%,d", value) + " gp" + (quantity > 1 && value > 0 ? " (" + gp(value / quantity) + " each)" : "")
            + "</html>");
    }

    /** "just now", "5m ago", "3h ago", "2 days ago". */
    static String ago(long at, long now) {
        if (at <= 0) {
            return "";
        }
        long seconds = Math.max(0, now - at);
        if (seconds < 60) {
            return "just now";
        }
        if (seconds < 3600) {
            return seconds / 60 + "m ago";
        }
        if (seconds < PlannerChores.DAY) {
            return seconds / 3600 + "h ago";
        }
        long days = seconds / PlannerChores.DAY;
        return days == 1 ? "yesterday" : days + " days ago";
    }

    // A chest's or casket's own picture (when it's an item, like clue caskets), or a tile with a monster's initials
    private BufferedImage sourcePicture(String name) {
        int id = sourceItem(name);
        id = id > 0 ? id : icons.itemId(name);
        BufferedImage picture = id > 0 ? icons.item(id, 1, false) : null;
        return picture != null ? picture : sourceTile(name);
    }

    /** The item that pictures a kind of loot that isn't a monster's (clue caskets by tier, key chests), or -1. */
    static int sourceItem(String name) {
        String lower = name == null ? "" : name.toLowerCase();
        if (lower.contains("clue") || lower.contains("casket")) {
            String[] tiers = {"beginner", "easy", "medium", "hard", "elite", "master"};
            int[] caskets = {ItemID.TRAIL_REWARD_CASKET_BEGINNER, ItemID.TRAIL_REWARD_CASKET_EASY, ItemID.TRAIL_REWARD_CASKET_MEDIUM,
                ItemID.TRAIL_REWARD_CASKET_HARD, ItemID.TRAIL_REWARD_CASKET_ELITE, ItemID.TRAIL_REWARD_CASKET_MASTER};
            for (int i = 0; i < tiers.length; i++) {
                if (lower.contains(tiers[i])) {
                    return caskets[i];
                }
            }
        }
        if (lower.startsWith("crystal chest")) {
            return ItemID.CRYSTAL_KEY;
        }
        if (lower.startsWith("muddy chest")) {
            return ItemID.MUDDY_KEY;
        }
        return -1;
    }

    // A rounded tile with up to two initials ("AD" for Abyssal demon, "Vo" for Vorkath), tinted from the name
    static BufferedImage sourceTile(String name) {
        BufferedImage out = new BufferedImage(PlannerUi.ICON_W, PlannerUi.ICON_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        float hue = ((name == null ? 0 : name.hashCode()) & 0xffff) / 65535f;
        g.setColor(Color.getHSBColor(hue, 0.35f, 0.32f));
        g.fillRoundRect(4, 2, 28, 28, 8, 8);
        g.setColor(Color.getHSBColor(hue, 0.30f, 0.45f));
        g.drawRoundRect(4, 2, 27, 27, 8, 8);
        String text = sourceInitials(name);
        g.setFont(FontManager.getRunescapeBoldFont());
        FontMetrics metrics = g.getFontMetrics();
        int x = 18 - metrics.stringWidth(text) / 2;
        int y = 16 + (metrics.getAscent() - metrics.getDescent()) / 2;
        g.setColor(new Color(0, 0, 0, 120));
        g.drawString(text, x + 1, y + 1);
        g.setColor(new Color(235, 235, 235));
        g.drawString(text, x, y);
        g.dispose();
        return out;
    }

    /** "AD" for Abyssal demon, "Vo" for Vorkath, "?" for nothing. */
    static String sourceInitials(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "?";
        }
        String[] words = name.replaceAll("\\(.*?\\)", " ").trim().split("[\\s-]+");
        if (words.length > 1 && !words[1].isEmpty() && Character.isLetterOrDigit(words[1].charAt(0))) {
            return ("" + words[0].charAt(0) + words[1].charAt(0)).toUpperCase();
        }
        String word = words[0];
        return word.length() == 1 ? word.toUpperCase() : Character.toUpperCase(word.charAt(0)) + word.substring(1, 2).toLowerCase();
    }

    // An item's picture (by its id, or by its name in older saves; with the quantity on it), or a tile with its initials
    private BufferedImage itemPicture(String name, int itemId, long quantity, boolean showQuantity) {
        int id = itemId > 0 ? itemId : "Coins".equalsIgnoreCase(name) ? ItemID.COINS : icons.itemId(name);
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
    static void focusBorder(JComponent field, JPanel box) {
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
    static final class Hinted extends JTextField {
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

    // ---- Calculator ----

    /** The CALCULATOR tab: its parts above the list stay while you type ({@link PlannerCalculatorView}). */
    private final class CalculatorPage extends Page {
        final PlannerCalculatorView view;

        CalculatorPage(PlannerCalculatorView view) {
            super("calculator", "Calculator: actions to your target level", PlannerUi.Shape.CALCULATOR);
            this.view = view;
            target = view.list;
            view.addTo(column, this::refresh);
            // The plan bar stays under the scrolling list, so you can compare while you scroll
            JPanel both = new JPanel(new BorderLayout(0, 6));
            both.setBackground(PlannerUi.BACKGROUND);
            both.add(scroll, BorderLayout.CENTER);
            both.add(view.dock, BorderLayout.SOUTH);
            body = both;
        }

        @Override
        boolean shown() {
            return config.plannerShowCalculator();
        }

        @Override
        void refresh() {
            view.update();
            super.refresh();
            column.revalidate();
            column.repaint();
        }

        @Override
        Built build() {
            return new Built(view.key(), view::fill);
        }
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
    static void image(JLabel label, BufferedImage picture, float alpha) {
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
