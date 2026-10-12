package com.lootpop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class LootNotification
{
    private final String sourceName;
    private final List<LootDisplayItem> items;
    private final long totalValue;
    private final long createdAtMillis;

    LootNotification(String sourceName, List<LootDisplayItem> items, long totalValue, long createdAtMillis)
    {
        this.sourceName = sourceName;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.totalValue = totalValue;
        this.createdAtMillis = createdAtMillis;
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

    long getAgeMillis(long nowMillis)
    {
        return Math.max(0L, nowMillis - createdAtMillis);
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
        return nowMillis - createdAtMillis > (long) displayDuration + fadeDuration;
    }
}
