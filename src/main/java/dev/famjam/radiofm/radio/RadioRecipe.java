package dev.famjam.radiofm.radio;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.CraftingInput;

public class RadioRecipe extends ShapedRecipe {

    public RadioRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern) {
        super(group, category, pattern, RadioItem.createDefault());
    }

    /**
     * RU: результат из конструктора строится до чтения конфига и остаётся без
     *     подсказки о слышимости; здесь конфиг уже есть, поэтому собираем заново
     * US: the constructor's result is built before the config is read and carries no
     *     hint about the range; here it exists, so build the item again
     */
    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return RadioItem.createDefault();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RadioRecipeSerializer.INSTANCE;
    }
}