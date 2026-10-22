package com.lootpop;

/** A missing market quote is not a zero-value item. HA never substitutes for a missing GE quote. */
final class LootPriceQuote
{
    final long value;
    final boolean pending;
    final boolean gePending;

    private LootPriceQuote(long value, boolean pending, boolean gePending)
    {
        this.value = pending ? 0 : Math.max(0, value);
        this.pending = pending;
        this.gePending = gePending;
    }

    static LootPriceQuote of(PriceMode mode, boolean definitionKnown, boolean tradeable, long ge, long ha)
    {
        boolean marketMissing = ge <= 0 && (!definitionKnown || tradeable);
        return new LootPriceQuote(mode == PriceMode.HIGH_ALCHEMY ? ha : ge,
            mode == PriceMode.HIGH_ALCHEMY ? !definitionKnown : marketMissing, marketMissing);
    }

    boolean shouldInclude(boolean showZeroValues) { return pending || value > 0 || showZeroValues; }

    static boolean passesMinimum(long knownValue, long minimum, java.util.List<LootDisplayItem> items)
    { return knownValue >= minimum || items.stream().anyMatch(LootDisplayItem::isPricePending); }
}
