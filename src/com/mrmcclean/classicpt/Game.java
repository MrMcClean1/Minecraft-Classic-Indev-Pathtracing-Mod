package com.mrmcclean.classicpt;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * The two games, read through reflection (their class and field names are the obfuscated ones of each jar,
 * found with javap; see the README). Everything the tracer needs: the level's block array and size, the
 * player's eye and view, the window size, each tile's six face textures, and the call that makes a new level.
 *
 *              Classic 0.30 (c0.30-s-1858)          Indev 20100223
 * Minecraft    com.mojang.minecraft.l               net.minecraft.client.d (the hooked renderer's field b)
 * level        field d: Level (named fields)        field d: net.minecraft.a.a.g, a = x size, b = z size, c = y
 * player       field f, Entity x y z xo.. yRot xRot field f, entity fields h i j / e f g / n o / p q
 * window       fields b, c                          fields b, c
 * tiles        level.tile.a.b[], texture b(int)     net.minecraft.a.a.b.x.c[], texture a(int)
 * blocks       index (y * zSize + z) * xSize + x in both
 */
final class Game {
    final String name;
    final boolean indev;
    final Object mc;
    private final Field fLevel, fPlayer, fW, fH;
    private Field lvX, lvY, lvZ, lvBlocks, lvWater;
    private Field px, py, pz, pxo, pyo, pzo, pyaw, ppitch, pyawO, ppitchO;
    private Object level;

    private Game(Object mc, boolean indev) throws Exception {
        this.mc = mc;
        this.indev = indev;
        this.name = indev ? "Indev 20100223" : "Classic 0.30 survival";
        Class<?> c = mc.getClass();
        fLevel = field(c, "d");
        fPlayer = field(c, "f");
        fW = field(c, "b");
        fH = field(c, "c");
    }

    /** The game for the hooked object (Classic: Minecraft itself; Indev: the frame renderer, whose b is Minecraft). */
    static Game of(Object owner) {
        try {
            String n = owner.getClass().getName();
            if (n.equals("com.mojang.minecraft.l")) return new Game(owner, false);
            if (n.equals("net.minecraft.client.a.e")) return new Game(field(owner.getClass(), "b").get(owner), true);
        } catch (Exception e) {
            Hooks.log("not a supported game: " + e);
        }
        return null;
    }

    static Field field(Class<?> c, String name) throws NoSuchFieldException {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(c.getName() + "." + name);
    }

    int width() { try { return fW.getInt(mc); } catch (Exception e) { return 854; } }
    int height() { try { return fH.getInt(mc); } catch (Exception e) { return 480; } }

    /** The level now, or null (title screen, generating). Reads its fields again when it changes. */
    Object level() {
        try {
            Object l = fLevel.get(mc);
            if (l != level && l != null) {
                Class<?> c = l.getClass();
                if (indev) { lvX = field(c, "a"); lvZ = field(c, "b"); lvY = field(c, "c"); lvBlocks = field(c, "d"); lvWater = null; }
                else { lvX = field(c, "width"); lvZ = field(c, "height"); lvY = field(c, "depth"); lvBlocks = field(c, "blocks"); lvWater = field(c, "waterLevel"); }
            }
            level = l;
            return l;
        } catch (Exception e) {
            return null;
        }
    }

    int sizeX() { try { return lvX.getInt(level); } catch (Exception e) { return 0; } }
    int sizeY() { try { return lvY.getInt(level); } catch (Exception e) { return 0; } }
    int sizeZ() { try { return lvZ.getInt(level); } catch (Exception e) { return 0; } }
    byte[] blocks() { try { return (byte[]) lvBlocks.get(level); } catch (Exception e) { return null; } }
    /** Classic's water level around the map; Indev keeps it elsewhere, so -1 (the tracer looks at the map's edge). */
    int waterLevel() { try { return lvWater == null ? -1 : lvWater.getInt(level); } catch (Exception e) { return -1; } }

    /** The sky's colour: Classic's level says; Indev's sky is the same light blue by default. */
    int skyColor() {
        try { return indev ? 0x99CCFF : field(level.getClass(), "skyColor").getInt(level); } catch (Exception e) { return 0x99CCFF; }
    }

    Object player() {
        try {
            Object p = fPlayer.get(mc);
            if (p != null && px == null) {
                Class<?> c = p.getClass();
                if (indev) {
                    px = field(c, "h"); py = field(c, "i"); pz = field(c, "j");
                    pxo = field(c, "e"); pyo = field(c, "f"); pzo = field(c, "g");
                    pyaw = field(c, "n"); ppitch = field(c, "o"); pyawO = field(c, "p"); ppitchO = field(c, "q");
                } else {
                    px = field(c, "x"); py = field(c, "y"); pz = field(c, "z");
                    pxo = field(c, "xo"); pyo = field(c, "yo"); pzo = field(c, "zo");
                    pyaw = field(c, "yRot"); ppitch = field(c, "xRot"); pyawO = field(c, "yRotO"); ppitchO = field(c, "xRotO");
                }
            }
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    /** Eye position and view angles at this frame's partial tick: x, y, z, yaw, pitch (degrees). */
    float[] view(float a) {
        Object p = player();
        if (p == null) return null;
        try {
            // Indev's camera turns by yaw + 180 (glRotatef(yaw + 180, 0, 1, 0) in its renderer, as Alpha does); Classic's doesn't
            return new float[]{
                    lerp(pxo.getFloat(p), px.getFloat(p), a), lerp(pyo.getFloat(p), py.getFloat(p), a), lerp(pzo.getFloat(p), pz.getFloat(p), a),
                    lerp(pyawO.getFloat(p), pyaw.getFloat(p), a) + (indev ? 180f : 0f), lerp(ppitchO.getFloat(p), ppitch.getFloat(p), a)};
        } catch (Exception e) {
            return null;
        }
    }

    void setView(float yaw, float pitch) {
        Object p = player();
        try {
            pyaw.setFloat(p, yaw); pyawO.setFloat(p, yaw);
            ppitch.setFloat(p, pitch); ppitchO.setFloat(p, pitch);
        } catch (Exception ignored) {
        }
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    /** Each tile id's six face textures (terrain.png tile numbers), -1 for no tile. */
    int[][] faces() {
        int[][] out = new int[256][];
        try {
            Class<?> base = Class.forName(indev ? "net.minecraft.a.a.b.x" : "com.mojang.minecraft.level.tile.a", false, mc.getClass().getClassLoader());
            Object[] tiles = (Object[]) field(base, indev ? "c" : "b").get(null);
            // the texture-by-face method is the (int) -> int one the grass tile overrides
            Method tex = null;
            for (Method m : tiles[2].getClass().getDeclaredMethods())
                if (m.getReturnType() == int.class && m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == int.class) tex = m;
            Method b = null;
            for (Class<?> k = base; k != null && b == null; k = k.getSuperclass())
                try { b = k.getDeclaredMethod(tex.getName(), int.class); } catch (NoSuchMethodException ignored) {}
            b.setAccessible(true);
            for (int i = 1; i < tiles.length && i < 256; i++) {
                if (tiles[i] == null) continue;
                out[i] = new int[6];
                for (int f = 0; f < 6; f++) out[i][f] = (Integer) b.invoke(tiles[i], f);
            }
        } catch (Exception e) {
            Hooks.log("could not read the tiles' textures: " + e);
        }
        return out;
    }

    /** Places a block through the level's own setter (the test's block-change check). */
    boolean setTile(int x, int y, int z, int id) {
        try {
            if (indev) throw new UnsupportedOperationException(); // Indev's setter isn't named; write the array
            level.getClass().getMethod("setTile", int.class, int.class, int.class, int.class).invoke(level, x, y, z, id);
            return true;
        } catch (Exception e) {
            byte[] b = blocks();
            if (b == null) return false;
            b[(y * sizeZ() + z) * sizeX() + x] = (byte) id;
            return true;
        }
    }

    /** Indev's title screen: make a small level (as its menu's Generate does) and close the menu. */
    void generateLevel() {
        try {
            mc.getClass().getMethod("a", int.class, int.class, int.class, int.class).invoke(mc, 0, 0, 0, 0);
            closeScreen();
        } catch (Exception e) {
            Hooks.log("could not make a level: " + e);
        }
    }

    /** Indev: is the player holding a torch? (inventory = the player's field b: items a[], selected c; id in field c) */
    boolean holdingTorch() {
        if (!indev) return false; // Classic 0.30 has no torches
        try {
            Object p = player();
            Object inv = field(p.getClass(), "b").get(p);
            Object[] items = (Object[]) field(inv.getClass(), "a").get(inv);
            int sel = field(inv.getClass(), "c").getInt(inv);
            Object stack = sel >= 0 && sel < items.length ? items[sel] : null;
            return stack != null && field(stack.getClass(), "c").getInt(stack) == 50;
        } catch (Exception e) {
            return false;
        }
    }

    private Method chat;
    private Object hud;

    /** A line in the game's chat (the HUD's String method); the log only if there is none. */
    void message(String s) {
        try {
            if (chat == null) {
                hud = field(mc.getClass(), indev ? "t" : "w").get(mc);
                for (Method m : hud.getClass().getMethods())
                    if (m.getReturnType() == void.class && m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == String.class) chat = m;
            }
            if (chat != null) chat.invoke(hud, s);
        } catch (Exception ignored) {
        }
    }

    /** No menu open (keys only act in the game itself). */
    boolean noScreen() {
        try { if (screen == null) screen = field(mc.getClass(), "o"); return screen.get(mc) == null; } catch (Exception e) { return true; }
    }

    private Method setScreen;
    private Field screen;

    /** Closes whatever menu is open (the setter that takes the type of Minecraft's field o, the open screen). */
    void closeScreen() {
        try {
            if (screen == null) {
                screen = field(mc.getClass(), "o");
                for (Method m : mc.getClass().getMethods())
                    if (m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == screen.getType()) setScreen = m;
            }
            if (setScreen != null && screen.get(mc) != null) setScreen.invoke(mc, (Object) null);
        } catch (Exception ignored) {
        }
    }
}
