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
    }

    /** One AI tick: the bot's tick and aiTick (Alpha's order: tick, then the AI in tickMovement). */
    void tickBot() {
        tick();
        aiTick();
    }

    @Override
    public void hurt(Entity source, int amount) {
        if (hurting) { super.hurt(source, amount); return; }
        hurting = true;
        try { takeDamage(source, amount); } finally { hurting = false; }
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
