package com.mrmcclean.classicpt.vk;

import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.EXTMemoryObject;
import org.lwjgl.opengl.EXTMemoryObjectWin32;
import org.lwjgl.opengl.EXTSemaphore;
import org.lwjgl.opengl.EXTSemaphoreWin32;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.*;

/**
 * Classic PT's Vulkan side: only NVIDIA's features, no tracer. Classic and Indev trace with OpenGL compute
 * (Tracer.java); its pictures are copied into images Vulkan shares with OpenGL, Streamline runs DLSS or Ray
 * Reconstruction on them on the Vulkan queue, and OpenGL reads the result back from a shared image. Frame Generation
 * shows the game's finished frame through VkPresent's swap chain, Reflex marks the frame through NvFeatures.
 *
 * This is the NVIDIA half of the Alpha/Beta/Tekkit tracers' VkLive, unchanged in what it does (runNv, sharedImage,
 * the semaphores, present, reflexFrame); VkBase, NvFeatures and VkPresent are their files with the package renamed.
 * Keep them in step. Windows only (Streamline is). DLSS 5 (kFeatureDLSS_NR) is not in Streamline SDK 2.14.16:
 * when NVIDIA ships its plugin and header, it goes into NvFeatures like Ray Reconstruction did.
 */
public final class VkNv extends VkBase {
    private VkNv() {}

    @Override protected boolean keepsDevice() { return true; }

    /** in: dlss, rr, fg, reflex (Booleans). Call on the render thread, with the game's OpenGL context current. */
    public static Object open(Map<String, Object> in) {
        VkNv v = new VkNv();
        try {
            v.start(in);
            return v;
        } catch (Throwable t) {
            if (v.nvPending) { NvFeatures.markStarted(); v.nvPending = false; }
            v.close();
            throw t instanceof RuntimeException ? (RuntimeException) t : new RuntimeException(t);
        }
    }

    public Map<String, Object> info() { return out; }

    NvFeatures nv;
    private boolean nvPending; // the "starting" breadcrumb is still there (cleared after the first DLSS frame)

