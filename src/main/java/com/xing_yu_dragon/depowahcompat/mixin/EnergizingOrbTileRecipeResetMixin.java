package com.xing_yu_dragon.depowahcompat.mixin;

import net.minecraft.world.item.crafting.RecipeHolder;
import owmii.powah.block.energizing.EnergizingOrbTile;
import owmii.powah.block.energizing.EnergizingRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复 Powah 充能台的一个 Bug：
 * {@code checkRecipe()} 在库存无配方匹配时只重置能量 buffer，从不把 {@code this.recipe} 置空。
 * 导致封包合成收料清空充能台后，RF 持续灌入时 fillEnergy 看到 recipe 仍非空，
 * 会反复把产物放回槽 0（无限复制）。
 *
 * 这里在 checkRecipe 返回时，若 containRecipe 为 false（当前无匹配配方），
 * 就把 recipe 置空，让充能台真正停止产出。
 * 注：仅使用本类字段（recipe/containRecipe），不 @Shadow 继承来的 getInventory()，避免加载期报错。
 */
@Mixin(EnergizingOrbTile.class)
public abstract class EnergizingOrbTileRecipeResetMixin {

    @Shadow
    private boolean containRecipe;

    @Shadow
    private RecipeHolder<EnergizingRecipe> recipe;

    @Inject(method = "checkRecipe", at = @At("RETURN"))
    private void de_powah_resetRecipeWhenEmpty(CallbackInfo ci) {
        if (!this.containRecipe) {
            this.recipe = null;
        }
    }
}
