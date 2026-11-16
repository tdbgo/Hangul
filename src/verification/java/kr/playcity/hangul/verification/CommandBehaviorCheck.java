package kr.playcity.hangul.verification;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Invokes the installed command adapter using real Brigadier and runtime result objects. */
final class CommandBehaviorCheck {
	static void run() throws Exception {
		Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
		var unsafeField = unsafeType.getDeclaredField("theUnsafe");
		unsafeField.setAccessible(true);
		Object widget = unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafeField.get(null), CommandSuggestions.class);
		EditBox input = new EditBox(new WidgetBehaviorCheck.CountingFont(), 120, 20, Component.empty());
		WidgetBehaviorCheck.setField(widget, "input", input);
		boolean modern;
		try { Class.forName("net.minecraft.commands.SuggestionBox$Result", false, CommandBehaviorCheck.class.getClassLoader()); modern = true; }
		catch (ClassNotFoundException legacy) { modern = false; }
		if (modern) {
			net.minecraft.SharedConstants.tryDetectVersion();
			net.minecraft.server.Bootstrap.bootStrap();
		}
		String hookName = modern ? "hangul$recoverResultCommand" : "hangul$recoverKoreanLayoutCommand";
		Method hook = Arrays.stream(CommandSuggestions.class.getDeclaredMethods())
			.filter(method -> method.getName().contains(hookName) && method.getReturnType() == CompletableFuture.class).findFirst().orElseThrow();
		hook.setAccessible(true);
		CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
		dispatcher.register(LiteralArgumentBuilder.literal("gamemode"));
		dispatcher.register(LiteralArgumentBuilder.<Object>literal("gated").requires(source -> false));
		dispatcher.register(LiteralArgumentBuilder.<Object>literal("debug").then(LiteralArgumentBuilder.literal("message")));
		String keyboard = (String) Class.forName("kr.playcity.hangul.KoreanSearch").getMethod("toDubeolsikHangul", String.class).invoke(null, "game");
		for (String text : List.of("/" + keyboard, "/디버그", "/debug 메시지", "/ga")) {
			input.setValue(text);
			StringReader reader = new StringReader(text);
			reader.skip();
			ParseResults<Object> parse = dispatcher.parse(reader, null);
			Suggestions vanilla = dispatcher.getCompletionSuggestions(parse, text.length()).join();
			Object future;
			if (modern) {
				Class<?> owner = Class.forName("net.minecraft.commands.SuggestionBox");
				var ownerConstructor = owner.getDeclaredConstructor(CommandDispatcher.class);
				ownerConstructor.setAccessible(true);
				Object box = ownerConstructor.newInstance(dispatcher);
				Class<?> result = Class.forName("net.minecraft.commands.SuggestionBox$Result");
				var constructor = result.getDeclaredConstructor(owner, ParseResults.class);
				constructor.setAccessible(true);
				future = hook.invoke(widget, constructor.newInstance(box, parse), text.length());
			} else {
				future = hook.invoke(widget, dispatcher, parse, text.length());
			}
			Suggestions suggestions = (Suggestions) ((CompletableFuture<?>) future).join();
			String expected = text.equals("/디버그") ? "debug" : text.startsWith("/debug ") ? "message" : "gamemode";
			if (suggestions.getList().stream().noneMatch(item -> item.getText().equals(expected)))
				throw new AssertionError("Installed command adapter failed to recover " + expected);
			if (!text.equals("/ga") && suggestions.getList().stream().anyMatch(item -> item.getText().equals("gated")))
				throw new AssertionError("Command recovery bypassed permissions");
			if (text.equals("/ga") && !suggestions.equals(vanilla)) throw new AssertionError("Latin suggestions changed");
		}
		System.out.println("COMMAND_BEHAVIOR_OK backend=" + (modern ? "result" : "dispatcher") + " layout, phonetics, nested literals, permissions, and Latin passthrough");
	}
}
