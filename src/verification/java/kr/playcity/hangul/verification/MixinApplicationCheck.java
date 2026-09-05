package kr.playcity.hangul.verification;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.util.HashSet;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

/** Loads and verifies the actual transformed game classes before native window initialization. */
public final class MixinApplicationCheck implements PreLaunchEntrypoint {
	@Override
	public void onPreLaunch() {
		try {
			var mod = FabricLoader.getInstance().getModContainer("hangul").orElseThrow();
			var config = JsonParser.parseString(Files.readString(mod.findPath("hangul.client.mixins.json").orElseThrow()))
				.getAsJsonObject();
			String prefix = config.get("package").getAsString().replace('.', '/') + "/";
			var targets = new HashSet<String>();
			int hooks = 0;
			for (var entry : config.getAsJsonArray("client")) {
				var node = new ClassNode();
				try (var input = Files.newInputStream(mod.findPath(prefix + entry.getAsString() + ".class").orElseThrow())) {
					new ClassReader(input).accept(node, ClassReader.SKIP_CODE);
				}
				for (var annotation : BinaryCompatibilityCheck.annotations(node.visibleAnnotations, node.invisibleAnnotations)) {
					if (!annotation.desc.endsWith("/Mixin;")) {
						continue;
					}
					String target = ((Type) BinaryCompatibilityCheck.list(BinaryCompatibilityCheck.value(annotation, "value")).getFirst()).getClassName();
					Class<?> transformed = Class.forName(target, false, getClass().getClassLoader());
					var methods = transformed.getDeclaredMethods();
					transformed.getDeclaredFields();
					targets.add(target);
					for (var method : node.methods) {
						for (var hook : BinaryCompatibilityCheck.annotations(method.visibleAnnotations, method.invisibleAnnotations)) {
							if (!(hook.desc.endsWith("/Inject;") || hook.desc.endsWith("/WrapMethod;")
								|| hook.desc.endsWith("/Redirect;") || hook.desc.endsWith("/Accessor;")
								|| hook.desc.endsWith("/ModifyVariable;")
								|| hook.desc.endsWith("/Invoker;"))) {
								continue;
							}
							if (Integer.valueOf(0).equals(BinaryCompatibilityCheck.value(hook, "require"))) {
								boolean hasTarget = java.util.Arrays.stream(methods).anyMatch(m -> m.getName().equals("setScreen"));
								if (!hasTarget) {
									continue;
								}
							}
							boolean applied = java.util.Arrays.stream(methods).anyMatch(m -> m.getName().contains(method.name));
							if (!applied) {
								throw new AssertionError("Missing transformed hook: " + target + " / " + method.name);
							}
							hooks++;
						}
					}
				}
			}
			if (targets.isEmpty() || hooks == 0) {
				throw new AssertionError("No Mixin targets checked");
			}
			System.out.println("MIXIN_APPLICATION_OK targets=" + targets.size() + " hooks=" + hooks);
			WidgetBehaviorCheck.run();
			System.exit(0);
		} catch (Throwable failure) {
			failure.printStackTrace();
			System.exit(1);
		}
	}
}
