package com.mrmcclean.classicpt;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Time-lapse recorder, as in the Tekkit, Beta and Alpha tracers (timelapse.enabled in classicpt.properties, off by
 * default; no menu in these versions): Page Down sets a keyframe where you stand and look, Page Up records. The
 * camera follows a Catmull-Rom spline through the keyframes with the look direction turned along the shortest arc
 * (slerp); each frame is a photo of photo.samples samples, saved as screenshots/timelapse-<date>/frame_0001.png...,
 * and ffmpeg (from the PATH) makes timelapse.mp4 afterwards. Neither Classic nor Indev's tracer has a time of day,
 * so keyframes hold position and view only. Keyframes are kept in classicpt-timelapse.txt.
 */
final class TimeLapse {
    private TimeLapse() {}

    /** A keyframe: the eye, and the view angles as the player's own fields hold them (degrees). */
    static final class Key {
        final float x, y, z, yaw, pitch;
        Key(float x, float y, float z, float yaw, float pitch) { this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch; }
    }
    static final List<Key> keys = new ArrayList<Key>();
    private static boolean loaded, running;
    private static int index, frames;
    private static File dir;
    private static Key kept;
    private static volatile boolean advance;
    static volatile File lastDir, lastVideo;
    static volatile String status = "";
    static volatile boolean encoding;

    static boolean running() { return running; }
    static int index() { return index; }

    private static File keyFile() { return new File("classicpt-timelapse.txt"); }

