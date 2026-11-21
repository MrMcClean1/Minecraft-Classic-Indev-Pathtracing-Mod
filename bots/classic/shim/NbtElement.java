package net.minecraft.nbt;

/**
 * Classic has no NBT; the bots' own (bots/classic/shim, moved next to Compat at build time). Classic saves a level
 * with Java serialization, so these are Serializable: a bot goes into the level file as its NbtCompound.
 */
public abstract class NbtElement implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
}
