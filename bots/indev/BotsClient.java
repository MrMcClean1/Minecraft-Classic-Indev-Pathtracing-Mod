package com.mrmcclean.classicpt.bots;

import net.minecraft.world.World;

/** Client bits on Indev: chat lines on screen (Classic PT's Game finds the HUD's message call). */
final class BotsClient {
    private BotsClient() {}

    /** Set by Classic PT: shows a line in the game's chat. */
    static java.util.function.Consumer<String> chat;

    static void show(String msg) { if (chat != null) chat.accept(msg); }

    static String name(int id) { return null; }

    static void markers(World w) {}
}
