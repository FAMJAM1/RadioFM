package dev.famjam.radiofm.radio;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.CraftingBookCategory;

public class RadioRecipeSerializer implements RecipeSerializer<RadioRecipe> {

    public static final RadioRecipeSerializer INSTANCE = new RadioRecipeSerializer();

    private static final MapCodec<RadioRecipe> CODEC = ShapedRecipePattern.MAP_CODEC
            .xmap(
                    pattern -> new RadioRecipe("", CraftingBookCategory.MISC, pattern),
                    recipe -> recipe.pattern
            );

    private static final StreamCodec<RegistryFriendlyByteBuf, RadioRecipe> STREAM_CODEC =
            ShapedRecipePattern.STREAM_CODEC.map(
                    pattern -> new RadioRecipe("", CraftingBookCategory.MISC, pattern),
                    recipe -> recipe.pattern
            );

    @Override
    public MapCodec<RadioRecipe> codec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, RadioRecipe> streamCodec() {
        return STREAM_CODEC;
    }
}