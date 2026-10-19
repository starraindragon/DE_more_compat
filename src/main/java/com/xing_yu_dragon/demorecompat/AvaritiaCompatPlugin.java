package com.xing_yu_dragon.demorecompat;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import committee.nova.mods.avaritia.init.registry.enums.ModCraftTier;

/**
 * data_energistics 插件入口（Avaritia 部分）。
 *
 * <p>数位化封包样板供应器（DigitalPackagedPatternProvider）驱动相邻机器走的是
 * packagedCrafting 适配器链路（PackagedDispatchState：recognizes → prepareBatch →
 * prepare/advance），与 "craftingMachines 容量 / ICraftingMachine / ME_STORAGE 能力"
 * 完全无关。官方也是纯靠 packagedCrafting 注册，不做任何能力注册。
 *
 * <p>这里按官方 AvaritiaPlugin 的做法，为四档等级工作台各注册一个
 * {@link AvaritiaTierCraftingAdapter}（SCULK / NETHER / END / EXTREME）。
 * 内置官方适配器由 DisableBuiltinAvaritiaAdapterMixin 禁用。
 */
@DataEnergisticsEntrypoint(requiredMods = {"avaritia"})
public class AvaritiaCompatPlugin implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        var logger = org.slf4j.LoggerFactory.getLogger("de_more_compat");

        registry.packagedCrafting().register(new AvaritiaTierCraftingAdapter(ModCraftTier.SCULK));
        registry.packagedCrafting().register(new AvaritiaTierCraftingAdapter(ModCraftTier.NETHER));
        registry.packagedCrafting().register(new AvaritiaTierCraftingAdapter(ModCraftTier.END));
        registry.packagedCrafting().register(new AvaritiaTierCraftingAdapter(ModCraftTier.EXTREME));
        logger.info("[Avaritia适配] 已注册四档等级工作台 packaged 适配器 (sculk/nether/end/extreme)");
    }
}
