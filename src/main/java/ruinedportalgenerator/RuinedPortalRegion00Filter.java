package ruinedportalgenerator;

import ChestLoot121.MCLootTables21;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.state.Dimension;
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Filter FSG stage-2 world seeds for region (0,0) overworld ruined portal:
 * <ul>
 *   <li>not ocean variant</li>
 *   <li>not underground / in_mountain setup</li>
 *   <li>chest: ≥27 iron nuggets, flint_and_steel or fire_charge</li>
 *   <li>obsidian ≥ {@link #getMissingObsidian(String)} (same table as {@code project.Repairable})</li>
 * </ul>
 *
 * <p>IDE 直接运行即可（无参默认）：读桌面大列表，写桌面 {@code output.txt}，8 线程。
 * <br>可选参数：{@code [inputPath] [threads] [batchSize] [outputPath]}
 * <br>统计打到 stderr。
 */
public final class RuinedPortalRegion00Filter {
	private static final Path DEFAULT_INPUT = Path.of("C:\\Users\\yang'yi\\Desktop\\1_21_fsg_stage_2.txt");
	private static final Path DEFAULT_OUTPUT = Path.of("C:\\Users\\yang'yi\\Desktop\\output.txt");
	private static final MCVersion VERSION = MCVersion.v1_21;
	private static final int REGION_X = 0;
	private static final int REGION_Z = 0;
	private static final LootTable TABLE = MCLootTables21.RUINED_PORTAL_CHEST_1_21;
	private static final CubiomesBiomeSampler.SampleHeight HEIGHT =
			CubiomesBiomeSampler.SampleHeight.HIGH_256;
	private static final long[] POISON = new long[0];

	public static void main(String[] args) throws Exception {
		Path input = args.length > 0 ? Path.of(args[0]) : DEFAULT_INPUT;
		int threads = args.length > 1 ? Integer.parseInt(args[1]) : 8;
		int batchSize = args.length > 2 ? Integer.parseInt(args[2]) : 4096;
		Path output = args.length > 3 ? Path.of(args[3]) : DEFAULT_OUTPUT;

		if (!Files.isRegularFile(input)) {
			throw new IOException("input not found: " + input.toAbsolutePath());
		}
		if (threads < 1) {
			throw new IllegalArgumentException("threads");
		}

		BlockingQueue<long[]> queue = new ArrayBlockingQueue<>(threads * 2);
		LongAdder hits = new LongAdder();
		LongAdder scanned = new LongAdder();
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
				try (CubiomesBiomeSampler sampler = CubiomesBiomeSampler.startDefault()) {
					RuinedPortal portal = new RuinedPortal(Dimension.OVERWORLD, VERSION);
					ChunkRand placementRand = new ChunkRand();
					ChunkRand pieceRand = new ChunkRand();
					Xoro xoro = new Xoro();
					LootContext ctx = new LootContext(0L);
					for (; ; ) {
						long[] batch = queue.take();
						if (batch == POISON) {
							return;
						}
						processBatch(batch, sampler, portal, placementRand, pieceRand, xoro, ctx,
								hits, scanned, outWriter, outLock);
					}
				} catch (Exception e) {
					e.printStackTrace();
					System.exit(2);
				}
			}, "rp-filter-" + t);
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
				"done read=%d scanned=%d hits=%d threads=%d batch=%d ms=%d (%.1f seeds/s) out=%s%n",
				readCount.get(), scanned.sum(), hits.sum(), threads, batchSize, ms,
				scanned.sum() * 1000.0 / Math.max(1, ms), output.toAbsolutePath());
	}

	private static void processBatch(
			long[] seeds,
			CubiomesBiomeSampler sampler,
			RuinedPortal portal,
			ChunkRand placementRand,
			ChunkRand pieceRand,
			Xoro xoro,
			LootContext ctx,
			LongAdder hits,
			LongAdder scanned,
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

		int[] biomeIds = sampler.sampleIdsStartChunksBatch(
				seeds, cx, cz, CubiomesBiomeSampler.Dimension.OVERWORLD, HEIGHT);

		List<Long> localHits = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			scanned.increment();
			RuinedPortalCenter.Variant variant = RuinedPortalVariantLookup.fromBiomeIdOrNull(biomeIds[i]);
			if (variant == null || variant == RuinedPortalCenter.Variant.OCEAN) {
				continue;
			}
			RuinedPortalCenter.LootKey key =
					RuinedPortalCenter.computeLootKey(seeds[i], cx[i], cz[i], variant, pieceRand);
			if (key.isUndergroundStyle()) {
				continue;
			}

			long lootSeed = lootSeed(xoro, seeds[i], key.centerChunkX(), key.centerChunkZ(), key.lootSalt());
			ctx.setSeed(lootSeed);
			if (!chestOk(TABLE.generate(ctx), key.template())) {
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
		int iron = 0;
		int obsidian = 0;
		boolean light = false;
		for (ItemStack s : items) {
			String name = s.getItem().getName();
			int c = s.getCount();
			switch (name) {
				case "iron_nugget" -> iron += c;
				case "obsidian" -> obsidian += c;
				case "flint_and_steel", "fire_charge" -> light = true;
				default -> {
				}
			}
		}
		if (iron < 27 || !light) {
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
			default -> 5; // portal_5 + giant_portal_*
		};
	}

	private static long[] toArray(List<Long> buf) {
		long[] a = new long[buf.size()];
		for (int i = 0; i < buf.size(); i++) {
			a[i] = buf.get(i);
		}
		return a;
	}

	private static long lootSeed(Xoro x, long worldSeed, int centerCX, int centerCZ, int salt) {
		long pop = x.getPopulationSeed(worldSeed, centerCX << 4, centerCZ << 4);
		x.setDecoratorSeed(pop, salt);
		return x.nextLong();
	}

	/** Xoroshiro population / decorator chain (1.18+). */
	static final class Xoro {
		private long seedLo, seedHi;

		void setSeed(long seed0, long seed1) {
			seedLo = seed0;
			seedHi = seed1;
			if ((seedLo | seedHi) == 0L) {
				seedLo = -7046029254386353131L;
				seedHi = 7640891576956012809L;
			}
		}

		static long mixStafford13(long l) {
			l = (l ^ l >>> 30) * -4658895280553007687L;
			l = (l ^ l >>> 27) * -7723592293110705685L;
			return l ^ l >>> 31;
		}

		void setSeed(long l) {
			long l2 = l ^ 0x6A09E667F3BCC909L;
			setSeed(mixStafford13(l2), mixStafford13(l2 - 7046029254386353131L));
		}

		long xoroNextLong() {
			long l = seedLo;
			long l2 = seedHi;
			long l3 = Long.rotateLeft(l + l2, 17) + l;
			seedLo = Long.rotateLeft(l, 49) ^ (l2 ^= l) ^ l2 << 21;
			seedHi = Long.rotateLeft(l2, 28);
			return l3;
		}

		long nextLong() {
			int a = (int) (xoroNextLong() >> 32);
			int b = (int) (xoroNextLong() >> 32);
			return ((long) a << 32) + (long) b;
		}

		long getPopulationSeed(long worldseed, int ox, int oz) {
			setSeed(worldseed);
			long a = nextLong() | 1L;
			long b = nextLong() | 1L;
			return (long) ox * a + (long) oz * b ^ worldseed;
		}

		void setDecoratorSeed(long populationSeed, int salt) {
			setSeed(populationSeed + salt);
		}
	}
}
