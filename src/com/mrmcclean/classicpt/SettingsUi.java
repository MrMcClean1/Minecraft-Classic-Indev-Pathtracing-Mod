package com.mrmcclean.classicpt;

import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

/**
 * Classic PT's settings screen (F12, or from the game's menus: none of these versions has a mod menu), laid out after
 * the Alpha/Beta/Tekkit tracers' Path Tracer Settings with the same names: a main page, Effects..., NVIDIA RTX... and
 * Photo, time-lapse.... Every change is saved to classicpt.properties at once and the picture starts adding up again.
 *
 * This class knows nothing of the game: gui/PtScreen (one per game, built against its own classes like the bots'
 * screens) is the game's screen and draws through Painter. Left click steps a choice forward, right click back; a
 * slider follows the mouse while held. Escape goes back a page, then closes.
 */
public final class SettingsUi {
    /** What the game's screen draws with (its own fill and font). */
    public interface Painter {
        void rect(int x0, int y0, int x1, int y1, int argb);
        void text(String s, int x, int y, int argb);
        int textWidth(String s);
    }

    private static final int MAIN = 0, EFFECTS = 1, RTX = 2, PHOTO = 3;
    private static final String[] TITLES = {"Path Tracer Settings", "Effects", "NVIDIA RTX", "Photo, time-lapse"};
    private static final int BW = 150, BH = 20, GAP = 4, ROW = BH + 2;

    private int page = MAIN;
    private final List<Item> items = new ArrayList<Item>();
    private boolean leftWas, rightWas;
    private Item dragging;
    private int lastW = -1, lastH = -1, lastPage = -1;
    private Item done;

    /** A button (a choice stepped by clicks) or a slider (0..1 along its width). */
    private abstract static class Item {
        int x, y, w = BW;
        final boolean slider;
        Item(boolean slider) { this.slider = slider; }
        abstract String label();
        void click(int dir) {}
        float value() { return 0; }
        void set(float v) {}
        boolean enabled() { return true; }
        boolean in(int mx, int my) { return mx >= x && mx < x + w && my >= y && my < y + BH; }
    }

    /** The screen open now (the test drives it), or the last one. */
    static SettingsUi current;

    public static SettingsUi open() { return current = new SettingsUi(); }

    // ---- for the test: what a click on a button does, without the mouse (the test never takes it)

    String title() { return TITLES[page]; }

    List<String> labels() {
        List<String> l = new ArrayList<String>();
        for (Item it : items) l.add(it.label());
        return l;
    }

    /** Clicks the first button on this page whose label starts with prefix; true when it was there. Done/Back included. */
    boolean click(String prefix, int dir) {
        for (Item it : items)
            if (it.label().startsWith(prefix)) {
                if (it == done) { if (page == MAIN) closeRequested = true; else page = MAIN; }
                else if (!it.slider) it.click(dir);
                return true;
            }
        return false;
    }

    /** A test click on Done: the screen closes on its next frame. */
    private boolean closeRequested;

    private static Settings s() { return Hooks.tracer.settings; }

    /** Saved at once, and the picture starts adding up again. */
    private static void changed() {
        s().save();
        Hooks.tracer.resetHistory();
    }

    // ------------------------------------------------------------------ the pages

