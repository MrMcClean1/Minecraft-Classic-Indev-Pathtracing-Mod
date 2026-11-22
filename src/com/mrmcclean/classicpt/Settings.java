package com.mrmcclean.classicpt;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/**
 * The tracer's settings, kept in .minecraft/classicpt.properties. The names, choices and keys follow the Tekkit, Beta
 * and Alpha path tracers' betapt-settings.properties (keep them in step; the table in HANDOFF.md lists each option by
 * version): F7 on/off, F4 preset (Fast, Standard, Fancy), F9 denoiser (OFF, Standard, Advanced), F10 look (Beta,
 * Traced, Realistic), F6 photo mode (when photo.enabled), Home the view (path traced, colours, normals, light),
 * Page Down a time-lapse keyframe and Page Up records it (when timelapse.enabled).
 * The file also holds what has no key here (these old versions have no mod menu): bounces, render scale,
 * brightness, sharpening, reflections, cloud shadows, fog and trace distance, the effects, photo and time-lapse.
 * NVIDIA RTX (as the other tracers' Effects > NVIDIA RTX, all off by default, an RTX card and the DLLs in
 * .minecraft/betapt-nvidia needed): upscaler=dlss (its mode from renderScale), rayReconstruction, frameGeneration,
 * lowLatency 0 off / 1 on / 2 NVIDIA Reflex.
 * Older files' own names (denoiser 0-2, look 0-2, heldLight, wavingPlants, waterWaves) still load.
 */
final class Settings {
    static final String[] PRESETS = {"Fast", "Standard", "Fancy"};
    static final String[] DENOISERS = {"OFF", "Standard", "Advanced"};
    static final String[] LOOKS = {"Beta", "Traced", "Realistic"};
    static final String[] VIEWS = {"Path traced", "Colours", "Normals", "Light"};
    static final int UPSCALER_BASIC = 0, UPSCALER_DLSS = 1, UPSCALER_FSR = 2;
    // trace.comp, the same bits as Alpha/Beta (pt_base.glsl) where they have them
    static final int FX_SPARKLE = 4, FX_GODRAYS = 8, FX_WAVING = 16, FX_WAVES = 32, FX_CLOUDS = 64, FX_FASTLEAVES = 128;

    int preset = 1, denoiser = 2, look = 1, view = 0;
    int bounces = 2, samples = 2;
    float renderScale = 1f, brightness = 1f, cloudShadows = 0.55f, fogDistance = 0f;
    boolean reflections = true, waving = false, waves = true, clouds = true, heldLight = true;
    int upscaler = UPSCALER_BASIC, lowLatency = 0;
    boolean rayReconstruction = false, frameGeneration = false;
    /** Sharpening after the picture is made (AMD RCAS, as the other tracers): 0 = off (the default here). */
    float sharpen = 0f;
    /** How far camera rays look, in blocks: 0 = as far as the level goes (Auto). */
    int traceDistance = 0;
    // photo mode (F6) and the time-lapse recorder, off by default as everywhere
    boolean photoEnabled = false;
    int photoSamples = 256;
    float photoAperture = 0f; // lens radius in blocks, 0 = no depth of field
    static final float MAX_APERTURE = 0.5f;
    boolean timelapseEnabled = false, timelapseVideo = true;
    int timelapseFrames = 120, timelapseFps = 30;
    /** On when the game starts (`enabled`; off by default here, where the others start on). */
    boolean startOn = false;
    /** Optional effects, off by default here: sun shafts, snow and ice glinting in the sun, solid (Fast) leaves. */
    boolean godRays = false, snowSparkle = false, fastLeaves = false;

    int effects() {
        return (waving ? FX_WAVING : 0) | (waves ? FX_WAVES : 0) | (clouds ? FX_CLOUDS : 0) | (godRays ? FX_GODRAYS : 0)
                | (snowSparkle ? FX_SPARKLE : 0) | (fastLeaves ? FX_FASTLEAVES : 0);
    }

