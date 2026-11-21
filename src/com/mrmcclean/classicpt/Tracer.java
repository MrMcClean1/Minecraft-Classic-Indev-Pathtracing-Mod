package com.mrmcclean.classicpt;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * The OpenGL side: the level as one 3D texture (Classic and Indev keep the whole level in one array, so there are
 * no chunks to manage), terrain.png and clouds.png, a table of each block's shape and face textures, the light
 * list, and four compute passes drawn over the world before the HUD:
 *   trace.comp (path tracing) -> temporal.comp (adds frames up) -> atrous.comp (spatial filter) -> compose.comp.
 * The denoiser follows the Alpha/Beta tracers' (OFF: frames added up only; Standard: + 3 filter passes;
 * Advanced: + firefly cut, 5 passes, brightness-aware). Needs OpenGL 4.3 (compute shaders).
 */
final class Tracer {
    // shapes (trace.comp)
    static final int AIR = 0, CUBE = 1, CUTOUT = 2, CROSS = 3, WATER = 4, LAVA = 5, SLAB = 6, TORCH = 7, LOW = 8;
    static final int MAX_LIGHTS = 64;

    private final Game g;
    final Settings settings = new Settings();
    boolean ok, failed;
    String status = "not started";
    private int prog, progTemporal, progAtrous, progCompose, worldTex, atlasTex, cloudsTex, infoBuf, lightBuf;
    private int lightTex, albedoTex, outTex;
    private final int[] geomTex = new int[2], accumTex = new int[2], tmpTex = new int[2];
    private int outW, outH, frame, history, cur;
    private boolean historyValid;
    private float[] prevCam;            // last frame's camera: position, forward, right, up
    private final long start = System.nanoTime();
    byte[] copy;                        // what the GPU has
    private int sx, sy, sz, scanY;
    private Object levelSeen;
    private float waterY, groundY;
    int lightCount, layersUploaded;
    double lastMs;
    boolean photo;                      // photo mode: add up until clean, then save
    String photoSaved;
    int[][] faces;

    Tracer(Game g) {
        this.g = g;
        settings.load();
    }

