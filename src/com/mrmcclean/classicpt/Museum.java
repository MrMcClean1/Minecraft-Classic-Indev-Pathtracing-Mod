package com.mrmcclean.classicpt;

import org.lwjgl.input.Mouse;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Two more uses of the test's run file (.minecraft/classicpt-test/run), for the history museum
 * (tekkitsmp-pt/tools/museum). Both need no input and close the game when done:
 *   export <file>                               save the level the game makes at start (Classic's generator needs
 *                                               the game's window), with the game's own writer
 *   museum <level file> <x y z yaw pitch>...    load that level, and from each view take the game's picture and the
 *                                               traced one (museum-N-game.png, museum-N-traced.png, museum-N.png
 *                                               side by side) in classicpt-test/
 */
final class Museum {
    private Museum() {}

    private static String mode;
    private static String file;
    private static final List<float[]> views = new ArrayList<float[]>();
    private static boolean loaded, done;
    private static int frames, view, stage, wait;
    private static BufferedImage game;
    private static File dir;

    static boolean running() { return mode != null && !done; }

    /** From Test.init: the run file's words. */
    static boolean start(String words) {
        String[] w = words.trim().split("\\s+");
        if (w.length < 2 || !(w[0].equals("export") || w[0].equals("museum"))) return false;
        mode = w[0];
        file = w[1];
        for (int i = 2; i + 5 <= w.length; i += 5)
            views.add(new float[]{Float.parseFloat(w[i]), Float.parseFloat(w[i + 1]), Float.parseFloat(w[i + 2]), Float.parseFloat(w[i + 3]), Float.parseFloat(w[i + 4])});
        dir = new File("classicpt-test");
        Hooks.log("museum run: " + mode + " " + file + ", " + views.size() + " views");
        return true;
    }

    static void frame(Game g) {
        if (!running()) return;
        frames++;
        if (Mouse.isCreated() && Mouse.isGrabbed()) Mouse.setGrabbed(false);
        g.closeScreen();
        if (mode.equals("export")) {
            if (g.level() == null) { if (g.indev && frames == 100) g.generateLevel(); return; }
            if (frames < 200) return;
            try { g.saveLevel(new File(file)); Hooks.log("exported the level to " + file); }
            catch (Exception e) { Hooks.log("export failed: " + e); }
            finish();
            return;
        }
        if (!loaded && frames > 100) {
            try { g.setLevel(g.loadLevel(new File(file))); loaded = true; Hooks.log("museum level loaded"); }
            catch (Exception e) { Hooks.log("could not load " + file + ": " + e); finish(); }
        }
        if (frames > 6000) finish();
    }

    static void beforeTrace(Game g) {
        if (!running() || !loaded || g.level() == null || g.player() == null) return;
        if (view >= views.size()) { finish(); return; }
        float[] v = views.get(view);
        g.setPos(v[0], v[1], v[2]);
        g.setView(v[3], v[4]);
        if (stage == 0 && ++wait > 120) { game = Test.grab(); Hooks.enabled = true; Hooks.tracer.resetHistory(); stage = 1; wait = 0; }
    }

    static void afterTrace(Game g) {
        if (!running() || !loaded || stage != 1) return;
        if (Hooks.tracer.history() >= 128 || ++wait > 900) {
            BufferedImage traced = Test.grab();
            try {
                ImageIO.write(game, "png", new File(dir, "museum-" + view + "-game.png"));
                ImageIO.write(traced, "png", new File(dir, "museum-" + view + "-traced.png"));
                BufferedImage c = new BufferedImage(game.getWidth() * 2, game.getHeight(), BufferedImage.TYPE_INT_RGB);
                c.getGraphics().drawImage(game, 0, 0, null);
                c.getGraphics().drawImage(traced, game.getWidth(), 0, null);
                ImageIO.write(c, "png", new File(dir, "museum-" + view + ".png"));
                Hooks.log("museum view " + view + " saved");
            } catch (Exception e) {
                Hooks.log("could not save museum pictures: " + e);
            }
            Hooks.enabled = false;
            view++;
            stage = 0;
            wait = 0;
        }
    }

    private static void finish() {
        done = true;
        Hooks.log("museum run done");
        System.exit(0);
    }
}
