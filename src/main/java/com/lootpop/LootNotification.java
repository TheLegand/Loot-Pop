package com.lootpop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class LootNotification
{
    private final String sourceName;
    private final List<LootDisplayItem> items;
    private final long totalValue;
    private long visibleAtMillis;
    private long pausedAtMillis = -1L;
    private long pausedDurationMillis;
    private volatile boolean collectionLogUnlock;
    private final long createdAtMillis;
    private final Set<Integer> collectionLogItems = ConcurrentHashMap.newKeySet();
    private volatile SealedDropSequence sealedDrop;
    private volatile boolean revealSkipped;
    private long skippedRevealDuration;
    private boolean soundPlayed;
    private boolean screenshotPending;
    private int lastRevealSoundStage = -1;
    private volatile long lateUnlockAtAge = -1L;
    private volatile long previousBest;

    void markPersonalBest(long previous) { previousBest = hasPendingPrices() ? 0 : Math.max(0, previous); }
    long getPreviousBest() { return previousBest; }
    boolean isPersonalBest() { return previousBest > 0; }
    boolean hasPendingPrices() { return items.stream().anyMatch(LootDisplayItem::isPricePending); }

    LootNotification(String sourceName, List<LootDisplayItem> items, long totalValue, long createdAtMillis)
    {
        this.sourceName = sourceName;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.totalValue = totalValue;
        this.visibleAtMillis = createdAtMillis;
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

    long getCreatedAtMillis() { return createdAtMillis; }

    List<LootDisplayItem> getDisplayItems()
    {
        return sealedDrop == null ? items : sealedDrop.getItems();
    }

    SealedDropSequence getSealedDrop() { return revealSkipped ? null : sealedDrop; }
    void setSealedDrop(SealedDropSequence sequence) { sealedDrop = sequence; }
    void skipReveal(long now)
    {
        if (!revealSkipped && sealedDrop != null)
        {
            skippedRevealDuration = Math.min(getAgeMillis(now), sealedDrop.getDurationMillis());
            revealSkipped = true;
        }
    }
    long getRevealDelayMillis()
    {
        return revealSkipped ? skippedRevealDuration : sealedDrop == null ? 0 : sealedDrop.getDurationMillis();
    }
    Set<Integer> getCollectionLogItems() { return Collections.unmodifiableSet(collectionLogItems); }
    boolean isCollectionLogItem(int id) { return collectionLogItems.contains(id); }
    long getLateUnlockAtAge() { return lateUnlockAtAge; }

    boolean markCollectionLogItem(int id, long now)
    {
        if (!collectionLogItems.add(id)) { return false; }
        collectionLogUnlock = true;
        // Late messages celebrate the matching item without replaying or rearranging the drop.
        if (getAgeMillis(now) > 300 && (sealedDrop == null
            || getAgeMillis(now) >= sealedDrop.getRevealEndMillis()))
        {
            lateUnlockAtAge = getAgeMillis(now);
        }
        return true;
    }

    boolean takeSound()
    {
        if (soundPlayed) { return false; }
        soundPlayed = true;
        return true;
    }

    void requestScreenshot() { screenshotPending = true; }
    boolean takeScreenshot()
    {
        if (!screenshotPending) { return false; }
        screenshotPending = false;
        return true;
    }

    boolean takeRevealSound(int stage)
    {
        if (stage <= lastRevealSoundStage) { return false; }
        lastRevealSoundStage = stage;
        return true;
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
        SealedDropSequence sequence = getSealedDrop();
        hovered = hovered && (sequence == null || getAgeMillis(nowMillis) >= sequence.getDurationMillis());
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
        lateUnlockAtAge = -1L;
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
