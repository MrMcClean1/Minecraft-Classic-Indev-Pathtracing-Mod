package com.mrmcclean.classicpt;

import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Classic PT's test (like AlphaTest): an empty file .minecraft/classicpt-test/run arms it for one launch (it is
 * removed when the test starts). It needs no input and never grabs the mouse, so it can run behind other windows:
 *  - Indev: makes a small level from the title screen (Classic starts in one);
 *  - looks over the level, takes the game's own picture, turns the tracer on, lets it add up, takes the traced one;
 *  - checks: OpenGL 4.3, the shader, the level copy on the GPU, a traced picture with detail and sane brightness,
 *    colours that agree with the game's picture (a 16 x 9 grid of hues; shadows and light differ on purpose), the
 *    F7 toggle, and that a placed block reaches the GPU;
 *  - writes classicpt-test/<date>/report.txt, game.png, traced.png, compare.png, then closes the game.
 */
final class Test {
    private Test() {}

    private static boolean on, done;
    private static Game g;
    private static int frames, stage, wait, tracedFrames;
    private static boolean generated;
    private static double msSum;
    private static BufferedImage game, traced, colours, afterBlock;
    private static final List<String> lines = new ArrayList<String>();
    private static int passed, failed;
    private static int[] placed;

    static boolean running() { return on && !done; }

    static void init(Game game) {
        g = game;
        File run = new File("classicpt-test", "run");
        if (run.isFile() && run.delete()) {
            on = true;
            Hooks.log("test armed: " + g.name);
        }
    }

    private static void check(boolean ok, String what) {
        if (ok) passed++; else failed++;
        lines.add((ok ? "PASS " : "FAIL ") + what);
        Hooks.log((ok ? "PASS " : "FAIL ") + what);
    }

    private static void note(String s) { lines.add("     " + s); Hooks.log(s); }

    /** Every frame (menus too). */
    static void frame() {
        if (!running()) return;
        frames++;
        if (Mouse.isCreated() && Mouse.isGrabbed()) Mouse.setGrabbed(false); // never take the mouse from MrMcClean
        g.closeScreen();                                                    // the pause menu a background window gets
        if (g.level() == null) {
            if (g.indev && frames > 100 && !generated) { generated = true; note("making a small level"); g.generateLevel(); }
            if (frames > 3000) finish("no level after 3000 frames");
        }
    }

    static void beforeTrace(float a) {
        if (!running() || g.level() == null || g.player() == null) return;
        g.setView(135f, 18f); // look over the level, a little down
        switch (stage) {
            case 0: // let the game build its picture of the level
                if (++wait > 150) { stage = 1; wait = 0; Hooks.enabled = false; }
                break;
            case 1:
                if (++wait > 30) { game = grab(); stage = 2; wait = 0; Hooks.enabled = true; Hooks.tracer.resetHistory(); }
                break;
            default:
                break;
        }
    }

    static void afterTrace(float a) {
        if (!running() || g.level() == null || g.player() == null) return;
        Tracer t = Hooks.tracer;
        switch (stage) {
            case 2: // add up a still picture
                if (t.failed) { finish(null); return; }
                if (Hooks.enabled) { msSum += t.lastMs; tracedFrames++; }
                if (t.history() >= 48 || ++wait > 600) { traced = grab(); stage = 5; wait = 0; t.settings.view = 1; t.resetHistory(); }
                break;
            case 5: // the Colours view: textures only, no light, for the colour check
                if (++wait > 20) { colours = grab(); t.settings.view = 0; t.resetHistory(); stage = 3; wait = 0; }
                break;
            case 3: { // a block in view: does the GPU get it?
                float[] v = g.view(a);
                double yaw = Math.toRadians(v[3]);
                int x = (int) Math.floor(v[0] + Math.sin(yaw) * 4), y = (int) Math.floor(v[1]), z = (int) Math.floor(v[2] - Math.cos(yaw) * 4);
                x = Math.max(0, Math.min(g.sizeX() - 1, x)); y = Math.max(0, Math.min(g.sizeY() - 1, y)); z = Math.max(0, Math.min(g.sizeZ() - 1, z));
                placed = new int[]{x, y, z};
                g.setTile(x, y, z, 41);
                stage = 4;
                break;
            }
            case 4:
                if (++wait > 60) { afterBlock = grab(); finish(null); }
                break;
            default:
                break;
        }
    }

