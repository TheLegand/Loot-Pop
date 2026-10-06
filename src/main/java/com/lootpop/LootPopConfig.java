package com.lootpop;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(LootPopConfig.GROUP)
public interface LootPopConfig extends Config
{
    String GROUP = "lootpop";

    @ConfigSection(
        name = "Appearance",
        description = "Popup layout and background",
        position = 0
    )
    String appearanceSection = "appearance";

    @ConfigSection(
        name = "Behaviour",
        description = "Popup timing and stacking",
        position = 1
    )
    String behaviourSection = "behaviour";

    @ConfigSection(
        name = "Loot filters",
        description = "Choose which drops create a popup",
        position = 2
    )
    String filtersSection = "filters";

    @ConfigSection(
        name = "Value colours",
        description = "Price tiers, colour fade and legendary effects",
        position = 3
    )
    String valueColoursSection = "valueColours";

    @ConfigSection(
        name = "Colours",
        description = "Popup colours",
        position = 4,
        closedByDefault = true
    )
    String coloursSection = "colours";

    @ConfigItem(
        keyName = "popupStyle",
        name = "Popup style",
        description = "Choose one of the three compact popup layouts",
        position = 0,
        section = appearanceSection
    )
    default PopupStyle popupStyle()
    {
        return PopupStyle.MINIMAL_EDGE;
    }

    @ConfigItem(
        keyName = "backgroundMode",
        name = "Background",
        description = "Use a solid or transparent background",
        position = 1,
        section = appearanceSection
    )
    default BackgroundMode backgroundMode()
    {
        return BackgroundMode.SOLID;
    }

    @Range(min = 70, max = 160)
    @ConfigItem(
        keyName = "scale",
        name = "Scale",
        description = "Scale the whole popup",
        position = 2,
        section = appearanceSection
    )
    default int scale()
    {
        return 100;
    }

    @Range(min = 1, max = 12)
    @ConfigItem(
        keyName = "maxItems",
        name = "Visible items",
        description = "Maximum number of item icons shown in each popup",
        position = 3,
        section = appearanceSection
    )
    default int maxItems()
    {
        return 8;
    }

    @ConfigItem(
        keyName = "showShadow",
        name = "Drop shadow",
        description = "Draw a soft shadow behind solid popups",
        position = 4,
        section = appearanceSection
    )
    default boolean showShadow()
    {
        return true;
    }

    @Range(min = 1000, max = 15000)
    @ConfigItem(
        keyName = "displayDuration",
        name = "Display time (ms)",
        description = "How long a popup remains fully visible",
        position = 0,
        section = behaviourSection
    )
    default int displayDuration()
    {
        return 3500;
    }

    @Range(min = 0, max = 4000)
    @ConfigItem(
        keyName = "fadeDuration",
        name = "Fade time (ms)",
        description = "How long the popup takes to fade out",
        position = 1,
        section = behaviourSection
    )
    default int fadeDuration()
    {
        return 650;
    }

    @Range(min = 1, max = 6)
    @ConfigItem(
        keyName = "maxPopups",
        name = "Maximum popups",
        description = "Maximum number of loot notifications shown at once",
        position = 2,
        section = behaviourSection
    )
    default int maxPopups()
    {
        return 3;
    }

    @Range(min = 0, max = 16)
    @ConfigItem(
        keyName = "popupSpacing",
        name = "Popup spacing",
        description = "Vertical space between stacked popups",
        position = 3,
        section = behaviourSection
    )
    default int popupSpacing()
    {
        return 5;
    }

    @ConfigItem(
        keyName = "newestOnTop",
        name = "Newest on top",
        description = "Place the newest notification at the top of the stack",
        position = 4,
        section = behaviourSection
    )
    default boolean newestOnTop()
    {
        return true;
    }

    @ConfigItem(
        keyName = "priceMode",
        name = "Price source",
        description = "Value drops using Grand Exchange or high alchemy prices",
        position = 0,
        section = filtersSection
    )
    default PriceMode priceMode()
    {
        return PriceMode.GRAND_EXCHANGE;
    }

