package com.snuffys.planner;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Snuffy's Planner's sidebar tab: TODAY, WEEKLY, TIMERS, SLAYER, DROPS and NOTES & TO-DO, each under a heading you
 * click to fold it (like the Skilling Helper's GOALS and THIS SESSION), and each one can be hidden in the settings.
 * A section is only rebuilt when what it shows changes; a timer redraws it each second for the countdowns.
 */
class PlannerPanel extends PluginPanel {

    private static final String GROUP = PlannerChores.GROUP;
    private static final Color ROW = ColorScheme.DARKER_GRAY_COLOR;
    private static final Color SUBTLE = new Color(180, 180, 180);

    private final PlannerConfig config;
    private final ConfigManager configManager;
    private final PlannerChores chores;
    private final PlannerTimers timers;
    private final FarmingTracker farming;
    private final PlannerSlayer slayer;
    private final DropTracker drops;
    private final PlannerNotes notes;

    private final JPanel content = new JPanel();
    private final Section today = new Section("TODAY", "today");
    private final Section weekly = new Section("WEEKLY", "weekly");
    private final Section timerSection = new Section("TIMERS", "timers");
    private final Section slayerSection = new Section("SLAYER", "slayer");
    private final Section dropSection = new Section("DROPS", "drops");
    private final Section noteSection = new Section("NOTES & TO-DO", "notes");
    private final JLabel empty = small("Log in to fill your Planner.", Color.GRAY);
    // The notes box stays put while the to-do list above it is rebuilt, so typing isn't interrupted
    private final JTextArea notesBox = new JTextArea(6, 10);
    private final JPanel todoList = new JPanel();
    private final JTextField todoField = new JTextField();
    private String notesProfile;
    private String layout = "";
    // Sources whose items are shown (clicked open)
    private final Set<String> openSources = new HashSet<>();
    private final Timer clock = new Timer(1000, e -> refresh());
    private final Timer notesSave = new Timer(1000, e -> saveNotes());

    PlannerPanel(PlannerConfig config, ConfigManager configManager, PlannerChores chores, PlannerTimers timers,
                 FarmingTracker farming, PlannerSlayer slayer, DropTracker drops, PlannerNotes notes) {
        super();
        this.config = config;
        this.configManager = configManager;
        this.chores = chores;
        this.timers = timers;
        this.farming = farming;
        this.slayer = slayer;
        this.drops = drops;
        this.notes = notes;

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        wrapper.add(content, BorderLayout.NORTH);
        JLabel title = new JLabel("Snuffy's Planner", SwingConstants.CENTER);
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(Color.WHITE);
        title.setBorder(new EmptyBorder(0, 0, 8, 0));
        add(title, BorderLayout.NORTH);
        add(wrapper, BorderLayout.CENTER);

        buildNotesBox();
        notesSave.setRepeats(false);
        clock.start();
        refresh();
    }

    void stop() {
        clock.stop();
        notesSave.stop();
        saveNotes();
    }

    /** Redraws what changed. On the Swing thread. */
    void refresh() {
        boolean loggedIn = configManager.getRSProfileKey() != null;
        String profile = configManager.getRSProfileKey();
        if (profile != null && !profile.equals(notesProfile)) {
            notesProfile = profile;
            notesBox.setText(notes.notes());
        }
        today.update(config.plannerShowToday() && loggedIn, () -> chores(false));
        weekly.update(config.plannerShowWeekly() && loggedIn, () -> chores(true));
        timerSection.update(config.plannerShowTimers() && loggedIn, this::timers);
        slayerSection.update(config.plannerShowSlayer() && loggedIn, this::slayer);
        dropSection.update(config.plannerShowDrops() && loggedIn, this::drops);
        noteSection.update(config.plannerShowNotes() && loggedIn, this::notesSection);
        String now = loggedIn + "|" + today.shown + weekly.shown + timerSection.shown + slayerSection.shown + dropSection.shown
            + noteSection.shown;
        if (!now.equals(layout)) {
            layout = now;
            content.removeAll();
            if (!loggedIn) {
                content.add(empty);
            }
            for (Section section : new Section[]{today, weekly, timerSection, slayerSection, dropSection, noteSection}) {
                if (section.shown) {
                    content.add(section.heading);
                    content.add(section.body);
                }
            }
            content.revalidate();
            content.repaint();
        }
    }

    // ---- Sections ----

    /** What a section shows: its heading's extra words, a key that changes when the body must be redrawn, and the body. */
    private static final class Built {
        final String headingExtra;
        final String key;
        final Runnable fill;

        Built(String headingExtra, String key, Runnable fill) {
            this.headingExtra = headingExtra;
            this.key = key;
            this.fill = fill;
        }
    }

    /** A heading you click to fold, and the rows under it. */
    private final class Section {
        final String name;
        final String id;
        final JLabel heading;
        final JPanel body = new JPanel();
        boolean shown;
        String key = null;

        Section(String name, String id) {
            this.name = name;
            this.id = id;
            heading = new JLabel(name, SwingConstants.CENTER);
            heading.setFont(FontManager.getRunescapeSmallFont());
            heading.setForeground(Color.GRAY);
            heading.setBorder(new EmptyBorder(6, 0, 4, 0));
            heading.setAlignmentX(Component.CENTER_ALIGNMENT);
            heading.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            heading.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        setFolded(!folded());
                        key = null;
                        refresh();
                    }
                }
            });
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            body.setBackground(ColorScheme.DARK_GRAY_COLOR);
            body.setAlignmentX(Component.CENTER_ALIGNMENT);
        }

        boolean folded() {
            return Boolean.parseBoolean(configManager.getConfiguration(GROUP, "plannerCollapsed." + id));
        }

        void setFolded(boolean folded) {
            if (folded) {
                configManager.setConfiguration(GROUP, "plannerCollapsed." + id, true);
            } else {
                configManager.unsetConfiguration(GROUP, "plannerCollapsed." + id);
            }
        }

        void update(boolean show, Supplier<Built> build) {
            shown = show;
            if (!show) {
                return;
            }
            boolean folded = folded();
            Built built = build.get();
            // "- TODAY" when open, "+ TODAY" when folded (plain signs: the game font has no arrows)
            String text = (folded ? "+ " : "- ") + name + (built.headingExtra == null ? "" : "  " + built.headingExtra);
            heading.setText(text);
            heading.setToolTipText(folded ? "Click to show " + name : "Click to hide " + name);
            heading.setMaximumSize(new Dimension(Integer.MAX_VALUE, heading.getPreferredSize().height));
            String fresh = folded + "|" + built.key;
            if (fresh.equals(key)) {
                return;
            }
            key = fresh;
            body.removeAll();
            if (!folded) {
                current = body;
                built.fill.run();
            }
            body.revalidate();
            body.repaint();
        }
    }

    // The body being filled
    private JPanel current;

    private void addRow(JComponent row) {
        current.add(wrap(row));
    }

    // ---- TODAY / WEEKLY ----

    private Built chores(boolean weeklyList) {
        long now = System.currentTimeMillis() / 1000;
        long next = weeklyList ? PlannerChores.lastWeeklyReset(now) + PlannerChores.WEEK : PlannerChores.lastDailyReset(now) + PlannerChores.DAY;
        List<PlannerChores.Row> rows = chores.rows(weeklyList);
        int hidden = chores.hiddenCount(weeklyList);
        StringBuilder key = new StringBuilder().append(hidden).append('|');
        int left = 0;
        for (PlannerChores.Row row : rows) {
            key.append(row.id).append(row.name).append(row.note).append(row.done).append(row.detail).append(';');
            left += row.done ? 0 : 1;
        }
        long[] kingdom = weeklyList && config.plannerShowKingdom() ? chores.kingdom() : null;
        String contract = weeklyList && config.plannerShowContract() ? chores.contract() : null;
        String contractStatus = contract == null || "done".equals(contract) ? null : contractPatch(contract);
        if (kingdom != null) {
            key.append(kingdom[0]).append(kingdom[1]).append(daysAgo(kingdom[2], now));
        }
        key.append(contract).append(contractStatus);
        String extra = (left > 0 ? left + " left, " : "") + "reset in " + formatDuration(next - now);
        return new Built(extra, key.toString(), () -> {
            for (PlannerChores.Row row : rows) {
                addRow(choreRow(row));
            }
            if (rows.isEmpty()) {
                addRow(small(weeklyList ? "No weeklies: add your own below." : "No dailies yet: add your own below.", Color.GRAY));
            }
            if (kingdom != null) {
                int percent = PlannerChores.approvalPercent(kingdom[0]);
                addRow(infoRow("Kingdom of Miscellania", percent + "% approval, coffer " + gp(kingdom[1]),
                    percent < 50 ? HudColors.WARN : Color.LIGHT_GRAY,
                    "As last seen " + daysAgo(kingdom[2], now) + ". Approval drops each day you don't help out there."));
            }
            if (contract != null) {
                addRow(infoRow("Farming contract", "done".equals(contract) ? "done: claim from Guinevere" : contract
                    + (contractStatus != null ? " (" + contractStatus + ")" : ""), "done".equals(contract) ? HudColors.GOOD : Color.LIGHT_GRAY,
                    "Guinevere at the Farming Guild; the patch as you last saw it"));
            }
            JPanel buttons = buttons(button("Add...", () -> addChore(weeklyList)));
            if (hidden > 0) {
                buttons.add(button("Show hidden (" + hidden + ")", () -> chores.showHidden(weeklyList)));
            }
            current.add(buttons);
        });
    }

    // The contract crop's patch at the Farming Guild, as last seen in TIMERS
    private String contractPatch(String crop) {
        long now = System.currentTimeMillis() / 1000;
        for (FarmingTracker.Record patch : farming.records()) {
            if (patch.title.startsWith("Farming Guild") && patch.crop.equalsIgnoreCase(crop)) {
                return patchStatus(patch, now);
            }
        }
        return "not planted";
    }

    private JComponent choreRow(PlannerChores.Row row) {
        JCheckBox box = new JCheckBox("<html>" + esc(row.name) + (row.detail != null ? " <font color='#a0a0a0'>" + esc(row.detail)
            + "</font>" : "") + "</html>", row.done);
        box.setFont(FontManager.getRunescapeSmallFont());
        box.setForeground(row.done ? Color.GRAY : Color.WHITE);
        box.setBackground(ROW);
        box.setFocusPainted(false);
        box.addActionListener(e -> chores.setDone(row.id, box.isSelected()));
        String how = row.custom ? "Your own: tick it by hand; it unticks at the " + (row.weekly ? "weekly" : "daily") + " reset"
            : row.fromGame ? "Ticks itself from the game (or tick it by hand)" : "Tick it by hand";
        box.setToolTipText("<html>" + (row.note != null ? esc(row.note) + "<br>" : "") + "<font color='#a0a0a0'>" + how
            + ". Right-click to edit or " + (row.custom ? "delete" : "hide") + ".</font></html>");
        JPopupMenu menu = new JPopupMenu();
        menu.add(item("Edit...", () -> editChore(row)));
        menu.add(item(row.custom ? "Delete" : "Hide", () -> chores.remove(row.id)));
        box.setComponentPopupMenu(menu);
        return box;
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

    // ---- TIMERS ----

    private Built timers() {
        long now = System.currentTimeMillis() / 1000;
        List<FarmingTracker.Record> patches = config.farmingPatchList() ? farming.records() : java.util.Collections.emptyList();
        long birdhouses = timers.birdhousesFullAt();
        List<PlannerTimers.Timer> list = timers.timers();
        StringBuilder key = new StringBuilder();
        for (FarmingTracker.Record patch : patches) {
            key.append(patch.key).append(patch.crop).append(patchStatus(patch, now)).append(';');
        }
        key.append(birdhouses > 0 ? birdhouseStatus(birdhouses, now) : "").append('|');
        int running = 0;
        for (PlannerTimers.Timer timer : list) {
            key.append(timer.id).append(timer.name).append(timer.left(now)).append(timer.endsAt > 0).append(';');
            running += timer.running(now) ? 1 : 0;
        }
        return new Built(running > 0 ? running + " running" : null, key.toString(), () -> {
            if (birdhouses > 0) {
                String status = birdhouseStatus(birdhouses, now);
                addRow(infoRow("Bird houses", status, "ready".equals(status) ? HudColors.GOOD : Color.LIGHT_GRAY,
                    "Fossil Island bird houses: full 50 minutes after you seed them"));
            }
            for (FarmingTracker.Record patch : patches) {
                addRow(patchRow(patch, now));
            }
            for (PlannerTimers.Timer timer : list) {
                addRow(timerRow(timer, now));
            }
            if (patches.isEmpty() && birdhouses == 0 && list.isEmpty()) {
                addRow(small("Visit your farming patches, seed bird houses or add a timer.", Color.GRAY));
            }
            current.add(buttons(button("Add timer...", this::addTimer)));
        });
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

    // One patch: where on the left, crop and when on the right; right-click to forget it
    private JComponent patchRow(FarmingTracker.Record patch, long now) {
        String status = patchStatus(patch, now);
        Color colour = "ready".equals(status) ? HudColors.GOOD : "dead".equals(status) ? HudColors.BAD
            : "diseased".equals(status) ? HudColors.WARN : Color.LIGHT_GRAY;
        JPanel row = infoRow("<html>" + esc(patch.title) + "<br><font color='#b4b4b4'>" + esc(patch.crop) + "</font></html>", status,
            colour, patch.title + ": " + patch.crop + " (as you last saw it)" + (patch.slack > 0
                ? "; ~ until one of your patches has been seen growing, the time may be out by a growth cycle" : ""));
        JPopupMenu menu = new JPopupMenu();
        menu.add(item("Forget this patch", () -> farming.forget(patch.key)));
        row.setComponentPopupMenu(menu);
        return row;
    }

    private JComponent timerRow(PlannerTimers.Timer timer, long now) {
        boolean running = timer.running(now);
        boolean ended = timer.ended(now);
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(ROW);
        row.setBorder(new EmptyBorder(3, 6, 3, 4));
        JLabel name = small("<html>" + esc(timer.name) + "<br><font color='#b4b4b4'>"
            + (ended ? "done" : PlannerTimers.clock(timer.left(now))) + "</font></html>", Color.WHITE);
        JButton toggle = button(running ? "Stop" : ended ? "Again" : "Start", () -> {
            if (running) {
                timers.stop(timer.id);
            } else {
                timers.start(timer.id);
            }
        });
        row.add(name, BorderLayout.CENTER);
        row.add(toggle, BorderLayout.EAST);
        if (ended) {
            name.setForeground(HudColors.GOOD);
        }
        row.setToolTipText(timer.name + ": " + PlannerTimers.clock(timer.seconds) + ". Right-click to change or delete it");
        JPopupMenu menu = new JPopupMenu();
        menu.add(item("Edit...", () -> editTimer(timer)));
        if (ended) {
            menu.add(item("Reset", () -> timers.stop(timer.id)));
        }
        menu.add(item("Delete", () -> timers.delete(timer.id)));
        row.setComponentPopupMenu(menu);
        name.setComponentPopupMenu(menu);
        return row;
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

    // ---- SLAYER ----

    private Built slayer() {
        PlannerSlayer.Task task = slayer.task();
        String key = task == null ? "none" : task.encode();
        String extra = task != null && task.remaining > 0 ? task.remaining + " left" : null;
        return new Built(extra, key, () -> {
            if (task == null) {
                addRow(small("Log in to read your Slayer task.", Color.GRAY));
                return;
            }
            if (task.remaining > 0) {
                addRow(infoRow("<html>" + esc(task.name == null ? "Your task" : task.name)
                        + (task.area != null ? "<br><font color='#b4b4b4'>" + esc(task.area) + "</font>" : "") + "</html>",
                    task.remaining + (task.assigned > 0 ? " of " + task.assigned : "") + " left", Color.WHITE,
                    "Your Slayer task and kills left, from the game"));
            } else {
                addRow(infoRow("No task", "", Color.LIGHT_GRAY, "Get one from a Slayer master"));
            }
            addRow(infoRow("Points", String.format("%,d", task.points), Color.LIGHT_GRAY, "Slayer reward points"));
            addRow(infoRow("Streak", task.streak + (task.streak == 1 ? " task" : " tasks"), Color.LIGHT_GRAY,
                "Tasks done in a row (Krystilia and Mortimer keep their own)"));
        });
    }

    // ---- DROPS ----

    private Built drops() {
        List<Map.Entry<String, DropTracker.Source>> sources = drops.sources();
        List<DropTracker.Drop> recent = drops.recent();
        int shownRecent = Math.min(recent.size(), config.plannerRecentDrops());
        long valuable = config.plannerValuableDrop();
        Color highlight = config.plannerValuableColor();
        long total = 0;
        StringBuilder key = new StringBuilder().append(openSources).append(valuable).append(highlight).append(shownRecent).append('|');
        for (Map.Entry<String, DropTracker.Source> source : sources) {
            total += source.getValue().value;
            key.append(source.getKey()).append(source.getValue().count).append(source.getValue().value).append(';');
        }
        for (int i = 0; i < shownRecent; i++) {
            DropTracker.Drop drop = recent.get(i);
            key.append(drop.at).append(drop.item).append(drop.quantity).append(';');
        }
        long sum = total;
        return new Built(sum > 0 ? gp(sum) + " gp" : null, key.toString(), () -> {
            if (sources.isEmpty()) {
                addRow(small("Your kills' drops and other loot show here.", Color.GRAY));
                return;
            }
            for (Map.Entry<String, DropTracker.Source> entry : sources) {
                addRow(sourceRow(entry.getKey(), entry.getValue()));
                if (openSources.contains(entry.getKey())) {
                    entry.getValue().items.entrySet().stream()
                        .sorted((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]))
                        .limit(15)
                        .forEach(item -> addRow(itemRow(item.getKey(), item.getValue()[0], item.getValue()[1])));
                }
            }
            if (shownRecent > 0) {
                JLabel label = small("Recent", Color.GRAY);
                label.setBorder(new EmptyBorder(4, 0, 2, 0));
                current.add(label);
                for (int i = 0; i < shownRecent; i++) {
                    DropTracker.Drop drop = recent.get(i);
                    JPanel row = infoRow("<html>" + esc(drop.item) + (drop.quantity > 1 ? " x " + String.format("%,d", drop.quantity) : "")
                            + "<br><font color='#b4b4b4'>" + esc(drop.source) + "</font></html>", gp(drop.value),
                        drop.value >= valuable ? highlight : Color.LIGHT_GRAY, drop.source + ": " + drop.item);
                    if (drop.value >= valuable) {
                        ((JLabel) ((BorderLayout) row.getLayout()).getLayoutComponent(BorderLayout.CENTER)).setForeground(highlight);
                    }
                    addRow(row);
                }
            }
            current.add(buttons(button("Reset all", () -> {
                if (JOptionPane.showConfirmDialog(this, "Clear every drop you've tracked on this account?", "Reset all drops",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    openSources.clear();
                    drops.resetAll();
                }
            })));
        });
    }

    private JComponent sourceRow(String name, DropTracker.Source source) {
        boolean open = openSources.contains(name);
        JPanel row = infoRow("<html>" + (open ? "- " : "+ ") + esc(name) + "<br><font color='#b4b4b4'>" + String.format("%,d", source.count)
            + (source.count == 1 ? " kill/open" : " kills/opens") + "</font></html>", gp(source.value) + " gp", Color.WHITE,
            "Click to " + (open ? "hide" : "show") + " the items; right-click to reset");
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    if (!openSources.remove(name)) {
                        openSources.add(name);
                    }
                    refresh();
                }
            }
        });
        JPopupMenu menu = new JPopupMenu();
        menu.add(item("Reset " + name, () -> {
            openSources.remove(name);
            drops.reset(name);
        }));
        row.setComponentPopupMenu(menu);
        return row;
    }

    private JComponent itemRow(String item, long quantity, long value) {
        JPanel row = infoRow("   " + item + " x " + String.format("%,d", quantity), gp(value), Color.LIGHT_GRAY, item);
        row.setBackground(new Color(35, 35, 35));
        return row;
    }

    // ---- NOTES & TO-DO ----

    private void buildNotesBox() {
        notesBox.setLineWrap(true);
        notesBox.setWrapStyleWord(true);
        notesBox.setFont(FontManager.getRunescapeSmallFont());
        notesBox.setBackground(ROW);
        notesBox.setForeground(Color.WHITE);
        notesBox.setCaretColor(Color.WHITE);
        notesBox.setBorder(new EmptyBorder(4, 6, 4, 6));
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
        todoList.setLayout(new BoxLayout(todoList, BoxLayout.Y_AXIS));
        todoList.setBackground(ColorScheme.DARK_GRAY_COLOR);
        todoField.setToolTipText("A new to-do: type it and press Enter");
        todoField.addActionListener(e -> addTodo());
    }

    private void saveNotes() {
        if (notesProfile != null && notesProfile.equals(configManager.getRSProfileKey())) {
            notes.setNotes(notesBox.getText());
        }
    }

    private void addTodo() {
        String text = todoField.getText().trim();
        if (!text.isEmpty()) {
            notes.add(text);
            todoField.setText("");
        }
    }

    private Built notesSection() {
        List<PlannerNotes.Todo> todos = notes.todos();
        StringBuilder key = new StringBuilder();
        int left = 0;
        for (PlannerNotes.Todo todo : todos) {
            key.append(todo.text).append(todo.done).append(';');
            left += todo.done ? 0 : 1;
        }
        String extra = left > 0 ? left + " to do" : null;
        boolean anyTicked = todos.size() > left;
        return new Built(extra, key.toString(), () -> {
            todoList.removeAll();
            for (int i = 0; i < todos.size(); i++) {
                todoList.add(wrap(todoRow(i, todos.get(i), todos.size())));
            }
            current.add(todoList);
            JPanel adder = new JPanel(new BorderLayout(4, 0));
            adder.setBackground(ColorScheme.DARK_GRAY_COLOR);
            adder.add(todoField, BorderLayout.CENTER);
            adder.add(button("Add", this::addTodo), BorderLayout.EAST);
            adder.setAlignmentX(Component.CENTER_ALIGNMENT);
            adder.setMaximumSize(new Dimension(Integer.MAX_VALUE, adder.getPreferredSize().height));
            current.add(adder);
            if (anyTicked) {
                current.add(buttons(button("Clear ticked", notes::clearDone)));
            }
            JLabel label = small("Notes", Color.GRAY);
            label.setBorder(new EmptyBorder(6, 0, 2, 0));
            current.add(label);
            JPanel box = new JPanel(new BorderLayout());
            box.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
            box.add(notesBox);
            box.setAlignmentX(Component.CENTER_ALIGNMENT);
            current.add(box);
        });
    }

    private JComponent todoRow(int index, PlannerNotes.Todo todo, int count) {
        JCheckBox box = new JCheckBox("<html>" + esc(todo.text) + "</html>", todo.done);
        box.setFont(FontManager.getRunescapeSmallFont());
        box.setForeground(todo.done ? Color.GRAY : Color.WHITE);
        box.setBackground(ROW);
        box.setFocusPainted(false);
        box.addActionListener(e -> notes.setDone(index, box.isSelected()));
        box.setToolTipText("Right-click to edit, move or delete");
        JPopupMenu menu = new JPopupMenu();
        menu.add(item("Edit...", () -> {
            JTextField text = new JTextField(todo.text);
            if (form("Edit to-do", new String[]{"To-do"}, text) && !text.getText().trim().isEmpty()) {
                notes.edit(index, text.getText().trim());
            }
        }));
        if (index > 0) {
            menu.add(item("Move up", () -> notes.move(index, -1)));
        }
        if (index < count - 1) {
            menu.add(item("Move down", () -> notes.move(index, 1)));
        }
        menu.add(item("Delete", () -> notes.delete(index)));
        box.setComponentPopupMenu(menu);
        return box;
    }

    // ---- Small pieces ----

    private static JLabel small(String text, Color colour) {
        JLabel label = new JLabel(text.startsWith("<html>") ? text : "<html>" + text + "</html>");
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(colour);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        return label;
    }

    // A row: text on the left, a value on the right
    private static JPanel infoRow(String left, String right, Color rightColour, String tooltip) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(ROW);
        row.setBorder(new EmptyBorder(3, 6, 3, 6));
        JLabel where = new JLabel(left);
        where.setFont(FontManager.getRunescapeSmallFont());
        where.setForeground(Color.WHITE);
        JLabel what = new JLabel(right, SwingConstants.RIGHT);
        what.setFont(FontManager.getRunescapeSmallFont());
        what.setForeground(rightColour);
        row.add(where, BorderLayout.CENTER);
        row.add(what, BorderLayout.EAST);
        row.setToolTipText(tooltip);
        return row;
    }

    // Full width, a small gap under it
    private static JComponent wrap(JComponent row) {
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
        wrap.setBorder(new EmptyBorder(0, 0, 3, 0));
        wrap.add(row);
        wrap.setAlignmentX(Component.CENTER_ALIGNMENT);
        wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height + 3));
        return wrap;
    }

    private static JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setFont(FontManager.getRunescapeSmallFont());
        button.setFocusPainted(false);
        button.setMargin(new java.awt.Insets(1, 6, 1, 6));
        button.addActionListener(e -> action.run());
        return button;
    }

    private static JPanel buttons(JButton... list) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
        row.setBackground(ColorScheme.DARK_GRAY_COLOR);
        for (JButton button : list) {
            row.add(button);
        }
        row.setAlignmentX(Component.CENTER_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
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
        return days <= 0 ? "today" : days == 1 ? "yesterday" : days + " days ago";
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

    private static String esc(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
