package project;

import ruinedportalgenerator.CubiomesBiomeSampler;

import java.io.IOException;

/**
 * 1.21 FSG 结构种子预筛（仅低 48 位）。
 *
 * <p>两级漏斗：
 * <ol>
 *   <li>{@link #cheapGate} — 纯 LCG，无 IPC（超集）</li>
 *   <li>{@link #trySeed} — cheap 通过后再 cubiomes {@code STRUCTFSG} 确认下界群系</li>
 * </ol>
 */
public final class Fsg121StructureSeed {
	static final long MASK_48 = 0xFFFFFFFFFFFFL;
	private static final long MULT = 0x5DEECE66DL;
	private static final long ADD = 0xBL;

	private static final int PORTAL_SALT = 34222645;
	private static final int PORTAL_SPACING = 40;
	private static final int PORTAL_RANGE = 25;

	private static final int VILLAGE_SALT = 10387312;
	private static final int VILLAGE_SPACING = 34;
	private static final int VILLAGE_RANGE = 26;

	private static final int NETHER_SALT = 30084232;
	private static final int NETHER_SPACING = 27;
	private static final int NETHER_RANGE = 23;

	private static final int BASTION_R_SQ = 100 * 100;
	private static final int FORTRESS_R_SQ = 160 * 160;

	private static final ThreadLocal<Lcg> LCG = ThreadLocal.withInitial(Lcg::new);

	private Fsg121StructureSeed() {
	}

	public record Hit(
			long structureSeed,
			int portalChunkX, int portalChunkZ,
			int villageChunkX, int villageChunkZ,
			int bastionChunkX, int bastionChunkZ,
			int fortressChunkX, int fortressChunkZ
	) {
		public int portalOriginX() {
			return portalChunkX << 4;
		}

		public int portalOriginZ() {
			return portalChunkZ << 4;
		}

		public int villageBlockX() {
			return villageChunkX << 4;
		}

		public int villageBlockZ() {
			return villageChunkZ << 4;
		}

		public static Hit from(CubiomesBiomeSampler.FsgStructHit h) {
			if (h == null) {
				return null;
			}
			return new Hit(h.structureSeed(),
					h.portalChunkX(), h.portalChunkZ(),
					h.villageChunkX(), h.villageChunkZ(),
					h.bastionChunkX(), h.bastionChunkZ(),
					h.fortressChunkX(), h.fortressChunkZ());
		}
	}

	/**
	 * 热路径：仅 LCG。目标数千万 seeds/s。
	 * 通过后仍须 {@link #trySeed} 做群系确认（玄武岩三角洲等）。
	 */
	public static boolean cheapGate(long structureSeed48) {
		long seed = structureSeed48 & MASK_48;
		Lcg rnd = LCG.get();

		long packed = featureChunk(rnd, seed, 0, 0, PORTAL_SALT, PORTAL_SPACING, PORTAL_RANGE);
		int pcx = (int) (packed >> 32);
		int pcz = (int) packed;
		if (pcx < 0 || pcx > 3 || pcz < 0 || pcz > 3) {
			return false;
		}

		packed = featureChunk(rnd, seed, 0, 0, VILLAGE_SALT, VILLAGE_SPACING, VILLAGE_RANGE);
		int vcx = (int) (packed >> 32);
		int vcz = (int) packed;
		if (vcx < 0 || vcx > 6 || vcz < 0 || vcz > 6) {
			return false;
		}

		for (int rx = -1; rx <= 0; rx++) {
			for (int rz = -1; rz <= 0; rz++) {
				packed = featureChunk(rnd, seed, rx, rz, NETHER_SALT, NETHER_SPACING, NETHER_RANGE);
				int bcx = (int) (packed >> 32);
				int bcz = (int) packed;
				if (!bastionRoll(rnd, seed, bcx, bcz)) {
					continue;
				}
				int bx = bcx << 4;
				int bz = bcz << 4;
				if (bx * bx + bz * bz > BASTION_R_SQ) {
					continue;
				}
				if (hasFortressSlotNear(rnd, seed, bcx, bcz, bx, bz)) {
					return true;
				}
			}
		}
		return false;
	}

	/** cheap 通过后再调 cubiomes 下界群系。 */
	public static Hit trySeed(long structureSeed48, CubiomesBiomeSampler biomes) throws IOException {
		long seed = structureSeed48 & MASK_48;
		if (!cheapGate(seed)) {
			return null;
		}
		return Hit.from(biomes.structFsg(seed));
	}

	private static boolean hasFortressSlotNear(Lcg rnd, long seed, int bastionCx, int bastionCz, int bbx, int bbz) {
		int brx = Math.floorDiv(bastionCx, NETHER_SPACING);
		int brz = Math.floorDiv(bastionCz, NETHER_SPACING);
		for (int frx = brx - 1; frx <= brx + 1; frx++) {
			for (int frz = brz - 1; frz <= brz + 1; frz++) {
				if (frx == brx && frz == brz) {
					continue;
				}
				long packed = featureChunk(rnd, seed, frx, frz, NETHER_SALT, NETHER_SPACING, NETHER_RANGE);
				int fcx = (int) (packed >> 32);
				int fcz = (int) packed;
				int dx = (fcx << 4) - bbx;
				int dz = (fcz << 4) - bbz;
				if (dx * dx + dz * dz <= FORTRESS_R_SQ) {
					return true;
				}
			}
		}
		return false;
	}

	/** Cubiomes chunkGenerateRnd + nextInt(5) ≥ 2. */
	private static boolean bastionRoll(Lcg rnd, long worldSeed, int chunkX, int chunkZ) {
		rnd.setSeed(worldSeed);
		long mixed = (rnd.nextLong() * chunkX) ^ (rnd.nextLong() * chunkZ) ^ worldSeed;
		rnd.setSeed(mixed);
		return rnd.nextInt(5) >= 2;
	}

	/** @return {@code (chunkX << 32) | (chunkZ & 0xffffffffL)} */
	private static long featureChunk(Lcg rnd, long seed, int rx, int rz, int salt, int spacing, int range) {
		rnd.setSeed(seed + (long) rx * 341873128712L + (long) rz * 132897987541L + salt);
		int cx = rx * spacing + rnd.nextInt(range);
		int cz = rz * spacing + rnd.nextInt(range);
		return ((long) cx << 32) | (cz & 0xffffffffL);
	}

	/** java.util.Random compatible LCG (48-bit). */
	static final class Lcg {
		long s;

		void setSeed(long seed) {
			s = (seed ^ MULT) & MASK_48;
		}

		int next(int bits) {
			s = (s * MULT + ADD) & MASK_48;
			return (int) (s >>> (48 - bits));
		}

		int nextInt(int n) {
			if ((n & -n) == n) {
				return (int) ((n * (long) next(31)) >> 31);
			}
			int bits, val;
			do {
				bits = next(31);
				val = bits % n;
			} while (bits - val + (n - 1) < 0);
			return val;
		}

		long nextLong() {
			return ((long) next(32) << 32) + next(32);
		}
	}
}
