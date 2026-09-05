package kr.playcity.hangul.verification;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.jar.JarFile;
import net.fabricmc.loader.impl.game.minecraft.McVersionLookup;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/** Descriptor checks complement, but do not replace, actual Mixin application and IME testing. */
public final class BinaryCompatibilityCheck {
	private final Map<String, ClassNode> classes = new HashMap<>();
	private final JarFile game;
	private int checked;
	private int failures;
	private int screenHooks;

	private BinaryCompatibilityCheck(final JarFile game) {
		this.game = game;
	}

	public static void main(final String[] args) throws Exception {
		if (args.length != 2) {
			throw new IllegalArgumentException("Expected candidate JAR and Minecraft client JAR");
		}
		try (var mod = new JarFile(args[0]); var game = new JarFile(args[1])) {
			var check = new BinaryCompatibilityCheck(game);
			check.verifyMetadata(mod, args[1]);
			var modClasses = new ArrayList<ClassNode>();
			for (var entry : mod.stream().filter(e -> e.getName().endsWith(".class")).toList()) {
				var node = new ClassNode();
				try (var input = mod.getInputStream(entry)) {
					new ClassReader(input).accept(node, 0);
				}
				modClasses.add(node);
				check.classes.put(node.name, node);
			}
			check.check(!modClasses.isEmpty(), "Candidate contains no classes");
			for (var node : modClasses) {
				check.verifyClass(node);
			}
			check.check(check.screenHooks == 1, "Expected one screen-lifecycle hook, found " + check.screenHooks);
			var keys = check.node("com/mojang/blaze3d/platform/InputConstants");
			var required = new ArrayList<>(List.of("PRESS", "KEY_BACKSPACE", "KEY_CAPSLOCK", "KEY_F6", "KEY_LSHIFT", "KEY_RSHIFT", "MOD_SUPER"));
			for (char key = 'A'; key <= 'Z'; key++) {
				required.add("KEY_" + key);
			}
			for (String key : required) {
				check.check(keys != null && keys.fields.stream().anyMatch(f -> f.name.equals(key)
					&& f.desc.equals("I") && (f.access & Opcodes.ACC_STATIC) != 0), "Input constant " + key);
			}
			System.out.println("BINARY_COMPATIBILITY checks=" + check.checked + " failures=" + check.failures);
			if (check.failures != 0) {
				throw new AssertionError("Candidate is not binary compatible");
			}
		}
	}

	private void verifyMetadata(final JarFile mod, final String gamePath) throws Exception {
		try (var input = new InputStreamReader(mod.getInputStream(mod.getJarEntry("fabric.mod.json")), StandardCharsets.UTF_8)) {
			var metadata = JsonParser.parseReader(input).getAsJsonObject();
			var predicates = new HashSet<String>();
			metadata.getAsJsonObject("depends").getAsJsonArray("minecraft").forEach(v -> predicates.add(v.getAsString()));
			var expected = new HashSet<String>();
			metadata.getAsJsonObject("custom").getAsJsonArray("hangul:tested_game_versions").forEach(v -> {
				String raw = v.getAsString();
				expected.add(McVersionLookup.normalizeVersion(raw, McVersionLookup.getRelease(raw)));
			});
			check(predicates.equals(expected), "Minecraft predicates differ from normalized tested versions");
			var gameVersion = McVersionLookup.getVersionExceptClassVersion(Path.of(gamePath));
			check(predicates.contains(gameVersion.getNormalized()), "Candidate does not permit Minecraft " + gameVersion.getRaw()
				+ " (Loader " + gameVersion.getNormalized() + ")");
		}
	}

