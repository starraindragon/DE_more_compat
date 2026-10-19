package com.xing_yu_dragon.demorecompat;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod("de_more_compat")
public class DePowahCompat {

    public DePowahCompat(IEventBus modEventBus) {
        // 与 data_energistics 官方一致：Avaritia 工作台的封包驱动完全由
        // packagedCrafting 适配器（AvaritiaTierCraftingAdapter，见 AvaritiaCompatPlugin）
        // 通过 recognizes/prepare/advance 直接操作方块实体完成，不注册任何
        // ME_STORAGE / ItemHandler 能力——把合成格暴露成普通外部存储反而会让
        // 封包机或其它设备误推料、污染合成格。
    }
}
