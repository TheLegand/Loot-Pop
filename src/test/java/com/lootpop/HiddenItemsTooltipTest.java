package com.lootpop;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;
import javax.swing.JToolTip;
import org.junit.Test;
import static org.junit.Assert.*;

public class HiddenItemsTooltipTest
{
    @Test public void firstFramePaintsRealSpritesAndTheEntireBackground() throws Exception
    {
        BufferedImage sprite = new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = sprite.createGraphics(); sg.setColor(Color.RED); sg.fillRect(0, 0, 36, 32); sg.dispose();
        HiddenItemsTooltip.ItemRow row = new HiddenItemsTooltip.ItemRow(sprite, "Dragon boots x3", "600,000 gp", Color.ORANGE);
        HiddenItemsTooltip tooltip = new HiddenItemsTooltip(1, Arrays.asList(row));
        tooltip.setPreferredLocation(new Point(20, 30));
        BufferedImage canvas = new BufferedImage(400, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics(); Font original = g.getFont();
        Dimension size = tooltip.render(g);
        assertEquals(original, g.getFont()); assertTrue(size.width >= 180 && size.width <= 280);
        Rectangle bounds = row.getBounds();
        assertEquals(Color.RED.getRGB(), canvas.getRGB(bounds.x + 10, bounds.y + 12));
        assertTrue((canvas.getRGB(20 + size.width - 6, 30 + size.height - 6) >>> 24) > 0);
        g.dispose();
    }

    @Test public void missingImagesLongNamesAndLargeValuesDoNotOverlapRows()
    {
        HiddenItemsTooltip.ItemRow first = new HiddenItemsTooltip.ItemRow(null,
            "A very long hidden item name x2,147,483,647", "4,611,686,014,132,420,609 gp", Color.CYAN);
        HiddenItemsTooltip.ItemRow second = new HiddenItemsTooltip.ItemRow(null, "Bones", "Price pending", Color.WHITE);
        HiddenItemsTooltip tooltip = new HiddenItemsTooltip(12, Arrays.asList(first, second));
        Graphics2D g = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB).createGraphics();
        Dimension size = tooltip.render(g); g.dispose();
        assertFalse(first.getBounds().intersects(second.getBounds()));
        assertTrue(first.getBounds().width <= size.width);
        assertTrue(second.getBounds().getMaxY() < size.height);
        assertEquals(4, tooltip.getChildren().size());
    }

    @Test public void sidebarUsesTheSameSpriteRowsAndExportsAnActualTooltipPreview() throws Exception
    {
        HiddenItemsTooltip tooltip = new HiddenItemsTooltip(3, Arrays.asList(
            new HiddenItemsTooltip.ItemRow(SealedDropRendererTest.sword(new Color(137, 203, 210)), "Rune blade x21", "21,000,000 gp", Color.CYAN),
            new HiddenItemsTooltip.ItemRow(SealedDropRendererTest.coins(), "Coins x2,500", "2,500 gp", Color.LIGHT_GRAY),
            new HiddenItemsTooltip.ItemRow(null, "Unpriced relic", "Price pending", Color.ORANGE)),
            new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        JToolTip swing = tooltip.swingTip(new javax.swing.JLabel());
        Dimension size = swing.getPreferredSize(); swing.setSize(size);
        BufferedImage canvas = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics(); swing.printAll(g); g.dispose();
        assertTrue(size.width >= 180 && size.width <= 280); assertTrue(size.height > 80);
        assertTrue((canvas.getRGB(size.width - 6, size.height - 6) >>> 24) > 0);
        Path file = Path.of("build", "tooltip-preview", "hidden-items.png");
        Files.createDirectories(file.getParent()); ImageIO.write(canvas, "png", file.toFile());
    }

    @Test public void widthFollowsItsContentsInsteadOfReservingAWideEmptyPriceColumn()
    {
        HiddenItemsTooltip small = new HiddenItemsTooltip(1, Arrays.asList(
            new HiddenItemsTooltip.ItemRow(null, "Bones", "40 gp", Color.WHITE)));
        HiddenItemsTooltip wide = new HiddenItemsTooltip(1, Arrays.asList(
            new HiddenItemsTooltip.ItemRow(null, "Dragon bones x21", "21,000,000 gp", Color.CYAN)));
        Graphics2D g = new BufferedImage(400, 200, BufferedImage.TYPE_INT_ARGB).createGraphics();
        Dimension smallSize = small.render(g), wideSize = wide.render(g); g.dispose();
        assertTrue(smallSize.width < wideSize.width);
        assertTrue(wideSize.width <= 280); assertTrue(wideSize.height < 65);
    }
}
