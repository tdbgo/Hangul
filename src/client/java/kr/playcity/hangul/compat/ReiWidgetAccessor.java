package kr.playcity.hangul.compat;

import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Font belongs to REI's widget superclass, not its text field. */
@Pseudo
@Mixin(targets = "me.shedaniel.rei.api.client.gui.widgets.Widget", remap = false)
public interface ReiWidgetAccessor {
	@Accessor("font") Font hangul$getReiFont();
}
