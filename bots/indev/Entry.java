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
            drawBots();
        }
        BotTest.frame(mc);
        boolean t = Keyboard.isCreated() && Keyboard.isKeyDown(Keyboard.KEY_T), slash = Keyboard.isCreated() && Keyboard.isKeyDown(Keyboard.KEY_SLASH);
        if (mc.world != null && mc.player != null && mc.screen == null && !BotTest.ON) {
            if (t && !tWas) mc.openScreen(new ChatScreen(""));
            else if (slash && !slashWas) mc.openScreen(new ChatScreen("/"));
        }
        tWas = t; slashWas = slash;
        VoiceChat.tick(mc.world != null && mc.screen == null, mc.player, new java.io.File(".")); // optional voice chat (the game runs in .minecraft)
        if (mc.world != null && mc.screen == null) BotList.nametags(mc); // names over the bots (nametags=true by default)
        if (mc.world != null && mc.screen == null && Keyboard.isCreated() && Keyboard.isKeyDown(Keyboard.KEY_TAB)) BotList.draw(mc); // hold Tab: the bots
        ChatLines.draw(mc);
    }

    /**
     * Indev finds an entity's renderer by its class or that class's direct superclass only: a bot (IndevBot, then
     * BotEntity, then the game's mob) had none, so it was never drawn. The bot classes get the mob renderer.
     */
    private static void drawBots() {
        try {
            Class<?> d = Class.forName("net.minecraft.client.a.a.i", true, Entry.class.getClassLoader());
            Object dispatcher = d.getField("a").get(null);
            java.lang.reflect.Field f = d.getDeclaredField("e");
            f.setAccessible(true);
            @SuppressWarnings("unchecked") java.util.Map<Class<?>, Object> m = (java.util.Map<Class<?>, Object>) f.get(dispatcher);
            Object mob = m.get(net.minecraft.entity.mob.MobEntity.class);
            if (mob == null) { Bots.log("no mob renderer to draw bots with"); return; }
            m.put(BotEntity.class, mob);
            m.put(IndevBot.class, mob);
        } catch (Throwable t) {
            Bots.log("bots won't be drawn: " + t);
        }
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