    private void init() {
        if (!GLContext.getCapabilities().OpenGL43) {
            failed = true;
            status = "needs OpenGL 4.3 (compute shaders); this context: " + GL11.glGetString(GL11.GL_VERSION);
            Hooks.log(status);
            return;
        }
        try {
            prog = program("trace");
            progTemporal = program("temporal");
            progAtrous = program("atrous");
            progCompose = program("compose");
            atlasTex = loadTexture("/terrain.png", false);
            cloudsTex = loadTexture("/clouds.png", true);
            infoBuf = blockTable();
            lightBuf = GL15.glGenBuffers();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, lightBuf);
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, MAX_LIGHTS * 16L, GL15.GL_DYNAMIC_DRAW);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
            ok = true;
            status = "running (" + GL11.glGetString(GL11.GL_RENDERER) + ")";
            Hooks.log("path tracer ready: " + status);
        } catch (Throwable t) {
            failed = true;
            status = "failed to start: " + t.getMessage();
            Hooks.log(status);
        }
    }

    private static String read(String path) throws Exception {
        InputStream in = Tracer.class.getResourceAsStream(path);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        for (int n; (n = in.read(b)) > 0; ) o.write(b, 0, n);
        in.close();
        return new String(o.toByteArray(), "UTF-8");
    }

    private static int program(String name) throws Exception {
        int s = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER);
        GL20.glShaderSource(s, read("/assets/classicpt/" + name + ".comp"));
        GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == 0) throw new IllegalStateException(name + ": " + GL20.glGetShaderInfoLog(s, 16384));
        int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, s);
        GL20.glLinkProgram(p);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == 0) throw new IllegalStateException(name + " link: " + GL20.glGetProgramInfoLog(p, 16384));
        return p;
    }

    /** A texture from the game's jar (terrain.png, clouds.png); 0 if the jar hasn't it. */
    private int loadTexture(String path, boolean smoothRepeat) throws Exception {
        InputStream in = g.mc.getClass().getResourceAsStream(path);
        if (in == null) return 0;
        BufferedImage img = ImageIO.read(in);
        in.close();
        int w = img.getWidth(), h = img.getHeight();
        ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 4);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int p = img.getRGB(x, y);
                buf.put((byte) (p >> 16)).put((byte) (p >> 8)).put((byte) p).put((byte) (p >>> 24));
            }
        buf.flip();
        int t = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, t);
        int filter = GL11.GL_NEAREST, wrap = smoothRepeat ? GL11.GL_REPEAT : GL12.GL_CLAMP_TO_EDGE;
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wrap);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, wrap);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return t;
    }

    /** Shape of each block id (the textures come from the game's own tiles). */
    static int shapeOf(int id, boolean indev, boolean exists) {
        if (!exists) return AIR;
        switch (id) {
            case 8: case 9: return WATER;
            case 10: case 11: return LAVA;
            case 18: case 20: return CUTOUT;
            case 6: case 37: case 38: case 39: case 40: case 51: return CROSS;
            case 44: return SLAB;
            case 50: return TORCH;
        }
        if (indev) {
            if (id == 52) return WATER;   // Indev 0.31's infinite water source
            if (id == 53) return LAVA;    // and infinite lava source
            if (id == 55 || id == 59) return CROSS; // gear, crops
            if (id == 60) return LOW;     // farmland
        }
        return CUBE;
    }

    private int blockTable() {
        faces = g.faces();
        IntBuffer b = BufferUtils.createIntBuffer(256 * 8);
        for (int id = 0; id < 256; id++) {
            int[] f = faces[id];
            b.put(id == 0 ? AIR : shapeOf(id, g.indev, f != null));
            for (int k = 0; k < 6; k++) b.put(f == null ? 0 : Math.max(0, f[k]));
            b.put(0);
        }
        b.flip();
        int buf = GL15.glGenBuffers();
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buf);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, b, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
        return buf;
    }

    // ---------------------------------------------------------------- the level on the GPU

    private void worldTexture(Object level) {
        byte[] blocks = g.blocks();
        sx = g.sizeX(); sy = g.sizeY(); sz = g.sizeZ();
        if (blocks == null || sx * sy * sz != blocks.length) return;
        copy = blocks.clone();
        if (worldTex != 0) GL11.glDeleteTextures(worldTex);
        worldTex = GL11.glGenTextures();
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, worldTex);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        ByteBuffer buf = BufferUtils.createByteBuffer(copy.length);
        buf.put(copy).flip();
        // the level's index is (y * zSize + z) * xSize + x, so the texture is x by z by y
        GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL30.GL_R8UI, sx, sz, sy, 0, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_BYTE, buf);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
        levelSeen = level;
        resetHistory();
        edges();
        Hooks.log("level on the GPU: " + sx + " x " + sy + " x " + sz + ", water around it at " + waterY + ", ground " + groundY);
    }

    /** What lies around the map: Classic says (its water level, ground two below); Indev from the map's own border. */
    private void edges() {
        int wl = g.waterLevel();
        if (wl >= 0) { waterY = wl; groundY = wl - 2; return; }
        List<Integer> ws = new ArrayList<Integer>(), gs = new ArrayList<Integer>();
        for (int i = 0; i < Math.max(sx, sz); i += 4)
            for (int side = 0; side < 4; side++) {
                int x = side == 0 ? 0 : side == 1 ? sx - 1 : Math.min(i, sx - 1), z = side == 2 ? 0 : side == 3 ? sz - 1 : Math.min(i, sz - 1);
                int top = -1, wtop = -1;
                for (int y = sy - 1; y >= 0 && top < 0; y--) {
                    int id = copy[(y * sz + z) * sx + x] & 255;
                    if (id == 8 || id == 9) { if (wtop < 0) wtop = y; }
                    else if (id != 0) top = y;
                }
                if (wtop >= 0) ws.add(wtop + 1);
                gs.add(top + 1);
            }
        Collections.sort(ws);
        Collections.sort(gs);
        groundY = gs.isEmpty() ? 0 : gs.get(gs.size() / 2);
        waterY = ws.size() > gs.size() / 3 ? ws.get(ws.size() / 2) : -1000;
    }

    /** A few layers a frame: any that changed go to the GPU (blocks placed, mined, flowing). */
    private void scan() {
        byte[] blocks = g.blocks();
        if (blocks == null || blocks.length != copy.length) return;
        int layer = sx * sz, per = Math.max(1, Math.min(sy, (1 << 20) / layer));
        ByteBuffer buf = null;
        for (int k = 0; k < per; k++) {
            int y = scanY;
            scanY = (scanY + 1) % sy;
            int o = y * layer;
            boolean diff = false;
            for (int i = o; i < o + layer; i++) if (blocks[i] != copy[i]) { diff = true; break; }
            if (!diff) continue;
            System.arraycopy(blocks, o, copy, o, layer);
            if (buf == null) buf = BufferUtils.createByteBuffer(layer);
            buf.clear();
            buf.put(copy, o, layer).flip();
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, worldTex);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0, 0, 0, y, sx, sz, 1, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_BYTE, buf);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
            layersUploaded++;
        }
    }

    /** The nearest light-giving blocks (torches, fire, lava with air over it), for the shader to pick from. */
    private void lights(float[] v) {
        final int cx = (int) Math.floor(v[0]), cy = (int) Math.floor(v[1]), cz = (int) Math.floor(v[2]);
        List<int[]> found = new ArrayList<int[]>();
        int r = 20;
        for (int y = Math.max(0, cy - 12); y < Math.min(sy, cy + 12); y++)
            for (int z = Math.max(0, cz - r); z < Math.min(sz, cz + r); z++)
                for (int x = Math.max(0, cx - r); x < Math.min(sx, cx + r); x++) {
                    int id = copy[(y * sz + z) * sx + x] & 255;
                    int shape = id == 0 ? AIR : shapeOf(id, g.indev, faces[id] != null);
                    if (id == 50 || id == 51 || (shape == LAVA && (y + 1 >= sy || copy[((y + 1) * sz + z) * sx + x] == 0)))
                        found.add(new int[]{x, y, z, id, (x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz)});
                }
        Collections.sort(found, new Comparator<int[]>() { public int compare(int[] a, int[] b) { return a[4] - b[4]; } });
        lightCount = Math.min(MAX_LIGHTS, found.size());
        FloatBuffer f = BufferUtils.createFloatBuffer(MAX_LIGHTS * 4);
        for (int i = 0; i < lightCount; i++) { int[] l = found.get(i); f.put(l[0] + 0.5f).put(l[1] + 0.5f).put(l[2] + 0.5f).put(l[3]); }
        while (f.hasRemaining()) f.put(0);
        f.flip();
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, lightBuf);
        GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, f);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    private static int texture(int w, int h, int format, boolean smooth) {
        int t = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, t);
        int f = smooth ? GL11.GL_LINEAR : GL11.GL_NEAREST;
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, f);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, f);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL42.glTexStorage2D(GL11.GL_TEXTURE_2D, 1, format, w, h);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return t;
    }

    /** The denoiser's pictures, at the traced size (the window times the render scale). */
    private void targets(int w, int h) {
        if (w == outW && h == outH && outTex != 0) return;
        if (outTex != 0) {
            GL11.glDeleteTextures(lightTex); GL11.glDeleteTextures(albedoTex); GL11.glDeleteTextures(outTex);
            for (int i = 0; i < 2; i++) { GL11.glDeleteTextures(geomTex[i]); GL11.glDeleteTextures(accumTex[i]); GL11.glDeleteTextures(tmpTex[i]); }
        }
        lightTex = texture(w, h, GL30.GL_RGBA16F, false);
        albedoTex = texture(w, h, GL11.GL_RGBA8, false);
        outTex = texture(w, h, GL11.GL_RGBA8, true);
        for (int i = 0; i < 2; i++) {
            geomTex[i] = texture(w, h, GL30.GL_RGBA16F, false);
            accumTex[i] = texture(w, h, GL30.GL_RGBA16F, false);
            tmpTex[i] = texture(w, h, GL30.GL_RGBA16F, false);
        }
        outW = w;
        outH = h;
        resetHistory();
    }

    // ---------------------------------------------------------------- each frame

    /** Traces the frame and draws it over the world. Returns false when it didn't (no level, not ready). */
    boolean render(float partial) {
        if (failed) return false;
        if (!ok) { init(); if (!ok) return false; }
        Object level = g.level();
        float[] v = g.view(partial);
        if (level == null || v == null) return false;
        long t0 = System.nanoTime();
        if (level != levelSeen || copy == null) worldTexture(level);
        if (copy == null) return false;
        scan();
        if (frame % 20 == 0) lights(v);
        int w = g.width(), h = g.height();
        if (w <= 0 || h <= 0) return false;
        Settings st = settings;
        int tw = Math.max(16, Math.round(w * st.renderScale)), th = Math.max(16, Math.round(h * st.renderScale));
        targets(tw, th);

        // camera (Classic and Indev: pitch down is positive, yaw turns from -z toward +x)
        double yaw = Math.toRadians(v[3]), pitch = Math.toRadians(v[4]);
        float fx = (float) (Math.sin(yaw) * Math.cos(pitch)), fy = (float) -Math.sin(pitch), fz = (float) (-Math.cos(yaw) * Math.cos(pitch));
        float rx = (float) Math.cos(yaw), rz = (float) Math.sin(yaw);
        float ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;  // right x forward
        float[] camNow = {v[0], v[1], v[2], fx, fy, fz, rx, 0, rz, ux, uy, uz};
        if (photo && prevCam != null && !Arrays.equals(prevCam, camNow)) { photo = false; Hooks.say("Photo cancelled: the view moved"); }
        float tan = (float) Math.tan(Math.toRadians(70) / 2), aspect = tw / (float) th;
        int gx = (tw + 7) / 8, gy = (th + 7) / 8;
        int prev = cur;
        cur ^= 1;

        // 1. trace
        GL20.glUseProgram(prog);
        GL20.glUniform3i(loc(prog, "uSize"), sx, sy, sz);
        camera(prog, camNow, tan, aspect, tw, th);
        GL20.glUniform1i(loc(prog, "uFrame"), frame);
        GL20.glUniform1i(loc(prog, "uLightCount"), lightCount);
        GL20.glUniform1i(loc(prog, "uSamples"), photo ? Math.max(4, st.samples) : st.samples);
        GL20.glUniform1i(loc(prog, "uBounces"), st.bounces);
        GL20.glUniform1i(loc(prog, "uView"), st.view);
        GL20.glUniform1i(loc(prog, "uEffects"), st.effects());
        GL20.glUniform1i(loc(prog, "uReflect"), st.reflections ? 1 : 0);
        float[] sun = norm(0.35f, 0.86f, 0.37f);
        GL20.glUniform3f(loc(prog, "uSunDir"), sun[0], sun[1], sun[2]);
        GL20.glUniform3f(loc(prog, "uSunColor"), 3.0f, 2.85f, 2.6f);
        int skyRgb = g.skyColor();
        float sr = lin(skyRgb >> 16), sg = lin(skyRgb >> 8), sb = lin(skyRgb);
        GL20.glUniform3f(loc(prog, "uSkyTop"), sr * 1.8f, sg * 1.8f, sb * 1.8f); // the level's own sky colour (Classic 0x99CCFF)
        GL20.glUniform3f(loc(prog, "uSkyHorizon"), sr * 0.6f + 0.4f, sg * 0.6f + 0.4f, sb * 0.6f + 0.4f); // towards its white fog
        GL20.glUniform3f(loc(prog, "uFogColor"), sr * 0.6f + 0.4f, sg * 0.6f + 0.4f, sb * 0.6f + 0.4f);
        GL20.glUniform1f(loc(prog, "uWaterY"), waterY);
        GL20.glUniform1f(loc(prog, "uGroundY"), groundY);
        GL20.glUniform1f(loc(prog, "uTime"), (System.nanoTime() - start) / 1e9f);
        GL20.glUniform1f(loc(prog, "uCloudY"), sy + 2);
        GL20.glUniform1f(loc(prog, "uCloudShadow"), cloudsTex == 0 ? 0f : st.cloudShadows);
        GL20.glUniform1f(loc(prog, "uFogEnd"), st.fogDistance);
        float held = st.heldLight && g.holdingTorch() ? 1f : 0f;
        GL20.glUniform4f(loc(prog, "uHeld"), v[0] + rx * 0.4f, v[1] - 0.3f, v[2] + rz * 0.4f, held);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, worldTex);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlasTex);
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, cloudsTex);
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 2, infoBuf);
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 3, lightBuf);
        image(0, lightTex, GL15.GL_WRITE_ONLY, GL30.GL_RGBA16F);
        image(1, albedoTex, GL15.GL_WRITE_ONLY, GL11.GL_RGBA8);
        image(2, geomTex[cur], GL15.GL_WRITE_ONLY, GL30.GL_RGBA16F);
        GL43.glDispatchCompute(gx, gy, 1);
        barrier();

        // 2. temporal
        GL20.glUseProgram(progTemporal);
        camera(progTemporal, camNow, tan, aspect, tw, th);
        float[] pc = prevCam == null ? camNow : prevCam;
        GL20.glUniform3f(loc(progTemporal, "uPrevCam"), pc[0], pc[1], pc[2]);
        GL20.glUniform3f(loc(progTemporal, "uPrevFwd"), pc[3], pc[4], pc[5]);
        GL20.glUniform3f(loc(progTemporal, "uPrevRight"), pc[6], pc[7], pc[8]);
        GL20.glUniform3f(loc(progTemporal, "uPrevUp"), pc[9], pc[10], pc[11]);
        GL20.glUniform1i(loc(progTemporal, "uHistoryValid"), historyValid ? 1 : 0);
        GL20.glUniform1f(loc(progTemporal, "uMaxHistory"), photo ? 4096f : st.denoiser == 2 ? 32f : 64f);
        GL20.glUniform1i(loc(progTemporal, "uAdvanced"), st.denoiser == 2 ? 1 : 0);
        image(0, lightTex, GL15.GL_READ_ONLY, GL30.GL_RGBA16F);
        image(1, geomTex[cur], GL15.GL_READ_ONLY, GL30.GL_RGBA16F);
        image(2, accumTex[prev], GL15.GL_READ_ONLY, GL30.GL_RGBA16F);
        image(3, geomTex[prev], GL15.GL_READ_ONLY, GL30.GL_RGBA16F);
        image(4, accumTex[cur], GL15.GL_WRITE_ONLY, GL30.GL_RGBA16F);
        GL43.glDispatchCompute(gx, gy, 1);
        barrier();

        // 3. spatial filter: Standard 3 passes, Advanced 5 (also by brightness); OFF none
        int src = accumTex[cur];
        int passes = st.denoiser == 0 || st.view != 0 ? 0 : st.denoiser == 1 ? 3 : 5;
        if (photo) passes = Math.min(passes, 2); // photo mode has hundreds of frames: only a light touch
        GL20.glUseProgram(progAtrous);
        camera(progAtrous, camNow, tan, aspect, tw, th);
        GL20.glUniform1i(loc(progAtrous, "uAdvanced"), st.denoiser == 2 ? 1 : 0);
        for (int i = 0; i < passes; i++) {
            int dst = tmpTex[i & 1];
            GL20.glUniform1i(loc(progAtrous, "uStep"), 1 << i);
            image(0, src, GL15.GL_READ_ONLY, GL30.GL_RGBA16F);
            image(1, geomTex[cur], GL15.GL_READ_ONLY, GL30.GL_RGBA16F);
            image(2, dst, GL15.GL_WRITE_ONLY, GL30.GL_RGBA16F);
            GL43.glDispatchCompute(gx, gy, 1);
            barrier();
            src = dst;
        }

        // 4. compose
        GL20.glUseProgram(progCompose);
        GL20.glUniform2i(loc(progCompose, "uRes"), tw, th);
        GL20.glUniform1f(loc(progCompose, "uExposure"), 0.5f);
        GL20.glUniform1f(loc(progCompose, "uBrightness"), st.brightness);
        GL20.glUniform1i(loc(progCompose, "uLook"), st.view == 0 ? st.look : 1);
        image(0, src, GL15.GL_READ_ONLY, GL30.GL_RGBA16F);
        image(1, albedoTex, GL15.GL_READ_ONLY, GL11.GL_RGBA8);
        image(2, outTex, GL15.GL_WRITE_ONLY, GL11.GL_RGBA8);
        GL43.glDispatchCompute(gx, gy, 1);
        GL42.glMemoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL42.GL_TEXTURE_FETCH_BARRIER_BIT | GL42.GL_TEXTURE_UPDATE_BARRIER_BIT);

        GL20.glUseProgram(0);
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
        draw(w, h);
        prevCam = camNow;
        historyValid = true;
        frame++;
        history++;
        if (photo && history >= 512) savePhoto(tw, th);
        lastMs = (System.nanoTime() - t0) / 1e6;
        return true;
    }

    private static void camera(int p, float[] c, float tan, float aspect, int w, int h) {
        GL20.glUniform3f(loc(p, "uCam"), c[0], c[1], c[2]);
        GL20.glUniform3f(loc(p, "uFwd"), c[3], c[4], c[5]);
        GL20.glUniform3f(loc(p, "uRight"), c[6], c[7], c[8]);
        GL20.glUniform3f(loc(p, "uUp"), c[9], c[10], c[11]);
        GL20.glUniform1f(loc(p, "uTan"), tan);
        GL20.glUniform1f(loc(p, "uAspect"), aspect);
        GL20.glUniform2i(loc(p, "uRes"), w, h);
    }

    private static void image(int unit, int tex, int access, int format) { GL42.glBindImageTexture(unit, tex, 0, false, 0, access, format); }
    private static void barrier() { GL42.glMemoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT); }

    /** Photo mode (F6): hold still, the picture adds up for 512 frames, then it goes to screenshots/ as a PNG. */
    void startPhoto() {
        photo = true;
        resetHistory();
        Hooks.say("Photo mode: hold still...");
    }

    private void savePhoto(int w, int h) {
        photo = false;
        try {
            ByteBuffer b = BufferUtils.createByteBuffer(w * h * 4);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, outTex);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, b);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int i = ((h - 1 - y) * w + x) * 4;
                    img.setRGB(x, y, (b.get(i) & 255) << 16 | (b.get(i + 1) & 255) << 8 | (b.get(i + 2) & 255));
                }
            File dir = new File("screenshots");
            dir.mkdirs();
            File f = new File(dir, "classicpt_" + new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date()) + ".png");
            ImageIO.write(img, "png", f);
            photoSaved = f.getPath();
            Hooks.say("Photo saved: " + f.getName());
        } catch (Exception e) {
            Hooks.say("Photo failed: " + e.getMessage());
        }
    }

    /** The traced picture over the whole window (scaled up if the render scale is under 1), the game's GL state kept. */
    private void draw(int w, int h) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, 1, 0, 1, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glViewport(0, 0, w, h);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, outTex);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0); GL11.glVertex2f(0, 0);
        GL11.glTexCoord2f(1, 0); GL11.glVertex2f(1, 0);
        GL11.glTexCoord2f(1, 1); GL11.glVertex2f(1, 1);
        GL11.glTexCoord2f(0, 1); GL11.glVertex2f(0, 1);
        GL11.glEnd();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopAttrib();
    }

    void resetHistory() { history = 0; historyValid = false; }
    int history() { return history; }

    private static int loc(int p, String n) { return GL20.glGetUniformLocation(p, n); }
    private static float lin(int c) { return (float) Math.pow((c & 255) / 255.0, 2.2); }
    private static float[] norm(float x, float y, float z) { float l = (float) Math.sqrt(x * x + y * y + z * z); return new float[]{x / l, y / l, z / l}; }
}
