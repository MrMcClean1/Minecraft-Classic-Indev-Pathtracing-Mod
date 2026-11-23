# CLAUDE.md

Sibling of the Tekkit, Beta and Alpha path tracers, for **Classic 0.30 survival (`c0.30-s-1858`)** and **Indev 20100223**.
The full handoff (who MrMcClean is, rules: no em dashes, commit trailer, never push, background muted tests) is in
`C:\MinecraftBetaRaytracing\tekkitsmp-pt\CLAUDE.md`. Read it first. The README has the game classes per version.

Build: `.\build.ps1` (javac --release 8, no Gradle). Install: `.\install.ps1 c0.30-s-1858`, `.\install.ps1 in-20100223`.
Shader check off screen: `tekkitsmp-pt\tools\glcheck\GlCheck.java src\assets\classicpt trace.comp temporal.comp atrous.comp compose.comp`.
Test: empty file `<instance>\.minecraft\classicpt-test\run` (the word `rtx` in it adds DLSS and Ray Reconstruction), launch the instance in the background; report in `classicpt-test\<date>\`.
NVIDIA: DLSS 5 (`kFeatureDLSS_NR`) has no Streamline plugin or header yet (SDK 2.14.16). When it ships it goes into `vk/.../NvFeatures.java` like Ray Reconstruction did (stub note in `VkNv`'s javadoc). Frame Generation and Reflex are wired as in Alpha (`VkBridge.presentFrame` before `Display.update`); the `rtx` test checks Reflex is on and that Frame Generation starts and presents through its Vulkan window, but generated frames need a focused window (Streamline: "window not focused"), which the background test never has: check in play.

## Keep in step with the other tracers

| Feature | Alpha/Beta/Tekkit | Here |
|---|---|---|
| On/off | F7 | F7 (on at start, as the others; `enabled=false` in classicpt.properties starts it off) |
| Preset | F4, Fast/Standard/Fancy | F4, same (samples, bounces, render scale) |
| Denoiser | F9, OFF/Standard/Advanced (temporal + a-trous) | F9, same names and idea (temporal.comp, atrous.comp) |
| Look | F10, Beta/Traced/Realistic | F10, same (compose.comp) |
| Photo mode | F6 when photo.enabled; samples, aperture (depth of field), sun time | F6 when `photo.enabled` (off by default), `photo.samples`, `photo.aperture` (thin lens in trace.comp, `uDof`); no sun time (one fixed sun) |
| Time-lapse | Page Down keyframe, menu Record | Page Down keyframe, Page Up record (`timelapse.*`, off by default), `TimeLapse.java`; no time of day |
| View | Home | Home (path traced, colours, normals, light) |
| Effects bits | FX_WAVING 16, FX_WAVES 32 (pt_base.glsl) | same bits + FX_CLOUDS 64 |
| Light colours, Fresnel, sway, waves | pt_base.glsl | copied into trace.comp: change both |
| Settings screen | Options > Path Tracer Settings | F12: `SettingsUi` (pages Path Tracer Settings, Effects, NVIDIA RTX, Photo/time-lapse; saves at once) shown by `gui/PtScreen`, the game's own screen class built per game against the remapped jar (build.ps1, after the bots) and reaching SettingsUi by name (the remapper only accepts game, JDK and LWJGL references). The test opens it, steps the denoiser, visits each page (settings-*.png) |
| Cloud shadows, held torch light, brightness, reflections, bounces, render scale, sharpening, trace distance | settings screen | the screen (F12) and classicpt.properties, the same key names as betapt-settings.properties (`Settings.apply` still reads the older ones); option-by-version table in HANDOFF.md |
| Sky | their own sky model | what the camera sees is the game's own sky: its sky plane (level height + 10, the level's sky colour) and clouds (height + 2, clouds.png over 2048 blocks, 0.6 blocks/s) under the linear fog the game set this frame (read from OpenGL at `afterTerrain`, `Tracer.readGameFog`), run backwards through compose's tone curve (`displayToLight` in trace.comp). The light the sky gives the world is unchanged (`sky()`). The test's sky check compares the game's and the traced sky looking up at 6 pitches (sky-*-compare.png). Note: the test's main game.png is grabbed at the terrain hook, before the game draws its sky, clouds and hand |
| DLSS, Ray Reconstruction, Frame Generation, Reflex | Effects > NVIDIA RTX | screen NVIDIA RTX... or classicpt.properties `upscaler=dlss`, `rayReconstruction`, `frameGeneration`, `lowLatency=2` (off by default); DLLs in `.minecraft\betapt-nvidia` |
| Vulkan side | VkLive (tracer + NVIDIA), VkBase, NvFeatures, VkPresent | `vk/`: VkNv (NVIDIA only, the OpenGL hand-off from VkLive), VkBase/NvFeatures/VkPresent copied with the package renamed: change both |
| Vulkan hardware tracer, DeepDVC | yes | not ported (the tracer stays OpenGL compute) |
| FSR 3.1, god rays, snow sparkle, Fast leaves, on at start | menu | `upscaler=fsr` (`Fsr3`, `Fsr3Bindings`, `FsrShaders`, AMD's shaders in assets/classicpt/fsr3, fed dlss_inputs' depth and motion), `fx.godRays`, `fx.snowSparkle`, `leaves=fast`, `enabled`: all off by default here |
| Mobs in the trace, far terrain | yes | not yet |
| Game's mobs, bots, particles, cracks, outline, hand over the picture | the picture fills the depth buffer, the game draws them after | the same since 2026-11-22: `Hooks.afterTerrain` (injected after the solid terrain pass by `tweak/Hook.hookWorld`) draws the picture with traced depth; `hideStart/hideEnd` blank the game's clouds, sky plane, water and map edge it stands in for. Before that the picture went over everything before the HUD (still the fallback if the hook isn't found) |
