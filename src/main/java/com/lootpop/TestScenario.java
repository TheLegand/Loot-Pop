package com.lootpop;

public enum TestScenario
{
    SINGLE_POPUP("Single popup"),
    POPUP_STACK("Three separate kills"),
    MANY_ITEMS("Many items + overflow"),
    COLLECTION_LOG("Collection log unlock");

    private final String displayName;

    TestScenario(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
