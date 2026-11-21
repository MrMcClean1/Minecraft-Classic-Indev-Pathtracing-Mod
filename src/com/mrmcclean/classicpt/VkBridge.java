package com.mrmcclean.classicpt;

import org.lwjgl.opengl.GL11;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;

/**
 * The door to the Vulkan side (vk/, VkNv): NVIDIA DLSS, Ray Reconstruction, Frame Generation and Reflex, as in the
 * Alpha/Beta/Tekkit tracers' VkBridge. Classic and Indev run LWJGL 2; Vulkan needs LWJGL 3 and both use the same
 * package names, so the Vulkan code and LWJGL 3 ship in our jar as assets/classicpt/vk/*.bin, are unpacked to
 * .minecraft/classicpt/vk/ and load in a class loader of their own. The two sides pass only plain Java values
 * (OpenGL texture names, numbers, arrays, strings, maps).
 *
 * Everything here is off unless classicpt.properties asks for it (upscaler=dlss, lowLatency=2, frameGeneration).
 * The NVIDIA DLLs are the other tracers': .minecraft/betapt-nvidia (Streamline SDK 2.14.16, NVIDIA's own files).
 */
final class VkBridge {
    private VkBridge() {}

    private static final String[] FILES = {"classicpt-vk", "lwjgl", "lwjgl-natives-windows", "lwjgl-vulkan", "lwjgl-opengl", "lwjgl-opengl-natives-windows"};
    private static URLClassLoader loader;
    private static Object live;
    private static String liveKey;
    private static boolean failed;
    /** Why the Vulkan side stopped or what the NVIDIA features say (the log and the test report). */
    static String status = "off", error;
    private static Method mDlss, mDlssSizes, mDlssReady, mDlssRR, mRrSizes, mRrReady, mReflex, mReflexOn, mNvStatus, mPresent, mStopPresent, mPresentStatus, mClose;

    private static synchronized ClassLoader loader() throws Exception {
        if (loader != null) return loader;
        File dir = new File("classicpt", "vk").getAbsoluteFile();
        dir.mkdirs();
        // LWJGL 3 lists a device's extensions on its 64 KB thread stack when it makes a Vulkan instance (NVIDIA has
        // about 250): 1 MB, set before LWJGL 3 first loads
        if (System.getProperty("org.lwjgl.system.stackSize") == null) System.setProperty("org.lwjgl.system.stackSize", "1024");
        URL[] urls = new URL[FILES.length];
        byte[] buf = new byte[65536];
        for (int i = 0; i < FILES.length; i++) {
            File target = new File(dir, FILES[i] + ".jar");
            InputStream s = VkBridge.class.getResourceAsStream("/assets/classicpt/vk/" + FILES[i] + ".bin");
            if (s == null) throw new IllegalStateException("missing " + FILES[i] + " in the Classic PT jar");
            FileOutputStream o = new FileOutputStream(target);
            for (int n; (n = s.read(buf)) > 0; ) o.write(buf, 0, n);
            o.close();
            s.close();
            urls[i] = target.toURI().toURL();
        }
        loader = new URLClassLoader(urls, ClassLoader.getSystemClassLoader().getParent()); // can't see LWJGL 2
        return loader;
    }

    /** LWJGL 2's natives folder (org.lwjgl.librarypath) is hidden while LWJGL 3 loads its own native code. */
    private interface Call<T> { T run() throws Exception; }
    private static <T> T withLwjgl3(Call<T> c) throws Exception {
        String lwjgl2Path = System.getProperty("org.lwjgl.librarypath");
        System.clearProperty("org.lwjgl.librarypath");
        try {
            return c.run();
        } finally {
            if (lwjgl2Path != null) System.setProperty("org.lwjgl.librarypath", lwjgl2Path);
        }
    }

    static boolean wanted(Settings s) { return s.upscaler == Settings.UPSCALER_DLSS || s.lowLatency == 2 || s.frameGeneration; }

    /**
     * Opens the Vulkan side with what the settings ask for, or again when that changed (Streamline's device is kept
     * for the whole game, so a reopen is quick). False when it isn't running (reason in error).
     */
    static boolean ensure(Settings s) {
        if (failed) return false;
        final boolean dlss = s.upscaler == Settings.UPSCALER_DLSS, rr = dlss && s.rayReconstruction, fg = dlss && s.frameGeneration, reflex = s.lowLatency == 2;
        String key = dlss + "," + rr + "," + fg + "," + reflex;
        if (live != null && key.equals(liveKey)) return true;
        try {
            close();
            withLwjgl3(new Call<Object>() { public Object run() throws Exception {
                Class<?> c = loader().loadClass("com.mrmcclean.classicpt.vk.VkNv");
                Map<String, Object> in = new HashMap<String, Object>();
                in.put("dlss", dlss); in.put("rr", rr); in.put("fg", fg); in.put("reflex", reflex);
                live = c.getMethod("open", Map.class).invoke(null, in);
                mDlss = c.getMethod("dlss", int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, float[].class, boolean.class);
                mDlssSizes = c.getMethod("dlssSizes", int.class, int.class, int.class);
                mDlssReady = c.getMethod("dlssReady");
                mDlssRR = c.getMethod("dlssRR", int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, float[].class, boolean.class);
                mRrSizes = c.getMethod("rrSizes", int.class, int.class, int.class);
                mRrReady = c.getMethod("rrReady");
                mReflex = c.getMethod("reflexFrame", int.class);
                mReflexOn = c.getMethod("reflexOn");
                mNvStatus = c.getMethod("nvStatus");
                mPresent = c.getMethod("present", int.class, int.class, int.class, boolean.class);
                mStopPresent = c.getMethod("stopPresent");
                mPresentStatus = c.getMethod("presentStatus");
                mClose = c.getMethod("close");
                return null;
            }});
            liveKey = key;
            status = (String) mNvStatus.invoke(live);
            @SuppressWarnings("unchecked") Map<String, Object> info = (Map<String, Object>) live.getClass().getMethod("info").invoke(live);
            Hooks.log("NVIDIA features (" + info.get("device") + ", " + info.get("sync") + "): " + status);
            return true;
        } catch (Throwable t) {
            Throwable cause = t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null ? t.getCause() : t;
            error = cause.toString();
            failed = true;
            status = "Vulkan side failed: " + error;
            Hooks.log(status);
            cause.printStackTrace();
            live = null;
            return false;
        }
    }

