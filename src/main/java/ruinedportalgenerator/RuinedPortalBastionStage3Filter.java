package ruinedportalgenerator;

import ChestLoot121.MCLootTables21;
import Xinyuiii.enumType.BastionType;
import Xinyuiii.properties.BastionGenerator;
import Xinyuiii.reecriture.BastionPools.BastionStructureLoot;
import Xinyuiii.reecriture.NewDecoratorRandom;
import Xinyuiii.reecriture.NewLootTables;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.state.Dimension;
import com.seedfinding.mccore.util.data.Pair;
import com.seedfinding.mccore.util.pos.BPos;
import com.seedfinding.mccore.util.pos.CPos;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.loot.LootContext;
import com.seedfinding.mcfeature.loot.LootTable;
import com.seedfinding.mcfeature.loot.item.ItemStack;
import com.seedfinding.mcfeature.structure.BastionRemnant;
import com.seedfinding.mcfeature.structure.RuinedPortal;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Stage-3 filter (IDE 无参运行):
 * <ul>
 *   <li>OW region (0,0) ruined-portal chest <b>or</b> nearby bastion rampart (“高塔”) chests
 *       contain at least one iron/gold sword with looting III</li>
 *   <li>bastion rampart chests + first 72 piglin barters: ≥20 obsidian and ≥60 string</li>
 *   <li>if bastion type is {@link BastionType#STABLES} (player “stable”), require ≥1
 *       {@code hoglin_stable/ramparts/ramparts_1} (“三箱塔”)</li>
 * </ul>
 * Bastion must be within 100 nether blocks of (0,0).
 */
public final class RuinedPortalBastionStage3Filter {
	private static final Path DEFAULT_INPUT = Path.of("C:\\Users\\yang'yi\\Desktop\\1_21_fsg_stage_3.txt");
	private static final Path DEFAULT_OUTPUT = Path.of("C:\\Users\\yang'yi\\Desktop\\output_stage3.txt");
	private static final MCVersion OW_VERSION = MCVersion.v1_21;
	/**
	 * BastionGenerator / seedfinding EnchantRandomly: use 1.20 tables + decorator salt.
	 * {@link MCVersion#v1_21} often yields empty enchant lists → {@code nextInt(0)}.
	 */
	private static final MCVersion BASTION_VERSION = MCVersion.v1_20;
	private static final int NETHER_RADIUS = 100;
	private static final int NETHER_RADIUS_SQ = NETHER_RADIUS * NETHER_RADIUS;
	private static final int BARTER_TRADES = 108;
	private static final int MIN_OBSIDIAN = 20;
	private static final int MIN_STRING = 0;

	private static final LootTable PORTAL_TABLE = MCLootTables21.RUINED_PORTAL_CHEST_1_21;
	/** seedfinding LootTable.apply/processWeights is mutable — not safe across threads. */
	private static final Object LOOT_LOCK = new Object();
	private static final CubiomesBiomeSampler.SampleHeight OW_HEIGHT =
			CubiomesBiomeSampler.SampleHeight.HIGH_256;

	private static final long[] POISON = new long[0];

	// piglin_bartering 1.21.6+ (total weight 469) — same layout as PearlBlazeFilter
	private static final long BARTER_MD5_0 = 0xF79B444CDB83B923L;
	private static final long BARTER_MD5_1 = 0xE09FAD0DCB68166AL;
	private static final int TOTAL_WEIGHT = 469;
	private static final int STRING_ENTRY = 8;
	private static final int OBSIDIAN_ENTRY = 10;
	private static final int[] WEIGHTS = {
			5, 8, 8, 8, 10, 10, 10, 10, 20, 20, 40, 40, 40, 40, 40, 40, 40, 40, 40
	};
	/** 0=none; -1=soul-speed book/boots; else nextInt(bound) for uniform count. */
	private static final int[] EXTRA = {
			-1, -1, 0, 0, 0, 27, 3, 0, 7, 8, 0, 3, 0, 3, 7, 7, 7, 9, 9
	};
	private static final byte[] WEIGHT_TO_ENTRY = new byte[TOTAL_WEIGHT];

	static {
		int pos = 0;
		for (int i = 0; i < WEIGHTS.length; i++) {
			for (int w = 0; w < WEIGHTS[i]; w++) {
				WEIGHT_TO_ENTRY[pos++] = (byte) i;
			}
		}
		if (pos != TOTAL_WEIGHT) {
			throw new IllegalStateException("barter weight sum " + pos);
		}
	}

	public static void main(String[] args) throws Exception {
		Path input = args.length > 0 ? Path.of(args[0]) : DEFAULT_INPUT;
		int threads = args.length > 1 ? Integer.parseInt(args[1]) : 8;
		int batchSize = args.length > 2 ? Integer.parseInt(args[2]) : 256;
		Path output = args.length > 3 ? Path.of(args[3]) : DEFAULT_OUTPUT;

		if (!Files.isRegularFile(input)) {
			throw new IOException("input not found: " + input.toAbsolutePath());
		}
		Path parent = output.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}

		// Warm shared loot tables once (avoids concurrent apply → processWeights AIOOBE).
		synchronized (LOOT_LOCK) {
			PORTAL_TABLE.apply(MCVersion.v1_16_1, LootContext.DEFAULT_LUCK);
			NewLootTables.BASTION_OTHER_CHEST_1_20_0.apply(BASTION_VERSION, LootContext.DEFAULT_LUCK);
			NewLootTables.BASTION_BRIDGE_CHEST_1_20_0.apply(BASTION_VERSION, LootContext.DEFAULT_LUCK);
			NewLootTables.BASTION_HOGLIN_STABLE_CHEST_1_20_0.apply(BASTION_VERSION, LootContext.DEFAULT_LUCK);
			NewLootTables.BASTION_TREASURE_CHEST_1_20_0.apply(BASTION_VERSION, LootContext.DEFAULT_LUCK);
		}

		BlockingQueue<long[]> queue = new ArrayBlockingQueue<>(threads * 2);
		LongAdder hits = new LongAdder();
		LongAdder scanned = new LongAdder();
		AtomicLong readCount = new AtomicLong();
		Object outLock = new Object();
		BufferedWriter outWriter = Files.newBufferedWriter(output, StandardCharsets.UTF_8);

		Thread[] workers = new Thread[threads];
		for (int t = 0; t < threads; t++) {
			workers[t] = new Thread(() -> {
				try (CubiomesBiomeSampler sampler = CubiomesBiomeSampler.startDefault()) {
					WorkerState st = new WorkerState(sampler);
					for (;;) {
						long[] batch = queue.take();
						if (batch == POISON) {
							return;
						}
						List<Long> local = new ArrayList<>();
						for (long seed : batch) {
							scanned.increment();
							if (st.check(seed)) {
								local.add(seed);
								hits.increment();
							}
						}
						if (!local.isEmpty()) {
							synchronized (outLock) {
								for (Long s : local) {
									outWriter.write(Long.toString(s));
									outWriter.newLine();
								}
								outWriter.flush();
							}
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
					System.exit(2);
				}
			}, "stage3-" + t);
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
		outWriter.close();

		long ms = (System.nanoTime() - t0) / 1_000_000L;
		System.err.printf(
				"done read=%d scanned=%d hits=%d threads=%d ms=%d (%.1f seeds/s) out=%s%n",
				readCount.get(), scanned.sum(), hits.sum(), threads, ms,
				scanned.sum() * 1000.0 / Math.max(1, ms), output.toAbsolutePath());
	}

	private static final class WorkerState {
		final CubiomesBiomeSampler sampler;
		final RuinedPortal portal = new RuinedPortal(Dimension.OVERWORLD, OW_VERSION);
		final BastionRemnant bastionRemnant = new BastionRemnant(BASTION_VERSION);
		final BastionGenerator bastionGen = new BastionGenerator(BASTION_VERSION);
		final ChunkRand placementRand = new ChunkRand();
		final ChunkRand pieceRand = new ChunkRand();
		final Xoro xoro = new Xoro();
		final BarterRng barter = new BarterRng();
		final LootContext portalCtx = new LootContext(0L); // no version: matches Region00Filter / MCLootTables21

		WorkerState(CubiomesBiomeSampler sampler) {
			this.sampler = sampler;
		}

		boolean check(long worldSeed) throws IOException {
			// --- ruined portal region (0,0) ---
			CPos portalStart = portal.getInRegion(worldSeed, 0, 0, placementRand);
			int[] biomeIds = sampler.sampleIdsStartChunksBatch(
					new long[]{worldSeed},
					new int[]{portalStart.getX()},
					new int[]{portalStart.getZ()},
					CubiomesBiomeSampler.Dimension.OVERWORLD,
					OW_HEIGHT);
			RuinedPortalCenter.Variant variant = RuinedPortalVariantLookup.fromBiomeIdOrNull(biomeIds[0]);
			if (variant == null) {
				return false;
			}
			RuinedPortalCenter.LootKey key = RuinedPortalCenter.computeLootKey(
					worldSeed, portalStart.getX(), portalStart.getZ(), variant, pieceRand);
			long portalLootSeed = lootSeed(xoro, worldSeed, key.centerChunkX(), key.centerChunkZ(), key.lootSalt());
			portalCtx.setSeed(portalLootSeed);
			List<ItemStack> portalLoot;
			synchronized (LOOT_LOCK) {
				portalLoot = generateStacks(PORTAL_TABLE, portalCtx);
			}
			boolean hasLooting3 = hasLooting3Sword(portalLoot);

			// --- bastion within 100 of nether (0,0) ---
			CPos bastionPos = findBastionNearOrigin(worldSeed);
			if (bastionPos == null) {
				return false;
			}
			if (!bastionGen.generate(worldSeed, bastionPos)) {
				return false;
			}
			if (bastionGen.getType() == BastionType.STABLES) {
				int[] info = bastionGen.getStableInfo();
				if (info == null || info[0] < 1) {
					return false; // need 三箱塔
				}
			}

			int towerObs = 0;
			int towerString = 0;
			List<RampartChest> ramparts;
			synchronized (LOOT_LOCK) {
				ramparts = rampartChestsWithLoot(bastionGen, worldSeed);
			}
			for (RampartChest chest : ramparts) {
				if (hasLooting3Sword(chest.items)) {
					hasLooting3 = true;
				}
				for (ItemStack s : chest.items) {
					String name = s.getItem().getName();
					if ("obsidian".equals(name)) {
						towerObs += s.getCount();
					} else if ("string".equals(name)) {
						towerString += s.getCount();
					}
				}
			}
			if (!hasLooting3) {
				return false;
			}

			int[] bartered = countBarterObsString(worldSeed, barter);
			return towerObs + bartered[0] >= MIN_OBSIDIAN && towerString + bartered[1] >= MIN_STRING;
		}

		CPos findBastionNearOrigin(long worldSeed) {
			CPos best = null;
			int bestDist = Integer.MAX_VALUE;
			for (int rx = -1; rx <= 0; rx++) {
				for (int rz = -1; rz <= 0; rz++) {
					CPos pos = bastionRemnant.getInRegion(worldSeed, rx, rz, placementRand);
					if (pos == null) {
						continue;
					}
					int bx = (pos.getX() << 4) + 8;
					int bz = (pos.getZ() << 4) + 8;
					int d = bx * bx + bz * bz;
					if (d <= NETHER_RADIUS_SQ && d < bestDist) {
						bestDist = d;
						best = pos;
					}
				}
			}
			return best;
		}
	}

	/** All chests generated in vanilla order; only rampart ones returned (decorator phase intact). */
	public static List<RampartChest> rampartChestsWithLoot(BastionGenerator gen, long worldSeed) {
		List<Pair<BPos, LootTable>> chestsPos = new ArrayList<>();
		List<Boolean> rampartFlags = new ArrayList<>();
		for (BastionGenerator.Piece p : gen.getPieces()) {
			List<LootTable> tables = BastionStructureLoot.STRUCTURE_LOOT_1_20_0.get(p.getName());
			if (tables == null || tables.isEmpty()) {
				continue;
			}
			boolean rampart = isRampartPiece(p.getName());
			List<BPos> offsets = BastionStructureLoot.STRUCTURE_LOOT_OFFSETS.get(p.getName());
			List<BPos> pos = new ArrayList<>(offsets.size());
			for (BPos offset : offsets) {
				pos.add(p.pos.add(p.getTransformedPos(offset, p.rotation)));
			}
			for (int i = 0; i < tables.size(); i++) {
				chestsPos.add(new Pair<>(pos.get(i), tables.get(i)));
				rampartFlags.add(rampart);
			}
		}

		List<RampartChest> out = new ArrayList<>();
		List<CPos> chunkPos = new ArrayList<>();
		NewDecoratorRandom rand = new NewDecoratorRandom();
		for (int ci = 0; ci < chestsPos.size(); ci++) {
			Pair<BPos, LootTable> chest = chestsPos.get(ci);
			CPos chunk = chest.getFirst().toChunkPos();
			long populationSeed = rand.getPopulationSeed(worldSeed, chunk.getX() << 4, chunk.getZ() << 4);
			rand.setDecoratorSeed(populationSeed, 0, 4); // 1.19.3+
			if (chunkPos.contains(chunk)) {
				int num = Collections.frequency(chunkPos, chunk);
				for (int i = 0; i < num; i++) {
					rand.nextLong();
				}
			}
			LootContext context = new LootContext(rand.nextLong(), BASTION_VERSION);
			List<ItemStack> items = generateStacks(chest.getSecond(), context);
			chunkPos.add(chunk);
			if (rampartFlags.get(ci)) {
				out.add(new RampartChest(items));
			}
		}
		return out;
	}

	private static boolean isRampartPiece(String name) {
		return name.contains("/ramparts/") || name.contains("/rampart_");
	}

	/** Avoid {@link LootTable#generate(LootContext)} merge-by-Item (can drop distinct enchanted copies). */
	public static List<ItemStack> generateStacks(LootTable table, LootContext context) {
		List<ItemStack> out = new ArrayList<>();
		table.generate(context, stack -> {
			if (stack != null && !stack.isEmpty()) {
				out.add(stack);
			}
		});
		return out;
	}

	public static boolean hasLooting3Sword(List<ItemStack> items) {
		for (ItemStack s : items) {
			String n = s.getItem().getName();
			if (!"golden_sword".equals(n) && !"iron_sword".equals(n)) {
				continue;
			}
			for (Pair<String, Integer> ench : s.getItem().getEnchantments()) {
				if (ench.getFirst() == null || ench.getSecond() == null) {
					continue;
				}
				if (normalizeEnchant(ench.getFirst()).equals("looting") && ench.getSecond() >= 3) {
					return true;
				}
			}
		}
		return false;
	}

	private static String normalizeEnchant(String id) {
		String n = id.toLowerCase(java.util.Locale.ROOT);
		int colon = n.indexOf(':');
		return colon >= 0 ? n.substring(colon + 1) : n;
	}

	/** @return int[2] = {obsidian, string} from first 72 barters */
	static int[] countBarterObsString(long worldSeed, BarterRng rng) {
		rng.setSequence(worldSeed, BARTER_MD5_0, BARTER_MD5_1);
		int obs = 0;
		int str = 0;
		for (int n = 0; n < BARTER_TRADES; n++) {
			int entry = WEIGHT_TO_ENTRY[rng.nextInt(TOTAL_WEIGHT)] & 0xFF;
			int extra = EXTRA[entry];
			if (entry == OBSIDIAN_ENTRY) {
				obs += 1;
			} else if (entry == STRING_ENTRY) {
				str += 3 + rng.nextInt(7); // 3..9
			} else if (extra < 0) {
				rng.nextInt(1);
				rng.nextInt(3);
			} else if (extra > 0) {
				rng.nextInt(extra);
			}
		}
		return new int[]{obs, str};
	}

	public record RampartChest(List<ItemStack> items) {
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

	/** Same Xoroshiro barter sequence as PearlBlazeFilter. */
	static final class BarterRng {
		private static final long SILVER_RATIO_64 = 0x6A09E667F3BCC909L;
		private static final long SUBTRACT_CONSTANT = 0x61C8864680B583EBL;
		private long seedLo, seedHi;

		void setSequence(long worldSeed, long md5Lo, long md5Hi) {
			long unmixedLo = worldSeed ^ SILVER_RATIO_64;
			long unmixedHi = unmixedLo - SUBTRACT_CONSTANT;
			setSeed(Xoro.mixStafford13(unmixedLo ^ md5Lo), Xoro.mixStafford13(unmixedHi ^ md5Hi));
		}

		void setSeed(long lo, long hi) {
			seedLo = lo;
			seedHi = hi;
			if ((seedLo | seedHi) == 0L) {
				seedLo = -7046029254386353131L;
				seedHi = 7640891576956012809L;
			}
		}

		long nextLong() {
			long lo = seedLo;
			long hi = seedHi;
			long result = Long.rotateLeft(lo + hi, 17) + lo;
			hi ^= lo;
			seedLo = Long.rotateLeft(lo, 49) ^ hi ^ (hi << 21);
			seedHi = Long.rotateLeft(hi, 28);
			return result;
		}

		int nextInt() {
			return (int) nextLong();
		}

		int nextInt(int bound) {
			long l = Integer.toUnsignedLong(nextInt());
			long m = l * (long) bound;
			long n = m & 0xFFFFFFFFL;
			if (n < (long) bound) {
				int t = Integer.remainderUnsigned(~bound + 1, bound);
				while (n < (long) t) {
					l = Integer.toUnsignedLong(nextInt());
					m = l * (long) bound;
					n = m & 0xFFFFFFFFL;
				}
			}
			return (int) (m >>> 32);
		}
	}
}
