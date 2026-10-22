package com.lootpop;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class LootInsightsTest
{
    static LootHistoryItem item(int id, String name, long value, String rate)
    { return new LootHistoryItem(1, name, value, value / 2, value, rate, DropRarity.RARE, null).withItemId(id); }

    static LootHistoryEntry entry(String source, long time, PriceMode mode, LootHistoryItem... items)
    {
        LootHistoryEntry entry = new LootHistoryEntry(source, Arrays.stream(items).mapToLong(LootHistoryItem::getStackValue).sum(),
            time, DropRarity.RARE, Arrays.asList(items));
        entry.setPriceMode(mode); return entry;
    }

    @Test public void sourceProfilesRespectSourcePriceBasisAndDeduplicateSnapshots()
    {
        LootHistoryEntry ge = entry("Boss", 100, PriceMode.GRAND_EXCHANGE, item(1, "Reward", 120, "1 / 128"));
        LootHistoryEntry ha = entry("Boss", 200, PriceMode.HIGH_ALCHEMY, item(1, "Reward", 50, "1 / 128"));
        LootHistoryEntry other = entry("Goblin", 300, PriceMode.GRAND_EXCHANGE, item(2, "Coins", 900, null));
        LootInsights.Summary summary = LootInsights.source(Arrays.asList(ge, ha, other, ge), "Boss", PriceMode.GRAND_EXCHANGE);
        assertEquals(1, summary.drops); assertEquals(120, summary.total); assertSame(ge, summary.bestDrop);
        assertEquals(100, summary.firstAt); assertEquals(100, summary.lastAt);
        assertEquals(50, LootInsights.source(Arrays.asList(ge, ha), "Boss", PriceMode.HIGH_ALCHEMY).total);
        assertEquals(0, LootInsights.source(Collections.singletonList(ge), "Missing", PriceMode.GRAND_EXCHANGE).drops);
    }

    @Test public void pendingDropsKeepSubtotalButCannotWinBestDropOrMostValuableItem()
    {
        LootHistoryEntry full = entry("Boss", 100, PriceMode.GRAND_EXCHANGE, item(1, "Known", 10, "1 / 100"));
        LootHistoryItem missing = item(2, "New reward", 0, "1 / 5000").withPriceState(true, true);
        LootHistoryEntry partial = entry("Boss", 200, PriceMode.GRAND_EXCHANGE,
            item(3, "Known stack", 1000, null), missing);
        LootInsights.Summary result = LootInsights.source(Arrays.asList(full, partial), "Boss", PriceMode.GRAND_EXCHANGE);
        assertEquals(1010, result.total); assertEquals(1, result.pendingDrops);
        assertSame(full, result.bestDrop); assertEquals("Known stack", result.valuable.item.getName());
        assertSame(missing, result.rarest.item);
        LootInsights.Summary unknown = LootInsights.source(Collections.singletonList(
            entry("Boss", 300, PriceMode.GRAND_EXCHANGE, missing)), "Boss", PriceMode.GRAND_EXCHANGE);
        assertNull(unknown.bestDrop); assertNull(unknown.valuable); assertEquals(1, unknown.pendingDrops);
    }

    @Test public void rarestUsesKnownSingleProbabilityRatherThanPriceOrRarityLabel()
    {
        LootHistoryItem rare = item(1, "Rare", 1, "1 / 5,000");
        LootHistoryItem expensive = item(2, "Expensive", 100000, "1 / 100");
        LootInsights.Summary summary = LootInsights.source(Collections.singletonList(entry("Boss", 1,
            PriceMode.GRAND_EXCHANGE, expensive, rare, item(3, "Unknown", 2, null),
            item(4, "Varies", 3, "1 / 100000 | 1 / 10"))), "Boss", PriceMode.GRAND_EXCHANGE);
        assertSame(rare, summary.rarest.item); assertSame(expensive, summary.valuable.item);
    }

    @Test public void recapPreservesTotalsAndHighlightsPastRecentHistoryLimit()
    {
        LootSessionRecap recap = new LootSessionRecap();
        LootHistoryEntry first = entry("Boss", 1, PriceMode.GRAND_EXCHANGE, item(1, "Big", 500, "1 / 5000"));
        recap.record(first);
        for (int i = 0; i < 100; i++)
        { recap.record(entry("Goblin", 2 + i, PriceMode.GRAND_EXCHANGE, item(2, "Coins", 1, null))); }
        recap.record(entry("Boss", 200, PriceMode.HIGH_ALCHEMY, item(1, "Big", 10, "1 / 5000")));
        LootInsights.Summary summary = recap.snapshot(PriceMode.GRAND_EXCHANGE);
        assertEquals(101, summary.drops); assertEquals(600, summary.total); assertSame(first, summary.bestDrop);
        assertEquals("Big", summary.rarest.item.getName()); assertEquals(10, recap.snapshot(PriceMode.HIGH_ALCHEMY).total);
        recap.record(entry("Goblin", 300, PriceMode.GRAND_EXCHANGE, item(2, "Coins", 1, null)));
        assertEquals("Snapshots must not change during a later refresh", 101, summary.drops);
    }

    @Test public void lateCollectionEventsAreMatchedAndCountedExactlyOnce()
    {
        LootSessionRecap recap = new LootSessionRecap();
        recap.record(entry("Boss", 10, PriceMode.GRAND_EXCHANGE, item(1, "Reward", 5, "1 / 512")));
        recap.confirmCollection("Other", 10, 1); recap.confirmCollection("Boss", 11, 1);
        assertEquals(0, recap.snapshot(PriceMode.GRAND_EXCHANGE).logSlots());
        recap.confirmCollection("Boss", 10, 1); recap.confirmCollection("Boss", 10, 1);
        assertEquals(1, recap.snapshot(PriceMode.GRAND_EXCHANGE).logSlots());
        assertEquals("Reward", recap.snapshot(PriceMode.GRAND_EXCHANGE).unlocks.get(0).item.getName());
    }

    @Test public void recapLimitsLogArtworkButKeepsSlotCountAndClearRemovesAllPriceModes()
    {
        LootSessionRecap recap = new LootSessionRecap();
        for (int i = 1; i <= 10; i++)
        {
            LootHistoryItem item = item(i, "Log " + i, i, null); item.setCollectionLogItem(true);
            recap.record(entry("Boss", i, PriceMode.GRAND_EXCHANGE, item));
        }
        LootInsights.Summary summary = recap.snapshot(PriceMode.GRAND_EXCHANGE);
        assertEquals(10, summary.logSlots()); assertEquals(6, summary.unlocks.size());
        assertEquals("Log 10", summary.unlocks.get(0).item.getName());
        recap.record(entry("Boss", 100, PriceMode.HIGH_ALCHEMY, item(1, "Reward", 1, null)));
        recap.clear();
        for (PriceMode mode : PriceMode.values())
        { assertEquals(0, recap.snapshot(mode).drops); assertEquals(0, recap.snapshot(mode).logSlots()); }
    }

    @Test public void elapsedTimeHandlesIdleHoursAndClockChanges()
    {
        assertEquals("<1m", LootPopPanel.elapsed(100, 99));
        assertEquals("1m", LootPopPanel.elapsed(0, 60000));
        assertEquals("2h 5m", LootPopPanel.elapsed(0, 125 * 60000));
    }
}
