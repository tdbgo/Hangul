package kr.playcity.hangul.mixin;

import kr.playcity.hangul.HangulController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftScreenMixin {
	// 26.1 owns setScreen here; 26.2+ uses GuiScreenMixin instead.
	// Binary compatibility verification requires exactly one of these hooks to match.
	@Inject(method = "setScreen", at = @At("HEAD"), require = 0, expect = 0)
	private void hangul$endFallbackSession(final CallbackInfo callback) {
		HangulController.onScreenChanged();
	}
}
