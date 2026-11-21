package net.minecraft.item;

/** Classic has blocks only, no items: the table is empty (the bowl is there only so Brain's stew line compiles). */
public class Item {
    public static final Item[] BY_ID = new Item[32000];
    public static final Item BOWL = new Item(25);
    public final int id;

    protected Item(int i) { id = 256 + i; }
}
