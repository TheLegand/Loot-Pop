package com.lootpop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Saved observations, not a kill counter or an estimate of unobserved loot. */
final class LootJournal
{
    final List<LootHistoryEntry> entries = new ArrayList<>();
    final Map<String, Long> bests = new LinkedHashMap<>();
    final List<LootHistoryEntry> trophies = new ArrayList<>();

    synchronized long record(LootHistoryEntry entry, int limit, int days, long now)
    {
        return record(entry, limit, days, now, 500);
    }

    synchronized long record(LootHistoryEntry entry, int limit, int days, long now, int trophyLimit)
    {
        if (entries.stream().anyMatch(e -> e.getId().equals(entry.getId()))
            || trophies.stream().anyMatch(e -> e.getId().equals(entry.getId()))) { return 0; }
        String key = entry.sourceKey();
        long previous = bests.getOrDefault(key, 0L);
        if (!entry.hasPendingPrices() && entry.getTotalValue() > previous)
        {
            for (LootHistoryEntry old : trophies)
            {
                if (old.sourceKey().equals(key)) { old.setSourceBest(false); }
            }
            for (LootHistoryEntry old : entries)
            {
                if (old.sourceKey().equals(key)) { old.setSourceBest(false); }
            }
            entry.setSourceBest(true);
            bests.put(key, entry.getTotalValue());
            if (previous > 0) { entry.setPreviousBest(previous); }
        }
        entries.add(0, entry);
        keepTrophy(entry, trophyLimit);
        prune(limit, days, now);
        return entry.getPreviousBest();
    }

    synchronized void prune(int limit, int days, long now)
    {
        long cutoff = now - Math.max(1, days) * 86_400_000L;
        entries.removeIf(e -> e.getTimestamp() < cutoff);
        while (entries.size() > Math.max(1, limit)) { entries.remove(entries.size() - 1); }
        // Bound source metadata too; observed sources normally number far fewer than this.
        while (bests.size() > 4096) { bests.remove(bests.keySet().iterator().next()); }
    }

    synchronized List<LootHistoryEntry> snapshot() { return new ArrayList<>(entries); }
    synchronized List<LootHistoryEntry> trophySnapshot() { return new ArrayList<>(trophies); }

    synchronized void keepTrophy(LootHistoryEntry entry, int limit)
    {
        trophies.removeIf(e -> !e.isTrophy());
        if (entry.isTrophy() && trophies.stream().noneMatch(e -> e.getId().equals(entry.getId())))
        { trophies.add(entry); }
        trimTrophies(limit);
    }

    synchronized void trimTrophies(int limit)
    {
        trophies.sort(java.util.Comparator.comparingLong(LootHistoryEntry::getTimestamp).reversed());
        while (trophies.size() > Math.max(1, limit)) { trophies.remove(trophies.size() - 1); }
    }
}