    private static BufferedImage grab() {
        int w = g.width(), h = g.height();
        ByteBuffer b = BufferUtils.createByteBuffer(w * h * 4);
        GL11.glReadBuffer(GL11.GL_BACK);
        GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, b);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int i = ((h - 1 - y) * w + x) * 4;
                img.setRGB(x, y, (b.get(i) & 255) << 16 | (b.get(i + 1) & 255) << 8 | (b.get(i + 2) & 255));
            }
        return img;
    }

    private static double[] stats(BufferedImage img) {
        double s = 0, s2 = 0;
        int n = 0;
        for (int y = 0; y < img.getHeight(); y += 2)
            for (int x = 0; x < img.getWidth(); x += 2) {
                int p = img.getRGB(x, y);
                double l = 0.299 * (p >> 16 & 255) + 0.587 * (p >> 8 & 255) + 0.114 * (p & 255);
                s += l; s2 += l * l; n++;
            }
        double m = s / n;
        return new double[]{m, Math.sqrt(Math.max(0, s2 / n - m * m))};
    }

    /** Share of grid cells whose colour (r, g, b over their sum) agrees between the two pictures. */
    private static double hueAgreement(BufferedImage a, BufferedImage b) {
        int gx = 16, gy = 9, agree = 0, cells = 0;
        for (int cy = 0; cy < gy; cy++)
            for (int cx = 0; cx < gx; cx++) {
                double[] ca = cell(a, cx, cy, gx, gy), cb = cell(b, cx, cy, gx, gy);
                if (ca == null || cb == null) continue;
                cells++;
                if (Math.abs(ca[0] - cb[0]) < 0.07 && Math.abs(ca[1] - cb[1]) < 0.07) agree++;
            }
        return cells == 0 ? 0 : agree / (double) cells;
    }

    private static double[] cell(BufferedImage img, int cx, int cy, int gx, int gy) {
        int x0 = cx * img.getWidth() / gx, x1 = (cx + 1) * img.getWidth() / gx, y0 = cy * img.getHeight() / gy, y1 = (cy + 1) * img.getHeight() / gy;
        double r = 0, gg = 0, bb = 0;
        for (int y = y0; y < y1; y += 2)
            for (int x = x0; x < x1; x += 2) { int p = img.getRGB(x, y); r += p >> 16 & 255; gg += p >> 8 & 255; bb += p & 255; }
        double s = r + gg + bb;
        if (s < (x1 - x0) * (y1 - y0) / 4.0 * 30) return null; // too dark to have a colour
        return new double[]{r / s, gg / s};
    }

    private static void finish(String why) {
        if (done) return;
        Tracer t = Hooks.tracer;
        lines.add(0, "Classic PT test, " + g.name + ", " + new Date());
        if (why != null) check(false, why);
        check(GLContext.getCapabilities().OpenGL43, "OpenGL 4.3 compute shaders (" + GL11.glGetString(GL11.GL_RENDERER) + ", " + GL11.glGetString(GL11.GL_VERSION) + ")");
        check(t.ok, "the tracer started: " + t.status);
        byte[] blocks = g.blocks();
        if (t.copy != null && blocks != null) {
            int a = 0, b = 0;
            for (int i = 0; i < blocks.length; i++) { if (blocks[i] != 0) a++; if (t.copy[i] != 0) b++; }
            check(java.util.Arrays.equals(blocks, t.copy), "the GPU's copy of the level matches it (" + g.sizeX() + " x " + g.sizeY() + " x " + g.sizeZ() + ", " + b + " blocks)");
        } else check(false, "the level reached the GPU");
        if (placed != null) {
            int i = (placed[1] * g.sizeZ() + placed[2]) * g.sizeX() + placed[0];
            check(t.copy != null && (t.copy[i] & 255) == 41, "a block placed at " + placed[0] + " " + placed[1] + " " + placed[2] + " reached the GPU (" + t.layersUploaded + " layers re-sent)");
        }
        if (game != null && traced != null) {
            double[] sg = stats(game), st = stats(traced);
            note(String.format("brightness: game %.0f (spread %.0f), traced %.0f (spread %.0f)", sg[0], sg[1], st[0], st[1]));
            check(st[1] > 8, "the traced picture has detail (spread " + Math.round(st[1]) + ")");
            check(st[0] > 25 && st[0] < 235, "the traced picture is neither black nor blown out (mean " + Math.round(st[0]) + ")");
            // textures: the game's picture against the tracer's Colours view (light differs on purpose: torches, shadows)
            double h = colours == null ? 0 : hueAgreement(game, colours);
            check(h >= 0.6, String.format("textures: colours agree with the game's picture in %.0f%% of the grid (60%% needed)", h * 100));
            note(String.format("tracer frame time (CPU side, GPU queued) %.2f ms over %d frames, %d lights", msSum / Math.max(1, tracedFrames), tracedFrames, t.lightCount));
        } else check(false, "both pictures taken");
        boolean before = Hooks.enabled;
        Hooks.toggle();
        boolean flipped = Hooks.enabled != before;
        Hooks.toggle();
        check(flipped && Hooks.enabled == before, "F7's toggle turns it off and on again");
        File dir = new File("classicpt-test", new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date()));
        dir.mkdirs();
        try {
            if (game != null) ImageIO.write(game, "png", new File(dir, "game.png"));
            if (traced != null) ImageIO.write(traced, "png", new File(dir, "traced.png"));
            if (afterBlock != null) ImageIO.write(afterBlock, "png", new File(dir, "traced-with-block.png"));
            if (colours != null) ImageIO.write(colours, "png", new File(dir, "colours-view.png"));
            if (game != null && traced != null) {
                BufferedImage c = new BufferedImage(game.getWidth(), game.getHeight() * 2, BufferedImage.TYPE_INT_RGB);
                c.getGraphics().drawImage(game, 0, 0, null);
                c.getGraphics().drawImage(traced, 0, game.getHeight(), null);
                ImageIO.write(c, "png", new File(dir, "compare.png"));
            }
            lines.add("RESULT " + passed + " passed, " + failed + " failed");
            PrintWriter w = new PrintWriter(new FileOutputStream(new File(dir, "report.txt")));
            for (String s : lines) w.println(s);
            w.close();
        } catch (Exception e) {
            Hooks.log("could not write the report: " + e);
        }
        Hooks.log("test done: " + passed + " passed, " + failed + " failed, report in " + dir.getAbsolutePath());
        done = true;
        System.exit(failed == 0 ? 0 : 1);
    }
}
