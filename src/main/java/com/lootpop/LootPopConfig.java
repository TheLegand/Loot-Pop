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
        name = "Drop rarity",
        description = "Wiki drop-rate tiers, lucky drops and item spotlighting",
        position = 4
    )
    String dropRaritySection = "dropRarity";

    @ConfigSection(
        name = "Hover details",
        description = "Choose the information shown while hovering an item",
        position = 5,
        closedByDefault = true
    )
    String hoverDetailsSection = "hoverDetails";

    @ConfigSection(
        name = "Colours",
        description = "Popup colours",
        position = 8,
        closedByDefault = true
    )
    String coloursSection = "colours";

    @ConfigSection(
        name = "Tier effects",
        description = "Choose animated effects for each value tier",
        position = 6
    )
    String tierEffectsSection = "tierEffects";

    @ConfigSection(
        name = "Sound & testing",
        description = "Rare-drop sounds and instant popup previews",
        position = 9,
        closedByDefault = true
    )
    String soundTestingSection = "soundTesting";

    @ConfigSection(
        name = "Accessibility",
        description = "Motion, contrast and colour accessibility",
        position = 7,
        closedByDefault = true
    )
    String accessibilitySection = "accessibility";

    @ConfigSection(
        name = "Rare screenshots",
        description = "Optional local screenshots for valuable or rare drops",
        position = 10,
        closedByDefault = true
    )
    String screenshotsSection = "screenshots";

    @ConfigSection(
        name = "Side panel",
        description = "Recent loot and session summary controls",
        position = 11,
        closedByDefault = true
    )
    String sidePanelSection = "sidePanel";

    @ConfigItem(
        keyName = "visualMode",
        name = "Experience mode",
        description = "Performance keeps the original lightweight look; Enhanced enables all visual polish",
        position = 0,
        section = appearanceSection
    )
    default VisualMode visualMode()
    {
        return VisualMode.ENHANCED;
    }

    @ConfigItem(
        keyName = "popupStyle",
        name = "Popup style",
        description = "Choose Classic Frame, Rugged Panel or Loot Strip",
        position = 1,
        section = appearanceSection
    )
    default PopupStyle popupStyle()
    {
        return PopupStyle.CLASSIC_FRAME;
    }

    @ConfigItem(
        keyName = "backgroundMode",
        name = "Background",
        description = "Use a solid or transparent background",
        position = 2,
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
        position = 3,
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
        position = 4,
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
        position = 5,
        section = appearanceSection
    )
    default boolean showShadow()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showItemTooltips",
        name = "Item hover details",
        description = "Show quantity, GE and high alchemy values, stack value and Wiki drop chance while hovering an icon",
        position = 6,
        section = appearanceSection
    )
    default boolean showItemTooltips()
    {
        return true;
    }

    @ConfigItem(
        keyName = "hoverDetailMode",
        name = "Hover preset",
        description = "Compact shows essentials, Detailed shows everything, and Custom uses the switches below",
        position = 0,
        section = hoverDetailsSection
    )
    default HoverDetailMode hoverDetailMode()
    {
        return HoverDetailMode.DETAILED;
    }

    @ConfigItem(
        keyName = "hoverShowPrices",
        name = "Custom: GE and HA prices",
        description = "Show Grand Exchange and high alchemy unit prices in Custom mode",
        position = 1,
        section = hoverDetailsSection
    )
    default boolean hoverShowPrices()
    {
        return true;
    }

    @ConfigItem(
        keyName = "hoverShowStackValue",
        name = "Custom: stack value",
        description = "Show the selected price source stack value in Custom mode",
        position = 2,
        section = hoverDetailsSection
    )
    default boolean hoverShowStackValue()
    {
        return true;
    }

    @ConfigItem(
        keyName = "hoverShowDropChance",
        name = "Custom: drop chance",
        description = "Show the bundled OSRS Wiki drop chance in Custom mode",
        position = 3,
        section = hoverDetailsSection
    )
    default boolean hoverShowDropChance()
    {
        return true;
    }

    @Range(min = 500, max = 60000)
    @ConfigItem(
        keyName = "displayDuration",
        name = "Visible time (ms)",
        description = "How long the popup stays fully visible before fading",
        position = 0,
        section = behaviourSection
    )
    default int displayDuration()
    {
        return 3500;
    }

    @Range(min = 0, max = 10000)
    @ConfigItem(
        keyName = "fadeDuration",
        name = "Fade-out time (ms)",
        description = "How long the popup takes to disappear after its visible time",
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
        keyName = "pauseOnHover",
        name = "Pause while hovering",
        description = "Pause a popup's visible and fade timers while the mouse is over it",
        position = 5,
        section = behaviourSection
    )
    default boolean pauseOnHover()
    {
        return true;
    }

    @Range(min = 0, max = 30)
    @ConfigItem(
        keyName = "queuedPopups",
        name = "Queued popups",
        description = "Keep extra kills waiting when the visible popup stack is full; zero disables the queue",
        position = 6,
        section = behaviourSection
    )
    default int queuedPopups()
    {
        return 12;
    }

    @ConfigItem(
        keyName = "adaptiveDisplayTime",
        name = "Adaptive display time",
        description = "Keep the normal visible time as a minimum and give rare drops extra time",
        position = 7,
        section = behaviourSection
    )
    default boolean adaptiveDisplayTime()
    {
        return true;
    }

    @Range(min = 0, max = 10000)
    @ConfigItem(
        keyName = "rareExtraTime",
        name = "Rare extra time (ms)",
        description = "Extra visible time for Rare and Very rare drops",
        position = 8,
        section = behaviourSection
    )
    default int rareExtraTime()
    {
        return 1_500;
    }

    @Range(min = 0, max = 20000)
    @ConfigItem(
        keyName = "ultraRareExtraTime",
        name = "Ultra extra time (ms)",
        description = "Extra visible time for Ultra rare drops",
        position = 9,
        section = behaviourSection
    )
    default int ultraRareExtraTime()
    {
        return 3_500;
    }

    @ConfigItem(
        keyName = "smoothEntrance",
        name = "Smooth entrance",
        description = "Slide and scale Enhanced popups into view with a short item reveal",
        position = 10,
        section = behaviourSection
    )
    default boolean smoothEntrance()
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

    @Range(min = 0, max = 2_000_000_000)
    @ConfigItem(
        keyName = "lowValuePrice",
        name = "Low value price",
        description = "Price where the low value colour begins",
        position = 2,
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
        position = 3,
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
        position = 4,
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
        position = 5,
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
        position = 6,
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
        position = 7,
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
        position = 8,
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
        position = 9,
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
        position = 10,
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
        position = 11,
        section = valueColoursSection
    )
    default Color legendaryValueColour()
    {
        return new Color(255, 48, 61);
    }

    @ConfigItem(
        keyName = "enableDropRarity",
        name = "Drop rarity system",
        description = "Classify items independently from GP value using bundled OSRS Wiki drop rates",
        position = 0,
        section = dropRaritySection
    )
    default boolean enableDropRarity()
    {
        return true;
    }

    @Range(min = 2, max = 1_000_000)
    @ConfigItem(
        keyName = "uncommonRate",
        name = "Uncommon from 1 in",
        description = "Minimum one-in rate classified as Uncommon",
        position = 1,
        section = dropRaritySection
    )
    default int uncommonRate()
    {
        return 128;
    }

    @Range(min = 2, max = 1_000_000)
    @ConfigItem(
        keyName = "rareRate",
        name = "Rare from 1 in",
        description = "Minimum one-in rate classified as Rare",
        position = 2,
        section = dropRaritySection
    )
    default int rareRate()
    {
        return 512;
    }

    @Range(min = 2, max = 1_000_000)
    @ConfigItem(
        keyName = "veryRareRate",
        name = "Very rare from 1 in",
        description = "Minimum one-in rate classified as Very rare",
        position = 3,
        section = dropRaritySection
    )
    default int veryRareRate()
    {
        return 2_000;
    }

    @Range(min = 2, max = 1_000_000)
    @ConfigItem(
        keyName = "ultraRareRate",
        name = "Ultra rare from 1 in",
        description = "Minimum one-in rate classified as Ultra rare",
        position = 4,
        section = dropRaritySection
    )
    default int ultraRareRate()
    {
        return 5_000;
    }

    @Range(min = 2, max = 1_000_000)
    @ConfigItem(
        keyName = "luckyDropRate",
        name = "Lucky badge from 1 in",
        description = "Show the lucky marker when a popup contains an item at least this rare",
        position = 5,
        section = dropRaritySection
    )
    default int luckyDropRate()
    {
        return 1_000;
    }

    @ConfigItem(
        keyName = "showLuckyIndicator",
        name = "Lucky drop marker",
        description = "Show a small rarity-coloured star for lucky drops",
        position = 6,
        section = dropRaritySection
    )
    default boolean showLuckyIndicator()
    {
        return true;
    }

    @ConfigItem(
        keyName = "itemSpotlights",
        name = "Ultra-rare item glow",
        description = "Show a powerful animated glow behind Ultra rare items in Enhanced mode",
        position = 7,
        section = dropRaritySection
    )
    default boolean itemSpotlights()
    {
        return true;
    }

    @ConfigItem(
        keyName = "rarityPalette",
        name = "Rarity palette",
        description = "Choose the default or colourblind-safe rarity colours",
        position = 0,
        section = accessibilitySection
    )
    default RarityPalette rarityPalette()
    {
        return RarityPalette.DEFAULT;
    }

    @ConfigItem(
        keyName = "reduceMotion",
        name = "Reduce motion",
        description = "Keep glass, gradients and static rarity cues but disable animated tier effects",
        position = 1,
        section = accessibilitySection
    )
    default boolean reduceMotion()
    {
        return false;
    }

    @ConfigItem(
        keyName = "highContrastText",
        name = "High contrast text",
        description = "Use stronger text outlines for readability",
        position = 2,
        section = accessibilitySection
    )
    default boolean highContrastText()
    {
        return false;
    }

    @ConfigItem(
        keyName = "automaticScreenshots",
        name = "Automatic screenshots",
        description = "Save a local RuneLite screenshot when a drop meets either threshold below",
        position = 0,
        section = screenshotsSection
    )
    default boolean automaticScreenshots()
    {
        return false;
    }

    @ConfigItem(
        keyName = "screenshotRarity",
        name = "Rarity threshold",
        description = "Minimum known Wiki rarity that triggers a screenshot",
        position = 1,
        section = screenshotsSection
    )
    default ScreenshotRarity screenshotRarity()
    {
        return ScreenshotRarity.ULTRA_RARE;
    }

    @Range(min = 0, max = 2_000_000_000)
    @ConfigItem(
        keyName = "screenshotMinimumValue",
        name = "Value threshold",
        description = "Total GP value that triggers a screenshot; zero uses rarity only",
        position = 2,
        section = screenshotsSection
    )
    default int screenshotMinimumValue()
    {
        return 100_000_000;
    }

    @Range(min = 5, max = 50)
    @ConfigItem(
        keyName = "historySize",
        name = "Recent loot entries",
        description = "Maximum number of drops retained in the in-memory side-panel history",
        position = 0,
        section = sidePanelSection
    )
    default int historySize()
    {
        return 20;
    }

    @ConfigItem(
        keyName = "lowValueEffects",
        name = "Low — floating motes",
        description = "Show calm floating particles on low-value drops",
        position = 0,
        section = tierEffectsSection
    )
    default boolean lowValueEffects()
    {
        return true;
    }

    @ConfigItem(
        keyName = "mediumValueEffects",
        name = "Medium — sparkles",
        description = "Show rising sparkle particles on medium-value drops",
        position = 1,
        section = tierEffectsSection
    )
    default boolean mediumValueEffects()
    {
        return true;
    }

    @ConfigItem(
        keyName = "highValueEffects",
        name = "High — embers",
        description = "Show energetic ember particles on high-value drops",
        position = 2,
        section = tierEffectsSection
    )
    default boolean highValueEffects()
    {
        return true;
    }

    @ConfigItem(
        keyName = "insaneValueEffects",
        name = "Insane — energy pulse",
        description = "Show a pulsing aura and bright particles on insane-value drops",
        position = 3,
        section = tierEffectsSection
    )
    default boolean insaneValueEffects()
    {
        return true;
    }

    @ConfigItem(
        keyName = "legendaryEffects",
        name = "Legendary — crimson storm",
        description = "Show the crimson storm, light pillar and impact burst on legendary drops",
        position = 4,
        section = tierEffectsSection
    )
    default boolean legendaryEffects()
    {
        return true;
    }

    @Range(min = 25, max = 150)
    @ConfigItem(
        keyName = "effectIntensity",
        name = "Effects intensity",
        description = "Adjust the amount and brightness of enabled tier effects",
        position = 5,
        section = tierEffectsSection
    )
    default int effectIntensity()
    {
        return 100;
    }

    @ConfigItem(
        keyName = "rareDropSounds",
        name = "Rare drop sounds",
        description = "Play a different sound for High, Insane and Legendary drops in Enhanced mode",
        position = 0,
        section = soundTestingSection
    )
    default boolean rareDropSounds()
    {
        return true;
    }

    @Range(min = 0, max = 127)
    @ConfigItem(
        keyName = "dropSoundVolume",
        name = "Sound volume",
        description = "Volume of Loot Pop rare-drop sounds",
        position = 1,
        section = soundTestingSection
    )
    default int dropSoundVolume()
    {
        return 80;
    }

    @ConfigItem(
        keyName = "testTier",
        name = "Test tier",
        description = "Choose the value tier used by the next test popup",
        position = 2,
        section = soundTestingSection
    )
    default TestTier testTier()
    {
        return TestTier.LEGENDARY;
    }

    @ConfigItem(
        keyName = "testScenario",
        name = "Test scenario",
        description = "Preview one popup, three separate kills, many items with overflow, or a Collection Log unlock",
        position = 3,
        section = soundTestingSection
    )
    default TestScenario testScenario()
    {
        return TestScenario.SINGLE_POPUP;
    }

    @ConfigItem(
        keyName = "showTestPopup",
        name = "Show test popup",
        description = "Show a preview popup immediately; this switch resets itself",
        position = 4,
        section = soundTestingSection
    )
    default boolean showTestPopup()
    {
        return false;
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
