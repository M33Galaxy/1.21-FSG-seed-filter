package project;

import ChestLoot121.MCLootTables21;
import Xinyuiii.enumType.BastionType;
import Xinyuiii.properties.BastionGenerator;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.util.block.BlockBox;
import com.seedfinding.mccore.util.pos.CPos;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.loot.LootContext;
import com.seedfinding.mcfeature.loot.LootTable;
import com.seedfinding.mcfeature.loot.item.ItemStack;
import ruinedportalgenerator.CubiomesBiomeSampler;
import ruinedportalgenerator.CubiomesTerrainSampler;
import ruinedportalgenerator.RuinedPortalBastionStage3Filter;
import ruinedportalgenerator.RuinedPortalCenter;
import ruinedportalgenerator.RuinedPortalRepairable121Filter;
import ruinedportalgenerator.RuinedPortalVariantLookup;
import Xinyuiii.reecriture.NewLootTables;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 1.21 FSG 整合筛：随机 48 位结构种子预筛 → 扩高 16 位 → 按快→慢漏斗筛选 worldSeed。
 *
 * <p>CLI：无参启动 GUI；{@code --cli [threads]} 连续筛，命中打印到控制台；
 * {@code --cli --out fsgresults.txt [threads]} 同时写文件。
 */
public final class Fsg121Project {
	/** 可选文件名提示；默认不写文件（{@link Options#output} 为 null）。 */
	public static final Path DEFAULT_OUTPUT_NAME = Path.of("fsgresults.txt");

	public enum StopMode {
		/** 找到 1 个即停 */
		ONE,
		/** 找到 N 个即停（N∈[1,100]） */
		COUNT,
		/** 连续筛到手动停止 */
		CONTINUOUS
	}

	@FunctionalInterface
	public interface HitListener {
		void onHit(long worldSeed);
	}

	@FunctionalInterface
	public interface StatusListener {
		void onStatus(SearchStatus status);
	}

	public record SearchStatus(
			long structTried,
			long structPass,
			long scanned,
			long hits,
			long passBlaze,
			long passBarter,
			long passLootPre,
			long passVillage,
			long passPortal,
			long passBastion,
			long passTerrain,
			long passSpawn,
			double elapsedSec
	) {
	}

	public static final class Options {
		/** null = 不写文件，只靠 {@link #hitListener} / 控制台 */
		public Path output = null;
		public int threads = Math.max(1, Runtime.getRuntime().availableProcessors());
		public StopMode stopMode = StopMode.CONTINUOUS;
		/** COUNT / ONE 时的目标命中数；ONE 时强制为 1 */
		public int targetHits = 1;
		public HitListener hitListener = s -> {
		};
		public StatusListener statusListener = s -> {
		};
		public Runnable finishedListener = () -> {
		};
	}

	public static final class Session {
		final AtomicBoolean running = new AtomicBoolean(true);
		private volatile Thread boss;

		public void stop() {
			running.set(false);
		}

		public boolean isRunning() {
			return running.get();
		}

		void bind(Thread boss) {
			this.boss = boss;
		}

		public void await() throws InterruptedException {
			Thread t = boss;
			if (t != null) {
				t.join();
			}
		}
	}

	private static final long MASK_48 = 0xFFFFFFFFFFFFL;
	private static final MCVersion OW_VERSION = MCVersion.v1_21;
	private static final MCVersion BASTION_VERSION = MCVersion.v1_20;
	private static final int MIN_IRON = 27;
	private static final int MIN_OBSIDIAN = 20;
	private static final int MIN_PEARLS = 16;
	private static final int BARTER_TRADES = 108;
	/** Spawn must be within this Chebyshev? No — axis-aligned: |dx|<=48 and |dz|<=48 of portal start origin. */
	private static final int SPAWN_PORTAL_ORIGIN_RANGE = 48;

