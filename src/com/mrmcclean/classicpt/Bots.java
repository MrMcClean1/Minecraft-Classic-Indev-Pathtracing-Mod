package com.mrmcclean.classicpt;

import java.lang.reflect.Method;

/**
 * The way to Classic Bots (Alpha PT's bots, compiled per game against its own classes; see bots/ and build.ps1). Classic
 * PT itself is built without the game's classes, so the bots' entry class is found by name in the game's class loader.
 */
final class Bots {
    private Bots() {}

    private static Method frame, entity;
    private static boolean tried;

    private static boolean load(ClassLoader cl, boolean indev) {
        if (tried) return frame != null;
        tried = true;
        if (!indev) return false; // Classic 0.30: not yet
        try {
            Class<?> c = Class.forName("com.mrmcclean.classicpt.bots.indev.Entry", true, cl);
            frame = c.getMethod("frame", Object.class);
            entity = c.getMethod("entity", Object.class, String.class);
            return true;
        } catch (Throwable t) {
            Hooks.log("Classic Bots not loaded: " + t);
            return false;
        }
    }

    static void frame(Game g) throws Exception {
        if (load(g.mc.getClass().getClassLoader(), g.indev)) frame.invoke(null, g.mc);
    }

    static Object entity(Object level, String id) throws Exception {
        if (!tried) load(level.getClass().getClassLoader(), level.getClass().getName().startsWith("net.minecraft.a."));
        return entity == null ? null : entity.invoke(null, level, id);
    }
}
