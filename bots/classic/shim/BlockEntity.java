package net.minecraft.block.entity;

import net.minecraft.world.World;

/** No block entities in Classic: World.getBlockEntity is always null. */
public class BlockEntity {
    public World world;
    public int x, y, z;
}
