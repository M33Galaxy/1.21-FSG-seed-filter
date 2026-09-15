package ruinedportalgenerator;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Surface / dig-down Y via cubiomes {@code terrainnoise} CLI
 * ({@code native/cubiomes_terrain_cli.exe}), built against {@code c:\Cubiomes}.
 *
 * <p>{@code SURFACE}/{@code DIG} approximate vanilla {@code WORLD_SURFACE_WG}:
 * density ground plus sea-level water fill (rivers/oceans), not bare riverbed.
 *
 * <p>Slow — call only after fast loot / placement filters.
 */
public final class CubiomesTerrainSampler implements AutoCloseable {
	/** Vanilla dig floor: {@code heightAccessor.getMinY() + 15} with minY=-64. */
	public static final int DIG_MIN_Y = -49;

	private final Process process;
	private final BufferedWriter stdin;
	private final BufferedReader stdout;
	private final Object lock = new Object();
	private long loadedSeed = Long.MIN_VALUE;

	public CubiomesTerrainSampler(Path cliPath) throws IOException {
		Objects.requireNonNull(cliPath, "cliPath");
		if (!Files.isRegularFile(cliPath)) {
			throw new IOException("terrain CLI not found: " + cliPath.toAbsolutePath()
					+ " (run native/build_terrain_cli.bat)");
		}
		ProcessBuilder pb = new ProcessBuilder(cliPath.toAbsolutePath().toString());
		pb.redirectErrorStream(true);
		this.process = pb.start();
		this.stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8), 1 << 16);
		this.stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8), 1 << 16);
		String ready = stdout.readLine();
		if (ready == null || !ready.startsWith("READY")) {
			closeQuietly();
			throw new IOException("terrain CLI did not READY, got: " + ready);
		}
	}

	public static CubiomesTerrainSampler startDefault() throws IOException {
		return new CubiomesTerrainSampler(findDefaultCli());
	}

	public static Path findDefaultCli() throws IOException {
		return project.NativeCliSupport.terrainCli();
	}

	/** WORLD_SURFACE_WG ≈ getBaseHeight-1 (includes sea-level water). */
	public int topSolidY(long worldSeed, int blockX, int blockZ) throws IOException {
		synchronized (lock) {
			ensureSeed(worldSeed);
			stdin.write("SURFACE ");
			stdin.write(Integer.toString(blockX));
			stdin.write(' ');
			stdin.write(Integer.toString(blockZ));
			stdin.newLine();
			stdin.flush();
			return readOkInt("SURFACE");
		}
	}

	/**
	 * Dig-down like 26.2 {@code RuinedPortalStructure.findSuitableY}: stop when 3/4 BB corners
	 * are WORLD_SURFACE-opaque (solid or sea-level water fill).
	 */
	public int digPortalY(long worldSeed, int candidateY, int minY,
			int x0, int z0, int x1, int z1, int x2, int z2, int x3, int z3) throws IOException {
		synchronized (lock) {
			ensureSeed(worldSeed);
			stdin.write("DIG ");
			stdin.write(Integer.toString(candidateY));
			stdin.write(' ');
			stdin.write(Integer.toString(minY));
			stdin.write(' ');
			stdin.write(Integer.toString(x0));
			stdin.write(' ');
			stdin.write(Integer.toString(z0));
			stdin.write(' ');
			stdin.write(Integer.toString(x1));
			stdin.write(' ');
			stdin.write(Integer.toString(z1));
			stdin.write(' ');
			stdin.write(Integer.toString(x2));
			stdin.write(' ');
			stdin.write(Integer.toString(z2));
			stdin.write(' ');
			stdin.write(Integer.toString(x3));
			stdin.write(' ');
			stdin.write(Integer.toString(z3));
			stdin.newLine();
			stdin.flush();
			return readOkInt("DIG");
		}
	}

	private void ensureSeed(long worldSeed) throws IOException {
		if (loadedSeed == worldSeed) {
			return;
		}
		stdin.write("SEED ");
		stdin.write(Long.toUnsignedString(worldSeed));
		stdin.newLine();
		stdin.flush();
		String line = stdout.readLine();
		if (line == null || !line.startsWith("OK")) {
			throw new IOException("SEED failed: " + line);
		}
		loadedSeed = worldSeed;
	}

	private int readOkInt(String op) throws IOException {
		String line = stdout.readLine();
		if (line == null || !line.startsWith("OK ")) {
			throw new IOException(op + " failed: " + line);
		}
		return Integer.parseInt(line.substring(3).trim());
	}

	@Override
	public void close() {
		synchronized (lock) {
			try {
				stdin.write("QUIT\n");
				stdin.flush();
			} catch (IOException ignored) {
			}
			closeQuietly();
		}
	}

	private void closeQuietly() {
		try {
			stdin.close();
		} catch (IOException ignored) {
		}
		try {
			stdout.close();
		} catch (IOException ignored) {
		}
		process.destroy();
		try {
			process.waitFor(2, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		if (process.isAlive()) {
			process.destroyForcibly();
		}
	}
}
