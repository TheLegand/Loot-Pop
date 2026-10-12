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

    LootDisplayItem(int itemId, int quantity, long unitPrice)
    {
        this.itemId = itemId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
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
            merged.put(item.itemId, new LootDisplayItem(item.itemId, quantity, existing.unitPrice));
        }
        return new ArrayList<>(merged.values());
    }
}
