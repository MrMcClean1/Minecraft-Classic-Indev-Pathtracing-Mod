package com.mrmcclean.classicpt.bots;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.BasicAI;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.monster.CreeperEntity;
import net.minecraft.entity.mob.monster.SpiderEntity;
import net.minecraft.entity.mob.monster.ZombieEntity;
import net.minecraft.entity.mob.player.PlayerEntity;
import net.minecraft.entity.mob.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

/**
 * Classic 0.30's side of the members Alpha has and Classic doesn't (the STUB lines in bots/classic.mappings). The bots
 * are Alpha's sources; after compiling, tools/Remap turns each use of a stub into a call here: a field read into
 * get_name(owner), a write into set_name(owner, value), a method call into name(owner, args...).
 *
 * Classic keeps positions as floats and has no item stacks (an inventory is block ids and counts), no metadata, no
 * block entities, no materials, no NBT (a level is saved with Java serialization) and one player. A mob walks by its AI
 * object (BasicAI's xxa, yya, jumping), so the bots' forward and sideways speeds go there.
 */
public final class Compat {
    private Compat() {}

    /** The player's name for the bots (single player Classic has none that matters). */
    public static String playerName = "Player";
    private static final Random RANDOM = new Random();

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

    public static int floor(double d) { int i = (int) d; return d < i ? i - 1 : i; }
    public static int floor(float f) { int i = (int) f; return f < i ? i - 1 : i; }

    public static void setPosition(Entity e, double x, double y, double z) { e.setPositionf((float) x, (float) y, (float) z); }

    public static void setPositionAndAngles(Entity e, double x, double y, double z, float yaw, float pitch) {
        e.setPositionAndAnglesf((float) x, (float) y, (float) z, yaw, pitch);
    }

    public static double squaredDistanceTo(Entity a, Entity b) {
        double dx = a.xf - b.xf, dy = a.yf - b.yf, dz = a.zf - b.zf;
        return dx * dx + dy * dy + dz * dz;
    }

