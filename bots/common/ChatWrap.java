package com.mrmcclean.classicpt.bots;

import java.util.ArrayList;
import java.util.List;

/**
 * Long chat on more than one line: the chat box you type in grows a line at a time (up to MAX_TYPED characters in
 * single player; a server takes the game's own 100), and long lines on screen (a bot's reply) break at spaces to
 * fit (each game's ChatScreen; ChatLines for the lines on screen).
 */
public final class ChatWrap {
    private ChatWrap() {}

    /** Enough for a long order to a bot. */
    public static final int MAX_TYPED = 256;
    /** For the bot test: lines the last wrapped chat line took on screen, and the chat box's lines when last drawn. */
    public static int pieces, typedLines;

    public interface Width { int of(String s); }

    /** The chat box, for the bot test: type into it as the keyboard would, and read what it holds. */
    public interface Keys { void classicbots$type(char c, int key); String classicbots$text(); }

    /** s in lines no wider than width pixels: broken at a space (a word longer than a line is cut), later lines indented. */
    public static List<String> wrap(Width w, String s, int width) {
        List<String> out = new ArrayList<>();
        String rest = s;
        while (rest.length() > 1 && w.of(rest) > width) {
            int end = 1;
            while (end < rest.length() && w.of(rest.substring(0, end + 1)) <= width) end++;
            int cut = rest.lastIndexOf(' ', end);
            if (cut < end / 3) cut = end; // no space to break at: cut the word
            out.add(rest.substring(0, cut));
            String next = rest.substring(cut).trim();
            rest = next.isEmpty() ? "" : "  " + next;
        }
        if (!rest.isEmpty() || out.isEmpty()) out.add(rest);
        return out;
    }

    /** For the bot test: when set, the next chat box frame drawn is saved there (the whole screen, chat lines and all). */
    public static java.io.File grabTo;

    /** The frame as drawn so far (the back buffer), saved to grabTo. */
    public static void grab(int w, int h) {
        java.io.File f = grabTo;
        grabTo = null;
        if (f == null) return;
        try {
            java.nio.ByteBuffer buf = org.lwjgl.BufferUtils.createByteBuffer(w * h * 3);
            org.lwjgl.opengl.GL11.glPixelStorei(org.lwjgl.opengl.GL11.GL_PACK_ALIGNMENT, 1);
            org.lwjgl.opengl.GL11.glReadPixels(0, 0, w, h, org.lwjgl.opengl.GL11.GL_RGB, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, buf);
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int i = ((h - 1 - y) * w + x) * 3;
                img.setRGB(x, y, (buf.get(i) & 255) << 16 | (buf.get(i + 1) & 255) << 8 | buf.get(i + 2) & 255);
            }
            f.getParentFile().mkdirs();
            javax.imageio.ImageIO.write(img, "png", f);
        } catch (Throwable ignored) {}
    }

    private static java.lang.reflect.Field mjfField;
    private static boolean mjfLooked;

    /**
     * MojangFix (in the modpack) gives the chat screen its own edit box (a cursor, history) and sends what it holds:
     * its text, with room made for a long line (max characters); null where there's no MojangFix.
     */
    public static String mojangFixText(int max) {
        try {
            if (!mjfLooked) { mjfLooked = true; mjfField = Class.forName("pl.telvarost.mojangfixstationapi.client.text.chat.ChatScreenVariables").getField("textField"); }
            if (mjfField == null) return null;
            Object tf = mjfField.get(null);
            if (tf == null) return null;
            String text = null;
            for (java.lang.reflect.Method m : tf.getClass().getMethods()) {
                String n = m.getName();
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == int.class && (n.equals("method_1878") || n.equals("setMaxLength"))) m.invoke(tf, max);
                else if (m.getParameterCount() == 0 && m.getReturnType() == String.class && (n.equals("method_1876") || n.equals("getText"))) text = (String) m.invoke(tf);
            }
            return text;
        } catch (Throwable t) { mjfField = null; return null; }
    }
}
