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
 *  - with the word rtx in the run file: NVIDIA DLSS super resolution (Performance, half size up to the window), then
 *    Ray Reconstruction, each checked against the native traced picture (dlss.png, rr.png);
 *  - the settings file read with the other tracers' names; photo mode (two stills of 32 samples agree far better than
 *    two of 4; depth of field changes the still); a short time-lapse (pictures, the camera put back, the MP4);
 *  - music and sound are off while it runs (in memory only, as the other tests);
 *  - writes classicpt-test/<date>/report.txt, game.png, traced.png, compare.png, then closes the game.
 */
final class Test {
    private Test() {}

    private static boolean on, done;
    private static Game g;
    private static int frames, stage, wait, tracedFrames;
    private static boolean generated;
    private static double msSum, frameMsSum;
    private static long lastFrameNs;
    private static int frameCount;
    private static BufferedImage game, traced, colours, afterBlock, dlssPic, rrPic;
    private static boolean rtx;
    private static int dlssDone, rrDone;
    private static String dlssSize = "", rrSize = "", nvAfterDlss = "", nvAfterRr = "";
    private static float keptScale;
    private static int keptUpscaler;
    private static boolean keptRr, keptFg, reflexOn;
    private static int keptLowLatency;
    private static String nvReflex = "", nvFg = "", fgPresent = "";
    // the sky check: the game's own sky and the traced one, looking up at a few pitches
    private static final float[] SKY_PITCH = {-80f, -50f, -25f, -5f, 5f, 18f};
    private static final BufferedImage[] gameSky = new BufferedImage[SKY_PITCH.length], tracedSky = new BufferedImage[SKY_PITCH.length];
    private static int skyI;
    // the settings screen check (F12): its pages as pictures, and what each step did
    private static final String[] UI_PAGES = {"Effects...", "NVIDIA RTX...", "Photo, time-lapse..."};
    private static final BufferedImage[] uiPics = new BufferedImage[1 + UI_PAGES.length];
    private static int uiI, uiGrab = -1; // uiGrab: which settings picture frame() takes (after the screen has drawn)
    private static boolean uiOpened, uiStepped, uiSaved, uiPagesOk = true, uiClosed;
    private static String uiNote = "";
    private static final List<String> lines = new ArrayList<String>();
    private static int passed, failed;
    private static int[] placed;
    private static int placedId = 41; // what was put there last: gold, then a leaf block for the Fast leaves check

    static boolean running() { return on && !done; }

    static void init(Game game) {
        g = game;
        File run = new File("classicpt-test", "run");
        if (!run.isFile()) return;
        String words = "";
        try { words = new String(java.nio.file.Files.readAllBytes(run.toPath()), "UTF-8"); } catch (Exception ignored) {}
        if (!run.delete()) return;
        if (Museum.start(words)) return; // export / museum runs (the history museum)
        on = true;
        rtx = words.toLowerCase().contains("rtx");
        mute(true);
        Hooks.log("test armed: " + g.name + (rtx ? " (with NVIDIA DLSS and Ray Reconstruction)" : ""));
    }

    // ---- quiet: the game's music and sound options (the first two switches of its options, as in options.txt) off
    private static Object options;
    private static java.lang.reflect.Field[] soundFields;
    private static boolean[] keptSound;
    private static String muteNote = "the game's options weren't found: not muted";

