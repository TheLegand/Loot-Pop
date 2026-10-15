package com.lootpop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class LootNotification
{
    private final String sourceName;
    private final List<LootDisplayItem> items;
    private final long totalValue;
    private long visibleAtMillis;
    private long pausedAtMillis = -1L;
    private long pausedDurationMillis;
    private boolean collectionLogUnlock;

    LootNotification(String sourceName, List<LootDisplayItem> items, long totalValue, long createdAtMillis)
    {
        this.sourceName = sourceName;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.totalValue = totalValue;
        this.visibleAtMillis = createdAtMillis;
    }

    String getSourceName()
    {
        return sourceName;
    }

    List<LootDisplayItem> getItems()
    {
        return items;
    }

    long getTotalValue()
    {
        return totalValue;
    }

    boolean isCollectionLogUnlock()
    {
        return collectionLogUnlock;
    }

    void setCollectionLogUnlock(boolean collectionLogUnlock)
    {
        this.collectionLogUnlock = collectionLogUnlock;
    }

    long getAgeMillis(long nowMillis)
    {
        long effectiveNow = pausedAtMillis >= 0L ? pausedAtMillis : nowMillis;
        return Math.max(0L, effectiveNow - visibleAtMillis - pausedDurationMillis);
    }

    void setHovered(boolean hovered, long nowMillis)
    {
        if (hovered && pausedAtMillis < 0L)
        {
            pausedAtMillis = nowMillis;
        }
        else if (!hovered && pausedAtMillis >= 0L)
        {
            pausedDurationMillis += Math.max(0L, nowMillis - pausedAtMillis);
            pausedAtMillis = -1L;
        }
    }

    void restartAt(long nowMillis)
    {
        visibleAtMillis = nowMillis;
        pausedAtMillis = -1L;
        pausedDurationMillis = 0L;
    }

    float getAlpha(long nowMillis, int displayDuration, int fadeDuration)
    {
        long age = getAgeMillis(nowMillis);
        if (age <= displayDuration)
        {
            return 1.0f;
        }

        if (fadeDuration <= 0)
        {
            return 0.0f;
        }

        float progress = (age - displayDuration) / (float) fadeDuration;
        return Math.max(0.0f, 1.0f - progress);
    }

    boolean isExpired(long nowMillis, int displayDuration, int fadeDuration)
    {
        return getAgeMillis(nowMillis) > (long) displayDuration + fadeDuration;
    }
}
