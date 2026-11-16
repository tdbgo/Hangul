package kr.playcity.hangul.verification;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Startup-only fixture: no world, text, input mode, or keyboard events are changed. */
@Mixin(Minecraft.class)
public abstract class NativeClientSmoke {
	@Unique private int hangul$readyTicks;

	@Inject(method = "tick", at = @At("TAIL"))
	private void hangul$checkStartup(CallbackInfo callback) {
		Minecraft client = (Minecraft) (Object) this;
		if (!(client.gui.screen() instanceof TitleScreen) || client.gui.overlay() != null) {
			hangul$readyTicks = 0;
			return;
		}
		if (++hangul$readyTicks == 60) {
			LoggerFactory.getLogger("Hangul").info("NATIVE_CLIENT_SMOKE_OK title screen ready; native IME not exercised");
			client.stop();
		}
	}
}
