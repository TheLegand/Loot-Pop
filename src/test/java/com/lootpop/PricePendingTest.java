package com.lootpop;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class PricePendingTest
{
    @Test
    public void missingTradeableGeNeverFallsBackToHighAlchemy()
    {
        LootPriceQuote quote = LootPriceQuote.of(PriceMode.GRAND_EXCHANGE, true, true, 0, 120000);
        assertTrue(quote.pending); assertTrue(quote.gePending); assertEquals(0, quote.value);
        assertTrue(quote.shouldInclude(false));
        LootPriceQuote alch = LootPriceQuote.of(PriceMode.HIGH_ALCHEMY, true, true, 0, 120000);
        assertFalse(alch.pending); assertTrue(alch.gePending); assertEquals(120000, alch.value);
    }

    @Test
    public void genuineZeroValuesAreDifferentFromUnavailableDefinitions()
    {
        LootPriceQuote zero = LootPriceQuote.of(PriceMode.GRAND_EXCHANGE, true, false, 0, 0);
        assertFalse(zero.pending); assertFalse(zero.shouldInclude(false)); assertTrue(zero.shouldInclude(true));
        assertTrue(LootPriceQuote.of(PriceMode.GRAND_EXCHANGE, false, false, 0, 0).pending);
        assertFalse(LootPriceQuote.of(PriceMode.HIGH_ALCHEMY, true, true, 0, 0).pending);
        assertTrue(LootPriceQuote.of(PriceMode.HIGH_ALCHEMY, false, false, 0, 0).pending);
        assertFalse(LootPriceQuote.of(PriceMode.GRAND_EXCHANGE, true, true, 25000, 10000).pending);
    }

    @Test
    public void pendingStacksSurviveMergingAndDoNotFailMinimumValueFilter()
    {
        LootDisplayItem pending = new LootDisplayItem(1, 1, 0, "1 / 5000", 5000, true);
        java.util.List<LootDisplayItem> merged = LootDisplayItem.mergeDuplicates(Arrays.asList(pending,
            new LootDisplayItem(1, 2, 40000)));
        assertTrue(merged.get(0).isPricePending()); assertEquals(3, merged.get(0).getQuantity());
        assertEquals(0, merged.get(0).getTotalValue());
        assertTrue(LootPriceQuote.passesMinimum(0, 1000000, merged));
        assertFalse(LootPriceQuote.passesMinimum(10, 1000000, Collections.singletonList(new LootDisplayItem(2, 1, 10))));
    }

    @Test
    public void pendingTotalsAreExplicitAndCannotTriggerPopupRecords()
    {
        assertEquals("Price pending", LootValueText.compact(0, true));
        assertEquals("100K+ gp", LootValueText.compact(100000, true));
        assertEquals("0 gp", LootValueText.compact(0, false));
        assertTrue(LootValueText.detail(100000, true).contains("incomplete"));
        LootNotification popup = new LootNotification("Boss", Collections.singletonList(
            new LootDisplayItem(1, 1, 0, null, 0, true)), 0, 1000);
        popup.markPersonalBest(50); assertFalse(popup.isPersonalBest());
    }

    @Test
    public void incompleteDropCannotCreateOrBeatJournalRecordButStillCanBeARareTrophy()
    {
        LootJournal journal = new LootJournal(); long now = System.currentTimeMillis();
        LootHistoryEntry pending = new LootHistoryEntry("Boss", 500000, now, DropRarity.ULTRA_RARE,
            Arrays.asList(new LootHistoryItem(1, "New item", 0, 120000, 0, "1 / 5000", DropRarity.ULTRA_RARE, null)
                .withItemId(1).withPriceState(true, true),
                new LootHistoryItem(1, "Known item", 500000, 100000, 500000, null, DropRarity.COMMON, null).withItemId(2)));
        assertEquals(0, journal.record(pending, 100, 90, now));
        assertTrue(journal.bests.isEmpty()); assertFalse(pending.isSourceBest());
        assertEquals(1, journal.trophySnapshot().size());
        assertEquals(0, journal.record(LootJournalTest.entry("Boss", 100, now + 1), 100, 90, now));
        journal.record(pending, 100, 90, now);
        assertEquals(Long.valueOf(100), journal.bests.get("GRAND_EXCHANGE:Boss"));
    }
}
