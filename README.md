# DE More Compat (`de_more_compat`)

**中文**
一个 NeoForge 模组，让 [DataEnergistics](https://github.com/ModularMCLib/DataEnergistics)（数据能源学）的**封包样板供应器**能驱动其它模组的机器执行配方。目前支持两类机器：

- **Powah 充能台**（Energizing Orb）—— `powah:energizing` 充能配方（官方 data_energistics **不提供** Powah 支持，此集成由本模组新增）；
- **无尽贪婪：重生 四档等级工作台**（`avaritia:sculk_crafting_table` / `nether` / `end` / `extreme`）—— `avaritia:crafting_table_recipe` 合成配方。

编码一个封包样板后，系统会自动完成 **塞料 → 机器处理 → 收取产物 → 返还余料 → 入网** 的完整链路。

**English**
A NeoForge mod that lets [DataEnergistics](https://github.com/ModularMCLib/DataEnergistics) **packaged pattern providers** drive machines from other mods. Currently supported:

- **Powah Energizing Orb** — `powah:energizing` recipes (data_energistics has **no** Powah integration; this one is added by this mod);
- **Avaritia: Rebirth tier crafting tables** (`avaritia:sculk_crafting_table` / `nether` / `end` / `extreme`) — `avaritia:crafting_table_recipe` recipes.

Encode a packaged pattern and the system handles **feed → process → collect → return leftovers → deliver into the network** automatically.

- **中文**：Minecraft `1.21.1` · NeoForge `21.1.248+` · Mod ID `de_more_compat` · 版本 `2.0.0`
- **English**: Minecraft `1.21.1` · NeoForge `21.1.248+` · Mod ID `de_more_compat` · Version `2.0.0`

---

## 为什么需要这个模组 / Why This Mod Exists

**中文**
Powah 与 Avaritia 的处境完全不同：

- **Powah**：data_energistics 官方**完全没有** Powah 集成——3.3.5 的 jar 里没有任何 `powah` 相关类。本模组从零实现了充能台适配器，并顺带修掉了 Powah 充能台自身的一个产物复制 Bug。
- **Avaritia**：官方自带适配器，但存在隐蔽缺陷——`advance` 用假玩家打开工作台菜单取产物，而 `BaseTileMenu.stillValid` 要求**玩家距方块 8 格内**。假玩家站在世界原点，距离校验必然失败，菜单的产物槽永远是空的 → 操作永远无法完成 → 工作台被永久占用 → **第一次合成成功后，后续材料再也进不去工作台**（表现为"完全没反应"）。

本模组停用官方 Avaritia 适配器并注册修正版；Powah 部分则是全新实现。

**English**
Powah and Avaritia are in very different situations:

- **Powah**: data_energistics has **no** Powah integration at all — there is not a single `powah` class in the 3.3.5 jar. This mod implements the Energizing Orb adapter from scratch, and additionally fixes an item-duplication bug in the orb itself.
- **Avaritia**: the official adapter exists but has a subtle defect — `advance` opens the table menu with a fake player to collect the result, while `BaseTileMenu.stillValid` requires the **player to be within 8 blocks**. The fake player sits at the world origin, so the distance check always fails, the menu's result slot stays empty, the operation can never finish, and the table stays claimed forever — **after the first successful craft, no further materials ever enter the table** (looks like "nothing happens at all").

This mod disables the built-in Avaritia adapter and registers a fixed one; the Powah side is a brand-new implementation.

---

## 功能 / Features

**中文**
- 通过 `@DataEnergisticsEntrypoint` 注册两个插件入口，分别对应 Powah 与 Avaritia；
- **Powah**：封包样板编码 `powah:energizing` 后，自动把材料送入充能台输入槽（1–6），能量充满后自动收取产物；
- **Avaritia**：为 SCULK / NETHER / END / EXTREME 四档各注册一个适配器，自动摆放合成格、取走产物、核对余料（桶、工具等不会被吞）；
- **修复假玩家距离问题**：把假玩家移动到工作台坐标，并显式刷新懒计算的产物槽；
- **支持连续合成与批量**：同一条封包请求可以连续处理多件（10 件、20 件批量均已实测通过）；
- **Mixin 停用官方重复适配器**（`DisableBuiltinAvaritiaAdapterMixin`），避免同一台工作台被两个适配器争抢；
- **Mixin 修复 Powah 产物复制**（`EnergizingOrbTileRecipeResetMixin`）：`checkRecipe` 在无匹配配方时不清空 `recipe` 字段，导致收料后仍会反复吐出同一产物。

**English**
- Registers two `@DataEnergisticsEntrypoint` plugins, one per integration;
- **Powah**: after encoding a `powah:energizing` packaged pattern, materials are fed into input slots 1–6 and the result is collected once the orb is fully charged;
- **Avaritia**: one adapter per tier (SCULK / NETHER / END / EXTREME) — places the grid, takes the result, and verifies native remainders (buckets, tools etc. are not swallowed);
- **Fake-player distance fix**: moves the fake player to the table position and explicitly refreshes the lazily-computed result slot;
- **Continuous and batch crafting**: a single packaged request can process many items in sequence (10-item and 20-item batches verified);
- **Mixin disables the duplicate built-in adapter** (`DisableBuiltinAvaritiaAdapterMixin`) so two adapters never fight over the same table;
- **Mixin fixes Powah result duplication** (`EnergizingOrbTileRecipeResetMixin`): `checkRecipe` never clears the `recipe` field when nothing matches, so the orb keeps re-emitting the same result after collection.

---

## 使用要点 / Usage Notes

**中文**
1. **必须用 JEI 把配方转移成"封包样板"**。直接拿普通加工样板、或在普通 AE2 样板终端里手动编码，样板不会带上 recipe reference，封包机会在 `prepareBatch` 第一道闸门静默返回 `null`——表现就是"完全没反应"。
2. 封包样板供应器必须**紧邻**（或通过连接器接到）目标机器。
3. 目标工作台/充能台**必须完全空闲**，有残留物品时适配器会拒绝接管。
4. **Powah 充能台不负责供电**：请在旁边接 RF 源（Powah 能量线/发电机等），否则材料进去了也永远不会完成。

**English**
1. **Encode the pattern as a "packaged pattern" via JEI.** A plain processing pattern, or one encoded manually in a normal AE2 pattern terminal, carries no recipe reference; the provider silently returns `null` at the first gate of `prepareBatch` — which looks exactly like "nothing happens".
2. The packaged pattern provider must be **adjacent** to (or connected to) the target machine.
3. The target table/orb **must be completely empty**; otherwise the adapter refuses to take over.
4. **The Powah orb does not supply its own power**: attach an RF source (Powah cables/generators), or materials will sit there forever.

---

## 依赖 / Dependencies

| 依赖 / Dependency | 版本 / Version | 说明 / Notes |
|---|---|---|
| NeoForge | `21.1.248+` | 必需 / required |
| [DataEnergistics](https://www.curseforge.com/minecraft/mc-mods/data-energistics) | `[3.3.5, 4.0.0)` | 必需，提供封包 API / required, provides the packaged API |
| Applied Energistics 2 | `19.x` | DataEnergistics 依赖 / required by DataEnergistics |
| [Powah](https://github.com/Technici4n/Powah) | `6.2+` | **可选**，检测到才启用 / **optional**, enabled only when present |
| [Re-Avaritia](https://github.com/Nova-Committee/Re-Avaritia) | `1.4.1+` | **可选**，检测到才启用 / **optional**, enabled only when present |

> **中文**：唯一的硬性依赖是 **data_energistics ≥ 3.3.5**。Powah 与 Re-Avaritia 在 `neoforge.mods.toml` 中均为 `type="optional"`，未安装时只是对应集成不启用，模组本身照常加载。
> **English**: The only hard requirement is **data_energistics ≥ 3.3.5**. Powah and Re-Avaritia are both `type="optional"`: when absent, only their integrations stay disabled while the mod loads normally.

---

## 选择性启用 / Selective Loading

**中文**
Powah 与 Avaritia 的代码都是"按需启用"的，三层门禁共同保证缺模组时不会出现 `NoClassDefFoundError` 或注入崩溃：

| 层次 | 机制 | 缺模组时的行为 |
|---|---|---|
| 插件入口 | `@DataEnergisticsEntrypoint(requiredMods = {"powah"})` / `{"avaritia"}` | DataEnergistics 在**实例化之前**就跳过整个入口类 |
| Mixin 注入 | `DeMoreCompatMixinPlugin` 在 `shouldApplyMixin` 中用 `LoadingModList` 判断：`DisableBuiltinAvaritiaAdapterMixin` 需 avaritia，`EnergizingOrbTileRecipeResetMixin` 需 powah | 目标模组不存在时不注入，也就不会因 `required=true` 崩游戏 |
| 依赖元数据 | `neoforge.mods.toml` 中 `type="optional"` | 加载器不会因缺少该模组而拒绝启动 |

因此：只装数据能源学可以正常跑；只装 Powah 就只启用充能台集成；只装 Avaritia 就只启用四档工作台集成；三者都装则全部启用。

> 已实测：分别单独禁用 Avaritia 与 Powah，模组均正常加载、只注册对应适配器，Mixin 门禁日志正确打印 `应用=true/false`。

**English**
Powah and Avaritia code is enabled on demand; three layers together prevent `NoClassDefFoundError` or injection crashes when a mod is absent:

| Layer | Mechanism | Behaviour when the mod is missing |
|---|---|---|
| Plugin entrypoint | `@DataEnergisticsEntrypoint(requiredMods = {"powah"})` / `{"avaritia"}` | DataEnergistics skips the whole entrypoint class **before instantiating it** |
| Mixin application | `DeMoreCompatMixinPlugin` checks `LoadingModList` in `shouldApplyMixin`: `DisableBuiltinAvaritiaAdapterMixin` needs avaritia, `EnergizingOrbTileRecipeResetMixin` needs powah | No injection is attempted, so `required=true` cannot crash the game |
| Dependency metadata | `type="optional"` in `neoforge.mods.toml` | The loader does not refuse to start without the mod |

So: data_energistics alone runs fine; Powah alone enables only the orb integration; Avaritia alone enables only the four tier tables; all three together enable everything.

> Verified: disabling Avaritia and Powah separately, the mod still loads, only the matching adapter registers, and the mixin-gate log correctly prints `apply=true/false`.

---

## 工作原理 / How It Works

### Avaritia 等级工作台

**中文**
- `prepare`：校验配方（类型必须为 `avaritia:crafting_table_recipe`，且配方等级不超过工作台档位），把封包输入排布成工作台尺寸的网格并试算，把网格、余料、期望产物写入进度 NBT；
- `advance`（第一拍）：把网格里的原料逐格 `insertItem` 进工作台，并 `delivered` 标记输入已消耗；
- `advance`（后续拍）：把假玩家移到工作台坐标 → 打开菜单 → 强制刷新产物槽 → 取走结果 → 核对余料与预期一致 → `returned` 产物、清空工作台、`complete`；
- `batchCapacity` 恒为 `1`：工作台菜单一次只能原子完成一次合成，不能用重复取结果来模拟批量。

**English**
- `prepare`: validates the recipe (type must be `avaritia:crafting_table_recipe`, tier must not exceed the table's level), lays inputs out on a table-sized grid, dry-runs it, and stores grid / returns / expected result in the progress NBT;
- `advance` (first tick): inserts the grid contents into the table slot-by-slot and marks inputs `delivered`;
- `advance` (later ticks): moves the fake player to the table position → opens the menu → forces the result slot to refresh → takes the result → verifies remainders match expectations → `returned`s the result, clears the table, and `complete`s;
- `batchCapacity` is always `1`: the table menu can only complete one atomic craft at a time, so batching by repeatedly taking results is forbidden.

### Powah 充能台

**中文**
充能台共 7 格：槽 0 是产物槽，槽 1–6 是输入区。`prepare` 把材料塞进槽 1 起并记录期望产物；能量充满后充能台自动消耗材料、把产物放进槽 0；`advance` 轮询槽 0，匹配成功即交付产物、标记输入消耗、清空并完成。

**English**
The orb has 7 slots: slot 0 is the output, slots 1–6 are inputs. `prepare` inserts materials from slot 1 upward and records the expected result; once fully charged the orb consumes the inputs and puts the result in slot 0; `advance` polls slot 0, and on a match delivers the result, marks inputs consumed, clears the orb, and completes.

---

## 安装 / Installation

**中文**：把打包好的 jar 放入整合包的 `mods` 目录，启动游戏即可。本模组是**整合包包内模组**，通常随包分发，不单独提供下载。若你在整合包中使用或分发，请在发布页注明包含此模组及其 AGPL 许可。

**English**: Drop the built jar into the pack's `mods` folder and start the game. This is a **pack-internal mod**: it ships with the pack and is not distributed standalone. If you use or redistribute it in a pack, please credit the mod and its AGPL license on the pack's release page.

---

## 构建 / Building

**中文**
需要 **JDK 21** 与 Gradle（仓库自带 wrapper，8.8）。在工程目录执行：

```bash
./gradlew build
```

产物位于 `build/libs/de_more_compat-2.0.0.jar`。

注意两点：

- `build.gradle` 里的 `compileOnly` 用**绝对路径**引用整合包中的 `data_energistics` / `Powah` / `appliedenergistics2` / `Re-Avaritia` jar，换机器前请先改这些路径。**编译期四个 jar 都需要**（否则适配器类编不过），但**运行期**只有 data_energistics 是必需的；
- `gradle.properties` 里写死了本地代理 `127.0.0.1:7897`，代理未启动时请删除这些 `systemProp.*` 配置，或使用 `--offline`（依赖已缓存时可用）。

**English**
Requires **JDK 21** and Gradle (the wrapper pins 8.8):

```bash
./gradlew build
```

The jar lands in `build/libs/de_more_compat-2.0.0.jar`.

Two caveats:

- `compileOnly` entries in `build.gradle` reference the pack's `data_energistics` / `Powah` / `appliedenergistics2` / `Re-Avaritia` jars by **absolute path** — update them when moving machines;
- `gradle.properties` hardcodes a local proxy at `127.0.0.1:7897`; remove those `systemProp.*` lines when it is not running, or build with `--offline` (works when dependencies are cached).

---

## 已知限制 / Known Limitations

**中文**
- Avaritia 每次封包操作只完成 **1 次**合成，批量由上层排队逐次驱动；
- 中子压缩器（Neutron Compressor）与极限锻造台（Extreme Smithing Table）**未覆盖**，沿用 data_energistics 官方适配器；
- Powah 充能台最多 6 种输入、只接受物品（不支持流体/能量输入）；
- 目标机器被其它操作占用时，适配器会被静默跳过，直到占用释放。

**English**
- Each Avaritia packaged operation completes exactly **one** craft; batching is driven by repeated queued operations;
- Neutron Compressor and Extreme Smithing Table are **not covered** — the built-in data_energistics adapters still handle them;
- The Powah orb accepts at most 6 item inputs and does not accept fluids/energy as pattern inputs;
- When the target machine is claimed by another operation, the adapter is silently skipped until the claim is released.

---

## 故障排查 / Troubleshooting

| 现象 / Symptom | 常见原因 / Likely Cause |
|---|---|
| 封包机完全不动作，机器里没有任何物品 / Nothing happens, machine stays empty | 样板不是"封包样板"（未带 recipe reference），或供应器没有连到机器 / Pattern is not a packaged pattern, or the provider is not connected |
| 材料进了机器但产物一直不出现 / Materials go in but result never appears | 上一次操作卡死占用了机器；拆掉重放机器可清理 / A previous operation is stuck holding the machine; breaking and replacing it clears the claim |
| Powah 充能台材料进去了但永不完成 / Orb never completes | 旁边没有 RF 供电 / No RF source adjacent |
| 产物被复制 / Items duplicated | 确认 `EnergizingOrbTileRecipeResetMixin` 已生效（Powah 版本需在 6.2+） / Verify the Powah mixin applies (Powah 6.2+) |
| 日志刷 `Tried to load invalid item` / Log spam | 旧版本适配器用 `ItemStack.parse` 解析空槽位；2.0.0 已改用 `parseOptional` / Fixed in 2.0.0 via `parseOptional` |

---

## 变更记录 / Changelog

### 2.0.0 — 新增无尽贪婪支持 / Avaritia support added

**中文**
- 新增：无尽贪婪：重生 四档等级工作台的封包驱动（SCULK / NETHER / END / EXTREME）；
- 变更（**不兼容**）：模组 ID 由 `de_powah_compat` 更名为 `de_more_compat`。1.0.0 创建且尚未完成的封包操作在进度数据里记录的是旧 ID，升级后无法被识别、会一直挂起；升级前请先让进行中的封包合成完成，或拆掉相关机器以释放占用；
- 变更：Powah 与 Re-Avaritia 改为**可选依赖**，仅在检测到对应模组时启用；新增 `DeMoreCompatMixinPlugin` 作为 Mixin 门禁，硬依赖只剩 `data_energistics ≥ 3.3.5`；
- 修复：假玩家距离导致产物槽恒空、操作永久卡死、工作台被长期占用的问题；
- 修复：空槽位反序列化产生的错误日志刷屏；
- 实测：连续合成 10 件、20 件批量通过。

**English**
- Added: packaged crafting for all four Avaritia tier tables (SCULK / NETHER / END / EXTREME);
- Changed (**breaking**): the mod ID was renamed from `de_powah_compat` to `de_more_compat`. Packaged operations created by 1.0.0 that are still in flight record the old ID in their progress data, so they cannot be resolved after upgrading and will hang forever; finish pending packaged crafts, or break the involved machines to release their claims, before upgrading;
- Changed: Powah and Re-Avaritia are now **optional dependencies**, enabled only when detected; `DeMoreCompatMixinPlugin` gates the mixins, leaving `data_energistics ≥ 3.3.5` as the only hard requirement;
- Fixed: fake-player distance left the result slot empty, stalling operations and permanently claiming tables;
- Fixed: error-log spam from deserializing empty slots;
- Verified: continuous crafting and 10-/20-item batches.

### 1.0.0 — 首个版本，仅 Powah 兼容 / First release, Powah only

**中文**
- 新增：Powah 充能台（Energizing Orb）封包驱动——编码 `powah:energizing` 的封包样板后，自动完成塞料、充能、收取产物与返还余料；
- 修复：Powah `EnergizingOrbTile.checkRecipe` 在无匹配配方时不重置 `recipe` 字段，导致收料后仍反复把同一产物放回槽 0（物品复制）；
- 范围：**仅支持 Powah**，不含无尽贪婪（Avaritia）等级工作台；
- 标识：该版本的模组 ID 为 `de_powah_compat`（2.0.0 起更名为 `de_more_compat`）；
- 依赖：该版本中 Powah 为**硬性必需**，缺少 Powah 时模组不会加载；
- 说明：data_energistics 官方从未提供 Powah 集成，Powah 支持自本版本起由本模组新增。

**English**
- Added: packaged crafting for the Powah Energizing Orb — encoding a `powah:energizing` packaged pattern automates feeding, charging, result collection and leftover return;
- Fixed: Powah's `EnergizingOrbTile.checkRecipe` never resets the `recipe` field when nothing matches, so the orb kept putting the same result back into slot 0 after collection (item duplication);
- Scope: **Powah only** — no Avaritia tier tables;
- Identifier: the mod ID in this release was `de_powah_compat` (renamed to `de_more_compat` in 2.0.0);
- Dependency: Powah was a **hard requirement** in this release; the mod would not load without it;
- Note: data_energistics has never shipped a Powah integration; Powah support has been provided by this mod since this version.

---

## 许可 / License

**中文**

本项目实现 data_energistics 的 `PackagedMachineAdapter` 接口，因此代码以 **GNU Affero General Public License v3.0 or later**（AGPL-3.0-or-later）授权，详见 [LICENSE](LICENSE)。

**上游许可声明：**

- **DataEnergistics**（[ModularMCLib/DataEnergistics](https://github.com/ModularMCLib/DataEnergistics)，作者 fish_dan / QiuYe / TedXenon）：官方许可为 **`AGPL-3.0-or-later (code); All Rights Reserved (resources/assets)`**——代码以 AGPL-3.0-or-later 授权，资源（贴图/音频等）保留所有权利。本项目只实现其 `PackagedMachineAdapter` 接口，**不复用任何资源**。
- **Powah**（[Technici4n/Powah](https://github.com/Technici4n/Powah)）：**GNU LGPL-3.0**。本项目的 Mixin 修改并引用了 Powah 的 `EnergizingOrbTile`，按 LGPL-3.0 §4 保留 Powah 的许可声明与版权（Copyright (c) owmii / Technici4n），分发时附带 LGPL-3.0 许可证副本，并允许用户将修改后的 Powah 换回原版重新链接（relink）。
- **Re-Avaritia**（Nova Committee）：本项目的适配器调用其公开 API（`ITierCraftingRecipe`、`TierCraftTile`、`TierCraftMenu` 等）并对其行为做兼容处理，不复制其代码与资源。

**English**

This project implements data_energistics' `PackagedMachineAdapter` interface; therefore its code is licensed under **AGPL-3.0-or-later**. See [LICENSE](LICENSE).

**Upstream license notices:**

- **DataEnergistics** ([ModularMCLib/DataEnergistics](https://github.com/ModularMCLib/DataEnergistics), authors fish_dan / QiuYe / TedXenon): licensed **`AGPL-3.0-or-later (code); All Rights Reserved (resources/assets)`**. This project only implements its public adapter interface and **reuses none of its assets**.
- **Powah** ([Technici4n/Powah](https://github.com/Technici4n/Powah)): **GNU LGPL-3.0**. This project's mixin modifies and references Powah's `EnergizingOrbTile`; per LGPL-3.0 §4 the Powah license notice and copyright (Copyright (c) owmii / Technici4n) are retained, the LGPL-3.0 text ships with distributions, and users may relink a modified Powah back to the original.
- **Re-Avaritia** (Nova Committee): this project calls its public API (`ITierCraftingRecipe`, `TierCraftTile`, `TierCraftMenu`, etc.) and works around its behaviour, without copying its code or assets.

---

## 作者 / Author

**中文**：[xing_yu_dragon](https://github.com/starraindragon)
**English**: [xing_yu_dragon](https://github.com/starraindragon)
