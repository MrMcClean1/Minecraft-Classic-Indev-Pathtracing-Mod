package com.mrmcclean.classicpt.bots;

import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/**
 * Where Classic PT's hooks come in (by reflection: Classic PT itself is built without the game's classes). Classic has
 * no mod loader and its chat box is for servers only, so this is the Fabric mixins' job for Alpha:
 *  - frame(mc): every frame. Bots on, the T key (or /) opens the bots' chat box, their lines go to the HUD's chat, the
 *    bot test.
 *  - entity(level, id): nothing on Classic (a level is saved with Java serialization, which knows the bot's class).
 */
public final class Entry {
    private Entry() {}

    private static boolean started, tWas, slashWas;

    public static void frame(Object o) {
        Minecraft mc = (Minecraft) o;
        if (!started) {
            started = true;
            Bots.init();
            BotsClient.chat = s -> { if (mc.hud != null) mc.hud.addChatMessage(s); };
            BotTest.init(mc);
        }
        BotTest.frame(mc);
        boolean t = Keyboard.isCreated() && Keyboard.isKeyDown(Keyboard.KEY_T), slash = Keyboard.isCreated() && Keyboard.isKeyDown(Keyboard.KEY_SLASH);
        if (mc.world != null && mc.player != null && mc.screen == null && !BotTest.ON) {
            if (t && !tWas) mc.openScreen(new ChatScreen(""));
            else if (slash && !slashWas) mc.openScreen(new ChatScreen("/"));
        }
        tWas = t; slashWas = slash;
        if (mc.world != null && mc.screen == null && Keyboard.isCreated() && Keyboard.isKeyDown(Keyboard.KEY_TAB)) BotList.draw(mc); // hold Tab: the bots
    }

    public static Object entity(Object level, String id) { return null; }

    /** What the player typed (the chat box's Enter). */
    static void said(Minecraft mc, String msg) {
        if (msg.trim().isEmpty() || mc.player == null) return;
        net.minecraft.entity.mob.player.PlayerEntity p = mc.player;
        if (!msg.trim().toLowerCase(java.util.Locale.ROOT).startsWith("/bot")) Bots.broadcast(Names.world(p), "<" + p.name + "> " + msg);
        Bots.chat(p, msg);
    }
}
