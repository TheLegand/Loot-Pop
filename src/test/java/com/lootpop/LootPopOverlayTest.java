package com.lootpop;

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
}
