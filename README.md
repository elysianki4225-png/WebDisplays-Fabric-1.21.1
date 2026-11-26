# WebDisplays Fabric 1.21.1

[简体中文](#简体中文) | [English](#english) | [Русский](#русский)

---

## 简体中文

Minecraft **1.21.1** Fabric 平台的 [WebDisplays](https://www.mcmod.cn/class/8643.html) 移植与性能优化版本：内置 **MCEF**、**Cloth Config** 及所需 **Fabric API 子模块**（单个独立 `.jar` 文件即可使用），支持 **x86_64 与 ARM64（`aarch64`）** 架构，并支持游戏内 **简体中文 / English / Русский** 多语言界面。

### 下载链接
- **GitHub Releases 发布页（获取最新版本）**：
  https://github.com/elysianki4225-png/WebDisplays-Fabric-1.21.1/releases
- **当前主分支最新 `.jar` 直链下载**：
  https://github.com/elysianki4225-png/WebDisplays-Fabric-1.21.1/releases/download/build-a7bed41/webdisplays-fabric-2.1.0-1.21.1.jar
- **主分支自动更新最新稳定下载（合并到 `main` 后自动指向最新版）**：
  https://github.com/elysianki4225-png/WebDisplays-Fabric-1.21.1/releases/latest/download/webdisplays-fabric-2.1.0-1.21.1.jar

> **安装说明**：只需将下载的 `webdisplays-fabric-*.jar` 放入 Minecraft 1.21.1 的 `mods/` 文件夹并安装 Fabric Loader（>= 0.16.14）即可，**无需额外安装** MCEF、Cloth Config 或 Fabric API（若同时安装了完整版 Fabric API 也可正常兼容）。

### 核心特性
1. **单文件打包（Jar-in-Jar）**：已内置 `:mcef`（`2.1.6-1.21.1+wd.opt.1`）、`cloth-config-fabric`（`15.0.140`）以及 18 个运行时所需 Fabric API 子模块。
2. **双架构与安全兼容（x86_64 + ARM64 / aarch64）**：
   - `MCEFPlatform` 与 JCEF `org.cef.OS` 完整适配 `linux_amd64`、`linux_arm64`、`windows_amd64`、`windows_arm64`、`macos_amd64`、`macos_arm64`。
   - 自动检测 Android / FCL / Pojav 等无桌面 JCEF 本地库的环境并安全跳过初始化，避免闪退。
3. **渲染与性能深度优化**：
   - **MCEF / JCEF**：`MCEFRenderer` 使用 `glTexSubImage2D` 复用显存纹理并重置 `GL_UNPACK_*` 状态；`CefRenderUpdateMixin` 对 `N_DoMessageLoopWork()` 进行自适应帧率节流；移除启动阻塞 `Thread.sleep`；`MCEFDownloader` 支持 64 KB 缓冲、超时重试、Zip-Slip 安全校验与离线缓存回退。
   - **世界与屏幕渲染**：`ScreenBaker` 预烘焙 `6 × 16` 边框面并实现 `FabricBakedModel`（兼容 Sodium/Indium 与原版渲染）；`ScreenRenderer` 支持屏幕背面剔除、视距裁剪、分帧错峰创建浏览器（`maxBrowsersPerFrame`）与不可见屏幕 500ms 滞回暂停（`pauseCulledScreens`）。
   - **交互与网络**：`MinePadRenderer` 缓存 `PadID` UUID 查询；`ClientProxy` 按 game tick 缓存激光笔射线检测；`WDNetworkRegistry` 安全释放 Netty 缓冲区；完整升级 Minecraft 1.21.1 数据包（`recipe/`、`advancement/`、`loot_table/`）及多语言（`zh_cn`、`en_us`、`ru_ru`）。

### 本地构建
需要 **JDK 21**：
```bash
chmod +x gradlew
./gradlew build
```
构建产物位于 `build/libs/webdisplays-fabric-2.1.0-1.21.1.jar`。

---

## English

Minecraft **1.21.1** Fabric port of [WebDisplays](https://www.mcmod.cn/class/8643.html) with bundled **MCEF**, **Cloth Config**, and required **Fabric API** modules (single standalone `.jar`), rendering/CEF performance optimizations, **x86_64 + ARM64 (`aarch64`)** support, and **English / 简体中文 / Русский** in-game localization.

### Download
- **GitHub Releases (All Builds)**:
  https://github.com/elysianki4225-png/WebDisplays-Fabric-1.21.1/releases
- **Direct `.jar` Download (Current `main` Build)**:
  https://github.com/elysianki4225-png/WebDisplays-Fabric-1.21.1/releases/download/build-a7bed41/webdisplays-fabric-2.1.0-1.21.1.jar
- **Latest `main` Direct `.jar` Link (auto-updated on `main` pushes)**:
  https://github.com/elysianki4225-png/WebDisplays-Fabric-1.21.1/releases/latest/download/webdisplays-fabric-2.1.0-1.21.1.jar

> **Installation**: Drop `webdisplays-fabric-*.jar` into your Minecraft 1.21.1 `mods/` folder with Fabric Loader (>= 0.16.14). No separate MCEF, Cloth Config, or Fabric API installation is required (installing full Fabric API alongside it remains compatible).

### Highlights
1. **Single-JAR Packaging (Jar-in-Jar)**: Bundles `:mcef` (`2.1.6-1.21.1+wd.opt.1`), `cloth-config-fabric` (`15.0.140`), and all 18 required modular `fabric-*` runtime modules directly inside `META-INF/jars/`.
2. **x86_64 & ARM64 (`aarch64`) Support**:
   - `MCEFPlatform` and JCEF `org.cef.OS` detect and support `linux_amd64`, `linux_arm64`, `windows_amd64`, `windows_arm64`, `macos_amd64`, and `macos_arm64`.
   - Android/Pojav/FCL environments without desktop JCEF binaries are safely detected and skipped instead of crashing on startup.
3. **Performance & Stability Optimizations**:
   - **MCEF / JCEF**: `MCEFRenderer` reuses GPU textures via `glTexSubImage2D` and resets `GL_UNPACK_*` state; `MCEFBrowser` fixes popup strides and right/middle-click mapping; `CefRenderUpdateMixin` adaptively throttles `N_DoMessageLoopWork()`; `CefInitMixin` removes artificial startup `Thread.sleep`; `MCEFDownloader` uses 64 KB buffered streaming, timeouts/retries, Zip-Slip validation, and offline cache fallback.
   - **World & Screen Rendering**: `ScreenBaker` pre-bakes all `6 × 16` border quads and implements `FabricBakedModel` for Sodium/Indium and Vanilla; `ScreenRenderer` adds backface culling, distance culling, staggered browser creation (`maxBrowsersPerFrame`), and 500ms-hysteresis `setVisible(false)` pausing for culled screens (`pauseCulledScreens`).
   - **HUD, MinePad & Networking**: `MinePadRenderer` caches `PadID` UUID lookups; `ClientProxy` caches laser crosshair raycasts per tick; `WDNetworkRegistry` cleanly releases `RegistryFriendlyByteBuf` instances; updated all 1.21.1 datapack directories (`recipe/`, `advancement/`, `loot_table/`) and `en_us` / `zh_cn` / `ru_ru` translations.

### Building
Requires **JDK 21**:
```bash
chmod +x gradlew
./gradlew build
```
The standalone `.jar` is generated at `build/libs/webdisplays-fabric-2.1.0-1.21.1.jar`.

---

## Русский

Порт мода [WebDisplays](https://www.mcmod.cn/class/8643.html) для Minecraft **1.21.1** (Fabric) со встроенными зависимостями (**MCEF**, **Cloth Config** и нужными модулями **Fabric API** в одном `.jar`-файле), оптимизациями рендеринга и CEF, поддержкой архитектур **x86_64 и ARM64 (`aarch64`)**, а также локализацией на **русский, английский и китайский языки**.

### Ссылки для скачивания
- **Страница релизов GitHub (все сборки)**:
  https://github.com/elysianki4225-png/WebDisplays-Fabric-1.21.1/releases
- **Прямая ссылка на текущий `.jar` ветки `main`**:
  https://github.com/elysianki4225-png/WebDisplays-Fabric-1.21.1/releases/download/build-a7bed41/webdisplays-fabric-2.1.0-1.21.1.jar
- **Прямая ссылка на последнюю стабильную сборку `main` (обновляется автоматически)**:
  https://github.com/elysianki4225-png/WebDisplays-Fabric-1.21.1/releases/latest/download/webdisplays-fabric-2.1.0-1.21.1.jar

> **Установка**: просто поместите скачанный файл `webdisplays-fabric-*.jar` в папку `mods/` для Minecraft 1.21.1 с установленным Fabric Loader (>= 0.16.14). Отдельно устанавливать MCEF, Cloth Config или Fabric API не требуется (при наличии полного Fabric API совместимость сохраняется).

### Основные возможности
1. **Единый `.jar` (Jar-in-Jar)**: внутри мода уже упакованы `:mcef` (`2.1.6-1.21.1+wd.opt.1`), `cloth-config-fabric` (`15.0.140`) и все 18 необходимых модулей `fabric-*`.
2. **Поддержка x86_64 и ARM64 (`aarch64`)**:
   - `MCEFPlatform` и `org.cef.OS` поддерживают `linux_amd64`, `linux_arm64`, `windows_amd64`, `windows_arm64`, `macos_amd64` и `macos_arm64`.
   - Среды Android/Pojav/FCL без нативных библиотек JCEF определяются безопасно и пропускаются без вылета игры.
3. **Оптимизации производительности и стабильности**:
   - **MCEF / JCEF**: `MCEFRenderer` переиспользует текстуры через `glTexSubImage2D` и сбрасывает состояние `GL_UNPACK_*`; `MCEFBrowser` исправляет маппинг кнопок мыши и буферы всплывающих окон; `CefRenderUpdateMixin` ограничивает частоту `N_DoMessageLoopWork()`; в `CefInitMixin` убрана задержка `Thread.sleep`; `MCEFDownloader` использует буфер 64 КБ, таймауты, проверку Zip-Slip и локальный кэш при отсутствии сети.
   - **Рендер экранов и мира**: `ScreenBaker` заранее запекает `6 × 16` граней и реализует `FabricBakedModel` для совместимости с Sodium/Indium и ванильным рендером; `ScreenRenderer` добавляет отсечение задних сторон (`enableBackfaceCulling`), отсечение по дистанции, поэтапное создание браузеров (`maxBrowsersPerFrame`) и паузу скрытых экранов через `setVisible(false)` с гистерезисом 500 мс (`pauseCulledScreens`).
   - **Интерфейс, MinePad и сеть**: кэширование `PadID` в `MinePadRenderer`, кэширование рейкаста лазерной указки на тик в `ClientProxy`, корректное освобождение `RegistryFriendlyByteBuf` в `WDNetworkRegistry`, обновление датапака под 1.21.1 (`recipe/`, `advancement/`, `loot_table/`) и полная локализация (`en_us`, `zh_cn`, `ru_ru`).

### Сборка из исходников
Требуется **JDK 21**:
```bash
chmod +x gradlew
./gradlew build
```
Готовый `.jar` будет создан по пути `build/libs/webdisplays-fabric-2.1.0-1.21.1.jar`.
