# CLAUDE.md

Sibling of the Tekkit, Beta and Alpha path tracers, for **Classic 0.30 survival (`c0.30-s-1858`)** and **Indev 20100223**.
The full handoff (who MrMcClean is, rules: no em dashes, commit trailer, never push, background muted tests) is in
`C:\MinecraftBetaRaytracing\tekkitsmp-pt\CLAUDE.md`. Read it first. The README has the game classes per version.

Build: `.\build.ps1` (javac --release 8, no Gradle). Install: `.\install.ps1 c0.30-s-1858`, `.\install.ps1 in-20100223`.
Shader check off screen: `tekkitsmp-pt\tools\glcheck\GlCheck.java src\assets\classicpt trace.comp temporal.comp atrous.comp compose.comp`.
Test: empty file `<instance>\.minecraft\classicpt-test\run` (the word `rtx` in it adds DLSS and Ray Reconstruction), launch the instance in the background; report in `classicpt-test\<date>\`.
NVIDIA: DLSS 5 (`kFeatureDLSS_NR`) has no Streamline plugin or header yet (SDK 2.14.16). When it ships it goes into `vk/.../NvFeatures.java` like Ray Reconstruction did (stub note in `VkNv`'s javadoc). Frame Generation and Reflex are wired as in Alpha (`VkBridge.presentFrame` before `Display.update`) but not verified here: DLSS-G needs a focused window, which the background test never has.

## Keep in step with the other tracers

| Feature | Alpha/Beta/Tekkit | Here |
|---|---|---|
| On/off | F7 | F7 (off at start) |
| Preset | F4, Fast/Standard/Fancy | F4, same (samples, bounces, render scale) |
| Denoiser | F9, OFF/Standard/Advanced (temporal + a-trous) | F9, same names and idea (temporal.comp, atrous.comp) |
| Look | F10, Beta/Traced/Realistic | F10, same (compose.comp) |
| Photo mode | F6 | F6, 512 frames, PNG in screenshots/ |
| View | Home | Home (path traced, colours, normals, light) |
| Effects bits | FX_WAVING 16, FX_WAVES 32 (pt_base.glsl) | same bits + FX_CLOUDS 64 |
| Light colours, Fresnel, sway, waves | pt_base.glsl | copied into trace.comp: change both |
| Cloud shadows, held torch light, brightness, reflections, bounces, render scale | settings screen | classicpt.properties (no mod menu in these versions) |
| DLSS, Ray Reconstruction, Frame Generation, Reflex | Effects > NVIDIA RTX | classicpt.properties `upscaler=dlss`, `rayReconstruction`, `frameGeneration`, `lowLatency=2` (off by default); DLLs in `.minecraft\betapt-nvidia` |
| Vulkan side | VkLive (tracer + NVIDIA), VkBase, NvFeatures, VkPresent | `vk/`: VkNv (NVIDIA only, the OpenGL hand-off from VkLive), VkBase/NvFeatures/VkPresent copied with the package renamed: change both |
| Vulkan hardware tracer, DeepDVC | yes | not ported (the tracer stays OpenGL compute) |
| Mobs in the trace, far terrain | yes | not yet |
