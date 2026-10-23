package com.lootpop;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static com.lootpop.JournalTestFiles.*;

public class LootJournalTest
{
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    static LootHistoryEntry entry(String source, long value, long time)
    {
        return new LootHistoryEntry(source, value, time, DropRarity.ULTRA_RARE,
            Collections.singletonList(new LootHistoryItem(2, "Ancient blade", 50000, 12000, value,
                "1 / 5,000", DropRarity.ULTRA_RARE, null).withItemId(4151)));
    }

    @Test
    public void recordsAreStrictImprovementsNotFirstDropsOrTiesAndSeparateValuationModes()
    {
        LootJournal journal = new LootJournal();
        long now = System.currentTimeMillis();
        assertEquals(0, journal.record(entry("Boss", 100, now), 100, 90, now));
        assertEquals(0, journal.record(entry("Boss", 100, now), 100, 90, now));
        assertEquals(100, journal.record(entry("Boss", 250, now), 100, 90, now));
        assertEquals(0, journal.record(entry("Other boss", 500, now), 100, 90, now));
        LootHistoryEntry alch = entry("Boss", 300, now); alch.setPriceMode(PriceMode.HIGH_ALCHEMY);
        assertEquals(0, journal.record(alch, 100, 90, now));
        assertEquals(250, journal.record(entry("Boss", 400, now), 100, 90, now));
    }

    @Test
    public void retentionBoundsHistoryButDoesNotForgetSourceRecord()
    {
        LootJournal journal = new LootJournal();
        long now = System.currentTimeMillis();
        journal.record(entry("Boss", 100, now - 3 * 86400000L), 2, 1, now);
        assertTrue(journal.snapshot().isEmpty());
        assertEquals(100, journal.record(entry("Boss", 200, now), 2, 1, now));
        journal.record(entry("Boss", 10, now), 2, 1, now);
        journal.record(entry("Boss", 20, now), 2, 1, now);
        assertEquals(2, journal.snapshot().size());
        assertEquals(200, journal.record(entry("Boss", 300, now), 2, 1, now));
    }

    @Test
    public void metadataRoundTripPreservesSnapshotsUnlocksAndRecordsWithoutSavingSprites() throws Exception
    {
        LootJournalStore store = store(temporary.newFolder().toPath(), new com.google.gson.Gson());
        LootJournal journal = new LootJournal(); long now = System.currentTimeMillis();
        journal.record(entry("Boss", 100, now), 100, 90, now);
        LootHistoryEntry record = entry("Boss", 200, now + 1);
        record.setCollectionLogUnlock(true); journal.record(record, 100, 90, now);
        store.save("profile-a", journal);
        LootHistoryEntry restored = store.load("profile-a").snapshot().get(0);
        assertEquals(200, restored.getTotalValue()); assertEquals(100, restored.getPreviousBest());
        assertTrue(restored.isCollectionLogUnlock()); assertEquals(4151, restored.getItems().get(0).getItemId());
        assertEquals(2, restored.getItems().get(0).getQuantity());
        assertEquals("1 / 5,000", restored.getItems().get(0).getDropRate());
        assertEquals(12000, restored.getItems().get(0).getHighAlchemyPrice());
        assertNull(restored.getItems().get(0).getImage());
        String json = Files.readString(pathFor(store, "profile-a"));
        assertFalse(json.contains("profile-a")); assertFalse(json.contains("image"));
        store.save("profile-a", journal);
        assertTrue(Files.exists(pathFor(store, "profile-a").resolveSibling(pathFor(store, "profile-a").getFileName() + ".bak")));
    }

    @Test
    public void profileKeysCannotEscapeDirectoryAndAccountsNeverMix() throws Exception
    {
        Path directory = temporary.newFolder().toPath(); LootJournalStore store = store(directory, new com.google.gson.Gson());
        assertEquals(directory, pathFor(store, "../../another-folder").getParent());
        assertNotEquals(pathFor(store, "account1"), pathFor(store, "account2"));
        assertTrue(store.load("account2").snapshot().isEmpty());
    }

    @Test
    public void inheritsClientGsonOptionsWithoutChangingPersistedFieldNames() throws Exception
    {
        com.google.gson.Gson clientGson = new com.google.gson.GsonBuilder()
            .serializeNulls()
            .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();
        LootJournalStore store = store(temporary.newFolder().toPath(), clientGson);
        long now = System.currentTimeMillis();
        LootJournal journal = new LootJournal();
        LootHistoryEntry entry = new LootHistoryEntry("Boss", 100, now, DropRarity.UNKNOWN,
            Collections.singletonList(new LootHistoryItem(1, "Relic", 100, 10, 100,
                null, DropRarity.UNKNOWN, null).withItemId(1)));
        journal.record(entry, 100, 90, now);
        store.save("A", journal);
        String json = Files.readString(pathFor(store, "A"));
        assertTrue("Inherited serializeNulls option", json.contains("\"chance\":null"));
        assertTrue("Existing Journal field names remain compatible", json.contains("\"sourceBest\":true"));
        assertFalse(json.contains("source_best"));
        assertTrue(store.load("A").snapshot().get(0).isSourceBest());
    }

