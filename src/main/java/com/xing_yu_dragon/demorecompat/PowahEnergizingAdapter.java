package com.xing_yu_dragon.demorecompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import owmii.powah.block.energizing.EnergizingOrbTile;

import java.util.ArrayList;
import java.util.List;

/**
 * 让 data_energistics 的封包样板能驱动 Powah 充能台（Energizing Orb）执行 powah:energizing 充能配方。
 *
 * <p>工作机制（基于反编译确认）：
 * <ul>
 *   <li>{@code prepare}：把封包样板解析出的输入材料塞进充能台库存槽 1 起（槽 0 是产物槽），
 *       塞料后主动触发 {@code onSlotChanged} 让充能台重新匹配配方；</li>
 *   <li>充能：机器旁需有 RF 源（用户自行供电）。能量 buffer 灌满后 {@code fillEnergy()}
 *       会清空整个库存，并把配方结果固定放到槽 0；</li>
 *   <li>{@code advance}：轮询槽 0，一旦出现与期望产物匹配的物品，就交付产物并完成。</li>
 * </ul>
 *
 * <p>本适配器不负责供电——封包合成时请确保充能台旁有 RF 供给。
 *
 * <p>日志约定：{@code supportsRecipe}/{@code recognizes}/{@code advance} 都是每 tick 调用的热路径，
 * 只记录"每次合成一条"的成功日志；各类失败原因走 {@code debug}，避免刷屏。
 */
public class PowahEnergizingAdapter implements PackagedMachineAdapter {

    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("de_more_compat", "powah_energizing");
    private static final ObjectSet<ResourceLocation> RECIPE_TYPES =
            ObjectSet.of(ResourceLocation.fromNamespaceAndPath("powah", "energizing"));

    private static final Logger LOGGER = LoggerFactory.getLogger(PowahEnergizingAdapter.class);

