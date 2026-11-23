package com.mrmcclean.classicpt.gui;

import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Method;

/**
 * Classic PT's settings screen as the game's own screen (so the game stops taking input while it is open). Written
 * against Alpha's names like the bots' screens and built once per game (build.ps1: package gui.classic / gui.indev,
 * then remapped to the game's names with bots/<game>.mappings). Everything else is in SettingsUi, reached by name:
 * the remapper accepts only the game's classes, the JDK's and LWJGL's here. SettingsUi draws back through rect, text
 * and textWidth below. Classic draws a screen with render(int, int), Indev with render(int, int, float): both are
 * here, the game calls its own.
 */
public class PtScreen extends Screen {
    private Object ui;
    private Method frame, back, closed;

    public PtScreen() {
        try {
            Class<?> c = Class.forName("com.mrmcclean.classicpt.SettingsUi", true, PtScreen.class.getClassLoader());
            ui = c.getMethod("open").invoke(null);
            frame = c.getMethod("frame", Object.class, int.class, int.class, int.class, int.class);
            back = c.getMethod("back");
            closed = c.getMethod("closed");
        } catch (Exception e) {
            throw new IllegalStateException("Classic PT settings: " + e, e);
        }
    }

    @Override public void init() { Keyboard.enableRepeatEvents(false); }

    @Override public void removed() { call(closed); }

    public void render(int mouseX, int mouseY) { draw(mouseX, mouseY); }

    public void render(int mouseX, int mouseY, float delta) { draw(mouseX, mouseY); }

    private void draw(int mouseX, int mouseY) {
        if (Boolean.TRUE.equals(call(frame, this, width, height, mouseX, mouseY))) minecraft.openScreen(null);
    }

    @Override
    protected void keyPressed(char c, int key) {
        if ((key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_F12) && Boolean.TRUE.equals(call(back))) minecraft.openScreen(null);
    }

    private Object call(Method m, Object... args) {
        try { return m.invoke(ui, args); } catch (Exception e) { System.out.println("[ClassicPT] settings screen: " + e); return null; }
    }

    // SettingsUi's painter
    public void rect(int x0, int y0, int x1, int y1, int argb) { fill(x0, y0, x1, y1, argb); }

    public void text(String s, int x, int y, int argb) { textRenderer.drawWithShadow(s, x, y, argb); }

    public int textWidth(String s) { return textRenderer.getWidth(s); }
}
