package com.mrmcclean.classicpt.bots;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;

/**
 * Where Classic PT's hooks come in (by reflection: Classic PT itself is built without the game's classes). Indev has no
 * mod loader, no chat and no entity registry, so this is the Fabric mixins' job for Alpha:
 *  - frame(mc): every frame. Bots on, the T key (or /) opens the chat box, chat lines on screen, the bot test.
 *  - entity(level, id): the level loader asks for an entity it doesn't know; "ClassicBot" is a bot.
 */
public final class Entry {
    private Entry() {}

    private static boolean started, tWas, slashWas;

    public static void frame(Object o) {
        Minecraft mc = (Minecraft) o;
        if (!started) {
            started = true;
            Bots.init();
            BotsClient.chat = ChatLines::add;
            BotTest.init(mc);
        }
        BotTest.frame(mc);
        boolean t = Keyboard.isCreated() && Keyboard.isKeyDown(Keyboard.KEY_T), slash = Keyboard.isCreated() && Keyboard.isKeyDown(Keyboard.KEY_SLASH);
        if (mc.world != null && mc.player != null && mc.screen == null && !BotTest.ON) {
            if (t && !tWas) mc.openScreen(new ChatScreen(""));
            else if (slash && !slashWas) mc.openScreen(new ChatScreen("/"));
        }
        tWas = t; slashWas = slash;
        ChatLines.draw(mc);
    }

    public static Object entity(Object level, String id) {
        return "ClassicBot".equals(id) ? Bots.factory.apply((World) level) : null;
    }

    /** What the player typed (the chat box's Enter). */
    static void said(Minecraft mc, String msg) {
        if (msg.trim().isEmpty() || mc.player == null) return;
        net.minecraft.entity.mob.player.PlayerEntity p = mc.player;
        if (!msg.trim().toLowerCase(java.util.Locale.ROOT).startsWith("/bot")) Bots.broadcast(Names.world(p), "<" + p.name + "> " + msg);
        Bots.chat(p, msg);
    }

}
