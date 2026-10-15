package com.lootpop;

import java.awt.image.BufferedImage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LootPopPanelTest
{
    @Test
    public void createsEncodedWikiSearchUrl()
    {
        assertEquals(
            "https://oldschool.runescape.wiki/w/Special:Search?search=Dragon+bones",
            LootPopPanel.wikiSearchUrl("Dragon bones")
        );
    }

    @Test
    public void encodesWikiSpecialCharacters()
    {
        assertEquals(
            "https://oldschool.runescape.wiki/w/Special:Search?search=TzTok-Jad+%26+friends",
            LootPopPanel.wikiSearchUrl("TzTok-Jad & friends")
        );
    }

    @Test
    public void formatsPanelStackQuantities()
    {
        assertEquals("x15", LootPopPanel.formatPanelQuantity(15));
        assertEquals("x1.5K", LootPopPanel.formatPanelQuantity(1_500));
        assertEquals("x55K", LootPopPanel.formatPanelQuantity(55_000));
        assertEquals("x2.5M", LootPopPanel.formatPanelQuantity(2_500_000));
    }

    @Test
    public void createsBrandedNavigationIconAtRuneLiteSize()
    {
        BufferedImage icon = LootPopPanel.createNavigationIcon();
        assertEquals(32, icon.getWidth());
        assertEquals(32, icon.getHeight());
    }

    @Test
    public void trimsTransparentNavigationIconPaddingWithoutChangingAspectRatio()
    {
        BufferedImage source = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        for (int y = 5; y < 15; y++)
        {
            for (int x = 6; x < 14; x++)
            {
                source.setRGB(x, y, 0xFFFF0000);
            }
        }

        BufferedImage trimmed = LootPopPanel.trimTransparentPadding(source);

        assertEquals(10, trimmed.getWidth());
        assertEquals(10, trimmed.getHeight());
    }
}