    private void build(int w, int h) {
        items.clear();
        final Settings st = s();
        List<Item> l = new ArrayList<Item>();
        if (page == MAIN) {
            l.add(button(new Label() { public String get() { return "Path tracer: " + (Hooks.enabled ? "ON" : "OFF"); } }, new Step() { public void by(int d) { Hooks.toggle(); } }));
            l.add(button(new Label() { public String get() { return "Preset: " + presetName(); } }, new Step() { public void by(int d) { st.applyPreset(Math.floorMod(st.preset + d, 3)); } }));
            l.add(button(new Label() { public String get() { return "Denoiser: " + Settings.DENOISERS[st.denoiser]; } }, new Step() { public void by(int d) { st.denoiser = Math.floorMod(st.denoiser + d, 3); } }));
            l.add(button(new Label() { public String get() { return "Look: " + Settings.LOOKS[st.look]; } }, new Step() { public void by(int d) { st.look = Math.floorMod(st.look + d, 3); } }));
            l.add(button(new Label() { public String get() { return "Upscaler: " + (st.upscaler == Settings.UPSCALER_DLSS ? "NVIDIA DLSS" : st.upscaler == Settings.UPSCALER_FSR ? "AMD FSR 3.1" : "Basic"); } },
                    new Step() { public void by(int d) { int[] order = {Settings.UPSCALER_BASIC, Settings.UPSCALER_FSR, Settings.UPSCALER_DLSS}; int i = 0; for (int k = 0; k < 3; k++) if (order[k] == st.upscaler) i = k; st.upscaler = order[Math.floorMod(i + d, 3)]; } }));
            l.add(slider(new Label() { public String get() { return "Render scale: " + Math.round(st.renderScale * 100) + "%" + (st.upscaler == Settings.UPSCALER_DLSS ? " (" + dlssMode() + ")" : ""); } },
                    0.25f, 1f, 0.05f, new Get() { public float get() { return st.renderScale; } }, new Set() { public void to(float v) { st.renderScale = v; } }));
            l.add(slider(new Label() { public String get() { return "Brightness: " + Math.round(st.brightness * 100) + "%"; } },
                    0.5f, 2f, 0.05f, new Get() { public float get() { return st.brightness; } }, new Set() { public void to(float v) { st.brightness = v; } }));
            l.add(slider(new Label() { public String get() { return "Sharpening: " + (st.sharpen <= 0 ? "OFF" : Math.round(st.sharpen * 100) + "%"); } },
                    0f, 1f, 0.05f, new Get() { public float get() { return st.sharpen; } }, new Set() { public void to(float v) { st.sharpen = v; } }));
            l.add(button(new Label() { public String get() { return "Bounces: " + st.bounces; } }, new Step() { public void by(int d) { st.bounces = 1 + Math.floorMod(st.bounces - 1 + d, 4); } }));
            l.add(button(new Label() { public String get() { return "Samples: " + st.samples; } }, new Step() { public void by(int d) { st.samples = 1 + Math.floorMod(st.samples - 1 + d, 8); } }));
            l.add(button(new Label() { public String get() { return "Trace distance: " + (st.traceDistance <= 0 ? "Auto" : st.traceDistance + ""); } },
                    new Step() { public void by(int d) { int[] o = {0, 64, 128, 256, 512}; int i = 0; for (int k = 0; k < o.length; k++) if (o[k] == st.traceDistance) i = k; st.traceDistance = o[Math.floorMod(i + d, o.length)]; } }));
            l.add(button(new Label() { public String get() { return "View: " + Settings.VIEWS[st.view]; } }, new Step() { public void by(int d) { st.view = Math.floorMod(st.view + d, Settings.VIEWS.length); } }));
            l.add(button(new Label() { public String get() { return "On at start: " + (st.startOn ? "ON" : "OFF"); } }, new Step() { public void by(int d) { st.startOn = !st.startOn; } }));
            l.add(link("Effects...", EFFECTS));
            l.add(link("NVIDIA RTX...", RTX));
            l.add(link("Photo, time-lapse...", PHOTO));
        } else if (page == EFFECTS) {
            l.add(toggle("God rays", new Get() { public float get() { return st.godRays ? 1 : 0; } }, new Set() { public void to(float v) { st.godRays = v > 0; } }));
            l.add(toggle("Snow sparkle", new Get() { public float get() { return st.snowSparkle ? 1 : 0; } }, new Set() { public void to(float v) { st.snowSparkle = v > 0; } }));
            l.add(toggle("Waving plants", new Get() { public float get() { return st.waving ? 1 : 0; } }, new Set() { public void to(float v) { st.waving = v > 0; } }));
            l.add(toggle("Water waves", new Get() { public float get() { return st.waves ? 1 : 0; } }, new Set() { public void to(float v) { st.waves = v > 0; } }));
            l.add(toggle("Clouds", new Get() { public float get() { return st.clouds ? 1 : 0; } }, new Set() { public void to(float v) { st.clouds = v > 0; } }));
            l.add(slider(new Label() { public String get() { return "Cloud shadows: " + (st.cloudShadows <= 0 ? "OFF" : Math.round(st.cloudShadows * 100) + "%"); } },
                    0f, 1f, 0.05f, new Get() { public float get() { return st.cloudShadows; } }, new Set() { public void to(float v) { st.cloudShadows = v; } }));
            l.add(button(new Label() { public String get() { return "Reflect: " + (st.reflections ? "Water" : "OFF"); } }, new Step() { public void by(int d) { st.reflections = !st.reflections; } }));
            l.add(button(new Label() { public String get() { return "Torch: " + (st.heldLight ? "Yours" : "OFF"); } }, new Step() { public void by(int d) { st.heldLight = !st.heldLight; } }));
            l.add(button(new Label() { public String get() { return "Leaves: " + (st.fastLeaves ? "Fast" : "Fancy"); } }, new Step() { public void by(int d) { st.fastLeaves = !st.fastLeaves; } }));
            l.add(slider(new Label() { public String get() { return "Fog: " + (st.fogDistance <= 0 ? "OFF" : Math.round(st.fogDistance) + " blocks"); } },
                    0f, 512f, 16f, new Get() { public float get() { return st.fogDistance; } }, new Set() { public void to(float v) { st.fogDistance = v; } }));
        } else if (page == RTX) {
            l.add(button(new Label() { public String get() { return "DLSS: " + (st.upscaler == Settings.UPSCALER_DLSS ? "ON, " + dlssMode() : "OFF"); } },
                    new Step() { public void by(int d) { st.upscaler = st.upscaler == Settings.UPSCALER_DLSS ? Settings.UPSCALER_BASIC : Settings.UPSCALER_DLSS; } }));
            l.add(button(new Label() { public String get() { return "Ray Reconstruction: " + (st.rayReconstruction ? "ON" : "OFF"); } },
                    new Step() { public void by(int d) { st.rayReconstruction = !st.rayReconstruction; Hooks.tracer.rrFailed = false; } }));
            l.add(button(new Label() { public String get() { return "Frame Generation: " + (st.frameGeneration ? "ON" : "OFF"); } },
                    new Step() { public void by(int d) { st.frameGeneration = !st.frameGeneration; } }));
            l.add(button(new Label() { public String get() { return "Low latency: " + (st.lowLatency == 0 ? "OFF" : st.lowLatency == 1 ? "ON" : "NVIDIA Reflex"); } },
                    new Step() { public void by(int d) { st.lowLatency = Math.floorMod(st.lowLatency + d, 3); } }));
        } else {
            l.add(toggle("Photo mode (F6)", new Get() { public float get() { return st.photoEnabled ? 1 : 0; } }, new Set() { public void to(float v) { st.photoEnabled = v > 0; } }));
            l.add(button(new Label() { public String get() { return "Photo samples: " + st.photoSamples; } },
                    new Step() { public void by(int d) { int[] o = {32, 64, 128, 256, 512, 1024, 2048}; int i = 3; for (int k = 0; k < o.length; k++) if (o[k] == st.photoSamples) i = k; st.photoSamples = o[Math.floorMod(i + d, o.length)]; } }));
            l.add(slider(new Label() { public String get() { return "Depth of field: " + (st.photoAperture <= 0 ? "OFF" : String.format("%.2f", st.photoAperture)); } },
                    0f, Settings.MAX_APERTURE, 0.01f, new Get() { public float get() { return st.photoAperture; } }, new Set() { public void to(float v) { st.photoAperture = v; } }));
            l.add(toggle("Time-lapse (PgDn, PgUp)", new Get() { public float get() { return st.timelapseEnabled ? 1 : 0; } }, new Set() { public void to(float v) { st.timelapseEnabled = v > 0; } }));
            l.add(button(new Label() { public String get() { return "Time-lapse frames: " + st.timelapseFrames; } },
                    new Step() { public void by(int d) { int[] o = {60, 120, 240, 480, 960}; int i = 1; for (int k = 0; k < o.length; k++) if (o[k] == st.timelapseFrames) i = k; st.timelapseFrames = o[Math.floorMod(i + d, o.length)]; } }));
            l.add(button(new Label() { public String get() { return "Video fps: " + st.timelapseFps; } },
                    new Step() { public void by(int d) { int[] o = {24, 30, 60}; int i = 1; for (int k = 0; k < o.length; k++) if (o[k] == st.timelapseFps) i = k; st.timelapseFps = o[Math.floorMod(i + d, o.length)]; } }));
            l.add(toggle("Video (ffmpeg)", new Get() { public float get() { return st.timelapseVideo ? 1 : 0; } }, new Set() { public void to(float v) { st.timelapseVideo = v > 0; } }));
        }
        // two columns, centred; then Done (Back on the inner pages) under them
        int rows = (l.size() + 1) / 2;
        int top = Math.max(24, (h - (rows + 1) * ROW - 8 - (page == RTX ? 36 : 0)) / 2);
        int left = w / 2 - BW - GAP / 2;
        for (int i = 0; i < l.size(); i++) {
            Item it = l.get(i);
            it.x = left + (i % 2) * (BW + GAP);
            it.y = top + (i / 2) * ROW;
            items.add(it);
        }
        done = button(new Label() { public String get() { return page == MAIN ? "Done" : "Back"; } }, null);
        done.w = 200;
        done.x = w / 2 - 100;
        done.y = Math.min(h - BH - 4, top + rows * ROW + 6 + (page == RTX ? 36 : 0));
        items.add(done);
    }

