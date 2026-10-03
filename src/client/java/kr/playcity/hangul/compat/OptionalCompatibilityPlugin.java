package kr.playcity.hangul.compat;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/** Checks optional widget signatures before applying an adapter, without loading the widget. */
public final class OptionalCompatibilityPlugin implements IMixinConfigPlugin {
	private static final String IMBLOCKER = "io.github.reserveword.imblocker.common.IMBlockerMixinPlugin";

	@Override public void onLoad(String mixinPackage) {}
	@Override public String getRefMapperConfig() { return null; }
	@Override public List<String> getMixins() { return null; }
	@Override public void acceptTargets(Set<String> ownTargets, Set<String> otherTargets) {}
	@Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
	@Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}

	@Override
	public boolean shouldApplyMixin(String target, String mixin) {
		boolean accessor = mixin.endsWith(".ReiWidgetAccessor");
		if (!accessor && !mixin.endsWith(".ReiTextFieldMixin")) return false;
		if (read(IMBLOCKER) != null) {
			LoggerFactory.getLogger("Hangul").info("REI adapter disabled: IMBlocker owns the optional input widget");
			return false;
		}
		ClassNode widget = read(target);
		if (widget == null) return false;
		if (accessor) return field(widget, "font", "Lnet/minecraft/client/gui/Font;");
		ClassNode base = read("me.shedaniel.rei.api.client.gui.widgets.Widget");
		boolean supported = base != null && field(base, "font", "Lnet/minecraft/client/gui/Font;")
			&& !method(widget, "preeditUpdated", "(Lnet/minecraft/client/input/PreeditEvent;)Z")
			&& field(widget, "text", "Ljava/lang/String;")
			&& field(widget, "bounds", "Lme/shedaniel/math/Rectangle;")
			&& field(widget, "cursorPos", "I") && field(widget, "highlightPos", "I")
			&& field(widget, "firstCharacterIndex", "I") && field(widget, "editable", "Z")
			&& field(widget, "focused", "Z") && field(widget, "visible", "Z")
			&& field(widget, "hasBorder", "Z")
			&& method(widget, "render", "(Lme/shedaniel/rei/api/client/gui/compat/GuiGraphics;IIF)V")
			&& method(widget, "charTyped", "(Lnet/minecraft/client/input/CharacterEvent;)Z")
			&& method(widget, "addText", "(Ljava/lang/String;)V")
			&& method(widget, "setText", "(Ljava/lang/String;)V")
			&& method(widget, "setFocused", "(Z)V") && method(widget, "setVisible", "(Z)V")
			&& method(widget, "setIsEditable", "(Z)V");
		if (!supported) LoggerFactory.getLogger("Hangul").warn("Unrecognized REI text widget; leaving its input unchanged");
		return supported;
	}

	private static ClassNode read(String name) {
		try {
			// FML supports transformed bytecode retrieval only; this does not initialize the class.
			return MixinService.getService().getBytecodeProvider().getClassNode(name, true);
		} catch (ClassNotFoundException | java.io.IOException absent) {
			return null;
		}
	}
	private static boolean field(ClassNode node, String name, String desc) {
		return node.fields.stream().anyMatch(field -> field.name.equals(name) && field.desc.equals(desc));
	}
	private static boolean method(ClassNode node, String name, String desc) {
		return node.methods.stream().anyMatch(method -> method.name.equals(name) && method.desc.equals(desc));
	}
}
