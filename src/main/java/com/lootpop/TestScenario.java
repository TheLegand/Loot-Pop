package com.lootpop;

public enum TestScenario
{
    SINGLE_POPUP("Single popup"),
    POPUP_STACK("Three separate kills"),
    MANY_ITEMS("Many items + overflow"),
    COLLECTION_LOG("Collection log unlock"),
    SEALED_DROP("Sealed Drop: one rare item"),
    SEALED_DOUBLE("Sealed Drop: two rare items"),
    SEALED_BATCH("Sealed Drop: multiple rares"),
    COLLECTION_LOG_COMMON("Collection Log: common item"),
    COLLECTION_LOG_LATE("Collection Log: delayed unlock"),
    PERSONAL_BEST("Personal best + rare reveal"),
    PRICE_PENDING("Price pending: mixed drop"),
    PRICE_PENDING_ONLY("Price pending: no known prices");

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
