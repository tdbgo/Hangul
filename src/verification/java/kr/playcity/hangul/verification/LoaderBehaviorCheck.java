package kr.playcity.hangul.verification;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarFile;
import java.util.HashSet;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

/** Loads and verifies the actual transformed game classes before native window initialization. */
public final class LoaderBehaviorCheck {
	private LoaderBehaviorCheck() {}

	public static void runAndExit() {
		try (var mod = new JarFile(System.getProperty("hangul.verification.jar"))) {
			if (Boolean.getBoolean("hangul.verification.thirdPartyOnly")) {
				// Isolate an upstream Mixin failure: Hangul is not present in this loader profile.
				Class.forName("net.minecraft.client.Minecraft", false, LoaderBehaviorCheck.class.getClassLoader()).getDeclaredMethods();
				Class.forName("me.shedaniel.rei.impl.client.gui.widget.basewidgets.TextFieldWidget", false,
					LoaderBehaviorCheck.class.getClassLoader()).getDeclaredMethods();
				System.out.println("THIRD_PARTY_SMOKE_OK (no Hangul loaded)");
				writeReceipt(0, 0, "control");
				System.exit(0);
			}
			com.google.gson.JsonObject config;
			try (var reader = new InputStreamReader(mod.getInputStream(mod.getJarEntry("hangul.client.mixins.json")), StandardCharsets.UTF_8)) {
				config = JsonParser.parseReader(reader).getAsJsonObject();
			}
			String prefix = config.get("package").getAsString().replace('.', '/') + "/";
			var targets = new HashSet<String>();
			int hooks = 0;
			for (var entry : config.getAsJsonArray("client")) {
				var node = new ClassNode();
				try (var input = mod.getInputStream(mod.getJarEntry(prefix + entry.getAsString() + ".class"))) {
					new ClassReader(input).accept(node, ClassReader.SKIP_CODE);
				}
				for (var annotation : annotations(node.visibleAnnotations, node.invisibleAnnotations)) {
					if (!annotation.desc.endsWith("/Mixin;")) {
						continue;
					}
					String target = ((Type) list(value(annotation, "value")).getFirst()).getClassName();
					Class<?> transformed = Class.forName(target, false, LoaderBehaviorCheck.class.getClassLoader());
					if (entry.getAsString().equals("AbstractSignEditScreenMixin")
						&& java.util.Arrays.stream(transformed.getDeclaredFields()).anyMatch(field -> field.getName().equals("signLines"))) {
						Class<?> line = Class.forName(target + "$SignLineEditBox", false, LoaderBehaviorCheck.class.getClassLoader());
						if (!"net.minecraft.client.gui.components.EditBox".equals(line.getSuperclass().getName()))
							throw new AssertionError("Unverified modern sign widget");
						continue;
					}
					var methods = transformed.getDeclaredMethods();
					var appliedGroups = new java.util.HashMap<String, Integer>();
					transformed.getDeclaredFields();
					targets.add(target);
					for (var method : node.methods) {
						for (var hook : annotations(method.visibleAnnotations, method.invisibleAnnotations)) {
							if (!(hook.desc.endsWith("/Inject;") || hook.desc.endsWith("/WrapMethod;")
								|| hook.desc.endsWith("/Redirect;") || hook.desc.endsWith("/Accessor;")
								|| hook.desc.endsWith("/ModifyVariable;")
								|| hook.desc.endsWith("/Invoker;"))) {
								continue;
							}
							boolean applied = java.util.Arrays.stream(methods).anyMatch(m -> m.getName().contains(method.name));
							var group = annotations(method.visibleAnnotations, method.invisibleAnnotations).stream()
								.filter(a -> a.desc.endsWith("/Group;")).findFirst().orElse(null);
							if (group != null) {
								String name = (String) value(group, "name");
								if (!Integer.valueOf(1).equals(value(group, "min")) || !Integer.valueOf(1).equals(value(group, "max")))
									throw new AssertionError("Injection group must require exactly one hook: " + name);
								// Redirect handler stubs may be merged even when their invocation is absent.
								// Select by the original runtime call site, then require its installed handler.
								boolean selected = hook.desc.endsWith("/Redirect;") ? hasInvocation(transformed, hook)
									: list(value(hook, "method")).stream().anyMatch(selector -> java.util.Arrays.stream(methods)
										.anyMatch(m -> selector.equals(m.getName()) || selector.equals(m.getName() + Type.getMethodDescriptor(m))));
								appliedGroups.merge(name, selected && applied ? 1 : 0, Integer::sum);
								if (!selected || !applied) continue;
							} else if (Integer.valueOf(0).equals(value(hook, "require"))) {
								boolean hasTarget = list(value(hook, "method")).stream().anyMatch(selector ->
									java.util.Arrays.stream(methods).anyMatch(m -> selector.equals(m.getName())
										|| selector.equals(m.getName() + Type.getMethodDescriptor(m))));
								if (!hasTarget) {
									continue;
								}
							}
							if (!applied) {
								throw new AssertionError("Missing transformed hook: " + target + " / " + method.name);
							}
							hooks++;
						}
					}
					for (var group : appliedGroups.entrySet()) {
						if (group.getValue() != 1) throw new AssertionError("Expected one applied hook in " + group.getKey() + ": " + group.getValue());
					}
				}
			}
			if (targets.isEmpty() || hooks == 0) {
				throw new AssertionError("No Mixin targets checked");
			}
			System.out.println("MIXIN_APPLICATION_OK targets=" + targets.size() + " hooks=" + hooks);
			InputBackendCheck.run();
			ImeWidthBehaviorCheck.run();
			WidgetBehaviorCheck.run();
			CommandBehaviorCheck.run();
			if (Boolean.getBoolean("hangul.verification.rei")) OptionalWidgetBehaviorCheck.run();
			if (mod.getJarEntry("META-INF/mods.toml") != null) {
				verifyForgePackMetadata(mod);
			}
			writeReceipt(targets.size(), hooks, "candidate");
			System.exit(0);
		} catch (Throwable failure) {
			failure.printStackTrace();
			System.exit(1);
		}
	}