    /** The presets set samples, bounces and render scale, as in the other tracers. */
    void applyPreset(int p) {
        preset = p;
        if (p == 0) { samples = 1; bounces = 1; renderScale = 0.5f; }
        else if (p == 1) { samples = 2; bounces = 2; renderScale = 1f; }
        else { samples = 4; bounces = 3; renderScale = 1f; }
    }

    private static File file() { return new File("classicpt.properties"); }

    void load() {
        Properties p = new Properties();
        try { FileInputStream in = new FileInputStream(file()); p.load(in); in.close(); } catch (Exception ignored) { return; }
        apply(p);
    }

    /** A set of saved settings (the other tracers' names, or this one's older ones); anything missing stays as it is. */
    void apply(Properties p) {
        preset = i(p, "preset", preset);
        // denoiser: the other tracers' denoise=true/false + denoiser=standard/advanced, or the older 0-2
        String dn = p.getProperty("denoiser");
        if (dn != null) {
            dn = dn.trim().toLowerCase();
            denoiser = dn.equals("standard") ? 1 : dn.equals("advanced") ? 2 : i(p, "denoiser", denoiser);
        }
        if (p.getProperty("denoise") != null && !b(p, "denoise", true)) denoiser = 0;
        else if (p.getProperty("denoise") != null && denoiser == 0) denoiser = 2;
        // look: the other tracers' look.realism 0..1 (0 Beta, 0.25 their default, 1 realistic), or the older 0-2
        if (p.getProperty("look.realism") != null) { float r = f(p, "look.realism", 0.25f); look = r < 0.125f ? 0 : r < 0.625f ? 1 : 2; }
        else look = i(p, "look", look);
        bounces = i(p, "bounces", bounces); samples = i(p, "samples", samples);
        renderScale = f(p, "renderScale", renderScale); brightness = f(p, "brightness", brightness);
        cloudShadows = f(p, "cloudShadows", cloudShadows); fogDistance = f(p, "fogDistance", fogDistance);
        String rf = p.getProperty("reflections");
        if (rf != null) { rf = rf.trim(); reflections = rf.equals("1") || rf.equals("2") || rf.equalsIgnoreCase("true"); }
        waving = b(p, "fx.wavingPlants", b(p, "wavingPlants", waving));
        waves = b(p, "fx.waterWaves", b(p, "waterWaves", waves));
        clouds = b(p, "clouds", clouds);
        if (p.getProperty("heldLights") != null) heldLight = i(p, "heldLights", 2) > 0; // 0 off, 1 yours, 2 everyone's (single player: the same)
        else heldLight = b(p, "heldLight", heldLight);
        String up = String.valueOf(p.getProperty("upscaler")).trim();
        upscaler = "dlss".equalsIgnoreCase(up) ? UPSCALER_DLSS : "fsr".equalsIgnoreCase(up) ? UPSCALER_FSR : UPSCALER_BASIC;
        lowLatency = Math.max(0, Math.min(2, i(p, "lowLatency", lowLatency)));
        rayReconstruction = b(p, "rayReconstruction", rayReconstruction); frameGeneration = b(p, "frameGeneration", frameGeneration);
        sharpen = Math.max(0f, Math.min(1f, f(p, "sharpen", sharpen)));
        traceDistance = Math.max(0, Math.min(1024, i(p, "traceDistance", traceDistance)));
        photoEnabled = b(p, "photo.enabled", photoEnabled);
        photoSamples = Math.max(1, Math.min(65536, i(p, "photo.samples", photoSamples)));
        photoAperture = Math.max(0f, Math.min(MAX_APERTURE, f(p, "photo.aperture", photoAperture)));
        timelapseEnabled = b(p, "timelapse.enabled", timelapseEnabled);
        timelapseFrames = Math.max(2, Math.min(100000, i(p, "timelapse.frames", timelapseFrames)));
        timelapseFps = Math.max(1, Math.min(240, i(p, "timelapse.fps", timelapseFps)));
        timelapseVideo = b(p, "timelapse.video", timelapseVideo);
        startOn = b(p, "enabled", startOn);
        godRays = b(p, "fx.godRays", godRays);
        snowSparkle = b(p, "fx.snowSparkle", snowSparkle);
        if (p.getProperty("leaves") != null) fastLeaves = "fast".equalsIgnoreCase(p.getProperty("leaves").trim());
        renderScale = Math.max(0.25f, Math.min(1f, renderScale));
        if ("off".equalsIgnoreCase(up)) renderScale = 1f; // the others' "off": native size
        bounces = Math.max(1, Math.min(4, bounces));
        samples = Math.max(1, Math.min(8, samples));
    }

