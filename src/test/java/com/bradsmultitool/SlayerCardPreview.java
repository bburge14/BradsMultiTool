package com.bradsmultitool;

import com.google.gson.Gson;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;

public class SlayerCardPreview {
    public static void main(String[] args) throws Exception {
        SlayerData data = new SlayerData();
        data.load(new Gson());
        BufferedImage out = new BufferedImage(480, 330, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setColor(new Color(90, 80, 60));
        g.fillRect(0, 0, 480, 330);
        g.setFont(FontManager.getRunescapeSmallFont());

        SlayerData.Master m = data.bestMaster(90, 40);
        PanelComponent a = panel();
        a.getChildren().add(new PortraitHeaderComponent(data.picture(m.icon), m.name, new Color(0, 200, 83), "No task: get one here\n" + m.area));
        a.getChildren().add(TextBlockComponent.heading("Getting There", new Color(255, 185, 0)));
        for (String r : m.routes.subList(0, 2)) {
            a.getChildren().add(new TextBlockComponent(r, Color.WHITE, true));
        }
        a.getChildren().add(TextBlockComponent.heading("Next: Duradel/Kuradal (Combat 100, Slayer 50)", new Color(180, 180, 180)));
        g.translate(5, 5);
        a.render(g);

        SlayerData.Task t = data.task("Abyssal demons");
        PanelComponent b = panel();
        b.getChildren().add(new PortraitHeaderComponent(data.picture(t.icon), "Abyssal demons", new Color(255, 185, 0), "143 left"));
        b.getChildren().add(LineComponent.builder().left("Kill Rate:").right("312 /hr").build());
        b.getChildren().add(LineComponent.builder().left("Done In:").right("28m").build());
        b.getChildren().add(TextBlockComponent.heading("Where To Go", new Color(255, 185, 0)));
        for (SlayerData.Place p : t.places.subList(0, 3)) {
            b.getChildren().add(LineComponent.builder().left(p.name).leftColor(Color.WHITE).right(p.tags()).rightColor(new Color(180, 180, 180)).build());
        }
        b.getChildren().add(TextBlockComponent.heading("Getting To Catacombs of Kourend", new Color(255, 185, 0)));
        for (String r : data.travel("Catacombs of Kourend").subList(0, 2)) {
            b.getChildren().add(new TextBlockComponent(r, Color.WHITE, true));
        }
        g.translate(235, 0);
        b.render(g);
        ImageIO.write(out, "png", new java.io.File(args[0]));
    }

    private static PanelComponent panel() {
        PanelComponent p = new PanelComponent();
        p.setPreferredSize(new Dimension(210, 0));
        p.setGap(new java.awt.Point(0, 3));
        return p;
    }
}
