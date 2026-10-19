package com.xing_yu_dragon.demorecompat;

import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedCraftingGrid;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedIngredientAssignment;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedOutputMatching;

import appeng.api.crafting.IPatternDetails;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.crafting.pattern.EncodedProcessingPattern;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import com.mojang.authlib.GameProfile;
import committee.nova.mods.avaritia.api.common.crafting.ITierCraftingRecipe;
import committee.nova.mods.avaritia.api.common.crafting.TierInput;
import committee.nova.mods.avaritia.api.common.wrapper.ItemStackWrapper;
import committee.nova.mods.avaritia.common.crafting.recipe.ShapedTableCraftingRecipe;
import committee.nova.mods.avaritia.common.menu.TierCraftMenu;
import committee.nova.mods.avaritia.common.tile.TierCraftTile;
import committee.nova.mods.avaritia.init.registry.ModRecipeTypes;
import committee.nova.mods.avaritia.init.registry.enums.ModCraftTier;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 无尽贪婪：重生（Avaritia）四档等级工作台的封包适配器。
 *
 * <p>实现 data_energistics 的 {@link PackagedMachineAdapter}，让封包样板能驱动
 * SCULK / NETHER / END / EXTREME 四档等级工作台执行 {@code avaritia:crafting_table_recipe} 配方。
 *
 * <p>与官方 {@code integration.technology.avaritia.packaged.TierCraftingAdapter} 的区别（也是本模组存在的理由）：
 * <ul>
 *   <li>{@link #advance} 先把假玩家移动到工作台坐标：官方版用位于世界原点的假玩家打开菜单，
 *       而 {@code BaseTileMenu.stillValid} 要求玩家在 8 格内，校验失败会让菜单的产物槽恒为空，
 *       操作永远无法完成并长期占用工作台（表现为"第一次能合成、之后材料再也进不去"）。</li>
 *   <li>读取产物槽前显式调用 {@code canTakeCraftingResult()} 刷新懒计算的产物槽。</li>
 *   <li>反序列化用 {@code ItemStack.parseOptional}，避免空槽位每 tick 刷错误日志。</li>
 * </ul>
 *
 * <p>流程：{@code prepare} 校验配方并记录网格/余料 → {@code advance} 先塞料（delivered）→
 * 再开菜单取产物、核对余料 → {@code returned}/{@code delivered} → {@code complete}。
 * 内置官方适配器由 {@code DisableBuiltinAvaritiaAdapterMixin} 停用，避免同一工作台被两个适配器争抢。
 */
public final class AvaritiaTierCraftingAdapter implements PackagedMachineAdapter {

    private static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(
            "avaritia", "crafting_table_recipe");

    private static final Logger LOGGER = LoggerFactory.getLogger("de_more_compat");

    private final ModCraftTier tier;
    private final ResourceLocation id;

    public AvaritiaTierCraftingAdapter(ModCraftTier tier) {
        this.tier = tier;
        this.id = ResourceLocation.fromNamespaceAndPath("de_more_compat",
                "avaritia_" + tier.name().toLowerCase(Locale.ROOT) + "_crafting");
    }

    @Override
    public ResourceLocation id() {
        return this.id;
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(TYPE, ResourceLocation.fromNamespaceAndPath(
                "avaritia", tier.name().toLowerCase(Locale.ROOT) + "_craft"),
                ResourceLocation.fromNamespaceAndPath("avaritia", tier.name));
    }

    @Override
    public ObjectSet<ResourceLocation> workstationItemIds() {
        return ObjectSet.of(ResourceLocation.fromNamespaceAndPath(
                "avaritia", tier.name().toLowerCase(Locale.ROOT) + "_crafting_table"));
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return table(level, position) != null;
    }

    @Override
    public @Nullable ItemStack completeEncoding(ServerLevel level, ResourceLocation recipeId, ItemStack encodedPattern) {
        var processing = encodedPattern.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        var holder = level.getRecipeManager().byKey(recipeId);
        if (processing == null || holder.isEmpty() || !(holder.get().value() instanceof ITierCraftingRecipe recipe)) return null;
        var supplied = new KeyCounter();
        for (var input : processing.sparseInputs()) if (input != null) supplied.add(input.what(), input.amount());
        int recipeTier = recipe.getTier();
        if (recipeTier < 1 || recipeTier > 4) return null;
        int size = recipeTier * 2 + 1;
        int width = recipe instanceof ShapedTableCraftingRecipe shaped ? shaped.getWidth() : size;
        var grid = PackagedCraftingGrid.assign(new ObjectArrayList<>(recipe.getIngredients()), width, size, new KeyCounter[] { supplied });
        if (grid == null) return null;
        var nativeInput = TierInput.of(size, size, grid, recipeTier);
        if (!recipe.matches(nativeInput, level)) return null;
        var selected = level.getRecipeManager().getRecipeFor(ModRecipeTypes.CRAFTING_TABLE_RECIPE.get(), nativeInput, level);
        if (selected.isEmpty() || !selected.get().id().equals(recipeId)) return null;
        ItemStack produced = recipe.assemble(nativeInput, level.registryAccess());
        var returned = returnedStacks(grid, consumedSlots(nativeInput, size), recipe.getRemainingItems(nativeInput));
        var pattern = new AEProcessingPattern(AEItemKey.of(encodedPattern));
        if (!PackagedOutputMatching.matchesWithAdditionalReturns(pattern, produced, returned)) return null;
        var missing = new KeyCounter();
        for (ItemStack stack : returned) missing.add(AEItemKey.of(stack), stack.getCount());
        var additions = new KeyCounter();
        var returnKeys = new ObjectArrayList<AEItemKey>();
        for (var entry : missing) returnKeys.add((AEItemKey) entry.getKey());
        for (var key : returnKeys) {
            long original = missing.get(key);
            long low = 0;
            long high = original;
            while (low < high) {
                long removed = low + (high - low + 1) / 2;
                var remaining = new ObjectArrayList<ItemStack>();
                for (var resource : missing) {
                    long count = resource.getLongValue() - (resource.getKey().equals(key) ? removed : 0);
                    if (count > 0) remaining.add(((AEItemKey) resource.getKey()).toStack(Math.toIntExact(count)));
                }
                if (PackagedOutputMatching.matchesWithAdditionalReturns(pattern, produced, remaining)) low = removed;
                else high = removed - 1;
            }
            if (low > 0) {
                additions.add(key, low);
                missing.set(key, original - low);
            }
        }
        var outputs = new ObjectArrayList<@Nullable GenericStack>(processing.sparseOutputs());
        int tail = outputs.size();
        while (tail > 0 && outputs.get(tail - 1) == null) tail--;
        for (var entry : additions) {
            if (tail >= AEProcessingPattern.MAX_OUTPUT_SLOTS) return null;
            var output = new GenericStack(entry.getKey(), entry.getLongValue());
            if (tail < outputs.size()) outputs.set(tail, output);
            else outputs.add(output);
            tail++;
        }
        ItemStack copy = encodedPattern.copy();
        copy.set(AEComponents.ENCODED_PROCESSING_PATTERN, new EncodedProcessingPattern(processing.sparseInputs(), outputs));
        return copy;
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        TierCraftTile table = table(level, position);
        if (table == null || !empty(table.getInventory())) return null;
        // Avaritia 的菜单一次只产出一份结果，没有原子化的批量输入/输出；
        // 因此单个封包操作绝不能通过反复取结果来模拟批量。
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !(holder.get().value() instanceof ITierCraftingRecipe recipe) ||
                recipe.getType() != ModRecipeTypes.CRAFTING_TABLE_RECIPE.get() ||
                recipe.getTier() > tier.ordinal() + 1)
            return null;

        int size = tier.size;
        ObjectList<ItemStack> grid;
        if (recipe instanceof ShapedTableCraftingRecipe shaped) {
            grid = PackagedCraftingGrid.assign(new ObjectArrayList<>(recipe.getIngredients()),
                    shaped.getWidth(), size, inputs);
        } else {
            var assigned = PackagedIngredientAssignment.match(new ObjectArrayList<>(recipe.getIngredients()), inputs);
            if (assigned == null || assigned.size() > size * size) return null;
            grid = new ObjectArrayList<>(size * size);
            for (int index = 0; index < size * size; index++) {
                grid.add(index < assigned.size() ? assigned.get(index) : ItemStack.EMPTY);
            }
        }
        if (grid == null || grid.size() != size * size) return null;
        TierInput nativeInput = TierInput.of(size, size, grid, tier.ordinal() + 1);
        if (!recipe.matches(nativeInput, level)) return null;
        var chosen = level.getRecipeManager().getRecipeFor(ModRecipeTypes.CRAFTING_TABLE_RECIPE.get(), nativeInput, level);
        if (chosen.isEmpty() || !chosen.get().id().equals(recipeId)) return null;
        ItemStack result = recipe.assemble(nativeInput, level.registryAccess());
        var nativeRemaining = level.getRecipeManager().getRemainingItemsFor(
                ModRecipeTypes.CRAFTING_TABLE_RECIPE.get(), nativeInput, level);
        var consumed = consumedSlots(nativeInput, size);
        var returned = returnedStacks(grid, consumed, nativeRemaining);
        if (!PackagedOutputMatching.matchesWithAdditionalReturns(pattern, result, returned)) return null;

        CompoundTag progress = new CompoundTag();
        progress.put("inputs", encodeStacks(grid, level.registryAccess()));
        progress.put("returns", encodeStacks(returned, level.registryAccess()));
        progress.put("result", result.saveOptional(level.registryAccess()));
        return progress;
    }

    @Override
    public long batchCapacity(ServerLevel level, BlockPos position, Direction face, ResourceLocation recipeId,
                              IPatternDetails pattern, KeyCounter[] prototype, long requestedCount) {
        TierCraftTile table = table(level, position);
        if (table == null || !empty(table.getInventory()) || requestedCount <= 0) return 0;
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !(holder.get().value() instanceof ITierCraftingRecipe recipe) ||
                recipe.getType() != ModRecipeTypes.CRAFTING_TABLE_RECIPE.get() || recipe.getTier() > tier.ordinal() + 1)
            return 0;
        int size = tier.size;
        ObjectList<ItemStack> grid;
        if (recipe instanceof ShapedTableCraftingRecipe shaped) {
            grid = PackagedCraftingGrid.assign(new ObjectArrayList<>(recipe.getIngredients()), shaped.getWidth(), size, prototype);
        } else {
            var assigned = PackagedIngredientAssignment.match(new ObjectArrayList<>(recipe.getIngredients()), prototype);
            if (assigned == null || assigned.size() > size * size) return 0;
            grid = new ObjectArrayList<>(size * size);
            for (int index = 0; index < size * size; index++) grid.add(index < assigned.size() ? assigned.get(index) : ItemStack.EMPTY);
        }
        if (grid == null || grid.size() != size * size) return 0;
        TierInput nativeInput = TierInput.of(size, size, grid, tier.ordinal() + 1);
        if (!recipe.matches(nativeInput, level)) return 0;
        var chosen = level.getRecipeManager().getRecipeFor(ModRecipeTypes.CRAFTING_TABLE_RECIPE.get(), nativeInput, level);
        if (chosen.isEmpty() || !chosen.get().id().equals(recipeId)) return 0;
        ItemStack result = recipe.assemble(nativeInput, level.registryAccess());
        if (result.isEmpty()) return 0;
        // 一次菜单事务只能原子完成一次合成，因此批量上限恒为 1。
        return 1;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        TierCraftTile table = table(operation.level(), operation.position());
        if (table == null) return false;
        CompoundTag progress = operation.progress();
        int slots = tier.size * tier.size;
        ObjectList<ItemStack> inputs = decodeStacks(
                progress.getList("inputs", Tag.TAG_COMPOUND), operation.level().registryAccess());
        ObjectList<ItemStack> returns = decodeStacks(
                progress.getList("returns", Tag.TAG_COMPOUND), operation.level().registryAccess());
        ItemStack result = ItemStack.parse(operation.level().registryAccess(), progress.getCompound("result"))
                .orElseThrow(() -> new IllegalArgumentException("Missing Avaritia table result"));
        if (inputs.size() != slots) {
            throw new IllegalArgumentException("Invalid persisted Avaritia table grid");
        }
        ItemStackWrapper inventory = table.getInventory();
        if (!progress.getBoolean("delivered")) {
            for (int index = 0; index < slots; index++) {
                if (!inventory.getStackInSlot(index).isEmpty()) return false;
            }
            for (int index = 0; index < slots; index++) {
                ItemStack input = inputs.get(index);
                if (input.isEmpty()) continue;
                ItemStack rejected = inventory.insertItem(index, input.copy(), false);
                if (!rejected.isEmpty()) throw new IllegalStateException("Avaritia table input insertion changed");
                operation.delivered(AEItemKey.of(input), input.getCount());
            }
            progress.putBoolean("delivered", true);
            operation.changed();
            LOGGER.info("[Avaritia适配] 开始合成: 位置={}, 档位={}", operation.position(), tier);
            return true;
        }
        for (int index = 0; index < slots; index++) {
            ItemStack actualInput = inventory.getStackInSlot(index);
            if (!actualInput.isEmpty() && (!ItemStack.isSameItemSameComponents(actualInput, inputs.get(index)) ||
                    actualInput.getCount() > inputs.get(index).getCount())) {
                throw new IllegalStateException("Avaritia table input changed outside this operation");
            }
        }
        var fake = FakePlayerFactory.get(operation.level(), new GameProfile(
                UUID.nameUUIDFromBytes(operation.id().toString().getBytes(StandardCharsets.UTF_8)),
                "data_energistics_packaged"));
        // 关键修复：BaseTileMenu.stillValid 要求"玩家距方块 8 格内"，而假玩家默认在世界原点。
        // 距离校验失败会让 assembleCurrentResult() 直接返回空，产物槽永远为空、操作永久卡死，
        // 工作台被持续占用，后续封包请求就表现为"材料不进工作台"。
        fake.setPos(operation.position().getX() + 0.5,
                operation.position().getY() + 0.5,
                operation.position().getZ() + 0.5);
        var menu = (TierCraftMenu) table.createMenu(0, fake.getInventory());
        // 产物槽是懒计算的：ResultContainer 只在 slotsChanged 时填充。
        // 这里显式触发一次重算，等价于玩家打开界面时发生的事。
        menu.canTakeCraftingResult();
        var output = menu.getSlot(0);
        ItemStack actual = output.getItem().copy();
        if (actual.isEmpty()) {
            if (!progress.getBoolean("dbg_empty")) {
                progress.putBoolean("dbg_empty", true);
                LOGGER.info("[Avaritia适配] 结果槽为空, 等待中: 位置={}, 档位={}, stillValid={}",
                        operation.position(), tier, menu.stillValid(fake));
            }
            return false;
        }
        if (!PackagedOutputMatching.matches(operation, result.copyWithCount(actual.getCount()), actual))
            throw new IllegalStateException("Unexpected Avaritia table output");
        ItemStack taken = output.remove(actual.getCount());
        if (!ItemStack.matches(actual, taken)) throw new IllegalStateException("Avaritia result extraction changed");
        output.onTake(fake, taken);
        actual = taken;
        operation.returned(AEItemKey.of(actual), actual.getCount());

        var actualReturns = new ObjectArrayList<ItemStack>();
        for (int index = 0; index < slots; index++) {
            ItemStack returned = inventory.getStackInSlot(index).copy();
            if (!returned.isEmpty()) actualReturns.add(returned);
        }
        for (int index = 0; index < fake.getInventory().getContainerSize(); index++) {
            ItemStack returned = fake.getInventory().getItem(index).copy();
            if (!returned.isEmpty()) actualReturns.add(returned);
        }
        if (!sameStacks(actualReturns, returns)) {
            throw new IllegalStateException("Avaritia table native remainder changed");
        }
        for (int index = 0; index < slots; index++) inventory.setStackInSlot(index, ItemStack.EMPTY);
        fake.getInventory().clearContent();
        for (ItemStack returned : actualReturns) operation.returned(AEItemKey.of(returned), returned.getCount());
        operation.complete();
        LOGGER.info("[Avaritia适配] 合成完成: {} 级工作台 产物 {} x{}", tier, actual.getItem(), actual.getCount());
        return true;
    }

    private @Nullable TierCraftTile table(ServerLevel level, BlockPos position) {
        if (!level.isLoaded(position) || !(level.getBlockEntity(position) instanceof TierCraftTile table)) return null;
        return table.tier == this.tier ? table : null;
    }

    /** 逐格（含空槽）序列化，保留槽位数量，与 data_energistics 的 NbtCodecs 格式一致。 */
    private static ListTag encodeStacks(List<ItemStack> stacks, HolderLookup.Provider registries) {
        var list = new ListTag();
        for (ItemStack stack : stacks) {
            list.add(stack.saveOptional(registries));
        }
        return list;
    }

    /** 逐格反序列化；空槽位是空 CompoundTag，必须用 parseOptional 否则会刷错误日志。 */
    private static ObjectList<ItemStack> decodeStacks(ListTag list, HolderLookup.Provider registries) {
        var out = new ObjectArrayList<ItemStack>();
        for (int i = 0; i < list.size(); i++) {
            out.add(ItemStack.parseOptional(registries, list.getCompound(i)));
        }
        return out;
    }

    private static boolean empty(ItemStackWrapper inventory) {
        for (int index = 0; index < inventory.getSlots(); index++) {
            if (!inventory.getStackInSlot(index).isEmpty()) return false;
        }
        return true;
    }

    private static int[] consumedSlots(TierInput input, int tableSize) {
        var consumed = new ObjectArrayList<Integer>();
        for (int row = 0; row < input.height(); row++) {
            for (int column = 0; column < input.width(); column++) {
                if (!input.getItem(column, row).isEmpty()) {
                    consumed.add((input.top() + row) * tableSize + input.left() + column);
                }
            }
        }
        return consumed.stream().mapToInt(Integer::intValue).toArray();
    }

    private static ObjectList<ItemStack> returnedStacks(ObjectList<ItemStack> grid, int[] consumed,
                                                        List<? extends ItemStack> nativeRemaining) {
        var consumedSet = new IntOpenHashSet();
        for (int index : consumed) consumedSet.add(index);
        var returned = new ObjectArrayList<ItemStack>();
        for (int index = 0; index < grid.size(); index++) {
            ItemStack leftover = grid.get(index).copy();
            if (consumedSet.contains(index)) leftover.shrink(1);
            if (!leftover.isEmpty()) returned.add(leftover);
        }
        for (ItemStack remainder : nativeRemaining) if (!remainder.isEmpty()) returned.add(remainder.copy());
        return returned;
    }

    private static boolean sameStacks(List<? extends ItemStack> actual, List<? extends ItemStack> expected) {
        var actualCounts = new Object2LongLinkedOpenHashMap<AEItemKey>();
        var expectedCounts = new Object2LongLinkedOpenHashMap<AEItemKey>();
        for (ItemStack stack : actual) actualCounts.addTo(AEItemKey.of(stack), stack.getCount());
        for (ItemStack stack : expected) expectedCounts.addTo(AEItemKey.of(stack), stack.getCount());
        return actualCounts.equals(expectedCounts);
    }
}