    private static void mute(boolean on) {
        try {
            if (options == null) {
                for (Class<?> k = g.mc.getClass(); k != null && options == null; k = k.getSuperclass())
                    for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                        if (f.getType().isPrimitive() || f.getType().isArray() || f.getType().getName().startsWith("java")) continue;
                        boolean file = false;
                        List<java.lang.reflect.Field> bools = new ArrayList<java.lang.reflect.Field>();
                        for (java.lang.reflect.Field o : f.getType().getDeclaredFields()) {
                            if (o.getType() == File.class) file = true;
                            if (o.getType() == boolean.class && !java.lang.reflect.Modifier.isStatic(o.getModifiers())) bools.add(o);
                        }
                        if (!file || bools.size() < 2) continue;
                        f.setAccessible(true);
                        options = f.get(g.mc);
                        soundFields = new java.lang.reflect.Field[]{bools.get(0), bools.get(1)};
                        for (java.lang.reflect.Field o : soundFields) o.setAccessible(true);
                        break;
                    }
                if (options == null) { Hooks.log("test: the game's options weren't found, so it isn't muted"); return; }
            }
            if (on) { keptSound = new boolean[]{soundFields[0].getBoolean(options), soundFields[1].getBoolean(options)}; soundFields[0].setBoolean(options, false); soundFields[1].setBoolean(options, false); muteNote = "music and sound off while the test runs (options " + soundFields[0].getName() + ", " + soundFields[1].getName() + " were " + keptSound[0] + ", " + keptSound[1] + ")"; Hooks.log("test: " + muteNote); }
            else if (keptSound != null) { soundFields[0].setBoolean(options, keptSound[0]); soundFields[1].setBoolean(options, keptSound[1]); }
        } catch (Throwable t) { Hooks.log("test: couldn't mute: " + t); }
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
        g.keepAlive();                                                      // nor let the test's player die
        if (uiGrab >= 0) { uiPics[uiGrab] = grab(); uiGrab = -1; }
        if (stage < 80 || stage > 89) g.closeScreen();                       // the pause menu a background window gets (not the settings screen under test)
        if (g.level() == null) {
            if (g.indev && frames > 100 && !generated) { generated = true; note("making a small level"); g.generateLevel(); }
            if (frames > 3000) finish("no level after 3000 frames");
        }
    }

    static void beforeTrace(float a) {
        if (!running() || g.level() == null || g.player() == null) return;
        if (!placedOnTop) { placedOnTop = true; onTop(); } // the saved level may have left the player in a cave
        if (towardSun) g.setView(137f - (g.indev ? 180f : 0f), -15f); // the god rays check: toward the sun (Indev's view turns by 180)
        else if (stage >= 50 && stage < 60) g.setView(135f, 89f); // the crack check: straight down at the block underfoot
        else if (stage == 70 || stage == 71) g.setView(135f, SKY_PITCH[skyI]); // the sky check: looking up
        else g.setView(135f, 18f); // look over the level, a little down
        if (stage >= 50 && stage < 60) crack(stage == 51 || stage == 53 ? 0.6f : 0f);
        switch (stage) {
            case 0: // let the game build its picture of the level
                if (++wait > 300) { stage = 70; wait = 0; skyI = 0; Hooks.enabled = false; } // after the move to the surface, the game redraws its picture there
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
            case 70: // the sky check: the game's own sky at this pitch, then the traced one
                if (++wait > 15) { gameSky[skyI] = grab(); Hooks.enabled = true; t.resetHistory(); stage = 71; wait = 0; }
                break;
            case 71:
                if (t.history() >= 24 || ++wait > 300) {
                    tracedSky[skyI] = grab();
                    Hooks.enabled = false; wait = 0;
                    if (++skyI < SKY_PITCH.length) stage = 70; else { skyI = 0; stage = 80; Hooks.enabled = true; }
                }
                break;
            case 80: // the settings screen, opened as F12 does
                if (wait++ == 0) { g.openSettings(); break; }
                if (wait < 12) break;
                String sn = g.screenName();
                uiOpened = sn != null && sn.endsWith(".PtScreen") && SettingsUi.current != null;
                if (!uiOpened) { uiNote = "open screen: " + sn; stage = 1; wait = 0; Hooks.enabled = false; break; }
                uiGrab = 0;
                { // a step on Denoiser changes the setting and saves it; two more steps put it back
                    SettingsUi ui = SettingsUi.current;
                    int before = t.settings.denoiser;
                    ui.click("Denoiser", 1);
                    uiStepped = t.settings.denoiser == (before + 1) % 3;
                    java.util.Properties pr = new java.util.Properties();
                    try (java.io.FileInputStream in = new java.io.FileInputStream("classicpt.properties")) { pr.load(in); } catch (Exception ignored) {}
                    String saved = pr.getProperty("denoise") + "/" + pr.getProperty("denoiser");
                    uiSaved = t.settings.denoiser == 0 ? "false/advanced".equals(saved) || "false/standard".equals(saved)
                            : (t.settings.denoiser == 1 ? "true/standard" : "true/advanced").equals(saved);
                    ui.click("Denoiser", 1); ui.click("Denoiser", 1);
                    uiNote = "main page: " + ui.labels();
                    if (t.settings.denoiser != before) uiStepped = false;
                }
                uiI = 0; stage = 81; wait = 0;
                break;
            case 81: // each inner page, then Back
                if (wait++ == 0) { if (!SettingsUi.current.click(UI_PAGES[uiI], 1)) uiPagesOk = false; break; }
                if (wait < 10) break;
                if (wait == 10) { uiGrab = 1 + uiI; if (!SettingsUi.current.title().equals(UI_PAGES[uiI].replace("...", ""))) uiPagesOk = false; break; }
                SettingsUi.current.click("Back", 1);
                wait = 0;
                if (++uiI >= UI_PAGES.length) stage = 82;
                break;
            case 82: // Done closes it
                if (wait++ == 0) { SettingsUi.current.click("Done", 1); break; }
                if (wait < 10) break;
                uiClosed = g.screenName() == null;
                stage = 1; wait = 0; Hooks.enabled = false;
                break;
            case 2: // add up a still picture
                if (t.failed) { finish(null); return; }
                if (Hooks.enabled) {
                    msSum += t.lastMs; tracedFrames++;
                    long now = System.nanoTime(); // whole frames, the game's part too (the window is in the background: no vsync wait)
                    if (lastFrameNs != 0) { frameMsSum += (now - lastFrameNs) / 1e6; frameCount++; }
                    lastFrameNs = now;
                }
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
                if (++wait > 60) {
                    afterBlock = grab();
                    if (!rtx) { photoStart(t); break; }
                    Settings s = t.settings; // in memory only, put back before the report
                    keptScale = s.renderScale; keptUpscaler = s.upscaler; keptRr = s.rayReconstruction;
                    s.upscaler = Settings.UPSCALER_DLSS; s.renderScale = 0.5f; s.rayReconstruction = false;
                    t.resetHistory();
                    stage = 10; wait = 0;
                }
                break;
            case 10: // DLSS super resolution: 60 frames it made
                if (t.dlssActive && t.dlssFrames >= 60 || ++wait > 900 || VkBridge.failed()) {
                    if (t.dlssActive) dlssPic = grab();
                    dlssDone = t.dlssFrames; dlssSize = t.renderSize(); nvAfterDlss = VkBridge.status;
                    t.settings.rayReconstruction = true;
                    t.resetHistory();
                    stage = 11; wait = 0;
                }
                break;
            case 11: // Ray Reconstruction: 90 frames it made
                if (t.rrActive && t.rrFrames >= 90 || ++wait > 900 || VkBridge.failed() || t.rrFailed) {
                    if (t.rrActive) rrPic = grab();
                    rrDone = t.rrFrames; rrSize = t.renderSize(); nvAfterRr = VkBridge.status;
                    keptLowLatency = t.settings.lowLatency;
                    t.settings.lowLatency = 2; // NVIDIA Reflex
                    stage = 12; wait = 0;
                }
                break;
            case 12: // Reflex: on and marking frames
                if (++wait > 40) {
                    reflexOn = VkBridge.reflexOn(); nvReflex = VkBridge.status;
                    t.settings.lowLatency = keptLowLatency;
                    keptFg = t.settings.frameGeneration;
                    t.settings.frameGeneration = true; // DLSS Frame Generation: its Vulkan window over the game's
                    stage = 13; wait = 0;
                }
                break;
            case 13:
                if (++wait > 90) {
                    fgPresent = VkBridge.presentStatus; nvFg = VkBridge.status;
                    Settings s = t.settings;
                    s.frameGeneration = keptFg;
                    s.renderScale = keptScale; s.upscaler = keptUpscaler; s.rayReconstruction = keptRr;
                    t.resetHistory();
                    photoStart(t);
                }
                break;
            case 20: case 21: case 22: case 23: case 24: { // photo mode: five stills
                if (t.photo && ++wait < 3000) break;
                File f = t.photoSaved == null ? null : new File(t.photoSaved);
                shots[stage - 20] = f;
                wait = 0;
                Settings s = t.settings;
                if (stage == 20) { check(f != null && f.isFile(), "photo mode saves a still (" + (f == null ? "none" : f.getName()) + ", focus " + Math.round(t.focus * 10) / 10.0 + " blocks)"); t.startPhoto(null, null); }
                if (stage == 21) { noise32 = diff(read(shots[0]), read(shots[1])); s.photoSamples = 4; t.startPhoto(null, null); }
                if (stage == 22) t.startPhoto(null, null);
                if (stage == 23) {
                    double d4 = diff(read(shots[2]), read(shots[3]));
                    check(d4 > 0 && noise32 < d4 / 2.0, String.format("photo adds up to a noise-free still: difference between two stills %.4f at 32 samples, %.4f at 4", noise32, d4));
                    s.photoSamples = 32; s.photoAperture = 0.2f; t.startPhoto(null, null);
                }
                if (stage == 24) {
                    double d = diff(read(shots[0]), read(shots[4]));
                    check(d > Math.max(0.004, noise32 * 2), String.format("depth of field changes the still (difference to the sharp one %.4f)", d));
                    s.photoAperture = keptAperture; s.photoSamples = keptSamples; s.photoEnabled = keptPhoto; s.clouds = keptClouds;
                    timeLapseStart(t);
                    break;
                }
                stage++;
                break;
            }
            case 30: { // the time-lapse records
                if (TimeLapse.running() && ++wait < 6000) break;
                if (TimeLapse.running()) TimeLapse.finish(g, false, t.settings);
                File d = TimeLapse.lastDir;
                int n = 0;
                for (int i = 1; i <= 4; i++) if (d != null && new File(d, String.format("frame_%04d.png", i)).isFile()) n++;
                check(n == 4, "time-lapse pictures saved, numbered: " + n + " of 4 (" + TimeLapse.status + ")");
                float[] v = g.view(1f);
                double moved = v == null || tlStart == null ? 99 : Math.abs(v[0] - tlStart[0]) + Math.abs(v[1] - tlStart[1]) + Math.abs(v[2] - tlStart[2]);
                check(moved < 0.6, String.format("the camera is back where it was after the time-lapse (%.2f blocks off; the game's own gravity may move it a little)", moved));
                stage = 31; wait = 0;
                break;
            }
            case 31: { // the video
                if (TimeLapse.encoding && ++wait < 3600) break;
                File v = TimeLapse.lastVideo;
                if (TimeLapse.status.startsWith("No ffmpeg")) note("time-lapse video: " + TimeLapse.status);
                else check(v != null && v.isFile() && v.length() > 1000, "time-lapse video made by ffmpeg: " + TimeLapse.status);
                TimeLapse.keys.clear(); TimeLapse.keys.addAll(keptKeys);
                Settings s = t.settings;
                s.timelapseEnabled = keptTl; s.timelapseFrames = keptTlFrames; s.photoSamples = keptSamples;
                optionsStart(t);
                break;
            }
            case 40: { // FSR 3.1: 60 frames it made, against the native traced picture
                if (!(t.fsrActive && t.fsrFrames >= 60) && ++wait < 900) break;
                if (t.fsrActive) fsrPic = grab();
                check(t.fsrFrames >= 60 && fsrPic != null, "FSR 3.1 upscaling made " + t.fsrFrames + " frames (render scale 50%, " + t.renderSize() + ")");
                rtxPicture(fsrPic, "FSR 3.1");
                Settings s = t.settings;
                s.upscaler = keptUpscaler; s.renderScale = keptScale;
                towardSun = true; // from the next frame
                plain = null; t.resetHistory();
                stage = 41; wait = 0;
                break;
            }
            case 41: // toward the sun (where god rays show), without them, settled
                if (t.history() < 48 && ++wait < 600) break;
                plain = grab();
                t.settings.godRays = true; t.resetHistory();
                stage = 42; wait = 0;
                break;
            case 42: { // god rays on
                if (t.history() < 48 && ++wait < 600) break;
                BufferedImage gr = grab();
                double d = diff(plain, gr), m0 = stats(plain)[0], m1 = stats(gr)[0];
                check(d > 0.002 && m1 >= m0, String.format("god rays change the picture and add light (difference %.4f, mean %.0f to %.0f)", d, m0, m1));
                godPic = gr;
                t.settings.godRays = false;
                towardSun = false;
                // a leaf block where the gold block was, for Fast leaves
                if (placed != null) { g.setTile(placed[0], placed[1], placed[2], 18); placedId = 18; }
                t.resetHistory();
                stage = 43; wait = 0;
                break;
            }
            case 43: // fancy leaves, settled
                if (t.history() < 48 && ++wait < 600) break;
                plain = grab();
                t.settings.fastLeaves = true; t.resetHistory();
                stage = 44; wait = 0;
                break;
            case 44: { // Fast leaves
                if (t.history() < 48 && ++wait < 600) break;
                BufferedImage fl = grab();
                double d = diff(plain, fl);
                check(d > 0.001, String.format("Fast leaves change how a leaf block looks (difference %.4f)", d));
                leavesPic = fl;
                t.settings.fastLeaves = false;
                // a patch of snow at the leaf block's spot, where this version has snow
                boolean snow = t.faces != null && t.faces.length > 78 && t.faces[78] != null;
                if (snow && placed != null) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                    g.setTile(Math.max(0, Math.min(g.sizeX() - 1, placed[0] + dx)), placed[1], Math.max(0, Math.min(g.sizeZ() - 1, placed[2] + dz)), 78);
                if (!snow) note("snow sparkle: no snow block (id 78) in " + g.name + ", so nothing to sparkle; the setting is still read");
                t.resetHistory();
                stage = snow ? 45 : 50; wait = 0;
                if (!snow) Hooks.enabled = false;
                break;
            }
            case 45: // snow, settled
                if (t.history() < 48 && ++wait < 600) break;
                plain = grab();
                t.settings.snowSparkle = true; t.resetHistory();
                stage = 46; wait = 0;
                break;
            case 46: { // snow sparkle on: glints show only where a texel's tilt sends the sun toward the camera
                if (t.history() < 48 && ++wait < 600) break;
                BufferedImage sp = grab();
                double[] st = stats(sp);
                note(String.format("snow sparkle: picture difference %.4f against no sparkle (glints depend on the sun's angle to the view)", diff(plain, sp)));
                check(st[0] > 25 && st[0] < 235, "the picture stays sane with snow sparkle on (mean " + Math.round(st[0]) + ")");
                t.settings.snowSparkle = false;
                stage = 50; wait = 0; Hooks.enabled = false;
                break;
            }
            // Block cracks while digging: the game draws them over the picture. Off (the game's own picture), then on.
            case 50: if (++wait > 40) { crackOff = grab(); stage = 51; wait = 0; } break;
            case 51: if (++wait > 10) { crackOn = grab(); stage = 52; wait = 0; Hooks.enabled = true; t.resetHistory(); } break;
            case 52: if (t.history() >= 32 || ++wait > 600) { tracedCrackOff = grab(); stage = 53; wait = 0; } break;
            case 53:
                if (++wait > 10) {
                    BufferedImage on = grab();
                    double game = centreDiff(crackOff, crackOn), traced = centreDiff(tracedCrackOff, on);
                    write("crack-game.png", crackOn);
                    write("crack-traced.png", on);
                    note(String.format("crack: the middle of the picture changes %.4f in the game's own picture, %.4f traced", game, traced));
                    check(game > 0.002 && traced > game * 0.3, "block cracks show over the traced picture (crack-traced.png)");
                    stage = 47; wait = 0;
                }
                break;
            case 47: // a few traced frames so the GPU's copy has every change the steps above made, then the report
                if (++wait > 30) finish(null);
                break;
            default:
                break;
        }
    }

    private static BufferedImage crackOff, crackOn, tracedCrackOff;

    /** The game's dig progress (0 none, up to 1): Indev's renderer field a, Classic's renderer field i. */
    private static void crack(float progress) {
        try {
            Object r = Game.field(g.mc.getClass(), "e").get(g.mc);
            Game.field(r.getClass(), g.indev ? "a" : "i").setFloat(r, progress);
        } catch (Exception e) {
            note("crack: " + e);
        }
    }

    /** Mean colour change in the middle third of two pictures (0..1). */
    private static double centreDiff(BufferedImage a, BufferedImage b) {
        if (a == null || b == null) return 0;
        int w = a.getWidth(), h = a.getHeight(), n = 0;
        double sum = 0;
        for (int y = h / 3; y < 2 * h / 3; y += 2)
            for (int x = w / 3; x < 2 * w / 3; x += 2) {
                int p = a.getRGB(x, y), q = b.getRGB(x, y);
                sum += Math.abs((p >> 16 & 255) - (q >> 16 & 255)) + Math.abs((p >> 8 & 255) - (q >> 8 & 255)) + Math.abs((p & 255) - (q & 255));
                n += 3;
            }
        return sum / 255.0 / Math.max(1, n);
    }

    private static final List<Object[]> extraPics = new ArrayList<Object[]>();
    private static void write(String name, BufferedImage img) { extraPics.add(new Object[]{name, img}); }

    private static boolean placedOnTop;

    /** The player on the highest block in the middle of the level, eye 1.62 above it: an open view over the level. */
    private static void onTop() {
        byte[] b = g.blocks();
        int nx = g.sizeX(), ny = g.sizeY(), nz = g.sizeZ();
        if (b == null || nx <= 0) return;
        int x = nx / 2, z = nz / 2;
        for (int y = ny - 1; y >= 0; y--)
            if (b[(y * nz + z) * nx + x] != 0) { g.setPos(x + 0.5f, y + 1 + 1.62f, z + 0.5f); note("player put on the surface at " + x + " " + (y + 1) + " " + z); return; }
    }

    // ---- the optional effects, FSR and start-on
    private static BufferedImage fsrPic, plain, godPic, leavesPic;
    private static boolean towardSun;

    private static void optionsStart(Tracer t) {
        Settings dflt = new Settings();
        check(dflt.startOn && !dflt.godRays && !dflt.snowSparkle && !dflt.fastLeaves && dflt.upscaler == Settings.UPSCALER_BASIC,
                "on at start by default (as the other versions); god rays, snow sparkle, Fast leaves and FSR off by default");
        java.util.Properties p = new java.util.Properties();
        p.setProperty("enabled", "true"); p.setProperty("fx.godRays", "true"); p.setProperty("fx.snowSparkle", "true");
        p.setProperty("leaves", "fast"); p.setProperty("upscaler", "fsr");
        Settings a = new Settings();
        a.apply(p);
        check(a.startOn && a.godRays && a.snowSparkle && a.fastLeaves && a.upscaler == Settings.UPSCALER_FSR,
                "classicpt.properties reads enabled, fx.godRays, fx.snowSparkle, leaves and upscaler=fsr");
        Settings s = t.settings;
        keptScale = s.renderScale; keptUpscaler = s.upscaler;
        s.upscaler = Settings.UPSCALER_FSR; s.renderScale = 0.5f;
        t.resetHistory();
        stage = 40; wait = 0;
    }

    // ---- photo mode and the time-lapse
    private static final File[] shots = new File[5];
    private static double noise32;
    private static boolean keptPhoto, keptTl, keptClouds;
    private static int keptSamples, keptTlFrames;
    private static float keptAperture;
    private static float[] tlStart;
    private static final List<TimeLapse.Key> keptKeys = new ArrayList<TimeLapse.Key>();

    private static void photoStart(Tracer t) {
        Settings s = t.settings; // in memory only, put back before the report
        // the settings file with the other tracers' names (and the older ones still read)
        java.util.Properties p = new java.util.Properties();
        p.setProperty("denoise", "false"); p.setProperty("look.realism", "1.0"); p.setProperty("heldLights", "0");
        p.setProperty("fx.waterWaves", "false"); p.setProperty("upscaler", "off"); p.setProperty("photo.enabled", "true");
        p.setProperty("timelapse.frames", "48"); p.setProperty("sharpen", "0.5"); p.setProperty("traceDistance", "96");
        Settings a = new Settings();
        a.renderScale = 0.5f;
        a.apply(p);
        check(a.denoiser == 0 && a.look == 2 && !a.heldLight && !a.waves && a.renderScale == 1f && a.photoEnabled && a.timelapseFrames == 48
                && a.sharpen == 0.5f && a.traceDistance == 96, "classicpt.properties takes the other tracers' option names");
        java.util.Properties old = new java.util.Properties();
        old.setProperty("denoiser", "1"); old.setProperty("look", "0"); old.setProperty("heldLight", "false"); old.setProperty("wavingPlants", "true");
        Settings b = new Settings();
        b.apply(old);
        check(b.denoiser == 1 && b.look == 0 && !b.heldLight && b.waving, "and still reads its own older names");
        Settings dflt = new Settings();
        check(!dflt.photoEnabled && !dflt.timelapseEnabled && dflt.sharpen == 0f && dflt.photoAperture == 0f, "photo mode, the time-lapse, sharpening and depth of field are off by default");
        keptPhoto = s.photoEnabled; keptSamples = s.photoSamples; keptAperture = s.photoAperture;
        keptClouds = s.clouds; s.clouds = false; // the stills are compared for noise: clouds drift between them as the game's do
        s.photoEnabled = true; s.photoSamples = 32; s.photoAperture = 0f;
        Hooks.enabled = true;
        t.startPhoto(null, null);
        stage = 20; wait = 0;
    }

    private static void timeLapseStart(Tracer t) {
        Settings s = t.settings;
        TimeLapse.ensureLoaded();
        keptKeys.clear(); keptKeys.addAll(TimeLapse.keys);
        keptTl = s.timelapseEnabled; keptTlFrames = s.timelapseFrames; keptSamples = s.photoSamples;
        s.timelapseEnabled = true; s.timelapseFrames = 4; s.photoSamples = 8;
        float[] v = g.view(1f);
        tlStart = v;
        float yaw = v[3] - (g.indev ? 180f : 0f);
        double r = Math.toRadians(yaw);
        float fx = (float) Math.sin(r), fz = (float) -Math.cos(r);
        TimeLapse.keys.clear(); // in memory only: the player's own keyframe file is left alone
        TimeLapse.keys.add(new TimeLapse.Key(v[0], v[1], v[2], yaw, v[4]));
        TimeLapse.keys.add(new TimeLapse.Key(v[0] + fx * 2, v[1] + 1, v[2] + fz * 2, yaw + 20, v[4]));
        TimeLapse.keys.add(new TimeLapse.Key(v[0] + fx * 4, v[1] + 2, v[2] + fz * 4, yaw - 25, v[4] + 5));
        String m = TimeLapse.start(g, t);
        check(TimeLapse.running(), "the time-lapse starts: " + m);
        stage = 30; wait = 0;
    }

    private static BufferedImage read(File f) {
        try { return f == null || !f.isFile() ? null : ImageIO.read(f); } catch (Exception e) { return null; }
    }

    /** Mean difference per channel (0..1) between two pictures of the same size; 1 if one is missing. */
    private static double diff(BufferedImage a, BufferedImage b) {
        if (a == null || b == null || a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return 1;
        double sum = 0; long n = 0;
        for (int y = 0; y < a.getHeight(); y++)
            for (int x = 0; x < a.getWidth(); x++) {
                int p = a.getRGB(x, y), q = b.getRGB(x, y);
                sum += Math.abs((p >> 16 & 255) - (q >> 16 & 255)) + Math.abs((p >> 8 & 255) - (q >> 8 & 255)) + Math.abs((p & 255) - (q & 255));
                n += 3;
            }
        return sum / 255.0 / Math.max(1, n);
    }

    static BufferedImage grab() {
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

    /**
     * The sky in both pictures (the top rowsPercent of rows): the mean of the pixels that are sky in both (blue at
     * least as strong as green and red, and not a near-white cloud). Returns game rgb, traced rgb, the largest channel
     * difference; null with too few sky pixels.
     */
    private static double[] skyMatch(BufferedImage a, BufferedImage b, int rowsPercent) {
        double[] s = new double[7];
        int n = 0;
        for (int y = 0; y < a.getHeight() * rowsPercent / 100; y += 2)
            for (int x = 0; x < a.getWidth(); x += 2) {
                int p = a.getRGB(x, y), q = b.getRGB(x, y);
                if (!skyLike(p) || !skyLike(q)) continue;
                for (int k = 0; k < 3; k++) { s[k] += p >> (16 - 8 * k) & 255; s[3 + k] += q >> (16 - 8 * k) & 255; }
                n++;
            }
        if (n < 100) return null;
        for (int k = 0; k < 6; k++) s[k] /= n;
        for (int k = 0; k < 3; k++) s[6] = Math.max(s[6], Math.abs(s[k] - s[3 + k]));
        return s;
    }

    private static boolean skyLike(int p) {
        int r = p >> 16 & 255, g = p >> 8 & 255, b = p & 255;
        return b >= g && g >= r - 4 && b > 120 && !(r > 245 && g > 245 && b > 245);
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

    /** An NVIDIA picture against the native traced one of the same view (afterBlock): detail, brightness, colours. */
    private static void rtxPicture(BufferedImage p, String what) {
        if (p == null || afterBlock == null) return;
        double[] sp = stats(p), sn = stats(afterBlock);
        double h = hueAgreement(afterBlock, p);
        note(String.format("%s picture: mean %.0f (native %.0f), spread %.0f (native %.0f), colours agree in %.0f%%", what, sp[0], sn[0], sp[1], sn[1], h * 100));
        check(sp[1] > 8 && Math.abs(sp[0] - sn[0]) < 0.25 * sn[0] + 8, what + "'s picture matches the native one's brightness and has detail");
        check(h >= 0.7, String.format("%s: colours agree with the native traced picture in %.0f%% of the grid (70%% needed)", what, h * 100));
    }

    private static void finish(String why) {
        if (done) return;
        Tracer t = Hooks.tracer;
        lines.add(0, "Classic PT test, " + g.name + ", " + new Date());
        lines.add(1, "     " + muteNote);
        if (why != null) check(false, why);
        check(GLContext.getCapabilities().OpenGL43, "OpenGL 4.3 compute shaders (" + GL11.glGetString(GL11.GL_RENDERER) + ", " + GL11.glGetString(GL11.GL_VERSION) + ")");
        check(t.ok, "the tracer started: " + t.status);
        byte[] blocks = g.blocks();
        if (t.copy != null && blocks != null) {
            int a = 0, b = 0;
            for (int i = 0; i < blocks.length; i++) { if (blocks[i] != 0) a++; if (t.copy[i] != 0) b++; }
            // the bots work on their own during the test: a few blocks they changed since the last frame's update may
            // not be on the GPU yet, so a handful of differences is fine; a broken copy differs in thousands
            int off = 0;
            for (int i = 0; i < blocks.length; i++) if (blocks[i] != t.copy[i]) off++;
            check(blocks.length == t.copy.length && off <= 32, "the GPU's copy of the level matches it (" + g.sizeX() + " x " + g.sizeY() + " x " + g.sizeZ() + ", " + b + " blocks, " + off + " changed since the last update)");
        } else check(false, "the level reached the GPU");
        if (placed != null) {
            int i = (placed[1] * g.sizeZ() + placed[2]) * g.sizeX() + placed[0];
            check(t.copy != null && (t.copy[i] & 255) == placedId, "a block placed at " + placed[0] + " " + placed[1] + " " + placed[2] + " reached the GPU (" + t.layersUploaded + " layers re-sent)");
        }
        if (game != null && traced != null) {
            double[] sg = stats(game), st = stats(traced);
            note(String.format("brightness: game %.0f (spread %.0f), traced %.0f (spread %.0f)", sg[0], sg[1], st[0], st[1]));
            check(st[1] > 8, "the traced picture has detail (spread " + Math.round(st[1]) + ")");
            check(st[0] > 25 && st[0] < 235, "the traced picture is neither black nor blown out (mean " + Math.round(st[0]) + ")");
            // textures: the game's picture against the tracer's Colours view (light differs on purpose: torches, shadows)
            double h = colours == null ? 0 : hueAgreement(game, colours);
            check(h >= 0.6, String.format("textures: colours agree with the game's picture in %.0f%% of the grid (60%% needed)", h * 100));
            float[] gf = t.gameFog;
            note(String.format("the game's fog (read by the tracer): colour %.0f %.0f %.0f, %.0f to %.0f blocks; sky plane at %d, camera at %.1f",
                    gf[0] * 255, gf[1] * 255, gf[2] * 255, gf[3], gf[4], g.sizeY() + 10, g.view(0)[1]));
            check(uiOpened, "settings screen: F12 opens it" + (uiOpened ? "" : " (" + uiNote + ")"));
            if (uiOpened) {
                note(uiNote);
                check(uiStepped && uiSaved, "settings screen: a click steps the denoiser and saves it to classicpt.properties, two more put it back");
                check(uiPagesOk, "settings screen: Effects, NVIDIA RTX and Photo, time-lapse open and go back");
                check(uiClosed, "settings screen: Done closes it");
            }
            double worst = 0;
            int views = 0;
            for (int i = 0; i < SKY_PITCH.length; i++) {
                double[] sky = gameSky[i] == null || tracedSky[i] == null ? null : skyMatch(gameSky[i], tracedSky[i], 100);
                if (sky == null) { note("sky at pitch " + SKY_PITCH[i] + ": no sky in both pictures"); continue; }
                views++;
                worst = Math.max(worst, sky[6]);
                note(String.format("sky at pitch %.0f: game %.0f %.0f %.0f, traced %.0f %.0f %.0f (cloud-free sky pixels in both)", SKY_PITCH[i], sky[0], sky[1], sky[2], sky[3], sky[4], sky[5]));
            }
            check(views > 0 && worst < 14, String.format("sky: the traced sky is the game's colour, looking up at %d pitches (largest difference %.0f of 255)", views, worst));
            note(String.format("tracer frame time (CPU side, GPU queued) %.2f ms over %d frames, %d lights", msSum / Math.max(1, tracedFrames), tracedFrames, t.lightCount));
            note(String.format("whole frame time while tracing %.2f ms (%.0f fps) over %d frames at %d x %d, render scale %.2f", frameMsSum / Math.max(1, frameCount), 1000.0 / Math.max(0.001, frameMsSum / Math.max(1, frameCount)), frameCount, g.width(), g.height(), t.settings.renderScale));
        } else check(false, "both pictures taken");
        if (rtx) {
            note("NVIDIA: " + (VkBridge.error != null ? VkBridge.error : nvAfterRr));
            check(!VkBridge.failed(), "the Vulkan side for NVIDIA's features started" + (VkBridge.error != null ? ": " + VkBridge.error : ""));
            check(dlssDone >= 60 && dlssPic != null, "DLSS super resolution made " + dlssDone + " frames (Performance, " + dlssSize + "; " + nvAfterDlss + ")");
            rtxPicture(dlssPic, "DLSS");
            check(rrDone >= 90 && rrPic != null, "Ray Reconstruction made " + rrDone + " frames (" + rrSize + ")" + (t.rrFailed ? ", then refused: " + nvAfterRr : ""));
            rtxPicture(rrPic, "Ray Reconstruction");
            check(reflexOn, "NVIDIA Reflex starts and marks frames (" + nvReflex + ")");
            check(fgPresent.matches("Vulkan window: \\d+x\\d+.*"), "Frame Generation starts: frames go out through its Vulkan window (" + fgPresent + "; " + nvFg + ")");
            note("Frame Generation makes frames only in a focused window (Streamline: \"window not focused\"); the test stays in the background, so generated frames are checked in play");
        }
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
            String[] uiNames = {"settings-main", "settings-effects", "settings-rtx", "settings-photo"};
            for (int i = 0; i < uiPics.length; i++) if (uiPics[i] != null) ImageIO.write(uiPics[i], "png", new File(dir, uiNames[i] + ".png"));
            for (int i = 0; i < SKY_PITCH.length; i++)
                if (gameSky[i] != null && tracedSky[i] != null) {
                    BufferedImage c = new BufferedImage(gameSky[i].getWidth(), gameSky[i].getHeight() * 2, BufferedImage.TYPE_INT_RGB);
                    c.getGraphics().drawImage(gameSky[i], 0, 0, null);
                    c.getGraphics().drawImage(tracedSky[i], 0, gameSky[i].getHeight(), null);
                    ImageIO.write(c, "png", new File(dir, "sky" + Math.round(-SKY_PITCH[i]) + "-compare.png"));
                }
            if (dlssPic != null) ImageIO.write(dlssPic, "png", new File(dir, "dlss.png"));
            if (rrPic != null) ImageIO.write(rrPic, "png", new File(dir, "rr.png"));
            for (Object[] e : extraPics) ImageIO.write((BufferedImage) e[1], "png", new File(dir, (String) e[0]));
            if (fsrPic != null) ImageIO.write(fsrPic, "png", new File(dir, "fsr.png"));
            if (godPic != null) ImageIO.write(godPic, "png", new File(dir, "god-rays.png"));
            if (leavesPic != null) ImageIO.write(leavesPic, "png", new File(dir, "fast-leaves.png"));
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
        try { for (int i = 0; i < shots.length; i++) if (shots[i] != null && shots[i].isFile()) java.nio.file.Files.copy(shots[i].toPath(), new File(dir, "photo-" + (i + 1) + ".png").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); } catch (Exception ignored) {}
        mute(false);
        Hooks.log("test done: " + passed + " passed, " + failed + " failed, report in " + dir.getAbsolutePath());
        done = true;
        System.exit(failed == 0 ? 0 : 1);
    }
}
