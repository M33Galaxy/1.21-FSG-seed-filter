package ruinedportalgenerator;

import ruinedportalgenerator.RuinedPortalCenter.Variant;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Maps cubiomes {@code BiomeID} / biome names to ruined-portal variants using
 * Minecraft 26.2 {@code has_structure/ruined_portal_*} tags.
 *
 * <p>Prefer {@link #fromBiomeId(int)} on the filter hot path (no string work).
 * IDs match cubiomes {@code biomes.h} / {@code getBiomeAt} return values
 * (1.18+ renamed biomes keep their numeric aliases, e.g. {@code snowy_plains=12}).
 */
public final class RuinedPortalVariantLookup {
	private static final Set<String> CAVE_LAYER_BIOMES = Set.of(
			"dripstone_caves", "lush_caves"
	);
	private static final Set<Integer> CAVE_LAYER_IDS = Set.of(
			174 /* dripstone_caves */, 175 /* lush_caves */
	);

	/** Indexed by cubiomes BiomeID; null = not in any ruined_portal tag. */
	private static final Variant[] BY_ID;
	private static final Map<String, Variant> BY_NAME;

	static {
		Map<String, Variant> m = new HashMap<>();
		Variant[] ids = new Variant[256];

		put(m, ids, Variant.DESERT, 2, "desert");

		put(m, ids, Variant.JUNGLE, 21, "jungle");
		put(m, ids, Variant.JUNGLE, 168, "bamboo_jungle");
		put(m, ids, Variant.JUNGLE, 23, "sparse_jungle"); // jungle_edge

		put(m, ids, Variant.SWAMP, 6, "swamp");
		put(m, ids, Variant.SWAMP, 184, "mangrove_swamp");

		put(m, ids, Variant.OCEAN, 0, "ocean");
		put(m, ids, Variant.OCEAN, 10, "frozen_ocean");
		put(m, ids, Variant.OCEAN, 46, "cold_ocean");
		put(m, ids, Variant.OCEAN, 45, "lukewarm_ocean");
		put(m, ids, Variant.OCEAN, 44, "warm_ocean");
		put(m, ids, Variant.OCEAN, 24, "deep_ocean");
		put(m, ids, Variant.OCEAN, 50, "deep_frozen_ocean");
		put(m, ids, Variant.OCEAN, 49, "deep_cold_ocean");
		put(m, ids, Variant.OCEAN, 48, "deep_lukewarm_ocean");
		put(m, ids, Variant.OCEAN, 47, "deep_warm_ocean");

		put(m, ids, Variant.NETHER, 8, "nether_wastes");
		put(m, ids, Variant.NETHER, 170, "soul_sand_valley");
		put(m, ids, Variant.NETHER, 171, "crimson_forest");
		put(m, ids, Variant.NETHER, 172, "warped_forest");
		put(m, ids, Variant.NETHER, 173, "basalt_deltas");

		put(m, ids, Variant.MOUNTAIN, 37, "badlands");
		put(m, ids, Variant.MOUNTAIN, 165, "eroded_badlands");
		put(m, ids, Variant.MOUNTAIN, 38, "wooded_badlands"); // wooded_badlands_plateau
		put(m, ids, Variant.MOUNTAIN, 3, "windswept_hills"); // mountains
		put(m, ids, Variant.MOUNTAIN, 34, "windswept_forest"); // wooded_mountains
		put(m, ids, Variant.MOUNTAIN, 131, "windswept_gravelly_hills"); // gravelly_mountains
		put(m, ids, Variant.MOUNTAIN, 36, "savanna_plateau");
		put(m, ids, Variant.MOUNTAIN, 163, "windswept_savanna"); // shattered_savanna
		put(m, ids, Variant.MOUNTAIN, 25, "stony_shore"); // stone_shore
		put(m, ids, Variant.MOUNTAIN, 177, "meadow");
		put(m, ids, Variant.MOUNTAIN, 181, "frozen_peaks");
		put(m, ids, Variant.MOUNTAIN, 180, "jagged_peaks");
		put(m, ids, Variant.MOUNTAIN, 182, "stony_peaks");
		put(m, ids, Variant.MOUNTAIN, 179, "snowy_slopes");
		put(m, ids, Variant.MOUNTAIN, 185, "cherry_grove");

		put(m, ids, Variant.STANDARD, 16, "beach");
		put(m, ids, Variant.STANDARD, 26, "snowy_beach");
		put(m, ids, Variant.STANDARD, 7, "river");
		put(m, ids, Variant.STANDARD, 11, "frozen_river");
		put(m, ids, Variant.STANDARD, 5, "taiga");
		put(m, ids, Variant.STANDARD, 30, "snowy_taiga");
		put(m, ids, Variant.STANDARD, 32, "old_growth_pine_taiga"); // giant_tree_taiga
		put(m, ids, Variant.STANDARD, 160, "old_growth_spruce_taiga"); // giant_spruce_taiga
		put(m, ids, Variant.STANDARD, 4, "forest");
		put(m, ids, Variant.STANDARD, 132, "flower_forest");
		put(m, ids, Variant.STANDARD, 27, "birch_forest");
		put(m, ids, Variant.STANDARD, 155, "old_growth_birch_forest"); // tall_birch_forest
		put(m, ids, Variant.STANDARD, 29, "dark_forest");
		put(m, ids, Variant.STANDARD, 186, "pale_garden");
		put(m, ids, Variant.STANDARD, 178, "grove");
		put(m, ids, Variant.STANDARD, 14, "mushroom_fields");
		put(m, ids, Variant.STANDARD, 140, "ice_spikes");
		put(m, ids, Variant.STANDARD, 174, "dripstone_caves");
		put(m, ids, Variant.STANDARD, 175, "lush_caves");
		put(m, ids, Variant.STANDARD, 35, "savanna");
		put(m, ids, Variant.STANDARD, 12, "snowy_plains"); // snowy_tundra
		put(m, ids, Variant.STANDARD, 1, "plains");
		put(m, ids, Variant.STANDARD, 129, "sunflower_plains");

		BY_NAME = Collections.unmodifiableMap(m);
		BY_ID = ids;
	}

	private RuinedPortalVariantLookup() {
	}

	private static void put(Map<String, Variant> m, Variant[] ids, Variant v, int id, String name) {
		Variant prevName = m.put(normalize(name), v);
		if (prevName != null && prevName != v) {
			throw new IllegalStateException("biome tag overlap: " + name + " " + prevName + " vs " + v);
		}
		if (id < 0 || id >= ids.length) {
			throw new IllegalStateException("biome id out of range: " + id + " " + name);
		}
		Variant prevId = ids[id];
		if (prevId != null && prevId != v) {
			throw new IllegalStateException("biome id overlap: " + id + " " + prevId + " vs " + v);
		}
		ids[id] = v;
	}

	public static String normalize(String biomeName) {
		String n = biomeName.trim().toLowerCase(Locale.ROOT);
		int slash = n.indexOf(':');
		if (slash >= 0) {
			n = n.substring(slash + 1);
		}
		return n;
	}

	/** Hot path: cubiomes numeric id → variant (null if not in any ruined_portal tag). */
	public static Variant fromBiomeIdOrNull(int biomeId) {
		if (biomeId < 0 || biomeId >= BY_ID.length) {
			return null;
		}
		return BY_ID[biomeId];
	}

	public static Optional<Variant> fromBiomeId(int biomeId) {
		return Optional.ofNullable(fromBiomeIdOrNull(biomeId));
	}

	/** @return empty if not in any ruined-portal tag (e.g. {@code deep_dark}) */
	public static Optional<Variant> fromBiomeName(String biomeName) {
		if (biomeName == null || biomeName.isEmpty()) {
			return Optional.empty();
		}
		return Optional.ofNullable(BY_NAME.get(normalize(biomeName)));
	}

	public static boolean isCaveLayerBiome(String biomeName) {
		return biomeName != null && CAVE_LAYER_BIOMES.contains(normalize(biomeName));
	}

	public static boolean isCaveLayerId(int biomeId) {
		return CAVE_LAYER_IDS.contains(biomeId);
	}

	public static Map<String, Variant> table() {
		return BY_NAME;
	}
}
