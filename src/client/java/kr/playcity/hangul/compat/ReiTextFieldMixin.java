package kr.playcity.hangul.compat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import kr.playcity.hangul.InlinePreedit;
import kr.playcity.hangul.NativeImeSupport;
import kr.playcity.hangul.PreeditCache;
import kr.playcity.hangul.PreeditValueProvider;
import me.shedaniel.math.Rectangle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.PreeditEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Inline preedit for REI's own text widget. No REI code or runtime dependency is bundled. */
@Pseudo
@Mixin(targets = "me.shedaniel.rei.impl.client.gui.widget.basewidgets.TextFieldWidget", remap = false)
public abstract class ReiTextFieldMixin implements PreeditValueProvider {
	@Shadow private Rectangle bounds;
	@Shadow private String text;
	@Shadow protected int cursorPos;
	@Shadow protected int highlightPos;
	@Shadow protected int firstCharacterIndex;
	@Shadow protected boolean editable;
	@Shadow private boolean focused;
	@Shadow private boolean visible;
	@Shadow private boolean hasBorder;
	@Unique private PreeditEvent hangul$preedit;
	@Unique private int hangul$start;
	@Unique private int hangul$end;
	@Unique private final PreeditCache hangul$cache = new PreeditCache();

	// REI inherits the listener's default preedit handler; provide the widget-specific handler.
	public boolean preeditUpdated(final PreeditEvent event) {
		if (!focused || !visible || !editable || event == null || event.fullText().isEmpty()) {
			hangul$clear();
			return focused && visible && editable;
		}
		if (hangul$preedit == null || !hangul$valid()) {
			hangul$start = Math.min(cursorPos, highlightPos);
			hangul$end = Math.max(cursorPos, highlightPos);
		}
		hangul$preedit = event;
		return true;
	}

	@Inject(method = "charTyped", at = @At("HEAD"))
	private void hangul$onCharacter(CharacterEvent event, CallbackInfoReturnable<Boolean> callback) { hangul$clear(); }
	@Inject(method = {"setText", "addText"}, at = @At("HEAD"))
	private void hangul$onText(String value, CallbackInfo callback) { hangul$clear(); }
	@Inject(method = {"setFocused", "setVisible", "setIsEditable"}, at = @At("HEAD"))
	private void hangul$onInactive(boolean enabled, CallbackInfo callback) { if (!enabled) hangul$clear(); }

	@WrapMethod(method = "render")
	private void hangul$renderInlinePreedit(@Coerce GuiGraphicsExtractor graphics, int mouseX, int mouseY,
		float delta, Operation<Void> original) {
		if (!hangul$active()) {
			original.call(graphics, mouseX, mouseY, delta);
			return;
		}
		InlinePreedit.Visual visual = hangul$visual();
		String savedText = text;
		int savedCursor = cursorPos, savedHighlight = highlightPos, savedFirst = firstCharacterIndex;
		try {
			Font font = ((ReiWidgetAccessor) this).hangul$getReiFont();
			text = visual.value();
			cursorPos = highlightPos = visual.cursor();
			int width = Math.max(0, bounds.width - (hasBorder ? 8 : 0));
			firstCharacterIndex = Math.max(0, Math.min(firstCharacterIndex, cursorPos));
			firstCharacterIndex = Math.max(firstCharacterIndex,
				cursorPos - font.plainSubstrByWidth(text.substring(0, cursorPos), width, true).length());
			original.call(graphics, mouseX, mouseY, delta);
			if (graphics != null) hangul$underline(graphics, visual, width);
		} finally {
			text = savedText;
			cursorPos = savedCursor;
			highlightPos = savedHighlight;
			firstCharacterIndex = savedFirst;
		}
	}

	@Unique @Override public String hangul$getVisualValue() { return hangul$active() ? hangul$visual().value() : text; }
	@Unique private boolean hangul$valid() { return hangul$start >= 0 && hangul$end >= hangul$start && hangul$end <= text.length(); }
	@Unique private boolean hangul$active() { return hangul$preedit != null && focused && visible && editable && hangul$valid(); }
	@Unique private InlinePreedit.Visual hangul$visual() {
		return hangul$cache.get(text, hangul$start, hangul$end, hangul$preedit.fullText(), hangul$preedit.caretPosition());
	}
	@Unique private void hangul$clear() { hangul$preedit = null; hangul$cache.clear(); hangul$start = hangul$end = 0; }
	@Unique private void hangul$underline(GuiGraphicsExtractor graphics, InlinePreedit.Visual visual, int width) {
		Font font = ((ReiWidgetAccessor) this).hangul$getReiFont();
		int x = bounds.x + (hasBorder ? 4 : 0), y = bounds.y + (hasBorder ? (bounds.height - 8) / 2 : 0);
		int caretX = x + Math.min(width, font.width(text.substring(firstCharacterIndex, cursorPos)));
		NativeImeSupport.positionCandidateWindow(caretX, y, 10);
		int start = Math.max(firstCharacterIndex, visual.compositionStart());
		int end = Math.max(start, visual.compositionEnd());
		int left = x + Math.min(width, font.width(text.substring(firstCharacterIndex, start)));
		int right = x + Math.min(width, font.width(text.substring(firstCharacterIndex, end)));
		if (right > left) graphics.fill(left, y + 9, right, y + 10, 0xFFFFFFFF);
	}
}
