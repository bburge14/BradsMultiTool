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
    // Where to train each milestone (the places of its trees, rocks and creatures)
    private final LocationData locations;
    private final QuestProgress quests;
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
    // The goal card open, with its plans under it (open to start with)
    private boolean goalOpen = true;
    // What you typed over the game's XP (null: from the game). A typed target is kept per skill (PlannerCalculator)
    private Integer currentOverride;
    private boolean adjusting;
    // A plan's milestone waiting for a method picked from the actions (its index), or null
    private Integer picking;
    // Typing redraws the list once it pauses
    private final Timer typed;
    private Runnable redraw = () -> { };

    PlannerCalculatorView(PlannerCalculator calc, CalculatorData data, RecipeData recipes, TrainingData training,
                          LocationData locations, QuestProgress quests, PlannerPanel.Icons icons) {
        this.calc = calc;
        this.locations = locations;
        this.quests = quests;
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
        bonusKey.append(calc.planFolded("bonuses"));
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
            tab.setToolTipText(PlannerUi.fit(tips[i]));
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
            cell.setToolTipText(PlannerUi.fit(s.getName() + (xp >= 0 ? ": level " + CalculatorData.level(xp, false) : "")
                + (hasGoal ? ", goal " + goalText(s, calc.goal(s)) : "")));
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
    /**
     * A skill's goal in words: when it's the supplies the plan you sent ends getting (the goal is where getting them
     * takes you, not a level), "supplies", or "level 60, then supplies" while you're below the level they need; else
     * as {@link #goalText(int)}.
     */
    private String goalText(Skill skill, int goal) {
        SkillPlans.Plan tracked = calc.trackedPlan(skill);
        int level = CalculatorData.level(goal, true);
        if (tracked != null && goal != Experience.getXpForLevel(Math.min(level, 126)) && endsInSupplies(tracked)) {
            // (the next level a supply needs that you don't have yet: from level 1, the first one's, then the next...)
            int now = CalculatorData.level(Math.max(0, calc.xp(skill)), false);
            int need = 0;
            for (SkillPlans.Step step : tracked.steps) {
                CalculatorData.Action action = step.supplies.isEmpty() ? null : CalculatorData.named(data.calc(skill), step.action);
                if (action != null && action.level > now && (need == 0 || action.level < need)) {
                    need = action.level;
                }
            }
            return need > 0 ? "level " + need + ", then supplies" : "supplies";
        }
        return goalText(goal);
    }

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
            JLabel name = PlannerUi.label(s.getName() + " → " + goalText(s, goal).replace("level ", ""), s == shown ? Color.WHITE
                : new Color(200, 200, 200));
            name.setMinimumSize(new Dimension(0, 0));
            BufferedImage icon = icons.skill(s, true);
            if (icon != null) {
                name.setIcon(new ImageIcon(icon));
                name.setIconTextGap(5);
            }
            row.add(name, BorderLayout.CENTER);
            row.add(PlannerUi.label((int) Math.floor(done * 100) + "%", GOAL_GOLD), BorderLayout.EAST);
            row.setToolTipText(PlannerUi.fit("<html>" + s.getName() + ": goal " + goalText(s, goal) + ", " + String.format("%,d", goal - xp)
                + " xp to go" + (plans > 0 ? "<br>" + plans + (plans == 1 ? " plan" : " plans") : "")
                + (s == shown ? "" : "<br>Click to open " + s.getName()) + "</html>"));
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
        field.setToolTipText(PlannerUi.fit(tooltip));
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
        // Folds away like the plan's sections (the total stays on the header)
        box.add(section("bonuses", "XP bonuses", on.isEmpty() ? (calc.planFolded("bonuses") ? "none on" : "")
            : "x" + trim(Math.round(total * 1000) / 1000.0)));
        if (calc.planFolded("bonuses")) {
            return box;
        }
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
        PlannerUi.Tip tip = new PlannerUi.Tip(bonus.name, "XP " + bonus.label());
        List<String> clash = new ArrayList<>();
        for (CalculatorData.Bonus other : skillCalc.bonuses) {
            if (other != bonus && !other.stacks.contains(bonus.id)) {
                clash.add(other.name);
            }
        }
        if (!clash.isEmpty() && skillCalc.bonuses.size() > 1) {
            tip.line("Not with " + String.join(", ", clash), PlannerUi.Tip.WARN);
        }
        tip.hint("Only counts for the actions it applies to");
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
        PlannerUi.tips(row, tip.build());
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
        search.setToolTipText(PlannerUi.fit("Show only the actions with this in their name"));
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
        // (how products are counted, alched or sold, for everything worked out from here)
        PlannerCalculator.selling = new PlannerCalculator.Selling(calc.sellBy(), this::alch, price("Nature rune"));
        Skill shown = skill();
        if (shown == null) {
            return "none";
        }
        StringBuilder key = new StringBuilder(shown.name()).append('|').append(current()).append('|').append(target())
            .append('|').append(calc.mode()).append('|').append(search.getText().trim().toLowerCase(Locale.ROOT))
            .append('|').append(String.join(",", calc.bonuses(shown))).append('|').append(calc.bankSeen())
            .append('|').append(calc.showMembers()).append(calc.showPrices()).append('|').append(calc.priceVersion())
            .append('|').append(calc.trackerInstalled()).append('|').append(picking).append('|').append(calc.canPlan())
            .append('|').append(calc.sort()).append('|').append(calc.sellBy());
        key.append('|').append(calc.owned().hashCode()).append('|').append(calc.buyLimitsKnown()).append(calc.planFolds()).append(calc.planBudget(shown))
            .append('|').append(calc.tradesVersion()).append('|').append(openSteps).append('|').append(calc.gatherState());
        SkillPlans.Plan plan = calc.plan(shown);
        key.append('|').append(SkillPlans.encodeAll(calc.plans(shown))).append(calc.openIndex(shown)).append(calc.planExpanded()).append(goalOpen).append(calc.pinnedPlanSkill())
            .append(calc.goalPick(shown)).append(calc.follow(shown)).append(':').append(calc.goal(shown))
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
            + (fastest.estimated ? " est." : "") + (fastest.seconds > 0 ? ", ~" + PlannerPanel.formatDuration(fastest.seconds) : ""));
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
        numbers.setToolTipText(PlannerUi.fit(line.getToolTipText()));
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
        label.setToolTipText(PlannerUi.fit(tooltip));
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
        toggle.setToolTipText(PlannerUi.fit(open ? "Fold the plan away" : "Show your plan's milestones here, to compare as you scroll"));
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
        row.setToolTipText(PlannerUi.fit(tip));
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
        label.setToolTipText(PlannerUi.fit(text));
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
            notes.add(duration(row.seconds, row.estimated));
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
        return card.interactive(click, menu.getComponentCount() > 0 ? menu : null, actionTip(shown, row, now, target));
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

    private String actionTip(Skill shown, PlannerCalculator.Row row, int now, int target) {
        CalculatorData.Action action = row.action;
        PlannerUi.Tip tip = new PlannerUi.Tip(action.name, "level " + action.level + (action.members ? ", members" : ""));
        tip.section("Training");
        tip.row("XP each", xp(row.xpEach) + (row.xpEach != action.xp ? " (" + xp(action.xp) + " without bonuses)" : ""));
        if (target > now) {
            tip.row("To your target", String.format("%,d", row.needed) + (row.needed == 1 ? " action" : " actions"));
        }
        if (row.xpHour > 0) {
            tip.row("Rate", String.format("%,d", row.xpHour) + " xp/hr" + (row.estimated ? " (est.)" : ""),
                row.estimated ? PlannerUi.Tip.WARN : null);
        }
        if (row.seconds > 0) {
            tip.row("Time", "~" + PlannerPanel.formatDuration(row.seconds));
        }
        // Faster ways to do it (1-tick, 3-tick, clicking fast): a milestone of it can be timed at one (its Pace)
        boolean faster = false;
        for (PlannerRates.Way way : PlannerRates.ways(shown, action, PlannerCalculator.recipesFor(shown, action, recipes))) {
            if (!PlannerRates.NORMAL.equals(way.name)) {
                if (!faster) {
                    tip.section("Faster ways (a milestone's Pace)");
                    faster = true;
                }
                tip.row(way.name, "~" + String.format("%,d", Math.round(way.perHour * row.xpEach)) + " xp/hr", PlannerUi.Tip.BLUE);
                tip.line(way.about, PlannerUi.Tip.MUTED);
            }
        }
        if (row.estimated) {
            tip.line("The rate is an estimate: a typical pace, banking included; yours may differ", PlannerUi.Tip.MUTED);
        }
        if (calc.showPrices() && (row.costEach != null || row.earnEach != null)) {
            tip.section("Money, each");
            if (row.costEach != null && row.costEach > 0) {
                tip.row("Materials", String.format("%,.0f", row.costEach) + " gp", PlannerUi.Tip.BAD);
            }
            if (row.geEach != null) {
                boolean more = row.alchEach != null && row.geEach > row.alchEach;
                tip.row("On the GE (after tax)", String.format("%,.0f", row.geEach) + " gp" + (more ? ", pays more" : "")
                    + (!row.alchWins && row.earnEach != null ? "  \u2190 counted" : ""), more ? PlannerUi.Tip.GOOD : PlannerUi.Tip.MUTED);
            }
            if (row.alchEach != null) {
                boolean more = row.geEach == null || row.alchEach > row.geEach;
                tip.row("High alched (less a rune)", String.format("%,.0f", row.alchEach) + " gp" + (more && row.geEach != null
                    ? ", pays more" : "") + (row.alchWins ? "  \u2190 counted" : ""), more ? PlannerUi.Tip.GOOD : PlannerUi.Tip.MUTED);
            }
            Double perXp = row.perXp();
            if (perXp != null) {
                tip.row("Net per xp", (row.profit < 0 ? "-" : "+") + String.format("%.2f", Math.abs(perXp)) + " gp",
                    row.profit < 0 ? PlannerUi.Tip.BAD : PlannerUi.Tip.GOOD);
                if (target > now) {
                    tip.row("Net to your target", signed(row.profit * row.needed),
                        row.profit < 0 ? PlannerUi.Tip.BAD : PlannerUi.Tip.GOOD);
                }
            }
        }
        if (row.have > 0) {
            tip.line("You have " + String.format("%,d", row.have) + " (bank as last seen and inventory)");
        }
        if (row.locked) {
            tip.line("Needs level " + action.level, PlannerUi.Tip.BAD);
        } else if (row.suggested) {
            tip.line("The wiki's suggested method at your level", PlannerUi.Tip.GOOD);
        }
        if (picking != null) {
            tip.hint("Click to use it for the milestone");
        } else if (calc.canPlan()) {
            tip.hint("Right-click: Add to plan");
        }
        return tip.build();
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
        PlannerUi.Tip tip = new PlannerUi.Tip(action.name, "you have " + String.format("%,d", row.have));
        if (count < row.have && !row.locked) {
            tip.line(usedUp ? "Its materials go to the ones above it (more XP each)"
                : "Makes " + String.format("%,d", count) + ": the rest of its materials go to the ones above it", PlannerUi.Tip.MUTED);
        }
        tip.row("XP", String.format("%,d", (long) gained), PlannerUi.Tip.GOOD);
        if (!row.locked) {
            tip.row("Level", before + " \u2192 " + after);
            tip.row(reaches ? "Your target" : "Short of your target", reaches ? "reached"
                : String.format("%,d", (long) (target - now - gained)) + " xp", reaches ? PlannerUi.Tip.GOOD : PlannerUi.Tip.WARN);
        }
        if (calc.showPrices() && count > 0 && (row.costEach != null || row.earnEach != null)) {
            tip.section("Money");
            if (row.costEach != null) {
                tip.row("Materials", PlannerPanel.gp(Math.round(row.costEach * count)), PlannerUi.Tip.BAD);
            }
            if (row.earnEach != null) {
                tip.row(row.alchWins ? "High alched" : "Sells for", PlannerPanel.gp(Math.round(row.earnEach * count)),
                    PlannerUi.Tip.GOOD);
            }
            Double cost = row.cost(count);
            Double earn = row.earn(count);
            double net = (earn == null ? 0 : earn) - (cost == null ? 0 : cost);
            tip.row("Net", signed(net), net < 0 ? PlannerUi.Tip.BAD : PlannerUi.Tip.GOOD);
        }
        if (row.locked) {
            tip.line("Needs level " + action.level, PlannerUi.Tip.BAD);
        }
        tip.hint("Bank as last seen plus inventory");
        return card.interactive(null, null, tip.build());
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
            list.add(note(calc.goal(shown) > planXp() ? "No plans for your goal yet" : "No " + shown.getName() + " plans yet",
                "A plan is a goal and milestones on the way, each with its own method (\"1-20 Logs, 20-50 Willow logs\"). "
                + "Start one from the wiki's suggestions or empty; make as many as you like and export the one you follow "
                + "to the tracker"));
            int goal = newGoal();
            list.add(linkRow(PlannerUi.linkLabel("Suggest a plan...", "A new plan to " + goal + ": fastest, cheapest, money "
                + "maker, balanced, AFK or the wiki's guide (compare them, then pick); then change any of it",
                () -> suggest(shown, actions, on, goal)),
                PlannerUi.linkLabel("New plan", "An empty plan: add milestones by right-clicking actions, or +",
                    () -> newPlan(shown))));
            fillSupplies(shown);
            return;
        }
        // The open plan unfolds under its card in the list
        fillPlanList(shown, () -> fillOpenPlan(shown, skillCalc, on, now, actions, plan, level));
        fillSupplies(shown);
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
            list.add(linkRow(PlannerUi.linkLabel("Suggest a plan...", "Fill this plan to " + goal + ": fastest, cheapest, "
                + "money maker, balanced, AFK or the wiki's guide (compare them, then pick); then change any of it",
                () -> suggest(shown, actions, on, goal)),
                PlannerUi.linkLabel("Add a milestone", "Pick its method and levels", () -> stepDialog(shown, -1, goal))));
            return;
        }
        SkillPlans.Plan unfitted = plan.copy();
        if (fitSupplyMilestones(shown, skillCalc, on, plan, now)) {
            calc.savePlanQuietly(shown, plan, unfitted);
        }
        List<PlannerCalculator.StepRow> steps = PlannerCalculator.stepRows(shown, skillCalc, on, plan, now, recipes, training,
            this::price, calc::itemPrice);
        yourRate(shown, plan, steps, level);
        PlannerCalculator.Totals totals = PlannerCalculator.totals(plan, steps, now);

        int endLevel = Math.min(plan.goal, Math.max(totals.endLevel, level));
        // The whole plan's totals: a strip of tiles at the top (hover any for all of it), not another card
        String wholeTip = totals.xp > 0 ? planSummaryTip(steps, totals, level, endLevel) : null;
        JPanel tiles = new JPanel(new GridLayout(0, 2, PlannerUi.GAP, PlannerUi.GAP));
        tiles.setBackground(PlannerUi.BACKGROUND);
        tiles.add(tile("XP to go", PlannerPanel.gp(totals.xp) + " xp", String.format("%,d", totals.xp)
            + " XP left in the plan's milestones", Color.WHITE));
        tiles.add(tile("Time", totals.seconds > 0 ? duration(totals.seconds, totals.estimated > 0) + (totals.untimed > 0 ? "+" : "")
            : "-", (totals.untimed > 0 ? totals.untimed + " milestone" + (totals.untimed == 1 ? " has" : "s have") + " no XP rate, "
            + "so the time leaves " + (totals.untimed == 1 ? "it" : "them") + " out" : "At the wiki's training guides' XP rates")
            + estimatedNote(totals), Color.WHITE));
        tiles.add(tile("Level", level + " → " + (endsInSupplies(plan) ? "~" : "") + endLevel, endsInSupplies(plan)
            ? "Your level now, and about where getting the plan's supplies takes you (it moves as you get or buy them)"
            : endLevel < plan.goal ? "The milestones end at level " + endLevel
            + "; the goal is " + plan.goal : "Your level now, and where the plan ends (its goal)", Color.WHITE));
        // What the materials really cost you: what you have at what it cost you (bought, got yourself, or not known: GE
        // price), what's still to get at the GE price unless you get it yourself
        PlannerUi.Tip materialsTip = new PlannerUi.Tip("Materials", "what they really cost you");
        long[] real = calc.showPrices() ? materialsCost(shown, steps, materialsTip) : new long[2];
        if (real[1] > 0) {
            totals.cost = real[0];
        }
        boolean cost = calc.showPrices() && totals.cost > 0;
        boolean earn = calc.showPrices() && totals.earn > 0;
        String costTip = real[1] > 0 ? materialsTip.line("All of them at GE prices: " + PlannerPanel.gp(real[1]), PlannerUi.Tip.MUTED)
            .hint("Right-click one under Materials needed: where some came from").build()
            : "What the milestones' materials cost at Grand Exchange prices (" + String.format("%,.0f", totals.cost) + " gp)";
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
        if (wholeTip != null) {
            PlannerUi.tips(tiles, wholeTip);
        }
        list.add(PlannerUi.group("Whole plan", endsInSupplies(plan) ? "to the supplies (~" + endLevel + ")" : "to level " + endLevel));
        list.add(PlannerUi.spaced(tiles));

        // (a plan ending in a supply milestone is for the supplies: its goal is getting them, wherever that takes you)
        boolean forSupplies = endsInSupplies(plan);
        JLabel goalLink = PlannerUi.linkLabel(forSupplies ? "Goal: supplies" : "Goal: level " + plan.goal,
            forSupplies ? "The plan ends getting supplies for another plan: its goal is having them all, about level "
                + plan.goal + " (it moves as you get or buy them); click to set a level instead" : "Change the plan's final goal",
            () -> changeGoal(shown));
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
        fillMaterials(shown, steps);
        fillMoneyPlan(shown, steps);
        list.add(linkRow(PlannerUi.linkLabel("Suggest a plan...", "Replace the milestones to " + plan.goal + ": "
                + "fastest, cheapest, money maker, balanced, AFK or the wiki's guide (compare them, then pick; supply "
                + "milestones stay)", () -> suggest(shown, actions, on, plan.goal)),
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
    private String planSummaryTip(List<PlannerCalculator.StepRow> steps, PlannerCalculator.Totals totals, int level, int endLevel) {
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
        String time = totals.seconds > 0 ? duration(totals.seconds, totals.estimated > 0)
            + (totals.untimed > 0 ? " for " + (milestones - totals.untimed) + " of " + milestones : "")
            : "no XP rates";
        boolean prices = calc.showPrices() && (totals.cost > 0 || totals.earn > 0);
        double net = totals.earn - totals.cost;
        PlannerUi.Tip tip = new PlannerUi.Tip("The whole plan", "level " + level + " \u2192 " + endLevel);
        tip.line("The milestones added up", PlannerUi.Tip.MUTED);
        tip.section("To go");
        tip.row("XP", String.format("%,d", totals.xp));
        tip.row("Milestones", String.valueOf(milestones));
        if (totals.seconds > 0) {
            tip.row("Time", "~" + PlannerPanel.formatDuration(totals.seconds) + (totals.untimed > 0 ? " (+ " + totals.untimed
                + " without a rate)" : ""), totals.estimated > 0 ? PlannerUi.Tip.WARN : null);
            tip.row("Average", String.format("%,d", perHour) + " xp/hr");
        }
        if (totals.estimated > 0) {
            tip.line(totals.estimated + (totals.estimated == 1 ? " milestone's rate is an estimate" : " milestones' rates are "
                + "estimates") + " (a typical pace): take the time as rough", PlannerUi.Tip.MUTED);
        }
        if (totals.untimed > 0) {
            tip.line(totals.untimed + (totals.untimed == 1 ? " milestone has" : " milestones have") + " no XP rate, so the "
                + "time leaves " + (totals.untimed == 1 ? "it" : "them") + " out", PlannerUi.Tip.MUTED);
        }
        if (prices) {
            tip.section("Money to go");
            tip.row("Materials", PlannerPanel.gp(Math.round(totals.cost)), PlannerUi.Tip.BAD);
            tip.row("Products", PlannerPanel.gp(Math.round(totals.earn)), PlannerUi.Tip.GOOD);
            tip.row("Net", signed(net), net < 0 ? PlannerUi.Tip.BAD : PlannerUi.Tip.GOOD);
            tip.row("Per xp", gpPerXp(net / Math.max(1, totals.xp)), net < 0 ? PlannerUi.Tip.BAD : PlannerUi.Tip.GOOD);
        }
        // What the milestones' recorded sales came to (Record sales...)
        double cash = 0;
        double withUnsold = 0;
        int recorded = 0;
        for (PlannerCalculator.StepRow s : steps) {
            SoFar done = soFar(s);
            if (done != null) {
                cash += done.cash();
                withUnsold += done.net();
                recorded++;
            }
        }
        if (calc.showPrices() && recorded > 0) {
            tip.section("So far (" + recorded + (recorded == 1 ? " milestone" : " milestones") + " recorded)");
            tip.row("Cash so far", signed(cash), cash < 0 ? PlannerUi.Tip.BAD : PlannerUi.Tip.GOOD);
            tip.row("Net with unsold", signed(withUnsold), withUnsold < 0 ? PlannerUi.Tip.BAD : PlannerUi.Tip.GOOD);
        }
        return tip.build();
    }

    /** What a milestone's products came to so far, from what you've recorded selling and alching. */
    private static final class SoFar {
        long made;
        long left;
        double sold;
        double alched;
        /** What the ones not sold yet are worth now (as Sell By counts them). */
        double unsold;
        double cost;

        /** Sold and alched less the materials used: the gp it's really brought in. */
        double cash() {
            return sold + alched - cost;
        }

        /** With what's not sold yet at what it's worth now. */
        double net() {
            return cash() + unsold;
        }
    }

    // Null when nothing's recorded for it
    private static SoFar soFar(PlannerCalculator.StepRow s) {
        PlannerCalculator.Row row = s.row;
        if (row == null || s.step.sold <= 0 && s.step.alched <= 0 && s.step.dropped <= 0) {
            return null;
        }
        SoFar out = new SoFar();
        out.made = s.made();
        // (what you said you got each, else the prices now)
        double ge = s.step.soldEach > 0 ? s.step.soldEach
            : row.geEach != null ? row.geEach : row.earnEach != null && !row.alchWins ? row.earnEach : 0;
        double alch = s.step.alchedEach > 0 ? s.step.alchedEach : row.alchEach != null ? row.alchEach : 0;
        out.sold = s.step.sold * ge;
        out.alched = s.step.alched * alch;
        out.left = Math.max(0, out.made - s.step.sold - s.step.alched - s.step.dropped);
        out.unsold = row.earnEach == null ? 0 : out.left * row.earnEach;
        out.cost = row.costEach == null ? 0 : row.costEach * out.made;
        return out;
    }

    // Record sales...: how many of what the milestone made you sold on the GE and how many you high alched, and what
    // you got for each (filled in with the prices now: your trades', else the GE's, and the alch value less a rune)
    private void recordSales(Skill shown, int index, PlannerCalculator.StepRow s) {
        PlannerCalculator.Row row = s.row;
        javax.swing.JTextField sold = new javax.swing.JTextField(s.step.sold > 0 ? String.valueOf(s.step.sold) : "", 10);
        javax.swing.JTextField soldEach = new javax.swing.JTextField(each(s.step.soldEach > 0 ? s.step.soldEach
            : row.geEach != null ? row.geEach : 0), 10);
        javax.swing.JTextField alched = new javax.swing.JTextField(s.step.alched > 0 ? String.valueOf(s.step.alched) : "", 10);
        javax.swing.JTextField alchedEach = new javax.swing.JTextField(each(s.step.alchedEach > 0 ? s.step.alchedEach
            : row.alchEach != null ? row.alchEach : 0), 10);
        JPanel form = new JPanel(new GridLayout(0, 2, 6, 4));
        JLabel head = new JLabel("<html><b>" + PlannerUi.esc(s.step.action) + "</b>: what you did with them<br>"
            + "<font color='#a0a0a0'>You've made about " + String.format("%,d", s.made()) + " so far. The prices start at "
            + "today's (GE after tax; alch less a nature rune): change them to what you really got</font></html>");
        JPanel all = new JPanel(new BorderLayout(0, 8));
        all.add(head, BorderLayout.NORTH);
        form.add(new JLabel("Sold on the GE (how many)"));
        form.add(sold);
        form.add(new JLabel("Got each (gp, after tax)"));
        form.add(soldEach);
        form.add(new JLabel("High alched (how many)"));
        form.add(alched);
        form.add(new JLabel("Got each (gp, less the rune)"));
        form.add(alchedEach);
        all.add(form, BorderLayout.CENTER);
        if (JOptionPane.showConfirmDialog(list, all, "Record sales", JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        long soldCount = Math.max(0, parse(sold.getText().replace(",", "").trim()));
        long alchedCount = Math.max(0, parse(alched.getText().replace(",", "").trim()));
        double soldPrice = gpEach(soldEach.getText());
        double alchedPrice = gpEach(alchedEach.getText());
        // (a price left at today's isn't kept: it follows the prices; one you changed is)
        double soldNow = row.geEach != null ? row.geEach : 0;
        double alchNow = row.alchEach != null ? row.alchEach : 0;
        change(shown, p -> {
            if (index < p.steps.size()) {
                SkillPlans.Step step = p.steps.get(index);
                step.sold = soldCount;
                step.alched = alchedCount;
                step.soldEach = Math.abs(soldPrice - soldNow) < 0.5 ? 0 : soldPrice;
                step.alchedEach = Math.abs(alchedPrice - alchNow) < 0.5 ? 0 : alchedPrice;
            }
        });
    }

    // A price for a text field ("1,234"), or empty for none
    private static String each(double gp) {
        return gp > 0 ? String.format("%,.0f", gp) : "";
    }

    // The gp each typed ("1,234", "1.2k", "3m"), 0 for none
    private static double gpEach(String text) {
        String t = text.replace(",", "").trim().toLowerCase(Locale.ROOT);
        double times = 1;
        if (t.endsWith("k")) {
            times = 1_000;
        } else if (t.endsWith("m")) {
            times = 1_000_000;
        }
        if (times > 1) {
            t = t.substring(0, t.length() - 1);
        }
        try {
            return Math.max(0, Double.parseDouble(t) * times);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // An item's high alch value (0 until the game has said it, or when it has none)
    private int alch(String name) {
        int id = icons.itemId(name);
        return id > 0 ? calc.alchPrice(id) : 0;
    }

    // What one product counts as, said in words ("sold on the GE" or "high alched")
    private static String saleWay(PlannerCalculator.Row row) {
        return row.alchWins ? "high alched (less a nature rune)" : "on the GE";
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
                box.add(breakdownLine(row.alchWins ? "  High alched (less a rune)" : "  Sells for (after tax)"
                    + (calc.yourPrice(s.step.action) != null ? "*" : ""),
                    String.format("%,.0f", row.earnEach), PlannerUi.MUTED, new Color(150, 210, 150)));
                // (the other way, for comparison)
                Double other = row.alchWins ? row.geEach : row.alchEach;
                if (other != null) {
                    box.add(breakdownLine(row.alchWins ? "  or on the GE (after tax)" : "  or high alched (less a rune)",
                        String.format("%,.0f", other), PlannerUi.MUTED, PlannerUi.MUTED));
                }
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
            // What you've told it you did with what you made so far (Record sales...), and what that came to
            SoFar done = soFar(s);
            if (done != null) {
                box.add(breakdownLine("So far: made " + String.format("%,d", done.made), "", Color.WHITE, Color.WHITE));
                if (s.step.sold > 0) {
                    box.add(breakdownLine("  Sold " + String.format("%,d", s.step.sold) + " on the GE",
                        PlannerPanel.gp(Math.round(done.sold)), PlannerUi.MUTED, new Color(150, 210, 150)));
                }
                if (s.step.alched > 0) {
                    box.add(breakdownLine("  Alched " + String.format("%,d", s.step.alched),
                        PlannerPanel.gp(Math.round(done.alched)), PlannerUi.MUTED, new Color(150, 210, 150)));
                }
                if (s.step.dropped > 0) {
                    box.add(breakdownLine("  Dropped " + String.format("%,d", s.step.dropped), "0", PlannerUi.MUTED,
                        PlannerUi.MUTED));
                }
                if (done.left > 0) {
                    box.add(breakdownLine("  Not sold yet " + String.format("%,d", done.left) + " (worth now)",
                        PlannerPanel.gp(Math.round(done.unsold)), PlannerUi.MUTED, PlannerUi.MUTED));
                }
                if (done.cost > 0) {
                    box.add(breakdownLine("  Materials used", PlannerPanel.gp(Math.round(done.cost)), PlannerUi.MUTED,
                        new Color(220, 150, 140)));
                }
                box.add(breakdownLine("  Cash so far (sold less materials)", signed(done.cash()), PlannerUi.MUTED,
                    done.cash() < 0 ? COSTS : PlannerUi.READY));
                box.add(breakdownLine("  Net so far (with what's not sold)", signed(done.net()), PlannerUi.MUTED,
                    done.net() < 0 ? COSTS : PlannerUi.READY));
            }
        }
        JComponent wrapped = PlannerUi.fullWidth(box);
        wrapped.setToolTipText(PlannerUi.fit("<html>Materials and sales for one, then for the whole milestone<br>"
            + "<font color='#a0a0a0'>GE prices (* your own); sales after the 2% tax</font></html>"));
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
    // Each section's own colour, on its header and down the left of what's in it, so you always know where you are
    static final Color MILESTONES = new Color(90, 190, 110);
    static final Color MONEY = new Color(230, 180, 60);
    static final Color MATERIALS = new Color(110, 160, 235);
    static final Color SUPPLIES = new Color(180, 140, 230);

    private static Color sectionColour(String key) {
        switch (key) {
            case "milestones":
                return MILESTONES;
            case "money":
                return MONEY;
            case "materials":
                return MATERIALS;
            case "supplies":
                return SUPPLIES;
            default:
                return PlannerUi.MUTED;
        }
    }

    private JComponent section(String key, String title, String summary, JComponent... actions) {
        boolean folded = calc.planFolded(key);
        JPanel row = PlannerUi.group((folded ? "\u25B6 " : "\u25BC ") + title, summary, actions);
        Color colour = sectionColour(key);
        java.awt.Component name = ((java.awt.BorderLayout) row.getLayout()).getLayoutComponent(BorderLayout.WEST);
        if (name instanceof JLabel) {
            name.setForeground(colour);
            ((JLabel) name).setFont(FontManager.getRunescapeBoldFont());
        }
        row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, colour),
            new EmptyBorder(8, 5, 4, 0)));
        // (tall enough for the bold title and this border: the group's height was for its plain one, which cut the
        // tops of the letters off)
        int height = Math.max(name == null ? 0 : name.getPreferredSize().height, row.getComponentCount() > 1
            ? row.getComponent(row.getComponentCount() - 1).getPreferredSize().height : 0) + 8 + 4 + 2;
        row.setPreferredSize(new Dimension(10, height));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        row.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        row.setToolTipText(PlannerUi.fit(folded ? "Click to open" : "Click to fold away"));
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
        // (an ironman doesn't buy: no money plan)
        if (!calc.showPrices() || calc.ironman()) {
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
        // (what you get yourself isn't bought)
        java.util.Set<String> gathered = new java.util.HashSet<>();
        for (PlannerCalculator.Need need : PlannerCalculator.planMaterials(shown, steps, recipes, calc.owned())) {
            if (!need.tool && calc.gathers(shown, need.name)
                && PlannerCalculator.sourceOf(need.name, shown, data, recipes) != null) {
                gathered.add(need.name.toLowerCase(Locale.ROOT));
            }
        }
        PlannerCalculator.MoneyPlan money = PlannerCalculator.moneyPlan(shown, steps, recipes, owned, budget, this::price, limits,
            gathered);
        if (money.steps.isEmpty()) {
            return;
        }
        long needed = PlannerCalculator.budgetNeeded(shown, steps, recipes, owned, this::price, limits, gathered);
        JLabel budgetLink = PlannerUi.linkLabel(set > 0 ? "Budget: " + PlannerPanel.gp(set) : "Set a budget",
            "How much this plan may use (the rest of your cash is left alone)", () -> askBudget(shown, coins));
        boolean folded = calc.planFolded("money");
        // (short: the header has the budget link too)
        String summary = money.runsOut != null ? "short " + PlannerPanel.gp(Math.max(0, needed - budget)) : "ok";
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

    // A money ledger: lines of a label and an amount, no picture, the section's colour down its left
    private static JPanel ledger(Color edge) {
        JPanel box = new JPanel(new GridLayout(0, 1, 0, 1));
        box.setBackground(PlannerUi.CARD);
        box.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, edge),
            new EmptyBorder(4, 7, 4, 7)));
        return box;
    }

    private static void ledgerLine(JPanel ledger, String left, String right, Color rightColour, boolean first) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        JLabel name = PlannerUi.label(left, first ? Color.WHITE : PlannerUi.MUTED);
        name.setMinimumSize(new Dimension(0, 0));
        row.add(name, BorderLayout.CENTER);
        if (!right.isEmpty()) {
            row.add(PlannerUi.label(right, rightColour), BorderLayout.EAST);
        }
        ledger.add(row);
    }

    // A panel with a tooltip, and a click (or none)
    private static JComponent clickable(JPanel panel, Runnable click, String tooltip) {
        PlannerUi.tips(panel, tooltip);
        if (click != null) {
            panel.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    click.run();
                }
            };
            panel.addMouseListener(mouse);
            for (java.awt.Component child : panel.getComponents()) {
                child.addMouseListener(mouse);
            }
        }
        return PlannerUi.fullWidth(panel);
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
        // A ledger, not a card: the budget's lines in the money section's colour
        boolean short_ = money.runsOut != null;
        JPanel ledger = ledger(short_ ? COSTS : MONEY);
        ledgerLine(ledger, set > 0 ? "Budget" : "Budget (your coins)", PlannerPanel.gp(set > 0 ? set : coins), Color.WHITE, true);
        ledgerLine(ledger, "Costs after sales", signed(-money.loss), money.loss > 0 ? COSTS : PlannerUi.READY, false);
        ledgerLine(ledger, "The plan needs", PlannerPanel.gp(needed), needed > money.budget ? COSTS : PlannerUi.READY, false);
        ledgerLine(ledger, short_ ? "Short by" : "Left at the goal",
            PlannerPanel.gp(short_ ? Math.max(0, needed - money.budget) : Math.max(0, money.end())),
            short_ ? COSTS : PlannerUi.READY, false);
        PlannerUi.Tip tip = new PlannerUi.Tip("Money plan", "buy, make, sell, repeat");
        tip.line("Buy what each milestone needs that you don't have, make it, sell it, and buy the next lot with what it "
            + "brought in: you only need a round's worth at once", PlannerUi.Tip.MUTED);
        tip.section("Budget");
        tip.row(set > 0 ? "Your budget" : "Your coins (no budget set)", PlannerPanel.gp(set > 0 ? set : coins));
        tip.row("The whole plan needs", PlannerPanel.gp(needed), needed > money.budget ? PlannerUi.Tip.BAD : PlannerUi.Tip.GOOD);
        tip.row("The most one round needs", PlannerPanel.gp(money.peak));
        tip.row("Costs after sales", signed(-money.loss), money.loss > 0 ? PlannerUi.Tip.BAD : PlannerUi.Tip.GOOD);
        if (money.limitHours > 0) {
            tip.row("Buy limits: at least", PlannerPanel.formatDuration(money.limitHours * 3600), PlannerUi.Tip.WARN);
        }
        yourPriceRows(tip, planItems(money));
        tip.line("Sales after the GE's 2% tax; today's prices, so it's a guide", PlannerUi.Tip.MUTED);
        tip.hint("Click to set the budget");
        return clickable(ledger, () -> askBudget(shown, coins), tip.build());
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
        JPanel ledger = ledger(MONEY);
        ledgerLine(ledger, "Since you set the budget", "", Color.WHITE, true);
        ledgerLine(ledger, "Spent buying", PlannerPanel.gp(traded[0]), new Color(220, 150, 140), false);
        ledgerLine(ledger, "Sold for (after tax)", PlannerPanel.gp(traded[1]), new Color(150, 210, 150), false);
        ledgerLine(ledger, "Budget left", PlannerPanel.gp(Math.max(0, left)), left >= 0 ? Color.WHITE : COSTS, false);
        PlannerUi.Tip tip = new PlannerUi.Tip("Your real trades", "since " + java.time.format.DateTimeFormatter
            .ofPattern("MMM d, h:mm a").format(java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(since),
                java.time.ZoneId.systemDefault())));
        tip.line("Buys of the plan's materials and sales of what it makes, as they filled on the GE", PlannerUi.Tip.MUTED);
        tip.row("Spent", PlannerPanel.gp(traded[0]), PlannerUi.Tip.BAD);
        tip.row("Brought in (after tax)", PlannerPanel.gp(traded[1]), PlannerUi.Tip.GOOD);
        tip.row("Budget", PlannerPanel.gp(set));
        tip.row("Left", PlannerPanel.gp(left), left >= 0 ? PlannerUi.Tip.GOOD : PlannerUi.Tip.BAD);
        tip.hint("Only trades made while RuneLite ran with the Planner on");
        return clickable(ledger, null, tip.build());
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
    // The items going at your own price (your trades this week), as rows
    private void yourPriceRows(PlannerUi.Tip tip, java.util.Collection<String> items) {
        boolean first = true;
        for (String item : items) {
            Long yours = calc.yourPrice(item);
            if (yours != null) {
                if (first) {
                    tip.section("Your own prices (your trades this week)");
                    first = false;
                }
                tip.row(item, String.format("%,d", yours) + " gp", PlannerUi.Tip.BLUE);
            }
        }
    }

    // A milestone's money: how much to buy, in how many rounds, what it sells for, and the budget after it
    private JComponent moneyCard(PlannerCalculator.MoneyStep m) {
        SkillPlans.Step step = m.step.step;
        int kinds = m.buys.size();
        // One short row a milestone (its card is above, with its picture): what to buy, then the budget after it
        JPanel ledger = ledger(m.runsOut ? COSTS : MONEY);
        ledgerLine(ledger, step.action + "  " + step.from + "\u2192" + step.to,
            kinds == 0 ? "nothing to buy" : "buy " + PlannerPanel.gp(m.cost), kinds == 0 ? PlannerUi.MUTED : new Color(220, 150, 140), true);
        if (m.runsOut) {
            ledgerLine(ledger, "Not enough budget: loses " + PlannerPanel.gp(m.loss()), "", COSTS, false);
        } else {
            String how = m.rounds == 1 ? "in one go" : m.rounds + " rounds (" + (m.limitRounds >= m.cashRounds ? "buy limit" : "budget") + ")";
            ledgerLine(ledger, how + (m.earn > 0 ? " \u00b7 sells " + PlannerPanel.gp(m.earn) : ""),
                PlannerPanel.gp(Math.max(0, m.budgetAfter)) + " after", PlannerUi.MUTED, false);
        }
        PlannerUi.Tip tip = new PlannerUi.Tip(step.action, "level " + step.from + " \u2192 " + step.to);
        if (!m.buys.isEmpty()) {
            tip.section("To buy");
            for (PlannerCalculator.Buy buy : m.buys) {
                tip.row(String.format("%,d", buy.quantity) + " " + buy.name, PlannerPanel.gp(buy.cost)
                    + (buy.limit > 0 ? "  (limit " + String.format("%,d", buy.limit) + " / 4h)" : ""), PlannerUi.Tip.BAD);
            }
        }
        tip.section("Money");
        if (m.earn > 0) {
            tip.row("Sells for", PlannerPanel.gp(m.earn), PlannerUi.Tip.GOOD);
        }
        if (m.runsOut) {
            tip.row("It loses", PlannerPanel.gp(m.loss()), PlannerUi.Tip.BAD);
            tip.line("The budget has " + PlannerPanel.gp(Math.max(0, m.budgetBefore)) + " left here: not enough", PlannerUi.Tip.BAD);
        } else if (m.rounds > 1) {
            tip.row("Rounds", m.rounds + " of about " + PlannerPanel.gp(m.perRound));
            if (m.limitRounds > 1) {
                tip.row("Buy limits: at least", PlannerPanel.formatDuration((m.limitRounds - 1) * 4 * 3600), PlannerUi.Tip.WARN);
            }
        }
        tip.row("Budget before", PlannerPanel.gp(Math.max(0, m.budgetBefore)));
        if (!m.runsOut) {
            tip.row("Budget after", PlannerPanel.gp(Math.max(0, m.budgetAfter)));
        }
        return clickable(ledger, null, tip.build());
    }

    private static final Color GATHER_BLUE = new Color(138, 180, 248);

    // For a material got from another skill: "Woodcutting 60 (you're 50)" when that skill isn't at its level yet, else null
    private String waitingOn(Skill shown, PlannerCalculator.Need need) {
        if (need.tool || need.enough() || !calc.gathers(shown, need.name)) {
            return null;
        }
        PlannerCalculator.Source source = PlannerCalculator.sourceOf(need.name, shown, data, recipes);
        if (source == null) {
            return null;
        }
        int at = CalculatorData.level(Math.max(0, calc.xp(source.skill)), false);
        return source.action.level > at ? source.skill.getName() + " " + source.action.level + " (now " + at + ")" : null;
    }

    // "Firemaking" from a saved skill's name
    private static String skillName(String saved) {
        try {
            return Skill.valueOf(saved).getName();
        } catch (IllegalArgumentException e) {
            return saved;
        }
    }

    private static String repeat(String text, int times) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < times; i++) {
            out.append(text);
        }
        return out.toString();
    }

    // "WC", "Mining": a skill's short name for a material row
    private static String shortSkill(Skill skill) {
        switch (skill) {
            case WOODCUTTING:
                return "(WC)";
            case FISHING:
                return "(Fish)";
            case MINING:
                return "(Mine)";
            case HUNTER:
                return "(Hunt)";
            default:
                return "(" + skill.getName() + ")";
        }
    }

    // For a material you could get yourself: what gathering (or making) what's short takes, the XP it gives that skill,
    // and what buying it would cost, so you can see if it's worth it
    private void gatherRows(PlannerUi.Tip tip, Skill shown, PlannerCalculator.Need need, PlannerCalculator.Source source,
                            boolean gather) {
        if (source == null) {
            return;
        }
        long shortBy = Math.max(0, need.needed - need.have);
        tip.section(gather ? "You get it yourself" : "Or get it yourself");
        tip.row((source.gathered ? "Gathered" : "Made") + " in", source.skill.getName() + " (level " + source.action.level + ")",
            gather ? PlannerUi.Tip.BLUE : null);
        if (gather && shortBy > 0) {
            List<PlannerCalculator.Link> links = PlannerCalculator.chain(need.name, need.needed, shown, data, recipes,
                new java.util.HashMap<>(calc.owned()), calc::gathers);
            if (links.size() > 1) {
                for (PlannerCalculator.Link link : links) {
                    tip.row(repeat("  ", link.depth) + String.format("%,d", link.toGet()) + " " + link.item,
                        link.skill == null ? "a drop or shop" : link.skill.getName(), PlannerUi.Tip.MUTED);
                }
            }
        }
        if (shortBy > 0) {
            Supply supply = supply(source, shortBy);
            tip.row(String.format("%,d", shortBy) + " more gives", PlannerPanel.gp(supply.xp) + " " + source.skill.getName() + " xp",
                PlannerUi.Tip.GOOD);
            if (supply.seconds > 0) {
                tip.row("Time", duration(supply.seconds, supply.estimated), supply.estimated ? PlannerUi.Tip.WARN : null);
            }
            long buying = shortBy * (long) price(need.name);
            if (buying > 0) {
                tip.row("Buying them", PlannerPanel.gp(buying), PlannerUi.Tip.BAD);
                if (supply.seconds > 0) {
                    tip.row("Yourself saves", "~" + PlannerPanel.gp(buying * 3600 / supply.seconds) + " an hour",
                        PlannerUi.Tip.GOOD);
                }
            }
        }
        tip.hint("Right-click: " + (gather ? "buy it instead" : (source.gathered ? "gather" : "make") + " it yourself"));
    }

    /** "~2h 10m", with "est." when the rate behind it is an estimate rather than a training guide's. */
    static String duration(long seconds, boolean estimated) {
        return "~" + PlannerPanel.formatDuration(seconds) + (estimated ? " est." : "");
    }

    // Where an action's XP an hour comes from, for its tooltip
    private static String rateSource(PlannerCalculator.Row row) {
        if (row.estimated && row.pace != null && !PlannerRates.NORMAL.equals(row.pace)) {
            return " xp an hour (an estimate for " + PlannerUi.esc(row.pace) + "; yours may differ)";
        }
        return row.estimated ? " xp an hour (an estimate: a typical pace for it, banking included; yours may differ)"
            : " xp an hour (the wiki's training guide)";
    }

    // Said under a time that has estimates in it
    private static String estimatedNote(PlannerCalculator.Totals totals) {
        if (totals.estimated <= 0) {
            return "";
        }
        return "<br>" + totals.estimated + (totals.estimated == 1 ? " milestone's" : " milestones'") + " rate is an estimate "
            + "(a typical pace, not the wiki's), so take the time as rough";
    }

    /** What getting some of a material yourself takes in its skill: the XP, and the time at the guides' rate. */
    private static final class Supply {
        long xp;
        long seconds;
        boolean estimated;
    }

    private Supply supply(PlannerCalculator.Source source, long quantity) {
        Supply out = new Supply();
        List<CalculatorData.Bonus> on = CalculatorData.bonuses(data.calc(source.skill), calc.bonuses(source.skill));
        double each = CalculatorData.xpEach(source.action, on);
        out.xp = Math.round(each * quantity);
        int from = Math.max(calc.xp(source.skill), Experience.getXpForLevel(Math.max(1, source.action.level)));
        int to = (int) Math.min(Experience.MAX_SKILL_XP, from + out.xp);
        List<PlannerCalculator.Row> rows = PlannerCalculator.rows(source.skill, java.util.Collections.singletonList(source.action),
            on, from, to, recipes, training, new java.util.HashMap<>(), this::price, calc::itemPrice);
        out.seconds = rows.isEmpty() ? 0 : rows.get(0).seconds;
        out.estimated = !rows.isEmpty() && rows.get(0).estimated;
        return out;
    }

    // ---- Supplies: materials another skill's plan gets from this skill ----

    // In this skill's Plan view: what the other skills' plans need from it (yew logs for Firemaking), how much you have,
    // the XP and time it is; a click goes to that plan
    /**
     * A supply milestone is the supplies: it ends wherever getting all that the other plans still need takes you (from
     * your XP now, or its start), so its levels follow them; the level milestones after it keep their target levels and
     * start where it ends. Returns whether anything moved (the caller saves it).
     */
    private boolean fitSupplyMilestones(Skill shown, CalculatorData.Calc skillCalc, List<CalculatorData.Bonus> on,
                                        SkillPlans.Plan plan, int now) {
        boolean changed = false;
        // (when the plan ends with a supply milestone and its goal is there, the goal moves with it, up or down)
        SkillPlans.Step last = null;
        for (SkillPlans.Step step : plan.steps) {
            if (last == null || step.to > last.to || step.to == last.to && !step.supplies.isEmpty()) {
                last = step;
            }
        }
        boolean goalFromSupplies = last != null && !last.supplies.isEmpty() && plan.goal >= last.from && plan.goal <= last.to;
        // The plan reflows in order. A supply milestone is centred on its supplies, not a level: it starts where the one
        // before it ends (or at its action's level, if that's later) and runs as far as getting what's still to get
        // takes you. A level milestone after one keeps its target level and starts where it ends: when the supplies
        // take you further it gets shorter, when they take you less far (bought or banked some) it starts sooner; one
        // the supplies take you past is covered (done by them)
        List<SkillPlans.Step> order = new ArrayList<>(plan.steps);
        order.sort((a, b) -> Integer.compare(a.from, b.from));
        Integer cursor = null;
        boolean afterSupply = false;
        for (SkillPlans.Step step : order) {
            step.covered = false;
            if (step.supplies.isEmpty()) {
                if (afterSupply && cursor != null) {
                    if (step.to <= cursor) {
                        // (the supplies before it get you past its level: done by them; what follows starts at the cursor)
                        step.covered = true;
                        continue;
                    }
                    if (step.from != cursor) {
                        step.from = cursor;
                        changed = true;
                    }
                }
                cursor = step.to;
                afterSupply = false;
                continue;
            }
            CalculatorData.Action action = CalculatorData.named(skillCalc, step.action);
            step.endXp = 0;
            step.gotAll = false;
            if (action == null) {
                cursor = step.to;
                afterSupply = true;
                continue;
            }
            long items = suppliesToGet(step);
            int from = cursor == null ? step.from : Math.max(action.level, cursor);
            int to;
            step.gotAll = items <= 0;
            if (step.gotAll) {
                // You have all its supplies (cut, bought or banked): it's done and takes no levels. It sits where the one
                // before it ends (one level long, so it stays in the plan in case you use them up), and what comes after
                // starts there too
                to = Math.min(99, from + 1);
            } else {
                int per = 1;
                for (RecipeData.Recipe recipe : PlannerCalculator.recipesFor(shown, action, recipes)) {
                    per = Math.max(per, PlannerCalculator.perMake(recipe, action));
                }
                long actions = (items + per - 1) / per;
                double each = CalculatorData.xpEach(action, on);
                long start = Math.max(now, Experience.getXpForLevel(Math.max(1, from)));
                long end = Math.min(Experience.MAX_SKILL_XP, start + (long) Math.ceil(actions * each));
                to = Math.min(99, Math.max(from + 1, CalculatorData.level((int) end, false)));
                // (exactly as far as the supplies, wherever in a level that is: even short of its end level, when
                // what's left is less than a level)
                step.endXp = end;
            }
            if (from != step.from || to != step.to) {
                step.from = from;
                step.to = to;
                if (!goalFromSupplies) {
                    plan.goal = Math.max(plan.goal, to);
                }
                changed = true;
            }
            // (where the next one starts: its end, or its start when it's done and takes no levels)
            cursor = step.gotAll ? from : to;
            afterSupply = true;
        }
        if (goalFromSupplies) {
            // The end of the plan: the last milestone's end (a done supply milestone takes no levels: its start)
            int end = 0;
            for (SkillPlans.Step step : plan.steps) {
                if (!step.covered) {
                    end = Math.max(end, step.gotAll ? step.from : step.to);
                }
            }
            if (end > 0 && end != plan.goal) {
                plan.goal = end;
                changed = true;
            }
        }
        // (a done one first among those starting at the same level: it comes before what follows it)
        plan.steps.sort((a, b) -> a.from != b.from ? Integer.compare(a.from, b.from) : Boolean.compare(!a.gotAll, !b.gotAll));
        return changed;
    }

    // A count exactly ("1,323") up to 99,999 so the numbers on a line add up; past that, short ("123K")
    private static String count(long n) {
        return n < 100_000 ? String.format("%,d", n) : PlannerPanel.gp(n);
    }

    /**
     * The live link for supplies: on each refresh (bank, inventory, XP) every plan's supply milestones follow what you
     * still need to get, in your copy and in the tracker's (a plan you sent, or took from it), since what you have keeps
     * changing their levels; and which ones you have all the supplies of goes to the Skilling Helper, so its goal goes
     * past them to the next milestone. Whichever plan is open here.
     */
    void followSupplies() {
        try {
            for (Skill s : skills()) {
                SkillPlans.Plan plan = calc.plan(s);
                SkillPlans.Plan shared = calc.trackedPlan(s);
                // (a copy: the tracker's decoded plan is shared, never changed in place)
                SkillPlans.Plan tracked = shared == null ? null : shared.copy();
                int xp = calc.xp(s);
                Map<String, SkillPlans.SupplyState> states = new java.util.HashMap<>();
                if (xp >= 0 && (hasSupplies(plan) || hasSupplies(tracked))) {
                    CalculatorData.Calc skillCalc = data.calc(s);
                    List<CalculatorData.Bonus> on = CalculatorData.bonuses(skillCalc, calc.bonuses(s));
                    boolean linked = tracked != null && plan != null && calc.isTracked(s, plan);
                    // The tracker's copy when it differs from yours (your unsent edits stay yours): its supply
                    // milestones follow your supplies all the same (first, so yours wins where both have one)
                    if (hasSupplies(tracked) && !linked) {
                        if (fitSupplyMilestones(s, skillCalc, on, tracked, xp)) {
                            calc.saveTrackedSupplies(s, tracked);
                        }
                        addStates(tracked, states);
                    }
                    if (hasSupplies(plan)) {
                        SkillPlans.Plan before = plan.copy();
                        if (fitSupplyMilestones(s, skillCalc, on, plan, xp)) {
                            // (the tracker's copy too, when it's this plan)
                            calc.savePlanQuietly(s, plan, before);
                        }
                        addStates(plan, states);
                    }
                }
                calc.saveSupplyStates(s, states);
                // Linked (the goal follows the plan you sent): the plan is the master, so the goal is its end level
                SkillPlans.Plan now = calc.trackedPlan(s);
                SkillGoals.Follow follow = calc.follow(s);
                if (now != null && xp >= 0 && (follow == null || follow == SkillGoals.Follow.PLAN)) {
                    int goalXp = now.goalXp();
                    // (a plan ending in a supply milestone: the goal is exactly where getting those supplies takes you,
                    // not the level it's in)
                    SkillPlans.Step last = null;
                    for (SkillPlans.Step step : now.steps) {
                        if (last == null || step.to >= last.to) {
                            last = step;
                        }
                    }
                    SkillPlans.SupplyState end = last == null || last.supplies.isEmpty() ? null
                        : states.get(last.action.toLowerCase(java.util.Locale.ROOT));
                    if (end != null && end.toGet > 0 && end.endXp > xp && last.to == now.goal) {
                        goalXp = (int) Math.min(Experience.MAX_SKILL_XP, end.endXp);
                    }
                    int goal = calc.goal(s);
                    if (goal > 0 && goal != goalXp) {
                        calc.setGoal(s, goalXp, xp);
                    }
                }
            }
        } catch (RuntimeException e) {
            // (never stops the Planner drawing: the next refresh tries again)
        }
    }

    // Each supply milestone's supplies still to get and where they take you (fitSupplyMilestones just worked out its end)
    private void addStates(SkillPlans.Plan plan, Map<String, SkillPlans.SupplyState> states) {
        for (SkillPlans.Step step : plan.steps) {
            if (step.covered) {
                states.put(SkillPlans.COVERED + step.action.toLowerCase(java.util.Locale.ROOT), new SkillPlans.SupplyState(0, 0));
            }
            if (!step.supplies.isEmpty()) {
                long wanted = 0;
                long have = 0;
                for (SkillPlans.Supply supply : step.supplies) {
                    long[] still = stillNeeded(supply.skill, supply.item);
                    long w = still == null ? supply.quantity : still[0];
                    wanted += w;
                    have += still == null ? 0 : Math.min(w, still[1]);
                }
                states.put(step.action.toLowerCase(java.util.Locale.ROOT), new SkillPlans.SupplyState(
                    step.gotAll ? 0 : Math.max(0, wanted - have), step.endXp, wanted, have));
            }
        }
    }

    private static boolean hasSupplies(SkillPlans.Plan plan) {
        if (plan != null) {
            for (SkillPlans.Step step : plan.steps) {
                if (!step.supplies.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    // How far through getting a supply milestone's supplies you are (0 to 1, by count), or -1 for any other milestone
    private double supplyProgress(SkillPlans.Step step) {
        if (step.supplies.isEmpty()) {
            return -1;
        }
        long wanted = 0;
        long have = 0;
        for (SkillPlans.Supply supply : step.supplies) {
            long[] still = stillNeeded(supply.skill, supply.item);
            long w = still == null ? supply.quantity : still[0];
            wanted += w;
            have += still == null ? 0 : Math.min(w, still[1]);
        }
        return wanted <= 0 ? 1 : (double) have / wanted;
    }

    // How many of a supply milestone's items the other plans still need that you don't have
    private long suppliesToGet(SkillPlans.Step step) {
        long total = 0;
        for (SkillPlans.Supply supply : step.supplies) {
            long[] still = stillNeeded(supply.skill, supply.item);
            long wanted = still == null ? supply.quantity : still[0];
            long have = still == null ? 0 : still[1];
            total += Math.max(0, wanted - have);
        }
        return total;
    }

    /**
     * What another skill's plan still needs of an item it gets from here ([0]) and how many you have ([1]): its
     * materials from your XP there on, so what it's already made or burned isn't counted again.
     */
    private long[] stillNeeded(String consumerName, String item) {
        Skill consumer;
        try {
            consumer = Skill.valueOf(consumerName);
        } catch (IllegalArgumentException e) {
            return null;
        }
        SkillPlans.Plan plan = calc.plan(consumer);
        if (plan == null) {
            return new long[]{0, 0};
        }
        CalculatorData.Calc consumerCalc = data.calc(consumer);
        List<PlannerCalculator.StepRow> rows = PlannerCalculator.stepRows(consumer, consumerCalc,
            CalculatorData.bonuses(consumerCalc, calc.bonuses(consumer)), plan, Math.max(0, calc.xp(consumer)), recipes,
            training, this::price, calc::itemPrice);
        for (PlannerCalculator.Need need : PlannerCalculator.planMaterials(consumer, rows, recipes, calc.owned())) {
            if (need.name.equalsIgnoreCase(item)) {
                return new long[]{need.needed, need.have};
            }
        }
        return new long[]{0, 0};
    }

    private void fillSupplies(Skill shown) {
        // Each skill's plan's materials got yourself, followed all the way down; the steps in this skill
        Map<String, long[]> totals = new java.util.LinkedHashMap<>();
        Map<String, PlannerCalculator.Link> links = new java.util.HashMap<>();
        for (Skill consumer : skills()) {
            SkillPlans.Plan plan = consumer == shown ? null : calc.plan(consumer);
            if (plan == null) {
                continue;
            }
            CalculatorData.Calc consumerCalc = data.calc(consumer);
            List<PlannerCalculator.StepRow> rows = PlannerCalculator.stepRows(consumer, consumerCalc,
                CalculatorData.bonuses(consumerCalc, calc.bonuses(consumer)), plan, Math.max(0, calc.xp(consumer)), recipes,
                training, this::price, calc::itemPrice);
            Map<String, Integer> pool = new java.util.HashMap<>(calc.owned());
            for (PlannerCalculator.Need need : PlannerCalculator.planMaterials(consumer, rows, recipes, calc.owned())) {
                if (need.tool || !calc.gathers(consumer, need.name)
                    || PlannerCalculator.sourceOf(need.name, consumer, data, recipes) == null) {
                    continue;
                }
                for (PlannerCalculator.Link link : PlannerCalculator.chain(need.name, need.needed, consumer, data, recipes, pool,
                    calc::gathers)) {
                    if (link.skill != shown || planned(shown, consumer, link.item)) {
                        continue;
                    }
                    String key = link.item + "|" + consumer.name();
                    long[] sum = totals.computeIfAbsent(key, k -> new long[2]);
                    sum[0] += link.total;
                    sum[1] += link.have;
                    links.put(key, link);
                }
            }
        }
        // (what you have all of already drops off: nothing to get for it)
        totals.values().removeIf(sum -> sum[1] >= sum[0]);
        if (totals.isEmpty()) {
            return;
        }
        list.add(section("supplies", "Supplies for other plans", String.valueOf(totals.size())));
        if (calc.planFolded("supplies")) {
            return;
        }
        for (Map.Entry<String, long[]> entry : totals.entrySet()) {
            Skill consumer = Skill.valueOf(entry.getKey().substring(entry.getKey().indexOf('|') + 1));
            PlannerCalculator.Link link = links.get(entry.getKey());
            PlannerCalculator.Need need = new PlannerCalculator.Need(link.item, entry.getValue()[0],
                (int) Math.min(Integer.MAX_VALUE, entry.getValue()[1]), false);
            list.add(PlannerUi.spaced(supplyCard(consumer, need,
                new PlannerCalculator.Source(link.skill, link.action, link.gathered))));
        }
    }

    private JComponent supplyCard(Skill consumer, PlannerCalculator.Need need, PlannerCalculator.Source source) {
        long shortBy = Math.max(0, need.needed - need.have);
        Supply supply = supply(source, shortBy);
        PlannerUi.Card card = new PlannerUi.Card(need.name + " for " + consumer.getName(),
            String.format("%,d / %,d", need.have, need.needed), shortBy > 0 ? "+" + PlannerPanel.gp(supply.xp) + " xp" : "");
        card.left.setForeground(need.enough() ? PlannerUi.READY : Color.WHITE);
        card.right.setForeground(PlannerUi.READY);
        // (the supplies section's purple down the left)
        card.setBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, SUPPLIES));
        if (shortBy > 0) {
            long buying = shortBy * (long) price(need.name);
            card.third(supply.seconds > 0 ? duration(supply.seconds, supply.estimated) : "no XP rate",
                buying > 0 ? "saves " + PlannerPanel.gp(buying) : "");
            card.lowerRight.setForeground(PlannerUi.READY);
        } else {
            card.third("You have them all", "");
            card.lower.setForeground(PlannerUi.READY);
        }
        int at = CalculatorData.level(Math.max(0, calc.xp(source.skill)), false);
        if (shortBy > 0 && source.action.level > at) {
            JLabel[] needs = card.line("Needs " + source.skill.getName() + " " + source.action.level + " (you're " + at + ")", "");
            needs[0].setForeground(HudColors.WARN);
        }
        card.bar(new PlannerUi.Bar(Math.max(0.02, Math.min(1, need.needed > 0 ? (double) need.have / need.needed : 1)),
            need.enough() ? PlannerUi.READY : PlannerUi.GROWING));
        picture(card.icon, source.action);
        PlannerUi.Tip supplyTip = new PlannerUi.Tip(need.name, "for your " + consumer.getName() + " plan")
            .row("It needs", String.format("%,d", need.needed))
            .row("You have", String.format("%,d", need.have), need.enough() ? PlannerUi.Tip.GOOD : PlannerUi.Tip.WARN);
        if (shortBy > 0) {
            supplyTip.section((source.gathered ? "Gathering" : "Making") + " the " + String.format("%,d", shortBy) + " more")
                .row(source.skill.getName() + " xp", String.format("%,d", supply.xp), PlannerUi.Tip.GOOD);
            if (supply.seconds > 0) {
                supplyTip.row("Time", duration(supply.seconds, supply.estimated), supply.estimated ? PlannerUi.Tip.WARN : null);
            }
            long buying = shortBy * (long) price(need.name);
            if (buying > 0) {
                supplyTip.row("Buying them instead", PlannerPanel.gp(buying), PlannerUi.Tip.BAD);
            }
        }
        String tip = supplyTip.hint("Click: go to the " + consumer.getName() + " plan \u00b7 right-click: add it to your "
            + source.skill.getName() + " plan").build();
        // Right-click: make it a milestone of this skill's plan (its action, from your level to where its XP gets you)
        JPopupMenu menu = new JPopupMenu();
        if (shortBy > 0) {
            menu.add(menuItem("Add to the " + source.skill.getName() + " plan",
                // (it keeps all the plan needs: what you have counts against that, live)
                () -> addSupplyMilestone(source, consumer, need.name, need.needed, supply.xp)));
        }
        menu.add(menuItem("Go to the " + consumer.getName() + " plan", () -> pick(consumer)));
        return card.interactive(() -> pick(consumer), menu, tip);
    }

    // A supply as a milestone of its skill's plan (a new plan to its end if there isn't one): the milestones there make room
    /**
     * A supply into its skill's plan: onto a milestone there with the same method if it has one (its count of them is
     * kept for the other plan), else a new milestone after the other supplies, from your level for as long as its XP
     * takes. Either way the milestone says what's kept for whom, and it's left out of that milestone's sales.
     */
    private void addSupplyMilestone(PlannerCalculator.Source source, Skill consumer, String item, long quantity, long xp) {
        Skill skill = source.skill;
        SkillPlans.Plan plan = calc.plan(skill);
        if (plan == null) {
            plan = new SkillPlans.Plan(1);
            plan.name = "Supplies";
        }
        int level = CalculatorData.level(Math.max(0, calc.xp(skill)), false);
        SkillPlans.Step onto = null;
        for (SkillPlans.Step step : plan.steps) {
            if (step.action.equalsIgnoreCase(source.action.name) && step.to > level) {
                onto = step;
                break;
            }
        }
        if (onto == null) {
            // After every milestone that's already a supply (so a second one doesn't take the first one's place)
            int start = Math.max(level, source.action.level);
            boolean moved = true;
            while (moved) {
                moved = false;
                for (SkillPlans.Step step : plan.steps) {
                    if (!step.supplies.isEmpty() && step.from <= start && start < step.to) {
                        start = step.to;
                        moved = true;
                    }
                }
            }
            if (start >= 99) {
                JOptionPane.showMessageDialog(list, "The " + skill.getName() + " plan has no room left before 99 for it.",
                    "Supplies", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            long from = Math.max(calc.xp(skill), Experience.getXpForLevel(start));
            int end = Math.min(99, CalculatorData.level((int) Math.min(Experience.MAX_SKILL_XP, from + xp), false));
            if (end <= start) {
                end = start + 1;
            }
            List<CalculatorData.Bonus> on = CalculatorData.bonuses(data.calc(skill), calc.bonuses(skill));
            TrainingData.Method method = SkillPlans.methodFor(training, skill, source.action.name, start, end);
            onto = new SkillPlans.Step(source.action.name, start, end, method == null ? null : method.name,
                CalculatorData.xpEach(source.action, on));
            PlannerCalculator.insertStep(plan, onto);
            // (insertStep made room; the step itself is the one in the plan)
        }
        // The supply on it (this plan's again replaces its earlier count)
        SkillPlans.Step target = onto;
        target.supplies.removeIf(s -> s.skill.equals(consumer.name()) && s.item.equalsIgnoreCase(item));
        target.supplies.add(new SkillPlans.Supply(consumer.name(), item, quantity));
        plan.goal = Math.max(plan.goal, target.to);
        // Below its level: the climb to it, from the wiki's suggestions, in the levels no milestone covers (ordinary
        // milestones: change their methods as you like)
        if (target.from > level) {
            List<CalculatorData.Bonus> bonuses = CalculatorData.bonuses(data.calc(skill), calc.bonuses(skill));
            SkillPlans.Plan climb = PlannerCalculator.suggestedPlan(skill, CalculatorData.shown(data.calc(skill), calc.showMembers()),
                bonuses, training, level, target.from, calc.otherMethods(skill));
            PlannerCalculator.fillGaps(plan, climb, level, target.from, "to reach " + target.from + " for the "
                + consumer.getName() + " supply");
        }
        calc.savePlan(skill, plan);
        redraw.run();
    }

    // Whether this skill's open plan already carries a supply for that plan's item
    private boolean planned(Skill shown, Skill consumer, String item) {
        SkillPlans.Plan plan = calc.plan(shown);
        if (plan == null) {
            return false;
        }
        for (SkillPlans.Step step : plan.steps) {
            for (SkillPlans.Supply supply : step.supplies) {
                if (supply.skill.equals(consumer.name()) && supply.item.equalsIgnoreCase(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    // How long what's short takes to buy at the item's Grand Exchange buy limit (and what's left of it now)
    private void buyRows(PlannerUi.Tip tip, PlannerCalculator.Need need) {
        if (calc.ironman()) {
            return;
        }
        Long yours = calc.yourPrice(need.name);
        tip.section("Buying it");
        if (yours != null) {
            tip.row("Your price (trades this week)", String.format("%,d", yours) + " gp", PlannerUi.Tip.BLUE);
            tip.row("GE price", String.format("%,d", icons.price(need.name)) + " gp", PlannerUi.Tip.MUTED);
        } else if (icons.price(need.name) > 0) {
            tip.row("GE price", String.format("%,d", icons.price(need.name)) + " gp");
        }
        String limit = buyLimitTime(need);
        if (!limit.isEmpty()) {
            tip.line(limit, PlannerUi.Tip.MUTED);
        }
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
        return "Buy limit " + String.format("%,d", limit) + " every 4 hours"
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
        box.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, MATERIALS),
            new EmptyBorder(5, 7, 5, 7)));
        for (PlannerCalculator.Need need : needs) {
            // Got yourself (gathered or made in another skill) or bought
            PlannerCalculator.Source source = need.tool ? null : PlannerCalculator.sourceOf(need.name, shown, data, recipes);
            boolean gather = source != null && calc.gathers(shown, need.name);
            String waiting = waitingOn(shown, need);
            Color colour = need.enough() ? PlannerUi.READY : waiting != null ? HudColors.WARN : gather ? GATHER_BLUE : COSTS;
            String amounts = need.tool ? (need.enough() ? "have it" : "missing")
                : String.format("%,d / %,d", need.have, need.needed) + (gather && !need.enough() ? " " + shortSkill(source.skill) : "");
            String tip;
            if (need.tool) {
                tip = new PlannerUi.Tip(need.name, "a tool the plan's methods need")
                    .line(need.enough() ? "You have one" : "Not in your bank or inventory",
                        need.enough() ? PlannerUi.Tip.GOOD : PlannerUi.Tip.BAD).build();
            } else {
                PlannerUi.Tip materialTip = new PlannerUi.Tip(need.name, "a material")
                    .row("The plan needs", String.format("%,d", need.needed))
                    .row("You have", String.format("%,d", need.have), need.enough() ? PlannerUi.Tip.GOOD : null);
                if (!need.enough()) {
                    materialTip.row("Short", String.format("%,d", need.needed - need.have), PlannerUi.Tip.BAD);
                }
                if (!gather) {
                    buyRows(materialTip, need);
                }
                if (waiting != null) {
                    materialTip.line(waiting + " first", PlannerUi.Tip.WARN);
                }
                gatherRows(materialTip, shown, need, source, gather);
                originRows(materialTip, need.name, Math.min(need.have, need.needed));
                tip = materialTip.hint("Bank as last opened plus inventory \u00b7 right-click: where some came from").build();
            }
            JPanel row = new JPanel(new BorderLayout(6, 0));
            row.setOpaque(false);
            JLabel name = PlannerUi.label(need.name, Color.WHITE);
            name.setMinimumSize(new Dimension(0, 0));
            row.add(name, BorderLayout.CENTER);
            row.add(PlannerUi.label(amounts, colour), BorderLayout.EAST);
            row.setToolTipText(PlannerUi.fit(tip));
            JPopupMenu menu = new JPopupMenu();
            if (source != null) {
                // Right-click: get it yourself in the other skill, or buy it
                String verb = source.gathered ? "Gather" : "Make";
                menu.add(gather ? menuItem("Buy it instead", () -> {
                    calc.setGathers(shown, need.name, false);
                    redraw.run();
                }) : menuItem(verb + " it yourself (" + source.skill.getName() + ")", () -> {
                    calc.setGathers(shown, need.name, true);
                    redraw.run();
                }));
            }
            if (!need.tool) {
                // Where what you have came from, for the plan's money (what the Planner didn't see: trades while it was
                // off, ones from before)
                menu.add(menuItem("Where some came from...", () -> recordOriginDialog(need.name)));
                menu.add(menuItem("Forget where they came from", () -> {
                    if (confirm("Forget where your " + need.name + " came from? What you have counts at its GE price "
                        + "until it sees more.")) {
                        calc.forgetOrigin(icons.itemId(need.name));
                        redraw.run();
                    }
                }));
            }
            if (menu.getComponentCount() > 0) {
                row.setComponentPopupMenu(menu);
            }
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

    // Where what you have of a material came from, for its tooltip
    private void originRows(PlannerUi.Tip tip, String name, long have) {
        int id = icons.itemId(name);
        PlannerOrigins.Origin origin = calc.origin(id);
        long[] split = PlannerOrigins.ownedCost(origin, have, price(name));
        if (have <= 0) {
            return;
        }
        tip.section("What you have");
        if (split[1] > 0) {
            tip.row("Bought", String.format("%,d", split[1]) + " (paid " + PlannerPanel.gp(Math.round(split[1]
                * origin.paidEach())) + ")", PlannerUi.Tip.BAD);
        }
        if (split[2] > 0) {
            tip.row("Got yourself", String.format("%,d", split[2]) + " (free)", PlannerUi.Tip.GOOD);
        }
        if (split[3] > 0) {
            tip.row("Not known", String.format("%,d", split[3]) + " (at GE price)", PlannerUi.Tip.MUTED);
        }
    }

    // Tell it where some of an item came from: how many, bought (and what you paid for them in all) or got yourself
    private void recordOriginDialog(String name) {
        int id = icons.itemId(name);
        if (id <= 0) {
            JOptionPane.showMessageDialog(list, "Couldn't find " + name + "'s item.", "Where they came from",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        javax.swing.JSpinner count = new javax.swing.JSpinner(new javax.swing.SpinnerNumberModel(1, 1, Integer.MAX_VALUE, 1));
        javax.swing.JSpinner each = new javax.swing.JSpinner(new javax.swing.SpinnerNumberModel(Math.max(0, price(name)), 0, Integer.MAX_VALUE, 1));
        javax.swing.JRadioButton bought = new javax.swing.JRadioButton("Bought them", true);
        javax.swing.JRadioButton yourself = new javax.swing.JRadioButton("Got them myself (free)");
        javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();
        group.add(bought);
        group.add(yourself);
        yourself.addActionListener(e -> each.setEnabled(false));
        bought.addActionListener(e -> each.setEnabled(true));
        JPanel form = new JPanel(new GridLayout(0, 1, 0, 4));
        form.add(new JLabel("How many " + name + ":"));
        form.add(count);
        form.add(bought);
        form.add(yourself);
        form.add(new JLabel("Paid each (gp):"));
        form.add(each);
        if (JOptionPane.showConfirmDialog(list, form, "Where they came from", JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        long n = ((Number) count.getValue()).longValue();
        long paid = n * ((Number) each.getValue()).longValue();
        calc.recordOrigin(id, n, paid, bought.isSelected());
        redraw.run();
    }

    /**
     * What a plan's materials really cost you: what you have of each at what it cost you (bought at what you paid, got
     * yourself free, not known at its GE price), and what's still to get at its GE price unless you get it yourself
     * (Gather it yourself, or another skill's plan has a supply milestone for it). {@code [0]}: gp; {@code [1]}: all
     * of them at GE prices.
     */
    private long[] materialsCost(Skill shown, List<PlannerCalculator.StepRow> steps, PlannerUi.Tip tip) {
        long gp = 0;
        long market = 0;
        int shownRows = 0;
        for (PlannerCalculator.Need need : PlannerCalculator.planMaterials(shown, steps, recipes, calc.owned())) {
            if (need.tool || need.needed <= 0) {
                continue;
            }
            int each = price(need.name);
            long have = Math.min(need.have, need.needed);
            long toGet = need.needed - have;
            long[] owned = PlannerOrigins.ownedCost(calc.origin(icons.itemId(need.name)), have, each);
            boolean yourself = (calc.gathers(shown, need.name) && PlannerCalculator.sourceOf(need.name, shown, data, recipes) != null)
                || suppliedFor(shown, need.name);
            long getting = yourself ? 0 : toGet * (long) each;
            gp += owned[0] + getting;
            market += need.needed * (long) each;
            if (tip != null && shownRows++ < 6) {
                StringBuilder how = new StringBuilder();
                if (owned[1] > 0) {
                    how.append(String.format("%,d", owned[1])).append(" bought");
                }
                if (owned[2] > 0) {
                    how.append(how.length() > 0 ? ", " : "").append(String.format("%,d", owned[2])).append(" yours");
                }
                if (owned[3] > 0) {
                    how.append(how.length() > 0 ? ", " : "").append(String.format("%,d", owned[3])).append(" at GE");
                }
                if (toGet > 0) {
                    how.append(how.length() > 0 ? ", " : "").append(String.format("%,d", toGet)).append(yourself ? " to get" : " to buy");
                }
                tip.row(need.name, PlannerPanel.gp(owned[0] + getting));
                tip.line(how.toString(), PlannerUi.Tip.MUTED);
            }
        }
        return new long[] {gp, market};
    }

    // The milestone you're on, in a plan linked to your Skilling Helper goal (you sent it, the goal follows it): timed
    // at your XP an hour as the tracker counts it while you train, not the guide's rate
    private void yourRate(Skill shown, SkillPlans.Plan plan, List<PlannerCalculator.StepRow> steps, int level) {
        int rate = currentOverride == null ? calc.trackerXpRate(shown) : 0;
        SkillPlans.Plan tracked = rate > 0 ? calc.trackedPlan(shown) : null;
        SkillGoals.Follow follow = tracked == null ? null : calc.follow(shown);
        if (tracked == null || !(plan.sent || calc.isTracked(shown, plan))
            || follow != null && follow != SkillGoals.Follow.PLAN) {
            return;
        }
        for (PlannerCalculator.StepRow s : steps) {
            if (!s.done && s.row != null && s.step.covers(level)) {
                s.row.seconds = (long) Math.ceil(s.xp * 3600.0 / rate);
                s.row.xpHour = rate;
                s.row.estimated = false;
                s.yourRate = true;
            }
        }
    }

    // A milestone's note short enough for its card ("to reach 60 for the Firemaking supply": "Climb for Firemaking
    // (60)"); the whole note is in its tooltip
    static String shortNote(String note) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^to reach (\\d+) for the (.+) supply$").matcher(note);
        if (m.matches()) {
            return "Climb for " + m.group(2) + " (" + m.group(1) + ")";
        }
        m = java.util.regex.Pattern.compile("^to reach (\\d+) for (.+)$").matcher(note);
        if (m.matches()) {
            return "Climb to " + m.group(1) + " for " + m.group(2);
        }
        return note;
    }

    // Whether the plan ends with a supply milestone, its goal where that ends (the goal is the supplies, not a level)
    private static boolean endsInSupplies(SkillPlans.Plan plan) {
        SkillPlans.Step last = null;
        for (SkillPlans.Step step : plan.steps) {
            if (!step.covered && (last == null || step.to > last.to || step.to == last.to && !step.supplies.isEmpty())) {
                last = step;
            }
        }
        return last != null && !last.supplies.isEmpty() && plan.goal >= last.from && plan.goal <= last.to;
    }

    // Whether another skill's plan has a supply milestone getting this item for this skill's plan
    private boolean suppliedFor(Skill shown, String item) {
        for (Skill other : skills()) {
            SkillPlans.Plan plan = other == shown ? null : calc.plan(other);
            if (plan == null) {
                continue;
            }
            for (SkillPlans.Step step : plan.steps) {
                for (SkillPlans.Supply supply : step.supplies) {
                    if (supply.skill.equals(shown.name()) && supply.item.equalsIgnoreCase(item)) {
                        return true;
                    }
                }
            }
        }
        return false;
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
        boolean hasGoal = calc.goal(shown) > planXp();
        list.add(PlannerUi.group(shown.getName() + (hasGoal ? " goal" : " plans"),
            plans.isEmpty() || hasGoal ? "" : String.valueOf(plans.size()),
            new PlannerUi.IconButton(new PlannerUi.Glyph(PlannerUi.Shape.PLUS, 14, ColorScheme.LIGHT_GRAY_COLOR),
                "New plan", () -> newPlan(shown))));
        // The open plan's contents go under all the plan cards (so every plan is listed together)
        boolean showOpen = false;
        int level = levelNow();
        int xp = planXp();
        boolean expanded = calc.planExpanded();
        goalCard(shown, plans, xp, level);
        // With a goal, its plans are under it (folded away with it)
        for (int i = 0; i < plans.size() && (!hasGoal || goalOpen); i++) {
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
            // (the plan you sent, changed since: a draft until you send it)
            boolean draft = !tracked && plan.sent && calc.trackedPlan(shown) != null;
            card.corner.setText(tracked ? "Followed" : draft ? "Unsent changes" : "");
            card.corner.setForeground(draft ? HudColors.WARN : PlannerUi.READY);
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
            PlannerUi.Tip tip = new PlannerUi.Tip(planName(plan, i), "level " + start + " \u2192 " + plan.goal);
            tip.row("Milestones", String.valueOf(plan.steps.size()));
            tip.row("Done", (int) Math.floor(done * 100) + "% (you're level " + level + ")",
                done >= 1 ? PlannerUi.Tip.GOOD : null);
            if (tracked) {
                tip.line("Followed: your goal follows it in the tracker", PlannerUi.Tip.GOOD);
            } else if (plan.sent && calc.trackedPlan(shown) != null) {
                tip.line("Unsent changes: the tracker has it as you last sent it", PlannerUi.Tip.WARN);
            }
            tip.hint((unfolded ? "Click to fold it away" : "Click to open it") + " \u00b7 right-click: rename, duplicate or delete");
            list.add(indent(hasGoal, card.interactive(click, menu, tip.build())));
            if (unfolded && openPlan != null) {
                showOpen = true;
            }
        }
        if (showOpen) {
            openPlan.run();
        }
        // Other skills' plans: a click goes to that skill's
        Map<Skill, Integer> others = calc.planCounts(skills());
        others.remove(shown);
        if (!others.isEmpty()) {
            // (a heading, then the skills two to a row: a row that wraps on its own can't grow, so it drew over what's below)
            JPanel row = new JPanel(new BorderLayout(0, 2));
            row.setBackground(PlannerUi.BACKGROUND);
            row.setBorder(new EmptyBorder(0, 2, 6, 0));
            row.add(PlannerUi.label("Other skills' plans", PlannerUi.MUTED), BorderLayout.NORTH);
            JPanel links = new JPanel(new GridLayout(0, 2, 6, 2));
            links.setOpaque(false);
            for (Map.Entry<Skill, Integer> e : others.entrySet()) {
                Skill other = e.getKey();
                JLabel link = PlannerUi.linkLabel(other.getName() + " (" + e.getValue() + ")", "Your " + other.getName() + " plans",
                    () -> pick(other));
                link.setMinimumSize(new Dimension(0, 0));
                links.add(link);
            }
            row.add(links, BorderLayout.CENTER);
            list.add(PlannerUi.fullWidth(row));
        }
    }

    // The skill's goal (set here or in the Skilling Helper: the same goal) as a card like a plan's: its levels, what
    // it's set to train, how far along; click it for Change goal, Remove goal and Make a plan for it
    // A plan card under the goal: set in a little, so it reads as the goal's
    private static JComponent indent(boolean under, JComponent card) {
        JComponent spaced = PlannerUi.spaced(card);
        if (!under) {
            return spaced;
        }
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(PlannerUi.BACKGROUND);
        row.setBorder(new EmptyBorder(0, 12, 0, 0));
        row.add(spaced, BorderLayout.CENTER);
        return PlannerUi.fullWidth(row);
    }

    // The tracked plan's name in this skill's list ("Plan 2"), or "your plan"
    private String planNameOf(Skill shown, SkillPlans.Plan tracked) {
        List<SkillPlans.Plan> plans = calc.plans(shown);
        for (int i = 0; i < plans.size(); i++) {
            if (calc.isTracked(shown, plans.get(i))) {
                return planName(plans.get(i), i);
            }
        }
        return "your plan";
    }

    private void goalCard(Skill shown, List<SkillPlans.Plan> plans, int xp, int level) {
        int goal = calc.goal(shown);
        if (goal <= xp) {
            return;
        }
        int start = calc.goalStart(shown) > 0 ? Math.min(calc.goalStart(shown), xp) : Experience.getXpForLevel(level);
        double done = goal > start ? Math.max(0, Math.min(1, (double) (xp - start) / (goal - start))) : 1;
        SkillPlans.Plan tracked = calc.trackedPlan(shown);
        String pick = calc.goalPick(shown);
        // What the tracker follows (its Follow: the plan, your pick, the wiki; or the usual order, a pick first)
        SkillGoals.Follow following = calc.follow(shown);
        String planText = tracked == null ? null : "follows " + (tracked.name != null ? tracked.name : planNameOf(shown, tracked));
        String follows = following == SkillGoals.Follow.WIKI ? "the wiki's methods"
            : following == SkillGoals.Follow.PLAN && planText != null ? planText
            : following == SkillGoals.Follow.PICK && pick != null ? "via " + pick
            : pick != null ? "via " + pick : planText != null ? planText : "the wiki's methods";
        PlannerUi.Card card = new PlannerUi.Card((goalOpen ? "▾ " : "▸ ") + "Goal: " + goalText(shown, goal),
            CalculatorData.level(start, false) + " → " + CalculatorData.level(goal, true) + " · " + follows,
            "lvl " + level + " · " + (int) Math.floor(done * 100) + "%");
        card.right.setForeground(Color.WHITE);
        card.corner.setText(plans.isEmpty() ? "" : plans.size() + (plans.size() == 1 ? " plan" : " plans"));
        card.corner.setForeground(GOAL_GOLD);
        card.noIcon();
        card.bar(new PlannerUi.Bar(done, GOAL_GOLD));
        card.setBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, GOAL_GOLD));
        JPopupMenu menu = new JPopupMenu();
        menu.add(menuItem("Change goal...", () -> changeSkillGoal(shown)));
        menu.add(menuItem("Make a plan for it", () -> planFromGoal(shown, goal, pick)));
        // A plan made from it when the goal (or its method) has changed since: update it
        int made = calc.trackedPlan(shown) == null ? goalPlanIndex(shown) : -1;
        String changed = made >= 0 ? goalChanges(calc.plans(shown).get(made), goal, pick) : null;
        if (changed != null) {
            JLabel[] note = card.line("Goal changed: update the plan", "");
            note[0].setForeground(HudColors.WARN);
            note[0].setToolTipText(PlannerUi.fit("<html>" + PlannerUi.esc(changed).replace("\n", "<br>") + "<br>Right-click the goal > Update "
                + "the plan</html>"));
            menu.add(menuItem("Update \"" + planName(calc.plans(shown).get(made), made) + "\" from the tracker...",
                () -> updateFromGoal(shown, made, goal, pick)));
        }
        // The tracker follows what you sent: the plan you sent, changed since, is a draft (send or undo it); a copy
        // none of your plans is linked to (sent before plans remembered it) can be synced either way
        boolean drifted = tracked != null && plans.stream().noneMatch(p -> calc.isTracked(shown, p));
        if (drifted) {
            int sentAt = calc.sentIndex(shown);
            menu.addSeparator();
            if (sentAt >= 0) {
                SkillPlans.Plan sentPlan = plans.get(sentAt);
                String name = planName(sentPlan, sentAt);
                JLabel[] note = card.line("Changes to " + name + " not sent", "");
                note[0].setForeground(HudColors.WARN);
                note[0].setToolTipText(PlannerUi.fit("<html>The goal follows \"" + PlannerUi.esc(name) + "\" as you sent it; you've changed "
                    + "it since. Your changes stay a draft until you send them<br>Right-click the goal: send them, or "
                    + "undo them</html>"));
                menu.add(menuItem("Send changes to \"" + name + "\"", () -> {
                    CalculatorData.Calc skillCalc = data.calc(shown);
                    SkillPlans.Plan withXp = withXp(shown, sentPlan, CalculatorData.bonuses(skillCalc, calc.bonuses(shown)),
                        skillCalc);
                    int game = calc.xp(shown);
                    calc.export(shown, sentAt, withXp, game >= 0 ? game : current());
                    redraw.run();
                }));
                menu.add(menuItem("Undo changes to \"" + name + "\"", () -> {
                    if (confirm("Undo your changes to \"" + name + "\" since you sent it?")) {
                        calc.undoChanges(shown, sentAt);
                        redraw.run();
                    }
                }));
            } else {
                SkillPlans.Plan open = calc.plan(shown);
                int openAt = calc.openIndex(shown);
                JLabel[] note = card.line("Tracker has an older copy", "");
                note[0].setForeground(HudColors.WARN);
                note[0].setToolTipText(PlannerUi.fit("<html>The Skilling Helper follows a copy of a plan from before it was last changed: "
                    + tracked.steps.size() + " milestones to level " + tracked.goal
                    + "<br>Right-click the goal: send your open plan to it, or bring its copy in as a plan</html>"));
                if (open != null) {
                    menu.add(menuItem("Use the tracker's plan for \"" + planName(open, openAt) + "\"", () -> {
                        if (confirm("Replace \"" + planName(open, openAt) + "\" with the plan the tracker has (the one your "
                            + "goal follows)? Its milestones here go.")) {
                            calc.takeTracked(shown, openAt);
                            redraw.run();
                        }
                    }));
                }
                if (open != null && !open.steps.isEmpty()) {
                    menu.add(menuItem("Send \"" + planName(open, openAt) + "\" to the tracker", () -> {
                        CalculatorData.Calc skillCalc = data.calc(shown);
                        SkillPlans.Plan withXp = withXp(shown, open, CalculatorData.bonuses(skillCalc, calc.bonuses(shown)),
                            skillCalc);
                        int game = calc.xp(shown);
                        calc.export(shown, openAt, withXp, game >= 0 ? game : current());
                        redraw.run();
                    }));
                }
                menu.add(menuItem("Bring the tracker's copy in as a plan", () -> {
                    SkillPlans.Plan copy = tracked.copy();
                    copy.name = "From the tracker";
                    calc.newPlan(shown, copy);
                    // (it's the one sent now: the tracker has it as it is)
                    calc.markSentLast(shown);
                    calc.setMode(PlannerCalculator.Mode.PLAN);
                    redraw.run();
                }));
            }
            menu.add(menuItem("Take the plan out of the tracker", () -> {
                calc.untrack(shown);
                redraw.run();
            }));
            menu.addSeparator();
        }
        // Following a pick while a plan's sent: say so (the plan's still there to go back to)
        if (tracked != null && follows.startsWith("via ")) {
            JLabel[] note = card.line("Following your pick", "");
            note[0].setForeground(HudColors.WARN);
            note[0].setToolTipText(PlannerUi.fit("<html>The tracker follows " + PlannerUi.esc(pick) + " for now; your plan's "
                + "milestones still count your progress<br>Right-click the goal > Follow > Your plan to go back</html>"));
        }
        javax.swing.JMenu followMenu = new javax.swing.JMenu("Follow");
        Object[][] choices = {
            {null, "Usual order (pick, plan, wiki)", true},
            {SkillGoals.Follow.PLAN, tracked != null ? "Your plan" : "Your plan (none sent)", tracked != null},
            {SkillGoals.Follow.PICK, pick != null ? "Your pick: " + pick : "Your pick (none saved)", pick != null},
            {SkillGoals.Follow.WIKI, "The wiki's suggestion", true},
        };
        for (Object[] choice : choices) {
            SkillGoals.Follow follow = (SkillGoals.Follow) choice[0];
            javax.swing.JCheckBoxMenuItem item = new javax.swing.JCheckBoxMenuItem((String) choice[1], follow == following);
            item.setEnabled((Boolean) choice[2]);
            item.addActionListener(e -> {
                calc.setFollow(shown, follow);
                redraw.run();
            });
            followMenu.add(item);
        }
        menu.add(followMenu);
        menu.add(menuItem("Remove goal", () -> removeSkillGoal(shown)));
        list.add(PlannerUi.spaced(card.interactive(() -> {
            goalOpen = !goalOpen;
            redraw.run();
        }, menu, new PlannerUi.Tip("Your " + shown.getName() + " goal", goalText(shown, goal))
            .row("To go", String.format("%,d", goal - xp) + " xp")
            .row("Done", (int) Math.floor(done * 100) + "% since it was set")
            .row("Training", follows)
            .line("The same goal as the Skilling Helper's: change it in either", PlannerUi.Tip.MUTED)
            .hint("Click: " + (goalOpen ? "fold away" : "show") + " its plans \u00b7 right-click: change, make a plan, remove")
            .build())));
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
        SkillPlans.Plan plan = goalPlan(shown, goal, pick);
        plan.name = "Goal to " + plan.goal;
        calc.newPlan(shown, plan);
        calc.setPlanExpanded(true);
        calc.setMode(PlannerCalculator.Mode.PLAN);
        redraw.run();
    }

    /**
     * A plan to the Skilling Helper's goal as it's set: its method from your level (or from that method's level, with
     * the climb to it from the wiki's suggestions), or with no method set, the suggestions all the way, as the tracker
     * follows them. It remembers what it was made from (to tell when the goal changes).
     */
    private SkillPlans.Plan goalPlan(Skill shown, int goal, String pick) {
        SkillPlans.Plan plan = new SkillPlans.Plan(Math.min(99, CalculatorData.level(goal, true)));
        plan.fromGoal = goal;
        plan.fromPick = pick;
        CalculatorData.Calc skillCalc = data.calc(shown);
        List<CalculatorData.Bonus> on = CalculatorData.bonuses(skillCalc, calc.bonuses(shown));
        List<CalculatorData.Action> actions = CalculatorData.shown(skillCalc, calc.showMembers());
        int level = Math.max(1, CalculatorData.level(Math.max(0, calc.xp(shown)), false));
        if (level >= plan.goal) {
            return plan;
        }
        CalculatorData.Action action = pick == null ? null : CalculatorData.named(skillCalc, pick);
        if (action == null) {
            SkillPlans.Plan suggested = PlannerCalculator.suggestedPlan(shown, actions, on, training, level, plan.goal,
                calc.otherMethods(shown));
            plan.steps.addAll(suggested.steps);
            return plan;
        }
        int start = Math.min(plan.goal - 1, Math.max(level, action.level));
        TrainingData.Method method = SkillPlans.methodFor(training, shown, action.name, start, plan.goal);
        plan.steps.add(new SkillPlans.Step(action.name, start, plan.goal, method == null ? null : method.name,
            CalculatorData.xpEach(action, on)));
        if (start > level) {
            PlannerCalculator.fillGaps(plan, PlannerCalculator.suggestedPlan(shown, actions, on, training, level, start,
                calc.otherMethods(shown)), level, start, "to reach " + start + " for " + action.name);
        }
        return plan;
    }

    // The plan made from the Skilling Helper's goal (the open one if it is), or null; its index in [0]
    private int goalPlanIndex(Skill shown) {
        List<SkillPlans.Plan> plans = calc.plans(shown);
        int open = calc.openIndex(shown);
        if (open >= 0 && open < plans.size() && plans.get(open).fromGoal > 0) {
            return open;
        }
        for (int i = 0; i < plans.size(); i++) {
            if (plans.get(i).fromGoal > 0) {
                return i;
            }
        }
        return -1;
    }

    // What changed in the Skilling Helper since that plan was made from its goal ("Goal: level 90 -> 99"), or null
    private String goalChanges(SkillPlans.Plan plan, int goal, String pick) {
        List<String> out = new ArrayList<>();
        if (plan.fromGoal != goal) {
            out.add("Goal: " + goalText(plan.fromGoal) + " -> " + goalText(goal));
        }
        if (!java.util.Objects.equals(plan.fromPick == null ? null : plan.fromPick.toLowerCase(Locale.ROOT),
            pick == null ? null : pick.toLowerCase(Locale.ROOT))) {
            out.add("Method: " + (plan.fromPick == null ? "its suggestions" : plan.fromPick) + " -> "
                + (pick == null ? "its suggestions" : pick));
        }
        return out.isEmpty() ? null : String.join("\n", out);
    }

    // Rebuilds that plan from the goal as it's set now (its name and supply milestones kept), after saying what changes
    private void updateFromGoal(Skill shown, int index, int goal, String pick) {
        SkillPlans.Plan old = calc.plans(shown).get(index);
        String changes = goalChanges(old, goal, pick);
        if (changes == null || !confirm("The Skilling Helper's goal changed since \"" + planName(old, index) + "\" was made:\n"
            + changes + "\n\nRebuild its milestones from it? (Its name and supply milestones stay; change any method after.)")) {
            return;
        }
        SkillPlans.Plan plan = goalPlan(shown, goal, pick);
        plan.name = old.name;
        for (SkillPlans.Step step : old.steps) {
            if (!step.supplies.isEmpty()) {
                PlannerCalculator.insertStep(plan, step.copy());
            }
        }
        calc.replacePlan(shown, index, plan);
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
        boolean now = !step.gotAll && !step.covered && step.covers(level);
        // Ticked off once you're past its end; on the way, how far through it you are
        int percent = (int) Math.floor(s.progress * 100);
        boolean started = !s.done && s.progress > 0;
        // One thing a line: how many to go (and how far through) and the XP; the time and gp per XP; what the
        // materials cost and what the products sell for; the net
        long supplyItems = step.supplies.isEmpty() ? 0 : suppliesToGet(step);
        String left = !step.supplies.isEmpty() && supplyItems > 0
            ? count(supplyItems) + " to get"
            : step.gotAll ? "Done: have all the supplies"
            : step.covered ? "Done by the supplies before it"
            : s.done ? "Done (you're level " + level + ")"
            : row != null ? String.format("%,d", row.needed) + " to go" + (started ? " · " + percent + "%" : "")
            : (started ? percent + "% done" : "");
        PlannerUi.Card card = new PlannerUi.Card(step.action, left, s.done ? "" : PlannerPanel.gp(s.xp) + " xp");
        // (a supply milestone's end is where its supplies take you: about)
        card.corner.setText(step.gotAll ? "no levels" : step.covered ? "covered" : step.from + " → " + (step.supplies.isEmpty() ? "" : "~") + step.to);
        card.corner.setForeground(now ? Color.WHITE : PlannerUi.MUTED);
        card.right.setForeground(Color.WHITE);
        if (row != null && !s.done) {
            Double perXp = calc.showPrices() ? row.perXp() : null;
            // (the rate's source word only when there's room: with a gp/xp beside it, the tooltip says it)
            String source = s.yourRate ? " yours" : row.estimated ? " est." : "";
            if (perXp != null) {
                source = "";
            }
            String time = row.seconds > 0 ? "~" + PlannerPanel.formatDuration(row.seconds)
                + (row.xpHour > 0 ? " (" + PlannerPanel.gp(row.xpHour) + "/hr" + source + ")" : "")
                : "no XP rate";
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
        if (step.note != null) {
            JLabel[] noteLine = card.line(shortNote(step.note), "");
            noteLine[0].setToolTipText(PlannerUi.fit(step.note));
        }
        if (row != null && row.dropped && !s.done) {
            JLabel[] drop = card.line("Dropped (powerlevel)", row.droppedFaster ? "+20% rate" : "");
            drop[0].setForeground(PlannerUi.MUTED);
            drop[0].setToolTipText(PlannerUi.fit("Its products count as dropped (worth nothing)" + (row.droppedFaster
                ? ", and its estimated rate is about 20% quicker: no bank trips" : "") + ". Right-click > Sell by to sell or alch "
                + "them instead"));
        }
        // The other way pays more for what it makes (high alch over the GE, or the other way round): say so; the
        // milestone's Sell by picks
        if (!s.done && row != null && calc.showPrices() && row.geEach != null && row.alchEach != null && row.needed > 0) {
            boolean alchBetter = row.alchEach > row.geEach;
            if (alchBetter != row.alchWins && !row.dropped) {
                long more = Math.round(Math.abs(row.alchEach - row.geEach) * Math.max(0, row.needed - row.kept));
                JLabel[] better = card.line((alchBetter ? "Alch" : "GE") + " pays +" + PlannerPanel.gp(more), "");
                better[0].setForeground(HudColors.WARN);
                better[1].setForeground(HudColors.WARN);
                better[0].setToolTipText(PlannerUi.fit("<html>" + (alchBetter ? "High alching" : "Selling on the GE") + " gets "
                    + String.format("%,.0f", Math.max(row.alchEach, row.geEach)) + " each against "
                    + String.format("%,.0f", Math.min(row.alchEach, row.geEach)) + "<br>Right-click the milestone > Sell by to "
                    + "switch</html>"));
            }
        }
        if (!s.done && !step.supplies.isEmpty() && s.action != null && s.action.level > level) {
            JLabel[] needs = card.line("Needs lvl " + s.action.level + " (you're " + level + ")", "");
            needs[0].setForeground(HudColors.WARN);
        }
        // Its materials got from another skill that isn't at their level yet: it waits on that skill
        if (!s.done && row != null) {
            for (PlannerCalculator.Need need : PlannerCalculator.planMaterials(shown, java.util.Collections.singletonList(s),
                recipes, calc.owned())) {
                String waiting = waitingOn(shown, need);
                if (waiting != null) {
                    JLabel[] wait = card.line("Needs " + waiting, "");
                    wait[0].setForeground(HudColors.WARN);
                    wait[0].setToolTipText(PlannerUi.fit(PlannerUi.esc(need.name) + " comes from " + waiting + ": train that first"));
                    break;
                }
            }
        }
        // Its supplies go by what the other plan still needs, not by its levels: train the levels another way (willows
        // instead of teaks) and the supplies are still to get; train the other skill on and it needs fewer
        boolean suppliesLeft = false;
        for (SkillPlans.Supply supply : step.supplies) {
            long[] still = stillNeeded(supply.skill, supply.item);
            long wanted = still == null ? supply.quantity : still[0];
            long have = still == null ? 0 : still[1];
            long toGet = Math.max(0, wanted - have);
            long levelsMake = row == null || s.done ? 0 : row.needed;
            String consumer = skillName(supply.skill);
            JLabel[] kept;
            String tip;
            if (wanted <= 0) {
                kept = card.line(consumer + " needs none now", "");
                kept[0].setForeground(PlannerUi.MUTED);
                tip = "Your " + consumer + " plan no longer needs " + supply.item + " (it's past them, or uses something "
                    + "else): right-click > Stop keeping to sell what this makes";
            } else if (toGet == 0) {
                kept = card.line("Have all " + consumer + " needs", "");
                kept[0].setForeground(PlannerUi.READY);
                tip = "You have the " + supply.item + " your " + consumer + " plan still needs";
            } else {
                suppliesLeft = s.done;
                kept = card.line(consumer + ": " + count(have) + " / " + count(wanted), "");
                kept[0].setForeground(GATHER_BLUE);
                tip = "Your " + consumer + " plan still needs " + String.format("%,d", wanted) + " " + supply.item + " and you "
                    + "have " + String.format("%,d", have) + ": this milestone gets the other " + String.format("%,d", toGet)
                    + ", and ends wherever that takes your level (about " + step.to + "). They're kept, left out of its "
                    + "sales and net";
            }
            kept[0].setToolTipText(PlannerUi.fit(tip));
        }
        if (s.action != null) {
            picture(card.icon, s.action);
        } else {
            image(card.icon, icons.skill(shown, false));
        }
        if (s.done && suppliesLeft) {
            // (its levels are done, its supplies aren't: not ticked off)
            card.left.setText("Levels done, supplies to get");
            card.left.setForeground(HudColors.WARN);
            card.setBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, HudColors.WARN));
        } else if (s.done) {
            card.dim();
            card.left.setForeground(PlannerUi.READY);
            card.bar(new PlannerUi.Bar(1, PlannerUi.READY));
        } else {
            // (the milestones' green down the left: brighter and wider for the one you're on)
            card.setBorder(now ? BorderFactory.createMatteBorder(0, 4, 0, 0, SUGGESTED)
                : BorderFactory.createMatteBorder(0, 3, 0, 0, MILESTONES.darker()));
            // (a supply milestone fills by how many you have of what the other plans want, not by levels)
            double supplied = supplyProgress(step);
            if (supplied >= 0) {
                card.bar(new PlannerUi.Bar(Math.max(0.02, supplied), PlannerUi.GROWING));
            } else if (started) {
                card.bar(new PlannerUi.Bar(Math.max(0.02, s.progress), PlannerUi.GROWING));
            }
        }
        JPopupMenu menu = new JPopupMenu();
        menu.add(menuItem("Edit...", () -> stepDialog(shown, index, plan.goal)));
        if (!s.done && step.supplies.isEmpty()) {
            int insertFrom = Math.max(step.from, Math.min(step.to - 1, level));
            menu.add(menuItem("Insert a milestone in it...", () -> stepDialog(shown, -1, plan.goal, null, insertFrom, step.to)));
        }
        // (a supply milestone's method and XP are fixed: it can move, not change)
        boolean fixed = !step.supplies.isEmpty();
        if (!fixed) {
            menu.add(menuItem("Find a better method...", () -> {
                picking = index;
                search.setText("");
                redraw.run();
            }));
        }
        // (moving swaps two milestones' methods over their levels: not with a supply's, whose work is fixed; Edit moves it)
        if (index > 0 && !fixed && plan.steps.get(index - 1).supplies.isEmpty()) {
            menu.add(menuItem("Move up", () -> change(shown, p -> PlannerCalculator.swapSteps(p, index, index - 1))));
        }
        if (index < plan.steps.size() - 1 && !fixed && plan.steps.get(index + 1).supplies.isEmpty()) {
            menu.add(menuItem("Move down", () -> change(shown, p -> PlannerCalculator.swapSteps(p, index, index + 1))));
        }
        // The pace its time is worked out at: the wiki guide's rate, a normal pace, or a faster way (1-tick, 3-tick...)
        if (s.action != null && !s.done) {
            CalculatorData.Calc skillCalc = data.calc(shown);
            Map<String, Integer> paces = PlannerCalculator.paces(shown, s.action, CalculatorData.bonuses(skillCalc,
                calc.bonuses(shown)), Math.max(step.from, level), recipes, training);
            if (paces.size() > 1) {
                javax.swing.JMenu pace = new javax.swing.JMenu("Pace");
                String using = row != null ? row.pace : null;
                for (Map.Entry<String, Integer> entry : paces.entrySet()) {
                    String name = entry.getKey();
                    javax.swing.JCheckBoxMenuItem item = new javax.swing.JCheckBoxMenuItem(name + ": "
                        + PlannerPanel.gp(entry.getValue()) + " xp/hr" + (PlannerRates.GUIDE.equals(name) ? "" : " est."),
                        name.equals(using));
                    item.addActionListener(e -> change(shown, p -> {
                        if (index < p.steps.size()) {
                            // (the default, the guide's rate where there is one, else the normal pace, isn't saved)
                            String first = paces.keySet().iterator().next();
                            p.steps.get(index).pace = name.equals(first) ? null : name;
                        }
                    }));
                    pace.add(item);
                }
                menu.add(pace);
            }
        }
        if (row != null && calc.showPrices() && (row.geEach != null || row.alchEach != null)) {
            // How its products count: on the GE, high alched, or the setting's way (the better one marked)
            if (!s.done) {
                javax.swing.JMenu sell = new javax.swing.JMenu("Sell by");
                double geNow = row.geEach == null ? 0 : row.geEach;
                double alchNow = row.alchEach == null ? 0 : row.alchEach;
                boolean alchBetter = alchNow > geNow;
                String[][] ways = {
                    {null, "The setting (" + calc.sellBy() + ")"},
                    {"GE", "Grand Exchange: " + String.format("%,.0f", geNow) + " each" + (alchBetter ? "" : "  (pays more)")},
                    {"ALCH", "High alch: " + String.format("%,.0f", alchNow) + " each" + (alchBetter ? "  (pays more)" : "")},
                    {"DROP", "Drop them (powerlevel): nothing"},
                };
                for (String[] way : ways) {
                    javax.swing.JCheckBoxMenuItem item = new javax.swing.JCheckBoxMenuItem(way[1],
                        java.util.Objects.equals(way[0], step.sellBy));
                    item.addActionListener(e -> change(shown, p -> {
                        if (index < p.steps.size()) {
                            p.steps.get(index).sellBy = way[0];
                        }
                    }));
                    sell.add(item);
                }
                menu.add(sell);
            }
            menu.add(menuItem("Record sales...", () -> recordSales(shown, index, s)));
        }
        menu.add(menuItem("Delete", () -> change(shown, p -> PlannerCalculator.removeStep(p, index))));
        // Supplies it carries for other plans: each can be dropped (they're sold again)
        if (!step.supplies.isEmpty()) {
            menu.addSeparator();
            for (SkillPlans.Supply supply : new ArrayList<>(step.supplies)) {
                menu.add(menuItem("Stop keeping " + supply.item + " for " + skillName(supply.skill), () -> change(shown, p -> {
                    if (index < p.steps.size()) {
                        p.steps.get(index).supplies.removeIf(x -> x.skill.equals(supply.skill) && x.item.equalsIgnoreCase(supply.item));
                    }
                })));
            }
        }
        // Its materials: get each yourself (gathered or made in another skill) or buy it
        if (row != null && !s.done) {
            boolean first = true;
            for (PlannerCalculator.Need need : PlannerCalculator.planMaterials(shown, java.util.Collections.singletonList(s),
                recipes, calc.owned())) {
                PlannerCalculator.Source source = need.tool ? null : PlannerCalculator.sourceOf(need.name, shown, data, recipes);
                if (source == null) {
                    continue;
                }
                if (first) {
                    menu.addSeparator();
                    first = false;
                }
                boolean gather = calc.gathers(shown, need.name);
                menu.add(menuItem(gather ? "Buy " + need.name + " instead"
                    : (source.gathered ? "Gather " : "Make ") + need.name + " yourself (" + source.skill.getName() + ")", () -> {
                        calc.setGathers(shown, need.name, !gather);
                        redraw.run();
                    }));
            }
        }
        PlannerUi.Tip tip = new PlannerUi.Tip(step.action, "level " + step.from + " \u2192 " + step.to + (s.done ? " (done)" : ""));
        if (!s.done && row != null) {
            tip.section("Progress");
            if (started) {
                tip.row("Done", percent + "%  (" + String.format("%,d", s.made()) + " of " + String.format("%,d", s.total) + ")");
            }
            tip.row("To go", String.format("%,d", row.needed) + " actions");
            tip.row("XP", String.format("%,d", s.xp) + " at " + xp(row.xpEach) + " each");
            if (row.seconds > 0) {
                tip.row("Time", "~" + PlannerPanel.formatDuration(row.seconds) + (row.estimated ? " (est.)" : ""));
                tip.row("Rate", String.format("%,d", row.xpHour) + " xp/hr", row.estimated ? PlannerUi.Tip.WARN : null);
                tip.row("Rate from", s.yourRate ? "your XP/hr now (Skilling Helper)" : row.estimated ? (row.pace != null && !PlannerRates.NORMAL.equals(row.pace)
                    ? row.pace + " pace (estimate)" : "a typical pace (estimate)") : "the wiki's training guide",
                    PlannerUi.Tip.MUTED);
            }
            if (s.tooLow()) {
                tip.line("Needs level " + s.action.level + " to start", PlannerUi.Tip.BAD);
            }
            if (calc.showPrices()) {
                Double cost = row.cost(row.needed);
                Double earn = row.earn(row.needed);
                if (cost != null || earn != null) {
                    tip.section("Money to go");
                    if (cost != null && cost > 0) {
                        tip.row("Materials", PlannerPanel.gp(Math.round(cost)), PlannerUi.Tip.BAD);
                    }
                    if (earn != null) {
                        tip.row(row.alchWins ? "High alched" : "Sells for", PlannerPanel.gp(Math.round(earn)), PlannerUi.Tip.GOOD);
                    }
                    double net = (earn == null ? 0 : earn) - (cost == null ? 0 : cost);
                    tip.row("Net", signed(net), net < 0 ? PlannerUi.Tip.BAD : PlannerUi.Tip.GOOD);
                }
                SoFar done = soFar(s);
                if (done != null) {
                    tip.section("So far (seen, or recorded)");
                    tip.row("Made", String.format("%,d", done.made));
                    tip.row("Sold on the GE", String.format("%,d", s.step.sold) + "  " + PlannerPanel.gp(Math.round(done.sold)),
                        PlannerUi.Tip.GOOD);
                    tip.row("High alched", String.format("%,d", s.step.alched) + "  " + PlannerPanel.gp(Math.round(done.alched)),
                        PlannerUi.Tip.GOOD);
                    if (s.step.dropped > 0) {
                        tip.row("Dropped", String.format("%,d", s.step.dropped) + "  0", PlannerUi.Tip.MUTED);
                    }
                    if (done.left > 0) {
                        tip.row("Not sold yet", String.format("%,d", done.left) + "  worth " + PlannerPanel.gp(Math.round(done.unsold)),
                            PlannerUi.Tip.MUTED);
                    }
                    tip.row("Materials used", PlannerPanel.gp(Math.round(done.cost)), PlannerUi.Tip.BAD);
                    tip.row("Cash so far", signed(done.cash()), done.cash() < 0 ? PlannerUi.Tip.BAD : PlannerUi.Tip.GOOD);
                    tip.row("Net with unsold", signed(done.net()), done.net() < 0 ? PlannerUi.Tip.BAD : PlannerUi.Tip.GOOD);
                }
            }
        }
        // Where: the best places for it (the guides' picks with why, then the most of it), what you need for them
        // (the ones you can reach first; one that needs a quest you haven't done says which)
        List<LocationData.Place> places = locations == null ? java.util.Collections.emptyList()
            : LocationData.reachableFirst(locations.forAction(shown.getName(), step.action), quests);
        if (!places.isEmpty() && !s.done) {
            tip.section("Where");
            for (LocationData.Place place : places.subList(0, Math.min(3, places.size()))) {
                List<String> missing = place.missing(quests);
                if (!missing.isEmpty()) {
                    tip.line(place.label() + ": needs " + String.join(", ", missing), PlannerUi.Tip.BAD);
                    continue;
                }
                String why = place.best != null ? place.best : place.note;
                tip.line(place.label() + (why != null ? ": " + why : ""), place.best != null ? null : PlannerUi.Tip.MUTED);
            }
            if (places.size() > 3) {
                tip.line("+" + (places.size() - 3) + " more places", PlannerUi.Tip.MUTED);
            }
        }
        if (step.method != null) {
            tip.line("Guide method: " + step.method, PlannerUi.Tip.MUTED);
        }
        tip.hint("Click: breakdown \u00b7 right-click: more");
        String open = stepKey(shown, step);
        return card.interactive(() -> {
            if (!openSteps.remove(open)) {
                openSteps.add(open);
            }
            redraw.run();
        }, menu, tip.build());
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
        int index = calc.openIndex(shown);
        // The plan you sent, changed since: a draft until you send it (or undo back to what the tracker has)
        boolean draft = plan.sent && any && !same;
        JLabel status = PlannerUi.label(same ? "In the tracker" : draft ? "Changes not sent" : any ? "Tracker has a different plan"
            : "Not in the tracker", same ? PlannerUi.READY : draft ? HudColors.WARN : any ? PlannerUi.GROWING : PlannerUi.MUTED);
        status.setMinimumSize(new Dimension(0, 0));
        row.add(status, BorderLayout.CENTER);
        JPanel links = new JPanel();
        links.setLayout(new BoxLayout(links, BoxLayout.X_AXIS));
        links.setOpaque(false);
        Runnable send = () -> {
            int game = calc.xp(shown);
            calc.export(shown, index, withXp, game >= 0 ? game : current());
            redraw.run();
        };
        if (draft) {
            links.add(PlannerUi.linkLabel("Send changes", "Give the Skilling Helper this plan as it is now (its goal card "
                + "follows it; the goal becomes level " + plan.goal + ")", send));
            links.add(PlannerUi.label("  ·  ", PlannerUi.FAINT));
            links.add(PlannerUi.linkLabel("Undo", "Put this plan back as the tracker has it (your changes since you sent "
                + "it go)", () -> {
                    if (confirm("Undo your changes to \"" + planName(plan, index) + "\" since you sent it?")) {
                        calc.undoChanges(shown, index);
                        redraw.run();
                    }
                }));
        } else if (!same) {
            links.add(PlannerUi.linkLabel(any ? "Send" : "Export to tracker", "Set level " + plan.goal + " as the "
                + "skill's goal and give the Skilling Helper these milestones: its goal card shows the one you're on, with "
                + "its own bar, and suggests and outlines its method" + (any ? " (replaces the plan it has)" : ""), send));
            if (any) {
                // The other way round: this plan becomes what the tracker has
                links.add(PlannerUi.label("  ·  ", PlannerUi.FAINT));
                links.add(PlannerUi.linkLabel("Take", "Replace this plan's milestones with the tracker's (the plan your goal "
                    + "follows); its name stays", () -> {
                        if (confirm("Replace \"" + planName(plan, index) + "\" with the plan the tracker has (the one your goal "
                            + "follows)? Its milestones here go.")) {
                            calc.takeTracked(shown, index);
                            redraw.run();
                        }
                    }));
            }
        }
        if (any && !draft) {
            if (!same) {
                links.add(PlannerUi.label("  ·  ", PlannerUi.FAINT));
            }
            links.add(PlannerUi.linkLabel("Remove", "Take the plan out of the tracker (the goal stays)", () -> {
                calc.untrack(shown);
                redraw.run();
            }));
        }
        row.add(links, BorderLayout.EAST);
        PlannerUi.tips(row, "<html>Snuffy's Skilling Helper: " + (same ? "it follows this plan" : draft
            ? "it follows this plan as you sent it: your changes since are a draft until you send them" : any
            ? "it follows another plan (or an older copy of this one): Send makes it follow this plan; Take makes this plan "
            + "what it has" : "it doesn't "
            + "have this plan yet") + "<br>Edits stay here until you send them, so you can try things without changing "
            + "your goal</html>");
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

    // Suggest a plan: each aim's plan worked out and compared (time, gp, milestones), then the one picked fills the plan
    // (its name and supply milestones kept). An ironman gets the ones that don't go by prices
    private void suggest(Skill shown, List<CalculatorData.Action> actions, List<CalculatorData.Bonus> on, int goal) {
        SkillPlans.Plan old = calc.plan(shown);
        int level = levelNow();
        int to = Math.max(goal, level + 1);
        int xp = planXp();
        CalculatorData.Calc skillCalc = data.calc(shown);
        boolean prices = calc.showPrices() && !calc.ironman();
        List<PlannerCalculator.PlanAim> aims = new ArrayList<>();
        for (PlannerCalculator.PlanAim aim : PlannerCalculator.PlanAim.values()) {
            if (prices || !aim.priced()) {
                aims.add(aim);
            }
        }
        // Yours that aren't supplies (those always stay): with any, the suggestions can fill around them instead
        boolean mine = old != null && old.steps.stream().anyMatch(st -> st.supplies.isEmpty());
        JPanel form = new JPanel(new GridLayout(0, 1, 2, 6));
        JLabel head = new JLabel();
        form.add(head);
        javax.swing.JCheckBox keep = new javax.swing.JCheckBox("<html>Keep my milestones, suggest only the levels they "
            + "leave open<br><font color='#a0a0a0'>Before, between and after yours; untick to replace them</font></html>", mine);
        if (mine) {
            form.add(keep);
        }
        javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();
        // Each aim's two plans: [0] replacing yours (supply milestones kept), [1] filling around yours
        Map<javax.swing.JRadioButton, SkillPlans.Plan[]> picks = new java.util.LinkedHashMap<>();
        Map<javax.swing.JRadioButton, String[]> texts = new java.util.HashMap<>();
        for (PlannerCalculator.PlanAim aim : aims) {
            SkillPlans.Plan suggested = PlannerCalculator.aimedPlan(shown, actions, on, training, recipes, level, to, aim,
                calc.hourWorth(), this::price, calc::itemPrice);
            SkillPlans.Plan replace = suggested;
            if (old != null) {
                // (its supply milestones stay as they are: the suggestions make room for them)
                for (SkillPlans.Step step : old.steps) {
                    if (!step.supplies.isEmpty()) {
                        PlannerCalculator.insertStep(replace, step.copy());
                    }
                }
            }
            SkillPlans.Plan fill = mine ? PlannerCalculator.keepAndFill(suggested, old) : replace;
            SkillPlans.Plan[] both = {replace, fill};
            String[] about = new String[2];
            for (int i = 0; i < 2; i++) {
                SkillPlans.Plan plan = both[i];
                List<PlannerCalculator.StepRow> rows = PlannerCalculator.stepRows(shown, skillCalc, on, plan, xp, recipes, training,
                    this::price, calc::itemPrice);
                PlannerCalculator.Totals totals = PlannerCalculator.totals(plan, rows, xp);
                StringBuilder text = new StringBuilder("<html><b>").append(aim.label).append("</b>: ");
                text.append(totals.seconds > 0 ? duration(totals.seconds, totals.estimated > 0) + (totals.untimed > 0
                    ? " (+" + totals.untimed + " without a rate)" : "") : "no XP rates");
                if (prices && (totals.cost > 0 || totals.earn > 0)) {
                    text.append(" &middot; ").append(signed(totals.earn - totals.cost)).append(" gp");
                }
                if (i == 1) {
                    int added = PlannerCalculator.added(plan, old);
                    text.append(" &middot; adds ").append(added).append(added == 1 ? " milestone" : " milestones");
                } else {
                    text.append(" &middot; ").append(plan.steps.size()).append(plan.steps.size() == 1 ? " milestone" : " milestones");
                }
                text.append("<br><font color='#a0a0a0'>").append(aim.about)
                    .append(aim == PlannerCalculator.PlanAim.BALANCED ? ": " + PlannerPanel.gp(calc.hourWorth()) + " an hour (in the "
                        + "settings)" : "").append("</font></html>");
                about[i] = text.toString();
            }
            javax.swing.JRadioButton choice = new javax.swing.JRadioButton();
            group.add(choice);
            picks.put(choice, both);
            texts.put(choice, about);
            form.add(choice);
        }
        String planLabel = old != null && !old.steps.isEmpty() ? PlannerUi.esc(planName(old, calc.openIndex(shown))) : null;
        Runnable show = () -> {
            int which = keep.isSelected() && mine ? 1 : 0;
            head.setText("<html><b>Suggest a " + shown.getName() + " plan</b>, level " + level + " to " + to
                + (planLabel == null ? "" : which == 1 ? "<br><font color='#a0a0a0'>Added to \"" + planLabel + "\" around its "
                + "milestones</font>" : "<br><font color='#a0a0a0'>It replaces the milestones of \"" + planLabel
                + "\" (its supply milestones stay)</font>") + "</html>");
            javax.swing.JRadioButton first = null;
            for (Map.Entry<javax.swing.JRadioButton, SkillPlans.Plan[]> entry : picks.entrySet()) {
                javax.swing.JRadioButton choice = entry.getKey();
                choice.setText(texts.get(choice)[which]);
                SkillPlans.Plan plan = entry.getValue()[which];
                choice.setEnabled(which == 1 ? PlannerCalculator.added(plan, old) > 0 : !plan.steps.isEmpty());
                if (first == null && choice.isEnabled()) {
                    first = choice;
                }
            }
            javax.swing.JRadioButton picked = picks.keySet().stream().filter(javax.swing.AbstractButton::isSelected).findFirst()
                .orElse(null);
            if ((picked == null || !picked.isEnabled()) && first != null) {
                first.setSelected(true);
            }
        };
        keep.addActionListener(e -> show.run());
        show.run();
        if (picks.keySet().stream().noneMatch(java.awt.Component::isEnabled) && !mine) {
            JOptionPane.showMessageDialog(list, "Nothing to suggest for " + shown.getName() + " here: add milestones from the "
                + "actions instead.", "Suggest a plan", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (JOptionPane.showConfirmDialog(list, form, "Suggest a plan", JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        int which = keep.isSelected() && mine ? 1 : 0;
        SkillPlans.Plan plan = null;
        for (Map.Entry<javax.swing.JRadioButton, SkillPlans.Plan[]> entry : picks.entrySet()) {
            if (entry.getKey().isSelected() && entry.getKey().isEnabled()) {
                plan = entry.getValue()[which];
            }
        }
        if (plan == null) {
            return;
        }
        plan.name = old != null ? old.name : "Suggested";
        if (old != null) {
            plan.fromGoal = old.fromGoal;
            plan.fromPick = old.fromPick;
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
        // A supply milestone: its method and XP are fixed (it can start elsewhere; its end follows)
        boolean fixed = editing != null && !editing.supplies.isEmpty();
        long fixedXp = fixed ? Experience.getXpForLevel(Math.min(99, editing.to)) - Experience.getXpForLevel(editing.from) : 0;
        if (fixed) {
            method.setEnabled(false);
            to.setEnabled(false);
            form.add(new JLabel("<html><font color='#a0a0a0'>It's a supply for another plan: its method and XP stay;<br>"
                + "change where it starts and its end follows</font></html>"));
        }
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
        if (fixed && start >= 1 && start < 99) {
            end = Math.min(99, CalculatorData.level((int) Math.min(Experience.MAX_SKILL_XP,
                Experience.getXpForLevel(start) + fixedXp), false));
            end = Math.max(end, start + 1);
        }
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
