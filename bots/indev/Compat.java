package com.mrmcclean.classicpt.bots;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

/**
 * Indev 20100223's side of the members Alpha has and Indev doesn't (the STUB lines in bots/indev.mappings). The bots
 * are Alpha's sources; after compiling, tools/Remap turns each use of a stub into a call here: a field read into
 * get_name(owner), a write into set_name(owner, value), a method call into name(owner, args...).
 *
 * Indev keeps positions as floats and has no chunks, block metadata setter with the id, entity lists by type, or player
 * names; a level holds one player.
 */
public final class Compat {
    private Compat() {}

    /** The player's name for the bots (Indev has none; Classic PT sets the launcher's user name). */
    public static String playerName = "Player";

    // ------------------------------------------------------------------ positions (double in Alpha, float here)

    public static double get_x(Entity e) { return e.xf; }
    public static double get_y(Entity e) { return e.yf; }
    public static double get_z(Entity e) { return e.zf; }
    public static void set_x(Entity e, double v) { e.xf = (float) v; }
    public static void set_y(Entity e, double v) { e.yf = (float) v; }
    public static void set_z(Entity e, double v) { e.zf = (float) v; }
    public static double get_velocityX(Entity e) { return e.velocityXf; }
    public static double get_velocityY(Entity e) { return e.velocityYf; }
    public static double get_velocityZ(Entity e) { return e.velocityZf; }
    public static void set_velocityX(Entity e, double v) { e.velocityXf = (float) v; }
    public static void set_velocityY(Entity e, double v) { e.velocityYf = (float) v; }
    public static void set_velocityZ(Entity e, double v) { e.velocityZf = (float) v; }

    public static double get_minX(Box b) { return b.minXf; }
    public static double get_minY(Box b) { return b.minYf; }
    public static double get_minZ(Box b) { return b.minZf; }
    public static double get_maxX(Box b) { return b.maxXf; }
    public static double get_maxY(Box b) { return b.maxYf; }
    public static double get_maxZ(Box b) { return b.maxZf; }
    public static void set_minX(Box b, double v) { b.minXf = (float) v; }
    public static void set_minY(Box b, double v) { b.minYf = (float) v; }
    public static void set_minZ(Box b, double v) { b.minZf = (float) v; }
    public static void set_maxX(Box b, double v) { b.maxXf = (float) v; }
    public static void set_maxY(Box b, double v) { b.maxYf = (float) v; }
    public static void set_maxZ(Box b, double v) { b.maxZf = (float) v; }

    public static Box grown(Box b, double x, double y, double z) {
        return new Box(b.minXf - (float) x, b.minYf - (float) y, b.minZf - (float) z, b.maxXf + (float) x, b.maxYf + (float) y, b.maxZf + (float) z);
    }

    public static void setPosition(Entity e, double x, double y, double z) { call(SET_POS, e, (float) x, (float) y, (float) z); }

    /** Alpha's setPositionAndAngles takes the feet for mobs and the eyes for players; Indev's moveTo adds eyeHeight itself. */
    public static void setPositionAndAngles(Entity e, double x, double y, double z, float yaw, float pitch) {
        e.setPositionAndAnglesf((float) x, (float) y, (float) z, yaw, pitch);
    }

    public static float distanceTo(Entity a, Entity b) { return (float) Math.sqrt(squaredDistanceTo(a, b)); }

    public static double squaredDistanceTo(Entity a, Entity b) {
        double dx = a.xf - b.xf, dy = a.yf - b.yf, dz = a.zf - b.zf;
        return dx * dx + dy * dy + dz * dz;
    }

    public static float getEyeHeight(Entity e) { return e instanceof PlayerEntity ? 0 : e.height * 0.85F; }

    /** Line of sight eye to eye (Alpha's canSee), with Indev's own ray through blocks. */
    public static boolean canSee(Entity a, Entity b) {
        Vec3d from = new Vec3d(a.xf, a.yf + getEyeHeight(a), a.zf), to = new Vec3d(b.xf, b.yf + getEyeHeight(b), b.zf);
        return world(a).rayTrace(from, to) == null;
    }

