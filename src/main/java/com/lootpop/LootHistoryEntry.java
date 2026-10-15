package com.lootpop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class LootHistoryEntry
{
    private final String sourceName;
    private final long totalValue;
    private final long timestamp;
    private final DropRarity rarity;
    private final List<LootHistoryItem> items;
    private boolean collectionLogUnlock;

    LootHistoryEntry(
        String sourceName,
        long totalValue,
        long timestamp,
        DropRarity rarity,
        List<LootHistoryItem> items
    )
    {
        this.sourceName = sourceName;
        this.totalValue = totalValue;
        this.timestamp = timestamp;
        this.rarity = rarity;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
    }

    String getSourceName()
    {
        return sourceName;
    }

    long getTotalValue()
    {
        return totalValue;
    }

    long getTimestamp()
    {
        return timestamp;
    }

    DropRarity getRarity()
    {
        return rarity;
    }

    List<LootHistoryItem> getItems()
    {
        return items;
    }

    boolean isCollectionLogUnlock()
    {
        return collectionLogUnlock;
    }

    void setCollectionLogUnlock(boolean collectionLogUnlock)
    {
        this.collectionLogUnlock = collectionLogUnlock;
    }
}
