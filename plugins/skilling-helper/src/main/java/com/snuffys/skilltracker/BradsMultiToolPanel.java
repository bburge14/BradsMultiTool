package com.snuffys.skilltracker;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

public class BradsMultiToolPanel extends PluginPanel {

    // New issue on GitHub with a short template filled in
    private static final String NEW_ISSUE_URL = "https://github.com/bburge14/bradsmultitool/issues/new?body="
        + encode("**What happened?**\n\n\n**What did you expect?**\n\n\n**What were you doing / where in game?**\n\n");

    private static final Set<Skill> COMBAT_SKILLS = GoalLines.KILL_SKILLS;

    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance();
    private static final Color ACCENT_COLOR = new Color(0, 220, 220);
    // Level progress bar: red under a third of the way, orange under two thirds, then green
    static final Color BAR_LOW = new Color(190, 45, 40);
    static final Color BAR_MID = new Color(225, 135, 25);
    static final Color BAR_HIGH = new Color(15, 165, 105);
    static final Color BAR_BACKGROUND = new Color(60, 60, 60);
    static final Color GOAL_FILL = new Color(200, 160, 40);

    private final SkillTracker plugin;
    private final SkillTrackerConfig config;
    private final SkillIconManager skillIconManager;

    private final JLabel totalXpLabel = new JLabel();
    private final JLabel totalRateLabel = new JLabel();
    private final JLabel skillCountLabel = new JLabel();
    private final JLabel emptyLabel = new JLabel("<html>Gain XP to start tracking skills.<br>Right-click a skill for options.</html>");
    private final JPanel cardsContainer = new JPanel();
    private final JLabel goalsHeading = sectionHeading("GOALS");
    private final JLabel sessionHeading = sectionHeading("THIS SESSION");
    // What's laid out now (goal skills, then session skills), so the list is only rebuilt when it changes
    private String cardOrder = "";
    private final Map<Skill, SkillCard> cards = new EnumMap<>(Skill.class);

    public BradsMultiToolPanel(SkillTracker plugin, SkillTrackerConfig config, SkillIconManager skillIconManager) {
        super();
        this.plugin = plugin;
        this.config = config;
        this.skillIconManager = skillIconManager;

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel header = buildHeader(totalXpLabel, totalRateLabel, skillCountLabel,
            () -> askForGoal(null), () -> LinkBrowser.browse(NEW_ISSUE_URL));

        cardsContainer.setLayout(new BoxLayout(cardsContainer, BoxLayout.Y_AXIS));
        cardsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        cardsContainer.setBorder(new EmptyBorder(10, 0, 0, 0));

        emptyLabel.setFont(FontManager.getRunescapeSmallFont());
        emptyLabel.setForeground(Color.GRAY);
        cardsContainer.add(emptyLabel);

        // Pin cards to the top so they keep their natural height instead of stretching to fill the sidebar
        JPanel cardsWrapper = new JPanel(new BorderLayout());
        cardsWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        cardsWrapper.add(cardsContainer, BorderLayout.NORTH);

        add(header, BorderLayout.NORTH);
        add(cardsWrapper, BorderLayout.CENTER);

        updatePanel();
    }

