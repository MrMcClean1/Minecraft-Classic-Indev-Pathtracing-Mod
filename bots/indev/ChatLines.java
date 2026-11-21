package com.mrmcclean.classicpt.bots;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/** The chat lines on screen: Indev's HUD keeps a chat list but draws no text, so the bots' lines are drawn here. */
final class ChatLines {
    private ChatLines() {}

    private static final List<String> lines = new ArrayList<>();
    private static final List<Long> times = new ArrayList<>();

    static synchronized void add(String s) {
        // long lines wrapped at about 60 characters, as Alpha's chat does by width
        while (s.length() > 64) {
            int cut = s.lastIndexOf(' ', 64);
            if (cut < 20) cut = 64;
            put(s.substring(0, cut));
            s = "  " + s.substring(cut).trim();
        }
        put(s);
    }

    private static void put(String s) {
        lines.add(0, s);
        times.add(0, System.currentTimeMillis());
        while (lines.size() > 50) { lines.remove(lines.size() - 1); times.remove(times.size() - 1); }
    }

    /** The GUI's own size: Indev lays its screens out 240 high. */
    static int guiWidth(Minecraft mc) { return mc.displayWidth * 240 / Math.max(1, mc.displayHeight); }

    /** Over everything, the last 10 lines (all of them fade after 10 s; with the chat box open, none fade). */
    static synchronized void draw(Minecraft mc) {
        if (mc.textRenderer == null || mc.world == null) return;
        boolean open = mc.screen instanceof ChatScreen;
        long now = System.currentTimeMillis();
        int w = guiWidth(mc), h = 240, shown = 0;
        begin(w, h);
        for (int i = 0; i < lines.size() && shown < 10; i++) {
            long age = now - times.get(i);
            if (!open && age > 10000) break;
            int alpha = open ? 255 : (int) Math.max(0, Math.min(255, (10000 - age) / 4));
            if (alpha < 8) continue;
            int y = h - 32 - shown * 9;
            mc.textRenderer.drawWithShadow(lines.get(i), 2, y, 0xFFFFFF | alpha << 24);
            shown++;
        }
        end();
    }

    static void begin(int w, int h) {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, w, h, 0, 1000, 3000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glTranslatef(0, 0, -2000);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
    }

    static void end() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
    }
}
