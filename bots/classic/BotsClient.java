package com.mrmcclean.classicpt.bots;

import net.minecraft.world.World;

/** Client bits on Classic: lines go to the HUD's own chat (Entry sets it). */
final class BotsClient {
    private BotsClient() {}

    /** Set by Entry: shows a line in the game's chat. */
    static java.util.function.Consumer<String> chat;

    static void show(String msg) { VoiceChat.shown(msg); if (chat != null) chat.accept(msg); }

    static String name(int id) { return null; }

    static void markers(World w) {}
}
