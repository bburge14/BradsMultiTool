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
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
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

    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance();
    private static final Color ACCENT_COLOR = new Color(0, 220, 220);
    private static final Color LEVEL_COLOR = new Color(80, 200, 255);
    private static final Color LABEL_COLOR = new Color(190, 190, 190);
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

        Set<Skill> trackedSet = new HashSet<>(tracked);
        boolean layoutChanged = cards.keySet().removeIf(skill -> {
            if (!trackedSet.contains(skill)) {
                cardsContainer.remove(cards.get(skill));
                return true;
            }
            return false;
        });

        for (Skill skill : tracked) {
            SkillCard card = cards.get(skill);
            if (card == null) {
                card = new SkillCard(skill);
                cards.put(skill, card);
                cardsContainer.add(card);
                layoutChanged = true;
            }
            card.refresh();
        }

        boolean showEmpty = cards.isEmpty();
        if (showEmpty != (emptyLabel.getParent() != null)) {
            if (showEmpty) {
                cardsContainer.add(emptyLabel, 0);
            } else {
                cardsContainer.remove(emptyLabel);
            }
            layoutChanged = true;
        }

        if (layoutChanged) {
            cardsContainer.revalidate();
            cardsContainer.repaint();
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

    private class SkillCard extends JPanel {
        private final Skill skill;
        private final JLabel levelLabel = new JLabel();
        private final LevelProgressBar progressBar = new LevelProgressBar();
        private final JLabel gainedValue = valueLabel();
        private final JLabel remainingLabel = rowLabel("XP Remaining:");
        private final JLabel remainingValue = valueLabel();
        private final JLabel actionsLabel = rowLabel("Actions Left:");
        private final JLabel actionsValue = valueLabel();
        private final JLabel perHourValue = valueLabel();
        private final JLabel perMinuteLabel = rowLabel("Rate (XP/Min):");
        private final JLabel perMinuteValue = valueLabel();
        private final JLabel ttlValue = valueLabel();
        private final JMenuItem pauseItem = new JMenuItem();

        SkillCard(Skill skill) {
            this.skill = skill;
            setLayout(new BorderLayout(0, 4));
            setBackground(ColorScheme.DARKER_GRAY_COLOR);
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 6, 0, ColorScheme.DARK_GRAY_COLOR),
                new EmptyBorder(6, 8, 8, 8)));

            JPanel top = new JPanel(new BorderLayout(6, 0));
            top.setOpaque(false);
            BufferedImage icon = skillIconManager.getSkillImage(skill, true);
            JLabel nameLabel = new JLabel(skill.getName());
            if (icon != null) {
                nameLabel.setIcon(new ImageIcon(icon));
                nameLabel.setIconTextGap(6);
            }
            nameLabel.setFont(FontManager.getRunescapeBoldFont());
            nameLabel.setForeground(Color.WHITE);
            levelLabel.setFont(FontManager.getRunescapeBoldFont());
            levelLabel.setForeground(LEVEL_COLOR);
            top.add(nameLabel, BorderLayout.WEST);
            top.add(levelLabel, BorderLayout.EAST);

            JPanel body = new JPanel(new BorderLayout(0, 4));
            body.setOpaque(false);
            body.add(progressBar, BorderLayout.NORTH);

            JPanel rows = new JPanel(new GridLayout(0, 2, 0, 1));
            rows.setOpaque(false);
            rows.add(rowLabel("XP Gained:"));
            rows.add(gainedValue);
            rows.add(remainingLabel);
            rows.add(remainingValue);
            rows.add(actionsLabel);
            rows.add(actionsValue);
            rows.add(rowLabel("Rate (XP/Hr):"));
            rows.add(perHourValue);
            rows.add(perMinuteLabel);
            rows.add(perMinuteValue);
            rows.add(rowLabel("TTL (Est Time):"));
            rows.add(ttlValue);
            body.add(rows, BorderLayout.CENTER);

            add(top, BorderLayout.NORTH);
            add(body, BorderLayout.CENTER);

            JPopupMenu menu = new JPopupMenu();
            pauseItem.addActionListener(e -> plugin.toggleSkillPause(skill));
            menu.add(pauseItem);
            menu.add(menuItem("Reset rate", () -> plugin.resetSkillRate(skill)));
            menu.add(menuItem("Reset " + skill.getName(), () -> plugin.resetSkill(skill)));
            menu.add(menuItem("Hide " + skill.getName(), () -> plugin.addIgnoredSkill(skill)));
            menu.addSeparator();
            menu.add(menuItem("Reset all skills", plugin::resetSession));
            setComponentPopupMenu(menu);
            setToolTipText("Right-click for options");
            inheritPopupMenu(this);
        }

        void refresh() {
            int xp = plugin.getCurrentSkillXp(skill);
            int level = Experience.getLevelForXp(xp);
            levelLabel.setText("Lvl " + level);

            int xpPerHour = plugin.getSkillXpPerHour(skill);
            boolean paused = plugin.isSkillPaused(skill);
            gainedValue.setText(NUMBER_FORMAT.format(plugin.getSkillGained(skill)));
            perHourValue.setText(paused ? "Paused" : NUMBER_FORMAT.format(xpPerHour) + " /hr");
            perMinuteValue.setText(NUMBER_FORMAT.format(plugin.getSkillXpPerMinute(skill)) + " /min");
            perMinuteLabel.setVisible(config.showXpPerMinute());
            perMinuteValue.setVisible(config.showXpPerMinute());

            if (level >= Experience.MAX_REAL_LEVEL) {
                progressBar.setProgress(1.0);
                remainingLabel.setText("XP Remaining:");
                remainingValue.setText("--");
                actionsValue.setText("--");
                ttlValue.setText("--");
            } else {
                int levelStart = Experience.getXpForLevel(level);
                int levelEnd = Experience.getXpForLevel(level + 1);
                int remaining = levelEnd - xp;
                progressBar.setProgress((double) (xp - levelStart) / (levelEnd - levelStart));
                remainingLabel.setText("XP to Lvl " + (level + 1) + ":");
                remainingValue.setText(NUMBER_FORMAT.format(remaining) + " XP");
                // How many more of the last action (its XP drop) until the next level
                int perAction = plugin.getLastXpDrop(skill);
                actionsValue.setText(perAction > 0 ? NUMBER_FORMAT.format((remaining + perAction - 1) / perAction) : "--");
                ttlValue.setText(xpPerHour > 0 && !paused ? formatDuration(remaining * 3600L / xpPerHour) : "--");
            }

            pauseItem.setText(paused ? "Resume tracking" : "Pause tracking");
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

        private JLabel rowLabel(String text) {
            JLabel label = new JLabel(text);
            label.setFont(FontManager.getRunescapeSmallFont());
            label.setForeground(LABEL_COLOR);
            return label;
        }

        private JLabel valueLabel() {
            JLabel label = new JLabel();
            label.setFont(FontManager.getRunescapeSmallFont());
            label.setForeground(Color.WHITE);
            label.setHorizontalAlignment(SwingConstants.RIGHT);
            return label;
        }
    }

    private static class LevelProgressBar extends JComponent {
        private double progress;

        LevelProgressBar() {
            setPreferredSize(new Dimension(0, 16));
            setFont(FontManager.getRunescapeSmallFont());
        }

        void setProgress(double progress) {
            double clamped = Math.max(0, Math.min(1, progress));
            if (clamped != this.progress) {
                this.progress = clamped;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            g.setColor(BAR_BACKGROUND);
            g.fillRect(0, 0, w, h);
            g.setColor(progress < 1 / 3.0 ? BAR_LOW : progress < 2 / 3.0 ? BAR_MID : BAR_HIGH);
            g.fillRect(0, 0, (int) (w * progress), h);

            String text = String.format("%.1f%%", progress * 100);
            FontMetrics fm = g.getFontMetrics();
            int x = (w - fm.stringWidth(text)) / 2;
            int y = (h - fm.getHeight()) / 2 + fm.getAscent();
            g.setColor(Color.BLACK);
            g.drawString(text, x + 1, y + 1);
            g.setColor(Color.WHITE);
            g.drawString(text, x, y);
        }
    }
}
