package com.lootpop;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JToolTip;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;

/** Real sprites inside RuneLite's positioned tooltip; no HTML image/network loading. */
final class HiddenItemsTooltip extends PanelComponent
{
    private final Font font;
    private final String heading;

    HiddenItemsTooltip(int count, List<ItemRow> rows)
    { this(count, rows, new Font(Font.SANS_SERIF, Font.PLAIN, 13)); }

    HiddenItemsTooltip(int count, List<ItemRow> rows, Font font)
    {
        this.font = font.deriveFont(Math.max(13f, font.getSize2D()));
        heading = "Hidden items (" + count + ")";
        setBackgroundColor(new Color(25, 28, 33, 245));
        setBorder(new Rectangle(4, 4, 4, 4));
        setGap(new Point(0, 2));
        getChildren().add(LineComponent.builder().left(heading).leftColor(Color.WHITE).build());
        getChildren().addAll(rows);
        if (count > rows.size())
        { getChildren().add(LineComponent.builder().left("+" + (count - rows.size()) + " more").leftColor(Color.LIGHT_GRAY).build()); }
    }

    @Override public Dimension render(Graphics2D graphics)
    {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setFont(font);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        FontMetrics metrics = g.getFontMetrics();
        int desired = metrics.stringWidth(heading) + 8;
        for (LayoutableRenderableEntity child : getChildren())
        { if (child instanceof ItemRow) { desired = Math.max(desired, ((ItemRow) child).naturalWidth(metrics) + 8); } }
        setPreferredSize(new Dimension(Math.max(180, Math.min(280, desired)), 0));
        // PanelComponent's background uses prior child dimensions. Measure before
        // painting so a newly constructed hover has its full background immediately.
        Graphics2D measure = (Graphics2D) g.create();
        measure.setClip(0, 0, 0, 0); super.render(measure); measure.dispose();
        Dimension size = super.render(g); g.dispose(); return size;
    }

    JToolTip swingTip(JComponent owner)
    {
        JToolTip tip = new JToolTip()
        {
            @Override public Dimension getPreferredSize()
            {
                Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
                Dimension size = HiddenItemsTooltip.this.render(g); g.dispose(); return size;
            }
            @Override protected void paintComponent(java.awt.Graphics graphics)
            {
                HiddenItemsTooltip.this.setPreferredLocation(new Point());
                HiddenItemsTooltip.this.render((Graphics2D) graphics);
            }
        };
        tip.setComponent(owner); tip.setOpaque(false); tip.setBorder(BorderFactory.createEmptyBorder());
        for (LayoutableRenderableEntity child : getChildren())
        {
            if (child instanceof ItemRow && ((ItemRow) child).image instanceof net.runelite.client.util.AsyncBufferedImage)
            { ((net.runelite.client.util.AsyncBufferedImage) ((ItemRow) child).image).onLoaded(tip::repaint); }
        }
        return tip;
    }

    static final class ItemRow implements LayoutableRenderableEntity
    {
        private final BufferedImage image;
        private final String name, value;
        private final Color colour;
        private final Rectangle bounds = new Rectangle();
        private Point location = new Point();
        private int width = 272;

        ItemRow(BufferedImage image, String name, String value, Color colour)
        { this.image = image; this.name = name; this.value = value; this.colour = colour; }

        String getName() { return name; }
        String getValue() { return value; }
        int naturalWidth(FontMetrics metrics)
        { return 28 + metrics.stringWidth(name) + 8 + metrics.stringWidth(value); }

        @Override public Dimension render(Graphics2D graphics)
        {
            Graphics2D g = (Graphics2D) graphics.create();
            FontMetrics metrics = g.getFontMetrics();
            int valueWidth = metrics.stringWidth(value);
            int available = width - 28 - valueWidth - 8;
            boolean twoLines = available < 65;
            int height = Math.max(24, (twoLines ? 2 : 1) * metrics.getHeight() + 2);
            int baseline = location.y + (twoLines ? metrics.getAscent() : (height + metrics.getAscent() - metrics.getDescent()) / 2);
            LootArtwork.draw(g, image, location.x, location.y + (height - 20) / 2, 22, 20);
            if (image == null) { text(g, "?", location.x + 7, location.y + height / 2 + 5, Color.LIGHT_GRAY); }
            String shown = fit(name, metrics, twoLines ? width - 28 : available);
            text(g, shown, location.x + 28, baseline, colour);
            text(g, value, location.x + Math.max(28, width - valueWidth),
                baseline + (twoLines ? metrics.getHeight() : 0), new Color(235, 238, 243));
            g.dispose(); bounds.setBounds(location.x, location.y, width, height); return bounds.getSize();
        }

        private static String fit(String name, FontMetrics metrics, int available)
        {
            if (metrics.stringWidth(name) <= available) { return name; }
            int end = name.length();
            while (end > 0 && metrics.stringWidth(name.substring(0, end) + "...") > available) { end--; }
            return end == 0 ? "" : name.substring(0, end) + "...";
        }

        private static void text(Graphics2D g, String text, int x, int y, Color colour)
        { g.setColor(Color.BLACK); g.drawString(text, x + 1, y + 1); g.setColor(colour); g.drawString(text, x, y); }
        @Override public Rectangle getBounds() { return bounds; }
        @Override public void setPreferredLocation(Point point) { location = new Point(point); }
        @Override public void setPreferredSize(Dimension size) { width = Math.max(50, size.width); }
    }
}
