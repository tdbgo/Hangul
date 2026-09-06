package kr.playcity.hangul.verification;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/** Fabric and Quilt adapter for the shared, test-only verification suite. */
public final class MixinApplicationCheck implements PreLaunchEntrypoint {
	@Override
	public void onPreLaunch() {
		LoaderBehaviorCheck.runAndExit();
	}
}
