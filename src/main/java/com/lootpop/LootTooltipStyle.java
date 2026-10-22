package com.lootpop;

import java.awt.Color;

final class LootTooltipStyle
{
    static final Color ALCH_PROFIT = new Color(105, 209, 125);

    static Color highAlchemy(long ge, long ha, boolean gePending)
    { return !gePending && ha > ge ? ALCH_PROFIT : Color.WHITE; }

    static Color rarity(DropRarity rarity, RarityPalette palette)
    {
        boolean colourblind = palette == RarityPalette.COLOURBLIND;
        switch (rarity)
        {
            case UNCOMMON: return colourblind ? new Color(0, 158, 115) : new Color(105, 209, 125);
            case RARE: return colourblind ? new Color(0, 114, 178) : new Color(93, 173, 226);
            case VERY_RARE: return colourblind ? new Color(230, 159, 0) : new Color(199, 125, 255);
            case ULTRA_RARE: return colourblind ? new Color(213, 94, 0) : new Color(255, 77, 94);
            case COMMON: return new Color(184, 192, 204);
            default: return new Color(180, 180, 180);
        }
    }

    static String hex(Color colour)
    { return String.format("%02x%02x%02x", colour.getRed(), colour.getGreen(), colour.getBlue()); }

    private LootTooltipStyle() {}
}
