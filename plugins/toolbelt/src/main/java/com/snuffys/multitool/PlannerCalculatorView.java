package com.snuffys.multitool;

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
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
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
    /** The plan bar under the list (outside its scrolling): your milestones, open or folded, to compare as you scroll. */
    final JPanel dock = new JPanel(new BorderLayout());
    private String dockKey;
    private final JLabel resetLink;
    private final PlannerUi.Bar goalBar = new PlannerUi.Bar(0, PlannerUi.GROWING);
    private final PlannerPanel.Hinted search = new PlannerPanel.Hinted("Search actions");

    private Skill skill;
    // The goal's card in the Plan view is unfolded (its links showing)
    private boolean goalOpen;
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
        addTo(column, column, redraw);
    }

    /**
     * The skill, its target, the bonuses and the view's tabs in {@code top} (they stay put), the list (actions, banked,
     * the plan) in {@code column} (it scrolls).
     */
    void addTo(JPanel top, JPanel column, Runnable redraw) {
        this.redraw = redraw;
        top.add(header);
        top.add(grid);
        top.add(PlannerUi.spaced(inputs));
        top.add(bonusSlot);
        top.add(listHeader);
        searchSlot = PlannerUi.spaced(searchRow);
        top.add(searchSlot);
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

    /** For the preview: this milestone of the skill's open plan opened to its breakdown. */
    void previewBreakdown(Skill skill, int milestone) {
        openSteps.add(stepKey(skill, calc.plan(skill).steps.get(milestone)));
    }

    /** For the preview: choosing the method for this milestone. */
    void previewPicking(int milestone) {
        picking = milestone;
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
            levels.append(calc.xp(s) >= 0 ? CalculatorData.level(calc.xp(s), false) : 0).append(':').append(calc.goal(s))
                .append(calc.xp(s) / 10_000).append(',');
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
            // (goals are the Skilling Helper's: without it running there are none to show or set)
            goalText.setText(calc.trackerInstalled() ? "No goal set" : "");
            goalText.setForeground(PlannerUi.MUTED);
        }
        goalLink.setVisible(calc.trackerInstalled() && !isGoal && target > Math.max(0, game));
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
        updateDock(shown, mode, planXp(), goal);
        // (no search in Plan view)
        searchSlot.setVisible(mode != PlannerCalculator.Mode.PLAN);
        sortLink.setVisible(mode == PlannerCalculator.Mode.ACTIONS);
        sortLink.setText("Sort: " + calc.sort().shortLabel);
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
            int skillXp = Math.max(0, calc.xp(s));
            boolean hasGoal = calc.goal(s) > skillXp;
            JLabel cell = new JLabel();
            cell.setHorizontalAlignment(SwingConstants.CENTER);
            cell.setPreferredSize(new Dimension(20, 22));
            cell.setOpaque(true);
            cell.setBackground(picked ? new Color(70, 70, 70) : PlannerUi.CARD);
            // Picked: an orange line under it; a skill with a goal (set here or in the Skilling Helper): a gold one
            cell.setBorder(picked ? BorderFactory.createMatteBorder(0, 0, 2, 0, ColorScheme.BRAND_ORANGE)
                : hasGoal ? BorderFactory.createMatteBorder(0, 0, 2, 0, GOAL_GOLD) : new EmptyBorder(0, 0, 2, 0));
            BufferedImage icon = icons.skill(s, true);
            if (icon != null) {
                cell.setIcon(new ImageIcon(picked ? icon : fade(icon, 0.7f)));
            }
            int xp = calc.xp(s);
            cell.setToolTipText(s.getName() + (xp >= 0 ? ": level " + CalculatorData.level(xp, false) : "")
                + (hasGoal ? ", goal " + goalText(calc.goal(s)) : ""));
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
        JComponent goals = goalsList(shown);
        if (goals == null) {
            return box;
        }
        JPanel both = new JPanel(new BorderLayout(0, 0));
        both.setBackground(PlannerUi.CARD);
        both.add(box, BorderLayout.NORTH);
        both.add(goals, BorderLayout.CENTER);
        return both;
    }

    private static final Color GOAL_GOLD = new Color(220, 180, 50);

    // "level 99", or the XP when it's not a level's
    private static String goalText(int goal) {
        int level = CalculatorData.level(goal, true);
        return goal == Experience.getXpForLevel(Math.min(level, 126)) ? "level " + level : String.format("%,d xp", goal);
    }

    // Every skill with a goal ahead of you, set here or in the Skilling Helper (they share them): how far along, and a
    // click goes to that skill; null when there are none
    private JComponent goalsList(Skill shown) {
        JPanel rows = new JPanel();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setOpaque(false);
        rows.setBorder(new EmptyBorder(0, 6, 5, 6));
        int count = 0;
        for (Skill s : skills()) {
            int xp = Math.max(0, calc.xp(s));
            int goal = calc.goal(s);
            if (goal <= xp) {
                continue;
            }
            int start = calc.goalStart(s) > 0 ? Math.min(calc.goalStart(s), xp) : Experience.getXpForLevel(CalculatorData.level(xp, false));
            double done = goal > start ? Math.max(0, Math.min(1, (double) (xp - start) / (goal - start))) : 1;
            int plans = calc.plans(s).size();
            JPanel row = new JPanel(new BorderLayout(6, 0));
            row.setOpaque(false);
            JLabel name = PlannerUi.label(s.getName() + " → " + goalText(goal).replace("level ", ""), s == shown ? Color.WHITE
                : new Color(200, 200, 200));
            name.setMinimumSize(new Dimension(0, 0));
            BufferedImage icon = icons.skill(s, true);
            if (icon != null) {
                name.setIcon(new ImageIcon(icon));
                name.setIconTextGap(5);
            }
            row.add(name, BorderLayout.CENTER);
            row.add(PlannerUi.label((int) Math.floor(done * 100) + "%", GOAL_GOLD), BorderLayout.EAST);
            row.setToolTipText("<html>" + s.getName() + ": goal " + goalText(goal) + ", " + String.format("%,d", goal - xp)
                + " xp to go" + (plans > 0 ? "<br>" + plans + (plans == 1 ? " plan" : " plans") : "")
                + (s == shown ? "" : "<br>Click to open " + s.getName()) + "</html>");
            row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            row.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (s != skill) {
                        pick(s);
                    }
                }
            });
            row.setAlignmentX(JComponent.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
            if (count == 0) {
                JLabel heading = PlannerUi.label("Goals", PlannerUi.MUTED);
                heading.setBorder(new EmptyBorder(2, 0, 2, 0));
                heading.setAlignmentX(JComponent.LEFT_ALIGNMENT);
                rows.add(heading);
            }
            rows.add(row);
            count++;
        }
        return count == 0 ? null : rows;
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
        // Sort: a menu of orders for the Actions list
        sortLink.setBorder(new EmptyBorder(0, 4, 0, 8));
        searchRow.add(sortLink, BorderLayout.EAST);
    }

    private final JLabel sortLink = PlannerUi.linkLabel("Sort", "How the actions are ordered", this::sortMenu);

    private void sortMenu() {
        JPopupMenu menu = new JPopupMenu();
        PlannerCalculator.Sort current = calc.sort();
        for (PlannerCalculator.Sort sort : PlannerCalculator.Sort.values()) {
            javax.swing.JMenuItem item = new javax.swing.JCheckBoxMenuItem(sort.label, sort == current);
            item.addActionListener(e -> {
                calc.setSort(sort);
                redraw.run();
            });
            menu.add(item);
        }
        menu.show(sortLink, 0, sortLink.getHeight());
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
            .append('|').append(calc.trackerInstalled()).append('|').append(picking).append('|').append(calc.canPlan())
            .append('|').append(calc.sort());
        key.append('|').append(calc.owned().hashCode()).append('|').append(calc.buyLimitsKnown()).append(calc.planFolds()).append(calc.planBudget(shown))
            .append('|').append(calc.tradesVersion()).append('|').append(openSteps);
        SkillPlans.Plan plan = calc.plan(shown);
        key.append('|').append(SkillPlans.encodeAll(calc.plans(shown))).append(calc.openIndex(shown)).append(calc.planExpanded()).append(goalOpen).append(calc.pinnedPlanSkill())
            .append(calc.goalPick(shown))
            .append(calc.planCounts(skills()).keySet())
            .append('|').append(plan == null ? "" : SkillPlans.encode(plan)).append(calc.isTracked(shown, plan))
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
            fillPlan(shown, skillCalc, on, planXp(), target, actions);
        } else {
            // Picking a milestone's method: every action priced over that milestone's levels (what's left of it)
            int from = now;
            int to = target;
            if (picking != null) {
                SkillPlans.Step step = calc.plan(shown).steps.get(picking);
                int start = Experience.getXpForLevel(Math.max(1, step.from));
                to = Experience.getXpForLevel(Math.min(99, step.to));
                from = now > start && now < to ? now : start;
            }
            now = from;
            target = to;
            List<PlannerCalculator.Row> rows = PlannerCalculator.rows(shown, actions, on, now, target, recipes, training, owned,
                this::price, calc::itemPrice);
            String filter = search.getText().trim().toLowerCase(Locale.ROOT);
            if (mode == PlannerCalculator.Mode.BANKED) {
                fillBanked(shown, skillCalc, on, now, target, rows, owned, filter);
            } else {
                fillActions(shown, on, now, target, PlannerCalculator.sorted(rows, calc.sort()), filter);
            }
        }
        list.add(note("Where these figures come from", "From RuneLite's Skill Calculator. Times are from the wiki's "
            + "training guides; prices are the Grand Exchange's (or yours, from your trades this week), sales after its 2% tax"));
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
            JComponent best = bestMethods(shown, rows, step);
            if (best != null) {
                list.add(PlannerUi.spaced(best));
            }
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

    // The best picks for a milestone's levels, each a click to use: cheapest per XP, fastest, most profit, most XP each
    // (only actions you can do at its start)
    private JComponent bestMethods(Skill shown, List<PlannerCalculator.Row> rows, SkillPlans.Step step) {
        PlannerCalculator.Row cheapest = null;
        PlannerCalculator.Row fastest = null;
        PlannerCalculator.Row profit = null;
        PlannerCalculator.Row most = null;
        for (PlannerCalculator.Row row : rows) {
            if (row.action.level > step.from || row.needed <= 0) {
                continue;
            }
            Double perXp = calc.showPrices() ? row.perXp() : null;
            if (perXp != null && (cheapest == null || perXp > cheapest.perXp())) {
                cheapest = row;
            }
            if (row.xpHour > 0 && (fastest == null || row.xpHour > fastest.xpHour)) {
                fastest = row;
            }
            double net = netOf(row);
            if (calc.showPrices() && net > 0 && (profit == null || net > netOf(profit))) {
                profit = row;
            }
            if (most == null || row.xpEach > most.xpEach) {
                most = row;
            }
        }
        JPanel box = new JPanel(new GridLayout(0, 1, 0, 2));
        box.setBackground(PlannerUi.CARD);
        box.setBorder(new EmptyBorder(6, 8, 6, 8));
        box.add(PlannerUi.label("Best for " + step.from + " → " + step.to + " (click to use)", Color.WHITE));
        bestLine(box, shown, "Cheapest", cheapest, cheapest == null ? "" : gpPerXp(cheapest.perXp()));
        bestLine(box, shown, "Fastest", fastest, fastest == null ? "" : PlannerPanel.gp(fastest.xpHour) + " xp/hr"
            + (fastest.seconds > 0 ? ", ~" + PlannerPanel.formatDuration(fastest.seconds) : ""));
        bestLine(box, shown, "Most profit", profit, profit == null ? "" : signed(netOf(profit)) + " gp");
        bestLine(box, shown, "Most XP each", most, most == null ? "" : xp(most.xpEach) + " xp, "
            + String.format("%,d", most.needed) + " to go");
        return box.getComponentCount() > 1 ? box : null;
    }

    private static double netOf(PlannerCalculator.Row row) {
        Double cost = row.cost(row.needed);
        Double earn = row.earn(row.needed);
        return (earn == null ? 0 : earn) - (cost == null ? 0 : cost);
    }

    private void bestLine(JPanel box, Skill shown, String what, PlannerCalculator.Row row, String detail) {
        if (row == null) {
            return;
        }
        // Two lines: the pick (a click uses it), then its numbers
        JLabel line = PlannerUi.linkLabel(what + ": " + row.action.name, "Use " + row.action.name + " for this milestone ("
            + detail + ")", () -> pickMethod(shown, row.action, row.xpEach));
        line.setForeground(new Color(220, 220, 220));
        box.add(line);
        JLabel numbers = PlannerUi.label("   " + detail, PlannerUi.MUTED);
        numbers.setToolTipText(line.getToolTipText());
        box.add(numbers);
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
            long value = calc.showPrices() ? PlannerCalculator.bankedValue(shown, skillCalc, on, level, recipes, owned, this::price) : 0;
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
            list.add(note("Open your bank once to count it", "Open your bank once so the Planner can see what's in it; "
                + "until then this counts your inventory"));
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

    // ---- The plan bar (under the list) ----

    // Rebuilt only when what it shows changes; hidden in the Plan view (the plan's already there)
    private void updateDock(Skill shown, PlannerCalculator.Mode mode, int now, int goal) {
        SkillPlans.Plan plan = calc.plan(shown);
        boolean open = calc.dockOpen();
        boolean hide = mode == PlannerCalculator.Mode.PLAN || !calc.canPlan();
        String key = hide ? "hidden" : shown.name() + '|' + open + '|' + now / 1000 + '|' + goal + '|'
            + (plan == null ? "" : SkillPlans.encode(plan));
        if (key.equals(dockKey)) {
            return;
        }
        dockKey = key;
        dock.removeAll();
        dock.setVisible(!hide);
        if (!hide) {
            dock.add(dockBody(shown, plan, open, now, goal), BorderLayout.CENTER);
        }
        dock.revalidate();
        dock.repaint();
    }

    private JComponent dockBody(Skill shown, SkillPlans.Plan plan, boolean open, int now, int goal) {
        JPanel box = new JPanel(new BorderLayout());
        box.setBackground(PlannerUi.CARD);
        box.setBorder(new EmptyBorder(5, 7, 5, 7));
        boolean any = plan != null && !plan.steps.isEmpty();
        // The header: fold/unfold, what the plan is, and a link to the Plan view
        JPanel head = new JPanel(new BorderLayout(6, 0));
        head.setOpaque(false);
        String title = (open ? "▾ " : "▸ ") + (plan == null ? "Your plan" : planName(plan, calc.openIndex(shown)) + ": "
            + plan.steps.size() + (plan.steps.size() == 1 ? " milestone" : " milestones") + " to " + plan.goal);
        JLabel toggle = PlannerUi.label(title, Color.WHITE);
        toggle.setMinimumSize(new Dimension(0, 0));
        toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toggle.setToolTipText(open ? "Fold the plan away" : "Show your plan's milestones here, to compare as you scroll");
        toggle.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                calc.setDockOpen(!open);
                redraw.run();
            }
        });
        head.add(toggle, BorderLayout.CENTER);
        head.add(PlannerUi.linkLabel(any ? "Edit" : "Plan", "Open the Plan view for " + shown.getName(), () -> {
            calc.setMode(PlannerCalculator.Mode.PLAN);
            redraw.run();
        }), BorderLayout.EAST);
        box.add(head, BorderLayout.NORTH);
        if (!open) {
            return box;
        }
        JPanel rows = new JPanel();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setOpaque(false);
        int level = CalculatorData.level(now, false);
        if (goal > now) {
            int goalLvl = CalculatorData.level(goal, true);
            rows.add(dockRow("Goal", goal == Experience.getXpForLevel(Math.min(goalLvl, 126)) ? "Level " + goalLvl
                : String.format("%,d xp", goal), PlannerPanel.gp(goal - now) + " xp", PlannerUi.MUTED, null,
                "The skill's goal (the Skilling Helper's): " + String.format("%,d", goal - now) + " xp to go"));
        }
        if (!any) {
            JLabel none = PlannerUi.label("No milestones yet: right-click an action > Add to plan", PlannerUi.MUTED);
            none.setMinimumSize(new Dimension(0, 0));
            none.setAlignmentX(JComponent.LEFT_ALIGNMENT);
            rows.add(none);
        } else {
            for (SkillPlans.Step step : plan.steps) {
                boolean done = level >= step.to;
                boolean current = !done && level >= step.from;
                String left;
                if (done) {
                    left = "done";
                } else {
                    int end = Experience.getXpForLevel(Math.min(99, step.to));
                    int start = Math.max(now, Experience.getXpForLevel(step.from));
                    left = PlannerPanel.gp(Math.max(0, end - start)) + " xp";
                }
                rows.add(dockRow(step.from + "-" + step.to, step.action, left, done ? PlannerUi.MUTED
                    : current ? Color.WHITE : new Color(200, 200, 200), current ? SUGGESTED : null,
                    "Levels " + step.from + " to " + step.to + ": " + step.action + (current ? " (you're on this one)" : "")
                        + (done ? " (done)" : ", " + left + " to go")));
            }
        }
        JComponent body = rows;
        // A long plan scrolls inside the bar, so the list above keeps its room
        int max = 6;
        if (rows.getComponentCount() > max) {
            JScrollPane scroll = new JScrollPane(rows, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.setBorder(new EmptyBorder(0, 0, 0, 0));
            scroll.setOpaque(false);
            scroll.getViewport().setOpaque(false);
            scroll.getVerticalScrollBar().setUnitIncrement(16);
            scroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
            scroll.setPreferredSize(new Dimension(0, max * rows.getComponent(0).getPreferredSize().height));
            body = scroll;
        }
        JPanel under = new JPanel(new BorderLayout());
        under.setOpaque(false);
        under.setBorder(new EmptyBorder(4, 0, 0, 0));
        under.add(body, BorderLayout.CENTER);
        box.add(under, BorderLayout.CENTER);
        return box;
    }

    // One line of the plan bar: levels, method, XP left (the milestone you're on marked green on the left)
    private static JComponent dockRow(String levels, String method, String left, Color colour, Color mark, String tip) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setBorder(mark == null ? new EmptyBorder(1, 4, 1, 0) : BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 2, 0, 0, mark), new EmptyBorder(1, 2, 1, 0)));
        JLabel from = PlannerUi.label(levels, colour);
        from.setPreferredSize(new Dimension(40, from.getPreferredSize().height));
        JLabel name = PlannerUi.label(method, colour);
        name.setMinimumSize(new Dimension(0, 0));
        row.add(from, BorderLayout.WEST);
        row.add(name, BorderLayout.CENTER);
        row.add(PlannerUi.label(left, PlannerUi.MUTED), BorderLayout.EAST);
        row.setToolTipText(tip);
        row.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
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
            int[] typed = typedLevels(shown);
            if (typed != null) {
                // The levels typed above (Current to Target): it goes in there, the milestones there make room
                menu.add(menuItem("Add to plan (" + typed[0] + "-" + typed[1] + ")",
                    () -> addToPlanAt(shown, action, row.xpEach, typed[0], typed[1])));
            } else {
                menu.add(menuItem("Add to plan", () -> addToPlan(shown, action, row.xpEach)));
            }
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

    /**
     * The money on a card's fourth and fifth lines: "Costs 6.11M" and "Sells 810K" (each only when there's something),
     * then "Net" and the net with its badge; nothing when neither is priced.
     */
    private static void priceLines(PlannerUi.Card card, Double cost, Double earn) {
        double c = cost == null ? 0 : cost;
        double e = earn == null ? 0 : earn;
        if (c <= 0 && e <= 0) {
            return;
        }
        if (c <= 0 || e <= 0) {
            // Only one side (gathering sells, a few things only cost): it and the net on one line
            card.fourth(c > 0 ? "Costs " + PlannerPanel.gp(Math.round(c)) : "Sells " + PlannerPanel.gp(Math.round(e)), "");
            net(card.lastRight, cost, earn, true);
            return;
        }
        card.fourth("Costs " + PlannerPanel.gp(Math.round(c)), "Sells " + PlannerPanel.gp(Math.round(e)));
        card.last.setForeground(new Color(220, 150, 140));
        card.lastRight.setForeground(new Color(150, 210, 150));
        card.fifth("Net", "");
        net(card.fifthRight, cost, earn, true);
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
        return CalculatorData.level(planXp(), false);
    }

    // A plan goes by your real XP (the game's, else the boxes'): typing in Current to try a level out doesn't tick its
    // milestones off as done
    private int planXp() {
        Skill shown = skill();
        int game = shown == null ? -1 : calc.xp(shown);
        return game >= 0 ? game : current();
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
        if (plan == null) {
            fillPlanList(shown, null);
            list.add(PlannerUi.empty((calc.goal(shown) > planXp() ? "No plans for your goal yet: Make a plan (click the "
                + "goal), or start one here. " : "No plans for " + shown.getName() + " yet. ") + "A plan is a goal and milestones on the way, "
                + "each with its own method (\"1-20 Logs, 20-50 Willow logs\"). Make as many as you like and export the "
                + "one you follow to the tracker."));
            int goal = newGoal();
            list.add(linkRow(PlannerUi.linkLabel("Wiki suggestions", "A new plan from the wiki's training guides, from "
                + "your level to " + goal + "; then change any of it", () -> suggest(shown, actions, on, goal)),
                PlannerUi.linkLabel("New plan", "An empty plan: add milestones by right-clicking actions, or +",
                    () -> newPlan(shown))));
            return;
        }
        // The open plan unfolds under its card in the list
        fillPlanList(shown, () -> fillOpenPlan(shown, skillCalc, on, now, actions, plan, level));
    }

    // The open plan: totals, each milestone (what's left of it), the gaps, the whole plan, and exporting it
    private void fillOpenPlan(Skill shown, CalculatorData.Calc skillCalc, List<CalculatorData.Bonus> on, int now,
                              List<CalculatorData.Action> actions, SkillPlans.Plan plan, int level) {
        if (plan.steps.isEmpty()) {
            int goal = plan.goal > level ? plan.goal : newGoal();
            list.add(PlannerUi.group("Milestones", "", PlannerUi.linkLabel("Goal: level " + goal,
                "Change the plan's final goal", () -> changeGoal(shown))));
            list.add(PlannerUi.empty("No milestones yet: right-click an action > Add to plan, or start from the wiki's "
                + "suggestions."));
            list.add(linkRow(PlannerUi.linkLabel("Wiki suggestions", "Fill this plan from the wiki's training "
                + "guides, from your level to " + goal + "; then change any of it", () -> suggest(shown, actions, on, goal)),
                PlannerUi.linkLabel("Add a milestone", "Pick its method and levels", () -> stepDialog(shown, -1, goal))));
            return;
        }
        List<PlannerCalculator.StepRow> steps = PlannerCalculator.stepRows(shown, skillCalc, on, plan, now, recipes, training,
            this::price, calc::itemPrice);
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
        boolean milestonesFolded = calc.planFolded("milestones");
        list.add(section("milestones", "Milestones", milestonesFolded ? plan.steps.size() + " in all" : "", goalLink,
            new PlannerUi.IconButton(new PlannerUi.Glyph(PlannerUi.Shape.PLUS, 14, ColorScheme.LIGHT_GRAY_COLOR),
                "Add a milestone", () -> stepDialog(shown, -1, plan.goal))));
        int expected = level;
        for (int i = 0; i < steps.size() && !milestonesFolded; i++) {
            PlannerCalculator.StepRow step = steps.get(i);
            if (step.step.from > expected && step.step.from > level) {
                list.add(gap(shown, plan, expected, step.step.from));
            }
            list.add(PlannerUi.spaced(stepCard(shown, plan, i, step, level)));
            if (openSteps.contains(stepKey(shown, step.step)) && !step.done) {
                JComponent parts = breakdown(shown, step);
                if (parts != null) {
                    list.add(PlannerUi.spaced(parts));
                }
            }
            expected = Math.max(expected, step.step.to);
        }
        if (expected < plan.goal && !milestonesFolded) {
            list.add(gap(shown, plan, expected, plan.goal));
        }
        if (totals.xp > 0) {
            list.add(PlannerUi.spaced(planSummary(steps, totals, level, endLevel)));
        }
        fillMaterials(shown, steps);
        fillMoneyPlan(shown, steps);
        list.add(linkRow(PlannerUi.linkLabel("Wiki suggestions", "Replace the milestones with the wiki's training "
                + "guides' methods from your level to " + plan.goal, () -> suggest(shown, actions, on, plan.goal)),
            PlannerUi.linkLabel("Delete plan", "Delete this plan (the tracker keeps it if you exported it)", () -> {
                if (confirm("Delete the plan \"" + planName(plan, calc.openIndex(shown)) + "\"?")) {
                    calc.savePlan(shown, null);
                    redraw.run();
                }
            })));
        list.add(exportRow(shown, plan, on, skillCalc));
    }

    // The whole plan added up, under its milestones (each card above is its own levels only): XP, time to the goal,
    // the average XP an hour over it, and the money
    private JComponent planSummary(List<PlannerCalculator.StepRow> steps, PlannerCalculator.Totals totals, int level, int endLevel) {
        long timedXp = 0;
        for (PlannerCalculator.StepRow s : steps) {
            if (!s.done && s.row != null && s.row.seconds > 0) {
                timedXp += s.xp;
            }
        }
        long perHour = totals.seconds > 0 ? Math.round(timedXp * 3600.0 / totals.seconds) : 0;
        // The time where the guides give rates; the milestones without one are said, not hidden behind "no XP rate"
        int milestones = 0;
        for (PlannerCalculator.StepRow s : steps) {
            if (!s.done && s.xp > 0) {
                milestones++;
            }
        }
        String time = totals.seconds > 0 ? "~" + PlannerPanel.formatDuration(totals.seconds)
            + (totals.untimed > 0 ? " for " + (milestones - totals.untimed) + " of " + milestones : "")
            : "no XP rates";
        PlannerUi.Card card = new PlannerUi.Card("Whole plan: " + level + " → " + endLevel,
            milestones + " milestone" + (milestones == 1 ? "" : "s") + " to go", PlannerPanel.gp(totals.xp) + " xp");
        card.right.setForeground(Color.WHITE);
        boolean prices = calc.showPrices() && (totals.cost > 0 || totals.earn > 0);
        double net = totals.earn - totals.cost;
        card.third(time, prices ? gpPerXp(net / totals.xp) : "");
        if (prices) {
            card.lowerRight.setForeground(net < 0 ? COSTS : PlannerUi.READY);
            priceLines(card, totals.cost, totals.earn);
        }
        card.icon.setIcon(new ImageIcon(PlannerUi.picture(new PlannerUi.Glyph(PlannerUi.Shape.CALCULATOR, 20,
            new Color(200, 200, 200)))));
        card.setBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, PlannerUi.GROWING));
        StringBuilder tip = new StringBuilder("<html><b>The whole plan</b> (the milestones added up; each card above is "
            + "its own levels only)<br>").append(String.format("%,d", totals.xp)).append(" xp from level ").append(level)
            .append(" to ").append(endLevel);
        if (totals.seconds > 0) {
            tip.append("<br>About ").append(PlannerPanel.formatDuration(totals.seconds)).append(" at the wiki's training guides' "
                + "rates (").append(String.format("%,d", perHour)).append(" xp an hour on average)");
        }
        if (totals.untimed > 0) {
            tip.append("<br>").append(totals.untimed).append(totals.untimed == 1 ? " milestone has" : " milestones have")
                .append(" no XP rate, so the time leaves ").append(totals.untimed == 1 ? "it" : "them").append(" out");
        }
        if (prices) {
            tip.append("<br>Materials: worth ").append(String.format("%,.0f", totals.cost)).append(" gp on the GE")
                .append("<br>Sells for: ").append(String.format("%,.0f", totals.earn)).append(" gp")
                .append("<br>").append(netTip(totals.cost, totals.earn));
        }
        return card.interactive(null, null, tip.append("</html>").toString());
    }

    // An item's price for the plans: yours from your trades this week (Use Your Own Prices), else the GE's
    private int price(String name) {
        Long yours = calc.yourPrice(name);
        return yours != null && yours > 0 ? (int) Math.min(Integer.MAX_VALUE, yours) : icons.price(name);
    }

    // Milestones opened to their breakdown (by skill, levels and method)
    private final java.util.Set<String> openSteps = new java.util.HashSet<>();

    private static String stepKey(Skill shown, SkillPlans.Step step) {
        return shown.name() + ":" + step.from + "-" + step.to + ":" + step.action;
    }

    // A milestone's money, line by line: for one of what it makes, each material (how many x its price) and what it
    // sells for, and the net; then the same for all of them
    private JComponent breakdown(Skill shown, PlannerCalculator.StepRow s) {
        PlannerCalculator.Row row = s.row;
        if (row == null || row.needed <= 0) {
            return null;
        }
        List<PlannerCalculator.Need> needs = PlannerCalculator.planMaterials(shown, java.util.Collections.singletonList(s),
            recipes, calc.owned());
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(new Color(30, 30, 30));
        box.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, PlannerUi.GROWING),
            new EmptyBorder(5, 8, 5, 7)));
        boolean prices = calc.showPrices();
        box.add(breakdownLine("Each " + s.step.action, "", Color.WHITE, Color.WHITE));
        double materials = 0;
        int kinds = 0;
        for (PlannerCalculator.Need need : needs) {
            if (need.tool) {
                continue;
            }
            kinds++;
            double per = (double) need.needed / row.needed;
            int each = price(need.name);
            materials += per * each;
            boolean yours = calc.yourPrice(need.name) != null;
            box.add(breakdownLine("  " + need.name + " " + amount(per) + (prices ? " x " + String.format("%,d", each)
                + (yours ? "*" : "") : ""), prices ? String.format("%,.0f", per * each) : "", PlannerUi.MUTED, new Color(220, 150, 140)));
        }
        if (prices) {
            if (materials > 0 && kinds > 1) {
                box.add(breakdownLine("  Materials", String.format("%,.0f", materials), PlannerUi.MUTED, new Color(220, 150, 140)));
            }
            if (row.earnEach != null) {
                box.add(breakdownLine("  Sells for (after tax)" + (calc.yourPrice(s.step.action) != null ? "*" : ""),
                    String.format("%,.0f", row.earnEach), PlannerUi.MUTED, new Color(150, 210, 150)));
            }
            double net = (row.earnEach == null ? 0 : row.earnEach) - materials;
            if (materials > 0 || row.earnEach != null) {
                box.add(breakdownLine("  Net each", (net < 0 ? "-" : "+") + String.format("%,.1f", Math.abs(net)), PlannerUi.MUTED,
                    net < 0 ? COSTS : PlannerUi.READY));
            }
        }
        box.add(breakdownLine("All " + String.format("%,d", row.needed), "", Color.WHITE, Color.WHITE));
        for (PlannerCalculator.Need need : needs) {
            if (need.tool) {
                box.add(breakdownLine("  " + need.name + " (tool)", need.enough() ? "have it" : "to get", PlannerUi.MUTED,
                    need.enough() ? PlannerUi.READY : COSTS));
                continue;
            }
            box.add(breakdownLine("  " + need.name + " " + PlannerPanel.gp(need.needed),
                prices ? PlannerPanel.gp(need.needed * (long) price(need.name)) : "", PlannerUi.MUTED, new Color(220, 150, 140)));
        }
        if (prices) {
            Double cost = row.cost(row.needed);
            Double earn = row.earn(row.needed);
            if (earn != null) {
                box.add(breakdownLine("  Sells for (after tax)", PlannerPanel.gp(Math.round(earn)), PlannerUi.MUTED,
                    new Color(150, 210, 150)));
            }
            if (cost != null || earn != null) {
                double net = (earn == null ? 0 : earn) - (cost == null ? 0 : cost);
                box.add(breakdownLine("  Net", signed(net), PlannerUi.MUTED, net < 0 ? COSTS : PlannerUi.READY));
            }
        }
        JComponent wrapped = PlannerUi.fullWidth(box);
        wrapped.setToolTipText("<html>Each material for one, and for all of this milestone, at "
            + "Grand Exchange prices (* your price, from your trades this week); sales after the 2% tax</html>");
        return wrapped;
    }

    private static JPanel breakdownLine(String left, String right, Color leftColour, Color rightColour) {
        JPanel line = new JPanel(new BorderLayout(6, 0));
        line.setOpaque(false);
        JLabel name = PlannerUi.label(left, leftColour);
        name.setMinimumSize(new Dimension(0, 0));
        line.add(name, BorderLayout.CENTER);
        line.add(PlannerUi.label(right, rightColour), BorderLayout.EAST);
        return line;
    }

    // "1", "0.1", "2.5"
    private static String amount(double value) {
        return value == Math.rint(value) ? String.format("%,d", (long) value) : String.format("%,.2f", value).replaceAll("0+$", "");
    }

    // A section of the open plan with a fold arrow: click its header to fold it away or open it (remembered)
    private JComponent section(String key, String title, String summary, JComponent... actions) {
        boolean folded = calc.planFolded(key);
        JComponent row = PlannerUi.group((folded ? "\u25B6 " : "\u25BC ") + title, summary, actions);
        row.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        row.setToolTipText(folded ? "Click to open" : "Click to fold away");
        MouseAdapter click = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                calc.setPlanFolded(key, !folded);
                redraw.run();
            }
        };
        row.addMouseListener(click);
        for (java.awt.Component child : row.getComponents()) {
            child.addMouseListener(click);
        }
        return row;
    }

    // ---- The money plan: buying, making and selling the plan within a budget ----

    private void fillMoneyPlan(Skill shown, List<PlannerCalculator.StepRow> steps) {
        if (!calc.showPrices()) {
            return;
        }
        long set = calc.planBudget(shown);
        long coins = calc.coins();
        long budget = set > 0 ? set : coins;
        java.util.function.ToIntFunction<String> limits = name -> {
            int id = icons.itemId(name);
            return id > 0 ? calc.buyLimit(id) : 0;
        };
        Map<String, Integer> owned = calc.owned();
        PlannerCalculator.MoneyPlan money = PlannerCalculator.moneyPlan(shown, steps, recipes, owned, budget, this::price, limits);
        if (money.steps.isEmpty()) {
            return;
        }
        long needed = PlannerCalculator.budgetNeeded(shown, steps, recipes, owned, this::price, limits);
        JLabel budgetLink = PlannerUi.linkLabel(set > 0 ? "Budget: " + PlannerPanel.gp(set) : "Set a budget",
            "How much this plan may use (the rest of your cash is left alone)", () -> askBudget(shown, coins));
        boolean folded = calc.planFolded("money");
        String summary = money.runsOut != null ? "short " + PlannerPanel.gp(Math.max(0, needed - budget)) : "enough";
        list.add(section("money", "Money plan", summary, budgetLink));
        if (folded) {
            return;
        }
        list.add(PlannerUi.spaced(moneySummary(shown, money, set, coins, needed)));
        JComponent real = moneySoFar(shown, money, set);
        if (real != null) {
            list.add(PlannerUi.spaced(real));
        }
        for (PlannerCalculator.MoneyStep m : money.steps) {
            list.add(PlannerUi.spaced(moneyCard(m)));
        }
        if (money.runsOut != null) {
            int after = 0;
            for (PlannerCalculator.StepRow s : steps) {
                if (!s.done && s.step.from >= money.runsOut.step.step.to) {
                    after++;
                }
            }
            if (after > 0) {
                list.add(note(after + (after == 1 ? " milestone" : " milestones") + " after it not reached",
                    "With this budget the plan stops at " + money.runsOut.step.step.action + ". A budget of "
                    + PlannerPanel.gp(needed) + " carries the whole plan, or pick a cheaper method (right-click a "
                    + "milestone > Find a better method)"));
            }
        }
    }

    private void askBudget(Skill shown, long coins) {
        String typed = JOptionPane.showInputDialog(list, "How much should this plan use? (like 50m, 1.2b, 750k)\n"
            + "Leave it empty to use all your coins (" + PlannerPanel.gp(coins) + " seen in your bank and inventory).",
            calc.planBudget(shown) > 0 ? PlannerPanel.gp(calc.planBudget(shown)) : "");
        if (typed == null) {
            return;
        }
        long gp = parseGp(typed);
        if (gp < 0) {
            JOptionPane.showMessageDialog(list, "Type an amount like 50m, 1.2b or 750k.", "Budget", JOptionPane.WARNING_MESSAGE);
            return;
        }
        calc.setPlanBudget(shown, gp);
        redraw.run();
    }

    /** "50m", "1.2b", "750k", "1,000,000" in gp; 0 for empty; -1 when it isn't an amount. */
    static long parseGp(String text) {
        String t = text.trim().toLowerCase(Locale.ROOT).replace(",", "").replace(" ", "").replace("gp", "");
        if (t.isEmpty()) {
            return 0;
        }
        double times = 1;
        char last = t.charAt(t.length() - 1);
        if (last == 'k' || last == 'm' || last == 'b') {
            times = last == 'k' ? 1e3 : last == 'm' ? 1e6 : 1e9;
            t = t.substring(0, t.length() - 1);
        }
        try {
            double value = Double.parseDouble(t) * times;
            return value < 0 ? -1 : (long) Math.floor(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // The budget (set, or your coins), what the plan costs after sales, the budget the whole plan needs, and what's left
    // at the goal or how short it falls; a click sets the budget
    private JComponent moneySummary(Skill shown, PlannerCalculator.MoneyPlan money, long set, long coins, long needed) {
        PlannerUi.Card card = new PlannerUi.Card(set > 0 ? "Budget: " + PlannerPanel.gp(set) : "Budget: not set",
            set > 0 ? "Your coins" : "Using your coins", PlannerPanel.gp(coins));
        card.third("Plan costs after sales", signed(-money.loss));
        card.lowerRight.setForeground(money.loss > 0 ? COSTS : PlannerUi.READY);
        card.fourth("Budget it needs", PlannerPanel.gp(needed));
        card.lastRight.setForeground(needed > money.budget ? COSTS : PlannerUi.READY);
        if (money.runsOut != null) {
            card.fifth("Short by", PlannerPanel.gp(Math.max(0, needed - money.budget)));
            card.fifth.setForeground(COSTS);
            card.fifthRight.setForeground(COSTS);
            card.setBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, COSTS));
        } else {
            card.fifth("Left at the goal", PlannerPanel.gp(Math.max(0, money.end())));
            card.fifthRight.setForeground(PlannerUi.READY);
            card.setBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, PlannerUi.READY));
        }
        card.icon.setIcon(new ImageIcon(PlannerUi.picture(new PlannerUi.Glyph(PlannerUi.Shape.CALCULATOR, 20,
            new Color(200, 200, 200)))));
        String tip = "<html><b>Money plan</b>: buy what each milestone needs that you don't have, make it, sell it, and "
            + "buy the next lot with what it brought in, so you only need a round's worth at once<br>"
            + (set > 0 ? "Your budget: " + String.format("%,d", set) + " gp" : "No budget set: it uses all your coins ("
            + String.format("%,d", coins) + " gp, bank as last opened plus inventory)")
            + "<br>The whole plan needs a budget of about " + String.format("%,d", needed) + " gp"
            + "<br>The most one round needs: " + String.format("%,d", money.peak) + " gp"
            + (money.limitHours > 0 ? "<br>The buy limits make it take at least " + PlannerPanel.formatDuration(money.limitHours * 3600) : "")
            + "<br>Sales are after the Grand Exchange's 2% tax; prices are today's, so it's a guide"
            + yourPricesLine(planItems(money))
            + "<br><font color='#a0a0a0'>Click to set the budget</font></html>";
        return card.interactive(() -> askBudget(shown, coins), null, tip);
    }

    // What you've really spent and sold for the plan since the budget was set (your GE trades of its materials and
    // products), and the budget that leaves; null before any
    private JComponent moneySoFar(Skill shown, PlannerCalculator.MoneyPlan money, long set) {
        long since = calc.planBudgetSince(shown);
        if (set <= 0 || since <= 0) {
            return null;
        }
        java.util.Set<String> items = planItems(money);
        long[] traded = calc.traded(items, since);
        if (traded[0] <= 0 && traded[1] <= 0) {
            return null;
        }
        long left = set - traded[0] + traded[1];
        PlannerUi.Card card = new PlannerUi.Card("Since you set the budget", "Spent buying", PlannerPanel.gp(traded[0]));
        card.right.setForeground(new Color(220, 150, 140));
        card.third("Sold for (after tax)", PlannerPanel.gp(traded[1]));
        card.lowerRight.setForeground(new Color(150, 210, 150));
        card.fourth("Budget left", PlannerPanel.gp(Math.max(0, left)));
        card.lastRight.setForeground(left >= 0 ? Color.WHITE : COSTS);
        card.icon.setIcon(new ImageIcon(PlannerUi.picture(new PlannerUi.Glyph(PlannerUi.Shape.CLOCK, 20,
            new Color(200, 200, 200)))));
        card.setBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, PlannerUi.GROWING));
        String tip = "<html><b>Your real trades</b> for this plan since you set its budget ("
            + java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a").format(java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(since), java.time.ZoneId.systemDefault()))
            + "): buys of its materials and sales of what it makes, as they filled on the Grand Exchange<br>"
            + "Spent " + String.format("%,d", traded[0]) + " gp, brought in " + String.format("%,d", traded[1])
            + " gp after the 2% tax<br>Budget " + String.format("%,d", set) + " gp: " + String.format("%,d", left)
            + " gp left<br><font color='#a0a0a0'>Only trades made while RuneLite ran with the Planner on</font></html>";
        return card.interactive(null, null, tip);
    }

    // The items a money plan buys and sells: its materials and each milestone's product
    private static java.util.Set<String> planItems(PlannerCalculator.MoneyPlan money) {
        java.util.Set<String> items = new java.util.LinkedHashSet<>();
        for (PlannerCalculator.MoneyStep m : money.steps) {
            for (PlannerCalculator.Buy buy : m.buys) {
                items.add(buy.name);
            }
            if (m.step.step.action != null) {
                items.add(m.step.step.action);
            }
        }
        return items;
    }

    // "Your prices: Adamant dart tip 185, Adamant dart 190" for a tooltip (empty when none)
    private String yourPricesLine(java.util.Collection<String> items) {
        List<String> parts = new ArrayList<>();
        for (String item : items) {
            Long yours = calc.yourPrice(item);
            if (yours != null) {
                parts.add(PlannerUi.esc(item) + " " + String.format("%,d", yours));
            }
        }
        return parts.isEmpty() ? "" : "<br>Your own prices (your trades this week): " + String.join(", ", parts)
            + "; GE prices for the rest";
    }

    // A milestone's money: how much to buy, in how many rounds, what it sells for, and the budget after it
    private JComponent moneyCard(PlannerCalculator.MoneyStep m) {
        SkillPlans.Step step = m.step.step;
        int kinds = m.buys.size();
        PlannerUi.Card card = new PlannerUi.Card(step.action, kinds == 0 ? "Nothing to buy"
            : "Buys " + kinds + (kinds == 1 ? " material" : " materials"), m.cost > 0 ? PlannerPanel.gp(m.cost) : "");
        card.corner.setText(step.from + " → " + step.to);
        card.right.setForeground(new Color(220, 150, 140));
        if (m.runsOut) {
            card.third("Not enough budget", "");
            card.lower.setForeground(COSTS);
            card.fourth("It loses", PlannerPanel.gp(m.loss()));
            card.lastRight.setForeground(COSTS);
            card.fifth("Budget left", PlannerPanel.gp(Math.max(0, m.budgetBefore)));
            card.setBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, COSTS));
        } else {
            boolean byLimit = m.limitRounds >= m.cashRounds;
            card.third(m.rounds == 1 ? "In one go" : m.rounds + " rounds of " + PlannerPanel.gp(m.perRound),
                m.rounds > 1 ? (byLimit ? "buy limit" : "budget") : "");
            card.fourth(m.earn > 0 ? "Sells " + PlannerPanel.gp(m.earn) : "Nothing to sell", "");
            net(card.lastRight, (double) m.cost, (double) m.earn, true);
            card.fifth("Budget after", PlannerPanel.gp(Math.max(0, m.budgetAfter)));
            card.fifthRight.setForeground(Color.WHITE);
        }
        if (m.step.action != null) {
            picture(card.icon, m.step.action);
        }
        StringBuilder tip = new StringBuilder("<html><b>").append(PlannerUi.esc(step.action)).append("</b> ").append(step.from)
            .append(" to ").append(step.to);
        for (PlannerCalculator.Buy buy : m.buys) {
            tip.append("<br>Buy ").append(String.format("%,d", buy.quantity)).append(" ").append(PlannerUi.esc(buy.name))
                .append(": ").append(String.format("%,d", buy.cost)).append(" gp")
                .append(buy.limit > 0 ? " (limit " + String.format("%,d", buy.limit) + " a 4 hours)" : "");
        }
        if (m.runsOut) {
            tip.append("<br><font color='#e66e64'>It loses ").append(String.format("%,d", m.loss())).append(" gp; the budget has ")
                .append(String.format("%,d", Math.max(0, m.budgetBefore))).append(" gp left here</font>");
        } else if (m.rounds > 1) {
            tip.append("<br>").append(m.rounds).append(" rounds: buy about ").append(String.format("%,d", m.perRound))
                .append(" gp of it, make it, sell it, again")
                .append(m.limitRounds > 1 ? "<br>The buy limits allow one round a 4 hours: at least "
                    + PlannerPanel.formatDuration((m.limitRounds - 1) * 4 * 3600) : "");
        }
        tip.append("<br>Budget: ").append(String.format("%,d", Math.max(0, m.budgetBefore))).append(" gp before")
            .append(m.runsOut ? "" : ", " + String.format("%,d", Math.max(0, m.budgetAfter)) + " after").append("</html>");
        return card.interactive(null, null, tip.toString());
    }

    // How long what's short takes to buy at the item's Grand Exchange buy limit (and what's left of it now)
    private String buyTime(PlannerCalculator.Need need) {
        Long yours = calc.yourPrice(need.name);
        String own = yours == null ? "" : "<br>Your price: " + String.format("%,d", yours) + " gp (your trades this week; GE "
            + String.format("%,d", icons.price(need.name)) + ")";
        return own + buyLimitTime(need);
    }

    private String buyLimitTime(PlannerCalculator.Need need) {
        long shortBy = need.needed - need.have;
        int id = shortBy > 0 ? icons.itemId(need.name) : -1;
        int limit = id > 0 ? calc.buyLimit(id) : 0;
        if (limit <= 0) {
            return "";
        }
        int left = calc.buyLimitLeft(id);
        long now = left < 0 ? limit : left;
        long windows = shortBy <= now ? 0 : (shortBy - now + limit - 1) / limit;
        return "<br>Buy limit " + String.format("%,d", limit) + " every 4 hours"
            + (left >= 0 ? " (" + String.format("%,d", left) + " left now)" : "")
            + (windows == 0 ? ": you can buy it all now" : ": about " + PlannerPanel.formatDuration(windows * 4 * 3600)
            + " to buy it all");
    }

    // What the plan's milestones still need, in full, green when your bank and inventory have all of it, red when not
    private void fillMaterials(Skill shown, List<PlannerCalculator.StepRow> steps) {
        Map<String, Integer> owned = calc.owned();
        List<PlannerCalculator.Need> needs = PlannerCalculator.planMaterials(shown, steps, recipes, owned);
        if (needs.isEmpty()) {
            return;
        }
        int short_ = 0;
        for (PlannerCalculator.Need need : needs) {
            if (!need.enough()) {
                short_++;
            }
        }
        list.add(section("materials", "Materials needed", short_ == 0 ? "you have it all" : short_ + " short"));
        if (calc.planFolded("materials")) {
            return;
        }
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(PlannerUi.CARD);
        box.setBorder(new EmptyBorder(5, 7, 5, 7));
        for (PlannerCalculator.Need need : needs) {
            Color colour = need.enough() ? PlannerUi.READY : COSTS;
            String amounts = need.tool ? (need.enough() ? "have it" : "missing")
                : String.format("%,d / %,d", need.have, need.needed);
            String tip = need.tool ? need.name + ": a tool the plan's methods need" + (need.enough() ? " (you have one)"
                : " (not in your bank or inventory)")
                : "<html>" + PlannerUi.esc(need.name) + ": the plan needs " + String.format("%,d", need.needed)
                + ", you have " + String.format("%,d", need.have) + (need.enough() ? "" : "<br><font color='#e66e64'>"
                + String.format("%,d", need.needed - need.have) + " short</font>") + buyTime(need)
                + "<br>(bank as last opened plus inventory)</html>";
            JPanel row = new JPanel(new BorderLayout(6, 0));
            row.setOpaque(false);
            JLabel name = PlannerUi.label(need.name, Color.WHITE);
            name.setMinimumSize(new Dimension(0, 0));
            row.add(name, BorderLayout.CENTER);
            row.add(PlannerUi.label(amounts, colour), BorderLayout.EAST);
            row.setToolTipText(tip);
            row.setAlignmentX(JComponent.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
            box.add(row);
        }
        list.add(PlannerUi.spaced(box));
        if (!calc.bankSeen()) {
            list.add(note("Open your bank once to count it", "Open your bank once so these count what's in it; until then "
                + "it's your inventory"));
        }
    }

    // ---- Saved plans ----

    // A plan's name: what you called it, else "Plan 2"
    private static String planName(SkillPlans.Plan plan, int index) {
        return plan.name != null ? plan.name : "Plan " + (index + 1);
    }

    // The skill's saved plans (click one to open it, right-click for more), New plan, and other skills' plans
    private void fillPlanList(Skill shown, Runnable openPlan) {
        List<SkillPlans.Plan> plans = calc.plans(shown);
        int open = calc.openIndex(shown);
        list.add(PlannerUi.group(shown.getName() + (calc.goal(shown) > planXp() ? " goal and plans" : " plans"),
            plans.isEmpty() ? "" : String.valueOf(plans.size()),
            new PlannerUi.IconButton(new PlannerUi.Glyph(PlannerUi.Shape.PLUS, 14, ColorScheme.LIGHT_GRAY_COLOR),
                "New plan", () -> newPlan(shown))));
        int level = levelNow();
        int xp = planXp();
        boolean expanded = calc.planExpanded();
        goalCard(shown, plans, xp, level);
        for (int i = 0; i < plans.size(); i++) {
            SkillPlans.Plan plan = plans.get(i);
            int index = i;
            boolean isOpen = i == open;
            boolean unfolded = isOpen && expanded;
            boolean tracked = calc.isTracked(shown, plan);
            int start = plan.steps.isEmpty() ? level : Math.min(level, plan.steps.get(0).from);
            // How far you are from where it starts to its goal (by your real XP)
            int from = Experience.getXpForLevel(Math.max(1, start));
            int to = plan.goalXp();
            double done = to > from ? Math.max(0, Math.min(1, (double) (xp - from) / (to - from))) : 1;
            PlannerUi.Card card = new PlannerUi.Card((unfolded ? "▾ " : "▸ ") + planName(plan, i), start + " → " + plan.goal
                + " · " + plan.steps.size() + (plan.steps.size() == 1 ? " milestone" : " milestones"),
                "lvl " + level + " · " + (int) Math.floor(done * 100) + "%");
            card.right.setForeground(Color.WHITE);
            card.corner.setText(tracked ? "In tracker" : "");
            card.corner.setForeground(PlannerUi.READY);
            card.noIcon();
            card.bar(new PlannerUi.Bar(done, done >= 1 ? PlannerUi.READY : PlannerUi.GROWING));
            if (isOpen) {
                card.setBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, SUGGESTED));
            } else {
                card.title.setForeground(new Color(200, 200, 200));
            }
            // Pin to the game screen (the plan overlay, which is only there without the Skilling Helper): solid on the
            // pinned plan, faint on the others; pinning opens it
            boolean pinned = isOpen && shown == calc.pinnedPlanSkill();
            if (!calc.trackerInstalled()) {
                card.east(new PlannerUi.IconButton(new PlannerUi.Glyph(PlannerUi.Shape.PIN, 13, pinned ? Color.WHITE
                    : new Color(110, 110, 110)), pinned ? "Unpin it from the game screen" : "Pin it to the game screen "
                    + "(shown even when you're training something else)", () -> {
                        if (pinned) {
                            calc.setPinnedPlanSkill(null);
                        } else {
                            calc.openPlan(shown, index);
                            calc.setPinnedPlanSkill(shown);
                        }
                        redraw.run();
                    }));
            }
            JPopupMenu menu = new JPopupMenu();
            if (!isOpen) {
                menu.add(menuItem("Open", () -> openPlan(shown, index)));
            }
            if (!calc.trackerInstalled()) {
                menu.add(menuItem(pinned ? "Unpin from screen" : "Pin to screen", () -> {
                    if (pinned) {
                        calc.setPinnedPlanSkill(null);
                    } else {
                        calc.openPlan(shown, index);
                        calc.setPinnedPlanSkill(shown);
                    }
                    redraw.run();
                }));
            }
            menu.add(menuItem("Rename...", () -> renamePlan(shown, index, plan)));
            menu.add(menuItem("Duplicate", () -> {
                SkillPlans.Plan copy = plan.copy();
                copy.name = planName(plan, index) + " (copy)";
                calc.newPlan(shown, copy);
                redraw.run();
            }));
            menu.add(menuItem("Delete...", () -> {
                if (confirm("Delete the plan \"" + planName(plan, index) + "\"?")) {
                    calc.deletePlan(shown, index);
                    redraw.run();
                }
            }));
            // A click unfolds it (opening it) or folds it away
            Runnable click = isOpen ? () -> {
                calc.setPlanExpanded(!expanded);
                redraw.run();
            } : () -> {
                calc.setPlanExpanded(true);
                openPlan(shown, index);
            };
            list.add(PlannerUi.spaced(card.interactive(click, menu, "<html><b>"
                + PlannerUi.esc(planName(plan, i)) + "</b>: level " + start + " to " + plan.goal + ", " + plan.steps.size()
                + (plan.steps.size() == 1 ? " milestone" : " milestones") + "<br>" + (int) Math.floor(done * 100)
                + "% of the way (you're level " + level + ")" + (unfolded ? "<br>Click to fold it away" : "<br>Click to open it")
                + (tracked ? "<br>It's the plan the tracker follows" : "") + "<br>Right-click to rename, duplicate or delete</html>")));
            if (unfolded && openPlan != null) {
                openPlan.run();
            }
        }
        // Other skills' plans: a click goes to that skill's
        Map<Skill, Integer> others = calc.planCounts(skills());
        others.remove(shown);
        if (!others.isEmpty()) {
            JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
            row.setBackground(PlannerUi.BACKGROUND);
            row.setBorder(new EmptyBorder(0, 2, 6, 0));
            row.add(PlannerUi.label("Other skills: ", PlannerUi.MUTED));
            boolean first = true;
            for (Map.Entry<Skill, Integer> e : others.entrySet()) {
                if (!first) {
                    row.add(PlannerUi.label(" · ", PlannerUi.FAINT));
                }
                first = false;
                Skill other = e.getKey();
                row.add(PlannerUi.linkLabel(other.getName() + " (" + e.getValue() + ")", "Your " + other.getName() + " plans",
                    () -> pick(other)));
            }
            list.add(PlannerUi.fullWidth(row));
        }
    }

    // The skill's goal (set here or in the Skilling Helper: the same goal) as a card like a plan's: its levels, what
    // it's set to train, how far along; click it for Change goal, Remove goal and Make a plan for it
    private void goalCard(Skill shown, List<SkillPlans.Plan> plans, int xp, int level) {
        int goal = calc.goal(shown);
        if (goal <= xp) {
            return;
        }
        int start = calc.goalStart(shown) > 0 ? Math.min(calc.goalStart(shown), xp) : Experience.getXpForLevel(level);
        double done = goal > start ? Math.max(0, Math.min(1, (double) (xp - start) / (goal - start))) : 1;
        SkillPlans.Plan tracked = calc.trackedPlan(shown);
        String pick = calc.goalPick(shown);
        String follows = tracked != null ? "plan: " + (tracked.name != null ? tracked.name : "exported plan")
            : pick != null ? pick : "the wiki's suggestion";
        PlannerUi.Card card = new PlannerUi.Card((goalOpen ? "▾ " : "▸ ") + "Goal: " + goalText(goal),
            CalculatorData.level(start, false) + " → " + CalculatorData.level(goal, true) + " · " + follows,
            "lvl " + level + " · " + (int) Math.floor(done * 100) + "%");
        card.right.setForeground(Color.WHITE);
        card.corner.setText(calc.trackerInstalled() ? "In tracker" : "");
        card.corner.setForeground(GOAL_GOLD);
        card.noIcon();
        card.bar(new PlannerUi.Bar(done, GOAL_GOLD));
        card.setBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, GOAL_GOLD));
        JPopupMenu menu = new JPopupMenu();
        menu.add(menuItem("Change goal...", () -> changeSkillGoal(shown)));
        menu.add(menuItem("Make a plan for it", () -> planFromGoal(shown, goal, pick)));
        menu.add(menuItem("Remove goal", () -> removeSkillGoal(shown)));
        list.add(PlannerUi.spaced(card.interactive(() -> {
            goalOpen = !goalOpen;
            redraw.run();
        }, menu, "<html><b>Your " + shown.getName() + " goal</b>: " + goalText(goal) + ", " + String.format("%,d", goal - xp)
            + " xp to go (" + (int) Math.floor(done * 100) + "% from where it was set)<br>Training: " + PlannerUi.esc(follows)
            + "<br>The same goal as the Skilling Helper's: change it in either<br>Click for more; right-click to change or "
            + "remove it</html>")));
        if (goalOpen) {
            list.add(linkRow(PlannerUi.linkLabel("Change goal", "Set a new level or XP for it", () -> changeSkillGoal(shown)),
                PlannerUi.linkLabel("Make a plan", "A new plan to this goal, with milestones you can change",
                    () -> planFromGoal(shown, goal, pick)),
                PlannerUi.linkLabel("Remove goal", "Clear the goal (in the Skilling Helper too)", () -> removeSkillGoal(shown))));
        }
    }

    private void changeSkillGoal(Skill shown) {
        int goal = calc.goal(shown);
        String typed = JOptionPane.showInputDialog(list, "The " + shown.getName() + " goal: a level (2-99) or an XP amount:",
            CalculatorData.level(goal, true));
        int value = typed == null ? -1 : parse(typed);
        if (value < 2) {
            return;
        }
        int xpGoal = value <= 126 ? Experience.getXpForLevel(Math.min(99, value)) : Math.min(Experience.MAX_SKILL_XP, value);
        int game = calc.xp(shown);
        calc.setGoal(shown, xpGoal, game >= 0 ? game : current());
        redraw.run();
    }

    private void removeSkillGoal(Skill shown) {
        if (confirm("Remove your " + shown.getName() + " goal? (The Skilling Helper's too: it's the same goal.)")) {
            calc.setGoal(shown, 0, 0);
            redraw.run();
        }
    }

    // A new plan to the goal: one milestone from your level with what the goal trains, when that's a Calculator action
    private void planFromGoal(Skill shown, int goal, String pick) {
        SkillPlans.Plan plan = new SkillPlans.Plan(Math.min(99, CalculatorData.level(goal, true)));
        plan.name = "Goal to " + plan.goal;
        CalculatorData.Calc skillCalc = data.calc(shown);
        CalculatorData.Action action = pick == null ? null : CalculatorData.named(skillCalc, pick);
        int level = levelNow();
        if (action != null && level < plan.goal) {
            List<CalculatorData.Bonus> on = CalculatorData.bonuses(skillCalc, calc.bonuses(shown));
            TrainingData.Method method = SkillPlans.methodFor(training, shown, action.name, level, plan.goal);
            plan.steps.add(new SkillPlans.Step(action.name, Math.max(level, 1), plan.goal, method == null ? null : method.name,
                CalculatorData.xpEach(action, on)));
        }
        calc.newPlan(shown, plan);
        calc.setPlanExpanded(true);
        calc.setMode(PlannerCalculator.Mode.PLAN);
        redraw.run();
    }

    private void newPlan(Skill shown) {
        int count = calc.plans(shown).size();
        String typed = JOptionPane.showInputDialog(list, "Name the new " + shown.getName() + " plan:", "Plan " + (count + 1));
        if (typed == null) {
            return;
        }
        SkillPlans.Plan plan = new SkillPlans.Plan(newGoal());
        plan.name = typed.trim().isEmpty() ? null : typed.trim();
        calc.newPlan(shown, plan);
        calc.setMode(PlannerCalculator.Mode.PLAN);
        redraw.run();
    }

    private void openPlan(Skill shown, int index) {
        calc.openPlan(shown, index);
        redraw.run();
    }

    private void renamePlan(Skill shown, int index, SkillPlans.Plan plan) {
        String typed = JOptionPane.showInputDialog(list, "The plan's name:", planName(plan, index));
        if (typed != null) {
            SkillPlans.Plan renamed = plan.copy();
            renamed.name = typed.trim().isEmpty() ? null : typed.trim();
            calc.replacePlan(shown, index, renamed);
            redraw.run();
        }
    }

    // "Levels 45-50: no milestone (the tracker suggests the wiki's method)"
    // Levels no milestone covers: click to add one for them
    private JComponent gap(Skill shown, SkillPlans.Plan plan, int from, int to) {
        JComponent row = note("Levels " + from + "-" + to + ": no milestone (click to add)",
            "No milestone covers these levels; the Skilling Helper suggests the wiki's method there. Click to add a "
            + "milestone for them");
        clickable(row, new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                stepDialog(shown, -1, plan.goal, null, from, to);
            }
        });
        return row;
    }

    // The click on it and everything in it (a label with a tooltip would keep it otherwise)
    private static void clickable(java.awt.Component component, MouseAdapter click) {
        component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        component.addMouseListener(click);
        if (component instanceof java.awt.Container) {
            for (java.awt.Component child : ((java.awt.Container) component).getComponents()) {
                clickable(child, click);
            }
        }
    }

    // Buttons side by side, sharing the width: each link drawn as a button (a card with its text centred, lighter
    // when the mouse is over it)
    private static JComponent linkRow(JComponent... links) {
        JPanel row = new JPanel(new GridLayout(1, links.length, PlannerUi.GAP, 0));
        row.setBackground(PlannerUi.BACKGROUND);
        row.setBorder(new EmptyBorder(2, 0, 6, 0));
        for (JComponent link : links) {
            if (link instanceof JLabel) {
                JLabel button = (JLabel) link;
                button.setHorizontalAlignment(SwingConstants.CENTER);
                button.setOpaque(true);
                button.setBackground(PlannerUi.CARD);
                button.setForeground(new Color(220, 220, 220));
                button.setBorder(new EmptyBorder(6, 4, 6, 4));
                button.setMinimumSize(new Dimension(0, 0));
                button.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        button.setBackground(PlannerUi.CARD_HOVER);
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        button.setBackground(PlannerUi.CARD);
                        button.setForeground(new Color(220, 220, 220));
                    }
                });
            }
            row.add(link);
        }
        return PlannerUi.fullWidth(row);
    }

    // One milestone: its method and levels, the XP and actions left, time, net gp per XP, and cost and worth
    private JComponent stepCard(Skill shown, SkillPlans.Plan plan, int index, PlannerCalculator.StepRow s, int level) {
        SkillPlans.Step step = s.step;
        PlannerCalculator.Row row = s.row;
        boolean now = step.covers(level);
        // Ticked off once you're past its end; on the way, how far through it you are
        int percent = (int) Math.floor(s.progress * 100);
        boolean started = !s.done && s.progress > 0;
        // One thing a line: how many to go (and how far through) and the XP; the time and gp per XP; what the
        // materials cost and what the products sell for; the net
        String left = s.done ? "Done (you're level " + level + ")"
            : row != null ? String.format("%,d", row.needed) + " to go" + (started ? " · " + percent + "%" : "")
            : (started ? percent + "% done" : "");
        PlannerUi.Card card = new PlannerUi.Card(step.action, left, s.done ? "" : PlannerPanel.gp(s.xp) + " xp");
        card.corner.setText(step.from + " → " + step.to);
        card.corner.setForeground(now ? Color.WHITE : PlannerUi.MUTED);
        card.right.setForeground(Color.WHITE);
        if (row != null && !s.done) {
            Double perXp = calc.showPrices() ? row.perXp() : null;
            String time = row.seconds > 0 ? "~" + PlannerPanel.formatDuration(row.seconds)
                + (row.xpHour > 0 ? " (" + PlannerPanel.gp(row.xpHour) + "/hr)" : "") : "no XP rate";
            card.third(s.tooLow() ? "needs level " + s.action.level : time, perXp == null ? "" : gpPerXp(perXp));
            if (s.tooLow()) {
                card.lower.setForeground(COSTS);
            }
            if (perXp != null) {
                card.lowerRight.setForeground(perXp < 0 ? COSTS : PlannerUi.READY);
            }
            if (calc.showPrices()) {
                priceLines(card, row.cost(row.needed), row.earn(row.needed));
            }
        }
        if (s.action != null) {
            picture(card.icon, s.action);
        } else {
            image(card.icon, icons.skill(shown, false));
        }
        if (s.done) {
            card.dim();
            card.left.setForeground(PlannerUi.READY);
            card.bar(new PlannerUi.Bar(1, PlannerUi.READY));
        } else {
            if (now) {
                card.setBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, SUGGESTED));
            }
            if (started) {
                card.bar(new PlannerUi.Bar(Math.max(0.02, s.progress), PlannerUi.GROWING));
            }
        }
        JPopupMenu menu = new JPopupMenu();
        menu.add(menuItem("Edit...", () -> stepDialog(shown, index, plan.goal)));
        if (!s.done) {
            int insertFrom = Math.max(step.from, Math.min(step.to - 1, level));
            menu.add(menuItem("Insert a milestone in it...", () -> stepDialog(shown, -1, plan.goal, null, insertFrom, step.to)));
        }
        menu.add(menuItem("Find a better method...", () -> {
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
            if (started) {
                tip.append("<br>").append(percent).append("% done: ").append(String.format("%,d", s.made())).append(" of ")
                    .append(String.format("%,d", s.total)).append(" actions");
            }
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
        tip.append("<br><font color='#a0a0a0'>Click for its breakdown (each material and its price); right-click to edit, "
            + "move or delete</font></html>");
        String open = stepKey(shown, step);
        return card.interactive(() -> {
            if (!openSteps.remove(open)) {
                openSteps.add(open);
            }
            redraw.run();
        }, menu, tip.toString());
    }

    // Exporting to the Skilling Helper: only offered when it (or the Toolbelt) is installed
    private JComponent exportRow(Skill shown, SkillPlans.Plan plan, List<CalculatorData.Bonus> on, CalculatorData.Calc skillCalc) {
        if (!calc.trackerInstalled()) {
            return note("Export to tracker: with the Skilling Helper", "Install Snuffy's Skilling Helper to follow this plan "
                + "on its goal card: Export to tracker sends it there");
        }
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(PlannerUi.CARD);
        row.setBorder(new EmptyBorder(6, 7, 6, 7));
        SkillPlans.Plan withXp = withXp(shown, plan, on, skillCalc);
        boolean same = calc.isTracked(shown, withXp);
        boolean any = calc.trackedPlan(shown) != null;
        JLabel status = PlannerUi.label(same ? "In the tracker" : any ? "Tracker has another plan" : "Not in the tracker",
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

    // The Current and Target levels when you've typed either (else null): Add to plan uses them as the milestone's levels
    private int[] typedLevels(Skill shown) {
        if (currentOverride == null && calc.savedTarget(shown) <= 0) {
            return null;
        }
        int from = CalculatorData.level(current(), false);
        int to = Math.min(99, CalculatorData.level(target(), false));
        return from >= 1 && to > from ? new int[] {from, to} : null;
    }

    // In at exactly these levels: the milestones it overlaps make room (split around it when it's inside one)
    private void addToPlanAt(Skill shown, CalculatorData.Action action, double xpEach, int from, int to) {
        SkillPlans.Plan plan = calc.plan(shown);
        if (plan == null) {
            plan = new SkillPlans.Plan(Math.max(to, newGoal()));
        }
        TrainingData.Method method = SkillPlans.methodFor(training, shown, action.name, from, to);
        PlannerCalculator.insertStep(plan, new SkillPlans.Step(action.name, from, to, method == null ? null : method.name, xpEach));
        calc.savePlan(shown, plan);
        redraw.run();
    }

    private void addToPlan(Skill shown, CalculatorData.Action action, double xpEach) {
        SkillPlans.Plan plan = calc.plan(shown);
        if (plan == null) {
            plan = new SkillPlans.Plan(newGoal());
        }
        TrainingData.Method method = SkillPlans.methodFor(training, shown, action.name, action.level, plan.goal);
        if (!PlannerCalculator.addStep(plan, action, xpEach, method == null ? null : method.name, levelNow())) {
            // No room on its own (the plan's milestones already reach its goal): ask which levels, filled in from its
            // level to the end of the milestone it lands in; it goes in there and that one makes room
            int start = Math.min(98, Math.max(levelNow(), action.level));
            int end = plan.goal > start ? plan.goal : 99;
            for (SkillPlans.Step step : plan.steps) {
                if (step.from <= start && start < step.to) {
                    end = step.to;
                }
            }
            stepDialog(shown, -1, plan.goal, action.name, start, end);
            return;
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
        if (old != null && !old.steps.isEmpty() && !confirm("Replace the milestones of \"" + planName(old, calc.openIndex(shown))
            + "\" with the wiki's suggestions? (+ makes a new plan instead)")) {
            return;
        }
        int level = levelNow();
        SkillPlans.Plan plan = PlannerCalculator.suggestedPlan(shown, actions, on, training, level, Math.max(goal, level + 1),
            calc.otherMethods(shown));
        if (plan.steps.isEmpty()) {
            JOptionPane.showMessageDialog(list, "The wiki's training guides have nothing to suggest for " + shown.getName()
                + " here: add milestones from the actions instead.", "Wiki suggestions", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        plan.name = old != null ? old.name : "Wiki suggestions";
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
        stepDialog(shown, index, goal, null, 0, 0);
    }

    /**
     * Adds (index -1) or edits a milestone. A new one can come with its method and levels filled in ({@code
     * presetAction}, {@code presetFrom}-{@code presetTo}; null and 0 for the usual guesses). A new one goes in at its own
     * levels: the milestones it overlaps make room, split around it when it's inside one (74-81 inside 74-99 leaves
     * 81-99).
     */
    private void stepDialog(Skill shown, int index, int goal, String presetAction, int presetFrom, int presetTo) {
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
        } else if (presetAction != null) {
            method.setSelectedItem(presetAction);
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
        JTextField from = new JTextField(String.valueOf(editing != null ? editing.from : presetFrom > 0 ? presetFrom
            : Math.min(last, plan.goal - 1)));
        JTextField to = new JTextField(String.valueOf(editing != null ? editing.to : presetTo > 0 ? presetTo : plan.goal));
        JPanel form = new JPanel(new GridLayout(0, 1, 2, 2));
        form.add(new JLabel("Method"));
        form.add(method);
        form.add(new JLabel("From level"));
        form.add(from);
        form.add(new JLabel("To level"));
        form.add(to);
        if (editing == null && !plan.steps.isEmpty()) {
            JLabel hint = new JLabel("<html><font color='#a0a0a0'>Milestones it overlaps make room: inside one, it's<br>"
                + "split around it (74-81 in 74-99 leaves 81-99)</font></html>");
            form.add(hint);
        }
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
            // Made shorter: the levels it gave up become a gap to fill (click it), the others stay as they are;
            // made longer: the ones it grows into make room
            editing.action = name;
            editing.from = start;
            editing.to = end;
            editing.method = guide == null ? null : guide.name;
            editing.xp = xpEach;
            // Grown into others: they make room
            PlannerCalculator.insertStep(plan, editing);
        } else {
            // In at its own levels: the milestones it overlaps make room (split around it if it's inside one)
            PlannerCalculator.insertStep(plan, new SkillPlans.Step(name, start, end, guide == null ? null : guide.name, xpEach));
        }
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