    public static boolean isSubmergedIn(Entity e, Material m) {
        if (m == Material.WATER) return e.isEyeInWater();
        World w = world(e);
        int id = w.getBlock(floor(e.xf), floor(e.yf + getEyeHeight(e)), floor(e.zf));
        Block b = id > 0 ? Block.BY_ID[id] : null;
        return b != null && b.material == m;
    }

    /** The block at its eyes is solid (Alpha's isInsideWall). */
    public static boolean isInWall(Entity e) {
        World w = world(e);
        int id = w.getBlock(floor(e.xf), floor(e.yf + getEyeHeight(e)), floor(e.zf));
        Block b = id > 0 ? Block.BY_ID[id] : null;
        return b != null && b.material.blocksMovement();
    }

    /** No right-click on entities in Indev: never called by the game. */
    public static boolean interact(Entity e, PlayerEntity p) { return false; }

    public static String getTexture(MobEntity m) { return (String) get(TEXTURE, m); }

    private static final Map<Entity, Float> swing = new WeakHashMap<>();
    public static float get_attackAnimationProgress(MobEntity m) { Float f = swing.get(m); return f == null ? 0 : f; }
    public static void set_attackAnimationProgress(MobEntity m, float v) { swing.put(m, v); }
    public static boolean get_dead(MobEntity m) { return m.health <= 0; }
    public static void set_dead(MobEntity m, boolean v) {}
    public static boolean get_interpolateOnly(MobEntity m) { return false; }
    public static void set_interpolateOnly(MobEntity m, boolean v) {}

    public static String get_name(PlayerEntity p) { return playerName; }
    public static void set_name(PlayerEntity p, String n) {}

    public static World world(Entity e) { return (World) get(WORLD, e); }

    public static float hardness(Block b) { Object f = get(HARDNESS, b); return f == null ? 1 : (Float) f; }

