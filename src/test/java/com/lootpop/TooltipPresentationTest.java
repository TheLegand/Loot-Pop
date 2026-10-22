package com.lootpop;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ColorUtil;
import static org.junit.Assert.*;

public class TooltipPresentationTest
{
    static LootPopOverlay overlay(LootPopConfig config)
    {
        LootPopPlugin plugin = new LootPopPlugin()
        { @Override DropRarity rarityForRate(double rate) { return DropRarity.ULTRA_RARE; } };
        return new LootPopOverlay(plugin, config, null, null, new TooltipManager(),
            item -> SealedDropRendererTest.coins(), item -> "Reward " + item.getItemId());
    }

    @Test public void oneItemHasNeitherQuantityNorStackButHasColouredUnitPrices()
    {
        LootPopOverlay overlay = overlay(new LootPopConfig() {});
        String text = overlay.buildItemTooltip(new LootDisplayItem(1, 1, 50, "1 / 5000", 5000), "Relic", 50, 70, false);
        assertFalse(text.contains("Qty:")); assertFalse(text.contains("Stack:"));
        assertTrue(text.contains(ColorUtil.wrapWithColorTag("50 gp", LootTooltipStyle.rarity(DropRarity.ULTRA_RARE, RarityPalette.DEFAULT))));
        assertTrue(text.contains(ColorUtil.wrapWithColorTag("70 gp", LootTooltipStyle.ALCH_PROFIT)));
    }

    @Test public void quantityAndStackAppearForDuplicatesAndHaComparesUnitNotStack()
    {
        String text = overlay(new LootPopConfig() {}).buildItemTooltip(
            new LootDisplayItem(1, 100, 50), "Relic", 50, 70, false);
        assertTrue(text.contains("Qty:")); assertTrue(text.contains("Stack:")); assertTrue(text.contains("5,000 gp"));
        assertTrue(text.contains(ColorUtil.wrapWithColorTag("70 gp", LootTooltipStyle.ALCH_PROFIT)));
        assertEquals(Color.WHITE, LootTooltipStyle.highAlchemy(50, 50, false));
        assertEquals(Color.WHITE, LootTooltipStyle.highAlchemy(50, 10, false));
        assertEquals(Color.WHITE, LootTooltipStyle.highAlchemy(0, 70, true));
    }

    @Test public void pendingPriceDoesNotPretendToBeAnAlchProfit()
    {
        String text = overlay(new LootPopConfig() {}).buildItemTooltip(
            new LootDisplayItem(1, 2, 0, null, 0, true), "New item", 0, 70, true);
        assertTrue(text.contains("Price pending"));
        assertTrue(text.contains(ColorUtil.wrapWithColorTag("70 gp", Color.WHITE)));
        assertFalse(text.contains(ColorUtil.wrapWithColorTag("70 gp", LootTooltipStyle.ALCH_PROFIT)));
    }

    @Test public void compactCustomModesAndColourblindPaletteRemainSupported()
    {
        LootDisplayItem item = new LootDisplayItem(1, 2, 50);
        String compact = overlay(new LootPopConfig() {
            @Override public HoverDetailMode hoverDetailMode() { return HoverDetailMode.COMPACT; }
        }).buildItemTooltip(item, "Relic", 50, 70, false);
        assertFalse(compact.contains("GE:")); assertTrue(compact.contains("Stack:"));
        String custom = overlay(new LootPopConfig() {
            @Override public HoverDetailMode hoverDetailMode() { return HoverDetailMode.CUSTOM; }
            @Override public boolean hoverShowStackValue() { return false; }
            @Override public boolean hoverShowDropChance() { return false; }
            @Override public boolean hoverShowPrices() { return true; }
            @Override public RarityPalette rarityPalette() { return RarityPalette.COLOURBLIND; }
        }).buildItemTooltip(item, "Relic", 50, 70, false);
        assertFalse(custom.contains("Stack:")); assertFalse(custom.contains("Chance:"));
        assertTrue(custom.contains(ColorUtil.wrapWithColorTag("50 gp", LootTooltipStyle.rarity(DropRarity.ULTRA_RARE, RarityPalette.COLOURBLIND))));
    }

    @Test public void sidePanelUsesTheSameRulesAndEscapesNames()
    {
        LootHistoryItem single = history(1, 50, 70);
        String one = LootPopPanel.itemTooltip(single, RarityPalette.DEFAULT);
        assertFalse(one.contains("Qty:")); assertFalse(one.contains("Stack:"));
        assertTrue(one.contains("&lt;Relic&gt; &amp; stone"));
        assertTrue(one.contains("<font color='#69d17d'>70 gp</font>"));
        String stack = LootPopPanel.itemTooltip(history(2, 50, 50), RarityPalette.DEFAULT);
        assertTrue(stack.contains("Qty:")); assertTrue(stack.contains("Stack:"));
        assertTrue(stack.contains("<font color='#ffffff'>50 gp</font>"));
    }

    @Test public void groupedPricesUseMatchedObservationsNotUnrelatedRangeEndpoints()
    {
        LootHistoryEntry first = entry(history(1, 100, 150));
        LootHistoryEntry second = entry(history(1, 200, 250));
        LootDaySummary.Item combined = LootDaySummary.items(Arrays.asList(first, second)).get(0);
        assertTrue(combined.allHaAboveGe);
        String text = LootPopPanel.dailyTooltip(combined, RarityPalette.DEFAULT);
        assertTrue(text.contains("Qty:")); assertTrue(text.contains("Stack:"));
        assertTrue(text.contains("color='#69d17d'>150 gp"));
        LootDaySummary.Item mixed = LootDaySummary.items(Arrays.asList(first, entry(history(1, 200, 190)))).get(0);
        assertFalse(mixed.allHaAboveGe);
        assertTrue(LootPopPanel.dailyTooltip(mixed, RarityPalette.DEFAULT).contains("color='#ffffff'>150 gp"));
        String one = LootPopPanel.dailyTooltip(LootDaySummary.items(Collections.singletonList(first)).get(0), RarityPalette.DEFAULT);
        assertFalse(one.contains("Qty:")); assertFalse(one.contains("Stack:"));
    }

    @Test public void hiddenRowsKeepUnknownPricesExplicitAndLimitThePreviewList()
    {
        java.util.List<LootDisplayItem> items = new java.util.ArrayList<>();
        items.add(new LootDisplayItem(1, 1, 0, null, 0, true));
        for (int id = 2; id <= 12; id++) { items.add(new LootDisplayItem(id, id, 50)); }
        HiddenItemsTooltip tooltip = overlay(new LootPopConfig() {}).buildHiddenItemsTooltip(items, 0);
        assertEquals(10, tooltip.getChildren().size()); // title + 8 rows + more
        HiddenItemsTooltip.ItemRow first = (HiddenItemsTooltip.ItemRow) tooltip.getChildren().get(1);
        assertEquals("Price pending", first.getValue()); assertEquals("Reward 1", first.getName());
        assertEquals("Reward 2 x2", ((HiddenItemsTooltip.ItemRow) tooltip.getChildren().get(2)).getName());
    }

    private static LootHistoryItem history(int qty, long ge, long ha)
    { return new LootHistoryItem(qty, "<Relic> & stone", ge, ha, ge * qty, "1 / 5000", DropRarity.ULTRA_RARE, null).withItemId(1); }
    private static LootHistoryEntry entry(LootHistoryItem item)
    { return new LootHistoryEntry("Boss", item.getStackValue(), 1, DropRarity.ULTRA_RARE, Collections.singletonList(item)); }
}
