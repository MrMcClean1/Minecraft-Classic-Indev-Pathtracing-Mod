package com.mrmcclean.classicpt.bots;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.EntityMap;
import net.minecraft.world.World;

/**
 * Classic's bot: Alpha's BotEntity plus what Classic needs on top.
 *  - Classic's Mob.tick is final and a mob walks by its AI object, so the bot's own tick and aiTick run from BotAI,
 *    whose BasicAI then jumps and walks by the speeds the bot set (Compat sends forwardSpeed and the rest there).
 *  - Damage comes in through Classic's hurt; it goes through BotEntity.takeDamage first (which can faint instead).
 *  - Classic saves a level with Java serialization: the bot goes into the file as {@link Saved}, its NbtCompound.
 */
public class ClassicBot extends BotEntity {
    private static final long serialVersionUID = 1L;
    private transient boolean hurting;

    public ClassicBot(World w) {
        super(w);
        this.ai = new BotAI(this);
        // Classic draws a mob's model 1.62 below its y (its mobs' y is their eyes, as a player's is); a bot's y is its
        // feet, as in Alpha, so its model goes back up by as much (it was drawn inside the ground)
        this.obf$renderOffset = 1.62F;
        this.obf$modelName = "humanoid"; // Classic's Mob has none (its render draws nothing then); its humanoid mobs set it
    }

    private transient int calm;

    /** One AI tick: the bot's tick and aiTick (Alpha's order: tick, then the AI in tickMovement). */
    void tickBot() {
        tick();
        aiTick();
        // Classic has no food (an Alpha bot eats to heal): a bot not hurt for 5 s gets 1 health back every 2 s, so it
        // can fight again instead of running from everything at low health
        if (hurtTime > 0) calm = 0;
        else if (++calm > 100 && calm % 40 == 0 && health > 0 && health < 20) heal(1);
    }

    @Override
    public void hurt(Entity source, int amount) {
        if (hurting) { super.hurt(source, amount); return; }
        hurting = true;
        try { takeDamage(shooter(source), amount); } finally { hurting = false; }
    }

    /**
     * An arrow's hit comes from the arrow; the bot answers whoever shot it (a skeleton), as it answers a zombie's blow.
     * Alpha gives the shooter as the source already.
     */
    private static Entity shooter(Entity source) {
        if (source == null || !source.getClass().getName().equals("com.mojang.minecraft.item.Arrow")) return source;
        Object o = Compat.get(Compat.field(source.getClass(), "owner"), source);
        return o instanceof Entity ? (Entity) o : source;
    }

    /**
     * Classic's "head under water" looks at y, a Classic mob's eyes; a bot's y is its feet, so it ran out of air with
     * wet feet and drowned in shallow water. Its eyes are 1.53 up.
     */
    @Override
    public boolean isEyeInWater() {
        int bx = Compat.floor(xf), by = Compat.floor(yf + 1.53F), bz = Compat.floor(zf);
        if (!Compat.inside(world, bx, by, bz)) return false;
        int id = world.getBlock(bx, by, bz);
        return id == 8 || id == 9;
    }

    /**
     * The light it is drawn in: Classic takes it at y + heightOffset / 2 - 0.5, a Classic mob's chest; for a bot (y at
     * its feet) that was the block under it, so it was drawn dark. The middle of its body instead.
     */
    @Override
    public float obf$getBrightness(float partial) {
        return world.obf$getBrightness(Compat.floor(xf), Compat.floor(yf + 0.9F), Compat.floor(zf));
    }

    Object writeReplace() { return new Saved(this); }

    /** What a bot leaves in the level file. Its level and entity map are the ones being read back (references). */
    static final class Saved implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        final World level;
        final EntityMap map;
        final float x, y, z, yaw;
        final NbtCompound nbt = new NbtCompound();

        Saved(ClassicBot b) {
            level = b.world;
            map = b.obf$blockMap;
            x = b.xf; y = b.yf; z = b.zf; yaw = b.yaw;
            b.writeCustomNbt(nbt);
        }

        Object readResolve() {
            ClassicBot b = new ClassicBot(level);
            b.setPositionAndAnglesf(x, y, z, yaw, 0);
            b.obf$xOld = x; b.obf$yOld = y; b.obf$zOld = z; // where the entity map has it
            b.obf$blockMap = map;
            b.readCustomNbt(nbt);
            Bots.log("read back " + b.botName() + " (owner " + b.owner + ")");
            return b;
        }
    }
}
