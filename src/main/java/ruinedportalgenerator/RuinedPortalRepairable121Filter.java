package ruinedportalgenerator;

import ChestLoot121.MCLootTables21;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.state.Dimension;
import com.seedfinding.mccore.util.block.BlockBox;
import com.seedfinding.mccore.util.pos.BPos;
import com.seedfinding.mccore.util.pos.CPos;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.loot.LootContext;
import com.seedfinding.mcfeature.loot.LootTable;
import com.seedfinding.mcfeature.loot.item.ItemStack;
import com.seedfinding.mcfeature.structure.RuinedPortal;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 1.21 可补废门筛（对齐 {@code project.Repairable} 条件，几何/loot 用 26.2 链路）：
 * <ol>
 *   <li>region (0,0) 废门</li>
 *   <li>跳过 OCEAN / SWAMP（ocean_floor 高度图）与 underground / in_mountain</li>
 *   <li>箱子：打火石或火焰弹；黑曜石 ≥ {@link #getMissingObsidian(String)}</li>
 *   <li><b>最后</b> cubiomes terrain：surfaceY → dig → 最小门框无哭泣黑曜石</li>
 * </ol>
 *
 * <p>IDE 直接跑（无参默认）：读桌面列表，写 {@code output_repairable.txt}，8 线程。
 * <br>参数：{@code [inputPath] [threads] [batchSize] [outputPath]}
 */
public final class RuinedPortalRepairable121Filter {
	private static final Path DEFAULT_INPUT = Path.of("C:\\Users\\yang'yi\\Desktop\\test.txt");
	private static final Path DEFAULT_OUTPUT = Path.of("C:\\Users\\yang'yi\\Desktop\\output_repairable.txt");
	private static final MCVersion VERSION = MCVersion.v1_21;
	private static final int REGION_X = 0;
	private static final int REGION_Z = 0;
	private static final LootTable TABLE = MCLootTables21.RUINED_PORTAL_CHEST_1_21;
	/** seedfinding LootTable.apply/processWeights is mutable — not safe across threads. */
	private static final Object LOOT_LOCK = new Object();
	private static final CubiomesBiomeSampler.SampleHeight HEIGHT =
			CubiomesBiomeSampler.SampleHeight.HIGH_256;
	private static final long[] POISON = new long[0];
	private static final float CRYING_CHANCE = 0.15f;

	public static void main(String[] args) throws Exception {
		Path input = args.length > 0 ? Path.of(args[0]) : DEFAULT_INPUT;
		int threads = args.length > 1 ? Integer.parseInt(args[1]) : 8;
		int batchSize = args.length > 2 ? Integer.parseInt(args[2]) : 1024;
		Path output = args.length > 3 ? Path.of(args[3]) : DEFAULT_OUTPUT;

		if (!Files.isRegularFile(input)) {
			throw new IOException("input not found: " + input.toAbsolutePath());
		}
		if (threads < 1) {
			throw new IllegalArgumentException("threads");
		}

		// Warm once so concurrent generate() does not race on processWeights.
		synchronized (LOOT_LOCK) {
			TABLE.apply(VERSION, LootContext.DEFAULT_LUCK);
		}

		BlockingQueue<long[]> queue = new ArrayBlockingQueue<>(threads * 2);
		LongAdder hits = new LongAdder();
		LongAdder scanned = new LongAdder();
		LongAdder lootPass = new LongAdder();
		LongAdder terrainFail = new LongAdder();
		AtomicLong readCount = new AtomicLong();
		Object outLock = new Object();

		Path outParent = output.getParent();
		if (outParent != null) {
			Files.createDirectories(outParent);
		}
		BufferedWriter outWriter = Files.newBufferedWriter(output, StandardCharsets.UTF_8);

		Thread[] workers = new Thread[threads];
		for (int t = 0; t < threads; t++) {
			workers[t] = new Thread(() -> {
				try (CubiomesBiomeSampler biomes = CubiomesBiomeSampler.startDefault();
					 CubiomesTerrainSampler terrain = CubiomesTerrainSampler.startDefault()) {
					RuinedPortal portal = new RuinedPortal(Dimension.OVERWORLD, VERSION);
					ChunkRand placementRand = new ChunkRand();
					ChunkRand pieceRand = new ChunkRand();
					ChunkRand cryingRand = new ChunkRand();
					RuinedPortalRegion00Filter.Xoro xoro = new RuinedPortalRegion00Filter.Xoro();
					LootContext ctx = new LootContext(0L);
					for (; ; ) {
						long[] batch = queue.take();
						if (batch == POISON) {
							return;
						}
						processBatch(batch, biomes, terrain, portal, placementRand, pieceRand, cryingRand,
								xoro, ctx, hits, scanned, lootPass, terrainFail, outWriter, outLock);
					}
				} catch (Exception e) {
					e.printStackTrace();
					System.exit(2);
				}
			}, "rp-repair-" + t);
			workers[t].start();
		}

		long t0 = System.nanoTime();
		try (BufferedReader br = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
			List<Long> buf = new ArrayList<>(batchSize);
			String line;
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}
				buf.add(Long.parseLong(line));
				if (buf.size() >= batchSize) {
					queue.put(toArray(buf));
					readCount.addAndGet(buf.size());
					buf.clear();
				}
			}
			if (!buf.isEmpty()) {
				queue.put(toArray(buf));
				readCount.addAndGet(buf.size());
			}
		}

		for (int i = 0; i < threads; i++) {
			queue.put(POISON);
		}
		for (Thread w : workers) {
			w.join();
		}
		outWriter.flush();
		outWriter.close();

		long ms = (System.nanoTime() - t0) / 1_000_000L;
		System.err.printf(
				"done read=%d scanned=%d lootPass=%d terrainFail=%d hits=%d threads=%d batch=%d ms=%d (%.1f seeds/s) out=%s%n",
				readCount.get(), scanned.sum(), lootPass.sum(), terrainFail.sum(), hits.sum(),
				threads, batchSize, ms, scanned.sum() * 1000.0 / Math.max(1, ms), output.toAbsolutePath());
	}

	private static void processBatch(
			long[] seeds,
			CubiomesBiomeSampler biomes,
			CubiomesTerrainSampler terrain,
			RuinedPortal portal,
			ChunkRand placementRand,
			ChunkRand pieceRand,
			ChunkRand cryingRand,
			RuinedPortalRegion00Filter.Xoro xoro,
			LootContext ctx,
			LongAdder hits,
			LongAdder scanned,
			LongAdder lootPass,
			LongAdder terrainFail,
			BufferedWriter out,
			Object outLock
	) throws IOException {
		int n = seeds.length;
		int[] cx = new int[n];
		int[] cz = new int[n];
		for (int i = 0; i < n; i++) {
			CPos start = portal.getInRegion(seeds[i], REGION_X, REGION_Z, placementRand);
			cx[i] = start.getX();
			cz[i] = start.getZ();
		}

		int[] biomeIds = biomes.sampleIdsStartChunksBatch(
				seeds, cx, cz, CubiomesBiomeSampler.Dimension.OVERWORLD, HEIGHT);

		List<Long> localHits = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			scanned.increment();
			RuinedPortalCenter.Variant variant = RuinedPortalVariantLookup.fromBiomeIdOrNull(biomeIds[i]);
			if (variant == null
					|| variant == RuinedPortalCenter.Variant.OCEAN
					|| variant == RuinedPortalCenter.Variant.SWAMP
					|| variant == RuinedPortalCenter.Variant.NETHER) {
				continue;
			}

			RuinedPortalCenter.PieceGeom geom =
					RuinedPortalCenter.rollPieceGeom(seeds[i], cx[i], cz[i], variant, pieceRand);
			RuinedPortalCenter.Setup setup = geom.setup();
			if (setup.isVerticallyBuriedStyle() || "on_ocean_floor".equals(setup.placement())) {
				continue;
			}
			String place = setup.placement();
			if (!"on_land_surface".equals(place) && !"partly_buried".equals(place)) {
				continue;
			}

			int centerCX = geom.centerBlockX() >> 4;
			int centerCZ = geom.centerBlockZ() >> 4;
			long lootSeed = lootSeed(xoro, seeds[i], centerCX, centerCZ, variant.lootSalt);
			ctx.setSeed(lootSeed);
			List<ItemStack> loot;
			synchronized (LOOT_LOCK) {
				loot = TABLE.generate(ctx);
			}
			if (!chestOk(loot, geom.template())) {
				continue;
			}
			lootPass.increment();

			// --- terrain last ---
			BlockBox bb = geom.piece();
			int surfaceY = terrain.topSolidY(seeds[i], geom.centerBlockX(), geom.centerBlockZ());
			int candY = RuinedPortalCenter.candidateYBeforeDig(setup, surfaceY, geom.ySpan(), pieceRand);
			int projectedY = terrain.digPortalY(
					seeds[i], candY, CubiomesTerrainSampler.DIG_MIN_Y,
					bb.minX, bb.minZ, bb.maxX, bb.minZ, bb.minX, bb.maxZ, bb.maxX, bb.maxZ);

			if (minimalPortalHasCrying(geom, projectedY, cryingRand)) {
				terrainFail.increment();
				continue;
			}

			localHits.add(seeds[i]);
			hits.increment();
		}

		if (!localHits.isEmpty()) {
			synchronized (outLock) {
				for (Long s : localHits) {
					out.write(Long.toString(s));
					out.newLine();
				}
				out.flush();
			}
		}
	}

	private static boolean chestOk(List<ItemStack> items, String template) {
		int obsidian = 0;
		boolean light = false;
		for (ItemStack s : items) {
			String name = s.getItem().getName();
			int c = s.getCount();
			switch (name) {
				case "obsidian" -> obsidian += c;
				case "flint_and_steel", "fire_charge" -> light = true;
				default -> {
				}
			}
		}
		if (obsidian == 0 || !light) {
			return false;
		}
		return obsidian >= getMissingObsidian(template);
	}

	/** Same thresholds as {@code project.Repairable#getMissingObsidian}. */
	public static int getMissingObsidian(String portalType) {
		return switch (portalType) {
			case "portal_9", "portal_1" -> 2;
			case "portal_2", "portal_3" -> 4;
			case "portal_4", "portal_8" -> 3;
			case "portal_6", "portal_7" -> 1;
			case "portal_10" -> 7;
			default -> 5;
		};
	}

	/**
	 * BlockAge-style crying: {@code setPositionSeed(worldXYZ)} then {@code nextFloat() < 0.15}.
	 * Minimal frame only (Repairable {@code getMinimalPortal()}).
	 */
	public static boolean minimalPortalHasCrying(RuinedPortalCenter.PieceGeom geom, int projectedY, ChunkRand rand) {
		Map<com.seedfinding.mccore.block.Block, List<BPos>> blocks =
				RuinedPortalGenerator.STRUCTURE_TO_BLOCKS.get(geom.template());
		if (blocks == null) {
			return true;
		}
		List<BPos> locals = blocks.get(RuinedPortalGenerator.MINIMAL_OBSIDIAN_FRAME);
		if (locals == null || locals.isEmpty()) {
			return true;
		}
		BPos origin = new BPos(geom.anchor().getX(), projectedY, geom.anchor().getZ());
		for (BPos local : locals) {
			BPos world = local.transform(geom.mirror(), geom.rotation(), geom.pivot()).add(origin);
			rand.setPositionSeed(world, VERSION);
			if (rand.nextFloat() < CRYING_CHANCE) {
				return true;
			}
		}
		return false;
	}

	private static long[] toArray(List<Long> buf) {
		long[] a = new long[buf.size()];
		for (int i = 0; i < buf.size(); i++) {
			a[i] = buf.get(i);
		}
		return a;
	}

	private static long lootSeed(RuinedPortalRegion00Filter.Xoro x, long worldSeed, int centerCX, int centerCZ, int salt) {
		long pop = x.getPopulationSeed(worldSeed, centerCX << 4, centerCZ << 4);
		x.setDecoratorSeed(pop, salt);
		return x.nextLong();
	}
}
