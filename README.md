# World Preview TFC
Fork of [World Preview](https://www.curseforge.com/minecraft/mc-mods/world-preview).
World Preview TFC adds a World Preview tab to the Create World screen and uses TerraFirmaCraft (TFC) world generation to preview seeds.
It can visualize biomes, temperature, rainfall, land and water, rock types, rock layers, kaolin clay areas and hotspots.

Downloads: Modrinth (https://modrinth.com/mod/world-preview-terrafirmacraft) and CurseForge (https://www.curseforge.com/minecraft/mc-mods/world-preview-tfc).
Issue tracker: https://github.com/TKFT/World-Preview-TFC/issues

## Requirements
- Minecraft 1.21.1
- NeoForge 21.1.213+ (21.1.218 recommended)
- TerraFirmaCraft (TFC) 4.0.17+
- Java 21

## Installation
1. Install NeoForge for Minecraft 1.21.1.
2. Install TerraFirmaCraft (TFC) 4.0.17+.
3. Place the World Preview TFC jar in your instance `mods` folder.

## Usage
Open `Create New World` and switch to the `World Preview` tab. Drag on the map to move along X and Z. This will load:
- Biomes
- Heightmap (if enabled)
- Rainfall Map
- Temperature Map
- Land/Water/River Map
- Rock Layer Maps
- Rock Type Map
- Kaolin Clay Areas Map
- Hotspot Map
- Volcano Icons

### TFC Settings Tab

<img alt="tfc-settings" src="images/tfc-settings-tab.png" width="100%"/>

### Map Modes

Biomes Map Mode

<img alt="biomes" src="images/biome-map.png" width="100%"/>

Land and Water Map Mode

<img alt="land-water" src="images/land-water-map.png" width="100%"/>

Temperature Map Mode

<img alt="temperature" src="images/tempature-map.png" width="100%"/>

Rainfall Map Mode

<img alt="rainfall" src="images/rainfall-map.png" width="100%"/>

Rock Type Map Mode

<img alt="rock-type" src="images/rock-type-map.png" width="100%"/>

Rock Layers Map Mode

<img alt="rock-layer" src="images/rock-layer-map.png" width="100%"/>

### Large TFC Land/Water Exports

Open **Settings → Land/Water Export** after the preview finishes loading. The exporter uses the
currently selected seed, dimension, world-generation settings, datapacks, and installed mods. The
center defaults to `0,0` and accepts any whole block X/Z coordinates whose requested bounds remain
inside the integer coordinate range.

| Action | Block coverage | Blocks per pixel | PNG dimensions |
|---|---:|---:|---:|
| Export 50k Land/Water | 50,000 × 50,000 | 4 | 12,500 × 12,500 |
| Export 100k Land/Water | 100,000 × 100,000 | 8 (2×2 quart aggregation) | 12,500 × 12,500 |
| Export Both | Both presets, sequentially | As above | Two PNGs |

The PNG has exactly two indexed colors: land and water. Water includes final TFC ocean, deep-ocean,
trench, lake, river, river-mouth/channel, and compatible addon water biomes. Salinity alone is not
used, so freshwater is included and salty shores remain land. The 100k preset keeps a pixel as water
when any of its four quart samples is a narrow river/lake/channel; otherwise at least two samples
must be water. This keeps inland water visible without broadly expanding coastlines.

Exports are written to `<Minecraft instance>/world-preview-exports/` as a PNG plus a companion JSON
file. Filenames include the sanitized entered seed, preset, and center. JSON records the entered and
numeric seeds, dimension, exact inclusive bounds, resolution, versions, timestamp, classification
mode, TFC detection, and the detected TFC Large Biomes version when installed. The PNG is standalone.
Default palette values live in `config/world_preview_tfc/config.json` as
`landWaterExportLandColor` and `landWaterExportWaterColor` (RGB integers).

The exporter never creates or loads chunks. It samples the active final TFC biome source directly,
including its river overlay and compatible worldgen mixins. Rows are processed in bounded 256-row
tiles and streamed into a one-bit indexed PNG; exporter-owned image buffers are about 20–25 MiB,
with additional bounded per-thread TFC caches. Up to eight sampling threads are used. Runtime varies
greatly by CPU and worldgen addons: expect the 50k export to take many minutes on typical hardware;
the 100k preset performs four times as many biome lookups and can take roughly four times longer.
The UI remains responsive and shows phase, percentage, elapsed time, and ETA.

Cancel stops workers, closes the writer, and removes `.part` files. A PNG is moved to its final name
only after all rows and metadata are complete, so a cancelled job does not leave a misleading partial
image. For manual validation, export both presets at `0,0`, compare their central continents/oceans,
confirm rivers and lakes remain visible at 100k, repeat with TFC Large Biomes, test center
`-50000,-50000`, and cancel a run midway.

## Other Features
- Persistent seed storage
- Highlighting specific biomes
- Highly configurable and extendable
- Seed Searching
- Volcano Icons

## Compatibility
- Incompatible with the original World Preview Neoforged mod (mod id `world_preview`)
- No other known incompatibilities; reported to work with Beneath

## TODO
- Add compatibility with other worldgen mods like Real World and Eratosthenes
