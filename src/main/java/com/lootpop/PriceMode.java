package com.lootpop;

public enum PriceMode
{
    GRAND_EXCHANGE("Grand Exchange"),
    HIGH_ALCHEMY("High alchemy");

    private final String displayName;

    PriceMode(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
