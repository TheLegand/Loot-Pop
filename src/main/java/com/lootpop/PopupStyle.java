package com.lootpop;

public enum PopupStyle
{
    CLASSIC_FRAME("Classic Frame"),
    MINIMAL_EDGE("Rugged Panel"),
    ULTRA_COMPACT("Loot Strip");

    private final String displayName;

    PopupStyle(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
