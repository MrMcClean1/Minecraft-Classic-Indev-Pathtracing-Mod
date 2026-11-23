package com.mrmcclean.classicpt.bots;

import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Keyboard;

/** The bots' chat box (T, or / for a command): Classic's own is for servers only. */
public class ChatScreen extends Screen {
    private String text;
    private int ticks;

    public ChatScreen(String start) { text = start; }

    @Override public void init() { Keyboard.enableRepeatEvents(true); }

    @Override public void removed() { Keyboard.enableRepeatEvents(false); }

    @Override public void tick() { ticks++; }

    /** What the box holds (the bot test types into it with keyPressed). */
    String typed() { return text; }

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
        if (c >= ' ' && c != 127 && text.length() < ChatWrap.MAX_TYPED) text += c; // a full order to a bot: the box grows a line at a time
    }

    @Override
    public void render(int mouseX, int mouseY) {
        java.util.List<String> l = ChatWrap.wrap(s -> textRenderer.getWidth(s), "> " + text + (ticks / 6 % 2 == 0 ? "_" : ""), width - 8);
        int top = height - 2 - 12 * l.size();
        fill(2, top, width - 2, height - 2, Integer.MIN_VALUE);
        for (int i = 0; i < l.size(); i++) textRenderer.drawWithShadow(l.get(i), 4, top + 2 + 12 * i, 0xE0E0E0);
        ChatWrap.typedLines = l.size();
    }
}
