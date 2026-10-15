package com.lootpop;

final class SessionSnapshot
{
    private final int drops;
    private final long totalValue;
    private final String bestSource;
    private final long bestValue;

    SessionSnapshot(int drops, long totalValue, String bestSource, long bestValue)
    {
        this.drops = drops;
        this.totalValue = totalValue;
        this.bestSource = bestSource;
        this.bestValue = bestValue;
    }

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
