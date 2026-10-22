package com.lootpop;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;

public class LootArtworkTest
{
    @Test public void originalOverflowBadgeFitsOneSlotAndPreservesGraphicsState()
    {
        BufferedImage canvas = new BufferedImage(70, 70, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics(); java.awt.geom.AffineTransform original = g.getTransform();
        g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 13));
        g.setColor(Color.CYAN); java.awt.Composite alpha = g.getComposite();
        LootArtwork.drawOverflowBadge(g, 3, 10, 10, Color.RED);
        assertEquals(original, g.getTransform()); assertEquals(alpha, g.getComposite());
        assertEquals(Color.CYAN, g.getColor()); g.dispose();
        int whitePixels = 0, darkPixels = 0;
        for (int y = 0; y < canvas.getHeight(); y++)
        { for (int x = 0; x < canvas.getWidth(); x++)
            { if ((canvas.getRGB(x, y) >>> 24) > 0)
                {
                    assertTrue(x >= 14 && x < 41 && y >= 13 && y < 40);
                    if (canvas.getRGB(x, y) == Color.WHITE.getRGB()) { whitePixels++; }
                    if ((canvas.getRGB(x, y) & 0xffffff) == 0) { darkPixels++; }
                } } }
        assertTrue(whitePixels > 0); assertTrue(darkPixels > 0);
        assertEquals(36, LootArtwork.OVERFLOW_WIDTH); assertEquals(32, LootArtwork.OVERFLOW_HEIGHT);
    }

    @Test public void popupIconsAreSmallerButStayCenteredInTheirOriginalSlots()
    {
        java.awt.Rectangle bounds = LootArtwork.popupBounds(10, 20);
        assertEquals(new java.awt.Rectangle(12, 22, 32, 28), bounds);
        assertEquals(28, bounds.getCenterX(), .001);
        assertEquals(36, bounds.getCenterY(), .001);
    }

    @Test public void preservesOriginalPixelsAndAddsOnlyADarkContour()
    {
        BufferedImage source = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(1, 1, Color.YELLOW.getRGB());
        BufferedImage target = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = target.createGraphics(); LootArtwork.draw(g, source, 2, 2, 4, 4); g.dispose();
        assertEquals(Color.YELLOW.getRGB(), target.getRGB(3, 3));
        assertTrue((target.getRGB(2, 3) >>> 24) > 0);
        assertEquals(0, target.getRGB(0, 0));
        assertEquals(Color.YELLOW.getRGB(), source.getRGB(1, 1));
        assertEquals(0, source.getRGB(0, 1));
    }

    @Test public void doesNotCacheAnAsyncImagesBlankLoadingState()
    {
        BufferedImage source = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        BufferedImage target = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = target.createGraphics();
        LootArtwork.draw(g, source, 2, 2, 4, 4);
        assertEquals(0, target.getRGB(2, 3));
        source.setRGB(1, 1, Color.CYAN.getRGB());
        LootArtwork.draw(g, source, 2, 2, 4, 4); g.dispose();
        assertEquals(Color.CYAN.getRGB(), target.getRGB(3, 3));
        assertTrue((target.getRGB(2, 3) >>> 24) > 0);
    }

    @Test public void drawingDoesNotChangeTheCallersTransformOrOpacity()
    {
        BufferedImage source = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(1, 1, Color.YELLOW.getRGB());
        Graphics2D g = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB).createGraphics();
        g.translate(1, 1); g.setComposite(java.awt.AlphaComposite.SrcOver.derive(.5f));
        java.awt.geom.AffineTransform before = g.getTransform(); java.awt.Composite alpha = g.getComposite();
        LootArtwork.draw(g, source, 0, 0, 4, 4);
        assertEquals(before, g.getTransform()); assertEquals(alpha, g.getComposite()); g.dispose();
    }
}
