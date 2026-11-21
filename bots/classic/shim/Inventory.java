package net.minecraft.inventory;

import net.minecraft.item.ItemStack;

/** No containers in Classic: nothing is ever one of these. */
public interface Inventory {
    int getSize();
    ItemStack getItem(int slot);
    ItemStack removeItem(int slot, int n);
    void setItem(int slot, ItemStack s);
    String getInventoryName();
    int getMaxStackSize();
    void markDirty();
}
