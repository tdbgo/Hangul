package kr.playcity.hangul;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/** Recognizes the legacy sign editor and the newer EditBox-based sign layout. */
public final class SignCompatibilityPlugin implements IMixinConfigPlugin {
	@Override public void onLoad(String mixinPackage) {}
	@Override public String getRefMapperConfig() { return null; }
	@Override public List<String> getMixins() { return null; }
	@Override public void acceptTargets(Set<String> ownTargets, Set<String> otherTargets) {}
	@Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
	@Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}

	@Override
	public boolean shouldApplyMixin(String target, String mixin) {
		if (!mixin.endsWith(".AbstractSignEditScreenMixin")) return true;
		try {
			ClassNode screen = MixinService.getService().getBytecodeProvider().getClassNode(target, true);
			boolean legacy = screen.fields.stream().anyMatch(field -> field.name.equals("signField")
				&& field.desc.equals("Lnet/minecraft/client/gui/font/TextFieldHelper;"));
			boolean modern = screen.fields.stream().anyMatch(field -> field.name.equals("signLines")
				&& field.desc.equals("[Lnet/minecraft/client/gui/screens/inventory/AbstractSignEditScreen$SignLineEditBox;"));
			if (legacy == modern) throw new IllegalStateException("Unrecognized sign input layout");
			if (modern) {
				ClassNode line = MixinService.getService().getBytecodeProvider().getClassNode(target + "$SignLineEditBox", true);
				if (!"net/minecraft/client/gui/components/EditBox".equals(line.superName))
					throw new IllegalStateException("Sign lines no longer use the verified EditBox input path");
			}
			return legacy;
		} catch (ClassNotFoundException | java.io.IOException exception) {
			throw new IllegalStateException("Could not verify sign input layout", exception);
		}
	}
}
