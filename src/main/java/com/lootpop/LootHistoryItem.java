package com.lootpop;

import java.awt.image.BufferedImage;

final class LootHistoryItem
{
    private final int quantity;
    private final String name;
    private final long gePrice;
    private final long highAlchemyPrice;
    private final long stackValue;
    private final String dropRate;
    private final DropRarity rarity;
    private final BufferedImage image;

    LootHistoryItem(
        int quantity,
        String name,
        long gePrice,
        long highAlchemyPrice,
        long stackValue,
        String dropRate,
        DropRarity rarity,
        BufferedImage image
    )
    {
        this.quantity = quantity;
        this.name = name;
        this.gePrice = gePrice;
        this.highAlchemyPrice = highAlchemyPrice;
        this.stackValue = stackValue;
        this.dropRate = dropRate;
        this.rarity = rarity;
        this.image = image;
    }

    int getQuantity()
    {
        return quantity;
    }

    String getName()
    {
        return name;
    }

    long getGePrice()
    {
        return gePrice;
    }

    long getHighAlchemyPrice()
    {
        return highAlchemyPrice;
    }

    long getStackValue()
    {
        return stackValue;
    }

    String getDropRate()
    {
        return dropRate;
    }

    DropRarity getRarity()
    {
        return rarity;
    }

    BufferedImage getImage()
    {
        return image;
    }
}
