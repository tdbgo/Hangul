package kr.playcity.hangul;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Small opt-in configuration, written only when the user changes the setting. */
public final class ImePreferences {
	private ImePreferences() {}

	public static boolean load(Path path) throws IOException {
		if (!Files.exists(path)) return false;
		Properties values = new Properties();
		try (var reader = Files.newBufferedReader(path)) { values.load(reader); }
		return "true".equals(values.getProperty("halfWidthLatin", "false").trim());
	}

	public static void save(Path path, boolean enabled) throws IOException {
		Properties values = new Properties();
		if (Files.exists(path)) {
			try (var reader = Files.newBufferedReader(path)) { values.load(reader); }
		}
		values.setProperty("halfWidthLatin", Boolean.toString(enabled));
		Files.createDirectories(path.toAbsolutePath().getParent());
		Path temporary = Files.createTempFile(path.toAbsolutePath().getParent(), "hangul-", ".tmp");
		try {
			try (var writer = Files.newBufferedWriter(temporary)) {
				values.store(writer, "Hangul IME settings; opt-in Korean Latin width guard");
			}
			try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
			catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally { Files.deleteIfExists(temporary); }
	}
}
