package com.bradsmultitool;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Renders the skill tracker's header box offline (at the sidebar's width) to check its layout. */
public class HeaderPreview {
    public static void main(String[] args) throws Exception {
        JPanel header = BradsMultiToolPanel.buildHeader(new JLabel("1,020"), new JLabel("83,454"), new JLabel("2"), () -> { }, () -> { });
        int width = 205;
        header.setSize(new Dimension(width, header.getPreferredSize().height));
        layout(header);
        BufferedImage out = new BufferedImage(width, header.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        header.printAll(g);
        BufferedImage big = new BufferedImage(width * 2, header.getHeight() * 2, BufferedImage.TYPE_INT_ARGB);
        big.createGraphics().drawImage(out, 0, 0, width * 2, header.getHeight() * 2, null);
        ImageIO.write(big, "png", new java.io.File(args[0]));
    }

    private static void layout(java.awt.Container c) {
        c.doLayout();
        for (java.awt.Component child : c.getComponents()) {
            if (child instanceof java.awt.Container) {
                layout((java.awt.Container) child);
            }
        }
    }
}
