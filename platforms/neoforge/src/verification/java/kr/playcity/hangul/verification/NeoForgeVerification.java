package kr.playcity.hangul.verification;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/** Runs only in the isolated verification launch, never in the distributed mod. */
@Mod(value = "hangul_verification", dist = Dist.CLIENT)
public final class NeoForgeVerification {
	public NeoForgeVerification() {
		LoaderBehaviorCheck.runAndExit();
	}
}
