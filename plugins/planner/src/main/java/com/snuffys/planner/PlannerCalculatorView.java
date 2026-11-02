package com.snuffys.planner;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The Planner's CALCULATOR tab, drawn: a grid of skills, your current and target level and XP (from the game, and
 * typed over when you like; the target starts at your goal, else the next level) with Set as goal, the skill's XP
 * bonuses to tick, and a searchable list of its actions (or, in Banked view, what you own and the XP and level it gets
 * you). The parts above the list stay put while you type; the list is redrawn when what it shows changes. Swing
 * thread only.
 */
final class PlannerCalculatorView {

    /** A part of the page that's redrawn on its own (as tall as its contents, as wide as the list). */
    private static final class Slot extends JPanel {
        String key;

        Slot() {
            super(new BorderLayout());
            setBackground(PlannerUi.BACKGROUND);
            setAlignmentX(LEFT_ALIGNMENT);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        /** True (and remembers it) when the key changed. */
        boolean changed(String now) {
            if (now.equals(key)) {
                return false;
            }
            key = now;
            removeAll();
            return true;
        }
    }

    private static final Color FIELD = ColorScheme.DARK_GRAY_COLOR;
    private static final Color FIELD_LINE = new Color(62, 62, 62);
    private static final Color COSTS = new Color(230, 110, 100);
    private static final Color SUGGESTED = ColorScheme.PROGRESS_COMPLETE_COLOR;

    private final PlannerCalculator calc;
    private final CalculatorData data;
    private final RecipeData recipes;
    private final TrainingData training;
    private final PlannerPanel.Icons icons;

    private final Slot header = new Slot();
    private final Slot grid = new Slot();
    private final JPanel inputs = new JPanel(new BorderLayout());
    private final Slot bonusSlot = new Slot();
    private final Slot listHeader = new Slot();
    private final JPanel searchRow = new JPanel(new BorderLayout());
    /** Where the list goes (redrawn by the page, through {@link #build}). */
    final JPanel list = new JPanel();

    private final JLabel skillName = PlannerUi.bold("");
    private final JLabel skillIcon = new JLabel();
    private final JLabel skillLevel = PlannerUi.label("", PlannerUi.MUTED);
    private final JTextField currentLevel = field("Your level now");
    private final JTextField currentXp = field("Your XP now");
    private final JTextField targetLevel = field("The level to aim for");
    private final JTextField targetXp = field("The XP to aim for");
    private final JLabel goalText = PlannerUi.label("", PlannerUi.MUTED);
    private final JLabel goalLink;
    private final PlannerUi.Bar goalBar = new PlannerUi.Bar(0, PlannerUi.GROWING);
    private final PlannerPanel.Hinted search = new PlannerPanel.Hinted("Search actions");

    private Skill skill;
    // What you typed over the game's numbers (null: from the game, or the goal / next level)
    private Integer currentOverride;
    private Integer targetOverride;
    private boolean adjusting;
    // Typing redraws the list once it pauses
    private final Timer typed;
    private Runnable redraw = () -> { };

