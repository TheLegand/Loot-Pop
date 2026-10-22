package com.lootpop;

final class SessionSnapshot
{
    private final int drops;
    private final long totalValue;
    private final String bestSource;
    private final long bestValue;
    private final int pendingDrops;

    SessionSnapshot(int drops, long totalValue, String bestSource, long bestValue)
    {
        this(drops, totalValue, bestSource, bestValue, 0);
    }

    SessionSnapshot(int drops, long totalValue, String bestSource, long bestValue, int pendingDrops)
    {
        this.drops = drops;
        this.totalValue = totalValue;
        this.bestSource = bestSource;
        this.bestValue = bestValue;
        this.pendingDrops = pendingDrops;
    }

    int getPendingDrops() { return pendingDrops; }

    int getDrops()
    {
        return drops;
    }

    long getTotalValue()
    {
        return totalValue;
    }

    long getAverageValue()
    {
        return drops <= 0 ? 0L : totalValue / drops;
    }

    String getBestSource()
    {
        return bestSource;
    }

    long getBestValue()
    {
        return bestValue;
    }
}
