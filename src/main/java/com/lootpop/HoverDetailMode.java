package com.lootpop;

public enum HoverDetailMode
{
    COMPACT("Compact"),
    DETAILED("Detailed"),
    CUSTOM("Custom");

    private final String displayName;

    HoverDetailMode(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
