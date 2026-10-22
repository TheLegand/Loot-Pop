package com.lootpop;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class LootDaySummaryTest
{
    private static LootHistoryItem item(int id, int qty, long ge, String rate)
    { return new LootHistoryItem(qty, "Item " + id, ge, 5, ge * qty, rate, DropRarity.COMMON, null).withItemId(id); }

    private static LootHistoryEntry entry(long time, LootHistoryItem... items)
    { return new LootHistoryEntry("Goblin", Arrays.stream(items).filter(i -> !i.isPricePending())
        .mapToLong(LootHistoryItem::getStackValue).sum(), time, DropRarity.COMMON, Arrays.asList(items)); }

    @Test public void combinesQuantitiesAndRecordedValuesWithoutRepricing()
    {
        List<LootDaySummary.Item> result = LootDaySummary.items(Arrays.asList(
            entry(1, item(995, 20, 1, null)), entry(2, item(995, 30, 2, null))));
        assertEquals(1, result.size());
        assertEquals(50, result.get(0).quantity);
        assertEquals(80, result.get(0).value);
        assertEquals(1, result.get(0).geMin); assertEquals(2, result.get(0).geMax);
        assertEquals(20, result.get(0).sample.getQuantity()); // original observations remain untouched
    }

    @Test public void largeCombinedStacksDoNotOverflowInt()
    {
        LootHistoryEntry e = entry(1, item(995, 2_000_000_000, 1, null));
        assertEquals(4_000_000_000L, LootDaySummary.items(Arrays.asList(e, e)).get(0).quantity);
        assertEquals("x4B", LootPopPanel.formatPanelQuantity(4_000_000_000L));
    }

    @Test public void pendingPricesAndDifferentDropChancesRemainExplicit()
    {
        LootHistoryItem unknown = item(1, 2, 0, "1 / 1000").withPriceState(true, true);
        LootDaySummary.Item result = LootDaySummary.items(Arrays.asList(entry(1, item(1, 1, 200, "1 / 5000")),
            entry(2, unknown))).get(0);
        assertTrue(result.pending); assertTrue(result.gePending);
        assertEquals(200, result.value); assertEquals(3, result.quantity);
        assertEquals("Varies by drop/source", result.chance());
    }

    @Test public void datesAreGroupedNewestFirstInLocalTimezone()
    {
        long a = java.time.Instant.parse("2026-10-20T21:30:00Z").toEpochMilli();
        long b = java.time.Instant.parse("2026-10-20T18:30:00Z").toEpochMilli();
        java.util.Map<java.time.LocalDate, List<LootHistoryEntry>> groups = LootDaySummary.group(
            Arrays.asList(entry(b), entry(a)), ZoneId.of("Africa/Cairo"));
        assertEquals("2026-10-21", groups.keySet().iterator().next().toString());
        assertEquals(2, groups.size());
    }

    @Test(expected = IllegalArgumentException.class) public void refusesMixedPriceBases()
    {
        LootHistoryEntry ge = entry(1, item(1, 1, 20, null));
        LootHistoryEntry ha = entry(2, item(1, 1, 10, null)); ha.setPriceMode(PriceMode.HIGH_ALCHEMY);
        LootDaySummary.items(Arrays.asList(ge, ha));
    }

    @Test public void mergesByIdNotBySimilarNamesAndKeepsUnknownIdsSeparate()
    {
        assertEquals(2, LootDaySummary.items(Collections.singletonList(entry(1,
            item(1, 1, 20, null), item(2, 1, 20, null)))).size());
        assertEquals(2, LootDaySummary.items(Collections.singletonList(entry(1,
            item(-1, 1, 20, null), item(-2, 1, 20, null)))).size());
    }
}
