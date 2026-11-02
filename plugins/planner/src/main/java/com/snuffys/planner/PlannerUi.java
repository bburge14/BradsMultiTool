package com.snuffys.planner;

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
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The Planner sidebar's building blocks, in RuneLite's own look (Time Tracking, Loot Tracker): cards on the darker
 * grey with a hover shade, a header line with a title, a summary and small flat icon buttons, a drawn tick, a thin
 * progress bar, the tab icons (drawn, so they're sharp at any scale) and centred empty-state text. Swing thread only.
 */
final class PlannerUi {

    static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;
    static final Color CARD_HOVER = ColorScheme.DARKER_GRAY_HOVER_COLOR;
    static final Color BACKGROUND = ColorScheme.DARK_GRAY_COLOR;
    /** Second lines and summaries. */
    static final Color MUTED = new Color(150, 150, 150);
    /** Done items and hints. */
    static final Color FAINT = new Color(105, 105, 105);
    static final Color READY = ColorScheme.PROGRESS_COMPLETE_COLOR;
    static final Color GROWING = ColorScheme.PROGRESS_INPROGRESS_COLOR;
    static final Color TRACK = new Color(52, 52, 52);
    /** The gap under each card. */
    static final int GAP = 4;
    /** An item picture's size (Constants.ITEM_SPRITE_WIDTH / HEIGHT). */
    static final int ICON_W = 36;
    static final int ICON_H = 32;
    /**
     * The width wrapped text is broken to (pixels): the list is 206 wide with its scroll bar showing, less the empty
     * text's padding.
     */
    static final int WRAP = 180;
    /** A to-do line's text width: 206 less the card's padding (14), the gap (7) and the tick (16), with room spare. */
    static final int TODO_WRAP = 160;

    private PlannerUi() {
    }

    // ---- Text ----

    static JLabel label(String text, Color colour) {
        JLabel label = new JLabel(text);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(colour);
        return label;
    }

    static JLabel bold(String text) {
        JLabel label = new JLabel(text);
        label.setFont(FontManager.getRunescapeBoldFont());
        label.setForeground(Color.WHITE);
        return label;
    }

    /**
     * The text as HTML broken into lines no wider than {@code width} pixels in the small game font (measured here, so
     * it never runs past the sidebar, whatever the platform makes of HTML widths), optionally centred or struck out.
     */
    static String wrap(String text, int width, boolean centre, boolean strike) {
        FontMetrics metrics = new JLabel().getFontMetrics(FontManager.getRunescapeSmallFont());
        StringBuilder out = new StringBuilder("<html>").append(centre ? "<center>" : "").append(strike ? "<s>" : "");
        StringBuilder line = new StringBuilder();
        boolean first = true;
        for (String word : text.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (metrics.stringWidth(next) > width && line.length() > 0) {
                out.append(first ? "" : "<br>").append(esc(line.toString()));
                first = false;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        out.append(first ? "" : "<br>").append(esc(line.toString()));
        return out.append(strike ? "</s>" : "").append(centre ? "</center>" : "").append("</html>").toString();
    }

    /** Short centred muted text that wraps (for empty lists). */
    static JComponent empty(String text) {
        JLabel label = new JLabel(wrap(text, WRAP, true, false), SwingConstants.CENTER);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(MUTED);
        JPanel box = new JPanel(new BorderLayout());
        box.setBackground(BACKGROUND);
        box.setBorder(new EmptyBorder(14, 4, 14, 4));
        box.add(label, BorderLayout.CENTER);
        return fullWidth(box);
    }

    static String esc(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ---- Headers ----

    /**
     * A tab's header line: the title in white bold, a muted summary on the right and the action buttons after it.
     * {@code summary} is kept so it can be changed without rebuilding.
     */
    static JPanel header(String title, JLabel summary, JComponent... actions) {
        return heading(bold(title), 0, 20, summary, actions);
    }

    /**
     * A page's header with a back arrow before the title (a monster's own page in Drops): the title trims with "..."
     * when long, so the summary and buttons keep their room.
     */
    static JPanel backHeader(String title, String backTip, Runnable back, JLabel summary, JComponent... actions) {
        JPanel name = new JPanel(new BorderLayout(3, 0));
        name.setOpaque(false);
        IconButton arrow = new IconButton(new Glyph(Shape.BACK, 14, ColorScheme.LIGHT_GRAY_COLOR), backTip, back);
        name.add(arrow, BorderLayout.WEST);
        JLabel text = bold(title);
        text.setMinimumSize(new Dimension(0, 0));
        name.add(text, BorderLayout.CENTER);
        return heading(name, 0, 20, summary, actions);
    }

    /** A second header further down a tab ("Weeklies", "Notes"). */
    static JPanel section(String title, JLabel summary, JComponent... actions) {
        return heading(bold(title), 10, 20, summary, actions);
    }

    /** A small label over a group of cards ("Farming", "Recent"), with a muted count on the right. */
    static JPanel group(String title, String summary, JComponent... actions) {
        return heading(label(title, ColorScheme.LIGHT_GRAY_COLOR), 4, 0, new JLabel(summary), actions);
    }

    private static JPanel heading(JComponent name, int above, int least, JLabel summary, JComponent... actions) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(BACKGROUND);
        row.setBorder(new EmptyBorder(above, 1, 5, 0));
        row.add(name, BorderLayout.WEST);
        summary.setHorizontalAlignment(SwingConstants.RIGHT);
        summary.setFont(FontManager.getRunescapeSmallFont());
        summary.setForeground(MUTED);
        // (lets the summary shrink and trim with "..." rather than push the buttons off)
        summary.setMinimumSize(new Dimension(0, 0));
        row.add(summary, BorderLayout.CENTER);
        if (actions.length > 0) {
            JPanel buttons = new JPanel();
            buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
            buttons.setOpaque(false);
            for (JComponent action : actions) {
                buttons.add(Box.createHorizontalStrut(2));
                buttons.add(action);
            }
            row.add(buttons, BorderLayout.EAST);
        }
        // Tall enough for the buttons, so headers with and without them line up
        row.setPreferredSize(new Dimension(10, Math.max(least, name.getPreferredSize().height) + above + 5));
        return fullWidth(row);
    }

    // ---- Cards ----

    /**
     * A card like RuneLite's TimeablePanel: an item picture on the left, two lines of text, something on the right
     * (a tick or a button), and optionally a thin progress bar along the bottom.
     */
    static final class Card extends JPanel {
        final JPanel top = new JPanel(new BorderLayout(7, 0));
        final JLabel icon = new JLabel();
        final JLabel title;
        final JLabel left;
        final JLabel right;
        /** Beside the title, on the right (empty unless set). */
        final JLabel corner;
        private boolean hovered;
        private boolean clickable;

        Card(String titleText, String leftText, String rightText) {
            super(new BorderLayout());
            setBackground(CARD);
            top.setOpaque(false);
            top.setBorder(new EmptyBorder(4, 4, 4, 6));
            title = label(titleText, Color.WHITE);
            left = label(leftText, MUTED);
            right = label(rightText == null ? "" : rightText, MUTED);
            right.setHorizontalAlignment(SwingConstants.RIGHT);
            corner = label("", MUTED);
            corner.setHorizontalAlignment(SwingConstants.RIGHT);
            title.setMinimumSize(new Dimension(0, 0));
            left.setMinimumSize(new Dimension(0, 0));
            JPanel second = new JPanel(new BorderLayout(4, 0));
            second.setOpaque(false);
            second.add(left, BorderLayout.CENTER);
            second.add(right, BorderLayout.EAST);
            JPanel info = new JPanel(new java.awt.GridLayout(2, 1));
            info.setOpaque(false);
            info.setBorder(new EmptyBorder(1, 0, 1, 0));
            JPanel first = new JPanel(new BorderLayout(4, 0));
            first.setOpaque(false);
            first.add(title, BorderLayout.CENTER);
            first.add(corner, BorderLayout.EAST);
            info.add(first);
            info.add(second);
            icon.setHorizontalAlignment(SwingConstants.CENTER);
            icon.setPreferredSize(new Dimension(ICON_W, ICON_H));
            top.add(icon, BorderLayout.WEST);
            top.add(info, BorderLayout.CENTER);
            add(top, BorderLayout.CENTER);
        }

        /** The third line's left and right text (null until {@link #third} adds it). */
        JLabel lower;
        JLabel lowerRight;

        /** A fourth line's left and right text (null until {@link #fourth} adds it). */
        JLabel last;
        JLabel lastRight;

        /** Adds a fourth line of muted text under the third (after {@link #third}). */
        Card fourth(String leftText, String rightText) {
            JPanel info = (JPanel) ((BorderLayout) top.getLayout()).getLayoutComponent(BorderLayout.CENTER);
            info.setLayout(new java.awt.GridLayout(info.getComponentCount() + 1, 1));
            last = label(leftText, MUTED);
            last.setMinimumSize(new Dimension(0, 0));
            lastRight = label(rightText == null ? "" : rightText, MUTED);
            lastRight.setHorizontalAlignment(SwingConstants.RIGHT);
            JPanel row = new JPanel(new BorderLayout(4, 0));
            row.setOpaque(false);
            row.add(last, BorderLayout.CENTER);
            row.add(lastRight, BorderLayout.EAST);
            info.add(row);
            return this;
        }

        /** Adds a third line of muted text under the second (left and right). */
        Card third(String leftText, String rightText) {
            JPanel info = (JPanel) ((BorderLayout) top.getLayout()).getLayoutComponent(BorderLayout.CENTER);
            info.setLayout(new java.awt.GridLayout(3, 1));
            lower = label(leftText, MUTED);
            lower.setMinimumSize(new Dimension(0, 0));
            lowerRight = label(rightText == null ? "" : rightText, MUTED);
            lowerRight.setHorizontalAlignment(SwingConstants.RIGHT);
            JPanel row = new JPanel(new BorderLayout(4, 0));
            row.setOpaque(false);
            row.add(lower, BorderLayout.CENTER);
            row.add(lowerRight, BorderLayout.EAST);
            info.add(row);
            return this;
        }

        /** One line of text only (no second line). */
        Card single() {
            JPanel info = (JPanel) ((BorderLayout) top.getLayout()).getLayoutComponent(BorderLayout.CENTER);
            java.awt.Component first = info.getComponent(0);
            info.removeAll();
            info.setLayout(new BorderLayout());
            info.add(first, BorderLayout.CENTER);
            return this;
        }

        Card east(JComponent component) {
            JPanel wrap = new JPanel(new java.awt.GridBagLayout());
            wrap.setOpaque(false);
            wrap.add(component);
            top.add(wrap, BorderLayout.EAST);
            return this;
        }

        Card bar(Bar bar) {
            add(bar, BorderLayout.SOUTH);
            return this;
        }

        /** Without the picture column (to-do lines). */
        Card noIcon() {
            top.remove(icon);
            top.setBorder(new EmptyBorder(6, 8, 6, 6));
            return this;
        }

        /** Greys it out (done). */
        Card dim() {
            title.setForeground(FAINT);
            left.setForeground(FAINT);
            right.setForeground(FAINT);
            corner.setForeground(FAINT);
            if (lower != null) {
                lower.setForeground(FAINT);
                lowerRight.setForeground(FAINT);
            }
            if (last != null) {
                last.setForeground(FAINT);
                lastRight.setForeground(FAINT);
            }
            return this;
        }

        /** Shades it while the mouse is over it, with a left-click action if given and a right-click menu if given. */
        Card interactive(Runnable click, JPopupMenu menu, String tooltip) {
            clickable = click != null;
            if (clickable) {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    setHovered(true);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), Card.this);
                    setHovered(new Rectangle(getSize()).contains(p));
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (click != null && SwingUtilities.isLeftMouseButton(e) && !(e.getComponent() instanceof IconButton)) {
                        click.run();
                    }
                }
            };
            everywhere(this, c -> {
                if (!(c instanceof IconButton)) {
                    c.addMouseListener(mouse);
                    if (c instanceof JComponent && c != this) {
                        ((JComponent) c).setInheritsPopupMenu(true);
                    }
                }
            });
            if (menu != null) {
                setComponentPopupMenu(menu);
            }
            if (tooltip != null) {
                tips(this, tooltip);
            }
            return this;
        }

        private void setHovered(boolean on) {
            if (hovered != on) {
                hovered = on;
                setBackground(on ? CARD_HOVER : CARD);
                everywhere(this, c -> {
                    if (c instanceof Tick) {
                        ((Tick) c).hover = on;
                        c.repaint();
                    }
                });
            }
        }
    }

    // ---- Small parts ----

    /** A ring that fills green with a tick when done. */
    static final class Tick extends JComponent {
        private final boolean done;
        boolean hover;

        Tick(boolean done) {
            this.done = done;
            setPreferredSize(new Dimension(16, 16));
            setMinimumSize(new Dimension(16, 16));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = smooth(graphics);
            double x = (getWidth() - 14) / 2.0;
            double y = (getHeight() - 14) / 2.0;
            Ellipse2D circle = new Ellipse2D.Double(x + 0.5, y + 0.5, 13, 13);
            if (done) {
                g.setColor(READY);
                g.fill(circle);
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D check = new Path2D.Double();
                check.moveTo(x + 3.8, y + 7.2);
                check.lineTo(x + 6, y + 9.6);
                check.lineTo(x + 10.3, y + 4.6);
                g.draw(check);
            } else {
                g.setColor(hover ? new Color(190, 190, 190) : new Color(120, 120, 120));
                g.setStroke(new BasicStroke(1.4f));
                g.draw(circle);
            }
            g.dispose();
        }
    }

    /** A thin progress bar (like Time Tracking's), 0 to 1. */
    static final class Bar extends JComponent {
        double value;
        Color colour;

        Bar(double value, Color colour) {
            this.value = value;
            this.colour = colour;
            setPreferredSize(new Dimension(10, 4));
        }

        void set(double value, Color colour) {
            if (value != this.value || !colour.equals(this.colour)) {
                this.value = value;
                this.colour = colour;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(TRACK);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(colour);
            g.fillRect(0, 0, (int) Math.round(getWidth() * Math.max(0, Math.min(1, value))), getHeight());
        }
    }

    /** A small flat button showing a glyph: shaded on hover, with a tooltip. */
    static final class IconButton extends JComponent {
        private final Icon icon;
        private boolean hovered;

        IconButton(Icon icon, String tooltip, Runnable action) {
            this.icon = icon;
            setPreferredSize(new Dimension(20, 20));
            setMinimumSize(new Dimension(20, 20));
            setMaximumSize(new Dimension(20, 20));
            setToolTipText(tooltip);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        action.run();
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = smooth(graphics);
            if (hovered) {
                g.setColor(new Color(255, 255, 255, 28));
                g.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 5, 5));
            }
            g.dispose();
            icon.paintIcon(this, graphics, (getWidth() - icon.getIconWidth()) / 2, (getHeight() - icon.getIconHeight()) / 2);
        }
    }

    /** A text link ("Show hidden (2)"): muted, white under the mouse, on its own line at the right. */
    static JComponent link(String text, String tooltip, Runnable action) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(BACKGROUND);
        row.setBorder(new EmptyBorder(2, 0, 4, 2));
        row.add(linkLabel(text, tooltip, action), BorderLayout.EAST);
        return fullWidth(row);
    }

    /** The link's text alone, to put in a header. */
    static JLabel linkLabel(String text, String tooltip, Runnable action) {
        JLabel label = label(text, MUTED);
        label.setToolTipText(tooltip);
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                label.setForeground(Color.WHITE);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                label.setForeground(MUTED);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    action.run();
                }
            }
        });
        return label;
    }

    // ---- Layout helpers ----

    /** A card with the usual gap under it, as wide as the list. */
    static JComponent spaced(JComponent card) {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(BACKGROUND);
        wrap.setBorder(new EmptyBorder(0, 0, GAP, 0));
        wrap.add(card, BorderLayout.CENTER);
        return fullWidth(wrap);
    }

    static <T extends JComponent> T fullWidth(T component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
        return component;
    }

    /** A vertical list that's always as wide as its scroll pane (so nothing ever scrolls sideways). */
    static final class Column extends JPanel implements Scrollable {
        Column() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBackground(BACKGROUND);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(16, visible.height - 32);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    static void tips(Container root, String tooltip) {
        everywhere(root, c -> {
            if (c instanceof JComponent && !(c instanceof IconButton)) {
                ((JComponent) c).setToolTipText(tooltip);
            }
        });
    }

    static void everywhere(Component root, java.util.function.Consumer<Component> what) {
        what.accept(root);
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                everywhere(child, what);
            }
        }
    }

    static Graphics2D smooth(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    /** A picture centred on an item-sized canvas, faded when asked (done lines). */
    static BufferedImage fit(BufferedImage image, float alpha) {
        if (image == null) {
            return null;
        }
        int w = Math.max(ICON_W, image.getWidth());
        int h = Math.max(ICON_H, image.getHeight());
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        if (alpha < 1) {
            g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha));
        }
        g.drawImage(image, (w - image.getWidth()) / 2, (h - image.getHeight()) / 2, null);
        g.dispose();
        return out;
    }

    static javax.swing.border.Border line(Color colour) {
        return BorderFactory.createLineBorder(colour);
    }

    // ---- Glyphs ----

    /** The drawn icons: the tabs', the header buttons' and the timer buttons'. */
    enum Shape {
        PLUS, PLAY, STOP, AGAIN, TRASH, CHECKLIST, CLOCK, SKULL, BAG, NOTE, BACK, CALCULATOR, SEARCH, PIN
    }

    static final class Glyph implements Icon {
        private final Shape shape;
        private final int size;
        private final Color colour;

        Glyph(Shape shape, int size, Color colour) {
            this.shape = shape;
            this.size = size;
            this.colour = colour;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g = smooth(graphics);
            g.translate(x, y);
            // Drawn on a 16-unit grid
            double s = size / 16.0;
            g.scale(s, s);
            g.setColor(colour);
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (shape) {
                case PLUS:
                    g.draw(new Line2D.Double(8, 3, 8, 13));
                    g.draw(new Line2D.Double(3, 8, 13, 8));
                    break;
                case PLAY: {
                    Path2D p = new Path2D.Double();
                    p.moveTo(5, 3);
                    p.lineTo(13, 8);
                    p.lineTo(5, 13);
                    p.closePath();
                    g.fill(p);
                    break;
                }
                case STOP:
                    g.fill(new RoundRectangle2D.Double(4, 4, 8, 8, 2, 2));
                    break;
                case AGAIN: {
                    g.draw(new Arc2D.Double(3, 3, 10, 10, 90, 270, Arc2D.OPEN));
                    Path2D p = new Path2D.Double();
                    p.moveTo(8, 0.6);
                    p.lineTo(11, 3);
                    p.lineTo(8, 5.4);
                    p.closePath();
                    g.fill(p);
                    break;
                }
                case TRASH:
                    g.draw(new Line2D.Double(3, 4.5, 13, 4.5));
                    g.draw(new Line2D.Double(6.5, 2.5, 9.5, 2.5));
                    g.draw(new RoundRectangle2D.Double(4.5, 4.5, 7, 9, 2, 2));
                    g.draw(new Line2D.Double(7, 7, 7, 11));
                    g.draw(new Line2D.Double(9, 7, 9, 11));
                    break;
                case CHECKLIST:
                    for (int i = 0; i < 3; i++) {
                        double yy = 3.5 + i * 4.5;
                        Path2D tick = new Path2D.Double();
                        tick.moveTo(1.5, yy);
                        tick.lineTo(3, yy + 1.5);
                        tick.lineTo(5.5, yy - 1.5);
                        g.draw(tick);
                        g.draw(new Line2D.Double(8, yy, 14.5, yy));
                    }
                    break;
                case CLOCK:
                    g.draw(new Ellipse2D.Double(2, 2, 12, 12));
                    g.draw(new Line2D.Double(8, 8, 8, 4.5));
                    g.draw(new Line2D.Double(8, 8, 10.5, 9.5));
                    break;
                case SKULL: {
                    Path2D head = new Path2D.Double();
                    head.moveTo(3, 9.5);
                    head.curveTo(1.5, 2, 14.5, 2, 13, 9.5);
                    head.lineTo(11.5, 10.5);
                    head.lineTo(11.5, 13.5);
                    head.lineTo(4.5, 13.5);
                    head.lineTo(4.5, 10.5);
                    head.closePath();
                    g.fill(head);
                    Color hole = c == null ? CARD : c.getBackground();
                    g.setColor(hole == null ? CARD : hole);
                    g.fill(new Ellipse2D.Double(4.6, 6.3, 2.8, 2.8));
                    g.fill(new Ellipse2D.Double(8.6, 6.3, 2.8, 2.8));
                    g.fill(new Rectangle(7, 12, 1, 2));
                    g.fill(new Rectangle(9, 12, 1, 2));
                    break;
                }
                case BAG: {
                    Path2D bag = new Path2D.Double();
                    bag.moveTo(5.5, 5);
                    bag.curveTo(1.5, 8, 1.5, 14, 8, 14);
                    bag.curveTo(14.5, 14, 14.5, 8, 10.5, 5);
                    bag.closePath();
                    g.fill(bag);
                    g.draw(new Line2D.Double(5.5, 3.5, 10.5, 3.5));
                    g.draw(new Line2D.Double(6, 2, 8, 3.5));
                    g.draw(new Line2D.Double(10, 2, 8, 3.5));
                    break;
                }
                case NOTE:
                    g.draw(new RoundRectangle2D.Double(3, 1.8, 10, 12.4, 2, 2));
                    g.draw(new Line2D.Double(5.5, 5.5, 10.5, 5.5));
                    g.draw(new Line2D.Double(5.5, 8.2, 10.5, 8.2));
                    g.draw(new Line2D.Double(5.5, 10.9, 8.5, 10.9));
                    break;
                case CALCULATOR:
                    g.draw(new RoundRectangle2D.Double(3, 1.5, 10, 13, 2.5, 2.5));
                    g.fill(new RoundRectangle2D.Double(5, 3.6, 6, 2.6, 1, 1));
                    for (int row = 0; row < 2; row++) {
                        for (int col = 0; col < 3; col++) {
                            g.fill(new Ellipse2D.Double(4.9 + col * 2.4, 8.3 + row * 2.8, 1.5, 1.5));
                        }
                    }
                    break;
                case PIN: {
                    // A push pin leaning left: its round head, the collar, and the needle
                    g.fill(new Ellipse2D.Double(8, 2, 6, 6));
                    g.draw(new Line2D.Double(6, 6.5, 10.5, 11));
                    g.draw(new Line2D.Double(8, 9, 3, 14));
                    break;
                }
                case SEARCH:
                    g.draw(new Ellipse2D.Double(2.5, 2.5, 8, 8));
                    g.draw(new Line2D.Double(9.4, 9.4, 13.5, 13.5));
                    break;
                case BACK: {
                    Path2D chevron = new Path2D.Double();
                    chevron.moveTo(10, 3.5);
                    chevron.lineTo(5.5, 8);
                    chevron.lineTo(10, 12.5);
                    g.draw(chevron);
                    break;
                }
                default:
                    break;
            }
            g.dispose();
        }
    }

    static BufferedImage picture(Icon icon) {
        BufferedImage out = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        icon.paintIcon(null, g, 0, 0);
        g.dispose();
        return out;
    }
}
