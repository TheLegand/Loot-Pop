package com.lootpop;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;

/** Session totals outlive the recent-loot list without retaining an entire session's sprites. */
final class LootSessionRecap
{
    private final Map<PriceMode, LootInsights.Summary> summaries = new EnumMap<>(PriceMode.class);
    private final Deque<LootHistoryEntry> recent = new ArrayDeque<>();

    synchronized void record(LootHistoryEntry entry)
    {
        summaries.computeIfAbsent(entry.getPriceMode(), LootInsights.Summary::new).record(entry);
        recent.addFirst(entry);
        while (recent.size() > 32) { recent.removeLast(); }
    }

    synchronized void confirmCollection(String source, long timestamp, int itemId)
    {
        for (LootHistoryEntry entry : recent)
        {
            if (entry.getTimestamp() != timestamp || !entry.getSourceName().equals(source)) { continue; }
            for (LootHistoryItem item : entry.getItems())
            {
                if (item.getItemId() == itemId)
                { item.setCollectionLogItem(true); entry.setCollectionLogUnlock(true); }
            }
            summaries.get(entry.getPriceMode()).confirmCollection(entry);
            break;
        }
    }

    synchronized LootInsights.Summary snapshot(PriceMode mode)
    {
        LootInsights.Summary summary = summaries.get(mode);
        return summary == null ? new LootInsights.Summary(mode) : new LootInsights.Summary(summary);
    }

    synchronized void clear() { summaries.clear(); recent.clear(); }
}
