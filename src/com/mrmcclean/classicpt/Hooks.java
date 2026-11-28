package com.mrmcclean.classicpt;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * Called from the game's own bytecode (see tweak.Hook). The path tracer is off when the game starts. Keys, the same
 * as the Alpha and Beta path tracers': F7 on/off, F4 preset, F9 denoiser, F10 look, F6 photo mode, Home the view,
 * Page Down a time-lapse keyframe and Page Up records (photo mode and the time-lapse when switched on in the file);
 * F12 opens the Path Tracer Settings screen (SettingsUi), where everything in the file can be set.
 * Each change shows in the game's chat (and the log) and is saved to classicpt.properties.
 */
public final class Hooks {
    private Hooks() {}

    static final int[] KEYS = {Keyboard.KEY_F7, Keyboard.KEY_F4, Keyboard.KEY_F9, Keyboard.KEY_F10, Keyboard.KEY_F6, Keyboard.KEY_HOME,
            Keyboard.KEY_NEXT, Keyboard.KEY_PRIOR, Keyboard.KEY_F12};
    static Game game;
    static Tracer tracer;
    static boolean enabled;
    private static final boolean[] wasDown = new boolean[KEYS.length];
    private static boolean broken;

    private static boolean setup(Object owner) {
        if (broken) return false;
        if (game == null) {
            game = Game.of(owner);
            if (game == null) { broken = true; return false; }
            tracer = new Tracer(game);
            enabled = tracer.settings.startOn; // `enabled` in classicpt.properties: on from the start (on by default)
            log("loaded for " + game.name + "; F7 turns the path tracer on and off");
            Test.init(game);
        }
        return true;
    }

    /** The level loader met an entity id it doesn't know: Classic Bots' bot, or null. */
    public static Object entity(Object level, String id) {
        try { return Bots.entity(level, id); } catch (Throwable t) { log("bots: " + t); return null; }
    }

    /** Each frame, before Display.update (menus too). */
    public static void frame(Object owner) {
        try {
            if (setup(owner)) Bots.frame(game);
        } catch (Throwable t) {
            botsError(t);
        }
        try {
            if (setup(owner)) {
                Test.frame();
                Museum.frame(game);
                if (tracer.settings.frameGeneration) VkBridge.presentFrame(tracer.settings, game.width(), game.height()); // NVIDIA Frame Generation
            }
        } catch (Throwable t) {
            log("frame hook: " + t);
        }
    }

    // ------------------------------------------------------------------ Classic's arrow key (Tab is the bot list now)

    private static Object arrowBinding;

    /**
     * Classic's options make their key bindings array: one more, "Fire arrow" (Q), so it shows in Controls, can be
     * rebound there and is saved in options.txt like the others. Classic fired arrows with Tab, a fixed key.
     */
    public static Object bindings(Object array) {
        try {
            Class<?> kb = array.getClass().getComponentType();
            int n = java.lang.reflect.Array.getLength(array);
            Object out = java.lang.reflect.Array.newInstance(kb, n + 1);
            System.arraycopy(array, 0, out, 0, n);
            arrowBinding = kb.getConstructor(String.class, int.class).newInstance("Fire arrow", Keyboard.KEY_Q);
            java.lang.reflect.Array.set(out, n, arrowBinding);
            return out;
        } catch (Throwable t) {
            log("arrow key binding: " + t);
            return array;
        }
    }

    /** The key Classic fires an arrow with (in place of its Tab). */
    public static int arrowKey() {
        try { if (arrowBinding != null) return arrowBinding.getClass().getField("b").getInt(arrowBinding); } catch (Throwable ignored) {}
        return Keyboard.KEY_Q;
    }

    private static boolean botsErrorWritten;

    /** The bots failed: the log, and (once, with the stack) classicbots-test/error.txt while a bot test folder is there. */
    static void botsError(Throwable t) {
        log("bots: " + t);
        if (botsErrorWritten || !new java.io.File("classicbots-test").isDirectory()) return;
        botsErrorWritten = true;
        if (t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null) t = t.getCause();
        try (java.io.PrintStream p = new java.io.PrintStream(new java.io.FileOutputStream(new java.io.File("classicbots-test", "error.txt"), true))) { t.printStackTrace(p); } catch (Exception ignored) {}
    }