    @ConfigItem(
        keyName = "includeNpcLoot",
        name = "NPC drops",
        description = "Show loot dropped by NPCs",
        position = 1,
        section = filtersSection
    )
    default boolean includeNpcLoot()
    {
        return true;
    }

    @ConfigItem(
        keyName = "includeEventLoot",
        name = "Boss and event loot",
        description = "Show loot from bosses, raids, chests and other tracked events",
        position = 2,
        section = filtersSection
    )
    default boolean includeEventLoot()
    {
        return true;
    }

    @ConfigItem(
        keyName = "includePlayerLoot",
        name = "Player loot",
        description = "Show loot received from players",
        position = 3,
        section = filtersSection
    )
    default boolean includePlayerLoot()
    {
        return false;
    }

    @ConfigItem(
        keyName = "includePickpocketLoot",
        name = "Pickpocket loot",
        description = "Show tracked pickpocket loot",
        position = 4,
        section = filtersSection
    )
    default boolean includePickpocketLoot()
    {
        return false;
    }

    @ConfigItem(
        keyName = "sortByValue",
        name = "Sort by value",
        description = "Put the most valuable item icons first",
        position = 5,
        section = filtersSection
    )
    default boolean sortByValue()
    {
        return true;
    }

    @Range(min = 0, max = 2_000_000_000)
    @ConfigItem(
        keyName = "minimumDropValue",
        name = "Minimum total value",
        description = "Do not show drops worth less than this amount",
        position = 6,
        section = filtersSection
    )
    default int minimumDropValue()
    {
        return 0;
    }

    @ConfigItem(
        keyName = "showZeroValueItems",
        name = "Show valueless items",
        description = "Include items that have no known price",
        position = 7,
        section = filtersSection
    )
    default boolean showZeroValueItems()
    {
        return true;
    }

    @ConfigItem(
        keyName = "ignoredSources",
        name = "Ignored sources",
        description = "Comma-separated NPC or event names. Asterisks are supported",
        position = 8,
        section = filtersSection
    )
    default String ignoredSources()
    {
        return "";
    }

    @ConfigItem(
        keyName = "ignoredItems",
        name = "Ignored items",
        description = "Comma-separated item names. Asterisks are supported",
        position = 9,
        section = filtersSection
    )
    default String ignoredItems()
    {
        return "";
    }

