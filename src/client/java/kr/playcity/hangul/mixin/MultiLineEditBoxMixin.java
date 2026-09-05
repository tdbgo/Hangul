package kr.playcity.hangul.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.List;
import kr.playcity.hangul.FontLayoutRevision;
import kr.playcity.hangul.LayoutCache;
import kr.playcity.hangul.PreeditCache;
import kr.playcity.hangul.InlinePreedit;
import kr.playcity.hangul.NativeImeSupport;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.PreeditEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Renders OS IME composition inside book pages and every other multiline editor. */
@Mixin(MultiLineEditBox.class)
public abstract class MultiLineEditBoxMixin {
	@Shadow @Final private MultilineTextField textField;
	@Shadow private IMEPreeditOverlay preeditOverlay;

	@Unique private PreeditEvent hangul$preedit;
	@Unique private int hangul$preeditStart;
	@Unique private int hangul$preeditEnd;
	@Unique private final PreeditCache hangul$visualCache = new PreeditCache();
	@Unique private final LayoutCache<List<?>> hangul$layoutCache = new LayoutCache<>();

	@Inject(method = "preeditUpdated", at = @At("HEAD"))
	private void hangul$onPreedit(
		final PreeditEvent event,
		final CallbackInfoReturnable<Boolean> callback
	) {
		if (event == null || event.fullText().isEmpty()) {
			hangul$clearPreedit();
			return;
		}

		if (hangul$preedit == null || !hangul$hasValidRange()) {
			MultilineTextFieldAccessor fields = (MultilineTextFieldAccessor) textField;
			hangul$preeditStart = Math.min(
				fields.hangul$getCursor(),
				fields.hangul$getSelectCursor()
			);
			hangul$preeditEnd = Math.max(
				fields.hangul$getCursor(),
				fields.hangul$getSelectCursor()
			);
		}
		hangul$preedit = event;
	}

	@Inject(method = "charTyped", at = @At("HEAD"))
	private void hangul$onCharacter(
		final CharacterEvent event,
		final CallbackInfoReturnable<Boolean> callback
	) {
		hangul$clearPreedit();
	}

	@Inject(method = "setFocused", at = @At("HEAD"))
	private void hangul$onFocusChanged(final boolean focused, final CallbackInfo callback) {
		if (!focused) {
			hangul$clearPreedit();
		}
	}

	@Inject(method = "setValue(Ljava/lang/String;Z)V", at = @At("HEAD"))
	private void hangul$onValueReplaced(
		final String newValue,
		final boolean allowOverflow,
		final CallbackInfo callback
	) {
		hangul$clearPreedit();
	}

	@WrapMethod(method = "extractContents")
	private void hangul$renderInlinePreedit(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float partialTick,
		final Operation<Void> original
	) {
		if (hangul$preedit == null || !hangul$hasValidRange()) {
			original.call(graphics, mouseX, mouseY, partialTick);
			return;
		}

		MultilineTextFieldAccessor fields = (MultilineTextFieldAccessor) textField;
		InlinePreedit.Visual visual = hangul$visualCache.get(
			textField.value(),
			hangul$preeditStart,
			hangul$preeditEnd,
			hangul$preedit.fullText(),
			hangul$preedit.caretPosition()
		);

		String savedValue = fields.hangul$getValue();
		int savedCursor = fields.hangul$getCursor();
		int savedSelection = fields.hangul$getSelectCursor();
		List<?> savedLines = fields.hangul$getDisplayLines();
		int width = fields.hangul$getWidth();
		long revision = FontLayoutRevision.current();
		try {
			fields.hangul$setValue(visual.value());
			fields.hangul$setCursor(visual.cursor());
			fields.hangul$setSelectCursor(visual.cursor());
			List<?> lines = hangul$layoutCache.get(visual.value(), width, revision);
			if (lines == null) {
				lines = new ArrayList<>();
				fields.hangul$setDisplayLines(lines);
				fields.hangul$reflowDisplayLines();
				hangul$layoutCache.put(visual.value(), width, revision, lines);
			} else {
				fields.hangul$setDisplayLines(lines);
			}
			original.call(graphics, mouseX, mouseY, partialTick);
		} finally {
			fields.hangul$setValue(savedValue);
			fields.hangul$setCursor(savedCursor);
			fields.hangul$setSelectCursor(savedSelection);
			fields.hangul$setDisplayLines(savedLines);
		}
	}

	@Redirect(
		method = "extractContents",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setPreeditOverlay(Lnet/minecraft/client/gui/components/Renderable;)V"
		)
	)
	private void hangul$positionCandidateWithoutPopup(
		final GuiGraphicsExtractor graphics,
		final Renderable renderable
	) {
		IMEPreeditOverlayAccessor overlay = (IMEPreeditOverlayAccessor) renderable;
		NativeImeSupport.positionCandidateWindow(
			overlay.hangul$getInputLeft(),
			overlay.hangul$getInputTop(),
			overlay.hangul$getInputHeight()
		);
	}

	@Unique
	private boolean hangul$hasValidRange() {
		return hangul$preeditStart >= 0
			&& hangul$preeditStart <= hangul$preeditEnd
			&& hangul$preeditEnd <= textField.value().length();
	}

	@Unique
	private void hangul$clearPreedit() {
		hangul$visualCache.clear();
		hangul$layoutCache.clear();
		hangul$preedit = null;
		hangul$preeditStart = 0;
		hangul$preeditEnd = 0;
		preeditOverlay = null;
	}
}