    PlannerCalculatorView(PlannerCalculator calc, CalculatorData data, RecipeData recipes, TrainingData training,
                          PlannerPanel.Icons icons) {
        this.calc = calc;
        this.data = data;
        this.recipes = recipes;
        this.training = training;
        this.icons = icons;
        typed = new Timer(250, e -> redraw.run());
        typed.setRepeats(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(PlannerUi.BACKGROUND);
        list.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        goalLink = PlannerUi.linkLabel("Set as goal", "Make this target the skill's goal (as the Skilling Helper's Set Goal "
            + "does): its goal bar, suggestions and banked XP follow it", this::setAsGoal);
        buildInputs();
        buildSearch();
    }

    /** Adds the page's parts to its column (once). {@code redraw} redraws the page. */
    void addTo(JPanel column, Runnable redraw) {
        this.redraw = redraw;
        column.add(header);
        column.add(grid);
        column.add(PlannerUi.spaced(inputs));
        column.add(bonusSlot);
        column.add(listHeader);
        column.add(PlannerUi.spaced(searchRow));
        column.add(list);
    }

    void stop() {
        typed.stop();
    }

    /** For the preview: shows this skill, view and search. */
    void preview(Skill picked, boolean banked, String text) {
        calc.setBanked(banked);
        calc.setSkill(picked);
        skill = picked;
        currentOverride = null;
        targetOverride = null;
        search.setText(text);
        typed.stop();
    }

    // ---- The numbers ----

    private List<Skill> skills() {
        return data.skills();
    }

    private Skill skill() {
        if (skill == null) {
            skill = calc.skill(skills());
        }
        return skill;
    }

    private int current() {
        if (currentOverride != null) {
            return currentOverride;
        }
        return Math.max(0, calc.xp(skill()));
    }

    private int target() {
        if (targetOverride != null) {
            return targetOverride;
        }
        int now = current();
        int goal = calc.goal(skill());
        if (goal > now) {
            return goal;
        }
        int level = CalculatorData.level(now, false);
        return level >= 99 ? Experience.MAX_SKILL_XP : Experience.getXpForLevel(level + 1);
    }

    private void pick(Skill picked) {
        skill = picked;
        currentOverride = null;
        targetOverride = null;
        calc.setSkill(picked);
        search.setText("");
        redraw.run();
    }

    // ---- The parts above the list (each refresh) ----

    /** Brings the parts above the list up to date (only redrawing what changed). */
    void update() {
        Skill shown = skill();
        if (shown == null) {
            if (header.changed("none")) {
                header.add(PlannerUi.header("Calculator", new JLabel("")), BorderLayout.CENTER);
                header.add(PlannerUi.empty("The calculator's data didn't load."), BorderLayout.SOUTH);
            }
            return;
        }
        int now = current();
        int target = target();
        int game = calc.xp(shown);
        int goal = calc.goal(shown);

        String summary = target > now ? PlannerPanel.gp(target - now) + " xp to go" : "Target reached";
        if (header.changed(summary)) {
            header.add(PlannerUi.header("Calculator", new JLabel(summary)), BorderLayout.CENTER);
        }

        StringBuilder levels = new StringBuilder(shown.name());
        for (Skill s : skills()) {
            levels.append(calc.xp(s) >= 0 ? CalculatorData.level(calc.xp(s), false) : 0).append(',');
        }
        if (grid.changed(levels.toString())) {
            grid.add(PlannerUi.spaced(skillGrid(shown)), BorderLayout.CENTER);
        }

        skillName.setText(shown.getName());
        image(skillIcon, icons.skill(shown, false));
        skillLevel.setText(game >= 0 ? "Level " + CalculatorData.level(game, false) + " · " + String.format("%,d", game) + " xp"
            : "Type your XP");
        adjusting = true;
        try {
            show(currentLevel, String.valueOf(CalculatorData.level(now, false)));
            show(currentXp, String.format("%,d", now));
            show(targetLevel, String.valueOf(CalculatorData.level(target, true)));
            show(targetXp, String.format("%,d", target));
        } finally {
            adjusting = false;
        }
        boolean isGoal = goal > 0 && goal == target;
        int targetLvl = CalculatorData.level(target, true);
        String targetText = target == Experience.getXpForLevel(Math.min(targetLvl, 126)) ? "level " + targetLvl : String.format("%,d xp", target);
        if (isGoal) {
            goalText.setText("Your goal: " + targetText);
            goalText.setForeground(Color.WHITE);
        } else if (goal > 0) {
            int goalLvl = CalculatorData.level(goal, true);
            goalText.setText("Goal: " + (goal == Experience.getXpForLevel(Math.min(goalLvl, 126)) ? "level " + goalLvl
                : String.format("%,d xp", goal)) + (goal <= now ? " (reached)" : ""));
            goalText.setForeground(PlannerUi.MUTED);
        } else {
            goalText.setText("No goal set");
            goalText.setForeground(PlannerUi.MUTED);
        }
        goalLink.setVisible(!isGoal && target > Math.max(0, game));
        int start = isGoal && calc.goalStart(shown) > 0 ? calc.goalStart(shown) : Experience.getXpForLevel(CalculatorData.level(now, false));
        double done = target > start ? (double) (now - start) / (target - start) : 1;
        goalBar.set(Math.max(0, Math.min(1, done)), done >= 1 ? PlannerUi.READY : PlannerUi.GROWING);

        CalculatorData.Calc skillCalc = data.calc(shown);
        Set<String> on = calc.bonuses(shown);
        StringBuilder bonusKey = new StringBuilder(shown.name()).append('|');
        on.forEach(id -> bonusKey.append(id).append(','));
        if (bonusSlot.changed(bonusKey.toString()) && !skillCalc.bonuses.isEmpty()) {
            bonusSlot.add(bonuses(shown, skillCalc, on), BorderLayout.CENTER);
        }

        boolean banked = calc.banked();
        if (listHeader.changed(banked + shown.name())) {
            JLabel toggle = PlannerUi.linkLabel(banked ? "All actions" : "Banked", banked ? "List every action to your target"
                : "What your bank and inventory get you: the XP and level from each action", () -> {
                    calc.setBanked(!banked);
                    redraw.run();
                });
            listHeader.add(PlannerUi.group(banked ? "Banked: what you own" : "Actions", "", toggle), BorderLayout.CENTER);
        }
    }

    private static void show(JTextField field, String text) {
        if (!field.isFocusOwner() && !field.getText().equals(text)) {
            field.setText(text);
        }
    }

    // Every skill the calculator has, as the Skills tab orders them: click one to pick it
    private JComponent skillGrid(Skill shown) {
        List<Skill> all = skills();
        int columns = 9;
        JPanel box = new JPanel(new GridLayout((all.size() + columns - 1) / columns, columns, 2, 2));
        box.setBackground(PlannerUi.CARD);
        box.setBorder(new EmptyBorder(4, 4, 4, 4));
        for (Skill s : all) {
            boolean picked = s == shown;
            JLabel cell = new JLabel();
            cell.setHorizontalAlignment(SwingConstants.CENTER);
            cell.setPreferredSize(new Dimension(20, 22));
            cell.setOpaque(true);
            cell.setBackground(picked ? new Color(70, 70, 70) : PlannerUi.CARD);
            cell.setBorder(picked ? BorderFactory.createMatteBorder(0, 0, 2, 0, ColorScheme.BRAND_ORANGE) : new EmptyBorder(0, 0, 2, 0));
            BufferedImage icon = icons.skill(s, true);
            if (icon != null) {
                cell.setIcon(new ImageIcon(picked ? icon : fade(icon, 0.7f)));
            }
            int xp = calc.xp(s);
            cell.setToolTipText(s.getName() + (xp >= 0 ? ": level " + CalculatorData.level(xp, false) : ""));
            cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            cell.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (s != skill) {
                        cell.setBackground(PlannerUi.CARD_HOVER);
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    cell.setBackground(s == skill ? new Color(70, 70, 70) : PlannerUi.CARD);
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (s != skill) {
                        pick(s);
                    }
                }
            });
            box.add(cell);
        }
        return box;
    }

