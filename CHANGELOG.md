# Changelog

## 1.3.1

Changes since 1.3.0.

- Fixed duplicate NPC loot popups when using Bonecrusher. The same drop is no longer counted twice in the session or Journal.
- Identical rewards from separate rapid NPC kills are now kept as separate drops.
- Added **Show side panel** at the top of the plugin settings. The sidebar button can be hidden or restored without restarting the plugin; popups, session tracking and saved history keep working.

## 1.3.0

Changes since 1.2.0.

### Loot popups and reveals

- Added Sealed Drop reveals in Enhanced mode. Eligible rare items reveal one at a time before the rest of the loot appears.
- Added a gold reveal for confirmed new Collection Log unlocks, even when the item itself is common.
- Reveal rarity and speed are configurable, and clicking skips the animation. Performance mode stays instant; Reduce Motion uses brief fades.
- Popup reading time now starts after the reveal finishes. Item names, values and hover details stay hidden until their reward is revealed.
- Added per-source personal bests, with an optional NEW BEST banner when a drop beats a previous record.

### Keep more of your loot history

- Added a Loot Journal that saves observed drops locally, with a separate history for each character. Browse by date, filter by source, or switch to Moments and Records to find the highlights.
- Journal files use RuneLite's Filepath API and plugin data directory, with automatic migration from the earlier development location when the destination is absent.
- Choose Separate to see each drop on its own, or Grouped to combine the items recorded on a given day. Grouping only changes the Journal view; live popups still show each drop separately.
- Added a Trophy Shelf for source bests, Ultra rare drops and new Collection Log unlocks. It has its own storage limit, so highlights can stay after ordinary history expires.

### Look back at a session or source

- Added Session Recap with your session total, best drop, top item, rarest known drop and Collection Log highlights. It keeps those highlights even after drops leave the Recent Loot list.
- Added Source Profiles. Click a source name to see its saved observations, recorded value, best drop and recent highlights.
- GE and HA summaries stay separate. Journal values are kept as recorded rather than recalculated at today's prices.

### Everyday improvements and fixes

- Refreshed the bundled OSRS Wiki drop tables with newly listed drops, revised chances and updated NPC-version entries. Drop-rate lookups still work offline while playing.
- Refreshed the sidebar with clearer text, a simpler session summary and less repeated copy. Journal history scrolls inside the panel instead of stretching the game window.
- Added clearer date controls and a date picker. The newest date opens first, and you can keep several dates open while browsing.
- Added right-click actions to copy item names, open the Wiki and view source profiles. Esc closes menus or backs out of sidebar details without adding a game-wide shortcut.
- Added a confirmation before resetting a non-empty session. Session Reset leaves the saved Journal and Trophy Shelf alone.
- Missing GE quotes now show Price pending or a known subtotal instead of being treated as zero. Incomplete drops cannot set best-drop records, and HA is not used as a fallback for a missing GE price.
- Improved item separation, Collection Log markers and overflow hover readability. Qty and Stack rows are omitted for single items.
- Improved matching of Collection Log confirmations to the correct recent item, including delayed messages.

### About saved history

The Journal records drops observed while saving is enabled; it does not import earlier loot or track every kill. By default, it keeps up to 2,000 drops for 90 days and up to 500 trophies. These limits are configurable. History stays on your device, and Session Recap lasts only for the current session. Totals are recorded loot value, not net profit.
