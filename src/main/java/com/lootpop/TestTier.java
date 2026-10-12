package com.lootpop;

public enum TestTier
{
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    INSANE("Insane"),
    LEGENDARY("Legendary");

    private final String displayName;

    TestTier(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