    private void start(Map<String, Object> in) {
        boolean wantDlss = Boolean.TRUE.equals(in.get("dlss")), wantReflex = Boolean.TRUE.equals(in.get("reflex"));
        if ((wantDlss || wantReflex) && NvFeatures.crashedLastTime()) { wantDlss = false; wantReflex = false; }
        if (wantDlss || wantReflex) { NvFeatures.markStarting(); nvPending = true; }
        if (wantDlss) NvFeatures.startStreamline(); // before any Vulkan instance: it becomes the Vulkan library

        GLCapabilities caps;
        try { caps = GL.getCapabilities(); } catch (IllegalStateException e) { caps = GL.createCapabilities(); }
        if (!caps.GL_EXT_memory_object || !caps.GL_EXT_memory_object_win32)
            throw new IllegalStateException("OpenGL can't share memory with Vulkan (GL_EXT_memory_object missing)");
        byte[] uuid = null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (GL11C.glGetInteger(EXTMemoryObject.GL_NUM_DEVICE_UUIDS_EXT) > 0) {
                ByteBuffer u = stack.malloc(EXTMemoryObject.GL_UUID_SIZE_EXT);
                EXTMemoryObject.glGetUnsignedBytei_vEXT(EXTMemoryObject.GL_DEVICE_UUID_EXT, 0, u);
                uuid = new byte[EXTMemoryObject.GL_UUID_SIZE_EXT];
                u.get(uuid);
            }
        }
        boolean glSem = caps.GL_EXT_semaphore && caps.GL_EXT_semaphore_win32 && !Boolean.getBoolean("betapt.vk.cpuSync");
        String semExt = KHRExternalSemaphoreWin32.VK_KHR_EXTERNAL_SEMAPHORE_WIN32_EXTENSION_NAME;
        List<String> opt = new ArrayList<String>();
        if (glSem) opt.add(semExt);
        if (keepDevice()) opt.add(EXT_SWAPCHAIN); // VkPresent (Frame Generation)
        optionalExtensions = opt.toArray(new String[0]);
        createDevice(uuid, KHRExternalMemoryWin32.VK_KHR_EXTERNAL_MEMORY_WIN32_EXTENSION_NAME);
        System.out.println("[ClassicPT] Vulkan side on " + out.get("device") + " (NVIDIA features only)");
        out.put("glRenderer", GL11C.glGetString(GL11C.GL_RENDERER));
        nv = new NvFeatures();
        if (wantDlss) nv.dlssAfterDevice(physical);
        if (wantDlss && Boolean.TRUE.equals(in.get("rr"))) nv.rrAfterDevice(); // Ray Reconstruction rides on DLSS
        if (wantDlss) nv.fgCheck(); // Frame Generation: what it says about this GPU (the log)
        if (wantDlss && Boolean.TRUE.equals(in.get("fg"))) nv.fgStart(Integer.getInteger("betapt.fg.generated", 1)); // with Streamline's Reflex
        if (wantReflex && !nv.fgOn()) {
            if (timelineOn) nv.reflexStart(device, false);
            else nv.reflexStatus = "Reflex: this driver has no timeline semaphores";
        }
        if (nvPending && !nv.dlssReady()) { NvFeatures.markStarted(); nvPending = false; } // DLSS clears it after its first frame
        if (glSem && optionalEnabled.contains(semExt)) {
            try { createSemaphores(); }
            catch (RuntimeException e) { glDone = 0; out.put("syncError", e.toString()); } // fall back to the CPU wait
        }
        out.put("sync", glDone != 0 ? "GPU semaphores" : "CPU wait");
        out.put("nvidia", nvStatus());
    }

    /** One line for the log and the test report: what's on, and why not when it isn't. */
    public String nvStatus() {
        StringBuilder s = new StringBuilder();
        if (nv != null) {
            if (!nv.dlssStatus.isEmpty()) s.append(nv.dlssStatus);
            else if (NvFeatures.status.contains("skipped")) s.append(NvFeatures.status);
            if (!nv.rrStatus.isEmpty()) s.append(s.length() > 0 ? "; " : "").append(nv.rrStatus);
            if (!nv.reflexStatus.isEmpty()) s.append(s.length() > 0 ? "; " : "").append(nv.reflexStatus);
            if (!nv.fgRunStatus.isEmpty()) s.append(s.length() > 0 ? "; " : "").append(nv.fgRunStatus);
        }
        return s.toString();
    }

    // ---- GPU hand-off between OpenGL and Vulkan (as VkLive)
    long vkWaitGl, vkSignalGl;
    int glDone, glReady;

    private void createSemaphores() {
        int ht = VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_WIN32_BIT;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long[] vk = new long[2];
            int[] gl = new int[2];
            for (int k = 0; k < 2; k++) {
                LongBuffer lp = stack.mallocLong(1);
                VkExportSemaphoreCreateInfo ex = VkExportSemaphoreCreateInfo.calloc(stack).sType$Default().handleTypes(ht);
                check(vkCreateSemaphore(device, VkSemaphoreCreateInfo.calloc(stack).sType$Default().pNext(ex.address()), null, lp), "semaphore");
                vk[k] = lp.get(0);
                gl[k] = EXTSemaphore.glGenSemaphoresEXT();
                PointerBuffer h = stack.mallocPointer(1);
                check(KHRExternalSemaphoreWin32.vkGetSemaphoreWin32HandleKHR(device, VkSemaphoreGetWin32HandleInfoKHR.calloc(stack)
                        .sType$Default().semaphore(vk[k]).handleType(ht), h), "semaphore handle");
                EXTSemaphoreWin32.glImportSemaphoreWin32HandleEXT(gl[k], EXTSemaphoreWin32.GL_HANDLE_TYPE_OPAQUE_WIN32_EXT, h.get(0));
            }
            vkWaitGl = vk[0]; glDone = gl[0];
            vkSignalGl = vk[1]; glReady = gl[1];
            int err = GL11C.glGetError();
            if (err != 0) throw new IllegalStateException("OpenGL error 0x" + Integer.toHexString(err) + " importing the Vulkan semaphores");
        }
    }

    boolean semaphores() { return glDone != 0; }

    // ---- DLSS and Ray Reconstruction: OpenGL's inputs are copied into images Vulkan shares; Streamline runs on the
    // Vulkan queue into a window-sized image OpenGL then shows (as VkLive)

    private static final int[] DL_VK = {VK_FORMAT_R8G8B8A8_UNORM, VK_FORMAT_R32_SFLOAT, VK_FORMAT_R16G16_SFLOAT, VK_FORMAT_R8G8B8A8_UNORM};
    private static final int[] DL_GL = {GL11C.GL_RGBA8, GL30C.GL_R32F, GL30C.GL_RG16F, GL11C.GL_RGBA8};
    private final long[][] dl = new long[4][]; // colour, depth, motion, output: {image, memory, view, format, w, h, usage, GL texture, GL memory}
    private static final int[] RR_VK = {VK_FORMAT_R16G16B16A16_SFLOAT, VK_FORMAT_R32_SFLOAT, VK_FORMAT_R16G16_SFLOAT, VK_FORMAT_R16G16B16A16_SFLOAT,
            VK_FORMAT_R16G16B16A16_SFLOAT, VK_FORMAT_R16G16B16A16_SFLOAT, VK_FORMAT_R16G16B16A16_SFLOAT};
    private static final int[] RR_GL = {GL30C.GL_RGBA16F, GL30C.GL_R32F, GL30C.GL_RG16F, GL30C.GL_RGBA16F, GL30C.GL_RGBA16F, GL30C.GL_RGBA16F, GL30C.GL_RGBA16F};
    private final long[][] rr = new long[NvFeatures.RR_IMAGES][];
    private long dlssFence;
    private VkCommandBuffer dlssCb;

    public boolean dlssReady() { return nv != null && nv.dlssReady(); }
    /** {optimal width, height, min width, height, max width, height} for a DLSS mode and window size, or null. */
    public int[] dlssSizes(int mode, int ow, int oh) { return dlssReady() ? nv.dlssSizes(mode, ow, oh) : null; }
    public boolean rrReady() { return nv != null && nv.rrReady(); }
    public int[] rrSizes(int mode, int ow, int oh) { return rrReady() ? nv.rrSizes(mode, ow, oh) : null; }

    private void waitDlss() {
        if (dlssCb == null) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            check(vkWaitForFences(device, stack.longs(dlssFence), true, 10_000_000_000L), "DLSS wait");
            vkResetFences(device, stack.longs(dlssFence));
        }
        vkFreeCommandBuffers(device, commandPool, dlssCb);
        dlssCb = null;
    }

    /** An image Vulkan and OpenGL share, in GENERAL layout. */
    long[] sharedImage(int w, int h, int vkFormat, int glFormat) {
        int handleType = VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT;
        int usage = VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
        long img = image(w, h, vkFormat, usage, handleType);
        long mem = lastImageMemory, size = lastImageSize;
        long view = imageView(img, vkFormat);
        int tex, glm;
        int prevTex = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            glm = EXTMemoryObject.glCreateMemoryObjectsEXT();
            EXTMemoryObject.glMemoryObjectParameterivEXT(glm, EXTMemoryObject.GL_DEDICATED_MEMORY_OBJECT_EXT, stack.ints(GL11C.GL_TRUE));
            PointerBuffer handle = stack.mallocPointer(1);
            check(KHRExternalMemoryWin32.vkGetMemoryWin32HandleKHR(device, VkMemoryGetWin32HandleInfoKHR.calloc(stack).sType$Default()
                    .memory(mem).handleType(handleType), handle), "vkGetMemoryWin32HandleKHR");
            EXTMemoryObjectWin32.glImportMemoryWin32HandleEXT(glm, size, EXTMemoryObjectWin32.GL_HANDLE_TYPE_OPAQUE_WIN32_EXT, handle.get(0));
            tex = GL11C.glGenTextures();
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, tex);
            EXTMemoryObject.glTexStorageMem2DEXT(GL11C.GL_TEXTURE_2D, 1, glFormat, w, h, glm, 0);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL_CLAMP);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL_CLAMP);
        }
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, prevTex);
        VkCommandBuffer cb = beginOneShot();
        imageBarrier(cb, img, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_GENERAL, 0, VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT,
                VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT);
        endOneShot(cb);
        return new long[]{img, mem, view, vkFormat, w, h, usage, tex, glm};
    }

    private static final int GL_CLAMP = 0x812F; // GL_CLAMP_TO_EDGE

    void freeShared(long[] d) {
        vkDestroyImageView(device, d[2], null);
        vkDestroyImage(device, d[0], null);
        final long mem = d[1];
        synchronized (buffers) {
            for (java.util.Iterator<long[]> it = buffers.iterator(); it.hasNext(); ) {
                long[] x = it.next();
                if (x[0] == VK_NULL_HANDLE && x[1] == mem) it.remove();
            }
        }
        vkFreeMemory(device, d[1], null);
        GL11C.glDeleteTextures((int) d[7]);
        EXTMemoryObject.glDeleteMemoryObjectsEXT((int) d[8]);
    }

    /**
     * DLSS: upscales the tone-mapped picture glColor (RGBA8, rw x rh) with its depth (R32F) and motion (RG16F, in
     * screen units, pointing to last frame) to ow x oh. c: jitter x, y in pixels, tan(half fov y), near, camera
     * position xyz, forward xyz, right xyz, up xyz. Returns the OpenGL texture with the result, or 0.
     */
    public int dlss(int glColor, int glDepth, int glMv, int rw, int rh, int ow, int oh, int mode, float[] c, boolean reset) {
        if (!dlssReady()) return 0;
        return runNv(false, dl, DL_VK, DL_GL, new int[]{glColor, glDepth, glMv}, rw, rh, ow, oh, mode, c, reset);
    }

    /**
     * DLSS Ray Reconstruction: glColor the noisy HDR picture (RGBA16F, albedo x light, not denoised); depth and motion
     * as for dlss; glAlbedo the diffuse albedo, glSpec the specular albedo, glNormal the world normal with roughness in w
     * (all RGBA16F, rw x rh). Returns the OpenGL texture with the HDR result (RGBA16F, ow x oh), or 0.
     */
    public int dlssRR(int glColor, int glDepth, int glMv, int glAlbedo, int glSpec, int glNormal, int rw, int rh, int ow, int oh, int mode, float[] c, boolean reset) {
        if (!rrReady()) return 0;
        return runNv(true, rr, RR_VK, RR_GL, new int[]{glColor, glDepth, glMv, glAlbedo, glSpec, glNormal}, rw, rh, ow, oh, mode, c, reset);
    }

    private int runNv(boolean isRr, long[][] imgs, int[] vkFormats, int[] glFormats, int[] src, int rw, int rh, int ow, int oh, int mode, float[] c, boolean reset) {
        int n = imgs.length;
        try {
            waitDlss();
            for (int i = 0; i < n; i++) {
                int w = i == n - 1 ? ow : rw, h = i == n - 1 ? oh : rh;
                if (imgs[i] != null && imgs[i][4] == w && imgs[i][5] == h) continue;
                if (imgs[i] != null) { vkDeviceWaitIdle(device); freeShared(imgs[i]); imgs[i] = null; }
                imgs[i] = sharedImage(w, h, vkFormats[i], glFormats[i]);
                reset = true;
            }
            if (dlssFence == 0) {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    LongBuffer lp = stack.mallocLong(1);
                    check(vkCreateFence(device, VkFenceCreateInfo.calloc(stack).sType$Default(), null, lp), "DLSS fence");
                    dlssFence = lp.get(0);
                }
            }
            for (int i = 0; i < src.length; i++)
                GL43C.glCopyImageSubData(src[i], GL11C.GL_TEXTURE_2D, 0, 0, 0, 0, (int) imgs[i][7], GL11C.GL_TEXTURE_2D, 0, 0, 0, 0, rw, rh, 1);
            VkCommandBuffer cb = beginOneShot();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkMemoryBarrier.Buffer all = VkMemoryBarrier.calloc(1, stack);
                all.get(0).sType$Default().srcAccessMask(VK_ACCESS_MEMORY_WRITE_BIT).dstAccessMask(VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT);
                vkCmdPipelineBarrier(cb, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, all, null, null);
                boolean ok = isRr ? nv.rrRecord(cb, mode, imgs, c, reset) : nv.dlssRecord(cb, mode, imgs, c, reset);
                vkCmdPipelineBarrier(cb, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, all, null, null);
                if (!ok) {
                    vkEndCommandBuffer(cb);
                    vkFreeCommandBuffers(device, commandPool, cb);
                    // it refused (the reason is in nv.dlssStatus / rrStatus) and the game carries on: no crash, so the
                    // next start mustn't skip the NVIDIA features
                    if (nvPending) { NvFeatures.markStarted(); nvPending = false; }
                    out.put("nvidia", nvStatus());
                    return 0;
                }
                if (!semaphores()) {
                    GL11C.glFinish(); // OpenGL's copies are done
                    endOneShot(cb);   // and so is DLSS
                } else {
                    check(vkEndCommandBuffer(cb), "end");
                    IntBuffer texs = stack.mallocInt(n), layouts = stack.mallocInt(n);
                    for (int i = 0; i < n; i++) { texs.put(i, (int) imgs[i][7]); layouts.put(i, EXTSemaphore.GL_LAYOUT_GENERAL_EXT); }
                    EXTSemaphore.glSignalSemaphoreEXT(glDone, stack.mallocInt(0), texs, layouts);
                    GL11C.glFlush();
                    check(vkQueueSubmit(queue, VkSubmitInfo.calloc(stack).sType$Default()
                            .waitSemaphoreCount(1).pWaitSemaphores(stack.longs(vkWaitGl)).pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_ALL_COMMANDS_BIT))
                            .pCommandBuffers(stack.pointers(cb)).pSignalSemaphores(stack.longs(vkSignalGl)), dlssFence), "DLSS submit");
                    dlssCb = cb;
                    EXTSemaphore.glWaitSemaphoreEXT(glReady, stack.mallocInt(0), texs, layouts);
                }
            }
            if (nvPending) { NvFeatures.markStarted(); nvPending = false; }
            return (int) imgs[n - 1][7];
        } catch (Throwable t) {
            if (isRr) nv.rrStatus = "Ray Reconstruction stopped: " + t; else nv.dlssStatus = "DLSS stopped: " + t;
            out.put("nvidia", nvStatus());
            return 0;
        }
    }

    // ---- showing frames through Vulkan (VkPresent, for Frame Generation)

    private VkPresent presenter;
    private boolean presentFailed;
    private long presented;

    /** Shows the game's finished frame (OpenGL texture glTex, w x h, RGBA8) in a Vulkan window over the game's; false if it can't. */
    public boolean present(int glTex, int w, int h, boolean vsync) {
        if (presentFailed) return false;
        if (!surfaceOn || !optionalEnabled.contains(EXT_SWAPCHAIN)) { presentFailed = true; out.put("present", "Vulkan window: needs NVIDIA Streamline (DLSS on)"); return false; }
        if (presenter == null) presenter = new VkPresent(this);
        if (!presenter.present(glTex, w, h, vsync)) { presentFailed = true; presenter = null; }
        out.put("present", presenter != null ? presenter.status : "Vulkan window: off after an error");
        if (nv != null && nv.fgOn() && ++presented % 600 == 0) System.out.println("[ClassicPT] " + nv.fgReport());
        return !presentFailed;
    }

    /** The game's own picture shows again. */
    public void stopPresent() {
        if (presenter != null) { presenter.close(); presenter = null; }
        presentFailed = false;
    }

    public String presentStatus() { Object s = out.get("present"); return s != null ? s.toString() : ""; }

    // ---- Reflex

    public boolean reflexOn() { return nv != null && (nv.reflexOn() || nv.fgOn()); }

    /** 0: start of the frame (Reflex's sleep, then "simulation start"); 1..5: Reflex's markers for this frame. */
    public void reflexFrame(int phase) {
        if (!reflexOn()) return;
        if (nv.fgOn()) { if (phase == 0) nv.fgFrameStart(); else nv.pcl(phase); return; } // Streamline's Reflex
        if (phase == 0) nv.reflexFrameStart();
        else nv.marker(phase);
    }

    public void close() {
        if (device != null) {
            stopPresent();
            vkDeviceWaitIdle(device);
            for (long sem : new long[]{vkWaitGl, vkSignalGl}) if (sem != 0) vkDestroySemaphore(device, sem, null);
            if (dlssCb != null) { vkFreeCommandBuffers(device, commandPool, dlssCb); dlssCb = null; }
            if (dlssFence != 0) vkDestroyFence(device, dlssFence, null);
            for (int i = 0; i < dl.length; i++) if (dl[i] != null) { freeShared(dl[i]); dl[i] = null; }
            for (int i = 0; i < rr.length; i++) if (rr[i] != null) { freeShared(rr[i]); rr[i] = null; }
            if (nv != null) nv.close();
        }
        if (glDone != 0) EXTSemaphore.glDeleteSemaphoresEXT(glDone);
        if (glReady != 0) EXTSemaphore.glDeleteSemaphoresEXT(glReady);
        glDone = glReady = 0;
        cleanup(); // frees every buffer and memory; the device too unless Streamline's (kept for the whole game)
        device = null;
    }
}
