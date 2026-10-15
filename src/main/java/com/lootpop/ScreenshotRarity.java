package com.lootpop;

public enum ScreenshotRarity
{
    RARE("Rare (1/512+)"),
    VERY_RARE("Very rare (1/2,000+)"),
    ULTRA_RARE("Ultra rare (1/5,000+)");

    private final String displayName;

    ScreenshotRarity(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