    private static String presetName() {
        Settings st = s();
        boolean match = st.preset == 0 ? st.samples == 1 && st.bounces == 1 && st.renderScale == 0.5f
                : st.preset == 1 ? st.samples == 2 && st.bounces == 2 && st.renderScale == 1f
                : st.samples == 4 && st.bounces == 3 && st.renderScale == 1f;
        return match ? Settings.PRESETS[st.preset] : "Custom";
    }

    private static String dlssMode() {
        int m = Hooks.tracer.dlssMode();
        return m == 6 ? "DLAA" : m == 3 ? "Quality" : m == 2 ? "Balanced" : m == 1 ? "Performance" : "Ultra Performance";
    }

    private interface Label { String get(); }
    private interface Step { void by(int dir); }
    private interface Get { float get(); }
    private interface Set { void to(float v); }

    private static Item button(final Label label, final Step step) {
        return new Item(false) {
            String label() { return label.get(); }
            void click(int dir) { if (step != null) { step.by(dir); changed(); } }
        };
    }

    private static Item toggle(final String name, final Get get, final Set set) {
        return button(new Label() { public String get() { return name + ": " + (get.get() > 0 ? "ON" : "OFF"); } },
                new Step() { public void by(int d) { set.to(get.get() > 0 ? 0 : 1); } });
    }

