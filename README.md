# WebDisplays Fabric 1.21.1

Minecraft **1.21.1** Fabric port of [WebDisplays](https://www.mcmod.cn/class/8643.html) with embedded **MCEF** + **Cloth Config** + required **Fabric API** modules (single standalone `.jar`), rendering/CEF performance optimizations, and **x86_64 + ARM64 (`aarch64`)** support.

## Highlights

### 1. Single-JAR Packaging (Jar-in-Jar)
You only need to drop the built `webdisplays-fabric-*.jar` into `mods/`. The output JAR bundles (`include`):
- Embedded `:mcef` subproject (`mcef` 2.1.6-1.21.1+wd.opt.1)
- `cloth-config-fabric` (`15.0.140`)
- All required modular `fabric-*` runtime modules (`fabric-api-base`, `fabric-block-view-api-v2`, `fabric-command-api-v2`, `fabric-events-interaction-v0`, `fabric-item-group-api-v1`, `fabric-key-binding-api-v1`, `fabric-lifecycle-events-v1`, `fabric-message-api-v1`, `fabric-model-loading-api-v1`, `fabric-networking-api-v1`, `fabric-object-builder-api-v1`, `fabric-registry-sync-v0`, `fabric-renderer-api-v1`, `fabric-renderer-indigo`, `fabric-rendering-v1`, `fabric-resource-loader-v0`, `fabric-screen-api-v1`, `fabric-transitive-access-wideners-v1`)

> Note: Installing full Fabric API alongside this mod remains compatible; the bundled modules satisfy all standalone runtime dependencies when Fabric API is not installed separately.

### 2. Architecture Support (`x86_64` & `ARM64 / aarch64`)
- `MCEFPlatform` and JCEF `org.cef.OS` detect and support:
  - `linux_amd64`, `linux_arm64`
  - `windows_amd64`, `windows_arm64`
  - `macos_amd64`, `macos_arm64`
- Android/Pojav/FCL-style environments without desktop JCEF binaries are detected safely (`isAndroidRuntime()`) and skipped gracefully instead of crashing with an uncaught `UnsatisfiedLinkError`.
- Native library loading in `CefApp` handles optional Windows DLLs safely and catches `Throwable` around `System.load`.

### 3. Performance & Stability Optimizations
- **MCEF / JCEF Rendering & Texture Upload**:
  - `MCEFRenderer` reuses allocated GL textures (`glTexSubImage2D` instead of reallocating `glTexImage2D` every paint) and resets `GL_UNPACK_ROW_LENGTH` / `SKIP_PIXELS` / `SKIP_ROWS` / `ALIGNMENT` before uploads to avoid state corruption.
  - `MCEFBrowser` fixes initial texture ID handling, popup buffer sizing & stride, and recursive drag handling.
  - `CefRenderUpdateMixin` throttles `N_DoMessageLoopWork()` adaptively instead of spinning unconstrained every render frame.
  - `CefInitMixin` removes the artificial startup `Thread.sleep`.
  - `MCEFDownloader` uses 64 KB buffered streaming, single-connection redirect handling, timeouts/retries, and Zip-Slip path validation.
- **WebDisplays World & Screen Rendering**:
  - `ScreenBaker` pre-bakes all `6 x 16` screen border `BakedQuad`s and implements `FabricBakedModel` for fast Sodium/Indium and Vanilla chunk meshing; `ModelBlockRendererMixin` cleans up its `ThreadLocal` context on return.
  - `ScreenRenderer` performs screen backface culling, distance culling, staggered browser creation (`maxBrowsersPerFrame`), automatic `setVisible(false)` CEF pause hysteresis for culled screens (`pauseCulledScreens`), uninitialized-MCEF guards, and restores `GL_DEPTH_TEST`.
  - `MinePadRenderer` caches `PadID` UUID lookups instead of copying NBT every frame.
  - `ClientProxy` caches laser crosshair raycasts per tick, avoids redundant NBT copies and block-outline toggles, and respects `MCEF.isInitialized()`.
  - `WDNetworkRegistry` safely releases `RegistryFriendlyByteBuf` instances in `finally` blocks and checks client world readiness.
  - Fixed Minecraft 1.21.1 datapack directories (`recipe/`, `advancement/`, `loot_table/`), `"id"` item result fields, `ADV_PAD_BREAK` identifier (`webdisplays:pad_break`), `MouseHandlerMixin` action filtering, and `ClientConfig`/`WDConfigScreen` defaults.

## Building

Requires **JDK 21** and internet access to Maven repositories (`maven.fabricmc.net`, `maven.shedaniel.me`, `mcef-download.cinemamod.com`, `repo.maven.apache.org`):

```bash
chmod +x gradlew
./gradlew build
```

The standalone bundled `.jar` is output to `build/libs/webdisplays-fabric-<version>.jar`.
