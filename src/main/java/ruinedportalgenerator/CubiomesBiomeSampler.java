package ruinedportalgenerator;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Biome / noise sampling <b>only</b> via the cubiomes native CLI
 * ({@code native/cubiomes_biome_cli.exe}), never via {@code mc_biome}.
 *
 * <p>Prefer {@link SampleHeight#HIGH_256} and {@link #sampleIdsFixedStartChunkBatch}
 * for surface-portal filters (1:4 quart sample, IDs only).
 */
public final class CubiomesBiomeSampler implements AutoCloseable {
	public enum SampleHeight {
		SURFACE_BAND_72(72),
		HIGH_256(256);

		public final int blockY;

		SampleHeight(int blockY) {
			this.blockY = blockY;
		}
	}

	public enum Dimension {
		OVERWORLD(0),
		NETHER(-1),
		END(1);

		public final int cubiomesDim;

		Dimension(int cubiomesDim) {
			this.cubiomesDim = cubiomesDim;
		}
	}

	public record Sample(int biomeId, String biomeName, int blockX, int blockY, int blockZ, SampleHeight heightMode) {
		public boolean looksLikeCaveLayer() {
			return RuinedPortalVariantLookup.isCaveLayerId(biomeId)
					|| RuinedPortalVariantLookup.isCaveLayerBiome(biomeName);
		}
	}

	public record Query(long worldSeed, Dimension dim, int blockX, int blockY, int blockZ) {
	}

	private final Process process;
	private final BufferedWriter stdin;
	private final BufferedReader stdout;
	private final Object lock = new Object();

	public CubiomesBiomeSampler(Path cliPath) throws IOException {
		Objects.requireNonNull(cliPath, "cliPath");
		if (!Files.isRegularFile(cliPath)) {
			throw new IOException("cubiomes CLI not found: " + cliPath.toAbsolutePath()
					+ " (build native/cubiomes_biome_cli.exe first)");
		}
		ProcessBuilder pb = new ProcessBuilder(cliPath.toAbsolutePath().toString());
		pb.redirectErrorStream(true);
		this.process = pb.start();
		this.stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8), 1 << 20);
		this.stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8), 1 << 20);
		String ready = stdout.readLine();
		if (ready == null || !ready.startsWith("READY")) {
			closeQuietly();
			throw new IOException("cubiomes CLI did not READY, got: " + ready);
		}
	}

	public static CubiomesBiomeSampler startDefault() throws IOException {
		return new CubiomesBiomeSampler(findDefaultCli());
	}

	public static Path findDefaultCli() throws IOException {
		return project.NativeCliSupport.biomeCli();
	}

	public Sample sampleBlock(long worldSeed, Dimension dim, int blockX, int blockY, int blockZ) throws IOException {
		return sampleBlock(worldSeed, dim, blockX, blockY, blockZ, null);
	}

	public Sample sampleAtStartChunk(long worldSeed, Dimension dim, int startChunkX, int startChunkZ, SampleHeight height)
			throws IOException {
		return sampleBlock(worldSeed, dim, startChunkX << 4, height.blockY, startChunkZ << 4, height);
	}

	/**
	 * Hot path: fixed start-chunk origin + Y → cubiomes biome <b>ids only</b>
	 * ({@code SEEDS} protocol, scale 4 / QuartPos).
	 */
	public int[] sampleIdsFixedStartChunkBatch(long[] worldSeeds, int startChunkX, int startChunkZ,
			Dimension dim, SampleHeight height) throws IOException {
		return sampleIdsFixedStartChunkBatch(worldSeeds, 0, worldSeeds.length, startChunkX, startChunkZ, dim, height);
	}

	public int[] sampleIdsFixedStartChunkBatch(long[] worldSeeds, int offset, int length,
			int startChunkX, int startChunkZ, Dimension dim, SampleHeight height) throws IOException {
		if (offset < 0 || length < 0 || offset + length > worldSeeds.length) {
			throw new IllegalArgumentException("bad slice");
		}
		int bx = startChunkX << 4;
		int by = height.blockY;
		int bz = startChunkZ << 4;
		int d = dim.cubiomesDim;
		synchronized (lock) {
			ensureAlive();
			stdin.write("SEEDS ");
			stdin.write(Integer.toString(length));
			stdin.write(' ');
			stdin.write(Integer.toString(d));
			stdin.write(' ');
			stdin.write(Integer.toString(bx));
			stdin.write(' ');
			stdin.write(Integer.toString(by));
			stdin.write(' ');
			stdin.write(Integer.toString(bz));
			stdin.newLine();
			for (int i = 0; i < length; i++) {
				stdin.write(Long.toString(worldSeeds[offset + i]));
				stdin.newLine();
			}
			stdin.flush();
			return readIdLines(length);
		}
	}

	/**
	 * Per-seed start chunk (e.g. structure {@code getInRegion}) → biome ids at chunk origin + fixed Y.
	 */
	public int[] sampleIdsStartChunksBatch(long[] worldSeeds, int[] startChunkX, int[] startChunkZ,
			Dimension dim, SampleHeight height) throws IOException {
		return sampleIdsStartChunksBatch(worldSeeds, startChunkX, startChunkZ, 0, worldSeeds.length, dim, height);
	}

	public int[] sampleIdsStartChunksBatch(long[] worldSeeds, int[] startChunkX, int[] startChunkZ,
			int offset, int length, Dimension dim, SampleHeight height) throws IOException {
		if (worldSeeds.length != startChunkX.length || worldSeeds.length != startChunkZ.length) {
			throw new IllegalArgumentException("array length mismatch");
		}
		if (offset < 0 || length < 0 || offset + length > worldSeeds.length) {
			throw new IllegalArgumentException("bad slice");
		}
		int by = height.blockY;
		int d = dim.cubiomesDim;
		synchronized (lock) {
			ensureAlive();
			stdin.write("SEEDSXZ ");
			stdin.write(Integer.toString(length));
			stdin.write(' ');
			stdin.write(Integer.toString(d));
			stdin.write(' ');
			stdin.write(Integer.toString(by));
			stdin.newLine();
			for (int i = 0; i < length; i++) {
				int idx = offset + i;
				stdin.write(Long.toString(worldSeeds[idx]));
				stdin.write(' ');
				stdin.write(Integer.toString(startChunkX[idx]));
				stdin.write(' ');
				stdin.write(Integer.toString(startChunkZ[idx]));
				stdin.newLine();
			}
			stdin.flush();
			return readIdLines(length);
		}
	}

	private int[] readIdLines(int length) throws IOException {
		int[] out = new int[length];
		for (int i = 0; i < length; i++) {
			String line = stdout.readLine();
			if (line == null) {
				throw new IOException("cubiomes CLI closed unexpectedly at " + i + "/" + length);
			}
			if (line.startsWith("ERR")) {
				throw new IOException("cubiomes CLI: " + line);
			}
			out[i] = Integer.parseInt(line.trim());
		}
		return out;
	}

	public List<Sample> sampleAtStartChunksBatch(
			long[] worldSeeds, int[] startChunkX, int[] startChunkZ,
			Dimension dim, SampleHeight height
	) throws IOException {
		if (worldSeeds.length != startChunkX.length || worldSeeds.length != startChunkZ.length) {
			throw new IllegalArgumentException("array length mismatch");
		}
		List<Query> queries = new ArrayList<>(worldSeeds.length);
		for (int i = 0; i < worldSeeds.length; i++) {
			queries.add(new Query(worldSeeds[i], dim, startChunkX[i] << 4, height.blockY, startChunkZ[i] << 4));
		}
		return sampleBatch(queries, height);
	}

	/** Same start chunk for every seed; returns full Samples (name included). Prefer {@link #sampleIdsFixedStartChunkBatch}. */
	public List<Sample> sampleFixedStartChunkBatch(long[] worldSeeds, int startChunkX, int startChunkZ,
			Dimension dim, SampleHeight height) throws IOException {
		int[] ids = sampleIdsFixedStartChunkBatch(worldSeeds, startChunkX, startChunkZ, dim, height);
		int bx = startChunkX << 4;
		int by = height.blockY;
		int bz = startChunkZ << 4;
		List<Sample> out = new ArrayList<>(ids.length);
		for (int id : ids) {
			out.add(new Sample(id, null, bx, by, bz, height));
		}
		return out;
	}

	public List<Sample> sampleBatch(List<Query> queries) throws IOException {
		return sampleBatch(queries, null);
	}

	private List<Sample> sampleBatch(List<Query> queries, SampleHeight height) throws IOException {
		synchronized (lock) {
			ensureAlive();
			int n = queries.size();
			stdin.write("BATCH " + n);
			stdin.newLine();
			for (Query q : queries) {
				stdin.write(Long.toString(q.worldSeed()));
				stdin.write(' ');
				stdin.write(Integer.toString(q.dim().cubiomesDim));
				stdin.write(' ');
				stdin.write(Integer.toString(q.blockX()));
				stdin.write(' ');
				stdin.write(Integer.toString(q.blockY()));
				stdin.write(' ');
				stdin.write(Integer.toString(q.blockZ()));
				stdin.newLine();
			}
			stdin.flush();

			List<Sample> out = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				String line = stdout.readLine();
				if (line == null) {
					throw new IOException("cubiomes CLI closed unexpectedly at " + i + "/" + n);
				}
				if (line.startsWith("ERR")) {
					throw new IOException("cubiomes CLI: " + line);
				}
				Query q = queries.get(i);
				out.add(parseOk(line, q.blockX(), q.blockY(), q.blockZ(), height));
			}
			return out;
		}
	}

	public SamplePair sampleStartChunkBothHeights(long worldSeed, Dimension dim, int startChunkX, int startChunkZ)
			throws IOException {
		Sample low = sampleAtStartChunk(worldSeed, dim, startChunkX, startChunkZ, SampleHeight.SURFACE_BAND_72);
		Sample high = sampleAtStartChunk(worldSeed, dim, startChunkX, startChunkZ, SampleHeight.HIGH_256);
		return new SamplePair(low, high);
	}

	/**
	 * Region (0,0) village: cubiomes {@code getStructurePos} + {@code isViableStructurePos}.
	 * Does not use mcfeature for biome viability.
	 */
	public boolean villageRegion00Viable(long worldSeed) throws IOException {
		return villageRegion00(worldSeed) != null;
	}

	/** @return block XZ + cubiomes biome id, or null if not viable */
	public VillageHit villageRegion00(long worldSeed) throws IOException {
		synchronized (lock) {
			ensureAlive();
			stdin.write("VILLAGE ");
			stdin.write(Long.toUnsignedString(worldSeed));
			stdin.newLine();
			stdin.flush();
			String line = stdout.readLine();
			if (line == null) {
				throw new IOException("cubiomes CLI closed unexpectedly on VILLAGE");
			}
			if (line.startsWith("FAIL")) {
				return null;
			}
			if (line.startsWith("ERR")) {
				throw new IOException("cubiomes CLI: " + line);
			}
			String[] p = line.split("\\s+");
			if (p.length < 4 || !"OK".equals(p[0])) {
				throw new IOException("bad VILLAGE response: " + line);
			}
			return new VillageHit(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
		}
	}

	public record VillageHit(int blockX, int blockZ, int biomeId) {
	}

	/** Region (0,0) village attempt block XZ (structure-seed / low-48 only). */
	public int[] villagePosRegion00(long worldSeed) throws IOException {
		synchronized (lock) {
			ensureAlive();
			stdin.write("VILLAGEPOS ");
			stdin.write(Long.toUnsignedString(worldSeed));
			stdin.newLine();
			stdin.flush();
			String line = stdout.readLine();
			if (line == null) {
				throw new IOException("cubiomes CLI closed unexpectedly on VILLAGEPOS");
			}
			if (line.startsWith("FAIL")) {
				return null;
			}
			if (line.startsWith("ERR")) {
				throw new IOException("cubiomes CLI: " + line);
			}
			String[] p = line.split("\\s+");
			if (p.length < 3 || !"OK".equals(p[0])) {
				throw new IOException("bad VILLAGEPOS response: " + line);
			}
			return new int[]{Integer.parseInt(p[1]), Integer.parseInt(p[2])};
		}
	}

	/**
	 * Batch post-barter check: one {@code applySeed} per seed → portal biome + village viable.
	 * @return length==n, each {@link FsgCheck#villageOk()} / {@link FsgCheck#portalBiomeId()}
	 */
	public FsgCheck[] fsgCheckBatch(long[] worldSeeds, int offset, int length,
			int portalBlockX, int portalBlockY, int portalBlockZ, int villageBlockX, int villageBlockZ)
			throws IOException {
		if (offset < 0 || length < 0 || offset + length > worldSeeds.length) {
			throw new IllegalArgumentException("bad slice");
		}
		synchronized (lock) {
			ensureAlive();
			stdin.write("FSGCHECK ");
			stdin.write(Integer.toString(length));
			stdin.write(' ');
			stdin.write(Integer.toString(portalBlockX));
			stdin.write(' ');
			stdin.write(Integer.toString(portalBlockY));
			stdin.write(' ');
			stdin.write(Integer.toString(portalBlockZ));
			stdin.write(' ');
			stdin.write(Integer.toString(villageBlockX));
			stdin.write(' ');
			stdin.write(Integer.toString(villageBlockZ));
			stdin.newLine();
			for (int i = 0; i < length; i++) {
				stdin.write(Long.toString(worldSeeds[offset + i]));
				stdin.newLine();
			}
			stdin.flush();
			FsgCheck[] out = new FsgCheck[length];
			for (int i = 0; i < length; i++) {
				String line = stdout.readLine();
				if (line == null) {
					throw new IOException("cubiomes CLI closed unexpectedly at FSGCHECK " + i);
				}
				if (line.startsWith("ERR")) {
					throw new IOException("cubiomes CLI: " + line);
				}
				String[] p = line.split("\\s+");
				if (p.length < 2) {
					throw new IOException("bad FSGCHECK response: " + line);
				}
				out[i] = new FsgCheck("1".equals(p[0]), Integer.parseInt(p[1]));
			}
			return out;
		}
	}

	public record FsgCheck(boolean villageOk, int portalBiomeId) {
	}

	/**
	 * Approximate overworld spawn XZ via cubiomes {@code getSpawn} (1.18+).
	 * @return {@code int[]{x,z}}
	 */
	public int[] spawnXZ(long worldSeed) throws IOException {
		synchronized (lock) {
			ensureAlive();
			stdin.write("SPAWN ");
			stdin.write(Long.toUnsignedString(worldSeed));
			stdin.newLine();
			stdin.flush();
			String line = stdout.readLine();
			if (line == null) {
				throw new IOException("cubiomes CLI closed unexpectedly on SPAWN");
			}
			if (line.startsWith("ERR")) {
				throw new IOException("cubiomes CLI: " + line);
			}
			String[] p = line.split("\\s+");
			if (p.length < 3 || !"OK".equals(p[0])) {
				throw new IOException("bad SPAWN response: " + line);
			}
			return new int[]{Integer.parseInt(p[1]), Integer.parseInt(p[2])};
		}
	}

	/**
	 * 48-bit FSG structure prefilter via cubiomes {@code STRUCTFSG}:
	 * portal/village chunk ranges + nether bastion≤100 + fortress≤160
	 * (bastion roll + viability; basalt deltas → fortress).
	 */
	public FsgStructHit structFsg(long structureSeed48) throws IOException {
		synchronized (lock) {
			ensureAlive();
			stdin.write("STRUCTFSG ");
			stdin.write(Long.toUnsignedString(structureSeed48 & 0xFFFFFFFFFFFFL));
			stdin.newLine();
			stdin.flush();
			String line = stdout.readLine();
			if (line == null) {
				throw new IOException("cubiomes CLI closed unexpectedly on STRUCTFSG");
			}
			if (line.startsWith("FAIL")) {
				return null;
			}
			if (line.startsWith("ERR")) {
				throw new IOException("cubiomes CLI: " + line);
			}
			String[] p = line.split("\\s+");
			if (p.length < 9 || !"OK".equals(p[0])) {
				throw new IOException("bad STRUCTFSG response: " + line);
			}
			return new FsgStructHit(
					structureSeed48 & 0xFFFFFFFFFFFFL,
					Integer.parseInt(p[1]), Integer.parseInt(p[2]),
					Integer.parseInt(p[3]), Integer.parseInt(p[4]),
					Integer.parseInt(p[5]), Integer.parseInt(p[6]),
					Integer.parseInt(p[7]), Integer.parseInt(p[8]));
		}
	}

	/**
	 * Batch {@link #structFsg}: one IPC round-trip for many structure seeds.
	 * @return parallel to input slice; null entries failed the gate
	 */
	public FsgStructHit[] structFsgBatch(long[] structureSeeds, int offset, int length) throws IOException {
		if (offset < 0 || length < 0 || offset + length > structureSeeds.length) {
			throw new IllegalArgumentException("bad slice");
		}
		synchronized (lock) {
			ensureAlive();
			stdin.write("STRUCTFSGBATCH ");
			stdin.write(Integer.toString(length));
			stdin.newLine();
			for (int i = 0; i < length; i++) {
				stdin.write(Long.toUnsignedString(structureSeeds[offset + i] & 0xFFFFFFFFFFFFL));
				stdin.newLine();
			}
			stdin.flush();
			FsgStructHit[] out = new FsgStructHit[length];
			for (int i = 0; i < length; i++) {
				String line = stdout.readLine();
				if (line == null) {
					throw new IOException("cubiomes CLI closed unexpectedly at STRUCTFSGBATCH " + i);
				}
				if (line.startsWith("ERR")) {
					throw new IOException("cubiomes CLI: " + line);
				}
				String[] p = line.split("\\s+");
				if (p.length == 1 && "0".equals(p[0])) {
					out[i] = null;
					continue;
				}
				if (p.length < 9 || !"1".equals(p[0])) {
					throw new IOException("bad STRUCTFSGBATCH response: " + line);
				}
				out[i] = new FsgStructHit(
						structureSeeds[offset + i] & 0xFFFFFFFFFFFFL,
						Integer.parseInt(p[1]), Integer.parseInt(p[2]),
						Integer.parseInt(p[3]), Integer.parseInt(p[4]),
						Integer.parseInt(p[5]), Integer.parseInt(p[6]),
						Integer.parseInt(p[7]), Integer.parseInt(p[8]));
			}
			return out;
		}
	}

	public record FsgStructHit(
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
	}

	public record SamplePair(Sample y72, Sample y256) {
		public boolean disagree() {
			return y72.biomeId() != y256.biomeId();
		}
	}

	private Sample sampleBlock(long worldSeed, Dimension dim, int blockX, int blockY, int blockZ, SampleHeight mode)
			throws IOException {
		synchronized (lock) {
			ensureAlive();
			stdin.write("BIOME " + worldSeed + " " + dim.cubiomesDim + " " + blockX + " " + blockY + " " + blockZ);
			stdin.newLine();
			stdin.flush();
			String line = stdout.readLine();
			if (line == null) {
				throw new IOException("cubiomes CLI closed unexpectedly");
			}
			if (line.startsWith("ERR")) {
				throw new IOException("cubiomes CLI: " + line);
			}
			return parseOk(line, blockX, blockY, blockZ, mode);
		}
	}

	private static Sample parseOk(String line, int blockX, int blockY, int blockZ, SampleHeight mode) throws IOException {
		String[] parts = line.split("\\s+", 3);
		if (parts.length < 3 || !"OK".equals(parts[0])) {
			throw new IOException("bad cubiomes response: " + line);
		}
		int id = Integer.parseInt(parts[1]);
		String name = parts[2].trim();
		return new Sample(id, name, blockX, blockY, blockZ, mode);
	}

	private void ensureAlive() throws IOException {
		if (!process.isAlive()) {
			throw new IOException("cubiomes CLI process is dead");
		}
	}

	@Override
	public void close() {
		synchronized (lock) {
			try {
				if (process.isAlive()) {
					stdin.write("QUIT");
					stdin.newLine();
					stdin.flush();
					process.waitFor(2, TimeUnit.SECONDS);
				}
			} catch (Exception ignored) {
			} finally {
				closeQuietly();
			}
		}
	}

	private void closeQuietly() {
		try {
			stdin.close();
		} catch (Exception ignored) {
		}
		try {
			stdout.close();
		} catch (Exception ignored) {
		}
		process.destroyForcibly();
	}
}