    private static Item slider(final Label label, final float min, final float max, final float step, final Get get, final Set set) {
        return new Item(true) {
            String label() { return label.get(); }
            float value() { return (get.get() - min) / (max - min); }
            void set(float v) {
                float x = min + Math.max(0f, Math.min(1f, v)) * (max - min);
                x = Math.round(x / step) * step;
                if (Math.abs(x - get.get()) > 1e-4f) { set.to(Math.max(min, Math.min(max, x))); changed(); }
            }
        };
    }

    private Item link(final String name, final int to) {
        return new Item(false) {
            String label() { return name; }
            void click(int dir) { page = to; lastPage = -1; }
        };
    }

    // ------------------------------------------------------------------ drawing and input

    private Painter painter;

    /** gui/PtScreen as a Painter: its rect, text and textWidth, found by name (it is built against the game's classes). */
    private static Painter painterOf(final Object screen) {
        try {
            final java.lang.reflect.Method rect = screen.getClass().getMethod("rect", int.class, int.class, int.class, int.class, int.class);
            final java.lang.reflect.Method text = screen.getClass().getMethod("text", String.class, int.class, int.class, int.class);
            final java.lang.reflect.Method width = screen.getClass().getMethod("textWidth", String.class);
            return new Painter() {
                public void rect(int x0, int y0, int x1, int y1, int argb) { try { rect.invoke(screen, x0, y0, x1, y1, argb); } catch (Exception ignored) {} }
                public void text(String s, int x, int y, int argb) { try { text.invoke(screen, s, x, y, argb); } catch (Exception ignored) {} }
                public int textWidth(String s) { try { return (Integer) width.invoke(screen, s); } catch (Exception e) { return s.length() * 6; } }
            };
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("settings screen without rect/text/textWidth: " + screen.getClass().getName());
        }
    }

