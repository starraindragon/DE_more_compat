package com.xing_yu_dragon.demorecompat.mixin;

import com.fish_dan_.data_energistics.integration.technology.avaritia.packaged.TierCraftingAdapter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * data_energistics 3.3.5 自带了一个 Avaritia 等级工作台适配器（TierCraftingAdapter），
 * 它先于我们注册并声明了相同的工作台物品 ID，封包样板系统会优先调用它，
 * 导致我们自写的 {@code AvaritiaWorkbenchAdapter} 永远不被选中（表现为“完全没变化”）。
 *
 * 这里直接让内置适配器的 recognizes 恒返回 false：
 * 封包样板遍历到它时认为它不识别 Avaritia 工作台，从而 fallback 到我们注册的适配器，
 * 使本 mod 的重写逻辑真正接管工作台驱动。
 *
 * 注意：TierCraftingAdapter 是 data_energistics 的类（非 Minecraft 类），故 remap = false。
 */
@Mixin(value = TierCraftingAdapter.class, remap = false)
public abstract class DisableBuiltinAvaritiaAdapterMixin {

    @Inject(method = "recognizes", at = @At("HEAD"), cancellable = true)
    private void de_more_compat$disableBuiltinAvaritia(ServerLevel level, BlockPos pos,
                                                       CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
