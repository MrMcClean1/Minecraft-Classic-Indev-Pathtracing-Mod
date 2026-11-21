package com.mrmcclean.classicpt.bots;

import net.minecraft.world.World;

/**
 * Indev's bot: Alpha's BotEntity plus what Indev needs on top. Its save id ("ClassicBot", read back by the level loader
 * through Classic PT's hook), and no despawning (Indev removes a mob that has been far from the player for a while).
 */
public class IndevBot extends BotEntity {
    public IndevBot(World w) { super(w); }

    @Override protected String getSaveId() { return "ClassicBot"; }

    @Override public void tickMovement() { despawnCounter = 0; super.tickMovement(); }
}