    /** Called by gui/PtScreen each frame: draws the screen and takes its clicks. True when Done was clicked (it closes). */
    public boolean frame(Object screen, int w, int h, int mx, int my) {
        if (painter == null) painter = painterOf(screen);
        return frame(painter, w, h, mx, my);
    }

    /** Draws the screen and takes this frame's clicks. True when Done was clicked (the screen closes). */
    public boolean frame(Painter p, int w, int h, int mx, int my) {
        if (w != lastW || h != lastH || page != lastPage) { build(w, h); lastW = w; lastH = h; lastPage = page; }
        boolean left = Mouse.isButtonDown(0), right = Mouse.isButtonDown(1);
        boolean close = closeRequested;
        closeRequested = false;
        if (dragging != null) {
            if (left) dragging.set((mx - dragging.x - 4) / (float) (dragging.w - 8));
            else dragging = null;
        } else if ((left && !leftWas) || (right && !rightWas)) {
            for (Item it : items)
                if (it.in(mx, my) && it.enabled()) {
                    if (it == done) { if (page == MAIN) close = true; else page = MAIN; }
                    else if (it.slider) { if (left) { dragging = it; it.set((mx - it.x - 4) / (float) (it.w - 8)); } }
                    else it.click(left ? 1 : -1);
                    break;
                }
        }
        leftWas = left; rightWas = right;
        if (page != lastPage && !close) { build(w, h); lastPage = page; }

        p.rect(0, 0, w, h, 0x90000000);
        String title = TITLES[page];
        p.text(title, w / 2 - p.textWidth(title) / 2, 10, 0xFFFFFF);
        for (Item it : items) draw(p, it, it.in(mx, my) || it == dragging);
        if (page == RTX) rtxStatus(p, w);
        else if (page == MAIN) {
            String hint = "Left click: next, right click: back. F7 on/off, F12 this screen.";
            p.text(hint, w / 2 - p.textWidth(hint) / 2, h - 12, 0x808080);
        }
        return close;
    }

    private static void draw(Painter p, Item it, boolean hover) {
        int x0 = it.x, y0 = it.y, x1 = it.x + it.w, y1 = it.y + BH;
        p.rect(x0, y0, x1, y1, hover ? 0xFFFFFFFF : 0xFF000000);
        if (it.slider) {
            p.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFF2A2A2A);
            int kx = x0 + 1 + Math.round(it.value() * (it.w - 10));
            p.rect(kx, y0 + 1, kx + 8, y1 - 1, hover ? 0xFFB0B0B0 : 0xFF8A8A8A);
        } else {
            p.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, hover ? 0xFF6E7BB0 : 0xFF5A5A5A);
        }
        String s = it.label();
        p.text(s, (x0 + x1) / 2 - p.textWidth(s) / 2, y0 + (BH - 8) / 2, hover ? 0xFFFFA0 : 0xE0E0E0);
    }

    /** What the NVIDIA side says, a line or two, under the buttons. */
    private void rtxStatus(Painter p, int w) {
        String st = VkBridge.status == null || VkBridge.status.isEmpty() ? "off" : VkBridge.status;
        if (!VkBridge.wanted(s())) st = "off (DLSS, Frame Generation or Reflex starts NVIDIA's side)";
        int y = done.y - 34;
        List<String> lines = new ArrayList<String>();
        String line = "";
        for (String word : ("Status: " + st).split(" ")) {
            if (p.textWidth(line + " " + word) > w - 40 && !line.isEmpty()) { lines.add(line); line = word; }
            else line = line.isEmpty() ? word : line + " " + word;
        }
        lines.add(line);
        for (int i = 0; i < Math.min(3, lines.size()); i++) p.text(lines.get(i), w / 2 - p.textWidth(lines.get(i)) / 2, y + i * 10, 0xA0A0A0);
    }

    /** Escape: back to the main page; true when already there (the screen closes). */
    public boolean back() {
        if (page == MAIN) return true;
        page = MAIN;
        return false;
    }

    /** The screen closed: the settings are already saved; nothing else to keep. */
    public void closed() { s().save(); }
}