    public static double distanceTo(Entity e, double x, double y, double z) {
        double dx = e.xf - x, dy = e.yf - y, dz = e.zf - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** A player's y is its eyes already (as in Alpha); a mob's is its feet. */
    public static float getEyeHeight(Entity e) { return e instanceof PlayerEntity ? 0 : e.height * 0.85F; }

    public static boolean canSee(Entity a, Entity b) {
        Vec3d from = new Vec3d(a.xf, a.yf + getEyeHeight(a), a.zf), to = new Vec3d(b.xf, b.yf + getEyeHeight(b), b.zf);
        return a.world.rayTrace(from, to) == null;
    }

    private static Block blockAt(Entity e, float dy) {
        World w = e.world;
        int x = floor(e.xf), y = floor(e.yf + dy), z = floor(e.zf);
        if (!inside(w, x, y, z)) return null;
        int id = w.getBlock(x, y, z);
        return id > 0 && id < Block.BY_ID.length ? Block.BY_ID[id] : null;
    }

    public static boolean isSubmergedIn(Entity e, Material m) {
        if (m == Material.WATER) return e.isEyeInWater();
        Block b = blockAt(e, getEyeHeight(e));
        return b != null && get_material(b) == m;
    }

    public static boolean isInWall(Entity e) {
        Block b = blockAt(e, getEyeHeight(e));
        return b != null && get_material(b).blocksMovement();
    }

    /** Classic has no right-click on mobs: never called by the game. */
    public static boolean interact(Entity e, PlayerEntity p) { return false; }

    public static Random get_random(Entity e) { return RANDOM; }
    public static void set_random(Entity e, Random r) {}

    /** Ticks lived: Classic's mobs and dropped things each keep a private tickCount. */
    public static int get_ticks(Entity e) { Object o = get(field(e.getClass(), "tickCount"), e); return o == null ? 0 : (Integer) o; }
    public static void set_ticks(Entity e, int v) {}
    public static int get_onFireTimer(Entity e) { return 0; }
    public static void set_onFireTimer(Entity e, int v) {}
    public static int get_breath(Entity e) { return e instanceof MobEntity ? ((MobEntity) e).airSupply : 300; }
    public static void set_breath(Entity e, int v) { if (e instanceof MobEntity) ((MobEntity) e).airSupply = v; }

    /** BotEntity's super.tick(): Classic's Mob.tick is final and the game calls it; the bot's own part runs from BotAI. */
    public static void tick(Entity e) {}

    public static boolean isAlive(Entity e) { return !e.removed && (!(e instanceof MobEntity) || ((MobEntity) e).health > 0); }

    /** Alpha's takeDamage: Classic's hurt (ClassicBot sends its own through BotEntity.takeDamage first). */
    public static boolean takeDamage(Entity e, Entity source, int amount) { e.hurt(source, amount); return true; }

    public static String getSaveId(Entity e) { return "ClassicBot"; }

    public static void writeCustomNbt(Entity e, NbtCompound n) { if (e instanceof MobEntity) n.putShort("Health", (short) ((MobEntity) e).health); }
    public static void readCustomNbt(Entity e, NbtCompound n) { if (e instanceof MobEntity && n.contains("Health")) ((MobEntity) e).health = n.getShort("Health"); }

    // ------------------------------------------------------------------ mobs (walking is the AI object's)

    private static final Map<Entity, float[]> moves = new WeakHashMap<>(); // walkSpeed, swing; for mobs without a BasicAI also sideways, forward, jump
    private static float[] mv(Entity e) { return moves.computeIfAbsent(e, k -> new float[]{0.7F, 0, 0, 0, 0}); }
    private static BasicAI ai(MobEntity m) { return m.ai instanceof BasicAI ? (BasicAI) m.ai : null; }

    public static float get_walkSpeed(MobEntity m) { return mv(m)[0]; }
    public static void set_walkSpeed(MobEntity m, float v) { mv(m)[0] = v; }
    public static float get_sidewaysSpeed(MobEntity m) { BasicAI a = ai(m); return a != null ? a.sidewaysSpeed : mv(m)[2]; }
    public static void set_sidewaysSpeed(MobEntity m, float v) { BasicAI a = ai(m); if (a != null) a.sidewaysSpeed = v; else mv(m)[2] = v; }
    public static float get_forwardSpeed(MobEntity m) { BasicAI a = ai(m); return a != null ? a.forwardSpeed : mv(m)[3]; }
    public static void set_forwardSpeed(MobEntity m, float v) { BasicAI a = ai(m); if (a != null) a.forwardSpeed = v; else mv(m)[3] = v; }
    public static boolean get_jumping(MobEntity m) { BasicAI a = ai(m); return a != null ? a.jumping : mv(m)[4] != 0; }
    public static void set_jumping(MobEntity m, boolean v) { BasicAI a = ai(m); if (a != null) a.jumping = v; else mv(m)[4] = v ? 1 : 0; }
    public static float get_attackAnimationProgress(MobEntity m) { return mv(m)[1]; }
    public static void set_attackAnimationProgress(MobEntity m, float v) { mv(m)[1] = v; }
    public static boolean get_interpolateOnly(MobEntity m) { return false; }
    public static void set_interpolateOnly(MobEntity m, boolean v) {}

    /** BotEntity's super.aiTick(): nothing (the AI object is Classic's whole mob brain). */
    public static void aiTick(MobEntity m) {}
    public static String getTexture(MobEntity m) { Object t = get(field(MobEntity.class, "textureName"), m); return t == null ? "/char.png" : (String) t; }
    public static String getAmbientSound(MobEntity m) { return null; }
    public static String getHurtSound(MobEntity m) { return "random.hurt"; }
    public static String getDeathSound(MobEntity m) { return "random.hurt"; }

    /** Classic's monsters (no common class): zombies (and skeletons, which are zombies), creepers, spiders. */
    public static boolean isMonster(Object o) { return o instanceof ZombieEntity || o instanceof CreeperEntity || o instanceof SpiderEntity; }

    public static String get_name(PlayerEntity p) { return playerName; }
    public static void set_name(PlayerEntity p, String n) {}

    public static World world(Entity e) { return e.world; }

    // ------------------------------------------------------------------ the player's inventory (block ids and counts)

    /** A copy: Classic keeps ids and counts in two arrays, so a write to it changes nothing. */
    public static ItemStack[] get_items(PlayerInventory inv) {
        ItemStack[] out = new ItemStack[inv.slots.length];
        for (int i = 0; i < out.length; i++) if (inv.slots[i] > 0 && inv.counts[i] > 0) out[i] = new ItemStack(inv.slots[i], inv.counts[i], 0);
        return out;
    }
    public static void set_items(PlayerInventory inv, ItemStack[] v) {}

    /** Into the player's inventory one at a time; true if it all went in (Alpha's addItem). Blocks only. */
    public static boolean addItem(PlayerInventory inv, ItemStack s) {
        if (s.id <= 0 || s.id >= Block.BY_ID.length || Block.BY_ID[s.id] == null) return false;
        while (s.size > 0 && inv.addResource(s.id)) s.size--;
        return s.size <= 0;
    }

    public static ItemStack getSelectedItem(PlayerInventory inv) {
        int i = inv.selectedSlot;
        return i >= 0 && i < inv.slots.length && inv.slots[i] > 0 && inv.counts[i] > 0 ? new ItemStack(inv.slots[i], inv.counts[i], 0) : null;
    }

    // ------------------------------------------------------------------ dropped things (Classic's Item entity: one block id)

    private static final Map<Entity, ItemStack> dropped = new WeakHashMap<>();

    /** The same stack each time, so the bots' "take some, remove it when empty" works. */
    public static ItemStack get_item(ItemEntity e) {
        ItemStack s = dropped.get(e);
        if (s == null) { Object r = get(field(ItemEntity.class, "resource"), e); dropped.put(e, s = new ItemStack(r == null ? 0 : (Integer) r, 1, 0)); }
        return s;
    }
    public static void set_item(ItemEntity e, ItemStack s) { dropped.put(e, s); }
    public static int get_pickUpDelay(ItemEntity e) { return 0; }
    public static void set_pickUpDelay(ItemEntity e, int v) {}

    public static ItemEntity newItemEntity(World w, double x, double y, double z, ItemStack s) {
        ItemEntity e = new ItemEntity(w, (float) x, (float) y, (float) z, s.id);
        dropped.put(e, s);
        return e;
    }

    // ------------------------------------------------------------------ blocks

    /** Classic has no materials: picked by id. Stone and the rest break by hand in Classic, so only bedrock is STONE (no tool can). */
    public static Material get_material(Block b) {
        switch (b.id) {
            case 0: return Material.AIR;
            case 6: case 37: case 38: case 39: case 40: return Material.PLANT;
            case 8: case 9: return Material.WATER;
            case 10: case 11: return Material.LAVA;
            case 5: case 17: case 47: return Material.WOOD;
            case 7: return Material.STONE;
            default: return Material.SOLID;
        }
    }
    public static void set_material(Block b, Material m) {}

    /** Classic's own drops, but a bot breaking a log gets the log (a player gets planks, and "bring me wood" means logs). */
    public static void dropItems(Block b, World w, int x, int y, int z, int meta) {
        if (b.id == 17) { if (!w.creativeMode) w.spawnEntity(new ItemEntity(w, x + 0.5F, y + 0.5F, z + 0.5F, 17)); return; }
        b.dropItemsf(w, x, y, z, 1.0F);
    }
    public static int getDropItem(Block b, int meta, Random r) { return b.id == 17 ? 17 : b.dropId(); }
    public static void onBroken(Block b, World w, int x, int y, int z, int meta) {}
    public static int getRenderType(Block b) { return get_material(b) == Material.PLANT ? 1 : 0; }

    public static float hardness(Block b) {
        switch (b.id) {
            case 18: case 6: case 37: case 38: case 39: case 40: return 0.2F;
            case 2: case 3: case 12: case 13: return 0.6F;
            case 5: case 17: case 47: return 2.0F;
            case 1: case 4: case 48: case 14: case 15: case 16: case 43: case 44: case 45: return 1.5F;
            case 41: case 42: return 3.0F;
            case 49: return 10.0F;
            case 7: return -1;
            default: return 0.8F;
        }
    }

    // ------------------------------------------------------------------ the level

    public static List get_entities(World w) { return w.entityMap.all; }
    public static void set_entities(World w, List l) {}

    public static List get_players(World w) {
        return w.player instanceof PlayerEntity ? Collections.singletonList(w.player) : Collections.emptyList();
    }
    public static void set_players(World w, List l) {}

    public static boolean get_isMultiplayer(World w) { return false; }
    public static void set_isMultiplayer(World w, boolean v) {}

    public static long get_ticks(World w) { Object o = get(field(World.class, "tickCount"), w); return o == null ? 0 : (Integer) o & 0xFFFFFFFFL; }
    public static void set_ticks(World w, long v) {}
    public static long get_time(World w) { return get_ticks(w); }
    public static void set_time(World w, long v) {}
    public static void setTime(World w, long v) {}
    public static int get_difficulty(World w) { return 2; }
    public static void set_difficulty(World w, int v) {}

    public static byte getData(World w, int x, int y, int z) { return 0; }
    public static int getBlockMetadata(World w, int x, int y, int z) { return 0; }
    public static boolean setBlockWithMetadata(World w, int x, int y, int z, int id, int meta) { return setBlock(w, x, y, z, id); }

    /** Only air or a block Classic has (Classic's setTile takes any byte and then fails to draw or tick it). */
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

    public static int getHeight(World w, int x, int z) {
        if (x < 0 || z < 0 || x >= w.sizeX || z >= w.sizeZ) return 0;
        for (int y = w.sizeY - 1; y >= 0; y--) if (w.getBlock(x, y, z) != 0) return y + 1;
        return 0;
    }

    public static net.minecraft.block.entity.BlockEntity getBlockEntity(World w, int x, int y, int z) { return null; }

    public static boolean isChunkLoaded(World w, int x, int y, int z) { return inside(w, x, y, z); }

    /** A stack of more than one block becomes that many of Classic's dropped blocks. */
    public static boolean addEntity(World w, Entity e) {
        w.spawnEntity(e);
        ItemStack s = e instanceof ItemEntity ? dropped.get(e) : null;
        if (s != null && s.size > 1) {
            for (int i = 1; i < s.size; i++) w.spawnEntity(new ItemEntity(w, e.xf, e.yf, e.zf, s.id));
            s.size = 1;
        }
        return true;
    }

    public static void playSound(World w, double x, double y, double z, String s, float v, float p) { w.playSoundf(s, (float) x, (float) y, (float) z, v, p); }
    public static void playSound(World w, Entity e, String s, float v, float p) { w.playSoundf(s, e.xf, e.yf, e.zf, v, p); }

    static boolean inside(World w, int x, int y, int z) { return x >= 0 && y >= 0 && z >= 0 && x < w.sizeX && y < w.sizeY && z < w.sizeZ; }

    public static void markDirty(Inventory i) {}

    // ------------------------------------------------------------------ what Classic keeps protected or private

    private static final Map<String, java.lang.reflect.Field> fields = new HashMap<>();

    /** A field by name in the class or its supers (null if none). */
    static java.lang.reflect.Field field(Class<?> c, String n) {
        String k = c.getName() + "." + n;
        if (fields.containsKey(k)) return fields.get(k);
        java.lang.reflect.Field f = null;
        for (Class<?> x = c; x != null && f == null; x = x.getSuperclass())
            try { f = x.getDeclaredField(n); f.setAccessible(true); } catch (Exception ignored) {}
        fields.put(k, f);
        return f;
    }

    static Object get(java.lang.reflect.Field f, Object o) { try { return f == null ? null : f.get(o); } catch (Exception e) { return null; } }
}