    static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        keys.clear();
        try {
            if (!keyFile().isFile()) return;
            for (String line : java.nio.file.Files.readAllLines(keyFile().toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\\s+");
                if (p.length >= 5) keys.add(new Key(Float.parseFloat(p[0]), Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3]), Float.parseFloat(p[4])));
            }
        } catch (Exception e) { Hooks.log("time-lapse keyframes: " + e); }
    }

    private static void save() {
        StringBuilder sb = new StringBuilder("# time-lapse keyframes: x y z (eye) yaw pitch\n");
        for (Key k : keys) sb.append(String.format(Locale.ROOT, "%.4f %.4f %.4f %.3f %.3f%n", k.x, k.y, k.z, k.yaw, k.pitch));
        try { java.nio.file.Files.write(keyFile().toPath(), sb.toString().getBytes("UTF-8")); } catch (Exception e) { Hooks.log("time-lapse keyframes: " + e); }
    }

    /** The player's eye and view angles now, as a keyframe (Indev's view turns by 180 in Game.view: taken off again). */
    private static Key here(Game g) {
        float[] v = g.view(1f);
        return v == null ? null : new Key(v[0], v[1], v[2], v[3] - (g.indev ? 180f : 0f), v[4]);
    }

    static String addKey(Game g) {
        ensureLoaded();
        Key k = here(g);
        if (k == null) return "No level";
        keys.add(k);
        save();
        return String.format(Locale.ROOT, "Time-lapse keyframe %d at %.0f %.0f %.0f (Page Up records)", keys.size(), k.x, k.y, k.z);
    }

    // ---- the path
    /** The camera at u (0..1): eye x, y, z, yaw, pitch. */
    static float[] at(double u) {
        int n = keys.size();
        if (n == 1) { Key k = keys.get(0); return new float[]{k.x, k.y, k.z, k.yaw, k.pitch}; }
        double s = Math.max(0, Math.min(1, u)) * (n - 1);
        int i = Math.min(n - 2, (int) Math.floor(s));
        double t = s - i;
        Key k0 = keys.get(Math.max(0, i - 1)), k1 = keys.get(i), k2 = keys.get(i + 1), k3 = keys.get(Math.min(n - 1, i + 2));
        double[] a = dir(k1.yaw, k1.pitch), b = dir(k2.yaw, k2.pitch), d = slerp(a, b, t);
        // Classic and Indev: yaw turns from -z toward +x, pitch down is positive
        float yaw = (float) Math.toDegrees(Math.atan2(d[0], -d[2])), pitch = (float) Math.toDegrees(-Math.asin(Math.max(-1, Math.min(1, d[1]))));
        return new float[]{(float) cr(k0.x, k1.x, k2.x, k3.x, t), (float) cr(k0.y, k1.y, k2.y, k3.y, t), (float) cr(k0.z, k1.z, k2.z, k3.z, t), yaw, pitch};
    }

    static double cr(double p0, double p1, double p2, double p3, double t) {
        return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (-p0 + 3 * p1 - 3 * p2 + p3) * t * t * t);
    }

    static double[] dir(float yawDeg, float pitchDeg) {
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        return new double[]{Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), -Math.cos(yaw) * Math.cos(pitch)};
    }

    static double[] slerp(double[] a, double[] b, double t) {
        double c = Math.max(-1, Math.min(1, a[0] * b[0] + a[1] * b[1] + a[2] * b[2]));
        double th = Math.acos(c);
        if (th < 1e-4) return a;
        double sa = Math.sin((1 - t) * th) / Math.sin(th), sb = Math.sin(t * th) / Math.sin(th);
        return new double[]{a[0] * sa + b[0] * sb, a[1] * sa + b[1] * sb, a[2] * sa + b[2] * sb};
    }

    // ---- recording
    static String start(Game g, Tracer tr) {
        ensureLoaded();
        if (keys.size() < 2) return "A time-lapse needs two keyframes (Page Down)";
        if (!Hooks.enabled) return "The time-lapse needs the path tracer on (F7)";
        dir = new File(new File("screenshots"), "timelapse-" + new java.text.SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new java.util.Date()));
        dir.mkdirs();
        kept = here(g);
        frames = tr.settings.timelapseFrames;
        index = 0; advance = false; lastVideo = null; lastDir = dir;
        running = true;
        setup(g, tr);
        status = "Recording " + frames + " pictures to " + dir.getName() + " (Page Up stops)";
        return status;
    }

    private static float[] pose;

    private static void setup(Game g, Tracer tr) {
        pose = at(frames <= 1 ? 0 : index / (double) (frames - 1));
        place(g);
        tr.startPhoto(new File(dir, String.format(Locale.ROOT, "frame_%04d.png", index + 1)), new Runnable() { public void run() { advance = true; } });
    }

    private static void place(Game g) { g.setPos(pose[0], pose[1], pose[2]); g.setView(pose[3], pose[4]); }

    /** Every frame before the trace: the next picture once the last is saved; the camera held on the path. */
    static void beforeTrace(Game g, Tracer tr) {
        if (!running) return;
        if (advance) {
            advance = false;
            if (++index >= frames) { finish(g, true, tr.settings); return; }
            setup(g, tr);
        }
        place(g);
    }

    static void finish(Game g, boolean done, Settings st) {
        if (!running) return;
        running = false;
        if (kept != null) { g.setPos(kept.x, kept.y, kept.z); g.setView(kept.yaw, kept.pitch); }
        status = (done ? "Time-lapse done: " : "Time-lapse stopped: ") + index + " pictures in " + dir.getName();
        Hooks.say(status);
        if (done && st.timelapseVideo && index > 1) encode(dir, st.timelapseFps);
    }

    /** ffmpeg (from the PATH) turns the numbered pictures into timelapse.mp4, on a background thread. */
    static void encode(final File d, final int rate) {
        encoding = true;
        Thread t = new Thread(new Runnable() { public void run() {
            File out = new File(d, "timelapse.mp4");
            try {
                Process p = new ProcessBuilder("ffmpeg", "-y", "-loglevel", "error", "-framerate", Integer.toString(rate), "-i",
                        new File(d, "frame_%04d.png").getPath(), "-vf", "pad=ceil(iw/2)*2:ceil(ih/2)*2", "-c:v", "libx264", "-pix_fmt", "yuv420p", out.getPath())
                        .redirectErrorStream(true).redirectOutput(new File(d, "ffmpeg.log")).start();
                int code = p.waitFor();
                if (code == 0 && out.isFile()) { lastVideo = out; status = "Time-lapse video: " + out.getName() + " in " + d.getName(); }
                else status = "ffmpeg failed (exit " + code + "): see ffmpeg.log in " + d.getName();
            } catch (java.io.IOException e) {
                status = "No ffmpeg on the PATH: the pictures are in " + d.getName() + " (no video)";
            } catch (Exception e) {
                status = "ffmpeg: " + e;
            }
            Hooks.log("time-lapse: " + status);
            encoding = false;
        } }, "classicpt-timelapse-ffmpeg");
        t.setDaemon(true);
        t.start();
    }
}
