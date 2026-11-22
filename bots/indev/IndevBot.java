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

    /**
     * The light it is drawn in. Indev's renderer takes one block's light at y + c_() (half the height), and the light
     * in the block a mob stands in can read near 0 in open air (the test saw 0.05 there, 1.00 three blocks up), so bots
     * were drawn black. The brightest block of its body and the one over its head: under a real roof all of those are
     * dark anyway. Only the renderer calls c_(); a(float), the light its hurt flash uses, is the same light.
     */
    private int brightest() {
        int x = Compat.floor(xf), y = Compat.floor(yf), z = Compat.floor(zf), best = y + 1;
        float l = -1;
        for (int dy = 0; dy <= 2; dy++) { float v = world.obf$c(x, y + dy, z); if (v > l) { l = v; best = y + dy; } }
        return best;
    }

    @Override public float obf$c_() { return brightest() + 0.5F - yf; }

    @Override public float obf$a(float partial) { return world.obf$c(Compat.floor(xf), brightest(), Compat.floor(zf)); }
}
