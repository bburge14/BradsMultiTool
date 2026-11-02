package com.snuffys.planner;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
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
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
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
    static final Color COSTS = new Color(230, 110, 100);
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
    private JComponent searchSlot = new JPanel();
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
    private final JLabel resetLink;
    private final PlannerUi.Bar goalBar = new PlannerUi.Bar(0, PlannerUi.GROWING);
    private final PlannerPanel.Hinted search = new PlannerPanel.Hinted("Search actions");

    private Skill skill;
    // What you typed over the game's XP (null: from the game). A typed target is kept per skill (PlannerCalculator)
    private Integer currentOverride;
    private boolean adjusting;
    // A plan's milestone waiting for a method picked from the actions (its index), or null
    private Integer picking;
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
        resetLink = PlannerUi.linkLabel("Reset", "Forget the target you typed: back to your goal, else the next level",
            this::resetTarget);
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
        searchSlot = PlannerUi.spaced(searchRow);
        column.add(searchSlot);
        column.add(list);
    }

    void stop() {
        typed.stop();
    }

    /** For the preview: shows this skill, view and search. */
    void preview(Skill picked, PlannerCalculator.Mode mode, String text) {
        calc.setMode(mode);
        picking = null;
        calc.setSkill(picked);
        skill = picked;
        currentOverride = null;
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
        int saved = skill() == null ? 0 : calc.savedTarget(skill());
        if (saved > 0) {
            return saved;
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
        resetLink.setVisible(calc.savedTarget(shown) > 0);
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

        PlannerCalculator.Mode mode = picking != null ? PlannerCalculator.Mode.ACTIONS : calc.mode();
        // (no search in Plan view)
        searchSlot.setVisible(mode != PlannerCalculator.Mode.PLAN);
        if (listHeader.changed(mode + shown.name() + picking)) {
            listHeader.add(modeTabs(mode), BorderLayout.CENTER);
        }
    }

    // Actions · Banked · Plan: the list's three views, the one shown in white with an orange line under it
    private JComponent modeTabs(PlannerCalculator.Mode mode) {
        JPanel row = new JPanel(new GridLayout(1, 3, 2, 0));
        row.setBackground(PlannerUi.BACKGROUND);
        row.setBorder(new EmptyBorder(4, 0, 5, 0));
        String[] names = {"Actions", "Banked", "Plan"};
        String[] tips = {"Every action to your target", "What your bank and inventory get you: the XP and level from each action",
            "Your plan for the skill: milestones on the way to a goal, each with its own method"};
        PlannerCalculator.Mode[] modes = PlannerCalculator.Mode.values();
        for (int i = 0; i < modes.length; i++) {
            PlannerCalculator.Mode m = modes[i];
            boolean on = m == mode;
            JLabel tab = PlannerUi.label(names[i], on ? Color.WHITE : PlannerUi.MUTED);
            tab.setHorizontalAlignment(SwingConstants.CENTER);
            tab.setOpaque(true);
            tab.setBackground(on ? new Color(60, 60, 60) : PlannerUi.CARD);
            tab.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 2, 0,
                on ? ColorScheme.BRAND_ORANGE : PlannerUi.CARD), new EmptyBorder(4, 0, 3, 0)));
            tab.setToolTipText(tips[i]);
            if (!on) {
                tab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                tab.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        tab.setBackground(PlannerUi.CARD_HOVER);
                        tab.setForeground(Color.WHITE);
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        tab.setBackground(PlannerUi.CARD);
                        tab.setForeground(PlannerUi.MUTED);
                    }

                    @Override
                    public void mouseClicked(MouseEvent e) {
                        picking = null;
                        calc.setMode(m);
                        redraw.run();
                    }
                });
            }
            row.add(tab);
        }
        return PlannerUi.fullWidth(row);
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
        JPanel links = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        links.setOpaque(false);
        links.add(resetLink);
        links.add(goalLink);
        goal.add(links, BorderLayout.EAST);
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
                int xp = Experience.getXpForLevel(level);
                keepTarget(xp);
                mirror(targetXp, String.format("%,d", xp));
            }
        });
        listen(targetXp, text -> {
            int xp = parse(text);
            if (xp >= 0 && xp <= Experience.MAX_SKILL_XP) {
                keepTarget(xp);
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
            calc.setSavedTarget(shown, 0);
            redraw.run();
        }
    }

    // A typed target stays for the skill (across skills and logins) until Reset or Set as goal
    private void keepTarget(int xp) {
        if (skill() != null) {
            calc.setSavedTarget(skill(), xp);
        }
    }

    private void resetTarget() {
        if (skill() != null) {
            calc.setSavedTarget(skill(), 0);
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

    /** The list's key (what it shows) and what fills it: a card per action, per thing you own, or per milestone. */
    String key() {
        Skill shown = skill();
        if (shown == null) {
            return "none";
        }
        StringBuilder key = new StringBuilder(shown.name()).append('|').append(current()).append('|').append(target())
            .append('|').append(calc.mode()).append('|').append(search.getText().trim().toLowerCase(Locale.ROOT))
            .append('|').append(String.join(",", calc.bonuses(shown))).append('|').append(calc.bankSeen())
            .append('|').append(calc.showMembers()).append(calc.showPrices()).append('|').append(calc.priceVersion())
            .append('|').append(calc.trackerInstalled()).append('|').append(picking).append('|').append(calc.canPlan());
        key.append('|').append(calc.owned().hashCode());
        SkillPlans.Plan plan = calc.plan(shown);
        key.append('|').append(plan == null ? "" : SkillPlans.encode(plan)).append(calc.isTracked(shown, plan))
            .append(calc.trackedPlan(shown) != null);
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
        // On a free world only the free actions (as RuneLite's Skill Calculator does)
        List<CalculatorData.Action> actions = CalculatorData.shown(skillCalc, calc.showMembers());
        int hidden = skillCalc.actions.size() - actions.size();
        if (hidden > 0) {
            list.add(note("Members-only actions hidden on this free world", hidden + " members-only " + shown.getName()
                + " actions aren't shown while you're on a free world (Hide Members On Free Worlds in the settings)"));
        }
        if (picking != null && (calc.plan(shown) == null || picking >= calc.plan(shown).steps.size())) {
            picking = null;
        }
        PlannerCalculator.Mode mode = picking != null ? PlannerCalculator.Mode.ACTIONS : calc.mode();
        if (mode == PlannerCalculator.Mode.PLAN) {
            fillPlan(shown, skillCalc, on, now, target, actions);
        } else {
            List<PlannerCalculator.Row> rows = PlannerCalculator.rows(shown, actions, on, now, target, recipes, training, owned,
                icons::price, calc::itemPrice);
            String filter = search.getText().trim().toLowerCase(Locale.ROOT);
            if (mode == PlannerCalculator.Mode.BANKED) {
                fillBanked(shown, skillCalc, on, now, target, rows, owned, filter);
            } else {
                fillActions(shown, on, now, target, rows, filter);
            }
        }
        list.add(PlannerUi.empty("From RuneLite's Skill Calculator. Times are from the wiki's training guides; costs at "
            + "Grand Exchange prices."));
    }

    // Every action (searched), or with a milestone waiting for a method, the actions to pick it from
    private void fillActions(Skill shown, List<CalculatorData.Bonus> on, int now, int target, List<PlannerCalculator.Row> rows,
                             String filter) {
        SkillPlans.Plan plan = calc.plan(shown);
        if (picking != null) {
            SkillPlans.Step step = plan.steps.get(picking);
            list.add(banner("Pick the method for " + step.from + " → " + step.to + " (now " + step.action + ")",
                "Cancel", "Back to the plan without changing it", () -> {
                    picking = null;
                    redraw.run();
                }));
        } else if (plan != null && !plan.steps.isEmpty()) {
            list.add(banner("Plan: " + plan.steps.size() + (plan.steps.size() == 1 ? " milestone" : " milestones") + " to level "
                + plan.goal, "Show", "Your plan for " + shown.getName() + " (right-click an action to add it)", () -> {
                    calc.setMode(PlannerCalculator.Mode.PLAN);
                    redraw.run();
                }));
        }
        int shownCount = 0;
        for (PlannerCalculator.Row row : rows) {
            if (filter.isEmpty() || row.action.name.toLowerCase(Locale.ROOT).contains(filter)) {
                list.add(PlannerUi.spaced(actionCard(shown, on, row, now, target)));
                shownCount++;
            }
        }
        if (shownCount == 0) {
            list.add(PlannerUi.empty(filter.isEmpty() ? "No actions for " + shown.getName() + "."
                : "No " + shown.getName() + " action has \"" + filter + "\" in its name."));
        }
    }

    // What you own: the XP in all of it, the level that gets you to, its value, and each action you have the makings of
    private void fillBanked(Skill shown, CalculatorData.Calc skillCalc, List<CalculatorData.Bonus> on, int now, int target,
                            List<PlannerCalculator.Row> rows, Map<String, Integer> owned, String filter) {
        int level = CalculatorData.level(now, false);
        if (!recipes.recipes(shown).isEmpty()) {
            double all = PlannerCalculator.bankedXp(shown, skillCalc, on, level, recipes, owned);
            int after = PlannerCalculator.bankedLevel(now, all);
            JPanel tiles = new JPanel(new GridLayout(1, 2, PlannerUi.GAP, 0));
            tiles.setBackground(PlannerUi.BACKGROUND);
            tiles.add(tile("Banked XP", PlannerPanel.gp((long) all) + " xp", String.format("%,d", (long) all) + " XP from "
                + "what you own at your level: the most XP each first, each item used once", Color.WHITE));
            int targetLvl = CalculatorData.level(target, true);
            boolean reaches = now + (long) all >= target;
            JComponent levelTile = tile("Level · aim " + targetLvl, level + " → " + after, "Your level with all of it: "
                + (reaches ? "past your target (level " + targetLvl + ")"
                : PlannerPanel.gp(target - now - (long) all) + " xp short of your target (level " + targetLvl + ")"),
                reaches ? PlannerUi.READY : Color.WHITE);
            tiles.add(levelTile);
            list.add(PlannerUi.spaced(tiles));
            long value = calc.showPrices() ? PlannerCalculator.bankedValue(shown, skillCalc, on, level, recipes, owned, icons::price) : 0;
            if (value > 0) {
                // What it all sells for once made, less what the materials are worth: the cards' nets added up
                Map<String, Integer> madeAll = PlannerCalculator.bankedMade(shown, skillCalc, on, level, recipes, owned);
                double cost = 0;
                double earn = 0;
                for (PlannerCalculator.Row row : rows) {
                    Integer times = row.locked ? null : PlannerCalculator.bankedTimes(shown, row.action, recipes, madeAll);
                    if (times != null && times > 0) {
                        cost += row.cost(times) == null ? 0 : row.cost(times);
                        earn += row.earn(times) == null ? 0 : row.earn(times);
                    }
                }
                JPanel money = new JPanel(new GridLayout(1, 2, PlannerUi.GAP, 0));
                money.setBackground(PlannerUi.BACKGROUND);
                money.add(tile("Materials", PlannerPanel.gp(value) + " gp", "What the items that Banked XP uses up are worth "
                    + "on the Grand Exchange (" + String.format("%,d", value) + " gp)", Color.WHITE));
                double net = earn - cost;
                money.add(tile("Net", signed(net) + " gp", "<html>What it all sells for once made (" + String.format("%,.0f", earn)
                    + " gp) less what the materials are worth (" + String.format("%,.0f", cost) + " gp): "
                    + (net < 0 ? "a loss" : "a gain") + "</html>", net < 0 ? COSTS : PlannerUi.READY));
                list.add(PlannerUi.spaced(money));
            }
        }
        if (!calc.bankSeen()) {
            list.add(PlannerUi.empty("Open your bank once so the Planner can see what's in it; until then this counts your "
                + "inventory."));
        }
        // The cards stack up: each starts from the level the ones above it got you to, so the last one ends at the
        // total (not each from your level on its own)
        Map<String, Integer> made = PlannerCalculator.bankedMade(shown, skillCalc, on, level, recipes, owned);
        int count = 0;
        double running = now;
        for (PlannerCalculator.Row row : rows) {
            if (row.have <= 0 || !(filter.isEmpty() || row.action.name.toLowerCase(Locale.ROOT).contains(filter))) {
                continue;
            }
            Integer times = PlannerCalculator.bankedTimes(shown, row.action, recipes, made);
            int used = row.locked ? 0 : times == null ? row.have : times;
            list.add(PlannerUi.spaced(bankedCard(row, used, running, target)));
            running += used * row.xpEach;
            count++;
        }
        if (count == 0) {
            list.add(PlannerUi.empty("Nothing in your bank or inventory for " + shown.getName() + (filter.isEmpty() ? "" : " with \""
                + filter + "\"") + "."));
        }
    }

    private static JComponent tile(String name, String value, String tooltip, Color colour) {
        JPanel tile = new JPanel(new GridLayout(2, 1));
        tile.setBackground(PlannerUi.CARD);
        tile.setBorder(new EmptyBorder(5, 8, 5, 8));
        tile.add(PlannerUi.label(name, PlannerUi.MUTED));
        JLabel number = new JLabel(value);
        number.setFont(FontManager.getRunescapeFont());
        number.setForeground(colour);
        tile.add(number);
        PlannerUi.tips(tile, tooltip);
        return tile;
    }

    // A short muted line over the list (wrapped)
    private static JComponent note(String text, String tooltip) {
        JLabel label = new JLabel(PlannerUi.wrap(text, PlannerUi.WRAP + 16, false, false));
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(PlannerUi.MUTED);
        label.setToolTipText(tooltip);
        JPanel box = new JPanel(new BorderLayout());
        box.setBackground(PlannerUi.BACKGROUND);
        box.setBorder(new EmptyBorder(0, 2, 6, 2));
        box.add(label, BorderLayout.CENTER);
        return PlannerUi.fullWidth(box);
    }

    // A one-line card with text and a link on the right ("Plan: 4 milestones to level 99   Show")
    private static JComponent banner(String text, String link, String linkTip, Runnable action) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(PlannerUi.CARD);
        row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, SUGGESTED),
            new EmptyBorder(5, 6, 5, 7)));
        JLabel label = PlannerUi.label(text, Color.WHITE);
        label.setMinimumSize(new Dimension(0, 0));
        label.setToolTipText(text);
        row.add(label, BorderLayout.CENTER);
        row.add(PlannerUi.linkLabel(link, linkTip, action), BorderLayout.EAST);
        return PlannerUi.spaced(row);
    }

    // One action: its picture, name and level, XP each and how many to the target; time, what you own, its net gp per
    // XP, and what its materials cost and products are worth to the target
    private JComponent actionCard(Skill shown, List<CalculatorData.Bonus> on, PlannerCalculator.Row row, int now, int target) {
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
        boolean prices = calc.showPrices();
        Double perXp = prices ? row.perXp() : null;
        if (!notes.isEmpty() || perXp != null) {
            card.third(String.join(" · ", notes), perXp == null ? "" : gpPerXp(perXp));
            if (perXp != null) {
                card.lowerRight.setForeground(perXp < 0 ? COSTS : PlannerUi.READY);
            }
        }
        int count = target > now ? row.needed : 1;
        String money = prices ? money(row.cost(count), row.earn(count), target > now ? "" : " each") : null;
        if (money != null) {
            card.fourth(money, "");
            net(card.lastRight, row.cost(count), row.earn(count), !row.locked);
        }
        picture(card.icon, action);
        if (row.locked) {
            card.dim();
        } else if (row.suggested) {
            card.title.setForeground(SUGGESTED);
            card.setBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, SUGGESTED));
        }
        Runnable click = null;
        JPopupMenu menu = new JPopupMenu();
        if (picking != null) {
            click = () -> pickMethod(shown, action, row.xpEach);
            menu.add(menuItem("Use for this milestone", click));
        } else if (calc.canPlan()) {
            menu.add(menuItem("Add to plan", () -> addToPlan(shown, action, row.xpEach)));
        }
        return card.interactive(click, menu.getComponentCount() > 0 ? menu : null, actionTip(row, now, target));
    }

    /**
     * "Mats 1.24M · Sells 2.10M": what the materials and products are worth on the GE (the net goes beside it, from
     * {@link #net}), or null when neither is known.
     */
    static String money(Double cost, Double earn, String suffix) {
        List<String> parts = new ArrayList<>();
        // What each side is worth on the GE, in plain text (neither is profit: the net beside it is)
        if (cost != null && cost > 0) {
            parts.add("Mats " + PlannerPanel.gp(Math.round(cost)) + suffix);
        }
        if (earn != null && earn > 0) {
            parts.add("Sells " + PlannerPanel.gp(Math.round(earn)) + suffix);
        }
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    /**
     * The net (what the products sell for less what the materials cost) on this label, with a green plus or red minus
     * badge; nothing when neither is known. Plain (no colour) on a greyed card.
     */
    static void net(JLabel label, Double cost, Double earn, boolean colour) {
        double c = cost == null ? 0 : cost;
        double e = earn == null ? 0 : earn;
        if (c <= 0 && e <= 0) {
            return;
        }
        double net = e - c;
        Color tone = colour ? net < 0 ? COSTS : PlannerUi.READY : PlannerUi.MUTED;
        label.setText(signed(net));
        label.setForeground(tone);
        label.setIcon(new NetBadge(net >= 0, tone));
        label.setIconTextGap(3);
    }

    // A small round badge: a plus for a net gain, a minus for a net loss
    static final class NetBadge implements javax.swing.Icon {
        private static final int SIZE = 11;
        private final boolean gain;
        private final Color colour;

        NetBadge(boolean gain, Color colour) {
            this.gain = gain;
            this.colour = colour;
        }

        @Override
        public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(colour);
            g2.fillOval(x, y, SIZE, SIZE);
            g2.setColor(PlannerUi.CARD);
            g2.fillRect(x + 2, y + SIZE / 2 - 1, SIZE - 4, 2);
            if (gain) {
                g2.fillRect(x + SIZE / 2 - 1, y + 2, 2, SIZE - 4);
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }

    // The net in words for a tooltip: "Net: a loss of 28,000 gp" / "a gain of"
    private static String netTip(Double cost, Double earn) {
        double net = (earn == null ? 0 : earn) - (cost == null ? 0 : cost);
        return "Net: " + (net < 0 ? "<font color='#e05a5a'>a loss of " : "<font color='#5ac85a'>a gain of ")
            + String.format("%,.0f", Math.abs(net)) + " gp</font> (what it sells for less what the materials cost)";
    }

    private static javax.swing.JMenuItem menuItem(String text, Runnable action) {
        javax.swing.JMenuItem item = new javax.swing.JMenuItem(text);
        item.addActionListener(e -> action.run());
        return item;
    }

    private String actionTip(PlannerCalculator.Row row, int now, int target) {
        CalculatorData.Action action = row.action;
        StringBuilder tip = new StringBuilder("<html><b>").append(PlannerUi.esc(action.name)).append("</b> (level ")
            .append(action.level).append(action.members ? ", members" : "").append(")");
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
        if (calc.showPrices()) {
            if (row.costEach != null && row.costEach > 0) {
                tip.append("<br>Materials: worth ").append(String.format("%,.0f", row.costEach)).append(" gp each on the GE");
            }
            if (row.earnEach != null && row.earnEach > 0) {
                tip.append("<br>Sells for: ").append(String.format("%,.0f", row.earnEach)).append(" gp each on the GE");
            }
            Double perXp = row.perXp();
            if (perXp != null) {
                tip.append("<br>Net: ").append(row.profit < 0 ? "<font color='#e05a5a'>loses " : "<font color='#5ac85a'>makes ").append(String.format("%.2f", Math.abs(perXp)))
                    .append(" gp per xp</font>");
                if (target > now) {
                    tip.append(" (").append(row.profit < 0 ? "a loss of " : "a gain of ").append(String.format("%,.0f",
                        Math.abs(row.profit * row.needed))).append(" gp to your target)");
                }
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
        if (picking != null) {
            tip.append("<br><font color='#a0a0a0'>Click to use it for the milestone</font>");
        } else if (calc.canPlan()) {
            tip.append("<br><font color='#a0a0a0'>Right-click: Add to plan</font>");
        }
        return tip.append("</html>").toString();
    }

    // In Banked view: how many you own, the XP in them, the level that gets you to, and what they're worth
    private JComponent bankedCard(PlannerCalculator.Row row, int used, double from, int target) {
        CalculatorData.Action action = row.action;
        double gained = (row.locked ? row.have : used) * row.xpEach;
        // Its materials all went to the ones above it (they share them and give more XP)
        boolean usedUp = !row.locked && used <= 0;
        int now = (int) Math.min(Experience.MAX_SKILL_XP, from);
        int before = CalculatorData.level(now, false);
        int after = PlannerCalculator.bankedLevel(now, gained);
        int count = row.locked ? row.have : used;
        String owned = count == row.have ? String.format("%,d", row.have) : String.format("%,d of %,d", count, row.have);
        PlannerUi.Card card = new PlannerUi.Card(action.name, "x " + owned + " · "
            + PlannerPanel.gp((long) gained) + " xp", row.locked ? "" : usedUp ? "used above" : "lvl " + before + " → " + after);
        // Its level only when you're not there yet: the rest you can already make
        card.corner.setText(row.locked ? "needs lvl " + action.level : "");
        boolean reaches = !row.locked && !usedUp && now + gained >= target;
        card.right.setForeground(reaches ? PlannerUi.READY : Color.WHITE);
        String money = calc.showPrices() ? money(row.cost(count), row.earn(count), "") : null;
        if (money != null) {
            card.third(money, "");
            net(card.lowerRight, row.cost(count), row.earn(count), !row.locked && !usedUp);
        }
        picture(card.icon, action);
        if (row.locked || usedUp) {
            card.dim();
        }
        return card.interactive(null, null, "<html><b>" + PlannerUi.esc(action.name) + "</b>: you have "
            + String.format("%,d", row.have) + " (bank as last seen and inventory)<br>"
            + (count < row.have && !row.locked ? (usedUp ? "Its materials go to the ones above it (more XP each)<br>"
            : "Makes " + String.format("%,d", count) + ": the rest of its materials go to the ones above it<br>") : "") + String.format("%,d", (long) gained)
            + " xp" + (row.locked ? "" : ": with the ones above it, level " + before + " → " + after
            + (reaches ? " (reaches your target)" : ", " + String.format("%,d", (long) (target - now - gained))
            + " xp short of your target"))
            + (calc.showPrices() && row.costEach != null && count > 0 ? "<br>Materials: worth " + String.format("%,.0f",
            row.costEach * count) + " gp on the GE (" + String.format("%,.0f", row.costEach) + " each)" : "")
            + (calc.showPrices() && row.earnEach != null && count > 0 ? "<br>Sells for: " + String.format("%,.0f",
            row.earnEach * count) + " gp on the GE (" + String.format("%,.0f", row.earnEach) + " each)" : "")
            + (calc.showPrices() && count > 0 && (row.costEach != null || row.earnEach != null) ? "<br>"
            + netTip(row.cost(count), row.earn(count)) : "")
            + (row.locked ? "<br><font color='#a0a0a0'>Needs level " + action.level + "</font>" : "")
            + "</html>");
    }

    // ---- Plan view ----

    private int levelNow() {
        return CalculatorData.level(current(), false);
    }

    // A new plan's goal: the target you typed or the skill's goal (to 99), else 99. Not the next level the target
    // box shows by default: a plan to it would be full after its first milestone
    private int newGoal() {
        Skill shown = skill();
        int level = levelNow();
        boolean chosen = shown != null && (calc.savedTarget(shown) > 0 || calc.goal(shown) > current());
        int targetLevel = Math.min(99, CalculatorData.level(target(), false));
        return chosen && targetLevel > level ? targetLevel : 99;
    }

    // The skill's plan: totals, each milestone (what's left of it), the gaps, and exporting it to the tracker
    private void fillPlan(Skill shown, CalculatorData.Calc skillCalc, List<CalculatorData.Bonus> on, int now, int target,
                          List<CalculatorData.Action> actions) {
        if (!calc.canPlan()) {
            list.add(PlannerUi.empty("Log in once on this account to make a plan: plans are kept for each account."));
            return;
        }
        SkillPlans.Plan plan = calc.plan(shown);
        int level = levelNow();
        if (plan == null || plan.steps.isEmpty()) {
            int goal = plan != null && plan.goal > level ? plan.goal : newGoal();
            list.add(PlannerUi.empty("No plan for " + shown.getName() + " yet. A plan is a goal (level " + goal + ") and "
                + "milestones on the way, each with its own method: start from the wiki's suggestions, or right-click "
                + "actions to add them."));
            list.add(linkRow(PlannerUi.linkLabel("Start from suggestions", "Fill the milestones from the wiki's training "
                + "guides, from your level to " + goal + "; then change any of them", () -> suggest(shown, actions, on, goal)),
                PlannerUi.linkLabel("Add a milestone", "Pick its method and levels", () -> stepDialog(shown, -1, goal))));
            return;
        }
        List<PlannerCalculator.StepRow> steps = PlannerCalculator.stepRows(shown, skillCalc, on, plan, now, recipes, training,
            icons::price, calc::itemPrice);
        PlannerCalculator.Totals totals = PlannerCalculator.totals(plan, steps, now);

        JPanel tiles = new JPanel(new GridLayout(0, 2, PlannerUi.GAP, PlannerUi.GAP));
        tiles.setBackground(PlannerUi.BACKGROUND);
        tiles.add(tile("XP to go", PlannerPanel.gp(totals.xp) + " xp", String.format("%,d", totals.xp)
            + " XP left in the plan's milestones", Color.WHITE));
        tiles.add(tile("Time", totals.seconds > 0 ? "~" + PlannerPanel.formatDuration(totals.seconds) + (totals.untimed > 0 ? "+" : "")
            : "-", totals.untimed > 0 ? totals.untimed + " milestone" + (totals.untimed == 1 ? " has" : "s have") + " no XP rate "
            + "in the wiki's guides, so the time leaves " + (totals.untimed == 1 ? "it" : "them") + " out"
            : "At the wiki's training guides' XP rates", Color.WHITE));
        int endLevel = Math.min(plan.goal, Math.max(totals.endLevel, level));
        tiles.add(tile("Level", level + " → " + endLevel, endLevel < plan.goal ? "The milestones end at level " + endLevel
            + "; the goal is " + plan.goal : "Your level now, and where the plan ends (its goal)", Color.WHITE));
        boolean cost = calc.showPrices() && totals.cost > 0;
        boolean earn = calc.showPrices() && totals.earn > 0;
        String costTip = "What the milestones' materials cost at Grand Exchange prices (" + String.format("%,.0f", totals.cost) + " gp)";
        String earnTip = "What the milestones' products are worth at Grand Exchange prices (" + String.format("%,.0f", totals.earn) + " gp)";
        if (cost && earn) {
            // Costs and earnings both: the net here, each of them on a row of their own below
            double net = totals.earn - totals.cost;
            tiles.add(tile("Net", signed(net) + " gp", "What the products are worth less what the materials cost",
                net < 0 ? COSTS : PlannerUi.READY));
            tiles.add(tile("Materials", PlannerPanel.gp(Math.round(totals.cost)) + " gp", costTip, Color.WHITE));
            tiles.add(tile("Sells for", PlannerPanel.gp(Math.round(totals.earn)) + " gp", earnTip, Color.WHITE));
        } else if (cost) {
            tiles.add(tile("Net", signed(-totals.cost) + " gp", costTip + ": nothing to sell after", COSTS));
        } else if (earn) {
            tiles.add(tile("Net", signed(totals.earn) + " gp", earnTip + ": no materials to buy", PlannerUi.READY));
        } else {
            tiles.add(tile("Milestones", String.valueOf(plan.steps.size()), "The plan's milestones", Color.WHITE));
        }
        list.add(PlannerUi.spaced(tiles));

        JLabel goalLink = PlannerUi.linkLabel("Goal: level " + plan.goal, "Change the plan's final goal", () -> changeGoal(shown));
        list.add(PlannerUi.group("Milestones", "", goalLink,
            new PlannerUi.IconButton(new PlannerUi.Glyph(PlannerUi.Shape.PLUS, 14, ColorScheme.LIGHT_GRAY_COLOR),
                "Add a milestone", () -> stepDialog(shown, -1, plan.goal))));
        int expected = level;
        for (int i = 0; i < steps.size(); i++) {
            PlannerCalculator.StepRow step = steps.get(i);
            if (step.step.from > expected && step.step.from > level) {
                list.add(gap(expected, step.step.from));
            }
            list.add(PlannerUi.spaced(stepCard(shown, plan, i, step, level)));
            expected = Math.max(expected, step.step.to);
        }
        if (expected < plan.goal) {
            list.add(gap(expected, plan.goal));
        }
        list.add(linkRow(PlannerUi.linkLabel("Start from suggestions", "Replace the milestones with the wiki's training "
                + "guides' methods from your level to " + plan.goal, () -> suggest(shown, actions, on, plan.goal)),
            PlannerUi.linkLabel("Clear", "Delete this plan (the tracker keeps any plan you exported)", () -> {
                if (confirm("Delete your " + shown.getName() + " plan?")) {
                    calc.savePlan(shown, null);
                    redraw.run();
                }
            })));
        list.add(exportRow(shown, plan, on, skillCalc));
    }

    // "Levels 45-50: no milestone (the tracker suggests the wiki's method)"
    private static JComponent gap(int from, int to) {
        return note("Levels " + from + "-" + to + ": no milestone here (the wiki's suggestion)", "No milestone covers these "
            + "levels; the Skilling Helper suggests the wiki's method there");
    }

    // Links side by side at the right
    private static JComponent linkRow(JComponent... links) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setBackground(PlannerUi.BACKGROUND);
        row.setBorder(new EmptyBorder(2, 0, 6, 2));
        row.add(javax.swing.Box.createHorizontalGlue());
        for (int i = 0; i < links.length; i++) {
            if (i > 0) {
                row.add(PlannerUi.label("  ·  ", PlannerUi.FAINT));
            }
            row.add(links[i]);
        }
        return PlannerUi.fullWidth(row);
    }

    // One milestone: its method and levels, the XP and actions left, time, net gp per XP, and cost and worth
    private JComponent stepCard(Skill shown, SkillPlans.Plan plan, int index, PlannerCalculator.StepRow s, int level) {
        SkillPlans.Step step = s.step;
        PlannerCalculator.Row row = s.row;
        boolean now = step.covers(level);
        PlannerUi.Card card = new PlannerUi.Card(step.action, s.done ? "done" : PlannerPanel.gp(s.xp) + " xp"
            + (now ? " left" : ""), row != null && !s.done ? String.format("%,d", row.needed) : "");
        card.corner.setText(step.from + " → " + step.to);
        card.corner.setForeground(now ? Color.WHITE : PlannerUi.MUTED);
        card.right.setForeground(Color.WHITE);
        if (row != null && !s.done) {
            Double perXp = calc.showPrices() ? row.perXp() : null;
            String time = row.seconds > 0 ? "~" + PlannerPanel.formatDuration(row.seconds) : "";
            String note = s.tooLow() ? (time.isEmpty() ? "" : time + " · ") + "needs level " + s.action.level : time;
            if (!note.isEmpty() || perXp != null) {
                card.third(note, perXp == null ? "" : gpPerXp(perXp));
                if (s.tooLow()) {
                    card.lower.setForeground(COSTS);
                }
                if (perXp != null) {
                    card.lowerRight.setForeground(perXp < 0 ? COSTS : PlannerUi.READY);
                }
            }
            String money = calc.showPrices() ? money(row.cost(row.needed), row.earn(row.needed), "") : null;
            if (money != null) {
                card.fourth(money, "");
                net(card.lastRight, row.cost(row.needed), row.earn(row.needed), true);
            }
        }
        if (s.action != null) {
            picture(card.icon, s.action);
        } else {
            image(card.icon, icons.skill(shown, false));
        }
        if (s.done) {
            card.dim();
        } else if (now) {
            card.setBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, SUGGESTED));
        }
        JPopupMenu menu = new JPopupMenu();
        menu.add(menuItem("Edit...", () -> stepDialog(shown, index, plan.goal)));
        menu.add(menuItem("Change method (pick an action)", () -> {
            picking = index;
            search.setText("");
            redraw.run();
        }));
        if (index > 0) {
            menu.add(menuItem("Move up", () -> change(shown, p -> PlannerCalculator.swapSteps(p, index, index - 1))));
        }
        if (index < plan.steps.size() - 1) {
            menu.add(menuItem("Move down", () -> change(shown, p -> PlannerCalculator.swapSteps(p, index, index + 1))));
        }
        menu.add(menuItem("Delete", () -> change(shown, p -> PlannerCalculator.removeStep(p, index))));
        StringBuilder tip = new StringBuilder("<html><b>").append(PlannerUi.esc(step.action)).append("</b>: level ")
            .append(step.from).append(" to ").append(step.to);
        if (s.done) {
            tip.append(" (done)");
        } else if (row != null) {
            tip.append("<br>").append(String.format("%,d", s.xp)).append(" xp: ").append(String.format("%,d", row.needed))
                .append(" actions at ").append(xp(row.xpEach)).append(" xp each");
            if (row.seconds > 0) {
                tip.append("<br>About ").append(PlannerPanel.formatDuration(row.seconds)).append(" at ")
                    .append(String.format("%,d", row.xpHour)).append(" xp an hour (the wiki's training guide)");
            }
            if (s.tooLow()) {
                tip.append("<br><font color='#e66e64'>Needs level ").append(s.action.level).append(" to start</font>");
            }
        }
        if (step.method != null) {
            tip.append("<br><font color='#a0a0a0'>Guide method: ").append(PlannerUi.esc(step.method)).append("</font>");
        }
        tip.append("<br><font color='#a0a0a0'>Right-click to edit, move or delete</font></html>");
        return card.interactive(null, menu, tip.toString());
    }

    // Exporting to the Skilling Helper: only offered when it (or the Toolbelt) is installed
    private JComponent exportRow(Skill shown, SkillPlans.Plan plan, List<CalculatorData.Bonus> on, CalculatorData.Calc skillCalc) {
        if (!calc.trackerInstalled()) {
            return PlannerUi.empty("Install Snuffy's Skilling Helper to follow this plan on its goal card: Export to "
                + "tracker sends it there.");
        }
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(PlannerUi.CARD);
        row.setBorder(new EmptyBorder(6, 7, 6, 7));
        SkillPlans.Plan withXp = withXp(shown, plan, on, skillCalc);
        boolean same = calc.isTracked(shown, withXp);
        boolean any = calc.trackedPlan(shown) != null;
        JLabel status = PlannerUi.label(same ? "In the tracker" : any ? "Tracker has an older plan" : "Not in the tracker",
            same ? PlannerUi.READY : any ? PlannerUi.GROWING : PlannerUi.MUTED);
        status.setMinimumSize(new Dimension(0, 0));
        row.add(status, BorderLayout.CENTER);
        JPanel links = new JPanel();
        links.setLayout(new BoxLayout(links, BoxLayout.X_AXIS));
        links.setOpaque(false);
        if (!same) {
            links.add(PlannerUi.linkLabel(any ? "Update" : "Export to tracker", "Set level " + plan.goal + " as the skill's goal "
                + "and give the Skilling Helper these milestones: its goal card shows the one you're on, with its own bar, "
                + "and suggests and outlines its method" + (any ? " (replaces the plan it has)" : ""), () -> {
                    int game = calc.xp(shown);
                    calc.export(shown, withXp, game >= 0 ? game : current());
                    redraw.run();
                }));
        }
        if (any) {
            if (!same) {
                links.add(PlannerUi.label("  ·  ", PlannerUi.FAINT));
            }
            links.add(PlannerUi.linkLabel("Remove", "Take the plan out of the tracker (the goal stays)", () -> {
                calc.untrack(shown);
                redraw.run();
            }));
        }
        row.add(links, BorderLayout.EAST);
        PlannerUi.tips(row, "<html>Snuffy's Skilling Helper: " + (same ? "it follows this plan" : any
            ? "it follows an earlier version of this plan" : "it doesn't have this plan yet") + "</html>");
        return PlannerUi.spaced(row);
    }

    // The plan with each milestone's XP each (with your bonuses) and guide method filled in, as it's exported
    private SkillPlans.Plan withXp(Skill shown, SkillPlans.Plan plan, List<CalculatorData.Bonus> on, CalculatorData.Calc skillCalc) {
        SkillPlans.Plan out = plan.copy();
        for (SkillPlans.Step step : out.steps) {
            CalculatorData.Action action = CalculatorData.named(skillCalc, step.action);
            if (action != null) {
                step.xp = CalculatorData.xpEach(action, on);
            }
            if (step.method == null || training.named(shown, step.method) == null) {
                TrainingData.Method method = SkillPlans.methodFor(training, shown, step.action, step.from, step.to);
                step.method = method == null ? null : method.name;
            }
        }
        return out;
    }

    // Changes the saved plan and redraws
    private void change(Skill shown, java.util.function.Consumer<SkillPlans.Plan> what) {
        SkillPlans.Plan plan = calc.plan(shown);
        if (plan != null) {
            what.accept(plan);
            calc.savePlan(shown, plan);
            redraw.run();
        }
    }

    private void addToPlan(Skill shown, CalculatorData.Action action, double xpEach) {
        SkillPlans.Plan plan = calc.plan(shown);
        if (plan == null) {
            plan = new SkillPlans.Plan(newGoal());
        }
        TrainingData.Method method = SkillPlans.methodFor(training, shown, action.name, action.level, plan.goal);
        if (!PlannerCalculator.addStep(plan, action, xpEach, method == null ? null : method.name, levelNow())) {
            // The plan is full up to its goal: offer to raise the goal so this goes on after it
            String typed = plan.goal >= 99 ? null : JOptionPane.showInputDialog(list, "Your plan already ends at level "
                + plan.goal + ". Raise its goal to fit " + action.name + " after it (level " + (plan.goal + 1) + "-99):",
                99);
            int goal = typed == null ? -1 : parse(typed);
            if (goal <= plan.goal || goal > 99) {
                if (plan.goal >= 99) {
                    JOptionPane.showMessageDialog(list, "Your plan already reaches 99 from level " + Math.max(levelNow(),
                        action.level) + ". Right-click a milestone to edit its levels or change its method.", "Add to plan",
                        JOptionPane.INFORMATION_MESSAGE);
                }
                return;
            }
            PlannerCalculator.raiseGoal(plan, goal);
            if (!PlannerCalculator.addStep(plan, action, xpEach, method == null ? null : method.name, levelNow())) {
                return;
            }
        }
        calc.savePlan(shown, plan);
        redraw.run();
    }

    private void pickMethod(Skill shown, CalculatorData.Action action, double xpEach) {
        Integer index = picking;
        picking = null;
        if (index != null) {
            change(shown, plan -> {
                SkillPlans.Step step = plan.steps.get(index);
                TrainingData.Method method = SkillPlans.methodFor(training, shown, action.name, step.from, step.to);
                step.action = action.name;
                step.method = method == null ? null : method.name;
                step.xp = xpEach;
            });
        }
        redraw.run();
    }

    private void suggest(Skill shown, List<CalculatorData.Action> actions, List<CalculatorData.Bonus> on, int goal) {
        SkillPlans.Plan old = calc.plan(shown);
        if (old != null && !old.steps.isEmpty() && !confirm("Replace your " + shown.getName() + " plan with the wiki's suggestions?")) {
            return;
        }
        int level = levelNow();
        SkillPlans.Plan plan = PlannerCalculator.suggestedPlan(shown, actions, on, training, level, Math.max(goal, level + 1),
            calc.otherMethods(shown));
        if (plan.steps.isEmpty()) {
            JOptionPane.showMessageDialog(list, "The wiki's training guides have nothing to suggest for " + shown.getName()
                + " here: add milestones from the actions instead.", "Start from suggestions", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        calc.savePlan(shown, plan);
        redraw.run();
    }

    private void changeGoal(Skill shown) {
        SkillPlans.Plan plan = calc.plan(shown);
        if (plan == null) {
            return;
        }
        String typed = JOptionPane.showInputDialog(list, "The plan's final goal (level 2-99):", plan.goal);
        int goal = typed == null ? -1 : parse(typed);
        if (goal < 2 || goal > 99 || goal == plan.goal) {
            return;
        }
        PlannerCalculator.setGoal(plan, goal);
        calc.savePlan(shown, plan);
        redraw.run();
    }

    // Adds a milestone (index -1) or edits one: its method and levels. Neighbours that met it still meet it.
    private void stepDialog(Skill shown, int index, int goal) {
        SkillPlans.Plan plan = calc.plan(shown);
        if (plan == null) {
            plan = new SkillPlans.Plan(goal);
        }
        SkillPlans.Step editing = index >= 0 && index < plan.steps.size() ? plan.steps.get(index) : null;
        CalculatorData.Calc skillCalc = data.calc(shown);
        List<CalculatorData.Action> actions = new ArrayList<>(CalculatorData.shown(skillCalc, calc.showMembers()));
        actions.sort((a, b) -> Integer.compare(a.level, b.level));
        javax.swing.JComboBox<String> method = new javax.swing.JComboBox<>();
        for (CalculatorData.Action action : actions) {
            method.addItem(action.name);
        }
        int last = plan.steps.isEmpty() ? levelNow() : plan.steps.get(plan.steps.size() - 1).to;
        if (editing != null) {
            method.setSelectedItem(editing.action);
        } else {
            CalculatorData.Action best = null;
            for (CalculatorData.Action action : actions) {
                if (action.level <= Math.max(1, last)) {
                    best = action;
                }
            }
            if (best != null) {
                method.setSelectedItem(best.name);
            }
        }
        JTextField from = new JTextField(String.valueOf(editing != null ? editing.from : Math.min(last, plan.goal - 1)));
        JTextField to = new JTextField(String.valueOf(editing != null ? editing.to : plan.goal));
        JPanel form = new JPanel(new GridLayout(0, 1, 2, 2));
        form.add(new JLabel("Method"));
        form.add(method);
        form.add(new JLabel("From level"));
        form.add(from);
        form.add(new JLabel("To level"));
        form.add(to);
        if (JOptionPane.showConfirmDialog(list, form, editing != null ? "Edit milestone" : "Add a milestone",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        int start = parse(from.getText());
        int end = parse(to.getText());
        String name = (String) method.getSelectedItem();
        if (name == null || start < 1 || end <= start || end > 99) {
            JOptionPane.showMessageDialog(list, "The levels need to go up, from 1 to 99.", "Milestone", JOptionPane.WARNING_MESSAGE);
            return;
        }
        CalculatorData.Action action = CalculatorData.named(skillCalc, name);
        TrainingData.Method guide = SkillPlans.methodFor(training, shown, name, start, end);
        List<CalculatorData.Bonus> on = CalculatorData.bonuses(skillCalc, calc.bonuses(shown));
        double xpEach = action == null ? 0 : CalculatorData.xpEach(action, on);
        if (editing != null) {
            for (SkillPlans.Step other : plan.steps) {
                if (other != editing && other.to == editing.from) {
                    other.to = start;
                } else if (other != editing && other.from == editing.to) {
                    other.from = end;
                }
            }
            plan.steps.removeIf(other -> other != editing && other.to <= other.from);
            editing.action = name;
            editing.from = start;
            editing.to = end;
            editing.method = guide == null ? null : guide.name;
            editing.xp = xpEach;
        } else {
            plan.steps.add(new SkillPlans.Step(name, start, end, guide == null ? null : guide.name, xpEach));
        }
        plan.steps.sort((a, b) -> Integer.compare(a.from, b.from));
        plan.goal = Math.max(plan.goal, end);
        calc.savePlan(shown, plan);
        redraw.run();
    }

    private boolean confirm(String question) {
        return JOptionPane.showConfirmDialog(list, question, "Plan", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
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
