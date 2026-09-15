# 1.21+ FSG Seed Filter

Minecraft **1.21-26.2** Filtered Seed Glitchless filter with a small Swing GUI (中文 / English).

## Filter Conditions

### Overworld

Litable Ruined portal from chunk (0,0) to chunk (3,3), Village (for food/beds) starting point from chunk (0,0) to chunk (6,6).

Ruined portal chest has at least 27 iron nuggets.

Spawnpoint within 3 chunks from ruined portal.

### Nether

Bastion within 100 blocks from origin, and fortress within 160 blocks from bastion.

Guarenteed at least 16 pearls within 108 piglin barters, and 20 obsidian in all bastion chests + 108 piglin barters + rest obsidian from ow ruined portal after litting the portal.

At least 1 looting 3 sword from ow ruined portal/bastion.

At least 6 rods within 3 blaze kills.

**The conditions sometimes might not match (e.g. sometimes the ruined portal is not litable) due to we didn't simulated the whole world generation.**

## Requirements

- **Java 17+**
- Windows x64 (ships `cubiomes_biome_cli.exe` / `cubiomes_terrain_cli.exe`)

## Build

```bat
gradlew.bat fatJar
```

Output: `build\libs\1.21-FSG-1.0.jar`

## Run

```bat
java -jar build\libs\1.21-FSG-1.0.jar
```

## Credits

- Cubiomes for biomes / terrain / structure placement
- seedfinding libraries
- Xinyuiii BastionGenerator
