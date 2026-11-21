package com.mrmcclean.classicpt;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/**
 * The tracer's settings, kept in .minecraft/classicpt.properties. The names, choices and keys follow the Alpha and
 * Beta path tracers' Path Tracer Settings (keep them in step):
 *   F7 on/off, F4 preset (Fast, Standard, Fancy), F9 denoiser (OFF, Standard, Advanced), F10 look (Beta, Traced,
 *   Realistic), F6 photo mode, Home the view (path traced, colours, normals, light).
 * The file also holds what has no key here: bounces, render scale, brightness, reflections, cloud shadows, fog
 * distance and the effects (waving plants, water waves, clouds). These old versions have no mod menu.
 */
final class Settings {
    static final String[] PRESETS = {"Fast", "Standard", "Fancy"};
    static final String[] DENOISERS = {"OFF", "Standard", "Advanced"};
    static final String[] LOOKS = {"Beta", "Traced", "Realistic"};
    static final String[] VIEWS = {"Path traced", "Colours", "Normals", "Light"};
    static final int FX_WAVING = 16, FX_WAVES = 32, FX_CLOUDS = 64; // trace.comp, the same bits as Alpha/Beta

    int preset = 1, denoiser = 2, look = 1, view = 0;
    int bounces = 2, samples = 2;
    float renderScale = 1f, brightness = 1f, cloudShadows = 0.55f, fogDistance = 0f;
    boolean reflections = true, waving = false, waves = true, clouds = true, heldLight = true;

    int effects() { return (waving ? FX_WAVING : 0) | (waves ? FX_WAVES : 0) | (clouds ? FX_CLOUDS : 0); }

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
        preset = i(p, "preset", preset); denoiser = i(p, "denoiser", denoiser); look = i(p, "look", look);
        bounces = i(p, "bounces", bounces); samples = i(p, "samples", samples);
        renderScale = f(p, "renderScale", renderScale); brightness = f(p, "brightness", brightness);
        cloudShadows = f(p, "cloudShadows", cloudShadows); fogDistance = f(p, "fogDistance", fogDistance);
        reflections = b(p, "reflections", reflections); waving = b(p, "wavingPlants", waving); waves = b(p, "waterWaves", waves);
        clouds = b(p, "clouds", clouds); heldLight = b(p, "heldLight", heldLight);
        renderScale = Math.max(0.25f, Math.min(1f, renderScale));
        bounces = Math.max(1, Math.min(4, bounces));
        samples = Math.max(1, Math.min(8, samples));
    }

    void save() {
        Properties p = new Properties();
        p.setProperty("preset", "" + preset); p.setProperty("denoiser", "" + denoiser); p.setProperty("look", "" + look);
        p.setProperty("bounces", "" + bounces); p.setProperty("samples", "" + samples);
        p.setProperty("renderScale", "" + renderScale); p.setProperty("brightness", "" + brightness);
        p.setProperty("cloudShadows", "" + cloudShadows); p.setProperty("fogDistance", "" + fogDistance);
        p.setProperty("reflections", "" + reflections); p.setProperty("wavingPlants", "" + waving); p.setProperty("waterWaves", "" + waves);
        p.setProperty("clouds", "" + clouds); p.setProperty("heldLight", "" + heldLight);
        try {
            FileOutputStream o = new FileOutputStream(file());
            p.store(o, "Classic PT (path tracer) settings. Keys: F7 on/off, F4 preset, F9 denoiser, F10 look, F6 photo, Home view."
                    + " preset 0-2 Fast/Standard/Fancy, denoiser 0-2 OFF/Standard/Advanced, look 0-2 Beta/Traced/Realistic,"
                    + " renderScale 0.25-1, fogDistance 0 = off");
            o.close();
        } catch (Exception ignored) {
        }
    }

    private static int i(Properties p, String k, int d) { try { return Integer.parseInt(p.getProperty(k).trim()); } catch (Exception e) { return d; } }
    private static float f(Properties p, String k, float d) { try { return Float.parseFloat(p.getProperty(k).trim()); } catch (Exception e) { return d; } }
    private static boolean b(Properties p, String k, boolean d) { String v = p.getProperty(k); return v == null ? d : Boolean.parseBoolean(v.trim()); }
}
