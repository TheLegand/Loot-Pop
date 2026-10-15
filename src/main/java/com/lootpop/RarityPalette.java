package com.lootpop;

public enum RarityPalette
{
    DEFAULT("Default"),
    COLOURBLIND("Colourblind safe");

    private final String displayName;

    RarityPalette(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
