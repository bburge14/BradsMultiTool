package com.snuffys.multitool;

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
import java.util.Collections;
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
    /** A skill plan's milestone bar (under the goal's gold). */
    static final Color MILESTONE_FILL = new Color(70, 140, 200);

    private final SkillTracker plugin;
    private final SkillTrackerConfig config;
    private final SkillIconManager skillIconManager;

    private final JLabel totalXpLabel = new JLabel();
    private final JLabel totalRateLabel = new JLabel();
    private final JLabel skillCountLabel = new JLabel();
    private final JLabel emptyLabel = new JLabel("<html>Gain XP to start tracking skills.<br>Right-click a skill for options.</html>");
    private final JPanel cardsContainer = new JPanel();
    private final JLabel goalsHeading = foldingHeading("GOALS", "goals");
    private final JLabel sessionHeading = foldingHeading("THIS SESSION", "session");
    private final JLabel rumourHeading = foldingHeading("HUNTER RUMOUR", "rumour");
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
        JPanel trained = statBlock("TRAINED", skills);
        trained.setToolTipText("Skills you've gained XP in this session");
        stats.add(trained);
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

        // Skills with a goal are pinned under Goals (even if not trained this session); the rest follow,
        // with the skill pinned to the game screen always among them (its card is where you unpin it)
        List<Skill> goals = plugin.getGoalSkills();
        List<Skill> session = new ArrayList<>();
        for (Skill skill : tracked) {
            // Skills without a goal drop off after a while without XP (Drop Idle Skills After)
            if (!goals.contains(skill) && plugin.isRecentlyTrained(skill)) {
                session.add(skill);
            }
        }
        // Most recent XP first in each list (ties, like Attack and Hitpoints from one hit, keep skill order)
        java.util.Comparator<Skill> latestFirst = java.util.Comparator.comparingLong((Skill skill) -> -plugin.lastXpTime(skill));
        goals = new ArrayList<>(goals);
        goals.sort(latestFirst);
        session.sort(latestFirst);
        Skill pinnedSkill = plugin.getPinnedSkill();
        if (pinnedSkill != null && !goals.contains(pinnedSkill) && !session.contains(pinnedSkill)) {
            session.add(0, pinnedSkill);
        }
        Set<Skill> shown = new HashSet<>(goals);
        shown.addAll(session);
        cards.keySet().removeIf(skill -> !shown.contains(skill));
        for (Skill skill : shown) {
            cards.computeIfAbsent(skill, SkillCard::new).refresh();
        }

        boolean goalsFolded = plugin.isListCollapsed("goals");
        boolean sessionFolded = plugin.isListCollapsed("session");
        // Your Hunters' Rumour, if you have one
        boolean rumourFolded = plugin.isListCollapsed("rumour");
        List<String[]> rumour = plugin.getHunterRumours().lines();
        StringBuilder rumourText = new StringBuilder();
        for (String[] line : rumour) {
            rumourText.append(line[0]).append(line[1]).append(';');
        }
        String order = goals + "|" + session + "|" + goalsFolded + sessionFolded + rumourFolded + "|" + rumourText;
        if (!order.equals(cardOrder)) {
            cardOrder = order;
            cardsContainer.removeAll();
            if (shown.isEmpty()) {
                cardsContainer.add(emptyLabel);
            }
            // Each list under a heading you click to fold it away or open it again
            if (!goals.isEmpty()) {
                setHeading(goalsHeading, "GOALS", goalsFolded, goals.size());
                cardsContainer.add(goalsHeading);
                if (!goalsFolded) {
                    goals.forEach(skill -> cardsContainer.add(cards.get(skill)));
                }
            }
            if (!session.isEmpty()) {
                if (!goals.isEmpty() || sessionFolded) {
                    setHeading(sessionHeading, "THIS SESSION", sessionFolded, session.size());
                    cardsContainer.add(sessionHeading);
                }
                if (!sessionFolded) {
                    session.forEach(skill -> cardsContainer.add(cards.get(skill)));
                }
            }
            if (!rumour.isEmpty()) {
                setHeading(rumourHeading, "HUNTER RUMOUR", rumourFolded, 1);
                rumourHeading.setToolTipText(rumourFolded ? "Click to show your Hunters' Rumour" : "Click to hide your Hunters' Rumour");
                cardsContainer.add(rumourHeading);
                if (!rumourFolded) {
                    cardsContainer.add(rumourRow(rumour, plugin.getHunterRumours().places()));
                }
            }
            cardsContainer.revalidate();
            cardsContainer.repaint();
        }
    }

    // The rumour card: a line each (creature, catches, where), hover for every place the creature lives
    private JPanel rumourRow(List<String[]> lines, List<String> places) {
        StringBuilder html = new StringBuilder("<html>");
        for (String[] line : lines) {
            html.append(html.length() > 6 ? "<br>" : "").append("<font color='#bebebe'>").append(line[0]).append("</font> ")
                .append(line[1]);
        }
        JLabel text = new JLabel(html.append("</html>").toString());
        text.setFont(FontManager.getRunescapeSmallFont());
        text.setForeground(Color.WHITE);
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(new EmptyBorder(4, 6, 4, 6));
        row.add(text, BorderLayout.CENTER);
        if (!places.isEmpty()) {
            row.setToolTipText("<html><b>Where to find it</b><br>" + String.join("<br>", places) + "</html>");
        }
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
        wrap.setBorder(new EmptyBorder(0, 0, 6, 0));
        wrap.add(row);
        wrap.setAlignmentX(Component.CENTER_ALIGNMENT);
        wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height + 6));
        return wrap;
    }

    // "- GOALS" when open, "+ GOALS (3)" when folded (plain signs: the game font has no arrows)
    private static void setHeading(JLabel heading, String name, boolean folded, int count) {
        heading.setText(folded ? "+ " + name + " (" + count + ")" : "- " + name);
        heading.setToolTipText(folded ? "Click to show these skills" : "Click to hide these skills");
    }

    private JLabel foldingHeading(String text, String list) {
        JLabel label = sectionHeading(text);
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    plugin.setListCollapsed(list, !plugin.isListCollapsed(list));
                }
            }
        });
        return label;
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
        // Modifying: starts from the goal it has
        int existing = skill != null ? plugin.getSkillGoal(skill) : 0;
        if (existing > 0) {
            int existingLevel = Experience.getLevelForXp(existing);
            target.setText(existing == Experience.getXpForLevel(existingLevel) ? String.valueOf(existingLevel)
                : String.format("%,d", existing));
        }
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
        int result = JOptionPane.showConfirmDialog(this, form, skill == null ? "Set goal" : (existing > 0 ? "Modify goal for " : "Goal for ") + skill.getName(),
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

    /** " (to level 67)": the level your XP plus what's banked reaches; empty when it doesn't make a level. */
    static String bankedLevel(int xp, double banked) {
        int now = Experience.getLevelForXp(xp);
        int reach = Experience.getLevelForXp((int) Math.min(Experience.MAX_SKILL_XP, xp + (long) banked));
        return reach > now ? " (to level " + reach + ")" : "";
    }

    /** 9,112 / 55.8K / 1.24M, like RuneLite's XP tracker. */
    static String shortNumber(long value) {
        if (value >= 1_000_000) {
            return String.format("%.2fM", value / 1_000_000.0);
        }
        // No decimal from 100K up ("262K", not "262.3K"): it wouldn't fit the narrow stat columns
        if (value >= 100_000) {
            return String.format("%.0fK", value / 1_000.0);
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
        // The skill plan's milestone you're on (exported from the Planner): its own bar under the goal's
        private final LevelProgressBar milestoneBar = new LevelProgressBar();
        // Logs, fish, ore, food cooked this session and how many an hour ("Logs: 342 (1,050/hr)")
        private final JLabel items = stat();
        private final JLabel suggestion = stat();
        // "You're training on ...: track it for your goal?" with Track / Dismiss (see MethodDetector)
        private final JPanel offerRow = new JPanel(new BorderLayout(4, 2));
        private final JLabel offerText = stat();
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
        // Set goal with none; Modify goal and Clear goal with one
        private final JMenuItem setGoalItem = new JMenuItem("Set goal...");
        private final JMenuItem modifyGoalItem = new JMenuItem("Modify goal...");
        private final JMenuItem clearGoalItem = new JMenuItem("Clear goal");
        private boolean pinHover;
        // What this skill's goal suggestions aim for (its own choice, or the default)
        private final JMenu focusMenu = new JMenu("Suggest");
        // Pick the guide method the suggestion uses (filled in each time the menu opens, for your level)
        private final JMenu methodMenu = new JMenu("Method");
        private final Map<SkillTrackerConfig.TrainingFocus, JRadioButtonMenuItem> focusItems = new EnumMap<>(SkillTrackerConfig.TrainingFocus.class);
        private final JRadioButtonMenuItem defaultFocusItem = new JRadioButtonMenuItem();

        SkillCard(Skill skill) {
            this.skill = skill;
            setLayout(new BorderLayout(6, 5));
            setBackground(ColorScheme.DARKER_GRAY_COLOR);
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 6, 0, ColorScheme.DARK_GRAY_COLOR),
                new EmptyBorder(7, 7, 7, 16)));

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
            offerRow.setOpaque(false);
            offerRow.add(offerText, BorderLayout.CENTER);
            JPanel offerButtons = new JPanel(new GridLayout(1, 2, 4, 0));
            offerButtons.setOpaque(false);
            offerButtons.add(offerButton("Track it", () -> plugin.getMethodDetector().accept(skill)));
            offerButtons.add(offerButton("Dismiss", () -> plugin.getMethodDetector().dismiss(skill)));
            offerRow.add(offerButtons, BorderLayout.SOUTH);
            offerRow.setVisible(false);
            items.setVisible(false);
            for (JComponent row : new JComponent[]{levelBar, goalBar, milestoneBar, items, suggestion, offerRow}) {
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row == suggestion || row == offerRow || row == items
                    ? Integer.MAX_VALUE : row.getPreferredSize().height));
                bars.add(row);
                bars.add(Box.createVerticalStrut(3));
            }
            goalBar.setGoalStyle();
            milestoneBar.setGoalStyle();
            milestoneBar.fill = MILESTONE_FILL;
            milestoneBar.setVisible(false);

            // While this skill is pinned to the game screen, a pin is drawn over the card's top-right
            // corner (see paintChildren), so nothing else moves; clicking it unpins
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    // Only one skill is pinned at a time: pinning this one moves the pin from the other
                    if (SwingUtilities.isLeftMouseButton(e) && pinBounds().contains(e.getPoint())) {
                        plugin.setPinnedSkill(pinned ? null : skill);
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (pinHover) {
                        pinHover = false;
                        repaint();
                    }
                }
            });
            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    boolean over = pinBounds().contains(e.getPoint());
                    if (over != pinHover) {
                        pinHover = over;
                        repaint();
                    }
                    setCursor(over ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
                    setToolTipText(over ? pinTip() : skill.getName() + " - right-click for options");
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
            setGoalItem.addActionListener(e -> askForGoal(skill));
            modifyGoalItem.addActionListener(e -> askForGoal(skill));
            clearGoalItem.addActionListener(e -> plugin.setSkillGoal(skill, 0));
            menu.add(setGoalItem);
            menu.add(modifyGoalItem);
            menu.add(clearGoalItem);
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
            menu.add(methodMenu);
            menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
                @Override
                public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                    fillMethods();
                }

                @Override
                public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                }

                @Override
                public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
                }
            });
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

        private JButton offerButton(String text, Runnable action) {
            JButton button = new JButton(text);
            button.setFocusable(false);
            button.setMargin(new java.awt.Insets(1, 4, 1, 4));
            button.addActionListener(e -> action.run());
            return button;
        }

        // What you're training on when it isn't the suggestion or your pick, offered to track instead
        private void updateOffer() {
            String target = plugin.getMethodDetector().offer(skill);
            offerRow.setVisible(target != null);
            if (target != null) {
                offerText.setText("<html><div width='170'><font color='#ffff00'>You're training on:</font> "
                    + plugin.describeTarget(skill, target) + ". Track it for your goal?</div></html>");
            }
        }

        // Suggested (automatic), then each guide method your level and quests allow; the one in use is ticked
        private void fillMethods() {
            methodMenu.removeAll();
            int level = Experience.getLevelForXp(plugin.getCurrentSkillXp(skill));
            String chosen = plugin.getChosenMethod(skill);
            ButtonGroup group = new ButtonGroup();
            // Money Maker: the money-making guides, with their GP/hr
            if (plugin.getTrainingFocus(skill) == SkillTrackerConfig.TrainingFocus.MONEY) {
                List<MoneyData.Guide> guides = plugin.getTrainingAdvisor().moneyToPick(skill, level);
                boolean picked = guides.stream().anyMatch(g -> g.name.equals(chosen));
                JRadioButtonMenuItem best = new JRadioButtonMenuItem("Most profitable (automatic)", !picked);
                best.addActionListener(e -> plugin.setChosenMethod(skill, null));
                group.add(best);
                methodMenu.add(best);
                methodMenu.addSeparator();
                for (MoneyData.Guide guide : guides.subList(0, Math.min(guides.size(), 15))) {
                    JRadioButtonMenuItem item = new JRadioButtonMenuItem(guide.name + " - " + guide.profitText(),
                        guide.name.equals(chosen));
                    item.addActionListener(e -> plugin.setChosenMethod(skill, guide.name));
                    group.add(item);
                    methodMenu.add(item);
                }
                methodMenu.setEnabled(!guides.isEmpty());
                return;
            }
            List<TrainingData.Method> methods = plugin.getTrainingAdvisor().methodsToPick(skill, level);
            boolean chosenAvailable = methods.stream().anyMatch(m -> m.name.equals(chosen));
            JRadioButtonMenuItem auto = new JRadioButtonMenuItem("Suggested (automatic)", chosen == null || !chosenAvailable);
            auto.addActionListener(e -> plugin.setChosenMethod(skill, null));
            group.add(auto);
            methodMenu.add(auto);
            methodMenu.addSeparator();
            for (TrainingData.Method method : methods) {
                // The guide's own XP/hr where it gives one (an estimate)
                JRadioButtonMenuItem item = new JRadioButtonMenuItem(method.name + (method.fastest ? " (fastest)" : "")
                    + (method.xpHour > 0 ? " - ~" + shortNumber(method.xpHour) + " xp/hr" : ""), method.name.equals(chosen));
                if (method.about != null) {
                    item.setToolTipText("<html><div width='260'>" + method.about + "</div></html>");
                }
                item.addActionListener(e -> plugin.setChosenMethod(skill, method.name));
                group.add(item);
                methodMenu.add(item);
            }
            methodMenu.setEnabled(!methods.isEmpty());
        }

        // The pin's spot: the card's top-right corner, in the padding kept clear for it
        private Rectangle pinBounds() {
            return new Rectangle(getWidth() - PIN_IMAGE.getWidth() - 2, 2, PIN_IMAGE.getWidth(), PIN_IMAGE.getHeight());
        }

        private String pinTip() {
            Skill other = plugin.getPinnedSkill();
            if (pinned) {
                return "Pinned to the game screen - click to unpin";
            }
            return other != null ? "Click to pin to the game screen instead of " + other.getName()
                : "Click to pin to the game screen";
        }

        // Always there: solid while pinned, faint otherwise (brighter under the mouse)
        @Override
        protected void paintChildren(Graphics g) {
            super.paintChildren(g);
            Rectangle at = pinBounds();
            Graphics2D g2 = (Graphics2D) g.create();
            float alpha = pinned ? 1f : pinHover ? 0.75f : 0.3f;
            g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha));
            g2.drawImage(PIN_IMAGE, at.x, at.y, null);
            g2.dispose();
        }

        // Only the fields switched on under Skill Tracker, two to a row
        private void layoutStats() {
            // Combat skills count kills instead, once they have a goal (an action is a hit otherwise)
            boolean full = !config.compactOverlays();
            boolean showActions = full && config.showTrackerActions() && (!COMBAT_SKILLS.contains(skill) || plugin.getSkillGoal(skill) > 0);
            boolean goal = config.showTrackerGoal() && plugin.getSkillGoal(skill) > 0;
            String wanted = "" + config.showTrackerProgressBar() + config.showTrackerXpGained() + config.showTrackerXpToLevel()
                + showActions + config.showTrackerXpPerHour() + config.showXpPerMinute() + config.showTrackerTimeToLevel() + goal + full;
            if (wanted.equals(layout)) {
                return;
            }
            layout = wanted;
            stats.removeAll();
            addStat(full && config.showTrackerXpGained(), gained);
            addStat(full && config.showTrackerXpToLevel(), left);
            addStat(config.showTrackerXpPerHour(), perHour);
            // Combat XP comes per hit, not per action, so the count means nothing there
            addStat(showActions, actions);
            addStat(config.showTrackerTimeToLevel(), timeToLevel);
            addStat(full && config.showXpPerMinute(), perMinute);
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
            updateItems();
            // First, as it finds where the current step ends (for the actions count)
            updateSuggestion(level, goalXp, xp);
            updateOffer();

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
            updateMilestone(level, goalXp, xp, xpPerHour, paused);

            pauseItem.setText(pausedByUser() ? "Resume tracking" : "Pause tracking");
            suggestedItem.setVisible(plugin.getGoalTarget(skill) != null);
            boolean nowPinned = skill == plugin.getPinnedSkill();
            Skill otherPinned = plugin.getPinnedSkill();
            pinItem.setText(nowPinned ? "Unpin from screen"
                : otherPinned != null ? "Pin to screen (instead of " + otherPinned.getName() + ")" : "Pin to screen");
            boolean hasGoal = plugin.getSkillGoal(skill) > 0;
            setGoalItem.setVisible(!hasGoal);
            modifyGoalItem.setVisible(hasGoal);
            clearGoalItem.setVisible(hasGoal);
            if (pinned != nowPinned) {
                pinned = nowPinned;
                repaint();
            }
            SkillTrackerConfig.TrainingFocus own = plugin.getOwnTrainingFocus(skill);
            defaultFocusItem.setText("Default (" + config.trainingFocus() + ")");
            (own != null ? focusItems.get(own) : defaultFocusItem).setSelected(true);
            focusMenu.setVisible(skill != Skill.SLAYER);
            methodMenu.setVisible(skill != Skill.SLAYER && hasGoal);
        }

        // The plan's milestone you're on: a bar from its start to its end, with the time to it
        private void updateMilestone(int level, int goalXp, int xp, int xpPerHour, boolean paused) {
            SkillPlans.Step step = goalXp > xp && config.showTrackerGoal() ? plugin.getPlanMilestone(skill, level) : null;
            boolean show = step != null && step.to < 100;
            if (milestoneBar.isVisible() != show) {
                milestoneBar.setVisible(show);
                revalidate();
            }
            if (!show) {
                return;
            }
            int start = Experience.getXpForLevel(step.from);
            int end = Experience.getXpForLevel(step.to);
            double progress = end > start ? (double) (xp - start) / (end - start) : 1;
            int left = Math.max(0, end - xp);
            String eta = left == 0 ? "Done!" : xpPerHour > 0 && !paused ? formatDuration(left * 3600L / xpPerHour) : shortNumber(left) + " left";
            milestoneBar.set(progress, "Step: Lvl " + step.to, String.format("%.1f%%", Math.max(0, Math.min(1, progress)) * 100), eta);
            String tip = "<html>Your plan's milestone: level " + step.to + " with " + step.action + " (from " + step.from + ")"
                + planTip(plugin.getSkillPlan(skill), level) + "</html>";
            if (!Objects.equals(milestoneBar.getToolTipText(), tip)) {
                milestoneBar.setToolTipText(tip);
            }
        }

        // The whole plan, for a tooltip: "<br>1-20: Logs" for each milestone, the one you're on in white
        private String planTip(SkillPlans.Plan plan, int level) {
            if (plan == null) {
                return "";
            }
            StringBuilder out = new StringBuilder("<br><b>Your plan to level " + plan.goal + "</b>");
            for (SkillPlans.Step s : plan.steps) {
                boolean on = s.covers(level);
                out.append("<br>").append(on ? "<font color='#ffffff'>" : "<font color='#bebebe'>").append(s.from).append("-")
                    .append(s.to).append(": ").append(s.action).append(on ? " (now)" : "").append("</font>");
            }
            return out.toString();
        }

        // How many more of the last action (its XP drop) until the next level, or until the goal if set to
        private void setActions(int xp) {
            GoalLines.Actions count = GoalLines.actions(plugin, config, skill, xp, stepEndLevel);
            if (!Objects.equals(actions.getToolTipText(), count.tip)) {
                actions.setToolTipText(count.tip);
            }
            setStat(actions, count.name, count.value);
        }

        // "Logs: 342 (1,050/hr)" for each kind of item counted (Show Items Gathered)
        private void updateItems() {
            List<String[]> lines = !config.compactOverlays() && config.showTrackerItems() ? plugin.itemLines(skill) : Collections.emptyList();
            if (items.isVisible() != !lines.isEmpty()) {
                items.setVisible(!lines.isEmpty());
            }
            List<String> parts = new ArrayList<>();
            for (String[] line : lines) {
                parts.add(label(line[0] + ":") + line[1]);
            }
            setText(items, "<html>" + String.join("<br>", parts) + "</html>");
        }

        // The wiki guide's method for this level, and what to switch to before the goal
        private void updateSuggestion(int level, int goalXp, int xp) {
            List<String> lines = new ArrayList<>();
            String tooltip = null;
            stepEndLevel = 0;
            if (goalXp > xp && config.showTrackerGoal() && config.showTrainingSuggestion() && !config.compactOverlays()) {
                String target = plugin.getGoalTarget(skill);
                if (target != null) {
                    lines.add(label("Tracking:") + plugin.describeGoalTarget(skill) + " " + label("(your pick)"));
                } else if (skill == Skill.SLAYER) {
                    slayerSuggestion(lines);
                } else {
                    // By the Training Focus setting: fastest, other methods, cheapest or money maker
                    // The leg you're on ("Train: Willow trees to 60"); hover it for the whole way to the goal
                    List<TrainingAdvisor.Stage> plan = plugin.getTrainingAdvisor().plan(skill, level, Experience.getLevelForXp(goalXp));
                    if (!plan.isEmpty() && plan.get(0).suggestion.planStep != null) {
                        SkillPlans.Step step = plan.get(0).suggestion.planStep;
                        lines.add(label("Milestone:") + "level " + step.to + " " + label("(" + step.action + ")"));
                        String where = GoalLines.where(plan.get(0).suggestion);
                        if (where != null) {
                            lines.add(label("Where:") + where);
                        }
                    } else if (!plan.isEmpty()) {
                        TrainingAdvisor.Stage first = plan.get(0);
                        lines.add(label(first.suggestion.label) + first.suggestion.text
                            + (plan.size() > 1 ? " " + label("to " + first.to) : ""));
                        String where = GoalLines.where(first.suggestion);
                        if (where != null) {
                            lines.add(label("Where:") + where);
                        }
                    }
                    // Hover: what the guide says about the method you're on
                    String about = !plan.isEmpty() && plan.get(0).suggestion.method != null ? plan.get(0).suggestion.method.about : null;
                    List<String> places = !plan.isEmpty() && plan.get(0).suggestion.method != null
                        ? plan.get(0).suggestion.method.places : Collections.emptyList();
                    if (about != null || !places.isEmpty()) {
                        tooltip = "<html><div width='260'>" + (about != null ? about : "")
                            + (places.isEmpty() ? "" : (about != null ? "<br><br>" : "") + "<b>Where:</b> " + String.join(", ", places))
                            + "</div></html>";
                    }
                    if (!plan.isEmpty() && plan.get(0).suggestion.planStep != null) {
                        stepEndLevel = plan.get(0).suggestion.planStep.to;
                        String steps = "<html>" + planTip(plugin.getSkillPlan(skill), level).substring("<br>".length()) + "</html>";
                        tooltip = tooltip == null ? steps : tooltip.replace("</html>", "<br><br>" + steps.substring("<html>".length()));
                    } else if (plan.size() > 1) {
                        stepEndLevel = plan.get(0).to;
                        StringBuilder steps = new StringBuilder("<html><b>On the way to your goal</b>");
                        for (TrainingAdvisor.Stage stage : plan) {
                            steps.append("<br>").append(stage.from).append("-").append(stage.to).append(": ")
                                .append(stage.suggestion.text);
                        }
                        tooltip = tooltip == null ? steps.append("</html>").toString()
                            : tooltip.replace("</html>", "<br>" + steps.substring("<html>".length()) + "</html>");
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
                double banked = plugin.getGoalItems().bankedXp(skill);
                if (banked >= 1 && config.showBankedXp()) {
                    lines.add(label("Banked:") + shortNumber((long) banked) + " XP" + bankedLevel(xp, banked));
                }
                long materials = plugin.getGoalItems().materialsValue(skill);
                if (materials > 0 && config.showMaterialsValue()) {
                    lines.add(label("Materials worth:") + shortNumber(materials) + " gp");
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
        // The goal style's colour (gold; a plan's milestone blue)
        Color fill = GOAL_FILL;

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
            g.setColor(goal ? fill : progress < 1 / 3.0 ? BAR_LOW : progress < 2 / 3.0 ? BAR_MID : BAR_HIGH);
            g.fillRect(0, 0, (int) (w * progress), h);

            FontMetrics fm = g.getFontMetrics();
            int y = (h - fm.getHeight()) / 2 + fm.getAscent();
            int leftEnd = 4 + fm.stringWidth(leftText);
            int rightStart = w - 4 - fm.stringWidth(rightText);
            shadowed(g, leftText, 4, y);
            // The middle text centred, moved over when it would run into the left (or dropped if there's no room)
            int centreX = Math.max((w - fm.stringWidth(centreText)) / 2, leftEnd + 6);
            if (centreX + fm.stringWidth(centreText) <= rightStart - 6) {
                shadowed(g, centreText, centreX, y);
            }
            shadowed(g, rightText, rightStart, y);
        }

        private static void shadowed(Graphics g, String text, int x, int y) {
            g.setColor(Color.BLACK);
            g.drawString(text, x + 1, y + 1);
            g.setColor(Color.WHITE);
            g.drawString(text, x, y);
        }
    }
}
