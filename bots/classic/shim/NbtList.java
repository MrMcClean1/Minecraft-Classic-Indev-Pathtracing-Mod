package net.minecraft.nbt;

import java.util.ArrayList;
import java.util.List;

/** Alpha's NbtList for the Classic bots. */
public class NbtList extends NbtElement {
    private static final long serialVersionUID = 1L;
    private final List<NbtElement> l = new ArrayList<>();

    public void addElement(NbtElement e) { l.add(e); }
    public NbtElement get(int i) { return l.get(i); }
    public int size() { return l.size(); }
}
