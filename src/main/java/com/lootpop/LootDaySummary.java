package com.lootpop;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** A presentation of recorded snapshots, never a revaluation at today's prices. */
final class LootDaySummary
{
    static Map<LocalDate, List<LootHistoryEntry>> group(List<LootHistoryEntry> entries, ZoneId zone)
    {
        Map<LocalDate, List<LootHistoryEntry>> days = new TreeMap<>(Comparator.reverseOrder());
        for (LootHistoryEntry entry : entries)
        {
            LocalDate day = Instant.ofEpochMilli(entry.getTimestamp()).atZone(zone).toLocalDate();
            days.computeIfAbsent(day, ignored -> new ArrayList<>()).add(entry);
        }
        days.values().forEach(day -> day.sort(Comparator.comparingLong(LootHistoryEntry::getTimestamp).reversed()));
        return days;
    }

    static List<Item> items(List<LootHistoryEntry> entries)
    {
        Map<String, Item> merged = new LinkedHashMap<>();
        PriceMode basis = entries.isEmpty() ? null : entries.get(0).getPriceMode();
        for (LootHistoryEntry entry : entries)
        {
            if (entry.getPriceMode() != basis) { throw new IllegalArgumentException("Cannot mix GE and HA snapshots"); }
            for (LootHistoryItem item : entry.getItems())
            {
                String key = item.getItemId() >= 0 ? "id:" + item.getItemId() : "name:" + item.getName();
                merged.computeIfAbsent(key, ignored -> new Item(item)).add(item);
            }
        }
        List<Item> result = new ArrayList<>(merged.values());
        result.sort(Comparator.comparingLong((Item item) -> item.value).reversed()
            .thenComparing(item -> item.sample.getName()));
        return result;
    }

    static final class Item
    {
        final LootHistoryItem sample;
        long quantity;
        long value;
        long geMin = Long.MAX_VALUE, geMax;
        long haMin = Long.MAX_VALUE, haMax;
        boolean pending, gePending, variedChance;
        boolean allHaAboveGe = true;
        private final String chance;

        private Item(LootHistoryItem sample) { this.sample = sample; chance = sample.getDropRate(); }

        private void add(LootHistoryItem item)
        {
            quantity += item.getQuantity();
            if (!item.isPricePending()) { value += item.getStackValue(); }
            pending |= item.isPricePending();
            gePending |= item.isGePending();
            allHaAboveGe &= !item.isGePending() && item.getHighAlchemyPrice() > item.getGePrice();
            if (!item.isGePending())
            { geMin = Math.min(geMin, item.getGePrice()); geMax = Math.max(geMax, item.getGePrice()); }
            haMin = Math.min(haMin, item.getHighAlchemyPrice());
            haMax = Math.max(haMax, item.getHighAlchemyPrice());
            variedChance |= !Objects.equals(chance, item.getDropRate());
        }

        String chance() { return variedChance ? "Varies by drop/source" : chance == null ? "N/A" : chance; }
    }
}
