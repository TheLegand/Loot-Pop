package com.lootpop;

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
}
