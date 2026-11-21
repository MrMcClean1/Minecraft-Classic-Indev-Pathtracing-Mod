package com.mrmcclean.classicpt.bots;

import net.minecraft.entity.ai.BasicAI;

/** A bot's AI object: Classic's BasicAI (jumping, walking, pushing) with the bot deciding instead of wandering. */
public class BotAI extends BasicAI {
    private static final long serialVersionUID = 1L;
    private final ClassicBot bot;

    BotAI(ClassicBot bot) { this.bot = bot; }

    @Override
    public void update() {
        noActionTime = 0; // BasicAI removes a mob far from the player that has done nothing for a while
        bot.tickBot();
    }
}