    // The skill's name and level, the four boxes, the goal line and the bar
    private void buildInputs() {
        inputs.setBackground(PlannerUi.CARD);
        JPanel body = new JPanel(new BorderLayout(0, 6));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(6, 7, 7, 7));

        JPanel title = new JPanel(new BorderLayout(6, 0));
        title.setOpaque(false);
        skillIcon.setPreferredSize(new Dimension(25, 25));
        skillIcon.setHorizontalAlignment(SwingConstants.CENTER);
        title.add(skillIcon, BorderLayout.WEST);
        JPanel names = new JPanel(new GridLayout(2, 1));
        names.setOpaque(false);
        names.add(skillName);
        names.add(skillLevel);
        title.add(names, BorderLayout.CENTER);
        body.add(title, BorderLayout.NORTH);

        JPanel boxes = new JPanel(new GridLayout(2, 2, 6, 4));
        boxes.setOpaque(false);
        boxes.add(labelled("Current level", currentLevel));
        boxes.add(labelled("Current XP", currentXp));
        boxes.add(labelled("Target level", targetLevel));
        boxes.add(labelled("Target XP", targetXp));
        body.add(boxes, BorderLayout.CENTER);

        JPanel goal = new JPanel(new BorderLayout(4, 0));
        goal.setOpaque(false);
        goalText.setMinimumSize(new Dimension(0, 0));
        goal.add(goalText, BorderLayout.CENTER);
        goal.add(goalLink, BorderLayout.EAST);
        body.add(goal, BorderLayout.SOUTH);

        inputs.add(body, BorderLayout.CENTER);
        inputs.add(goalBar, BorderLayout.SOUTH);

