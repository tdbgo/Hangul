package kr.playcity.hangul.verification;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/** Runs only in the isolated verification launch, never in the distributed mod. */
@Mod(value = "hangul_verification", dist = Dist.CLIENT)
public final class NeoForgeVerification {
	public NeoForgeVerification() {
		if (Boolean.getBoolean("hangul.verification.nativeInputCheck")) {
			try {
				byte[] candidate = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(System.getProperty("hangul.verification.jar")));
				String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(candidate));
				org.slf4j.LoggerFactory.getLogger("Hangul").info("NATIVE_INPUT_CHECK_READY candidate_sha256={} (manual IME validation required)", hash);
				return;
			} catch (Exception failure) {
				throw new IllegalStateException("Could not identify the native input candidate", failure);
			}
		}
		LoaderBehaviorCheck.runAndExit();
	}
}