    @ConfigItem(
        keyName = "enableValueColours",
        name = "Value colour tiers",
        description = "Colour the accent, total value and background fade by drop value",
        position = 0,
        section = valueColoursSection
    )
    default boolean enableValueColours()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showSeparator",
        name = "Content separator",
        description = "Draw a subtle line between the header or value and the item icons",
        position = 1,
        section = valueColoursSection
    )
    default boolean showSeparator()
    {
        return true;
    }

    @Range(min = 0, max = 100)
    @ConfigItem(
        keyName = "valueTintStrength",
        name = "Colour fade strength",
        description = "Strength of the value-coloured background gradient in both background modes",
        position = 2,
        section = valueColoursSection
    )
    default int valueTintStrength()
    {
        return 38;
    }

    @Range(min = 0, max = 2_000_000_000)
    @ConfigItem(
        keyName = "lowValuePrice",
        name = "Low value price",
        description = "Price where the low value colour begins",
        position = 3,
        section = valueColoursSection
    )
    default int lowValuePrice()
    {
        return 20_000;
    }

    @Alpha
    @ConfigItem(
        keyName = "lowValueColour",
        name = "Low value colour",
        description = "Colour used for low value drops",
        position = 4,
        section = valueColoursSection
    )
    default Color lowValueColour()
    {
        return new Color(102, 178, 255);
    }

    @Range(min = 0, max = 2_000_000_000)
    @ConfigItem(
        keyName = "mediumValuePrice",
        name = "Medium value price",
        description = "Price where the medium value colour begins",
        position = 5,
        section = valueColoursSection
    )
    default int mediumValuePrice()
    {
        return 100_000;
    }

    @Alpha
    @ConfigItem(
        keyName = "mediumValueColour",
        name = "Medium value colour",
        description = "Colour used for medium value drops",
        position = 6,
        section = valueColoursSection
    )
    default Color mediumValueColour()
    {
        return new Color(154, 255, 154);
    }

    @Range(min = 0, max = 2_000_000_000)
    @ConfigItem(
        keyName = "highValuePrice",
        name = "High value price",
        description = "Price where the high value colour begins",
        position = 7,
        section = valueColoursSection
    )
    default int highValuePrice()
    {
        return 1_000_000;
    }

    @Alpha
    @ConfigItem(
        keyName = "highValueColour",
        name = "High value colour",
        description = "Colour used for high value drops",
        position = 8,
        section = valueColoursSection
    )
    default Color highValueColour()
    {
        return new Color(255, 150, 0);
    }

    @Range(min = 0, max = 2_000_000_000)
    @ConfigItem(
        keyName = "insaneValuePrice",
        name = "Insane value price",
        description = "Price where the insane value colour begins",
        position = 9,
        section = valueColoursSection
    )
    default int insaneValuePrice()
    {
        return 10_000_000;
    }

    @Alpha
    @ConfigItem(
        keyName = "insaneValueColour",
        name = "Insane value colour",
        description = "Colour used for insane value drops",
        position = 10,
        section = valueColoursSection
    )
    default Color insaneValueColour()
    {
        return new Color(255, 102, 178);
    }

    @Range(min = 0, max = 2_000_000_000)
    @ConfigItem(
        keyName = "legendaryValuePrice",
        name = "Legendary value price",
        description = "Price where the legendary colour and effects begin",
        position = 11,
        section = valueColoursSection
    )
    default int legendaryValuePrice()
    {
        return 100_000_000;
    }

    @Alpha
    @ConfigItem(
        keyName = "legendaryValueColour",
        name = "Legendary colour",
        description = "Colour used for legendary drops",
        position = 12,
        section = valueColoursSection
    )
    default Color legendaryValueColour()
    {
        return new Color(255, 211, 78);
    }

    @ConfigItem(
        keyName = "legendaryEffects",
        name = "Legendary particles",
        description = "Show an animated shimmer and particles on legendary drops",
        position = 13,
        section = valueColoursSection
    )
    default boolean legendaryEffects()
    {
        return true;
    }

    @Alpha
    @ConfigItem(
        keyName = "backgroundColour",
        name = "Background colour",
        description = "Base popup background colour",
        position = 0,
        section = coloursSection
    )
    default Color backgroundColour()
    {
        return new Color(15, 17, 19);
    }

    @Range(min = 20, max = 255)
    @ConfigItem(
        keyName = "solidOpacity",
        name = "Solid opacity",
        description = "Opacity used by the Solid background mode",
        position = 1,
        section = coloursSection
    )
    default int solidOpacity()
    {
        return 235;
    }

    @Range(min = 20, max = 220)
    @ConfigItem(
        keyName = "transparentOpacity",
        name = "Transparent opacity",
        description = "Opacity used by the Transparent background mode",
        position = 2,
        section = coloursSection
    )
    default int transparentOpacity()
    {
        return 105;
    }

    @Alpha
    @ConfigItem(
        keyName = "accentColour",
        name = "Accent colour",
        description = "Frame or edge accent colour",
        position = 3,
        section = coloursSection
    )
    default Color accentColour()
    {
        return new Color(151, 134, 62);
    }

    @Alpha
    @ConfigItem(
        keyName = "npcNameColour",
        name = "NPC name colour",
        description = "Colour used for the NPC name",
        position = 4,
        section = coloursSection
    )
    default Color npcNameColour()
    {
        return new Color(255, 224, 140);
    }

    @Alpha
    @ConfigItem(
        keyName = "totalValueColour",
        name = "Total value colour",
        description = "Colour used for the total drop value",
        position = 5,
        section = coloursSection
    )
    default Color totalValueColour()
    {
        return new Color(42, 218, 83);
    }

}
