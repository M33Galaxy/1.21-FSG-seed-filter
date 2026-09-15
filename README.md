# 1.21+ FSG Seed Finder

Minecraft **1.21** FSG-style seed searcher with a small Swing GUI (中文 / English).

Pipeline: random 48-bit structure seeds → cheap LCG gate → cubiomes nether/OW checks → expand 65536 world seeds → blaze / barter / portal / village / bastion / terrain / spawn filters.

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

Headless continuous search (hits to stdout):

```bat
java -cp build\libs\1.21-FSG-1.0.jar project.Fsg121Project --cli
java -cp build\libs\1.21-FSG-1.0.jar project.Fsg121Project --cli --out fsgresults.txt 8
```

## What’s included

| Area | Role |
|------|------|
| `project/` | GUI, search engine, structure-seed gate, i18n, native CLI unpack |
| `ChestLoot121/` | 1.21 ruined-portal loot table |
| `ruinedportalgenerator/` | Cubiomes IPC, portal geometry, bastion stage helpers |
| `src/main/resources/native/` | Cubiomes CLI binaries embedded in the jar |

## Gradle dependencies (minimal)

- seedfinding: `mc_core`, `mc_feature`, `mc_biome`, `mc_terrain`, `mc_noise`, `mc_math`, `mc_seed`
- JitPack: [BastionGenerator](https://github.com/Xinyuuu7/BastionGenerator)

Not used (and not included): VillageGenerator, seed-checker, latticg, mc_reversal.

## License / credits

- Cubiomes for biomes / terrain / structure placement
- seedfinding libraries
- Xinyuiii BastionGenerator
