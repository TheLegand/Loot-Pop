package com.lootpop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** A bounded, deterministic timeline. Items keep their final slots throughout the reveal. */
final class SealedDropSequence
{
    private final List<LootDisplayItem> items;
    private final int candidateCount;
    private final int[] stages;
    private final long[] starts;
    private final long[] ends;
    private final boolean reducedMotion;
    private final long fadeMillis;

    SealedDropSequence(List<LootDisplayItem> source, Set<Integer> unlocks, double minimumRate,
        boolean includeUnlocks, boolean reducedMotion, int speedPercent, int visibleCapacity)
    {
        this.reducedMotion = reducedMotion;
        double speed = 100.0 / Math.max(50, Math.min(200, speedPercent));
        // Settling into the ordinary popup must stay quick, even at a slow reveal speed.
        fadeMillis = 180;
        List<LootDisplayItem> selected = new ArrayList<>();
        List<LootDisplayItem> ordinary = new ArrayList<>();
        for (LootDisplayItem item : source)
        {
            if ((item.getDropRateOneIn() >= minimumRate
                || (includeUnlocks && unlocks.contains(item.getItemId())))
                && selected.size() < visibleCapacity)
            {
                selected.add(item);
            }
            else
            {
                ordinary.add(item);
            }
        }
        candidateCount = selected.size();
        selected.addAll(ordinary);
        items = Collections.unmodifiableList(selected);
        // First two items get individual reveals; further rare items share the final reveal.
        int stageCount = reducedMotion ? Math.min(1, candidateCount) : Math.min(3, candidateCount);
        stages = new int[candidateCount];
        starts = new long[stageCount];
        ends = new long[stageCount];
        long cursor = Math.round(160 * speed);
        for (int stage = 0; stage < stageCount; stage++)
        {
            boolean collection = false;
            for (int i = 0; i < candidateCount; i++)
            {
                stages[i] = reducedMotion ? 0 : Math.min(i, 2);
                if (stages[i] == stage && unlocks.contains(items.get(i).getItemId()))
                {
                    collection = true;
                }
            }
            starts[stage] = cursor;
            ends[stage] = cursor + Math.round((reducedMotion ? 350 : collection ? 1_850 : 1_550) * speed);
            cursor = ends[stage];
        }
    }

    List<LootDisplayItem> getItems() { return items; }
    int getCandidateCount() { return candidateCount; }
    int getStage(int index) { return index < candidateCount ? stages[index] : -1; }
    int getStageCount() { return starts.length; }
    boolean isReducedMotion() { return reducedMotion; }
    int activeStage(long age)
    {
        int active = 0;
        for (int i = 0; i < starts.length; i++) { if (age >= starts[i]) { active = i; } }
        return active;
    }
    long getRevealEndMillis() { return ends.length == 0 ? 0 : ends[ends.length - 1]; }
    long getDurationMillis() { return candidateCount == 0 ? 0 : getRevealEndMillis() + fadeMillis; }

    double progress(long age, int index)
    {
        if (index >= candidateCount) { return 1.0; }
        int stage = stages[index];
        return (age - starts[stage]) / (double) (ends[stage] - starts[stage]);
    }

    float normalAlpha(long age)
    {
        return candidateCount == 0 ? 1f : (float) clamp((age - getRevealEndMillis()) / (double) fadeMillis);
    }

    static double clamp(double value) { return Math.max(0.0, Math.min(1.0, value)); }
    static double ease(double value)
    {
        double t = clamp(value);
        return t * t * (3.0 - 2.0 * t);
    }
}
