package com.lootpop;

import com.google.gson.Gson;
import com.google.gson.FieldNamingPolicy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** Only item metadata is stored. No sprites, login names, credentials or network requests. */
final class LootJournalStore
{
    private final Path directory;
    private final Gson gson;

    LootJournalStore(Path directory, Gson clientGson)
    {
        this.directory = directory;
        // Keep the on-disk field names stable while inheriting RuneLite's Gson configuration.
        this.gson = clientGson.newBuilder().setFieldNamingPolicy(FieldNamingPolicy.IDENTITY).create();
    }

    Path fileFor(String profile)
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(profile.getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder();
            for (byte b : digest) { name.append(String.format(java.util.Locale.ROOT, "%02x", b & 255)); }
            return directory.resolve(name + ".json");
        }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    LootJournal load(String profile) throws IOException
    {
        Path file = fileFor(profile);
        if (!Files.exists(file)) { return new LootJournal(); }
        if (Files.size(file) > 16 * 1024 * 1024) { throw new IOException("Journal exceeds the safe read limit"); }
        try
        {
            Data data = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), Data.class);
            if (data == null || (data.version != 1 && data.version != 2) || data.entries == null || data.bests == null
                || data.entries.size() > 7000 || data.bests.size() > 4096)
            { throw new IOException("Unsupported journal format"); }
            LootJournal journal = new LootJournal();
            Map<String, LootHistoryEntry> all = new LinkedHashMap<>();
            for (Entry saved : data.entries)
            {
                if (saved == null || saved.source == null || saved.source.length() > 300 || saved.value < 0
                    || saved.time <= 0 || saved.items == null || saved.items.size() > 128
                    || (data.version == 2 && (saved.id == null || saved.id.length() > 64 || saved.id.isEmpty())))
                { throw new IOException("Invalid journal entry"); }
                List<LootHistoryItem> items = new ArrayList<>();
                for (Item item : saved.items)
                {
                    if (item == null || item.id < 0 || item.qty <= 0 || item.name == null || item.name.length() > 300
                        || item.ge < 0 || item.ha < 0 || item.value < 0 || (item.chance != null && item.chance.length() > 100))
                    { throw new IOException("Invalid journal item"); }
                    items.add(new LootHistoryItem(item.qty, item.name, item.ge, item.ha, item.value,
                        item.chance, item.rarity == null ? DropRarity.UNKNOWN : item.rarity, null).withItemId(item.id)
                        .withPriceState(item.pending, item.gePending));
                    items.get(items.size() - 1).setCollectionLogItem(item.collection);
                }
                LootHistoryEntry entry = new LootHistoryEntry(saved.source, saved.value, saved.time,
                    saved.rarity == null ? DropRarity.UNKNOWN : saved.rarity, items);
                entry.setCollectionLogUnlock(saved.collection);
                entry.setPreviousBest(Math.max(0, saved.previous));
                entry.setPriceMode(saved.mode == null ? PriceMode.GRAND_EXCHANGE : saved.mode);
                if (data.version == 2) { entry.setId(saved.id); }
                entry.setSourceBest(saved.sourceBest);
                if (all.put(entry.getId(), entry) != null) { throw new IOException("Duplicate journal entry"); }
            }
            for (Map.Entry<String, Long> best : data.bests.entrySet())
            {
                if (best.getKey() == null || best.getKey().length() > 400 || best.getValue() == null || best.getValue() < 0)
                { throw new IOException("Invalid journal record"); }
                journal.bests.put(best.getKey(), best.getValue());
            }
            if (data.version == 1)
            {
                if (all.size() > 5000) { throw new IOException("Too many legacy entries"); }
                journal.entries.addAll(all.values());
                journal.entries.sort(java.util.Comparator.comparingLong(LootHistoryEntry::getTimestamp).reversed());
                java.util.Set<String> champions = new java.util.HashSet<>();
                for (LootHistoryEntry entry : journal.entries)
                {
                    if (entry.getTotalValue() > 0 && entry.getTotalValue() == journal.bests.getOrDefault(entry.sourceKey(), -1L)
                        && champions.add(entry.sourceKey())) { entry.setSourceBest(true); }
                    journal.keepTrophy(entry, 500);
                }
            }
            else
            {
                restoreList(data.history, all, journal.entries, 5000);
                restoreList(data.trophies, all, journal.trophies, 2000);
                for (LootHistoryEntry entry : all.values())
                {
                    if (entry.hasPendingPrices() || entry.getTotalValue() != journal.bests.getOrDefault(entry.sourceKey(), -1L))
                    { entry.setSourceBest(false); }
                }
                journal.trophies.removeIf(e -> !e.isTrophy());
            }
            journal.entries.sort(java.util.Comparator.comparingLong(LootHistoryEntry::getTimestamp).reversed());
            journal.trophies.sort(java.util.Comparator.comparingLong(LootHistoryEntry::getTimestamp).reversed());
            return journal;
        }
        catch (RuntimeException badData) { throw new IOException("Journal could not be read", badData); }
    }

    void save(String profile, LootJournal journal) throws IOException
    {
        Data data = new Data();
        data.version = 2;
        data.entries = new ArrayList<>();
        data.bests = new LinkedHashMap<>();
        data.history = new ArrayList<>();
        data.trophies = new ArrayList<>();
        synchronized (journal)
        {
            data.bests.putAll(journal.bests);
            Map<String, LootHistoryEntry> unique = new LinkedHashMap<>();
            for (LootHistoryEntry entry : journal.entries) { data.history.add(entry.getId()); unique.put(entry.getId(), entry); }
            for (LootHistoryEntry entry : journal.trophies) { data.trophies.add(entry.getId()); unique.put(entry.getId(), entry); }
            for (LootHistoryEntry entry : unique.values())
            {
                Entry saved = new Entry();
                saved.source = entry.getSourceName(); saved.value = entry.getTotalValue(); saved.time = entry.getTimestamp();
                saved.rarity = entry.getRarity(); saved.collection = entry.isCollectionLogUnlock();
                saved.previous = entry.getPreviousBest(); saved.mode = entry.getPriceMode();
                saved.id = entry.getId(); saved.sourceBest = entry.isSourceBest();
                for (LootHistoryItem item : entry.getItems())
                {
                    Item copy = new Item();
                    copy.id = item.getItemId(); copy.qty = item.getQuantity(); copy.name = item.getName();
                    copy.ge = item.getGePrice(); copy.ha = item.getHighAlchemyPrice(); copy.value = item.getStackValue();
                    copy.chance = item.getDropRate(); copy.rarity = item.getRarity(); saved.items.add(copy);
                    copy.pending = item.isPricePending(); copy.gePending = item.isGePending();
                    copy.collection = item.isCollectionLogItem();
                }
                data.entries.add(saved);
            }
        }
        Files.createDirectories(directory);
        Path file = fileFor(profile);
        Path temporary = Files.createTempFile(directory, "journal-", ".tmp");
        try
        {
            Files.writeString(temporary, gson.toJson(data), StandardCharsets.UTF_8);
            if (Files.size(temporary) > 16 * 1024 * 1024) { throw new IOException("Journal exceeds storage limit"); }
            if (Files.exists(file))
            {
                Files.copy(file, file.resolveSibling(file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            }
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported)
            { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        }
        finally { Files.deleteIfExists(temporary); }
    }

    private static final class Data
    {
        int version;
        List<Entry> entries;
        Map<String, Long> bests;
        List<String> history;
        List<String> trophies;
    }
    private static final class Entry
    {
        String source; long value; long time; DropRarity rarity; boolean collection; long previous; PriceMode mode;
        String id; boolean sourceBest;
        List<Item> items = new ArrayList<>();
    }
    private static final class Item
    {
        int id; int qty; String name; long ge; long ha; long value; String chance; DropRarity rarity;
        boolean pending; boolean gePending; boolean collection;
    }

    private static void restoreList(List<String> ids, Map<String, LootHistoryEntry> all,
        List<LootHistoryEntry> output, int limit) throws IOException
    {
        if (ids == null || ids.size() > limit) { throw new IOException("Invalid journal index"); }
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String id : ids)
        {
            if (!all.containsKey(id) || !seen.add(id)) { throw new IOException("Invalid journal reference"); }
            output.add(all.get(id));
        }
    }
}