	/**
	 * Overworld surface-ish variants to speculative-loot (exclude ocean/swamp/nether).
	 * STANDARD/MOUNTAIN may still roll buried setups — those attempts are discarded.
	 */
	private static final RuinedPortalCenter.Variant[] LOOT_VARIANTS = {
			RuinedPortalCenter.Variant.STANDARD,
			RuinedPortalCenter.Variant.DESERT,
			RuinedPortalCenter.Variant.JUNGLE,
			RuinedPortalCenter.Variant.MOUNTAIN
	};

	private static final LootTable PORTAL_TABLE = MCLootTables21.RUINED_PORTAL_CHEST_1_21;
	private static final Object LOOT_LOCK = new Object();
	private static final CubiomesBiomeSampler.SampleHeight OW_HEIGHT =
			CubiomesBiomeSampler.SampleHeight.HIGH_256;

	private static final long BLAZE_MD5_0 = 0xA9EC152F9C889472L;
	private static final long BLAZE_MD5_1 = 0xCB9B0580C2B91A9EL;
	private static final long BARTER_MD5_0 = 0xF79B444CDB83B923L;
	private static final long BARTER_MD5_1 = 0xE09FAD0DCB68166AL;
	private static final long SILVER_RATIO_64 = 0x6A09E667F3BCC909L;
	private static final long SUBTRACT_CONSTANT = 0x61C8864680B583EBL;

