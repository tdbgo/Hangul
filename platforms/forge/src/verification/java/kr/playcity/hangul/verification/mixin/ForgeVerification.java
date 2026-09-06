package kr.playcity.hangul.verification.mixin;

import kr.playcity.hangul.verification.LoaderBehaviorCheck;

import net.minecraft.client.main.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test-only adapter, excluded from the published mod. */
@Mixin(Main.class)
public abstract class ForgeVerification {
	@Inject(method = "main", at = @At("HEAD"))
	private static void verifyBeforeWindow(String[] args, CallbackInfo callback) {
		LoaderBehaviorCheck.runAndExit();
	}
}
