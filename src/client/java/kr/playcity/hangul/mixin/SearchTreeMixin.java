package kr.playcity.hangul.mixin;

import kr.playcity.hangul.KoreanSearch;
import net.minecraft.client.searchtree.SearchTree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Function;
import java.util.stream.Stream;

/** Adds Korean-aware terms once when vanilla name search trees are built. */
@Mixin(SearchTree.class)
public interface SearchTreeMixin {
	@ModifyVariable(method = "plainText", at = @At("HEAD"), argsOnly = true)
	private static <T> Function<T, Stream<String>> hangul$addInitialConsonantIndex(
		final Function<T, Stream<String>> vanillaExtractor
	) {
		return value -> vanillaExtractor.apply(value).flatMap(KoreanSearch::indexTerms);
	}
}