    private static final String TAG_OUTPUT = "output";

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return RECIPE_TYPES;
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof EnergizingOrbTile;
    }

    @Override
    public boolean supportsRecipe(ServerLevel level, BlockPos pos,
                                  ResourceLocation recipeId, ResourceLocation machineKind) {
        return RECIPE_TYPES.contains(recipeId) && recognizes(level, pos);
    }

    @Override
    public boolean supportsReusableInputs() {
        // Powah 充能配方会消耗输入（充能完成后清空库存），不可复用
        return false;
    }

    @Override
    public CompoundTag prepare(ServerLevel level, BlockPos pos, Direction face,
                               ResourceLocation recipeId, IPatternDetails details, KeyCounter[] totals) {
        if (!(level.getBlockEntity(pos) instanceof EnergizingOrbTile orb)) {
            LOGGER.debug("[Powah适配] prepare: {} 处不是充能台, 拒绝", pos);
            return null;
        }

        // 解析输入：每个输入槽取第一个物品（Powah 充能台只接受物品）
        List<ItemStack> inputs = new ArrayList<>();
        for (KeyCounter counter : totals) {
            ItemStack stack = firstItem(counter);
            if (stack.isEmpty()) {
                LOGGER.debug("[Powah适配] prepare: 某输入槽为空, 拒绝");
                return null;
            }
            inputs.add(stack);
        }
        if (inputs.isEmpty()) {
            LOGGER.debug("[Powah适配] prepare: 无任何输入, 拒绝");
            return null;
        }

        // 充能台库存 7 槽；槽 0 是产物槽（充能完成后产物固定放槽 0），输入只能放槽 1..6
        if (inputs.size() > 6) {
            LOGGER.debug("[Powah适配] prepare: 输入 {} 种 > 6 槽上限, 拒绝", inputs.size());
            return null;
        }

        // 期望产物（取封包样板输出）
        ItemStack output = patternOutput(details);
        if (output.isEmpty()) {
            LOGGER.debug("[Powah适配] prepare: 样板无物品输出, 拒绝");
            return null;
        }

        // 充能台必须完全空闲才能接新作业：槽 0（输出槽）和输入槽都要空
        // （源码 canInsert 要求槽 0 为空，配方匹配也是针对整个库存）
        if (!orb.getInventory().getStackInSlot(0).isEmpty()) {
            LOGGER.debug("[Powah适配] prepare: 充能台槽0(输出)有残留 {}, 拒绝", orb.getInventory().getStackInSlot(0));
            return null;
        }
        for (int s = 1; s <= inputs.size(); s++) {
            if (!orb.getInventory().getStackInSlot(s).isEmpty()) {
                LOGGER.debug("[Powah适配] prepare: 充能台槽 {} 有残留, 拒绝", s);
                return null;
            }
        }

        // 塞材料到输入区（槽 1 起），槽 0 留给充能台产物；塞料后主动触发配方检查
        // （Powah 只在 onSlotChanged 时 checkRecipe，不靠每 tick 扫描）
        for (int s = 0; s < inputs.size(); s++) {
            orb.getInventory().setStackInSlot(s + 1, inputs.get(s));
            orb.onSlotChanged(s + 1);
        }
        LOGGER.info("[Powah适配] 开始充能: 配方={}, 材料={} 种, 期望产物={}", recipeId, inputs.size(), output);

        // 写进度 NBT：记录期望产物 + 输入清单，供 advance 比对/标记输入消耗
        CompoundTag tag = new CompoundTag();
        tag.putString("recipe", recipeId.toString());
        tag.put(TAG_OUTPUT, output.saveOptional(level.registryAccess()));
        tag.putInt("input_count", inputs.size());
        ListTag inputList = new ListTag();
        for (ItemStack in : inputs) {
            CompoundTag e = new CompoundTag();
            e.put("stack", in.saveOptional(level.registryAccess()));
            e.putInt("count", in.getCount());
            inputList.add(e);
        }
        tag.put("inputs", inputList);
        return tag;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        ServerLevel level = operation.level();
        if (!(level.getBlockEntity(operation.position()) instanceof EnergizingOrbTile orb)) {
            LOGGER.debug("[Powah适配] advance: {} 处不是充能台", operation.position());
            return false;
        }

        CompoundTag progress = operation.progress();
        HolderLookup.Provider lookup = level.registryAccess();
        ItemStack expected = ItemStack.parseOptional(lookup, progress.getCompound(TAG_OUTPUT));
        if (expected.isEmpty()) {
            return false;
        }

        // Powah 充能台产物固定放槽 0；检测槽 0 是否出现期望产物
        ItemStack stack0 = orb.getInventory().getStackInSlot(0);
        if (!stack0.isEmpty() && ItemStack.isSameItemSameComponents(stack0, expected)) {
            // 1. 归还产物给封包机（进入操作 outputs，由 data_energistics 自动入网）
            operation.returned(AEItemKey.of(stack0), stack0.getCount());
            // 2. 标记输入已消耗（Powah 充能会消耗材料；key 必须是 operation 记录里的输入，否则 overdraft）
            ListTag inputList = progress.getList("inputs", Tag.TAG_COMPOUND);
            for (int i = 0; i < inputList.size(); i++) {
                CompoundTag e = inputList.getCompound(i);
                ItemStack in = ItemStack.parseOptional(lookup, e.getCompound("stack"));
                if (!in.isEmpty()) {
                    operation.delivered(AEItemKey.of(in), e.getInt("count"));
                }
            }
            // 3. 清空充能台并停止产出（Mixin 会把 recipe 置空，充能台不再吐产物）
            orb.getInventory().clear();
            orb.onSlotChanged(0);
            operation.changed();
            operation.complete();
            LOGGER.info("[Powah适配] 充能完成: 产物={} x{}", stack0.getItem(), stack0.getCount());
            return true;
        }

        // 尚未充能完成，等待下一次轮询
        return false;
    }

    @Override
    public long batchCapacity(ServerLevel level, BlockPos pos, Direction face,
                              ResourceLocation recipeId, IPatternDetails details,
                              KeyCounter[] totals, long max) {
        // Powah 充能台一次只处理一批（单产物），这里固定返回 1
        return Math.min(1, max);
    }

    private static ItemStack firstItem(KeyCounter counter) {
        if (counter == null) {
            return ItemStack.EMPTY;
        }
        for (AEKey key : counter.keySet()) {
            if (key instanceof AEItemKey itemKey) {
                long count = counter.get(key);
                return itemKey.toStack((int) Math.max(1, count));
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack patternOutput(IPatternDetails details) {
        for (GenericStack generic : details.getOutputs()) {
            if (generic != null && generic.what() instanceof AEItemKey itemKey) {
                long count = generic.amount();
                return itemKey.toStack((int) Math.max(1, count));
            }
        }
        return ItemStack.EMPTY;
    }
}
