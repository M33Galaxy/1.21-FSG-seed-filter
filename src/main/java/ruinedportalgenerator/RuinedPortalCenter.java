package ruinedportalgenerator;

import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.util.block.BlockBox;
import com.seedfinding.mccore.util.block.BlockMirror;
import com.seedfinding.mccore.util.block.BlockRotation;
import com.seedfinding.mccore.util.math.Vec3i;
import com.seedfinding.mccore.util.pos.BPos;
import com.seedfinding.mccore.util.pos.CPos;
import com.seedfinding.mccore.version.MCVersion;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Computes ruined-portal center chunk (and related piece fields) without terrain.
 * Matches Minecraft 26.2 {@code RuinedPortalStructure#findGenerationPoint} up through
 * bounding-box center XZ (before {@code findSuitableY}).
 *
 * <p>Biome / noise sampling must go through {@link CubiomesBiomeSampler} (cubiomes),
 * not {@code mc_biome}. Prefer {@link #computeFromCubiomes} with
 * {@link CubiomesBiomeSampler.SampleHeight#HIGH_256} for surface portals.
 * Use {@link CubiomesBiomeSampler#sampleIdsFixedStartChunkBatch} / {@link CubiomesBiomePool} in filters.
 *
 * <p><b>Underground portals may be inaccurate:</b> vanilla biome-checks at projected Y;
 * fixed-Y surface sampling can mis-classify buried portals (and under peaks even Y≈80
 * can still be a cave biome). See {@link Result#undergroundVariantUncertain()}.
 */
public final class RuinedPortalCenter {
	private static final MCVersion VERSION = MCVersion.v1_21;
	private static final float GIANT_CHANCE = 0.05f;

	private RuinedPortalCenter() {
	}

	public enum Variant {
		STANDARD(40010, List.of(
				new Setup("underground", 1.0f, 0.5f),
				new Setup("on_land_surface", 0.5f, 0.5f)
		)),
		DESERT(40011, List.of(new Setup("partly_buried", 0.0f, 1.0f))),
		JUNGLE(40012, List.of(new Setup("on_land_surface", 0.5f, 1.0f))),
		MOUNTAIN(40013, List.of(
				new Setup("in_mountain", 1.0f, 0.5f),
				new Setup("on_land_surface", 0.5f, 0.5f)
		)),
		NETHER(40014, List.of(new Setup("in_nether", 0.5f, 1.0f))),
		OCEAN(40015, List.of(new Setup("on_ocean_floor", 0.0f, 1.0f))),
		SWAMP(40016, List.of(new Setup("on_ocean_floor", 0.0f, 1.0f)));

		public final int lootSalt;
		public final List<Setup> setups;

		Variant(int lootSalt, List<Setup> setups) {
			this.lootSalt = lootSalt;
			this.setups = setups;
		}
	}

	public record Setup(String placement, float airPocketProbability, float weight) {
		boolean isVerticallyBuriedStyle() {
			return "underground".equals(placement) || "in_mountain".equals(placement);
		}
	}

	/**
	 * @param surfaceBiomeName     biome id from cubiomes (high-Y sample), or null if variant given directly
	 * @param setup                chosen setup (placement / air-pocket)
	 * @param caveBiomeHint        true if sample was a cave-layer id (should be rare/absent at Y=256)
	 * @param surfaceApproximation true when variant came from fixed-Y cubiomes sample
	 */
	public record Result(
			Variant variant,
			Setup setup,
			String template,
			boolean airPocket,
			BlockRotation rotation,
			BlockMirror mirror,
			CPos startChunk,
			CPos centerChunk,
			BPos centerBlock,
			CPos chestChunk,
			BPos chestBlock,
			int lootSalt,
			String surfaceBiomeName,
			boolean caveBiomeHint,
			boolean surfaceApproximation
	) {
		/**
		 * True when this may not match a real <em>underground</em> portal.
		 * At Y=256, depth does not pull cave biomes, so {@code caveBiomeHint} is normally false;
		 * uncertainty is then mostly “buried-style setup” (vanilla still biome-checks at projected Y).
		 */
		public boolean undergroundVariantUncertain() {
			return surfaceApproximation && (setup.isVerticallyBuriedStyle() || caveBiomeHint);
		}
	}

	/**
	 * Sample biome via cubiomes at the start-chunk origin + fixed Y, then compute piece fields.
	 * Default height mode for surface loot work: {@link CubiomesBiomeSampler.SampleHeight#HIGH_256}.
	 */
	public static Result computeFromCubiomes(
			CubiomesBiomeSampler sampler,
			long worldSeed,
			int startChunkX,
			int startChunkZ,
			CubiomesBiomeSampler.SampleHeight height
	) throws java.io.IOException {
		CubiomesBiomeSampler.Sample sample = sampler.sampleAtStartChunk(
				worldSeed, CubiomesBiomeSampler.Dimension.OVERWORLD, startChunkX, startChunkZ, height);
		return fromCubiomesSample(worldSeed, startChunkX, startChunkZ, sample);
	}

	public static Result fromCubiomesSample(long worldSeed, int startChunkX, int startChunkZ, CubiomesBiomeSampler.Sample sample) {
		Variant variant = RuinedPortalVariantLookup.fromBiomeIdOrNull(sample.biomeId());
		if (variant == null && sample.biomeName() != null) {
			variant = RuinedPortalVariantLookup.fromBiomeName(sample.biomeName()).orElse(null);
		}
		String label = sample.biomeName() != null ? sample.biomeName() : ("id=" + sample.biomeId());
		if (variant == null) {
			throw new IllegalArgumentException(
					"biome not in any ruined_portal tag (no portal / unsupported): " + label
							+ " @ (" + sample.blockX() + "," + sample.blockY() + "," + sample.blockZ() + ")"
							+ " heightMode=" + sample.heightMode());
		}
		boolean caveHint = sample.looksLikeCaveLayer();
		return compute(worldSeed, startChunkX, startChunkZ, variant, new ChunkRand(), label, caveHint, true);
	}

	/**
	 * Resolve variant from an already-known biome name (e.g. previously sampled via cubiomes).
	 * Prefer {@link #computeFromCubiomes} for new sampling.
	 */
	public static Result computeFromSurfaceBiome(long worldSeed, int startChunkX, int startChunkZ, String surfaceBiomeName) {
		Variant variant = RuinedPortalVariantLookup.fromBiomeName(surfaceBiomeName)
				.orElseThrow(() -> new IllegalArgumentException(
						"biome not in any ruined_portal tag (no portal / unsupported): " + surfaceBiomeName));
		boolean caveHint = RuinedPortalVariantLookup.isCaveLayerBiome(surfaceBiomeName);
		return compute(worldSeed, startChunkX, startChunkZ, variant, new ChunkRand(), surfaceBiomeName, caveHint, true);
	}

	public static Optional<Result> tryComputeFromSurfaceBiome(long worldSeed, int startChunkX, int startChunkZ, String surfaceBiomeName) {
		return RuinedPortalVariantLookup.fromBiomeName(surfaceBiomeName)
				.map(v -> compute(worldSeed, startChunkX, startChunkZ, v, new ChunkRand(),
						surfaceBiomeName, RuinedPortalVariantLookup.isCaveLayerBiome(surfaceBiomeName), true));
	}

	/**
	 * Minimal outputs needed for chest loot: center chunk + decorator salt.
	 * Skips chest-block transform and most Result allocations.
	 */
	public record LootKey(
			int centerChunkX, int centerChunkZ, int lootSalt, Variant variant, String template, Setup setup
	) {
		public boolean isUndergroundStyle() {
			return setup != null && setup.isVerticallyBuriedStyle();
		}
	}

	/**
	 * Hot path for loot filters: carver RNG → template/rotation → BB center chunk + salt.
	 * Does <b>not</b> compute chest block/chunk (loot seed uses center chunk, not chest chunk).
	 */
	public static LootKey computeLootKey(long worldSeed, int startChunkX, int startChunkZ, Variant variant) {
		return computeLootKey(worldSeed, startChunkX, startChunkZ, variant, new ChunkRand());
	}

	public static LootKey computeLootKey(long worldSeed, int startChunkX, int startChunkZ, Variant variant, ChunkRand rand) {
		PieceRoll roll = rollPiece(worldSeed, startChunkX, startChunkZ, variant, rand);
		Vec3i center = roll.piece().getCenter();
		return new LootKey(center.getX() >> 4, center.getZ() >> 4, variant.lootSalt, variant, roll.template(), roll.setup());
	}

	public static Result compute(long worldSeed, int startChunkX, int startChunkZ, Variant variant) {
		return compute(worldSeed, startChunkX, startChunkZ, variant, new ChunkRand(), null, false, false);
	}

	public static Result compute(long worldSeed, int startChunkX, int startChunkZ, Variant variant, ChunkRand rand) {
		return compute(worldSeed, startChunkX, startChunkZ, variant, rand, null, false, false);
	}

	private static Result compute(
			long worldSeed, int startChunkX, int startChunkZ, Variant variant, ChunkRand rand,
			String surfaceBiomeName, boolean caveBiomeHint, boolean surfaceApproximation
	) {
		PieceRoll roll = rollPiece(worldSeed, startChunkX, startChunkZ, variant, rand);
		Vec3i center = roll.piece().getCenter();
		BPos centerBlock = new BPos(center.getX(), center.getY(), center.getZ());
		CPos centerChunk = centerBlock.toChunkPos();
		CPos startChunk = new CPos(startChunkX, startChunkZ);

		BPos chestLocal = RuinedPortalGenerator.STRUCTURE_TO_LOOT.get(roll.template())
				.values().iterator().next();
		BPos chestBlock = chestLocal.transform(roll.mirror(), roll.rotation(), roll.pivot()).add(roll.anchor());
		CPos chestChunk = chestBlock.toChunkPos();

		return new Result(variant, roll.setup(), roll.template(), roll.airPocket(), roll.rotation(), roll.mirror(),
				startChunk, centerChunk, centerBlock, chestChunk, chestBlock, variant.lootSalt,
				surfaceBiomeName, caveBiomeHint, surfaceApproximation);
	}

	/**
	 * Carver RNG through BB (before Y). Leaves {@code rand} ready for {@code findSuitableY}
	 * (e.g. partly_buried {@code nextInt(2,8)}).
	 */
	public record PieceGeom(
			Setup setup, boolean airPocket, String template,
			BlockRotation rotation, BlockMirror mirror,
			BPos anchor, BPos pivot, BlockBox piece
	) {
		public int ySpan() {
			return piece.getYSpan();
		}

		public int centerBlockX() {
			return piece.getCenter().getX();
		}

		public int centerBlockZ() {
			return piece.getCenter().getZ();
		}
	}

	/** Carver RNG through BB — shared by {@link #computeLootKey} and full {@link #compute}. */
	private record PieceRoll(
			Setup setup, boolean airPocket, String template,
			BlockRotation rotation, BlockMirror mirror,
			BPos anchor, BPos pivot, BlockBox piece
	) {
		PieceGeom toGeom() {
			return new PieceGeom(setup, airPocket, template, rotation, mirror, anchor, pivot, piece);
		}
	}

	/**
	 * Public piece roll for repairable / Y pipelines. Same RNG consumption as vanilla through BB.
	 */
	public static PieceGeom rollPieceGeom(long worldSeed, int startChunkX, int startChunkZ, Variant variant, ChunkRand rand) {
		return rollPiece(worldSeed, startChunkX, startChunkZ, variant, rand).toGeom();
	}

	/**
	 * Candidate piece Y before dig-down (26.2 {@code findSuitableY} first stage).
	 * Only implements land placements used by the repairable filter;
	 * underground / mountain / nether need their own RNG branches.
	 */
	public static int candidateYBeforeDig(Setup setup, int surfaceY, int ySpan, ChunkRand rand) {
		String p = setup.placement();
		if ("partly_buried".equals(p)) {
			return surfaceY - ySpan + rand.getInt(2, 8);
		}
		// on_land_surface (and any other non-RNG placement we allow)
		return surfaceY;
	}

	private static PieceRoll rollPiece(long worldSeed, int startChunkX, int startChunkZ, Variant variant, ChunkRand rand) {
		Objects.requireNonNull(variant, "variant");
		rand.setCarverSeed(worldSeed, startChunkX, startChunkZ, VERSION);

		Setup setup = chooseSetup(rand, variant.setups);
		boolean airPocket = sampleAirPocket(rand, setup.airPocketProbability());

		String template;
		if (rand.nextFloat() < GIANT_CHANCE) {
			template = rand.getRandom(RuinedPortalGenerator.STRUCTURE_LOCATION_GIANT_PORTALS);
		} else {
			template = rand.getRandom(RuinedPortalGenerator.STRUCTURE_LOCATION_PORTALS);
		}

		BlockRotation rotation = BlockRotation.getRandom(rand);
		BlockMirror mirror = rand.nextFloat() < 0.5f ? BlockMirror.NONE : BlockMirror.FRONT_BACK;

		BPos size = Objects.requireNonNull(RuinedPortalGenerator.STRUCTURE_SIZE.get(template), template);
		BPos anchor = new BPos(startChunkX << 4, 0, startChunkZ << 4);
		BPos pivot = new BPos(size.getX() / 2, 0, size.getZ() / 2);
		BlockBox piece = BlockBox.getBoundingBox(anchor, rotation, pivot, mirror, size);
		return new PieceRoll(setup, airPocket, template, rotation, mirror, anchor, pivot, piece);
	}

	private static Setup chooseSetup(ChunkRand rand, List<Setup> setups) {
		if (setups.size() == 1) {
			return setups.get(0);
		}
		// Matches RuinedPortalStructure.findGenerationPoint (26.2 JSON: STANDARD/MOUNTAIN both 0.5+0.5).
		float total = 0.0f;
		for (Setup s : setups) {
			total += s.weight();
		}
		float pick = rand.nextFloat();
		for (Setup s : setups) {
			pick -= s.weight() / total;
			if (pick < 0.0f) {
				return s;
			}
		}
		return setups.get(setups.size() - 1);
	}

	/** Matches RuinedPortalStructure.sample */
	private static boolean sampleAirPocket(ChunkRand rand, float probability) {
		if (probability == 0.0f) {
			return false;
		}
		if (probability == 1.0f) {
			return true;
		}
		return rand.nextFloat() < probability;
	}

	public static void main(String[] args) throws Exception {
		long seed = 7602692791733261101L;
		int cx = -72, cz = 53;
		try (CubiomesBiomeSampler sampler = CubiomesBiomeSampler.startDefault()) {
			CubiomesBiomeSampler.SamplePair both = sampler.sampleStartChunkBothHeights(
					seed, CubiomesBiomeSampler.Dimension.OVERWORLD, cx, cz);
			System.out.println("Y72  " + both.y72());
			System.out.println("Y256 " + both.y256());
			System.out.println("disagree=" + both.disagree());

			Result r = computeFromCubiomes(sampler, seed, cx, cz, CubiomesBiomeSampler.SampleHeight.HIGH_256);
			System.out.println(r);
			System.out.println("centerChunk OK: " + (r.centerChunk().getX() == -72 && r.centerChunk().getZ() == 52));
			System.out.println("chest " + r.chestBlock() + " uncertain=" + r.undergroundVariantUncertain());
		}
	}
}
