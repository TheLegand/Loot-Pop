package com.lootpop;

public enum VisualMode
{
    PERFORMANCE("Performance"),
    ENHANCED("Enhanced");

    private final String displayName;

    VisualMode(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