    /**
     * The header box: title and subtitle on the left with a small "Issues?" button centred beside them,
     * three equal stat columns (caption centred over value), and the Set Goal button across the bottom.
     */
    static JPanel buildHeader(JLabel totalXp, JLabel rate, JLabel skills, Runnable onSetGoal, Runnable onIssues) {
        JPanel header = new JPanel(new BorderLayout(0, 8));
        header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(2, 0, 0, 0, ACCENT_COLOR),
            new EmptyBorder(8, 8, 8, 8)));

        // "Snuffy's" on its own small line, so the plugin's name fits beside the Issues? button
        JPanel titles = new JPanel(new GridLayout(3, 1));
        titles.setOpaque(false);
        JLabel brand = new JLabel("Snuffy's");
        brand.setFont(FontManager.getRunescapeSmallFont());
        brand.setForeground(Color.LIGHT_GRAY);
        JLabel title = new JLabel("Skilling Helper");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(ACCENT_COLOR);
        JLabel subtitle = new JLabel("XP, goals and training");
        subtitle.setFont(FontManager.getRunescapeSmallFont());
        subtitle.setForeground(Color.GRAY);
        titles.add(brand);
        titles.add(title);
        titles.add(subtitle);

        JButton issues = new JButton("Issues?");
        issues.setToolTipText("Report a problem or idea: opens a new issue on the plugin's GitHub page");
        issues.setFont(FontManager.getRunescapeSmallFont());
        issues.setFocusPainted(false);
        issues.setMargin(new java.awt.Insets(2, 6, 2, 6));
        issues.addActionListener(e -> onIssues.run());
        // Centred vertically against the two title lines
        JPanel corner = new JPanel(new java.awt.GridBagLayout());
        corner.setOpaque(false);
        corner.add(issues);

        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.setOpaque(false);
        top.add(titles, BorderLayout.CENTER);
        top.add(corner, BorderLayout.EAST);
        header.add(top, BorderLayout.NORTH);

        JPanel stats = new JPanel(new GridLayout(1, 3, 4, 0));
        stats.setOpaque(false);
        stats.add(statBlock("TOTAL XP", totalXp));
        stats.add(statBlock("XP / HR", rate));
        stats.add(statBlock("SKILLS", skills));
        header.add(stats, BorderLayout.CENTER);

        JButton goalButton = new JButton("Set Goal");
        goalButton.setToolTipText("Pick a skill and a target level or XP; it's pinned under Goals");
        goalButton.setFocusPainted(false);
        goalButton.addActionListener(e -> onSetGoal.run());
        header.add(goalButton, BorderLayout.SOUTH);
        return header;
    }

    private static String encode(String text) {
        try {
            return URLEncoder.encode(text, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    /**
     * Must be called on the Swing EDT.
     */
    public void updatePanel() {
        List<Skill> tracked = plugin.getTrackedSkills();
        int totalRate = 0;
        for (Skill skill : tracked) {
            if (!plugin.isSkillPaused(skill)) {
                totalRate += plugin.getSkillXpPerHour(skill);
            }
        }
        totalXpLabel.setText(NUMBER_FORMAT.format(plugin.getTotalCombatXp()));
        totalRateLabel.setText(NUMBER_FORMAT.format(totalRate));
        skillCountLabel.setText(String.valueOf(tracked.size()));

        // Skills with a goal are pinned under Goals (even if not trained this session); the rest follow
        List<Skill> goals = plugin.getGoalSkills();
        List<Skill> session = new ArrayList<>();
        for (Skill skill : tracked) {
            if (!goals.contains(skill)) {
                session.add(skill);
            }
        }
        Set<Skill> shown = new HashSet<>(goals);
        shown.addAll(session);
        cards.keySet().removeIf(skill -> !shown.contains(skill));
        for (Skill skill : shown) {
            cards.computeIfAbsent(skill, SkillCard::new).refresh();
        }

        String order = goals + "|" + session;
        if (!order.equals(cardOrder)) {
            cardOrder = order;
            cardsContainer.removeAll();
            if (shown.isEmpty()) {
                cardsContainer.add(emptyLabel);
            }
            if (!goals.isEmpty()) {
                cardsContainer.add(goalsHeading);
                goals.forEach(skill -> cardsContainer.add(cards.get(skill)));
                if (!session.isEmpty()) {
                    cardsContainer.add(sessionHeading);
                }
            }
            session.forEach(skill -> cardsContainer.add(cards.get(skill)));
            cardsContainer.revalidate();
            cardsContainer.repaint();
        }
    }

    private static JLabel sectionHeading(String text) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(Color.GRAY);
        label.setBorder(new EmptyBorder(2, 0, 4, 0));
        // Full width and aligned like the cards, so the text sits in the middle of the panel
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height));
        return label;
    }

    /**
     * Set a goal: a level (2-99) or an XP amount. With no skill given, asks which skill too.
     */
    // A small red push pin (drawn here rather than shipped as an image)
    private static final BufferedImage PIN_IMAGE = drawPin();

    private static BufferedImage drawPin() {
        BufferedImage image = new BufferedImage(12, 12, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(200, 200, 200));
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(5, 7, 1, 11);
        g.setColor(new Color(220, 50, 50));
        g.fillOval(4, 0, 8, 8);
        g.setColor(new Color(255, 140, 140));
        g.fillOval(6, 2, 3, 3);
        g.dispose();
        return image;
    }

    // The "follow the settings" choice in the Suggest pickers
    private static final String DEFAULT_FOCUS = "Default (from settings)";

    private void askForGoal(Skill skill) {
        JComboBox<Skill> skills = new JComboBox<>();
        for (Skill s : Skill.values()) {
            if (!"OVERALL".equals(s.name())) {
                skills.addItem(s);
            }
        }
        skills.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                // "Woodcutting (45)": your level beside each skill
                Object text = value instanceof Skill
                    ? ((Skill) value).getName() + " (" + Experience.getLevelForXp(plugin.getCurrentSkillXp((Skill) value)) + ")"
                    : value;
                return super.getListCellRendererComponent(list, text, index, selected, focus);
            }
        });
        JTextField target = new JTextField(10);
        JLabel current = new JLabel();
        JPanel form = new JPanel(new GridLayout(0, 1, 0, 4));
        if (skill == null) {
            form.add(new JLabel("Skill:"));
            form.add(skills);
        } else {
            skills.setSelectedItem(skill);
        }
        // Your level and XP in the chosen skill (and its goal, if it has one), updated as you pick
        Runnable showCurrent = () -> current.setText(currentText((Skill) skills.getSelectedItem()));
        skills.addActionListener(e -> showCurrent.run());
        showCurrent.run();
        form.add(current);
        form.add(new JLabel("Goal (a level like 70, or XP like 1,000,000):"));
        form.add(target);
        // What its suggestions aim for: the skill's own focus, or the default from the settings
        JComboBox<Object> focus = new JComboBox<>();
        Runnable fillFocus = () -> {
            Skill chosen = (Skill) skills.getSelectedItem();
            focus.removeAllItems();
            focus.addItem(DEFAULT_FOCUS);
            for (SkillTrackerConfig.TrainingFocus f : SkillTrackerConfig.TrainingFocus.values()) {
                focus.addItem(f);
            }
            SkillTrackerConfig.TrainingFocus own = chosen != null ? plugin.getOwnTrainingFocus(chosen) : null;
            focus.setSelectedItem(own != null ? own : DEFAULT_FOCUS);
        };
        skills.addActionListener(e -> fillFocus.run());
        fillFocus.run();
        form.add(new JLabel("Suggest:"));
        form.add(focus);
        int result = JOptionPane.showConfirmDialog(this, form, skill == null ? "Set goal" : "Goal for " + skill.getName(),
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION || target.getText().trim().isEmpty()) {
            return;
        }
        try {
            int value = Integer.parseInt(target.getText().trim().replace(",", ""));
            int goalXp = value <= Experience.MAX_REAL_LEVEL ? Experience.getXpForLevel(Math.max(2, value)) : value;
            Object picked = focus.getSelectedItem();
            plugin.setTrainingFocus((Skill) skills.getSelectedItem(),
                picked instanceof SkillTrackerConfig.TrainingFocus ? (SkillTrackerConfig.TrainingFocus) picked : null);
            plugin.setSkillGoal((Skill) skills.getSelectedItem(), Math.min(goalXp, Experience.MAX_SKILL_XP));
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Enter a level like 70 or an XP amount like 1,000,000.");
        }
    }

    private String currentText(Skill skill) {
        if (skill == null) {
            return "";
        }
        int xp = plugin.getCurrentSkillXp(skill);
        String text = "<html>Level " + Experience.getLevelForXp(xp) + " <font color='#a0a0a0'>(" + String.format("%,d", xp) + " XP)</font>";
        int goal = plugin.getSkillGoal(skill);
        if (goal > xp) {
            int goalLevel = Experience.getLevelForXp(goal);
            text += "<br><font color='#a0a0a0'>Current goal:</font> "
                + (goal == Experience.getXpForLevel(goalLevel) ? "Level " + goalLevel : String.format("%,d XP", goal))
                + " <font color='#a0a0a0'>(" + String.format("%,d", goal - xp) + " to go)</font>";
        }
        return text + "</html>";
    }

    private static JPanel statBlock(String caption, JLabel value) {
        // Caption centred over its value, so the three columns line up evenly
        JPanel block = new JPanel(new GridLayout(2, 1));
        block.setOpaque(false);
        JLabel captionLabel = new JLabel(caption, SwingConstants.CENTER);
        value.setHorizontalAlignment(SwingConstants.CENTER);
        captionLabel.setFont(FontManager.getRunescapeSmallFont());
        captionLabel.setForeground(Color.GRAY);
        value.setFont(FontManager.getRunescapeBoldFont());
        value.setForeground(Color.WHITE);
        block.add(captionLabel);
        block.add(value);
        return block;
    }

    static String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m " + (seconds % 60) + "s";
    }

    /** 9,112 / 55.8K / 1.24M, like RuneLite's XP tracker. */
    static String shortNumber(long value) {
        if (value >= 1_000_000) {
            return String.format("%.2fM", value / 1_000_000.0);
        }
        if (value >= 10_000) {
            return String.format("%.1fK", value / 1_000.0);
        }
        return NUMBER_FORMAT.format(value);
    }

    /**
     * One skill: its icon on the left, stats in two columns beside it (only the fields switched on), the
     * level bar ("Lvl 73 | 31.90% | Lvl 74", red to green) and, if you've set one, a goal bar.
     */
    private class SkillCard extends JPanel {
        private final Skill skill;
        private final JLabel gained = stat();
        private final JLabel left = stat();
        private final JLabel perHour = stat();
        private final JLabel actions = stat();
        private final JLabel timeToLevel = stat();
        private final JLabel perMinute = stat();
        private final JPanel stats = new JPanel(new GridLayout(0, 2, 6, 2));
        private final LevelProgressBar levelBar = new LevelProgressBar();
        private final LevelProgressBar goalBar = new LevelProgressBar();
        private final JLabel suggestion = stat();
        private boolean pinned;
        // Where the step of the goal's plan you're on ends (the method changes there), or 0
        private int stepEndLevel;
        // Which fields are currently laid out, so they're only rebuilt when the settings change
        private String layout = "";
        private final JMenuItem pauseItem = new JMenuItem();
        // Back to the suggested method after picking something with "Track for goal"
        private final JMenuItem suggestedItem = new JMenuItem("Use suggested method");
        // Pin this skill to the game screen (only one at a time), or unpin it
        private final JMenuItem pinItem = new JMenuItem();
        // What this skill's goal suggestions aim for (its own choice, or the default)
        private final JMenu focusMenu = new JMenu("Suggest");
        private final Map<SkillTrackerConfig.TrainingFocus, JRadioButtonMenuItem> focusItems = new EnumMap<>(SkillTrackerConfig.TrainingFocus.class);
        private final JRadioButtonMenuItem defaultFocusItem = new JRadioButtonMenuItem();

        SkillCard(Skill skill) {
            this.skill = skill;
            setLayout(new BorderLayout(6, 5));
            setBackground(ColorScheme.DARKER_GRAY_COLOR);
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 6, 0, ColorScheme.DARK_GRAY_COLOR),
                new EmptyBorder(7, 7, 7, 7)));

            JLabel icon = new JLabel();
            BufferedImage image = skillIconManager.getSkillImage(skill, false);
            if (image != null) {
                icon.setIcon(new ImageIcon(image));
            }
            icon.setToolTipText(skill.getName());
            icon.setVerticalAlignment(SwingConstants.CENTER);
            icon.setPreferredSize(new Dimension(26, 0));

            stats.setOpaque(false);
            // Stacked at their own heights (the suggestion can be two lines)
            JPanel bars = new JPanel();
            bars.setLayout(new BoxLayout(bars, BoxLayout.Y_AXIS));
            bars.setOpaque(false);
            for (JComponent row : new JComponent[]{levelBar, goalBar, suggestion}) {
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row == suggestion ? Integer.MAX_VALUE : row.getPreferredSize().height));
                bars.add(row);
                bars.add(Box.createVerticalStrut(3));
            }
            goalBar.setGoalStyle();

            // While this skill is pinned to the game screen, a pin is drawn over the card's top-right
            // corner (see paintChildren), so nothing else moves; clicking it unpins
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e) && pinned && pinBounds().contains(e.getPoint())) {
                        plugin.setPinnedSkill(null);
                    }
                }
            });
            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    boolean over = pinned && pinBounds().contains(e.getPoint());
                    setCursor(over ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
                    setToolTipText(over ? "Pinned to the game screen - click to unpin" : skill.getName() + " - right-click for options");
                }
            });

            add(icon, BorderLayout.WEST);
            add(stats, BorderLayout.CENTER);
            add(bars, BorderLayout.SOUTH);
            layoutStats();

            JPopupMenu menu = new JPopupMenu();
            pauseItem.addActionListener(e -> plugin.toggleSkillPause(skill));
            suggestedItem.addActionListener(e -> plugin.setGoalTarget(skill, null));
            pinItem.addActionListener(e -> plugin.setPinnedSkill(skill == plugin.getPinnedSkill() ? null : skill));
            menu.add(pauseItem);
            menu.add(menuItem("Reset rate", () -> plugin.resetSkillRate(skill)));
            menu.add(menuItem("Set goal...", () -> askForGoal(skill)));
            menu.add(menuItem("Clear goal", () -> plugin.setSkillGoal(skill, 0)));
            ButtonGroup focusGroup = new ButtonGroup();
            defaultFocusItem.addActionListener(e -> plugin.setTrainingFocus(skill, null));
            focusGroup.add(defaultFocusItem);
            focusMenu.add(defaultFocusItem);
            focusMenu.addSeparator();
            for (SkillTrackerConfig.TrainingFocus f : SkillTrackerConfig.TrainingFocus.values()) {
                JRadioButtonMenuItem item = new JRadioButtonMenuItem(f.toString());
                item.addActionListener(e -> plugin.setTrainingFocus(skill, f));
                focusGroup.add(item);
                focusMenu.add(item);
                focusItems.put(f, item);
            }
            menu.add(focusMenu);
            menu.add(suggestedItem);
            menu.add(pinItem);
            menu.add(menuItem("Reset " + skill.getName(), () -> plugin.resetSkill(skill)));
            menu.add(menuItem("Hide " + skill.getName(), () -> plugin.addIgnoredSkill(skill)));
            menu.addSeparator();
            menu.add(menuItem("Reset all skills", plugin::resetSession));
            setComponentPopupMenu(menu);
            setToolTipText(skill.getName() + " - right-click for options");
            inheritPopupMenu(this);
        }

        // The pin's spot: the card's top-right corner, inside its padding
        private Rectangle pinBounds() {
            return new Rectangle(getWidth() - PIN_IMAGE.getWidth() - 2, 1, PIN_IMAGE.getWidth(), PIN_IMAGE.getHeight());
        }

        @Override
        protected void paintChildren(Graphics g) {
            super.paintChildren(g);
            if (pinned) {
                Rectangle at = pinBounds();
                g.drawImage(PIN_IMAGE, at.x, at.y, null);
            }
        }

        // Only the fields switched on under Skill Tracker, two to a row
        private void layoutStats() {
            // Combat skills count kills instead, once they have a goal (an action is a hit otherwise)
            boolean showActions = config.showTrackerActions() && (!COMBAT_SKILLS.contains(skill) || plugin.getSkillGoal(skill) > 0);
            boolean goal = config.showTrackerGoal() && plugin.getSkillGoal(skill) > 0;
            String wanted = "" + config.showTrackerProgressBar() + config.showTrackerXpGained() + config.showTrackerXpToLevel()
                + showActions + config.showTrackerXpPerHour() + config.showXpPerMinute() + config.showTrackerTimeToLevel() + goal;
            if (wanted.equals(layout)) {
                return;
            }
            layout = wanted;
            stats.removeAll();
            addStat(config.showTrackerXpGained(), gained);
            addStat(config.showTrackerXpToLevel(), left);
            addStat(config.showTrackerXpPerHour(), perHour);
            // Combat XP comes per hit, not per action, so the count means nothing there
            addStat(showActions, actions);
            addStat(config.showTrackerTimeToLevel(), timeToLevel);
            addStat(config.showXpPerMinute(), perMinute);
            levelBar.setVisible(config.showTrackerProgressBar());
            goalBar.setVisible(goal);
            stats.revalidate();
            revalidate();
        }

        private void addStat(boolean show, JLabel label) {
            if (show) {
                stats.add(label);
            }
        }

        void refresh() {
            layoutStats();
            int xp = plugin.getCurrentSkillXp(skill);
            int level = Experience.getLevelForXp(xp);
            int xpPerHour = plugin.getSkillXpPerHour(skill);
            boolean paused = plugin.isSkillPaused(skill);

            setStat(gained, "Gained", shortNumber(plugin.getSkillGained(skill)));
            setStat(perHour, "XP/hr", plugin.isSkillAutoPaused(skill) ? "Idle" : paused ? "Paused" : shortNumber(xpPerHour));
            setStat(perMinute, "XP/min", shortNumber(plugin.getSkillXpPerMinute(skill)));
            int goalXp = plugin.getSkillGoal(skill);
            // First, as it finds where the current step ends (for the actions count)
            updateSuggestion(level, goalXp, xp);

            if (level >= Experience.MAX_REAL_LEVEL) {
                setStat(left, "Left", "--");
                setActions(xp);
                setStat(timeToLevel, "TTL", "--");
                levelBar.set(1.0, "Lvl " + level, "Max", "");
            } else {
                int levelStart = Experience.getXpForLevel(level);
                int levelEnd = Experience.getXpForLevel(level + 1);
                int remaining = levelEnd - xp;
                setStat(left, "Left", shortNumber(remaining));
                setActions(xp);
                setStat(timeToLevel, "TTL", xpPerHour > 0 && !paused ? formatDuration(remaining * 3600L / xpPerHour) : "--");
                double progress = (double) (xp - levelStart) / (levelEnd - levelStart);
                levelBar.set(progress, "Lvl " + level, String.format("%.2f%%", progress * 100), "Lvl " + (level + 1));
            }

            if (goalXp > 0) {
                int start = Math.min(plugin.getSkillGoalStart(skill), xp);
                int remaining = Math.max(0, goalXp - xp);
                double progress = goalXp > start ? (double) (xp - start) / (goalXp - start) : 1;
                int goalLevel = Experience.getLevelForXp(goalXp);
                String target = goalXp == Experience.getXpForLevel(goalLevel) ? "Goal: Lvl " + goalLevel : "Goal: " + shortNumber(goalXp);
                String eta = remaining == 0 ? "Done!" : xpPerHour > 0 && !paused ? formatDuration(remaining * 3600L / xpPerHour) : shortNumber(remaining) + " left";
                goalBar.set(progress, target, String.format("%.1f%%", Math.min(1, progress) * 100), eta);
            }

            pauseItem.setText(pausedByUser() ? "Resume tracking" : "Pause tracking");
            suggestedItem.setVisible(plugin.getGoalTarget(skill) != null);
            boolean nowPinned = skill == plugin.getPinnedSkill();
            pinItem.setText(nowPinned ? "Unpin from screen" : "Pin to screen");
            if (pinned != nowPinned) {
                pinned = nowPinned;
                repaint();
            }
            SkillTrackerConfig.TrainingFocus own = plugin.getOwnTrainingFocus(skill);
            defaultFocusItem.setText("Default (" + config.trainingFocus() + ")");
            (own != null ? focusItems.get(own) : defaultFocusItem).setSelected(true);
            focusMenu.setVisible(skill != Skill.SLAYER);
        }

        // How many more of the last action (its XP drop) until the next level, or until the goal if set to
        private void setActions(int xp) {
            GoalLines.Actions count = GoalLines.actions(plugin, config, skill, xp, stepEndLevel);
            if (!Objects.equals(actions.getToolTipText(), count.tip)) {
                actions.setToolTipText(count.tip);
            }
            setStat(actions, count.name, count.value);
        }

        // The wiki guide's method for this level, and what to switch to before the goal
        private void updateSuggestion(int level, int goalXp, int xp) {
            List<String> lines = new ArrayList<>();
            String tooltip = null;
            stepEndLevel = 0;
            if (goalXp > xp && config.showTrackerGoal() && config.showTrainingSuggestion()) {
                String target = plugin.getGoalTarget(skill);
                if (target != null) {
                    lines.add(label("Tracking:") + SkillTracker.goalTargetName(target) + " " + label("(your pick)"));
                } else if (skill == Skill.SLAYER) {
                    slayerSuggestion(lines);
                } else {
                    // By the Training Focus setting: fastest, other methods, cheapest or money maker
                    // The leg you're on ("Train: Willow trees to 60"); hover it for the whole way to the goal
                    List<TrainingAdvisor.Stage> plan = plugin.getTrainingAdvisor().plan(skill, level, Experience.getLevelForXp(goalXp));
                    if (!plan.isEmpty()) {
                        TrainingAdvisor.Stage first = plan.get(0);
                        lines.add(label(first.suggestion.label) + first.suggestion.text
                            + (plan.size() > 1 ? " " + label("to " + first.to) : ""));
                    }
                    if (plan.size() > 1) {
                        stepEndLevel = plan.get(0).to;
                        StringBuilder steps = new StringBuilder("<html><b>On the way to your goal</b>");
                        for (TrainingAdvisor.Stage stage : plan) {
                            steps.append("<br>").append(stage.from).append("-").append(stage.to).append(": ")
                                .append(stage.suggestion.text);
                        }
                        tooltip = steps.append("</html>").toString();
                    }
                }
                // For production skills and Prayer: the best thing to make or use from your inventory and bank
                // Low levels: quests worth doing for their XP instead ("Quest XP: The Tourist Trap, +3 more")
                if (target == null && skill != Skill.SLAYER) {
                    List<String> xpQuests = plugin.getTrainingAdvisor().xpQuests(skill, level);
                    if (!xpQuests.isEmpty()) {
                        lines.add(label("Quest XP:") + xpQuests.get(0)
                            + (xpQuests.size() > 1 ? " " + label("+" + (xpQuests.size() - 1) + " more") : ""));
                        String quests = "<b>Quests worth doing for " + skill.getName() + " XP</b><br>" + String.join("<br>", xpQuests);
                        tooltip = tooltip == null ? "<html>" + quests + "</html>"
                            : tooltip.replace("</html>", "<br><br>" + quests + "</html>");
                    }
                }
                GoalLines.ItemLine item = GoalLines.itemLine(plugin, skill);
                if (item != null) {
                    lines.add(label(item.label) + item.item + " " + label(item.have));
                }
                // Combat goals: how fast you're killing (faster with potions and better gear)
                String killRate = GoalLines.killRate(plugin, skill);
                if (killRate != null) {
                    lines.add(label("Kill rate:") + killRate);
                }
                // The tools it needs: green if you have them (carried, worn or banked), red if not
                Map<String, Boolean> tools = GoalLines.tools(plugin, skill, level);
                if (!tools.isEmpty()) {
                    List<String> parts = new ArrayList<>();
                    tools.forEach((name, have) -> parts.add("<font color='" + (have ? "#64dc64" : "#ff6464") + "'>" + name + "</font>"));
                    lines.add(label("Needs:") + String.join(", ", parts));
                }
            }
            if (suggestion.isVisible() != !lines.isEmpty()) {
                suggestion.setVisible(!lines.isEmpty());
            }
            setText(suggestion, "<html>" + String.join("<br>", lines) + "</html>");
            if (!Objects.equals(suggestion.getToolTipText(), tooltip)) {
                suggestion.setToolTipText(tooltip);
            }
        }

        // Slayer: the best master for your combat and Slayer level, and the next one up
        private void slayerSuggestion(List<String> lines) {
            SlayerData slayer = plugin.getSlayerData();
            SlayerData.Master master = slayer.bestMaster(plugin.getCombatLevel(), plugin.getCurrentSkillLevel(Skill.SLAYER));
            if (master == null) {
                return;
            }
            lines.add(label("Train:") + "Tasks from " + master.name);
            SlayerData.Master next = slayer.nextMaster(master);
            if (next != null) {
                lines.add(label("At Combat " + next.combat + (next.slayer > 0 ? ", Slayer " + next.slayer : "") + ":") + next.name);
            }
        }

        private String label(String text) {
            return "<font color='#bebebe'>" + text + "</font> ";
        }

        private void setStat(JLabel label, String name, String value) {
            // One line, never wrapped: a wrapped cell makes every row of the grid taller
            setText(label, "<html><nobr><font color='#bebebe'>" + name + ":</font> " + value + "</nobr></html>");
        }

        private boolean pausedByUser() {
            return plugin.isSkillPaused(skill) && !plugin.isSkillAutoPaused(skill);
        }

        private JMenuItem menuItem(String text, Runnable action) {
            JMenuItem item = new JMenuItem(text);
            item.addActionListener(e -> action.run());
            return item;
        }

        private void inheritPopupMenu(Container parent) {
            for (Component child : parent.getComponents()) {
                if (child instanceof JComponent) {
                    ((JComponent) child).setInheritsPopupMenu(true);
                }
                if (child instanceof Container) {
                    inheritPopupMenu((Container) child);
                }
            }
        }

        // Only touch the label when its text changes, so the card isn't laid out again on every update
        private void setText(JLabel label, String text) {
            if (!text.equals(label.getText())) {
                label.setText(text);
            }
        }

        private JLabel stat() {
            JLabel label = new JLabel();
            label.setFont(FontManager.getRunescapeSmallFont());
            label.setForeground(Color.WHITE);
            return label;
        }
    }

    /** A bar with text on the left, centre and right: "Lvl 73 | 31.90% | Lvl 74". */
    private static class LevelProgressBar extends JComponent {
        private double progress;
        private String leftText = "";
        private String centreText = "";
        private String rightText = "";
        private boolean goal;

        LevelProgressBar() {
            setPreferredSize(new Dimension(0, 16));
            setFont(FontManager.getRunescapeSmallFont());
        }

        void setGoalStyle() {
            goal = true;
            setPreferredSize(new Dimension(0, 14));
        }

        void set(double progress, String left, String centre, String right) {
            double clamped = Math.max(0, Math.min(1, progress));
            if (clamped != this.progress || !left.equals(leftText) || !centre.equals(centreText) || !right.equals(rightText)) {
                this.progress = clamped;
                leftText = left;
                centreText = centre;
                rightText = right;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            g.setColor(BAR_BACKGROUND);
            g.fillRect(0, 0, w, h);
            // Level bar: red under a third of the way, orange under two thirds, then green; goal bar: gold
            g.setColor(goal ? GOAL_FILL : progress < 1 / 3.0 ? BAR_LOW : progress < 2 / 3.0 ? BAR_MID : BAR_HIGH);
            g.fillRect(0, 0, (int) (w * progress), h);

            FontMetrics fm = g.getFontMetrics();
            int y = (h - fm.getHeight()) / 2 + fm.getAscent();
            shadowed(g, leftText, 4, y);
            shadowed(g, centreText, (w - fm.stringWidth(centreText)) / 2, y);
            shadowed(g, rightText, w - 4 - fm.stringWidth(rightText), y);
        }

        private static void shadowed(Graphics g, String text, int x, int y) {
            g.setColor(Color.BLACK);
            g.drawString(text, x + 1, y + 1);
            g.setColor(Color.WHITE);
            g.drawString(text, x, y);
        }
    }
}
