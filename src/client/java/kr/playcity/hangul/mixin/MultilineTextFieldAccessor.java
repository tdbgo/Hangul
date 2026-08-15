package kr.playcity.hangul.mixin;

import net.minecraft.client.gui.components.MultilineTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MultilineTextField.class)
public interface MultilineTextFieldAccessor {
	@Accessor("value")
	String hangul$getValue();

	@Accessor("value")
	void hangul$setValue(String value);

	@Accessor("cursor")
	int hangul$getCursor();

	@Accessor("cursor")
	void hangul$setCursor(int cursor);

	@Accessor("selectCursor")
	int hangul$getSelectCursor();

	@Accessor("selectCursor")
	void hangul$setSelectCursor(int selectCursor);

	@Invoker("reflowDisplayLines")
	void hangul$reflowDisplayLines();
}
