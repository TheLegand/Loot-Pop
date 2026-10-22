package com.lootpop;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Insights use observed snapshots only. Never mix price modes or infer kills/profit. */
final class LootInsights
{
    static Summary source(List<LootHistoryEntry> entries, String source, PriceMode mode)
    {
        Summary result = new Summary(mode);
        Set<String> ids = new LinkedHashSet<>();
        entries.stream().filter(e -> e.getPriceMode() == mode && source.equals(e.getSourceName()))
            .sorted(Comparator.comparingLong(LootHistoryEntry::getTimestamp))
            .filter(e -> ids.add(e.getId())).forEach(result::record);
        return result;
    }

    static final class Highlight
    {
        final LootHistoryItem item;
        final String source;
        final long timestamp;

        Highlight(LootHistoryItem item, LootHistoryEntry entry)
        { this.item = item; source = entry.getSourceName(); timestamp = entry.getTimestamp(); }
    }

    static final class Summary
    {
        final PriceMode mode;
        int drops, pendingDrops;
        long total, firstAt, lastAt;
        LootHistoryEntry bestDrop;
        Highlight valuable, rarest;
        final List<Highlight> unlocks = new ArrayList<>();
        private final Set<String> unlockKeys = new LinkedHashSet<>();

        Summary(PriceMode mode) { this.mode = mode; }

        Summary(Summary original)
        {
            mode = original.mode; drops = original.drops; pendingDrops = original.pendingDrops;
            total = original.total; firstAt = original.firstAt; lastAt = original.lastAt;
            bestDrop = original.bestDrop; valuable = original.valuable; rarest = original.rarest;
            unlocks.addAll(original.unlocks); unlockKeys.addAll(original.unlockKeys);
        }

        void record(LootHistoryEntry entry)
        {
            if (entry.getPriceMode() != mode) { throw new IllegalArgumentException("Mixed price modes"); }
            firstAt = drops == 0 ? entry.getTimestamp() : Math.min(firstAt, entry.getTimestamp());
            lastAt = Math.max(lastAt, entry.getTimestamp());
            drops++; total += entry.getTotalValue();
            if (entry.hasPendingPrices()) { pendingDrops++; }
            else if (bestDrop == null || entry.getTotalValue() > bestDrop.getTotalValue()) { bestDrop = entry; }
            for (LootHistoryItem item : entry.getItems())
            {
                Highlight observed = new Highlight(item, entry);
                if (!item.isPricePending() && (valuable == null || item.getStackValue() > valuable.item.getStackValue()))
                { valuable = observed; }
                double chance = chance(item);
                if (Double.isFinite(chance) && chance > 0 && (rarest == null || chance > chance(rarest.item)))
                { rarest = observed; }
            }
            confirmCollection(entry);
        }

        void confirmCollection(LootHistoryEntry entry)
        {
            for (LootHistoryItem item : entry.getItems())
            {
                String key = item.getItemId() >= 0 ? "id:" + item.getItemId() : "name:" + item.getName();
                if (item.isCollectionLogItem() && unlockKeys.add(key))
                {
                    unlocks.add(0, new Highlight(item, entry));
                    unlocks.sort(Comparator.comparingLong((Highlight h) -> h.timestamp).reversed());
                    while (unlocks.size() > 6) { unlocks.remove(unlocks.size() - 1); }
                }
            }
        }

        int logSlots() { return unlockKeys.size(); }

        private static double chance(LootHistoryItem item)
        {
            String rate = item.getDropRate();
            // Multiple possible rates have no single meaningful ranking.
            return rate == null || rate.contains("|") || rate.contains("+ more")
                || rate.contains(";") || rate.contains(" or ") ? 0
                : DropRateService.parseOneIn(rate.replace(",", ""));
        }
    }

    private LootInsights() { }
}
