package com.mrmcclean.classicpt.bots;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.mob.player.PlayerEntity;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Hold Tab: the bots in this level, one block of lines each (name, how far, what it's doing, what it carries), like
 * a player list. The same file in Indev and Classic (bots/common).
 */
final class BotList {
    private BotList() {}

    static void draw(Minecraft mc) {
        if (mc.textRenderer == null || mc.world == null || mc.player == null) return;
        PlayerEntity p = mc.player;
        List<BotEntity> bots = Bots.bots(mc.world);
        int h = 240, w = mc.displayWidth * h / Math.max(1, mc.displayHeight);
        int boxW = Math.min(w - 8, 300), x0 = (w - boxW) / 2, lines = 1 + Math.max(1, bots.size() * 3), y0 = 12;
        begin(w, h);
        fill(x0 - 2, y0 - 2, x0 + boxW + 2, y0 + lines * 9 + 2, 0x90000000);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        mc.textRenderer.drawWithShadow("Bots: " + bots.size() + (BotConfig.enabled ? "" : " (off: /bot on)"), x0, y0, 0xFFFFA0);
        int y = y0 + 9;
        if (bots.isEmpty()) mc.textRenderer.drawWithShadow("none here; /bot spawn [name]", x0, y, 0xA0A0A0);
        for (BotEntity b : bots) {
            double dx = b.x - p.x, dz = b.z - p.z;
            mc.textRenderer.drawWithShadow(b.botName() + "  " + Math.round(Math.sqrt(dx * dx + dz * dz)) + " blocks, at "
                    + (int) Math.floor(b.x) + " " + (int) Math.floor(b.y) + " " + (int) Math.floor(b.z), x0, y, 0xFFFFFF);
            mc.textRenderer.drawWithShadow("  " + cut(mc, b.brain.statusLine(), boxW - 8), x0, y + 9, 0xC0C0C0);
            mc.textRenderer.drawWithShadow("  carrying " + cut(mc, b.brain.inventoryLine(), boxW - 60), x0, y + 18, 0xA0A0A0);
            y += 27;
        }
        end();
    }

    private static String cut(Minecraft mc, String s, int width) {
        if (mc.textRenderer.getWidth(s) <= width) return s;
        while (s.length() > 3 && mc.textRenderer.getWidth(s + "...") > width) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    private static void fill(int x1, int y1, int x2, int y2, int argb) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f((argb >> 16 & 255) / 255F, (argb >> 8 & 255) / 255F, (argb & 255) / 255F, (argb >>> 24) / 255F);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x1, y2); GL11.glVertex2f(x2, y2); GL11.glVertex2f(x2, y1); GL11.glVertex2f(x1, y1);
        GL11.glEnd();
        GL11.glColor4f(1, 1, 1, 1);
    }

    private static void begin(int w, int h) {
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
        GL11.glColor4f(1, 1, 1, 1);
    }

    private static void end() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
    }
}
