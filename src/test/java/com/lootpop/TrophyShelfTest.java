package com.lootpop;

import java.nio.file.Files;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static com.lootpop.JournalTestFiles.*;

public class TrophyShelfTest
{
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private static LootHistoryEntry drop(String source, long value, long time, DropRarity rarity)
    {
        return new LootHistoryEntry(source, value, time, rarity, Collections.singletonList(
            new LootHistoryItem(1, "Relic", value, 100, value, null, rarity, null).withItemId(1)));
    }

    @Test
    public void trophyRetentionIsIndependentFromHistoryAgeAndCount()
    {
        LootJournal journal = new LootJournal(); long now = System.currentTimeMillis();
        LootHistoryEntry old = drop("Boss", 1000, now - 100 * 86400000L, DropRarity.ULTRA_RARE);
        journal.record(old, 1, 1, now, 50);
        assertTrue(journal.snapshot().isEmpty()); assertEquals(1, journal.trophySnapshot().size());
        journal.record(drop("Boss", 1, now, DropRarity.COMMON), 1, 1, now, 50);
        journal.record(drop("Boss", 2, now + 1, DropRarity.COMMON), 1, 1, now, 50);
        assertEquals(1, journal.snapshot().size()); assertSame(old, journal.trophySnapshot().get(0));
    }

    @Test
    public void oneDropWithThreeReasonsOccupiesOnlyOneShelfPlace()
    {
        LootJournal journal = new LootJournal(); long now = System.currentTimeMillis();
        LootHistoryEntry entry = drop("Boss", 1000, now, DropRarity.ULTRA_RARE);
        entry.setCollectionLogUnlock(true); journal.record(entry, 100, 90, now, 50);
        journal.keepTrophy(entry, 50); journal.record(entry, 100, 90, now, 50);
        assertEquals(1, journal.snapshot().size()); assertEquals(1, journal.trophySnapshot().size());
        assertTrue(entry.isSourceBest()); assertTrue(entry.isCollectionLogUnlock());
    }

    @Test
    public void sourceChampionsReplaceOnlyBestOnlyEntriesAndValuationModesAreIndependent()
    {
        LootJournal journal = new LootJournal(); long now = System.currentTimeMillis();
        LootHistoryEntry first = drop("Boss", 100, now, DropRarity.COMMON);
        LootHistoryEntry second = drop("Boss", 200, now + 1, DropRarity.COMMON);
        journal.record(first, 100, 90, now); journal.record(second, 100, 90, now);
        assertFalse(first.isSourceBest()); assertEquals(1, journal.trophySnapshot().size());
        LootHistoryEntry alch = drop("Boss", 50, now + 2, DropRarity.COMMON);
        alch.setPriceMode(PriceMode.HIGH_ALCHEMY); journal.record(alch, 100, 90, now);
        assertEquals(2, journal.trophySnapshot().size());
        second.setCollectionLogUnlock(true);
        journal.record(drop("Boss", 300, now + 3, DropRarity.COMMON), 100, 90, now);
        assertEquals(3, journal.trophySnapshot().size()); assertFalse(second.isSourceBest());
    }

    @Test
    public void shelfUsesExplicitSeparateCapacityAndKeepsNewestTrophies()
    {
        LootJournal journal = new LootJournal(); long now = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) { journal.record(drop("Boss", i + 1, now + i, DropRarity.ULTRA_RARE), 100, 90, now, 2); }
        assertEquals(5, journal.snapshot().size()); assertEquals(2, journal.trophySnapshot().size());
        assertEquals(now + 4, journal.trophySnapshot().get(0).getTimestamp());
    }

    @Test
    public void shelfOnlyEntriesAndPendingFlagsSurviveSaveAndReload() throws Exception
    {
        LootJournal journal = new LootJournal(); long now = System.currentTimeMillis();
        LootHistoryItem relic = new LootHistoryItem(1, "New relic", 0, 12000, 0, "1 / 5000", DropRarity.ULTRA_RARE, null)
            .withItemId(1).withPriceState(true, true);
        relic.setCollectionLogItem(true);
        LootHistoryEntry entry = new LootHistoryEntry("Boss", 0, now - 100 * 86400000L,
            DropRarity.ULTRA_RARE, Collections.singletonList(relic));
        entry.setCollectionLogUnlock(true); journal.record(entry, 100, 1, now);
        LootJournalStore store = store(temporary.newFolder().toPath(), new com.google.gson.Gson()); store.save("A", journal);
        LootJournal loaded = store.load("A"); assertTrue(loaded.snapshot().isEmpty());
        LootHistoryEntry trophy = loaded.trophySnapshot().get(0);
        assertEquals(entry.getId(), trophy.getId()); assertTrue(trophy.hasPendingPrices());
        assertTrue(trophy.getItems().get(0).isGePending()); assertTrue(trophy.getItems().get(0).isCollectionLogItem());
        assertFalse(trophy.isSourceBest()); assertTrue(loaded.bests.isEmpty());
    }

    @Test
    public void historyAndShelfShareOneCanonicalEntryAfterReload() throws Exception
    {
        LootJournal journal = new LootJournal(); long now = System.currentTimeMillis();
        journal.record(drop("Boss", 1000, now, DropRarity.ULTRA_RARE), 100, 90, now);
        LootJournalStore store = store(temporary.newFolder().toPath(), new com.google.gson.Gson()); store.save("A", journal);
        LootJournal loaded = store.load("A"); assertSame(loaded.snapshot().get(0), loaded.trophySnapshot().get(0));
    }

    @Test
    public void legacyJournalIsReadWithoutLosingHistoryAndCreatesShelfFromAvailableMetadata() throws Exception
    {
        LootJournalStore store = store(temporary.newFolder().toPath(), new com.google.gson.Gson());
        LootJournal journal = new LootJournal(); long now = System.currentTimeMillis();
        journal.record(drop("Boss", 1000, now, DropRarity.ULTRA_RARE), 100, 90, now);
        store.save("A", journal);
        com.google.gson.JsonObject legacy = new com.google.gson.JsonParser().parse(Files.readString(pathFor(store, "A"))).getAsJsonObject();
        legacy.addProperty("version", 1); legacy.remove("history"); legacy.remove("trophies");
        for (com.google.gson.JsonElement element : legacy.getAsJsonArray("entries"))
        { element.getAsJsonObject().remove("id"); element.getAsJsonObject().remove("sourceBest"); }
        Files.writeString(pathFor(store, "A"), legacy.toString());
        LootJournal loaded = store.load("A"); assertEquals(1, loaded.snapshot().size());
        assertEquals(1, loaded.trophySnapshot().size()); assertTrue(loaded.trophySnapshot().get(0).isSourceBest());
        assertTrue(Files.readString(pathFor(store, "A")).contains("\"version\":1"));
        store.save("A", loaded);
        assertTrue(Files.readString(pathFor(store, "A")).contains("\"version\":2"));
        assertTrue(Files.readString(pathFor(store, "A").resolveSibling(pathFor(store, "A").getFileName() + ".bak")).contains("\"version\":1"));
    }
}