    @Test
    public void asyncProfileSwitchesFlushInOrderAndKeepIndependentRecords() throws Exception
    {
        LootJournalStore store = store(temporary.newFolder().toPath(), new com.google.gson.Gson());
        LootJournalService service = service(store);
        long now = System.currentTimeMillis();
        service.switchProfile("A");
        record(service, "Boss", 100, now); record(service, "Boss", 200, now + 1);
        service.switchProfile("B"); record(service, "Boss", 300, now + 2);
        service.switchProfile("A"); record(service, "Boss", 400, now + 3);
        closeAndDrain(service);
        assertEquals(3, store.load("A").snapshot().size());
        assertEquals(200, store.load("A").snapshot().get(0).getPreviousBest());
        assertEquals(1, store.load("B").snapshot().size());
        assertEquals(0, store.load("B").snapshot().get(0).getPreviousBest());
    }

    @Test
    public void unreadableHistoryIsNotSilentlyOverwritten() throws Exception
    {
        LootJournalStore store = store(temporary.newFolder().toPath(), new com.google.gson.Gson());
        Files.writeString(pathFor(store, "A"), "{bad-json");
        LootJournalService service = service(store);
        service.switchProfile("A"); record(service, "Boss", 100, System.currentTimeMillis());
        closeAndDrain(service);
        assertEquals("{bad-json", Files.readString(pathFor(store, "A")));
        assertFalse(service.canClear()); assertTrue(service.status().contains("untouched"));
        assertTrue("A compact sidebar must still receive actionable file errors", service.notice().contains("untouched"));
    }

    @Test
    public void lateCollectionConfirmationSurvivesRestart() throws Exception
    {
        LootJournalStore store = store(temporary.newFolder().toPath(), new com.google.gson.Gson());
        LootJournalService service = service(store);
        long now = System.currentTimeMillis(); service.switchProfile("A"); record(service, "Boss", 100, now);
        service.confirmCollection("Boss", now); closeAndDrain(service);
        assertTrue(store.load("A").snapshot().get(0).isCollectionLogUnlock());
    }

    @Test
    public void confirmedClearResetsRecordsWhilePreservingLastBackup() throws Exception
    {
        LootJournalStore store = store(temporary.newFolder().toPath(), new com.google.gson.Gson());
        LootJournal baseline = new LootJournal();
        baseline.record(entry("Boss", 100, System.currentTimeMillis()), 100, 90, System.currentTimeMillis());
        store.save("A", baseline);
        LootJournalService service = service(store);
        service.switchProfile("A"); service.clear(); closeAndDrain(service);
        assertTrue(store.load("A").snapshot().isEmpty());
        assertTrue(store.load("A").bests.isEmpty());
        assertTrue(Files.readString(pathFor(store, "A").resolveSibling(pathFor(store, "A").getFileName() + ".bak")).contains("Boss"));
    }

    @Test
    public void directoryIsResolvedOnceOnTheBackgroundLaneNotAtConstruction() throws Exception
    {
        Thread caller = Thread.currentThread();
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        Path directory = temporary.newFolder().toPath();
        LootJournalStore store = new LootJournalStore(() ->
        {
            assertNotSame(caller, Thread.currentThread());
            calls.incrementAndGet();
            return net.runelite.client.util.Filepath.Unchecked.getRooted(directory);
        }, new com.google.gson.Gson());
        assertEquals(0, calls.get());
        LootJournalService service = service(store);
        service.switchProfile("A");
        record(service, "Boss", 100, System.currentTimeMillis());
        service.switchProfile("B");
        record(service, "Boss", 200, System.currentTimeMillis());
        closeAndDrain(service);
        assertEquals(1, calls.get());
        assertEquals(100, store.load("A").snapshot().get(0).getTotalValue());
        assertEquals(200, store.load("B").snapshot().get(0).getTotalValue());
    }

    @Test
    public void failedBackupLeavesPrimaryUntouchedAndCleansTemporaryFile() throws Exception
    {
        Path directory = temporary.newFolder().toPath();
        LootJournalStore store = store(directory, new com.google.gson.Gson());
        LootJournal journal = new LootJournal();
        long now = System.currentTimeMillis();
        journal.record(entry("Boss", 100, now), 100, 90, now);
        store.save("A", journal);
        Path primary = pathFor(store, "A");
        byte[] original = Files.readAllBytes(primary);
        Path backup = primary.resolveSibling(primary.getFileName() + ".bak");
        Files.createDirectory(backup);
        Files.writeString(backup.resolve("keep.txt"), "untouched");
        journal.record(entry("Boss", 200, now + 1), 100, 90, now);
        assertThrows(java.io.IOException.class, () -> store.save("A", journal));
        assertArrayEquals(original, Files.readAllBytes(primary));
        assertEquals("untouched", Files.readString(backup.resolve("keep.txt")));
        try (java.util.stream.Stream<Path> files = Files.list(directory))
        { assertFalse(files.anyMatch(p -> p.getFileName().toString().endsWith(".tmp"))); }
    }

    private static void record(LootJournalService service, String source, long value, long now)
    {
        service.record(entry(source, value, now), new LootNotification(source, Collections.emptyList(), value, now), true, 0);
    }

    private static void closeAndDrain(LootJournalService service) throws Exception
    {
        java.lang.reflect.Field field = LootJournalService.class.getDeclaredField("scheduler"); field.setAccessible(true);
        ScheduledExecutorService io = (ScheduledExecutorService) field.get(service);
        service.close();
        java.lang.reflect.Field tail = LootJournalService.class.getDeclaredField("tail"); tail.setAccessible(true);
        ((java.util.concurrent.CompletableFuture<?>) tail.get(service)).get(5, TimeUnit.SECONDS);
        io.shutdown(); assertTrue(io.awaitTermination(5, TimeUnit.SECONDS));
    }

    private static LootJournalService service(LootJournalStore store)
    {
        return new LootJournalService(store, () -> {}, () -> 100, () -> 90,
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor());
    }
}
