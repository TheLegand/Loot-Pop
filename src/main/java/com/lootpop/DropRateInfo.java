package com.lootpop;

final class DropRateInfo
{
    private final String displayRate;
    private final double oneIn;

    DropRateInfo(String displayRate, double oneIn)
    {
        this.displayRate = displayRate;
        this.oneIn = oneIn;
    }

    String getDisplayRate()
    {
        return displayRate;
    }

    double getOneIn()
    {
        return oneIn;
    }
}