    /** True from afterTerrain to beforeHud: this frame's picture is already down, under the game's mobs and hand. */
    private static boolean early, drawnEarly;

    /**
     * Right after the game's solid terrain, before its mobs, particles, block cracks, water, clouds and hand: the traced
     * picture goes down here with its depth, so the game draws the bots, mobs, cracks, outline and hand over it.
     */
    public static void afterTerrain(float partial, Object owner) {
        early = true;
        drawnEarly = false;
        try {
            if (!setup(owner)) return;
            trace(partial, true);
        } catch (Throwable t) {
            stopped(t);
        }
    }

    /** Around the game's own clouds, sky and water: drawn into nothing while the traced picture stands in for them. */
    public static void hideStart() {
        if (drawnEarly) { GL11.glColorMask(false, false, false, false); GL11.glDepthMask(false); }
    }

    public static void hideEnd() {
        if (drawnEarly) { GL11.glColorMask(true, true, true, true); GL11.glDepthMask(true); }
    }

    /** After the world is drawn, before the HUD. */
    public static void beforeHud(float partial, Object owner) {
        boolean wasEarly = early;
        early = drawnEarly = false;
        try {
            if (!setup(owner)) return;
            if (!wasEarly) trace(partial, false); // no terrain hook this frame: the picture over everything, as before
            Test.afterTrace(partial);
            Museum.afterTrace(game);
        } catch (Throwable t) {
            stopped(t);
        }
    }

    private static void trace(float partial, boolean under) {
        if (!Test.running() && !Museum.running() && Keyboard.isCreated() && game.noScreen()) keys();
        Test.beforeTrace(partial);
        TimeLapse.beforeTrace(game, tracer); // a recording time-lapse holds the camera on its path
        Museum.beforeTrace(game);
        if (!enabled) return;
        tracer.underGame = under;
        boolean drawn = tracer.render(partial);
        tracer.underGame = false;
        if (under) drawnEarly = drawn;
    }

    private static void stopped(Throwable t) {
        log("path tracer stopped: " + t);
        t.printStackTrace();
        enabled = false;
        drawnEarly = false;
    }

    private static void keys() {
        for (int i = 0; i < KEYS.length; i++) {
            boolean down = Keyboard.isKeyDown(KEYS[i]);
            if (down && !wasDown[i]) press(i);
            wasDown[i] = down;
        }
    }

    private static void press(int i) {
        Settings s = tracer.settings;
        switch (i) {
            case 0: toggle(); return;
            case 1: s.applyPreset((s.preset + 1) % 3); say("Path tracer preset: " + Settings.PRESETS[s.preset]); break;
            case 2: s.denoiser = (s.denoiser + 1) % 3; say("Path tracer denoiser: " + Settings.DENOISERS[s.denoiser]); break;
            case 3: s.look = (s.look + 1) % Settings.LOOKS.length; say("Path tracer look: " + Settings.LOOKS[s.look]); break;
            case 4:
                if (!s.photoEnabled) say("Photo mode is off: photo.enabled=true in classicpt.properties");
                else if (enabled) tracer.startPhoto(); else say("Photo mode needs the path tracer on (F7)");
                return;
            case 6: say(s.timelapseEnabled ? TimeLapse.addKey(game) : "Time-lapse is off: timelapse.enabled=true in classicpt.properties"); return;
            case 7:
                if (!s.timelapseEnabled) say("Time-lapse is off: timelapse.enabled=true in classicpt.properties");
                else if (TimeLapse.running()) TimeLapse.finish(game, false, s);
                else say(TimeLapse.start(game, tracer));
                return;
            case 5: s.view = (s.view + 1) % Settings.VIEWS.length; say("Path tracer view: " + Settings.VIEWS[s.view]); break;
            case 8: game.openSettings(); return;
        }
        s.save();
        tracer.resetHistory();
    }

    static void toggle() {
        enabled = !enabled;
        if (enabled) { tracer.resetHistory(); tracer.rrFailed = false; } // Ray Reconstruction gets another try
        say("Path tracer: " + (enabled ? "ON" : "OFF") + (tracer.failed ? " (" + tracer.status + ")" : ""));
    }

    static void say(String s) {
        log(s);
        if (game != null && !Test.running() && !Museum.running()) game.message(s);
    }

    static void log(String s) { System.out.println("[ClassicPT] " + s); }
}
