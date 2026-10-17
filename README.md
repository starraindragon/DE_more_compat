# Powah 兼容 / Powah Compat (de_powah_compat)

**中文**：一个 NeoForge 模组，让 [DataEnergistics](https://github.com/ModularMCLib/DataEnergistics) 的封包样板机制能驱动 [Powah](https://github.com/Technici4n/Powah) 充能台（Energizing Orb）执行 `powah:energizing` 充能配方。编码封包样板后，系统会自动把充能配方所需的材料送入充能台，充能完成后自动收取产物。

**English**: A NeoForge mod that lets [DataEnergistics](https://github.com/ModularMCLib/DataEnergistics) packaged crafting drive a [Powah](https://github.com/Technici4n/Powah) Energizing Orb. Encode a packaged pattern and the system automatically feeds the recipe materials into the orb, then collects the output once charging is complete.

- **中文**：Minecraft `1.21.1` · NeoForge `21.1.248+` · Mod ID `de_powah_compat`
- **English**: Minecraft `1.21.1` · NeoForge `21.1.248+` · Mod ID `de_powah_compat`

---

## 整合包包内模组 / Pack-Internal Mod

**中文**
本模组是**整合包包内模组**：它作为整合包的一部分随整合包一起分发，通常**不单独提供独立下载**。使用者无需单独安装本模组，只要整合包带上了它，放入 `mods` 目录即可直接使用。若你在整合包中使用或分发本模组，请在整合包发布页注明包含此模组及其 AGPL 许可。

**English**
This mod is a **pack-internal mod**: it ships as part of a modpack and is generally **not distributed as a standalone download**. Users do not install it separately — once the pack includes it, it is used directly from the `mods` folder. If you use or redistribute this mod inside a modpack, please credit the mod and its AGPL license on the pack's release page.

---

## 功能 / Features

**中文**
- 通过 `@DataEnergisticsEntrypoint` 注册一个 Powah 充能台适配器（`PowahEnergizingAdapter`）；
- 封包样板编码 `powah:energizing` 配方后，自动完成 **塞料 → 充能 → 收取产物 → 交付** 的完整链路；
- 附带的 Mixin（`EnergizingOrbTileRecipeResetMixin`）修复 Powah 充能台 `checkRecipe` 在无配方时残留 `recipe` 导致反复产出同一个产物的 Bug（可避免封包合成时的物品复制）。

**English**
- Registers a Powah Energizing Orb adapter (`PowahEnergizingAdapter`) via `@DataEnergisticsEntrypoint`;
- Once a `powah:energizing` recipe is encoded as a packaged pattern, it automatically handles the full **feed → charge → collect → deliver** chain;
- A bundled Mixin (`EnergizingOrbTileRecipeResetMixin`) fixes a Powah bug where `checkRecipe` leaves a stale `recipe` when there is no matching recipe, which caused the orb to keep producing the same output repeatedly (and could duplicate items during packaged crafting).

---

## 工作原理 / How It Works

**中文**
Powah 充能台库存共 7 格：槽 0 是产物槽（充能完成后产物固定放槽 0），槽 1–6 是输入区。适配器按 data_energistics 的封包协议工作：

- `prepare`：解析封包样板输入，把每种输入材料塞进充能台槽 1 起，并记录输入清单与期望产物；
- 充能：机器旁的 RF 源持续灌入，能量灌满后充能台自动消耗材料并把产物放到槽 0；
- `advance`：轮询槽 0，出现期望产物后把产物 `returned` 归还给封包机（入网）、把输入 `delivered` 标记为已消耗、清空充能台并 `complete`。

> 注意：本模组**不负责给充能台供电**——封包合成时请确保充能台旁有 RF 供给（如 Powah 的能量线 / 发电机）。

**English**
A Powah Energizing Orb has 7 inventory slots: slot 0 is the output slot (the charged result always lands in slot 0), slots 1–6 are the input area. The adapter follows data_energistics' packaged-crafting protocol:

- `prepare`: resolves the pattern inputs, inserts each input material into slots 1+ and records the input list and expected output;
- Charging: an RF source beside the orb keeps feeding energy; once the buffer is full, the orb consumes the materials and places the result in slot 0;
- `advance`: polls slot 0; when the expected result appears, it `returned`s the output to the packager (into the network), `delivered`s the inputs as consumed, clears the orb and `complete`s.

> Note: this mod does **not** supply power to the orb — during packaged crafting, make sure there is an RF source beside it (e.g. Powah energy cables / generators).

---

## 依赖 / Dependencies

**中文**
- [NeoForge](https://neoforged.net/) `21.1.248+`
- [DataEnergistics](https://www.curseforge.com/minecraft/mc-mods/data-energistics) `[3.3.5, 4.0.0)`
- [Powah](https://github.com/Technici4n/Powah) `6.2+`
- Applied Energistics 2

**English**
- [NeoForge](https://neoforged.net/) `21.1.248+`
- [DataEnergistics](https://www.curseforge.com/minecraft/mc-mods/data-energistics) `[3.3.5, 4.0.0)`
- [Powah](https://github.com/Technici4n/Powah) `6.2+`
- Applied Energistics 2

---

## 安装 / Installation

**中文**：把打包好的 jar 放入整合包的 `mods` 目录，启动游戏即可。作为整合包包内模组，随包分发时无需用户额外操作。

**English**: Place the built jar into the modpack's `mods` folder and start the game. As a pack-internal mod, no extra step is required when it ships with the pack.

---

## 构建 / Building

**中文**
本仓库不包含编译产物。在工程目录执行：

```bash
./gradlew build
```

构建产物位于 `build/libs/de_powah_compat-<version>.jar`。构建前请确认 `build.gradle` 中 `compileOnly` 引用的 `data_energistics-*.jar` / `Powah-*.jar` / `appliedenergistics2-*.jar` 路径正确。

**English**
This repository does not include build artifacts. From the project directory run:

```bash
./gradlew build
```

The built jar is at `build/libs/de_powah_compat-<version>.jar`. Make sure the `data_energistics-*.jar` / `Powah-*.jar` / `appliedenergistics2-*.jar` referenced by `compileOnly` in `build.gradle` point to the correct paths before building.

---

## 许可 / License

**中文**

本项目实现 data_energistics 的 [PackagedMachineAdapter](https://github.com/ModularMCLib/DataEnergistics) 接口，因此本项目代码以 **GNU Affero General Public License v3.0 or later**（AGPL-3.0-or-later）授权，详见 [LICENSE](LICENSE)。

**上游许可声明：**

- **DataEnergistics**（[ModularMCLib/DataEnergistics](https://github.com/ModularMCLib/DataEnergistics)，作者 fish_dan / QiuYe / TedXenon）：官方许可为 **`AGPL-3.0-or-later (code); All Rights Reserved (resources/assets)`**——即**代码部分**以 AGPL-3.0-or-later 授权，**资源（贴图/音频等）部分保留所有权利**。本项目只实现其 `PackagedMachineAdapter` 接口、**不复用其任何资源**。
- **Powah**（[Technici4n/Powah](https://github.com/Technici4n/Powah)）：**GNU LGPL-3.0**。本项目的 Mixin 修改并引用了 Powah 的 `EnergizingOrbTile`，按 LGPL-3.0 第 4 条要求在此保留 Powah 的许可声明与版权（Copyright (c) owmii / Technici4n），并在分发时附带 LGPL-3.0 许可证副本、允许用户将修改后的 Powah 换回原版重新链接（relink）。

**English**

This project implements data_energistics' [PackagedMachineAdapter](https://github.com/ModularMCLib/DataEnergistics) interface; therefore this project's code is licensed under the **GNU Affero General Public License v3.0 or later** (AGPL-3.0-or-later). See [LICENSE](LICENSE).

**Upstream license notices:**

- **DataEnergistics** ([ModularMCLib/DataEnergistics](https://github.com/ModularMCLib/DataEnergistics), authors fish_dan / QiuYe / TedXenon): official license is **`AGPL-3.0-or-later (code); All Rights Reserved (resources/assets)`** — the **code** is AGPL-3.0-or-later, while the **assets (textures/audio etc.) are All Rights Reserved**. This project only implements its `PackagedMachineAdapter` interface and **reuses none of its assets**.
- **Powah** ([Technici4n/Powah](https://github.com/Technici4n/Powah)): **GNU LGPL-3.0**. This project's Mixin modifies and references Powah's `EnergizingOrbTile`; per LGPL-3.0 §4 we retain Powah's license notice and copyright (Copyright (c) owmii / Technici4n), distribute the LGPL-3.0 license text, and permit relinking a modified Powah back to the original.

---

## 作者 / Author

**中文**：[xing_yu_dragon](https://github.com/starraindragon)

**English**: [xing_yu_dragon](https://github.com/starraindragon)

---