    public static double distanceTo(Entity e, double x, double y, double z) {
        double dx = e.xf - x, dy = e.yf - y, dz = e.zf - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ------------------------------------------------------------------ what Indev keeps protected or private (its own names)

    private static final java.lang.reflect.Field WORLD = field(Entity.class, "d"), TEXTURE = field(MobEntity.class, "V"), HARDNESS = field(Block.class, "aL");
    private static final java.lang.reflect.Method SET_POS = method(Entity.class, "b", float.class, float.class, float.class);

    private static java.lang.reflect.Field field(Class<?> c, String n) {
        try { java.lang.reflect.Field f = c.getDeclaredField(n); f.setAccessible(true); return f; } catch (Exception e) { Bots.log("Compat: no field " + n + ": " + e); return null; }
    }

    private static java.lang.reflect.Method method(Class<?> c, String n, Class<?>... args) {
        try { java.lang.reflect.Method m = c.getDeclaredMethod(n, args); m.setAccessible(true); return m; } catch (Exception e) { Bots.log("Compat: no method " + n + ": " + e); return null; }
    }

    private static Object get(java.lang.reflect.Field f, Object o) { try { return f.get(o); } catch (Exception e) { return null; } }

    private static void call(java.lang.reflect.Method m, Object o, Object... args) { try { m.invoke(o, args); } catch (Exception e) { Bots.log("Compat: " + m.getName() + ": " + e); } }

    public static ItemEntity newItemEntity(World w, double x, double y, double z, ItemStack s) { return new ItemEntity(w, (float) x, (float) y, (float) z, s); }

    // ------------------------------------------------------------------ the level

    public static List get_entities(World w) { return w.entityMap.all; }
    public static void set_entities(World w, List l) {}

    public static List get_players(World w) {
        return w.player instanceof PlayerEntity ? Collections.singletonList(w.player) : Collections.emptyList();
    }
    public static void set_players(World w, List l) {}

    public static boolean get_isMultiplayer(World w) { return false; }
    public static void set_isMultiplayer(World w, boolean v) {}

    public static long get_ticks(World w) { return w.time & 0xFFFFFFFFL; }
    public static void set_ticks(World w, long v) { w.time = (int) v; }
    public static void setTime(World w, long v) { w.time = (int) v; }

    public static int getBlockMetadata(World w, int x, int y, int z) { return inside(w, x, y, z) ? w.getData(x, y, z) & 15 : 0; }

    public static boolean setBlockWithMetadata(World w, int x, int y, int z, int id, int meta) {
        if (!setBlock(w, x, y, z, id)) return false;
        w.setData(x, y, z, meta);
        return true;
    }

    /** Only air or a block Indev has (Indev's setter takes any id, then can't draw it: a door froze the game). */
    public static boolean setBlock(World w, int x, int y, int z, int id) {
        if (!inside(w, x, y, z) || id < 0 || id >= Block.BY_ID.length || id != 0 && Block.BY_ID[id] == null) return false;
        return w.setTile(x, y, z, id);
    }

    public static List getEntitiesOfType(World w, Class<?> type, Box b) {
        List<Entity> out = new ArrayList<>();
        for (Object o : w.entityMap.all) {
            Entity e = (Entity) o;
            if (!type.isInstance(e)) continue;
            Box s = e.shape;
            if (s.maxXf > b.minXf && s.minXf < b.maxXf && s.maxYf > b.minYf && s.minYf < b.maxYf && s.maxZf > b.minZf && s.minZf < b.maxZf) out.add(e);
        }
        return out;
    }

    /** The first air above the highest block that isn't air (Alpha's getHeight, from the height map). */
    public static int getHeight(World w, int x, int z) {
        if (x < 0 || z < 0 || x >= w.sizeX || z >= w.sizeZ) return 0;
        for (int y = w.sizeY - 1; y >= 0; y--) if (w.getBlock(x, y, z) != 0) return y + 1;
        return 0;
    }

    public static net.minecraft.block.entity.BlockEntity getBlockEntity(World w, int x, int y, int z) {
        if (!inside(w, x, y, z)) return null;
        int id = w.getBlock(x, y, z);
        return id > 0 && Block.BY_ID[id] instanceof net.minecraft.block.BlockWithBlockEntity ? w.getOrMakeBlockEntity(x, y, z) : null;
    }

    public static boolean isChunkLoaded(World w, int x, int y, int z) { return inside(w, x, y, z); }

    public static boolean addEntity(World w, Entity e) { w.spawnEntity(e); return true; }

    public static void playSound(World w, double x, double y, double z, String s, float v, float p) { w.playSoundf((float) x, (float) y, (float) z, s, v, p); }

    static boolean inside(World w, int x, int y, int z) { return x >= 0 && y >= 0 && z >= 0 && x < w.sizeX && y < w.sizeY && z < w.sizeZ; }

    // ------------------------------------------------------------------ items

    public static ItemStack copy(ItemStack s) { return new ItemStack(s.id, s.size, s.metadata); }

    public static int getMaxSize(ItemStack s) { Item i = s.id >= 256 ? Item.BY_ID[s.id] : null; return i == null ? 64 : i.obf$c(); }

    /** Indev: a block needs the right tool if it is stone or iron (the player's canHarvest). */
    public static boolean canMineBlock(ItemStack s, Block b) { Item i = s.id >= 256 ? Item.BY_ID[s.id] : null; return i != null && i.obf$a(b); }

    public static float getMiningSpeed(ItemStack s, Block b) { Item i = s.id >= 256 ? Item.BY_ID[s.id] : null; return i == null ? 1 : i.obf$b(b); }

    public static int getAttackDamage(ItemStack s, Entity e) { Item i = s.id >= 256 ? Item.BY_ID[s.id] : null; return i == null ? 1 : i.obf$a(); }

    public static void markDirty(Inventory i) {}

    public static Material get_SNOW() { return null; }
    public static void set_SNOW(Material m) {}
    public static Material get_SNOW_LAYER() { return null; }
    public static void set_SNOW_LAYER(Material m) {}

    static int floor(float f) { int i = (int) f; return f < i ? i - 1 : i; }
}
