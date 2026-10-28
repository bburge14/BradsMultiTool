package com.bradsmultitool;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
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

    private static final Set<Skill> COMBAT_SKILLS = EnumSet.of(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE,
        Skill.HITPOINTS, Skill.RANGED);

    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance();
    private static final Color ACCENT_COLOR = new Color(0, 220, 220);
    // Level progress bar: red under a third of the way, orange under two thirds, then green
    private static final Color BAR_LOW = new Color(190, 45, 40);
    private static final Color BAR_MID = new Color(225, 135, 25);
    private static final Color BAR_HIGH = new Color(15, 165, 105);
    private static final Color BAR_BACKGROUND = new Color(60, 60, 60);

    private final BradsMultiToolPlugin plugin;
    private final BradsMultiToolConfig config;
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

    public BradsMultiToolPanel(BradsMultiToolPlugin plugin, BradsMultiToolConfig config, SkillIconManager skillIconManager) {
        super();
        this.plugin = plugin;
        this.config = config;
        this.skillIconManager = skillIconManager;

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel header = new JPanel(new BorderLayout(0, 8));
        header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(2, 0, 0, 0, ACCENT_COLOR),
            new EmptyBorder(8, 8, 8, 8)));

        JPanel titles = new JPanel(new GridLayout(2, 1));
        titles.setOpaque(false);
        JLabel title = new JLabel("Brad's Multi-Tool");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(ACCENT_COLOR);
        JLabel subtitle = new JLabel("Skill Tracker");
        subtitle.setFont(FontManager.getRunescapeSmallFont());
        subtitle.setForeground(Color.GRAY);
        titles.add(title);
        titles.add(subtitle);
        header.add(titles, BorderLayout.NORTH);

        JPanel stats = new JPanel(new GridLayout(1, 3, 6, 0));
        stats.setOpaque(false);
        stats.add(statBlock("TOTAL XP", totalXpLabel));
        stats.add(statBlock("XP / HR", totalRateLabel));
        stats.add(statBlock("SKILLS", skillCountLabel));
        header.add(stats, BorderLayout.CENTER);

        JButton goalButton = new JButton("Set Goal");
        goalButton.setToolTipText("Pick a skill and a target level or XP; it's pinned under Goals");
        goalButton.setFocusPainted(false);
        goalButton.addActionListener(e -> askForGoal(null));
        header.add(goalButton, BorderLayout.SOUTH);

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

        JButton reportButton = new JButton("Report an Issue");
        reportButton.setToolTipText("Open a new issue on the plugin's GitHub page");
        reportButton.setFocusPainted(false);
        reportButton.addActionListener(e -> LinkBrowser.browse(NEW_ISSUE_URL));
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        footer.setBorder(new EmptyBorder(10, 0, 0, 0));
        footer.add(reportButton, BorderLayout.CENTER);

        add(header, BorderLayout.NORTH);
        add(cardsWrapper, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);

        updatePanel();
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
        JLabel label = new JLabel(text);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(Color.GRAY);
        label.setBorder(new EmptyBorder(2, 2, 4, 0));
        return label;
    }

    /**
     * Set a goal: a level (2-99) or an XP amount. With no skill given, asks which skill too.
     */
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
                return super.getListCellRendererComponent(list, value instanceof Skill ? ((Skill) value).getName() : value,
                    index, selected, focus);
            }
        });
        JTextField target = new JTextField(10);
        JPanel form = new JPanel(new GridLayout(0, 1, 0, 4));
        if (skill == null) {
            form.add(new JLabel("Skill:"));
            form.add(skills);
        } else {
            skills.setSelectedItem(skill);
        }
        form.add(new JLabel("Goal (a level like 70, or XP like 1,000,000):"));
        form.add(target);
        int result = JOptionPane.showConfirmDialog(this, form, skill == null ? "Set goal" : "Goal for " + skill.getName(),
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION || target.getText().trim().isEmpty()) {
            return;
        }
        try {
            int value = Integer.parseInt(target.getText().trim().replace(",", ""));
            int goalXp = value <= Experience.MAX_REAL_LEVEL ? Experience.getXpForLevel(Math.max(2, value)) : value;
            plugin.setSkillGoal((Skill) skills.getSelectedItem(), Math.min(goalXp, Experience.MAX_SKILL_XP));
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Enter a level like 70 or an XP amount like 1,000,000.");
        }
    }

    private static JPanel statBlock(String caption, JLabel value) {
        JPanel block = new JPanel(new GridLayout(2, 1));
        block.setOpaque(false);
        JLabel captionLabel = new JLabel(caption);
        captionLabel.setFont(FontManager.getRunescapeSmallFont());
        captionLabel.setForeground(Color.GRAY);
        value.setFont(FontManager.getRunescapeBoldFont());
        value.setForeground(Color.WHITE);
        block.add(captionLabel);
        block.add(value);
        return block;
    }

    private static String formatDuration(long seconds) {
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
        // Which fields are currently laid out, so they're only rebuilt when the settings change
        private String layout = "";
        private final JMenuItem pauseItem = new JMenuItem();

        SkillCard(Skill skill) {
            this.skill = skill;
            setLayout(new BorderLayout(8, 5));
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
            icon.setPreferredSize(new Dimension(32, 0));

            stats.setOpaque(false);
            JPanel bars = new JPanel(new GridLayout(0, 1, 0, 3));
            bars.setOpaque(false);
            bars.add(levelBar);
            bars.add(goalBar);
            goalBar.setGoalStyle();

            add(icon, BorderLayout.WEST);
            add(stats, BorderLayout.CENTER);
            add(bars, BorderLayout.SOUTH);
            layoutStats();

            JPopupMenu menu = new JPopupMenu();
            pauseItem.addActionListener(e -> plugin.toggleSkillPause(skill));
            menu.add(pauseItem);
            menu.add(menuItem("Reset rate", () -> plugin.resetSkillRate(skill)));
            menu.add(menuItem("Set goal...", () -> askForGoal(skill)));
            menu.add(menuItem("Clear goal", () -> plugin.setSkillGoal(skill, 0)));
            menu.add(menuItem("Reset " + skill.getName(), () -> plugin.resetSkill(skill)));
            menu.add(menuItem("Hide " + skill.getName(), () -> plugin.addIgnoredSkill(skill)));
            menu.addSeparator();
            menu.add(menuItem("Reset all skills", plugin::resetSession));
            setComponentPopupMenu(menu);
            setToolTipText(skill.getName() + " - right-click for options");
            inheritPopupMenu(this);
        }

        // Only the fields switched on under Skill Tracker, two to a row
        private void layoutStats() {
            boolean showActions = config.showTrackerActions() && !COMBAT_SKILLS.contains(skill);
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

            setStat(gained, "XP Gained", shortNumber(plugin.getSkillGained(skill)));
            setStat(perHour, "XP/hr", plugin.isSkillAutoPaused(skill) ? "Idle" : paused ? "Paused" : shortNumber(xpPerHour));
            setStat(perMinute, "XP/min", shortNumber(plugin.getSkillXpPerMinute(skill)));

            if (level >= Experience.MAX_REAL_LEVEL) {
                setStat(left, "XP Left", "--");
                setStat(actions, "Actions", "--");
                setStat(timeToLevel, "TTL", "--");
                levelBar.set(1.0, "Lvl " + level, "Max", "");
            } else {
                int levelStart = Experience.getXpForLevel(level);
                int levelEnd = Experience.getXpForLevel(level + 1);
                int remaining = levelEnd - xp;
                setStat(left, "XP Left", shortNumber(remaining));
                // How many more of the last action (its XP drop) until the next level
                int perAction = plugin.getLastXpDrop(skill);
                setStat(actions, "Actions", perAction > 0 ? shortNumber((remaining + perAction - 1) / perAction) : "--");
                setStat(timeToLevel, "TTL", xpPerHour > 0 && !paused ? formatDuration(remaining * 3600L / xpPerHour) : "--");
                double progress = (double) (xp - levelStart) / (levelEnd - levelStart);
                levelBar.set(progress, "Lvl " + level, String.format("%.2f%%", progress * 100), "Lvl " + (level + 1));
            }

            int goalXp = plugin.getSkillGoal(skill);
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
        }

        private void setStat(JLabel label, String name, String value) {
            label.setText("<html><font color='#bebebe'>" + name + ":</font> " + value + "</html>");
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

        private JLabel stat() {
            JLabel label = new JLabel();
            label.setFont(FontManager.getRunescapeSmallFont());
            label.setForeground(Color.WHITE);
            return label;
        }
    }

    /** A bar with text on the left, centre and right: "Lvl 73 | 31.90% | Lvl 74". */
    private static class LevelProgressBar extends JComponent {
        private static final Color GOAL_FILL = new Color(200, 160, 40);
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
