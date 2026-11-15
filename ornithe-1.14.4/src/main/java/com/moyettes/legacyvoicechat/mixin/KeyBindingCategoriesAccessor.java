package com.moyettes.legacyvoicechat.mixin;

import net.minecraft.client.options.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** The game's own category order, for LVC's keybind category (was OSL keybinds' internal accessor, gone in gen2). */
@Mixin(KeyBinding.class)
public interface KeyBindingCategoriesAccessor {

	@Accessor("CATEGORY_SORT_ORDER")
	static Map<String, Integer> lvc$getCategorySortOrder() {
		throw new AssertionError();
	}
}
