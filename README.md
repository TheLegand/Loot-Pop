<p align="center">
  <img src="docs/loot-pop-logo.png" width="210" alt="Loot Pop logo">
</p>

<h1 align="center">Loot Pop</h1>

<p align="center">
  A compact, value-aware loot popup for RuneLite.<br>
  See every drop instantly without giving up valuable game-screen space.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/RuneLite-Plugin%20Hub-F28C28" alt="RuneLite Plugin Hub">
  <img src="https://img.shields.io/badge/version-1.1.0-3fb950" alt="Version 1.1.0">
  <img src="https://img.shields.io/badge/Java-11-5382a1" alt="Java 11">
  <img src="https://img.shields.io/badge/license-BSD--2--Clause-8b5cf6" alt="BSD 2-Clause license">
</p>

## Pick your experience

Loot Pop includes two complete experience modes. Both retain the essential loot information; only the presentation cost changes.

| | **Performance** | **Enhanced** |
| --- | --- | --- |
| Best for | Maximum FPS and low visual noise | Full presentation and rare-drop excitement |
| Compact loot popups | Yes | Yes |
| Combined item stacks and coloured `xN` labels | Yes | Yes |
| Value tiers and item hover details | Yes | Yes |
| Glass treatment and item glows | No | Yes |
| Animated tier particles | No | Yes |
| High, Insane and Legendary sound cues | No | Yes |

**Performance** keeps the lightweight behaviour of the original release while retaining every core usability feature. **Enhanced** adds glass, colour-reactive lighting, particles, item glows and layered rare-drop sounds. Change modes at any time from the plugin settings.

## Three distinct popup styles

Every layout supports both **Solid** and **Transparent** backgrounds, configurable opacity, value colours and scaling.

<table>
  <tr>
    <th>Classic Frame</th>
    <th>Rugged Panel</th>
    <th>Loot Strip</th>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/classic-frame-v1.1.png" width="310" alt="Classic Frame popup"></td>
    <td align="center"><img src="docs/screenshots/rugged-panel.png" width="310" alt="Rugged Panel popup"></td>
    <td align="center"><img src="docs/screenshots/loot-strip.png" width="310" alt="Loot Strip popup"></td>
  </tr>
  <tr>
    <td align="center">Balanced two-row layout with a clean full outline.</td>
    <td align="center">Reinforced cut corners, dark header and tier-coloured hardware.</td>
    <td align="center">Fast horizontal layout with separate source, item and value modules.</td>
  </tr>
</table>

## Value tiers in motion

Enhanced mode gives each value tier its own visual language. Thresholds, colours and individual effects are fully configurable.

<table>
  <tr>
    <th>Low · 20K</th>
    <th>Medium · 100K</th>
  </tr>
  <tr>
    <td align="center"><img src="docs/demos/low-tier.gif" alt="Low value floating motes"></td>
    <td align="center"><img src="docs/demos/medium-tier.gif" alt="Medium value rising sparkles"></td>
  </tr>
  <tr>
    <th>High · 1M</th>
    <th>Insane · 10M</th>
  </tr>
  <tr>
    <td align="center"><img src="docs/demos/high-tier.gif" alt="High value ember effect"></td>
    <td align="center"><img src="docs/demos/insane-tier.gif" alt="Insane value energy effect"></td>
  </tr>
  <tr>
    <th colspan="2">Legendary · 100M</th>
  </tr>
  <tr>
    <td colspan="2" align="center"><img src="docs/demos/legendary-tier.gif" width="486" alt="Legendary crimson drop celebration"></td>
  </tr>
</table>

Legendary drops break beyond the popup frame with a crimson light pillar, energy rain, crown star, rotating rays, shockwaves and an impact burst. Every tier effect is enabled by default and can be disabled separately.

## Highlights

- Combines duplicate entries from the same drop into one icon and one total quantity.
- Uses natural RuneScape-style `xN` labels coloured by the complete value of each item stack.
- Prevents long quantities and coin values from overlapping neighbouring items.
- Shows native RuneLite hover details with item name, quantity, unit price and stack value.
- Supports Grand Exchange or high-alchemy valuation.
- Provides configurable visible time and fade-out duration.
- Displays up to six simultaneous popups with adjustable spacing and item limits.
- Adds a compact `+N` indicator when a drop contains more items than the visible limit.
- Includes distinct optional sound cues for High, Insane and Legendary drops.
- Provides built-in test popups for every value tier—no rare drop required.
- Supports NPC, boss/event, player and pickpocket loot.
- Includes source and item ignore lists with wildcard support.
- Starts beside the inventory area and remains movable and resizable through RuneLite's overlay controls.

## Controls and configuration

Open RuneLite settings and search for **Loot Pop**.

- **Appearance:** experience mode, popup style, background, scale and visible items.
- **Behaviour:** full-visibility time, fade duration, popup spacing and simultaneous popup count.
- **Loot filters:** minimum value, loot sources and ignore lists.
- **Value colours:** custom thresholds and colours for every tier.
- **Tier effects:** enable effects per tier and adjust their global intensity.
- **Sound & testing:** rare-drop audio, volume and instant tier previews.

Hold <kbd>Alt</kbd> while dragging the overlay to reposition it. While holding <kbd>Alt</kbd>, drag a corner handle to resize the complete popup without stretching the item sprites. RuneLite preserves the chosen position and size.

## Installation

1. Open RuneLite.
2. Open the **Plugin Hub**.
3. Search for **Loot Pop**.
4. Select **Install**.

Plugin updates are delivered through the RuneLite Plugin Hub after review.

## Privacy and fair play

Loot Pop is display-only. It does not automate game input, contact an external server, upload data or require an API key. Item sprites and pricing data come from RuneLite's existing APIs.

## Development

The project targets Java 11 and the current RuneLite release.

```powershell
.\gradlew.bat test
.\gradlew.bat run
```

The development client must be tested manually in-game.

## Attribution

The event-handling approach was checked against the reviewed Plugin Hub revision of [DevMisery/LootHUD](https://github.com/DevMisery/LootHUD/tree/a0293870c2f4e318be016e6a904c0126ff169886). The rendering, layouts, effects, settings and implementation in this repository are original.

## License

Loot Pop is available under the [BSD 2-Clause License](LICENSE).