	private void verifyClass(final ClassNode mod) throws Exception {
		String target = null;
		for (var annotation : annotations(mod.visibleAnnotations, mod.invisibleAnnotations)) {
			if (annotation.desc.endsWith("/Mixin;")) {
				target = ((Type) list(value(annotation, "value")).getFirst()).getInternalName();
			}
		}
		ClassNode gameTarget = target == null ? null : node(target);
		if (target != null) {
			check(gameTarget != null, "Mixin target " + target);
		}
		for (var field : mod.fields) {
			for (var annotation : annotations(field.visibleAnnotations, field.invisibleAnnotations)) {
				if (annotation.desc.endsWith("/Shadow;")) {
					check(member(target, field.name, field.desc, false), "Shadow " + target + "." + field.name);
				}
			}
		}
		for (var method : mod.methods) {
			for (var instruction : method.instructions) {
				if (instruction instanceof MethodInsnNode call && gameClass(call.owner)) {
					check(member(call.owner, call.name, call.desc, true), "Method " + call.owner + "." + call.name + call.desc);
				}
				if (instruction instanceof FieldInsnNode field && gameClass(field.owner)) {
					check(member(field.owner, field.name, field.desc, false), "Field " + field.owner + "." + field.name + field.desc);
				}
			}
			for (var annotation : annotations(method.visibleAnnotations, method.invisibleAnnotations)) {
				if (annotation.desc.endsWith("/Shadow;")) {
					check(member(target, method.name, method.desc, true), "Shadow method " + target + "." + method.name);
				}
				if (annotation.desc.endsWith("/Accessor;")) {
					Type type = Type.getMethodType(method.desc);
					String descriptor = type.getReturnType().getSort() == Type.VOID
						? type.getArgumentTypes()[0].getDescriptor() : type.getReturnType().getDescriptor();
					check(member(target, (String) value(annotation, "value"), descriptor, false), "Accessor " + target + "." + value(annotation, "value"));
				}
				if (annotation.desc.endsWith("/Invoker;")) {
					check(member(target, (String) value(annotation, "value"), method.desc, true), "Invoker " + target + "." + value(annotation, "value"));
				}
				for (var selectorValue : list(value(annotation, "method"))) {
					String selector = (String) selectorValue;
					List<MethodNode> methods = gameTarget == null ? List.of() : gameTarget.methods.stream()
						.filter(m -> selector.equals(m.name) || selector.equals(m.name + m.desc)).toList();
					boolean screenHook = method.name.equals("hangul$endFallbackSession")
						&& (mod.name.endsWith("/MinecraftScreenMixin") || mod.name.endsWith("/GuiScreenMixin"));
					if (screenHook) {
						screenHooks += methods.size();
					} else {
						check(!methods.isEmpty(), "Injection target " + target + "." + selector);
					}
					for (var injected : methods) {
						check((method.access & Opcodes.ACC_STATIC) == (injected.access & Opcodes.ACC_STATIC), "Injection staticness " + method.name);
						Type[] actual = Type.getArgumentTypes(method.desc), expected = Type.getArgumentTypes(injected.desc);
						if (annotation.desc.endsWith("/Inject;")) {
							check(actual.length == 1 || actual.length == expected.length + 1
								&& Arrays.equals(Arrays.copyOf(actual, actual.length - 1), expected), "Callback arguments " + method.name);
						}
						if (annotation.desc.endsWith("/WrapMethod;")) {
							check(actual.length == expected.length + 1 && Arrays.equals(Arrays.copyOf(actual, actual.length - 1), expected), "Wrapper arguments " + method.name);
						}
						for (var site : list(value(annotation, "at"))) {
							var at = (AnnotationNode) site;
							String invokeTarget = (String) value(at, "target");
							if (!"INVOKE".equals(value(at, "value")) || invokeTarget == null) {
								continue;
							}
							boolean found = false;
							for (var instruction : injected.instructions) {
								if (instruction instanceof MethodInsnNode call
									&& invokeTarget.equals("L" + call.owner + ";" + call.name + call.desc)) {
									found = true;
								}
							}
							check(found, "Invocation site " + target + "." + injected.name + " -> " + invokeTarget);
						}
					}
				}
			}
		}
	}

	private ClassNode node(final String name) throws Exception {
		if (name == null || classes.containsKey(name)) {
			return classes.get(name);
		}
		var entry = game.getJarEntry(name + ".class");
		try (var input = entry == null ? ClassLoader.getSystemResourceAsStream(name + ".class") : game.getInputStream(entry)) {
			if (input == null) {
				return null;
			}
			var node = new ClassNode();
			new ClassReader(input).accept(node, 0);
			classes.put(name, node);
			return node;
		}
	}

	private boolean member(final String owner, final String name, final String descriptor, final boolean method) throws Exception {
		var node = node(owner);
		if (node == null) {
			return false;
		}
		if (method && node.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(descriptor))
			|| !method && node.fields.stream().anyMatch(f -> f.name.equals(name) && f.desc.equals(descriptor))) {
			return true;
		}
		if (node.superName != null && member(node.superName, name, descriptor, method)) {
			return true;
		}
		for (String parent : node.interfaces) {
			if (member(parent, name, descriptor, method)) {
				return true;
			}
		}
		return false;
	}

	static List<AnnotationNode> annotations(final List<AnnotationNode> visible, final List<AnnotationNode> invisible) {
		var all = new ArrayList<AnnotationNode>();
		if (visible != null) all.addAll(visible);
		if (invisible != null) all.addAll(invisible);
		return all;
	}

	static Object value(final AnnotationNode annotation, final String name) {
		if (annotation.values != null) {
			for (int index = 0; index < annotation.values.size(); index += 2) {
				if (name.equals(annotation.values.get(index))) return annotation.values.get(index + 1);
			}
		}
		return null;
	}

	static List<?> list(final Object value) {
		return value instanceof List<?> list ? list : value == null ? List.of() : List.of(value);
	}

	private static boolean gameClass(final String owner) {
		return owner.startsWith("net/minecraft/") || owner.startsWith("com/mojang/blaze3d/");
	}

	private void check(final boolean condition, final String message) {
		checked++;
		if (!condition) {
			failures++;
			System.err.println("FAIL " + message);
		}
	}
}