    void save() {
        Properties p = new Properties();
        p.setProperty("preset", "" + preset);
        p.setProperty("denoise", "" + (denoiser != 0)); p.setProperty("denoiser", denoiser == 1 ? "standard" : "advanced");
        p.setProperty("look.realism", look == 0 ? "0.0" : look == 1 ? "0.25" : "1.0");
        p.setProperty("bounces", "" + bounces); p.setProperty("samples", "" + samples);
        p.setProperty("renderScale", "" + renderScale); p.setProperty("brightness", "" + brightness); p.setProperty("sharpen", "" + sharpen);
        p.setProperty("cloudShadows", "" + cloudShadows); p.setProperty("fogDistance", "" + fogDistance); p.setProperty("traceDistance", "" + traceDistance);
        p.setProperty("reflections", reflections ? "1" : "0"); p.setProperty("fx.wavingPlants", "" + waving); p.setProperty("fx.waterWaves", "" + waves);
        p.setProperty("clouds", "" + clouds); p.setProperty("heldLights", heldLight ? "2" : "0");
        p.setProperty("upscaler", upscaler == UPSCALER_DLSS ? "dlss" : upscaler == UPSCALER_FSR ? "fsr" : "basic"); p.setProperty("lowLatency", "" + lowLatency);
        p.setProperty("enabled", "" + startOn); p.setProperty("fx.godRays", "" + godRays); p.setProperty("fx.snowSparkle", "" + snowSparkle);
        p.setProperty("leaves", fastLeaves ? "fast" : "fancy");
        p.setProperty("rayReconstruction", "" + rayReconstruction); p.setProperty("frameGeneration", "" + frameGeneration);
        p.setProperty("photo.enabled", "" + photoEnabled); p.setProperty("photo.samples", "" + photoSamples); p.setProperty("photo.aperture", "" + photoAperture);
        p.setProperty("timelapse.enabled", "" + timelapseEnabled); p.setProperty("timelapse.frames", "" + timelapseFrames);
        p.setProperty("timelapse.fps", "" + timelapseFps); p.setProperty("timelapse.video", "" + timelapseVideo);
        try {
            FileOutputStream o = new FileOutputStream(file());
            p.store(o, "Classic PT (path tracer) settings, the same names as the other path tracers'. Keys: F7 on/off, F4 preset, F9 denoiser,"
                    + " F10 look, F6 photo (photo.enabled), Home view, Page Down time-lapse keyframe, Page Up record (timelapse.enabled)."
                    + " preset 0-2 Fast/Standard/Fancy, renderScale 0.25-1, fogDistance 0 = off, traceDistance 0 = Auto, sharpen 0-1;"
                    + " photo.aperture 0-0.5 blocks (depth of field); enabled = on at start; upscaler basic/fsr/dlss (FSR 3.1, any GPU);"
                    + " fx.godRays, fx.snowSparkle, leaves fast/fancy; NVIDIA RTX: rayReconstruction, frameGeneration,"
                    + " lowLatency 0-2 (2 = Reflex)");
            o.close();
        } catch (Exception ignored) {
        }
    }

    private static int i(Properties p, String k, int d) { try { return Integer.parseInt(p.getProperty(k).trim()); } catch (Exception e) { return d; } }
    private static float f(Properties p, String k, float d) { try { return Float.parseFloat(p.getProperty(k).trim()); } catch (Exception e) { return d; } }
    private static boolean b(Properties p, String k, boolean d) { String v = p.getProperty(k); return v == null ? d : Boolean.parseBoolean(v.trim()); }
}
