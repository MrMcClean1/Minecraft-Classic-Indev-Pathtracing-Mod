package net.minecraft.item;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;

/** Alpha's ItemStack for the Classic bots: a block id and a count (Classic's own inventory keeps the two in arrays). */
public class ItemStack implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    public int size, id, metadata;

    public ItemStack(int id, int size, int metadata) { this.id = id; this.size = size; this.metadata = metadata; }
    public ItemStack(int id) { this(id, 1, 0); }
    public ItemStack(Item i) { this(i.id, 1, 0); }
    public ItemStack(Block b) { this(b.id, 1, 0); }
    public ItemStack(NbtCompound n) { this(n.getShort("id"), n.getByte("Count"), n.getShort("Damage")); }

    public NbtCompound writeNbt(NbtCompound n) { n.putShort("id", (short) id); n.putByte("Count", (byte) size); n.putShort("Damage", (short) metadata); return n; }
    public Item getItem() { return id >= 256 ? Item.BY_ID[id] : null; }
    public ItemStack copy() { return new ItemStack(id, size, metadata); }
    public int getMaxSize() { return 99; } // Classic's inventory slot
    public boolean canMineBlock(Block b) { return false; }
    public float getMiningSpeed(Block b) { return 1; }
    public int getAttackDamage(Entity e) { return 1; }
    public void takeDamageAndBreak(int n) {}
}
