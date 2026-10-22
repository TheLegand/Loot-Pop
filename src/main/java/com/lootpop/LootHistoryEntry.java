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
    private volatile boolean collectionLogUnlock;
    private volatile long previousBest;
    private PriceMode priceMode = PriceMode.GRAND_EXCHANGE;
    private String id = java.util.UUID.randomUUID().toString();
    private volatile boolean sourceBest;

    String getId() { return id; }
    void setId(String id) { this.id = id; }
    boolean isSourceBest() { return sourceBest && !hasPendingPrices(); }
    void setSourceBest(boolean best) { sourceBest = best; }
    boolean hasPendingPrices() { return items.stream().anyMatch(LootHistoryItem::isPricePending); }
    boolean isTrophy() { return isSourceBest() || collectionLogUnlock || rarity == DropRarity.ULTRA_RARE; }
    String sourceKey() { return priceMode.name() + ":" + sourceName; }

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

    long getPreviousBest() { return previousBest; }
    boolean isPersonalBest() { return previousBest > 0 && !hasPendingPrices(); }
    void setPreviousBest(long previous) { previousBest = previous; }
    PriceMode getPriceMode() { return priceMode; }
    void setPriceMode(PriceMode mode) { priceMode = mode; }
    boolean isMoment()
    {
        return collectionLogUnlock || isPersonalBest() || rarity.ordinal() >= DropRarity.RARE.ordinal();
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
