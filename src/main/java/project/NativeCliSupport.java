package project;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 定位 / 从 fat-jar 解压 cubiomes CLI（biome + terrain）。
 */
public final class NativeCliSupport {
	private static final AtomicReference<Path> DIR = new AtomicReference<>();

	private NativeCliSupport() {
	}

	public static Path biomeCli() throws IOException {
		return resolve("cubiomes_biome_cli.exe");
	}

	public static Path terrainCli() throws IOException {
		return resolve("cubiomes_terrain_cli.exe");
	}

	private static Path resolve(String name) throws IOException {
		Path dir = ensureDir();
		Path exe = dir.resolve(name);
		if (Files.isRegularFile(exe)) {
			return exe;
		}
		throw new IOException("native CLI missing after extract: " + exe);
	}

	private static Path ensureDir() throws IOException {
		Path existing = DIR.get();
		if (existing != null && Files.isDirectory(existing)) {
			return existing;
		}
		synchronized (NativeCliSupport.class) {
			existing = DIR.get();
			if (existing != null && Files.isDirectory(existing)) {
				return existing;
			}
			Path dir = findOrExtract();
			DIR.set(dir);
			return dir;
		}
	}

	private static Path findOrExtract() throws IOException {
		// 1) cwd / project native/
		Path cwd = Path.of("").toAbsolutePath();
		for (Path d = cwd; d != null; d = d.getParent()) {
			Path cand = d.resolve("native");
			if (Files.isRegularFile(cand.resolve("cubiomes_biome_cli.exe"))
					&& Files.isRegularFile(cand.resolve("cubiomes_terrain_cli.exe"))) {
				return cand;
			}
		}
		// 2) classpath resources → temp
		Path tmp = Files.createTempDirectory("fsg121-native-");
		tmp.toFile().deleteOnExit();
		extractResource("native/cubiomes_biome_cli.exe", tmp.resolve("cubiomes_biome_cli.exe"));
		extractResource("native/cubiomes_terrain_cli.exe", tmp.resolve("cubiomes_terrain_cli.exe"));
		return tmp;
	}

	private static void extractResource(String resource, Path dest) throws IOException {
		try (InputStream in = NativeCliSupport.class.getClassLoader().getResourceAsStream(resource)) {
			if (in == null) {
				throw new IOException("classpath resource missing: " + resource
						+ " (build fatJar with natives under src/main/resources/native/)");
			}
			Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
			dest.toFile().setExecutable(true);
			dest.toFile().deleteOnExit();
		}
	}
}
