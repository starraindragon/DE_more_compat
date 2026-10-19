package com.xing_yu_dragon.demorecompat.mixin;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin 门禁：每个 Mixin 只在它依赖的模组真正存在时才应用。
 *
 * <p>两个 Mixin 分别门禁：
 * <ul>
 *   <li>{@code EnergizingOrbTileRecipeResetMixin} → 需要 {@code powah}（目标是 Powah 的类）；</li>
 *   <li>{@code DisableBuiltinAvaritiaAdapterMixin} → 需要 {@code avaritia}（目标类本身是
 *       data_energistics 的类、恒存在，但只有当 Avaritia 在场时官方适配器才会注册，才需要去停用它）。</li>
 * </ul>
 *
 * <p>关键：判断模组是否存在必须用 {@link LoadingModList}（模组发现阶段就已就绪），
 * **不能**用 {@link ModList}——后者在模组构建完成后才可用，而 {@code shouldApplyMixin}
 * 可能在 {@code ModList} 就绪前就被调用，此时 {@code ModList.get()} 返回 null，若据此判
 * "未加载"并跳过注入，就会导致官方 Avaritia 适配器没被停用、封包合成卡死（历史教训）。
 */
public final class DeMoreCompatMixinPlugin implements IMixinConfigPlugin {

    private static final String POWAH_MIXIN = ".EnergizingOrbTileRecipeResetMixin";
    private static final String AVARITIA_MIXIN = ".DisableBuiltinAvaritiaAdapterMixin";

    private static final Logger LOGGER = LoggerFactory.getLogger("de_more_compat");

    @Override
    public void onLoad(String mixinPackage) {
        LOGGER.info("[Mixin门禁] de_more_compat mixin 插件已加载 (Powah/Avaritia 按模组存在性门禁)");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(AVARITIA_MIXIN)) {
            boolean apply = isModLoaded("avaritia");
            LOGGER.info("[Mixin门禁] DisableBuiltinAvaritiaAdapterMixin 应用={} (avaritia {})",
                    apply, apply ? "已加载" : "未加载");
            return apply;
        }
        if (mixinClassName.endsWith(POWAH_MIXIN)) {
            boolean apply = isModLoaded("powah");
            LOGGER.info("[Mixin门禁] EnergizingOrbTileRecipeResetMixin 应用={} (powah {})",
                    apply, apply ? "已加载" : "未加载");
            return apply;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // 不做目标合并
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // 无需处理
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // 无需处理
    }

    /**
     * 用最早期可用的来源判断模组是否加载：
     * 优先 {@link LoadingModList}（模组发现阶段即就绪），再兜底 {@link ModList}。
     */
    private static boolean isModLoaded(String modId) {
        try {
            LoadingModList loading = LoadingModList.get();
            if (loading != null && loading.getModFileById(modId) != null) {
                return true;
            }
        } catch (Throwable ignored) {
            // 忽略：极端早期可能抛异常
        }
        try {
            ModList modList = ModList.get();
            if (modList != null && modList.isLoaded(modId)) {
                return true;
            }
        } catch (Throwable ignored) {
            // 忽略
        }
        return false;
    }
}