	private static final int TOTAL_WEIGHT = 469;
	private static final int PEARL_ENTRY = 6;
	private static final int STRING_ENTRY = 8;
	private static final int OBSIDIAN_ENTRY = 10;
	private static final int[] WEIGHTS = {
			5, 8, 8, 8, 10, 10, 10, 10, 20, 20, 40, 40, 40, 40, 40, 40, 40, 40, 40
	};
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
		if (args.length > 0 && "--cli".equals(args[0])) {
			Options opt = new Options();
			opt.stopMode = StopMode.CONTINUOUS;
			opt.output = null;
			opt.hitListener = seed -> System.out.println(seed);
			int argi = 1;
			if (argi < args.length && "--out".equals(args[argi])) {
				argi++;
				if (argi >= args.length) {
					throw new IllegalArgumentException("--out requires a path");
				}
				opt.output = Path.of(args[argi++]);
			}
			if (argi < args.length) {
				opt.threads = Integer.parseInt(args[argi]);
			}
			opt.statusListener = s -> {
				if (s.structPass() % 20 == 0 && s.structPass() > 0) {
					System.err.printf(
							"progress structPass=%d tried=%d hits=%d scanned=%d (%.0f ws/s)%n",
							s.structPass(), s.structTried(), s.hits(), s.scanned(),
							s.scanned() / Math.max(s.elapsedSec(), 1e-9));
				}
			};
			Session session = start(opt);
			Runtime.getRuntime().addShutdownHook(new Thread(session::stop));
			session.await();
			return;
		}
		Fsg121Gui.launch();
	}

	public static Session start(Options opt) {
		Objects.requireNonNull(opt, "opt");
		int target = switch (opt.stopMode) {
			case ONE -> 1;
			case COUNT -> Math.max(1, Math.min(100, opt.targetHits));
			case CONTINUOUS -> Integer.MAX_VALUE;
		};
		Session session = new Session();
		Thread boss = new Thread(() -> runSearch(opt, session, target), "fsg121-boss");
		session.bind(boss);
		boss.setDaemon(false);
		boss.start();
		return session;
	}

	private static void runSearch(Options opt, Session session, int targetHits) {
		Path output = opt.output;
		int threads = Math.max(1, opt.threads);
		long t0 = System.nanoTime();
		LongAdder hits = new LongAdder();
		LongAdder scanned = new LongAdder();
		LongAdder structTried = new LongAdder();
		LongAdder structPass = new LongAdder();
		LongAdder passBlaze = new LongAdder();
		LongAdder passBarter = new LongAdder();
		LongAdder passLootPre = new LongAdder();
		LongAdder passVillage = new LongAdder();
		LongAdder passPortal = new LongAdder();
		LongAdder passBastion = new LongAdder();
		LongAdder passTerrain = new LongAdder();
		LongAdder passSpawn = new LongAdder();
		AtomicLong expandedDone = new AtomicLong();
		AtomicBoolean hitCap = new AtomicBoolean(false);
		try {
			if (output != null) {
				Path parent = output.getParent();
				if (parent != null) {
					Files.createDirectories(parent);
				}
			}

			synchronized (LOOT_LOCK) {
				PORTAL_TABLE.apply(OW_VERSION, LootContext.DEFAULT_LUCK);
				NewLootTables.BASTION_OTHER_CHEST_1_20_0.apply(BASTION_VERSION, LootContext.DEFAULT_LUCK);
				NewLootTables.BASTION_BRIDGE_CHEST_1_20_0.apply(BASTION_VERSION, LootContext.DEFAULT_LUCK);
				NewLootTables.BASTION_HOGLIN_STABLE_CHEST_1_20_0.apply(BASTION_VERSION, LootContext.DEFAULT_LUCK);
				NewLootTables.BASTION_TREASURE_CHEST_1_20_0.apply(BASTION_VERSION, LootContext.DEFAULT_LUCK);
			}

			final BufferedWriter writer;
			if (output != null) {
				writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8);
			} else {
				writer = null;
			}
			try {
				ExecutorService executor = Executors.newFixedThreadPool(threads);
				for (int i = 0; i < threads; i++) {
					executor.submit(() -> {
						try (CubiomesBiomeSampler biomes = CubiomesBiomeSampler.startDefault();
							 CubiomesTerrainSampler terrain = CubiomesTerrainSampler.startDefault()) {
							Worker w = new Worker(biomes, terrain, writer,
									hits, scanned, passBlaze, passBarter, passLootPre, passVillage,
									passPortal, passBastion, passTerrain, passSpawn,
									opt.hitListener, targetHits, hitCap, session.running);
							ThreadLocalRandom rnd = ThreadLocalRandom.current();
							long localTried = 0L;
							long lastStatusLocal = 0L;
							while (session.running.get() && !hitCap.get()) {
								long base = rnd.nextLong() & MASK_48;
								structTried.increment();
								localTried++;
								if (!Fsg121StructureSeed.cheapGate(base)) {
									if (localTried - lastStatusLocal >= 2_000_000L) {
										lastStatusLocal = localTried;
										double sec = (System.nanoTime() - t0) / 1e9;
										opt.statusListener.onStatus(new SearchStatus(
												structTried.sum(), structPass.sum(), scanned.sum(), hits.sum(),
												passBlaze.sum(), passBarter.sum(), passLootPre.sum(), passVillage.sum(),
												passPortal.sum(), passBastion.sum(), passTerrain.sum(), passSpawn.sum(),
												sec));
									}
									continue;
								}
								Fsg121StructureSeed.Hit hit = Fsg121StructureSeed.Hit.from(biomes.structFsg(base));
								if (hit == null) {
									continue;
								}
								structPass.increment();
								w.processStructureSeed(hit);
								expandedDone.incrementAndGet();
								double sec = (System.nanoTime() - t0) / 1e9;
								opt.statusListener.onStatus(new SearchStatus(
										structTried.sum(), structPass.sum(), scanned.sum(), hits.sum(),
										passBlaze.sum(), passBarter.sum(), passLootPre.sum(), passVillage.sum(),
										passPortal.sum(), passBastion.sum(), passTerrain.sum(), passSpawn.sum(),
										sec));
								lastStatusLocal = localTried;
							}
						} catch (Exception e) {
							e.printStackTrace();
							session.running.set(false);
						}
					});
				}
				executor.shutdown();
				while (!executor.awaitTermination(500, TimeUnit.MILLISECONDS)) {
					if (!session.running.get() || hitCap.get()) {
						executor.shutdownNow();
						executor.awaitTermination(60, TimeUnit.SECONDS);
						break;
					}
				}
				if (writer != null) {
					writer.flush();
				}
			} finally {
				if (writer != null) {
					try {
						writer.close();
					} catch (IOException ignored) {
					}
				}
			}

			double sec = (System.nanoTime() - t0) / 1e9;
			opt.statusListener.onStatus(new SearchStatus(
					structTried.sum(), structPass.sum(), scanned.sum(), hits.sum(),
					passBlaze.sum(), passBarter.sum(), passLootPre.sum(), passVillage.sum(),
					passPortal.sum(), passBastion.sum(), passTerrain.sum(), passSpawn.sum(),
					sec));
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			session.running.set(false);
			try {
				opt.finishedListener.run();
			} catch (Exception ignored) {
			}
		}
	}

	private static final class Worker {
		final CubiomesBiomeSampler biomes;
		final CubiomesTerrainSampler terrain;
		final BufferedWriter writer;
		final LongAdder hits, scanned, passBlaze, passBarter, passLootPre, passVillage, passPortal, passBastion, passTerrain, passSpawn;
		final HitListener hitListener;
		final int targetHits;
		final AtomicBoolean hitCap;
		final AtomicBoolean running;

		final BastionGenerator bastionGen = new BastionGenerator(BASTION_VERSION);
		final ChunkRand pieceRand = new ChunkRand();
		final ChunkRand cryingRand = new ChunkRand();
		final XoroRng rng = new XoroRng();
		final DecorXoro xoro = new DecorXoro();
		final LootContext portalCtx = new LootContext(0L);
		final StringBuilder lineBuf = new StringBuilder(32);
		/** Reused speculative loot slots for LOOT_VARIANTS. */
		final PortalLootPass[] lootSlots = new PortalLootPass[LOOT_VARIANTS.length];

		Worker(CubiomesBiomeSampler biomes, CubiomesTerrainSampler terrain, BufferedWriter writer,
				LongAdder hits, LongAdder scanned, LongAdder passBlaze, LongAdder passBarter, LongAdder passLootPre,
				LongAdder passVillage, LongAdder passPortal, LongAdder passBastion, LongAdder passTerrain,
				LongAdder passSpawn, HitListener hitListener, int targetHits, AtomicBoolean hitCap,
				AtomicBoolean running) {
			this.biomes = biomes;
			this.terrain = terrain;
			this.writer = writer;
			this.hits = hits;
			this.scanned = scanned;
			this.passBlaze = passBlaze;
			this.passBarter = passBarter;
			this.passLootPre = passLootPre;
			this.passVillage = passVillage;
			this.passPortal = passPortal;
			this.passBastion = passBastion;
			this.passTerrain = passTerrain;
			this.passSpawn = passSpawn;
			this.hitListener = hitListener;
			this.targetHits = targetHits;
			this.hitCap = hitCap;
			this.running = running;
		}

		void processStructureSeed(Fsg121StructureSeed.Hit hit) throws IOException {
			if (!running.get() || hitCap.get()) {
				return;
			}
			long base = hit.structureSeed();
			CPos portalStart = new CPos(hit.portalChunkX(), hit.portalChunkZ());
			CPos bastionPos = new CPos(hit.bastionChunkX(), hit.bastionChunkZ());
			int[] vilPos = {hit.villageBlockX(), hit.villageBlockZ()};

			long[] survivors = new long[256];
			int[] survivorObs = new int[256];
			int[] survivorMask = new int[256];
			PortalLootPass[][] survivorPasses = new PortalLootPass[256][];
			int nSurv = 0;

			for (int high = 0; high < 65536; high++) {
				long worldSeed = ((long) high << 48) | base;
				scanned.increment();
				if (!passesBlaze(worldSeed, rng)) {
					continue;
				}
				passBlaze.increment();
				BarterResult barter = runBarter(worldSeed, rng);
				if (barter == null) {
					continue;
				}
				passBarter.increment();

				int mask = speculativePortalLoot(worldSeed, portalStart);
				if (mask == 0) {
					continue;
				}
				passLootPre.increment();

				if (nSurv >= survivors.length) {
					int ns = survivors.length * 2;
					survivors = java.util.Arrays.copyOf(survivors, ns);
					survivorObs = java.util.Arrays.copyOf(survivorObs, ns);
					survivorMask = java.util.Arrays.copyOf(survivorMask, ns);
					survivorPasses = java.util.Arrays.copyOf(survivorPasses, ns);
				}
				survivors[nSurv] = worldSeed;
				survivorObs[nSurv] = barter.obs();
				survivorMask[nSurv] = mask;
				PortalLootPass[] copy = new PortalLootPass[LOOT_VARIANTS.length];
				for (int v = 0; v < LOOT_VARIANTS.length; v++) {
					if ((mask & (1 << v)) != 0) {
						copy[v] = lootSlots[v];
					}
				}
				survivorPasses[nSurv] = copy;
				nSurv++;
			}
			if (nSurv == 0) {
				return;
			}

			int portalBX = portalStart.getX() << 4;
			int portalBZ = portalStart.getZ() << 4;
			CubiomesBiomeSampler.FsgCheck[] checks = biomes.fsgCheckBatch(
					survivors, 0, nSurv,
					portalBX, OW_HEIGHT.blockY, portalBZ,
					vilPos[0], vilPos[1]);

			for (int i = 0; i < nSurv; i++) {
				if (!running.get() || hitCap.get()) {
					return;
				}
				CubiomesBiomeSampler.FsgCheck ck = checks[i];
				if (!ck.villageOk()) {
					continue;
				}
				passVillage.increment();
				if (!finishAfterBiome(survivors[i], portalStart, bastionPos, ck.portalBiomeId(),
						survivorObs[i], survivorMask[i], survivorPasses[i])) {
					continue;
				}
				long worldSeed = survivors[i];
				hits.increment();
				long h = hits.sum();
				if (writer != null) {
					lineBuf.setLength(0);
					lineBuf.append(worldSeed).append('\n');
					String text = lineBuf.toString();
					synchronized (writer) {
						writer.write(text);
						writer.flush();
					}
				}
				try {
					hitListener.onHit(worldSeed);
				} catch (Exception ignored) {
				}
				if (h >= targetHits) {
					hitCap.set(true);
					running.set(false);
					return;
				}
			}
		}

		/**
		 * Try STANDARD/DESERT/JUNGLE/MOUNTAIN loot. Fills {@link #lootSlots} for bits set in the mask.
		 * @return bitmask of variants whose surface placement + chest satisfies iron/light/obs
		 */
		int speculativePortalLoot(long worldSeed, CPos portalStart) {
			int mask = 0;
			for (int v = 0; v < LOOT_VARIANTS.length; v++) {
				lootSlots[v] = null;
				RuinedPortalCenter.Variant variant = LOOT_VARIANTS[v];
				RuinedPortalCenter.PieceGeom geom = RuinedPortalCenter.rollPieceGeom(
						worldSeed, portalStart.getX(), portalStart.getZ(), variant, pieceRand);
				String place = geom.setup().placement();
				if (!"on_land_surface".equals(place) && !"partly_buried".equals(place)) {
					continue;
				}
				int centerCX = geom.centerBlockX() >> 4;
				int centerCZ = geom.centerBlockZ() >> 4;
				long ls = lootSeed(xoro, worldSeed, centerCX, centerCZ, variant.lootSalt);
				portalCtx.setSeed(ls);
				List<ItemStack> portalLoot;
				synchronized (LOOT_LOCK) {
					portalLoot = RuinedPortalBastionStage3Filter.generateStacks(PORTAL_TABLE, portalCtx);
				}
				PortalChestStats stats = portalChestStats(portalLoot, geom.template());
				if (stats == null) {
					continue;
				}
				boolean loot3 = RuinedPortalBastionStage3Filter.hasLooting3Sword(portalLoot);
				lootSlots[v] = new PortalLootPass(variant, geom, portalLoot, stats.excessObs(), loot3);
				mask |= 1 << v;
			}
			return mask;
		}

		boolean finishAfterBiome(long worldSeed, CPos portalStart, CPos bastionPos, int portalBiomeId,
				int barterObs, int lootMask, PortalLootPass[] passes) throws IOException {
			RuinedPortalCenter.Variant variant = RuinedPortalVariantLookup.fromBiomeIdOrNull(portalBiomeId);
			int vIdx = variantIndex(variant);
			if (vIdx < 0 || (lootMask & (1 << vIdx)) == 0) {
				return false;
			}
			PortalLootPass pre = passes[vIdx];
			if (pre == null) {
				return false;
			}
			passPortal.increment();

			// Re-roll winning variant so pieceRand matches findSuitableY's nextInt (partly_buried).
			RuinedPortalCenter.PieceGeom geom = RuinedPortalCenter.rollPieceGeom(
					worldSeed, portalStart.getX(), portalStart.getZ(), variant, pieceRand);
			RuinedPortalCenter.Setup setup = geom.setup();
			boolean hasLooting3 = pre.looting3();
			int excessObs = pre.excessObs();

			if (!bastionGen.generate(worldSeed, bastionPos)) {
				return false;
			}
			if (bastionGen.getType() == BastionType.STABLES) {
				int[] info = bastionGen.getStableInfo();
				if (info == null || info[0] < 1) {
					return false;
				}
			}

			int towerObs = 0;
			List<RuinedPortalBastionStage3Filter.RampartChest> ramparts;
			synchronized (LOOT_LOCK) {
				ramparts = RuinedPortalBastionStage3Filter.rampartChestsWithLoot(bastionGen, worldSeed);
			}
			for (RuinedPortalBastionStage3Filter.RampartChest chest : ramparts) {
				if (RuinedPortalBastionStage3Filter.hasLooting3Sword(chest.items())) {
					hasLooting3 = true;
				}
				for (ItemStack s : chest.items()) {
					if ("obsidian".equals(s.getItem().getName())) {
						towerObs += s.getCount();
					}
				}
			}
			if (!hasLooting3) {
				return false;
			}
			if (towerObs + barterObs + excessObs < MIN_OBSIDIAN) {
				return false;
			}
			passBastion.increment();

			BlockBox bb = geom.piece();
			int surfaceY = terrain.topSolidY(worldSeed, geom.centerBlockX(), geom.centerBlockZ());
			int candY = RuinedPortalCenter.candidateYBeforeDig(setup, surfaceY, geom.ySpan(), pieceRand);
			int projectedY = terrain.digPortalY(
					worldSeed, candY, CubiomesTerrainSampler.DIG_MIN_Y,
					bb.minX, bb.minZ, bb.maxX, bb.minZ, bb.minX, bb.maxZ, bb.maxX, bb.maxZ);
			if (RuinedPortalRepairable121Filter.minimalPortalHasCrying(geom, projectedY, cryingRand)) {
				return false;
			}
			passTerrain.increment();

			int ox = portalStart.getX() << 4;
			int oz = portalStart.getZ() << 4;
			int[] spawn = biomes.spawnXZ(worldSeed);
			if (Math.abs(spawn[0] - ox) > SPAWN_PORTAL_ORIGIN_RANGE
					|| Math.abs(spawn[1] - oz) > SPAWN_PORTAL_ORIGIN_RANGE) {
				return false;
			}
			passSpawn.increment();
			return true;
		}

		static int variantIndex(RuinedPortalCenter.Variant variant) {
			if (variant == null) {
				return -1;
			}
			for (int i = 0; i < LOOT_VARIANTS.length; i++) {
				if (LOOT_VARIANTS[i] == variant) {
					return i;
				}
			}
			return -1;
		}
	}

	record PortalLootPass(
			RuinedPortalCenter.Variant variant,
			RuinedPortalCenter.PieceGeom geom,
			List<ItemStack> loot,
			int excessObs,
			boolean looting3
	) {
	}

	/** 前两只棒合计 &lt;2 剪枝；三只合计 ≥6。 */
	static boolean passesBlaze(long worldSeed, XoroRng rng) {
		rng.setSequence(worldSeed, BLAZE_MD5_0, BLAZE_MD5_1);
		int a = nextBlazeRods(rng);
		int b = nextBlazeRods(rng);
		if (a + b < 2) {
			return false;
		}
		int c = nextBlazeRods(rng);
		return a + b + c >= 6;
	}

	static int nextBlazeRods(XoroRng rng) {
		int base = rng.nextInt(2);
		int extra = Math.round(rng.nextFloat() * 3.0f);
		return base + extra;
	}

	/**
	 * 108 次交易：珍珠剪枝同 PearlBlaze，同趟累计 barterObs（RNG 仍走满含线条目）。
	 * @return null if pearls fail
	 */
	static BarterResult runBarter(long worldSeed, XoroRng rng) {
		rng.setSequence(worldSeed, BARTER_MD5_0, BARTER_MD5_1);
		int pearls = 0;
		int obs = 0;
		for (int n = 1; n <= BARTER_TRADES; n++) {
			int entry = WEIGHT_TO_ENTRY[rng.nextInt(TOTAL_WEIGHT)] & 0xFF;
			int extra = EXTRA[entry];
			if (entry == PEARL_ENTRY) {
				pearls += 2 + rng.nextInt(3);
			} else if (entry == OBSIDIAN_ENTRY) {
				obs += 1;
			} else if (entry == STRING_ENTRY) {
				rng.nextInt(7); // 3..9 的 nextInt，保持相位
			} else if (extra < 0) {
				rng.nextInt(1);
				rng.nextInt(3);
			} else if (extra > 0) {
				rng.nextInt(extra);
			}
			if (n <= 72) {
				if (pearls + 4 * (72 - n) < 12) {
					return null;
				}
			} else if (pearls + 4 * (108 - n) < MIN_PEARLS) {
				return null;
			}
		}
		if (pearls < MIN_PEARLS) {
			return null;
		}
		return new BarterResult(obs);
	}

	static PortalChestStats portalChestStats(List<ItemStack> items, String template) {
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
		if (iron < MIN_IRON || !light || obsidian == 0) {
			return null;
		}
		int missing = RuinedPortalRepairable121Filter.getMissingObsidian(template);
		if (obsidian < missing) {
			return null;
		}
		return new PortalChestStats(obsidian - missing);
	}

	private static long lootSeed(DecorXoro x, long worldSeed, int centerCX, int centerCZ, int salt) {
		long pop = x.getPopulationSeed(worldSeed, centerCX << 4, centerCZ << 4);
		x.setDecoratorSeed(pop, salt);
		return x.nextLong();
	}

	record BarterResult(int obs) {
	}

	record PortalChestStats(int excessObs) {
	}

	/** 1.18+ population / decorator Xoroshiro (loot seed). */
	static final class DecorXoro {
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

	/** Shared Xoroshiro for blaze / barter entity sequences. */
	static final class XoroRng {
		private long seedLo, seedHi;

		void setSequence(long worldSeed, long md5Lo, long md5Hi) {
			long unmixedLo = worldSeed ^ SILVER_RATIO_64;
			long unmixedHi = unmixedLo - SUBTRACT_CONSTANT;
			setSeed(mixStafford13(unmixedLo ^ md5Lo), mixStafford13(unmixedHi ^ md5Hi));
		}

		void setSeed(long lo, long hi) {
			seedLo = lo;
			seedHi = hi;
			if ((seedLo | seedHi) == 0L) {
				seedLo = -7046029254386353131L;
				seedHi = 7640891576956012809L;
			}
		}

		static long mixStafford13(long seed) {
			seed = (seed ^ (seed >>> 30)) * -4658895280553007687L;
			seed = (seed ^ (seed >>> 27)) * -7723592293110705685L;
			return seed ^ (seed >>> 31);
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

		float nextFloat() {
			return (nextLong() >>> 40) * 5.9604645E-8F;
		}
	}
}