        listen(currentLevel, text -> {
            int level = parse(text);
            if (level >= 1 && level <= 99) {
                currentOverride = Experience.getXpForLevel(level);
                mirror(currentXp, String.format("%,d", currentOverride));
            }
        });
        listen(currentXp, text -> {
            int xp = parse(text);
            if (xp >= 0 && xp <= Experience.MAX_SKILL_XP) {
                currentOverride = xp;
                mirror(currentLevel, String.valueOf(CalculatorData.level(xp, false)));
            }
        });
        listen(targetLevel, text -> {
            int level = parse(text);
            if (level >= 1 && level <= 126) {
                targetOverride = Experience.getXpForLevel(level);
                mirror(targetXp, String.format("%,d", targetOverride));
            }
        });
        listen(targetXp, text -> {
            int xp = parse(text);
            if (xp >= 0 && xp <= Experience.MAX_SKILL_XP) {
                targetOverride = xp;
                mirror(targetLevel, String.valueOf(CalculatorData.level(xp, true)));
            }
        });
    }

    // Puts the matching number in the box that goes with the one being typed in
    private void mirror(JTextField field, String text) {
        adjusting = true;
        try {
            if (!field.getText().equals(text)) {
                field.setText(text);
            }
        } finally {
            adjusting = false;
        }
    }

    private void listen(JTextField field, java.util.function.Consumer<String> typedText) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                changed();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                changed();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                changed();
            }

            private void changed() {
                if (!adjusting && field.isFocusOwner()) {
                    typedText.accept(field.getText());
                    typed.restart();
                }
            }
        });
        field.addActionListener(e -> redraw.run());
    }

    /** "302,288" -> 302288; -1 when it isn't a whole number. */
    static int parse(String text) {
        String digits = text.replace(",", "").replace(" ", "").trim();
        if (digits.isEmpty() || digits.length() > 10 || !digits.chars().allMatch(Character::isDigit)) {
            return -1;
        }
        long value = Long.parseLong(digits);
        return value > Integer.MAX_VALUE ? -1 : (int) value;
    }

    private static JTextField field(String tooltip) {
        JTextField field = new JTextField();
        field.setFont(FontManager.getRunescapeSmallFont());
        field.setForeground(Color.WHITE);
        field.setCaretColor(Color.WHITE);
        field.setBackground(FIELD);
        field.setBorder(new CompoundBorder(PlannerUi.line(FIELD_LINE), new EmptyBorder(0, 5, 0, 5)));
        field.setToolTipText(tooltip);
        field.setPreferredSize(new Dimension(40, 21));
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                field.setBorder(new CompoundBorder(PlannerUi.line(ColorScheme.MEDIUM_GRAY_COLOR), new EmptyBorder(0, 5, 0, 5)));
                field.selectAll();
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                field.setBorder(new CompoundBorder(PlannerUi.line(FIELD_LINE), new EmptyBorder(0, 5, 0, 5)));
            }
        });
        return field;
    }

    private static JComponent labelled(String name, JTextField field) {
        JPanel box = new JPanel(new BorderLayout(0, 2));
        box.setOpaque(false);
        box.add(PlannerUi.label(name, PlannerUi.MUTED), BorderLayout.NORTH);
        box.add(field, BorderLayout.CENTER);
        return box;
    }

    private void setAsGoal() {
        Skill shown = skill();
        int target = target();
        int game = calc.xp(shown);
        if (shown != null && target > 0) {
            calc.setGoal(shown, target, game >= 0 ? game : current());
            targetOverride = null;
            redraw.run();
        }
    }

    // The skill's bonuses: tick one to count it (ticking one that can't go with another unticks that)
    private JComponent bonuses(Skill shown, CalculatorData.Calc skillCalc, Set<String> on) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(PlannerUi.BACKGROUND);
        double total = 1;
        for (CalculatorData.Bonus bonus : CalculatorData.bonuses(skillCalc, on)) {
            total *= bonus.value;
        }
        box.add(PlannerUi.group("XP bonuses", on.isEmpty() ? "" : "x" + trim(Math.round(total * 1000) / 1000.0)));
        JPanel rows = new JPanel(new GridLayout(0, 1));
        rows.setBackground(PlannerUi.CARD);
        rows.setBorder(new EmptyBorder(2, 0, 2, 0));
        for (CalculatorData.Bonus bonus : skillCalc.bonuses) {
            rows.add(bonusRow(shown, skillCalc, on, bonus));
        }
        box.add(PlannerUi.spaced(rows));
        return box;
    }

    private JComponent bonusRow(Skill shown, CalculatorData.Calc skillCalc, Set<String> on, CalculatorData.Bonus bonus) {
        boolean ticked = on.contains(bonus.id);
        JPanel row = new JPanel(new BorderLayout(7, 0));
        row.setBackground(PlannerUi.CARD);
        row.setBorder(new EmptyBorder(3, 7, 3, 7));
        PlannerUi.Tick tick = new PlannerUi.Tick(ticked);
        row.add(tick, BorderLayout.WEST);
        JLabel name = PlannerUi.label(bonus.name, ticked ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
        name.setMinimumSize(new Dimension(0, 0));
        row.add(name, BorderLayout.CENTER);
        row.add(PlannerUi.label(bonus.label(), ticked ? PlannerUi.READY : PlannerUi.MUTED), BorderLayout.EAST);
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        StringBuilder tip = new StringBuilder("<html>").append(PlannerUi.esc(bonus.name)).append(": XP ").append(bonus.label());
        List<String> clash = new ArrayList<>();
        for (CalculatorData.Bonus other : skillCalc.bonuses) {
            if (other != bonus && !other.stacks.contains(bonus.id)) {
                clash.add(other.name);
            }
        }
        if (!clash.isEmpty() && skillCalc.bonuses.size() > 1) {
            tip.append("<br><font color='#a0a0a0'>Not with ").append(PlannerUi.esc(String.join(", ", clash))).append("</font>");
        }
        tip.append("<br><font color='#a0a0a0'>Only counts for the actions it applies to.</font></html>");
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                row.setBackground(PlannerUi.CARD_HOVER);
                tick.hover = true;
                tick.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                row.setBackground(PlannerUi.CARD);
                tick.hover = false;
                tick.repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                calc.setBonuses(shown, CalculatorData.toggle(skillCalc, on, bonus.id, !ticked));
                redraw.run();
            }
        };
        PlannerUi.everywhere(row, c -> c.addMouseListener(mouse));
        PlannerUi.tips(row, tip.toString());
        return row;
    }

    private void buildSearch() {
        searchRow.setBackground(PlannerUi.CARD);
        searchRow.setBorder(PlannerUi.line(PlannerUi.CARD));
        JLabel glass = new JLabel(new PlannerUi.Glyph(PlannerUi.Shape.SEARCH, 14, PlannerUi.MUTED));
        glass.setBorder(new EmptyBorder(0, 7, 0, 0));
        searchRow.add(glass, BorderLayout.WEST);
        search.setOpaque(false);
        search.setForeground(Color.WHITE);
        search.setCaretColor(Color.WHITE);
        search.setFont(FontManager.getRunescapeSmallFont());
        search.setBorder(new EmptyBorder(6, 6, 6, 4));
        search.setToolTipText("Show only the actions with this in their name");
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                typed.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                typed.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                typed.restart();
            }
        });
        PlannerPanel.focusBorder(search, searchRow);
        searchRow.add(search, BorderLayout.CENTER);
    }

    // ---- The list ----

    /** The list's key (what it shows) and what fills it: a card per action, or per thing you own in Banked view. */
    String key() {
        Skill shown = skill();
        if (shown == null) {
            return "none";
        }
        StringBuilder key = new StringBuilder(shown.name()).append('|').append(current()).append('|').append(target())
            .append('|').append(calc.banked()).append('|').append(search.getText().trim().toLowerCase(Locale.ROOT))
            .append('|').append(String.join(",", calc.bonuses(shown))).append('|').append(calc.bankSeen());
        key.append('|').append(calc.owned().hashCode());
        return key.toString();
    }

    void fill() {
        list.removeAll();
        Skill shown = skill();
        if (shown == null) {
            return;
        }
        CalculatorData.Calc skillCalc = data.calc(shown);
        List<CalculatorData.Bonus> on = CalculatorData.bonuses(skillCalc, calc.bonuses(shown));
        int now = current();
        int target = target();
        Map<String, Integer> owned = calc.owned();
        List<PlannerCalculator.Row> rows = PlannerCalculator.rows(shown, skillCalc, on, now, target, recipes, training, owned,
            icons::price);
        String filter = search.getText().trim().toLowerCase(Locale.ROOT);
        boolean banked = calc.banked();
        if (banked) {
            fillBanked(shown, skillCalc, on, now, target, rows, owned, filter);
        } else {
            int shownCount = 0;
            for (PlannerCalculator.Row row : rows) {
                if (filter.isEmpty() || row.action.name.toLowerCase(Locale.ROOT).contains(filter)) {
                    list.add(PlannerUi.spaced(actionCard(row, now, target)));
                    shownCount++;
                }
            }
            if (shownCount == 0) {
                list.add(PlannerUi.empty(filter.isEmpty() ? "No actions for " + shown.getName() + "."
                    : "No " + shown.getName() + " action has \"" + filter + "\" in its name."));
            }
        }
        list.add(PlannerUi.empty("From RuneLite's Skill Calculator. Times are from the wiki's training guides; costs at "
            + "Grand Exchange prices."));
    }

    // What you own: the XP in all of it, the level that gets you to, and each action you have the makings of
    private void fillBanked(Skill shown, CalculatorData.Calc skillCalc, List<CalculatorData.Bonus> on, int now, int target,
                            List<PlannerCalculator.Row> rows, Map<String, Integer> owned, String filter) {
        int level = CalculatorData.level(now, false);
        if (!recipes.recipes(shown).isEmpty()) {
            double all = PlannerCalculator.bankedXp(shown, skillCalc, on, level, recipes, owned);
            int after = PlannerCalculator.bankedLevel(now, all);
            JPanel tiles = new JPanel(new GridLayout(1, 2, PlannerUi.GAP, 0));
            tiles.setBackground(PlannerUi.BACKGROUND);
            tiles.add(tile("Banked XP", PlannerPanel.gp((long) all) + " xp", String.format("%,d", (long) all) + " XP from "
                + "what you own at your level: the most XP each first, each item used once"));
            JComponent levelTile = tile("Level after", level + " → " + after, "Your level with all of it: "
                + (now + (long) all >= target ? "past your target" : PlannerPanel.gp(target - now - (long) all) + " xp short of your target"));
            tiles.add(levelTile);
            list.add(PlannerUi.spaced(tiles));
        }
        if (!calc.bankSeen()) {
            list.add(PlannerUi.empty("Open your bank once so the Planner can see what's in it; until then this counts your "
                + "inventory."));
        }
        int count = 0;
        for (PlannerCalculator.Row row : rows) {
            if (row.have <= 0 || !(filter.isEmpty() || row.action.name.toLowerCase(Locale.ROOT).contains(filter))) {
                continue;
            }
            list.add(PlannerUi.spaced(bankedCard(row, now, target)));
            count++;
        }
        if (count == 0) {
            list.add(PlannerUi.empty("Nothing in your bank or inventory for " + shown.getName() + (filter.isEmpty() ? "" : " with \""
                + filter + "\"") + "."));
        }
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

    // One action: its picture, name and level, XP each and how many to the target; time, what you own and its cost
    private JComponent actionCard(PlannerCalculator.Row row, int now, int target) {
        CalculatorData.Action action = row.action;
        PlannerUi.Card card = new PlannerUi.Card(action.name, xp(row.xpEach) + " xp each",
            target > now ? String.format("%,d", row.needed) : "-");
        card.corner.setText("Lvl " + action.level);
        card.right.setForeground(Color.WHITE);
        List<String> notes = new ArrayList<>();
        if (row.seconds > 0) {
            notes.add("~" + PlannerPanel.formatDuration(row.seconds));
        }
        if (row.have > 0) {
            notes.add("have " + PlannerPanel.gp(row.have));
        }
        Double perXp = row.perXp();
        String money = perXp == null ? "" : gpPerXp(perXp) + (target > now ? " · " + signed(row.profit * row.needed) : "");
        if (!notes.isEmpty() || !money.isEmpty()) {
            card.third(String.join(" · ", notes), money);
            if (perXp != null) {
                card.lowerRight.setForeground(perXp < 0 ? COSTS : PlannerUi.READY);
            }
        }
        picture(card.icon, action);
        if (row.locked) {
            card.dim();
        } else if (row.suggested) {
            card.title.setForeground(SUGGESTED);
            card.setBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, SUGGESTED));
        }
        return card.interactive(null, null, actionTip(row, now, target));
    }

    private String actionTip(PlannerCalculator.Row row, int now, int target) {
        CalculatorData.Action action = row.action;
        StringBuilder tip = new StringBuilder("<html><b>").append(PlannerUi.esc(action.name)).append("</b> (level ")
            .append(action.level).append(")");
        tip.append("<br>").append(xp(row.xpEach)).append(" xp each");
        if (row.xpEach != action.xp) {
            tip.append(" with your bonuses (").append(xp(action.xp)).append(" without)");
        }
        if (target > now) {
            tip.append("<br>").append(String.format("%,d", row.needed)).append(row.needed == 1 ? " action" : " actions")
                .append(" for ").append(String.format("%,d", target - now)).append(" xp");
        }
        if (row.seconds > 0) {
            tip.append("<br>About ").append(PlannerPanel.formatDuration(row.seconds)).append(" at ")
                .append(String.format("%,d", row.xpHour)).append(" xp an hour (the wiki's training guide)");
        }
        Double perXp = row.perXp();
        if (perXp != null) {
            tip.append("<br>").append(row.profit < 0 ? "Costs " : "Earns ").append(String.format("%,.0f", Math.abs(row.profit)))
                .append(" gp each, ").append(String.format("%.2f", Math.abs(perXp))).append(" gp per xp");
            if (target > now) {
                tip.append("<br>To your target: ").append(row.profit < 0 ? "costs " : "earns ")
                    .append(String.format("%,.0f", Math.abs(row.profit * row.needed))).append(" gp");
            }
        }
        if (row.have > 0) {
            tip.append("<br>You have ").append(String.format("%,d", row.have)).append(" (bank as last seen and inventory)");
        }
        if (row.locked) {
            tip.append("<br><font color='#a0a0a0'>Needs level ").append(action.level).append("</font>");
        } else if (row.suggested) {
            tip.append("<br><font color='#a0a0a0'>The wiki's suggested method at your level</font>");
        }
        return tip.append("</html>").toString();
    }

    // In Banked view: how many you own, the XP in them, and the level that gets you to
    private JComponent bankedCard(PlannerCalculator.Row row, int now, int target) {
        CalculatorData.Action action = row.action;
        double gained = row.have * row.xpEach;
        int after = PlannerCalculator.bankedLevel(now, gained);
        PlannerUi.Card card = new PlannerUi.Card(action.name, "x " + String.format("%,d", row.have) + " · "
            + PlannerPanel.gp((long) gained) + " xp", "→ " + after);
        card.corner.setText("Lvl " + action.level);
        boolean reaches = now + gained >= target;
        card.right.setForeground(reaches ? PlannerUi.READY : Color.WHITE);
        picture(card.icon, action);
        if (row.locked) {
            card.dim();
        }
        return card.interactive(null, null, "<html><b>" + PlannerUi.esc(action.name) + "</b>: you have "
            + String.format("%,d", row.have) + " (bank as last seen and inventory)<br>" + String.format("%,d", (long) gained)
            + " xp, to level " + after + (reaches ? " (past your target)" : ", " + String.format("%,d", (long) (target - now - gained))
            + " xp short of your target") + (row.locked ? "<br><font color='#a0a0a0'>Needs level " + action.level + "</font>" : "")
            + "</html>");
    }

    private void picture(JLabel label, CalculatorData.Action action) {
        if (action.icon > 0) {
            image(label, icons.item(action.icon, 1, false));
        } else {
            image(label, icons.skill(skill(), false));
            if (action.sprite >= 0) {
                icons.sprite(action.sprite, sprite -> image(label, sprite));
            }
        }
    }

    private static BufferedImage fade(BufferedImage image, float alpha) {
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha));
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return out;
    }

    private static void image(JLabel label, BufferedImage picture) {
        PlannerPanel.image(label, picture, 1f);
    }

    /** "37.5", "25", "1,337". */
    static String xp(double xp) {
        return xp == Math.rint(xp) ? String.format("%,d", (long) xp) : String.format("%,.1f", xp);
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    // "-3.4 gp/xp", "+1.2 gp/xp"
    private static String gpPerXp(double perXp) {
        return (perXp < 0 ? "-" : "+") + String.format(Math.abs(perXp) >= 10 ? "%.0f" : "%.1f", Math.abs(perXp)) + " gp/xp";
    }

    // "-1.21M", "+350K"
    private static String signed(double gp) {
        return (gp < 0 ? "-" : "+") + PlannerPanel.gp((long) Math.abs(gp));
    }
}
