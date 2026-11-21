package com.mrmcclean.classicpt.bots;

import net.minecraft.block.Block;

/** Classic has no block names, so the bots carry their own (Classic's ids, which Alpha's match); plus the block facts they need. */
public final class Names {
    private Names() {}

    /** Classic has no crafting: bots gather, build, follow and fight with what they find. */
    static final boolean CRAFTING = false;

    private static final String[] NAMES = new String[400];
    static {
        String[] b = {"", "stone", "grass", "dirt", "cobblestone", "planks", "sapling", "bedrock", "water", "water", "lava", "lava", "sand", "gravel",
                "gold ore", "iron ore", "coal ore", "wood", "leaves", "sponge", "glass", "red cloth", "orange cloth", "yellow cloth", "lime cloth",
                "green cloth", "aqua cloth", "cyan cloth", "blue cloth", "purple cloth", "indigo cloth", "violet cloth", "magenta cloth", "pink cloth",
                "black cloth", "gray cloth", "white cloth", "dandelion", "rose", "brown mushroom", "red mushroom", "gold block", "iron block",
                "double slab", "slab", "bricks", "tnt", "bookshelf", "mossy cobblestone", "obsidian"};
        System.arraycopy(b, 0, NAMES, 0, b.length);
    }

    static String key(int id) { return id >= 0 && id < NAMES.length && NAMES[id] != null ? NAMES[id] : ""; }

    static String name(int id) { String k = key(id); return k.isEmpty() ? "thing " + id : k; }

    /** How long a block takes to break. */
    static float hardness(Block b) { return Compat.hardness(b); }

    /** Breaks by hand with drops: everything in Classic but bedrock. */
    static boolean handMineable(Block b) { return b.id != 7; }

    static void breakEffect(net.minecraft.world.World w, int x, int y, int z, int id, int meta) {
        Block b = id > 0 && id < Block.BY_ID.length ? Block.BY_ID[id] : null;
        if (b != null && b.soundType != null) w.playSoundf("step." + b.soundType.obf$name, x + 0.5F, y + 0.5F, z + 0.5F, 1.0F, 0.8F);
    }

    /** Single player: an answer to a command shows in chat. */
    static void tell(net.minecraft.entity.mob.player.PlayerEntity p, String msg) { BotsClient.show(msg); }

    public static float eye(net.minecraft.entity.Entity e) { return e.height * 0.85F; }

    public static net.minecraft.world.World world(net.minecraft.entity.Entity e) { return Compat.world(e); }

    static int food(int id) { return 0; } // no food in Classic
}
