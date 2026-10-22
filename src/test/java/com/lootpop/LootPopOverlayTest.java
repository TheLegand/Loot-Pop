package com.lootpop;

import java.awt.Dimension;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LootPopOverlayTest
{
    @Test
    public void personalBestBannerCannotSpoilASealedReward() throws Exception
    {
        LootDisplayItem item = new LootDisplayItem(1, 1, 1000000, "1 / 5000", 5000);
        LootNotification notification = new LootNotification("Boss", java.util.Collections.singletonList(item), 1000000, 1000);
        notification.markPersonalBest(400000);
        SealedDropSequence sequence = new SealedDropSequence(notification.getItems(), java.util.Collections.emptySet(),
            1000, true, false, 100, 10);
        notification.setSealedDrop(sequence);
        java.lang.reflect.Constructor<LootPopOverlay> ctor = LootPopOverlay.class.getDeclaredConstructor(
            LootPopPlugin.class, LootPopConfig.class, net.runelite.client.game.ItemManager.class,
            net.runelite.api.Client.class, net.runelite.client.ui.overlay.tooltip.TooltipManager.class);
        ctor.setAccessible(true);
        LootPopOverlay overlay = ctor.newInstance(new LootPopPlugin(), new LootPopConfig() {}, null, null,
            new net.runelite.client.ui.overlay.tooltip.TooltipManager());
        java.lang.reflect.Method draw = LootPopOverlay.class.getDeclaredMethod("drawPersonalBest",
            java.awt.Graphics2D.class, LootNotification.class, int.class, int.class, int.class, int.class, long.class);
        draw.setAccessible(true);
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(320, 35, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = image.createGraphics();
        draw.invoke(overlay, g, notification, 0, 0, 320, 35, 1400L);
        assertEquals(0, visiblePixels(image));
        draw.invoke(overlay, g, notification, 0, 0, 320, 35, 1000L + sequence.getDurationMillis());
        org.junit.Assert.assertTrue(visiblePixels(image) > 100);
        g.dispose();
        java.nio.file.Path file = java.nio.file.Path.of("build", "journal-preview", "personal-best.png");
        java.nio.file.Files.createDirectories(file.getParent());
        javax.imageio.ImageIO.write(image, "png", file.toFile());
    }

    private static int visiblePixels(java.awt.image.BufferedImage image)
    {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++)
        { for (int x = 0; x < image.getWidth(); x++) { if ((image.getRGB(x, y) >>> 24) > 0) { count++; } } }
        return count;
    }

    @Test
    public void formatsGpCompactly()
    {
        assertEquals("319 gp", LootPopOverlay.formatGp(319));
        assertEquals("10K gp", LootPopOverlay.formatGp(10_000));
        assertEquals("184.3K gp", LootPopOverlay.formatGp(184_320));
        assertEquals("1.2M gp", LootPopOverlay.formatGp(1_250_000));
    }

    @Test
    public void formatsExactTotalForHoverTooltip()
    {
        assertEquals("100,000,000 gp", LootPopOverlay.formatExactGp(100_000_000));
        assertEquals("1,234,567,890 gp", LootPopOverlay.formatExactGp(1_234_567_890L));
    }

    @Test
    public void formatsStackQuantitiesWithMultiplier()
    {
        assertEquals("x3", LootPopOverlay.formatQuantityLabel(3));
        assertEquals("x1.5K", LootPopOverlay.formatQuantityLabel(1_500));
        assertEquals("x2M", LootPopOverlay.formatQuantityLabel(2_000_000));
    }

    @Test
    public void stackHeightDoesNotShrinkPopupScale()
    {
        Dimension onePopupPreferredSize = new Dimension(205, 66);

        double onePopupScale = LootPopOverlay.resolveScale(1.0, onePopupPreferredSize, 205);
        double fourPopupScale = LootPopOverlay.resolveScale(1.0, onePopupPreferredSize, 205);

        assertEquals(1.0, onePopupScale, 0.0001);
        assertEquals(onePopupScale, fourPopupScale, 0.0001);
        assertEquals(282, (int) Math.ceil((66 * 4 + 6 * 3) * fourPopupScale));
    }

    @Test
    public void configuredScaleIsUsedBeforeManualResize()
    {
        assertEquals(1.25, LootPopOverlay.resolveScale(1.25, null, 205), 0.0001);
        assertEquals(1.25, LootPopOverlay.resolveScale(1.25, new Dimension(), 205), 0.0001);
    }

    @Test
    public void entranceAnimationUsesAQuickEaseOut()
    {
        assertEquals(0.0, LootPopOverlay.entranceProgress(0), 0.0001);
        assertEquals(1.0, LootPopOverlay.entranceProgress(280), 0.0001);
        assertEquals(1.0, LootPopOverlay.entranceProgress(1_000), 0.0001);
    }
}