	private static void writeReceipt(int targets, int hooks, String scope) throws Exception {
		String result = System.getProperty("hangul.verification.result");
		if (result == null) return;
		byte[] candidate = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(System.getProperty("hangul.verification.jar")));
		String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(candidate));
		java.nio.file.Files.writeString(java.nio.file.Path.of(result),
			"PASS sha256=" + hash + " targets=" + targets + " hooks=" + hooks + " scope=" + scope + "\n");
	}

	private static boolean hasInvocation(Class<?> type, org.objectweb.asm.tree.AnnotationNode hook) throws java.io.IOException {
		var source = new ClassNode();
		try (var input = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
			if (input == null) throw new java.io.IOException("Missing runtime class resource " + type.getName());
			new ClassReader(input).accept(source, 0);
		}
		for (Object site : list(value(hook, "at"))) {
			String target = (String) value((org.objectweb.asm.tree.AnnotationNode) site, "target");
			for (var method : source.methods) {
				if (list(value(hook, "method")).stream().noneMatch(selector -> selector.equals(method.name)
					|| selector.equals(method.name + method.desc))) continue;
				for (var instruction : method.instructions) {
					if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
						&& ("L" + call.owner + ";" + call.name + call.desc).equals(target)) return true;
				}
			}
		}
		return false;
	}
	private static void verifyForgePackMetadata(JarFile mod) throws java.io.IOException {
		var entry = mod.getJarEntry("pack.mcmeta");
		if (entry == null) throw new AssertionError("Forge artifact is missing pack.mcmeta");
		try (var reader = new InputStreamReader(mod.getInputStream(entry), StandardCharsets.UTF_8)) {
			var pack = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("pack");
			if (!"Hangul resources".equals(pack.get("description").getAsString())) {
				throw new AssertionError("Invalid Forge pack description");
			}
			// Forge reads the same metadata as both client resources and server data.
			for (var type : net.minecraft.server.packs.PackType.values()) {
				var formats = net.minecraft.server.packs.metadata.pack.PackFormat.packCodec(type)
					.codec().parse(com.mojang.serialization.JsonOps.INSTANCE, pack).getOrThrow();
				var current = net.minecraft.DetectedVersion.BUILT_IN.packVersion(type);
				if (!formats.isValueInRange(current)) {
					throw new AssertionError("Unsupported Forge pack format: " + type + " " + current);
				}
			}
		}
		System.out.println("FORGE_PACK_METADATA_OK client_resources=true server_data=true");
	}

	static java.util.List<org.objectweb.asm.tree.AnnotationNode> annotations(
			java.util.List<org.objectweb.asm.tree.AnnotationNode> visible,
			java.util.List<org.objectweb.asm.tree.AnnotationNode> invisible) {
		var all = new java.util.ArrayList<org.objectweb.asm.tree.AnnotationNode>();
		if (visible != null) all.addAll(visible);
		if (invisible != null) all.addAll(invisible);
		return all;
	}
	static Object value(org.objectweb.asm.tree.AnnotationNode annotation, String name) {
		if (annotation.values != null) {
			for (int i = 0; i < annotation.values.size(); i += 2) {
				if (name.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
			}
		}
		return null;
	}
	static java.util.List<?> list(Object value) {
		return value instanceof java.util.List<?> list ? list : value == null ? java.util.List.of() : java.util.List.of(value);
	}
}
