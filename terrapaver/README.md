# TerraPaver · 筑路工坊 (Minecraft 1.21.1 Fabric)

[简体中文](#简体中文) | [English](#english)

---

## 简体中文

**TerraPaver（筑路工坊）** 是一个基于 **Minecraft 1.21.1 Fabric + Kotlin** 开发的超强智能自动铺路与铁路基建模组（依赖前置 `fabric-language-kotlin`，所需 Fabric API 模块已内置于单一 JAR 中）。

### 核心特性
1. **极简上手，三步铺路**：
   - 仅需一个核心道具 **「筑路测绘仪」**（可在生存合成，也可在创造栏获取）。
   - 右键设起点 `A` -> 右键设终点 `B`（或在界面内一键向前延伸 `100m / 250m / 500m`）-> 再右键即可开始施工，支持一键撤销。
2. **强大 AI 地形工程**：
   - **支持未加载区块施工**：后台采用流式区块强制加载窗口，边勘测边施工、完成后自动释放，上千格跨大陆干线也能流畅铺设。
   - **AI 智能绕障寻路 + 纵断面缓坡优化**：支持「AI 智能绕障（A* 寻路 + Chaikin 曲线平滑）」与「直线直穿」双模式，自动限制最大坡度（默认 `1:4`）。
   - **机械动力（Create）铁路路基适配**：生成平缓道砟路基与超大列车净空（**铁轨由玩家自己放置**），隧道与高架自动预留充足通行空间。
   - **现代公路模式**：自动沥青/石砌/乡间路面、黄色虚线/白线标线、护栏与路灯。
   - **遇到河流/峡谷/山体自动应对**：
     - 路浮空或跨河时，自动架设 **高架桥并向下打桩生成桥墩支柱** 直抵河床/地面；也可切换为 **穿河防水盾构隧道**（带玻璃顶观景窗）。
     - 遇到山体自动 **开凿隧道**，支持拱形 / 圆形盾构 / 矩形截面，且 **隧道直径自动适配路宽**，也支持手动微调尺寸。
3. **谷歌 Material Design 3 浅色界面 & 实时进度条**：
   - 潜行+右键测绘仪或按 `P` 键打开 **Google M3 浅色设置界面**，内含 4 套动态莫奈主题色、实时截面预览图与中英文切换支持。
   - 施工全程在屏幕顶部显示 Material 3 悬浮进度条（勘测进度 / 施工百分比 / 隧道数 / 高架数 / 方块数）。

### 构建方式
```bash
cd terrapaver
chmod +x gradlew
./gradlew build
```

---

## English

**TerraPaver** is an AI-powered highway & Create-ready railbed construction mod for **Minecraft 1.21.1 Fabric**, written in **Kotlin** (requires `fabric-language-kotlin`; required Fabric API modules are bundled via Jar-in-Jar).

### Key Features
1. **Effortless Gameplay**: One item (**Paving Surveyor**) — Right-click Start `A` -> Right-click End `B` (or click `+100m / +250m / +500m` ahead in the GUI) -> Right-click to Pave, with one-click **Undo**.
2. **Powerful Terrain AI**:
   - **Unloaded Chunk Streaming**: Dynamically loads & releases chunks along the route so multi-hundred/thousand-block corridors pave smoothly beyond loaded render distance.
   - **Smart A* Alignment & Grade Solver**: Smooth Chaikin curves + bidirectional slope clamping (`1:2` to `1:8`).
   - **Create-Ready Railbed Mode**: Builds compacted ballast & wide/tall clearances ready for **Create** trains (leaves top flat for player-placed tracks).
   - **Auto Tunnels & Elevated Viaducts**: Automatically bores **Arch / Circular / Box Tunnels** through mountains (auto-adapting diameter to road/rail width or custom sized) and builds **Viaducts with deep Support Piers** whenever the road spans rivers, valleys, or air — plus an optional **Underwater Shield Tunnel** river mode!
3. **Google Material Design 3 Light UI & Live HUD Progress Bar**:
   - Press `P` or `Shift + Right-Click` to open the Material 3 Light configuration studio with 4 dynamic seed palettes, live cross-section schematic preview, and full **English / 简体中文** support.
