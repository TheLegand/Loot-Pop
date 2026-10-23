package com.lootpop;

import com.google.gson.Gson;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.runelite.client.RuneLite;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.util.Filepath;
import static com.lootpop.JournalTestFiles.*;
import static org.junit.Assert.*;

/** Runs only in a subprocess with an isolated user.home. Never launches the game. */
public final class JournalMigrationProbe
{
    public static void main(String[] args) throws Exception
    {
        Path root = Path.of(System.getProperty("user.home"), ".runelite");
        assertEquals(root, RuneLite.RUNELITE_DIR.toPath());
        Path legacy = root.resolve("loot-pop-journal");
        Path target = root.resolve("plugin-data/loot-pop");
        LootPopPlugin plugin = new LootPopPlugin();
        LootJournalStore current = new LootJournalStore(() -> directory(plugin), new Gson());
        switch (args[0])
        {
            case "fresh":
                assertTrue(current.load("A").snapshot().isEmpty());
                assertFalse(Files.exists(legacy));
                current.save("A", journal(100));
                assertEquals(target, pathFor(current, "A").getParent());
                assertEquals(100, current.load("A").snapshot().get(0).getTotalValue());
                break;
            case "legacy":
                LootJournalStore old = store(legacy, new Gson());
                old.save("A", journal(100));
                old.save("A", journal(200));
                old.save("B", journal(300));
                Map<String, byte[]> originals = contents(legacy);
                LootJournal loaded = current.load("A");
                assertEquals(200, loaded.snapshot().get(0).getTotalValue());
                assertEquals(1, loaded.trophySnapshot().size());
                assertTrue(loaded.trophySnapshot().get(0).isCollectionLogUnlock());
                assertEquals("Ancient blade — أثر", loaded.snapshot().get(0).getItems().get(0).getName());
                assertEquals(300, current.load("B").snapshot().get(0).getTotalValue());
                assertEquals(target, pathFor(current, "A").getParent());
                assertFalse(Files.exists(legacy));
                assertContents(originals, target);
                byte[] before = Files.readAllBytes(pathFor(current, "A"));
                current.save("A", loaded);
                assertArrayEquals(before, Files.readAllBytes(backup(pathFor(current, "A"))));
                LootJournalStore reopened = new LootJournalStore(() -> directory(new LootPopPlugin()), new Gson());
                assertEquals(200, reopened.load("A").snapshot().get(0).getTotalValue());
                break;
            case "collision":
                LootJournalStore oldCollision = store(legacy, new Gson());
                oldCollision.save("A", journal(100));
                store(target, new Gson()).save("A", journal(900));
                Map<String, byte[]> oldFiles = contents(legacy);
                Map<String, byte[]> newFiles = contents(target);
                assertEquals(900, current.load("A").snapshot().get(0).getTotalValue());
                assertContents(oldFiles, legacy);
                assertContents(newFiles, target);
                break;
            case "failure":
                store(legacy, new Gson()).save("A", journal(100));
                Map<String, byte[]> intact = contents(legacy);
                Files.writeString(root.resolve("plugin-data"), "blocked parent");
                readOnly(current);
                assertContents(intact, legacy);
                assertEquals("blocked parent", Files.readString(root.resolve("plugin-data")));
                break;
            case "corrupt":
                Files.createDirectories(legacy);
                Path corrupt = pathFor(store(legacy, new Gson()), "A");
                Files.writeString(corrupt, "{bad-json");
                readOnly(current);
                assertEquals("{bad-json", Files.readString(pathFor(current, "A")));
                assertFalse(Files.exists(backup(pathFor(current, "A"))));
                break;
            default: throw new AssertionError("Unknown probe");
        }
    }

    private static Filepath directory(LootPopPlugin plugin) throws IOException
    {
        try
        {
            Method method = Plugin.class.getDeclaredMethod("getPluginDirectory");
            method.setAccessible(true);
            return (Filepath) method.invoke(plugin);
        }
        catch (InvocationTargetException failure)
        {
            if (failure.getCause() instanceof IOException) { throw (IOException) failure.getCause(); }
            throw new AssertionError(failure.getCause());
        }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static LootJournal journal(long value)
    {
        long now = System.currentTimeMillis();
        LootHistoryEntry entry = new LootHistoryEntry("Boss", value, now, DropRarity.ULTRA_RARE,
            Collections.singletonList(new LootHistoryItem(2, "Ancient blade — أثر", value / 2, 10,
                value, "1 / 5,000", DropRarity.ULTRA_RARE, null).withItemId(4151)));
        entry.setCollectionLogUnlock(true);
        LootJournal journal = new LootJournal();
        journal.record(entry, 100, 90, now);
        return journal;
    }

    private static Path backup(Path file) { return file.resolveSibling(file.getFileName() + ".bak"); }

    private static Map<String, byte[]> contents(Path directory) throws IOException
    {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (java.util.stream.Stream<Path> stream = Files.list(directory))
        {
            for (Path file : (Iterable<Path>) stream::iterator)
            { files.put(file.getFileName().toString(), Files.readAllBytes(file)); }
        }
        return files;
    }

    private static void assertContents(Map<String, byte[]> expected, Path directory) throws IOException
    {
        Map<String, byte[]> actual = contents(directory);
        assertEquals(expected.keySet(), actual.keySet());
        for (String name : expected.keySet()) { assertArrayEquals(name, expected.get(name), actual.get(name)); }
    }

    private static void readOnly(LootJournalStore store) throws Exception
    {
        ScheduledExecutorService io = Executors.newSingleThreadScheduledExecutor();
        LootJournalService service = new LootJournalService(store, () -> {}, () -> 100, () -> 90, io);
        try
        {
            service.switchProfile("A");
            long now = System.currentTimeMillis();
            service.record(journal(999).snapshot().get(0),
                new LootNotification("Boss", Collections.emptyList(), 999, now), true, 0);
            service.close();
            java.lang.reflect.Field tail = LootJournalService.class.getDeclaredField("tail");
            tail.setAccessible(true);
            ((CompletableFuture<?>) tail.get(service)).get(5, TimeUnit.SECONDS);
            assertFalse(service.canClear());
            assertTrue(service.notice(), service.notice().contains("untouched"));
        }
        finally { io.shutdownNow(); }
    }
}
