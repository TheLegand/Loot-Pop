package com.lootpop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LootDisplayItem
{
    private final int itemId;
    private final int quantity;
    private final long unitPrice;
    private final String dropRate;
    private final double dropRateOneIn;

    LootDisplayItem(int itemId, int quantity, long unitPrice)
    {
        this(itemId, quantity, unitPrice, null, 0.0);
    }

    LootDisplayItem(int itemId, int quantity, long unitPrice, String dropRate)
    {
        this(itemId, quantity, unitPrice, dropRate, 0.0);
    }

    LootDisplayItem(int itemId, int quantity, long unitPrice, String dropRate, double dropRateOneIn)
    {
        this.itemId = itemId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.dropRate = dropRate;
        this.dropRateOneIn = dropRateOneIn;
    }

    int getItemId()
    {
        return itemId;
    }

    int getQuantity()
    {
        return quantity;
    }

    long getUnitPrice()
    {
        return unitPrice;
    }

    long getTotalValue()
    {
        return unitPrice * quantity;
    }

    String getDropRate()
    {
        return dropRate;
    }

    double getDropRateOneIn()
    {
        return dropRateOneIn;
    }

    static List<LootDisplayItem> mergeDuplicates(List<LootDisplayItem> items)
    {
        Map<Integer, LootDisplayItem> merged = new LinkedHashMap<>();
        for (LootDisplayItem item : items)
        {
            LootDisplayItem existing = merged.get(item.itemId);
            if (existing == null)
            {
                merged.put(item.itemId, item);
                continue;
            }

            long combinedQuantity = (long) existing.quantity + item.quantity;
            int quantity = (int) Math.min(Integer.MAX_VALUE, combinedQuantity);
            String dropRate = existing.dropRate != null ? existing.dropRate : item.dropRate;
            double oneIn = existing.dropRateOneIn > 0.0 ? existing.dropRateOneIn : item.dropRateOneIn;
            merged.put(item.itemId, new LootDisplayItem(item.itemId, quantity, existing.unitPrice, dropRate, oneIn));
        }
        return new ArrayList<>(merged.values());
    }
}
