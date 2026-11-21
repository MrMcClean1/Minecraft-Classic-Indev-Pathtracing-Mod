package com.mrmcclean.classicpt.bots;

import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Keyboard;

/** Indev has no chat; this is the bots' own chat box (T, or / for a command), like Alpha's multiplayer one. */
public class ChatScreen extends Screen {
    private String text;
    private int ticks;

    public ChatScreen(String start) { text = start; }

    @Override public void init() { Keyboard.enableRepeatEvents(true); }

    @Override public void removed() { Keyboard.enableRepeatEvents(false); }

    @Override public void tick() { ticks++; }

    @Override
    protected void keyPressed(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE) { minecraft.openScreen(null); return; }
        if (key == Keyboard.KEY_RETURN) {
            String t = text.trim();
            minecraft.openScreen(null);
            Entry.said(minecraft, t);
            return;
        }
        if (key == Keyboard.KEY_BACK) { if (!text.isEmpty()) text = text.substring(0, text.length() - 1); return; }
        if (c >= ' ' && c != 127 && text.length() < 100) text += c;
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        DrawableHelper.fill(2, height - 14, width - 2, height - 2, Integer.MIN_VALUE);
        textRenderer.drawWithShadow("> " + text + (ticks / 6 % 2 == 0 ? "_" : ""), 4, height - 12, 0xE0E0E0);
    }

    @Override public boolean shouldPauseGame() { return false; }
}
