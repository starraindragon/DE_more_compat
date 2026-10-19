package com.xing_yu_dragon.demorecompat;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;

/**
 * data_energistics 插件入口。
 * data_energistics 通过 NeoForge 注解扫描发现带 @DataEnergisticsEntrypoint 的类，
 * 实例化后在 register() 里向 packagedCrafting 注册适配器。
 */
@DataEnergisticsEntrypoint(requiredMods = {"powah"})
public class PowahCompatPlugin implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.packagedCrafting().register(new PowahEnergizingAdapter());
        org.slf4j.LoggerFactory.getLogger("de_more_compat")
                .info("[Powah适配] 已在 data_energistics packagedCrafting 注册 Powah 充能台适配器");
    }
}
