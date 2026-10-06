package com.lootpop;

public enum BackgroundMode
{
    SOLID("Solid"),
    TRANSPARENT("Transparent");

    private final String displayName;

    BackgroundMode(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
