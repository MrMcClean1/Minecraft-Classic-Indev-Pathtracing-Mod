package net.minecraft.nbt;

import java.util.HashMap;
import java.util.Map;

/** Alpha's NbtCompound for the Classic bots: a map, saved with the level by Java serialization. */
public class NbtCompound extends NbtElement {
    private static final long serialVersionUID = 1L;
    private final Map<String, Object> m = new HashMap<>();

    public void put(String k, NbtElement v) { m.put(k, v); }
    public void putByte(String k, byte v) { m.put(k, v); }
    public void putShort(String k, short v) { m.put(k, v); }
    public void putInt(String k, int v) { m.put(k, v); }
    public void putLong(String k, long v) { m.put(k, v); }
    public void putFloat(String k, float v) { m.put(k, v); }
    public void putDouble(String k, double v) { m.put(k, v); }
    public void putString(String k, String v) { m.put(k, v); }
    public void putCompound(String k, NbtCompound v) { m.put(k, v); }
    public void putBoolean(String k, boolean v) { m.put(k, v); }
    public boolean contains(String k) { return m.containsKey(k); }

    private Number num(String k) { Object o = m.get(k); return o instanceof Number ? (Number) o : 0; }
    public byte getByte(String k) { return num(k).byteValue(); }
    public short getShort(String k) { return num(k).shortValue(); }
    public int getInt(String k) { return num(k).intValue(); }
    public long getLong(String k) { return num(k).longValue(); }
    public float getFloat(String k) { return num(k).floatValue(); }
    public double getDouble(String k) { return num(k).doubleValue(); }
    public String getString(String k) { Object o = m.get(k); return o instanceof String ? (String) o : ""; }
    public NbtCompound getCompound(String k) { Object o = m.get(k); return o instanceof NbtCompound ? (NbtCompound) o : new NbtCompound(); }
    public NbtList getList(String k) { Object o = m.get(k); return o instanceof NbtList ? (NbtList) o : new NbtList(); }
    public boolean getBoolean(String k) { Object o = m.get(k); return o instanceof Boolean ? (Boolean) o : getByte(k) != 0; }
}
