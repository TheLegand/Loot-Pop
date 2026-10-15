package com.lootpop;

import java.awt.Dimension;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LootPopOverlayTest
{
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