    static boolean failed() { return failed; }

    private static boolean call(Method m) {
        try { return live != null && m != null && (Boolean) m.invoke(live); } catch (Throwable t) { return false; }
    }

    static boolean dlssReady() { return call(mDlssReady); }
    static boolean rrReady() { return call(mRrReady); }

    static int[] dlssSizes(final int mode, final int ow, final int oh) { return sizes(mDlssSizes, mode, ow, oh); }
    static int[] rrSizes(final int mode, final int ow, final int oh) { return sizes(mRrSizes, mode, ow, oh); }

    private static int[] sizes(final Method m, final int mode, final int ow, final int oh) {
        if (live == null || m == null) return null;
        try { return withLwjgl3(new Call<int[]>() { public int[] run() throws Exception { return (int[]) m.invoke(live, mode, ow, oh); } }); }
        catch (Throwable t) { return null; }
    }

    /** DLSS super resolution: the OpenGL texture with the window-sized picture, or 0 (the reason in status). */
    static int dlss(final int color, final int depth, final int motion, final int rw, final int rh, final int ow, final int oh, final int mode, final float[] c, final boolean reset) {
        if (live == null) return 0;
        try {
            int t = withLwjgl3(new Call<Integer>() { public Integer run() throws Exception { return (Integer) mDlss.invoke(live, color, depth, motion, rw, rh, ow, oh, mode, c, reset); } });
            status = (String) mNvStatus.invoke(live);
            return t;
        } catch (Throwable t) { status = "DLSS: " + t; return 0; }
    }

    /** Ray Reconstruction: the OpenGL texture with the denoised, window-sized HDR picture (RGBA16F), or 0. */
    static int dlssRR(final int color, final int depth, final int motion, final int albedo, final int specular, final int normal,
                      final int rw, final int rh, final int ow, final int oh, final int mode, final float[] c, final boolean reset) {
        if (live == null) return 0;
        try {
            int t = withLwjgl3(new Call<Integer>() { public Integer run() throws Exception {
                return (Integer) mDlssRR.invoke(live, color, depth, motion, albedo, specular, normal, rw, rh, ow, oh, mode, c, reset); } });
            status = (String) mNvStatus.invoke(live);
            return t;
        } catch (Throwable t) { status = "Ray Reconstruction: " + t; return 0; }
    }

    static boolean reflexOn() { return call(mReflexOn); }

    static void reflexFrame(int phase) {
        if (live == null || mReflex == null) return;
        try { mReflex.invoke(live, phase); } catch (Throwable ignored) {}
    }

    // ---- Frame Generation: the finished frame (world and HUD) goes out through a Vulkan swap chain (VkPresent)
    private static int presentTex, presentW, presentH;
    private static boolean presenting;
    static String presentStatus = "";

    /** Just before the game swaps its window: with Frame Generation on, the back buffer goes out through Vulkan. */
    static void presentFrame(Settings s, int w, int h) {
        boolean want = s.frameGeneration && s.upscaler == Settings.UPSCALER_DLSS && Hooks.enabled && live != null && mPresent != null;
        if (!want) { if (presenting) stopPresent(); return; }
        if (w <= 0 || h <= 0) return;
        int prev = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        if (presentTex == 0 || w != presentW || h != presentH) {
            if (presentTex != 0) GL11.glDeleteTextures(presentTex);
            presentTex = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, presentTex);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR); // one level: complete
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, 0x813D, 0); // GL_TEXTURE_MAX_LEVEL
            presentW = w; presentH = h;
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, presentTex);
        GL11.glReadBuffer(GL11.GL_BACK);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prev);
        try {
            final int tw = w, th = h;
            boolean ok = withLwjgl3(new Call<Boolean>() { public Boolean run() throws Exception { return (Boolean) mPresent.invoke(live, presentTex, tw, th, false); } });
            String st = (String) mPresentStatus.invoke(live);
            if (!st.equals(presentStatus)) Hooks.log(st);
            presentStatus = st;
            presenting = ok;
        } catch (Throwable t) { presentStatus = "Vulkan window: " + t; Hooks.log("Vulkan window stopped: " + t); stopPresent(); }
    }

    private static void stopPresent() {
        presenting = false;
        try { if (live != null && mStopPresent != null) mStopPresent.invoke(live); } catch (Throwable ignored) {}
    }

    /** Closes the Vulkan side (it opens again when the settings ask for it). */
    static void close() {
        if (live == null) return;
        try {
            withLwjgl3(new Call<Object>() { public Object run() throws Exception { mClose.invoke(live); return null; } });
        } catch (Throwable t) { Hooks.log("Vulkan side close: " + t); }
        live = null;
        liveKey = null;
        presenting = false;
    }
}
