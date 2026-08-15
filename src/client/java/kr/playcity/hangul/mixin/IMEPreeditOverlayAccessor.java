package kr.playcity.hangul.mixin;

import net.minecraft.client.gui.components.IMEPreeditOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(IMEPreeditOverlay.class)
public interface IMEPreeditOverlayAccessor {
	@Accessor("inputLeft")
	int hangul$getInputLeft();

	@Accessor("inputTop")
	int hangul$getInputTop();

	@Accessor("inputHeight")
	int hangul$getInputHeight();
}
