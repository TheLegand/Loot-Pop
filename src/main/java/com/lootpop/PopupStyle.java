package com.lootpop;

public enum PopupStyle
{
    CLASSIC_FRAME("Classic Frame"),
    MINIMAL_EDGE("Minimal Edge"),
    ULTRA_COMPACT("Ultra Compact");

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
