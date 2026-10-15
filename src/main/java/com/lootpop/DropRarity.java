package com.lootpop;

enum DropRarity
{
    UNKNOWN("Unknown"),
    COMMON("Common"),
    UNCOMMON("Uncommon"),
    RARE("Rare"),
    VERY_RARE("Very rare"),
    ULTRA_RARE("Ultra rare");

    private final String displayName;

    DropRarity(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
