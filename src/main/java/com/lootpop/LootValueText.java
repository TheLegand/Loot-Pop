package com.lootpop;

final class LootValueText
{
    private LootValueText() { }

    static String compact(long knownValue, boolean pending)
    {
        return !pending ? LootPopOverlay.formatGp(knownValue) : knownValue <= 0
            ? "Price pending" : LootPopOverlay.formatGp(knownValue).replace(" gp", "+ gp");
    }

    static String detail(long knownValue, boolean pending)
    {
        return pending ? "Known subtotal: " + LootPopOverlay.formatExactGp(knownValue)
            + " / Total incomplete: missing prices" : LootPopOverlay.formatExactGp(knownValue);
    }
}
