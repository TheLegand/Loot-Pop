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
}
