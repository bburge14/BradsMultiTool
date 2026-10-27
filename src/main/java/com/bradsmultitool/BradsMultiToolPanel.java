package com.bradsmultitool;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

public class BradsMultiToolPanel extends PluginPanel {

    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance();

    private final BradsMultiToolPlugin plugin;
    private final BradsMultiToolConfig config;
    private final SkillIconManager skillIconManager;

    private final JLabel totalXpLabel = new JLabel();
    private final JLabel emptyLabel = new JLabel("Gain XP to start tracking skills.");
    private final JPanel cardsContainer = new JPanel();
    private final Map<Skill, SkillCard> cards = new EnumMap<>(Skill.class);

    public BradsMultiToolPanel(BradsMultiToolPlugin plugin, BradsMultiToolConfig config, SkillIconManager skillIconManager) {
        super(false);
        this.plugin = plugin;
        this.config = config;
        this.skillIconManager = skillIconManager;

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel title = new JLabel("Brad's Multi-Tool");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(new Color(0, 220, 220));
        header.add(title);

        totalXpLabel.setFont(FontManager.getRunescapeSmallFont());
        totalXpLabel.setForeground(Color.WHITE);
        totalXpLabel.setBorder(new EmptyBorder(4, 0, 6, 0));
        header.add(totalXpLabel);

        JPanel buttons = new JPanel(new GridLayout(1, 2, 6, 0));
        buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JButton resetSession = new JButton("Reset Session");
        resetSession.addActionListener(e -> plugin.resetSession());
        JButton testVoice = new JButton("Test Voice");
        testVoice.addActionListener(e -> plugin.testVoiceAlert());
        buttons.add(resetSession);
        buttons.add(testVoice);
        header.add(buttons);

        cardsContainer.setLayout(new BoxLayout(cardsContainer, BoxLayout.Y_AXIS));
        cardsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        cardsContainer.setBorder(new EmptyBorder(10, 0, 0, 0));

        emptyLabel.setFont(FontManager.getRunescapeSmallFont());
        emptyLabel.setForeground(Color.GRAY);
        cardsContainer.add(emptyLabel);

        add(header, BorderLayout.NORTH);
        add(cardsContainer, BorderLayout.CENTER);

        updatePanel();
    }

    /**
     * Must be called on the Swing EDT.
     */
    public void updatePanel() {
        totalXpLabel.setText("Total XP gained: " + NUMBER_FORMAT.format(plugin.getTotalCombatXp()));

        List<Skill> tracked = plugin.getTrackedSkills();
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

    private class SkillCard extends JPanel {
        private final Skill skill;
        private final JLabel gainedLabel = new JLabel();
        private final JLabel rateLabel = new JLabel();
        private final JLabel lastDropLabel = new JLabel();
        private final JButton pauseButton = new JButton();

        SkillCard(Skill skill) {
            this.skill = skill;
            setLayout(new BorderLayout(8, 0));
            setBackground(ColorScheme.DARKER_GRAY_COLOR);
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 6, 0, ColorScheme.DARK_GRAY_COLOR),
                new EmptyBorder(6, 6, 6, 6)));

            BufferedImage icon = skillIconManager.getSkillImage(skill, false);
            JLabel iconLabel = new JLabel(icon != null ? new ImageIcon(icon) : null);
            iconLabel.setPreferredSize(new Dimension(28, 28));
            add(iconLabel, BorderLayout.WEST);

            JPanel stats = new JPanel(new GridLayout(4, 1));
            stats.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            JLabel nameLabel = new JLabel(skill.getName());
            nameLabel.setFont(FontManager.getRunescapeBoldFont());
            nameLabel.setForeground(new Color(255, 185, 0));
            stats.add(nameLabel);
            for (JLabel label : new JLabel[]{gainedLabel, rateLabel, lastDropLabel}) {
                label.setFont(FontManager.getRunescapeSmallFont());
                label.setForeground(Color.WHITE);
                stats.add(label);
            }
            add(stats, BorderLayout.CENTER);

            JPanel actions = new JPanel(new GridLayout(2, 2, 2, 2));
            actions.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            pauseButton.addActionListener(e -> plugin.toggleSkillPause(skill));
            JButton rateButton = smallButton("Rate", "Restart the XP/hr measurement from now");
            rateButton.addActionListener(e -> plugin.resetSkillRate(skill));
            JButton resetButton = smallButton("Reset", "Stop tracking this skill until it gains XP again");
            resetButton.addActionListener(e -> plugin.resetSkill(skill));
            JButton ignoreButton = smallButton("Hide", "Add this skill to the ignored list");
            ignoreButton.addActionListener(e -> plugin.addIgnoredSkill(skill));
            styleSmall(pauseButton);
            actions.add(pauseButton);
            actions.add(rateButton);
            actions.add(resetButton);
            actions.add(ignoreButton);
            add(actions, BorderLayout.EAST);
        }

        void refresh() {
            gainedLabel.setText("Gained: " + NUMBER_FORMAT.format(plugin.getSkillGained(skill)));
            String rate = NUMBER_FORMAT.format(plugin.getSkillXpPerHour(skill)) + " XP/hr";
            if (config.showXpPerMinute()) {
                rate += " (" + NUMBER_FORMAT.format(plugin.getSkillXpPerMinute(skill)) + "/min)";
            }
            rateLabel.setText(rate);
            lastDropLabel.setText("Last drop: " + NUMBER_FORMAT.format(plugin.getLastXpDrop(skill)));

            boolean paused = plugin.isSkillPaused(skill);
            pauseButton.setText(paused ? "Go" : "Pause");
            pauseButton.setToolTipText(paused ? "Resume XP/hr timing" : "Pause XP/hr timing");
            rateLabel.setForeground(paused ? Color.GRAY : Color.WHITE);
        }

        private JButton smallButton(String text, String tooltip) {
            JButton button = new JButton(text);
            button.setToolTipText(tooltip);
            styleSmall(button);
            return button;
        }

        private void styleSmall(JButton button) {
            button.setFont(FontManager.getRunescapeSmallFont());
            button.setMargin(new java.awt.Insets(0, 2, 0, 2));
            button.setFocusPainted(false);
        }
    }
}
